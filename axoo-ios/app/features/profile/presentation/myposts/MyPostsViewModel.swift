import Foundation

@MainActor
protocol MyPostsSource: PollVotingSource {
    func myPostsPage(cursor: String?) async throws -> HomeFeedPage
    func vote(_ post: Post, value: Post.Vote) async throws -> Post
}

@MainActor
final class MyPostsViewModel {
    private let source: MyPostsSource
    private let page: (String?) async throws -> HomeFeedPage
    private(set) var posts: [Post] = []
    private(set) var nextCursor: String?
    private(set) var loading = false
    private(set) var loadingMore = false
    private(set) var error = false
    private(set) var failedLoadMore = false
    private(set) var loaded = false
    private(set) var request: Task<Void, Never>?
    private var voteTasks: [String: Task<Void, Never>] = [:]
    private var pollTasks: [String: Task<Void, Never>] = [:]
    private(set) var notice: String?
    private var revisions: [String: Int] = [:]
    private var removed = Set<String>()
    private var created = Set<String>()
    var onChange: () -> Void = {}
    var onPostChange: (Post) -> Void = { _ in }

    init(source: MyPostsSource, page: ((String?) async throws -> HomeFeedPage)? = nil) {
        self.source = source
        self.page = page ?? { try await source.myPostsPage(cursor: $0) }
    }

    func refresh() { request?.cancel(); load(cursor: nil) }
    func loadMore() {
        guard !loading, !loadingMore, let cursor = nextCursor else { return }
        load(cursor: cursor)
    }
    func retry() { if failedLoadMore { loadMore() } else { refresh() } }

    private func load(cursor: String?) {
        loading = cursor == nil
        loadingMore = cursor != nil
        error = false
        onChange()
        let expectedRevisions = revisions
        request = Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await self.page(cursor)
                guard !Task.isCancelled else { return }
                var seen = Set<String>()
                let fetched = page.posts.map { post in
                    revisions[post.id, default: 0] != expectedRevisions[post.id, default: 0]
                        ? (posts.first { $0.id == post.id } ?? post) : post
                }
                let recent = posts.filter { created.contains($0.id) && revisions[$0.id, default: 0] != expectedRevisions[$0.id, default: 0] }
                posts = (cursor == nil ? recent + fetched : posts + fetched).filter { !removed.contains($0.id) && seen.insert($0.id).inserted }
                nextCursor = page.nextCursor
                loaded = true
                failedLoadMore = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = true
                failedLoadMore = cursor != nil
            }
            loading = false
            loadingMore = false
            onChange()
        }
    }

    func update(_ post: Post) {
        guard let index = posts.firstIndex(where: { $0.id == post.id }), posts[index] != post else { return }
        revisions[post.id, default: 0] += 1
        posts[index] = post
        onChange()
    }
    func insert(_ post: Post) {
        removed.remove(post.id)
        created.insert(post.id)
        posts.removeAll { $0.id == post.id }
        posts.insert(post, at: 0)
        revisions[post.id, default: 0] += 1
        onChange()
    }
    func remove(_ id: String) {
        removed.insert(id)
        posts.removeAll { $0.id == id }
        voteTasks[id]?.cancel()
        voteTasks[id] = nil
        onChange()
    }
    func removeAuthor(_ id: String) {
        for post in posts where post.userID == id { remove(post.id) }
    }
    func vote(_ id: String, direction: Post.Vote) {
        guard voteTasks[id] == nil, let current = posts.first(where: { $0.id == id }), !current.isVoting else { return }
        let next: Post.Vote = current.vote == direction ? .none : direction
        var optimistic = current
        optimistic.score += next.rawValue - current.vote.rawValue
        optimistic.vote = next
        optimistic.isVoting = true
        update(optimistic)
        onPostChange(optimistic)
        voteTasks[id] = Task { [weak self] in
            guard let self else { return }
            defer { voteTasks[id] = nil }
            do {
                let updated = try await source.vote(current, value: next)
                guard !Task.isCancelled, let latest = posts.first(where: { $0.id == id }) else { return }
                var result = latest
                result.score = updated.score
                result.vote = updated.vote
                result.isVoting = false
                update(result)
                onPostChange(result)
            } catch {
                guard !Task.isCancelled, var latest = posts.first(where: { $0.id == id }) else { return }
                latest.score = current.score
                latest.vote = current.vote
                latest.isVoting = false
                update(latest)
                onPostChange(latest)
            }
        }
    }
    func stop() {
        request?.cancel()
        voteTasks.values.forEach { $0.cancel() }
        pollTasks.values.forEach { $0.cancel() }
        voteTasks.removeAll()
        loading = false
        loadingMore = false
    }
    func clearNotice() { notice = nil }
    func applyPoll(_ poll: PostPoll, postID: String) {
        guard var post = posts.first(where: { $0.id == postID }), post.poll != poll else { return }
        post.poll = poll
        post.canEdit = false
        update(post)
        onPostChange(post)
    }
    func votePoll(postID: String, optionID: String) {
        guard pollTasks[postID] == nil, let original = posts.first(where: { $0.id == postID })?.poll,
              !original.hasVoted, !original.isSending, original.options.contains(where: { $0.id == optionID }) else { return }
        applyPoll(original.selecting(optionID), postID: postID)
        pollTasks[postID] = Task {
            defer { pollTasks[postID] = nil }
            do {
                let confirmed = try await source.votePoll(postID: postID, optionID: optionID)
                applyPoll(confirmed, postID: postID)
            } catch {
                if posts.first(where: { $0.id == postID })?.poll?.isSending == true { applyPoll(original, postID: postID) }
                guard !Task.isCancelled else { return }
                notice = "Voto non salvato. Riprova."
                onChange()
            }
        }
    }
}
