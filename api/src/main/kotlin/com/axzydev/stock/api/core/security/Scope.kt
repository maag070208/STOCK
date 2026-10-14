package com.axzydev.stock.api.core.security

// How far a permission reaches (access-control.md section 2). The order matters: a higher scope includes the lower ones.
enum class Scope {
	NONE,
	OWN,
	AREA,
	ALL;

	companion object {
		fun highest(scopes: Iterable<Scope>): Scope = scopes.maxOrNull() ?: NONE
	}
}
