import XCTest
@testable import Axoo

@MainActor
final class MyPostsViewModelTests: XCTestCase {
    func testPaginationDeduplicatesAndRetriesFailedPage() async {
        let source = MyPostsStub()
        let model = MyPostsViewModel(source: source)
        model.refresh()
        await model.request?.value
        source.fail = true
        model.loadMore()
        await model.request?.value
        XCTAssertEqual(model.posts.map(\.id), ["first"])
        XCTAssertTrue(model.failedLoadMore)
        source.fail = false
        model.retry()
        await model.request?.value
        XCTAssertEqual(model.posts.map(\.id), ["first", "second"])
        XCTAssertEqual(source.cursors.compactMap { $0 }, ["next", "next"])
        XCTAssertNil(model.nextCursor)
        XCTAssertFalse(model.error)
        model.stop()
    }

    func testRefreshFailureRetainsLoadedPostsAndRetriesFirstPage() async {
        let source = MyPostsStub()
        let model = MyPostsViewModel(source: source)
        model.refresh()
        await model.request?.value
        source.fail = true
        model.refresh()
        await model.request?.value
        XCTAssertEqual(model.posts.map(\.id), ["first"])
        XCTAssertFalse(model.failedLoadMore)
        source.fail = false
        model.retry()
        await model.request?.value
        XCTAssertNil(source.cursors.last!)
        model.stop()
    }

    func testUpdatesAndDeletesSurviveStaleRefresh() async {
        let source = MyPostsStub()
        let model = MyPostsViewModel(source: source)
        model.refresh()
        await model.request?.value
        source.wait = true
        model.refresh()
        while source.continuation == nil { await Task.yield() }
        model.remove("first")
        model.insert(source.post("created"))
        source.continuation?.resume(returning: HomeFeedPage(posts: [source.post("first")], nextCursor: nil))
        await model.request?.value
        XCTAssertEqual(model.posts.map(\.id), ["created"])
        model.stop()
    }

    func testOpeningAfterDetailDoesNotReplaceTheListOrPagination() async {
        let source = MyPostsStub()
        let model = MyPostsViewModel(source: source)
        model.refresh()
        await model.request?.value
        model.loadMore()
        await model.request?.value
        var edited = source.post("second")
        edited.body = "edited"
        model.update(edited)
        XCTAssertEqual(model.posts.map(\.id), ["first", "second"])
        XCTAssertEqual(model.posts.last?.body, "edited")
        XCTAssertNil(model.nextCursor)
        model.stop()
    }
}

@MainActor
private final class MyPostsStub: MyPostsSource {
    var fail = false
    var wait = false
    var cursors: [String?] = []
    var continuation: CheckedContinuation<HomeFeedPage, Error>?
    func post(_ id: String) -> Post {
        Post(id: id, nickname: "Me", campus: "Campus", timeLabel: "ora", body: id, userID: "me")
    }
    func myPostsPage(cursor: String?) async throws -> HomeFeedPage {
        cursors.append(cursor)
        if fail { throw URLError(.notConnectedToInternet) }
        if wait { return try await withCheckedThrowingContinuation { continuation = $0 } }
        return HomeFeedPage(posts: cursor == nil ? [post("first")] : [post("first"), post("second")], nextCursor: cursor == nil ? "next" : nil)
    }
    func vote(_ post: Post, value: Post.Vote) async throws -> Post { post }
}
