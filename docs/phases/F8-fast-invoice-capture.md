# F8: Fast invoice capture

**Goal:** creating a supplier invoice and capturing its materials takes seconds, with the keyboard and without leaving the
list.

**Status:** Done, 2026-10-15. F8-01 to F8-04 in the API, F8-05 to F8-08 in the web; the app does not change.
**Revised, 2026-10-31:** closing by hand is gone (`INVOICE_CLOSED` no longer exists): an invoice completes only when
everything is received and placed, and an invoice that will not arrive is cancelled with a note. The receiving flow was
redesigned in the same change (see [plan-mejora-stockflow.md](../plan-mejora-stockflow.md) and
[material-receiving.md](../modules/material-receiving.md) section 8).

**Depends on:** F4 (invoices, counts, placement) and F5 (the screens it follows). It is a redesign of the invoice screens
of F4, decided by the owner on 2026-10-15 from the mock `stockflow-facturas-mock.html`.

**Permissions added:** none. Creating and cancelling an invoice need `invoices.create`, which the office and the manager
already hold; assigning a whole invoice needs `receipts.assign`.

## Deliverables

### API

- **A future invoice date is allowed.** The rule that no invoice may be dated after today (`DATE_IN_FUTURE`) is dropped
  for invoices: the office records what the supplier's paper says.
- **One search box.** `POST /invoices/query` takes `search`: the invoice number, the supplier's code or its name. It
  combines with the other filters with AND, and each of the three with OR among themselves.
- **Cancelling instead of closing.** `POST /invoices/{id}/closure` no longer finishes an invoice: `CLOSED` is reached
  only when the operators place everything. An invoice that will not be received is cancelled with a note
  (`POST /invoices/{id}/cancellation`, audited as `INVOICE_CANCELLED`), while nothing has been counted.
- **Assigning a whole invoice.** `PUT /invoices/{id}/assignments` gives every line (or the `lineIds` it names) to one
  operator in a single action, which is the common case at the counter.
- **The contact is gone.** `contactId` leaves the create request and the invoice view. The column stays in the database
  with what it holds, and the API stops reading and writing it.

### Web

- **The list**: one search box (number or supplier) and five status chips that are also work queues (`Todas`,
  `En recepción`, `Por ubicar`, `Completadas`, `Canceladas`), combined with AND; the row opens the detail; the empty
  state says so; pagination, sorting and the table/cards switch stay.
- **New invoice** is a modal over the list with four fields (number, supplier, date, warehouse), the focus on the number,
  the last supplier and warehouse remembered for the session, `Guardar` (back to the list, with an aviso) and
  `Guardar y agregar materiales` (to the detail, with the focus on the capture bar). `N` opens it, `Esc` closes it, and
  the form does not close while it is saving.
- **The detail**: a quick capture bar (material, quantity, `Agregar`) above the lines. `Enter` adds and puts the focus
  back on the material; a missing field takes the focus instead of an error; a material that is already a line adds to it
  (`PATCH /invoices/{id}/lines/{lineId}`). The bar understands a barcode as well as a code or a name. `Quitar` removes a
  line while nothing of it is counted. `Asignar toda la factura` gives every line to one operator, and `Cancelar factura`
  asks for a reason. The table shows invoiced, expected, counted, accepted, placed, remaining and the locations of each
  line, with how many lines are counted and placed.

### App

- None. The operators' reception keeps its own screens.

## Acceptance criteria

1. An invoice is created with the four fields and `menos de 10 segundos` with the keyboard only, and both buttons do what
   the mock says.
2. Five materials are captured one after another without touching the mouse, and a repeated material adds to its line.
3. The list filters by text and by status at the same time.
4. An invoice that nothing has counted can be cancelled with a reason, and it can no longer be closed by hand.
5. Cancelling leaves the invoice `CANCELLED` and the audit log holds `INVOICE_CANCELLED` with the note, who and when.
6. An invoice with a counted share cannot be cancelled (`INVOICE_STATUS_INVALID`), and the screen says why.
7. A future date is accepted, and the date the list shows is the one that was written.
8. An invoice is `CLOSED` only when every share is placed; until then `POST /invoices/{id}/closure` is refused
   (`INVOICE_LOCKED` when it is already terminal, `INVOICE_STATUS_INVALID` otherwise).
9. `Asignar toda la factura` gives every line to one operator in one action, and replaces the previous split.

## Open questions

- **Remembering the last supplier and warehouse**: for the session only, or per person in the database? The mock asks for
  the session, which is what this phase does.
- **Editing an invoice** (supplier, warehouse or date after creating it) stays out of scope, as in the mock.

## Decided

- **Contact** (2026-10-15): it is not captured anywhere any more, but the data that exists is not deleted.
- **Closing by hand** (2026-10-15): a new action of the API, audited, allowed only when nothing has been counted, so the
  fast flow can close an invoice that never had operators.
- **Future dates** (2026-10-15): allowed for invoices.
