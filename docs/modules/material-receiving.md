# Material receiving

**Status:** Captured from the business owner, 2026-10-11. Field rules are in [validation.md](../rules/validation.md). Open questions are in section 9.

## 1. Purpose

After an invoice's lines are assigned to warehouse operators, each operator physically counts the goods that should arrive. Any difference is adjusted immediately by another role. Then the operator places the goods into locations.

## 2. Actors

| Role | Step |
|---|---|
| `WAREHOUSE_OPERATOR` | Counts the assignments they received. Places counted goods into locations |
| `WAREHOUSE_MANAGER` | Adjusts discrepancies in its warehouses |
| `OFFICE` | Adjusts discrepancies in any warehouse |
| `ADMIN` | Adjusts discrepancies in any warehouse |

## 3. Assignment

An **assignment** is one invoice line given to one operator, with the quantity that operator should receive. Its **expected quantity** is that share of the line. See [material-entry.md](material-entry.md).

The operator sees a list of their assignments, grouped by invoice. Each row shows the material, the unit (from the product), and the expected quantity.

## 4. Count

For each assignment, the operator enters:

| Field | Rule |
|---|---|
| `countedQuantity` | Required. Zero or more. Unit is the assignment's unit |
| `notes` | Optional |

The result is one of two outcomes:

- **Match.** `countedQuantity` equals `expectedQuantity`. The assignment is ready to place.
- **Discrepancy.** They differ. `difference = countedQuantity − expectedQuantity`. Examples from the owner:

| Material | Expected | Counted | Difference | Outcome |
|---|---:|---:|---:|---|
| Screws (tornillos) | 500 | 500 | 0 | Match |
| Nails (clavos) | 100 | 80 | −20 | Discrepancy: the supplier sent less |
| Wire (alambre) | 120 | 180 | +60 | Discrepancy: the supplier sent more |

Only exact matches pass. There is no tolerance (see section 9).

## 5. Adjustment

**No discrepancy is ever left open.** A discrepancy is closed by adjusting the expected quantity to the counted quantity:

- If the supplier sent **less**, the expected quantity goes **down** to the counted quantity. Nails: 100 becomes 80.
- If the supplier sent **more**, the expected quantity goes **up** to the counted quantity. Wire: 120 becomes 180.

The adjustment is made by a user with `discrepancies.resolve` in the scope of the warehouse: `OFFICE`, `WAREHOUSE_MANAGER` in its warehouses, or `ADMIN`.

Each adjustment writes an audit entry with:

- The original expected quantity and the adjusted one.
- The difference.
- The user who made it, and the date.
- A required note explaining the adjustment.

### Invariant: the line always balances

For every invoice line, the sum of its assignments' expected quantities equals the line quantity. An adjustment changes the assignment and the line by the same difference, so the invoice never shows a mismatch. Example: a line of 100 split into 60 and 40. The first assignment is counted at 50, so it is adjusted to 50. The line becomes 90, and the invoice line is updated to 90.

An adjustment to zero is allowed, for example if nothing arrived. The assignment stays on record with zero.

## 6. Invoice status

The invoice moves to `IN_ASSIGNMENT` only when:

1. Every assignment on it has been counted, and
2. Every discrepancy has been adjusted.

No invoice can move on with an open difference.

## 7. Placement

After the count and any adjustment, the operator places each assignment into locations of the invoice's warehouse. The counted quantity and the expected quantity are then equal, so the operator places the full amount.

- The placed quantity can be split across several locations. The sum must equal the counted quantity (see section 9).
- Each placement records the location and the quantity.
- A placement increases the stock of that location. Stock is derived from movements, so the placement creates the receipt movement. See [vision-and-scope.md](../architecture/vision-and-scope.md).
- When every assignment on an invoice is placed, the invoice moves to `CLOSED`.

Example: 500 screws counted. The operator places 300 in `K05A` and 200 in `K05B`. Each location can also hold other products, with their own quantities.

## 8. Invoice status lifecycle

Proposed. The owner's original set (OPEN, PENDING, CLOSED) plus the status "en asignación" that the owner named:

| Status | Key | Meaning | Set when |
|---|---|---|---|
| Abierta | `OPEN` | Invoice is being captured | Invoice is created |
| Pendiente | `PENDING` | Assignments exist; counting is in progress | Saved with at least one assignment |
| En asignación | `IN_ASSIGNMENT` | Every assignment is counted and adjusted; placement is in progress | Last count and last adjustment are done |
| Cerrada | `CLOSED` | Every assignment is placed | Last assignment is placed |

These values are rows in the `INVOICE_STATUS` catalog. The labels exist in `es` and `en`. See [catalogs.md](../rules/catalogs.md).

## 9. Open questions

1. **Correcting a count.** Can the operator correct a count after entering it? *Proposed:* yes, until the invoice reaches `IN_ASSIGNMENT`.
2. **Splitting placement.** Can one counted quantity go into several locations? *Proposed:* yes.
3. **Tolerance.** Should small differences be accepted automatically, for example by weight? *Proposed:* no. Exact match only.
4. **Who places.** Only the operator who counted an assignment can place it? *Proposed:* yes.
5. **Supplier notice.** Does an adjustment need to tell the supplier? The system records the adjustment; any notice happens outside it.
