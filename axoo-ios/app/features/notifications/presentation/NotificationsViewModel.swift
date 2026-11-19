import Foundation

@MainActor
final class NotificationsViewModel {
    private let source: NotificationsSource
    private(set) var items: [AppNotification] = []
    private(set) var cursor: String?
    private(set) var loading = false
    private(set) var loadingMore = false
    private(set) var error = false
    private(set) var failedLoadMore = false
    private(set) var unreadCount = 0 { didSet { onUnreadChange(unreadCount) } }
    private(set) var request: Task<Void, Never>?
    private(set) var countRequest: Task<Void, Never>?
    private(set) var seenRequest: Task<Void, Never>?
    private var countRevision = 0
    private var through: String?
    private var acknowledged: String?
    private var dirty = true
    private var visible = false
    var onChange: () -> Void = {}
    var onUnreadChange: (Int) -> Void = { _ in }

    init(source: NotificationsSource) { self.source = source }
    func open() { visible = true; if dirty { refresh() } else { refreshUnread() } }
    func close() { visible = false }
    func foreground() { dirty = true; if visible { refresh() } else { refreshUnread() } }
    func received() { foreground() }
    func refresh() { request?.cancel(); load(cursor: nil) }
    func loadMore() { if !loading && !loadingMore, let cursor { load(cursor: cursor) } }
    func retry() { if failedLoadMore { loadMore() } else { refresh() } }

    func refreshUnread() {
        countRequest?.cancel()
        countRevision += 1
        let revision = countRevision
        countRequest = Task { [weak self] in
            guard let self else { return }
            do {
                let count = try await source.unread()
                guard !Task.isCancelled, revision == countRevision else { return }
                unreadCount = max(0, count)
            } catch { /* Preserve the last confirmed count. */ }
        }
    }

    private func load(cursor: String?) {
        loading = cursor == nil
        loadingMore = cursor != nil
        error = false
        onChange()
        let revision = countRevision
        request = Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await source.page(cursor: cursor)
                guard !Task.isCancelled else { return }
                var ids = Set<String>()
                items = (cursor == nil ? page.notifications : items + page.notifications).filter { ids.insert($0.id).inserted }
                self.cursor = page.nextCursor.flatMap { $0.isEmpty ? nil : $0 }
                if cursor == nil {
                    through = page.seenThrough.flatMap { $0.isEmpty ? nil : $0 }
                    acknowledged = nil
                }
                if revision == countRevision { unreadCount = max(0, page.unreadCount) }
                dirty = false
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

    func acknowledge() {
        guard visible, !loading, !error, seenRequest == nil, let through, through != acknowledged else { return }
        countRevision += 1
        let revision = countRevision
        seenRequest = Task { [weak self] in
            guard let self else { return }
            var success = false
            defer {
                seenRequest = nil
                if success, self.through != through { acknowledge() }
            }
            do {
                let count = try await source.seen(through: through)
                guard !Task.isCancelled else { return }
                acknowledged = through
                success = true
                if revision == countRevision { unreadCount = max(0, count) }
            } catch { /* A later successful display retries the acknowledgement. */ }
        }
    }
    func stop() { request?.cancel(); countRequest?.cancel(); seenRequest?.cancel(); visible = false }
}
