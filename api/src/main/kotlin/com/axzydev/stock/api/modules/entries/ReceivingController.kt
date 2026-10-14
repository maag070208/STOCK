package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/receipts")
class ReceivingController(private val receiving: ReceivingService) {

	// The signed-in operator's assignments still to count or place, grouped by invoice.
	@GetMapping("/mine")
	@RequiresPermission(ReceivingService.VIEW)
	fun mine(user: CurrentUser, locale: Locale): List<MyReceiptView> = receiving.mine(user, locale.language)

	@PostMapping("/assignments/{id}/count")
	@RequiresPermission(ReceivingService.RECEIVE)
	fun count(@PathVariable id: UUID, @RequestBody request: CountRequest, user: CurrentUser): AssignmentView = receiving.count(user, id, request)

	@PostMapping("/assignments/{id}/adjustment")
	@RequiresPermission(ReceivingService.RESOLVE)
	fun adjust(@PathVariable id: UUID, @RequestBody request: AdjustmentRequest, user: CurrentUser): AssignmentView = receiving.adjust(user, id, request)

	@PostMapping("/assignments/{id}/placements")
	@RequiresPermission(ReceivingService.PLACE)
	fun place(@PathVariable id: UUID, @RequestBody request: PlacementsRequest, user: CurrentUser): AssignmentView = receiving.place(user, id, request)

	@PostMapping("/discrepancies/query")
	@RequiresPermission(ReceivingService.RESOLVE)
	fun discrepancies(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<DiscrepancyView> =
		receiving.discrepancies(user, request, locale.language)
}
