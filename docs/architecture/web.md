# Web

**Status:** Accepted. Adopts the web standard of CYC ([web-fsd.md](../../../CYC/docs/arquitectura/web-fsd.md), [axzy-ui-system.md](../../../CYC/docs/arquitectura/axzy-ui-system.md), D-007, D-008, D-053, D-055). Versions are taken from CYC's `web/package.json` and get checked when the project is created.

## 1. Stack

| Concern | Choice | Version |
|---|---|---|
| UI library | React | 19.0.0 |
| Build | Vite, TypeScript | ^6.1, ~5.7 |
| Global state | Redux Toolkit, react-redux | ^2.5, ^9.2 |
| Routing | React Router, `HashRouter` | ^7.2 |
| HTTP | axios | ^1.7 |
| i18n | i18next, react-i18next | ^26.4, ^17.0 |
| Validation | yup | ^1.6 |
| UI kit | `@axzydev/axzy_ui_system` | ^1.4.7 |
| Styles | Tailwind CSS via `@tailwindcss/vite` | 4.3.3 |
| Icons | react-icons | ^5.5 |
| Lint | ESLint, `eslint-plugin-boundaries` | ^9, ^7.2 |
| E2E and unit tests | Playwright | ^1.63 |

Use pnpm (D-015). Commit `pnpm-lock.yaml` only.

## 2. Layers (Feature-Sliced Design)

| Layer | Path | Role | May import from |
|---|---|---|---|
| `app` | `src/app/` | Bootstrap, store, router, guards, theme | every layer |
| `shared` | `src/shared/` | HTTP client, own UI, lib, i18n, validation | `shared` only |
| `entities` | `src/entities/<domain>/` | API, model and slice per domain | `entities`, `shared` |
| `features` | `src/features/<domain>/<use-case>/` | A use case: `model/use*.ts` and `ui/` | `app` (types only), `entities`, `shared` |
| `widgets` | `src/widgets/<name>/` | Complex composition (PDFs, panels) | `app`, `features`, `entities`, `shared` |
| `pages` | `src/pages/` | Routes | every layer |

`eslint-plugin-boundaries` runs with `default: disallow`, so an illegal import fails lint. The entry point is `src/app/main.tsx`. Path aliases: `@app`, `@shared`, `@entities`, `@features`, `@widgets`, `@pages`.

An entity exposes only its barrel (`index.ts`):

```
entities/product/
├── api/productApi.ts     # uses shared client and tableRequest
├── model/
│   ├── types.ts
│   └── useProduct.ts     # data hooks
└── index.ts              # the only public surface
```

Redux holds only cross-cutting state: `auth`, `toast`, `notifications`. Everything else lives in the API and local hooks.

## 3. HTTP client and session

`shared/api/client` is the only HTTP client. Base URL resolves in this order: `window.__APP_CONFIG__.API_URL`, then `import.meta.env.VITE_API_URL`, then `/api/v1`.

- **Request:** adds `Authorization: Bearer` from the store, `Accept-Language`, and `X-Client-Channel: web` ([identity.md](../modules/identity.md) section 1).
- **Response 401:** one single-flight refresh, then one retry. If the refresh fails, or the account was deactivated (`ACCOUNT_DEACTIVATED`), the session ends.
- **Errors** are normalized to `ApiError { status, code, message, details }`. `fieldErrors()` maps the details to the form's fields.
- **The session** (access token, refresh token and the user) is kept in `localStorage["stockflow.auth"]`, so it survives a reload. Signing out calls `auth/logout` with the refresh token, then clears it.
- **The user** comes from `auth/me` when the web starts. Only the latest answer is applied, so a slow, older answer never overwrites a newer change such as the language.

## 4. Auth, guards and permissions

- `SessionGate` wraps every route. Without a session it sends the person to `/login`. With a pending password change it sends them to `/change-password`, and nowhere else (`sessionRedirect`, in `app/guards`). There is no forgotten-password screen: an administrator resets the password ([identity.md](../modules/identity.md) section 5).
- `SessionGate` loads `/auth/me`, which returns `permissions: { key: scope }`. The menu is built from `APP_SCREENS`, and shows only the screens the person can open.
- `usePermissions()` returns the map, `useCan(key)` returns a boolean, and `<RequirePermission anyOf>` guards a route. A route the person cannot open shows the `403` page.
- `APP_SCREENS` (`entities/permission`) is the single source that maps each screen to its permissions. The access console's "What each role sees" view reads the same source.
- The access console (`/roles`) requires `roles.manage`: roles, the matrix, policies, and what each role sees. A person's roles and exceptions are given from the users screen (`users.permissions`). The audit log is its own screen (`audit.view`).

