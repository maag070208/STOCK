# Design system

**Status:** Adopted, 2026-10-13. **Redesigned to bento, 2026-10-31, web and app.** The web runs the Axzy UI System's `appearance="bento"` over the tokens of `design/stockflow-DESIGN.md`, and the app draws the same language with its own Compose components (`ui/components/Bento.kt`, `BrandArt.kt`, `ui/theme/Bento.kt`). The sections below about structure, tables, forms, states and accessibility still apply; the ones that describe the old surfaces, shell and colors are superseded by section 2.

## 1. Source

The design is one file: [`design/stockflow-DESIGN.md`](../../design/stockflow-DESIGN.md) ("StockFlow Bento"). The product is called StockFlow and the design language is the kit's bento.

The file has two parts:

- **Front matter:** the tokens. Colors, typography, corner radii, spacing, layout measures, motion, elevation, shadows and component specifications.
- **Prose:** how to use them, and what to avoid.

`node scripts/design-tokens.mjs` turns it into `web/src/shared/ui/design-tokens.ts`, `web/src/app/design-tokens.css` and the app's `DesignTokens.kt`. During the migration the web runs it with `--web`, so the app's tokens stay on the previous design until its wave.

### 1.1 The bento appearance does the surfaces

The kit's `appearance="bento"` draws the light canvas (`#F4F4F6`), the borderless white cards with 24px corners and a diffuse shadow, the pill controls, the floating white sidebar and top bar, the ink active item and the large page header. A screen **never** re-implements them: no `bg-white`, no `border`, no `shadow-*`, no custom card radius. The theme (`web/src/shared/ui/theme.ts`) only passes the palette (orange accent, ink neutrals) and the density (`0.8`); it must not pass `radius` or `shadow`, because those replace the bento defaults.

Pastel color enters only through `ITCard tone` or the kit's bento tones (`ITBentoTile`): one tone per meaning, `plain` when the value is zero. The home is a dashboard built with `ITBentoGrid`/`ITBentoStatStrip`/`ITBentoTile`, not a menu of modules.

The **entry screen** is a split page: the form on the canvas on the left, and on the right a flat isometric scene of the product (a shelf with cartons and a scanned label) whose figures float, bob and pulse. The scene is drawn with the design's own colors and animates only while the person allows motion. There is **no language control on the entry screen**: the language is chosen inside the system, in the top bar, and moves to a system settings screen in a later wave.

Nothing in the web or the app chooses a color, a text size or a corner radius by hand. They read the tokens.

## 2. What the design asks for

| Subject | Rule |
|---|---|
| One canvas | A bento canvas holds everything: no dark glass shell, no colored page backgrounds |
| Workspace | Canvas `#f4f4f6`, ink `#14171c`, surface `#ffffff`, muted text `#64748b`, thin borders `#e5e7eb` |
| Shell | A white floating sidebar card and a top bar the color of the canvas, with round white controls and ink text |
| Accent | Orange `#fe6e00`. It carries action, the active state and focus; it is not a surface. The tiles are pastel, one tone per meaning |
| Type | The system sans font, bold headings (800). Numbers are tabular. Labels are 12px semibold |
| Corners | Cards 24px, bento tiles 20px, fields 16px, controls and badges full pills. Never a square card |
| Spacing | A 4px rhythm. Sections 24px apart, cards 24px inside, bento grid 10px apart |
| Layout | Content up to 1440px wide. Top bar 64px. Sidebar 248px, or 72px collapsed |
| Status badges | The pastel tones of the catalogs (`status-*-bg` / `status-*-fg`), one per meaning; a value in zero is `plain` |
| Sign-in | A split page: the form on a white panel, and a flat isometric illustration of the product, animated, on the right. No language control there |
| Dark mode | It exists in full: canvas `#0e1116`, surface `#1a1d24`. The switch lives in the top bar |

## 3. Tokens are generated

`scripts/design-tokens.mjs` reads the design file and writes the token files. It has no dependencies.

```
node scripts/design-tokens.mjs
```

