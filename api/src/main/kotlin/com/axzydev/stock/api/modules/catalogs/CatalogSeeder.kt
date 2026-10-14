package com.axzydev.stock.api.modules.catalogs

import java.sql.Connection
import java.util.UUID

data class SeedItem(val code: String, val es: String, val en: String, val attributes: String = "{}")
data class SeedCatalog(val code: String, val items: List<SeedItem>)

// The catalogs a new database starts with: the values the owner confirmed (F1). The labels are in docs/i18n/glossary.md.
object SeedCatalogs {
	val all = listOf(
		SeedCatalog(
			"INVOICE_STATUS",
			listOf(
				SeedItem("OPEN", "Abierta", "Open"),
				SeedItem("PENDING", "Pendiente", "Pending"),
				SeedItem("IN_ASSIGNMENT", "En asignación", "In assignment"),
				SeedItem("CLOSED", "Cerrada", "Closed", """{"terminal": true}"""),
			),
		),
		SeedCatalog(
			"ORDER_STATUS",
			listOf(
				SeedItem("CREATED", "Creada", "Created"),
				SeedItem("FULFILLING", "Surtiendo", "Fulfilling"),
				SeedItem("FULFILLED", "Surtida", "Fulfilled"),
				SeedItem("VALIDATED", "Validada", "Validated", """{"terminal": true}"""),
				SeedItem("CANCELLED", "Cancelada", "Cancelled", """{"terminal": true}"""),
			),
		),
		SeedCatalog("UNIT_OF_MEASURE", listOf(SeedItem("PIECE", "Pieza", "Piece", """{"decimal_places": 0}"""))),
		// F2: the address of suppliers and customers. Proposed in validation.md section 5: Mexico and its 32 states
		// (ISO 3166-1 and 3166-2). PAYMENT_TERMS starts empty: the owner has not given its values, and an administrator adds them.
		SeedCatalog("COUNTRY", listOf(SeedItem("MX", "México", "Mexico"))),
		SeedCatalog("STATE", MEXICAN_STATES.map { (code, es, en) -> SeedItem("MX_$code", es, en, """{"country": "MX"}""") }),
		SeedCatalog("PAYMENT_TERMS", emptyList()),
		// F4: the types of stock movement (docs/i18n/glossary.md). The code creates each one; its direction is its attribute.
		SeedCatalog(
			"MOVEMENT_TYPE",
			listOf(
				SeedItem("OPENING", "Inventario inicial", "Opening inventory", """{"direction": "in"}"""),
				SeedItem("RECEIPT", "Entrada", "Receipt", """{"direction": "in"}"""),
				SeedItem("OUT", "Salida", "Out", """{"direction": "out"}"""),
				SeedItem("IN_RETURN", "Devolución a stock", "Return to stock", """{"direction": "in"}"""),
			),
		),
	)
}

private val MEXICAN_STATES = listOf(
	Triple("AGU", "Aguascalientes", "Aguascalientes"),
	Triple("BCN", "Baja California", "Baja California"),
	Triple("BCS", "Baja California Sur", "Baja California Sur"),
	Triple("CAM", "Campeche", "Campeche"),
	Triple("CHP", "Chiapas", "Chiapas"),
	Triple("CHH", "Chihuahua", "Chihuahua"),
	Triple("CMX", "Ciudad de México", "Mexico City"),
	Triple("COA", "Coahuila", "Coahuila"),
	Triple("COL", "Colima", "Colima"),
	Triple("DUR", "Durango", "Durango"),
	Triple("GUA", "Guanajuato", "Guanajuato"),
	Triple("GRO", "Guerrero", "Guerrero"),
	Triple("HID", "Hidalgo", "Hidalgo"),
	Triple("JAL", "Jalisco", "Jalisco"),
	Triple("MEX", "Estado de México", "State of Mexico"),
	Triple("MIC", "Michoacán", "Michoacán"),
	Triple("MOR", "Morelos", "Morelos"),
	Triple("NAY", "Nayarit", "Nayarit"),
	Triple("NLE", "Nuevo León", "Nuevo León"),
	Triple("OAX", "Oaxaca", "Oaxaca"),
	Triple("PUE", "Puebla", "Puebla"),
	Triple("QUE", "Querétaro", "Querétaro"),
	Triple("ROO", "Quintana Roo", "Quintana Roo"),
	Triple("SLP", "San Luis Potosí", "San Luis Potosí"),
	Triple("SIN", "Sinaloa", "Sinaloa"),
	Triple("SON", "Sonora", "Sonora"),
	Triple("TAB", "Tabasco", "Tabasco"),
	Triple("TAM", "Tamaulipas", "Tamaulipas"),
	Triple("TLA", "Tlaxcala", "Tlaxcala"),
	Triple("VER", "Veracruz", "Veracruz"),
	Triple("YUC", "Yucatán", "Yucatán"),
	Triple("ZAC", "Zacatecas", "Zacatecas"),
)

// Adds what is missing and never changes what exists: after the first run the database is the source of truth,
// and a label or an order an administrator changed stays as they left it.
class CatalogSeeder(private val connection: Connection) {

	fun seed(catalogs: List<SeedCatalog>) {
		for (catalog in catalogs) {
			val catalogId = id(
				"insert into catalog (id, code, is_system, created_at, updated_at) values (?, ?, true, now(), now()) on conflict (code) do nothing",
				"select id from catalog where code = ?",
				catalog.code,
			)
			catalog.items.forEachIndexed { index, item -> seedItem(catalogId, item, sortOrder = index + 1) }
		}
	}

	private fun seedItem(catalogId: UUID, item: SeedItem, sortOrder: Int) {
		val inserted = connection.prepareStatement(
			"""
			insert into catalog_item (id, catalog_id, code, sort_order, attributes, created_at, updated_at)
			values (?, ?, ?, ?, ?::jsonb, now(), now()) on conflict (catalog_id, code) do nothing returning id
			""".trimIndent(),
		).use { statement ->
			statement.setObject(1, UUID.randomUUID())
			statement.setObject(2, catalogId)
			statement.setString(3, item.code)
			statement.setInt(4, sortOrder)
			statement.setString(5, item.attributes)
			statement.executeQuery().use { if (it.next()) it.getObject(1, UUID::class.java) else null }
		} ?: return
		connection.prepareStatement("insert into catalog_item_label (catalog_item_id, locale, label) values (?, ?, ?)").use { insert ->
			for ((locale, label) in listOf("es" to item.es, "en" to item.en)) {
				insert.setObject(1, inserted)
				insert.setString(2, locale)
				insert.setString(3, label)
				insert.executeUpdate()
			}
		}
	}

	private fun id(insert: String, select: String, code: String): UUID {
		connection.prepareStatement(insert).use {
			it.setObject(1, UUID.randomUUID())
			it.setString(2, code)
			it.executeUpdate()
		}
		return connection.prepareStatement(select).use { statement ->
			statement.setString(1, code)
			statement.executeQuery().use { it.next(); it.getObject(1, UUID::class.java) }
		}
	}
}
