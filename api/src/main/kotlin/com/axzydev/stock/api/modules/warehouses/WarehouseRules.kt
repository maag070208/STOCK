package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.Rules

// The field rules of warehouses and locations (validation.md section 3, locations.md section 4).
object WarehouseRules {

	// The system's location in every warehouse, the only code outside the pattern.
	const val TEMPORARY = "TEMPORARY"

	val LOCATION_CODE = Regex("^[A-Z][0-9]{2}[A-Z]$")

	private val AISLE = Regex("^[A-Z]$")
	private val RACK = Regex("^[0-9]{2}$")
	const val MAX_LEVELS = 26

	// A warehouse's code follows the code rule every module shares.
	fun code(value: String): ErrorCode? = Rules.code(value)

	fun aisle(value: String): ErrorCode? = if (AISLE.matches(value)) null else ErrorCode.FORMAT_INVALID

	fun rack(value: String): ErrorCode? = if (RACK.matches(value)) null else ErrorCode.FORMAT_INVALID

	fun levels(value: Int): ErrorCode? = if (value in 1..MAX_LEVELS) null else ErrorCode.FORMAT_INVALID

	// One location per level, lettered from A: aisle K, rack 05 and 3 levels give K05A, K05B and K05C.
	fun rackCodes(aisle: String, rack: String, levels: Int): List<String> = (0 until levels).map { "$aisle$rack${'A' + it}" }
}
