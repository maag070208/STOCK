package com.axzydev.stock.api.core.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import java.time.Instant
import java.util.UUID

// F1-09, F1-10 and F1-11 · unit: how a scope is resolved from roles, exceptions and warehouses.
class AccessSnapshotTest {

	private val now = Instant.parse("2026-10-13T12:00:00Z")

	private val snapshot = AccessSnapshot(
		activePermissions = setOf("orders.view", "orders.fulfill", "catalogs.view"),
		activeRoles = setOf("OFFICE", "WAREHOUSE_OPERATOR"),
		matrix = mapOf(
			"OFFICE" to mapOf("orders.view" to Scope.ALL, "catalogs.view" to Scope.ALL),
			"WAREHOUSE_OPERATOR" to mapOf("orders.view" to Scope.AREA, "orders.fulfill" to Scope.AREA),
			"RETIRED" to mapOf("orders.fulfill" to Scope.ALL),
		),
	)

	@Test
	fun `with two roles the effective scope is the highest one`() {
		assertEquals(Scope.ALL, snapshot.scopeOf("orders.view", setOf("OFFICE", "WAREHOUSE_OPERATOR"), emptyList(), now))
	}

	@Test
	fun `roles add permissions and never subtract them`() {
		val scopes = snapshot.scopesOf(setOf("OFFICE", "WAREHOUSE_OPERATOR"), emptyList(), now)
		assertEquals(mapOf("catalogs.view" to Scope.ALL, "orders.fulfill" to Scope.AREA, "orders.view" to Scope.ALL), scopes)
	}

	@Test
	fun `a permission no role grants is NONE, and so is one outside the catalog`() {
		assertEquals(Scope.NONE, snapshot.scopeOf("orders.fulfill", setOf("OFFICE"), emptyList(), now))
		assertEquals(Scope.NONE, snapshot.scopeOf("unknown.permission", setOf("OFFICE"), emptyList(), now))
	}

	@Test
	fun `an inactive role gives nothing`() {
		assertEquals(Scope.NONE, snapshot.scopeOf("orders.fulfill", setOf("RETIRED"), emptyList(), now))
	}

	@Test
	fun `a valid exception overrides the role scope, up or down`() {
		val raise = listOf(PermissionException("orders.fulfill", Scope.ALL, now.plusSeconds(60)))
		assertEquals(Scope.ALL, snapshot.scopeOf("orders.fulfill", setOf("WAREHOUSE_OPERATOR"), raise, now))
		val lower = listOf(PermissionException("orders.view", Scope.NONE, null))
		assertEquals(Scope.NONE, snapshot.scopeOf("orders.view", setOf("OFFICE"), lower, now))
	}

	@Test
	fun `an expired exception is ignored`() {
		val expired = listOf(PermissionException("orders.fulfill", Scope.ALL, now.minusSeconds(1)))
		assertEquals(Scope.AREA, snapshot.scopeOf("orders.fulfill", setOf("WAREHOUSE_OPERATOR"), expired, now))
		val atThisInstant = listOf(PermissionException("orders.fulfill", Scope.ALL, now))
		assertEquals(Scope.AREA, snapshot.scopeOf("orders.fulfill", setOf("WAREHOUSE_OPERATOR"), atThisInstant, now))
	}

	@Test
	fun `when the access data cannot be loaded every scope is NONE`() {
		assertEquals(Scope.NONE, AccessSnapshot.EMPTY.scopeOf("orders.view", setOf("OFFICE"), emptyList(), now))
	}

	private val central = UUID.randomUUID()
	private val north = UUID.randomUUID()
	private val me = UUID.randomUUID()

	private fun user(scope: Scope) = CurrentUser(
		id = me, username = "ana", roles = setOf("WAREHOUSE_OPERATOR"), warehouseIds = setOf(central),
		permissions = mapOf("orders.view" to scope), mustChangePassword = false,
	)

	@Test
	fun `a user with AREA reaches a record only when its warehouse is one of theirs`() {
		assertTrue(user(Scope.AREA).reaches("orders.view", warehouseId = central))
		assertFalse(user(Scope.AREA).reaches("orders.view", warehouseId = north))
		assertFalse(user(Scope.AREA).reaches("orders.view", warehouseId = null))
	}

	@Test
	fun `OWN reaches what the user created or is assigned to, ALL reaches everything, NONE nothing`() {
		assertTrue(user(Scope.OWN).reaches("orders.view", warehouseId = north, ownerIds = setOf(me)))
		assertFalse(user(Scope.OWN).reaches("orders.view", warehouseId = central, ownerIds = setOf(UUID.randomUUID())))
		assertTrue(user(Scope.ALL).reaches("orders.view", warehouseId = north))
		assertFalse(user(Scope.NONE).reaches("orders.view", warehouseId = central, ownerIds = setOf(me)))
	}

	@Test
	fun `outside the scope a read answers NOT_FOUND and a write INSUFFICIENT_PERMISSIONS`() {
		val read = assertThrows(ApiException::class.java) { user(Scope.AREA).requireRead("orders.view", warehouseId = north) }
		assertEquals(ErrorCode.NOT_FOUND, read.code)
		val write = assertThrows(ApiException::class.java) { user(Scope.AREA).requireWrite("orders.view", warehouseId = north) }
		assertEquals(ErrorCode.INSUFFICIENT_PERMISSIONS, write.code)
		user(Scope.AREA).requireRead("orders.view", warehouseId = central)
	}
}
