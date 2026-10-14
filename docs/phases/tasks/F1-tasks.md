# F1 tasks: Access and catalogs

Phase: [F1-access-and-catalogs.md](../F1-access-and-catalogs.md).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F1-01 | Permission catalog from code | API | F0 | |
| F1-02 | Users with temporary password | API | F1-01 | |
| F1-03 | Login and refresh-token rotation | API | F1-02 | |
| F1-04 | Password change revokes sessions | API | F1-03 | |
| F1-05 | Lockout and unlock | API | F1-03 | |
| F1-06 | Rate limit on login and reset | API | F1-03 | |
| F1-07 | Channels `web.access` and `app.access` | API | F1-03 | |
| F1-08 | Seed roles and the matrix | API | F1-01 | |
| F1-09 | Multi-role scope resolution | API | F1-08 | |
| F1-10 | Per-user exceptions with expiry | API | F1-09 | |
| F1-11 | Warehouse scope (`AREA`) | API | F1-09 | |
| F1-12 | Policy evaluation | API | F1-09 | |
| F1-13 | Policy administration | API | F1-12 | |
| F1-14 | Anti-lockout and system roles | API | F1-08 | |
| F1-15 | Catalog engine | API | F1-01 | |
| F1-16 | Seed catalogs | API | F1-15 | |
| F1-17 | Audit log | API | F1-08, F0-08 | |
| F1-18 | Web sign-in and forced password change | Web | F1-03, F1-04 | |
| F1-19 | Web access console | Web | F1-13, F1-15 | |
| F1-20 | App sign-in | App | F1-07 | |

## F1-01 · Permission catalog from code · API · after F0
- Unit (red): every permission key matches `module.action` in lowercase, and no two keys repeat.
- E2E (red): `GET /api/v1/permissions` as `ADMIN` lists the seeded catalog; as `APP_USER` it returns `403`.
- Accept: the seed runs as a migration, and running it twice writes nothing new.

## F1-02 · Users with temporary password · API · after F1-01
- Unit (red): a new user is created with `mustChangePassword = true`, and the temporary password is random and never returned again.
- E2E (red): an administrator creates a user; the response carries the temporary password once, as `temporaryPassword`, and the user resource never holds a password or its hash; a second read does not show it either.
- Accept: `POST` by a user without `users.create` returns `403 INSUFFICIENT_PERMISSIONS`.

## F1-03 · Login and refresh-token rotation · API · after F1-02
- Unit (red): rotating a refresh token invalidates the previous one.
- E2E (red): login returns both tokens; refreshing with an old token returns `401 TOKEN_REVOKED`.
- Accept: passwords are stored as BCrypt hashes, and the login response never includes the hash.

## F1-04 · Password change revokes sessions · API · after F1-03
- Unit (red): changing the password revokes every refresh token of the user.
- E2E (red): after the change, the old refresh token fails; the new tokens work.
- Accept: a user with `mustChangePassword` can call only the change-password endpoint, and anything else returns `403 PASSWORD_CHANGE_REQUIRED`.

## F1-05 · Lockout and unlock · API · after F1-03
- Unit (red): the fifth failed login locks the account; a success before the fifth resets the counter.
- E2E (red): five wrong passwords return `423 ACCOUNT_LOCKED`; the correct password is refused until an administrator unlocks; the unlock is audited.
- Accept: the lockout threshold is 5, as in [D-005](../../../../CYC/DECISIONES.md).

## F1-06 · Rate limit on login and reset · API · after F1-03
- Unit (red): the limiter counts failed logins only, and every reset request.
- E2E (red): the limit returns `429 RATE_LIMITED` in the envelope.
- Accept: the limit is per IP, and the trusted proxy setting decides the real IP.

## F1-07 · Channels `web.access` and `app.access` · API · after F1-03
- Unit (red): the channel check uses the request's origin, and denies a channel the user does not hold.
- E2E (red): `APP_USER` signs in, and a web request returns `403 INSUFFICIENT_PERMISSIONS`; an `ADMIN` signs in on both.
- Accept: the matrix in [roles.md](../../rules/roles.md) is the seed.

## F1-08 · Seed roles and the matrix · API · after F1-01
- Unit (red): the seed matrix matches [roles.md](../../rules/roles.md) section 3, cell by cell.
- E2E (red): a change to a role's permission takes effect on the next request, without re-login.
- Accept: `ADMIN` holds every permission.

