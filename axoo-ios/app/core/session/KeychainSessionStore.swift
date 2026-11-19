import Foundation
import Security

enum SessionStoreFailure: LocalizedError {
    case unavailable
    var errorDescription: String? { "Non riusciamo ad aggiornare l'accesso sul dispositivo. Riprova." }
}

struct StoredSession: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Date
}

//in Swift protocol e' un'interfaccia di Kotlin
@MainActor
protocol SessionStore {
    func read() throws -> StoredSession?
    func save(_ session: StoredSession) throws
    func clear() throws
}

@MainActor
final class KeychainSessionStore: SessionStore {
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: (Bundle.main.bundleIdentifier ?? "com.axoo.ios") + ".session",
         kSecAttrAccount as String: "supabase"]
    }

    func read() throws -> StoredSession? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data,
              let session = try? JSONDecoder().decode(StoredSession.self, from: data)
        else { throw SessionStoreFailure.unavailable }
        return session
    }

    func save(_ session: StoredSession) throws {
        let data = try JSONEncoder().encode(session)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw SessionStoreFailure.unavailable }
    }

    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw SessionStoreFailure.unavailable }
    }
}
