import XCTest
import UIKit
@testable import Axoo

final class PlaybackPolicyTests: XCTestCase {
    func testSamplingUsesRasterSizeAndScreenScale() {
        XCTAssertEqual(PlaybackPolicy.maximumFrameRate(sizePx: 96, scale: 3), 45)
        XCTAssertEqual(PlaybackPolicy.maximumFrameRate(sizePx: 97, scale: 3), 60)
        XCTAssertEqual(PlaybackPolicy.maximumFrameRate(sizePx: 64, scale: 2), 45)
    }
    @MainActor func testViewUsesRasterBucketsForTheFrameRateThreshold() {
        let emoji = AnimatedLottieView(frame: CGRect(x: 0, y: 0, width: 32, height: 32))
        XCTAssertEqual(emoji.maximumFPS, 45)
        emoji.bounds.size = CGSize(width: 33, height: 33)
        XCTAssertEqual(emoji.maximumFPS, 60)
        emoji.pickerRaster = true
        XCTAssertEqual(emoji.maximumFPS, 60)
    }
    func testPlaybackBudgets() {
        XCTAssertEqual(PlaybackPolicy.maximumComposerAnimatedEmoji, 20)
        XCTAssertEqual(PlaybackPolicy.maximumTextAnimations, 50)
        XCTAssertEqual(PlaybackPolicy.maximumPickerAnimations, 24)
        XCTAssertEqual(PlaybackPolicy.maximumPoolBytes, 8 * 1024 * 1024)
        XCTAssertEqual(PlaybackPolicy.maximumCachedEntries, 24)
    }
    func testSelectionPrefersReadyVisibleCellsAndStartsInScreenOrder() {
        let candidates: [PlaybackPolicy.Candidate<Int, Int>] = [
            .init(id: 1, value: 1, ready: false, visibleArea: 100, visibleCenterY: 50,
                  visiblePermille: 1000, top: 0, left: 0, stableOrder: 0),
            .init(id: 2, value: 2, ready: true, visibleArea: 10, visibleCenterY: 20,
                  visiblePermille: 100, top: 0, left: 0, stableOrder: 1),
            .init(id: 3, value: 3, ready: true, visibleArea: 100, visibleCenterY: 50,
                  visiblePermille: 1000, top: 30, left: 0, stableOrder: 2),
            .init(id: 4, value: 4, ready: true, visibleArea: 100, visibleCenterY: 70,
                  visiblePermille: 1000, top: 10, left: 0, stableOrder: 3),
        ]
        let first = PlaybackPolicy.decide(candidates: candidates, activeIDs: [], playbackAllowed: true,
                                          maxConcurrent: 2, viewportCenterY: 50)
        XCTAssertEqual(first.desiredIDs, [3, 4])
        XCTAssertEqual(first.toStart.map(\.id), [4, 3])
        let retained = PlaybackPolicy.decide(candidates: candidates, activeIDs: [3], playbackAllowed: true,
                                             maxConcurrent: 2, viewportCenterY: 50)
        XCTAssertEqual(retained.toStart.map(\.id), [4])
        let stopped = PlaybackPolicy.decide(candidates: candidates, activeIDs: [3], playbackAllowed: false,
                                            maxConcurrent: 2, viewportCenterY: 50)
        XCTAssertTrue(stopped.desiredIDs.isEmpty)
        XCTAssertTrue(stopped.toStart.isEmpty)
    }
}
