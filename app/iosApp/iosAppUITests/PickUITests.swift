// F5-18 · e2e on iOS: picking the material of an order assigned to the operator, as on Android
// (tests/e2e-app/android-order-pick.test.mjs). The simulator has no camera, so the codes are typed; the camera itself is
// checked on a phone.
import XCTest

@MainActor
final class PickUITests: StockFlowUITestCase {

    // Types into a field: a tap, then the value once the field has the keyboard. The last newline sends the keyboard's
    // action, which finds the material, confirms the location or saves the pick.
    private func type(_ tag: String, _ value: String) {
        let field = element(tag)
        XCTAssertTrue(field.waitForExistence(timeout: timeout), "no field tagged \(tag)")
        field.tap()
        let focused = expectation(for: NSPredicate(format: "hasKeyboardFocus == true"), evaluatedWith: field)
        wait(for: [focused], timeout: timeout)
        field.typeText(value + "\n")
    }

    func testAnOperatorPicksTheMaterialOfAnAssignedOrder() async throws {
        let warehouse = try await StockFlowApi.newWarehouse()
        let locations = try await StockFlowApi.newRack(warehouse.id)
        let location = locations[0]
        let product = try await StockFlowApi.newProduct("Codo galvanizado de prueba")
        let customer = try await StockFlowApi.newCustomer()
        let user = try await StockFlowApi.newUser(["WAREHOUSE_OPERATOR"])
        // The operator works in this warehouse, reads its stock, and is in its staff template, which is who an order can
        // be assigned to.
        _ = try await StockFlowApi.call("PUT", "/users/\(user.id)/warehouses", token: try await StockFlowApi.adminToken(), body: ["warehouseIds": [warehouse.id]])
        try await StockFlowApi.grant(user.id, "stock.view", "AREA")
        try await StockFlowApi.staff(warehouse.id, [user.id])
        try await StockFlowApi.stockIn(warehouseId: warehouse.id, locationId: location.id, operator: user, product: product, quantity: 100)
        let order = try await StockFlowApi.newOrder(warehouseId: warehouse.id, customerId: customer.id, product: product, quantity: 80, operatorId: user.id)

        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(element("module-orders").waitForExistence(timeout: timeout), "the home offers the orders")
        element("module-orders").tap()
        XCTAssertTrue(element("page-orders-my-orders").waitForExistence(timeout: timeout), "the orders menu holds the pick page")
        element("page-orders-my-orders").tap()

        let row = element("order-\(order.number)")
        XCTAssertTrue(row.waitForExistence(timeout: timeout), "the order assigned to the operator is listed")
        row.tap()
        XCTAssertTrue(element("pick-scan").waitForExistence(timeout: timeout), "the pick form asks for the material")

        // The material is on the order: its line opens with the location that holds it.
        type("pick-scan", product.code)
        XCTAssertTrue(element("pick-location-\(location.code)").waitForExistence(timeout: timeout), "the location that holds the material is offered")
        type("pick-location-scan", location.code)
        type("pick-quantity", "50")
        XCTAssertTrue(text("Surtido registrado.").waitForExistence(timeout: timeout), "the pick is recorded")

        // The API moved the order to FULFILLING and kept the pick on the line.
        let picked = try await StockFlowApi.order(order.id)
        XCTAssertEqual((picked["status"] as? [String: Any])?["code"] as? String, "FULFILLING")
        let lines = picked["lines"] as? [[String: Any]] ?? []
        XCTAssertEqual((lines.first?["pickedQuantity"] as? NSNumber)?.intValue, 50)
    }

    func testAMaterialThatIsNotOnTheOrderIsRefused() async throws {
        let warehouse = try await StockFlowApi.newWarehouse()
        let locations = try await StockFlowApi.newRack(warehouse.id)
        let product = try await StockFlowApi.newProduct("Tornillo de la orden")
        let other = try await StockFlowApi.newProduct("Tuerca de otro pedido")
        let customer = try await StockFlowApi.newCustomer()
        let user = try await StockFlowApi.newUser(["WAREHOUSE_OPERATOR"])
        _ = try await StockFlowApi.call("PUT", "/users/\(user.id)/warehouses", token: try await StockFlowApi.adminToken(), body: ["warehouseIds": [warehouse.id]])
        try await StockFlowApi.grant(user.id, "stock.view", "AREA")
        try await StockFlowApi.staff(warehouse.id, [user.id])
        try await StockFlowApi.stockIn(warehouseId: warehouse.id, locationId: locations[0].id, operator: user, product: product, quantity: 10)
        let order = try await StockFlowApi.newOrder(warehouseId: warehouse.id, customerId: customer.id, product: product, quantity: 10, operatorId: user.id)

        launchSignedOut()
        signIn(user.username, user.password)
        XCTAssertTrue(element("module-orders").waitForExistence(timeout: timeout))
        element("module-orders").tap()
        XCTAssertTrue(element("page-orders-my-orders").waitForExistence(timeout: timeout))
        element("page-orders-my-orders").tap()
        let row = element("order-\(order.number)")
        XCTAssertTrue(row.waitForExistence(timeout: timeout))
        row.tap()
        XCTAssertTrue(element("pick-scan").waitForExistence(timeout: timeout))

        type("pick-scan", other.code)
        XCTAssertTrue(text("Este material no está en la orden.").waitForExistence(timeout: timeout), "the app says the material is not on the order")
        XCTAssertFalse(element("pick-location-\(locations[0].code)").exists, "no location is offered for a material that is not on the order")
    }
}
