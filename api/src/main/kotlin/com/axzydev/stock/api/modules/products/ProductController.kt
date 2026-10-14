package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val products: ProductService, private val imports: ProductImportService, private val lookups: ProductLookup) {

	// A scanned or typed value: barcode, then code, then name (barcode-scanning.md section 3).
	@GetMapping("/lookup")
	@RequiresPermission(VIEW)
	fun lookup(@RequestParam("value", required = false) value: String?, user: CurrentUser, locale: Locale): LookupResult = lookups.lookup(user, value, locale.language)

	@PostMapping("/query")
	@RequiresPermission(VIEW)
	fun query(@RequestBody request: TableRequest, locale: Locale): TablePage<ProductView> = products.query(request, locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(VIEW)
	fun get(@PathVariable id: UUID, locale: Locale): ProductView = products.get(id, locale.language)

	@PostMapping
	@RequiresPermission(MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreateProductRequest, user: CurrentUser, locale: Locale): ProductView = products.create(user, request, locale.language)

	@PatchMapping("/{id}")
	@RequiresPermission(MANAGE)
	fun update(@PathVariable id: UUID, @RequestBody request: UpdateProductRequest, user: CurrentUser, locale: Locale): ProductView =
		products.update(user, id, request, locale.language)

	@DeleteMapping("/{id}")
	@RequiresPermission(MANAGE)
	fun deactivate(@PathVariable id: UUID, user: CurrentUser, locale: Locale): ProductView = products.deactivate(user, id, locale.language)

	@PostMapping("/{id}/reactivate")
	@RequiresPermission(MANAGE)
	fun reactivate(@PathVariable id: UUID, user: CurrentUser, locale: Locale): ProductView = products.reactivate(user, id, locale.language)

	// The legacy file, uploaded as a form: a dry run that writes nothing, then the confirmation with the dry run's checksum.
	// The media type is not declared: the access check answers first, and a request without the form is FORMAT_INVALID.
	@PostMapping("/import/dry-run")
	@RequiresPermission(MANAGE)
	fun importDryRun(@RequestParam("file") file: MultipartFile, locale: Locale): ImportReport = imports.dryRun(file.bytes, locale)

	@PostMapping("/import")
	@RequiresPermission(MANAGE)
	fun importConfirm(@RequestParam("file") file: MultipartFile, @RequestParam("checksum") checksum: String, user: CurrentUser, locale: Locale): ImportReport =
		imports.confirm(user, file.bytes, checksum, locale)

	companion object {
		const val VIEW = "products.view"
		const val MANAGE = "products.manage"
	}
}
