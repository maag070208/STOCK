package com.axzydev.stock.api.core.errors

// Flat envelope, as in api.md section 2: { error, code, message, details }.
// `details` names each field that failed, with its own code and message.
data class ErrorResponse(
	val error: String,
	val code: String,
	val message: String,
	val details: List<ErrorDetail> = emptyList(),
)

data class ErrorDetail(val field: String, val code: String, val message: String)
