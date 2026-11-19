import XCTest
@testable import Axoo

final class CommentThreadTests: XCTestCase {
    func testRootOrderAndChronologicalDepthFirstReplies() {
        let rows = CommentThread.flatten([item("b"), item("late", parent: "a", second: 9), item("a"),
                                          item("grandchild", parent: "early", second: 5), item("early", parent: "a", second: 1)])
        XCTAssertEqual(rows.map { $0.item.comment.id }, ["b", "a", "early", "grandchild", "late"])
        XCTAssertEqual(rows.map(\.depth), [0, 0, 1, 2, 1])
        XCTAssertEqual(rows.map(\.rootID), ["b", "a", "a", "a", "a"])
        XCTAssertEqual(rows.map(\.isLast), [true, false, false, false, true])
    }
    func testOrphansCyclesAndDuplicateIDsNeverLoopOrLoseUniqueComments() {
        let rows = CommentThread.flatten([item("orphan", parent: "missing"), item("a", parent: "b"), item("b", parent: "a"), item("a")])
        XCTAssertEqual(rows.count, 3)
        XCTAssertEqual(Set(rows.map { $0.item.comment.id }), ["orphan", "a", "b"])
        XCTAssertEqual(rows.first?.depth, 0)
    }
    func testDeepThreadsUseIterationAndOnlyFourConnectorLevels() {
        var items: [CommentItem] = []
        items.reserveCapacity(2001)
        for level in 0...2000 {
            let parent: String? = level == 0 ? nil : String(level - 1)
            items.append(item(String(level), parent: parent))
        }
        let rows = CommentThread.flatten(Array(items.reversed()))
        XCTAssertEqual(rows.last?.depth, 2000)
        let connectorLevels: [Int] = rows.flatMap { $0.linesAbove + $0.linesBelow }
        XCTAssertTrue(connectorLevels.allSatisfy { (1...4).contains($0) })
    }
    func testDeletedAncestorsLosePersonalContentAndArePrunedOnlyAfterLastDescendant() throws {
        let items = [item("a"), item("b", parent: "a"), item("c", parent: "b"), item("other")]
        let ancestors = CommentThread.removing(["a", "b"], from: items)
        let deleted = try XCTUnwrap(ancestors.first?.comment)
        XCTAssertTrue(deleted.isDeleted)
        XCTAssertEqual(deleted.body, "")
        XCTAssertEqual(deleted.userID, "")
        XCTAssertEqual(deleted.nickname, "")
        XCTAssertEqual(CommentThread.flatten(ancestors).map(\.depth), [0, 1, 2, 0])
        XCTAssertEqual(CommentThread.removing(["c"], from: ancestors).map { $0.comment.id }, ["other"])
    }
    func testConnectorContinuityAcrossRecycledRows() {
        let rows = CommentThread.flatten([item("root"), item("a", parent: "root", second: 1),
                                          item("nested", parent: "a", second: 2), item("last", parent: "root", second: 3)])
        XCTAssertEqual(rows.map(\.linesAbove), [[], [1], [1, 2], [1]])
        XCTAssertEqual(rows.map(\.linesBelow), [[1], [1, 2], [1], []])
        for (first, second) in zip(rows, rows.dropFirst()) { XCTAssertEqual(first.linesBelow, second.linesAbove) }
    }
    func testReplyStaysAvailableWhileVoteIsPending() {
        var pending = item("comment")
        pending.isBusy = true
        pending.isVoting = true
        XCTAssertTrue(pending.canReply)
        XCTAssertFalse(pending.canInteract)
        pending.isVoting = false
        XCTAssertFalse(pending.canReply)
    }
    func testCommentLimitCountsUnicodeScalarsRatherThanGraphemesOrUTF16() {
        XCTAssertTrue(CommentInput.isValid(String(repeating: "😂", count: 500)))
        XCTAssertFalse(CommentInput.isValid(String(repeating: "😂", count: 501)))
        XCTAssertFalse(CommentInput.isValid("  \n\t"))
        let family = "👨‍👩‍👧‍👦"
        XCTAssertEqual(family.count, 1)
        XCTAssertEqual(family.unicodeScalars.count, 7)
        XCTAssertFalse(CommentInput.isValid(String(repeating: family, count: 72)))
        XCTAssertTrue(CommentInput.isValid("A😂B e\u{301} 🇮🇹"))
    }
    private func item(_ id: String, parent: String? = nil, second: TimeInterval = 0) -> CommentItem {
        CommentItem(comment: Comment(id: id, postID: "post", parentID: parent, userID: "user", nickname: "Studente", body: "Testo",
                                     createdAt: Date(timeIntervalSince1970: second)))
    }
}
