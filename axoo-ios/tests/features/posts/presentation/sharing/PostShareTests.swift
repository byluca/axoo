import XCTest
import UIKit
@testable import Axoo

@MainActor
final class PostShareTests: XCTestCase {
    func testLongPostKeepsItsLastPixelsAndDoesNotChangeTheCardLayout() throws {
        let post = Post(id: "shared", nickname: "Studente", campus: "UniBa", timeLabel: "ora",
            body: (1...35).map { "Riga \($0): testo completo del post" }.joined(separator: "\n"))
        let card = PostCardView(post: post, loadsMedia: false) { _ in }
        let size = card.sizeThatFits(CGSize(width: 366, height: CGFloat.greatestFiniteMagnitude))
        card.frame = CGRect(origin: .zero, size: size)
        card.layoutIfNeeded()
        let marker = UIView(frame: CGRect(x: 0, y: size.height - 40, width: size.width, height: 40))
        marker.backgroundColor = .green
        card.addSubview(marker)
        let frame = card.frame
        let image = try XCTUnwrap(PostShare.image(of: card))
        let cg = try XCTUnwrap(image.cgImage)
        XCTAssertEqual(card.frame, frame)
        XCTAssertGreaterThan(cg.height, cg.width * 5 / 4)
        XCTAssertLessThanOrEqual(cg.width, 1081)
        XCTAssertLessThanOrEqual(cg.height, 4097)
        let pixels = rgba(cg)
        XCTAssertEqual(Array(pixels[0..<4]), [255, 0, 191, 255])
        let center = cg.width / 2
        XCTAssertTrue((0..<cg.height).contains { y in
            let index = (y * cg.width + center) * 4
            return pixels[index] == 0 && pixels[index + 1] == 255 && pixels[index + 2] == 0
        }, "The end of the card must be present even when it extends beyond the screen")
        XCTAssertTrue((cg.height * 9 / 10..<cg.height).contains { y in
            (cg.width / 3..<cg.width * 2 / 3).contains { x in
                let index = (y * cg.width + x) * 4
                return pixels[index] == 255 && pixels[index + 1] == 255 && pixels[index + 2] == 255
            }
        }, "The footer must include the white wordmark")
    }

    func testShortPostsHaveAPortraitCanvasAndEmptyCardsCannotBeExported() throws {
        let post = Post(id: "short", nickname: "", campus: "", timeLabel: "", body: "Ciao!")
        let card = PostCardView(post: post, loadsMedia: false) { _ in }
        XCTAssertNil(PostShare.image(of: card))
        card.frame = CGRect(origin: .zero, size: card.sizeThatFits(CGSize(width: 366, height: CGFloat.greatestFiniteMagnitude)))
        let image = try XCTUnwrap(PostShare.image(of: card))
        let cg = try XCTUnwrap(image.cgImage)
        XCTAssertEqual(Double(cg.width), 1080, accuracy: 1)
        XCTAssertEqual(Double(cg.height), 1350, accuracy: 1)
    }

    func testUnloadedMediaIsNotReadyForSharing() {
        var post = Post(id: "media", nickname: "", campus: "", timeLabel: "", body: "Una foto")
        post.mediaURL = "https://example.invalid/photo.png"
        let card = PostCardView(post: post, loadsMedia: false) { _ in }
        XCTAssertFalse(card.isShareMediaReady)
        post.mediaURL = nil
        card.configure(post)
        XCTAssertTrue(card.isShareMediaReady)
    }

    func testSharingADarkCardExportsALightCardAndRestoresItsStyle() throws {
        let post = Post(id: "dark", nickname: "Studente", campus: "UniBa", timeLabel: "ora", body: "Ciao!")
        let card = PostCardView(post: post, loadsMedia: false) { _ in }
        card.overrideUserInterfaceStyle = .dark
        card.frame = CGRect(origin: .zero, size: card.sizeThatFits(CGSize(width: 366, height: CGFloat.greatestFiniteMagnitude)))
        card.layoutIfNeeded()
        let image = try XCTUnwrap(PostShare.image(of: card))
        XCTAssertEqual(card.overrideUserInterfaceStyle, .dark)
        XCTAssertEqual(card.traitCollection.userInterfaceStyle, .dark)
        let cg = try XCTUnwrap(image.cgImage)
        let pixels = rgba(cg)
        var white = 0
        var darkBackground = 0
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            if pixels[offset] == 255 && pixels[offset + 1] == 255 && pixels[offset + 2] == 255 { white += 1 }
            if pixels[offset] == 35 && pixels[offset + 1] == 46 && pixels[offset + 2] == 59 { darkBackground += 1 }
        }
        XCTAssertGreaterThan(white, cg.width * cg.height / 10)
        XCTAssertEqual(darkBackground, 0)
    }

    private func rgba(_ image: CGImage) -> [UInt8] {
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        pixels.withUnsafeMutableBytes { buffer in
            let context = CGContext(data: buffer.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)!
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return pixels
    }
}
