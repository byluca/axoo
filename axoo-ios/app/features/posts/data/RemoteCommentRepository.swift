import Foundation

@MainActor
final class RemoteCommentRepository: CommentRepository {
    let access: PostDetailAccess
    let posts: PostRepository
    let isPreview = false
    var currentUserAvatar: AvatarConfiguration? { posts.profile.currentUser?.avatarConfig }
    private var commentsByID: [String: CommentDTO] = [:]
    init(posts: PostRepository, authorID: String) {
        self.posts = posts
        access = PostDetailAccess(userID: posts.profile.currentUser?.id ?? "", postAuthorID: authorID)
    }
    func post(postID: String) async throws -> Post? { try await posts.post(postID).asPost() }
    func votePost(_ post: Post, value: Post.Vote) async throws -> Post {
        try await posts.vote(post, value: value)
    }
    func votePoll(postID: String, optionID: String) async throws -> PostPoll {
        try await posts.votePoll(postID: postID, optionID: optionID)
    }
    func comments(postID: String) async throws -> [Comment] {
        struct Response: Decodable { let comments: [CommentDTO]? }
        let response: Response = try await posts.decode("GET", path: base(postID))
        let comments = response.comments ?? []
        comments.forEach { commentsByID[$0.id] = $0 }
        let items = try comments.map { CommentItem(comment: try $0.comment()) }
        let blocked = Set(items.filter { posts.blockedUsers.contains($0.comment.userID) }.map { $0.comment.id })
        return CommentThread.removing(blocked, from: items).map(\.comment)
    }
    func create(postID: String, clientID: String, body: String, parentID: String?, entities: [EmojiEntity] = [], media: PostEditorMedia? = nil) async throws -> Comment {
        struct Response: Decodable { let comment: CommentDTO }
        var payload: [String: Any] = ["client_id": clientID, "body": body, "entities": entities.map(\.dictionary)]
        if let media { payload["media"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(media.payload)) }
        if let parentID = parentID { payload["reply_to_comment_id"] = parentID }
        let response: Response = try await posts.decode("POST", path: base(postID), body: APIJSON.body(payload))
        commentsByID[response.comment.id] = response.comment
        KlipyClient.shared.reportShare(media)
        return try response.comment.comment()
    }
    func edit(postID: String, commentID: String, body: String, entities: [EmojiEntity] = []) async throws -> Comment {
        guard let original = commentsByID[commentID], original.userId == access.userID,
              PostEditorMedia.fromPayload(original.media)?.kind != .sticker,
              EmojiDocument.canEdit(text: original.body ?? "", raw: original.entities) else { throw HTTPFailure(status: 422) }
        _ = try await posts.api.request("PATCH", path: "\(try base(postID))/\(try APIIdentifier.path(commentID))",
                                        body: APIJSON.body(["body": body, "entities": entities.map(\.dictionary)]))
        var updated = original
        updated.body = body
        updated.entities = entities.map(\.payload)
        updated.updatedAt = ISO8601DateFormatter().string(from: Date())
        commentsByID[commentID] = updated
        return try updated.comment()
    }
    func delete(postID: String, commentID: String) async throws {
        _ = try await posts.api.request("DELETE", path: "\(try base(postID))/\(try APIIdentifier.path(commentID))")
    }
    func vote(postID: String, commentID: String, vote: Comment.Vote) async throws -> CommentVoteResult {
        let response: VoteResponse = try await posts.decode("PUT", path: "\(try base(postID))/\(try APIIdentifier.path(commentID))/vote",
                                                            body: APIJSON.body(["value": vote.rawValue]))
        return CommentVoteResult(score: response.vote.score, vote: Comment.Vote(rawValue: response.vote.value) ?? .none)
    }
    func report(postID: String, target: PostReportTarget, reason: String) async throws {
        let type: String, id: String
        switch target { case .post: type = "post"; id = postID; case .comment(let commentID): type = "comment"; id = commentID }
        _ = try await posts.api.request("POST", path: "reports", body: APIJSON.body(["target_type": type, "target_id": id, "reason": reason]))
    }
    func block(userID: String) async throws { try await posts.block(userID) }
    func hidePost(postID: String) async throws { try await posts.hide(postID) }
    func deletePost(postID: String) async throws {
        _ = try await posts.api.request("DELETE", path: "posts/\(try APIIdentifier.path(postID))")
    }
    private func base(_ postID: String) throws -> String { "posts/\(try APIIdentifier.path(postID))/comments" }
}

struct CommentDTO: Decodable {
    let id: String
    let postId: String
    let clientId: String?
    let parentCommentId: String?
    let userId: String
    let nickname: String?
    let universityName: String?
    var body: String?
    let createdAt: String
    var updatedAt: String?
    let isDeleted: Bool?
    let upvotes: Int?
    let downvotes: Int?
    let myVote: String?
    var entities: [JSONValue]?
    var avatarConfig: AvatarConfiguration?
    var media: JSONValue?
    func comment() throws -> Comment {
        guard let created = APIJSON.date(createdAt) else { throw AuthFailure.invalidResponse }
        let value = Comment(id: id, postID: postId, clientID: clientId, parentID: parentCommentId,
                            userID: userId, nickname: nickname ?? "", universityName: universityName, body: body ?? "", entities: EmojiEntity.decode(entities), createdAt: created,
                            isEdited: updatedAt.flatMap(APIJSON.date).map { $0 > created } ?? false,
                            score: (upvotes ?? 0) - (downvotes ?? 0),
                            vote: myVote == "upvote" ? .up : myVote == "downvote" ? .down : .none,
                            canEdit: EmojiDocument.canEdit(text: body ?? "", raw: entities), avatarConfig: avatarConfig, media: PostEditorMedia.fromPayload(media))
        return isDeleted == true ? value.deleted() : value
    }
}
