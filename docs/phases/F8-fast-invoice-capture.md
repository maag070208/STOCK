# F8: Fast invoice capture

**Goal:** creating a supplier invoice and capturing its materials takes seconds, with the keyboard and without leaving the
list.

**Depends on:** F4 (invoices, counts, placement) and F5 (the screens it follows). It is a redesign of the invoice screens
of F4, decided by the owner on 2026-10-15 from the mock `stockflow-facturas-mock.html`; the reception flow of the
operators (`/receipts/mine`, counts, adjustments, placement) does not change.

**Permissions added:** none. Creating and closing an invoice need `invoices.create`, which the office and the manager
already hold.

## Deliverables

### API

- **A future invoice date is allowed.** The rule that no invoice may be dated after today (`DATE_IN_FUTURE`) is dropped
  for invoices: the office records what the supplier's paper says.
- **One search box.** `POST /invoices/query` takes `search`: the invoice number, the supplier's code or its name. It
  combines with the other filters with AND, and each of the three with OR among themselves.
- **Closing by hand.** `POST /invoices/{id}/closure` closes an invoice that is `OPEN` or `PENDING` (nothing counted yet)
  and has at least one line. It is audited as `INVOICE_CLOSED`. An invoice already in `IN_ASSIGNMENT`, or `CLOSED`, is
  refused: the operators' work is not thrown away.
- **The contact is gone.** `contactId` leaves the create request and the invoice view. The column stays in the database
  with what it holds, and the API stops reading and writing it.

### Web

- **The list**: one search box (number or supplier) and three status chips (`Todas`, `Abiertas`, `Cerradas`), combined
  with AND; the row opens the detail; the empty state says so; pagination, sorting and the table/cards switch stay.
- **New invoice** is a modal over the list with four fields (number, supplier, date, warehouse), the focus on the number,
  the last supplier and warehouse remembered for the session, `Guardar` (back to the list, with an aviso) and
  `Guardar y agregar materiales` (to the detail, with the focus on the capture bar). `N` opens it, `Esc` closes it, and
  the form does not close while it is saving.
- **The detail**: a quick capture bar (material, quantity, `Agregar`) above the lines. `Enter` adds and puts the focus
  back on the material; a missing field takes the focus instead of an error; a material that is already a line adds to it
  (`PATCH /invoices/{id}/lines/{lineId}`). `Quitar` removes a line, and `Cerrar factura` appears only while the invoice is
  open and has lines.

### App

- None. The operators' reception keeps its own screens.

## Acceptance criteria

1. An invoice is created with the four fields and `menos de 10 segundos` with the keyboard only, and both buttons do what
   the mock says.
2. Five materials are captured one after another without touching the mouse, and a repeated material adds to its line.
3. The list filters by text and by status at the same time.
4. An invoice without lines cannot be closed, and the button is not there.
5. Closing leaves the invoice `CLOSED` and the audit log holds `INVOICE_CLOSED` with who and when.
6. An invoice with a counted share cannot be closed by hand (`INVOICE_STATUS_INVALID`), and the screen says why.
7. A future date is accepted, and the date the list shows is the one that was written.

## Open questions

- **Remembering the last supplier and warehouse**: for the session only, or per person in the database? The mock asks for
  the session, which is what this phase does.
- **Editing an invoice** (supplier, warehouse or date after creating it) stays out of scope, as in the mock.

## Decided

- **Contact** (2026-10-15): it is not captured anywhere any more, but the data that exists is not deleted.
- **Closing by hand** (2026-10-15): a new action of the API, audited, allowed only when nothing has been counted, so the
  fast flow can close an invoice that never had operators.
- **Future dates** (2026-10-15): allowed for invoices.
