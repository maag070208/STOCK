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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
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
	private val idempotency: IdempotencyService,
	private val json: JsonMapper,
) {
	@PostMapping("/query")
	@RequiresPermission(OrderService.VIEW)
	fun query(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<OrderView> =
		orders.query(user, request, locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(OrderService.VIEW)
	fun get(@PathVariable id: UUID, user: CurrentUser, locale: Locale): OrderView = orders.get(user, id, locale.language)

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

	@PostMapping("/{id}/picks")
	@RequiresPermission(OrderService.FULFILL)
	fun pick(@PathVariable id: UUID, @RequestBody request: PickBody, user: CurrentUser, locale: Locale): OrderView =
		orders.pick(user, id, request, locale.language)
}
