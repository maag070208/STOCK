package com.axzydev.stock.data.api

import kotlinx.serialization.Serializable

// The table contract of the API (docs/architecture/api.md section 2): the lists are read with
// POST /{resource}/query and a body of { page, limit, filters, sort }. A missing sort is the resource's own order.
@Serializable
data class TableQuery(
	val page: Int? = null,
	val limit: Int? = null,
	val filters: Map<String, String>? = null,
	val sort: TableSort? = null,
)

@Serializable
data class TableSort(val key: String? = null, val direction: String? = null)

// What a table query answers. The app reads the rows; the rest of the envelope is not kept.
@Serializable
data class Page<T>(
	val data: List<T> = emptyList(),
	val total: Long = 0,
)

// The page size the app asks for: the API's own maximum, so a floor list arrives in one call.
const val PAGE_LIMIT = 200
