# F5 tasks: Orders

Phase: [F5-orders.md](../F5-orders.md).

**Progress, 2026-10-14:** F5-01, F5-02, F5-03 and F5-15 are done in the API: an order is created with `Idempotency-Key`, its lines reserve their quantity under an advisory lock on the material and warehouse (`available = balance − reserved by open orders`, `STOCK_INSUFFICIENT` says how much is available), the number is unique per warehouse, and the customer must be active. `OrdersEndpointTest` covers creation, the reservation, the duplicate number, the inactive customer, idempotency and the concurrent case for the last unit. F5-04, F5-05 and F5-06 are done too: an order is assigned to one active operator of its warehouse, each pick is an `OUT` movement (the operator chooses the location, never the system), a pick above the location balance or the line's remaining quantity is refused, and the order moves `CREATED → FULFILLING → FULFILLED`. `OrderFulfillmentTest` covers the assignment, the picks, the statuses and the refusals. F5-07, F5-08 and F5-09 are done: a `FULFILLED` order is validated and locked, a mismatch returns it to `FULFILLING` with a note, a difference the manager or the administrator accepts adjusts the line to the picked quantity, and an order with no picks is cancelled and releases its reservation. `OrderClosureTest` covers them, and `OrderRulesTest` covers the status machine and the reassignment quantities. F5-10, F5-11 and F5-12 are done as well: an open order changes operator, picked quantities move to another open order of the same warehouse and material (no balance changes), and picked quantities return to a location of the warehouse through an `IN_RETURN` movement. Each action needs a note, is guarded by `orders.reassign`, and is audited with its source, target and quantity; `OrderReassignmentTest` and `AuditCoverageTest` cover them. The `orders.*` permissions are seeded. The order query takes one status code or several, so the app asks for the open orders of a warehouse in one call. F5-18 is done in the app: the orders module holds "Mis órdenes", the open orders assigned to the signed-in operator, and each one opens the pick form — scan the material, read the balance of every location that holds it, scan or tap the location, and enter the quantity, which the app keeps at or below that balance; the API writes the `OUT` movement and moves the order. `OrderPickViewModelTest` and the `android-order-pick.test.mjs` device suite (on a phone) and `PickUITests` (on the iOS simulator) cover it. F5-18 also decided the stock question (stock.md section 4): `stock.view` is seeded to `OFFICE` with `ALL` and to `WAREHOUSE_MANAGER` and `WAREHOUSE_OPERATOR` with `AREA`, so the pick screen can show those balances. F5-13 is done in the API: `order_alerts` keeps one row per person, order and day, and `GET /orders/alerts` answers the alerts of the signed-in person — the day's check runs first, so the app and the web always show today's alerts. Every open order whose due date has come, or is tomorrow, tells the assigned operator and the people who validate orders in its warehouse; from the due date on it tells the creator too. A unique key on the three columns is what stops the same day from being raised twice, and a validated or cancelled order raises none. `OrderAlertsTest` and `OrderAlertRulesTest` cover it. Next: the PDF (F5-14), the web screens (F5-16, F5-17) and the rest of the app (F5-19).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F5-01 | Order header | API | F4-02, F2-10 | |
| F5-02 | Lines and availability | API | F5-01 | |
| F5-03 | Reservations under lock | API | F5-02 | |
| F5-04 | Assignment to an operator | API | F5-01, F1-11 | |
| F5-05 | Picks | API | F5-03, F5-04 | |
| F5-06 | Statuses from picking to fulfilled | API | F5-05 | |
| F5-07 | Validation and lock | API | F5-06 | |
| F5-08 | Validation mismatch and accepting a difference | API | F5-07 | |
| F5-09 | Cancellation with no picks | API | F5-03 | |
| F5-10 | Reassignment: change operator | API | F5-05 | |
| F5-11 | Reassignment: move picks to another order | API | F5-05 | |
| F5-12 | Reassignment: return to stock | API | F5-05, F4-01 | |
| F5-13 | Due date alerts | API | F5-01 | |
| F5-14 | Comprobante PDF | API | F5-07 | |
| F5-15 | Order creation is idempotent | API | F5-01, F0-07 | |
| F5-16 | Order screens | Web | F5-01, F5-04 | |
| F5-17 | Order actions and PDF download | Web | F5-07 to F5-14 | |
| F5-18 | App: pick | App | F5-05, F3-13 | |
| F5-19 | App: reassignment and alerts | App | F5-10, F5-11, F5-13 | |

