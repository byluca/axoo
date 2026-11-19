import XCTest
@testable import Axoo

@MainActor
final class ProfileRepositoryTests: XCTestCase {
    private let userData = Data(#"{"id":"alice","nickname":"alice","university_id":"uniba"}"#.utf8)

    func testInvalidNicknameIsRejectedBeforeAnyRequest() async throws {
        let api = ProfileAPI()
        let user = try APIJSON.decode(AccountProfile.self, from: userData)
        let repository = ProfileRepository(api: api, currentUser: user)
        for nickname in ["ab", String(repeating: "a", count: 25), "Alice", "has space", "èèè"] {
            do {
                _ = try await repository.updateNickname(nickname)
                XCTFail("Expected an invalid nickname to be rejected")
            } catch {
                XCTAssertEqual((error as NSError).domain, "Nickname")
            }
        }
        XCTAssertTrue(api.paths.isEmpty)
        XCTAssertEqual(repository.currentUser, user)
        XCTAssertNil(AccountProfile.nicknameValidationError("abc"))
        XCTAssertNil(AccountProfile.nicknameValidationError(String(repeating: "a", count: 24)))
    }

    func testGetMeReusesTheUserLoadedAtStartup() async throws {
        let api = ProfileAPI()
        api.response = userData
        let repository = ProfileRepository(api: api)
        let first = try await repository.getMe()
        let second = try await repository.getMe()
        XCTAssertEqual(first, second)
        XCTAssertEqual(repository.currentUser, first)
        XCTAssertEqual(api.paths, ["users/me"])
    }

    func testForcedRefreshUpdatesRolesAndNotifiesSharedProfile() async throws {
        let api = ProfileAPI()
        let repository = ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: userData))
        var changes = 0
        repository.onChange = { changes += 1 }
        api.response = Data(#"{"id":"alice","nickname":"alice","university_id":"uniba","is_moderator":true,"is_admin":false}"#.utf8)
        let promoted = try await repository.getMe(forceRefresh: true)
        XCTAssertEqual(promoted.isModerator, true)
        XCTAssertEqual(changes, 1)
        api.response = Data(#"{"id":"alice","nickname":"alice","university_id":"uniba","is_moderator":false,"is_admin":false}"#.utf8)
        let demoted = try await repository.getMe(forceRefresh: true)
        XCTAssertEqual(demoted.isModerator, false)
        XCTAssertEqual(repository.currentUser, demoted)
        XCTAssertEqual(changes, 2)
        XCTAssertEqual(api.paths, ["users/me", "users/me"])
    }

    func testSettingsUpdatesReachExistingEditorAndCommentRepositories() async throws {
        let api = ProfileAPI()
        let repository = ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: userData))
        let posts = PostRepository(profile: repository)
        let editor = PostEditorViewModel(service: posts, mode: .create)
        let comments = RemoteCommentRepository(posts: posts, authorID: "bob")
        var changes = 0
        repository.onChange = { changes += 1 }

        api.response = Data(#"{"id":"alice","nickname":"alice2","university_id":"uniba"}"#.utf8)
        _ = try await repository.updateNickname("alice2")
        api.response = Data("{}".utf8)
        try await repository.updateAvatar(["hairColor": "b9a05f", "glassesVariant": "variant02"])

        XCTAssertEqual(editor.author?.nickname, "alice2")
        XCTAssertEqual(editor.author?.avatar.parameters["glassesProbability"], "100")
        XCTAssertEqual(comments.currentUserAvatar, repository.currentUser?.avatarConfig)
        XCTAssertNotNil(comments.currentUserAvatar)
        XCTAssertEqual(changes, 2)
        XCTAssertEqual(api.paths, ["users/me/nickname", "users/avatar"])
    }

    func testFailedAvatarSavePreservesCurrentUser() async throws {
        let api = ProfileAPI()
        api.fail = true
        let user = try APIJSON.decode(AccountProfile.self, from: userData)
        let repository = ProfileRepository(api: api, currentUser: user)
        var changed = false
        repository.onChange = { changed = true }
        do {
            try await repository.updateAvatar(["hairColor": "b9a05f"])
            XCTFail("Expected the save to fail")
        } catch is URLError { }
        XCTAssertEqual(repository.currentUser, user)
        XCTAssertFalse(changed)
    }

    func testLogoutDiscardsAnInFlightProfileResponse() async throws {
        let api = ProfileAPI()
        api.suspend = true
        let repository = ProfileRepository(api: api)
        var changed = false
        repository.onChange = { changed = true }
        let task = Task { try await repository.getMe() }
        for _ in 0..<1000 {
            if api.pending != nil { break }
            await Task.yield()
        }
        let pending = try XCTUnwrap(api.pending)
        repository.stop()
        pending.resume(returning: userData)
        do {
            _ = try await task.value
            XCTFail("A stopped repository must discard the response")
        } catch is CancellationError { }
        XCTAssertNil(repository.currentUser)
        XCTAssertFalse(changed)
        do {
            _ = try await repository.getMe()
            XCTFail("A stopped repository must not start another request")
        } catch is CancellationError { }
        XCTAssertEqual(api.paths.count, 1)
    }
}

@MainActor
private final class ProfileAPI: APIClient {
    var paths: [String] = []
    var response = Data()
    var fail = false
    var suspend = false
    var pending: CheckedContinuation<Data, Never>?

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        paths.append(path)
        if fail { throw URLError(.notConnectedToInternet) }
        if suspend { return await withCheckedContinuation { pending = $0 } }
        return response
    }
}
