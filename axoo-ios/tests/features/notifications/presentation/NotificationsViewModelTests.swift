import XCTest
import UIKit
@testable import Axoo

@MainActor
final class NotificationsViewModelTests: XCTestCase {
    func testSkeletonCoversInitialLoadAndPaginationAndLeavesCachedItemsVisible() async {
        let source = InboxSource()
        source.fail = true
        let model = NotificationsViewModel(source: source)
        let host = NotificationsHost(viewModel: model)
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        let table = host.view.subviews.compactMap { $0 as? UITableView }.first!
        func hasSkeleton() -> Bool {
            (table.tableFooterView as? UIStackView)?.arrangedSubviews.contains { $0 is SkeletonCardView } == true
        }
        model.open()
        XCTAssertTrue(hasSkeleton())
        await model.request?.value
        XCTAssertFalse(hasSkeleton())
        XCTAssertTrue(model.error)
        source.fail = false
        model.retry()
        XCTAssertTrue(hasSkeleton())
        await model.request?.value
        XCTAssertFalse(hasSkeleton())
        XCTAssertEqual(table.numberOfRows(inSection: 0), 1)
        model.loadMore()
        XCTAssertTrue(hasSkeleton())
        XCTAssertEqual(table.numberOfRows(inSection: 0), 1)
        await model.request?.value
        XCTAssertFalse(hasSkeleton())
        model.refresh()
        XCTAssertFalse(hasSkeleton())
        XCTAssertEqual(table.numberOfRows(inSection: 0), 2)
        await model.request?.value
        model.stop()
    }

    func testAcknowledgesOnlyAfterSuccessfulVisibleDisplayAndPreservesNewArrivalCount() async {
        let source = InboxSource()
        let model = NotificationsViewModel(source: source)
        model.acknowledge()
        model.open()
        await model.request?.value
        XCTAssertTrue(source.seenIDs.isEmpty)
        XCTAssertEqual(model.unreadCount, 3)
        model.acknowledge()
        await model.seenRequest?.value
        XCTAssertEqual(source.seenIDs, ["3"])
        XCTAssertEqual(model.unreadCount, 1)
        model.acknowledge()
        XCTAssertEqual(source.seenIDs, ["3"])
        model.stop()
    }

    func testFailedOpenDoesNotMarkSeen() async {
        let source = InboxSource()
        source.fail = true
        let model = NotificationsViewModel(source: source)
        model.open()
        await model.request?.value
        model.acknowledge()
        XCTAssertTrue(model.error)
        XCTAssertTrue(source.seenIDs.isEmpty)
        model.stop()
    }

    func testPaginationFailureKeepsListAndRetriesFailedCursorWithoutDuplicates() async {
        let source = InboxSource()
        let model = NotificationsViewModel(source: source)
        model.open()
        await model.request?.value
        source.fail = true
        model.loadMore()
        await model.request?.value
        XCTAssertEqual(model.items.map(\.id), ["3"])
        XCTAssertTrue(model.failedLoadMore)
        source.fail = false
        model.retry()
        await model.request?.value
        XCTAssertEqual(model.items.map(\.id), ["3", "2"])
        XCTAssertEqual(source.cursors.compactMap { $0 }, ["2", "2"])
        XCTAssertNil(model.cursor)
        model.stop()
    }

    func testLateCountCannotUndoAcknowledgement() async {
        let source = InboxSource()
        let model = NotificationsViewModel(source: source)
        model.open()
        await model.request?.value
        source.waitCount = true
        model.refreshUnread()
        while source.countContinuation == nil { await Task.yield() }
        model.acknowledge()
        await model.seenRequest?.value
        XCTAssertEqual(model.unreadCount, 1)
        source.countContinuation?.resume(returning: 9)
        await model.countRequest?.value
        XCTAssertEqual(model.unreadCount, 1)
        model.stop()
    }
}

@MainActor
private final class InboxSource: NotificationsSource {
    var fail = false
    var waitCount = false
    var countContinuation: CheckedContinuation<Int, Error>?
    var seenIDs: [String] = []
    var cursors: [String?] = []
    func item(_ id: String) -> AppNotification {
        AppNotification(id: id, actorId: "actor", postId: "post", commentId: "comment", kind: "post_comment", nickname: "Giulia",
                        message: "Giulia ha commentato il tuo post", preview: "Testo", createdAt: "2026-10-16T10:00:00Z", seenAt: nil)
    }
    func page(cursor: String?) async throws -> NotificationsPage {
        cursors.append(cursor)
        if fail { throw URLError(.notConnectedToInternet) }
        return NotificationsPage(notifications: cursor == nil ? [item("3")] : [item("3"), item("2")],
                                 nextCursor: cursor == nil ? "2" : "", seenThrough: "3", unreadCount: 3)
    }
    func unread() async throws -> Int {
        if waitCount { return try await withCheckedThrowingContinuation { countContinuation = $0 } }
        return 3
    }
    func seen(through: String) async throws -> Int { seenIDs.append(through); return 1 }
}
