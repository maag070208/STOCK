# Brand and assets

**Status:** Captured from the brand pack `stockflow-icons`, 2026-10-14. Files are copied into the repository in phase F0 (tasks F0-15 and F0-16). Artwork is not edited.

## 1. Identity

- **Name:** StockFlow. Confirmed by the owner, 2026-10-14. It replaces the working name "Stock" in [vision-and-scope.md](vision-and-scope.md).
- **Mark:** a conveyor belt with boxes, the mark of a warehouse in motion.
- **Light and dark:** every asset comes in both variants.

These are the colors **of the artwork**: icons, launcher and animations. The colors of the interface come from the design file, not from this table. See section 6 and [design-system.md](design-system.md).

| Role | Color |
|---|---|
| Primary (navy) | `#16315F`. Used for `theme-color` and the launcher background |
| Accent (orange) | `#F28C28` |
| Secondary (turquoise) | `#3A86A8` |
| Light background | `#F2F6FC` |
| Dark background | `#0C1730` |

## 2. Files and where they go

| Pack file | Repository location | Used by |
|---|---|---|
| `master/*.svg` (full icon and glyph, light and dark) | `design/brand/master/` | Source for every other file |
| `master/stockflow-glyph-light.svg`, `stockflow-glyph-dark.svg` | Also copied to `web/public/brand/` | The mark inside the web. The shell is dark in both modes, so it always shows the dark-background glyph |
| `ios/AppIcon.appiconset/` | `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` | iOS launcher. Light, Dark and Tinted at 1024 px |
| `android/light/res/` | `app/androidApp/src/main/res/` | Android launcher, adaptive icon and Android 13 monochrome icon |
| `android/dark/res/` | `design/brand/android-dark/` | Kept as the dark set. Not used by the launcher |
| `android/playstore-icon-*.png` (512 px) | `design/brand/store/` | Google Play listing. Not part of the build |
| `web/favicon.svg`, `favicon.ico`, `apple-touch-icon.png`, `icon-192.png`, `icon-512.png`, maskable icons | `web/public/` | Browser tab, home screen, PWA install |
| `web/dark/` | `design/brand/web-dark/` | Kept as the dark set |
| `web/site.webmanifest` | `web/public/site.webmanifest` | PWA name, colors and icons |
| `web/head-snippet.html` | Merged into `web/index.html` | Favicon links and `theme-color` for light and dark |
| `stockflow-intro-*.json`, `stockflow-loader-*.json` | `web/public/animations/` and `app/shared/src/commonMain/composeResources/files/animations/` | Lottie animations, section 4 |

The pack's `LEEME.txt` describes the same layout and is kept with the pack, not copied.

## 3. Rules

1. **No edits to artwork.** Changes come back from the designer as a new pack, and the copies are replaced, not hand-edited.
2. **Light and dark ship together.** A screen never shows a light asset in dark mode.
3. **Decorative assets have no alt text.** The loading animation has an accessible label from i18n, `brand.loading`, in `es` and `en`.
4. **The splash never blocks sign-in.** The intro animation plays once, and the app or page continues when it ends or when it is skipped.

## 4. Lottie animations

| File | Length | Use |
|---|---|---|
| `stockflow-intro-{light,dark}.json` | 130 frames, 30 fps (about 4.3 s) | First load of the web and the app, before sign-in |
| `stockflow-loader-{light,dark}.json` | 120 frames, 30 fps (4 s) | Loading states on lists and forms |

- The variant follows the theme.
- Reduced motion: when the user asks for reduced motion, the animation shows its first frame, without moving.
- **Web:** `lottie-react`, the library CYC already uses ([web.md](web.md) section 11). The version is checked in F0.
- **App:** `compottie` 2.3.3. Animations load from the shared resources folder and play through `rememberLottiePainter`.

## 5. Decisions

Confirmed by the owner, 2026-10-14:

1. **Name:** StockFlow.
2. **Android launcher:** the light set. The dark set is kept for other uses.
3. **Lottie in the app:** `compottie` 2.3.3, a Kotlin Multiplatform player for Android and iOS. Chosen in F0, 2026-10-14.

## 6. Brand and the design system

The owner adopted a design for the interface on 2026-10-13: [design-system.md](design-system.md). From then on:

- The interface (web and app) takes its colors, type and shapes from the design file: orange `#fe6e00`, warm neutrals and the black glass shell.
- The brand pack gives the **artwork** only: icons, launcher, favicons and Lottie animations. The artwork keeps its own colors, and is still copied without edits (rule 1).
- A test checks that the glyphs served by the web are exact copies of the masters.
- The app draws the glyph from vector drawables that `scripts/brand-vectors.mjs` writes from the masters. A vector cannot be a byte copy of an SVG, so a test checks that every path, color and stroke of the master is in it, unchanged.

## 7. Open questions

1. The web's `theme-color` still uses the pack's `#F2F6FC` and `#0C1730`. Confirm whether it moves to the design's shell color.
2. The artwork's navy and turquoise are not in the design's palette. Confirm whether the designer supplies artwork in the design's orange and neutrals, or the artwork stays as it is.
