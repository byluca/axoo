import XCTest
@testable import Axoo

final class EmojiRasterSizePolicyTests: XCTestCase {
    func testInlineBucketsFollowAndroidAcrossScreenScales() {
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 0, scale: 3), 78)
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 16, scale: 3), 78)
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 32, scale: 3), 96)
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 33, scale: 3), 144)
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 200, scale: 3), 600)
        XCTAssertEqual(EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: 600, scale: 3), 1024)
        XCTAssertEqual(EmojiRasterSizePolicy.pickerSizePx(scale: 3), 144)
        XCTAssertEqual(EmojiRasterSizePolicy.pickerSizePx(scale: 2), 96)
    }
    func testSharedKeyIncludesPathsAndRasterSizeButPlaybackKeyIgnoresSize() {
        let path = URL(fileURLWithPath: "/fixture/emoji.tgs")
        let key = AnimatedEmojiKey(id: 1, path: path, sizePx: 96)
        XCTAssertEqual(key, AnimatedEmojiKey(id: 1, path: path, sizePx: 96))
        XCTAssertNotEqual(key, AnimatedEmojiKey(id: 2, path: path, sizePx: 96))
        XCTAssertNotEqual(key, AnimatedEmojiKey(id: 1, path: path, sizePx: 48))
        let changed = AnimatedEmojiKey(id: 1, path: URL(fileURLWithPath: "/fixture/other.tgs"), sizePx: 96)
        XCTAssertNotEqual(key, changed)
        XCTAssertNotEqual(key.playbackKey, changed.playbackKey)
        XCTAssertEqual(key.playbackKey, AnimatedEmojiKey(id: 1, path: path, sizePx: 48).playbackKey)
    }
}
