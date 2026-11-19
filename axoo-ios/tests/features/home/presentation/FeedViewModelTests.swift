import XCTest
@testable import Axoo

@MainActor
final class FeedViewModelTests: XCTestCase {
    func testPublishSelectsNewAndDoesNotPinPostToHot() async {
        let screen = makeFeed(source: StubHomeSource())
        await screen.refresh(.hot)
        screen.insertCreatedPost(samplePost("created"))
        XCTAssertEqual(screen.selectedFeed, .new)
        XCTAssertEqual(screen.scrollToTopSignal, 1)
        XCTAssertFalse(screen.state(for: .new).isLoading)
        await screen.requests[.new]?.value
        await screen.refresh(.hot)
        XCTAssertEqual(screen.state(for: .new).postIDs, ["created", "shared", "new"])
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["shared", "hot"])
        screen.stop()
    }

    func testAcknowledgedPostUsesServerOrderAndIsNotPinnedOnLaterRefreshes() async {
        let source = StubHomeSource()
        let screen = makeFeed(source: source)
        screen.insertCreatedPost(samplePost("created"))
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.state(for: .new).postIDs.first, "created")
        source.firstPageIDs = ["newer", "created", "older"]
        await screen.refresh(.new)
        XCTAssertEqual(screen.state(for: .new).postIDs, ["newer", "created", "older"])
        source.firstPageIDs = ["newer"]
        await screen.refresh(.new)
        XCTAssertEqual(screen.state(for: .new).postIDs, ["newer"])
        screen.stop()
    }

    func testRefreshErrorKeepsJustPublishedPostAndRetryDoesNotDuplicateIt() async {
        let source = StubHomeSource()
        source.shouldFail = true
        let screen = makeFeed(source: source)
        screen.insertCreatedPost(samplePost("created"))
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.state(for: .new).postIDs, ["created"])
        XCTAssertNotNil(screen.state(for: .new).errorMessage)
        source.shouldFail = false
        screen.retry(.new)
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.state(for: .new).postIDs, ["created", "shared", "new"])
        XCTAssertNil(screen.state(for: .new).errorMessage)
        screen.stop()
    }

    func testCreatedPreviewPostBelongsToCurrentUserAndStartsWithoutComments() async throws {
        let session = DetailTestSession()
        var post = samplePost("created-example")
        post.userID = "me"
        let screen = session.screen(post: post, onChange: { _ in }, onDeleted: { _ in })
        XCTAssertTrue(screen.viewModel.ownsPost)
        screen.viewModel.start()
        await screen.viewModel.loadTask?.value
        XCTAssertTrue(screen.viewModel.state.items.isEmpty)
        session.stop()
    }

    func testIndependentFeedsAndPaginationDeduplicateIDs() async {
        let source = StubHomeSource()
        let screen = makeFeed(source: source)
        screen.start()
        await screen.requests[.hot]?.value
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["shared", "hot"])
        XCTAssertEqual(screen.state(for: .new).postIDs, ["shared", "new"])
        screen.loadMore(.hot)
        await screen.requests[.hot]?.value
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["shared", "hot", "page-2"])
        XCTAssertEqual(screen.state(for: .new).postIDs, ["shared", "new"])
        XCTAssertNil(screen.state(for: .hot).nextCursor)
        screen.stop()
    }

    func testLocalVoteSurvivesNewSelectionAndRefresh() async {
        let screen = makeFeed(source: StubHomeSource(), unread: 3)
        screen.start()
        await screen.requests[.hot]?.value
        await screen.requests[.new]?.value
        screen.vote(postID: "shared", direction: .up)
        screen.select(.new)
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.newUnreadCount, 0)
        XCTAssertEqual(screen.posts["shared"]?.vote, .up)
        XCTAssertEqual(screen.posts["shared"]?.score, 11)
        await screen.refresh(.hot)
        XCTAssertEqual(screen.posts["shared"]?.score, 11)
        screen.vote(postID: "shared", direction: .down)
        XCTAssertEqual(screen.posts["shared"]?.score, 9)
        screen.stop()
    }

    func testRefreshFailurePreservesContentAndRetryReloadsFirstPage() async {
        let source = StubHomeSource()
        let screen = makeFeed(source: source)
        await screen.refresh(.hot)
        source.shouldFail = true
        await screen.refresh(.hot)
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["shared", "hot"])
        XCTAssertNotNil(screen.state(for: .hot).errorMessage)
        source.shouldFail = false
        screen.retry(.hot)
        await screen.requests[.hot]?.value
        XCTAssertEqual(source.cursors.last, "first")
        XCTAssertNil(screen.state(for: .hot).errorMessage)
    }

    func testPaginationFailureRetainsCursorAndRetriesSamePage() async {
        let source = StubHomeSource()
        let screen = makeFeed(source: source)
        await screen.refresh(.hot)
        source.shouldFail = true
        screen.loadMore(.hot)
        await screen.requests[.hot]?.value
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["shared", "hot"])
        XCTAssertEqual(screen.state(for: .hot).nextCursor, "page-2")
        source.shouldFail = false
        screen.retry(.hot)
        await screen.requests[.hot]?.value
        XCTAssertEqual(source.cursors.last, "page-2")
        XCTAssertEqual(screen.state(for: .hot).postIDs.count, 3)
    }

    func testDetailCountAndRemovalSurviveBothFeedRefreshes() async throws {
        let screen = makeFeed(source: StubHomeSource())
        await screen.refresh(.hot)
        var post = try XCTUnwrap(screen.posts["shared"])
        post.commentsCount = 12
        post.toggleVote(.up)
        screen.applyDetailPost(post)
        await screen.refresh(.new)
        XCTAssertEqual(screen.posts["shared"]?.commentsCount, 12)
        XCTAssertEqual(screen.posts["shared"]?.vote, .up)
        screen.removePost("shared")
        await screen.refresh(.hot)
        await screen.refresh(.new)
        XCTAssertNil(screen.posts["shared"])
        XCTAssertFalse(screen.state(for: .hot).postIDs.contains("shared"))
        XCTAssertFalse(screen.state(for: .new).postIDs.contains("shared"))
        screen.stop()
    }

    func testStoppedRequestCannotRepopulateFeed() async {
        let source = SuspendedHomeSource()
        let screen = makeFeed(source: source)
        screen.start()
        let tasks = Array(screen.requests.values)
        for _ in 0..<1000 {
            if source.pending.count == 2 { break }
            await Task.yield()
        }
        XCTAssertEqual(source.pending.count, 2)
        screen.stop()
        for continuation in source.pending {
            continuation.resume(returning: HomeFeedPage(posts: [samplePost("late")], nextCursor: nil))
        }
        for task in tasks { await task.value }
        XCTAssertTrue(screen.posts.isEmpty)
    }
}

