# Stock documentation

Index of the project documentation. If a decision is not written here, it is not decided.

## Structure

| Folder | Content |
|---|---|
| [architecture/](architecture/) | Vision, scope, stack, web architecture |
| [i18n/](i18n/) | Supported locales, translation rules, message keys |
| [rules/](rules/) | Business rules and non-negotiable conventions |
| [phases/](phases/) | Phase plan, deliverables, acceptance criteria |
| [modules/](modules/) | Specification of each functional module |

## Architecture

- [architecture/vision-and-scope.md](architecture/vision-and-scope.md): what the system is, scope, open questions.
- [architecture/web.md](architecture/web.md): React web stack, layers, auth, UI kit.
- [architecture/api.md](architecture/api.md): Kotlin and Spring Boot API, PostgreSQL, packages, stock consistency.
- [architecture/mobile-app.md](architecture/mobile-app.md): Kotlin Multiplatform operator app, scanning, tests.
- [architecture/brand.md](architecture/brand.md): name, colors, icons, and Lottie animations from the brand pack.
- [architecture/design-system.md](architecture/design-system.md): the design the web and the app follow, its tokens, and how each side applies them.
- [phases/README.md](phases/README.md): the eight phases, their order, and their acceptance criteria.

## Rules

- [rules/language-and-naming.md](rules/language-and-naming.md): English in code and docs; display text in Spanish (default) and English.
- [rules/catalogs.md](rules/catalogs.md): every enumerated value is database data.
- [rules/roles.md](rules/roles.md): seed roles and their matrix.
- [rules/access-control.md](rules/access-control.md): RBAC with scopes, per-user exceptions, ABAC policies.
- [rules/validation.md](rules/validation.md): field validation for every input, and the error codes.

## Translations

- [i18n/README.md](i18n/README.md): locales, default, keys, rules.
- [i18n/messages.md](i18n/messages.md): error messages, `es` and `en`.
- [i18n/glossary.md](i18n/glossary.md): roles, statuses, units and domain terms, `es` and `en`.

## Reference

- House standard: `/Users/axzy/DEV/CYC/DECISIONES.md` (decision register) and `/Users/axzy/DEV/CYC/docs/` (guides). Stock adopts the access model and the web stack from it.

## Modules

- [modules/material-entry.md](modules/material-entry.md): invoice header, lines, and assignment to warehouse operators.
- [modules/labels.md](modules/labels.md): ZPL label catalog (product, location, others) and printing rules.
- [modules/barcode-scanning.md](modules/barcode-scanning.md): operator app scanning for entries, picks and reassignments. Barcode = product code by default.
- [modules/products.md](modules/products.md): materials (Materiales): fields, unit of measure, legacy import.
- [modules/order-fulfillment.md](modules/order-fulfillment.md): exit (order fulfillment). Pending the owner's flow.
- [modules/suppliers.md](modules/suppliers.md): suppliers, their contacts, fiscal and payment data.
- [modules/customers.md](modules/customers.md): customers and their contacts. Mirrors suppliers.
- [modules/locations.md](modules/locations.md): warehouses, locations (`K05A`), groups by aisle and rack, and stock per location.
- [modules/material-receiving.md](modules/material-receiving.md): counting, adjustment of differences, and placement.
- [modules/stock.md](modules/stock.md): stock API: movements, balances, lists, and the opening inventory.
- [modules/identity.md](modules/identity.md): identity and access API: sessions, users, roles, policies, catalogs, audit log.

## Status

- Architecture, rules, modules, translations and phases are written. Open questions are listed at the end of each document.
- Phase F0 is in code: `api/`, `web/` and `app/`, with the design from [architecture/design-system.md](architecture/design-system.md) applied to the web and the app.
- Phase F1 is done (2026-10-14): access, catalogs and the audit log in the API, the web console, and sign-in on the app. See its closure in [phases/F1-access-and-catalogs.md](phases/F1-access-and-catalogs.md).
- Phase F2 is done (2026-10-14): warehouses and locations, materials with the import of the legacy file, suppliers and customers, in the API and the web. See its closure in [phases/F2-master-data.md](phases/F2-master-data.md).
- Phases F3 (labels, printers and lookup) and F4 (stock and material entry) are next. They can run in parallel.
