# Reports and kardex

**Status:** Accepted, 2026-10-14 (F6). The columns and the minimum stock were confirmed by the owner.

## 1. Purpose

The owner sees the stock as it is, how each material got there, and takes the result away as a file. Reports read; they
never change stock. Every report answers the same rows the export writes, so a file and the screen never disagree.

## 2. Actors and permissions

| Permission | Scopes | Seed grants | Guards |
|---|---|---|---|
| `reports.view` | `AREA`, `ALL` | `ADMIN` ALL, `OFFICE` ALL, `WAREHOUSE_MANAGER` AREA | Reading every report |
| `reports.export` | `AREA`, `ALL` | `ADMIN` ALL, `OFFICE` ALL, `WAREHOUSE_MANAGER` AREA | Exporting a report to XLSX or PDF |

`WAREHOUSE_OPERATOR` and `APP_USER` hold neither: the reports are for the office and the warehouse's manager. With
`AREA`, every report is answered over the reader's warehouses only, and a warehouse outside their area answers `404`,
never an empty list.

## 3. Reports

All of them are `POST`, with filters, and answer the table contract (`data`, `total`) plus the totals the screen shows.

| Report | Path | Filters | Rows |
|---|---|---|---|
| Current stock | `/api/v1/reports/stock` | `warehouseId`, `aisle`, `rack`, `location`, `product` (code or name), `belowMinimum` | warehouse, aisle, rack, location, material code and name, unit, quantity |
| Kardex | `/api/v1/reports/kardex` | `productId` (required), `warehouseId`, `from`, `to` | date and time, movement type, warehouse, location, quantity in and out, running balance of the location, running balance of the product in the warehouse, user, reason, source |
| Movements | `/api/v1/reports/movements` | `warehouseId`, `productId`, `type`, `userId`, `from`, `to` | date and time, type, warehouse, location, material, quantity, direction (in or out), user, reason, source |
| Orders | `/api/v1/reports/orders` | `warehouseId`, `customerId`, `status`, `from`, `to` | order number, warehouse, customer, status, due date, material, asked, picked, moved to another order, moved in, created on, validated on |
| Invoices and entries | `/api/v1/reports/invoices` | `warehouseId`, `supplierId`, `status`, `from`, `to` | invoice number, supplier, warehouse, status, date, material, expected, counted, difference, placed |

**Current stock** also answers the totals the screen groups by: per warehouse, per aisle and rack, and the grand total.
The grand total is always the sum of the rows it lists (acceptance criterion 1). With `belowMinimum` only the materials
whose total in the scoped warehouses is below their `minimum` are listed.

**Kardex** answers the movements of one material in one warehouse in order, oldest first, with two running balances: the
one of the location the movement touched, and the one of the material in the warehouse. The last running balance of the
warehouse equals the material's current balance there (acceptance criterion 2).

Movements in the kardex and the movements report are read from `stock_movements` (append-only), with the direction of
their movement type: an `OUT` subtracts, everything else adds.

**Orders** sums, per order and material: what was asked, what was picked, what was moved to another order and what came
in from another one (`order_reassignments`, written by the reassignment of F5-10 to F5-12), and the dates.

## 4. Exports

`POST /api/v1/reports/{report}/export` with `{ format: "XLSX" | "PDF", ...filters }`, guarded by `reports.export`,
answers the file: the same rows, with the same filters, as the report of the screen (acceptance criterion 4). The API
builds it, never the browser, and every export is audited as `REPORT_EXPORTED` with the report, the format, the filters
and the row count (acceptance criterion 5). XLSX is written by `core/files/XlsxWriter`, the counterpart of the reader the
import uses; PDF is a table drawn with the same library as the comprobante of an order.

## 5. Minimum stock

`products.minimum` is one number per material, in its unit, `0` when none was set, and it is edited on the product's
form. The current stock report compares the material's total over the scoped warehouses with it: `belowMinimum=true`
lists only what is below, and each row carries the minimum and the shortfall.

## 6. Rules

1. **Reports read.** No report writes stock, movements or balances.
2. **One result, two shapes.** The screen and the file come from the same rows and the same totals.
3. **Scope is applied before the totals.** A total never includes a row the reader cannot see.
4. **Out of scope is `404`.** A warehouse the reader cannot reach answers as if it did not exist.
5. **Every export is audited** with who, when, which report, which format and how many rows.

## 7. Open questions

- **Retention of the exported files.** Nothing is kept: the file is built when it is asked for and answered. If the
  owner wants them kept, it is a decision of F7 (backups).
- **Scheduled reports.** A report sent by itself (daily or weekly) is not in this phase.
