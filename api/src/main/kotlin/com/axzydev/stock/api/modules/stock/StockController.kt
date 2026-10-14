package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Locale
import java.util.UUID

// Stock is read here. It changes only through the operations that cause it (the opening inventory, placement, picks),
// so there is no endpoint to write, change or delete a movement.
@RestController
@RequestMapping("/api/v1/stock")
class StockController(private val stock: StockService, private val queries: StockQueries, private val opening: OpeningInventoryService) {

	// Each balance with its location and material: the groups by aisle and rack are the filters `aisle` and `rack`.
	@PostMapping("/balances/query")
	@RequiresPermission(StockService.VIEW)
	fun balances(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<BalanceView> = queries.balances(user, request, locale.language)

	// Each material's stock in each warehouse.
	@PostMapping("/products/query")
	@RequiresPermission(StockService.VIEW)
	fun products(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): TablePage<ProductTotalView> =
		queries.productTotals(user, request, locale.language)

	@GetMapping("/movements/{id}")
	@RequiresPermission(StockService.VIEW)
	fun movement(@PathVariable id: UUID, user: CurrentUser, locale: Locale): MovementView = stock.movement(user, id, locale.language)

	// The opening inventory, from the legacy file uploaded as a form, into the warehouse's TEMPORARY location.
	@PostMapping("/opening/dry-run")
	@RequiresPermission(OPENING)
	fun openingDryRun(@RequestParam("file") file: MultipartFile, @RequestParam("warehouseId", required = false) warehouseId: UUID?, locale: Locale): OpeningReport =
		opening.dryRun(file.bytes, warehouseId, locale)

	@PostMapping("/opening")
	@RequiresPermission(OPENING)
	fun openingConfirm(
		@RequestParam("file") file: MultipartFile,
		@RequestParam("warehouseId", required = false) warehouseId: UUID?,
		@RequestParam("checksum") checksum: String,
		user: CurrentUser,
		locale: Locale,
	): OpeningReport = opening.confirm(user, file.bytes, warehouseId, checksum, locale)

	companion object {
		const val OPENING = "stock.opening"
	}
}