| Output | Used by |
|---|---|
| `web/src/shared/ui/design-tokens.ts` | The Axzy UI System theme, and the tests |
| `web/src/app/design-tokens.css` | The web's Tailwind theme, the component variables and the dark mode block |
| `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/theme/DesignTokens.kt` | The app's theme and components |
| `app/androidApp/src/main/res/values/design_colors.xml` and `values-night/design_colors.xml` | The color of the Android window before the first frame |

Rules:

1. To change the look, edit the design file, run the generator, and commit the design file with the generated files.
2. Generated files are never edited by hand. `tests/ops/design-tokens.test.mjs` fails when one of them does not match the design file.
3. A value that the design file does not have is not added in code. It is added to the design file, or it is listed in section 7 as an adaptation.

In the app's tokens, a component keeps its references resolved: `rgba(0, 0, 0, 0.70)` becomes the color `0xB3000000`, and `6px 12px` becomes vertical and horizontal padding.

## 4. Web

### Two Tailwind builds

The web has two sets of Tailwind classes: the Axzy UI System's, already compiled, and the app's own. The app's build uses the prefix `sf`.

```css
@layer theme, base, axzy-ui-system, components, utilities;

@import "tailwindcss" prefix(sf);
@import "./design-tokens.css";
@import "@axzydev/axzy_ui_system/layered.css";
```

- An app class is written `sf:flex`, `sf:lg:grid-cols-2`, `sf:bg-surface-elevated`. Its theme variables are `--sf-color-*`, `--sf-radius-*`, and so on.
- The two builds never emit a class or a variable with the same name, so the app's utilities can sit **above** the kit's layer. They apply only where the app writes them.
- A class without the prefix is the kit's, or it does nothing. `tests/unit/design-classes.spec.ts` fails on one.

This differs from CYC ([D-055](../../../CYC/DECISIONES.md)), which puts the kit's layer last. With the kit last, the kit's reset (`h1 { font-size: inherit }`, `select { font: inherit }`) removed the app's own text sizes and borders. The prefix solves the problem D-055 solved, two builds fighting over one class name, and lets the app's utilities win where the app uses them.

### The theme holds only the design

The generated theme clears Tailwind's own colors, radii, shadows, fonts and text sizes. `sf:bg-red-500` or `sf:text-sm` produce nothing. What exists:

| Kind | Classes |
|---|---|
| Colors | `sf:bg-background`, `sf:text-on-surface-muted`, `sf:border-outline-strong`, `sf:bg-primary`, and one per color in the design |
| Type | `sf:text-display-hero`, `sf:text-headline-xl`, `sf:text-headline-lg`, `sf:text-title-md`, `sf:text-body-lg`, `sf:text-body-md`, `sf:text-body-sm`, `sf:text-label-md`, `sf:text-label-sm`, `sf:text-code-sm`. Each sets size, line height, weight and tracking |
| Corners | `sf:rounded-xs` 4px, `sf:rounded-sm` 6px, `sf:rounded-md` 8px, `sf:rounded-lg` 12px, `sf:rounded-xl` 16px, `sf:rounded-2xl` 20px, `sf:rounded-3xl` 24px, `sf:rounded-pill` for controls and badges |
| Shadows | `sf:shadow-subtle`, `sf:shadow-raised`, `sf:shadow-dialog` |
| Spacing | Tailwind's number scale, which is the design's 4px rhythm: `1` = xs 4px, `2` = sm 8px, `3` = md 12px, `4` = lg 16px, `6` = xl 24px, `8` = 2xl 32px, `10` = 3xl 40px, `16` = 4xl 64px |

Layout measures, motion, elevation and component specifications are plain variables: `--sf-layout-container-max`, `--sf-motion-normal`, `--sf-elevation-shell-blur`, `--sf-c-nav-item-rounded`.

### Axzy UI System

The kit draws the shell and, from F1, the tables, forms and dialogs. It receives the design in three ways:

1. **Palette and appearance** (`shared/ui/theme.ts`): the accent, the semantic colors, the neutral table colors and the shell, passed to `ITThemeProvider` with `appearance: "bento"` and `density: 0.8`. `radius` and `shadow` are **not** passed: the bento defaults (24px cards, diffuse shadow) are the design.
2. **The app's own CSS** (`app/index.css`): the canvas and type from the tokens; the kit's text colors mapped to the design's ink; the tabular-figures hook; the dashboard entrance animation; and the entry illustration's keyframes.
3. **Two small overrides** (`app/index.css`), each one a candidate for a kit variable:

