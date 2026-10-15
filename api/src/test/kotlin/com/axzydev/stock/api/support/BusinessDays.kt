package com.axzydev.stock.api.support

import com.axzydev.stock.api.core.time.BusinessTime
import java.time.LocalDate
import java.time.ZoneId

// The day the API works in (America/Mexico_City), for the fixtures that write a date the API validates. The machine's
// day and the business day differ for part of every evening, and an invoice dated tomorrow is refused with DATE_IN_FUTURE.
object BusinessDays {

	fun today(): LocalDate = LocalDate.now(ZoneId.of(BusinessTime.ZONE))

	fun today(plusDays: Long): LocalDate = today().plusDays(plusDays)
}
