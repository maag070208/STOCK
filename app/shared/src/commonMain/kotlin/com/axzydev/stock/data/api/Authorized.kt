package com.axzydev.stock.data.api

// Runs a call of the API with the session's access token. The session refreshes an expired token once and repeats the
// call; a session the API has ended signs the person out. Every feature that calls the API goes through it.
interface Authorized {
	suspend fun <T> withToken(call: suspend (token: String) -> T): T
}