## F5-01 · Order header · API · after F4-02, F2-10
- Unit (red): the order number is 1 to 30 characters of letters, digits, `-` and `/`, unique per warehouse; the due date is a valid date and not before today at creation; the customer must be active.
- E2E (red): create an order; a repeated number in the same warehouse returns `DUPLICATE_VALUE`; the same number in another warehouse is accepted; an inactive customer returns an error.
- Accept: `ADMIN` and `OFFICE` can create; `WAREHOUSE_MANAGER` cannot.

## F5-02 · Lines and availability · API · after F5-01
- Unit (red): `available = balance in warehouse − reserved by open orders`; a line above available is rejected with `STOCK_INSUFFICIENT`; one line per material per order.
- E2E (red): asking 150 when 140 are available returns the error and saves nothing; asking 100 saves the order and reserves 100.
- Accept: the message, in the user's locale, says how much is available.

## F5-03 · Reservations under lock · API · after F5-02
- Unit (red): the reservation is taken inside the lock on product and warehouse.
- E2E (red): two orders created in parallel for the last unit: exactly one succeeds, the other gets `STOCK_INSUFFICIENT`. Tested on PostgreSQL.
- Accept: no reservation exceeds the available stock.

## F5-04 · Assignment to an operator · API · after F5-01, F1-11
- Unit (red): an assignment goes only to an active operator of the order's warehouse.
- E2E (red): `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouse) assign; the operator sees the order in their list; an operator of another warehouse gets `404`.
- Accept: assigning changes no stock.

## F5-05 · Picks · API · after F5-03, F5-04
- Unit (red): a pick cannot exceed the location's balance or the line's remaining quantity; each pick reduces the reservation and the balance by the same amount.
- E2E (red): picking 80 from `K05A` creates one `OUT` movement and leaves `K05A` at 0; picking 90 from a location with 80 returns the error; picking from another warehouse's location returns `LOCATION_NOT_IN_WAREHOUSE`.
- Accept: the operator chooses the location; the system never suggests one.

## F5-06 · Statuses from picking to fulfilled · API · after F5-05
- Unit (red): the transitions `CREATED → FULFILLING → FULFILLED` are allowed; any other is rejected.
- E2E (red): the first pick moves the order to `FULFILLING`; the last pick moves it to `FULFILLED`.
- Accept: each change is audited with who, when and the previous status.

## F5-07 · Validation and lock · API · after F5-06
- Unit (red): validation is allowed only from `FULFILLED`; a validated order rejects every change.
- E2E (red): `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouse) validate; any change to a validated order returns `ORDER_LOCKED`; a user without `orders.validate` gets `403`.
- Accept: validation requires no note when the picks match.

## F5-08 · Validation mismatch and accepting a difference · API · after F5-07
- Unit (red): a mismatch returns the order to `FULFILLING`; accepting a difference requires a note and `orders.accept_difference`, and adjusts the line to the picked quantity.
- E2E (red): a mismatch returns the order to `FULFILLING` and writes an audit entry with the note; `OFFICE` cannot accept a difference (`403`); `WAREHOUSE_MANAGER` can.
- Accept: the line after acceptance equals the picked quantity.

