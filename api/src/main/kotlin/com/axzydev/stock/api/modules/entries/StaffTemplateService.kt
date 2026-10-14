package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.identity.users.Person
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class StaffMemberView(val id: UUID, val username: String, val name: String)

// The staff template of each warehouse (material-entry.md, question 4): the operators its assignments pick from.
// An operator is an active person who can count receipts and works in the warehouse. Someone who stops being one
// leaves the list without anyone editing it.
@Service
class StaffTemplateService(
	private val staff: StaffMemberRepository,
	private val people: UserService,
	private val warehouses: WarehouseService,
	private val audit: AuditService,
) {
	// Who assigns entries in the warehouse reads its template, and so does who manages it.
	@Transactional(readOnly = true)
	fun list(user: CurrentUser, warehouseId: UUID): List<StaffMemberView> {
		requireExists(warehouseId)
		if (!user.reaches(ASSIGN, warehouseId) && !user.reaches(MANAGE, warehouseId)) throw ApiException(ErrorCode.NOT_FOUND)
		return operators(warehouseId).values.map { StaffMemberView(it.id, it.username, it.name) }.sortedBy { it.name }
	}

	// The manager of the warehouse, or someone with ALL, replaces the template.
	@Transactional
	fun replace(user: CurrentUser, warehouseId: UUID, userIds: List<UUID>?): List<StaffMemberView> {
		requireExists(warehouseId)
		user.requireWrite(MANAGE, warehouseId)
		val before = staff.findByWarehouseId(warehouseId).map { it.userId.toString() }.sorted()
		val ids = userIds?.toSet() ?: throw ValidationException(listOf(FieldError("userIds", ErrorCode.FIELD_REQUIRED)))
		val found = people.people(ids)
		if (ids.any { id -> found[id]?.let { isOperator(it, warehouseId) } != true }) {
			throw ValidationException(listOf(FieldError("userIds", ErrorCode.CATALOG_VALUE_INVALID)))
		}
		staff.deleteByWarehouseId(warehouseId)
		staff.flush()
		staff.saveAll(ids.map { StaffMember(warehouseId, it) })
		audit.record(user.actor, "STAFF_TEMPLATE_CHANGED", "warehouse", warehouseId.toString(), mapOf("userIds" to before), mapOf("userIds" to ids.map { it.toString() }.sorted()))
		return list(user, warehouseId)
	}

	// Who can join the template: the active operators who work in the warehouse.
	@Transactional(readOnly = true)
	fun candidates(user: CurrentUser, warehouseId: UUID): List<StaffMemberView> {
		requireExists(warehouseId)
		if (!user.reaches(MANAGE, warehouseId)) throw ApiException(ErrorCode.NOT_FOUND)
		return people.peopleInWarehouse(warehouseId).filter { isOperator(it, warehouseId) }.map { StaffMemberView(it.id, it.username, it.name) }.sortedBy { it.name }
	}

	// The operators of the template who still are operators of the warehouse, by id.
	@Transactional(readOnly = true)
	fun operators(warehouseId: UUID): Map<UUID, Person> =
		people.people(staff.findByWarehouseId(warehouseId).map { it.userId }).filterValues { isOperator(it, warehouseId) }

	private fun requireExists(warehouseId: UUID) {
		if (warehouses.codes(listOf(warehouseId)).isEmpty()) throw ApiException(ErrorCode.NOT_FOUND)
	}

	private fun isOperator(person: Person, warehouseId: UUID): Boolean =
		person.active && person.access.can(RECEIVE) && (person.access.scopeOf(RECEIVE) == com.axzydev.stock.api.core.security.Scope.ALL || warehouseId in person.access.warehouseIds)

	companion object {
		const val MANAGE = "staff_templates.manage"
		const val ASSIGN = "receipts.assign"
		const val RECEIVE = "receipts.receive"
	}
}
