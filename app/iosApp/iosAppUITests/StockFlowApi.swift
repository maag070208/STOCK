// The API, called by the UI tests to set up their data, as the Android suite does (testing.md rule 4).
// The tests sign in as `e2e-admin`, which the API keeps only with the profile `e2e`.
// Its password reaches the test runner as E2E_ADMIN_PASSWORD (scripts/ios-ui-tests.sh).
import Foundation

struct TestUser {
    let id: String
    let username: String
    let password: String
    let newPassword: String
    let token: String
}

enum StockFlowApi {
    static let base = "http://localhost:8080/api/v1"

    struct Failure: Error, CustomStringConvertible {
        let description: String
    }

    static func call(_ method: String, _ path: String, token: String? = nil, body: [String: Any]? = nil, channel: String = "web", headers: [String: String] = [:]) async throws -> (status: Int, json: [String: Any]) {
        var request = URLRequest(url: URL(string: base + path)!)
        request.httpMethod = method
        request.setValue(channel, forHTTPHeaderField: "X-Client-Channel")
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        let json = data.isEmpty ? [:] : (try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:])
        return ((response as! HTTPURLResponse).statusCode, json)
    }

    private static var admin: String?

    static func adminToken() async throws -> String {
        if let admin { return admin }
        guard let password = ProcessInfo.processInfo.environment["E2E_ADMIN_PASSWORD"], !password.isEmpty else {
            throw Failure(description: "E2E_ADMIN_PASSWORD is not set: run the tests with scripts/ios-ui-tests.sh")
        }
        let login = try await call("POST", "/auth/login", body: ["username": "e2e-admin", "password": password])
        guard login.status == 200, let token = login.json["token"] as? String else {
            throw Failure(description: "e2e-admin cannot sign in (\(login.status)): start the API with the profile e2e")
        }
        admin = token
        return token
    }

    private static func randomPart() -> String {
        String(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(10))
    }

    // A new person with these roles. With `pendingChange` they keep the temporary password, as a new account does.
    // Otherwise they already chose a password of their own, on the given channel.
    static func newUser(_ roles: [String], pendingChange: Bool = false, channel: String = "app") async throws -> TestUser {
        let username = "e2e" + randomPart()
        let created = try await call("POST", "/users", token: try await adminToken(), body: ["username": username, "name": "Persona de Prueba", "roles": roles])
        guard created.status == 201, let temporary = created.json["temporaryPassword"] as? String, let id = (created.json["user"] as? [String: Any])?["id"] as? String else {
            throw Failure(description: "user creation failed (\(created.status)): \(created.json)")
        }
        let chosen = "Clave-" + randomPart()
        if pendingChange { return TestUser(id: id, username: username, password: temporary, newPassword: chosen, token: "") }
        let login = try await call("POST", "/auth/login", body: ["username": username, "password": temporary], channel: channel)
        guard let token = login.json["token"] as? String else {
            throw Failure(description: "sign-in failed (\(login.status)): \(login.json)")
        }
        let changed = try await call("POST", "/auth/change-password", token: token, body: ["currentPassword": temporary, "newPassword": chosen], channel: channel)
        guard changed.status == 200, let own = changed.json["token"] as? String else {
            throw Failure(description: "password change failed (\(changed.status)): \(changed.json)")
        }
        return TestUser(id: id, username: username, password: chosen, newPassword: chosen, token: own)
    }

    struct TestProduct {
        let id: String
        let code: String
        let name: String
    }

    // A new active product, as an administrator creates it. Its code, which is also its barcode, has only letters
    // and digits.
    static func newProduct(_ name: String) async throws -> TestProduct {
        let code = "E2E" + randomPart().uppercased().prefix(9)
        let created = try await call("POST", "/products", token: try await adminToken(), body: ["code": code, "name": name, "unit": "PIECE"])
        guard created.status == 201, let id = created.json["id"] as? String else {
            throw Failure(description: "product creation failed (\(created.status)): \(created.json)")
        }
        return TestProduct(id: id, code: code, name: name)
    }

    struct TestWarehouse {
        let id: String
        let code: String
    }

    struct TestLocation {
        let id: String
        let code: String
    }

    struct TestPartner {
        let id: String
        let code: String
        let name: String
    }

    struct TestOrder {
        let id: String
        let number: String
    }

    // A warehouse with a rack of its own (org.md): where an order's material leaves from.
    static func newWarehouse() async throws -> TestWarehouse {
        let code = "W-" + randomPart().uppercased()
        let created = try await call("POST", "/warehouses", token: try await adminToken(), body: ["code": code, "name": "Almacén de prueba"])
        guard created.status == 201, let id = created.json["id"] as? String else {
            throw Failure(description: "warehouse creation failed (\(created.status)): \(created.json)")
        }
        return TestWarehouse(id: id, code: code)
    }

    static func newRack(_ warehouseId: String, aisle: String = "K", rack: String = "05", levels: Int = 2) async throws -> [TestLocation] {
        let created = try await call("POST", "/locations/racks", token: try await adminToken(), body: ["warehouseId": warehouseId, "aisle": aisle, "rack": rack, "levels": levels])
        guard created.status == 201, let locations = created.json["locations"] as? [[String: Any]] else {
            throw Failure(description: "rack creation failed (\(created.status)): \(created.json)")
        }
        return locations.compactMap { location in
            guard let id = location["id"] as? String, let code = location["code"] as? String else { return nil }
            return TestLocation(id: id, code: code)
        }
    }

    // The staff template of a warehouse: the operators an order can be assigned to (order-fulfillment.md section 7a).
    static func staff(_ warehouseId: String, _ userIds: [String]) async throws {
        let updated = try await call("PUT", "/warehouses/\(warehouseId)/staff", token: try await adminToken(), body: ["userIds": userIds])
        guard updated.status == 200 else { throw Failure(description: "staff template failed (\(updated.status)): \(updated.json)") }
    }

    // A permission an older database may not have seeded yet.
    static func grant(_ userId: String, _ permission: String, _ scope: String, reason: String = "Prueba de extremo a extremo") async throws {
        let granted = try await call("PUT", "/users/\(userId)/permissions/\(permission)", token: try await adminToken(), body: ["scope": scope, "reason": reason])
        guard granted.status == 200 else { throw Failure(description: "permission \(permission) failed (\(granted.status)): \(granted.json)") }
    }

    // The partner rules of the API (docs/modules/partners.md): a payment term, an address, and a valid tax id.
    private static func paymentTerms() async throws -> String {
        let labels = ["es": ["label": "Plazo de prueba"], "en": ["label": "Test term"]]
        let created = try await call("POST", "/catalogs/PAYMENT_TERMS/items", token: try await adminToken(), body: ["code": "E2E_TERMS", "labels": labels])
        guard created.status == 201 || created.json["code"] as? String == "DUPLICATE_VALUE" else {
            throw Failure(description: "payment term failed (\(created.status)): \(created.json)")
        }
        return "E2E_TERMS"
    }

    private static func partner(_ kind: String, _ name: String) async throws -> TestPartner {
        let body: [String: Any] = [
            "code": (kind == "customer" ? "CL-" : "PR-") + randomPart().uppercased(), "name": name, "taxId": "ADN010203AB1",
            "paymentTerms": try await paymentTerms(),
            "address": ["street": "Av. Juárez", "exteriorNumber": "120", "neighborhood": "Centro", "city": "Guadalajara", "state": "MX_JAL", "postalCode": "44100", "country": "MX"],
        ]
        let created = try await call("POST", "/\(kind)s", token: try await adminToken(), body: body)
        guard created.status == 201, let id = created.json["id"] as? String, let code = created.json["code"] as? String else {
            throw Failure(description: "\(kind) creation failed (\(created.status)): \(created.json)")
        }
        return TestPartner(id: id, code: code, name: name)
    }

    static func newCustomer(_ name: String = "Cliente de prueba") async throws -> TestPartner { try await partner("customer", name) }

    static func newSupplier(_ name: String = "Proveedor de prueba") async throws -> TestPartner { try await partner("supplier", name) }

    // Stock in one real location of a warehouse, through the material entry flow: an invoice of the operator's
    // warehouse, its assignment, the count and the placement that writes the RECEIPT movement. The opening inventory
    // would leave it in TEMPORARY, and a pick is taken from the shelves.
    static func stockIn(warehouseId: String, locationId: String, operator user: TestUser, product: TestProduct, quantity: Int) async throws {
        let supplier = try await newSupplier()
        let invoice = try await call("POST", "/invoices", token: try await adminToken(), body: [
            "number": "F-" + randomPart().uppercased(), "supplierId": supplier.id, "invoiceDate": ISO8601DateFormatter.string(from: Date(), timeZone: .current, formatOptions: [.withFullDate]),
            "warehouseId": warehouseId, "lines": [["productId": product.id, "quantity": quantity]],
        ], headers: ["Idempotency-Key": UUID().uuidString])
        guard invoice.status == 201, let lines = invoice.json["lines"] as? [[String: Any]], let lineId = lines.first?["id"] as? String, let invoiceId = invoice.json["id"] as? String else {
            throw Failure(description: "invoice creation failed (\(invoice.status)): \(invoice.json)")
        }
        let assigned = try await call("PUT", "/invoices/\(invoiceId)/lines/\(lineId)/assignments", token: try await adminToken(), body: ["assignments": [["operatorId": user.id, "quantity": quantity]]])
        guard assigned.status == 200, let shares = assigned.json["assignments"] as? [[String: Any]], let assignmentId = shares.first?["id"] as? String else {
            throw Failure(description: "assignment failed (\(assigned.status)): \(assigned.json)")
        }
        let counted = try await call("POST", "/receipts/assignments/\(assignmentId)/count", token: user.token, body: ["countedQuantity": String(quantity)], channel: "app")
        guard counted.status == 200 else { throw Failure(description: "count failed (\(counted.status)): \(counted.json)") }
        let placed = try await call("POST", "/receipts/assignments/\(assignmentId)/placements", token: user.token, body: ["placements": [["locationId": locationId, "quantity": String(quantity)]]], channel: "app")
        guard placed.status == 200 else { throw Failure(description: "placement failed (\(placed.status)): \(placed.json)") }
    }

    // An order for a material, assigned to the operator who fulfills it. Creation takes an Idempotency-Key.
    static func newOrder(warehouseId: String, customerId: String, product: TestProduct, quantity: Int, operatorId: String) async throws -> TestOrder {
        let number = "ORD-" + randomPart().uppercased()
        let due = ISO8601DateFormatter.string(from: Date().addingTimeInterval(3 * 86400), timeZone: .current, formatOptions: [.withFullDate])
        let created = try await call("POST", "/orders", token: try await adminToken(), body: [
            "number": number, "warehouseId": warehouseId, "customerId": customerId, "dueDate": due, "lines": [["productId": product.id, "quantity": quantity]],
        ], headers: ["Idempotency-Key": UUID().uuidString])
        guard created.status == 201, let id = created.json["id"] as? String else {
            throw Failure(description: "order creation failed (\(created.status)): \(created.json)")
        }
        let assigned = try await call("PUT", "/orders/\(id)/assignment", token: try await adminToken(), body: ["operatorId": operatorId])
        guard assigned.status == 200 else { throw Failure(description: "order assignment failed (\(assigned.status)): \(assigned.json)") }
        return TestOrder(id: id, number: number)
    }

    static func order(_ id: String) async throws -> [String: Any] {
        let read = try await call("GET", "/orders/\(id)", token: try await adminToken())
        guard read.status == 200 else { throw Failure(description: "order read failed (\(read.status)): \(read.json)") }
        return read.json
    }
}
