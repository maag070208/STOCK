package com.axzydev.stock.api.support

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// The clock of the test suite. A test moves it forward to check what depends on time, and puts it back when it ends.
@Component
@Primary
class TestClock : Clock() {

	@Volatile
	var ahead: Duration = Duration.ZERO

	override fun instant(): Instant = Instant.now().plus(ahead)

	override fun getZone(): ZoneId = ZoneOffset.UTC

	override fun withZone(zone: ZoneId): Clock = this

	// Runs the block as if this much time had passed.
	fun <T> after(time: Duration, block: () -> T): T {
		ahead = time
		try {
			return block()
		} finally {
			ahead = Duration.ZERO
		}
	}
}
