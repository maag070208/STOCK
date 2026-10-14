# F1: Access and catalogs

**Goal:** people can sign in, administrators decide what each role can do, and every value list is data in the database.

**Depends on:** F0.

**Permissions added:** `users.view`, `users.create`, `users.edit`, `users.delete`, `users.permissions`, `roles.manage`, `audit.view`, `config.view`, `config.manage`, `catalogs.view`, `catalogs.manage`, `web.access`, `app.access`.

## Specs

- [access-control.md](../rules/access-control.md): RBAC with scopes, exceptions, ABAC policies.
- [roles.md](../rules/roles.md): seed roles and their matrix.
- [catalogs.md](../rules/catalogs.md): value catalogs.
- [validation.md](../rules/validation.md): field rules for users and catalog values.

## Deliverables

### API

- **Users.** Created by an administrator with a temporary password and `mustChangePassword`. Password reset by an administrator. Unlock. Reactivate. Nobody changes their own roles.
- **Authentication.** Login with JWT access token and rotating refresh token. Lockout after 5 failed logins. Login and password-reset rate limits per IP.
- **Channels.** `web.access` is checked for web requests, and `app.access` for app requests.
- **Permissions.** Catalog seeded from the code, with the permissions that F1 needs. Modules add theirs later.
- **Roles.** Seed roles `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER`, `WAREHOUSE_OPERATOR`, `APP_USER`. Role matrix, multi-role, `maxScope`.
- **Per-user exceptions** with reason and expiry. `user_warehouses` for the `AREA` scope.
- **Policies.** Action registry, condition evaluation, first match decides, cache reloaded after each write, denial returns `POLICY_DENIED`.
- **Anti-lockout.** A change that leaves no active role with `roles.manage` is rejected. System roles cannot be deleted or renamed.
- **Catalogs.** `catalog`, `catalog_item` and `catalog_item_label` tables. Admin endpoints to create, label, reorder and deactivate. Codes are immutable.
- **Seed catalogs** that the owner confirmed: `INVOICE_STATUS` (`OPEN`, `PENDING`, `IN_ASSIGNMENT`, `CLOSED`), `ORDER_STATUS` (`CREATED`, `FULFILLING`, `FULFILLED`, `VALIDATED`, `CANCELLED`), and `UNIT_OF_MEASURE` with `PIECE`. Other values wait for the owner (see Open questions).
- **Audit log.** Append-only. Records every write, plus `ACCESS_DENIED`, `ROLE_*`, `PERMISSION_*`, `POLICY_*`, `USER_*`.

### Web

- Login, forced password change, and the private shell with the menu built from the user's permissions.
- **Access console** (`roles.manage` only): roles, matrix, per-user exceptions, policies, catalogs, and "what each role sees".
- Lists use the server-side table contract, with a filter and a sort on every data column.

### App

- Sign-in for users with `app.access`. Nothing else yet.

## Acceptance criteria

1. An administrator creates a user. The user signs in with the temporary password and is forced to change it before anything else.
2. Five failed logins lock the account. An administrator unlocks it.
3. A user with only `APP_USER` signs in to the app and gets `403 INSUFFICIENT_PERMISSIONS` on any web request.
4. A change to the role matrix takes effect on the user's next request, with no re-login.
5. A policy `DENY` for an action returns `403 POLICY_DENIED`, and the audit log records `ACCESS_DENIED` with the policy key.
6. A policy that names an unregistered action or field is rejected when saved.
7. The last active `ADMIN` cannot be removed or deactivated.
8. A user cannot change their own roles.
9. A catalog item cannot change its code. A deactivated item is not offered for new records and still shows in history.
10. An attempt to save a catalog label with a field outside the spec returns `UNKNOWN_FIELD`.
11. Every access denial and every write appears in the audit log.

## Closure

**Done, 2026-10-14.** Every criterion has a test that passes. The suites: API (`./gradlew test`), web (`pnpm test:unit`, `pnpm test:e2e`), app (`tests/e2e-app/` on Android, `scripts/ios-ui-tests.sh` on iOS).

| Criterion | Proof |
|---|---|
| 1 | API `AuthEndpointTest`; web `auth.spec.ts`; app sign-in on Android and iOS (`SignInUITests`) |
| 2 | API: five wrong passwords lock the account until an administrator unlocks it |
| 3 | API `AccessEndpointTest` and `AuthEndpointTest`: channels and their permissions |
| 4 | API and web `access.spec.ts`: a matrix change applies on the next request |
| 5 | API: a `DENY` policy answers `POLICY_DENIED`, and the log names the policy |
| 6 | API `PolicyRulesTest` and `AccessEndpointTest` |
| 7, 8 | API `AntiLockoutTest` and `AccessEndpointTest` |
| 9, 10 | API `CatalogRulesTest` and `CatalogsEndpointTest`; web `catalogs.spec.ts` |
| 11 | API `AuditCoverageTest`: every write of F1 and every denial leaves its record |

The iOS UI tests run on a Mac against the local API. CI does not run them yet: it would need a macOS runner with the API.

## Open questions

- Channels per role: may `OFFICE` and `WAREHOUSE_MANAGER` use the app, and may `WAREHOUSE_OPERATOR` use the web? The matrix assumes no (see [roles.md](../rules/roles.md) section 6).
- Confirm the seed values of `PAYMENT_TERMS`, `COUNTRY` and `STATE`. They are needed by F2 suppliers and customers.
