package com.axzydev.stock.api.modules.catalogs

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

// One label in one language, as the catalog item carries it.
data class CachedLabel(val label: String, val description: String?)

// A catalog item as the readers need it: its attributes parsed once, its labels by locale. Immutable, so the snapshot
// can be shared without a lock.
data class CachedItem(
	val id: UUID,
	val catalogCode: String,
	val code: String,
	val sortOrder: Int,
	val active: Boolean,
	val attributes: Map<String, Any?>,
	val labels: Map<String, CachedLabel>,
)

data class CachedCatalog(val id: UUID, val code: String, val isSystem: Boolean, val active: Boolean)

class CatalogSnapshot(
	val catalogs: Map<String, CachedCatalog>,
	private val byCode: Map<Pair<String, String>, CachedItem>,
	private val byId: Map<UUID, CachedItem>,
) {
	fun item(catalogCode: String, itemCode: String): CachedItem? = byCode[catalogCode to itemCode]

	fun items(catalogCode: String): List<CachedItem> = byId.values.filter { it.catalogCode == catalogCode }

	fun item(id: UUID): CachedItem? = byId[id]

	companion object {
		val EMPTY = CatalogSnapshot(emptyMap(), emptyMap(), emptyMap())
	}
}

// The catalogs and their items, kept in memory and loaded again after every write to them. They are configuration:
// read on almost every request (a status label, a unit's decimals), written almost never. Like AccessCache: a volatile
// immutable snapshot, fail-closed when it cannot load.
@Component
class CatalogCache(
	private val catalogs: CatalogRepository,
	private val items: CatalogItemRepository,
	private val json: JsonMapper,
	private val transactions: TransactionTemplate,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Volatile
	private var current: CatalogSnapshot = CatalogSnapshot.EMPTY

	val snapshot: CatalogSnapshot get() = current

	@EventListener(ApplicationReadyEvent::class)
	fun reload() {
		current = try {
			transactions.execute { load() } ?: CatalogSnapshot.EMPTY
		} catch (e: Exception) {
			// Fail-closed: without the catalogs every catalog value is refused.
			log.error("the catalogs could not be loaded; every catalog value is refused until they load", e)
			CatalogSnapshot.EMPTY
		}
	}

	// For the writes of CatalogService: the new data is read once their transaction has committed.
	fun reloadAfterCommit() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return reload()
		TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
			override fun afterCommit() = reload()
		})
	}

	private fun load(): CatalogSnapshot {
		val allItems = items.findAllWithLabels()
		val catalogById = catalogs.findAll().associateBy { it.id!! }
		val cachedItems = allItems.map { item ->
			@Suppress("UNCHECKED_CAST")
			CachedItem(
				id = item.id!!,
				catalogCode = catalogById.getValue(item.catalogId).code,
				code = item.code,
				sortOrder = item.sortOrder,
				active = item.active,
				attributes = json.readValue(item.attributes, Map::class.java) as Map<String, Any?>,
				labels = item.labels.mapValues { (_, label) -> CachedLabel(label.label, label.description) },
			)
		}
		return CatalogSnapshot(
			catalogs = catalogById.values.associate { it.code to CachedCatalog(it.id!!, it.code, it.isSystem, it.active) },
			byCode = cachedItems.associateBy { it.catalogCode to it.code },
			byId = cachedItems.associateBy { it.id },
		)
	}
}
