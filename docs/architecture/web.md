# Web

**Status:** Accepted. Adopts the web standard of CYC ([web-fsd.md](../../../CYC/docs/arquitectura/web-fsd.md), [axzy-ui-system.md](../../../CYC/docs/arquitectura/axzy-ui-system.md), D-007, D-008, D-053, D-055). Versions are taken from CYC's `web/package.json` and get checked when the project is created.

## 1. Stack

| Concern | Choice | Version |
|---|---|---|
| UI library | React | ^19.2 |
| Build | Vite, TypeScript | ^8.3, ~6.0 |
| Global state | Redux Toolkit, react-redux | ^2.5, ^9.2 |
| Routing | React Router, `HashRouter` | ^7.18 |
| HTTP | axios | ^1.20 |
| i18n | i18next, react-i18next | ^26.4, ^17.0 |
| Validation | yup | ^1.6 |
| UI kit | `@axzydev/axzy_ui_system` | 1.6.0 (exact: the app and the kit move together) |
| Styles | Tailwind CSS via `@tailwindcss/vite` | 4.3.3 |
| Icons | react-icons | ^5.7 |
| Lint | ESLint, `eslint-plugin-boundaries` | ^9.39, ^7.2 |
| E2E and unit tests | Playwright | ^1.64 |

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

The API contract for this shape is defined in the API docs, which come next. How a table of a screen is configured, and
what a list owes the person reading it, is in 8.7.

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

### 8.1 Styles in layers

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

### 8.2 Provider and layout

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

### 8.3 Components used

| Need | Component |
|---|---|
| Page chrome | `ITLayout`, `ITPage` (`title`, `description`, `breadcrumbs`, `actions`, `loading`, `error`) |
| Lists | `ITDataTable` (server-side through `fetchData`) |
| Forms | `ITInput`, `ITSelect`, `ITSearchSelect`, `ITDatePicker`, `ITTextarea`, `ITInputNumber` |
| Money and quantities | `ITInputNumber` |
| Dates | `ITDatePicker` |
| Pickers with search | `ITSearchSelect`, and the pickers of `shared/ui`-adjacent entities (`ProductPicker`, `PartnerPicker`, `WarehousePicker`) |
| Dialogs | `ITDialog`, `ITConfirmDialog`. The kit's dialog has no `role="dialog"`, so the app's content inside it carries `role="dialog"`, `aria-modal` and a label (`FormDialog` in `shared/ui`) |
| Facts of a record | `FactStrip` from `shared/ui` |
| Indicators | `ITStatCard`, and `KpiTile` from `shared/ui` |
| Sections | `PanelCard` from `shared/ui` |
| Charts | `ITLineBarChart` |

**The rules of a screen, module by module.** They are the rules the invoice screens were built with, written down so the
next module does not invent its own. They are not suggestions: the lint fails on the first one, the tests hold them, and a
screen that breaks them is a defect — the same as a screen that does not save.

### 8.4 Only the kit builds a screen

A screen, a feature or a widget is written **only** with the components of `@axzydev/axzy_ui_system`. Raw HTML elements
are a defect: `div`, `span`, `p`, `dl`, `dt`, `dd`, `table`, `thead`, `tbody`, `tr`, `th`, `td`, `ul`, `ol`, `li`, `h1`-`h6`,
`section`, `article`, `header`, `footer`, `nav`, `aside` and `main` are all forbidden in `src/pages`, `src/features`,
`src/entities` and `src/widgets`, and the lint fails on them. Only `<form>` is allowed outside the kit, because it carries
the `onSubmit` of `FormDialog`.

The lint (`no-restricted-syntax` in `eslint.config.js`) enforces it over every layer, and `<form>` is the single
exception. There is no list of converted files to grow: the rule is the whole of `src`.

| Instead of | Use |
|---|---|
| `div`, `span`, `section`, `article` | `ITStack`, `ITFlex`, `ITGrid`, `ITCard`, `ITDivider` |
| `p`, `h1`-`h6` | `ITText` (with `as`) |
| `ul`, `ol`, `li` | `ITStack` with the items, or `ITTable` when they are rows |
| `table`, `thead`, `tbody`, `tr`, `th`, `td` | `ITTable` (client-side data) or `ITDataTable` (server-side `fetchData`) |
| `dl`, `dt`, `dd` | `FactStrip` (the facts of a record) or `ITTable` |
| a label and its control | `ITField` |
| an icon that is a button | `IconButton` (`shared/ui`) |

`src/shared/ui` is where the app keeps the few primitives the kit does not have (`FormDialog`, `FactStrip`, `IconButton`,
`PlainTable`, `SecretValue`, `useSubmit`). Anything a screen needs beyond those comes from the kit, never from new raw
markup. If the kit lacks something, it is added to the kit (and its version here goes up), not worked around with a `div`.

### 8.5 The header of a page

