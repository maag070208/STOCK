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

## 5. Difference

A count that does not match the expected quantity is a **difference**. No quantity is rewritten and nothing is written off silently. Each assignment holds four values:

| Field | Rule |
|---|---|
| `invoicedQuantity` | The line's quantity as the supplier billed it. Never changes |
| `expectedQuantity` | This operator's share of the line. Never changes |
| `countedQuantity` | What the operator physically counted |
| `acceptedQuantity` | What the warehouse accepts into stock. `null` while a difference is open |

- **Match.** `countedQuantity` equals `expectedQuantity`. The count is accepted as soon as it is entered.
- **Difference.** They differ. `difference = countedQuantity − expectedQuantity`, and it stays visible after it is accepted, so the invoice keeps what the supplier billed.
- **Acceptance.** A user with `discrepancies.resolve` in the scope of the warehouse (`OFFICE`, `WAREHOUSE_MANAGER` in its warehouses, `ADMIN`) accepts the physical count: `acceptedQuantity` becomes `countedQuantity`.
- A count of zero is accepted like any other: the assignment stays on record with zero and has nothing to place.

The supplier's invoice is never edited. The line keeps its invoiced quantity and the assignment keeps its expected quantity; acceptance is recorded next to them, and the screen shows invoiced, expected, counted and accepted together.

Each acceptance writes an audit entry with:

- The expected, counted and accepted quantities.
- The difference.
- The user who made it, and the date.
- A required note explaining the acceptance.

### Balance

- For every invoice line, the sum of its assignments' expected quantities equals the line quantity. Accepting a count never changes either.
- An assignment is **settled** when its accepted quantity equals its counted quantity: a match settles on entry, a difference settles when it is accepted.
- An assignment is **placed** when it is settled and its placed quantity equals its accepted quantity.

## 6. Invoice status

A line moves on by itself: an open difference blocks **only its own line**, never the rest of the invoice. The line that matched can be placed while another one is still being counted or accepted.

- The invoice moves to `IN_ASSIGNMENT` when every line is assigned and every assignment is settled.
- It moves to `CLOSED` when every assignment is placed, whether or not it ever passed through `IN_ASSIGNMENT`.
- An invoice that will not be received is **cancelled**, never closed: see section 8.

## 7. Placement

Placement starts as soon as the assignment is settled (a match, or a difference that was accepted), even while other lines are still being counted or accepted.

- The operator places into locations of the invoice's warehouse.
- The quantity can be split across several locations, and it can be placed in parts across several calls. What is added never goes above the accepted quantity.
- When part of the accepted quantity is placed, the operator continues with the rest from the same screen.
- Each placement records the location and the quantity.
- A placement increases the stock of that location. Stock is derived from movements, so the placement creates the receipt movement. See [vision-and-scope.md](../architecture/vision-and-scope.md).
- When every assignment on an invoice is placed, the invoice moves to `CLOSED`.

Example: 500 screws counted and accepted. The operator places 300 in `K05A` and 200 in `K05B`. Each location can also hold other products, with their own quantities.

## 8. Invoice status lifecycle

The owner's original set (OPEN, PENDING, CLOSED) plus the status "en asignación" that the owner named, and the cancellation of an invoice that will not be received:

| Status | Key | Meaning | Set when |
|---|---|---|---|
| Abierta | `OPEN` | Invoice is being captured | Invoice is created |
| Pendiente | `PENDING` | Assignments exist; counting and placement are in progress | Saved with at least one assignment |
| Por ubicar | `IN_ASSIGNMENT` | Every assignment is counted and settled; placement is in progress | Last count or acceptance is done |
| Completada | `CLOSED` | Every assignment is placed | Last assignment is placed |
| Cancelada | `CANCELLED` | The invoice will not be received | Cancelled with a note while nothing has been counted |

`CLOSED` is reached only by receiving: an invoice completes when its goods are in the shelves, so no screen closes an invoice by hand. `CANCELLED` is the explicit way out for the invoice that never arrives. It needs a note, it is refused once something has been counted, and it is audited as `INVOICE_CANCELLED`.

These values are rows in the `INVOICE_STATUS` catalog. The labels exist in `es` and `en`. See [catalogs.md](../rules/catalogs.md).

## 9. Open questions

1. **Correcting a count.** Can the operator correct a count after entering it? *Proposed:* yes, until the assignment has a placement or its invoice is `IN_ASSIGNMENT`.
2. **Splitting placement.** Can one counted quantity go into several locations? *Proposed:* yes, and in several parts across several calls.
3. **Tolerance.** Should small differences be accepted automatically, for example by weight? *Proposed:* no. Exact match only.
4. **Who places.** Only the operator who counted an assignment can place it? *Proposed:* yes.
5. **Supplier notice.** Does an acceptance need to tell the supplier? The system records it; any notice happens outside it.
