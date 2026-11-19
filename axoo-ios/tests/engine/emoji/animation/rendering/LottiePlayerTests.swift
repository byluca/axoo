import XCTest
@testable import Axoo

@MainActor
final class LottiePlayerTests: XCTestCase {
    func testRequestsReceivedDuringRenderingCollapseToTheLatestFrame() async throws {
        let (animation, _) = try asset()
        let finished = expectation(description: "Latest requested frame published")
        var indices: [Int] = []
        let player = LottiePlayer(animation: animation, sizePx: 78, timeline: .synchronized(start: 100),
            clock: LottieFrameClock(automaticallyTicks: false),
            onFrameReady: { frame in
                indices.append(frame.index)
                if frame.index == 30 { finished.fulfill() }
            }, onFailure: { error in XCTFail("Render failed: \(error)") })
        defer { player.dispose() }
        // All requests occur before yielding to the worker: frame 15 must be skipped.
        player.play(at: 100)
        player.onVsync(100)
        player.onVsync(100.25)
        player.onVsync(100.5)
        await fulfillment(of: [finished], timeout: 5)
        XCTAssertEqual(indices, [0, 30])
        let image = try XCTUnwrap(player.currentFrame?.image)
        player.dispose()
        XCTAssertNil(player.currentFrame)
        XCTAssertNotNil(image.dataProvider?.data, "Published pixels outlive the player")
    }

    func testDisposalSuppressesAnAlreadyQueuedRender() async throws {
        let (animation, _) = try asset()
        let finished = expectation(description: "Disposed player never publishes")
        finished.isInverted = true
        let player = LottiePlayer(animation: animation, sizePx: 78, timeline: .synchronized(start: 100),
            clock: LottieFrameClock(automaticallyTicks: false),
            onFrameReady: { _ in finished.fulfill() }, onFailure: { _ in finished.fulfill() })
        player.play(at: 100)
        player.onVsync(100)
        player.dispose()
        await fulfillment(of: [finished], timeout: 0.3)
        XCTAssertNil(player.currentFrame)
    }

    func testResizingWhileAStillFrameIsQueuedOnlyPublishesTheNewSize() async throws {
        let (animation, _) = try asset()
        let finished = expectation(description: "New raster published without a display tick")
        let player = LottiePlayer(animation: animation, sizePx: 78,
            clock: LottieFrameClock(automaticallyTicks: false),
            onFrameReady: { frame in
                XCTAssertEqual(frame.image.width, 144)
                finished.fulfill()
            }, onFailure: { XCTFail("Render failed: \($0)") })
        defer { player.dispose() }
        player.renderStillFrame()
        player.resize(to: 144)
        player.renderStillFrame()
        await fulfillment(of: [finished], timeout: 5)
        XCTAssertEqual(player.currentFrame?.image.width, 144)
    }

    func testPauseAndImmediateResumeRecoverAfterTheCancelledRender() async throws {
        let (animation, _) = try asset()
        let finished = expectation(description: "Latest frame after resume")
        let player = LottiePlayer(animation: animation, sizePx: 78, timeline: .synchronized(start: 100),
            clock: LottieFrameClock(automaticallyTicks: false),
            onFrameReady: { frame in
                XCTAssertEqual(frame.index, 30)
                finished.fulfill()
            }, onFailure: { XCTFail("Render failed: \($0)") })
        defer { player.dispose() }
        player.play(at: 100)
        player.pause(at: 100.25)
        player.play(at: 100.5)
        await fulfillment(of: [finished], timeout: 5)
    }

    private func asset() throws -> (RLottieAnimation, URL) {
        let catalog = try AxoojiCatalog.load()
        let emoji = try XCTUnwrap(catalog.byID[1218])
        let path = try XCTUnwrap(catalog.animationURL(for: emoji))
        let animation = try RLottieAnimation(data: Data(contentsOf: path))
        XCTAssertGreaterThan(animation.frameCount, 30)
        XCTAssertEqual(animation.framesPerSecond, 60)
        return (animation, path)
    }
}
