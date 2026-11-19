import XCTest
import UIKit
@testable import Axoo

@MainActor
final class ProgrammaticPresentationTests: XCTestCase {
    func testCardMeasurementKeepsIndependentActionsInsideBounds() throws {
        let post = Post(id: "layout", nickname: "Nickname molto lungo", campus: "Università degli Studi di Bari",
                                   timeLabel: "1 h", category: "Categoria sconosciuta lunga", body: String(repeating: "Testo 👩🏽‍💻 **letterale**\n", count: 20),
                                   score: 12345, commentsCount: 1200)
        for width: CGFloat in [296, 366, 744] {
            for height: CGFloat in [640, 844] {
                let host = AxooViewController()
                let navigation = UINavigationController(rootViewController: host)
                navigation.loadViewIfNeeded()
                host.loadViewIfNeeded()
                let card = PostCardView(post: post) { _ in }
                host.view.addSubview(card)
                let measured = card.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
                XCTAssertTrue(measured.height.isFinite)
                card.frame = CGRect(origin: .zero, size: measured)
                card.layoutIfNeeded()
                let up = try XCTUnwrap(find("post-up-layout", in: card) as? UIButton)
                let down = try XCTUnwrap(find("post-down-layout", in: card) as? UIButton)
                let open = try XCTUnwrap(find("post-open-layout", in: card) as? UIControl)
                let comments = try XCTUnwrap(find("post-comments-layout", in: card) as? UIButton)
                let menu = try XCTUnwrap(find("post-menu-layout", in: card) as? UIButton)
                let share = try XCTUnwrap(find("post-share-layout", in: card) as? UIButton)
                XCTAssertEqual(share.frame.minX, comments.frame.maxX + 4, accuracy: 0.1)
                XCTAssertFalse(share.frame.intersects(menu.frame))
                for action in [up, down, comments, share, menu] {
                    XCTAssertGreaterThanOrEqual(action.bounds.width, 44)
                    XCTAssertGreaterThanOrEqual(action.bounds.height, 44)
                    XCTAssertTrue(card.bounds.contains(action.frame), "Action outside card at \(width), \(height)")
                    // The full-width content sits behind the vote column. Its
                    // touch target must not intercept the independent actions.
                    let center = CGPoint(x: action.frame.midX, y: action.frame.midY)
                    let target = card.hitTest(center, with: nil)
                    XCTAssertTrue(target === action || target?.isDescendant(of: action) == true)
                }
                XCTAssertFalse(up.frame.intersects(down.frame))
                let category = try XCTUnwrap(descendants(card).first { $0 is PostCategoryLabel })
                let point = category.convert(CGPoint(x: category.bounds.midX, y: category.bounds.midY), to: card)
                XCTAssertTrue(card.hitTest(point, with: nil) === open, "Category must not swallow the open action")
            }
        }
    }

    func testDetailPreservesLiteralTextAndAllowsSelection() throws {
        let text = "**Non è Markdown** 👨‍👩‍👧‍👦 🇮🇹\n" + String(repeating: "Testo completo\n", count: 30)
        let post = Post(id: "literal", nickname: "", campus: "", timeLabel: "ora", body: text)
        let feed = PostCardView(post: post) { _ in }
        let detail = PostCardView(post: post, presentation: .detail) { _ in }
        let size = CGSize(width: 366, height: CGFloat.greatestFiniteMagnitude)
        for card in [feed, detail] {
            card.frame = CGRect(origin: .zero, size: card.sizeThatFits(size))
            card.layoutIfNeeded()
            let body = try XCTUnwrap(descendants(card).first { $0 is UITextView } as? UITextView)
            XCTAssertEqual(body.text, text)
            XCTAssertEqual(body.textContainer.maximumNumberOfLines, 0)
            XCTAssertEqual(body.textContainer.lineBreakMode, .byWordWrapping)
            let bodyBounds = body.convert(body.bounds, to: card)
            XCTAssertTrue(card.bounds.contains(bodyBounds))
            XCTAssertGreaterThan(body.bounds.height, body.font!.lineHeight * 30)
        }
        let textView = try XCTUnwrap(descendants(detail).first { $0 is UITextView } as? UITextView)
        XCTAssertEqual(textView.text, text)
        XCTAssertTrue(textView.isSelectable)
        XCTAssertFalse(textView.isEditable)
        XCTAssertFalse(textView.isScrollEnabled)
    }

    func testVoteOnlyEmitsItsActionAndReconfigurationUpdatesScore() throws {
        var post = Post(id: "action", nickname: "", campus: "", timeLabel: "ora", body: "Test", score: 42)
        var actions: [String] = []
        let card = PostCardView(post: post) { action in
            switch action {
            case .vote(let vote): actions.append("vote-\(vote.rawValue)")
            case .open: actions.append("open")
            default: actions.append("other")
            }
        }
        let up = try XCTUnwrap(find("post-up-action", in: card) as? UIButton)
        up.sendActions(for: .touchUpInside)
        XCTAssertEqual(actions, ["vote-1"])
        post.toggleVote(.up)
        card.configure(post)
        XCTAssertTrue(up.isSelected)
        XCTAssertEqual((find("post-score-action", in: card) as? RollingNumberView)?.text, "43")

    }

    func testAuthRoutingAndManualFieldBinding() async throws {
        let operations = AuthOperations(service: PresentationAuthService())
        let root = AuthRootView(operations: operations)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        await waitUntil { root.topViewController is IntroView }
        root.viewModel.beginLogin()
        await waitUntil { root.topViewController is LoginView }
        await waitUntil { root.topViewController.flatMap { self.find("email-field", in: $0.view) } != nil }
        weak var login = root.topViewController
        let field = try XCTUnwrap(find("email-field", in: root.topViewController!.view) as? UITextField)
        field.text = "student@campus.it"
        field.sendActions(for: .editingChanged)
        XCTAssertEqual(root.viewModel.email, "student@campus.it")
        root.viewModel.sendCode()
        await root.viewModel.operation?.value
        await waitUntil { root.topViewController.flatMap { self.find("code-field", in: $0.view) } != nil }
        XCTAssertTrue(login === root.topViewController, "Email and OTP share one controller without duplicate bindings")
        let code = try XCTUnwrap(find("code-field", in: root.topViewController!.view) as? UITextField)
        code.text = "12a34567"
        code.sendActions(for: .editingChanged)
        XCTAssertEqual(root.viewModel.code, "123456")
        XCTAssertEqual(code.text, "123456")
        root.viewModel.signOut()
        await waitUntil { root.topViewController is IntroView }
        await waitUntil { login == nil }
    }

    private func waitUntil(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        for _ in 0..<100 {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertTrue(condition(), "UIKit binding did not settle", file: file, line: line)
    }
    private func descendants(_ view: UIView) -> [UIView] {
        [view] + view.subviews.flatMap { descendants($0) }
    }
    private func find(_ id: String, in view: UIView) -> UIView? {
        descendants(view).first { $0.accessibilityIdentifier == id }
    }
}

@MainActor
private final class PresentationAuthService: AuthService {
    func restore() async throws -> AccountAccess? { nil }
    func hasSession() throws -> Bool { false }
    func requestCode(email: String) async throws {}
    func verify(email: String, code: String) async throws -> AccountAccess {
        AccountAccess(status: .active, reason: "", suspendedUntil: nil)
    }
    func signOut() throws {}
}
