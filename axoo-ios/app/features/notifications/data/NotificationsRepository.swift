import Foundation

struct AppNotification: Decodable, Equatable, Identifiable {
    let id: String
    let actorId: String
    let postId: String
    let commentId: String
    let kind: String
    let nickname: String
    let message: String
    let preview: String
    let createdAt: String
    let seenAt: String?
    var previewEntities: [JSONValue]? = nil

    var previewDocument: EmojiDocument {
        EmojiDocument(text: preview.replacingOccurrences(of: "\n", with: " "),
                      entities: EmojiEntity.decode(previewEntities))
    }
}

struct NotificationsPage: Decodable {
    let notifications: [AppNotification]
    let nextCursor: String?
    let seenThrough: String?
    let unreadCount: Int
}

@MainActor
protocol NotificationsSource {
    func page(cursor: String?) async throws -> NotificationsPage
    func unread() async throws -> Int
    func seen(through: String) async throws -> Int
}

@MainActor
final class NotificationsRepository: NotificationsSource {
    private let api: APIClient
    init(api: APIClient) { self.api = api }
    func page(cursor: String?) async throws -> NotificationsPage {
        var query = [URLQueryItem(name: "limit", value: "20")]
        if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
        let data = try await api.request("GET", path: "notifications", query: query, body: nil)
        return try APIJSON.decode(NotificationsPage.self, from: data)
    }
    func unread() async throws -> Int {
        let data = try await api.request("GET", path: "notifications/unread-count")
        return try APIJSON.decode(Count.self, from: data).unreadCount
    }
    func seen(through: String) async throws -> Int {
        let data = try await api.request("POST", path: "notifications/seen", body: APIJSON.body(["seen_through": through]))
        return try APIJSON.decode(Count.self, from: data).unreadCount
    }
    private struct Count: Decodable { let unreadCount: Int }
}
