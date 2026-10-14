# F0 tasks: Foundations

Phase: [F0-foundations.md](../F0-foundations.md).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F0-01 | Repository and local environment | Ops | — | |
| F0-02 | API project with Flyway | API | F0-01 | |
| F0-03 | Health and readiness | API | F0-02 | |
| F0-04 | Error envelope | API | F0-02 | |
| F0-05 | Messages per error code, `es` and `en` | API | F0-04 | |
| F0-06 | OpenAPI at `/docs` | API | F0-03 | |
| F0-07 | Idempotency-Key component | API | F0-04 | |
| F0-08 | Audit port with a no-op adapter | API | F0-02 | |
| F0-09 | Web project and layers | Web | F0-01 | |
| F0-10 | Web layout with Axzy UI System | Web | F0-09 | |
| F0-11 | Web i18n, `es` default and `en` | Web | F0-09 | |
| F0-12 | App project with Android and iOS targets | App | F0-01 | |
| F0-13 | App API client and home screen | App | F0-03, F0-12 | |
| F0-14 | CI pipeline with affected-only runs | Ops | F0-02, F0-09, F0-12 | |
| F0-15 | Brand icons in web and app | Web, App | F0-09, F0-12 | |
| F0-16 | Lottie intro and loader | Web, App | F0-15 | |
| F0-17 | Design tokens from the design file | Ops | F0-09, F0-12 | |
| F0-18 | Design on the web | Web | F0-10, F0-17 | |
| F0-19 | Design on the app | App | F0-13, F0-17 | |

## F0-01 · Repository and local environment · Ops · no dependencies
- Unit (red): the compose configuration parses and names the project `stockflow`.
- E2E (red): `docker compose up` brings PostgreSQL to healthy within 60 seconds.
- Accept:
  - Folders `api/`, `web/`, `app/`, `docs/` and a root `docker-compose.yml` exist.
  - Each package has its own build file and lock file.

## F0-02 · API project with Flyway · API · after F0-01
- Unit (red): a migration file name that breaks the pattern `V<number>__<name>.sql` is rejected by the build check.
- E2E (red): starting the API against an empty database applies `V1` and the history table has one row.
- Accept:
  - The API starts with `docker compose up`.
  - Migrations run on start. Nothing is created by hand.

## F0-03 · Health and readiness · API · after F0-02
- Unit (red): the health service reports `DOWN` when the database check fails, using a stubbed check.
- E2E (red): `GET /api/v1/health` returns 200 with the database status; `GET /api/v1/health/ready` returns 503 before migrations finish and 200 after.
- Accept: both endpoints are public and return no personal data.

## F0-04 · Error envelope · API · after F0-02
- Unit (red): an exception maps to `{ error, code, message, details }` with the right HTTP status.
- E2E (red): a request with a malformed JSON body returns the flat envelope with code `FORMAT_INVALID`.
- Accept:
  - The envelope is flat, as in [api.md](../../architecture/api.md) section 2.
  - An unknown exception returns `500` with code `INTERNAL_ERROR` and no stack trace.

## F0-05 · Messages per error code, `es` and `en` · API · after F0-04
- Unit (red): every error code in the code base has a message in `es` and one in `en`. The test fails on a code without both.
- E2E (red): the envelope's `message` for `FORMAT_INVALID` is in Spanish by default, and in English when the request asks for `en`.
- Accept: a missing message fails the build, not the request.

## F0-06 · OpenAPI at `/docs` · API · after F0-03
- Unit (red): the OpenAPI configuration uses the `/api/v1` prefix.
- E2E (red): `GET /docs/json` lists both health endpoints.
- Accept: `/docs` loads the same document.

## F0-07 · Idempotency-Key component · API · after F0-04
- Unit (red): a key outside 8 to 100 characters, or with other symbols, is rejected. The same key with the same request returns the stored response. The same key with another request is rejected.
- E2E (red): a test-only probe endpoint, active in the test profile only, returns the first response on a repeated key, and `409 IDEMPOTENCY_KEY_REUSED` on a different request.
- Accept: the probe endpoint does not exist outside the test profile.

## F0-08 · Audit port with a no-op adapter · API · after F0-02
- Unit (red): a service that writes calls the audit port once with the actor, the action and the target.
- E2E (red): the test-only probe endpoint performs a write, and the test profile's recorder receives one audit call.
- Accept: the no-op adapter is replaced in F1 without changing callers.

## F0-09 · Web project and layers · Web · after F0-01
- Unit (red): a page that imports from a forbidden layer fails the lint rule.
- E2E (red): Playwright opens `/login` and the page renders.
- Accept: layers `app`, `shared`, `entities`, `features`, `widgets`, `pages` exist, and `app` and `shared` are non-empty.

