package com.axzydev.stock

import android.content.Context

// Set once by the Android entry point, for platform settings that need a Context.
object AndroidContext {
	var app: Context? = null
}