| Part | Rule |
|---|---|
| Breadcrumbs | **Every** `ITPage` passes `breadcrumbs`. The trail starts at Home (`trail()` in `shared/lib/breadcrumbs`), then the sections the page hangs from, and the page itself last. The last crumb is plain text; the ones before it navigate with the router (`onClick`), because the app is a `HashRouter` and an `href` would leave it. |
| A list of the first level | `Inicio › Impresoras`. When the section *is* the page, the section crumb is left out: "Inicio › Impresoras › Impresoras" is a defect. |
| A report, or a page inside a section | `Inicio › Reportes › Kardex`. |
| A record | `Inicio › Facturas › FF-187699C31A`. |
| Title | The **code or the number** of the record (`FF-187699C31A`), or the name of the list (`Facturas`). |
| Description | The name of the record (`Aceros cd4aed`). Never "code · name" in the title: it is what the breadcrumb already says, and it wraps on a narrow window. |
| Facts | The facts of a record go in **one slim card** (`FactStrip`): a small uppercase label and its value, four to a row (two when one of them is long, such as an address). A row of tall `ITStatCard`s is a defect: a number ends up shouting louder than the title. |
| Summary | What the page holds ("1 línea · 100 piezas") is a fact of the strip. It is not repeated above the table. |
| Actions | The page's actions, at the end of the header. The **badge of the state and the action that changes it** go together here (8.6). |

### 8.6 Buttons and icons

1. **A table's action is an icon.** The buttons of a row carry no text: only the icon, with the name on `ariaLabel` and
   `title` through [`IconButton`](../../web/src/shared/ui/IconButton.tsx) — the words a person reads in the tooltip are
   the words a screen reader and a test find. A row menu (`ITDropdownMenu`) puts an icon in its `trigger` with the name
   in `triggerLabel`. A page action, a dialog button or a form button keeps its text: those are not table cells.
2. **The icon says what it does.** The same action carries the same icon in every module:

   | Action | Icon |
   |---|---|
   | Give something to a person, or move it to another | a **person** (`FaUserPlus` when nobody has it, `FaUserEdit` when it moves) |
   | Settle a difference | `FaBalanceScale` |
   | Take a row away | `FaTrash`, in `danger` |
   | Edit a record | `FaEdit` |
   | More actions of a row | `FaEllipsisV` in the `trigger` of `ITDropdownMenu` |
   | Go to a record | `FaEye` |

   An exchange of arrows to mean "assign to a person" is a defect: it says nothing about what is being assigned.
3. **A button in a row of fields lines up with the boxes, not with the labels.** Its grid cell carries `className="sf:self-end"`.
4. **A screen ends with room.** The bottom margin belongs to the layout (`.sf-pb-12` on its content), so no page has to
   remember it and no action ends up glued to the edge of the window.
5. **A list of more than four choices is searched.** `ITSelect` is for the short, closed lists: two or three options such
   as a language, an effect or a scope. Anything a person would have to scan — materials, warehouses, suppliers,
   customers, operators, users, permissions, catalog values — is an `ITSearchSelect`, which filters as the person types,
   and when the choices come from the API and can be hundreds the picker **searches the API** instead of bringing a page.
6. **The action of the state lives with the state.** A screen whose main action changes the state of what it shows (close
   an invoice, validate an order, cancel it) puts that action in the page header, next to the badge of its state, not at
   the end of the content.

### 8.7 Tables

Every table — the `ITDataTable` of a server-side list, and the `PlainTable` a screen uses for the rows it already holds —
carries the same configuration, so a list looks and behaves the same everywhere:

```tsx
defaultItemsPerPage={25}
itemsPerPageOptions={[25, 50, 100]}
layout="fixed"
density="compact"
virtualized
virtualizedMaxHeight={400}
rowHeight={50}
showViewToggle={false}
```

1. **One table per screen.** A record with lines shows one table, not a table of lines and another of what happened to
   them. If two things describe the same row, they are columns of that row.
2. **One row per thing, and the work happens in the row that shows it.** The invoice's line that nobody has been given is
   a row that reads "Sin asignar", with the same actions as the others (give it to a person, settle a difference, take it
   away). A separate section for "what is left to do" is a defect.
3. **Every column with data has a filter and a sort.** Action columns have neither, and their buttons are icons (8.6).
4. **The page size is 25** because a list is read in pages, not scrolled whole. A screen or a test that needs a row
   further down has to ask for it: a filter, a search, the next page, or the size selector. The list of a table that
   grows without limit (printers, warehouses, partners) is **searched by the columns a person knows** — and the e2e
   database grows every run, so a test that assumes "it is on the first page" fails later for a reason that has nothing
   to do with the app.
5. **A record's table is read whole when it is a matrix.** When a table is read as a whole (what each role sees), it is
   scrolled to its end instead of hidden behind the virtualized height: the tests scroll the container, and the page
   remembers that the last rows are one wheel away.
6. `ITDataTable` has no built-in export. The export button goes in the page's `actions`, and it reuses the active filters
   of the last answer.

### 8.8 Forms, dialogs and the flow

1. **A dialog is `FormDialog`** (`shared/ui`): `ITDialog` with the app's `<form role="dialog">` inside, one submit, and
   one `Cancelar`. The kit's dialog is the frame; the content is the kit plus that form.