## F0-10 · Web layout with Axzy UI System · Web · after F0-09
- Unit (red): the theme configuration sets `appearance` to `flat`.
- E2E (red): at 1280 px the shell shows the sidebar and the top bar. At 375 px the top bar shows, and its menu button opens the sidebar. Neither size scrolls horizontally.
- Accept: styles are imported in the layered order from [web.md](../../architecture/web.md) section 8.

## F0-11 · Web i18n, `es` default and `en` · Web · after F0-09
- Unit (red): every key exists in both `es` and `en`; a key missing in one fails the test.
- E2E (red): the page is in Spanish by default; switching to English changes the text, and the choice stays after a reload.
- Accept: the display locale is `es` by default, and the user can switch to `en`.

## F0-12 · App project with Android and iOS targets · App · after F0-01
- Unit (red): a shared test reads the app's version and name.
- E2E (red): the Android app launches on the emulator and shows the home screen.
- Accept: the iOS app launches on the simulator and shows the same home screen.

## F0-13 · App API client and home screen · App · after F0-03, F0-12
- Unit (red): the health DTO parses the API response, tested with Ktor's mock engine.
- E2E (red): the home screen shows the API's health status, on Android and on iOS.
- Accept: an unreachable API shows an error in the user's locale, not a crash.

## F0-14 · CI pipeline with affected-only runs · Ops · after F0-02, F0-09, F0-12
- Unit (red): the script that picks affected packages picks only the packages whose files changed.
- E2E (red): a change to `web/` runs the web suite and not the API suite.
- Accept:
  - A failing test blocks the merge.
  - The pipeline runs the full suite before merging to the main branch.

## F0-15 · Brand icons in web and app · Web, App · after F0-09, F0-12
- Unit (red): every icon file named in the manifest exists and has the size it declares (192 and 512 px for the PWA icons).
- E2E (red): the web serves `/favicon.svg`, `/site.webmanifest` and `/icon-192.png`, and the browser tab shows the StockFlow favicon; the Android and iOS launchers show the StockFlow icon.
- Accept:
  - The files are copied from the pack, not edited (brand.md rule 1).
  - Light and dark variants are both present.

## F0-16 · Lottie intro and loader · Web, App · after F0-15
- Unit (red): each Lottie file parses as JSON and has the frame count in brand.md section 4; the player chooses the light or dark file from the theme.
- E2E (red): the web and the app play the intro once before the sign-in screen; with reduced motion on, the intro shows its first frame and does not move; the loader shows on a slow list.
- Accept:
  - The intro never blocks sign-in beyond its length (brand.md rule 4).
  - The loader has its accessible label in `es` and `en` (`brand.loading`).

## F0-17 · Design tokens from the design file · Ops · after F0-09, F0-12
- Unit (red): the generator reads the colors, type, corners, spacing and components of `design/command-center-DESIGN.md`; a component's references resolve to values (`rgba(0, 0, 0, 0.70)` is `0xB3000000` for the app).
- E2E (red): every generated file in `web/` and `app/` is identical to what the generator writes from the design file. A stale file fails the test.
- Accept:
  - `node scripts/design-tokens.mjs` writes the web tokens, the web theme, the app tokens and the Android window colors.
  - The dark mode roles are one table in the generator, used by the web and the app.
  - No generated file is edited by hand ([design-system.md](../../architecture/design-system.md) section 3).

## F0-18 · Design on the web · Web · after F0-10, F0-17
- Unit (red): the Axzy UI System theme takes every color from the tokens; every class written in the app starts with `sf:`; the stylesheet sets the prefix and the layer order of [web.md](../../architecture/web.md) section 8.
- E2E (red): in the browser, the canvas is `background` with `on-background` text in the system sans font; the sidebar and the top bar are black at 70% with a 12px blur and a white border at 10%; the sidebar is 256px, and 64px collapsed; the top bar is 64px; the active item is orange with white text and 8px corners; the content is capped at 1400px; the sign-in screen is two equal halves with a white card; dark mode uses the dark colors; a phone has no sideways scroll.
- Accept:
  - Expected values in the tests are read from the tokens, not typed.
  - The kit's reset does not undo the app's own text sizes or borders.
  - Overrides that depend on the kit's markup are listed in [design-system.md](../../architecture/design-system.md) section 4.

## F0-19 · Design on the app · App · after F0-13, F0-17
- Unit (red): the light and dark colors of the theme are the tokens'; a type style keeps its size, weight, line height and tracking; each status tone is a component of the design; the build has no Material artifact, and no source file types a color by hand.
- E2E (red): on the Android emulator, the pixels of the home screen match the design file: the shell behind the status bar and in the top bar, the canvas, the title, the card border and the status badge, in light and in dark mode. On the iOS simulator the same screen is checked by eye in both modes.
- Accept:
  - The app uses nothing from an earlier look: no Material theme, component or default color.
  - The status bar shows light icons over the shell on both platforms.
  - The Android window opens in the workspace color, with no flash.
