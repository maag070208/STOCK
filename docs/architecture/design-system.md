# Design system

**Status:** Adopted, 2026-10-13. The owner supplied the design document and asked for it on the web and on the app. The app uses nothing else: no Material components and no earlier look.

## 1. Source

The design is one file: [`design/command-center-DESIGN.md`](../../design/command-center-DESIGN.md). It is an exact copy of the owner's document, "Evreghen Command Center". That name is the name of the design language. The product is still called StockFlow.

The file has two parts:

- **Front matter:** the tokens. Colors, typography, corner radii, spacing, layout measures, motion, elevation, shadows and component specifications.
- **Prose:** how to use them, and what to avoid.

Nothing in the web or the app chooses a color, a text size or a corner radius by hand. They read the tokens.

## 2. What the design asks for

| Subject | Rule |
|---|---|
| Two layers | A light, warm workspace. Around it, a dark shell that looks like glass |
| Workspace | Canvas `#fcfaf7`, text `#423d38`, muted text `#797067`, thin borders `#e3e0dd` |
| Shell | Black at 70% opacity, 12px blur, a white border at 10%, white text. Never fully opaque |
| Accent | Orange `#fe6e00`. It carries action, the active item, focus and brand. Most surfaces stay neutral |
| Type | The system sans font. No web font. Module titles are 1.5rem bold. Small labels are uppercase with wide tracking |
| Corners | Inputs 6px, controls 8px, cards 12px, badges fully round. The shell panels are square |
| Spacing | A 4px rhythm. Sections 24px apart, cards 24px inside, card grids 16px apart |
| Layout | Content up to 1400px wide. Top bar 64px. Sidebar 256px, or 64px collapsed |
| Status badges | Five soft tones: yellow, gray, blue, lavender and green |
| Sign-in | Two equal halves: a white panel and an orange panel with a white card |
| Dark mode | It exists. The identity stays in the light workspace with the dark shell |

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
| Corners | `sf:rounded-xs` 4px, `sf:rounded-sm` 6px, `sf:rounded-md` 8px, `sf:rounded-lg` 12px, `sf:rounded-full` for badges |
| Shadows | `sf:shadow-subtle`, `sf:shadow-raised`, `sf:shadow-dialog` |
| Spacing | Tailwind's number scale, which is the design's 4px rhythm: `1` = xs 4px, `2` = sm 8px, `3` = md 12px, `4` = lg 16px, `6` = xl 24px, `8` = 2xl 32px, `10` = 3xl 40px, `16` = 4xl 64px |

Layout measures, motion, elevation and component specifications are plain variables: `--sf-layout-container-max`, `--sf-motion-normal`, `--sf-elevation-shell-blur`, `--sf-c-nav-item-rounded`.

### Axzy UI System

The kit draws the shell and, from F1, the tables, forms and dialogs. It receives the design in three ways:

1. **Palette** (`shared/ui/theme.ts`): accent, semantic colors and the neutral table colors, passed to `ITThemeProvider`. `appearance` is `flat` and `shadow` is `1`. The kit's `radius` is not set, because it gives one value to every corner and the design has four.
2. **The kit's public variables** (`app/index.css`): `--it-sidebar-*`, `--it-topbar-*` and `--it-layout-bg` take the shell colors. They are set in CSS because, in light mode, the kit turns a dark shell white.
3. **Overrides that depend on the kit's markup** (`app/index.css`, under `.sf-shell`). They are few, and each one is a candidate for a public variable in the kit:

| Override | Reason |
|---|---|
| `backdrop-filter` on `aside` and `header` | The kit blurs its shell only in the `soft` look, which also adds glows |
| Width of `.w-[248px]` and `.w-[72px]` | The kit's flat sidebar is 248px and 72px. The design asks for 256px and 64px |
| Corner and padding of `aside nav [role="button"]` | The kit draws navigation items as pills with its own padding |

The sidebar starts open, and the menu button in the top bar collapses it (`expandOnHover: false`, controlled by `AppShell`). The content container is capped with `contentClassName="sf:max-w-(--sf-layout-container-max)"`.

## 5. App

The app's interface has **no Material**: the dependency is removed. Every screen is drawn with Compose foundation and the pieces below. `tests/ops/app-design.test.mjs` fails if a Material artifact or import returns, or if a color is typed by hand outside `DesignTokens.kt`.

| Piece | File | What it is |
|---|---|---|
| `StockFlowTheme`, `StockFlow.colors` | `ui/theme/StockFlowTheme.kt` | The workspace colors, light and dark, from the tokens. Follows the system setting |
| `StockFlowType` | `ui/theme/StockFlowTheme.kt` | The ten type styles of the design, in the system sans font |
| `AppText` | `ui/components/AppText.kt` | The only way the app draws text: a type style and a color |
| `ShellStatusBar`, `ShellTopBar` | `ui/components/Shell.kt` | The shell: black glass behind the status bar, and the 64dp top bar with its faint border |
| `Card` | `ui/components/Card.kt` | The quiet card: thin border, 12dp corners, 24dp inside |
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
| Sign-in, white half | A global map | The StockFlow mark and the wordmark in the hero type style. From 1280px wide; narrower two-column screens use `headline-xl` so the wordmark fits |
| Artwork colors | Orange and warm neutrals only | Icons and Lottie animations keep the brand pack's navy, orange and turquoise. Artwork is not edited ([brand.md](brand.md) rule 1) |
| Navigation label, web | 13px bold | The kit sets 14px and weight 600 inline. It stays until the kit takes a variable for it |
| Layout, app | Sidebar and top bar of black glass | The app's layout is the mobile reference the owner chose on 2026-10-14, in StockFlow's orange: an orange hero under an orange status bar strip, with the brand on a white tile and the screen's headline (its key words in `primary-warm`), and the content on a sheet whose 32dp top corners lie over the hero. Fields and buttons are pills. The hero has line drawings (two rings and a gear) in white at 16%. The sign-in screens add the reference's phone: a white card with the StockFlow glyph, half under the sheet. Colors and type stay the design's; the sizes the design does not give live in `AppLayout` (`StockFlowTheme.kt`) |
| Page padding, app | 32px | 16dp at the sides of a phone |
| Card shadow, app | Soft shadow and thin border | Border only. The card is transparent, and a Compose shadow shows through it |
| Card shadow, web | Soft shadow | Not used yet: the first cards arrive with F1 |
| Surface inputs | "Neutral border", no height given | Web: 36px tall and 6px corners, as the shell input. App: a 56dp pill filled with `surface`, an icon on the left and the label inside while it is empty; an orange border with the cursor, a red one with an error; an eye shows a password |
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
| `web/tests/e2e/design.spec.ts` | Computed styles in the browser against the tokens: canvas, shell glass, sizes, active item, sign-in halves, type, dark mode, phone |
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
| Invoice `IN_ASSIGNMENT`, order `FULFILLING` | Blue (development) |
| Order `FULFILLED` | Lavender (integrated) |
| Invoice `CLOSED`, order `VALIDATED` | Green (production) |
| Order `CANCELLED` | No tone in the design. A sixth badge, in the `danger` color, would have to be added to the design file |

4. **Navigation in the app.** The design has no bottom bar or drawer. The pattern is chosen in F1, when the app has more than one screen.
