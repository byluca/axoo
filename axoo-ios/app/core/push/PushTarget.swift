import Foundation

struct PushTarget: Equatable, Sendable {
    let postID: String
    let commentID: String
    let recipientID: String

    init?(userInfo: [AnyHashable: Any]) {
        guard let type = userInfo["type"] as? String,
              ["post_comment", "comment_reply"].contains(type),
              let post = userInfo["post_id"] as? String, UUID(uuidString: post) != nil,
              let comment = userInfo["comment_id"] as? String, UUID(uuidString: comment) != nil,
              let recipient = userInfo["recipient_id"] as? String, UUID(uuidString: recipient) != nil else { return nil }
        postID = post; commentID = comment; recipientID = recipient
    }
}
