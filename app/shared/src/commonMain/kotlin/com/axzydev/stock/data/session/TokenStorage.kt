package com.axzydev.stock.data.session

// Where the refresh token waits between launches: the platform's secure storage (Android Keystore, iOS Keychain).
// The access token is never stored: it lives in memory only.
interface TokenStorage {
	fun read(): String?

	fun write(token: String)

	fun clear()
}

expect fun secureTokenStorage(): TokenStorage
