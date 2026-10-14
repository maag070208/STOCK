# F4 tasks: Stock and material entry

Phase: [F4-stock-and-entries.md](../F4-stock-and-entries.md).

**Progress, 2026-10-14:** F4-01 to F4-18 are done in the API (contracts in [stock.md](../../modules/stock.md) and [material-entry.md](../../modules/material-entry.md) section 9). Criteria 1 and 2 were checked on the real file; criteria 3 to 13 have their tests (`ReceivingEndpointTest`, `InvoicesEndpointTest`, `StockServiceTest`). Who reads stock besides `ADMIN` is an open question (stock.md section 5). F4-19 to F4-21 are done on the web: stock by location and by material, the opening inventory screen, invoices with both paths to assignment, the discrepancies screen, and the staff template on each warehouse. Office picks the invoice's warehouse from `GET /invoices/warehouse-options`, since it has no `locations.view`. Next: the app (F4-22, F4-23), which needs the barcode lookup of F3 (F3-13).

From F2, waiting for stock and invoices: F2 criterion 3 (a location with stock cannot be deactivated) is F4-05, with rack deactivation and the refusal of placements into `TEMPORARY`; F2 criterion 10 (a deactivated supplier is not offered for a new invoice) is F4-07.

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F4-01 | Movements, append-only | API | F2-03 | |
| F4-02 | Balances with lock and non-negative check | API | F4-01 | |
| F4-03 | Opening inventory: dry run | API | F4-02, F2-08 | |
| F4-04 | Opening inventory: confirm and control total | API | F4-03 | |
| F4-05 | Deactivation rule with stock | API | F4-02, F2-03 | |
| F4-06 | Stock queries | API | F4-02 | |
| F4-07 | Invoice header | API | F2-09, F2-01 | |
| F4-08 | Invoice lines | API | F4-07, F2-06 | |
| F4-09 | Invoice creation is idempotent | API | F4-07, F0-07 | |
| F4-10 | Invoice visibility by warehouse | API | F4-07, F1-11 | |
| F4-11 | Staff templates | API | F2-01, F1-11 | |
| F4-12 | Assignments: two paths | API | F4-08, F4-11 | |
| F4-13 | Assignments: quantities add up | API | F4-12 | |
| F4-14 | Counts | API | F4-13 | |
| F4-15 | Adjustment to the counted quantity | API | F4-14 | |
| F4-16 | Invoice status machine | API | F4-15, F1-16 | |
| F4-17 | Placement | API | F4-16, F4-02 | |
| F4-18 | Close the invoice | API | F4-17 | |
| F4-19 | Stock screens | Web | F4-06 | |
| F4-20 | Invoice screens | Web | F4-09, F4-12 | |
| F4-21 | Discrepancy screen | Web | F4-15 | |
| F4-22 | App: assignments and count | App | F4-14, F3-13 | |
| F4-23 | App: placement | App | F4-17, F3-13 | |

## F4-01 · Movements, append-only · API · after F2-03
- Unit (red): a movement has warehouse, location, product, quantity, user, time, reason and source; a movement with a zero quantity is rejected.
- E2E (red): a movement can be created; the API offers no update or delete (`405`).
- Accept: the types are `OPENING`, `RECEIPT`, `OUT`, `IN_RETURN`.

## F4-02 · Balances with lock and non-negative check · API · after F4-01
- Unit (red): applying an `OUT` above the balance is rejected; applying a `RECEIPT` raises the balance.
- E2E (red): a database check rejects a negative balance even when the service is bypassed in the test.
- Accept: the balance row is locked for update before it is read.

## F4-03 · Opening inventory: dry run · API · after F4-02, F2-08
- Unit (red): the total of the dry run is the sum of the balances read.
- E2E (red): the dry run on the fixture reports the products and the total, and writes nothing.
- Accept: the fixture total matches its own control total.

## F4-04 · Opening inventory: confirm and control total · API · after F4-03
- Unit (red): the confirm rejects a total that differs from the control total.
- E2E (red): confirming writes `OPENING` movements into `TEMPORARY` of `CENTRAL`; running again writes nothing; a changed total is rejected.
- Accept: with the real file, the control total is 10,914,130 (checked by hand once, recorded).

## F4-05 · Deactivation rule with stock · API · after F4-02, F2-03
- Unit (red): a location with a positive balance cannot be deactivated; a rack with one non-empty level cannot be deactivated.
- E2E (red): deactivating a location with stock returns `409`; after the stock leaves, it succeeds.
- Accept: this rule was deferred from F2 because it needs balances.

## F4-06 · Stock queries · API · after F4-02
- Unit (red): the total of a query equals the sum of the balances it returns.
- E2E (red): balances by location, by product, and by warehouse; a user outside the scope gets only their rows.
- Accept: the response uses the table contract.

## F4-07 · Invoice header · API · after F2-09, F2-01
- Unit (red): the invoice number is unique per supplier; the date is a valid date; the warehouse is active.
- E2E (red): create an invoice; a second one with the same number for the same supplier returns `DUPLICATE_VALUE`; the same number for another supplier is accepted.
- Accept: an inactive supplier is not accepted (decided here, not in F2).

