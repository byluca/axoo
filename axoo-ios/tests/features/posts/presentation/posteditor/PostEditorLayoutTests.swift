import XCTest
import UIKit
@testable import Axoo

@MainActor
final class PostEditorLayoutTests: XCTestCase {
    func testCreatePostPlacesEmojiBesideHintAndKeepsMediaToolbarCompact() throws {
        let editor = PostEditorHost(service: LayoutEditorService(), mode: .create) { _ in }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = editor
        window.makeKeyAndVisible()
        defer { window.isHidden = true; editor.viewModel.stop() }
        window.layoutIfNeeded()
        editor.view.layoutIfNeeded()
        let views = descendants(editor.view)
        let toggle = try XCTUnwrap(views.compactMap { $0 as? EmojiKeyboardToggle }.first)
        let hint = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-placeholder" } as? UILabel)
        let input = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-body" } as? UITextView)
        let toolbar = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-media-toolbar" })
        XCTAssertFalse(toolbar.hasAmbiguousLayout, "Horizontal: \(toolbar.constraintsAffectingLayout(for: .horizontal)); vertical: \(toolbar.constraintsAffectingLayout(for: .vertical))")
        XCTAssertTrue(toggle.superview === input.superview)
        XCTAssertEqual(toggle.frame.width, 40)
        XCTAssertEqual(hint.frame.minX, toggle.frame.maxX + 8, accuracy: 0.5)
        XCTAssertEqual(hint.frame.minX, input.frame.minX)
        XCTAssertEqual(toggle.tintColor, AxooTheme.textSecondary)
        XCTAssertEqual(hint.font.fontName, "Inter-Regular")
        XCTAssertEqual(hint.font.pointSize, AxooTypography.editorPlaceholder.pointSize)
        XCTAssertEqual(hint.font, input.font, "The hint must use the same font and size as the text being written")
        XCTAssertEqual(hint.numberOfLines, 1)
        let paragraph = try XCTUnwrap(input.typingAttributes[.paragraphStyle] as? NSParagraphStyle)
        XCTAssertEqual(paragraph.lineHeightMultiple, 1.12, accuracy: 0.001)
        XCTAssertEqual(toolbar.bounds.width, editor.view.bounds.width - 24, accuracy: 0.5)
        XCTAssertEqual(toolbar.bounds.height, 40)
        var previousRight: CGFloat = 0
        for identifier in ["editor-photo", "editor-poll", "editor-gif", "editor-sticker", "editor-meme"] {
            let button = try XCTUnwrap(views.first { $0.accessibilityIdentifier == identifier } as? UIButton)
            XCTAssertTrue(button.isDescendant(of: toolbar))
            XCTAssertEqual(button.bounds.height, 40)
            XCTAssertTrue(toolbar.bounds.contains(button.frame))
            XCTAssertGreaterThanOrEqual(button.frame.minX, previousRight)
            previousRight = button.frame.maxX
            button.layoutIfNeeded()
            XCTAssertEqual(button.titleLabel?.font.fontName, "Baloo2-Medium")
            XCTAssertGreaterThanOrEqual(button.titleLabel!.frame.minX, button.imageView!.frame.maxX)
        }
        editor.viewModel.setBody(String(repeating: "a", count: 400))
        let counter = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-counter" } as? UILabel)
        XCTAssertFalse(counter.isHidden)
        XCTAssertEqual(counter.text, "400/500")
    }
    func testNarrowEditorKeepsActionsReachableAndLongDraftScrollable() throws {
        for height: CGFloat in [568, 640] {
            let initial = PostEditorDraft(body: String(repeating: "Riga di testo\n", count: 30))
            let service = LayoutEditorService()
            let editor = PostEditorHost(service: service, mode: .edit(postID: "post"), initial: initial) { _ in }
            let host = UIViewController()
            host.addChild(editor)
            host.view.addSubview(editor.view)
            editor.view.pinEdges(to: host.view.layoutMarginsGuide)
            editor.didMove(toParent: host)
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: height))
            window.rootViewController = host
            window.makeKeyAndVisible()
            defer { window.isHidden = true; editor.viewModel.stop() }
            window.layoutIfNeeded()
            editor.view.layoutIfNeeded()
            editor.view.layoutIfNeeded()

