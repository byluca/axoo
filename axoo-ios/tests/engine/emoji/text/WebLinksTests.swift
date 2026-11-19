import XCTest
import UIKit
@testable import Axoo

@MainActor
final class WebLinksTests: XCTestCase {
    func testLinksUseDisplayedOffsetsAfterEmojiAndPreserveSavedDocument() throws {
        let document = EmojiDocument(text: "😀 testo https://example.com/path?x=1&y=2, fine",
            entities: [EmojiEntity(offset: 0, length: 1, id: 999999)])
        let view = EmojiTextView(readOnly: true)
        view.frame = CGRect(x: 0, y: 0, width: 180, height: 300)
        view.font = .systemFont(ofSize: 17)
        view.setDocument(document)
        view.layoutManager.ensureLayout(for: view.textContainer)
        XCTAssertEqual(view.document, document)
        let display = view.attributedText.string as NSString
        let range = display.range(of: "https://example.com/path?x=1&y=2")
        XCTAssertEqual((view.textStorage.attribute(.link, at: range.location, effectiveRange: nil) as? URL)?.absoluteString,
                       "https://example.com/path?x=1&y=2")
        XCTAssertNil(view.textStorage.attribute(.link, at: range.location + range.length, effectiveRange: nil))
        let glyph = view.layoutManager.glyphIndexForCharacter(at: range.location + 3)
        let rect = view.layoutManager.boundingRect(forGlyphRange: NSRange(location: glyph, length: 1), in: view.textContainer)
        XCTAssertTrue(view.point(inside: CGPoint(x: rect.midX, y: rect.midY), with: nil))
        let ordinary = display.range(of: "testo").location
        let ordinaryGlyph = view.layoutManager.glyphIndexForCharacter(at: ordinary)
        let ordinaryRect = view.layoutManager.boundingRect(forGlyphRange: NSRange(location: ordinaryGlyph, length: 1), in: view.textContainer)
        XCTAssertFalse(view.point(inside: CGPoint(x: ordinaryRect.midX, y: ordinaryRect.midY), with: nil))
        XCTAssertFalse(view.point(inside: CGPoint(x: 170, y: 290), with: nil))
    }

    func testOnlyWebAddressesBecomeClickableAndRebindingClearsOldLinks() {
        let view = EmojiTextView(readOnly: true)
        view.font = .systemFont(ofSize: 17)
        view.setDocument(EmojiDocument(text: "https://example.com"))
        XCTAssertNotNil(view.textStorage.attribute(.link, at: 0, effectiveRange: nil))
        view.setDocument(EmojiDocument(text: "user@example.com testo normale"))
        view.textStorage.enumerateAttribute(.link, in: NSRange(location: 0, length: view.textStorage.length)) { value, _, _ in
            XCTAssertNil(value)
        }
    }
}
