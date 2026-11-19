import XCTest
import UIKit
@testable import Axoo

@MainActor
final class AnimatedEmojiDrawableTests: XCTestCase {
    private final class Host: LottieHost {
        var isAttachedForLottie = true
        var invalidations = 0
        func invalidateLottie() { invalidations += 1 }
    }

    func testPausedAttachmentDefersNativeLoadUntilPlayback() async throws {
        let drawable = AnimatedEmojiDrawable(key: try key())
        let host = Host()
        defer { drawable.dispose() }
        drawable.attachTo(host)
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertFalse(drawable.isAnimationLoaded)
        XCTAssertFalse(drawable.isLoadingAnimation)
        XCTAssertNil(drawable.currentImage)
        drawable.requestPlayback(host, mode: .playing)
        try await waitUntil { drawable.currentImage != nil }
        XCTAssertTrue(drawable.isAnimationLoaded)
    }

    func testPausedAndStaticSubscribersDoNotReceiveAnotherHostsAnimationFrames() async throws {
        let drawable = AnimatedEmojiDrawable(key: try key())
        let playing = Host(), paused = Host(), still = Host()
        defer { drawable.dispose() }
        drawable.attachTo(playing)
        drawable.attachTo(paused)
        drawable.attachTo(still)
        drawable.requestPlayback(still, mode: .still)
        try await waitUntil { drawable.stillImage != nil }
        drawable.requestPlayback(paused, mode: .paused)
        let pausedCount = paused.invalidations, stillCount = still.invalidations
        let preview = drawable.stillImage
        drawable.requestPlayback(playing, mode: .playing)
        try await waitUntil { playing.invalidations >= pausedCount + 3 }
        XCTAssertEqual(paused.invalidations, pausedCount)
        XCTAssertEqual(still.invalidations, stillCount)
        XCTAssertTrue(drawable.stillImage === preview)
    }

    func testExplicitStaticEmojiRendersOneNativeFrameWithoutPlayback() async throws {
        let drawable = AnimatedEmojiDrawable(key: try key())
        let host = Host()
        defer { drawable.dispose() }
        drawable.attachTo(host)
        drawable.requestPlayback(host, mode: .still)
        try await waitUntil { drawable.stillImage != nil }
        XCTAssertTrue(drawable.isAnimationLoaded)
        let image = drawable.stillImage
        let count = host.invalidations
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertTrue(drawable.stillImage === image)
        XCTAssertEqual(host.invalidations, count)
    }

    func testDetachingBeforeTheQueuedLoadCompletesDoesNotStartPlayback() async throws {
        let drawable = AnimatedEmojiDrawable(key: try key())
        let host = Host()
        defer { drawable.dispose() }
        drawable.attachTo(host)
        drawable.requestPlayback(host, mode: .playing)
        drawable.detachFrom(host)
        try await waitUntil { !drawable.isLoadingAnimation }
        XCTAssertFalse(drawable.isAnimationLoaded)
        XCTAssertEqual(host.invalidations, 0)
    }

    private func key() throws -> AnimatedEmojiKey {
        let catalog = try AxoojiCatalog.load()
        let emoji = try XCTUnwrap(catalog.byID[1218])
        return AnimatedEmojiKey(id: emoji.id, path: try XCTUnwrap(catalog.animationURL(for: emoji)),
                                sizePx: 78)
    }
    private func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<500 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Animation condition was not reached within five seconds")
    }
}
