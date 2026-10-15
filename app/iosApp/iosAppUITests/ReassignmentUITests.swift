// F5-19 · e2e on iOS: the alert of an order that is due tomorrow, and moving its picked material to another order, as
// on Android (tests/e2e-app/android-order-reassignment.test.mjs). The simulator has no camera, so the codes are typed.
import XCTest

@MainActor
final class ReassignmentUITests: StockFlowUITestCase {

    // Types into a field and moves on with the keyboard's Next: the field below is reached with the keyboard, not with a
    // tap, because the keyboard covers it. The last newline sends the form.
    private func type(_ tag: String, _ value: String, next: String, _ last: String) {
        let field = element(tag)
        XCTAssertTrue(field.waitForExistence(timeout: timeout), "no field tagged \(tag)")
        field.tap()
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
        wait(for: [focused], timeout: timeout)
        field.typeText(value + "\n")
        let following = element(next)
        let moved = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: following)
        wait(for: [moved], timeout: timeout)
        following.typeText(last + "\n")
    }

    func testTheAlertOfAnOrderDueTomorrowOpensAndItsPickedMaterialMovesToAnotherOrder() async throws {
        // An administrator on the app channel: the warehouse roles have no app access, or no reassignment, and the
        // administrator has both everywhere.
        let user = try await StockFlowApi.newUser(["ADMIN"], channel: "app")
        let token = try await StockFlowApi.adminToken()
        try await StockFlowApi.grant(user.id, "orders.reassign", "ALL")
        try await StockFlowApi.grant(user.id, "orders.fulfill", "ALL")
        let warehouse = try await StockFlowApi.newWarehouse()
        let locations = try await StockFlowApi.newRack(warehouse.id)
        let location = locations[0]
        let product = try await StockFlowApi.newProduct("Codo galvanizado de prueba")
        let customer = try await StockFlowApi.newCustomer()
        // The administrator works in this warehouse, so its orders can be assigned to them and they validate them.
        _ = try await StockFlowApi.call("PUT", "/users/\(user.id)/warehouses", token: token, body: ["warehouseIds": [warehouse.id]])
        try await StockFlowApi.staff(warehouse.id, [user.id])
        try await StockFlowApi.stockIn(warehouseId: warehouse.id, locationId: location.id, operator: user, product: product, quantity: 100)
        let source = try await StockFlowApi.newOrder(warehouseId: warehouse.id, customerId: customer.id, product: product, quantity: 30, operatorId: user.id, dueIn: 1)
        let sourceOrder = try await StockFlowApi.order(source.id)
        let line = (sourceOrder["lines"] as? [[String: Any]])?.first
        try await StockFlowApi.pick(user, orderId: source.id, lineId: line?["id"] as? String ?? "", locationId: location.id, quantity: 20)
        let target = try await StockFlowApi.newOrder(warehouseId: warehouse.id, customerId: customer.id, product: product, quantity: 20, operatorId: user.id, assign: false)

        // The alert of the source order is one of the alerts of the day.
        let mine = try await StockFlowApi.alerts(user)
        XCTAssertTrue(mine.contains { (($0["order"] as? [String: Any])?["number"] as? String) == source.number && ($0["kind"] as? String) == "BEFORE" }, "\(mine)")

        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(element("module-orders").waitForExistence(timeout: timeout), "the home offers the orders")
        element("module-orders").tap()
        XCTAssertTrue(element("page-orders-reassignment").waitForExistence(timeout: timeout), "the orders menu holds the reassignment page")
        element("page-orders-reassignment").tap()

        // The alert is listed in the language of the person, and opens the order.
        let alert = element("alert-\(source.number)")
        XCTAssertTrue(alert.waitForExistence(timeout: timeout), "the alert of the order due tomorrow is listed")
        let kind = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Vence mañana")).firstMatch
        XCTAssertTrue(kind.waitForExistence(timeout: timeout), "the alert says it is due tomorrow, in Spanish")
        alert.tap()
        XCTAssertTrue(element("reassign-scan").waitForExistence(timeout: timeout), "the reassignment form asks for the material")

        // The material that was picked opens its line and the order that needs it.
        let scan = element("reassign-scan")
        scan.tap()
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: scan)
        wait(for: [focused], timeout: timeout)
        scan.typeText(product.code + "\n")
        XCTAssertTrue(element("reassign-picked").waitForExistence(timeout: timeout), "the picked quantity is shown")
        let targetRow = element("reassign-target-\(target.number)")
        XCTAssertTrue(targetRow.waitForExistence(timeout: timeout), "the order that needs the material is offered")

        // The note and the quantity, sent with the keyboard's Done.
        targetRow.tap()
        type("reassign-note", "Traspaso de prueba", next: "reassign-quantity", "20")
        XCTAssertTrue(text("Traspaso registrado.").waitForExistence(timeout: timeout), "the move is recorded")

        // The API moved the picked quantities, and no balance changed.
        let moved = try await StockFlowApi.order(source.id)
        let movedLine = (moved["lines"] as? [[String: Any]])?.first
        XCTAssertEqual((movedLine?["pickedQuantity"] as? NSNumber)?.intValue, 0)
        let received = try await StockFlowApi.order(target.id)
        let receivedLine = (received["lines"] as? [[String: Any]])?.first
        XCTAssertEqual((receivedLine?["pickedQuantity"] as? NSNumber)?.intValue, 20)
        XCTAssertEqual((received["status"] as? [String: Any])?["code"] as? String, "FULFILLED")
    }
}
