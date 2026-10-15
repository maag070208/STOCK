# F8 tasks: Fast invoice capture

Phase: [F8-fast-invoice-capture.md](../F8-fast-invoice-capture.md).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F8-01 | A future invoice date is allowed | API | F4-10 | |
| F8-02 | One search box in the invoice list | API | F4-10 | |
| F8-03 | Closing an invoice by hand | API | F4-18 | |
| F8-04 | The contact leaves the invoice | API | F8-01 | |
| F8-05 | The list: one search box and status chips | Web | F8-02 | |
| F8-06 | New invoice in a modal | Web | F8-04 | |
| F8-07 | The detail: quick capture and closing | Web | F8-03, F8-04 | |
| F8-08 | Keyboard and accessibility | Web | F8-05 to F8-07 | |

## F8-01 · A future invoice date is allowed · API
- Unit (red): the date rule of an invoice accepts tomorrow.
- E2E (red): an invoice dated tomorrow is created and read back with that date.
- Accept: nothing else about the date changes: it is still required, and it is still a date.

## F8-02 · One search box in the invoice list · API
- Unit (red): the search filter matches the number, the supplier's code and the supplier's name, and it combines with the
  status filter with AND.
- E2E (red): searching a supplier's name lists its invoices and nothing else; searching half a number lists that one.
- Accept: a filter outside the contract is still rejected with `UNKNOWN_FIELD`.

## F8-03 · Closing an invoice by hand · API
- Unit (red): an invoice with no counted share can be closed; one with a counted share cannot.
- E2E (red): an invoice with two lines and no operators closes, its status is `CLOSED`, and the audit log holds
  `INVOICE_CLOSED`; closing an invoice that is already closed answers `INVOICE_STATUS_INVALID`.
- Accept: closing needs `invoices.create` and the warehouse scope; an invoice of another warehouse answers `404`.

## F8-04 · The contact leaves the invoice · API
- Unit (red): the create request has no contact, and the invoice view has no contact.
- E2E (red): an invoice created without a contact is read back without one, and the supplier's contact is not required.
- Accept: what is already stored in `contact_id` is untouched.

**Progress, 2026-10-15:** F8-01 to F8-04 are done in the API, test first: the future date is accepted
(`InvoicesEndpointTest`), the search reads the number, the supplier's code and its name, the closure closes only what
nothing has counted (`InvoiceRulesTest` for the rule, `INVOICE_WITHOUT_LINES` and `INVOICE_STATUS_INVALID` in the e2e,
`AuditCoverageTest` for `INVOICE_CLOSED`), and the contact left the contract — sending it is `UNKNOWN_FIELD` — and the web
form and detail with it.

## F8-05 · The list: one search box and status chips · Web
- Unit (red): the search box and the chip become the filters of the request, combined.
- E2E (red): Playwright types a supplier's name, gets its invoices, switches to `Cerradas`, and gets only the closed one.
- Accept: the row opens the detail, with the keyboard too.

## F8-06 · New invoice in a modal · Web
- Unit (red): the modal asks for four fields and nothing else; the primary action navigates to the detail.
- E2E (red): `N` opens it, the focus is on the number, saving returns to the list with the aviso, and
  `Guardar y agregar materiales` lands on the detail with the focus on the material.
- Accept: `Esc` closes it, and it does not close while it is saving.

## F8-07 · The detail: quick capture and closing · Web
- Unit (red): a repeated material adds to the line that exists instead of creating another.
- E2E (red): five materials are captured with `Enter` only, one of them repeated, and the invoice closes with the button.
- Accept: without lines the button is not there, and a refused close shows the API's reason.

## F8-08 · Keyboard and accessibility · Web
- Unit (red): the modal keeps the focus inside while it is open and returns it to the button that opened it.
- E2E (red): the whole flow runs with the keyboard, and the dialog has its `role`, its `aria-modal` and its label.
- Accept: `prefers-reduced-motion` is respected and the controls have visible labels.

**Progress, 2026-10-15:** F8-05 to F8-08 are done in the web: the list with one box and the chips (the chip lives in the
address), the modal with the four fields and the two ways out, the detail with the quick capture that sums into an
existing line and the hand closing, and the keyboard and screen-reader work (focus trapped and given back, `role`,
`aria-modal`, reduced motion). `tests/unit/invoice-capture.spec.ts` (8 cases) and `tests/e2e/invoice-capture.spec.ts`
(6 cases) prove them, `web/tests/e2e/invoices.spec.ts` was rewritten for the modal (its two cases now assign from the
detail), and the whole web suite passes: 74 unit, `tsc`, `lint` and the e2e with the two pre-existing failures of
`design.spec.ts` only. The list asks the status as a list of codes, which is the one thing F8-05 needed from the API and
F8-02 did not have: the invoice filter now takes one code or several, as the orders one does.
