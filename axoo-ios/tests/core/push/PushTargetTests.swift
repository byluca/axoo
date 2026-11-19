import XCTest
@testable import Axoo

final class PushTargetTests: XCTestCase {
    func testCommentAndReplyTargetsUseTheExistingPost() {
        for type in ["post_comment", "comment_reply"] {
            let payload = validPayload(type: type)
            let target = PushTarget(userInfo: payload)
            XCTAssertEqual(target?.postID, payload["post_id"] as? String)
            XCTAssertEqual(target?.recipientID, payload["recipient_id"] as? String)
        }
    }
    func testIncompleteAndUnknownPayloadsAreIgnored() {
        XCTAssertNil(PushTarget(userInfo: validPayload(type: "unknown")))
        var payload = validPayload(type: "post_comment")
        payload.removeValue(forKey: "recipient_id")
        XCTAssertNil(PushTarget(userInfo: payload))
        payload = validPayload(type: "post_comment")
        payload["post_id"] = "not-a-uuid"
        XCTAssertNil(PushTarget(userInfo: payload))
        XCTAssertNil(PushTarget(userInfo: [:]))
    }
    private func validPayload(type: String) -> [AnyHashable: Any] {
        ["type": type, "post_id": UUID().uuidString, "comment_id": UUID().uuidString, "recipient_id": UUID().uuidString]
    }
}
