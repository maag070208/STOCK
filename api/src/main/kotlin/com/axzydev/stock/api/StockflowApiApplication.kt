package com.axzydev.stock.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Scheduling runs the daily jobs the modules own, such as the order due date alerts (order-fulfillment.md section 7a).
@SpringBootApplication
@EnableScheduling
class StockflowApiApplication

fun main(args: Array<String>) {
	runApplication<StockflowApiApplication>(*args)
}
