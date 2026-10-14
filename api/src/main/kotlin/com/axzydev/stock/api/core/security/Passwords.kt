package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom

// Passwords are stored as BCrypt hashes. A password is never returned and never logged.
@Component
class Passwords {
	private val encoder = BCryptPasswordEncoder()
	private val random = SecureRandom()

	fun hash(password: String): String = encoder.encode(password)!!

	fun matches(password: String, hash: String): Boolean = encoder.matches(password, hash)

	// A temporary password for a new account or a reset. It is shown once to the administrator.
	fun temporary(): String = (1..TEMPORARY_LENGTH).map { TEMPORARY_ALPHABET[random.nextInt(TEMPORARY_ALPHABET.length)] }.joinToString("")

	companion object {
		const val MIN_LENGTH = 10
		// BCrypt reads 72 bytes at most.
		const val MAX_LENGTH = 72
		const val TEMPORARY_LENGTH = 14
		// No 0, O, 1, l or I: a person reads this password and types it.
		const val TEMPORARY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

		fun check(password: String?) {
			if (password == null || password.length < MIN_LENGTH || password.toByteArray().size > MAX_LENGTH) {
				throw ApiException(ErrorCode.PASSWORD_INVALID)
			}
		}
	}
}
