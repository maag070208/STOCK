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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/receipts")
class ReceivingController(
	private val receiving: ReceivingService,
	private val idempotency: IdempotencyService,
	private val json: JsonMapper,
) {

	// The signed-in operator's assignments still to count or place, grouped by invoice.
	@GetMapping("/mine")
	@RequiresPermission(ReceivingService.VIEW)
	fun mine(@RequestParam(required = false) productId: UUID?, user: CurrentUser, locale: Locale): List<MyReceiptView> = receiving.mine(user, locale.language, productId)

	@PostMapping("/assignments/{id}/count")
	@RequiresPermission(ReceivingService.RECEIVE)
	fun count(
		@PathVariable id: UUID,
		@RequestHeader("Idempotency-Key", required = false) key: String?,
		@RequestBody request: CountRequest,
		user: CurrentUser,
	): ResponseEntity<String> = idempotent(key, id, request) { receiving.count(user, id, request) }

	@PostMapping("/assignments/{id}/adjustment")
	@RequiresPermission(ReceivingService.RESOLVE)
	fun adjust(@PathVariable id: UUID, @RequestBody request: AdjustmentRequest, user: CurrentUser): AssignmentView = receiving.adjust(user, id, request)

	@PostMapping("/assignments/{id}/placements")
	@RequiresPermission(ReceivingService.PLACE)
	fun place(
		@PathVariable id: UUID,
		@RequestHeader("Idempotency-Key", required = false) key: String?,
		@RequestBody request: PlacementsRequest,
		user: CurrentUser,
	): ResponseEntity<String> = idempotent(key, id, request) { receiving.place(user, id, request) }

	@PostMapping("/discrepancies/query")
	@RequiresPermission(ReceivingService.RESOLVE)
	fun discrepancies(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<DiscrepancyView> =
		receiving.discrepancies(user, request, locale.language)

	private fun idempotent(key: String?, assignmentId: UUID, request: Any, action: () -> AssignmentView): ResponseEntity<String> {
		if (key.isNullOrBlank()) throw ValidationException(listOf(FieldError("Idempotency-Key", ErrorCode.FIELD_REQUIRED)))
		val payload = json.writeValueAsBytes(mapOf("assignmentId" to assignmentId, "request" to request))
		val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload))
		val stored = idempotency.execute(key, hash) {
			StoredResponse(HttpStatus.OK.value(), json.writeValueAsString(action()))
		}
		return ResponseEntity.status(stored.status).contentType(MediaType.APPLICATION_JSON).body(stored.body)
	}
}
