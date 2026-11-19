import XCTest
import UIKit
@testable import Axoo

@MainActor
final class StickerCropTests: XCTestCase {
    func testCropExports512PixelsAndKeepsTransparencyAndPosition() throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let source = UIGraphicsImageRenderer(size: CGSize(width: 900, height: 300), format: format).image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 300, height: 300))
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 600, y: 0, width: 300, height: 300))
        }
        let crop = StickerCropView(frame: CGRect(x: 0, y: 0, width: 300, height: 500))
        crop.image = source
        crop.layoutIfNeeded()
        let scroll = try XCTUnwrap(crop.subviews.first as? UIScrollView)
        let center = try decoded(crop)
        XCTAssertEqual(center.width, 512)
        XCTAssertEqual(center.height, 512)
        XCTAssertEqual(pixel(center)[3], 0)
        scroll.contentOffset = .zero
        let left = pixel(try decoded(crop))
        XCTAssertGreaterThan(left[0], 240)
        XCTAssertEqual(left[3], 255)
        scroll.contentOffset = CGPoint(x: scroll.contentSize.width-scroll.bounds.width, y: 0)
        let right = pixel(try decoded(crop))
        XCTAssertGreaterThan(right[2], 240)
        XCTAssertEqual(right[3], 255)
    }
    private func decoded(_ crop: StickerCropView) throws -> CGImage {
        try XCTUnwrap(UIImage(data: XCTUnwrap(crop.croppedPNG()))?.cgImage)
    }
    private func pixel(_ image: CGImage) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: 512*512*4)
        bytes.withUnsafeMutableBytes { data in
            let context = CGContext(data: data.baseAddress, width: 512, height: 512, bitsPerComponent: 8, bytesPerRow: 512*4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            context.draw(image, in: CGRect(x: 0, y: 0, width: 512, height: 512))
        }
        let index = (256*512+256)*4
        return Array(bytes[index..<index+4])
    }
}
