
import Foundation
@testable import Axoo

enum CommentRepositoryScenario: String {
    case content, empty, loading, error, deep, own
    case sendError = "send-error", editError = "edit-error", voteError = "vote-error"

}

/// In-memory only. Idempotent create mirrors client_id so a retry cannot duplicate a comment.
@MainActor
final class InMemoryCommentRepository: CommentRepository {
    let access: PostDetailAccess
    private let scenario: CommentRepositoryScenario
    private let delay: UInt64
    private var storage: [String: [Comment]] = [:]
    private var failedLoad = false
    private var failedOperations = Set<String>()
    private var blocked = Set<String>()
    private var removedPosts = Set<String>()

    init(scenario: CommentRepositoryScenario = .content, delay: UInt64 = 250_000_000,
         postAuthorID: String? = nil) {
        self.scenario = scenario
        self.delay = delay
        access = PostDetailAccess(userID: "me", postAuthorID: postAuthorID ?? (scenario == .own ? "me" : "post-author"))
    }
    func comments(postID: String) async throws -> [Comment] {
        if scenario == .loading { while true { try await Task.sleep(nanoseconds: 60_000_000_000) } }
        try await pause()
        if scenario == .error && !failedLoad { failedLoad = true; throw URLError(.notConnectedToInternet) }
        guard !removedPosts.contains(postID) else { throw HTTPFailure(status: 404) }
        let comments = rows(postID).map { blocked.contains($0.userID) ? $0.deleted() : $0 }
        // Only root ordering matters: CommentThread orders each branch chronologically.
        return comments.sorted {
            if $0.score != $1.score { return $0.score > $1.score }
            if $0.createdAt != $1.createdAt { return $0.createdAt > $1.createdAt }
            return $0.id < $1.id
        }
    }
    func create(postID: String, clientID: String, body: String, parentID: String?, entities: [EmojiEntity] = [], media: PostEditorMedia? = nil) async throws -> Comment {
        try await pause()
        var comments = rows(postID)
        if let existing = comments.first(where: { $0.clientID == clientID }) { return existing }
        if scenario == .sendError && failedOperations.insert("send").inserted { throw URLError(.notConnectedToInternet) }
        guard media != nil || CommentInput.isValid(body) else { throw HTTPFailure(status: 422) }
        if let parent = parentID {
            guard let target = comments.first(where: { $0.id == parent }), !target.isDeleted, !blocked.contains(target.userID) else {
                throw HTTPFailure(status: 404)
            }
        }
        let comment = Comment(id: "server-" + clientID, postID: postID, clientID: clientID, parentID: parentID,
                              userID: access.userID, nickname: "Tu", body: body, entities: entities, createdAt: Date(), media: media)
        comments.append(comment)
        storage[postID] = comments
        return comment
    }
    func edit(postID: String, commentID: String, body: String, entities: [EmojiEntity] = []) async throws -> Comment {
        try await pause()
        if scenario == .editError && failedOperations.insert("edit").inserted { throw URLError(.notConnectedToInternet) }
        var comments = rows(postID)
        guard let index = comments.firstIndex(where: { $0.id == commentID && $0.userID == access.userID && !$0.isDeleted }),
              CommentInput.isValid(body) else { throw HTTPFailure(status: 422) }
        comments[index].body = body
        comments[index].entities = entities
        comments[index].isEdited = true
        storage[postID] = comments
        return comments[index]
    }
    func delete(postID: String, commentID: String) async throws {
        try await pause()
        var comments = rows(postID)
        guard let index = comments.firstIndex(where: { $0.id == commentID && $0.userID == access.userID }) else { throw HTTPFailure(status: 403) }
        comments[index] = comments[index].deleted()
        storage[postID] = comments
    }
    func vote(postID: String, commentID: String, vote: Comment.Vote) async throws -> CommentVoteResult {
        try await pause()
        if scenario == .voteError && failedOperations.insert("vote").inserted { throw URLError(.notConnectedToInternet) }
        var comments = rows(postID)
        guard let index = comments.firstIndex(where: { $0.id == commentID && !$0.isDeleted }) else { throw HTTPFailure(status: 404) }
        comments[index].score += vote.rawValue - comments[index].vote.rawValue
        comments[index].vote = vote
        storage[postID] = comments
        return CommentVoteResult(score: comments[index].score, vote: vote)
    }
    func report(postID: String, target: PostReportTarget, reason: String) async throws { try await pause() }
    func block(userID: String) async throws { try await pause(); blocked.insert(userID) }
    func deletePost(postID: String) async throws { try await pause(); removedPosts.insert(postID) }
    private func pause() async throws {
        if delay > 0 { try await Task.sleep(nanoseconds: delay) }
        try Task.checkCancellation()
    }
    private func rows(_ postID: String) -> [Comment] {
        if let existing = storage[postID] { return existing }
        let comments = scenario == .empty || postID.hasPrefix("created-") ? [] : PostDetailFixtures.comments(postID: postID, deep: scenario == .deep)
        storage[postID] = comments
        return comments
    }
}

