package com.axzydev.stock.api.modules.orders

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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
@RequestMapping("/api/v1/orders")
class OrderController(
	private val orders: OrderService,
	private val alerts: OrderAlertService,
	private val documents: OrderDocumentService,
	private val idempotency: IdempotencyService,
	private val json: JsonMapper,
) {
	@PostMapping("/query")
	@RequiresPermission(OrderService.VIEW)
	fun query(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<OrderView> =
		orders.query(user, request, locale.language)

	// The due date alerts of the signed-in person (order-fulfillment.md section 7a). The literal path goes before `/{id}`.
	@GetMapping("/alerts")
	@RequiresPermission(OrderService.VIEW)
	fun alerts(user: CurrentUser, locale: Locale): OrderAlertsResponse = this.alerts.mine(user, locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(OrderService.VIEW)
	fun get(@PathVariable id: UUID, user: CurrentUser, locale: Locale): OrderView = orders.get(user, id, locale.language)

	// The comprobante of a validated order (order-fulfillment.md section 10): the stored bytes, never regenerated.
	@GetMapping("/{id}/document")
	@RequiresPermission(OrderService.VIEW)
	fun document(@PathVariable id: UUID, user: CurrentUser): ResponseEntity<ByteArray> {
		val document = documents.read(user, id)
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${document.fileName}\"")
			.contentType(MediaType.parseMediaType(document.contentType))
			.body(document.content)
	}

	// Creation takes an Idempotency-Key (api.md section 2): the same key and request answer the first response again.
	@PostMapping
	@RequiresPermission(OrderService.CREATE)
	fun create(
		@RequestHeader("Idempotency-Key", required = false) key: String?,
		@RequestBody request: CreateOrderRequest,
		user: CurrentUser,
		locale: Locale,
	): ResponseEntity<String> {
		if (key.isNullOrBlank()) throw ValidationException(listOf(FieldError("Idempotency-Key", ErrorCode.FIELD_REQUIRED)))
		val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)))
		val stored = idempotency.execute(key, hash) {
			StoredResponse(HttpStatus.CREATED.value(), json.writeValueAsString(orders.create(user, request, locale.language)))
		}
		return ResponseEntity.status(stored.status).contentType(MediaType.APPLICATION_JSON).body(stored.body)
	}

	@PutMapping("/{id}/assignment")
	@RequiresPermission(OrderService.ASSIGN)
	fun assign(@PathVariable id: UUID, @RequestBody request: AssignRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.assign(user, id, request, locale.language)

	// The people an order of this warehouse can be assigned to, for the assignment form.
	@GetMapping("/operators")
	@RequiresPermission(OrderService.ASSIGN)
	fun operators(@RequestParam warehouseId: UUID, user: CurrentUser): List<OrderOperatorView> = orders.operators(user, warehouseId)

	// What a creator asks while writing the lines of a new order.
	@PostMapping("/availability")
	@RequiresPermission(OrderService.VIEW)
	fun availability(@RequestBody request: AvailabilityRequest, user: CurrentUser): AvailabilityResponse = orders.availability(user, request)

	@PostMapping("/{id}/picks")
	@RequiresPermission(OrderService.FULFILL)
	fun pick(@PathVariable id: UUID, @RequestBody request: PickBody, user: CurrentUser, locale: Locale): OrderView =
		orders.pick(user, id, request, locale.language)

	@PostMapping("/{id}/validation")
	@RequiresPermission(OrderService.VALIDATE)
	fun validate(@PathVariable id: UUID, @RequestBody request: NoteRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.validate(user, id, request, locale.language)

	@PostMapping("/{id}/accept-difference")
	@RequiresPermission(OrderService.ACCEPT_DIFFERENCE)
	fun acceptDifference(@PathVariable id: UUID, @RequestBody request: NoteRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.acceptDifference(user, id, request, locale.language)

	@PostMapping("/{id}/cancellation")
	@RequiresPermission(OrderService.CANCEL)
	fun cancel(@PathVariable id: UUID, user: CurrentUser, locale: Locale): OrderView = orders.cancel(user, id, locale.language)

	@PostMapping("/{id}/reassignments/operator")
	@RequiresPermission(OrderService.REASSIGN)
	fun reassignOperator(@PathVariable id: UUID, @RequestBody request: ReassignOperatorRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.reassignOperator(user, id, request, locale.language)

	@PostMapping("/{id}/reassignments/transfer")
	@RequiresPermission(OrderService.REASSIGN)
	fun transferPicks(@PathVariable id: UUID, @RequestBody request: TransferPicksRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.transferPicks(user, id, request, locale.language)

	@PostMapping("/{id}/reassignments/return")
	@RequiresPermission(OrderService.REASSIGN)
	fun returnPicks(@PathVariable id: UUID, @RequestBody request: ReturnPicksRequest, user: CurrentUser, locale: Locale): OrderView =
		orders.returnPicks(user, id, request, locale.language)
}
