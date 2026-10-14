# Products (materials)

**Status:** Draft from the legacy inventory file `INVENTARIO REAL LUNES 10 DE JUN 2024`, sheet `Hoja1`. Field rules are in [validation.md](../rules/validation.md). The API is in section 8. Open questions are in section 9.

**Naming:** the key is `products`, as in the permissions. The display name is **Materiales** (`es`) and **Materials** (`en`). Users call them materials; the code calls them products.

## 1. Purpose

The materials catalog lists every item the warehouse stocks, receives, and ships. Invoices, receiving and orders refer to it.

## 2. What the legacy file contains

Measured on the file, not assumed:

| Item | Finding |
|---|---|
| Rows | 1,400 rows with data. 1,398 are products. 2 rows are a stray note (see below) |
| Columns | `CIDPRODUCTO` (id), `CCODIGOPRODUCTO` (code), `CNOMBREPRODUCTO` (name), `CDESCRIPCIONPRODUCTO` (description), `CUNIDADMEDIDA` (unit), `CEXISTENCIAS` (stock) |
| Ids | 1 to 1,398, contiguous, unique |
| Codes | Unique. Uppercase letters and digits only. 6 to 14 characters. No spaces, no hyphens |
| Names | Up to 52 characters. 20 have extra or doubled spaces. 876 contain `"` (inches). 726 contain `/`. 290 contain `#` (sizes, such as `#10`). 2 use `¨` as an inch mark. 44 contain accented characters |
| Description | Identical to the name in every row. Not imported as a separate field |
| Unit | `PZA` for all 1,398 products |
| Stock | Whole numbers from 0 to 846,600. No negatives. 334 products have zero |
| Stray note | Column G, two rows below the data: `CONTPAQ` and `10914130`. The sum of all stock is 10,914,130, so the note is the accounting system's control total |
| Snapshot date | 10 June 2024, from the file name |

Examples of codes and names, as they appear: `ABRAA1B` → "ABRAZADERA DE ARRANQUE 1-5/8\"". `VSINFIN5161` → "VARILLA SINFIN GALV. 5/16\" X 1 MT".

## 3. Fields

| Field | Source | Rule |
|---|---|---|
| `code` | `CCODIGOPRODUCTO` | Required. Unique. Immutable. 2 to 20 characters, uppercase letters and digits (hyphen allowed for future codes) |
| `name` | `CNOMBREPRODUCTO` | Required. 2 to 120 characters. Trimmed, with doubled spaces collapsed. Case kept as written. Allowed characters: letters including accents, digits, space, and `" / - . , ( ) & ' ° # ¨`. `#` and `¨` were added on 2026-10-14, measured on the file: without them 292 names would be rejected. `¨` is kept as written, not turned into `"` |
| `legacy_id` | `CIDPRODUCTO` | Required on import. Unique. Read-only. Kept so each product can be traced to the legacy file |
| `default_unit` | `CUNIDADMEDIDA` | Required. A value from `UNIT_OF_MEASURE`. See section 4 |
| `barcode` | Product code, see [barcode-scanning.md](barcode-scanning.md) | Required. Unique among active products. Scanned by the operator app |
| `active` | Not in the file | Required. Default `true` |

Not product fields:

- **Description.** Duplicates the name in every row. Not imported (see section 7).
- **Stock.** Stock belongs to a location, not to a product. See section 5.

## 4. Unit of measure

`UNIT_OF_MEASURE` is a value catalog, as described in [catalogs.md](../rules/catalogs.md). The legacy file has one unit, but the business sells some materials by other units (confirmed by the owner).

| Key | Source value | Label (es) | `decimal_places` |
|---|---|---|---:|
| `PIECE` | `PZA` | Pieza | 0 |
| `METER` | Not in the file | Metro | 2 (proposed) |
| `KILOGRAM` | Not in the file | Kilogramo | 3 (proposed) |

The import maps `PZA` to `PIECE`.

**The unit is data on each product, not code.** Each product's `default_unit` is a value from this catalog, and an administrator sets it. The file cannot tell which materials are sold by meter or kilogram. Its names carry lengths, such as "TUBO GALVANIZADO 1-3/8\" X 6 MT", but each of those is one whole piece of 6 meters, so the file counts them as `PZA`. A material is sold by meter only when the business cuts it to length and stocks the cut lengths. Those products get `METER` as their unit, set by the administrator.

## 5. Opening inventory

The file's `CEXISTENCIAS` is the inventory the business has today, as of 10 June 2024. This is the base the system starts tracing from.

**Decision (owner, 2026-10-11):** the file is loaded as the opening inventory, not replaced by a new count.

- **Warehouse:** `CENTRAL` (assumed; see section 7).
- **Location:** every balance goes to the warehouse's **temporary location**, `TEMPORARY`. The warehouse then moves each product to its real location as it is counted and placed. See [locations.md](locations.md).
- **Traceability starts here.** The import is recorded as the first event of the material's history, with the file's quantity and its date as the opening balance. Every later movement builds on it.
- **Totals.** After import, the total is **10,914,130** units, equal to the control total in the file. The import is rejected if it does not match.
- **Counts.** 1,064 products have stock and 334 are at zero. Zero-stock products are imported and stay active.

