import XCTest
@testable import Axoo

final class RLottieAnimationTests: XCTestCase {
    func testCatalogAndBundledNativeAsset() throws {
        let catalog = try AxoojiCatalog.load()
        XCTAssertEqual(catalog.emojis.count, 635)
        XCTAssertEqual(catalog.categories.count, 17)
        XCTAssertEqual(catalog.categories.first?.id, "halloween")
        XCTAssertEqual(catalog.categories.first?.items.count, 14)
        let asset = try XCTUnwrap(catalog.byID[1218])
        let animation = try RLottieAnimation(data: Data(contentsOf: XCTUnwrap(catalog.animationURL(for: asset))))
        XCTAssertGreaterThan(animation.frameCount, 1)
        XCTAssertEqual(animation.framesPerSecond, 60)
        let first = try AnimationFrameRenderer.render(animation, frame: 0, edge: 96)
        let bytes = try XCTUnwrap(first.image.dataProvider?.data) as Data
        let copy = Data(bytes)
        for frame in 1..<min(12, animation.frameCount) { _ = try AnimationFrameRenderer.render(animation, frame: frame, edge: 96) }
        XCTAssertEqual(try XCTUnwrap(first.image.dataProvider?.data) as Data, copy,
                       "A published image must remain immutable while newer buffers are rendered/recycled")
        XCTAssertThrowsError(try AnimationFrameRenderer.render(animation, frame: animation.frameCount, edge: 96))
        XCTAssertThrowsError(try AnimationFrameRenderer.render(animation, frame: 0, edge: 0))
        XCTAssertThrowsError(try AnimationFrameRenderer.render(animation, frame: 0, edge: 1025))
    }
    func testMalformedTGSFailsWithoutCreatingAnAnimation() {
        XCTAssertThrowsError(try RLottieAnimation(data: Data([0x1f, 0x8b, 0x08])))
        XCTAssertThrowsError(try RLottieAnimation(data: Data("bad JSON".utf8)))
    }
}
