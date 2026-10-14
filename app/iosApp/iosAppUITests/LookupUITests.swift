// F3-13 · e2e on iOS: finding a material, as on Android (tests/e2e-app/android-lookup.test.mjs). The simulator has
// no camera, so the screen says so and the value is typed; the camera itself is checked on a phone.
import XCTest

@MainActor
final class LookupUITests: StockFlowUITestCase {

    private func openLookup(_ roles: [String] = ["WAREHOUSE_OPERATOR"]) async throws {
        let user = try await StockFlowApi.newUser(roles)
        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(element("open-lookup").waitForExistence(timeout: timeout), "the home offers the lookup")
        element("open-lookup").tap()
        XCTAssertTrue(element("lookup-value").waitForExistence(timeout: timeout), "the lookup opens")
    }

    // Types into a field: a tap, then the keys once the field has the keyboard. `replacing` characters are deleted first.
    private func type(_ tag: String, _ value: String, replacing: Int = 0) {
        let field = element(tag)
        field.tap()
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
        wait(for: [focused], timeout: timeout)
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: replacing) + value + "\n")
    }

    func testATypedCodeShowsItsProductAndANameWithThreeMatchesListsThemUntilOneIsPicked() async throws {
        let product = try await StockFlowApi.newProduct("Codo galvanizado de prueba")
        let word = "Abrazadera" + UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(6).lowercased()
        var named: [StockFlowApi.TestProduct] = []
        for index in 1...3 { named.append(try await StockFlowApi.newProduct("\(word) \(index)")) }
        try await openLookup()
        XCTAssertTrue(text("Este dispositivo no tiene cámara. Escribe el código o el nombre abajo.").exists, "the simulator has no camera")

        type("lookup-value", product.code)
        XCTAssertTrue(element("lookup-product").waitForExistence(timeout: timeout), "the product is shown")
        XCTAssertTrue(text("Codo galvanizado de prueba").exists)
        // Kept with the results, so the screen can be checked by eye in light and dark mode.
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "lookup-product"
        shot.lifetime = .keepAlways
        add(shot)

        type("lookup-value", word, replacing: product.code.count)
        XCTAssertTrue(text("3 materiales coinciden. Elige uno.").waitForExistence(timeout: timeout), "three candidates")
        XCTAssertFalse(element("lookup-product").exists, "nothing is chosen")
        element("candidate-\(named[1].code)").tap()
        XCTAssertTrue(element("lookup-product").waitForExistence(timeout: timeout), "the picked product is shown")
        XCTAssertTrue(text("\(word) 2").exists)
    }

    func testSomeoneWhoCannotReadProductsIsNotOfferedTheLookup() async throws {
        let user = try await StockFlowApi.newUser(["APP_USER"])
        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(text("Conectado a la API").waitForExistence(timeout: timeout))
        XCTAssertFalse(element("open-lookup").exists)
    }
}