/// Retains detail state across back/reopen in tests.
@MainActor
final class DetailTestSession {
    private var screens: [String: PostDetailHost] = [:]
    func screen(post: Post, title: String = "Post", editorService: PostEditorService? = nil,
                onChange: @escaping (Post) -> Void,
                onDeleted: @escaping (String) -> Void) -> PostDetailHost {
        let screen = screens[post.id] ?? PostDetailHost(post: post, repository:
            InMemoryCommentRepository(postAuthorID: post.userID.isEmpty ? nil : post.userID), title: title, editorService: editorService)
        screen.viewModel.synchronize(post: post)
        screen.viewModel.onPostChange = onChange
        screen.viewModel.onPostDeleted = onDeleted
        screens[post.id] = screen
        return screen
    }
    func stop() { screens.values.forEach { $0.viewModel.stop() } }
}

enum PostDetailFixtures {
    static func comments(postID: String, deep: Bool = false) -> [Comment] {
        func comment(_ id: String, _ nickname: String, _ body: String, parent: String? = nil,
                     user: String? = nil, second: Double = 0, score: Int = 0) -> Comment {
            Comment(id: id, postID: postID, parentID: parent, userID: user ?? id + "-user", nickname: nickname,
                    body: body, createdAt: Date(timeIntervalSince1970: 1_800_000_000 + second), score: score)
        }
        var result = [
            comment("root", "pausacaffe", "Il gruppo di studio si ritrova in biblioteca? Porto il caffè ☕️", score: 18),
            comment("reply", "fuorisede", "Sì! Al secondo piano, vicino alle finestre.", parent: "root", second: 10, score: 4),
            comment("nested", "matricola", "Perfetto, ci sono anch'io 👩🏽‍💻", parent: "reply", second: 20, score: 2),
            comment("own", "Tu", "Arrivo dopo lezione. Tengo due posti?", parent: "root", user: "me", second: 30, score: 3),
            comment("own-child", "bibliotecanotturna", "Grazie! Un posto per me.", parent: "own", second: 35, score: 1),
            comment("unicode", "campusinternazionale", "Un saluto a tutti 🇮🇹 🇪🇸 👨‍👩‍👧‍👦\nQuesto **testo resta letterale**.", second: 40, score: 6),
            comment("deleted", "", "", second: 50).deleted(),
            comment("survivor", "studentessa", "Questa risposta rimane leggibile anche se il commento sopra è stato eliminato.", parent: "deleted", second: 60)
        ]
        if deep {
            for index in 0..<20 {
                result.append(comment("deep-\(index)", "studente\(index)", "Risposta al livello \(index + 1). La conversazione continua senza uscire dallo schermo.",
                                      parent: index == 0 ? "nested" : "deep-\(index - 1)", second: Double(70 + index)))
            }
        }
        return result
    }
}

