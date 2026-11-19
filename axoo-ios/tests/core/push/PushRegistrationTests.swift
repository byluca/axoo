import XCTest
@testable import Axoo

@MainActor
final class PushRegistrationTests: XCTestCase {
    private let defaults = UserDefaults(suiteName: "push-tests-\(UUID().uuidString)")!
    private let api = PushAPI()

    func testRefreshKeepsInstallationIDAndUploadsTheNewRegistration() async {
        let first = expectation(description: "First upload"), second = expectation(description: "Refresh")
        api.onRequest = { count in (count == 1 ? first : second).fulfill() }
        let push = makeRegistration()
        push.activate(userID: "account-A")
        push.synchronize("fid-1")
        await fulfillment(of: [first], timeout: 1)
        push.synchronize("fid-2")
        await fulfillment(of: [second], timeout: 1)
        XCTAssertEqual(api.paths.count, 2)
        XCTAssertEqual(api.paths.first, api.paths.last)
        XCTAssertEqual(api.registrations, ["fid-1", "fid-2"])
    }

    func testLogoutWaitsForPendingUploadAndRemovesWithOriginalCredentials() async {
        let started = expectation(description: "Upload started"), removed = expectation(description: "Binding removed")
        var finishUpload: CheckedContinuation<Void, Never>?
        api.handler = {
            await withCheckedContinuation { finishUpload = $0; started.fulfill() }
        }
        var order: [String] = []
        let push = makeRegistration(remove: { _, registration, token in
            XCTAssertEqual(registration, "fid-1")
            XCTAssertEqual(token, "old-access")
            order.append("delete")
        }, invalidate: { order.append("invalidate"); removed.fulfill() })
        push.activate(userID: "account-A")
        push.synchronize("fid-1")
        await fulfillment(of: [started], timeout: 1)
        push.signedOut(accessToken: "old-access")
        push.synchronize("late-fid")
        push.activate(userID: "account-B")
        finishUpload?.resume()
        await fulfillment(of: [removed], timeout: 1)
        let ready = await push.prepare()
        XCTAssertTrue(ready)
        XCTAssertEqual(order, ["delete", "invalidate"])
        XCTAssertEqual(api.registrations, ["fid-1"])
        XCTAssertEqual(push.userID, "account-B")
    }

    func testLogoutDuringSDKRegistrationPreventsALateUpload() async {
        let started = expectation(description: "SDK started"), invalidated = expectation(description: "SDK invalidated")
        var finishRegistration: CheckedContinuation<String, Error>?
        let push = makeRegistration(register: {
            try await withCheckedThrowingContinuation { finishRegistration = $0; started.fulfill() }
        }, invalidate: { invalidated.fulfill() })
        push.activate(userID: "account-A")
        push.synchronize()
        await fulfillment(of: [started], timeout: 1)
        push.signedOut(accessToken: "old-access")
        finishRegistration?.resume(returning: "late-fid")
        await fulfillment(of: [invalidated], timeout: 1)
        XCTAssertTrue(api.paths.isEmpty)
        XCTAssertNil(push.userID)
    }

    func testUncertainUploadAndFailedBackendCleanupStillInvalidateSDK() async {
        let uploaded = expectation(description: "Upload attempted"), invalidated = expectation(description: "SDK invalidated")
        api.onRequest = { _ in uploaded.fulfill() }
        api.handler = { throw URLError(.networkConnectionLost) }
        var removed = false
        let push = makeRegistration(remove: { _, registration, token in
            XCTAssertEqual(registration, "fid-1")
            XCTAssertEqual(token, "old-access")
            removed = true
            throw URLError(.notConnectedToInternet)
        }, invalidate: { invalidated.fulfill() })
        push.activate(userID: "account-A")
        push.synchronize("fid-1")
        await fulfillment(of: [uploaded], timeout: 1)
        push.signedOut(accessToken: "old-access")
        await fulfillment(of: [invalidated], timeout: 1)
        XCTAssertTrue(removed)
        XCTAssertFalse(push.hasSavedRegistration)
    }

    func testFailedSDKCleanupIsRetriedBeforeTheNextRegistration() async {
        let failed = expectation(description: "Cleanup attempted")
        var attempts = 0
        let push = makeRegistration(invalidate: {
            attempts += 1
            if attempts == 1 { failed.fulfill(); throw URLError(.notConnectedToInternet) }
        })
        push.signedOut(accessToken: nil)
        await fulfillment(of: [failed], timeout: 1)
        XCTAssertTrue(push.hasSavedRegistration)
        push.activate(userID: "account-B")
        let ready = await push.prepare()
        XCTAssertTrue(ready)
        XCTAssertEqual(attempts, 2)
        XCTAssertFalse(push.hasSavedRegistration)
    }

    func testExpiredSessionCleanupDoesNotUseAnotherAccountsCredentials() async {
        let finished = expectation(description: "SDK invalidated")
        defaults.set("fid-1", forKey: "push_registration")
        let push = makeRegistration(remove: { _, _, _ in XCTFail("No token from an unrelated session") },
                                    invalidate: { finished.fulfill() })
        push.signedOut(accessToken: nil)
        await fulfillment(of: [finished], timeout: 1)
        XCTAssertFalse(push.hasSavedRegistration)
    }

    func testBackgroundCleanupIsNotDuplicatedAndPrecedesTheNextRegistration() async {
        let started = expectation(description: "Cleanup started")
        var finish: CheckedContinuation<Void, Never>?
        var invalidations = 0
        let push = makeRegistration(invalidate: {
            invalidations += 1
            await withCheckedContinuation { finish = $0; started.fulfill() }
        })
        push.signedOut(accessToken: nil)
        await fulfillment(of: [started], timeout: 1)
        XCTAssertTrue(push.hasSavedRegistration)
        XCTAssertNil(push.userID)
        push.signedOut(accessToken: nil)

        push.activate(userID: "account-B")
        var ready = false
        let preparing = Task { ready = await push.prepare() }
        await Task.yield()
        XCTAssertFalse(ready)
        push.synchronize("new-fid")
        XCTAssertTrue(api.paths.isEmpty)

        finish?.resume()
        await preparing.value
        XCTAssertTrue(ready)
        XCTAssertFalse(push.hasSavedRegistration)
        XCTAssertEqual(invalidations, 1)
    }

    private func makeRegistration(
        remove: @escaping (String, String, String) async throws -> Void = { _, _, _ in },
        register: @escaping () async throws -> String = { "fid-1" },
        invalidate: @escaping () async throws -> Void = {}
    ) -> PushRegistration {
        PushRegistration(api: api, defaults: defaults, remove: remove, register: register, invalidate: invalidate)
    }
}

@MainActor
private final class PushAPI: APIClient {
    var paths: [String] = []
    var registrations: [String] = []
    var onRequest: (Int) -> Void = { _ in }
    var handler: () async throws -> Void = {}
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        XCTAssertEqual(method, "PUT")
        paths.append(path)
        let payload = try JSONSerialization.jsonObject(with: try XCTUnwrap(body)) as? [String: String]
        registrations.append(try XCTUnwrap(payload?["registration"]))
        onRequest(paths.count)
        try await handler()
        return Data()
    }
}
