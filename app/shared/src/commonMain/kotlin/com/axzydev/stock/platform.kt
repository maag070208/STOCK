package com.axzydev.stock

import io.ktor.client.engine.HttpClientEngine

// Where the API is reached from each platform. Android emulators reach the host at 10.0.2.2.
expect val apiBaseUrl: String

expect fun platformEngine(): HttpClientEngine
