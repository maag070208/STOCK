This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform, you
  need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications. It contains
  several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name. For
      example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls. Similarly, if you want
      to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Pointing the app at the API

The app reads its API address from the build, not the source. On a simulator or an emulator it defaults to
`http://localhost:8080/api/v1`. To try the app on a real phone, put the machine that runs the API on the same
network and add its address to `local.properties` (never committed), then rebuild:

```
apiBaseUrl=http://<ip-of-your-machine>:8080/api/v1
```

You can also pass it once with `./gradlew :androidApp:assembleDebug -Pstockflow.apiBaseUrl=http://<ip>:8080/api/v1`.
On Android, the debug build already allows plain HTTP (`androidApp/src/debug/AndroidManifest.xml`); with a cable,
`adb reverse tcp:8080 tcp:8080` is another way to reach the API from the phone.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…