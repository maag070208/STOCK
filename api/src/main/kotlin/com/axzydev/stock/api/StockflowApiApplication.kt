package com.axzydev.stock.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StockflowApiApplication

fun main(args: Array<String>) {
	runApplication<StockflowApiApplication>(*args)
}
