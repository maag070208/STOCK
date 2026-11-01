# F4: Stock and material entry

**Goal:** the system holds the stock of every location, starts from the opening inventory, and records material entries from the supplier invoice to the shelf.

**Depends on:** F1, F2. Uses F3 for scanning and printing when it is done, but does not wait for it.

**Revised, 2026-10-31:** a difference no longer rewrites the expected quantity or the invoice line; the counted quantity is
accepted next to them. Lines may be placed as soon as they are settled, and an invoice that will not be received is
cancelled. See [plan-mejora-stockflow.md](../plan-mejora-stockflow.md) and
[material-receiving.md](../modules/material-receiving.md) sections 5 to 8.

**Permissions added:** `stock.view`, `stock.export`, `invoices.view`, `invoices.create`, `invoices.manage`, `receipts.view`, `receipts.assign`, `receipts.receive`, `receipts.place`, `discrepancies.resolve`, `staff_templates.manage`.

## Specs

- [material-entry.md](../modules/material-entry.md): invoice, lines, assignments.
- [material-receiving.md](../modules/material-receiving.md): counts, adjustments, placement, status lifecycle.
- [locations.md](../modules/locations.md) section 5: stock per location.
- [products.md](../modules/products.md) section 5: opening inventory.
- [barcode-scanning.md](../modules/barcode-scanning.md): scanning in the entry flow.

## Deliverables

### API: stock

- **Movements.** Append-only table. Types: `OPENING` (opening inventory), `RECEIPT` (placement of counted material), `OUT` (pick for an order, from F5), `IN_RETURN` (return to stock, from F5). Each movement stores warehouse, location, product, quantity, user, time, reason, and the source document.
- **Balances.** One row per location and product. Updated in the same transaction as its movement, under a pessimistic write lock. A database check forbids a negative balance.
- **Opening inventory.** Loads the legacy file's `CEXISTENCIAS` into the `TEMPORARY` location of `CENTRAL`, as `OPENING` movements. Dry run first. Confirm writes all balances in one transaction and checks the total against the file's control total (10,914,130). Rejects the whole load if the total does not match. Idempotent: a second run writes nothing.
- **Stock queries.** Balance by location, by product, and by warehouse, with the groups by aisle and rack.

### API: entries

- **Invoices.** Header: number (unique per supplier), supplier, contact, date, warehouse, status. Lines: product, unit (filled from the product), quantity. Creation takes an `Idempotency-Key`.
- **Staff templates.** Per warehouse, the list of operators to pick from. Managed by `WAREHOUSE_MANAGER` in its warehouses.
- **Assignments.** Two paths to the same data: assign after saving, or assign before saving. Each assignment gives a line to an operator with an expected quantity. Expected quantities of a line add up to the line quantity.
- **Counts.** An operator records the counted quantity of each of their assignments, with notes. A count is a match or a discrepancy. Counts are idempotent: a retry with the same `Idempotency-Key` does not count twice.
- **Acceptance of a difference.** Records the accepted quantity next to the counted one; the expected quantity and the invoiced line never change. Requires a note. Audited with the expected, counted and accepted values. Permission `discrepancies.resolve`.
- **Status.** `OPEN` on save. `PENDING` once an assignment exists. `IN_ASSIGNMENT` when every assignment is counted and settled (a match, or an accepted difference). `CLOSED` when every assignment is placed. `CANCELLED` when the invoice will not be received: a note, before anything is counted.
- **Placement.** An operator places a settled assignment into locations of the invoice's warehouse. The quantity may be placed in parts and across calls, never above the accepted quantity, and a line can be placed while other lines are still counted. Each placement is a `RECEIPT` movement and is idempotent.

### App

- The operator's current work, grouped by invoice, with the two tasks of the receipt on the home and how much is pending in each.
- Count screen: scan the material (F3 lookup), show its assignment, enter the counted quantity and notes. A settled count offers `Ubicar ahora`.
- Placement screen: choose locations and quantities, scan each location's code, place the accepted quantity in parts and continue with the rest.

### Web

- Invoice form and detail, with the two paths to assignment.
- Staff templates screen.
- Discrepancy list for `OFFICE`, `WAREHOUSE_MANAGER` and `ADMIN`, with the adjust action.
- Stock by location, read-only.

## Acceptance criteria

1. The opening inventory dry run reports 1,398 products, 10,914,130 units, and the control total matches.
2. Confirming the opening inventory writes it into `TEMPORARY` of `CENTRAL`. Running it again writes nothing.
3. A count of 500 for an expected 500 is a match. The assignment is ready to place.
4. Nails: expected 100, counted 80. The line keeps 100, the assignment accepts 80, and the audit entry has expected 100, counted 80 and accepted 80 with the note.
5. Wire: expected 120, counted 180. The line keeps 120 and the assignment accepts 180.
6. A line of 100 split into 60 and 40, with 60 counted at 50 and accepted: the line stays 100 and the assignments keep 60 and 40.
7. `IN_ASSIGNMENT` is not reached while any difference is open, but the lines that are settled can be placed before it is.
8. Placing 500 screws as 300 in `K05A` and 200 in `K05B` creates two `RECEIPT` movements and raises the two balances by those amounts.
9. Two placements that together exceed what has been counted are rejected. Placements never take a balance negative.
10. Two parallel placements into the same location, run against PostgreSQL, both finish with the correct balance. No update is lost.
11. A `WAREHOUSE_MANAGER` of another warehouse gets `404` on an invoice of this warehouse.
12. `CLOSED` is reached only when every assignment is placed.
13. Creating an invoice twice with the same `Idempotency-Key` creates one invoice.
14. An invoice cancelled before anything is counted is `CANCELLED` and is never received; after a count, the cancellation is refused.
15. A count or a placement sent twice with the same `Idempotency-Key` is written once, and the second answer is the first one.

## Open questions

- Confirm the rule for editing an invoice after it is saved: editable until the first count on it, as proposed in [material-entry.md](../modules/material-entry.md) section 7.
- Confirm the invoice number rule: unique per supplier, as proposed.