## 5. Server-side tables

Lists use `POST …/query` with `{ page, limit, filters, sort }`. `tableRequest` and `tableQuery` in `@shared/api/table` send that shape, and they serialize dates as an ISO range in the local time zone. `makeClientTableFetch` gives the same behavior for full client-side lists and is reused by the PDF and CSV exports.

`ITDataTable` takes the filters a screen manages as `externalFilters`, and it merges them with the columns' own before
calling `fetchData`; `reloadTrigger` asks it to read again when one of them changes. A screen that holds the filters of
its last answer — the reports do — can hand exactly those to another call, which is how an export is asked for with the
same filters as the screen ([reports.md](../modules/reports.md) section 4). The file itself is always the API's:
`downloadPost` saves the bytes it answers, with the name from its `Content-Disposition`.

The API contract for this shape is defined in the API docs, which come next.

## 6. i18n

- **Two display locales: `es` (default) and `en`.** The user switches in the top bar, and the choice is saved on their profile. Both locales must have every key. See [i18n/README.md](../i18n/README.md).
- Namespaces per domain: `shared/i18n/locales/{es,en}/<namespace>.json`.
- Keys are English and typed (`i18n.d.ts`). A static key that does not exist fails to compile.
- Keys built at runtime (`status.${row.status}`) go through `dyn()`. Enum values and field names are the keys themselves.
- Roles use `roles.<KEY>` through a `roleLabel` helper. Custom roles show their `name`.

## 7. Validation

`@shared/validation` exposes pure validators that return `string | null`. Each form hook defines `validateField` and `validate`.

## 8. UI: Axzy UI System

Every screen is built from `@axzydev/axzy_ui_system`. Its look, and the look of the app's own markup, come from the design tokens: see [design-system.md](design-system.md).

### Styles in layers

`src/app/index.css` declares the layer order, gives the app's Tailwind build the prefix `sf`, and imports the kit's layered build:

```css
@layer theme, base, axzy-ui-system, components, utilities;

@import "tailwindcss" prefix(sf);
@import "./design-tokens.css";
@import "@axzydev/axzy_ui_system/layered.css";
```

- The kit's classes have no prefix and stay inside the kit's layer. The app's classes are written `sf:…`.
- The two builds never share a class name, so the app's utilities sit above the kit's layer and apply only where the app writes them. CYC ([D-055](../../../CYC/DECISIONES.md)) puts the kit last instead; [design-system.md](design-system.md) section 4 explains the difference.
- The kit ships compiled. The app does not rebuild its utilities with `@source`.
- Do not use `!important` to fix layering. Use the layered build (`layered.css`), not the plain `index.css`.
- `design-tokens.css` is generated from the design file. It is never edited by hand.

### Provider and layout

```tsx
<ITThemeProvider showFab={false} appearance={themeSettings.appearance} shadow={themeSettings.shadow} theme={themeSettings.palette}>
  <App />
</ITThemeProvider>
```

- `themeSettings` (`shared/ui/theme.ts`) is built from the design tokens: `appearance="flat"`, `shadow={1}` and the palette. The kit's `radius` is not set, because the design has four corner sizes.
- `appearance="flat"` gives flat cards, a flat sidebar, and a flat header, as in CYC (D-053).
- `ITLayout` with `sidebarFullHeight` gives a full-height sidebar with the brand at the top. The sidebar starts open and collapses from the top bar's menu button.
- Every page uses `ITPage` with `noPadding`. The layout owns the single content margin.
- Dark mode uses the `.dark` class. The theme persists in `localStorage["it-theme-dark-mode"]`.

### Components used

