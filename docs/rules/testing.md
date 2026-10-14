# Testing

**Status:** Accepted, 2026-10-14. Every task in [phases/tasks/](../phases/tasks/) follows this rule.

## 1. Rule

Development is test-driven (TDD). Every task, without exception, has:

1. **At least one unit test**, for the logic the task adds or changes.
2. **At least one end-to-end test**, for the behavior the user or the API client sees.

Both are written **before** the code, and both are seen failing for the right reason first.

## 2. The cycle

1. **Red.** Write the test from the task's acceptance criteria. Run it. It must fail, and the failure message must say what is missing, not a syntax or setup error.
2. **Green.** Write the smallest code that makes the test pass.
3. **Refactor.** Clean the code with all tests green. No new behavior in this step.
4. **Commit** the test and the code together, after the tests of the affected packages pass.

A bug found later starts the same way: a failing test that reproduces it, then the fix.

## 3. Test types by package

| Package | Unit | End-to-end |
|---|---|---|
| API (Kotlin, Spring Boot) | JUnit 5 with MockK. Services, rules, state machines, validators, token logic | HTTP tests against the running API and a real PostgreSQL started with Testcontainers. They call the endpoints as a client would, with real tokens and seeded roles |
| Web (React) | Playwright, as in CYC ([D-011](../../../CYC/DECISIONES.md)). Pure functions and components that have logic | Playwright against the running web and the running API, through the screens |
| App (Kotlin Multiplatform) | `kotlin.test` in shared code, with Ktor's mock engine for the API client | Compose UI tests on the Android emulator. XCUITest on the iOS simulator for the same flows |

## 4. Rules that apply everywhere

1. **Permissions are tested in both directions.** Every endpoint, screen and action has a test that is allowed, and a test that is denied without the permission, with the error code the spec names. Out-of-scope access is tested too: `404` on reads by id, `403` on writes.
2. **Concurrency rules are tested with real PostgreSQL**, not mocks. Two transactions run in parallel, and the test asserts that exactly one succeeds when stock is short, and that no balance goes negative.
3. **Time is fixed.** Anything that depends on the date (due dates, alerts, expiry) takes a clock that the test controls. No test reads the real clock.
4. **Test data is created by the suite and removed by it.** E2E tests create their data through the API's test commands, as CYC does ([D-022](../../../CYC/DECISIONES.md)). Tests never write to the database directly.
   The web and app suites sign in as `e2e-admin`, an administrator the API keeps only with the profile `e2e` and the password `E2E_ADMIN_PASSWORD` (`.env`). The profile is for developers' machines and CI, never for production. It also raises the rate limits of login and password reset, because the suites sign in many times from one address and fail on purpose; the limit itself is tested by the API's own suite. The API's own suite does not need it: it changes the password of the bootstrap administrator, as a person would.
5. **Fixtures are small and anonymized.** The legacy file is never copied into the repository. Fixtures use a few rows with the same shape, plus the control total of the real file as a check value.
6. **Tests are deterministic.** No test depends on another test, on order, or on a previous run.
7. **Printing is tested against a fake printer.** A test server listens on the printing port and records what it receives. Printers use port 9100; the API's suite and the profile `e2e` move the printing port to 19100 (`stockflow.printing.port`), so the fake printer needs no privileges and never meets a real printer. Real Zebra printing is checked once by hand per phase, and that check is recorded.
8. **Mobile flows are tested on both platforms** before a phase closes: Android emulator and iOS simulator.
9. **Translations are tested.** Every key exists in `es` and `en`, placeholders match across locales, and every error code has both messages. A gap fails the build.

## 5. Running the tests

- Every change runs **only the specs it affects**, as CYC does. Running the full suite on each change is not required.
- The full suite runs before a merge to the main branch and before a phase closes.
- CI blocks a merge while any test fails.

## 6. Coverage

- At least **70% of lines** in services and business rules (CYC's target, [D-051](../../../CYC/DECISIONES.md)).
- State machines (order statuses, invoice statuses, reassignment actions) reach **every transition and every rejected transition** in tests.
- Coverage is a floor, not a goal. A test that only raises the number does not count.

## 7. Definition of done for a task

- The unit and end-to-end tests were written first, and each was seen failing.
- Both pass, and the affected suites pass.
- Permissions for the task are covered in both directions (rule 1).
- The task's acceptance criteria are all true, and the phase document still matches the behavior.
- No secret, real customer data, or the legacy file appears in the code, the tests, or the logs.
