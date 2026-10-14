package com.axzydev.stock.api.core.db

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.time.format.DateTimeParseException

// The table contract of api.md section 2: POST /{resource}/query with { page, limit, filters, sort }.
data class TableRequest(
	val page: Int? = null,
	val limit: Int? = null,
	val filters: Map<String, Any?>? = null,
	val sort: TableSort? = null,
)

data class TableSort(val key: String? = null, val direction: String? = null)

// The response shape the Axzy UI System's table reads, as in CYC.
data class TablePage<T>(
	val data: List<T>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
) {
	// The same page with its rows turned into others in one go, for rows that need data looked up together.
	fun <R> mapRows(transform: (List<T>) -> List<R>): TablePage<R> =
		TablePage(transform(data), total, page, pageIndex, totalPages, totalCount, limit, hasPreviousPage, hasNextPage)
}

typealias TableFilter<T> = (root: Root<T>, builder: CriteriaBuilder, value: Any) -> Predicate

// What a table lets the client filter and sort by. A filter or a sort outside these lists is rejected, never ignored.
class TableSpec<T : Any>(
	private val filters: Map<String, TableFilter<T>>,
	private val sorts: Map<String, String>,
	private val defaultSort: Sort,
) {
	fun specification(request: TableRequest): Specification<T> = Specification { root, _, builder ->
		val predicates = request.filters.orEmpty()
			.filterValues { it != null && it != "" }
			.map { (name, value) ->
				val filter = filters[name] ?: throw ValidationException(listOf(FieldError("filters.$name", ErrorCode.UNKNOWN_FIELD)))
				filter(root, builder, value!!)
			}
		builder.and(*predicates.toTypedArray())
	}

	fun sort(request: TableRequest): Sort {
		val key = request.sort?.key ?: return defaultSort
		val property = sorts[key] ?: throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		return Sort.by(if (request.sort.direction.equals("desc", ignoreCase = true)) Sort.Direction.DESC else Sort.Direction.ASC, property)
	}

	companion object {
		const val DEFAULT_LIMIT = 10
		const val MAX_LIMIT = 200

		private fun <T> path(root: Root<T>, property: String): Path<Any> =
			property.split(".").fold(root as Path<Any>) { node, part -> node.get(part) }

		private fun invalid(name: String): Nothing = throw ValidationException(listOf(FieldError("filters.$name", ErrorCode.FORMAT_INVALID)))

		// Text that contains the value, whatever its case.
		fun <T> contains(property: String): TableFilter<T> = { root, builder, value ->
			val pattern = "%" + value.toString().trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
			builder.like(builder.lower(path(root, property).`as`(String::class.java)), pattern, '\\')
		}

		fun <T> equalTo(property: String): TableFilter<T> = { root, builder, value -> builder.equal(path(root, property), value.toString()) }

		// The record with this id, such as the warehouse a location belongs to.
		fun <T> id(property: String): TableFilter<T> = { root, builder, value ->
			val id = try {
				java.util.UUID.fromString(value.toString())
			} catch (e: IllegalArgumentException) {
				invalid(property)
			}
			builder.equal(path(root, property), id)
		}

		// Text equal to the value once uppercased, for codes and their parts.
		fun <T> code(property: String): TableFilter<T> = { root, builder, value -> builder.equal(path(root, property), value.toString().trim().uppercase()) }

		fun <T> flag(property: String): TableFilter<T> = { root, builder, value ->
			val flag = when (value.toString()) {
				"true" -> true
				"false" -> false
				else -> invalid(property)
			}
			builder.equal(path(root, property), flag)
		}

		// A range of instants: [from, to], both in ISO 8601. Either end can be missing.
		fun <T> between(property: String): TableFilter<T> = { root, builder, value ->
			val ends = (value as? List<*>)?.takeIf { it.size == 2 } ?: invalid(property)
			val (from, to) = try {
				ends.map { end -> end?.toString()?.takeIf { it.isNotBlank() }?.let { java.time.OffsetDateTime.parse(it).toInstant() } }
			} catch (e: DateTimeParseException) {
				invalid(property)
			}
			if (from != null && to != null && from.isAfter(to)) invalid(property)
			@Suppress("UNCHECKED_CAST")
			val column = path(root, property) as Path<Instant>
			builder.and(*listOfNotNull(from?.let { builder.greaterThanOrEqualTo(column, it) }, to?.let { builder.lessThanOrEqualTo(column, it) }).toTypedArray())
		}
	}
}

// Runs a table query. `scope` is always applied with AND, so no filter can widen what the user may see.
fun <T : Any, R> JpaSpecificationExecutor<T>.table(
	request: TableRequest,
	spec: TableSpec<T>,
	scope: Specification<T>? = null,
	map: (T) -> R,
): TablePage<R> {
	val limit = (request.limit ?: TableSpec.DEFAULT_LIMIT).coerceIn(1, TableSpec.MAX_LIMIT)
	val page = (request.page ?: 1).coerceAtLeast(1)
	val where = spec.specification(request).let { if (scope == null) it else it.and(scope) }
	val result = findAll(where, PageRequest.of(page - 1, limit, spec.sort(request)))
	return TablePage(
		data = result.content.map(map),
		total = result.totalElements,
		page = page,
		pageIndex = page - 1,
		totalPages = result.totalPages,
		totalCount = result.totalElements,
		limit = limit,
		hasPreviousPage = page > 1,
		hasNextPage = page < result.totalPages,
	)
}
