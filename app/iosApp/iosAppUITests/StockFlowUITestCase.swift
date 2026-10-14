// What every UI test of the app needs: the app, its elements by test tag or by text, and forms filled as a person
// fills them. Compose exposes each test tag as the element's accessibility identifier.
import XCTest

@MainActor
class StockFlowUITestCase: XCTestCase {
    let app = XCUIApplication()
    let timeout: TimeInterval = 20

    override func setUp() async throws {
        continueAfterFailure = false
    }

    func element(_ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    func text(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    // Opens the app signed out. A session left by an earlier run ends through the screen, as a person would end it.
    func launchSignedOut() {
        app.launch()
        let username = element("username"), signOut = element("sign-out")
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline, !username.exists, !signOut.exists { _ = username.waitForExistence(timeout: 1) }
        if signOut.exists { signOut.tap() }
        XCTAssertTrue(username.waitForExistence(timeout: timeout), "the sign-in screen opens")
    }

    // Fills a form as a person would on a phone: taps the first field, types, and moves on with the keyboard's Next key.
    // The keyboard moves the fields below as it opens, so they are reached with the keyboard, not with a tap.
    // The last value is sent with Done. Compose gives a field the keyboard a moment later, so each value waits for it.
    func fillForm(_ fields: [(tag: String, value: String)]) {
        let first = element(fields[0].tag)
        XCTAssertTrue(first.waitForExistence(timeout: timeout), "no field tagged \(fields[0].tag)")
        first.tap()
        for (tag, value) in fields {
            let field = element(tag)
            let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
            wait(for: [focused], timeout: timeout)
            field.typeText(value + "\n")
        }
    }

    func signIn(_ username: String, _ password: String) {
        fillForm([("username", username), ("password", password)])
    }
}
