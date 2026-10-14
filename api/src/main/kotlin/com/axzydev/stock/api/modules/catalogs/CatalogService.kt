package com.axzydev.stock.api.modules.catalogs

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

data class LabelBody(val label: String? = null, val description: String? = null)
data class CreateCatalogRequest(val code: String? = null)

data class CreateItemRequest(
	val code: String? = null,
	val labels: Map<String, LabelBody>? = null,
	val sortOrder: Int? = null,
	val active: Boolean? = null,
	val attributes: Map<String, Any?>? = null,
)

// The code is not here: it cannot change. A field that is left out keeps its value.
data class UpdateItemRequest(
	val labels: Map<String, LabelBody>? = null,
	val sortOrder: Int? = null,
	val active: Boolean? = null,
	val attributes: Map<String, Any?>? = null,
)

data class CatalogView(val code: String, val isSystem: Boolean, val active: Boolean, val items: Long)

// `label` and `description` are in the language of the request. `labels` holds every language.
data class CatalogItemView(
	val code: String,
	val label: String?,
	val description: String?,
	val sortOrder: Int,
	val active: Boolean,
	val attributes: Map<String, Any?>,
	val labels: Map<String, LabelBody>,
)

@Service
class CatalogService(
	private val catalogs: CatalogRepository,
	private val items: CatalogItemRepository,
	private val audit: AuditService,
	private val json: JsonMapper,
	private val clock: Clock,
) {
	@Transactional(readOnly = true)
	fun list(): List<CatalogView> = catalogs.findAll().sortedBy { it.code }.map(::view)

	// Active items in their order. With `all`, the inactive ones too: history still shows them.
	@Transactional(readOnly = true)
	fun items(catalogCode: String, all: Boolean, language: String): List<CatalogItemView> {
		val catalog = find(catalogCode)
		return items.findByCatalogId(catalog.id!!)
			.filter { all || it.active }
			.sortedWith(compareBy({ it.sortOrder }, { it.code }))
			.map { view(it, language) }
	}

	@Transactional
	fun create(actor: CurrentUser, request: CreateCatalogRequest): CatalogView {
		val code = request.code?.trim()?.uppercase().orEmpty()
		if (code.isEmpty()) throw ValidationException(listOf(FieldError("code", ErrorCode.FIELD_REQUIRED)))
		if (!CatalogRules.CATALOG_CODE.matches(code)) throw ValidationException(listOf(FieldError("code", ErrorCode.FORMAT_INVALID)))
		if (catalogs.findByCode(code) != null) throw ValidationException(listOf(FieldError("code", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val catalog = catalogs.save(Catalog(code, isSystem = false, createdBy = actor.id, createdAt = now, updatedAt = now))
		audit.record(actor.actor, "CATALOG_CREATED", "catalog", code)
		return view(catalog)
	}

	@Transactional
	fun createItem(actor: CurrentUser, catalogCode: String, request: CreateItemRequest, language: String): CatalogItemView {
		val catalog = find(catalogCode)
		val code = request.code?.trim()?.uppercase().orEmpty()
		val labels = request.labels.orEmpty()
		val attributes = request.attributes.orEmpty()
		val active = request.active ?: true
		val errors = buildList {
			if (code.isEmpty()) add(FieldError("code", ErrorCode.FIELD_REQUIRED)) else if (!CatalogRules.CODE.matches(code)) add(FieldError("code", ErrorCode.FORMAT_INVALID))
			if (request.sortOrder != null && request.sortOrder < 0) add(FieldError("sortOrder", ErrorCode.FORMAT_INVALID))
			addAll(CatalogRules.labels(labels))
			addAll(CatalogRules.attributes(attributes))
			addAll(CatalogRules.activation(active, labels.keys))
		}
		if (errors.isNotEmpty()) throw ValidationException(errors)
		if (items.findByCatalogIdAndCode(catalog.id!!, code) != null) throw ValidationException(listOf(FieldError("code", ErrorCode.DUPLICATE_VALUE)))

		val now = clock.instant()
		val sortOrder = request.sortOrder ?: ((items.findByCatalogId(catalog.id!!).maxOfOrNull { it.sortOrder } ?: 0) + 1)
		val item = CatalogItem(catalog.id!!, code, sortOrder, active, json.writeValueAsString(attributes), actor.id, now, now)
		labels.forEach { (locale, body) -> item.labels[locale] = CatalogLabel(body.label!!.trim(), body.description?.trim()?.ifEmpty { null }) }
		val saved = items.save(item)
		audit.record(actor.actor, "CATALOG_ITEM_CREATED", "catalog_item", "$catalogCode:$code", after = snapshot(saved))
		return view(saved, language)
	}

	@Transactional
	fun updateItem(actor: CurrentUser, catalogCode: String, itemCode: String, request: UpdateItemRequest, language: String): CatalogItemView {
		val catalog = find(catalogCode)
		val item = items.findByCatalogIdAndCode(catalog.id!!, itemCode) ?: throw ApiException(ErrorCode.NOT_FOUND)
		val before = snapshot(item)
		val active = request.active ?: item.active
		val errors = buildList {
			if (request.sortOrder != null && request.sortOrder < 0) add(FieldError("sortOrder", ErrorCode.FORMAT_INVALID))
			addAll(CatalogRules.labels(request.labels.orEmpty()))
			addAll(CatalogRules.attributes(request.attributes.orEmpty()))
			addAll(CatalogRules.activation(active, item.labels.keys + request.labels.orEmpty().keys))
		}
		if (errors.isNotEmpty()) throw ValidationException(errors)

		request.labels?.forEach { (locale, body) -> item.labels[locale] = CatalogLabel(body.label!!.trim(), body.description?.trim()?.ifEmpty { null }) }
		request.sortOrder?.let { item.sortOrder = it }
		request.attributes?.let { item.attributes = json.writeValueAsString(it) }
		item.active = active
		item.updatedAt = clock.instant()
		audit.record(actor.actor, "CATALOG_ITEM_UPDATED", "catalog_item", "$catalogCode:$itemCode", before, snapshot(item))
		return view(item, language)
	}

	// For other modules: the active item of a catalog with this code, for a new record. Null when it does not exist or is inactive.
	@Transactional(readOnly = true)
	fun activeItemId(catalogCode: String, itemCode: String): UUID? {
		val catalog = catalogs.findByCode(catalogCode) ?: return null
		return items.findByCatalogIdAndCode(catalog.id!!, itemCode.trim().uppercase())?.takeIf { it.active }?.id
	}

	// For other modules: the active item with this code, with its attributes, for a new record. Null when it does not exist or is inactive.
	@Transactional(readOnly = true)
	fun activeItem(catalogCode: String, itemCode: String): Pair<UUID, Map<String, Any?>>? {
		val catalog = catalogs.findByCode(catalogCode) ?: return null
		val item = items.findByCatalogIdAndCode(catalog.id!!, itemCode.trim().uppercase())?.takeIf { it.active } ?: return null
		return item.id!! to attributes(item)
	}

	// For other modules: the item with this code, active or not, for filters over existing records.
	@Transactional(readOnly = true)
	fun itemId(catalogCode: String, itemCode: String): UUID? {
		val catalog = catalogs.findByCode(catalogCode) ?: return null
		return items.findByCatalogIdAndCode(catalog.id!!, itemCode.trim().uppercase())?.id
	}

	// For other modules: the items a list of records points to, labeled in the request's language. Inactive items too: history shows them.
	@Transactional(readOnly = true)
	fun itemViews(ids: Collection<UUID>, language: String): Map<UUID, CatalogItemView> =
		items.findAllById(ids.toSet()).associate { it.id!! to view(it, language) }

	private fun find(code: String): Catalog = catalogs.findByCode(code) ?: throw ApiException(ErrorCode.NOT_FOUND)

	private fun view(catalog: Catalog) = CatalogView(catalog.code, catalog.isSystem, catalog.active, items.countByCatalogId(catalog.id!!))

	@Suppress("UNCHECKED_CAST")
	private fun attributes(item: CatalogItem): Map<String, Any?> = json.readValue(item.attributes, Map::class.java) as Map<String, Any?>

	private fun view(item: CatalogItem, language: String): CatalogItemView {
		// Spanish is the default language: it stands in when the requested one has no label.
		val shown = item.labels[language] ?: item.labels["es"]
		return CatalogItemView(
			code = item.code,
			label = shown?.label,
			description = shown?.description,
			sortOrder = item.sortOrder,
			active = item.active,
			attributes = attributes(item),
			labels = item.labels.toSortedMap().mapValues { LabelBody(it.value.label, it.value.description) },
		)
	}

	private fun snapshot(item: CatalogItem): Map<String, Any?> = mapOf(
		"code" to item.code,
		"sortOrder" to item.sortOrder,
		"active" to item.active,
		"attributes" to attributes(item),
		"labels" to item.labels.toSortedMap().mapValues { it.value.label },
	)
}
