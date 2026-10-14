# Barcode scanning (operator app)

**Status:** Captured from the business owner, 2026-10-13. The barcode rules below are proposed for confirmation. Open questions are in section 7.

## 1. Purpose

Warehouse operators work from the app. For material entries, order picks and reassignments, the operator scans the barcode of the material instead of searching for it. The name is the fallback when a barcode cannot be read or does not exist.

## 2. Identifying a material

Every product has a barcode. The owner's rule: a material is identified by its barcode, or, when it has none, by its name.

| Field | Rule |
|---|---|
| `barcode` | Required on every active product. Unique among active products |
| Default value | The product code, printed as a Code 128 barcode. Needs no new identifier |
| Supplier barcode | Optional. An EAN or UPC from the supplier can be stored in `barcode` instead, if the business prefers it |

The legacy file has no barcodes. Every imported product takes its product code as its barcode, so all 1,398 products are scannable from the start (see [products.md](products.md)).

## 3. Lookup

The app resolves a scan in this order:

1. **Barcode.** An exact match on an active product's `barcode`.
2. **Code.** If the scanned value matches no barcode, an exact match on a product code, so a printed code works too.
3. **Name.** If nothing matches, or the operator has no barcode to scan, the app searches by name. The results show the code, the name, the unit, and the stock. The operator picks one. Nothing is chosen automatically when several results match.

A scan never creates a product or a line. It only finds an existing one.

**API.** `GET /api/v1/products/lookup?value=` with `products.view`. Answers `{ match, products }`: `match` is `BARCODE`, `CODE` or `NAME`, and each product carries `id`, `code`, `name`, `barcode`, `unit` and `stock`. A barcode or a code gives one product; a name gives up to 50 active products, by name, that contain the value, and none is chosen. `stock` is the product's total in the warehouses the person reads stock in, or `null` without `stock.view` ([stock.md](stock.md) section 5). No match is `BARCODE_NOT_FOUND`.

## 4. Scan rules for each action

The scanned material must belong to what the operator is working on. This is the rule that stops the wrong material from being counted, picked or moved.

### Entry (count)

1. The operator opens the assignments given to them.
2. Scans the material. The app finds the open assignments of that product.
3. If the product is in one assignment, the app opens it. If it is in several, from different invoices, the app lists them and the operator picks one.
4. The operator enters the counted quantity. The count rules are in [material-receiving.md](material-receiving.md).

A product with no open assignment for this operator is rejected with `PRODUCT_NOT_IN_ASSIGNMENT`.

### Exit (pick)

1. The operator opens an order assigned to them.
2. Scans the material. The app finds the order line for that product.
3. The app shows the balance of the material in each location of the warehouse. The operator picks the location, from whichever is most convenient.
4. The operator **scans the location's code** (for example `K05A`) to confirm it, then enters the quantity picked.

A location scanned for a pick must belong to the order's warehouse and hold the material, with `LOCATION_NOT_IN_WAREHOUSE` or `LOCATION_EMPTY` otherwise. A product not on the order is rejected with `PRODUCT_NOT_IN_ORDER`.

### Reassignment

1. The operator scans the picked material.
2. The app lists the open orders of the same warehouse that need that material.
3. The operator picks the target and the quantity, following [order-fulfillment.md](order-fulfillment.md) section 8.

## 5. Permissions

The app uses the permissions that already exist. Scanning adds no permission of its own.

| Action | Permission |
|---|---|
| Sign in to the app | `app.access` |
| Look up a product | `products.view` |
| Count an assignment | `receipts.receive` |
| Pick for an order | `orders.fulfill` |
| Reassign picked material | `orders.reassign` |

## 6. Errors

| Code | When |
|---|---|
| `BARCODE_NOT_FOUND` | No product matches the scan, and the operator has not searched by name |
| `BARCODE_INVALID` | The scan does not match the barcode rules in [validation.md](../rules/validation.md) |
| `PRODUCT_NOT_IN_ASSIGNMENT` | The product is not in an open assignment of this operator |
| `PRODUCT_NOT_IN_ORDER` | The product is not on the order being picked |
| `LOCATION_NOT_IN_WAREHOUSE` | The scanned location is in another warehouse |
| `LOCATION_EMPTY` | The scanned location holds none of the material |

## 7. Open questions

1. **Printing labels.** Product and location labels are needed before the first scan. They are printed from the label catalog. See [labels.md](labels.md).
3. **Supplier barcodes.** Does the business want EAN or UPC codes from suppliers, or only the internal code? *Proposed:* internal code only in v1.
4. **Camera or reader.** Is the scanning done with the phone's camera, or with a dedicated handheld reader? *Proposed:* camera in v1, as the vision doc already says.
