package com.axzydev.stock.api.db

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// F0-02 · unit: every migration file follows V<number>__<name>.sql and V1 exists.
class MigrationNamingTest {

	private val pattern = Regex("""V(\d+)__[a-z0-9_]+\.sql""")

	@Test
	fun `migration files follow the naming pattern and V1 exists`() {
		val dir = File("src/main/resources/db/migration")
		assertTrue(dir.isDirectory, "db/migration must exist")
		val names = dir.listFiles()!!.map { it.name }.sorted()
		assertTrue(names.isNotEmpty(), "at least one migration is required")
		names.forEach { name ->
			assertTrue(pattern.matches(name), "bad migration name: $name")
		}
		assertTrue(names.contains("V1__baseline.sql"), "V1__baseline.sql must exist")
	}
}
