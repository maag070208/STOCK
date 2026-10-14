import esCommon from "./locales/es/common.json" with { type: "json" };
import esAuth from "./locales/es/auth.json" with { type: "json" };
import esUsers from "./locales/es/users.json" with { type: "json" };
import esAccess from "./locales/es/access.json" with { type: "json" };
import esCatalogs from "./locales/es/catalogs.json" with { type: "json" };
import esAudit from "./locales/es/audit.json" with { type: "json" };
import esWarehouses from "./locales/es/warehouses.json" with { type: "json" };
import esProducts from "./locales/es/products.json" with { type: "json" };
import esPartners from "./locales/es/partners.json" with { type: "json" };
import esStock from "./locales/es/stock.json" with { type: "json" };
import esInvoices from "./locales/es/invoices.json" with { type: "json" };
import esLabels from "./locales/es/labels.json" with { type: "json" };
import enCommon from "./locales/en/common.json" with { type: "json" };
import enAuth from "./locales/en/auth.json" with { type: "json" };
import enUsers from "./locales/en/users.json" with { type: "json" };
import enAccess from "./locales/en/access.json" with { type: "json" };
import enCatalogs from "./locales/en/catalogs.json" with { type: "json" };
import enAudit from "./locales/en/audit.json" with { type: "json" };
import enWarehouses from "./locales/en/warehouses.json" with { type: "json" };
import enProducts from "./locales/en/products.json" with { type: "json" };
import enPartners from "./locales/en/partners.json" with { type: "json" };
import enStock from "./locales/en/stock.json" with { type: "json" };
import enInvoices from "./locales/en/invoices.json" with { type: "json" };
import enLabels from "./locales/en/labels.json" with { type: "json" };

export const defaultLocale = "es" as const;
export const supportedLocales = ["es", "en"] as const;
export type Locale = (typeof supportedLocales)[number];

// One namespace per domain (web.md section 6). Both locales have every key: a unit test fails otherwise.
export const namespaces = ["common", "auth", "users", "access", "catalogs", "audit", "warehouses", "products", "partners", "stock", "invoices", "labels"] as const;

export const resources = {
	es: { common: esCommon, auth: esAuth, users: esUsers, access: esAccess, catalogs: esCatalogs, audit: esAudit, warehouses: esWarehouses, products: esProducts, partners: esPartners, stock: esStock, invoices: esInvoices, labels: esLabels },
	en: { common: enCommon, auth: enAuth, users: enUsers, access: enAccess, catalogs: enCatalogs, audit: enAudit, warehouses: enWarehouses, products: enProducts, partners: enPartners, stock: enStock, invoices: enInvoices, labels: enLabels },
} as const;
