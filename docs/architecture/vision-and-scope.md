# Vision and scope

**Status:** Draft. Items marked *proposed* are confirmed before the phases start.

## 1. What it is

**Stock** is an inventory management system for warehouses. It answers what is in stock, where it is, how much there is, who moved it, and when, and it flags shortages before they become problems.

It has three parts:

- **API**: a REST backend that holds all business logic. It is the single source of truth.
- **Mobile app**: for operating the warehouse on the floor (receive, move, count).
- **Web app**: for administration (catalogs, users, roles, policies) and reports.

## 2. Principles

1. **Everything configurable is data.** Catalogs, roles, permission grants and policies live in the database and are administered through the API and the access console. See [catalogs.md](../rules/catalogs.md) and [access-control.md](../rules/access-control.md).
2. **Business rules live in the API.** The apps validate for user experience, but the API is the last barrier.
3. **Access is RBAC plus ABAC, default deny.** See [access-control.md](../rules/access-control.md).
4. **Repository in English; display in Spanish by default, with English too.** See [language-and-naming.md](../rules/language-and-naming.md).
5. **House standard first.** Access control and the web follow the CYC standard ([DECISIONES.md](../../../CYC/DECISIONES.md)) unless this project overrides it.

## 3. Users

| Role (key) | What they do | Where |
|---|---|---|
| `ADMIN` | Everything: configures catalogs, roles, policies and users | Web and app |
| `OFFICE` | Creates invoices and assigns material entries | Web |
| `WAREHOUSE_MANAGER` | Assigns material entries and manages staff templates | Web |
| `WAREHOUSE_OPERATOR` | Counts and places the materials assigned to them, fulfills orders | App |
| `APP_USER` | Lowest role: signs in to the app only | App |

Full matrix in [roles.md](../rules/roles.md).

## 4. Stack

| Layer | Technology | Status |
|---|---|---|
| Backend | Kotlin, Spring Boot, REST, OpenAPI | Confirmed |
| Database | PostgreSQL, Flyway migrations | *Proposed* |
| Authentication | JWT. Roles and permissions resolved on the server | *Proposed* |
| Mobile app | Kotlin Multiplatform (Android and iOS) | Confirmed |
| Mobile UI | Compose Multiplatform | *Proposed* |
| Web | React 19, Vite, TypeScript, FSD, Axzy UI System | Confirmed, per [web.md](web.md) |
| Local environment | Docker Compose | *Proposed* |

## 5. Scope v1

1. **Products**: SKU, name, unit of measure, category (catalog), barcode (the product code by default), active flag.
2. **Locations**: warehouse → aisle → rack → level. See [locations.md](../modules/locations.md).
3. **Stock**: quantity per product and location, derived from movements.
4. **Movements**: receipt, issue, adjustment, transfer. Each movement is immutable.
5. **Material entries**: invoices from suppliers, assigned to warehouse operators, who count the goods and place them into locations. See [material-entry.md](../modules/material-entry.md) and [material-receiving.md](../modules/material-receiving.md).
6. **Customers and contacts**: the buyers orders are sent to. See [customers.md](../modules/customers.md).
7. **Order fulfillment**: orders created by admin and office, by warehouse, with a reservation of stock. Warehouse operators pick and fulfill them, and the order is validated and produces a PDF. See [order-fulfillment.md](../modules/order-fulfillment.md).
8. **Labels**: printable ZPL templates for products and locations, administered in the database. See [labels.md](../modules/labels.md).
9. **Counts**: physical count per location, with differences and controlled adjustment.
10. **Invoices**: operational documents with a status (`INVOICE_STATUS`) that trigger stock movements. *Proposed:* operational only, not fiscal or accounting invoicing.
11. **Staff templates**: who works in each warehouse, used to assign entries.
12. **Users, roles, policies**: per [roles.md](../rules/roles.md) and [access-control.md](../rules/access-control.md).
13. **Audit log**: who did what, when, and from where, for every write.
14. **Reports**: current stock, product kardex, products below minimum stock.
15. **Language**: Spanish (default) and English for display, from v1. See [i18n/README.md](../i18n/README.md).

The exit flow is documented in [order-fulfillment.md](../modules/order-fulfillment.md).

## 6. Out of scope v1

- Fiscal invoicing, accounting, payables and receivables.
- Full purchasing and supplier management.
- Point of sale.
- Integration with external ERPs. The CONTPAQ connection is planned for later, not built in v1 (see [products.md](../modules/products.md)).
- Lot or serial traceability (evaluated for v2).

## 7. Core business rules

Detailed in [rules/](../rules/). Principles here:

- **Stock is never negative.** No operation leaves a location below zero.
- **Movements are immutable.** Corrections are new movements that offset the original.
- **Every stock change is traceable:** user, date, quantity, location, reason.
- **Operations are atomic.** A movement is applied completely or not at all.
- **Concurrency is protected.** Two operators cannot take the same stock at the same time.

## 8. Non-functional requirements

- **Security:** HTTPS outside local development; permissions checked on the server; modern password hashing; secrets kept out of the code.
- **Audit:** append-only, not editable from the applications.
- **Accessibility:** sufficient contrast, and the main screens work with keyboard and screen readers.
- **Backups:** daily database backup, with a restore test before production.
- **Idempotency:** movements accept an idempotency key, so a network retry cannot duplicate stock.

## 9. Phases

Details in [phases/](../phases/README.md). Summary:

| Phase | Deliverable |
|---|---|
| F0 | Foundations: repositories, local environment, API, web and app shells |
| F1 | Access and catalogs: users, roles, policies, catalogs, audit log |
| F2 | Master data: warehouses, locations, products with legacy import, suppliers, customers |
| F3 | Labels, printers and barcode lookup |
| F4 | Stock and material entry: movements, opening inventory, invoices, counts, placement |
| F5 | Orders: reservations, picks, validation, cancellation, reassignment, PDF |
| F6 | Reports and kardex |
| F7 | Hardening, backups, and deployment |

## 10. Open questions

1. **System name.** StockFlow, confirmed 2026-10-14, replaces "Stock". See [brand.md](brand.md).
2. **Multi-company.** *Proposed:* no in v1, one customer per installation. If several companies share an installation, the data model must support it from the start.
3. **Database.** *Proposed:* PostgreSQL.
4. **Barcode scanning.** Confirmed for the operator app in v1. The camera or a reader is still to decide. See [barcode-scanning.md](../modules/barcode-scanning.md).
5. **Mobile UI.** *Proposed:* Compose Multiplatform, shared between Android and iOS.
6. **Invoices.** Are they operational documents only, as proposed, or do they need fiscal fields?
7. **Hosting.** Not defined. Affects F6 only.
8. **Exit flow.** Documented in [order-fulfillment.md](../modules/order-fulfillment.md). Open questions remain in its section 9.
