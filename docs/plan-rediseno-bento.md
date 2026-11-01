# Bento redesign: web and app

**Status:** in progress, 2026-10-31. **Web wave done** (tokens, shell, home, entry screen and every module screen restyled); the **app wave** is done for the shell, the home, the module menu and the entry screen, and every other screen inherits it through `PageScaffold` and the shared components. The design language is the Axzy UI System's `appearance="bento"`, the same one the reference projects use (`~/DEV/PTNV/RESPONSIVA/web` and `.../PuertoNuevoApp`).

## 1. Decisions

- **One look, both platforms.** Light-first bento: soft gray canvas, borderless white cards with 24px corners, pastel tiles, pill controls and bold typography.
- **Orange stays the brand.** The accent is StockFlow's orange (`#FE6E00`), not the reference teal: primary buttons, links, focus and the active pill of a few controls. The pastel tiles keep the kit's tones (`sea`, `lavender`, `mint`, `sun`).
- **The kit does the surfaces.** The theme only passes the palette and the density. `radius` and `shadow` are **not** passed: the bento defaults (24px, diffuse shadow) are the design.
- **Web first, then the app.** The web validates the language on real screens; the app ports the same tokens and component vocabulary.
- **Density 0.8.** The rem scale is 80%, like the reference; the bento grid rows stay 84px.

## 2. Source of truth

- [`design/stockflow-DESIGN.md`](../design/stockflow-DESIGN.md): tokens + normative prose ("StockFlow Bento").
- `node scripts/design-tokens.mjs` generates the web tokens (`--web` while the app is still on the previous design) and, when the app wave starts, the app's `DesignTokens.kt`.
- `web/src/shared/ui/theme.ts` maps the tokens to the kit's palette.
- The kit: `@axzydev/axzy_ui_system@1.8.15`.

## 3. Waves

### Wave 1 — web foundation (done)

- Design document and token pipeline; `appearance="bento"`, density 0.8, no `radius`/`shadow`.
- Shell: `ITLayout` with `variant="bento"` sidebar and top bar, profile at the bottom, white floating cards over the canvas.
- Home: dashboard with the greeting, the invoice status strip (`ITBentoStatStrip`, click filters the list), pastel tiles with real counts per permission, latest invoices and quick actions.
- Entry screen: split page, the form on the canvas on the left and an animated flat illustration of the product on the right (no language control there; the language lives in the top bar). The Android entry has the same illustration, animated with Compose.
- Invoice list/detail: status colors moved to the entity, the detail's lines table in a card, chips and status badges.
- Report pages: pill tabs, filters and totals inside a card.
- Access and stock: the kit's pill tabs.
- Tests: `design.spec` rewritten to the bento contract; greeting assertions use the first name; the app's own CSS hooks are declared (`sf-shell`, `sf-on-primary`).

### Wave 2 — web screens (done)

Every module screen inherits the appearance; the audit checked products, stock, orders, order detail, reports, access, users, catalogs, labels, discrepancies, warehouses, suppliers/customers and the audit log. Fixes found in the audit: pill tabs, filter cards, table cards, the entry screen.

Known polish left for the end of the wave: the `Nuevo rol` action inside the access panel could move to the page header; the home could grow a third row (recent activity) when the data justifies it.

### Wave 3 — app (in progress)

1. Tokens (done): `node scripts/design-tokens.mjs` regenerated the app's `DesignTokens.kt` from the bento document; `ui/theme/Bento.kt` adds the tones, the ink, the field and the 24/20/16dp shapes, and the app compiles with its host tests green. The shell, the cards, the rows, the fields and the buttons were restyled, the bento components were added (`ui/components/Bento.kt`, `BrandArt.kt`), and the home and the entry screen were migrated; the other screens use the same `PageScaffold` and inherit the look.
2. Components to port from the reference app (`~/DEV/PTNV/RESPONSIVA/PuertoNuevoApp/shared/src/commonMain/kotlin/.../core/`): `Bento.kt` (tile, pill button, chips), `BentoBottomBar`, `BentoTopBar`, `BentoDrawer`, `BentoTabs`, `BentoCharts` (gauge, waves, spark, donut), `BentoState`, and the bento `AppTextField`.
3. Screens in waves: shell + home first (with the same tiles as the web home), then the receiving flow (count/placement), orders, materials, stock and labels.
4. The design e2e on Android (`tests/e2e-app/android-design.test.mjs`) moved to the bento contract: canvas color, pastel task tiles, white cards and the entry illustration; `android-home` and `android-intro` pass on the emulator, and the light and dark design checks pass.

### Wave 3 — done

The app runs the bento look: tokens regenerated from the same document, `Bento.kt` and `BrandArt.kt` components, the shell (`BentoScreen`), white cards, filled pill fields, pastel status chips, the dashboard home and the animated entry illustration. The host tests are green and the device suites (`android-home`, `android-design` in light and dark, `android-intro`) pass on the emulator.

### Wave 4 — close

Dark mode reviewed on both platforms, the previous design removed from the docs, and the "one look" checklist in `design-system.md` updated.

## 4. Rules that broke things (and how they are handled)

- Passing `shadow` to `ITThemeProvider` disables `--it-bento-card-shadow`: never pass it.
- The app's Tailwind uses the `sf:` prefix **before** the variant (`sf:xl:grid-cols-3`, `sf:hover:bg-...`), never after.
- Tailwind clears `--color-*` and `--text-*`: `bg-white`, `text-slate-*` or `text-sm` do not exist. Use the token utilities (`sf:text-title-md`, `sf:bg-surface-soft`).
- The kit's `--color-*` variables live in its layer and win over the app's `@theme`; the app's own CSS and inline styles use the design's names (`--sf-color-*`).
- `ITPage` centers in `max-w-7xl` (80rem, 1024px at density 0.8); `index.css` scopes it to the shell's container so pages use the design's 1440px column.
- Raw HTML is forbidden in screens (lint): use `ITFlex`, `ITStack`, `ITGrid`, `ITText`, `ITButton`, `ITCard`, `ITTable`. App-level CSS hooks are declared in `tests/unit/design-classes.spec.ts`.
- Playwright runs share `test-results` and the teardown empties the database: run unit and e2e suites in sequence, never in parallel.

## 5. Verification

```
cd web && pnpm run build && pnpm run lint && pnpm run test:unit
cd web && pnpm exec playwright test --project=e2e
cd app && ./gradlew :shared:testAndroidHostTest     # after the app wave
```

The screens were reviewed on screenshots of every route at 1440×900 and 390×844 (`/tmp/stockflow-tour`, `/tmp/stockflow-shots`), signed in as the development administrator.
