package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/warehouses")
class WarehouseController(private val warehouses: WarehouseService, private val locations: LocationService) {

	@PostMapping("/query")
	@RequiresPermission(WarehouseService.VIEW)
	fun query(@RequestBody request: TableRequest, user: CurrentUser): TablePage<WarehouseView> = warehouses.query(user, request)

	@GetMapping("/{id}")
	@RequiresPermission(WarehouseService.VIEW)
	fun get(@PathVariable id: UUID, user: CurrentUser): WarehouseView = warehouses.get(user, id)

	@GetMapping("/{id}/location-groups")
	@RequiresPermission(WarehouseService.VIEW)
	fun groups(@PathVariable id: UUID, user: CurrentUser): List<AisleGroupView> = warehouses.groups(user, id)

	@PostMapping
	@RequiresPermission(WarehouseService.MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreateWarehouseRequest, user: CurrentUser): WarehouseView = warehouses.create(user, request)

	@PatchMapping("/{id}")
	@RequiresPermission(WarehouseService.MANAGE)
	fun update(@PathVariable id: UUID, @RequestBody request: UpdateWarehouseRequest, user: CurrentUser): WarehouseView =
		warehouses.update(user, id, request)

	@DeleteMapping("/{id}")
	@RequiresPermission(WarehouseService.MANAGE)
	fun deactivate(@PathVariable id: UUID, user: CurrentUser): WarehouseView = warehouses.deactivate(user, id)

	@PostMapping("/{id}/reactivate")
	@RequiresPermission(WarehouseService.MANAGE)
	fun reactivate(@PathVariable id: UUID, user: CurrentUser): WarehouseView = warehouses.reactivate(user, id)

	// Deactivates every level of a rack, when all are empty.
	@DeleteMapping("/{id}/racks/{aisle}/{rack}")
	@RequiresPermission(WarehouseService.MANAGE)
	fun deactivateRack(@PathVariable id: UUID, @PathVariable aisle: String, @PathVariable rack: String, user: CurrentUser): RackView =
		locations.deactivateRack(user, id, aisle, rack)
}

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(private val locations: LocationService) {

	@PostMapping("/query")
	@RequiresPermission(WarehouseService.VIEW)
	fun query(@RequestBody request: TableRequest, user: CurrentUser): TablePage<LocationView> = locations.query(user, request)

	@GetMapping("/{id}")
	@RequiresPermission(WarehouseService.VIEW)
	fun get(@PathVariable id: UUID, user: CurrentUser): LocationView = locations.get(user, id)

	@PostMapping("/racks")
	@RequiresPermission(WarehouseService.MANAGE)
	@ResponseStatus(HttpStatus.CREATED)
	fun createRack(@RequestBody request: CreateRackRequest, user: CurrentUser): RackView = locations.createRack(user, request)

	@DeleteMapping("/{id}")
	@RequiresPermission(WarehouseService.MANAGE)
	fun deactivate(@PathVariable id: UUID, user: CurrentUser): LocationView = locations.deactivate(user, id)

	@PostMapping("/{id}/reactivate")
	@RequiresPermission(WarehouseService.MANAGE)
	fun reactivate(@PathVariable id: UUID, user: CurrentUser): LocationView = locations.reactivate(user, id)
}
