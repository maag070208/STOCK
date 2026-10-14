package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.warehouses.LocationService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class PrinterRequest(val name: String? = null, val warehouseId: UUID? = null, val ipAddress: String? = null)

data class UpdatePrinterRequest(val name: String? = null, val warehouseId: UUID? = null, val ipAddress: String? = null, val active: Boolean? = null)

data class PrinterView(val id: UUID, val name: String, val warehouseId: UUID, val warehouseCode: String?, val ipAddress: String, val port: Int, val active: Boolean, val updatedAt: Instant)

// What someone who prints sees of a printer: never its address.
data class PrintablePrinter(val id: UUID, val name: String, val warehouseId: UUID, val warehouseCode: String?)

data class PrintRequest(val templateKey: String? = null, val productId: UUID? = null, val locationId: UUID? = null, val printerId: UUID? = null, val copies: Int? = null)

data class PrintResult(val printerId: UUID, val templateKey: String, val version: Int, val copies: Int)

// Sends ZPL to a printer: one connection to its address on the printing port, the data, and close.
// A printer that does not answer within the timeout is PRINTER_UNREACHABLE (labels.md section 6).
@Component
class PrinterClient(
	// 9100 for every printer (labels.md section 5). The test settings move it, so a fake printer can listen.
	@Value("\${stockflow.printing.port}") private val port: Int,
	@Value("\${stockflow.printing.timeout-ms}") private val timeoutMs: Int,
) {
	fun send(ipAddress: String, zpl: String) {
		try {
			Socket().use { socket ->
				socket.connect(InetSocketAddress(ipAddress, port), timeoutMs)
				socket.soTimeout = timeoutMs
				socket.getOutputStream().apply {
					write(zpl.toByteArray(Charsets.UTF_8))
					flush()
				}
			}
		} catch (e: IOException) {
			throw ApiException(ErrorCode.PRINTER_UNREACHABLE)
		}
	}
}

