import Foundation

@MainActor
final class ModerationRepository {
    private let profile: ProfileRepository

    init(profile: ProfileRepository) { self.profile = profile }

    func queue() async throws -> [ModerationItem] {
        struct Response: Decodable { let items: [ModerationItem] }
        try await checkAccess()
        let data = try await profile.api.request("GET", path: "moderation/queue",
            query: [URLQueryItem(name: "status", value: "open")], body: nil)
        try await checkAccess()
        return try APIJSON.decode(Response.self, from: data).items
    }

    func decide(_ decision: ModerationDecision, item: ModerationItem, reason: AxooReportReason?) async throws {
        try await checkAccess()
        guard decision != .hide || reason != nil else { throw HTTPFailure(status: 400) }
        _ = try await profile.api.request("POST", path: "moderation/actions", body: APIJSON.body([
            "action": decision.rawValue,
            "target_type": item.targetType.rawValue,
            "target_id": item.targetId,
            "reason": reason?.rawValue ?? ""
        ]))
        try await checkAccess()
    }

    private func checkAccess() async throws {
        try Task.checkCancellation()
        let user = try await profile.getMe()
        guard user.isModerator == true else { throw HTTPFailure(status: 403) }
    }
}
