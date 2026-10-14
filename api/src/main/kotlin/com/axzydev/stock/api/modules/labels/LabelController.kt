package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/v1/label-templates")
class LabelTemplateController(private val templates: LabelTemplateService) {

	@GetMapping
	@RequiresPermission(LabelTemplateService.VIEW)
	fun list(locale: Locale): List<TemplateView> = templates.list(locale.language)

	@GetMapping("/{id}")
	@RequiresPermission(LabelTemplateService.VIEW)
	fun get(@PathVariable id: UUID, locale: Locale): TemplateView = templates.get(id, locale.language)

	@GetMapping("/{id}/versions")
	@RequiresPermission(LabelTemplateService.VIEW)
	fun versions(@PathVariable id: UUID): List<VersionView> = templates.versions(id)

	// The editor's live report: writes nothing.
	@PostMapping("/validate")
	@RequiresPermission(LabelTemplateService.MANAGE)
	fun validate(@RequestBody request: ValidateRequest, locale: Locale): ValidationReport = templates.validate(request, locale)

	@PostMapping
	@RequiresPermission(LabelTemplateService.MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreateTemplateRequest, user: CurrentUser, locale: Locale): TemplateView = templates.create(user, request, locale.language)

	@PatchMapping("/{id}")
	@RequiresPermission(LabelTemplateService.MANAGE)
	fun update(@PathVariable id: UUID, @RequestBody request: UpdateTemplateRequest, user: CurrentUser, locale: Locale): TemplateView =
		templates.update(user, id, request, locale.language)
}

@RestController
@RequestMapping("/api/v1/printers")
class PrinterController(private val printing: PrintService) {

	@GetMapping
	@RequiresPermission(PrintService.VIEW)
	fun list(): List<PrinterView> = printing.list()

	@GetMapping("/{id}")
	@RequiresPermission(PrintService.VIEW)
	fun get(@PathVariable id: UUID): PrinterView = printing.get(id)

	@PostMapping
	@RequiresPermission(PrintService.MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: PrinterRequest, user: CurrentUser): PrinterView = printing.create(user, request)

	@PatchMapping("/{id}")
	@RequiresPermission(PrintService.MANAGE)
	fun update(@PathVariable id: UUID, @RequestBody request: UpdatePrinterRequest, user: CurrentUser): PrinterView = printing.update(user, id, request)
}

@RestController
@RequestMapping("/api/v1/labels")
class PrintController(private val printing: PrintService) {

	// The printers this person may print on, without their addresses.
	@GetMapping("/printers")
	@RequiresPermission(LabelTemplateService.PRINT)
	fun printers(user: CurrentUser): List<PrintablePrinter> = printing.printable(user)

	@PostMapping("/print")
	@RequiresPermission(LabelTemplateService.PRINT)
	fun print(@RequestBody request: PrintRequest, user: CurrentUser): PrintResult = printing.print(user, request)
}
