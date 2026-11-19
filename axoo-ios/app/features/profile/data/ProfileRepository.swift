import Foundation

@MainActor
final class ProfileRepository {
    let api: APIClient
    private(set) var currentUser: AccountProfile?
    var onChange: () -> Void = {}
    private var active = true
    init(api: APIClient, currentUser: AccountProfile? = nil) {
        self.api = api
        self.currentUser = currentUser
    }

    func getMe(forceRefresh: Bool = false) async throws -> AccountProfile {
        try checkActive()
        if !forceRefresh, let currentUser { return currentUser }
        let data = try await request("GET", path: "users/me")
        return try cache(APIJSON.decode(AccountProfile.self, from: data))
    }

    func blockedUsers() async throws -> [AccountProfile] {
        struct Response: Decodable { let users: [AccountProfile] }
        let data = try await request("GET", path: "users/blocks")
        return try APIJSON.decode(Response.self, from: data).users
    }
    func getPublicProfile(_ id: String) async throws -> AccountProfile {
        let data = try await request("GET", path: "users/\(try APIIdentifier.path(id))")
        return try APIJSON.decode(AccountProfile.self, from: data)
    }

    func updateProfilePrivacy(isPublic: Bool) async throws -> AccountProfile {
        let data = try await request("PUT", path: "users/me/privacy",
            body: APIJSON.body(["is_profile_public": isPublic]))
        return try cache(APIJSON.decode(AccountProfile.self, from: data))
    }

    func updateBio(_ draft: EmojiDocument) async throws -> AccountProfile {
        if let error = AccountProfile.bioValidationError(draft) {
            throw NSError(domain: "Bio", code: 1, userInfo: [NSLocalizedDescriptionKey: error])
        }
        let document = draft.normalized
        let data = try await request("PUT", path: "users/me/bio", body: APIJSON.body([
            "bio": document.text, "bio_entities": document.entities.map(\.dictionary),
        ]))
        return try cache(APIJSON.decode(AccountProfile.self, from: data))
    }

    func reportUser(_ id: String, reason: String) async throws {
        _ = try await request("POST", path: "reports", body: APIJSON.body([
            "target_type": "user", "target_id": id, "reason": reason,
        ]))
    }
    func unblock(_ id: String) async throws {
        _ = try await request("DELETE", path: "users/blocks/\(try APIIdentifier.path(id))")
    }
    func nicknameAvailable(_ nickname: String) async throws -> Bool {
        struct Response: Decodable { let available: Bool }
        let data = try await request("GET", path: "users/me/nickname-availability",
            query: [URLQueryItem(name: "nickname", value: nickname)], body: nil)
        return try APIJSON.decode(Response.self, from: data).available
    }
    func updateNickname(_ nickname: String) async throws -> AccountProfile {
        if let error = AccountProfile.nicknameValidationError(nickname) {
            throw NSError(domain: "Nickname", code: 1, userInfo: [NSLocalizedDescriptionKey: error])
        }
        let data = try await request("PUT", path: "users/me/nickname",
            body: APIJSON.body(["nickname": nickname]))
        return try cache(APIJSON.decode(AccountProfile.self, from: data))
    }

    func updateAvatar(_ configuration: [String: String]) async throws {
        guard currentUser != nil else { throw AuthFailure.expiredSession }
        _ = try await request("PUT", path: "users/avatar",
            body: APIJSON.body(["avatar_config": configuration]))
        guard var user = currentUser else { throw AuthFailure.expiredSession }
        user.avatarConfig = AvatarConfiguration(values: configuration)
        _ = try cache(user)
    }

    func deleteAccount() async throws {
        _ = try await request("DELETE", path: "users/me", body: APIJSON.body(["confirmation": "DELETE"]))
        stop()
    }

    /// This repository belongs to one authenticated root and is never reused after logout.
    func stop() {
        active = false
        currentUser = nil
        onChange = {}
    }

    private func cache(_ user: AccountProfile) throws -> AccountProfile {
        guard currentUser == nil || currentUser?.id == user.id else { throw AuthFailure.invalidResponse }
        if currentUser != user {
            currentUser = user
            onChange()
        }
        return user
    }

    private func checkActive() throws {
        try Task.checkCancellation()
        guard active else { throw CancellationError() }
    }

    private func request(_ method: String, path: String, query: [URLQueryItem] = [], body: Data? = nil) async throws -> Data {
        try checkActive()
        let data = try await api.request(method, path: path, query: query, body: body)
        try checkActive()
        return data
    }
}
