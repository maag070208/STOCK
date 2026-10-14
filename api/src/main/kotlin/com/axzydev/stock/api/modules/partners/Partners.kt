package com.axzydev.stock.api.modules.partners

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.catalogs.CatalogService
import jakarta.persistence.Embeddable
import org.springframework.stereotype.Component
import java.util.UUID

// What suppliers and customers share (suppliers.md, customers.md): the structured address, payment terms and
// contacts, and the rules of validation.md for each. Each module keeps its own tables, endpoints and audit actions.

@Embeddable
class Address(
	var street: String,
	var exteriorNumber: String,
	var interiorNumber: String?,
	var neighborhood: String,
	var city: String,
	var stateId: UUID,
	var postalCode: String,
	var countryId: UUID,
)

// `state`, `country` and `paymentTerms` are catalog codes, such as MX_JAL, MX and NET_30.
data class AddressBody(
	val street: String? = null,
	val exteriorNumber: String? = null,
	val interiorNumber: String? = null,
	val neighborhood: String? = null,
	val city: String? = null,
	val state: String? = null,
	val postalCode: String? = null,
	val country: String? = null,
)

data class CreatePartnerRequest(
	val code: String? = null,
	val name: String? = null,
	val taxId: String? = null,
	val address: AddressBody? = null,
	val paymentTerms: String? = null,
)

// The code is not here: it cannot change. A field that is sent replaces the value; the address is sent whole.
data class UpdatePartnerRequest(val name: String? = null, val taxId: String? = null, val address: AddressBody? = null, val paymentTerms: String? = null)

// For an update, a field that is sent replaces the value: an empty email or phone removes it.
data class ContactRequest(val name: String? = null, val email: String? = null, val phone: String? = null)

data class CatalogRef(val code: String, val label: String?)

data class AddressView(
	val street: String,
	val exteriorNumber: String,
	val interiorNumber: String?,
	val neighborhood: String,
	val city: String,
	val state: CatalogRef?,
	val postalCode: String,
	val country: CatalogRef?,
)

data class ContactView(val id: UUID, val name: String, val email: String?, val phone: String?, val active: Boolean)

class PartnerValues(val code: String, val name: String, val taxId: String, val address: Address, val paymentTermsId: UUID)

class PartnerChanges(val name: String?, val taxId: String?, val address: Address?, val paymentTermsId: UUID?)

class ContactValues(val name: String, val email: String?, val phone: String?)

@Component
class PartnerRules(private val catalogs: CatalogService) {

	fun create(request: CreatePartnerRequest): PartnerValues {
		val check = Validator()
		val code = check.required("code", request.code, Rules::code) { it.uppercase() }
		val name = check.required("name", request.name, Rules::name)
		val taxId = check.required("taxId", request.taxId, Rules::taxId) { it.uppercase() }
		val address = address(check, request.address)
		val paymentTerms = catalogItem(check, "paymentTerms", PAYMENT_TERMS, request.paymentTerms)
		check.check()
		return PartnerValues(code, name, taxId, address!!, paymentTerms!!)
	}

	fun update(request: UpdatePartnerRequest): PartnerChanges {
		val check = Validator()
		val name = request.name?.let { check.required("name", it, Rules::name) }
		val taxId = request.taxId?.let { check.required("taxId", it, Rules::taxId) { value -> value.uppercase() } }
		val address = request.address?.let { address(check, it) }
		val paymentTerms = request.paymentTerms?.let { catalogItem(check, "paymentTerms", PAYMENT_TERMS, it) }
		check.check()
		return PartnerChanges(name, taxId, address, paymentTerms)
	}

	// A contact needs an email or a phone (suppliers.md section 5). `current` is the contact being changed, if any.
	fun contact(request: ContactRequest, current: ContactValues? = null): ContactValues {
		val check = Validator()
		val name = if (current == null || request.name != null) check.required("name", request.name, Rules::name) else current.name
		val email = if (current == null || request.email != null) check.optional("email", request.email, Rules::email) { it.lowercase() } else current.email
		val phone = if (current == null || request.phone != null) check.optional("phone", request.phone, Rules::phone, Rules::normalizePhone) else current.phone
		check.check()
		if (email == null && phone == null) {
			throw ValidationException(listOf(FieldError("email", ErrorCode.CONTACT_CHANNEL_REQUIRED), FieldError("phone", ErrorCode.CONTACT_CHANNEL_REQUIRED)))
		}
		return ContactValues(name, email, phone)
	}

	// The address with its catalog values named in the language of the request.
	fun addressView(address: Address, refs: Map<UUID, CatalogRef>) = AddressView(
		street = address.street,
		exteriorNumber = address.exteriorNumber,
		interiorNumber = address.interiorNumber,
		neighborhood = address.neighborhood,
		city = address.city,
		state = refs[address.stateId],
		postalCode = address.postalCode,
		country = refs[address.countryId],
	)

	// The catalog values a set of records points to, looked up together. Inactive ones too: history shows them.
	fun refs(ids: Collection<UUID>, language: String): Map<UUID, CatalogRef> =
		catalogs.itemViews(ids, language).mapValues { (_, item) -> CatalogRef(item.code, item.label) }

	// Each part of the address with its rule. The state must belong to the address's country.
	private fun address(check: Validator, body: AddressBody?): Address? {
		if (body == null) {
			check.reject("address", ErrorCode.FIELD_REQUIRED)
			return null
		}
		val street = check.required("address.street", body.street, Rules.text(120))
		val exterior = check.required("address.exteriorNumber", body.exteriorNumber, Rules::addressNumber)
		val interior = check.optional("address.interiorNumber", body.interiorNumber, Rules::addressNumber)
		val neighborhood = check.required("address.neighborhood", body.neighborhood, Rules.text(80))
		val city = check.required("address.city", body.city, Rules.text(80))
		val postalCode = check.required("address.postalCode", body.postalCode, Rules::postalCode)
		val country = catalogItem(check, "address.country", COUNTRY, body.country)
		val countryCode = body.country?.trim()?.uppercase()
		val state = catalogItem(check, "address.state", STATE, body.state) { attributes -> country == null || attributes["country"] == countryCode }
		return if (state != null && country != null) Address(street, exterior, interior, neighborhood, city, state, postalCode, country) else null
	}

	// An active value of the catalog, by its code. With `belongs`, the value must also pass that check of its attributes.
	private fun catalogItem(check: Validator, field: String, catalog: String, code: String?, belongs: (Map<String, Any?>) -> Boolean = { true }): UUID? {
		val clean = code?.trim()?.uppercase().orEmpty()
		if (clean.isEmpty()) {
			check.reject(field, ErrorCode.FIELD_REQUIRED)
			return null
		}
		val item = catalogs.activeItem(catalog, clean)
		if (item == null || !belongs(item.second)) {
			check.reject(field, ErrorCode.CATALOG_VALUE_INVALID)
			return null
		}
		return item.first
	}

	companion object {
		const val PAYMENT_TERMS = "PAYMENT_TERMS"
		const val COUNTRY = "COUNTRY"
		const val STATE = "STATE"
	}
}
