import XCTest
import UIKit
@testable import Axoo

@MainActor
final class DesignResourcesTests: XCTestCase {
    func testAndroidFontsAreRegisteredWithTheirRealNames() throws {
        for name in ["Inter-Regular", "Baloo2-Regular", "Baloo2-Bold", "Baloo2-SemiBold", "Baloo2-ExtraBold"] {
            let font = try XCTUnwrap(UIFont(name: name, size: 16), "Unregistered font: \(name)")
            XCTAssertEqual(font.fontName, name)
        }
    }

    func testAllAndroidIconsLoadFromCompiledCatalog() throws {
        for icon in AxooIcon.allCases {
            let image = try XCTUnwrap(UIImage(named: icon.rawValue), "Missing SVG asset: \(icon.rawValue)")
            XCTAssertGreaterThan(image.size.width, 0)
            XCTAssertGreaterThan(image.size.height, 0)
        }
    }

    func testAllAndroidIconsRenderVisiblePixelsAtButtonSize() throws {
        for icon in AxooIcon.allCases {
            let source = try XCTUnwrap(icon.image, "Missing icon: \(icon.rawValue)")
            let sized = try XCTUnwrap(icon.image(pointSize: 24), "Missing button icon: \(icon.rawValue)")
            for image in [source, sized] {
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                format.opaque = false
                let raster = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24), format: format).image { _ in
                    image.withTintColor(.black, renderingMode: .alwaysOriginal)
                        .draw(in: CGRect(x: 0, y: 0, width: 24, height: 24))
                }
                let cgImage = try XCTUnwrap(raster.cgImage)
                var pixels = [UInt8](repeating: 0, count: 24 * 24 * 4)
                try pixels.withUnsafeMutableBytes { buffer in
                    let context = try XCTUnwrap(CGContext(data: buffer.baseAddress, width: 24, height: 24,
                        bitsPerComponent: 8, bytesPerRow: 24 * 4, space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: 24, height: 24))
                }
                let visible = stride(from: 3, to: pixels.count, by: 4).filter { pixels[$0] > 16 }.count
                XCTAssertGreaterThan(visible, 5, "Blank icon: \(icon.rawValue)")
                XCTAssertLessThan(visible, 24 * 24, "Opaque rectangle instead of icon: \(icon.rawValue)")
            }
        }
    }
}
