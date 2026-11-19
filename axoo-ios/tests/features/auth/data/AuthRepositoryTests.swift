import XCTest
@testable import Axoo

@MainActor
final class AuthRepositoryTests: XCTestCase {
    private lazy var store = MemorySessionStore()
    private lazy var repository: AuthRepository = {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [AuthURLProtocol.self]
        return AuthRepository(
            configuration: AppConfiguration(apiURL: URL(string: "https://api.example.com/api/v1/")!,
                                            authURL: URL(string: "https://auth.example.com/auth/v1/")!,
                                            publishableKey: "test-public-key"),
            http: HTTPClient(session: URLSession(configuration: config)), store: store)
    }()

    func testTermsAcceptanceSendsAgeAndVersionThenUsesServerBootstrap() async throws {
        store.session = StoredSession(accessToken: "access", refreshToken: "refresh", expiresAt: Date().addingTimeInterval(3600))
        var accepted = false
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("users/me/terms") == true {
                XCTAssertEqual(request.httpMethod, "PUT")
                let body = try Self.body(request)
                XCTAssertEqual(body["version"] as? String, "2026-10-07")
                XCTAssertEqual(body["adult"] as? Bool, true)
                accepted = true
                return (204, "")
            }
            XCTAssertEqual(request.url?.path, "/api/v1/auth/session")
            return (200, accepted
                ? #"{"status":"active","terms_required":false,"terms_version":"2026-10-07"}"#
                : #"{"status":"active","terms_required":true,"terms_version":"2026-10-07"}"#)
        }
        let before = try await repository.restore()
        XCTAssertEqual(before?.termsRequired, true)
        let after = try await repository.acceptTerms(version: "2026-10-07")
        XCTAssertFalse(after.termsRequired)
    }

    func testHTTPFailureSurvivesAuthenticatedRequest() async throws {
        store.session = StoredSession(accessToken: "access", refreshToken: "refresh", expiresAt: Date().addingTimeInterval(3600))
        AuthURLProtocol.handler = { _ in
            (400, #"{"error":"Richiesta non valida"}"#)
        }
        do {
            _ = try await repository.request("POST", path: "posts", body: Data())
            XCTFail("Request failure must be returned to the view model")
        } catch let error as HTTPFailure {
            XCTAssertEqual(error.status, 400)
        }
    }

    func testAvatarSaveReachesTheExpectedEndpointAndUpdatesTheCachedProfile() async throws {
        store.session = StoredSession(accessToken: "access", refreshToken: "refresh", expiresAt: .distantFuture)
        let user = try APIJSON.decode(AccountProfile.self,
            from: Data(#"{"id":"alice","nickname":"alice","university_id":"uniba"}"#.utf8))
        let profile = ProfileRepository(api: repository, currentUser: user)
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.absoluteString, "https://api.example.com/api/v1/users/avatar")
            XCTAssertEqual(request.httpMethod, "PUT")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            XCTAssertEqual(try Self.body(request)["avatar_config"] as? [String: String], ["hairColor": "b9a05f"])
            return (204, "")
        }

        try await profile.updateAvatar(["hairColor": "b9a05f"])

        XCTAssertEqual(profile.currentUser?.avatarConfig?.values, ["hairColor": "b9a05f"])
    }

    func testAPIPathsWithSurroundingSlashesPreserveTheBasePathAndQuery() async throws {
        store.session = StoredSession(accessToken: "access", refreshToken: "refresh", expiresAt: .distantFuture)
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.absoluteString, "https://api.example.com/api/v1/users/avatar?source=editor")
            return (204, "")
        }

