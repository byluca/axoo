import XCTest
@testable import Axoo

@MainActor
final class ProfileViewModelTests: XCTestCase {
    func testInvalidDeletionConfirmationNeverReachesTheAPI() async throws {
        let api = ProfileModelAPI()
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try user()))
        var deletions = 0
        model.deleteAccount(confirmation: "wrong") { deletions += 1 }
        XCTAssertEqual(deletions, 0)
        XCTAssertTrue(api.paths.isEmpty)
        XCTAssertNil(model.deleteTask)
        XCTAssertEqual(model.state.notice, AxooOverlayCopy.deleteAccountInvalid)
    }

    func testNicknameValidationAvailabilityAndSaveUpdateSharedUser() async throws {
        let api = ProfileModelAPI()
        let repository = ProfileRepository(api: api, currentUser: try user())
        let model = ProfileViewModel(repository: repository)
        var changes = 0
        model.onUserChanged = { changes += 1 }
        model.beginNicknameEditing()
        XCTAssertFalse(model.canSaveNickname)
        model.changeNickname("A!")
        XCTAssertFalse(model.canSaveNickname)
        XCTAssertTrue(api.paths.isEmpty)

        model.changeNickname("alice2")
        await model.checkTask?.value
        XCTAssertTrue(model.canSaveNickname)
        var saved = 0
        model.saveNickname { saved += 1 }
        model.saveNickname { saved += 1 }
        await model.nicknameTask?.value
        XCTAssertEqual(saved, 1)
        XCTAssertEqual(changes, 1)
        XCTAssertEqual(repository.currentUser?.nickname, "alice2")
        XCTAssertFalse(model.state.isSavingNickname)
        XCTAssertFalse(model.canSaveNickname)
        XCTAssertEqual(api.paths, ["users/me/nickname-availability", "users/me/nickname"])
        model.stop()
    }

    func testLateAvailabilityDoesNotEnableSavingADifferentNickname() async throws {
        let api = ProfileModelAPI()
        api.heldPath = "users/me/nickname-availability"
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try user()))
        model.changeNickname("alice2")
        let request = model.checkTask
        await waitForRequest(api)
        let pending = try XCTUnwrap(api.pending)
        model.changeNickname("!")
        pending.resume(returning: Data(#"{"available":true}"#.utf8))
        await request?.value
        XCTAssertEqual(model.state.nickname, "!")
        XCTAssertFalse(model.canSaveNickname)
        XCTAssertEqual(model.state.nicknameMessage, "Il nickname deve avere almeno 3 caratteri")
        model.stop()
    }

    func testFailedNicknameSaveKeepsDraftAndCanRetry() async throws {
        let api = ProfileModelAPI()
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try user()))
        model.changeNickname("alice2")
        await model.checkTask?.value
        api.failedPath = "users/me/nickname"
        var saved = false
        model.saveNickname { saved = true }
        await model.nicknameTask?.value
        XCTAssertFalse(saved)
        XCTAssertEqual(model.user?.nickname, "alice")
        XCTAssertEqual(model.state.nickname, "alice2")
        XCTAssertTrue(model.canSaveNickname)
        api.failedPath = nil
        model.saveNickname { saved = true }
        await model.nicknameTask?.value
        XCTAssertTrue(saved)
        model.stop()
    }

    func testAvatarFailurePreservesUserAndAllowsRetry() async throws {
        let api = ProfileModelAPI()
        api.failedPath = "users/avatar"
        let original = try user()
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: original))
        var results: [Bool] = []
        model.updateAvatar(["hairColor": "b9a05f"]) { results.append($0) }
        model.updateAvatar(["hairColor": "000000"]) { results.append($0) }
        await model.avatarTask?.value
        XCTAssertEqual(model.user, original)
        XCTAssertEqual(results, [false])
        XCTAssertFalse(model.state.isSavingAvatar)
        api.failedPath = nil
        model.updateAvatar(["hairColor": "b9a05f"]) { results.append($0) }
        await model.avatarTask?.value
        XCTAssertEqual(results, [false, true])
        XCTAssertNotNil(model.user?.avatarConfig)
        model.stop()
    }

    func testUnblockFailureKeepsListAndSuccessNotifiesOnce() async {
        let api = ProfileModelAPI()
        let model = ProfileViewModel(repository: ProfileRepository(api: api))
        model.loadBlocked()
        await model.blockedTask?.value
        var unblocked: [String] = []
        model.onUnblocked = { unblocked.append($0) }
        api.failedPath = "users/blocks/bob"
        model.unblock("bob")
        await model.unblockTask?.value
        XCTAssertEqual(model.state.blocked.map(\.id), ["bob"])
        XCTAssertTrue(unblocked.isEmpty)
        XCTAssertNotNil(model.state.notice)
        model.clearNotice()
        api.failedPath = nil
        model.unblock("bob")
        model.unblock("bob")
        await model.unblockTask?.value
        XCTAssertTrue(model.state.blocked.isEmpty)
        XCTAssertEqual(unblocked, ["bob"])
        XCTAssertFalse(model.state.isUnblocking)
        model.stop()
    }

    func testDeleteOnlyCompletesAfterBackendSuccess() async throws {
        let api = ProfileModelAPI()
        let repository = ProfileRepository(api: api, currentUser: try user())
        let model = ProfileViewModel(repository: repository)
        var deleted = 0
        api.failedPath = "users/me"
        model.deleteAccount { deleted += 1 }
        await model.deleteTask?.value
        XCTAssertEqual(deleted, 0)
        XCTAssertNotNil(repository.currentUser)
        XCTAssertFalse(model.state.isDeletingAccount)
        api.failedPath = nil
        model.deleteAccount { deleted += 1 }
        model.deleteAccount { deleted += 1 }
        await model.deleteTask?.value
        XCTAssertEqual(deleted, 1)
        XCTAssertNil(repository.currentUser)
        model.stop()
    }

    func testStopDiscardsInFlightLoadAndRejectsNewWork() async throws {
        let api = ProfileModelAPI()
        api.heldPath = "users/me"
        let repository = ProfileRepository(api: api)
        let model = ProfileViewModel(repository: repository)
        model.load()
        let request = model.loadTask
        await waitForRequest(api)
        let pending = try XCTUnwrap(api.pending)
        model.stop()
        var notified = false
        model.onChange = { notified = true }
        pending.resume(returning: api.userData)
        await request?.value
        model.load()
        model.loadBlocked()
        model.changeNickname("alice2")
        XCTAssertNil(repository.currentUser)
        XCTAssertFalse(notified)
        XCTAssertFalse(model.state.isLoading)
        XCTAssertEqual(api.paths, ["users/me"])
    }

    func testStoppingAvatarSaveSuppressesLateCompletion() async throws {
        let api = ProfileModelAPI()
        api.heldPath = "users/avatar"
        let original = try user()
        let repository = ProfileRepository(api: api, currentUser: original)
        let model = ProfileViewModel(repository: repository)
        var completed = false
        model.updateAvatar(["hairColor": "b9a05f"]) { _ in completed = true }
        let request = model.avatarTask
        await waitForRequest(api)
        let pending = try XCTUnwrap(api.pending)
        model.stop()
        pending.resume(returning: Data("{}".utf8))
        await request?.value
        XCTAssertFalse(completed)
        XCTAssertEqual(repository.currentUser, original)
        XCTAssertFalse(model.state.isSavingAvatar)
    }

    private func user() throws -> AccountProfile {
        try APIJSON.decode(AccountProfile.self, from: Data(#"{"id":"alice","nickname":"alice","university_id":"campus"}"#.utf8))
    }

    private func waitForRequest(_ api: ProfileModelAPI) async {
        for _ in 0..<100 {
            if api.pending != nil { return }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertNotNil(api.pending)
    }
}

@MainActor
private final class ProfileModelAPI: APIClient {
    let userData = Data(#"{"id":"alice","nickname":"alice2","university_id":"campus"}"#.utf8)
    var paths: [String] = []
    var failedPath: String?
    var heldPath: String?
    var pending: CheckedContinuation<Data, Never>?

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        paths.append(path)
        if path == failedPath { throw URLError(.notConnectedToInternet) }
        if path == heldPath { return await withCheckedContinuation { pending = $0 } }
        switch path {
        case "users/me", "users/me/nickname": return userData
        case "users/me/nickname-availability": return Data(#"{"available":true}"#.utf8)
        case "users/blocks": return Data(#"{"users":[{"id":"bob","nickname":"bob","university_id":"campus"}]}"#.utf8)
        default: return Data("{}".utf8)
        }
    }
}
