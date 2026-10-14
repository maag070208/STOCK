package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.customers.CustomerService
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.UUID

// One line of the comprobante: the material as the customer sees it, and how much of it left the warehouse.
data class OrderDocumentLine(val code: String?, val name: String?, val unit: String?, val quantity: String)

// What the comprobante shows (order-fulfillment.md section 10): the warehouse, the order and its due date, the customer
// and its contact, each line, who created it, who fulfilled it, who validated it, and the signature lines.
data class OrderDocumentData(
	val labels: DocumentLabels,
	val warehouse: String,
	val number: String,
	val dueDate: String,
	val customer: String,
	val contact: String?,
	val lines: List<OrderDocumentLine>,
	val createdBy: String?,
	val operators: List<String>,
	val validatedBy: String?,
)

// The comprobante of an order (order-fulfillment.md section 10): built at validation from the stored order, written once,
// and answered again byte for byte. It lives in private storage — this table — and is never a public URL.
@Service
class OrderDocumentService(
	private val documents: OrderDocumentRepository,
	private val orders: OrderRepository,
	private val lines: OrderLineRepository,
	private val picks: OrderPickRepository,
	private val customers: CustomerService,
	private val warehouses: WarehouseService,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val people: UserService,
	private val renderer: OrderDocumentRenderer,
	private val clock: Clock,
) {
	// The document of a validated order. An order that already has one keeps it: it is never generated again.
	@Transactional
	fun store(order: Order, language: String): OrderDocument {
		documents.findById(order.id!!).orElse(null)?.let { return it }
		val built = OrderDocument(
			order.id!!,
			renderer.render(data(order, language), OrderDocumentText.labels(language)),
			CONTENT_TYPE,
			fileName(order),
			clock.instant(),
		)
		return documents.save(built)
	}

	// For the client: the document of an order the person may read.
	@Transactional(readOnly = true)
	fun read(user: CurrentUser, orderId: UUID): OrderDocument {
		val order = orders.findById(orderId).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		user.requireRead(OrderService.VIEW, order.warehouseId)
		return documents.findById(orderId).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
	}

	// The fields of section 10, looked up together from what the order stores.
	fun data(order: Order, language: String): OrderDocumentData {
		val list = lines.findByOrderId(order.id!!)
		val found = products.stockProducts(list.map { it.productId })
		val units = catalogs.itemViews(list.map { it.unitId }, language)
		val names = customers.names(listOf(order.customerId), listOfNotNull(order.contactId))
		val pickedBy = picks.findByLineIdIn(list.mapNotNull { it.id }).mapNotNull { it.pickedBy }.distinct()
		val peopleById = people.people((listOfNotNull(order.createdBy, order.operatorId, order.validatedBy) + pickedBy).distinct())
		val operators = (if (pickedBy.isEmpty()) listOfNotNull(order.operatorId) else pickedBy)
			.mapNotNull { peopleById[it]?.let { person -> person.name.ifBlank { person.username } } }
		return OrderDocumentData(
			labels = OrderDocumentText.labels(language),
			warehouse = warehouses.codes(listOf(order.warehouseId))[order.warehouseId] ?: "",
			number = order.number,
			dueDate = order.dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
			customer = names.first[order.customerId]?.second ?: "",
			contact = order.contactId?.let { names.second[it] },
			lines = list.sortedBy { found[it.productId]?.code }.map { line ->
				OrderDocumentLine(
					code = found[line.productId]?.code,
					name = found[line.productId]?.name,
					unit = units[line.unitId]?.label ?: units[line.unitId]?.code,
					quantity = quantity(line.quantity),
				)
			},
			createdBy = order.createdBy?.let { person -> peopleById[person]?.let { it.name.ifBlank { it.username } } },
			operators = operators,
			validatedBy = order.validatedBy?.let { person -> peopleById[person]?.let { it.name.ifBlank { it.username } } },
		)
	}

	// The quantity as the customer reads it: without the trailing zeros the stored decimal carries.
	private fun quantity(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

	// The file a download offers: the order number, as it is.
	private fun fileName(order: Order): String = "comprobante-${order.number}.pdf"

	companion object {
		const val CONTENT_TYPE = "application/pdf"

		// The signature lines of the receipt: the material is delivered and received by hand.
		fun signatureLabels(labels: DocumentLabels): List<String> =
			listOf(labels.signatureDelivers, labels.signatureReceives).map { "$it · ${labels.signatureLine}" }
	}
}

// Draws the comprobante. The standard font covers Latin-1, and anything else becomes "?", so no order fails to produce
// its receipt over a name the font cannot print.
@Component
class OrderDocumentRenderer {

	fun render(data: OrderDocumentData, labels: DocumentLabels): ByteArray {
		PDDocument().use { document ->
			val page = PDPage(PDRectangle.LETTER)
			document.addPage(page)
			val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
			val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
			PDPageContentStream(document, page).use { content ->
				var y = MARGIN_TOP
				fun line(text: String, font: PDFont = regular, size: Float = 10f, gap: Float = 16f) {
					content.beginText()
					content.setFont(font, size)
					content.newLineAtOffset(MARGIN, y)
					content.showText(printable(text))
					content.endText()
					y -= gap
				}
				line(labels.title, bold, 16f, 30f)
				line("${labels.warehouse}: ${data.warehouse}")
				line("${labels.number}: ${data.number}")
				line("${labels.dueDate}: ${data.dueDate}")
				line("${labels.customer}: ${data.customer}")
				data.contact?.let { line("${labels.contact}: $it") }

				// The materials: the code, the name, the unit and how much of it left.
				y -= 10f
				line(labels.lines, bold, 12f, 20f)
				line("${labels.lineCode}    ${labels.lineName}    ${labels.lineUnit}    ${labels.lineQuantity}", bold)
				data.lines.forEach { orderLine ->
					line("${orderLine.code.orEmpty()}    ${orderLine.name.orEmpty()}    ${orderLine.unit.orEmpty()}    ${orderLine.quantity}")
				}

				y -= 10f
				line("${labels.createdBy}: ${data.createdBy.orEmpty()}")
				line("${labels.operators}: ${data.operators.joinToString(", ")}")
				line("${labels.validatedBy}: ${data.validatedBy.orEmpty()}")

				// The signatures are physical; the receipt only leaves room for them.
				y -= 40f
				OrderDocumentService.signatureLabels(labels).forEach { label ->
					line("______________________________    $label", regular, 10f, 46f)
				}
			}
			val out = ByteArrayOutputStream()
			document.save(out)
			return out.toByteArray()
		}
	}

	// A character the standard font cannot print becomes "?".
	private fun printable(value: String): String =
		value.map { if (WinAnsiEncoding.INSTANCE.contains(it.code)) it else '?' }.joinToString("")

	private companion object {
		const val MARGIN = 56f
		const val MARGIN_TOP = 720f
	}
}
