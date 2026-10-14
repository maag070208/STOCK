package com.axzydev.stock

import io.ktor.client.engine.HttpClientEngine

// Where the API is reached. The value is written by the build (`API_BASE_URL`), so a phone can be pointed at the
// machine that runs the API without an address landing in the source. See app/README.md.
val apiBaseUrl: String = API_BASE_URL

expect fun platformEngine(): HttpClientEngine
