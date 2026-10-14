package com.axzydev.stock.api.core.idempotency

data class StoredResponse(val status: Int, val body: String)

data class Stored(val requestHash: String, val response: StoredResponse)
