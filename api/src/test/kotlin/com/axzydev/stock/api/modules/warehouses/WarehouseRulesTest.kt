package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.core.errors.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// F2-01 to F2-03 · unit: the warehouse code, the parts of a location code, and the codes of a rack.
class WarehouseRulesTest {

	@Test
	fun `a warehouse code is uppercase letters, digits and hyphens, 2 to 20 characters`() {
		assertNull(WarehouseRules.code("CENTRAL"))
		assertNull(WarehouseRules.code("ALM-02"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.code("C"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.code("CEN TRAL"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.code("ALMACÉN"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, WarehouseRules.code("A".repeat(21)))
	}

	@Test
	fun `aisle, rack and level count follow the location rules`() {
		assertNull(WarehouseRules.aisle("K"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.aisle("KK"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.aisle("1"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.aisle("Ñ"))
		assertNull(WarehouseRules.rack("05"))
		assertNull(WarehouseRules.rack("00"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.rack("5"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.rack("105"))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.rack("A5"))
		assertNull(WarehouseRules.levels(1))
		assertNull(WarehouseRules.levels(26))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.levels(0))
		assertEquals(ErrorCode.FORMAT_INVALID, WarehouseRules.levels(27))
	}

	@Test
	fun `a rack's codes are aisle, rack and the level letters from A`() {
		assertEquals(listOf("K05A", "K05B", "K05C"), WarehouseRules.rackCodes("K", "05", 3))
		assertEquals(listOf("K05A"), WarehouseRules.rackCodes("K", "05", 1))
		assertEquals('Z', WarehouseRules.rackCodes("A", "00", 26).last().last())
	}

	@Test
	fun `the temporary location is the only code outside the pattern`() {
		assertEquals("TEMPORARY", WarehouseRules.TEMPORARY)
		assertEquals(false, WarehouseRules.LOCATION_CODE.matches(WarehouseRules.TEMPORARY))
		assertEquals(true, WarehouseRules.LOCATION_CODE.matches("K05A"))
	}
}
