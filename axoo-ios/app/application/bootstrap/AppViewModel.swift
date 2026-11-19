import Foundation

@MainActor
final class AppViewModel {
    enum State { case loading, failed, ready }
    private let profile: ProfileRepository
    private(set) var posts: PostRepository?
    private(set) var feed: FeedViewModel?
    private(set) var state = State.loading
    private(set) var loadTask: Task<Void, Never>?
    var onChange: () -> Void = {}
    private var generation = UUID()
    private var stopped = false
    private var navigationTask: Task<Void, Never>?

    init(api: APIClient) { profile = ProfileRepository(api: api) }
    func load() {
        guard !stopped else { return }
        loadTask?.cancel()
        navigationTask?.cancel(); navigationTask = nil
        feed?.stop()
        posts = nil; feed = nil
        let token = UUID(); generation = token
        state = .loading
        onChange()
        loadTask = Task { [weak self] in
            guard let self else { return }
            defer { if generation == token { loadTask = nil } }
            do {
                _ = try await profile.getMe()
                let posts = PostRepository(profile: profile)
                try? await posts.loadBlocks()
                try Task.checkCancellation()
                guard generation == token else { return }
                let feed = FeedViewModel(source: posts)
                self.posts = posts; self.feed = feed
                feed.start()
                await feed.requests[.hot]?.value
                try Task.checkCancellation()
                guard generation == token else { return }
                state = .ready
            } catch {
                guard !Task.isCancelled, generation == token else { return }
                state = .failed
            }
            onChange()
        }
    }
    func stop() {
        stopped = true
        generation = UUID()
        loadTask?.cancel(); loadTask = nil
        feed?.stop()
        profile.stop()
        navigationTask?.cancel(); navigationTask = nil
    }

    func openPost(_ id: String, completion: @escaping (Post?) -> Void) {
        guard !stopped, let posts else { return }
        navigationTask?.cancel()
        let expected = generation
        navigationTask = Task {
            do {
                let post = try await posts.post(id)
                try Task.checkCancellation()
                guard generation == expected else { return }
                completion(post.asPost())
            } catch {
                guard !Task.isCancelled, generation == expected else { return }
                completion(nil)
            }
        }
    }
}