// Printers and printing (labels.md sections 5 and 6). A label only reaches a printer of the catalog, never an address
// a person typed; its data reaches the ZPL only through the sanitizer; and every print is audited, a failed one never.
@Service
class PrintService(
	private val printers: PrinterRepository,
	private val templates: LabelTemplateService,
	private val products: ProductService,
	private val locations: LocationService,
	private val warehouses: WarehouseService,
	private val client: PrinterClient,
	private val audit: AuditService,
	private val clock: Clock,
) {
	@Transactional(readOnly = true)
	fun list(): List<PrinterView> {
		val all = printers.findAll().sortedBy { it.name }
		val codes = warehouses.codes(all.map { it.warehouseId })
		return all.map { view(it, codes) }
	}

	@Transactional(readOnly = true)
	fun get(id: UUID): PrinterView = find(id).let { view(it, warehouses.codes(listOf(it.warehouseId))) }

	@Transactional
	fun create(actor: CurrentUser, request: PrinterRequest): PrinterView {
		val check = Validator()
		val name = check.required("name", request.name, ::name)
		val ip = check.required("ipAddress", request.ipAddress, Rules::ipAddress)
		warehouses.requireActive(check, "warehouseId", request.warehouseId)
		check.check()
		val now = clock.instant()
		val printer = printers.save(Printer(name, request.warehouseId!!, ip, actor.id, now, now))
		audit.record(actor.actor, "PRINTER_CREATED", "printer", printer.id.toString(), after = snapshot(printer))
		return get(printer.id!!)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdatePrinterRequest): PrinterView {
		val printer = find(id)
		val before = snapshot(printer)
		val check = Validator()
		val name = request.name?.let { check.required("name", it, ::name) }
		val ip = request.ipAddress?.let { check.required("ipAddress", it, Rules::ipAddress) }
		request.warehouseId?.let { warehouses.requireActive(check, "warehouseId", it) }
		check.check()
		name?.let { printer.name = it }
		ip?.let { printer.ipAddress = it }
		request.warehouseId?.let { printer.warehouseId = it }
		request.active?.let { printer.active = it }
		printer.updatedAt = clock.instant()
		audit.record(actor.actor, "PRINTER_UPDATED", "printer", id.toString(), before, snapshot(printer))
		return get(id)
	}

	// The active printers of the warehouses the person prints in.
	@Transactional(readOnly = true)
	fun printable(user: CurrentUser): List<PrintablePrinter> {
		val reachable = if (user.scopeOf(LabelTemplateService.PRINT) == Scope.ALL) printers.findAll() else printers.findByWarehouseIdIn(user.warehouseIds)
		val active = reachable.filter { it.active }.sortedBy { it.name }
		val codes = warehouses.codes(active.map { it.warehouseId })
		return active.map { PrintablePrinter(it.id!!, it.name, it.warehouseId, codes[it.warehouseId]) }
	}

	@Transactional
	fun print(user: CurrentUser, request: PrintRequest): PrintResult {
		val check = Validator()
		val template = request.templateKey?.let(templates::printable)
		if (template == null) check.reject("templateKey", if (request.templateKey.isNullOrBlank()) ErrorCode.FIELD_REQUIRED else ErrorCode.CATALOG_VALUE_INVALID)
		val printer = request.printerId?.let { printers.findById(it).orElse(null) }?.takeIf { it.active }
		if (printer == null) check.reject("printerId", if (request.printerId == null) ErrorCode.FIELD_REQUIRED else ErrorCode.CATALOG_VALUE_INVALID)
		when (val copies = request.copies) {
			null -> check.reject("copies", ErrorCode.FIELD_REQUIRED)
			else -> ZplRules.copies(copies)?.let { check.reject("copies", it) }
		}
		check.check()
		user.requireWrite(LabelTemplateService.PRINT, printer!!.warehouseId)
		val (label, zpl) = template!!
		val (target, values) = values(check, label, request, printer)
		val filled = ZplRules.fill(zpl, values, request.copies!!)
		// The filled label is checked again: whatever the data was, only allowed commands reach the printer.
		kotlin.check(ZplRules.check(label.key, filled).isEmpty()) { "a filled label broke the template rules" }
		client.send(printer.ipAddress, filled)
		audit.record(
			user.actor, "LABEL_PRINTED", target.first, target.second,
			after = mapOf(
				"templateId" to label.id.toString(), "templateKey" to label.key, "version" to label.version,
				"values" to values, "printerId" to printer.id.toString(), "copies" to request.copies,
			),
		)
		return PrintResult(printer.id!!, label.key, label.version, request.copies)
	}

	// The item of the label and its values: a product prints anywhere the person prints; a location, on a printer of its
	// own warehouse. A label of another key prints only its fixed text.
	private fun values(check: Validator, label: LabelTemplate, request: PrintRequest, printer: Printer): Pair<Pair<String, String>, Map<String, String>> =
		when (label.key) {
			"PRODUCT" -> {
				val product = request.productId?.let { runCatching { products.get(it, "es") }.getOrNull() }?.takeIf { it.active }
				if (product == null) {
					check.reject("productId", if (request.productId == null) ErrorCode.FIELD_REQUIRED else ErrorCode.CATALOG_VALUE_INVALID)
					check.check()
				}
				("product" to product!!.id.toString()) to
					mapOf("code" to product.code, "name" to product.name, "barcode" to product.barcode, "unit" to (product.unit.label ?: product.unit.code))
			}
			"LOCATION" -> {
				val location = request.locationId?.let(locations::labelData)
				if (location == null) {
					check.reject("locationId", if (request.locationId == null) ErrorCode.FIELD_REQUIRED else ErrorCode.CATALOG_VALUE_INVALID)
					check.check()
				}
				if (location!!.first != printer.warehouseId) throw ApiException(ErrorCode.LOCATION_NOT_IN_WAREHOUSE)
				("location" to request.locationId.toString()) to location.second
			}
			else -> ("label_template" to label.id.toString()) to emptyMap()
		}

	private fun name(value: String) = if (value.length > 60) ErrorCode.FIELD_TOO_LONG else Rules.name(value)

	private fun find(id: UUID): Printer = printers.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun view(printer: Printer, codes: Map<UUID, String>) =
		PrinterView(printer.id!!, printer.name, printer.warehouseId, codes[printer.warehouseId], printer.ipAddress, PORT, printer.active, printer.updatedAt)

	private fun snapshot(printer: Printer) = mapOf("name" to printer.name, "warehouseId" to printer.warehouseId.toString(), "ipAddress" to printer.ipAddress, "active" to printer.active)

	companion object {
		const val VIEW = "printers.view"
		const val MANAGE = "printers.manage"
		// The raw printing port of Zebra printers (labels.md section 5).
		const val PORT = 9100
	}
}
