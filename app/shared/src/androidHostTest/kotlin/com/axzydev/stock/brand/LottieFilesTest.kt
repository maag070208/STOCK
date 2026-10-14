package com.axzydev.stock.brand

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

// F0-16 · unit: each Lottie file parses and has the frame count of brand.md section 4, at 30 fps.
class LottieFilesTest {

	private val folder = File("src/commonMain/composeResources/files/animations")

	private fun frames(name: String): Pair<Int, Int> {
		val json = Json.parseToJsonElement(File(folder, name).readText()).jsonObject
		return json["op"]!!.jsonPrimitive.content.toInt() to json["fr"]!!.jsonPrimitive.content.toInt()
	}

	@Test
	fun `intro files have 130 frames and loader files have 120, all at 30 fps`() {
		for (theme in listOf("light", "dark")) {
			assertEquals(130 to 30, frames("stockflow-intro-$theme.json"))
			assertEquals(120 to 30, frames("stockflow-loader-$theme.json"))
		}
	}
}
