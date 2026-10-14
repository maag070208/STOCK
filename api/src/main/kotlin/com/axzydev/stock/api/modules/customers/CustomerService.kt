package com.axzydev.stock.api.modules.customers

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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

// `contacts` comes with a single customer, not with a list.
data class CustomerView(
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

// Customers and their contacts (customers.md), the mirror of suppliers. Only active customers are offered for new orders;
// a deactivated one stays on the orders that name it. The rules are the ones suppliers share, in PartnerRules.
@Service
class CustomerService(
	private val customers: CustomerRepository,
	private val contacts: CustomerContactRepository,
	private val rules: PartnerRules,
	private val audit: AuditService,
	private val clock: Clock,
) {
	private val table = TableSpec<Customer>(
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
	fun query(request: TableRequest, language: String): TablePage<CustomerView> =
		customers.table(request, table) { it }.mapRows { rows ->
			val refs = rules.refs(rows.flatMap(::catalogIds), language)
			rows.map { view(it, refs, contacts = null) }
		}

	@Transactional(readOnly = true)
	fun get(id: UUID, language: String): CustomerView = detail(find(id), language)

	@Transactional
	fun create(actor: CurrentUser, request: CreatePartnerRequest, language: String): CustomerView {
		val values = rules.create(request)
		if (customers.findByCode(values.code) != null) throw ValidationException(listOf(FieldError("code", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val customer = customers.save(Customer(values.code, values.name, values.taxId, values.address, values.paymentTermsId, actor.id, now, now))
		audit.record(actor.actor, "CUSTOMER_CREATED", "customer", customer.id.toString(), after = snapshot(customer))
		return detail(customer, language)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdatePartnerRequest, language: String): CustomerView {
		val customer = find(id)
		val before = snapshot(customer)
		val changes = rules.update(request)
		changes.name?.let { customer.name = it }
		changes.taxId?.let { customer.taxId = it }
		changes.address?.let { customer.address = it }
		changes.paymentTermsId?.let { customer.paymentTermsId = it }
		customer.updatedAt = clock.instant()
		audit.record(actor.actor, "CUSTOMER_UPDATED", "customer", id.toString(), before, snapshot(customer))
		return detail(customer, language)
	}

	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID, language: String): CustomerView {
		val customer = find(id)
		if (customer.active) {
			customer.active = false
			customer.updatedAt = clock.instant()
			audit.record(actor.actor, "CUSTOMER_DEACTIVATED", "customer", id.toString())
		}
		return detail(customer, language)
	}

	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID, language: String): CustomerView {
		val customer = find(id)
		if (!customer.active) {
			customer.active = true
			customer.updatedAt = clock.instant()
			audit.record(actor.actor, "CUSTOMER_REACTIVATED", "customer", id.toString())
		}
		return detail(customer, language)
	}

	@Transactional
	fun addContact(actor: CurrentUser, id: UUID, request: ContactRequest): ContactView {
		find(id)
		val values = rules.contact(request)
		val now = clock.instant()
		val contact = contacts.save(CustomerContact(id, values.name, values.email, values.phone, actor.id, now, now))
		audit.record(actor.actor, "CUSTOMER_CONTACT_CREATED", "customer_contact", contact.id.toString(), after = snapshot(contact))
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
		audit.record(actor.actor, "CUSTOMER_CONTACT_UPDATED", "customer_contact", contactId.toString(), before, snapshot(contact))
		return view(contact)
	}

	// Only active contacts of the chosen customer appear on an order; a deactivated one stays on the orders that name it.
	@Transactional
	fun setContactActive(actor: CurrentUser, id: UUID, contactId: UUID, active: Boolean): ContactView {
		val contact = findContact(id, contactId)
		if (contact.active != active) {
			contact.active = active
			contact.updatedAt = clock.instant()
			if (active) audit.record(actor.actor, "CUSTOMER_CONTACT_REACTIVATED", "customer_contact", contactId.toString())
			else audit.record(actor.actor, "CUSTOMER_CONTACT_DEACTIVATED", "customer_contact", contactId.toString())
		}
		return view(contact)
	}

	private fun find(id: UUID): Customer = customers.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	// For other modules: an order names an active customer, and optionally one of its active contacts.
	fun requireForNewRecord(check: com.axzydev.stock.api.core.validation.Validator, customerId: UUID?, contactId: UUID?) {
		val customer = customerId?.let { customers.findById(it).orElse(null) }
		when {
			customerId == null -> check.reject("customerId", ErrorCode.FIELD_REQUIRED)
			customer == null || !customer.active -> check.reject("customerId", ErrorCode.CATALOG_VALUE_INVALID)
		}
		if (contactId != null && (customerId == null || contacts.findByIdAndCustomerId(contactId, customerId)?.active != true)) {
			check.reject("contactId", ErrorCode.CATALOG_VALUE_INVALID)
		}
	}

	// For other modules: the code and name of each customer, and the name of each contact, for display.
	fun names(customerIds: Collection<UUID>, contactIds: Collection<UUID>): Pair<Map<UUID, Pair<String, String>>, Map<UUID, String>> =
		customers.findAllById(customerIds.toSet()).associate { it.id!! to (it.code to it.name) } to
			contacts.findAllById(contactIds.toSet()).associate { it.id!! to it.name }

	private fun findContact(id: UUID, contactId: UUID): CustomerContact {
		find(id)
		return contacts.findByIdAndCustomerId(contactId, id) ?: throw ApiException(ErrorCode.NOT_FOUND)
	}

	private fun catalogIds(customer: Customer) = listOf(customer.address.stateId, customer.address.countryId, customer.paymentTermsId)

	private fun detail(customer: Customer, language: String) =
		view(customer, rules.refs(catalogIds(customer), language), contacts.findByCustomerIdOrderByName(customer.id!!).map(::view))

	private fun view(customer: Customer, refs: Map<UUID, CatalogRef>, contacts: List<ContactView>?) = CustomerView(
		id = customer.id!!,
		code = customer.code,
		name = customer.name,
		taxId = customer.taxId,
		address = rules.addressView(customer.address, refs),
		paymentTerms = refs[customer.paymentTermsId],
		active = customer.active,
		createdAt = customer.createdAt,
		contacts = contacts,
	)

	private fun view(contact: CustomerContact) = ContactView(contact.id!!, contact.name, contact.email, contact.phone, contact.active)

	private fun snapshot(customer: Customer): Map<String, Any?> = mapOf(
		"code" to customer.code, "name" to customer.name, "taxId" to customer.taxId,
		"address" to with(customer.address) { listOfNotNull(street, exteriorNumber, interiorNumber, neighborhood, city, postalCode).joinToString(", ") },
		"stateId" to customer.address.stateId.toString(), "countryId" to customer.address.countryId.toString(),
		"paymentTermsId" to customer.paymentTermsId.toString(), "active" to customer.active,
	)

	private fun snapshot(contact: CustomerContact): Map<String, Any?> =
		mapOf("customerId" to contact.customerId.toString(), "name" to contact.name, "email" to contact.email, "phone" to contact.phone, "active" to contact.active)
}
