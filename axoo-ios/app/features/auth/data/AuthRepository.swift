import Foundation

/// The login coordinator serializes operations. Generation checks also reject
/// responses that arrive after logout; cancellation is checked before persistence.
@MainActor
final class AuthRepository: AuthService, APIClient {
    private let configuration: AppConfiguration
    private let http: HTTPClient
    private let store: SessionStore
    private var generation = UUID()
    private var passwordEmail: String?
    var passwordRequired: Bool { passwordEmail != nil }
    private var refreshOperation: (id: UUID, task: Task<StoredSession, Error>)?
    var onAccessChanged: () -> Void = {}
    var onSessionCleared: (String?) -> Void = { _ in }

    init(configuration: AppConfiguration, http: HTTPClient = HTTPClient(), store: SessionStore) {
        self.configuration = configuration
        self.http = http
        self.store = store
    }

    func hasSession() throws -> Bool { try store.read() != nil }

    func requestCode(email: String) async throws {
        try requireConfiguration()
        passwordEmail = nil
        do {
            let data = try await http.post(configuration.apiURL.appendingPathComponent("auth/email-eligibility"), body: ["email": email])
            let eligibility = try JSONDecoder().decode(Eligibility.self, from: data)
            guard eligibility.eligible else { throw AuthFailure.ineligible }
            try Task.checkCancellation()
            if eligibility.loginMethod == "account_password" { passwordEmail = email; return }
            if email == LoginInput.reviewEmail { return }
            let payload: [String: Any] = ["email": email, "create_user": true]
            let body = try JSONSerialization.data(withJSONObject: payload)
            _ = try await http.post(configuration.authURL.appendingPathComponent("otp"), json: body, headers: authHeaders)
        } catch let error as HTTPFailure {
            throw error.status == 429 ? AuthFailure.rateLimited : AuthFailure.unavailable
        }
    }

    func verify(email: String, code: String) async throws -> AccountAccess {
        try requireConfiguration()
        let expected = generation
        let data: Data
        do {
            if email == passwordEmail || email == LoginInput.reviewEmail {
                var components = URLComponents(url: configuration.authURL.appendingPathComponent("token"), resolvingAgainstBaseURL: false)!
                components.queryItems = [URLQueryItem(name: "grant_type", value: "password")]
                data = try await http.post(components.url!, body: ["email": email, "password": code], headers: authHeaders)
            } else {
                data = try await http.post(configuration.authURL.appendingPathComponent("verify"),
                                           body: ["email": email, "token": code, "type": "email"], headers: authHeaders)
            }
        } catch let error as HTTPFailure {
            if error.status == 429 { throw AuthFailure.rateLimited }
            if [400, 401, 403, 422].contains(error.status) { throw email == passwordEmail ? AuthFailure.invalidPassword : AuthFailure.invalidCode }
            throw AuthFailure.unavailable
        }
        let session = try decodeSession(data)
        try requireCurrent(expected)
        // Keep valid tokens if bootstrap is temporarily offline: the OTP is consumed.
        try store.save(session)
        return try await bootstrap(session, expected: expected)
    }

    func restore() async throws -> AccountAccess? {
        guard var session = try store.read() else { return nil }
        try requireConfiguration()
        let expected = generation
        let needsRefresh = session.expiresAt.timeIntervalSinceNow < 60
        if needsRefresh {
            session = try await refresh(session, expected: expected)
        }
        do {
            return try await bootstrap(session, expected: expected, allowRefresh: !needsRefresh)
        } catch is DecodingError {
            throw AuthFailure.invalidResponse
        }
    }

    func acceptTerms(version: String) async throws -> AccountAccess {
        _ = try await request("PUT", path: "users/me/terms", body: APIJSON.body(["version": version, "adult": true]))
        guard let access = try await restore() else { throw AuthFailure.expiredSession }
        return access
    }

    func signOut() throws {
        let accessToken = try? store.read()?.accessToken
        generation = UUID()
        passwordEmail = nil
        refreshOperation?.task.cancel()
        refreshOperation = nil
        try store.clear()
        onSessionCleared(accessToken)
    }

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        // Older Foundation versions preserve duplicate slashes when joining URL paths.
        let relativePath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        var components = URLComponents(url: configuration.apiURL.appendingPathComponent(relativePath), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        return try await authenticatedRequest(method, url: components.url!, body: body, contentType: "application/json")
    }

    func uploadSticker(_ png: Data) async throws -> JSONValue {
        try await uploadImage(png, name: "sticker", filename: "sticker.png", mime: "image/png", path: "media/stickers")
    }

    func uploadPhoto(_ jpeg: Data) async throws -> JSONValue {
        try await uploadImage(jpeg, name: "photo", filename: "photo.jpg", mime: "image/jpeg", path: "media/photos")
    }

    private func uploadImage(_ image: Data, name: String, filename: String, mime: String, path: String) async throws -> JSONValue {
        let boundary = UUID().uuidString
        var body = Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\nContent-Type: \(mime)\r\n\r\n".utf8)
        body.append(image)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let data = try await authenticatedRequest("POST", url: configuration.apiURL.appendingPathComponent(path),
            body: body, contentType: "multipart/form-data; boundary=\(boundary)")
        return try APIJSON.decode(JSONValue.self, from: data)
    }

