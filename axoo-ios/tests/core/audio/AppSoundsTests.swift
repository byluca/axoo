import XCTest
import AVFoundation
@testable import Axoo

final class AppSoundsTests: XCTestCase {
    func testCommentSoundIsBundledAndDecodes() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "comment_sent", withExtension: "wav", subdirectory: "sounds"))
        let player = try AVAudioPlayer(contentsOf: url)
        XCTAssertGreaterThan(player.duration, 0)
        XCTAssertLessThan(player.duration, 1)
        XCTAssertEqual(player.numberOfChannels, 1)
    }
}