| Override | Reason |
|---|---|
| `max-width: none` on the page column (`main > div > .max-w-7xl`) | `ITPage` centers in 80rem, which at density 0.8 is 1024px; the design's column is the shell's 1440px |
| Accent focus ring on the sidebar rail | The browser's default ring does not match the design |

The sidebar starts open, and the menu button in the top bar collapses it (`expandOnHover: false`, controlled by `AppShell`). The content container is capped with `contentClassName="sf:max-w-(--sf-layout-container-max)"`.

## 5. App

The app's interface has **no Material**: the dependency is removed. Every screen is drawn with Compose foundation and the pieces below. `tests/ops/app-design.test.mjs` fails if a Material artifact or import returns, or if a color is typed by hand outside `DesignTokens.kt`.

| Piece | File | What it is |
|---|---|---|
| `StockFlowTheme`, `StockFlow.colors` | `ui/theme/StockFlowTheme.kt` | The workspace colors, light and dark, from the tokens. Follows the system setting |
| `StockFlowType` | `ui/theme/StockFlowTheme.kt` | The ten type styles of the design, in the system sans font |
| `AppText` | `ui/components/AppText.kt` | The only way the app draws text: a type style and a color |
| `BentoScreen`, `BentoTopBar`, `BentoPageHeader`, `BentoIconButton`, `BentoTile`, `BentoStat`, `BentoLabel`, `BentoSectionTitle` | `ui/components/Bento.kt` | The bento shell: the canvas, the top bar of round white controls and the title, the tiles and the small counters |
| `BrandIllustration` | `ui/components/BrandArt.kt` | The flat isometric illustration of the entry screen, animated with Compose |
| `ShellStatusBar` | `ui/components/Shell.kt` | The strip behind the status bar, in the canvas color; the icons follow the mode |
| `Card` | `ui/components/Card.kt` | The bento card: a white 24dp surface, no border, 24dp inside |
| `StatusBadge`, `StatusTone` | `ui/components/StatusBadge.kt` | The five status tones, each read from its component in the design |
| `TextInput`, `PrimaryButton`, `ShellAction` | `ui/components/Inputs.kt` | The surface input, the primary button (`buttonPrimary`, pressed `buttonPrimaryHover`) and the text action of the shell (`buttonGhostShell`) |
| The brand mark | `composeResources/drawable/stockflow_glyph_{light,dark}.xml` | The master glyph as vector drawables, written by `scripts/brand-vectors.mjs`. Same paths and colors; only the frame is cropped to the art |

Rules:

1. A screen takes colors from `StockFlow.colors`, text styles from `StockFlowType`, and sizes from `DesignTokens.Spacing`, `DesignTokens.Layout` and `DesignTokens.Components`.
2. A new component is added to `ui/components` when a screen needs it, and it reads its specification from `DesignTokens.Components` when the design has one (`buttonPrimary`, `inputShell`, `navItem`, `tableHeader`).
3. The shell is dark in both modes, so the status bar always shows light icons. Android sets this in `MainActivity`, and iOS in `Info.plist`.
4. The Android window opens in the workspace color (`Theme.StockFlow`), light or dark, so the app does not open on a flash.

## 6. Dark mode

The design names four dark colors. They replace these roles, on the web and in the app. The generator holds this table, so both sides agree.

| Role | Dark value |
|---|---|
| `background` | `dark-background` `#413830` |
| `surface`, `surface-soft`, `surface-elevated` | `dark-surface` `#4a423a` |
| `on-background`, `on-surface` | `dark-on-surface` `#fafaf9` |
| `on-surface-muted` | `dark-on-surface-muted` `#b9b3ac` |
| `outline`, `outline-strong` | The shell's border: white at 10%. The design gives no dark outline |

Orange, the semantic colors, the status badges and the shell are the same in both modes. Both the web and the app follow the system setting.

## 7. Adaptations

The design was written for another product, on the desktop. These are the places where StockFlow differs, and why.

