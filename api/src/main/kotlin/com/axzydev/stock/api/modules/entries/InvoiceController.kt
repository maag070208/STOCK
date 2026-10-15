package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.idempotency.IdempotencyService
import com.axzydev.stock.api.core.idempotency.StoredResponse
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController(
	private val invoices: InvoiceService,
	private val idempotency: IdempotencyService,
	private val json: JsonMapper,
) {
	@PostMapping("/query")
	@RequiresPermission(InvoiceService.VIEW)
	fun query(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<InvoiceView> = invoices.query(user, request, locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(InvoiceService.VIEW)
	fun get(@PathVariable id: UUID, user: CurrentUser, locale: Locale): InvoiceView = invoices.get(user, id, locale.language)

	// Creation takes an Idempotency-Key (api.md section 2): the same key and request answer the first response again.
	@PostMapping
	@RequiresPermission(InvoiceService.CREATE)
	fun create(
		@RequestHeader("Idempotency-Key", required = false) key: String?,
		@RequestBody request: CreateInvoiceRequest,
		user: CurrentUser,
		locale: Locale,
	): ResponseEntity<String> {
		if (key.isNullOrBlank()) throw ValidationException(listOf(FieldError("Idempotency-Key", ErrorCode.FIELD_REQUIRED)))
		val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)))
		val stored = idempotency.execute(key, hash) {
			StoredResponse(HttpStatus.CREATED.value(), json.writeValueAsString(invoices.create(user, request, locale.language)))
		}
		return ResponseEntity.status(stored.status).contentType(MediaType.APPLICATION_JSON).body(stored.body)
	}

	// The warehouses an invoice can name: every active one. Who creates invoices needs them without reading locations.
	// Closing by hand, for the invoice captured fast (F8-03). It is audited, and it moves to CLOSED in one step.
	@PostMapping("/{id}/closure")
	@RequiresPermission(InvoiceService.CREATE)
	fun close(@PathVariable id: UUID, user: CurrentUser, locale: Locale): InvoiceView =
		invoices.close(user, id, locale.language)

	@GetMapping("/warehouse-options")
	@RequiresPermission(InvoiceService.CREATE)
	fun warehouseOptions(): List<Ref> = invoices.warehouseOptions()

	@PostMapping("/{id}/lines")
	@RequiresPermission(InvoiceService.CREATE)
	@ResponseStatus(HttpStatus.CREATED)
	fun addLine(@PathVariable id: UUID, @RequestBody request: AddLineRequest, user: CurrentUser, locale: Locale): LineView =
		invoices.addLine(user, id, request, locale.language)

	@PatchMapping("/{id}/lines/{lineId}")
	@RequiresPermission(InvoiceService.CREATE)
	fun updateLine(@PathVariable id: UUID, @PathVariable lineId: UUID, @RequestBody request: UpdateLineRequest, user: CurrentUser, locale: Locale): LineView =
		invoices.updateLine(user, id, lineId, request, locale.language)

	@DeleteMapping("/{id}/lines/{lineId}")
	@RequiresPermission(InvoiceService.CREATE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun removeLine(@PathVariable id: UUID, @PathVariable lineId: UUID, user: CurrentUser) = invoices.removeLine(user, id, lineId)

	@PutMapping("/{id}/lines/{lineId}/assignments")
	@RequiresPermission(InvoiceService.ASSIGN)
	fun setAssignments(@PathVariable id: UUID, @PathVariable lineId: UUID, @RequestBody request: AssignmentsRequest, user: CurrentUser, locale: Locale): LineView =
		invoices.setAssignments(user, id, lineId, request, locale.language)
}

data class StaffRequest(val userIds: List<UUID>? = null)

@RestController
@RequestMapping("/api/v1/warehouses/{id}/staff")
class StaffTemplateController(private val staff: StaffTemplateService) {

	@GetMapping
	@RequiresPermission(StaffTemplateService.ASSIGN, StaffTemplateService.MANAGE)
	fun list(@PathVariable id: UUID, user: CurrentUser): List<StaffMemberView> = staff.list(user, id)

	@PutMapping
	@RequiresPermission(StaffTemplateService.MANAGE)
	fun replace(@PathVariable id: UUID, @RequestBody request: StaffRequest, user: CurrentUser): List<StaffMemberView> = staff.replace(user, id, request.userIds)

	@GetMapping("/candidates")
	@RequiresPermission(StaffTemplateService.MANAGE)
	fun candidates(@PathVariable id: UUID, user: CurrentUser): List<StaffMemberView> = staff.candidates(user, id)
}
