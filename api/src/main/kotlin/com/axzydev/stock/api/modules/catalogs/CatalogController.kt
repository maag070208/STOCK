package com.axzydev.stock.api.modules.catalogs

import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/v1/catalogs")
class CatalogController(private val catalogs: CatalogService) {

	@GetMapping
	@RequiresPermission("catalogs.view")
	fun list(): List<CatalogView> = catalogs.list()

	@GetMapping("/{code}/items")
	@RequiresPermission("catalogs.view")
	fun items(@PathVariable code: String, @RequestParam(defaultValue = "false") all: Boolean, locale: Locale): List<CatalogItemView> =
		catalogs.items(code, all, locale.language)

	@PostMapping
	@RequiresPermission("catalogs.manage")
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreateCatalogRequest, user: CurrentUser): CatalogView = catalogs.create(user, request)

	@PostMapping("/{code}/items")
	@RequiresPermission("catalogs.manage")
	@ResponseStatus(HttpStatus.CREATED)
	fun createItem(@PathVariable code: String, @RequestBody request: CreateItemRequest, user: CurrentUser, locale: Locale): CatalogItemView =
		catalogs.createItem(user, code, request, locale.language)

	@PatchMapping("/{code}/items/{itemCode}")
	@RequiresPermission("catalogs.manage")
	fun updateItem(
		@PathVariable code: String,
		@PathVariable itemCode: String,
		@RequestBody request: UpdateItemRequest,
		user: CurrentUser,
		locale: Locale,
	): CatalogItemView = catalogs.updateItem(user, code, itemCode, request, locale.language)
}