| Need | Component |
|---|---|
| Page chrome | `ITLayout`, `ITPage` (`title`, `breadcrumbs`, `actions`, `loading`, `error`) |
| Lists | `ITDataTable` (server-side through `fetchData`) |
| Forms | `ITFormBuilder` (JSON-driven) and `ITField` |
| Money and quantities | `ITInputNumber` |
| Dates | `ITDatePicker` |
| Pickers with search | `ITSearchSelect` |
| Dialogs | `ITDialog`, `ITConfirmDialog`. The kit's dialog has no `role="dialog"`, so the app's content inside it carries `role="dialog"`, `aria-modal` and a label (`FormDialog` in `shared/ui`) |
| Indicators | `ITStatCard`, and `KpiTile` from `shared/ui` |
| Sections | `PanelCard` from `shared/ui` |
| Charts | `ITLineBarChart` |

### Tables

- Every column with data has a filter and a sort. Action columns have neither.
- Tables are virtualized by default (`virtualized`, `virtualizedMaxHeight={400}`, `rowHeight={50}`).
- `ITDataTable` has no built-in export. The export button goes in the page's `actions`, and it reuses the active filters.

### Breadcrumbs

Every page that hangs from another one passes `breadcrumbs` to `ITPage` (or `ITPageHeader`): section › detail, with the last
item as plain text and the ones before it linking to their list. Section labels come from `common:nav.*`. A screen that is
reached from a list does not carry a "back" button of its own: the breadcrumbs are the way back.

### Only the kit builds a screen

A screen, a feature or a widget is written **only** with the components of `@axzydev/axzy_ui_system`. Raw HTML elements are
a defect: `div`, `span`, `p`, `dl`, `dt`, `dd`, `table`, `thead`, `tbody`, `tr`, `th`, `td`, `ul`, `ol`, `li`, `h1`-`h6`,
`section`, `article`, `header`, `footer`, `nav`, `aside` and `main` are all forbidden in `src/pages`, `src/features`,
`src/entities` and `src/widgets`, and the lint fails on them. Only `<form>` is allowed outside the kit, because it carries
the `onSubmit` of `FormDialog`.

The lint (`no-restricted-syntax` in `eslint.config.js`) enforces it over the screens that are already converted — the
invoice ones first — and its `files` list grows with each screen, so the rest keep passing while they wait their turn.

| Instead of | Use |
|---|---|
| `div`, `span`, `section`, `article` | `ITStack`, `ITFlex`, `ITGrid`, `ITCard`, `ITDivider` |
| `p`, `h1`-`h6` | `ITText` (with `as`) |
| `ul`, `ol`, `li` | `ITStack` with the items, or `ITTable` when they are rows |
| `table`, `thead`, `tbody`, `tr`, `th`, `td` | `ITTable` (client-side data) or `ITDataTable` (server-side `fetchData`) |
| `dl`, `dt`, `dd` | `ITGrid` with `ITStatCard`, or `ITTable` |
| a label and its control | `ITField` |

`src/shared/ui` is where the app keeps the few primitives the kit does not have (`FormDialog`, `SecretValue`, `useSubmit`).
Anything a screen needs beyond those comes from the kit, never from new raw markup.

## 9. Insecure context

Users reach the web by IP over plain HTTP in some environments, and `crypto.randomUUID` is not available there. Use `newId()` from `@shared/lib`, which relies on `crypto.getRandomValues`. Use `<input type="file">` for uploads.

## 10. Testing

- Unit tests in `tests/unit` and end-to-end tests in `tests/e2e`, both with Playwright.
- Each change runs only the spec it affects. Never the full suite (D-011).
- E2E scenarios come from the API's test-provisioning commands, so the web never writes to the database directly (D-022).
- The end-to-end suite runs against the real API with the profile `e2e` ([testing.md](../rules/testing.md) rule 4). `tests/support/api.ts` signs in as `e2e-admin`, creates the people a test needs, and opens the web already signed in (`signInAs`).
- Tests that change shared data (the matrix, policies) work on data they create, such as their own role, and remove it. They never depend on another test.

## 11. Not adopted yet

These appear in CYC but are not needed by Stock until the flows say so:

- `@react-pdf/renderer` and `file-saver`: needed only for printed documents such as labels or invoices.
- `qrcode`: possibly needed for labels. Decide with the flows.
- Lottie: adopted for the intro and the loader only, from the brand pack. See [brand.md](brand.md).
- Electron packaging: not in the repository.
