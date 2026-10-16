# Phases

**Status:** Draft, 2026-10-13. Each phase is a deliverable that works end to end: API, web and app, in the parts that the phase touches. A phase is done when its acceptance criteria pass, not when its code is written.

## Order

```
F0 Foundations
 └── F1 Access and catalogs
      └── F2 Master data
           ├── F3 Labels, printers and lookup
           └── F4 Stock and material entry
                ├── F5 Orders (exit)
                └── F6 Reports and kardex
                     ├── F7 Hardening and deployment
                     ├── F8 Fast invoice capture
                     └── F9 Every screen, the same design
```

- F3 and F4 can run in parallel once F2 is done.
- F5 and F6 can run in parallel once F4 is done.
- F8 redesigns the invoice screens of F4 and is done before F7, so the hardening covers the screens that ship.
- F9 takes the design F8 settled ([screens.md](../architecture/screens.md)) to every other page, and runs before F7:
  the hardening covers the screens that ship, not the ones that are about to change.

## Phases

| Phase | Name | Delivers |
|---|---|---|
| [F0](F0-foundations.md) | Foundations | Repositories, local environment, empty API, web shell, app shell |
| [F1](F1-access-and-catalogs.md) | Access and catalogs | Users, roles, permissions, policies, catalogs, audit log, access console |
| [F2](F2-master-data.md) | Master data | Warehouses, locations, products with the legacy import, suppliers, customers |
| [F3](F3-labels-and-printers.md) | Labels, printers and lookup | Label templates, printers, printing, barcode lookup |
| [F4](F4-stock-and-entries.md) | Stock and material entry | Movements, balances, opening inventory, invoices, counts, adjustments, placement |
| [F5](F5-orders.md) | Orders | Orders, reservations, picks, validation, cancellation, reassignment, PDF |
| [F6](F6-reports.md) | Reports and kardex | Stock reports, kardex, movement and order reports |
| [F7](F7-hardening-and-deployment.md) | Hardening and deployment | Backups, restore drill, security review, production deployment |
| [F8](F8-fast-invoice-capture.md) | Fast invoice capture | The invoice screens of F4 with a modal, a quick capture bar and one search box |
| [F9](F9-screen-design.md) | Every screen, the same design | The anatomy of the invoice screens applied to every page |

## Tasks

Each phase is broken into tasks, with their red tests and acceptance criteria, in [tasks/](tasks/README.md). Every task is test-first, as [testing.md](../rules/testing.md) requires.

## Rules for every phase

1. **Each module spec is the source.** The phase names the spec; the spec decides the behavior. A phase never changes a rule on its own.
2. **Open questions block only their own part.** If a phase depends on an open question, the phase builds everything else and says what is blocked.
3. **Tests are part of the phase.** Business rules have unit tests. Stock and status rules have integration tests against real PostgreSQL. The web uses Playwright for the flows of the phase. Each change runs only the specs it affects, as CYC does ([D-011](../../../CYC/DECISIONES.md)).
4. **Coverage target:** at least 70% on business rules (services and state machines), as CYC requires.
5. **Migrations are forward-only.** A phase that changes a table adds a migration. Nothing is edited after it has been applied.
6. **Permissions come with the feature.** A phase that adds an endpoint adds its permission, its seed grants, and a test that denies access without it.
7. **Demo data is never mixed with real data.** Test data is created and removed by the test suite, and it is marked as such.

## Reference

- House standard, read only: `/Users/axzy/DEV/CYC/DECISIONES.md` and `/Users/axzy/DEV/CYC/docs/`. Nothing in that folder is edited from Stock.
- Architecture: [api.md](../architecture/api.md), [web.md](../architecture/web.md), [mobile-app.md](../architecture/mobile-app.md).
