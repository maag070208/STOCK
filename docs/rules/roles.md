# Roles

**Status:** Accepted, 2026-10-11, as specified by the business owner. Anything a role description does not mention is granted to no role except `ADMIN`. See section 6 for open questions.

## 1. Rule

Roles are dynamic data. An administrator can create more roles from the access console. Each user can hold several roles, and their permissions are the highest scope across them. See [access-control.md](access-control.md).

- **Keys** are English, UPPER_SNAKE_CASE (`WAREHOUSE_OPERATOR`).
- **Display names** of seed roles are translated through i18n (`roles.WAREHOUSE_OPERATOR`), Spanish by default. Custom roles store their `name` as typed.
- **System roles** cannot be deleted or renamed.

## 2. Seed roles

| Key | Display name (es) | Responsibility | Channels |
|---|---|---|---|
| `ADMIN` | Administrador | Everything | Web and app |
| `OFFICE` | Oficinas | Creates invoices. Assigns material entries. Resolves discrepancies | Web |
| `WAREHOUSE_MANAGER` | Gerente de almacén | Assigns material entries. Manages staff templates. Resolves discrepancies in its warehouses | Web |
| `WAREHOUSE_OPERATOR` | Almacén | Counts and places the materials assigned to them. Fulfills orders | App |
| `APP_USER` | Usuario app | Lowest role. Can only sign in to the app | App |

**Channels** are the `web.access` and `app.access` permissions. The table shows the proposed channels, which section 6 asks to confirm.

## 3. Seed matrix

Scopes: **ALL** every record · **AREA** the user's assigned warehouses · **OWN** records the user created or is assigned to · **—** no access.

| Permission | `ADMIN` | `OFFICE` | `WAREHOUSE_MANAGER` | `WAREHOUSE_OPERATOR` | `APP_USER` |
|---|---|---|---|---|---|
| Users, roles, audit, settings | ALL | — | — | — | — |
| Catalogs: read / manage | ALL / ALL | ALL / — | ALL / — | ALL / — | — |
| Products: read / manage | ALL / ALL | ALL / — | ALL / — | ALL / — | — |
| Customers and contacts: read / manage | ALL / ALL | ALL / — | ALL / — | — | — |
| Suppliers and contacts: read / manage | ALL / ALL | ALL / — | ALL / — | — | — |
| Locations: read / manage | ALL / ALL | — | AREA / — | AREA / — | — |
| Invoices: read / create / manage | ALL / ALL / ALL | ALL / ALL / — | AREA / — / — | — | — |
| Stock: read | ALL | ALL | AREA | AREA | — |
| Material entries (receipts): read | ALL | ALL | AREA | OWN | — |
| Material entries: assign | ALL | ALL | AREA | — | — |
| Receipts: count / place | ALL / ALL | — | — | OWN / OWN | — |
| Discrepancies: resolve | ALL | ALL | AREA | — | — |
| Orders: create / read / assign | ALL / ALL / ALL | ALL / ALL / ALL | — / AREA / AREA | — | — |
| Orders: read / fulfill | ALL / ALL | — | — | AREA / AREA | — |
| Orders: validate / cancel / reassign | ALL | ALL | AREA | — | — |
| Orders: accept a difference | ALL | — | AREA | — | — |
| Labels: print / manage | ALL / ALL | ALL / — | AREA / — | AREA / — | — |
| Staff templates: manage | ALL | — | AREA | — | — |
| Web access | ALL | ALL | ALL | — | — |
| App access | ALL | — | — | ALL | ALL |

Catalogs and products are read-only for `OFFICE`, `WAREHOUSE_MANAGER` and `WAREHOUSE_OPERATOR`. Those roles need them to fill in invoices and entries. Only `ADMIN` changes them.

The permission catalog, with the scope each permission supports, is in [access-control.md](access-control.md).

## 4. What each role does, in plain terms

- **`ADMIN`** has every permission. It is the only role that manages users, roles, catalogs, locations, and settings.
- **`OFFICE`** registers invoices, assigns the material entries that those invoices generate to warehouse staff, and resolves discrepancies.
- **`WAREHOUSE_MANAGER`** assigns entries to staff in its warehouses, manages the staff templates of those warehouses, and resolves discrepancies there.
- **`WAREHOUSE_OPERATOR`** counts the materials assigned to them, places them into locations, and fulfills orders in its warehouses. Its "OWN" scope means it only sees what is assigned to it.
- **`APP_USER`** signs in to the app and nothing else.

## 5. Protections

These rules stop the system from locking itself out. They come from CYC (section 7 of [roles-permisos.md](../../../CYC/docs/seguridad/roles-permisos.md)).

1. **Anti-lockout:** a change is rejected if it leaves no active role with `roles.manage`.
2. **System roles** cannot be deleted or renamed.
3. **Nobody changes their own roles.** Another administrator has to do it.
4. **Access-control permissions are not policy targets.** `roles.manage` is not a registered policy action, so no policy can restrict it.

Changes to roles and their matrix require `roles.manage` and are audited (`ROLE_*`, `PERMISSION_*`). Access tokens do not carry permissions, so a change applies on the next request, after the in-memory cache reloads.

## 6. Open questions

1. **Channels.** Should `OFFICE` and `WAREHOUSE_MANAGER` also use the app? Should `WAREHOUSE_OPERATOR` also use the web? The matrix assumes no.
2. **Counts, adjustments and transfers** are in the v1 scope, but no role except `ADMIN` holds them. Who performs them?
3. **Invoice status.** Only `ADMIN` can change the `INVOICE_STATUS` of an invoice. Who closes invoices?
4. **Assignment granularity.** Operators receive entries assigned to them personally (OWN). Should managers and office staff also assign per warehouse (AREA), as the matrix does?

## 7. Resolved

- **Templates** are staff templates (plantillas de personal): the managers of each warehouse define them. Scope `AREA`.
- **Orders** are outbound orders. `ADMIN` and `OFFICE` create them, and warehouse operators fulfill them. `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` validate. Cancelling and reassigning follow the owner's rule in the order spec. See [order-fulfillment.md](../modules/order-fulfillment.md).
- **Entries and exits** will be specified by the business owner. Their rules are not in the matrix yet.
