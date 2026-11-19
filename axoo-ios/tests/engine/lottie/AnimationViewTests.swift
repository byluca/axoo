import XCTest
import UIKit
@testable import Axoo

@MainActor
final class AnimationViewTests: XCTestCase {
    func testPausedDecorationRendersResizesAndRejectsFramesFromAnOldAsset() async throws {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()
        let view = AnimationView(name: "locked")
        view.playbackEnabled = false
        view.frame = CGRect(x: 20, y: 100, width: 64, height: 64)
        window.rootViewController!.view.addSubview(view)
        defer { view.removeFromSuperview(); window.isHidden = true }
        window.layoutIfNeeded()
        try await waitUntil { view.image != nil }
        let first = try XCTUnwrap(view.image)
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertTrue(view.image === first)

        view.frame.size = CGSize(width: 96, height: 96)
        view.layoutIfNeeded()
        let edge = Int(ceil(96 * window.screen.scale))
        try await waitUntil { view.image?.cgImage?.width == edge }
        view.playbackEnabled = true
        view.configure(name: "fixture-does-not-exist")
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(view.image, "A frame from the old asset must not be published after rebinding")
    }

    private func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<500 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Decorative frame was not ready within five seconds")
    }
}
