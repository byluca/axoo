import XCTest
import UIKit
@testable import Axoo

final class EmojiDocumentTests: XCTestCase {
    @MainActor func testAndroidCodePointOffsetsRoundTripThroughTextKit() {
        let prefix = "👩🏽‍💻 🇮🇹 caffè "
        let fallback = "👨‍👩‍👧‍👦"
        let text = prefix + fallback + " fine"
        let entity = EmojiEntity(offset: prefix.unicodeScalars.count, length: fallback.unicodeScalars.count, id: 999999)
        let document = EmojiDocument(text: text, entities: [entity])
        XCTAssertEqual((text as NSString).substring(with: document.range(of: entity)), fallback)
        let attributed = EmojiTextMapper.attributed(document, font: .systemFont(ofSize: 16), color: .black, edge: 24)
        XCTAssertEqual(attributed.string, prefix + "\u{FFFC} fine")
        XCTAssertEqual(EmojiTextMapper.document(from: attributed), document)
    }
    @MainActor func testUnavailableAnimationReservesBlankSpaceAndKeepsItsText() throws {
        let emoji = "🙂"
        let document = EmojiDocument(text: emoji, entities: [EmojiEntity(offset: 0, length: 1, id: 999999)])
        let attributed = EmojiTextMapper.attributed(document, font: .systemFont(ofSize: 16), color: .black, edge: 24)
        let span = try XCTUnwrap(attributed.attribute(.attachment, at: 0, effectiveRange: nil) as? AnimatedEmojiSpan)
        let image = try XCTUnwrap(span.image?.cgImage)
        let pixel = UnsafeMutablePointer<UInt8>.allocate(capacity: 4)
        defer { pixel.deallocate() }
        pixel.initialize(repeating: 0, count: 4)
        let context = try XCTUnwrap(CGContext(data: pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(image, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        XCTAssertEqual(pixel[3], 0)
        XCTAssertEqual(span.bounds.width, 24)
        XCTAssertEqual(EmojiTextMapper.document(from: attributed), document)
    }

    func testTrimmingMovesEntityOffsetsByCodePoints() {
        let document = EmojiDocument(text: " \n👩🏽‍💻 😀  ", entities: [EmojiEntity(offset: 7, length: 1, id: 12)])
        XCTAssertEqual(document.normalized.text, "👩🏽‍💻 😀")
        XCTAssertEqual(document.normalized.entities, [EmojiEntity(offset: 5, length: 1, id: 12)])
    }
    func testMalformedAndOverlappingEntitiesCannotBeEdited() {
        let entities = [EmojiEntity(offset: 0, length: 2, id: 1), EmojiEntity(offset: 1, length: 2, id: 2)]
        XCTAssertFalse(EmojiDocument.canEdit(text: "abc", raw: entities.map(\.payload)))
        XCTAssertFalse(EmojiDocument.canEdit(text: "a", raw: [EmojiEntity(offset: Int.max, length: 1, id: 1).payload]))
        XCTAssertTrue(EmojiDocument.canEdit(text: "😀", raw: [EmojiEntity(offset: 0, length: 1, id: 999999).payload]))
    }
    func testEmojiOnlyClassificationKeepsMixedTextAndLongRunsCompact() {
        XCTAssertEqual(EmojiDocument(text: "👩🏽‍💻 🇮🇹 ❤️").emojiOnlyCount, 3)
        XCTAssertEqual(EmojiDocument(text: "1️⃣").emojiOnlyCount, 1)
        XCTAssertNil(EmojiDocument(text: "1").emojiOnlyCount)
        XCTAssertNil(EmojiDocument(text: "ciao 😀").emojiOnlyCount)
        XCTAssertNil(EmojiDocument(text: "😀😀😀😀😀😀").emojiOnlyCount)
    }
}

@MainActor
final class EmojiTextViewTests: XCTestCase {
    func testAnimationBudgetFollowsDistinctVisibleRastersWhenScrolling() throws {
        let emojis = Array(AxoojiCatalog.shared.emojis.prefix(60))
        XCTAssertEqual(emojis.count, 60)
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let scroll = UIScrollView(frame: CGRect(x: 0, y: 80, width: 200, height: 440))
        scroll.contentSize = CGSize(width: 200, height: 1000)
        window.rootViewController!.view.addSubview(scroll)
        let group = UUID()
        let hosts = (0..<60).map { index -> AnimatedLottieView in
            let host = AnimatedLottieView(frame: CGRect(x: CGFloat(index % 5 * 40), y: CGFloat(index / 5 * 40), width: 32, height: 32))
            host.textPlaybackGroup = group
            host.configure(emojis[index])
            scroll.addSubview(host)
            return host
        }
        window.layoutIfNeeded()
        let store = EmojiPlaybackCoordinator.shared
        store.refreshVisibility()
        XCTAssertEqual(hosts.filter { store.activeHosts.contains($0.leaseID) }.count, 50)
        XCTAssertTrue(store.activeHosts.contains(hosts[0].leaseID))
        XCTAssertFalse(store.activeHosts.contains(hosts[50].leaseID))

        scroll.contentOffset = CGPoint(x: 0, y: 80)
        store.refreshVisibility()
        XCTAssertFalse(store.activeHosts.contains(hosts[0].leaseID))
        XCTAssertFalse(store.activeHosts.contains(hosts[1].leaseID))
        XCTAssertTrue(store.activeHosts.contains(hosts[50].leaseID))
        XCTAssertTrue(store.activeHosts.contains(hosts[59].leaseID))
        XCTAssertEqual(hosts.filter { store.activeHosts.contains($0.leaseID) }.count, 50)

        // A second text field gets its own allowance even for the same asset.
        let other = AnimatedLottieView(frame: CGRect(x: 50, y: 80, width: 32, height: 32))
        other.textPlaybackGroup = UUID()
        other.configure(emojis[0])
        scroll.addSubview(other)
        store.refreshVisibility()
        XCTAssertTrue(store.activeHosts.contains(other.leaseID))
    }

    func testPickerInsertionAtSelectionPreservesIDsWithSameFallback() throws {
        let catalog = try AxoojiCatalog.load()
        let duplicates = try XCTUnwrap(Dictionary(grouping: catalog.emojis, by: \.fallback).values.first { $0.count > 1 })
        let view = EmojiEditTextView()
        view.font = .systemFont(ofSize: 16)
        view.setDocument(EmojiDocument(text: "a👩🏽‍💻z"))
        view.selectedRange = NSRange(location: 1, length: "👩🏽‍💻".utf16.count)
        view.insertEmoji(duplicates[0])
        view.insertEmoji(duplicates[1])
        XCTAssertEqual(view.document.text, "a" + duplicates[0].fallback + duplicates[1].fallback + "z")
        XCTAssertEqual(view.document.entities.map(\.id), [duplicates[0].id, duplicates[1].id])
        XCTAssertEqual(view.selectedRange, NSRange(location: 3, length: 0))
        view.deleteBackward()
        XCTAssertEqual(view.document.entities.map(\.id), [duplicates[0].id])
        XCTAssertEqual(view.document.text, "a" + duplicates[0].fallback + "z")
    }
    func testTypingAroundAttachmentsDoesNotLosePlainTextOrIDs() throws {
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.emojis.first)
        let view = EmojiEditTextView()
        view.insertEmoji(emoji)
        view.insertText(" ciao")
        view.selectedRange = NSRange(location: 0, length: 0)
        view.insertText("👩🏽‍💻 ")
        XCTAssertEqual(view.document.text, "👩🏽‍💻 " + emoji.fallback + " ciao")
        XCTAssertEqual(view.document.entities.first?.offset, 5)
        XCTAssertEqual(view.document.entities.map(\.id), [emoji.id])
    }
    func testStaticAndUnknownIDsSurviveEditingAndCopyPaste() {
        let document = EmojiDocument(text: "😀❤️", entities: [
            EmojiEntity(type: "static_emoji", offset: 0, length: 1, id: 999999),
            EmojiEntity(offset: 1, length: 2, id: 999998)
        ])
        let view = EmojiEditTextView()
        view.setDocument(document)
        XCTAssertEqual(view.document, document)
        view.selectedRange = NSRange(location: 0, length: view.textStorage.length)
        view.copy(nil)
        let pasted = EmojiEditTextView()
        pasted.paste(nil)
        XCTAssertEqual(pasted.document, document)
        XCTAssertEqual(UIPasteboard.general.string, document.text)
    }
    func testEmojiKeyboardIsOptionalAndReturnsToSystemKeyboard() {
        let view = EmojiEditTextView()
        let toggle = EmojiKeyboardToggle()
        view.connectPicker(to: toggle)
        XCTAssertNil(view.inputView)
        toggle.onToggle()
        XCTAssertNotNil(view.inputView)
        XCTAssertTrue(toggle.pickerOpen)
        toggle.onToggle()
        XCTAssertNil(view.inputView)
        XCTAssertFalse(toggle.pickerOpen)
    }
    func testPublishedInlineEmojiKeepsTheTextLineHeight() throws {
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.emojis.first)
        let view = EmojiTextView(readOnly: true)
        view.font = .systemFont(ofSize: 16)
        view.frame = CGRect(x: 0, y: 0, width: 300, height: 80)
        view.setDocument(EmojiDocument(text: "Ciao mondo"))
        view.layoutManager.ensureLayout(for: view.textContainer)
        let textHeight = view.layoutManager.usedRect(for: view.textContainer).height
        view.setDocument(EmojiDocument(text: "Ciao " + emoji.fallback, entities: [
            EmojiEntity(offset: 5, length: emoji.fallback.unicodeScalars.count, id: emoji.id)
        ]))
        view.layoutManager.ensureLayout(for: view.textContainer)
        XCTAssertEqual(view.layoutManager.usedRect(for: view.textContainer).height, textHeight, accuracy: 0.5)
        let span = try XCTUnwrap(view.textStorage.attribute(.attachment, at: 5, effectiveRange: nil) as? AnimatedEmojiSpan)
        XCTAssertTrue(span.preservesFontLineHeight)
        XCTAssertGreaterThan(span.renderEdge, span.bounds.height)
    }

    func testRenderedAttachmentOccupiesItsTextLine() throws {
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.emojis.first)
        let view = EmojiTextView(readOnly: true)
        view.font = .systemFont(ofSize: 16)
        view.setDocument(EmojiDocument(text: "Ciao " + emoji.fallback, entities: [EmojiEntity(offset: 5, length: emoji.fallback.unicodeScalars.count, id: emoji.id)]))
        view.frame = CGRect(x: 0, y: 0, width: 300, height: 80)
        view.layoutIfNeeded()
        let host = try XCTUnwrap(view.subviews.compactMap { $0 as? AnimatedLottieView }.first)
        XCTAssertGreaterThan(host.frame.minX, 0)
        let line = view.layoutManager.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil)
        XCTAssertEqual(host.frame.midY, line.midY, accuracy: 0.5)
        XCTAssertFalse(view.clipsToBounds)
    }
}
