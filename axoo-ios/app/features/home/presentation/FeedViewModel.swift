import Foundation

/// Owns feed state and requests; HomeHost only renders it and forwards events.
@MainActor
final class FeedViewModel {
    private let source: HomeFeedSource
    private(set) var selectedFeed: HomeFeedTab = .hot
    private(set) var feedStates: [HomeFeedTab: HomeFeedState] = [.hot: HomeFeedState(), .new: HomeFeedState()]
    private(set) var posts: [String: Post] = [:]
    private(set) var newUnreadCount = 0
    private(set) var scrollToTopSignal = 0
    private var started = false
    private var createdIDs: [String] = []
    private var removedPosts = Set<String>()
    private var generations: [HomeFeedTab: UUID] = [:]
    private(set) var requests: [HomeFeedTab: Task<Void, Never>] = [:]
    private var voteTasks: [String: Task<Void, Never>] = [:]
    private var pollTasks: [String: Task<Void, Never>] = [:]
    private var voteOriginals: [String: Post] = [:]
    private var voteTokens: [String: UUID] = [:]
    private var revisions: [String: Int] = [:]
    private var blockedAuthors = Set<String>()
    private var unreadTask: Task<Void, Never>?
    private(set) var notice: String?
    var onChange: () -> Void = {}
    var onPostsChanged: ([String: Post]) -> Void = { _ in }

    init(source: HomeFeedSource, newUnreadCount: Int = 0) {
        self.source = source
        self.newUnreadCount = max(0, newUnreadCount)
    }

    private func notifyChange() {
        onChange()
        onPostsChanged(posts)
    }

    func state(for tab: HomeFeedTab) -> HomeFeedState { feedStates[tab] ?? HomeFeedState() }

    func start() {
        guard !started else { return }
        started = true
        for tab in HomeFeedTab.allCases { request(tab, replacing: true) }
        refreshUnread()
    }

    func select(_ tab: HomeFeedTab) {
        let changed = selectedFeed != tab
        selectedFeed = tab
        notifyChange()
        // Android refreshes New when selected, retaining current content.
        if changed && tab == .new { request(tab, replacing: true) }
    }

    func refreshAll() {
        for tab in HomeFeedTab.allCases { request(tab, replacing: true) }
    }

    func refresh(_ tab: HomeFeedTab) async {
        let task = request(tab, replacing: true)
        await task.value
    }

    func retry(_ tab: HomeFeedTab) {
        request(tab, replacing: !state(for: tab).failedLoadMore)
    }

    func loadMore(_ tab: HomeFeedTab) {
        let state = state(for: tab)
        guard state.nextCursor != nil, !state.isLoading, !state.isLoadingMore,
              state.errorMessage == nil, requests[tab] == nil else { return }
        request(tab, replacing: false)
    }

    func vote(postID: String, direction: Post.Vote) {
        guard var post = posts[postID], !post.isVoting else { return }
        let original = post
        revisions[postID, default: 0] += 1
        post.toggleVote(direction)
        post.isVoting = true
        posts[postID] = post
        notifyChange()
        voteOriginals[postID] = original
        let pending = post
        let token = UUID()
        voteTokens[postID] = token
        voteTasks[postID] = Task {
            defer {
                if voteTokens[postID] == token { voteTasks[postID] = nil; voteOriginals[postID] = nil; voteTokens[postID] = nil }
            }
            do {
                let confirmed = try await source.vote(pending, value: pending.vote)
                try Task.checkCancellation()
                guard voteTokens[postID] == token, var latest = posts[postID] else { return }
                latest.score = confirmed.score; latest.vote = confirmed.vote; latest.isVoting = false
                posts[postID] = latest
                source.synchronize(latest)
                notifyChange()
                revisions[postID, default: 0] += 1
            } catch {
                guard !Task.isCancelled, voteTokens[postID] == token, var latest = posts[postID] else { return }
                latest.score = original.score; latest.vote = original.vote; latest.isVoting = false
                posts[postID] = latest
                source.synchronize(latest)
                revisions[postID, default: 0] += 1
                notice = "Voto non salvato. Riprova."
                notifyChange()
            }
        }
    }

    func clearNotice() { notice = nil }
    func applyPoll(_ poll: PostPoll, postID: String) {
        guard var post = posts[postID], post.poll != poll else { return }
        post.poll = poll
        post.canEdit = false
        posts[postID] = post
        revisions[postID, default: 0] += 1
        notifyChange()
    }
    func votePoll(postID: String, optionID: String) {
        guard pollTasks[postID] == nil, let original = posts[postID]?.poll,
              !original.hasVoted, !original.isSending, original.options.contains(where: { $0.id == optionID }) else { return }
        applyPoll(original.selecting(optionID), postID: postID)
        pollTasks[postID] = Task {
            defer { pollTasks[postID] = nil }
            do {
                let confirmed = try await source.votePoll(postID: postID, optionID: optionID)
                applyPoll(confirmed, postID: postID)
            } catch {
                if posts[postID]?.poll?.isSending == true { applyPoll(original, postID: postID) }
                guard !Task.isCancelled else { return }
                notice = "Voto non salvato. Riprova."
                notifyChange()
            }
        }
    }
    func refreshUnread() {
        unreadTask?.cancel()
        unreadTask = Task {
            guard let count = try? await source.unreadCount(), !Task.isCancelled else { return }
            newUnreadCount = max(0, count)
            notifyChange()
        }
    }
    func unblockAuthor(_ userID: String) { blockedAuthors.remove(userID) }