## F1-09 · Multi-role scope resolution · API · after F1-08
- Unit (red): with two roles, the effective scope is the highest one.
- E2E (red): a user with `OFFICE` and `WAREHOUSE_OPERATOR` gets the union of both, at the highest scope.
- Accept: roles add permissions and never subtract them.

## F1-10 · Per-user exceptions with expiry · API · after F1-09
- Unit (red): a valid exception overrides the role scope; an expired one is ignored.
- E2E (red): an exception with a past expiry grants nothing; one with a future expiry grants access until it expires.
- Accept: every exception stores its reason and who granted it.

## F1-11 · Warehouse scope (`AREA`) · API · after F1-09
- Unit (red): a user with `AREA` sees a record only when the record's warehouse is in `user_warehouses`; outside the scope a read answers `NOT_FOUND` and a write `INSUFFICIENT_PERMISSIONS`.
- E2E (red): an administrator assigns warehouses to a user, and the user's record carries them.
- Accept: the read, write and list checks over HTTP run with the first resource that belongs to a warehouse (locations, F2): a read by id outside the scope returns `404`, a write `403`, and a list returns only rows inside the scope, with a total that counts only those rows. F1 has no permission with the `AREA` scope yet.

## F1-12 · Policy evaluation · API · after F1-09
- Unit (red): the first policy by priority, then key, whose conditions all hold decides; with no match the action is allowed.
- E2E (red): a `DENY` policy on an action returns `403 POLICY_DENIED`, and the audit log records `ACCESS_DENIED` with the policy key.
- Accept: the operators in [access-control.md](../../rules/access-control.md) section 6 are all tested, one by one.

## F1-13 · Policy administration · API · after F1-12
- Unit (red): a policy that names an unregistered action or field is rejected; a policy targeting `roles.manage` is rejected.
- E2E (red): an administrator creates a policy; a user without `roles.manage` gets `403`.
- Accept: changing a policy reloads the cache, and the change applies on the next request.

## F1-14 · Anti-lockout and system roles · API · after F1-08
- Unit (red): a change that leaves no active role with `roles.manage` is rejected; a system role cannot be deleted or renamed.
- E2E (red): removing the last active `ADMIN` returns `409 ANTI_LOCKOUT`; a user cannot change their own roles.
- Accept: the guard runs in the same transaction as the change.

## F1-15 · Catalog engine · API · after F1-01
- Unit (red): a catalog item cannot be activated without its `es` and `en` labels; a code cannot change after creation.
- E2E (red): create an item, label it, deactivate it; the deactivated item is not returned for new records and still appears in history.
- Accept: the endpoints need `catalogs.view` to read and `catalogs.manage` to change.

## F1-16 · Seed catalogs · API · after F1-15
- Unit (red): the seed loader adds missing values and never changes existing ones.
- E2E (red): `GET` returns `INVOICE_STATUS` (`OPEN`, `PENDING`, `IN_ASSIGNMENT`, `CLOSED`), `ORDER_STATUS` (five values) and `UNIT_OF_MEASURE` (`PIECE`), each with its `es` and `en` labels.
- Accept: running the seed twice writes nothing new.

## F1-17 · Audit log · API · after F1-08, F0-08
- Unit (red): the audit record carries actor, action, target, before and after, and time.
- E2E (red): a write creates one record; the API offers no update or delete for audit records (`405`).
- Accept: every access denial writes `ACCESS_DENIED`, and every role change writes `ROLE_*`.

## F1-18 · Web sign-in and forced password change · Web · after F1-03, F1-04
- Unit (red): the router sends a user with `mustChangePassword` to the change-password screen, and to nothing else.
- E2E (red): a new user signs in, changes the password, and reaches the menu.
- Accept: the menu shows only the screens the user's permissions allow.

## F1-19 · Web access console · Web · after F1-13, F1-15
- Unit (red): the matrix component shows the scope of each cell from the API.
- E2E (red): an administrator changes a matrix cell and a policy; a user without `roles.manage` cannot open the console (`403` page).
- Accept: "what each role sees" lists the screens of each role from `APP_SCREENS`.

## F1-20 · App sign-in · App · after F1-07
- Unit (red): the session store keeps the tokens and clears them on sign-out.
- E2E (red): an `APP_USER` signs in on Android and iOS and reaches the home screen; a user without `app.access` sees an error in their locale.
- Accept: the refresh token is stored in the platform's secure storage.
