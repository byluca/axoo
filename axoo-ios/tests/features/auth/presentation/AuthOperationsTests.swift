import XCTest
@testable import Axoo

@MainActor
final class AuthOperationsTests: XCTestCase {
    func testEmailAndCodeNormalization() {
        XCTAssertEqual(LoginInput.email("  STUDENTE@Campus.it\n"), "studente@campus.it")
        XCTAssertTrue(LoginInput.isValidEmail("studente@campus.it"))
        for invalid in ["", "a@@b.it", "a@b", "a b@c.it", "@c.it", "a@.it"] {
            XCTAssertFalse(LoginInput.isValidEmail(invalid), invalid)
        }
        XCTAssertEqual(LoginInput.code("12 34-56 78"), "123456")
        XCTAssertEqual(LoginInput.code("１２abc"), "")
    }

    func testOperationsReturnDataAndThrowValidationErrors() async throws {
        let service = FakeAuthService()
        let operations = AuthOperations(service: service)
        let initialAccess = try await operations.restore()
        XCTAssertNil(initialAccess)
        do {
            _ = try await operations.requestCode(email: "invalid")
            XCTFail("Invalid email should throw")
        } catch AuthValidationError.invalidEmail { }
        XCTAssertTrue(service.addresses.isEmpty)
        let address = try await operations.requestCode(email: " STUDENTE@Campus.it ")
        XCTAssertEqual(address, "studente@campus.it")
        XCTAssertEqual(service.addresses, [address])
        do {
            _ = try await operations.verify(email: address, code: "123")
            XCTFail("Invalid code should throw")
        } catch AuthValidationError.invalidCode { }
        let access = try await operations.verify(email: address, code: "123456")
        XCTAssertEqual(access, service.access)
        let hasSession = try operations.hasSession()
        XCTAssertTrue(hasSession)
        try await operations.signOut()
        XCTAssertFalse(service.loggedIn)
    }

    func testBootstrapFailureCanBeRecoveredByRestore() async throws {
        let service = FakeAuthService()
        service.failBootstrap = true
        let operations = AuthOperations(service: service)
        do {
            _ = try await operations.verify(email: "student@campus.it", code: "123456")
            XCTFail("Bootstrap should throw")
        } catch { }
        let hasSession = try operations.hasSession()
        XCTAssertTrue(hasSession)
        service.failBootstrap = false
        let restored = try await operations.restore()
        XCTAssertEqual(restored, service.access)
    }

    func testLoginModelNormalizesInputAndIgnoresDuplicateSendDuringCooldown() async {
        let service = FakeAuthService()
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.beginLogin()
        model.setInput("  STUDENT@Campus.it  ")
        model.setTermsAccepted(true)
        model.sendCode()
        model.sendCode()
        await model.operation?.value
        XCTAssertEqual(model.email, "student@campus.it")
        XCTAssertEqual(model.screen, .code)
        model.sendCode()
        XCTAssertEqual(service.addresses.count, 1)
        model.setInput("12 34-56")
        await model.operation?.value
        XCTAssertEqual(model.screen, .account(service.access))
        XCTAssertFalse(model.isBusy)
    }

    func testConsentIsRequiredBeforeSendingEmail() async {
        let service = FakeAuthService()
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.beginLogin()
        model.setInput("student@campus.it")
        XCTAssertFalse(model.canSubmit)
        model.sendCode()
        await model.operation?.value
        XCTAssertTrue(service.addresses.isEmpty)
        model.setTermsAccepted(true)
        XCTAssertTrue(model.canSubmit)
    }

    func testEmailConsentIsSavedAfterVerificationWithoutAnotherScreen() async {
        let service = FakeAuthService()
        service.access.termsRequired = true
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.beginLogin()
        model.setInput("student@campus.it")
        model.setTermsAccepted(true)
        model.sendCode()
        await model.operation?.value
        XCTAssertTrue(service.termsRequests.isEmpty)
        model.setInput("123456")
        await model.operation?.value
        XCTAssertEqual(service.termsRequests, [service.access.termsVersion])
        XCTAssertEqual(model.screen, .account(service.access))
        XCTAssertFalse(model.requiresTermsOnly)
    }

    func testFailedTermsAcceptanceKeepsTheAccountGatedAndAllowsRetry() async {
        let service = FakeAuthService()
        service.loggedIn = true
        service.access.termsRequired = true
        service.failTerms = true
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.start()
        await model.operation?.value
        XCTAssertEqual(model.screen, .email)
        XCTAssertTrue(model.requiresTermsOnly)
        model.sendCode()
        XCTAssertTrue(service.termsRequests.isEmpty)
        model.setTermsAccepted(true)
        model.sendCode()
        model.sendCode()
        await model.operation?.value
        XCTAssertEqual(service.termsRequests, [service.access.termsVersion])
        XCTAssertEqual(model.screen, .email)
        XCTAssertTrue(model.requiresTermsOnly)
        XCTAssertNotNil(model.errorMessage)
        service.failTerms = false
        model.sendCode()
        await model.operation?.value
        XCTAssertEqual(model.screen, .account(service.access))
        XCTAssertFalse(service.access.termsRequired)
        XCTAssertTrue(service.addresses.isEmpty)
        XCTAssertNil(model.errorMessage)
    }

