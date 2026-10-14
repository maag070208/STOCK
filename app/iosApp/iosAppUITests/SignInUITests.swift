// F1-20 · e2e on iOS: sign-in, the forced password change, the saved session and the sign-out.
// The same flows as the Android suite (tests/e2e-app/android-signin.test.mjs). Compose exposes each test tag as the
// element's accessibility identifier.
import XCTest

@MainActor
final class SignInUITests: StockFlowUITestCase {
    private var home: XCUIElement { text("Conectado a la API") }

    func testANewAppUserChangesTheTemporaryPasswordAndReachesTheHome() async throws {
        let user = try await StockFlowApi.newUser(["APP_USER"], pendingChange: true)
        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(text("Cambia tu contraseña").waitForExistence(timeout: timeout), "a temporary password leads to the password change")

        fillForm([("current-password", user.password), ("new-password", user.newPassword)])
        XCTAssertTrue(home.waitForExistence(timeout: timeout), "after the change the home opens")
    }

    func testAUserWithoutAppAccessSeesTheRefusalInSpanish() async throws {
        let office = try await StockFlowApi.newUser(["OFFICE"], channel: "web")
        launchSignedOut()
        signIn(office.username, office.password)
        XCTAssertTrue(text("No tienes permiso para realizar esta acción.").waitForExistence(timeout: timeout), "the refusal is shown")
        XCTAssertTrue(element("sign-in").exists, "the person stays on the sign-in screen")
    }

    func testAWrongPasswordIsRefusedWithTheApiMessage() async throws {
        let user = try await StockFlowApi.newUser(["APP_USER"])
        launchSignedOut()
        signIn(user.username, "Clave-incorrecta1")
        XCTAssertTrue(text("El usuario o la contraseña no son correctos.").waitForExistence(timeout: timeout))
    }

    func testTheSessionSurvivesARestartAndSigningOutEndsIt() async throws {
        let user = try await StockFlowApi.newUser(["WAREHOUSE_OPERATOR"])
        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(home.waitForExistence(timeout: timeout), "signed in")

        app.terminate()
        app.launch()
        XCTAssertTrue(home.waitForExistence(timeout: timeout), "the saved session opens the home without signing in again")

        element("sign-out").tap()
        XCTAssertTrue(element("username").waitForExistence(timeout: timeout), "signing out shows the sign-in screen")
        app.terminate()
        app.launch()
        XCTAssertTrue(element("username").waitForExistence(timeout: timeout), "after signing out no session is restored")
        XCTAssertFalse(home.exists)
    }
}