## F4-08 · Invoice lines · API · after F4-07, F2-06
- Unit (red): the unit is filled from the product; a quantity of zero or below is rejected; a quantity with more decimals than the unit allows is rejected; a product appears once per invoice.
- E2E (red): adding a product fills its unit; adding it again returns the error.
- Accept: the unit cannot be changed on the line.

## F4-09 · Invoice creation is idempotent · API · after F4-07, F0-07
- Unit (red): the stored response is returned for the same key and request.
- E2E (red): the same key twice creates one invoice; a different request with the same key returns `IDEMPOTENCY_KEY_REUSED`.
- Accept: the key rule is the one in [api.md](../../architecture/api.md) section 2.

## F4-10 · Invoice visibility by warehouse · API · after F4-07, F1-11
- Unit (red): an invoice is visible when its warehouse is in the user's scope.
- E2E (red): a `WAREHOUSE_MANAGER` of another warehouse gets `404` on the invoice.
- Accept: `OFFICE` sees every warehouse.

## F4-11 · Staff templates · API · after F2-01, F1-11
- Unit (red): a template lists only active operators of its warehouse.
- E2E (red): the manager of the warehouse edits its template; a manager of another warehouse gets `403`.
- Accept: `staff_templates.manage` is scoped to the warehouse.

## F4-12 · Assignments: two paths · API · after F4-08, F4-11
- Unit (red): an assignment gives one line to one operator with an expected quantity.
- E2E (red): path A: save the invoice, then assign. Path B: assign and save once. Both end with the same stored assignments.
- Accept: the invoice goes to `PENDING` when the first assignment exists.

## F4-13 · Assignments: quantities add up · API · after F4-12
- Unit (red): the expected quantities of a line must add up to the line quantity.
- E2E (red): a line of 100 split as 60 and 30 is rejected; split as 60 and 40 is accepted.
- Accept: the rule is checked on every change to the assignments.

## F4-14 · Counts · API · after F4-13
- Unit (red): a count equal to the expected quantity is a match; a different one is a discrepancy with the difference.
- E2E (red): the operator counts 500 for an expected 500 (match); 80 for an expected 100 (discrepancy with -20).
- Accept: the operator counts only their own assignments (`404` otherwise).

## F4-15 · Adjustment to the counted quantity · API · after F4-14
- Unit (red): the adjustment sets the expected quantity to the counted one and changes the line by the same difference; a note is required.
- E2E (red): nails 100 → 80, the line becomes 80; wire 120 → 180, the line becomes 180; the split example (line 100 = 60 + 40, 60 counted at 50) ends with the line at 90.
- Accept: each adjustment is audited with the original and adjusted values.

## F4-16 · Invoice status machine · API · after F4-15, F1-16
- Unit (red): every transition in [material-receiving.md](../../modules/material-receiving.md) section 8 is allowed, and every other one is rejected.
- E2E (red): the status moves `OPEN` → `PENDING` → `IN_ASSIGNMENT` as counts and adjustments complete.
- Accept: `IN_ASSIGNMENT` is not reached while a discrepancy is open.

## F4-17 · Placement · API · after F4-16, F4-02
- Unit (red): the placed quantities must add up to the counted quantity; a placement into a location of another warehouse is rejected; a placement into `TEMPORARY` is rejected.
- E2E (red): 500 screws placed as 300 in `K05A` and 200 in `K05B` create two `RECEIPT` movements and raise both balances.
- Accept: two placements into the same location in parallel leave the correct balance, tested against PostgreSQL.

## F4-18 · Close the invoice · API · after F4-17
- Unit (red): the invoice closes only when every assignment is placed.
- E2E (red): placing the last assignment moves the invoice to `CLOSED`; placing all but one does not.
- Accept: a closed invoice cannot be changed.

## F4-19 · Stock screens · Web · after F4-06
- Unit (red): the stock table formats the quantities with the unit's decimals.
- E2E (red): Playwright lists the balances of a location, and the list follows the user's scope.
- Accept: the screens are read-only.

## F4-20 · Invoice screens · Web · after F4-09, F4-12
- Unit (red): the assignment form blocks a split whose total differs from the line before submitting.
- E2E (red): `OFFICE` creates an invoice and assigns it by path A; then by path B; the detail shows the status.
- Accept: a supplier outside the active list cannot be chosen.

## F4-21 · Discrepancy screen · Web · after F4-15
- Unit (red): the adjust action asks for a note before enabling the button.
- E2E (red): `WAREHOUSE_MANAGER` adjusts a discrepancy in its warehouse; `OFFICE` adjusts one in any warehouse; a user without `discrepancies.resolve` does not see the action.
- Accept: each adjustment shows its audit entry.

## F4-22 · App: assignments and count · App · after F4-14, F3-13
- Unit (red): the count view model rejects a negative quantity and sends the count with the assignment id.
- E2E (red): on Android and on iOS, a `WAREHOUSE_OPERATOR` scans a material, sees its assignment, and counts it.
- Accept: a scan of a material not in an open assignment shows `PRODUCT_NOT_IN_ASSIGNMENT`.

## F4-23 · App: placement · App · after F4-17, F3-13
- Unit (red): the placement view model keeps the placed total equal to the counted quantity.
- E2E (red): on Android and on iOS, the operator places a counted assignment into two locations by scanning each location's code.
- Accept: a location scanned from another warehouse shows `LOCATION_NOT_IN_WAREHOUSE`.
