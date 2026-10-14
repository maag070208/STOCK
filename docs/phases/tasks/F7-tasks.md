# F7 tasks: Hardening and deployment

Phase: [F7-hardening-and-deployment.md](../F7-hardening-and-deployment.md).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F7-01 | Security review of the API and the web | API | F5, F6 | |
| F7-02 | Dependency audit in CI | Ops | F0-14 | |
| F7-03 | Rate limits in production values | API | F1-06 | |
| F7-04 | Backup with checksum | Ops | F0-01 | |
| F7-05 | Restore drill | Ops | F7-04 | |
| F7-06 | Load and concurrency test | Ops | F4-17, F5-03 | |
| F7-07 | Deployment images and smoke test | Ops | F7-03, F7-04 | |
| F7-08 | Rollback | Ops | F7-07 | |
| F7-09 | Secrets scan | Ops | F0-14 | |

## F7-01 · Security review of the API and the web · API · after F5, F6
- Unit (red): a text field with SQL or ZPL control characters (`'`, `;`, `^`, `~`) is rejected or sanitized before it reaches a query or the printer. Quotes and slashes in product names are accepted.
- E2E (red): a request without a token, with an expired token, and with a token of a user without the permission, each returns the right code, on every endpoint of the OpenAPI document.
- Accept: each finding has a written result and an owner.

## F7-02 · Dependency audit in CI · Ops · after F0-14
- Unit (red): the audit script fails on a high-severity finding in a fixture report.
- E2E (red): a pipeline run with a fixture dependency that has a known issue is blocked.
- Accept: a real high-severity finding blocks the build.

## F7-03 · Rate limits in production values · API · after F1-06
- Unit (red): the production limit is lower than the test limit, and the test limit is not used outside the test profile.
- E2E (red): the sixth failed login in a minute from one IP returns `429 RATE_LIMITED` in production settings.
- Accept: `TRUST_PROXY` is set in production, so the limit uses the client IP.

## F7-04 · Backup with checksum · Ops · after F0-01
- Unit (red): the backup script names the file with the date and writes a checksum next to it.
- E2E (red): a backup of the test database produces a file whose checksum matches.
- Accept: the backup runs daily and keeps the retention the owner decides.

## F7-05 · Restore drill · Ops · after F7-04
- Unit (red): the restore script refuses a target that is not an empty database.
- E2E (red): restoring the backup into an empty database reproduces the stock total and the product count exactly.
- Accept: the drill result is written in the operations notes.

## F7-06 · Load and concurrency test · Ops · after F4-17, F5-03
- Unit (red): the load script builds the expected traffic per warehouse from the owner's numbers.
- E2E (red): the concurrency suites from F4 and F5 pass at the expected load; no balance goes negative.
- Accept: the results are recorded with the environment used.

## F7-07 · Deployment images and smoke test · Ops · after F7-03, F7-04
- Unit (red): the image build fails if a secret is in a build argument.
- E2E (red): after deployment to staging, the smoke test signs in, reads a stock query, and prints a label to the test printer.
- Accept: the production deployment repeats the same smoke test.

## F7-08 · Rollback · Ops · after F7-07
- Unit (red): the rollback script selects the previous image tag.
- E2E (red): a deployment to staging is rolled back, and the smoke test passes on the previous version.
- Accept: the rollback is performed once, and the time it took is recorded.

## F7-09 · Secrets scan · Ops · after F0-14
- Unit (red): the scanner finds a fixture secret in a file and fails.
- E2E (red): the pipeline blocks a commit that contains a fixture secret.
- Accept: the scanner runs on every pull request.
