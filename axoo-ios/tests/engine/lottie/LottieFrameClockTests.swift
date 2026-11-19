import XCTest
@testable import Axoo

@MainActor
final class LottieFrameClockTests: XCTestCase {
    func testUIChangesCoalesceBeforePlaybackAndDoNotRepeatOnIdleTicks() {
        let clock = LottieFrameClock(automaticallyTicks: false)
        let updateID = UUID(), playerID = UUID()
        var events: [String] = []
        for index in 0..<100 { clock.scheduleUpdate(id: updateID) { events.append("layout \(index)") } }
        clock.addListener(id: playerID) { _ in events.append("frame") }
        clock.tick(100)
        clock.tick(101)
        XCTAssertEqual(events, ["layout 99", "frame", "frame"])
        clock.removeListener(id: playerID)
        clock.scheduleUpdate(id: updateID) { events.append("resume") }
        clock.tick(102)
        XCTAssertEqual(events.last, "resume", "UI updates must run even with no active players")
    }
    func testAnUpdateCanPauseAPlayerBeforeThatFramesPlaybackCallbacks() {
        let clock = LottieFrameClock(automaticallyTicks: false)
        let playerID = UUID()
        var ticks = 0
        clock.addListener(id: playerID) { _ in ticks += 1 }
        clock.scheduleUpdate(id: UUID()) { clock.removeListener(id: playerID) }
        clock.tick(100)
        XCTAssertEqual(ticks, 0)
    }
    func testVisibilityRequestsCoalesceAndAnimationTicksDoNotRescanTheUI() {
        let coordinator = EmojiPlaybackCoordinator.shared
        let before = coordinator.reconciliationCount
        for _ in 0..<100 { coordinator.setNeedsVisibilityUpdate() }
        LottieFrameClock.shared.tick(100)
        XCTAssertEqual(coordinator.reconciliationCount, before + 1)
        LottieFrameClock.shared.tick(101)
        XCTAssertEqual(coordinator.reconciliationCount, before + 1)
    }
    func testRenderPoolRestoresTwoWorkersAndBoundsExplicitOverrides() {
        XCTAssertEqual(LottieRenderPool.shared.workerCount, 2)
        XCTAssertEqual(LottieRenderPool(workerCount: 0).workerCount, 1)
        XCTAssertEqual(LottieRenderPool(workerCount: 20).workerCount, 4)
    }
}
