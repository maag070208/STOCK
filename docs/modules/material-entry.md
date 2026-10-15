# Material entry

**Status:** Captured from the business owner, 2026-10-11. Counting, discrepancies and placement are in [material-receiving.md](material-receiving.md). The API of both is in section 9. Open questions are in section 7.

## 1. Purpose

A material entry records goods that arrive at a warehouse, starting from a supplier invoice. The office registers the invoice and its lines and assigns them to warehouse operators. The operators then count and place the goods, as described in [material-receiving.md](material-receiving.md).

## 2. Actors

| Role | What they do | Permissions |
|---|---|---|
| `OFFICE` | Creates the invoice, adds the lines, assigns lines to operators | `invoices.create`, `invoices.view`, `receipts.assign` |
| `WAREHOUSE_MANAGER` | Assigns lines to operators in its warehouse | `receipts.assign` (AREA) |
| `WAREHOUSE_OPERATOR` | Counts and places the lines assigned to them | `receipts.receive`, `receipts.place` (OWN) |

## 3. Invoice header

The office fills in the form. Every field is validated as defined in [validation.md](../rules/validation.md):

| Field | Rule |
|---|---|
| Invoice number | Required. Unique per supplier (proposed, see section 7) |
| Supplier | Required. Chosen from the suppliers catalog. See [suppliers.md](suppliers.md) |
| Supplier contact | Optional. Chosen from the contacts of the selected supplier |
| Invoice date | Required |
| Warehouse | Required. The one warehouse that receives every line of this invoice |
| Status | Set by the system. See [material-receiving.md](material-receiving.md) section 8 |

The warehouse is required because each invoice goes to one warehouse. It is also what the `AREA` scope checks: a `WAREHOUSE_MANAGER` sees and assigns only invoices of its warehouses.

Saving the header opens the invoice detail.

## 4. Invoice lines

On the detail, the office searches products and adds as many as needed. For each product:

1. **Select** the product from the products catalog.
2. **Unit of measure** fills in automatically from the product's default unit (`UNIT_OF_MEASURE` catalog). It is not edited on this screen (proposed).
3. **Quantity** is required and greater than zero.

A product appears once per invoice (proposed). Adding it again is rejected.

## 5. Two paths to the same result

After the lines are added, the office chooses one of two paths. Both store the same data.

**Path A: save, then assign.**
1. Save the invoice with its lines. The invoice is saved with status OPEN.
2. Later, open the invoice detail and assign the lines to operators.

**Path B: assign, then save.**
1. On the same detail, assign the lines to operators before saving.
2. Save once. The lines and their assignments are stored together.

The office can use either path. Path B does not create a separate draft.

## 6. Closing an invoice

An invoice closes by itself when every share of every line is placed (section 7 of
[material-receiving.md](material-receiving.md)). An invoice captured fast, with lines and no operators, has no shares to
place, so the office closes it with `POST /invoices/{id}/closure` (decided 2026-10-15, F8-03). It is refused while any
share is counted, so the work of the warehouse is never thrown away.

## 6a. Assignment

An assignment gives one line to one `WAREHOUSE_OPERATOR`, with the quantity that operator should receive. Several operators can share one line.

- The assignment's quantity is the operator's **expected quantity**.
- The expected quantities of a line must add up to the line quantity. The screen rejects any other total (proposed, see section 7).
- Operators are picked from the warehouse's staff template (see section 7).
- Assigned lines appear in the operator's app for counting.

## 7. Open questions

1. **Splitting quantity.** Does each operator's share come from the office splitting the line? *Proposed:* yes, and the shares must add up to the line quantity.
2. **Invoice number uniqueness.** Unique per supplier, or globally? *Proposed:* per supplier, because two suppliers can use the same number.
3. **Editing after saving.** Can the office change lines or assignments after saving? *Proposed:* yes, until an operator records a count on the affected assignment. After that, the assignment is locked.
4. **Choosing operators.** Are the operators picked from the warehouse's staff template? *Proposed:* yes. The staff template is the list the assignment searches.
5. **Unit editing.** Can the unit be changed on an invoice line? *Proposed:* no. It comes from the product.

## 8. Not in this module

- Counting, discrepancies, and placement. See [material-receiving.md](material-receiving.md).
- Payment to suppliers.
- Invoice totals and prices. Not in the owner's description, so they are not modeled yet.

## 9. API

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1. With `AREA`, a person reaches the invoices of their warehouses; with `OWN`, an operator reaches their own assignments. Reads outside the scope answer `404`, writes `403`.

### Invoices and assignments

