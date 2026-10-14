// The API, called by the UI tests to set up their data, as the Android suite does (testing.md rule 4).
// The tests sign in as `e2e-admin`, which the API keeps only with the profile `e2e`.
// Its password reaches the test runner as E2E_ADMIN_PASSWORD (scripts/ios-ui-tests.sh).
import Foundation

struct TestUser {
    let username: String
    let password: String
    let newPassword: String
}

enum StockFlowApi {
    static let base = "http://localhost:8080/api/v1"

    struct Failure: Error, CustomStringConvertible {
        let description: String
    }

    static func call(_ method: String, _ path: String, token: String? = nil, body: [String: Any]? = nil, channel: String = "web") async throws -> (status: Int, json: [String: Any]) {
        var request = URLRequest(url: URL(string: base + path)!)
        request.httpMethod = method
        request.setValue(channel, forHTTPHeaderField: "X-Client-Channel")
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
        guard created.status == 201, let temporary = created.json["temporaryPassword"] as? String else {
            throw Failure(description: "user creation failed (\(created.status)): \(created.json)")
        }
        let chosen = "Clave-" + randomPart()
        if pendingChange { return TestUser(username: username, password: temporary, newPassword: chosen) }
        let login = try await call("POST", "/auth/login", body: ["username": username, "password": temporary], channel: channel)
        guard let token = login.json["token"] as? String else {
            throw Failure(description: "sign-in failed (\(login.status)): \(login.json)")
        }
        let changed = try await call("POST", "/auth/change-password", token: token, body: ["currentPassword": temporary, "newPassword": chosen], channel: channel)
        guard changed.status == 200 else {
            throw Failure(description: "password change failed (\(changed.status)): \(changed.json)")
        }
        return TestUser(username: username, password: chosen, newPassword: chosen)
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
}
