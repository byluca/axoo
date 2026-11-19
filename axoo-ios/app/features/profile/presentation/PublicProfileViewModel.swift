import Foundation

@MainActor
final class PublicProfileViewModel {
    let userID: String
    private let posts: PostRepository
    private(set) var user: AccountProfile?
    private(set) var isLoading = true
    private(set) var error: String?
    private(set) var notice: String?
    private(set) var isBlocking = false
    private(set) var unavailable = false
    private(set) var loadTask: Task<Void, Never>?
    private var blockTask: Task<Void, Never>?
    var onChange: () -> Void = {}
    var isOwn: Bool { posts.profile.currentUser?.id == userID }
    var canShowPosts: Bool { !isLoading && user != nil && (isOwn || user?.isProfilePublic == true) }

    init(userID: String, posts: PostRepository) {
        self.userID = userID
        self.posts = posts
    }
    func load() {
        loadTask?.cancel()
        isLoading = true; error = nil; unavailable = false
        onChange()
        loadTask = Task {
            defer { if !Task.isCancelled { isLoading = false; loadTask = nil; onChange() } }
            do {
                guard !posts.blockedUsers.contains(userID) else { throw HTTPFailure(status: 404) }
                let profile = try await posts.profile.getPublicProfile(userID)
                try Task.checkCancellation()
                guard !posts.blockedUsers.contains(userID) else { throw HTTPFailure(status: 404) }
                user = profile
            } catch {
                guard !Task.isCancelled else { return }
                user = nil
                unavailable = (error as? HTTPFailure)?.status == 404
                self.error = unavailable ? "Profilo non disponibile." : "Impossibile caricare il profilo. Tocca per riprovare."
            }
        }
    }
    func report(_ reason: AxooReportReason) async throws {
        try await posts.profile.reportUser(userID, reason: reason.rawValue)
    }
    func block(onBlocked: @escaping () -> Void) {
        guard !isOwn, !isBlocking else { return }
        isBlocking = true
        onChange()
        blockTask = Task {
            defer { if !Task.isCancelled { isBlocking = false; blockTask = nil; onChange() } }
            do {
                try await posts.block(userID)
                try Task.checkCancellation()
                onBlocked()
            } catch {
                guard !Task.isCancelled else { return }
                notice = AxooOverlayCopy.operationError
            }
        }
    }
    func clearNotice() { notice = nil }
    func cancelLoad() { loadTask?.cancel(); loadTask = nil }
    func stop() {
        cancelLoad()
        blockTask?.cancel(); blockTask = nil
        isBlocking = false
    }
}
