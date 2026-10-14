# F0: Foundations

**Goal:** three working shells (API, web, app) and a local environment that starts with one command. No business features yet.

**Depends on:** nothing.

**Permissions added:** none. The permission catalog starts in F1.

## Deliverables

### Repository

- Single repository with `api/`, `web/`, `app/` and `docs/`, and a `docker-compose.yml` at the root (CYC [D-014](../../../CYC/DECISIONES.md) follows the same layout).
- Each package keeps its own build file and lock file.
- Compose project name `stockflow`.

### API shell

- Spring Boot application in Kotlin, built with Gradle.
- Flyway runs on startup. The first migration creates the schema for the migration history only.
- `GET /api/v1/health` returns `200` with the database status.
- `GET /api/v1/health/ready` returns `200` only when migrations are applied.
- OpenAPI served at `/docs`.
- Error envelope and error codes, as in [api.md](../architecture/api.md) section 2.
- Message catalog with `es` (default) and `en` for every error code.
- `Idempotency-Key` handling as a reusable component. It is not used by any endpoint yet.
- Audit port, with a no-op adapter until F1 adds the audit log.
- Structured JSON logs. No personal data in logs.

### Web shell

- Vite, React 19, TypeScript, ESLint with `eslint-plugin-boundaries`, as in [web.md](../architecture/web.md).
- Feature-Sliced Design layers, created empty except `app` and `shared`.
- HashRouter with one public page (`/login`) and one private shell behind it.
- Axzy UI System: layered CSS, `ITThemeProvider` with `appearance="flat"`, `ITLayout` with a sidebar.
- i18next with `es` (default) and `en`.
- Playwright configured, with one smoke test.

### App shell

- Kotlin Multiplatform project with Android and iOS targets and a shared module.
- Compose Multiplatform screens: a sign-in placeholder and a home placeholder.
- Ktor client pointing at the API `/api/v1/health`, shown on the home screen.
- Camera permission and a scanner stub that returns a fixed value.
- Strings in `es` (default) and `en`, from the app's own catalog ([mobile-app.md](../architecture/mobile-app.md) section 6).

### Environment

- `docker compose up` starts PostgreSQL and the API. The web runs with its dev server.
- The API connects to PostgreSQL and applies migrations on start.

### Brand

- Icons, favicons and the web manifest from the brand pack, in light and dark, placed as [brand.md](../architecture/brand.md) section 2 describes.
- Lottie intro and loader, in light and dark, playing on the web and the app, with reduced motion respected.

### Design

- The owner's design file in `design/`, and a generator that writes the tokens for the web and the app from it ([design-system.md](../architecture/design-system.md)).
- Web: the light workspace, the black glass shell, the orange accent and the sign-in screen in two halves, through the Axzy UI System and the app's own prefixed utilities.
- App: its own theme and components built from the tokens. No Material.

### CI

- Build and unit tests for each package on every change.
- Only the packages affected by a change run their suites.

## Acceptance criteria

1. `docker compose up` starts PostgreSQL, and the API reports healthy on `/api/v1/health/ready`.
2. The OpenAPI page loads at `/docs` and lists the health endpoints.
3. A request with a malformed body returns the flat error envelope with a message in the user's locale.
4. The web shell loads, renders the layout, and the Playwright smoke test passes.
5. The app builds for Android and for iOS. The Android emulator and the iOS simulator both show the home placeholder with the API's health status.
6. A forbidden import between web layers fails lint.
7. The web and the app are drawn in the colors, type and shapes of the design file, in light and dark mode. A generated token file that does not match the design file fails the tests, and so does a Material import or a color typed by hand in the app.

## Out of scope

Any business entity. Authentication. Permissions. Catalogs.

## Open questions

- Pin the exact versions of JDK, Spring Boot, Kotlin, PostgreSQL, React and Compose Multiplatform at the start of F0, and record them here.
- Compose Multiplatform for the app UI: in use since F0, with Compose foundation only.
- The open questions of the design: [design-system.md](../architecture/design-system.md) section 9.
