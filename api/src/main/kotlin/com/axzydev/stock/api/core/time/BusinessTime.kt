package com.axzydev.stock.api.core.time

// The zone the business runs in: the day a due date, an alert or a report belongs to is this one, not the server's
// (api.md section 2, order-fulfillment.md section 7a).
object BusinessTime {
	const val ZONE = "America/Mexico_City"
}
