import Foundation

enum HomeFeedTab: String, CaseIterable, Identifiable {
    case hot, new
    var id: String { rawValue }
    var title: String { self == .hot ? "Hot" : "New" }
}

/// Feed data and mutations supplied by live or in-memory repositories.
struct HomeFeedPage {
    let posts: [Post]
    let nextCursor: String?
}

@MainActor
protocol HomeFeedSource: PollVotingSource {
    func synchronize(_ post: Post)
    func vote(_ post: Post, value: Post.Vote) async throws -> Post
    func page(for tab: HomeFeedTab, cursor: String?) async throws -> HomeFeedPage
    func unreadCount() async throws -> Int?
    func markSeen(_ posts: [Post]) async throws
}

extension HomeFeedSource {
    func synchronize(_ post: Post) { }
    func unreadCount() async throws -> Int? { nil }
    func markSeen(_ posts: [Post]) async throws { }
}

struct HomeFeedState {
    var postIDs: [String] = []
    var nextCursor: String?
    var isLoading = true
    var isLoadingMore = false
    var errorMessage: String?
    var failedLoadMore = false
    var isEmpty: Bool { postIDs.isEmpty && nextCursor == nil && !isLoading && errorMessage == nil }
}
