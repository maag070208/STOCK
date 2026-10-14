# Mobile app architecture

**Status:** Accepted for planning, 2026-10-13. Versions are pinned at the start of phase F0. The app is for operators and for users with `app.access`.

## 1. Scope of the app

The app does what the warehouse floor needs:

- Sign in (`app.access`).
- Count assigned materials for a material entry.
- Place counted materials into locations.
- Pick materials for an order.
- Reassign picked material.
- Scan barcodes and location codes for each of these.

The app does not create invoices, orders, suppliers or customers. Those belong to the web.

## 2. Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Shared code | Kotlin Multiplatform: domain models, API client, state, validation |
| Targets | Android and iOS |
| UI | Compose Multiplatform, shared between both platforms. Compose foundation only: no Material |
| Design | The app's own theme and components, built from the design tokens. See section 7 and [design-system.md](design-system.md) |
| HTTP | Ktor client, with kotlinx.serialization |
| State | ViewModels shared in common code, with immutable UI state |
| Navigation | Compose navigation |
| Camera scanning | Platform code behind an `expect`/`actual` interface. Android uses CameraX with an ML Kit barcode scanner. iOS uses AVFoundation |
| Session storage | Platform secure storage behind an `expect`/`actual` interface. Android Keystore, iOS Keychain |
| Strings | The app's own catalog, in `es` (default) and `en`, as [i18n/README.md](../i18n/README.md) requires. See section 6 |
| Brand | Launcher icons and Lottie animations from the brand pack. See [brand.md](brand.md) |
| Tests | `kotlin.test` for shared code, run on Android and on iOS. End-to-end runs on the Android emulator or a phone, and with XCUITest on the iOS simulator. See section 9 |

## 3. Layers

```
app/
├── shared/          common code for both platforms
│   ├── domain/      models, validation, state machines (mirror of the API statuses)
│   ├── data/        API client, DTOs, session
│   ├── feature/     one folder per flow: entries, picks, reassignment, sign-in
│   └── ui/          Compose screens and components
├── androidApp/      Android entry point and the camera scanner
└── iosApp/          iOS entry point and the camera scanner
```

Rules:

1. Shared code never imports a platform API. Platform code goes behind an `expect`/`actual` interface.
2. Each flow has one ViewModel. Its state is immutable and is the only thing the screen reads.
3. Validation runs in the app for feedback only. The API decides, and its error codes are shown to the user in their locale.
4. The app sends the `Idempotency-Key` for creating a movement, so a retry after a lost connection does not duplicate it.

## 4. Scanning

The scanner reads a barcode and returns the raw value. The app then asks the API which material or location that value is. The rules are in [barcode-scanning.md](../modules/barcode-scanning.md).

- **Camera.** `CameraPreview` and `rememberCameraPermission` are `expect` in `shared/.../scanner` and `actual` per platform: CameraX with the ML Kit barcode reader (bundled model) on Android, `AVCaptureMetadataOutput` on iOS. They read Code 128, which the labels print, and EAN-13, EAN-8, UPC-A, UPC-E and QR.
- **Permission.** The app asks with the system's own dialog the first time the person opens the scanner. Refused, the screen says so and the code can still be typed. A device without a camera, such as the iOS simulator, says so too.
- **Lookup.** `GET /products/lookup` with the scanned or typed value. One product is shown with its code, barcode, unit and stock (stock only with `stock.view`); several, found by name, are a list to pick from. The camera reading the same label again does not look it up again.
- **Session.** Every call goes through the session (`Authorized`): an expired access token is refreshed once and the call repeated; a session the API ended signs the person out.

The scanner never creates a line. If the value does not belong to the open assignment or order, the app shows the error from the API.

## 5. Local API from the emulator

Debug builds reach the local API at `http://localhost:8080/api/v1` unless the build says otherwise. A real phone cannot use `localhost`: point the app at the machine that runs the API with `apiBaseUrl=http://<ip>:8080/api/v1` in `app/local.properties` (never committed), or once with `./gradlew :androidApp:assembleDebug -Pstockflow.apiBaseUrl=...`. The build reads either and writes `API_BASE_URL` into the generated `ApiEnvironment.kt`, so no machine's address is in the source. `app/README.md` has the same steps.

The emulator, or a phone connected by USB, can also forwards the port with `adb reverse tcp:8080 tcp:8080`. The tests run this command themselves.

The Android emulator's host alias `10.0.2.2` timed out from the app on the Android 17 image used in F0, while the same address answered from the shell. Until that is understood, the forward is the path. iOS simulators use `localhost` directly.

## 6. Texts in the app

Texts live in the app's own catalog (`i18n/Texts.kt`), with an English key and Spanish and English values. The language comes from the user's choice, Spanish by default, and not from the device. Compose resource strings are not used, because Compose 1.12 does not expose a public way to set the resource language.

## 7. Interface

The interface takes its colors and type from [design-system.md](design-system.md), and its layout from the mobile reference the owner chose on 2026-10-14 (design-system.md section 7). It uses nothing else.

