import XCTest
@testable import Axoo

@MainActor
final class ReportViewModelTests: XCTestCase {
    func testFailureKeepsSelectionAndRetryCompletes() async {
        var attempts = 0
        let model = ReportViewModel { reason in
            XCTAssertEqual(reason, .spam)
            attempts += 1
            if attempts == 1 { throw URLError(.notConnectedToInternet) }
        }
        XCTAssertFalse(model.send())
        model.select(.spam)
        XCTAssertTrue(model.send())
        XCTAssertFalse(model.send())
        model.select(.other)
        await model.task?.value
        XCTAssertEqual(model.selected, .spam)
        XCTAssertNotNil(model.error)
        XCTAssertFalse(model.isComplete)
        XCTAssertTrue(model.send())
        await model.task?.value
        XCTAssertTrue(model.isComplete)
        XCTAssertEqual(attempts, 2)
    }

    func testLateResponseAfterDismissalCannotComplete() async {
        var continuation: CheckedContinuation<Void, Never>?
        let model = ReportViewModel { _ in await withCheckedContinuation { continuation = $0 } }
        model.select(.spam)
        model.send()
        let task = model.task
        for _ in 0..<1000 { if continuation != nil { break }; await Task.yield() }
        XCTAssertNotNil(continuation)
        model.stop()
        continuation?.resume()
        await task?.value
        XCTAssertFalse(model.isComplete)
        XCTAssertNil(model.error)
    }
}
