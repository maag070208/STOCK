# F5: Orders

**Goal:** administrators and the office create orders against available stock, warehouse operators pick them, and the order is validated, can be cancelled or reassigned as the owner decided, and produces a PDF.

**Depends on:** F4 (balances and movements), F2 (customers).

**Permissions added:** `orders.create`, `orders.view`, `orders.assign`, `orders.fulfill`, `orders.validate`, `orders.cancel`, `orders.reassign`, `orders.accept_difference`.

## Specs

- [order-fulfillment.md](../modules/order-fulfillment.md): the whole flow.
- [barcode-scanning.md](../modules/barcode-scanning.md): picks and reassignment in the app.

## Deliverables

### API

- **Orders.** Header: warehouse, customer, customer contact, order number (unique per warehouse), due date. Creation takes an `Idempotency-Key`. Created by `ADMIN` and `OFFICE`.
- **Assignment.** `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouses) assign an order to one operator. Its operator sees it in the app.
- **Lines.** Material, quantity (greater than zero, at most the available stock). One line per material per order.
- **Reservations.** Taken on creation, under a lock on the product and warehouse. `available = balance − reserved by open orders`. A line that exceeds available is rejected with `STOCK_INSUFFICIENT`, and the order is not saved.
- **Picks.** The operator picks from any location of the warehouse that holds the material. Each pick is an `OUT` movement, and it reduces the reservation and the balance by the same amount. A pick cannot exceed the balance of its location.
- **Statuses.** `CREATED` → `FULFILLING` → `FULFILLED` → `VALIDATED`, and `CANCELLED`. Each change is audited with who, when, and the previous status.
- **Validation.** `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in their warehouses) validate a `FULFILLED` order. A validated order is locked. If the picks do not match the lines, the order returns to `FULFILLING`. `WAREHOUSE_MANAGER` or `ADMIN` can accept a small difference with a note (`orders.accept_difference`).
- **Cancellation.** Allowed only with no picks. Releases the reservation.
- **Reassignment.** The three actions in [order-fulfillment.md](../modules/order-fulfillment.md) section 8: change the operator, move picked quantities to another open order, and return picked quantities to stock. Only the third creates an `IN_RETURN` movement. Each action needs a note and is audited.
- **Alerts.** Scheduled check of open orders against their due date. One day before, on the due date, and daily after it. Sent to the assigned operator, the warehouse manager and the creator, in the app and the web, and recorded so the same day is not sent twice. Section 7a of the order spec.
- **PDF.** Generated at validation from the stored order, and saved once in private storage. A second download returns the same file. Content as listed in section 10 of the order spec.

### App

- Assigned orders, and the pick screen: scan the material, see its balance per location, choose the location, scan its code, enter the quantity.
- Reassignment screen: scan the picked material, choose the target order, enter the quantity.

### Web

- Order form: warehouse, order number, date, customer, contact, then a material search with available stock per line.
- Order detail, with validation, cancellation and reassignment actions, each shown only when the user's permissions allow it.
- PDF download.

## Acceptance criteria

1. An order line above the available stock is rejected with `STOCK_INSUFFICIENT`, and no order is saved.
2. Two orders created at the same moment for the last unit: exactly one succeeds, and the other gets `STOCK_INSUFFICIENT`.
3. A pick creates an `OUT` movement, and the balance of that location goes down by the picked quantity.
4. The operator can pick a location different from the one another operator used, with no suggestion from the system.
5. Cancelling an order with picks is rejected. Cancelling an order with no picks releases its reservation.
6. Reassignment option 2 moves picked quantities to another open order of the same material. No balance changes.
7. Reassignment option 3 creates an `IN_RETURN` movement into the location the operator chooses. The balance goes up by the returned quantity.
8. After all picks are reassigned out, the order can be cancelled.
9. A validated order rejects any change with `ORDER_LOCKED`.
10. A `WAREHOUSE_OPERATOR` of another warehouse gets `404` on the order.
11. The PDF contains the warehouse, order number, date, customer, contact, each line, the creator, the operator, the validator, and signature lines.
12. A second PDF download returns the same file, byte for byte.
13. Reusing an `Idempotency-Key` for another order returns `409 IDEMPOTENCY_KEY_REUSED`.
14. An order with a due date tomorrow that is not validated raises an alert the day before. An order still open the day after its due date raises an alert again.
15. An order surtido partly on Monday and completed on Tuesday keeps its Monday picks and reservation.

## Open questions

These are open in [order-fulfillment.md](../modules/order-fulfillment.md) section 9. The phase builds around them:

- Shortage found while picking. Proposed: recorded, and the rest reassigned.
- Who cancels: decided, `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER`, only with no picks.
- Where the PDF is stored. Proposed: private storage, as CYC does ([D-023](../../../CYC/DECISIONES.md)).
