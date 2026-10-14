import type { PermissionMap } from "@entities/user";

// The screens of the web, in the order of the menu. The single source for the menu, the route guards
// and the access console's "what each role sees" (web.md section 4). A new screen is added here.
export interface AppScreen {
	readonly id: string;
	readonly labelKey: "nav.home" | "nav.warehouses" | "nav.products" | "nav.suppliers" | "nav.customers" | "nav.stock" | "nav.invoices" | "nav.discrepancies" | "nav.labels" | "nav.printers" | "nav.users" | "nav.roles" | "nav.catalogs" | "nav.audit";
	readonly path: string;
	readonly match?: "exact" | "prefix";
	// The screen shows when the person holds any of these. Absent: any signed-in person.
	readonly anyOf?: readonly string[];
}

export const APP_SCREENS: readonly AppScreen[] = [
	{ id: "home", labelKey: "nav.home", path: "/", match: "exact" },
	{ id: "warehouses", labelKey: "nav.warehouses", path: "/warehouses", anyOf: ["locations.view"] },
	{ id: "products", labelKey: "nav.products", path: "/products", anyOf: ["products.view"] },
	{ id: "invoices", labelKey: "nav.invoices", path: "/invoices", anyOf: ["invoices.view"] },
	{ id: "discrepancies", labelKey: "nav.discrepancies", path: "/discrepancies", anyOf: ["discrepancies.resolve"] },
	{ id: "stock", labelKey: "nav.stock", path: "/stock", anyOf: ["stock.view"] },
	{ id: "suppliers", labelKey: "nav.suppliers", path: "/suppliers", anyOf: ["suppliers.view"] },
	{ id: "customers", labelKey: "nav.customers", path: "/customers", anyOf: ["customers.view"] },
	{ id: "labels", labelKey: "nav.labels", path: "/labels", anyOf: ["labels.view"] },
	{ id: "printers", labelKey: "nav.printers", path: "/printers", anyOf: ["printers.view"] },
	{ id: "users", labelKey: "nav.users", path: "/users", anyOf: ["users.view"] },
	{ id: "roles", labelKey: "nav.roles", path: "/roles", anyOf: ["roles.manage"] },
	{ id: "catalogs", labelKey: "nav.catalogs", path: "/catalogs", anyOf: ["catalogs.view"] },
	{ id: "audit", labelKey: "nav.audit", path: "/audit", anyOf: ["audit.view"] },
];

export const isScreenVisible = (permissions: PermissionMap, screen: AppScreen): boolean =>
	!screen.anyOf || screen.anyOf.some((key) => (permissions[key] ?? "NONE") !== "NONE");

export const screenById = (id: string): AppScreen => APP_SCREENS.find((screen) => screen.id === id)!;