| Method and path | Permission | Notes |
|---|---|---|
| `POST /invoices/query` | `invoices.view` | Table contract. Filters: `search` (the number, the supplier's code or its name, with OR among the three), `number`, `supplierId`, `warehouseId`, `status` (one code or several, so the list can ask for the open ones in one query). Sort: `createdAt` (default, newest first), `number`, `invoiceDate` |
| `GET /invoices/{id}` | `invoices.view` | The invoice with its lines, each with its assignments, counts and placements |
| `POST /invoices` | `invoices.create` | Header `Idempotency-Key` (required). `number`, `supplierId`, `contactId` (optional), `invoiceDate`, `warehouseId`, `lines: [{ productId, quantity, assignments: [{ operatorId, quantity }] }]`. Path A sends no `assignments`; path B sends them and saves once |
| `POST /invoices/{id}/lines` | `invoices.create` | `productId`, `quantity`. While the invoice is `OPEN` or `PENDING` |
| `PATCH /invoices/{id}/lines/{lineId}` | `invoices.create` | `quantity`. Not once a share of the line is counted (`ASSIGNMENT_LOCKED`); with shares, they must still add up |
| `DELETE /invoices/{id}/lines/{lineId}` | `invoices.create` | Removes a line added by mistake, while nothing of it is counted. The audit log keeps it |
| `PUT /invoices/{id}/lines/{lineId}/assignments` | `receipts.assign` | `assignments: [{ operatorId, quantity }]`. Replaces the shares of the line, while none is counted |
| `POST /invoices/{id}/closure` | `invoices.create` | Closes an invoice by hand (F8-03): it needs lines (`INVOICE_WITHOUT_LINES`) and nothing counted (`INVOICE_STATUS_INVALID`). Audited as `INVOICE_CLOSED`. From the first count on, the placement closes it |
| `GET /warehouses/{id}/staff` | `receipts.assign` or `staff_templates.manage` | The operators of the warehouse's template |
| `PUT /warehouses/{id}/staff` | `staff_templates.manage` | `userIds`. Each one an active person who can count receipts and works in the warehouse |

**Rules as the API applies them.**
- The number is uppercased and unique per supplier (`DUPLICATE_VALUE` on `number`). The supplier and the warehouse must be active (`CATALOG_VALUE_INVALID`). The date is required, it is a date, and **it may be in the future** (decided 2026-10-15, F8-01: the office records what the supplier's paper says). There is no contact: it left the contract in F8-04, and what is already stored is untouched.
- A line names an active material once (`DUPLICATE_VALUE` on `lines.<i>.productId`). Its unit comes from the material and is not sent. Its quantity is positive with the unit's decimals (`QUANTITY_INVALID`).
- The shares of a line name operators of the warehouse's staff template, once each, and add up to the line quantity (`ASSIGNMENT_TOTAL_MISMATCH` on the line's `assignments`).
- The first share moves the invoice from `OPEN` to `PENDING`. A `CLOSED` invoice cannot change (`INVOICE_LOCKED`); other actions out of their status answer `INVOICE_STATUS_INVALID`.

### Receiving

| Method and path | Permission | Notes |
|---|---|---|
| `GET /receipts/mine` | `receipts.view` | The signed-in operator's assignments of `PENDING` and `IN_ASSIGNMENT` invoices, grouped by invoice |
| `POST /receipts/assignments/{id}/count` | `receipts.receive` | `countedQuantity` (zero or more), `notes` (optional). While the invoice is `PENDING`: a later count corrects the earlier one |
| `POST /receipts/discrepancies/query` | `discrepancies.resolve` | The open discrepancies the reader can resolve. Filter: `warehouseId` |
| `POST /receipts/assignments/{id}/adjustment` | `discrepancies.resolve` | `note` (required). The expected quantity becomes the counted one, and the line moves by the difference |
| `POST /receipts/assignments/{id}/placements` | `receipts.place` | `placements: [{ locationId, quantity }]`. While the invoice is `IN_ASSIGNMENT`. Several calls may place one assignment in parts |

An assignment is `{ id, lineId, operator, expectedQuantity, countedQuantity, difference, outcome, countNotes, countedAt, placedQuantity, placed, placements }`. `outcome` is `MATCH` or `DISCREPANCY` once counted.

- **Status.** The invoice moves to `IN_ASSIGNMENT` when every line with a quantity is assigned and every share is counted and settled (a match, or an adjusted discrepancy). It moves to `CLOSED` when every share is placed. A share adjusted to zero has nothing to place.
- **Placement.** Into active locations of the invoice's warehouse (`LOCATION_NOT_IN_WAREHOUSE` otherwise), never into `TEMPORARY`, never more than was counted (`PLACEMENT_EXCEEDS_COUNT`). Each placement is a `RECEIPT` movement whose source is the assignment ([stock.md](stock.md)).
- **One at a time.** A count, an adjustment or a placement locks its invoice first, so operators working on the same invoice at the same moment never lose an update, and two placements into the same location both count.
- **Audit actions:** `INVOICE_CREATED`, `INVOICE_LINE_ADDED`, `INVOICE_LINE_UPDATED`, `INVOICE_LINE_REMOVED`, `INVOICE_STATUS_CHANGED`, `RECEIPT_ASSIGNMENTS_CHANGED`, `RECEIPT_COUNTED`, `DISCREPANCY_ADJUSTED` (with the expected quantity before and after, the difference and the note), `RECEIPT_PLACED`, `STAFF_TEMPLATE_CHANGED`.
- **Not built:** editing the header of a saved invoice (`invoices.manage`); the open question 3 of this document covers lines and assignments only.
