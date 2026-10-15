# Order fulfillment (exit)

**Status:** Captured from the business owner, 2026-10-13. Open questions are in section 9.

## 1. Purpose

An order takes materials out of a warehouse. The office creates it with the materials and quantities, and a warehouse operator picks them from the locations and fulfills it. The counterpart of an exit is the material entry, in [material-entry.md](material-entry.md).

Orders belong to one warehouse each, the same as invoices.

## 2. Actors and permissions

| Role | What they do | Permissions |
|---|---|---|
| `ADMIN` | Creates orders. Everything else, until the owner says otherwise | All |
| `OFFICE` | Creates orders. Assigns orders to operators | `orders.create`, `orders.assign`, `orders.view` (ALL) |
| `WAREHOUSE_MANAGER` | Assigns orders to operators in its warehouses. Validates | `orders.assign`, `orders.validate` (AREA) |
| `WAREHOUSE_OPERATOR` | Fulfills orders of its warehouses | `orders.view`, `orders.fulfill` (AREA) |

Validating, cancelling and reassigning follow the owner's rule: `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` validate. Cancelling and reassigning are covered in sections 6 and 8.

## 3. Order header

| Field | Rule |
|---|---|
| Customer | Required. An active customer. See [customers.md](customers.md) |
| Customer contact | Optional. One of the selected customer's active contacts |
| Warehouse | Required. The warehouse the materials leave from. Only warehouses the user can access |
| Order number | Required. Unique per warehouse. 1 to 30 characters: letters, digits, `-`, `/`. Uppercased |
| Due date | Required. The day the order must be delivered. It drives the alerts in section 7 |
| Created on | Set by the system |
| Status | Set by the system. See section 6 |

## 4. Lines

The creator searches materials, and for each one sees the **available** stock in the order's warehouse. Then types the quantity to take out.

| Field | Rule |
|---|---|
| Material | Required. From the products catalog. One line per material per order (proposed) |
| Available | Shown, not editable. See section 5 |
| Quantity | Required. Greater than zero. At most the available quantity. Decimals follow the unit (see [validation.md](../rules/validation.md)) |
| Unit | From the material. Not editable on this screen |

A line over the available quantity is rejected with `STOCK_INSUFFICIENT`. The order is not saved with any line that fails. This is the rule that prevents running out during fulfillment: the shortage is found when the order is created, not when the operator picks.

## 5. Reservation

Available stock is the balance in the warehouse, minus what open orders have reserved:

> **available = balance in the warehouse − reserved by open orders**

- **On creation**, each line reserves its quantity.
- **On each pick**, the reservation falls by the picked quantity, and the balance falls by the same amount.
- **On cancellation**, the remaining reservation is released.

Two orders cannot reserve the same stock. The check and the reservation happen in one transaction per material and warehouse, so two orders created at the same moment cannot both take the last unit.

## 6. Statuses

Four statuses come from the owner. `CANCELLED` is added because cancellation is allowed and must end somewhere (proposed). These are the values of the `ORDER_STATUS` catalog, labels in `es` and `en`.

| Status | Key | Meaning |
|---|---|---|
| Creada | `CREATED` | Saved and reserved. Not started |
| Surtiendo | `FULFILLING` | An operator started picking |
| Surtida | `FULFILLED` | Every line is picked |
| Validada | `VALIDATED` | Checked and locked. Final |
| Cancelada | `CANCELLED` | Cancelled before validation. Final |

Transitions:

| From | To | Who |
|---|---|---|
| `CREATED` | `FULFILLING` | Operator, when they start picking |
| `FULFILLING` | `FULFILLED` | Operator, when every line is picked |
| `FULFILLED` | `VALIDATED` | `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER` |
| `CREATED`, `FULFILLING` | `CANCELLED` | Only when nothing has been picked (section 8) |

**Cancellation is allowed only before any pick.** Once material has been picked, the order goes through reassignment (section 8). A validated order cannot be cancelled.

## 7. Fulfillment

The operator opens an order assigned to them, and for each line picks the quantity from **any location of the warehouse that holds the material**. The operator chooses the location, from whichever is most convenient for them. The system does not suggest or force a location.

- The picked quantities of a line must add up to its quantity.
- Each pick is a movement out of one location, with the user and the time.
- A pick cannot exceed the balance of its location.
- Each pick is recorded against its location, so the history of every material still shows where it left from.
- Reassignment (section 8) can move an order to another operator while it is open.

## 7a. Assignment, due date and alerts

**Decided 2026-10-14.**

