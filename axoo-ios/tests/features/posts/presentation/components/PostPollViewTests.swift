import XCTest
import UIKit
@testable import Axoo

@MainActor
final class PostPollViewTests: XCTestCase {
    func testRowHitTargetsAndCardHeightStayStableAfterVoting() throws {
        for width: CGFloat in [296, 366] {
            var post = Post(id: "poll-post", nickname: "Alice", campus: "", timeLabel: "ora", body: "Domanda",
                                       poll: try PollFixtures.poll(voted: false))
            var votes: [String] = []
            let card = PostCardView(post: post, loadsMedia: false) {
                if case .pollVote(let id) = $0 { votes.append(id) }
            }
            let before = card.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
            card.frame = CGRect(origin: .zero, size: before)
            card.layoutIfNeeded()
            let poll = try XCTUnwrap(descendants(card).first { $0 is PostPollView } as? PostPollView)
            poll.layoutIfNeeded()
            let option = try XCTUnwrap(descendants(poll).first { $0.accessibilityIdentifier == "poll-option-o0" } as? UIControl)
            let point = option.convert(CGPoint(x: option.bounds.midX, y: option.bounds.midY), to: card)
            XCTAssertTrue(card.hitTest(point, with: nil) === option)
            XCTAssertTrue(card.bounds.contains(poll.convert(poll.bounds, to: card)))
            option.sendActions(for: .touchUpInside)
            XCTAssertEqual(votes, ["o0"])
            post.poll = post.poll?.selecting("o0")
            card.configure(post)
            option.sendActions(for: .touchUpInside)
            XCTAssertEqual(votes, ["o0"], "Taps during submission are ignored")
            post.poll = try PollFixtures.poll(voted: true)
            card.configure(post)
            XCTAssertEqual(card.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height, before.height)
            option.sendActions(for: .touchUpInside)
            XCTAssertEqual(votes, ["o0"], "The confirmed selection cannot change")
            XCTAssertTrue(option.accessibilityValue?.contains("100%") == true)
        }
    }

    func testSharedEmojiToggleTargetsFocusedOptionAndPreservesCursor() throws {
        let model = PostEditorViewModel(service: PollLayoutService(), mode: .create)
        model.setBody("Domanda")
        let editor = PostEditorHost(viewModel: model) { _ in }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = editor
        window.makeKeyAndVisible()
        defer { window.isHidden = true; model.stop() }
        window.layoutIfNeeded()
        let body = try XCTUnwrap(descendants(editor.view).first { $0.accessibilityIdentifier == "editor-body" } as? EmojiTextView)
        let originalHeight = body.bounds.height
        model.togglePoll()
        model.setPollOption(0, text: "ab", entities: [])
        model.setPollOption(1, text: "Tè", entities: [])
        editor.view.layoutIfNeeded()
        editor.view.layoutIfNeeded()
        XCTAssertEqual(body.bounds.height, originalHeight - 48, accuracy: 0.5)
        let input = try XCTUnwrap(descendants(editor.view).first { $0.accessibilityIdentifier == "editor-poll-option-0" } as? EmojiTextView)
        XCTAssertTrue(input.becomeFirstResponder())
        input.selectedRange = NSRange(location: 1, length: 0)
        let toggle = try XCTUnwrap(descendants(editor.view).compactMap { $0 as? EmojiKeyboardToggle }.first)
        toggle.sendActions(for: .touchUpInside)
        let keyboard = try XCTUnwrap(input.inputView)
        let picker = try XCTUnwrap(([keyboard] + descendants(keyboard)).compactMap { $0 as? EmojiPanelView }.first)
        let emoji = try XCTUnwrap(AxoojiCatalog.shared.emojis.first)
        picker.onSelect(emoji)
        XCTAssertEqual(model.state.draft.pollOptions?[0].text, "a" + emoji.fallback + "b")
        XCTAssertEqual(model.state.draft.pollOptions?[0].entities.first?.offset, 1)
        XCTAssertEqual(model.state.draft.pollOptions?[0].entities.first?.id, emoji.id)
        XCTAssertEqual(model.state.draft.body, "Domanda")
    }
    private func descendants(_ view: UIView) -> [UIView] { view.subviews.flatMap { [$0] + descendants($0) } }
}

@MainActor
private final class PollLayoutService: PostEditorService {
    func save(_ draft: PostEditorDraft, mode: PostEditorMode, clientID: String) async throws -> PostEditorResult {
        throw HTTPFailure(status: 501)
    }
}
