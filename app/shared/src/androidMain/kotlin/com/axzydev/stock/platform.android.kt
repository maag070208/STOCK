package com.axzydev.stock

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual val apiBaseUrl: String = "http://localhost:8080/api/v1"

actual fun platformEngine(): HttpClientEngine = OkHttp.create()