    private func authenticatedRequest(_ method: String, url: URL, body: Data?, contentType: String) async throws -> Data {
        try requireConfiguration()
        let expected = generation
        guard var session = try store.read() else { throw AuthFailure.expiredSession }
        let proactivelyRefreshed = session.expiresAt.timeIntervalSinceNow < 60
        if proactivelyRefreshed { session = try await refresh(session, expected: expected) }
        for attempt in 0...1 {
            try requireCurrent(expected)
            do {
                let data = try await http.request(method, url: url, body: body, contentType: contentType,
                                                  headers: ["Authorization": "Bearer \(session.accessToken)"])
                try requireCurrent(expected)
                return data
            } catch let error as HTTPFailure {
                try requireCurrent(expected)
                if error.status == 401 {
                    if attempt == 0 && !proactivelyRefreshed {
                        if let latest = try store.read(), latest.accessToken != session.accessToken { session = latest }
                        else { session = try await refresh(session, expected: expected) }
                        continue
                    }
                    try signOut()
                    onAccessChanged()
                    throw AuthFailure.expiredSession
                }
                throw error
            }
        }
        throw AuthFailure.expiredSession
    }

    private func bootstrap(_ session: StoredSession, expected: UUID, allowRefresh: Bool = false) async throws -> AccountAccess {
        let data: Data
        do {
            data = try await http.post(configuration.apiURL.appendingPathComponent("auth/session"), body: [:],
                                       headers: ["Authorization": "Bearer \(session.accessToken)"])
        } catch let error as HTTPFailure {
            try requireCurrent(expected)
            if error.status == 401 {
                if allowRefresh {
                    let refreshed = try await refresh(session, expected: expected)
                    return try await bootstrap(refreshed, expected: expected)
                }
                try signOut()
                throw AuthFailure.expiredSession
            }
            throw error.status == 429 ? AuthFailure.rateLimited : AuthFailure.unavailable
        }
        try requireCurrent(expected)
        let result = try JSONDecoder().decode(Bootstrap.self, from: data)
        guard let status = AccountAccess.Status(rawValue: result.status) else { throw AuthFailure.invalidResponse }
        return AccountAccess(status: status, reason: result.reason ?? "", suspendedUntil: result.suspended_until,
            moderationNotice: result.moderation_notice.map {
                ModerationNotice(id: $0.id, targetType: $0.target_type, reason: $0.reason ?? "")
            }, termsRequired: result.terms_required ?? false, termsVersion: result.terms_version ?? "2026-10-07")
    }

    private func refresh(_ session: StoredSession, expected: UUID) async throws -> StoredSession {
        let operation: (id: UUID, task: Task<StoredSession, Error>)
        if let current = refreshOperation { operation = current }
        else {
            operation = (UUID(), Task { try await self.performRefresh(session, expected: expected) })
            refreshOperation = operation
        }
        defer { if refreshOperation?.id == operation.id { refreshOperation = nil } }
        let result = try await operation.task.value
        try requireCurrent(expected)
        return result
    }

    private func performRefresh(_ session: StoredSession, expected: UUID) async throws -> StoredSession {
        var components = URLComponents(url: configuration.authURL.appendingPathComponent("token"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "grant_type", value: "refresh_token")]
        let data: Data
        do {
            data = try await http.post(components.url!, body: ["refresh_token": session.refreshToken], headers: authHeaders)
        } catch let error as HTTPFailure {
            try requireCurrent(expected)
            if [400, 401, 403].contains(error.status) {
                try signOut()
                onAccessChanged()
                throw AuthFailure.expiredSession
            }
            throw error.status == 429 ? AuthFailure.rateLimited : AuthFailure.unavailable
        }
        let refreshed = try decodeSession(data)
        try requireCurrent(expected)
        try store.save(refreshed)
        return refreshed
    }

    private func decodeSession(_ data: Data) throws -> StoredSession {
        let value = try JSONDecoder().decode(TokenResponse.self, from: data)
        guard !value.access_token.isEmpty, !value.refresh_token.isEmpty, value.expires_in > 0 else { throw AuthFailure.invalidResponse }
        return StoredSession(accessToken: value.access_token, refreshToken: value.refresh_token,
                             expiresAt: Date().addingTimeInterval(value.expires_in))
    }

    private var authHeaders: [String: String] { ["apikey": configuration.publishableKey] }
    private func requireConfiguration() throws {
        guard configuration.isConfigured else { throw AuthFailure.configuration }
    }
    private func requireCurrent(_ expected: UUID) throws {
        try Task.checkCancellation()
        guard generation == expected else { throw CancellationError() }
    }

    private struct Eligibility: Decodable {
        let eligible: Bool
        let loginMethod: String?
        enum CodingKeys: String, CodingKey { case eligible; case loginMethod = "login_method" }
    }
    private struct TokenResponse: Decodable {
        let access_token: String
        let refresh_token: String
        let expires_in: TimeInterval
    }
    private struct Bootstrap: Decodable {
        let status: String
        let reason: String?
        let suspended_until: String?
        let moderation_notice: Notice?
        let terms_required: Bool?
        let terms_version: String?
    }
    private struct Notice: Decodable {
        let id: String
        let target_type: String
        let reason: String?
    }
}
