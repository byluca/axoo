import XCTest
@testable import Axoo

final class PlaybackTimelineTests: XCTestCase {
    func testDecorativeTimeFreezesWhileEmojiKeepTheirSharedPhase() {
        var local = LottiePlaybackPosition(mode: .local)
        var shared = LottiePlaybackPosition(mode: .synchronized(start: 100))
        local.play(at: 100); shared.play(at: 100)
        local.pause(at: 101); shared.pause(at: 101)
        XCTAssertEqual(local.position(at: 110), 1)
        XCTAssertEqual(shared.position(at: 110), 10)
        local.play(at: 110); shared.play(at: 110)
        XCTAssertEqual(local.position(at: 111), 2)
        XCTAssertEqual(shared.position(at: 111), 11)
    }
    func testSharedTimelineUsesAssetFPSAndWrapsAfterSkippedTicks() {
        XCTAssertEqual(PlaybackTimeline.frame(at: 102.5, since: 100, fps: 60, count: 120), 30)
        XCTAssertEqual(PlaybackTimeline.frame(at: 102.5, since: 100, fps: 24, count: 48), 12)
        XCTAssertEqual(PlaybackTimeline.frame(at: 99, since: 100, fps: 60, count: 120), 0)
        XCTAssertEqual(PlaybackTimeline.frame(at: .infinity, since: 100, fps: 60, count: 120), 0)
    }
    func testSmallEmojiSamplingSkipsFramesWithoutSlowingTheAnimation() {
        XCTAssertEqual(PlaybackTimeline.frame(at: 0.07, since: 0, fps: 60, count: 120, maximumFPS: 45), 4)
        XCTAssertEqual(PlaybackTimeline.frame(at: 0.08, since: 0, fps: 60, count: 120, maximumFPS: 45), 4)
        XCTAssertEqual(PlaybackTimeline.frame(at: 0.14, since: 0, fps: 60, count: 120, maximumFPS: 45), 8)
        XCTAssertEqual(PlaybackTimeline.frame(at: 2, since: 0, fps: 60, count: 120, maximumFPS: 45), 0)
        XCTAssertEqual(PlaybackTimeline.frame(at: 0.5, since: 0, fps: 12, count: 24, maximumFPS: 45), 6)
    }
}