    func removeAuthor(_ userID: String) {
        blockedAuthors.insert(userID)
        posts.values.filter { $0.userID == userID }.map(\.id).forEach(removePost)
    }

    func applyEditorResult(_ result: PostEditorResult, post: Post) { applyDetailPost(result.applying(to: post)) }

    func applyDetailPost(_ post: Post) {
        guard posts[post.id] != nil else { return }
        posts[post.id] = post
        revisions[post.id, default: 0] += 1
        notifyChange()
        source.synchronize(post)
    }

    func insertCreatedPost(_ post: Post) {
        guard !removedPosts.contains(post.id) else { return }
        posts[post.id] = post
        revisions[post.id, default: 0] += 1
        if !createdIDs.contains(post.id) { createdIDs.insert(post.id, at: 0) }
        var next = state(for: .new)
        next.postIDs.removeAll { $0 == post.id }
        next.postIDs.insert(post.id, at: 0)
        next.isLoading = false
        next.isLoadingMore = false
        next.errorMessage = nil
        next.failedLoadMore = false
        feedStates[.new] = next
        selectedFeed = .new
        scrollToTopSignal += 1
        notifyChange()
        request(.new, replacing: true)
    }

    func removePost(_ id: String) {
        removedPosts.insert(id)
        createdIDs.removeAll { $0 == id }
        posts[id] = nil
        for tab in HomeFeedTab.allCases {
            var state = state(for: tab)
            state.postIDs.removeAll { $0 == id }
            feedStates[tab] = state
        }
        notifyChange()
    }

    func stop() {
        unreadTask?.cancel()
        voteTasks.values.forEach { $0.cancel() }
        pollTasks.values.forEach { $0.cancel() }
        for (id, original) in voteOriginals {
            posts[id]?.score = original.score; posts[id]?.vote = original.vote; posts[id]?.isVoting = false
        }
        voteTasks.removeAll(); voteOriginals.removeAll()
        voteTokens.removeAll()
        for task in requests.values { task.cancel() }
        requests.removeAll()
        generations.removeAll()
        started = false
        notifyChange()
    }

    @discardableResult
    private func request(_ tab: HomeFeedTab, replacing: Bool) -> Task<Void, Never> {
        requests[tab]?.cancel()
        let generation = UUID()
        let versions = revisions
        generations[tab] = generation
        var state = state(for: tab)
        let cursor = replacing ? nil : state.nextCursor
        state.isLoading = state.postIDs.isEmpty
        state.isLoadingMore = !replacing
        state.errorMessage = nil
        feedStates[tab] = state
        notifyChange()
        let task = Task {
            defer { if generations[tab] == generation { requests[tab] = nil } }
            do {
                try Task.checkCancellation()
                let page = try await source.page(for: tab, cursor: cursor)
                try Task.checkCancellation()
                guard generations[tab] == generation else { return }
                var next = self.state(for: tab)
                // Match Android: a just-published post stays in New until the server
                // includes it. Once acknowledged, the server controls its position.
                let receivedIDs = Set(page.posts.map(\.id))
                if tab == .new {
                    createdIDs.removeAll { receivedIDs.contains($0) }
                }
                let pending = tab == .new ? createdIDs.filter {
                    !removedPosts.contains($0) && !receivedIDs.contains($0)
                } : []
                var ids = replacing ? pending : next.postIDs
                var seen = Set(ids)
                for var post in page.posts where !removedPosts.contains(post.id) && !blockedAuthors.contains(post.userID) {
                    if revisions[post.id, default: 0] != versions[post.id, default: 0], let local = posts[post.id] {
                        post = local
                    }
                    if let pending = posts[post.id], pending.isVoting {
                        post.score = pending.score; post.vote = pending.vote; post.isVoting = true
                    }
                    posts[post.id] = post
                    if seen.insert(post.id).inserted { ids.append(post.id) }
                }
                next.postIDs = ids
                // A repeated cursor must not trigger an endless load-more loop.
                next.nextCursor = page.nextCursor == cursor ? nil : page.nextCursor
                next.isLoading = false
                next.isLoadingMore = false
                next.errorMessage = nil
                next.failedLoadMore = false
                feedStates[tab] = next
                notifyChange()
                if tab == .new && selectedFeed == .new {
                    do {
                        try await source.markSeen(page.posts)
                        try Task.checkCancellation()
                        guard generations[tab] == generation else { return }
                        unreadTask?.cancel()
                        newUnreadCount = 0
                        notifyChange()
                    } catch { /* Keep the unread count until the server acknowledges it. */ }
                }
            } catch {
                guard generations[tab] == generation else { return }
                var next = self.state(for: tab)
                next.isLoading = false
                next.isLoadingMore = false
                if !Task.isCancelled {
                    next.errorMessage = "C'è stato un problema di connessione. Riprova."
                    next.failedLoadMore = !replacing
                }
                feedStates[tab] = next
                notifyChange()
            }
        }
        requests[tab] = task
        return task
    }
}
