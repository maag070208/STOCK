# F9: every screen, the same design

**Status:** Draft, 2026-10-15. Depends on F8 (the invoice screens, which are the reference). Runs before F7, so the
hardening covers the screens that ship.

## What this phase is

The invoice screens of F8 are how a screen of this system is built:
[the anatomy of a screen](../architecture/screens.md). The rest of the pages grew one by one, each with its own order, and
the difference shows: some records carry a row of tall indicator cards where the invoice carries a strip, some have no
state next to their action, and a few keep two tables for what is one thing.

This phase takes **that anatomy, without changes**, to every page of the web. It is not a redesign: it is the invoice
design, applied.

## What is already true (audited 2026-10-15)

| Rule | Screens |
|---|---|
| Breadcrumbs that start at Home | **28 of 28** |
| The layout's room at the end (`.sf:pb-12`, 48 px measured) | 28 of 28 |
| Only the kit builds the screen (the lint fails otherwise) | 28 of 28 |
| The table's standard props (25 / 25,50,100 / fixed / compact / virtualized / 400 / 50) | all tables |
| The record's facts in one strip (`FactStrip`) | **4 of 6** records: invoice, order, material, partner. Missing: warehouse, label template |
| The title is the code and the name is the description | 5 of 6 records; **`LabelTemplatesPage`** keeps "code · name" in the title |
| The state and its action together in the header | 4 of 6 records; the warehouse and the label template act from the body |
| Chips of the state in the URL (lists) | only the invoice list |

## Acceptance criteria

1. Every page follows the anatomy of its shape (list, record, report, console, form) and passes the checklist of section 7
   of [screens.md](../architecture/screens.md).
2. The screens that list what grows without limit are searched by their columns.
3. A record's facts are one `FactStrip`; no screen shows a row of `ITStatCard`s as its header (the indicator cards stay
   where they are a result, such as the import's summary).
4. Every record whose state changes has that action in the header, next to its badge.
5. The lists that filter by state carry it in the URL, and a reload keeps the list.
6. The e2e suite passes with the new screens, and each converted screen has at least one assertion on the shape it now has
   (the strip, the header action, the chips).
7. The e2e suite leaves the database as it found it.

## Tasks

| ID | Task | Screens | After |
|---|---|---|---|
| F9-01 | The anatomy document and this plan | — | — |
| F9-02 | Record: warehouse (strip, title/description, the rack action in the header) | `WarehousePage` | F9-01 |
| F9-03 | Record: label template (title is the key, the state and Guardar in the header) | `LabelTemplatePage` | F9-01 |
| F9-04 | List: the state in the URL, as the invoice list does | `OrdersPage`, `ProductsPage` | F9-01 |
| F9-05 | List: the search of the columns that grow without limit | `ProductsPage`, `WarehousesPage` | F9-04 |
| F9-06 | Consoles: the access console's sections, each with its own table and its unsaved-changes notice | `AccessPage` | F9-01 |
| F9-07 | Report: the filters of the last request, kept for the export, and the totals in the strip | the five reports | F9-01 |
| F9-08 | Form: the same modal frame everywhere (one action, short labels, the focus kept inside) | `PartnerFormPage`, `OrderFormPage`, `ProductImportPage`, `OpeningInventoryPage` | F9-03 |
| F9-09 | A check that a screen cannot drift: the unit test of the anatomy (breadcrumbs, strip, header action, table props) | every page | F9-02…F9-08 |
| F9-10 | The e2e of each converted screen, with the shape asserted | every page | F9-02…F9-08 |

## Order of the work

1. **F9-02 and F9-03 first**: the two records that are furthest from the anatomy, so the pattern is settled on real
   screens before touching twelve lists.
2. Then the lists (F9-04, F9-05) and the consoles (F9-06).
3. Then the reports (F9-07) and the forms (F9-08), which are the most mechanical.
4. F9-09 last but before closing: a rule that is not checked is a rule that drifts back.
5. F9-10 travels with each task, not after them: a screen is converted when its test says so.

## Risks

| Risk | What we do |
|---|---|
| A screen loses something the owner uses (a filter, a shortcut) while being "ordered" | Every conversion is reviewed against the previous screenshot, and the e2e of the screen is kept passing before and after |
| The strip hides information that used to be a card | The strip holds four values; a fifth fact goes in a card of its own, not in a smaller font |
| The tests of the design (tokens, shell) are not the screen's tests | The design suite stays as it is; each screen's own test asserts its shape |