        for path in ["users/avatar", "/users/avatar", "//users/avatar//"] {
            _ = try await repository.request("PUT", path: path,
                query: [URLQueryItem(name: "source", value: "editor")],
                body: APIJSON.body(["avatar_config": ["hairColor": "b9a05f"]]))
        }
    }

    func testSignOutPassesOriginalCredentialsToPushCleanupAfterClearingSession() throws {
        store.session = StoredSession(accessToken: "old-access", refreshToken: "refresh", expiresAt: .distantFuture)
        var cleaned = false
        repository.onSessionCleared = { [self] token in
            XCTAssertEqual(token, "old-access")
            XCTAssertNil(store.session)
            cleaned = true
        }
        try repository.signOut()
        XCTAssertTrue(cleaned)
    }

    func testExpiredRefreshAlsoStartsPushCleanup() async throws {
        store.session = StoredSession(accessToken: "expired-access", refreshToken: "refresh", expiresAt: .distantPast)
        var cleaned = false
        repository.onSessionCleared = { token in
            XCTAssertEqual(token, "expired-access")
            cleaned = true
        }
        AuthURLProtocol.handler = { _ in (401, "{}") }
        do {
            _ = try await repository.restore()
            XCTFail("Expired refresh must require login")
        } catch AuthFailure.expiredSession { }
        XCTAssertTrue(cleaned)
    }

    func testEligibilityDenialNeverCallsSupabase() async throws {
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/api/v1/auth/email-eligibility")
            return (200, #"{"eligible":false}"#)
        }
        do {
            try await repository.requestCode(email: "person@example.com")
            XCTFail("An ineligible email must be rejected")
        } catch AuthFailure.ineligible { }
    }

    func testEligibleEmailSendsCreateUserBooleanAndAPIKey() async throws {
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("email-eligibility") == true {
                return (200, #"{"eligible":true}"#)
            }
            XCTAssertEqual(request.url?.path, "/auth/v1/otp")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "test-public-key")
            let body = try Self.body(request)
            XCTAssertEqual(body["email"] as? String, "student@campus.it")
            XCTAssertEqual(body["create_user"] as? Bool, true)
            return (200, "{}")
        }
        try await repository.requestCode(email: "student@campus.it")
    }

    func testReviewAccountDoesNotRequestOTP() async throws {
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/api/v1/auth/email-eligibility")
            XCTAssertEqual(try Self.body(request)["email"] as? String, LoginInput.reviewEmail)
            return (200, #"{"eligible":true}"#)
        }
        try await repository.requestCode(email: LoginInput.reviewEmail)
    }

    func testOfficialAccountUsesDedicatedPasswordWithoutRequestingOTP() async throws {
        var paths: [String] = []
        AuthURLProtocol.handler = { request in
            let path = request.url!.path
            paths.append(path)
            if path.hasSuffix("email-eligibility") {
                return (200, #"{"eligible":true,"login_method":"account_password"}"#)
            }
            if path == "/auth/v1/token" {
                XCTAssertEqual(request.url?.query, "grant_type=password")
                let body = try Self.body(request)
                XCTAssertEqual(body["email"] as? String, "aup@account.example")
                XCTAssertEqual(body["password"] as? String, "test-only-password")
                return (200, Self.tokens)
            }
            XCTAssertEqual(path, "/api/v1/auth/session")
            return (200, #"{"status":"active"}"#)
        }
        let operations = AuthOperations(service: repository)
        _ = try await operations.requestCode(email: "aup@account.example")
        XCTAssertTrue(operations.passwordRequired)
        let access = try await operations.verify(email: "aup@account.example", code: "test-only-password")
        XCTAssertEqual(access.status, .active)
        XCTAssertFalse(paths.contains("/auth/v1/otp"))
        XCTAssertFalse(paths.contains("/auth/v1/verify"))
        try repository.signOut()
        XCTAssertFalse(repository.passwordRequired)
    }

    func testReviewAccountUsesSupabasePasswordSession() async throws {
        AuthURLProtocol.handler = { request in
            if request.url?.path == "/auth/v1/token" {
                XCTAssertEqual(request.url?.query, "grant_type=password")
                XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "test-public-key")
                let body = try Self.body(request)
                XCTAssertEqual(body["email"] as? String, LoginInput.reviewEmail)
                XCTAssertEqual(body["password"] as? String, "123456")
                return (200, Self.tokens)
            }
            XCTAssertEqual(request.url?.path, "/api/v1/auth/session")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (200, #"{"status":"active"}"#)
        }
        let access = try await repository.verify(email: LoginInput.reviewEmail, code: "123456")
        XCTAssertEqual(access.status, .active)
        XCTAssertEqual(store.session?.refreshToken, "refresh")
    }

    func testVerifyPreservesTokensWhenBootstrapFails() async throws {
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("verify") == true {
                let body = try Self.body(request)
                XCTAssertEqual(body["type"] as? String, "email")
                XCTAssertEqual(body["token"] as? String, "123456")
                return (200, Self.tokens)
            }
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (503, "{}")
        }
        do {
            _ = try await repository.verify(email: "student@campus.it", code: "123456")
            XCTFail("Bootstrap failure must be surfaced")
        } catch AuthFailure.unavailable { }
        XCTAssertEqual(store.session?.refreshToken, "refresh")
    }

    func testExpiredSessionRefreshesBeforeBootstrap() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "refresh", expiresAt: .distantPast)
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("token") == true {
                XCTAssertEqual(request.url?.query, "grant_type=refresh_token")
                return (200, Self.tokens)
            }
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (200, #"{"status":"suspended","reason":"reason","suspended_until":null}"#)
        }
        let access = try await repository.restore()
        XCTAssertEqual(access?.status, .suspended)
        XCTAssertEqual(store.session?.accessToken, "access")
    }

    func testBackend401RefreshesUnexpiredSessionOnce() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "refresh", expiresAt: Date().addingTimeInterval(3600))
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("token") == true {
                let body = try Self.body(request)
                XCTAssertEqual(body["refresh_token"] as? String, "refresh")
                return (200, Self.tokens)
            }
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer old" {
                return (401, "{}")
            }
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (200, #"{"status":"active"}"#)
        }
        let access = try await repository.restore()
        XCTAssertEqual(access?.status, .active)
        XCTAssertEqual(store.session?.accessToken, "access")
    }

    func testBackend401AfterProactiveRefreshDoesNotRefreshAgain() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "original", expiresAt: .distantPast)
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("token") == true {
                let body = try Self.body(request)
                guard body["refresh_token"] as? String == "original" else {
                    XCTFail("A restore must attempt at most one refresh")
                    return (400, "{}")
                }
                return (200, Self.tokens)
            }
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (401, "{}")
        }
        do {
            _ = try await repository.restore()
            XCTFail("A rejected refreshed session must require login")
        } catch AuthFailure.expiredSession { }
        XCTAssertNil(store.session)
    }

    func testRejectedRefreshClearsSession() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "invalid", expiresAt: .distantPast)
        AuthURLProtocol.handler = { _ in (400, "{}") }
        do {
            _ = try await repository.restore()
            XCTFail("Invalid refresh must fail")
        } catch AuthFailure.expiredSession { }
        XCTAssertNil(store.session)
    }

    func testUnknownAccountStatusDoesNotGrantAccess() async throws {
        store.session = StoredSession(accessToken: "access", refreshToken: "refresh", expiresAt: Date().addingTimeInterval(3600))
        AuthURLProtocol.handler = { _ in (200, #"{"status":"unknown"}"#) }
        do {
            _ = try await repository.restore()
            XCTFail("Unknown status must fail closed")
        } catch AuthFailure.invalidResponse { }
    }

    func testUploadRejectedAfterRefreshClearsSessionAndNotifiesRoot() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "refresh", expiresAt: Date().addingTimeInterval(3600))
        var notifications = 0
        repository.onAccessChanged = { notifications += 1 }
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("token") == true { return (200, Self.tokens) }
            XCTAssertTrue(request.url?.path.hasSuffix("/media/photos") == true)
            XCTAssertTrue(request.value(forHTTPHeaderField: "Content-Type")?.hasPrefix("multipart/form-data;") == true)
            return (401, "{}")
        }
        do {
            _ = try await repository.uploadPhoto(Data([1, 2, 3]))
            XCTFail("A rejected refreshed session must require login")
        } catch AuthFailure.expiredSession { }
        XCTAssertNil(store.session)
        XCTAssertEqual(notifications, 1)
    }

    func testUpload401AfterProactiveRefreshDoesNotRefreshAgain() async throws {
        store.session = StoredSession(accessToken: "old", refreshToken: "original", expiresAt: .distantPast)
        AuthURLProtocol.handler = { request in
            if request.url?.path.hasSuffix("token") == true {
                XCTAssertEqual(try Self.body(request)["refresh_token"] as? String, "original")
                return (200, Self.tokens)
            }
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
            return (401, "{}")
        }
        do {
            _ = try await repository.uploadPhoto(Data([1]))
            XCTFail("Only one refresh is allowed per upload")
        } catch AuthFailure.expiredSession { }
        XCTAssertNil(store.session)
    }

    nonisolated private static var tokens: String {
        #"{"access_token":"access","refresh_token":"refresh","expires_in":3600}"#
    }

    nonisolated private static func body(_ request: URLRequest) throws -> [String: Any] {
        var data = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 1024)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                data.append(contentsOf: buffer.prefix(count))
            }
        }
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}

private final class AuthURLProtocol: URLProtocol {
    static var handler: (@Sendable (URLRequest) throws -> (Int, String))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            guard let handler = Self.handler else { throw URLError(.badServerResponse) }
            let (status, body) = try handler(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
                                           headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() { }
}

@MainActor
private final class MemorySessionStore: SessionStore {
    var session: StoredSession?
    func read() throws -> StoredSession? { session }
    func save(_ session: StoredSession) throws { self.session = session }
    func clear() throws { session = nil }
}
