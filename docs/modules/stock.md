# Stock: API contract

**Status:** Draft for F4, 2026-10-14. The rules are in [locations.md](locations.md) section 5, [products.md](products.md) section 5 and [api.md](../architecture/api.md) section 4. This document adds the endpoints and the choices those rules leave open.

## 1. Movements and balances

- **A movement** is one row: its type, warehouse, location, material, quantity, who, when, a reason, and the document that caused it (`source`: a type and an id). It is written once. The database rejects an update, a delete or a truncate.
- **The type** is a `MOVEMENT_TYPE` value: `OPENING`, `RECEIPT`, `OUT`, `IN_RETURN` ([glossary.md](../i18n/glossary.md)). Its attribute `direction` (`in` or `out`) says what it does to the balance; the code reads the attribute, not the name ([catalogs.md](../rules/catalogs.md) rule 5).
- **A balance** is one row per location and material. It changes in the same transaction as its movement. Every balance a request touches is locked before it is read, in a fixed order, so no update is lost and two requests never wait on each other in a circle. A movement out above the balance is refused (`409 STOCK_INSUFFICIENT`), and the database refuses a negative balance as a last guard.
- **The quantity** is positive, at most 999,999, with no more decimals than the material's unit allows (`decimal_places`), or `QUANTITY_INVALID`.
- **Where stock can go.** Only an active location of an active warehouse receives stock. The `TEMPORARY` location receives the opening inventory and nothing else; the opening inventory goes nowhere else.
- **No endpoint writes a movement.** Stock changes only through the operations that cause it: the opening inventory now, placement and picks later.

## 2. Endpoints

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1. With `AREA`, a reader sees the stock of their warehouses only, and a total counts only those rows.

| Method and path | Permission | Notes |
|---|---|---|
| `GET /stock/movements/{id}` | `stock.view` | A movement, with its type labeled in the request's language |
| `POST /stock/balances/query` | `stock.view` | Table contract. One row per balance above zero: warehouse, location, `aisle`, `rack`, material, unit, quantity. Filters: `warehouseId`, `locationId`, `productId`, `location` (code), `aisle`, `rack`, `product` (code or name). Sort: `warehouse`, `location` (default with the warehouse), `product`, `quantity` |
| `POST /stock/products/query` | `stock.view` | Table contract. One row per material and warehouse: the sum of its balances, and in how many locations it is. Filters: `warehouseId`, `productId`, `product`. Sort: `product` (default), `warehouse`, `quantity`, `locations` |
| `POST /stock/opening/dry-run` | `stock.opening` | A form with `file` (the legacy .xlsx) and `warehouseId`. Writes nothing |
| `POST /stock/opening` | `stock.opening` | The same form, with `checksum` (the dry run's). Writes the opening inventory |

The lists read two database views the stock module owns (`stock_balance_rows`, `stock_product_totals`), because a list of stock needs the warehouse, the location and the material of each row. The views only read; every change still goes through the service of the module that owns the table.

## 3. Opening inventory

The legacy file's stock goes into the warehouse's `TEMPORARY` location as `OPENING` movements, one per material with stock above zero ([products.md](products.md) section 5).

- **The report:** `{ checksum, products, withStock, total, controlTotal, controlMatches, missing: [{ row, code }], rejected, alreadyLoaded, movements }`. `missing` lists the file's materials that do not exist as products: their stock cannot be written, so it is not in `total`.
- **The confirmation** writes every movement in one transaction, and only when `total` equals the control total (`IMPORT_CONTROL_MISMATCH` otherwise; a missing material makes it fail). A `checksum` that is not the uploaded file's is `IMPORT_FILE_CHANGED`.
- **Once per warehouse.** When the warehouse already has its opening inventory, a confirmation writes nothing and says `alreadyLoaded`. Two confirmations at the same time load it once: the second waits for the first.
- **Checked on the real file (2026-10-14):** 1,398 materials, 10,914,130 units, the control total matched, and a second run wrote nothing (`LegacyFileAcceptanceTest`).
- **Audit:** `OPENING_INVENTORY_LOADED`, on the warehouse, with the count, the total and the checksum.

## 4. Permissions

| Permission | Scopes | Seed grants | Guards |
|---|---|---|---|
| `stock.view` | `AREA`, `ALL` | `ADMIN` | Reading stock and movements |
| `stock.opening` | `ALL` | `ADMIN` | Loading the opening inventory |

Who else reads stock is not decided ([access-control.md](../rules/access-control.md) section 4 leaves stock to its module). Until the owner says, only `ADMIN` reads it, and an administrator can grant it in the matrix. See question 1.

## 5. Open questions

1. **Who reads stock.** Should `OFFICE`, `WAREHOUSE_MANAGER` and `WAREHOUSE_OPERATOR` see stock, and with which scope? *Proposed:* `OFFICE` with `ALL`, the warehouse roles with `AREA`.
2. **Moving the opening inventory to real locations.** The opening inventory waits in `TEMPORARY` until each material is moved to its location ([products.md](products.md) section 5). That move is not a placement of an invoice, and it has no movement type yet. *Proposed:* a transfer (out of `TEMPORARY`, into the location) as its own operation, specified with the flows.