    func testFailedConsentAfterOtpRetriesWithoutVerifyingCodeAgain() async {
        let service = FakeAuthService()
        service.access.termsRequired = true
        service.failTerms = true
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.beginLogin()
        model.setInput("student@campus.it")
        model.setTermsAccepted(true)
        model.sendCode()
        await model.operation?.value
        model.setInput("123456")
        await model.operation?.value
        XCTAssertEqual(model.screen, .email)
        XCTAssertTrue(model.requiresTermsOnly)
        service.failTerms = false
        model.sendCode()
        await model.operation?.value
        XCTAssertEqual(service.verifications, 1)
        XCTAssertEqual(model.screen, .account(service.access))
    }

    func testLateRestoreCannotUndoLogoutOnScreen() async throws {
        let service = FakeAuthService()
        service.pauseRestore = true
        let root = LoginViewModel(operations: AuthOperations(service: service))
        root.start()
        let restore = root.operation
        for _ in 0..<1000 {
            if service.pendingRestore != nil { break }
            await Task.yield()
        }
        let pending = try XCTUnwrap(service.pendingRestore)
        root.signOut()
        await root.operation?.value
        pending.resume(returning: service.access)
        service.pendingRestore = nil
        await restore?.value
        XCTAssertEqual(root.screen, .intro)
        XCTAssertFalse(root.isBusy)
    }

    func testLogoutClearsSessionAndReturnsToIntroWithoutWaitingForPushCleanup() async {
        let service = FakeAuthService()
        service.loggedIn = true
        let cleaning = expectation(description: "Push cleanup started")
        var finish: CheckedContinuation<Void, Never>?
        var cleanup: Task<Void, Never>?
        var cleaned = false
        service.onSignOut = {
            cleanup = Task {
                await withCheckedContinuation { finish = $0; cleaning.fulfill() }
                cleaned = true
            }
        }
        let model = LoginViewModel(operations: AuthOperations(service: service))
        model.start()
        await model.operation?.value
        model.signOut()
        XCTAssertEqual(model.screen, .signingOut)
        XCTAssertTrue(model.isBusy)
        model.signOut()
        model.restore()
        model.beginLogin()
        XCTAssertEqual(model.screen, .signingOut)
        await model.operation?.value
        XCTAssertEqual(model.screen, .intro)
        XCTAssertFalse(model.isBusy)
        XCTAssertFalse(service.loggedIn)
        await fulfillment(of: [cleaning], timeout: 1)
        XCTAssertFalse(cleaned)

        finish?.resume()
        await cleanup?.value
        XCTAssertTrue(cleaned)
    }

    func testLocalLogoutFailureRequiresRetryBeforeReturningToIntro() async {
        let service = FakeAuthService()
        service.loggedIn = true
        service.failSignOut = true
        var cleanups = 0
        service.onSignOut = { cleanups += 1 }
        let model = LoginViewModel(operations: AuthOperations(service: service))

        model.signOut()
        await model.operation?.value
        XCTAssertEqual(model.screen, .signOutFailed)
        XCTAssertNotNil(model.errorMessage)
        XCTAssertFalse(model.isBusy)
        XCTAssertTrue(service.loggedIn)
        XCTAssertEqual(cleanups, 0)

        service.failSignOut = false
        model.signOut()
        await model.operation?.value
        XCTAssertEqual(model.screen, .intro)
        XCTAssertFalse(service.loggedIn)
        XCTAssertEqual(cleanups, 1)
    }

}

@MainActor
private final class FakeAuthService: AuthService {
    var verifications = 0
    var loggedIn = false
    var addresses: [String] = []
    var failBootstrap = false
    var pauseRestore = false
    var pendingRestore: CheckedContinuation<AccountAccess?, Never>?
    var termsRequests: [String] = []
    var failTerms = false
    var failSignOut = false
    var onSignOut: () -> Void = {}
    var access = AccountAccess(status: .active, reason: "", suspendedUntil: nil)
    func hasSession() throws -> Bool { loggedIn }
    func restore() async throws -> AccountAccess? {
        if pauseRestore { return await withCheckedContinuation { pendingRestore = $0 } }
        return loggedIn ? access : nil
    }
    func requestCode(email: String) async throws { addresses.append(email) }
    func verify(email: String, code: String) async throws -> AccountAccess {
        verifications += 1
        loggedIn = true
        if failBootstrap { throw URLError(.notConnectedToInternet) }
        return access
    }
    func acceptTerms(version: String) async throws -> AccountAccess {
        termsRequests.append(version)
        if failTerms { throw URLError(.notConnectedToInternet) }
        access.termsRequired = false
        return access
    }
    func signOut() throws {
        if failSignOut { throw SessionStoreFailure.unavailable }
        loggedIn = false
        onSignOut()
    }
}
