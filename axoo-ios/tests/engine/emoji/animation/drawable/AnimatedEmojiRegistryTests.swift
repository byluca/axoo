import XCTest
@testable import Axoo

@MainActor
final class AnimatedEmojiRegistryTests: XCTestCase {
    private final class Host: LottieHost {
        var isAttachedForLottie = false
        func invalidateLottie() {}
    }
    func testLeasesShareContentAndClosingOneKeepsTheOtherUsable() {
        let registry = AnimatedEmojiRegistry.shared
        let key = AnimatedEmojiKey(id: -100, path: URL(fileURLWithPath: "/fixture/shared.tgs"), sizePx: 78)
        let firstHost = Host(), secondHost = Host()
        let first = registry.acquire(key: key, host: firstHost)
        let second = registry.acquire(key: key, host: secondHost)
        XCTAssertTrue(first.drawable === second.drawable)
        first.close()
        first.close()
        XCTAssertTrue(first.isClosed)
        XCTAssertFalse(second.isClosed)
        second.play()
        second.pause()
        second.close()
        let reopened = registry.acquire(key: key, host: firstHost)
        XCTAssertTrue(reopened.drawable === second.drawable)
        reopened.close()
        registry.purgeUnused()
    }
    func testDestroyedHostReleasesItsAbandonedLease() {
        let registry = AnimatedEmojiRegistry.shared
        var host: Host? = Host()
        let key = AnimatedEmojiKey(id: -102, path: URL(fileURLWithPath: "/fixture/abandoned.tgs"), sizePx: 78)
        let lease = registry.acquire(key: key, host: host!)
        host = nil
        registry.collectAbandonedLeases()
        XCTAssertTrue(lease.isClosed)
        registry.purgeUnused()
    }

    func testActiveLeaseSurvivesEvictionOfMoreThan24UnusedDrawables() {
        let registry = AnimatedEmojiRegistry.shared
        registry.purgeUnused()
        let host = Host()
        let pinnedKey = AnimatedEmojiKey(id: -101, path: URL(fileURLWithPath: "/fixture/pinned.tgs"), sizePx: 78)
        let pinned = registry.acquire(key: pinnedKey, host: host)
        defer { pinned.close(); registry.purgeUnused() }
        for index in 0..<30 {
            let temporaryHost = Host()
            let key = AnimatedEmojiKey(id: index, path: URL(fileURLWithPath: "/fixture/\(index).tgs"), sizePx: 78)
            registry.acquire(key: key, host: temporaryHost).close()
        }
        let anotherHost = Host()
        let another = registry.acquire(key: pinnedKey, host: anotherHost)
        XCTAssertTrue(another.drawable === pinned.drawable)
        XCTAssertLessThanOrEqual(registry.entryCount, 25)
        another.close()
    }
}