- **No Material.** The dependency is not in the build. A screen is made of Compose foundation and the components in `shared/.../ui/components`: `AppText`, `HeroScreen`, `HeroBar`, `HeroHeadline`, `HeroIconButton`, `SheetTitle`, `TextInput`, `PrimaryButton`, `Card`, `StatusBadge`, `Icon`, `ShellStatusBar`.
- **Layout.** Every screen is a `HeroScreen`: the orange hero with the brand and the screen's headline or title, and the sheet with the content, its rounded top over the hero. Fields and buttons are pills. The sign-in screens show the reference's phone with the StockFlow glyph, half under the sheet.
- **Icons** are drawn in `Icon` (`AppIcon`) on a 24-unit grid with a round stroke. There is no icon font or library.
- **Theme.** `StockFlowTheme` gives the workspace colors, light or dark, from the system setting. `StockFlowType` gives the type styles. Both read `DesignTokens.kt`, which is generated from the design file. `AppLayout` holds the few sizes of the reference the design does not give: the sheet's corners, the pill, the brand tile.
- **No values by hand.** A color, a text size or a corner radius is never typed in a screen. `tests/ops/app-design.test.mjs` fails on a color literal outside the generated tokens.
- **Status bar.** The strip behind it is the hero's orange on every screen, so its icons are always light.
- **Rotation.** The screen a person is on survives Android rebuilding the activity when the phone turns.
- **The home is a hub.** After sign-in the app opens a button per module the person's permissions open, and each module opens its own menu of the pages it holds; the pages themselves belong to the module. The modules and their pages are listed once, in `ui/nav/Modules.kt`, with the permission each needs, and `App.kt` sends each page to its screen: **Entradas** (mis recepciones, discrepancias), **Materiales** (buscar material), **Existencias** (por material), **Ubicaciones** (almacenes) and **Órdenes**, which the API does not serve yet (F5) and so is listed with its page greyed out and a "Próximamente" badge. A module or a page whose permission the person does not hold is not shown. Adding a module is a line in that file and a screen in the navigation of `App.kt`.
- **Back.** The deepest thing open closes first: a page, then a module's menu, then the home, where the system leaves the app. Every screen's hero carries its own back button, and Android's back gesture and back button do the same (`AppBackHandler`, `expect`/`actual`: Android uses `activity.compose.BackHandler`; iOS has no back and keeps the hero's button).
- **The pages read, they do not write.** Mis recepciones, discrepancias, existencias and almacenes are lists the API answers (`POST /{resource}/query` and `GET /receipts/mine`); the app shows them and their empty, loading and refused states, and never changes stock from them.
- **A new component** goes to `ui/components` when a screen needs it, and reads its specification from `DesignTokens.Components` when the design has one.

## 8. Session

- **Sign-in** opens the app after the intro when there is no saved session. A new account goes to the password change before anything else.
- **Storage.** The access token lives in memory only. The refresh token is kept in the platform's secure storage: on Android it is encrypted with an AES-GCM key held in the Android Keystore; on iOS it is a Keychain item readable only on this device after the first unlock. On the next launch the app refreshes it and opens the home without asking again.
- **Sign-out** ends the session on the device at once, then tells the API.
- **Password managers.** The username and password fields declare what they hold, so the phone's password manager (Samsung Pass, Google) can fill them and offer to save them.
- **Language.** Before sign-in the app is in Spanish. After sign-in it follows the person's language.

## 9. Tests and proof

- Shared tests (`commonTest`) run on both targets: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`.
- End-to-end tests on Android are Node tests in `tests/e2e-app/`. They drive the device with `adb`, read the screen through the accessibility tree, and compare its pixels with the design file. They share one device, so they run one at a time: `node --test --test-concurrency=1 tests/e2e-app/`. The Android test library for Compose does not run on the Android 17 image yet.
- **Emulator or phone.** With one device connected, the tests use it. With several, `ANDROID_SERIAL` chooses one: `ANDROID_SERIAL=emulator-5554 node --test --test-concurrency=1 tests/e2e-app/`. A phone connected by USB reaches the local API through the same `adb reverse` port forward.
- **Forms are filled as a person would:** the test taps the first field, types, and moves on with the keyboard's Next key; the keyboard covers the fields below. A password manager's offer to save the sign-in is declined with Back.
- **A phone keeps its settings.** On an emulator the tests switch dark mode and the animation scale, and put them back. On a phone they change nothing: the design is checked in the mode the phone is in, and the reduced-motion test is skipped.
- While an app starts, the accessibility tree cannot be read. The intro is therefore recognized on the screen's pixels, not on the tree.
- **The camera on the emulator.** The project's emulator (`StockFlow_Pixel`) uses the virtual scene camera (`hw.camera.back=virtualscene`). The scanner test puts a product's Code 128 barcode on the scene's posters (`adb emu virtualscene-image`), walks the camera to them with the emulator's own macro (`Walk_to_image_room`), and checks that the app shows the product; then it puts the scene back. On a phone, or on an emulator with another camera, that test is skipped, and the camera is checked by hand with a printed label.
- **iOS end-to-end tests** are XCUITest, in the target `iosAppUITests` of `iosApp.xcodeproj`, with the same flows as the Android suite. `scripts/ios-ui-tests.sh [simulator] [xcodebuild options]` runs them against the local API (profile `e2e`); it passes the password of `e2e-admin` from `.env` to the test runner without showing it. Compose gives each test tag to the element as its accessibility identifier.
- On iOS the tests also fill forms with the keyboard: a tap on the first field, then the Next key. A field gets the keyboard a moment after the tap, so each value waits until its field has the focus.
- iOS screens are also checked by eye on the simulator, in light and dark mode.
- Shared logic (validation, state machines, DTO mapping): unit tests, with the same cases as the API for the rules both sides share.
- Each flow: one end-to-end run against a local API with seed data, on the Android emulator and the iOS simulator.
- A release is not ready until both platforms pass the flow tests.
