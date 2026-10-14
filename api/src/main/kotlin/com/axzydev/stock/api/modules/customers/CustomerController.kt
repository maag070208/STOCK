package com.axzydev.stock.api.modules.customers

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import com.axzydev.stock.api.modules.partners.ContactRequest
import com.axzydev.stock.api.modules.partners.ContactView
import com.axzydev.stock.api.modules.partners.CreatePartnerRequest
import com.axzydev.stock.api.modules.partners.UpdatePartnerRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController(private val customers: CustomerService) {

	@PostMapping("/query")
	@RequiresPermission(VIEW)
	fun query(@RequestBody request: TableRequest, locale: Locale): TablePage<CustomerView> = customers.query(request, locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(VIEW)
	fun get(@PathVariable id: UUID, locale: Locale): CustomerView = customers.get(id, locale.language)

	@PostMapping
	@RequiresPermission(MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreatePartnerRequest, user: CurrentUser, locale: Locale): CustomerView = customers.create(user, request, locale.language)

	@PatchMapping("/{id}")
	@RequiresPermission(MANAGE)
	fun update(@PathVariable id: UUID, @RequestBody request: UpdatePartnerRequest, user: CurrentUser, locale: Locale): CustomerView =
		customers.update(user, id, request, locale.language)

	@DeleteMapping("/{id}")
	@RequiresPermission(MANAGE)
	fun deactivate(@PathVariable id: UUID, user: CurrentUser, locale: Locale): CustomerView = customers.deactivate(user, id, locale.language)

	@PostMapping("/{id}/reactivate")
	@RequiresPermission(MANAGE)
	fun reactivate(@PathVariable id: UUID, user: CurrentUser, locale: Locale): CustomerView = customers.reactivate(user, id, locale.language)

	@PostMapping("/{id}/contacts")
	@RequiresPermission(MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun addContact(@PathVariable id: UUID, @RequestBody request: ContactRequest, user: CurrentUser): ContactView = customers.addContact(user, id, request)

	@PatchMapping("/{id}/contacts/{contactId}")
	@RequiresPermission(MANAGE)
	fun updateContact(@PathVariable id: UUID, @PathVariable contactId: UUID, @RequestBody request: ContactRequest, user: CurrentUser): ContactView =
		customers.updateContact(user, id, contactId, request)

	@DeleteMapping("/{id}/contacts/{contactId}")
	@RequiresPermission(MANAGE)
	fun deactivateContact(@PathVariable id: UUID, @PathVariable contactId: UUID, user: CurrentUser): ContactView =
		customers.setContactActive(user, id, contactId, active = false)

	@PostMapping("/{id}/contacts/{contactId}/reactivate")
	@RequiresPermission(MANAGE)
	fun reactivateContact(@PathVariable id: UUID, @PathVariable contactId: UUID, user: CurrentUser): ContactView =
		customers.setContactActive(user, id, contactId, active = true)

	companion object {
		const val VIEW = "customers.view"
		const val MANAGE = "customers.manage"
	}
}
