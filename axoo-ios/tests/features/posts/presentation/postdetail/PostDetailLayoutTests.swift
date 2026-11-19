import XCTest
import UIKit
@testable import Axoo

@MainActor
final class PostDetailLayoutTests: XCTestCase {
    func testCommentGIFMaskFollowsTheVisibleImageAndResetsForStickers() throws {
        let view = CommentView()
        for kind in [PostEditorMedia.Kind.gif, .sticker] {
            for dimensions in [(100, 400), (400, 100)] {
                let media = PostEditorMedia(kind: kind, url: "https://media.giphy.com/test.gif", previewURL: "https://media.giphy.com/test.gif",
                    width: dimensions.0, height: dimensions.1, mimeType: "image/gif", provider: "giphy", providerID: "test", photoData: nil, id: "test")
                let comment = Comment(id: "media", postID: "post", userID: "me", nickname: "Tu", body: "", createdAt: Date(), media: media)
                view.configure(CommentRow(item: CommentItem(comment: comment), depth: 0, rootID: "media", isFirst: true, isLast: true))
                view.frame = CGRect(origin: .zero, size: view.sizeThatFits(CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)))
                view.layoutIfNeeded()
                let image = try XCTUnwrap(view.subviews.compactMap { $0 as? PostMediaImageView }.first)
                XCTAssertEqual(image.frame.width / image.frame.height, CGFloat(dimensions.0) / CGFloat(dimensions.1), accuracy: 0.001)
                XCTAssertLessThanOrEqual(image.frame.height, 200)
                XCTAssertTrue(image.clipsToBounds)
                XCTAssertEqual(image.layer.cornerRadius, kind == .gif ? 12 : 0)
            }
        }
    }

    func testFeedDisplaysTheComplete500CharacterPostAndAllAnimatedEmoji() throws {
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.byID[1218])
        var text = ""
        var entities: [EmojiEntity] = []
        for row in 1...20 {
            if row > 1 { text += "\n" }
            text += "Riga \(row) caffè 👩🏽‍💻 "
            entities.append(EmojiEntity(offset: text.unicodeScalars.count,
                                        length: emoji.fallback.unicodeScalars.count, id: emoji.id))
            text += emoji.fallback
        }
        text += String(repeating: "a", count: 500 - text.unicodeScalars.count)
        let document = EmojiDocument(text: text, entities: entities)
        let post = Post(id: "complete-feed", nickname: "student", campus: "Campus",
                                   timeLabel: "ora", body: text, entities: entities)

        for width: CGFloat in [296, 366] {
            let card = PostCardView(post: post, presentation: .feed, loadsMedia: false) { _ in }
            card.frame = CGRect(origin: .zero, size: card.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)))
            card.layoutIfNeeded()
            let body = try XCTUnwrap(descendants(card).compactMap { $0 as? EmojiTextView }.first)
            body.layoutIfNeeded()
            XCTAssertEqual(body.document, document)

            var lineCount = 0
            let glyphs = body.layoutManager.glyphRange(for: body.textContainer)
            body.layoutManager.enumerateLineFragments(forGlyphRange: glyphs) { _, _, _, _, _ in lineCount += 1 }
            XCTAssertGreaterThan(lineCount, 7)
            let hosts = body.subviews.compactMap { $0 as? AnimatedLottieView }
            XCTAssertEqual(hosts.count, entities.count, "Every emoji must be drawn, including those after the seventh line")
            for host in hosts {
                XCTAssertTrue(body.bounds.contains(host.frame), "The full emoji must fit inside the measured post body")
            }
        }
    }

    func testPostHeadersUseSlightlyLargerAvatarAndFonts() throws {
        let post = Post(id: "layout", nickname: "student", campus: "Campus", timeLabel: "ora", body: "Test")
        for presentation in [PostCardView.Presentation.feed, .detail] {
            let card = PostCardView(post: post, presentation: presentation, loadsMedia: false) { _ in }
            card.frame = CGRect(origin: .zero, size: card.sizeThatFits(CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)))
            card.layoutIfNeeded()
            let views = descendants(card)
            let avatar = try XCTUnwrap(views.compactMap { $0 as? AnonAvatarView }.first)
            let author = try XCTUnwrap(views.compactMap { $0 as? UILabel }.first { $0.text == post.authorLabel })
            let metadata = try XCTUnwrap(views.compactMap { $0 as? UILabel }.first { $0.text == post.metadata })
            XCTAssertEqual(avatar.bounds.width, presentation == .feed ? 36 : 34)
            XCTAssertEqual(author.font.pointSize, (presentation == .feed ? AxooTypography.feedPostAuthor : AxooTypography.postAuthor).pointSize)
            XCTAssertEqual(metadata.font.pointSize, AxooTypography.postMetadata.pointSize)
            XCTAssertGreaterThanOrEqual(author.frame.minX, avatar.frame.maxX + 10)
        }
    }
    func testCommentHeaderKeepsAvatarAndTextApartAfterSizeIncrease() throws {
        let comment = Comment(id: "layout", postID: "post", userID: "me", nickname: "student", body: "Test", createdAt: Date())
        let row = CommentRow(item: CommentItem(comment: comment), depth: 0, rootID: "layout", isFirst: true, isLast: true)
        let view = CommentView()
        view.configure(row)
        view.frame = CGRect(origin: .zero, size: view.sizeThatFits(CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)))
        view.layoutIfNeeded()
        let avatar = try XCTUnwrap(view.subviews.compactMap { $0 as? AnonAvatarView }.first)
        let author = try XCTUnwrap(view.subviews.compactMap { $0 as? UILabel }.first { $0.text == comment.authorLabel })
        XCTAssertEqual(avatar.bounds.size, CGSize(width: 28, height: 28))
        XCTAssertEqual(author.font.pointSize, AxooTypography.postAuthor.pointSize)
        XCTAssertEqual(author.frame.minX, avatar.frame.maxX + 8)
    }
    func testCommentActionsFitNarrowAndDeepRows() {
        for width: CGFloat in [296, 366, 744] {
            for depth in [0, 1, 4, 12] {
                let comment = Comment(id: "layout", postID: "post", userID: "me", nickname: "Nickname molto lungo",
                                      body: String(repeating: "Testo Unicode 👨‍👩‍👧‍👦 🇮🇹\n", count: 6), createdAt: Date(), score: 12345)
                let row = CommentRow(item: CommentItem(comment: comment), depth: depth, rootID: "root", isFirst: true, isLast: true)
                let view = CommentView()
                view.configure(row)
                let size = view.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude))
                XCTAssertTrue(size.height.isFinite)
                view.frame = CGRect(origin: .zero, size: size)
                view.layoutIfNeeded()
                let controls = view.subviews.compactMap { $0 as? UIButton }.filter { !$0.isHidden }
                for control in controls {
                    XCTAssertGreaterThanOrEqual(control.bounds.width, 44)
                    XCTAssertGreaterThanOrEqual(control.bounds.height, 44)
                    XCTAssertTrue(view.bounds.contains(control.frame))
                }
                for (index, control) in controls.enumerated() {
                    for other in controls.dropFirst(index + 1) { XCTAssertFalse(control.frame.intersects(other.frame)) }
                }
            }
        }
    }
    func testOptimisticCommentKeepsLayoutAndEnablesActionsOnlyAfterConfirmation() throws {
        let comment = Comment(id: "layout", postID: "post", userID: "me", nickname: "Tu",
                              body: "Questo testo resta recuperabile", createdAt: Date())
        let view = CommentView()
        view.frame = CGRect(x: 0, y: 0, width: 390, height: 200)
        func row(_ delivery: CommentDelivery) -> CommentRow {
            CommentRow(item: CommentItem(comment: comment, delivery: delivery), depth: 0,
                       rootID: "layout", isFirst: true, isLast: true)
        }
        view.configure(row(.sending))
        view.layoutIfNeeded()
        let pendingSize = view.sizeThatFits(CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude))
        let reply = try XCTUnwrap(view.subviews.first { $0.accessibilityIdentifier == "comment-reply-\(comment.id)" } as? UIButton)
        let pendingReplyFrame = reply.frame
        XCTAssertFalse(reply.isHidden)
        XCTAssertFalse(reply.isEnabled)
        XCTAssertFalse(view.subviews.compactMap { $0 as? UILabel }.contains { $0.text?.contains("Invio") == true })
        view.configure(row(.sent))
        XCTAssertTrue(reply.isEnabled)
        XCTAssertEqual(reply.frame, pendingReplyFrame)
        XCTAssertEqual(view.sizeThatFits(CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude)), pendingSize)
    }
    func testComposerGrowsThenScrollsAndUsesOnlySystemInput() {
        let composer = CommentComposerView()
        var state = PostDetailState(post: Post(id: "post", nickname: "", campus: "", timeLabel: "", body: "Test"))
        composer.render(state)
        let size = CGSize(width: 320, height: CGFloat.greatestFiniteMagnitude)
        let emptyHeight = composer.sizeThatFits(size).height
        state.draft.text = String(repeating: "Riga di testo\n", count: 30)
        composer.render(state)
        let full = composer.sizeThatFits(size)
        XCTAssertGreaterThan(full.height, emptyHeight)
        XCTAssertLessThan(full.height, 300)
        composer.frame = CGRect(origin: .zero, size: full)
        composer.layoutIfNeeded()
        XCTAssertTrue(composer.textView.isScrollEnabled)
        XCTAssertNil(composer.textView.inputView)
        XCTAssertNil(composer.textView.inputAccessoryView)
        XCTAssertTrue(composer.bounds.contains(composer.textView.frame))
        let send = composer.subviews.first { $0.accessibilityIdentifier == "comment-send" }
        XCTAssertNotNil(send)
        XCTAssertFalse(send!.frame.intersects(composer.textView.frame))
    }
    func testReplyExcerptKeepsAnimatedEntitiesAndFitsBesideCancel() throws {
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.emojis.first)
        let prefix = " \n👩🏽‍💻\t"
        let comment = Comment(id: "reply", postID: "post", userID: "me", nickname: "student",
                              body: prefix + emoji.fallback + "\n fine ",
                              entities: [EmojiEntity(offset: prefix.unicodeScalars.count,
                                                     length: emoji.fallback.unicodeScalars.count, id: emoji.id)], createdAt: Date())
        let composer = CommentComposerView()
        var state = PostDetailState(post: Post(id: "post", nickname: "", campus: "", timeLabel: "", body: "Test"))
        state.items = [CommentItem(comment: comment)]
        state.draft.replyID = comment.id
        for width: CGFloat in [320, 390, 744] {
            composer.render(state)
            composer.frame = CGRect(origin: .zero, size: composer.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)))
            composer.layoutIfNeeded()
            let excerpt = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "reply-excerpt" } as? EmojiTextView)
            let close = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "cancel-reply" })
            let send = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "comment-send" })
            XCTAssertEqual(close.frame.size, CGSize(width: 48, height: 48))
            XCTAssertEqual(excerpt.document.text, "👩🏽‍💻 " + emoji.fallback + "  fine")
            XCTAssertEqual(excerpt.document.entities.first?.offset, 5)
            XCTAssertEqual(excerpt.document.entities.first?.id, emoji.id)
            XCTAssertEqual(excerpt.textContainer.maximumNumberOfLines, 1)
            XCTAssertEqual(excerpt.textContainer.lineBreakMode, .byTruncatingTail)
            XCTAssertTrue(excerpt.playbackEnabled)
            XCTAssertEqual(excerpt.frame.maxX, close.frame.minX)
            XCTAssertEqual(close.frame.maxX, send.frame.maxX)
            excerpt.layoutIfNeeded()
            let attachment = try XCTUnwrap(excerpt.textStorage.attribute(.attachment, at: "👩🏽‍💻 ".utf16.count, effectiveRange: nil) as? AnimatedEmojiSpan)
            XCTAssertEqual(attachment.renderEdge, AxooTypography.replyBody.lineHeight * 1.4)
        }
        state.draft.replyID = nil
        composer.render(state)
        let excerpt = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "reply-excerpt" } as? EmojiTextView)
        XCTAssertTrue(excerpt.isHidden)
        XCTAssertFalse(excerpt.playbackEnabled)
        XCTAssertTrue(excerpt.document.entities.isEmpty)
    }
    func testDeletedReplyDoesNotAnimateOldEntities() throws {
        let composer = CommentComposerView()
        let comment = Comment(id: "reply", postID: "post", userID: "me", nickname: "student", body: "😀",
                              entities: [EmojiEntity(offset: 0, length: 1, id: 1218)], createdAt: Date(), isDeleted: true)
        var state = PostDetailState(post: Post(id: "post", nickname: "", campus: "", timeLabel: "", body: "Test"))
        state.items = [CommentItem(comment: comment)]
        state.draft.replyID = comment.id
        composer.render(state)
        let excerpt = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "reply-excerpt" } as? EmojiTextView)
        XCTAssertEqual(excerpt.document, EmojiDocument(text: "Commento non disponibile"))
        state.editingID = comment.id
        composer.render(state)
        XCTAssertTrue(excerpt.isHidden)
        XCTAssertTrue(excerpt.document.entities.isEmpty)
    }
    func testComposerHintAlignsWithTextAndKeepsTheSameKeyboardClearance() throws {
        let composer = CommentComposerView()
        var state = PostDetailState(post: Post(id: "post", nickname: "", campus: "", timeLabel: "", body: "Test"))
        for width: CGFloat in [320, 390, 744] {
            composer.render(state)
            composer.frame = CGRect(origin: .zero, size: composer.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)))
            composer.layoutIfNeeded()
            let hint = try XCTUnwrap(composer.subviews.compactMap { $0 as? UILabel }.first { $0.text == "Commenta…" })
            let send = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "comment-send" })
            XCTAssertEqual(hint.frame.minX, composer.textView.frame.minX + composer.textView.textContainerInset.left)
            XCTAssertEqual(hint.frame.minY, composer.textView.frame.minY + composer.textView.textContainerInset.top)
            XCTAssertEqual(hint.font.pointSize, try XCTUnwrap(composer.textView.font).pointSize)
            XCTAssertEqual(composer.bounds.maxY - send.frame.maxY, CommentComposerView.keyboardGap)
        }
        state.draft.replyID = "reply"
        composer.render(state)
        composer.layoutIfNeeded()
        let icon = try XCTUnwrap(composer.subviews.first { $0.accessibilityIdentifier == "reply-icon" } as? UIImageView)
        XCTAssertFalse(icon.isHidden)
        XCTAssertEqual(icon.tintColor, AxooTheme.accent)
        XCTAssertNotNil(icon.image)
    }

    private func descendants(_ view: UIView) -> [UIView] {
        view.subviews.flatMap { [$0] + descendants($0) }
    }
}
