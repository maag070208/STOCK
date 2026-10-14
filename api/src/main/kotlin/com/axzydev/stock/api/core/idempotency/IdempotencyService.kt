package com.axzydev.stock.api.core.idempotency

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IdempotencyService(private val store: IdempotencyStore) {

	// The action and the stored response share one transaction: a retry never sees a half-applied write.
	@Transactional
	fun execute(key: String, requestHash: String, action: () -> StoredResponse): StoredResponse {
		if (!IdempotencyKey.isValid(key)) throw ApiException(ErrorCode.FORMAT_INVALID)
		val previous = store.find(key)
		if (previous != null) {
			if (previous.requestHash == requestHash) return previous.response
			throw ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
		}
		val response = action()
		store.save(key, requestHash, response)
		return response
	}
}
