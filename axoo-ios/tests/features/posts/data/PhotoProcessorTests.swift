import XCTest
import UIKit
import ImageIO
import UniformTypeIdentifiers
@testable import Axoo

final class PhotoProcessorTests: XCTestCase {
    func testLargeImageBecomesBoundedJPEG() async throws {
        let data = UIGraphicsImageRenderer(size: CGSize(width: 2000, height: 1000)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 2000, height: 1000))
        }.pngData()!
        let media = try await PhotoProcessor.prepare(data)
        XCTAssertEqual(media.width, 1600)
        XCTAssertEqual(media.height, 800)
        XCTAssertEqual(media.mimeType, "image/jpeg")
        let jpeg = try XCTUnwrap(media.photoData)
        XCTAssertLessThanOrEqual(jpeg.count, 3 * 1024 * 1024)
        XCTAssertEqual(Array(jpeg.prefix(2)), [0xFF, 0xD8])
    }

    func testOrientationIsAppliedBeforeMeasuringOutput() async throws {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 50)).image { _ in }.cgImage!
        let data = NSMutableData()
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, [kCGImagePropertyOrientation: 6] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        let media = try await PhotoProcessor.prepare(data as Data)
        XCTAssertEqual(media.height, media.width * 2)
    }

    func testInvalidDataIsRejected() async {
        do {
            _ = try await PhotoProcessor.prepare(Data([0, 1, 2]))
            XCTFail("Invalid image data must fail")
        } catch { }
    }
}