2. **The keyboard never leaves the modal.** Tab wraps inside the **whole dialog of the kit** (`[data-it-dialog="true"]`,
   its close button included), not only inside the form: the trap listens on the document while the dialog is open,
   `Escape` closes, and the focus returns to the button that opened it.
3. **Short labels.** A label is the noun, without a sentence: "Número", "Proveedor", "Fecha", "Almacén". The long
   explanation goes in the field's help text or in the tooltip, never in the label of a form that is filled every day.
4. **One primary action.** A dialog has one submit with the verb of what it does ("Guardar", "Cerrar factura", "Imprimir").
   A second button is `Cancelar` and nothing else.
5. **A save that continues the flow leads to the next step.** Creating an invoice does not return to the list: it opens
   the invoice, with the focus on capturing its materials. The list of a flow that has a next step never asks the person
   to find the record again.
6. **What the form needs is said while it is filled.** A form that must add up (the shares of a line) says what is left
   or what is over ("Faltan 50 por asignar") as the person types, and keeps its submit closed until it is right. A new
   row starts with the amount that is still missing.
7. **What is missing takes the focus.** A required field that is empty takes the focus instead of showing an error nobody
   asked for, and the API's message goes under its field, in the language of the screen.
8. **The form empties itself when the work is done.** A capture bar (the materials of an invoice) clears the search and
   the quantity after adding, and turns the browser's autocompletion off: the suggestions of a former record are noise in
   a bar that is used with one hand and the keyboard.
9. **A state that filters the list lives in the URL.** The chips of the invoice list ("Abierta", "En asignación",
   "Cerrada") are search parameters, so a filtered list can be shared, reloaded and tested.

### 8.9 Text and language

There is no text in the code: every word comes from `i18next` (`es` and `en`, the same keys, the same meaning). A label is
short, in sentence case, and says what the thing is; a message says what happened and what to do next. Numbers, dates and
quantities are formatted with the helpers of `shared/lib/format` (the business day included), never with the browser's
default.

### 8.10 What the tests can hold on to

A screen that follows these rules is also a screen a test can drive without guessing:

| Hook | For |
|---|---|
| `role` and the accessible name | every button, dialog, row and cell: the tests use `getByRole` |
| `input[name="..."]` | the fields of a form (`supplierId`, `capture-quantity`, `share-0`) |
| `[data-it-dialog="true"]` | the frame of a modal, for the focus trap |
| `[data-it-floating="true"]` | the panel of a search select, wherever the kit portaled it |
| `select[name="itemsPerPage"]` | the page size of a table |
| `data-testid` | only where nothing else identifies the piece (`quick-capture`) |

A test types into a search select the words a person types, and clicks the option in the panel that holds it. It never
reaches for a raw element of the kit's internals (`div.max-h-60` and its kind): that is the kit's business, and it changes
with the kit.

## 9. Insecure context

Users reach the web by IP over plain HTTP in some environments, and `crypto.randomUUID` is not available there. Use `newId()` from `@shared/lib`, which relies on `crypto.getRandomValues`. Use `<input type="file">` for uploads.

## 10. Testing

- Unit tests in `tests/unit` and end-to-end tests in `tests/e2e`, both with Playwright.
- Each change runs only the spec it affects. Never the full suite (D-011). A run of the whole suite happens before a
  release, and it is expected to take a couple of minutes and nothing more: a suite nobody waits for is a suite nobody
  runs.
- E2E scenarios come from the API's test-provisioning commands, so the web never writes to the database directly (D-022).
- The end-to-end suite runs against the real API with the profile `e2e` ([testing.md](../rules/testing.md) rule 4). `tests/support/api.ts` signs in as `e2e-admin`, creates the people a test needs, and opens the web already signed in (`signInAs`).
- Tests that change shared data (the matrix, policies) work on data they create, such as their own role, and remove it. They never depend on another test.
- **Three tests at a time, ten seconds for an element, thirty for the test.** Past three workers, the tests of one shared
  database slow each other into timeouts that have nothing to do with the app; a test that needs longer than ten seconds
  for an element is telling us the screen is slow, or the expectation is wrong.
- **One suite at a time.** Two Playwright runs against the same API and the same database fail each other, and the
  failure looks like a defect of the screen.
- **The e2e database grows with every run** and is never reset. Nothing may assume a row is on the first page: a test
  finds what it created by filtering, searching or naming it so it sorts first, and the screens that list what grows
  without limit are searched (8.7). This is the first cause of a "flaky" test, and it is not flakiness: it is a list that
  got longer.
- The tests drive the screen the way a person does (8.10): by role, by label and by name, typing into a search select and
  clicking the option. A hook of the kit's internals is not a hook of the app.

## 11. Not adopted yet

These appear in CYC but are not needed by Stock until the flows say so:

- `@react-pdf/renderer` and `file-saver`: needed only for printed documents such as labels or invoices.
- `qrcode`: possibly needed for labels. Decide with the flows.
- Lottie: adopted for the intro and the loader only, from the brand pack. See [brand.md](brand.md).
- Electron packaging: not in the repository.