## F5-09 · Cancellation with no picks · API · after F5-03
- Unit (red): cancellation is allowed only with no picks, and releases the reservation.
- E2E (red): cancelling an order with no picks releases its reservation, and the stock is available to another order; cancelling an order with picks returns `409`.
- Accept: `ADMIN`, `OFFICE` and `WAREHOUSE_MANAGER` (in its warehouse) can cancel; `WAREHOUSE_OPERATOR` gets `403`.

## F5-10 · Reassignment: change operator · API · after F5-05
- Unit (red): the operator changes only on `CREATED` or `FULFILLING` orders, and keeps the picks and reservations.
- E2E (red): the new operator sees the picks already made; the audit entry records from, to and the note.
- Accept: no balance changes.

## F5-11 · Reassignment: move picks to another order · API · after F5-05
- Unit (red): the moved quantity is at most the picked quantity and at most the target's remaining need; the target must be open and of the same warehouse and material.
- E2E (red): 20 picked units move from `ORD-0001` to `ORD-0002`, which needs 20; `ORD-0001` then has no picks and can be cancelled; no balance changes.
- Accept: the source's reservation for those units is released and the target's is reduced.

## F5-12 · Reassignment: return to stock · API · after F5-05, F4-01
- Unit (red): a return creates an `IN_RETURN` movement into the chosen location; a return to a location of another warehouse is rejected.
- E2E (red): returning 20 units to `K05B` raises its balance from 40 to 60; the order's picks go down by 20.
- Accept: this is the only reassignment that changes a balance.

## F5-13 · Due date alerts · API · after F5-01
- Unit (red): with a fixed clock, an open order raises an alert one day before its due date, on the due date, and daily after it; an order that is `VALIDATED` or `CANCELLED` raises none; the same day is never sent twice.
- E2E (red): with the clock at the day before the due date, the assigned operator, the manager and the creator each receive one alert; the next run the same day sends nothing.
- Accept: the alert channels are the app and the web. Email is proposed and is not in this task.

## F5-14 · Comprobante PDF · API · after F5-07
- Unit (red): the document data contains every field in section 10 of the order spec.
- E2E (red): validating an order stores one PDF; downloading twice returns the same bytes; a validated order's PDF does not change when a customer's name changes later.
- Accept: the PDF is stored in private storage, never at a public URL.

## F5-15 · Order creation is idempotent · API · after F5-01, F0-07
- Unit (red): the same key and request return the stored order.
- E2E (red): the same key twice creates one order; a different request with that key returns `IDEMPOTENCY_KEY_REUSED`.
- Accept: the key rule is the one in [api.md](../../architecture/api.md) section 2.

## F5-16 · Order screens · Web · after F5-01, F5-04
- Unit (red): the line form shows the available quantity and blocks a quantity above it before submitting.
- E2E (red): `OFFICE` creates an order with two lines, assigns it, and sees it in the list.
- Accept: a user without `orders.create` does not see the create button.

## F5-17 · Order actions and PDF download · Web · after F5-07 to F5-14
- Unit (red): each action button shows only when the user's permissions allow it and the order's status allows it.
- E2E (red): Playwright validates an order, reassigns picks, cancels one with no picks, and downloads the PDF.
- Accept: a locked order shows no action except the PDF download.

## F5-18 · App: pick · App · after F5-05, F3-13
- Unit (red): the pick view model keeps the quantity at or below the location balance shown by the API.
- E2E (red): on Android and on iOS, the operator scans a material, scans a location's code, enters a quantity, and the order moves to `FULFILLING`.
- Accept: a scanned material not on the order shows `PRODUCT_NOT_IN_ORDER`.

## F5-19 · App: reassignment and alerts · App · after F5-10, F5-11, F5-13
- Unit (red): the reassignment view model lists only open orders of the same warehouse that need the scanned material.
- E2E (red): on Android and on iOS, the operator sees an alert for an order due tomorrow, opens it, and reassigns picked quantities to another order.
- Accept: the app shows the alert list in the user's locale.
