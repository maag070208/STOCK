# API architecture

**Status:** Accepted for planning, 2026-10-13. Versions are pinned at the start of phase F0 to the latest stable release available then.

## 1. Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Framework | Spring Boot, with Spring Web (REST) |
| Build | Gradle, Kotlin DSL |
| Runtime | JDK, the current LTS release |
| Database | PostgreSQL 16 or newer |
| Persistence | Spring Data JPA, with explicit locks where stock changes |
| Migrations | Flyway. Every schema change is a migration. Nothing is created by hand |
| Validation | The rules in [validation.md](../rules/validation.md), enforced in a validation layer before the service, plus database constraints |
| Authentication | JWT access token and rotating refresh token. Passwords hashed with BCrypt |
| Authorization | RBAC with scopes, per-user exceptions and ABAC policies, as in [access-control.md](../rules/access-control.md). Resolved on the server for every request |
| API docs | OpenAPI, served at `/docs` (JSON at `/docs/json`) |
| Tests | JUnit 5, with Testcontainers running PostgreSQL for integration tests |
| Logs | Structured JSON logs. No personal data in logs (RFC, email, phone) |

Choices that differ from the house standard in CYC. The reason is the stack, not preference:

- CYC uses Express and Prisma ([D-001](../../../CYC/DECISIONES.md)). Stock uses Spring Boot and Spring Data JPA. The conventions carry over. The code does not.
- CYC tests the API with Playwright ([D-011](../../../CYC/DECISIONES.md)). Stock tests the API in Kotlin, against real PostgreSQL through Testcontainers, and keeps Playwright for the web.

## 2. Conventions

These come from CYC's API conventions ([convenciones.md](../../../CYC/docs/api/convenciones.md)), adapted to Stock.

- **Prefix:** every endpoint under `/api/v1`. A breaking change opens `/api/v2`.
- **Resources:** plural nouns, kebab-case, in English. Example: `/api/v1/catalog-items`.
- **Actions** that are not CRUD are a path segment: `/api/v1/orders/{id}/validation`, `/api/v1/orders/{id}/reassignments`.
- **Tables** (lists with filters and sort): `POST /api/v1/{resource}/query` with `{ page, limit, filters, sort }`. The response carries `data` and `total`. The reasons are in CYC's convention §3.
- **Errors** use a flat envelope:
  ```json
  { "error": "ValidationError", "code": "FIELD_REQUIRED", "message": "…", "details": [] }
  ```
  The `code` is English, from the codes in [validation.md](../rules/validation.md) and the module specs. The `message` is in the user's locale, Spanish by default, from the message catalog ([i18n/messages.md](../i18n/messages.md)).
- **Idempotency:** creating an invoice, an order, a movement, or a print job takes an `Idempotency-Key` header (8 to 100 characters: letters, digits, `_`, `-`). Sending the same key again returns the first response, and reusing a key for another request returns `409 IDEMPOTENCY_KEY_REUSED`.
- **Dates:** ISO 8601 in the API. Stored as `timestamptz` in UTC. Calendar dates as `date`. Conversion to the local zone happens in the apps.
- **Decimals:** JSON numbers with a dot. Quantities use `numeric` in the database, never floating point.
- **No hard deletes of business data.** Deactivation or a domain-specific cancellation keeps the record.

## 3. Packages

```
api/
└── src/main/kotlin/…/
    ├── core/                 shared infrastructure, used by every module
    │   ├── security/         authentication, JWT, permission checks, policy engine
    │   ├── errors/           error envelope and error codes
    │   ├── i18n/             Message catalog, `es` and `en`
    │   ├── audit/            audit port: every write is recorded
    │   ├── idempotency/      Idempotency-Key handling
    │   ├── validation/       field rules from validation.md
    │   └── db/               transactions, locks, pagination helpers
    └── modules/              one folder per domain
        ├── identity/         users, roles, permissions, policies
        ├── catalogs/         value catalogs and their items
        ├── warehouses/       warehouses and locations
        ├── products/         materials
        ├── partners/         what suppliers and customers share: address, contacts, their rules
        ├── suppliers/        suppliers and contacts
        ├── customers/        customers and contacts
        ├── stock/            movements and balances
        ├── entries/          invoices, assignments, counts, placement
        ├── orders/           orders, reservations, picks, reassignment
        ├── labels/           templates, printers, printing
        └── reports/          reports and kardex
```

Inside a module: `controller` (HTTP only) → `service` (business rules and transactions) → `repository` (persistence). A controller never touches a repository. A service never builds an HTTP response.

Modules talk through their services, never through each other's tables. An order that needs stock calls the stock module's service.

One exception, for reading only: a list that shows rows of several modules at once (stock with its locations and materials) reads a database view owned by the module that lists them. A view never writes, and every change still goes through the owner's service. When a module needs to ask another one something without depending on it (locations ask stock whether a location holds stock), the asking module defines a small interface and the other module implements it.

## 4. Stock consistency

Stock is the most sensitive data. Its rules come from [locations.md](../modules/locations.md) and [order-fulfillment.md](../modules/order-fulfillment.md):

- **Movements are append-only.** Each movement is a row. Nothing is updated or deleted.
- **Balances are a projection** per location and product, updated in the same transaction as the movement that changes them.
- **A balance cannot go below zero.** Enforced by a database check, and by a pessimistic write lock on the balance row before it is read and changed.
- **Reservations** take a lock on the product and warehouse, so two orders cannot reserve the same last unit.
- **Concurrency tests** run two operations in parallel against real PostgreSQL and assert that exactly one succeeds when stock is short.

## 5. Security

- Permissions are checked on the server for every request. The client's view of the menu is cosmetic.
- Channels: `web.access` and `app.access` are checked separately ([roles.md](../rules/roles.md)).
- Rate limit on login and on password reset, per IP (as in CYC [D-051](../../../CYC/DECISIONES.md)).
- Account lockout after 5 failed logins, as in CYC [D-005](../../../CYC/DECISIONES.md).
- Label templates and printer addresses are restricted (see [labels.md](../modules/labels.md)).
- Secrets come from the environment, never from the repository.

## 6. Local environment

Docker Compose at the repository root, with a project name of `stockflow`. The API, the PostgreSQL database, and the printers' test address are defined there. Migrations run when the API starts. Seeds of catalogs and permissions are migrations too.
