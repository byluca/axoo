import XCTest
import UIKit
@testable import Axoo

@MainActor
final class SharedNativeUITests: XCTestCase {
    func testConfirmationKeepsShortActionsTogetherAndStacksLongLabels() throws {
        for longLabels in [false, true] {
            let card = AxooConfirmationCard(title: "Vuoi uscire?", message: "Il post non è stato pubblicato.",
                cancelTitle: longLabels ? "Continua a scrivere" : "Annulla",
                actionTitle: longLabels ? "Esci senza pubblicare" : "Esci")
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
            window.rootViewController = card
            window.makeKeyAndVisible()
            defer { window.isHidden = true }
            window.layoutIfNeeded()
            card.view.layoutIfNeeded()
            card.view.setNeedsLayout()
            card.view.layoutIfNeeded()
            func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap { descendants($0) } }
            let views = descendants(card.view)
            let surface = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "confirmation-card" })
            let action = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "confirmation-action" } as? UIButton)
            let cancel = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "confirmation-cancel" } as? UIButton)
            let row = try XCTUnwrap(action.superview as? UIStackView)
            XCTAssertEqual(row.axis, longLabels ? .vertical : .horizontal)
            XCTAssertLessThanOrEqual(surface.bounds.width, 320)
            XCTAssertGreaterThanOrEqual(action.bounds.height, 44)
            if longLabels {
                XCTAssertGreaterThanOrEqual(action.frame.minY, cancel.frame.maxY)
            } else {
                XCTAssertEqual(action.frame.minY, cancel.frame.minY, accuracy: 0.1)
            }
            let title = try XCTUnwrap(views.compactMap { $0 as? UILabel }.first { $0.text == "Vuoi uscire?" })
            XCTAssertEqual(title.textAlignment, .left)
        }
    }

    func testAsyncConfirmationStaysOpenAndPreventsRepeatedSubmissionsWhileBusy() throws {
        var calls = 0
        let card = AxooConfirmationCard(title: "Codice invito",message: "Conferma il codice.",
            actionTitle: "Conferma",dismissOnAction: false) { calls += 1 }
        card.loadViewIfNeeded()
        func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap { descendants($0) } }
        let action = try XCTUnwrap(descendants(card.view).first { $0.accessibilityIdentifier == "confirmation-action" } as? UIButton)
        action.sendActions(for: .touchUpInside)
        XCTAssertEqual(calls,1)
        card.setBusy(true)
        XCTAssertFalse(action.isEnabled)
        action.sendActions(for: .touchUpInside)
        XCTAssertEqual(calls,1)
        card.setBusy(false)
        XCTAssertTrue(action.isEnabled)
        action.sendActions(for: .touchUpInside)
        XCTAssertEqual(calls,2)
    }

    func testCustomRefreshAttachesRunsOnceAndCanRefreshAgain() {
        UIView.setAnimationsEnabled(false)
        defer { UIView.setAnimationsEnabled(true) }
        let scroll = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.contentInset = UIEdgeInsets(top: 8, left: 4, bottom: 16, right: 4)
        scroll.contentSize = CGSize(width: 390, height: 2000)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = UIViewController()
        window.rootViewController!.view.addSubview(scroll)
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        window.layoutIfNeeded()
        var requests = 0
        let refresh = AxooRefresh(scrollView: scroll) { requests += 1 }
        XCTAssertTrue(scroll.refreshControl === refresh)
        refresh.beginRefreshing()
        refresh.sendActions(for: .valueChanged)
        refresh.sendActions(for: .valueChanged)
        XCTAssertEqual(requests, 1)
        XCTAssertTrue(refresh.isRefreshing)
        refresh.endRefreshing(); refresh.endRefreshing()
        XCTAssertFalse(refresh.isRefreshing)
        XCTAssertEqual(scroll.contentInset, UIEdgeInsets(top: 8, left: 4, bottom: 16, right: 4))
        refresh.beginRefreshing()
        refresh.sendActions(for: .valueChanged)
        XCTAssertEqual(requests, 2)
        refresh.endRefreshing()
    }
    func testDisabledRefreshCannotStart() {
        let scroll = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
        var requests = 0
        let refresh = AxooRefresh(scrollView: scroll) { requests += 1 }
        refresh.isEnabled = false
        refresh.sendActions(for: .valueChanged)
        XCTAssertEqual(requests, 0)
    }
    func testRollingNumberRebindingUsesNewRecordValue() {
        let score = RollingNumberView(frame: CGRect(x: 0, y: 0, width: 80, height: 40))
        score.configure(value: 99, identity: "a")
        score.configure(value: 100, identity: "a")
        score.configure(value: -3, identity: "b")
        XCTAssertEqual(score.text, "-3")
    }
    func testSkeletonRendersAtCollapsedWidthWithoutCrashing() {
        let skeleton = SkeletonCardView(type: .post)
        skeleton.frame = CGRect(x: 0, y: 0, width: 1, height: 224)
        skeleton.animationEnabled = false
        let image = UIGraphicsImageRenderer(size: skeleton.bounds.size).image { context in
            skeleton.layer.render(in: context.cgContext)
        }
        XCTAssertNotNil(image.pngData())
    }
    func testSheetContentPrecedesCancelAndHasFiniteScrollableHeight() {
        let sheet = AxooBottomSheet(title: "Opzioni", message: "Testo molto lungo su diverse righe")
        for index in 0..<20 { sheet.addButton("Azione \(index)") {} }
        XCTAssertEqual((sheet.stack.arrangedSubviews.first as? UILabel)?.text, "Opzioni")
        XCTAssertEqual(sheet.stack.arrangedSubviews.last?.accessibilityIdentifier, "sheet-cancel")
        let height = sheet.fittingHeight(width: 320)
        XCTAssertTrue(height.isFinite)
        XCTAssertGreaterThan(height, 600)
    }
    func testSheetUsesUIKitContentDetentAndNativeGrabber() throws {
        let sheet = AxooBottomSheet(title: "Opzioni")
        XCTAssertEqual(sheet.modalPresentationStyle, .pageSheet)
        XCTAssertNil(sheet.transitioningDelegate)
        let presentation = try XCTUnwrap(sheet.sheetPresentationController)
        XCTAssertTrue(presentation.delegate === sheet)
        XCTAssertEqual(presentation.detents.count, 1)
        XCTAssertEqual(presentation.detents.first?.identifier, .init("axoo-content"))
        XCTAssertTrue(presentation.prefersGrabberVisible)
        XCTAssertFalse(presentation.prefersScrollingExpandsWhenScrolledToEdge)
        XCTAssertTrue(presentation.prefersEdgeAttachedInCompactHeight)
        XCTAssertEqual(presentation.preferredCornerRadius, 28)
    }
    func testSheetBlocksAllInteractiveDismissalWhileSubmittingAndRestoresCancel() throws {
        let sheet = AxooBottomSheet(title: "Opzioni")
        let presentation = try XCTUnwrap(sheet.sheetPresentationController)
        let cancel = try XCTUnwrap(sheet.stack.arrangedSubviews.last as? UIButton)
        XCTAssertTrue(sheet.presentationControllerShouldDismiss(presentation))
        sheet.allowsDismissal = false
        XCTAssertTrue(sheet.isModalInPresentation)
        XCTAssertFalse(sheet.presentationControllerShouldDismiss(presentation))
        XCTAssertFalse(cancel.isEnabled)
        sheet.allowsDismissal = true
        XCTAssertFalse(sheet.isModalInPresentation)
        XCTAssertTrue(sheet.presentationControllerShouldDismiss(presentation))
        XCTAssertTrue(cancel.isEnabled)
    }
    func testSheetContentHeightUpdatesAfterAppendingOrHidingAnError() {
        let sheet = AxooBottomSheet(title: "Opzioni")
        let initial = sheet.fittingHeight(width: 320)
        let error = UILabel.axoo("Invio non riuscito. Riprova.")
        sheet.appendContent(error)
        XCTAssertGreaterThan(sheet.fittingHeight(width: 320), initial)
        error.isHidden = true
        sheet.preferredContentSizeDidChange()
        XCTAssertEqual(sheet.fittingHeight(width: 320), initial, accuracy: 0.1)
    }
    func testNativeSheetRunsOneActionOnlyAfterDismissal() async {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let host = AxooViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let sheet = AxooBottomSheet(title: "Opzioni")
        let selected = expectation(description: "Selection after UIKit dismissal")
        var calls = 0
        let button = sheet.addButton("Apri") {
            calls += 1
            XCTAssertNil(host.presentedViewController)
            selected.fulfill()
        }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            host.present(sheet, animated: false) { continuation.resume() }
        }
        button.sendActions(for: .touchUpInside)
        button.sendActions(for: .touchUpInside)
        await fulfillment(of: [selected], timeout: 3)
        XCTAssertEqual(calls, 1)
    }

    func testOptionsMenuUsesBottomSheetAndRebuildsAvailableActions() async throws {
        UIView.setAnimationsEnabled(false)
        defer { UIView.setAnimationsEnabled(true) }
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let host = AxooViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let menu = UIButton(type: .system)
        menu.frame = CGRect(x: 20, y: 100, width: 44, height: 44)
        host.view.addSubview(menu)
        var ownsPost = true
        let actionRan = expectation(description: "Action after closing the options sheet")
        menu.setOptionsSheet { sheet in
            sheet.addButton(ownsPost ? "Modifica" : "Segnala") {
                XCTAssertNil(host.presentedViewController)
                actionRan.fulfill()
            }
        }
        menu.sendActions(for: .touchUpInside)
        let first = try XCTUnwrap(host.presentedViewController as? AxooBottomSheet)
        XCTAssertEqual(first.view.accessibilityIdentifier, "options-sheet")
        let edit = try XCTUnwrap(first.stack.arrangedSubviews.first as? UIButton)
        XCTAssertEqual(edit.configuration?.title, "Modifica")
        let firstAppeared = expectation(description: "First sheet appeared")
        DispatchQueue.main.async { firstAppeared.fulfill() }
        await fulfillment(of: [firstAppeared], timeout: 3)
        edit.sendActions(for: .touchUpInside)
        await fulfillment(of: [actionRan], timeout: 3)

        ownsPost = false
        menu.sendActions(for: .touchUpInside)
        let second = try XCTUnwrap(host.presentedViewController as? AxooBottomSheet)
        XCTAssertEqual((second.stack.arrangedSubviews.first as? UIButton)?.configuration?.title, "Segnala")
        await second.close()
    }

    func testOptionsMenuWithNoAvailableActionsDoesNotPresentAnEmptySheet() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let host = AxooViewController()
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let menu = UIButton(type: .system)
        host.view.addSubview(menu)
        menu.setOptionsSheet { _ in }
        menu.sendActions(for: .touchUpInside)
        XCTAssertNil(host.presentedViewController)
    }
}
