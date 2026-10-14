package com.axzydev.stock.api.core.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

// Everything that depends on the time takes this clock, so a test can set it (testing.md rule 3).
@Configuration
class ClockConfig {

	@Bean
	fun clock(): Clock = Clock.systemUTC()
}