| Subject | Design | StockFlow |
|---|---|---|
| Name | Evreghen Command Center | StockFlow. The design's name stays on the file only |
| Sign-in | A 3D render on the white half | A flat isometric illustration of the warehouse drawn with the design's colors, with its figures floating; the wordmark sits above the form |
| Artwork colors | Orange and warm neutrals only | Icons and Lottie animations keep the brand pack's navy, orange and turquoise. Artwork is not edited ([brand.md](brand.md) rule 1) |
| Navigation label, web | 13px bold | The kit sets 14px and weight 600 inline. It stays until the kit takes a variable for it |
| Layout, app | White floating sidebar and top bar cards | The app is the bento look in Compose: the canvas, a top bar of round white controls with the title, and white 24dp cards below (no hero). Fields are filled 16dp pills, buttons are pills, status is a pastel chip; the entry screen carries the same animated illustration as the web, drawn with `Canvas` (`BrandIllustration`). The bento tokens live in `AppBento` (`ui/theme/Bento.kt`) |
| Page padding, app | 32px | 16dp at the sides of a phone |
| Card shadow, app | Soft shadow and thin border | The card is a white 24dp surface with no border and no drawn shadow: the canvas separates it |
| Card shadow, web | Soft shadow | Not used yet: the first cards arrive with F1 |
| Surface inputs | "Neutral border", no height given | Web: the kit's filled pill. App: a 52dp field filled with `surface-soft` and 16dp corners, an icon on the left and the label inside while it is empty; an orange ring with the cursor, a red one with an error; an eye shows a password |
| Touch targets, app | Buttons 40px, shell input 36px | 56dp pills, as in the reference, above the 48dp a thumb needs |
| Sign-in, app | Two halves side by side | The hero and sheet layout above, with the form on the sheet under a centered title. It keeps the light look in dark mode, because it is the brand moment |
| Dark mode, app | — | The hero, the status bar strip and the brand tile stay orange and white; the sheet and its fields take the dark workspace colors |
| Disabled button | — | No dimmed button. The button stays orange; an empty field says it is required when the form is sent |
| API status on the app's home | — | Gray while checking, green when connected, yellow when the API fails or cannot be reached |

## 8. Tests

| Test | Checks |
|---|---|
| `tests/ops/design-tokens.test.mjs` | The generator reads the design file, and every generated file is up to date |
| `tests/ops/app-design.test.mjs` | The app has no Material and no color typed by hand; the Android window uses the design's color |
| `web/tests/unit/theme.spec.ts`, `design-classes.spec.ts` | The kit's theme comes from the tokens; every class has the prefix; the layer order is the one in section 4 |
| `web/tests/e2e/design.spec.ts` | Computed styles in the browser against the tokens: canvas, white shell cards, sizes, active item, the split entry screen, type, dark mode, phone |
| `app/shared/src/commonTest/.../ui/StockFlowThemeTest.kt` | Light and dark roles, type conversion, status tones. Runs on Android and on iOS |
| `tests/e2e-app/android-design.test.mjs` | The pixels of the Android screen against the design file, in light and dark mode |

The iOS screens are checked by eye on the simulator, in light and dark mode, until the flows have XCUITest runs.

## 9. Open questions

1. **`theme-color` of the web.** It is still the brand pack's `#F2F6FC` and `#0C1730`. The design's values would be the shell color. To confirm.
2. **Brand pack against the design.** The pack's interface colors (navy `#16315F`, orange `#F28C28`, turquoise `#3A86A8`) are no longer used for the interface, only inside the artwork. To confirm, or to ask the designer for artwork in the design's orange.
3. **Status badges for business statuses.** The design's five tones were made for other statuses. Proposal, to confirm:

| Status | Tone |
|---|---|
| Invoice `OPEN`, order `CREATED` | Gray (planned) |
| Invoice `PENDING` | Yellow (mock) |
| Invoice `IN_ASSIGNMENT` | Blue (development) |
| Order `FULFILLING` | Blue (development) |
| Order `FULFILLED` | Lavender (integrated) |
| Invoice `CLOSED`, order `VALIDATED` | Green (production) |
| Invoice `CANCELLED`, order `CANCELLED` | Red (`danger`) |

4. **Navigation in the app.** The design has no bottom bar or drawer. The pattern is chosen in F1, when the app has more than one screen.
