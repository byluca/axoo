import Foundation

struct Comment: Identifiable, Equatable {
    enum Vote: Int { case down = -1, none = 0, up = 1 }
    let id: String
    let postID: String
    var clientID: String?
    var parentID: String?
    var userID: String
    var nickname: String
    var universityName: String? = nil
    var body: String
    var entities: [EmojiEntity] = []
    var createdAt: Date
    var isEdited = false
    var isDeleted = false
    var score = 0
    var vote: Vote = .none
    var canEdit = true
    var avatarConfig: AvatarConfiguration?
    var media: PostEditorMedia? = nil

    var stableID: String { clientID.map { "client-" + $0 } ?? id }
    var authorLabel: String { nickname.isEmpty ? "Anonimo" : nickname }
    var allowsEditing: Bool { canEdit && !isDeleted && media?.kind != .sticker }

    mutating func toggleVote(_ direction: Vote) {
        guard direction != .none, !isDeleted else { return }
        let next: Vote = vote == direction ? .none : direction
        score += next.rawValue - vote.rawValue
        vote = next
    }
    func deleted() -> Comment {
        var copy = self
        copy.isDeleted = true
        copy.body = ""
        copy.media = nil
        copy.entities = []
        copy.nickname = ""
        copy.userID = ""
        copy.avatarConfig = nil
        copy.score = 0
        copy.vote = .none
        return copy
    }
}


enum CommentInput {
    static let limit = 500
    static func normalized(_ text: String) -> String { text.trimmingCharacters(in: .whitespacesAndNewlines) }
    static func isValid(_ text: String) -> Bool {
        !normalized(text).isEmpty && text.unicodeScalars.count <= limit
    }
}

struct CommentVoteResult {
    let score: Int
    let vote: Comment.Vote
}

struct PostDetailAccess {
    let userID: String
    let postAuthorID: String
}

enum PostReportTarget { case post, comment(String) }

/// No UIKit or display models cross this boundary. A live implementation can map the Android API DTOs here.
@MainActor
protocol CommentRepository: AnyObject, PollVotingSource {
    var access: PostDetailAccess { get }
    var isPreview: Bool { get }
    var currentUserAvatar: AvatarConfiguration? { get }
    func post(postID: String) async throws -> Post?
    func votePost(_ post: Post, value: Post.Vote) async throws -> Post
    func comments(postID: String) async throws -> [Comment]
    func create(postID: String, clientID: String, body: String, parentID: String?, entities: [EmojiEntity], media: PostEditorMedia?) async throws -> Comment
    func edit(postID: String, commentID: String, body: String, entities: [EmojiEntity]) async throws -> Comment
    func delete(postID: String, commentID: String) async throws
    func vote(postID: String, commentID: String, vote: Comment.Vote) async throws -> CommentVoteResult
    func report(postID: String, target: PostReportTarget, reason: String) async throws
    func block(userID: String) async throws
    func hidePost(postID: String) async throws
    func deletePost(postID: String) async throws
}

extension CommentRepository {
    func hidePost(postID: String) async throws { throw HTTPFailure(status: 501) }
    var isPreview: Bool { true }
    var currentUserAvatar: AvatarConfiguration? { nil }
    func post(postID: String) async throws -> Post? { nil }
    func votePost(_ post: Post, value: Post.Vote) async throws -> Post { post }
}

enum CommentValidationError: LocalizedError {
    case invalidBody
    var errorDescription: String? { "Il commento deve contenere testo valido." }
}
