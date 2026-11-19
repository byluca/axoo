import XCTest
@testable import Axoo

@MainActor
final class NotificationsRepositoryTests: XCTestCase {
    func testPreviewKeepsEmojiIdentityAndOffsetsAfterFlatteningNewlines() throws {
        let json = #"{"id":"1","actor_id":"actor","post_id":"post","comment_id":"comment","kind":"post_comment","nickname":"Giulia","message":"Giulia ha commentato il tuo post","preview":"😀\n👩‍🎓❤️","preview_entities":[{"type":"custom_emoji","offset":5,"length":2,"custom_emoji_id":42}],"created_at":"2026-10-16T10:00:00Z","seen_at":null}"#
        let item = try APIJSON.decode(AppNotification.self, from: Data(json.utf8))
        let document = item.previewDocument
        let entity = try XCTUnwrap(document.validEntities.first)
        XCTAssertEqual(document.text, "😀 👩‍🎓❤️")
        XCTAssertEqual(entity.id, 42)
        XCTAssertEqual((document.text as NSString).substring(with: document.range(of: entity)), "❤️")
    }

    func testDecodesServerNicknameAndUsesAuthenticatedNotificationEndpoints() async throws {
        let api = InboxAPI()
        let repository = NotificationsRepository(api: api)
        let page = try await repository.page(cursor: "42")
        XCTAssertEqual(page.notifications.first?.message, "Giulia ha commentato il tuo post")
        XCTAssertEqual(page.notifications.first?.postId, "post")
        XCTAssertEqual(page.notifications.first?.commentId, "comment")
        XCTAssertEqual(page.notifications.first?.previewDocument, EmojiDocument(text: "Ciao"))
        XCTAssertEqual(page.seenThrough, "43")
        let unread = try await repository.unread()
        XCTAssertEqual(unread, 3)
        let remaining = try await repository.seen(through: "43")
        XCTAssertEqual(remaining, 1)
        XCTAssertEqual(api.paths, ["notifications", "notifications/unread-count", "notifications/seen"])
        XCTAssertEqual(api.cursor, "42")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.seenBody)) as? [String: String])
        XCTAssertEqual(body, ["seen_through": "43"])
    }
}

@MainActor
private final class InboxAPI: APIClient {
    var paths: [String] = []
    var cursor: String?
    var seenBody: Data?
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        paths.append(path)
        switch path {
        case "notifications":
            cursor = query.first { $0.name == "cursor" }?.value
            return Data(#"{"notifications":[{"id":"43","actor_id":"actor","post_id":"post","comment_id":"comment","kind":"post_comment","nickname":"Giulia","message":"Giulia ha commentato il tuo post","preview":"Ciao","created_at":"2026-10-16T10:00:00Z","seen_at":null}],"next_cursor":"","seen_through":"43","unread_count":3}"#.utf8)
        case "notifications/unread-count": return Data(#"{"unread_count":3}"#.utf8)
        case "notifications/seen": seenBody = body; return Data(#"{"unread_count":1}"#.utf8)
        default: throw URLError(.unsupportedURL)
        }
    }
}
