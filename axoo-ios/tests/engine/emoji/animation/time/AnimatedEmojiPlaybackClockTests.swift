import XCTest
@testable import Axoo

@MainActor
final class AnimatedEmojiPlaybackClockTests: XCTestCase {
    func testLateJoinersKeepTheSameStartTimeAndOtherAssetsHaveTheirOwn() {
        let clock = AnimatedEmojiPlaybackClock()
        XCTAssertEqual(clock.startTime(for: 1, at: 100), 100)
        XCTAssertEqual(clock.startTime(for: 1, at: 125), 100)
        XCTAssertEqual(clock.startTime(for: 2, at: 125), 125)
    }
    func testOldAnchorsAreEvictedAfter512Assets() {
        let clock = AnimatedEmojiPlaybackClock()
        for id in 0...512 { _ = clock.startTime(for: id, at: Double(id)) }
        XCTAssertEqual(clock.startTime(for: 1, at: 600), 1)
        XCTAssertEqual(clock.startTime(for: 0, at: 600), 600)
    }
}
