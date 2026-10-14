# F7: Hardening and deployment

**Goal:** the system can run in production, can be restored after a failure, and has been reviewed for security before anyone trusts it with real stock.

**Depends on:** F5 and F6.

**Permissions added:** none new.

## Deliverables

### Security

- **OWASP review** of the API and the web, with each finding fixed or accepted in writing.
- **Dependency audit** in CI. High-severity findings block the build.
- **Rate limits** on login and password reset, per IP, returning `429 RATE_LIMITED`.
- **Secrets** only from the environment. None in the repository, the images, or the logs.
- **HTTPS** on every environment outside local development.
- **Personal data.** RFC, email and phone are never written to logs. A decision on encryption at rest for these fields.
- **Access-control tests** for each sensitive endpoint: allowed, forbidden, out of scope, and no token.

### Backups and restore

- **Daily backup** of the database in PostgreSQL's custom format, with a checksum.
- **Restore drill.** A backup is restored into an empty database, the migrations are checked, and the stock totals are compared with the source. The drill is written down with its result.
- **Retention** defined by the owner (open).

### Load and concurrency

- **Concurrency tests** from F4 and F5 run in the pipeline against PostgreSQL.
- **Load test** of the stock endpoints and of order creation, at the expected number of users per warehouse, with the results recorded.

### Deployment

- Docker image per package: the API, the web, and the migrations run at startup.
- The production environment, its database, and its host. Hosting is still open.
- Smoke test after each deployment: health, a sign-in, one stock query, one print to a test printer.
- A rollback procedure, tested once.

### Monitoring

- Health endpoints checked by the host.
- Error logs with the error codes, so the most frequent business errors are visible.

## Acceptance criteria

1. The OWASP review has a written result, and no finding is open without an owner.
2. A restore drill on a copy of production data reproduces the stock totals exactly.
3. The sixth failed login in a minute from one IP gets `429`.
4. A deployment to the production environment passes its smoke test.
5. A rollback to the previous image is performed once in a staging environment and works.
6. No secret appears in the repository, the images, or the logs. Checked by a scan.

## Open questions

- Hosting: where production runs. The owner has not decided.
- Backup retention and where the copies are stored.
- Whether RFC, email and phone are encrypted at rest.
