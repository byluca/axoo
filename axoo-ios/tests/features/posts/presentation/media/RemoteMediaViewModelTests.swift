import XCTest
@testable import Axoo

@MainActor
final class RemoteMediaViewModelTests: XCTestCase {
    func testKlipyStickerUsesItsOwnTrendingAndSearchEndpoints() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .sticker)
        model.loadNextPage()
        let trending = try await request(api, at: 0)
        XCTAssertEqual(trending.path, "stickers/trending")
        trending.continuation.resume(returning: try gif("sticker", total: 1, kind: "sticker"))
        await model.loadTask?.value
        XCTAssertEqual(model.items.first?.kind, .sticker)
        model.search("gatto", immediately: true)
        let search = try await request(api, at: 1)
        XCTAssertEqual(search.path, "stickers/search")
        XCTAssertEqual(search.query["q"], "gatto")
        search.continuation.resume(returning: try gif("found", total: 1, kind: "sticker"))
        await model.loadTask?.value
        XCTAssertEqual(model.items.first?.kind, .sticker)
        model.stop()
    }

    func testGifPaginationAppendsAndStopsAtServerTotalWithoutDuplicateRequests() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .gif)
        var appended: [Range<Int>] = []
        model.onChange = { if case .append(let indices) = $0 { appended.append(indices) } }
        model.loadNextPage()
        model.loadNextPage()
        let first = try await request(api, at: 0)
        XCTAssertEqual(api.requests.count, 1)
        XCTAssertEqual(first.path, "gifs/trending")
        first.continuation.resume(returning: try gif("first", total: 2))
        await model.loadTask?.value
        model.loadNextPage()
        let second = try await request(api, at: 1)
        XCTAssertEqual(second.query["page"], "2")
        second.continuation.resume(returning: try gif("second", total: 1, page: 2))
        await model.loadTask?.value
        model.loadNextPage()
        XCTAssertEqual(api.requests.count, 2)
        XCTAssertEqual(model.items.map(\.id), ["first", "second"])
        XCTAssertEqual(appended, [0..<1, 1..<2])
        model.stop()
    }

    func testMemeUsesKlipySearchAndPagePagination() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .meme)
        model.search("sport", immediately: true)
        let first = try await request(api, at: 0)
        XCTAssertEqual(first.path, "static-memes/search")
        XCTAssertEqual(first.query["q"], "sport")
        XCTAssertNil(first.query["format_filter"])
        first.continuation.resume(returning: try meme("one", page: 1, hasNext: true))
        await model.loadTask?.value
        model.loadNextPage()
        let second = try await request(api, at: 1)
        XCTAssertEqual(second.query["page"], "2")
        second.continuation.resume(returning: try meme("two"))
        await model.loadTask?.value
        model.loadNextPage()
        XCTAssertEqual(api.requests.count, 2)
        XCTAssertEqual(model.items.count, 2)
        model.stop()
    }

    func testMemeSearchRejectsAnOlderResponseAfterNewResultsArrive() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .meme)
        model.loadNextPage()
        let oldRequest = try await request(api, at: 0)
        let oldTask = model.loadTask
        model.search("sport", immediately: true)
        let newRequest = try await request(api, at: 1)
        newRequest.continuation.resume(returning: try meme("sport"))
        await model.loadTask?.value
        oldRequest.continuation.resume(returning: try meme("italia"))
        await oldTask?.value
        XCTAssertEqual(model.items.map(\.id), ["sport"])
        XCTAssertFalse(model.hasError)
        XCTAssertFalse(model.isLoading)
        model.stop()
    }

    func testSearchDebouncesPreservesQueryAndRejectsThePreviousPage() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .gif)
        model.loadNextPage()
        let old = try await request(api, at: 0)
        let oldTask = model.loadTask
        model.search("ca")
        model.search("  campus  ")
        old.continuation.resume(returning: try gif("old", total: 1))
        await oldTask?.value
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertEqual(api.requests.count, 1)
        await model.searchTask?.value
        let current = try await request(api, at: 1)
        XCTAssertEqual(current.path, "gifs/search")
        XCTAssertEqual(current.query["q"], "  campus  ")
        XCTAssertEqual(current.query["page"], "1")
        current.continuation.resume(returning: try gif("current", total: 1))
        await model.loadTask?.value
        XCTAssertEqual(model.items.map(\.id), ["current"])
        model.stop()
    }

    func testSearchButtonCancelsPendingDebounceAndStartsImmediately() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .gif)
        model.search("pending")
        let debounce = model.searchTask
        model.search("submitted", immediately: true)
        let current = try await request(api, at: 0)
        await debounce?.value
        XCTAssertEqual(api.requests.count, 1)
        XCTAssertEqual(current.query["q"], "submitted")
        current.continuation.resume(returning: try gif("submitted", total: 1))
        await model.loadTask?.value
        model.stop()
    }

    func testFailedPageCanBeRetriedAtTheSameOffset() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .gif)
        model.loadNextPage()
        let failed = try await request(api, at: 0)
        failed.continuation.resume(throwing: URLError(.notConnectedToInternet))
        await model.loadTask?.value
        XCTAssertTrue(model.hasError)
        XCTAssertFalse(model.isLoading)
        model.loadNextPage()
        let retry = try await request(api, at: 1)
        XCTAssertEqual(retry.query["page"], "1")
        retry.continuation.resume(returning: try gif("retried", total: 1))
        await model.loadTask?.value
        XCTAssertEqual(model.items.map(\.id), ["retried"])
        XCTAssertFalse(model.hasError)
        model.stop()
    }

    func testClosingDiscardsLateResponseAndPreventsNewRequests() async throws {
        let api = MediaAPI()
        let model = makeModel(api, kind: .gif)
        model.loadNextPage()
        let pending = try await request(api, at: 0)
        let task = model.loadTask
        model.stop()
        var changes = 0
        model.onChange = { _ in changes += 1 }
        pending.continuation.resume(returning: try gif("late", total: 1))
        await task?.value
        model.loadNextPage()
        model.search("after-close", immediately: true)
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertFalse(model.isLoading)
        XCTAssertFalse(model.hasError)
        XCTAssertEqual(changes, 0)
        XCTAssertEqual(api.requests.count, 1)
    }

    func testPreviewWithoutRepositoryDoesNotStartLoading() {
        let model = RemoteMediaViewModel(repository: nil, kind: .gif)
        model.loadNextPage()
        XCTAssertFalse(model.isLoading)
        XCTAssertFalse(model.hasError)
        XCTAssertTrue(model.items.isEmpty)
        model.stop()
    }

    private func makeModel(_ api: MediaAPI, kind: PostEditorMedia.Kind) -> RemoteMediaViewModel {
        RemoteMediaViewModel(repository: RemoteMediaRepository(api: api, klipy: api), kind: kind)
    }
    private func request(_ api: MediaAPI, at index: Int) async throws -> MediaAPI.Request {
        for _ in 0..<1000 {
            if api.requests.count > index { return api.requests[index] }
            await Task.yield()
        }
        XCTFail("Expected media request \(index)")
        throw URLError(.timedOut)
    }
    private func gif(_ id: String, total: Int, kind: String = "gif", page: Int = 1) throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "result": true, "data": ["current_page": page, "has_next": total > 1,
                "data": [["slug": id, "title": id, "type": kind,
                    "file": ["hd": ["gif": ["url": "https://static.klipy.com/\(id).gif", "width": 100, "height": 100]]]]]]
        ])
    }
    private func meme(_ id: String, page: Int = 1, hasNext: Bool = false) throws -> Data {
        try JSONSerialization.data(withJSONObject: ["result": true, "data": ["current_page": page, "has_next": hasNext,
            "data": [["slug": id, "title": id, "type": "static_meme", "file": ["hd": ["png": [
                "url": "https://static.klipy.com/\(id).png", "width": 100, "height": 100
            ]]]]]]])
    }
}

@MainActor
private final class MediaAPI: APIClient {
    struct Request {
        let path: String
        let query: [String: String]
        let continuation: CheckedContinuation<Data, Error>
    }
    var requests: [Request] = []
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            requests.append(Request(path: path, query: Dictionary(uniqueKeysWithValues:
                query.map { ($0.name, $0.value ?? "") }), continuation: continuation))
        }
    }
}
