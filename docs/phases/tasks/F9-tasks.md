# F9 tasks: every screen, the same design

Phase: [F9-screen-design.md](../F9-screen-design.md). The anatomy every task applies:
[screens.md](../../architecture/screens.md).

| ID | Task | Area | Screens | After | Owner |
|---|---|---|---|---|---|
| F9-01 | The anatomy of a screen, and the plan | Docs | — | — | |
| F9-02 | The warehouse record: strip, title and description, the rack action in the header | Web | `WarehousePage` | F9-01 | |
| F9-03 | The label template record: the key as the title, the state and Guardar in the header | Web | `LabelTemplatePage` | F9-01 | |
| F9-04 | The state of a list in the URL | Web | `OrdersPage`, `ProductsPage` | F9-01 | |
| F9-05 | The columns that grow without limit are searched | Web | `ProductsPage`, `WarehousesPage` | F9-04 | |
| F9-06 | The access console: one table per section, and what is unsaved is said | Web | `AccessPage` | F9-01 | |
| F9-07 | The reports: the filters of the last request for the export, the totals in the strip | Web | the five report pages | F9-01 | |
| F9-08 | The forms and modals: one frame everywhere | Web | `PartnerFormPage`, `OrderFormPage`, `ProductImportPage`, `OpeningInventoryPage` | F9-03 | |
| F9-09 | A unit test of the anatomy, so no screen drifts | Web | every page | F9-02…F9-08 | |
| F9-10 | The e2e of each converted screen, with its shape asserted | Web | every page | F9-02…F9-08 | |