            let views = descendants(editor.view)
            let back = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-back" })
            let publish = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "editor-publish" } as? UIButton)
            for button in [back, publish] {
                let frame = button.convert(button.bounds, to: editor.view)
                XCTAssertTrue(editor.view.bounds.contains(frame))
                XCTAssertGreaterThanOrEqual(frame.width, 44)
                XCTAssertGreaterThanOrEqual(frame.height, button === publish ? 42 : 44)
            }
            XCTAssertFalse(back.convert(back.bounds, to: editor.view).intersects(publish.convert(publish.bounds, to: editor.view)))
            let scroll = try XCTUnwrap(views.compactMap { $0 as? UIScrollView }.first { !($0 is UITextView) })
            XCTAssertGreaterThan(scroll.contentSize.height, scroll.bounds.height)
            let body = try XCTUnwrap(views.compactMap { $0 as? UITextView }.first)
            XCTAssertEqual(body.text, editor.viewModel.state.draft.body)
            XCTAssertNil(body.inputView)
            XCTAssertFalse(publish.isEnabled, "An unchanged edit cannot be submitted")
        }
    }
    func testCategorySheetUsesAndroidNeutralGrayAndUppercaseOptions() throws {
        XCTAssertEqual(PostEditorDraft.categories, ["Meme", "Spotted", "Esami", "Confessioni", "Party"])
        let editor = PostEditorHost(service: LayoutEditorService(), mode: .create) { _ in }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = editor
        window.makeKeyAndVisible()
        defer { window.isHidden = true; editor.viewModel.stop() }
        window.layoutIfNeeded()
        let category = try XCTUnwrap(descendants(editor.view).first { $0.accessibilityIdentifier == "editor-category" } as? UIButton)
        category.sendActions(for: .touchUpInside)
        let sheet = try XCTUnwrap(editor.presentedViewController as? AxooBottomSheet)
        sheet.view.layoutIfNeeded()
        let options = sheet.stack.arrangedSubviews.compactMap { $0 as? UIButton }
        XCTAssertEqual(options.compactMap { $0.configuration?.title }, ["SENZA CATEGORIA"] + PostEditorDraft.categories.map { $0.uppercased() })
        let noCategory = try XCTUnwrap(options.first)
        XCTAssertTrue(noCategory.isSelected)
        XCTAssertEqual(noCategory.configuration?.baseBackgroundColor, UIColor(rgb: 0xF0F2F5))
        XCTAssertEqual(noCategory.configuration?.background.backgroundColor, UIColor(rgb: 0xF0F2F5))
        XCTAssertEqual(noCategory.configuration?.baseForegroundColor, AxooTheme.textPrimary)
        let expectedFont = try XCTUnwrap(UIFont(name: "Baloo2-Bold", size: 18))
        for option in options {
            XCTAssertEqual(option.titleLabel?.font.fontName, "Baloo2-Bold")
            XCTAssertEqual(option.titleLabel?.font.pointSize, expectedFont.pointSize)
        }
        XCTAssertEqual(options.first { $0.configuration?.title == "MEME" }?.configuration?.baseBackgroundColor,
                       UIColor(rgb: PostCategoryLabel.appearance(for: "Meme").background))
        XCTAssertEqual(editor.viewModel.state.draft.category, "", "Uppercase labels must not alter stored categories")
    }
    func testGifAndMemePickersDoNotCreateCloseButtonsOrNavigationBars() {
        for kind in [PostEditorMedia.Kind.gif, .meme] {
            let picker = RemoteMediaPickerScreen(api: nil, kind: kind)
            picker.loadViewIfNeeded()
            XCTAssertNil(picker.navigationItem.rightBarButtonItem)
            XCTAssertNil(picker.navigationController)
            XCTAssertTrue(picker.view.subviews.contains { $0 is RemoteMediaPickerPanelView })
            XCTAssertFalse(descendants(picker.view).contains { $0 is UINavigationBar })
        }
    }
    private func descendants(_ view: UIView) -> [UIView] {
        view.subviews.flatMap { [$0] + descendants($0) }
    }
}

@MainActor
private final class LayoutEditorService: PostEditorService {
    var author: PostEditorAuthor? {
        PostEditorAuthor(nickname: "Nickname molto lungo", university: "Università degli Studi di Bari Aldo Moro",
                         avatar: AvatarIdentity(seed: ""))
    }
    func save(_ draft: PostEditorDraft, mode: PostEditorMode, clientID: String) async throws -> PostEditorResult {
        throw URLError(.notConnectedToInternet)
    }
}
