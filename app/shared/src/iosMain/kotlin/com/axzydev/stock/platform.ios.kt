package com.axzydev.stock

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual val apiBaseUrl: String = "http://localhost:8080/api/v1"

actual fun platformEngine(): HttpClientEngine = Darwin.create()