@MainActor
private func makeFeed(source: HomeFeedSource, unread: Int = 0) -> FeedViewModel {
    FeedViewModel(source: source, newUnreadCount: unread)
}

@MainActor
private final class StubHomeSource: HomeFeedSource {
    private var updates: [String: Post] = [:]
    func synchronize(_ post: Post) { updates[post.id] = post }
    func vote(_ post: Post, value: Post.Vote) async throws -> Post {
        synchronize(post)
        return post
    }
    var firstPageIDs: [String]?
    var shouldFail = false
    var cursors: [String] = []
    func page(for tab: HomeFeedTab, cursor: String?) async throws -> HomeFeedPage {
        cursors.append(cursor ?? "first")
        if shouldFail { throw URLError(.notConnectedToInternet) }
        return cursor == nil
            ? HomeFeedPage(posts: (firstPageIDs ?? ["shared", tab.rawValue]).map { updates[$0] ?? samplePost($0) }, nextCursor: "page-2")
            : HomeFeedPage(posts: [samplePost(tab.rawValue), samplePost("page-2")], nextCursor: nil)
    }
}

@MainActor
private final class SuspendedHomeSource: HomeFeedSource {
    func vote(_ post: Post, value: Post.Vote) async throws -> Post { post }
    var pending: [CheckedContinuation<HomeFeedPage, Never>] = []
    func page(for tab: HomeFeedTab, cursor: String?) async throws -> HomeFeedPage {
        await withCheckedContinuation { pending.append($0) }
    }
}

private func samplePost(_ id: String) -> Post {
    Post(id: id, nickname: "studente", campus: "UniBa", timeLabel: "ora", body: "Test", score: 10)
}