- **Assignment.** `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouses) assign an order to one operator of its warehouse. Reassignment in section 8 moves an order that is already assigned.
- **Due date.** The order date is the due date: the day the order must leave the warehouse.
- **Multi-day fulfillment.** An order can be fulfilled over several days. What was picked yesterday stays picked, and its reservation stays. Nothing expires or is released automatically.
- **Alerts.** For every order that is not `VALIDATED` or `CANCELLED`:
  - one day before the due date, to the assigned operator and the warehouse manager;
  - on the due date, to the same people and the creator;
  - every day after the due date while it stays open, to the same people and the creator.
  - Alerts show in the app and the web. Email is proposed as a second channel. Each alert is recorded so it is not sent twice for the same day.

## 8. Cancellation and reassignment

**Confirmed by the owner, 2026-10-13.**

The owner's rule: an order can be cancelled until it is validated, but **if material is already picked, it goes through a reassignment process.** Picked material has left its location, so cancelling it would move stock with no record of where it went.

- **No picks yet:** the order can be cancelled. Its reservation is released.
- **Any pick made:** the order cannot be cancelled. Its picks go through reassignment first. Once none are left, it can be cancelled.

Reassignment has three actions. Each one needs a note, and each one is audited with its source, target, quantity, who did it, and when.

1. **Change the operator.** An open order (`CREATED` or `FULFILLING`) moves to another operator in the same warehouse. Its lines, picks and reservations stay with it. No stock changes.
2. **Move picked quantities to another order.** The picked quantities of an order move to another open order in the same warehouse with the same material. The moved quantity cannot exceed the target's remaining need. The source's reservation for those quantities is released, and the target's is reduced. No stock changes, because the goods are already out of the shelves and now serve the other order.
3. **Return picked quantities to stock.** Used only when no open order can take them (option 2 finds no target). The operator places the material back into a location of the warehouse, chosen from any location, and the return is a movement in. This is the only reassignment that changes a balance.

Rules:

- Allowed only in `CREATED` or `FULFILLING`. A validated order cannot be reassigned.
- Same warehouse for every action. Same material for option 2.
- The target order of option 2 must be open.
- Who: `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER`, within their warehouses (`orders.reassign`).

**What moved is kept (decided 2026-10-14):** every move of option 2 and every return of option 3 writes a row in
`order_reassignments` — the source order and line, the target order and line (or the location of a return), the kind
(`TRANSFER` or `RETURN`), the quantity, the note, who and when — in the same transaction as the picks. The picks
themselves only change line, so without this record the report of orders could not count what was moved. It is not a
substitute for the audit log: the log keeps the same facts for people, and this keeps them for the reports.

## 9. Open questions

3. **Shortage during picking.** If the operator finds less than the balance shows, is the order blocked, or is the shortage recorded and the rest reassigned?
5. **Cancelled state.** Confirm that `CANCELLED` is a status of its own.
## 10. Decided

- **Who cancels (decided 2026-10-14):** `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouses), only for orders with no picks.

- **Validation mismatch (decided 2026-10-14):** if the picks do not match the lines when the order is validated, the order returns to `FULFILLING` so the operator corrects them. Every return is audited with a note.
- **Accepting a small difference (decided 2026-10-14):** a difference the customer accepts can be approved by `WAREHOUSE_MANAGER` (in its warehouses) or `ADMIN`, with a note. The line is then adjusted to the picked quantity, the same way as in material entries. `OFFICE` can send the order back, but cannot accept a difference. Permission `orders.accept_difference`.

- **Pick location:** the operator chooses, from any location of the warehouse that holds the material.
- **Customer:** every order names an active customer, from the customers catalog ([customers.md](customers.md)).
- **Comprobante PDF:** generated by the API from the stored order, at `VALIDATED`. Saved once, and never regenerated with different data. Shows the warehouse, order number and due date, the customer and its contact, each line (material code, name, unit, quantity), the creator, the operator or operators, the validator, and signature lines. The PDF is the delivery receipt. Signatures are physical; the system has no signature step.
- **Where the PDF is stored (decided 2026-10-14):** private storage, in the API's own database (`order_documents`), reached only through `GET /orders/{id}/document` with the permission to read the order. Never a public URL. It is printed in the language of whoever validates, from [documents.md](../i18n/documents.md).
- **Partial fulfillment over several days:** allowed (section 7a).
- **Assignment:** `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER`, as in section 2.

## 11. Rules

1. **Stock is never negative**, even with concurrent orders.
2. **Reservations never exceed available stock.**
3. **Every pick is a movement.** Stock changes only through movements.
4. **A validated order is locked.** No line, quantity, or assignment changes after validation.
5. **An order only uses stock from its own warehouse.**
6. **Every status change is audited**: who, when, and the previous status.

## 12. Catalog

`ORDER_STATUS` is a value catalog, as described in [catalogs.md](../rules/catalogs.md). Its values are the five statuses in section 6.
