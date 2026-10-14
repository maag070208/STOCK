package com.axzydev.stock.api.core.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Counts events per key in a fixed window, in memory. With more than one API instance it needs a shared store.
class RateLimiter(private val max: Int, private val window: Duration, private val clock: Clock) {

	private class Count(val start: Instant, val hits: Int)

	private val counts = ConcurrentHashMap<String, Count>()

	fun blocked(key: String): Boolean = (current(key)?.hits ?: 0) >= max

	fun hit(key: String) {
		val now = clock.instant()
		counts.compute(key) { _, count ->
			if (count == null || expired(count, now)) Count(now, 1) else Count(count.start, count.hits + 1)
		}
		if (counts.size > SWEEP_ABOVE) counts.values.removeIf { expired(it, now) }
	}

	private fun current(key: String): Count? = counts[key]?.takeUnless { expired(it, clock.instant()) }

	private fun expired(count: Count, now: Instant): Boolean = !now.isBefore(count.start.plus(window))

	private companion object {
		const val SWEEP_ABOVE = 10_000
	}
}
