package com.axzydev.stock.api.modules.suppliers

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.partners.AddressView
import com.axzydev.stock.api.modules.partners.CatalogRef
import com.axzydev.stock.api.modules.partners.ContactRequest
import com.axzydev.stock.api.modules.partners.ContactValues
import com.axzydev.stock.api.modules.partners.ContactView
import com.axzydev.stock.api.modules.partners.CreatePartnerRequest
import com.axzydev.stock.api.modules.partners.PartnerRules
import com.axzydev.stock.api.modules.partners.UpdatePartnerRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

// `contacts` comes with a single supplier, not with a list.
data class SupplierView(
	val id: UUID,
	val code: String,
	val name: String,
	val taxId: String,
	val address: AddressView,
	val paymentTerms: CatalogRef?,
	val active: Boolean,
	val createdAt: Instant,
	val contacts: List<ContactView>?,
)

// Suppliers and their contacts (suppliers.md). Only active suppliers are offered for new invoices; a deactivated one
// stays on the invoices that name it. The rules are the ones customers share, in PartnerRules.
@Service
class SupplierService(
	private val suppliers: SupplierRepository,
	private val contacts: SupplierContactRepository,
	private val rules: PartnerRules,
	private val audit: AuditService,
	private val clock: Clock,
) {
	private val table = TableSpec<Supplier>(
		filters = mapOf(
			"code" to TableSpec.contains("code"),
			"name" to TableSpec.contains("name"),
			"taxId" to TableSpec.contains("taxId"),
			"active" to TableSpec.flag("active"),
		),
		sorts = mapOf("code" to "code", "name" to "name", "taxId" to "taxId", "createdAt" to "createdAt"),
		defaultSort = Sort.by("code"),
	)

	@Transactional(readOnly = true)
	fun query(request: TableRequest, language: String): TablePage<SupplierView> =
		suppliers.table(request, table) { it }.mapRows { rows ->
			val refs = rules.refs(rows.flatMap(::catalogIds), language)
			rows.map { view(it, refs, contacts = null) }
		}

	@Transactional(readOnly = true)
	fun get(id: UUID, language: String): SupplierView = detail(find(id), language)

	@Transactional
	fun create(actor: CurrentUser, request: CreatePartnerRequest, language: String): SupplierView {
		val values = rules.create(request)
		if (suppliers.findByCode(values.code) != null) throw ValidationException(listOf(FieldError("code", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val supplier = suppliers.save(Supplier(values.code, values.name, values.taxId, values.address, values.paymentTermsId, actor.id, now, now))
		audit.record(actor.actor, "SUPPLIER_CREATED", "supplier", supplier.id.toString(), after = snapshot(supplier))
		return detail(supplier, language)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdatePartnerRequest, language: String): SupplierView {
		val supplier = find(id)
		val before = snapshot(supplier)
		val changes = rules.update(request)
		changes.name?.let { supplier.name = it }
		changes.taxId?.let { supplier.taxId = it }
		changes.address?.let { supplier.address = it }
		changes.paymentTermsId?.let { supplier.paymentTermsId = it }
		supplier.updatedAt = clock.instant()
		audit.record(actor.actor, "SUPPLIER_UPDATED", "supplier", id.toString(), before, snapshot(supplier))
		return detail(supplier, language)
	}

	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID, language: String): SupplierView {
		val supplier = find(id)
		if (supplier.active) {
			supplier.active = false
			supplier.updatedAt = clock.instant()
			audit.record(actor.actor, "SUPPLIER_DEACTIVATED", "supplier", id.toString())
		}
		return detail(supplier, language)
	}

	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID, language: String): SupplierView {
		val supplier = find(id)
		if (!supplier.active) {
			supplier.active = true
			supplier.updatedAt = clock.instant()
			audit.record(actor.actor, "SUPPLIER_REACTIVATED", "supplier", id.toString())
		}
		return detail(supplier, language)
	}

	@Transactional
	fun addContact(actor: CurrentUser, id: UUID, request: ContactRequest): ContactView {
		find(id)
		val values = rules.contact(request)
		val now = clock.instant()
		val contact = contacts.save(SupplierContact(id, values.name, values.email, values.phone, actor.id, now, now))
		audit.record(actor.actor, "SUPPLIER_CONTACT_CREATED", "supplier_contact", contact.id.toString(), after = snapshot(contact))
		return view(contact)
	}

	@Transactional
	fun updateContact(actor: CurrentUser, id: UUID, contactId: UUID, request: ContactRequest): ContactView {
		val contact = findContact(id, contactId)
		val before = snapshot(contact)
		val values = rules.contact(request, ContactValues(contact.name, contact.email, contact.phone))
		contact.name = values.name
		contact.email = values.email
		contact.phone = values.phone
		contact.updatedAt = clock.instant()
		audit.record(actor.actor, "SUPPLIER_CONTACT_UPDATED", "supplier_contact", contactId.toString(), before, snapshot(contact))
		return view(contact)
	}

	// Only active contacts of the chosen supplier appear on an invoice; a deactivated one stays on the invoices that name it.
	@Transactional
	fun setContactActive(actor: CurrentUser, id: UUID, contactId: UUID, active: Boolean): ContactView {
		val contact = findContact(id, contactId)
		if (contact.active != active) {
			contact.active = active
			contact.updatedAt = clock.instant()
			if (active) audit.record(actor.actor, "SUPPLIER_CONTACT_REACTIVATED", "supplier_contact", contactId.toString())
			else audit.record(actor.actor, "SUPPLIER_CONTACT_DEACTIVATED", "supplier_contact", contactId.toString())
		}
		return view(contact)
	}

	// For other modules: a supplier and contact for a new record. The supplier must be active, and the contact, if any,
	// an active contact of that supplier; otherwise CATALOG_VALUE_INVALID on the field.
	@Transactional(readOnly = true)
	fun requireForNewRecord(check: com.axzydev.stock.api.core.validation.Validator, supplierId: UUID?, contactId: UUID?) {
		val supplier = supplierId?.let { suppliers.findById(it).orElse(null) }
		when {
			supplierId == null -> check.reject("supplierId", ErrorCode.FIELD_REQUIRED)
			supplier == null || !supplier.active -> check.reject("supplierId", ErrorCode.CATALOG_VALUE_INVALID)
		}
		if (contactId != null && (supplierId == null || contacts.findByIdAndSupplierId(contactId, supplierId)?.active != true)) {
			check.reject("contactId", ErrorCode.CATALOG_VALUE_INVALID)
		}
	}

	// For other modules: the suppliers whose code or name contains a term, for a search box that reads both (F8-02).
	@Transactional(readOnly = true)
	fun idsMatching(term: String): List<UUID> =
		suppliers.findAll(
			Specification { root, _, builder ->
				val like = "%" + term.trim().lowercase() + "%"
				builder.or(builder.like(builder.lower(root.get("code")), like), builder.like(builder.lower(root.get("name")), like))
			},
		).map { it.id!! }

	// For other modules: the code and name of each supplier, and the name of each contact, for display.
	@Transactional(readOnly = true)
	fun names(supplierIds: Collection<UUID>, contactIds: Collection<UUID>): Pair<Map<UUID, Pair<String, String>>, Map<UUID, String>> =
		suppliers.findAllById(supplierIds.toSet()).associate { it.id!! to (it.code to it.name) } to
			contacts.findAllById(contactIds.toSet()).associate { it.id!! to it.name }

	private fun find(id: UUID): Supplier = suppliers.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun findContact(id: UUID, contactId: UUID): SupplierContact {
		find(id)
		return contacts.findByIdAndSupplierId(contactId, id) ?: throw ApiException(ErrorCode.NOT_FOUND)
	}

	private fun catalogIds(supplier: Supplier) = listOf(supplier.address.stateId, supplier.address.countryId, supplier.paymentTermsId)

	private fun detail(supplier: Supplier, language: String) =
		view(supplier, rules.refs(catalogIds(supplier), language), contacts.findBySupplierIdOrderByName(supplier.id!!).map(::view))

	private fun view(supplier: Supplier, refs: Map<UUID, CatalogRef>, contacts: List<ContactView>?) = SupplierView(
		id = supplier.id!!,
		code = supplier.code,
		name = supplier.name,
		taxId = supplier.taxId,
		address = rules.addressView(supplier.address, refs),
		paymentTerms = refs[supplier.paymentTermsId],
		active = supplier.active,
		createdAt = supplier.createdAt,
		contacts = contacts,
	)

	private fun view(contact: SupplierContact) = ContactView(contact.id!!, contact.name, contact.email, contact.phone, contact.active)

	private fun snapshot(supplier: Supplier): Map<String, Any?> = mapOf(
		"code" to supplier.code, "name" to supplier.name, "taxId" to supplier.taxId,
		"address" to with(supplier.address) { listOfNotNull(street, exteriorNumber, interiorNumber, neighborhood, city, postalCode).joinToString(", ") },
		"stateId" to supplier.address.stateId.toString(), "countryId" to supplier.address.countryId.toString(),
		"paymentTermsId" to supplier.paymentTermsId.toString(), "active" to supplier.active,
	)

	private fun snapshot(contact: SupplierContact): Map<String, Any?> =
		mapOf("supplierId" to contact.supplierId.toString(), "name" to contact.name, "email" to contact.email, "phone" to contact.phone, "active" to contact.active)
}
