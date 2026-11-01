import type { PermissionMap } from "@entities/user";

// The sections of the menu, in its order. A screen without a section is a link of its own.
export type NavGroupId = "operation" | "inventory" | "partners" | "printing" | "admin";

export interface NavGroup {
	readonly id: NavGroupId;
	readonly labelKey: "nav.operation" | "nav.inventory" | "nav.partners" | "nav.printing" | "nav.admin";
}

export const NAV_GROUPS: readonly NavGroup[] = [
	{ id: "operation", labelKey: "nav.operation" },
	{ id: "inventory", labelKey: "nav.inventory" },
	{ id: "partners", labelKey: "nav.partners" },
	{ id: "printing", labelKey: "nav.printing" },
	{ id: "admin", labelKey: "nav.admin" },
];

// The screens of the web, in the order of the menu. The single source for the menu, the route guards
// and the access console's "what each role sees" (web.md section 4). A new screen is added here.
export interface AppScreen {
	readonly id: string;
	readonly labelKey: "nav.home" | "nav.warehouses" | "nav.products" | "nav.suppliers" | "nav.customers" | "nav.stock" | "nav.invoices" | "nav.orders" | "nav.discrepancies" | "nav.labels" | "nav.printers" | "nav.users" | "nav.roles" | "nav.catalogs" | "nav.audit" | "nav.reports";
	readonly path: string;
	readonly match?: "exact" | "prefix";
	readonly group?: NavGroupId;
	// The screen shows when the person holds any of these. Absent: any signed-in person.
	readonly anyOf?: readonly string[];
}

export const APP_SCREENS: readonly AppScreen[] = [
	{ id: "home", labelKey: "nav.home", path: "/", match: "exact" },
	{ id: "invoices", labelKey: "nav.invoices", path: "/invoices", group: "operation", anyOf: ["invoices.view"] },
	{ id: "orders", labelKey: "nav.orders", path: "/orders", group: "operation", anyOf: ["orders.view"] },
	{ id: "discrepancies", labelKey: "nav.discrepancies", path: "/discrepancies", group: "operation", anyOf: ["discrepancies.resolve"] },
	{ id: "stock", labelKey: "nav.stock", path: "/stock", group: "inventory", anyOf: ["stock.view"] },
	{ id: "products", labelKey: "nav.products", path: "/products", group: "inventory", anyOf: ["products.view"] },
	{ id: "warehouses", labelKey: "nav.warehouses", path: "/warehouses", group: "inventory", anyOf: ["locations.view"] },
	{ id: "suppliers", labelKey: "nav.suppliers", path: "/suppliers", group: "partners", anyOf: ["suppliers.view"] },
	{ id: "customers", labelKey: "nav.customers", path: "/customers", group: "partners", anyOf: ["customers.view"] },
	{ id: "labels", labelKey: "nav.labels", path: "/labels", group: "printing", anyOf: ["labels.view"] },
	{ id: "printers", labelKey: "nav.printers", path: "/printers", group: "printing", anyOf: ["printers.view"] },
	{ id: "reports", labelKey: "nav.reports", path: "/reports", match: "prefix", anyOf: ["reports.view"] },
	{ id: "users", labelKey: "nav.users", path: "/users", group: "admin", anyOf: ["users.view"] },
	{ id: "roles", labelKey: "nav.roles", path: "/roles", group: "admin", anyOf: ["roles.manage"] },
	{ id: "catalogs", labelKey: "nav.catalogs", path: "/catalogs", group: "admin", anyOf: ["catalogs.view"] },
	{ id: "audit", labelKey: "nav.audit", path: "/audit", group: "admin", anyOf: ["audit.view"] },
];

export const isScreenVisible = (permissions: PermissionMap, screen: AppScreen): boolean =>
	!screen.anyOf || screen.anyOf.some((key) => (permissions[key] ?? "NONE") !== "NONE");

export const screenById = (id: string): AppScreen => APP_SCREENS.find((screen) => screen.id === id)!;