The file is two years old, so the balances may differ from the shelves. The temporary location is where those differences are found: placing and counting a product surfaces any gap, and that gap is adjusted under the rules in [material-receiving.md](material-receiving.md).

## 6. Import

Proposed process, following the migration pattern in [CYC D-045](../../../CYC/DECISIONES.md):

1. **Dry run.** Read the file, normalize, validate every row, and report: accepted rows, rejected rows with the reason, and the reconciliation result. Nothing is written.
2. **Review.** Compare the dry run's totals with the control total (10,914,130) and the product count (1,398).
3. **Confirm.** Write the products and their opening balances in one transaction. The confirmation revalidates the file's checksum, so the reviewed file is the file imported.
4. **Idempotent.** Products are matched by `code`. Running the import again writes nothing it already wrote, and it does not duplicate balances.
5. **Excluded rows.** The two rows without a code are reported and skipped. The stray note is never imported as data.

## 7. Permissions

| Permission | Who |
|---|---|
| `products.view` | `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER`, `WAREHOUSE_OPERATOR` |
| `products.manage` | `ADMIN` |

## 8. API

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1. Reads need `products.view`, changes and the import `products.manage`.

| Method and path | Notes |
|---|---|
| `POST /products/query` | Table contract. Filters: `code`, `name`, `barcode`, `unit` (a unit's code), `active`. Sort: `code` (default), `name`, `barcode`, `createdAt` |
| `GET /products/{id}` | |
| `POST /products` | `code`, `name`, `unit` (a `UNIT_OF_MEASURE` code), `barcode` (optional: without it, the code) |
| `PATCH /products/{id}` | `name`, `unit`, `barcode`. A blank barcode goes back to the code. The code cannot change: sending it is `UNKNOWN_FIELD` |
| `DELETE /products/{id}` | Deactivates. The product stays in history and frees its barcode |
| `POST /products/{id}/reactivate` | Refused with `DUPLICATE_VALUE` on `barcode` if an active product took it meanwhile |
| `POST /products/import/dry-run` | A form with `file` (the .xlsx). Reads and checks, writes nothing. Response: the report below |
| `POST /products/import` | A form with `file` and `checksum` (the dry run's). Writes the new products in one transaction. Response: the report, with `created` |

A product is `{ id, code, name, barcode, unit: { code, label, decimalPlaces }, legacyId, active, createdAt }`. The unit's label is in the language of the request.

**The import report:** `{ checksum, accepted, newProducts, existingProducts, rejected: [{ row, field, code, message }], stockTotal, controlTotal, controlMatches, created }`.

- `row` is the row number as Excel shows it. `field` is the first field of the row that failed, and `code` and `message` say why: `FIELD_REQUIRED` on `code` for a row without a code (such as the two rows of the note), `FORMAT_INVALID`, `CATALOG_VALUE_INVALID` on `unit` for a unit other than `PZA`, `QUANTITY_INVALID` on `stock`, `BARCODE_INVALID`, or `DUPLICATE_VALUE` for a code or a legacy id that repeats in the file, or that another product already has.
- A product whose code exists is counted in `existingProducts` and left as it is. That is why a second import writes nothing.
- `stockTotal` adds the stock of every product row; `controlTotal` is the number below the note `CONTPAQ`. The confirmation is refused with `IMPORT_CONTROL_MISMATCH` when they differ.
- A confirmation whose `checksum` is not the uploaded file's SHA-256 is refused with `IMPORT_FILE_CHANGED`. A file that is not an .xlsx with the legacy columns is `IMPORT_FILE_INVALID`. Files up to 10 MB.
- Stock is not imported here. The opening inventory arrives with stock, in F4.

**Checked on the real file (2026-10-14):** 1,398 accepted, 2 rejected without a code, control total 10,914,130 matched, 1,398 created, and 0 on the second run. The check runs in the API's suite when `LEGACY_FILE` names the file (`LegacyFileAcceptanceTest`); the file itself stays out of the repository.

**Audit actions:** `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DEACTIVATED`, `PRODUCT_REACTIVATED`, and `PRODUCTS_IMPORTED` (one record per confirmation, with the counts).

## 9. Open questions

1. **Warehouse of the opening inventory.** Assumed `CENTRAL`, because it is the only warehouse named so far. Confirm.
2. **Cut-to-length materials.** Are any products sold cut to a length, such as wire by the meter or rod by the meter? If yes, they take `METER` and their stock is in meters. The file has none marked this way.
3. **Description.** Drop the description field, as proposed, or keep it empty for future use?
4. **Zero-stock products.** Keep them active, as proposed, or deactivate the ones without movement?
5. **CONTPAQ integration.** Planned, not built yet. Timing to define. The `legacy_id` and the control total are kept so the link can be built later without re-importing.
