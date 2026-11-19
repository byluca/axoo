
import XCTest
import UIKit
@testable import Axoo

@MainActor
final class PostDetailHostTests: XCTestCase {
    func testNotificationReloadKeepsTheRefreshGestureWithoutStartingItsIndicator() async throws {
        let screen = makeScreen(InMemoryCommentRepository(delay: 50_000_000))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = UINavigationController(rootViewController: screen)
        window.makeKeyAndVisible()
        defer { screen.viewModel.stop(); window.isHidden = true }
        screen.loadViewIfNeeded()
        screen.viewModel.start()
        await screen.viewModel.loadTask?.value
        screen.viewModel.stop()
        let table = try XCTUnwrap(screen.view.subviews.compactMap { $0 as? UITableView }.first)
        let refresh = try XCTUnwrap(table.refreshControl as? AxooRefresh)

        screen.setNotificationComment("nested")
        screen.viewWillAppear(false)
        XCTAssertTrue(screen.viewModel.state.isRefreshing)
        XCTAssertFalse(refresh.isRefreshing, "Opening a notification must not start the pull-to-refresh indicator")
        await screen.viewModel.loadTask?.value
        XCTAssertNotNil(screen.viewModel.item("nested"))
        XCTAssertFalse(refresh.isRefreshing)

        refresh.beginRefreshing()
        refresh.sendActions(for: .valueChanged)
        XCTAssertTrue(refresh.isRefreshing, "The manual refresh gesture remains available")
        await screen.viewModel.loadTask?.value
        for _ in 0..<100 {
            if !refresh.isRefreshing { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertFalse(refresh.isRefreshing)
    }

    func testRefreshResumesTheAlreadyLoadedStickerWithoutAnotherScroll() async throws {
        let post = Post(id: "post", nickname: "", campus: "", timeLabel: "", body: "Test",
                                   mediaURL: "fixture://sticker", mediaWidth: 100, mediaHeight: 100)
        let screen = PostDetailHost(post: post, repository: InMemoryCommentRepository(scenario: .empty, delay: 0))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = UINavigationController(rootViewController: screen)
        window.makeKeyAndVisible()
        defer { screen.viewModel.stop(); window.isHidden = true }
        screen.loadViewIfNeeded()
        screen.view.layoutIfNeeded()
        screen.viewDidAppear(false)
        await screen.viewModel.loadTask?.value
        let image = try XCTUnwrap(descendants(screen.card).compactMap { $0 as? PostMediaImageView }.first)
        let frames = [try XCTUnwrap(UIImage(systemName: "circle.fill")), try XCTUnwrap(UIImage(systemName: "square.fill"))]
        image.display(try XCTUnwrap(UIImage.animatedImage(with: frames, duration: 0.2)))
        let loadedFrame = try XCTUnwrap(image.animationImages?.first)
        for _ in 0..<2 {
            image.playbackEnabled = false
            XCTAssertFalse(image.isAnimating)
            screen.viewModel.refresh()
            await screen.viewModel.loadTask?.value
            await Task.yield()
            screen.view.layoutIfNeeded()
            screen.viewDidLayoutSubviews()
            XCTAssertTrue(image.playbackEnabled)
            XCTAssertTrue(image.isAnimating)
            XCTAssertTrue(image.animationImages?.first === loadedFrame)
        }
    }

    private func descendants(_ view: UIView) -> [UIView] {
        view.subviews.flatMap { [$0] + descendants($0) }
    }

    func testPresentationTraitChangesKeepTheListBound() throws {
        let screen = makeScreen(InMemoryCommentRepository(delay: 0))
        screen.loadViewIfNeeded()
        screen.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        screen.view.layoutIfNeeded()
        let table = try XCTUnwrap(screen.view.subviews.compactMap { $0 as? UITableView }.first)
        let original = try XCTUnwrap(table.dataSource)
        let counter = DetailReloadCounter(wrapping: original)
        table.dataSource = counter
        defer { table.dataSource = original }
        table.reloadData()
        table.layoutIfNeeded()
        counter.sectionRequests = 0

        let previousPresentation = UITraitCollection(traitsFrom: [
            screen.traitCollection, UITraitCollection(userInterfaceLevel: .elevated)
        ])
        screen.traitCollectionDidChange(previousPresentation)
        table.layoutIfNeeded()
        XCTAssertEqual(counter.sectionRequests, 0, "Presenting a sheet must not reload the comment list")

    }

    func testReturnSwipeUsesRightDirectionAndProtectsCommentEditing() async throws {
        let screen = makeScreen(InMemoryCommentRepository(delay: 0))
        let navigation = AxooNavigationController(rootViewController: AxooViewController())
        navigation.loadViewIfNeeded()
        navigation.pushViewController(screen, animated: false)
        screen.loadViewIfNeeded()
        screen.viewModel.start()
        await screen.viewModel.loadTask?.value
        let swipe = try XCTUnwrap(screen.view.gestureRecognizers?.compactMap { $0 as? UISwipeGestureRecognizer }.first)
        XCTAssertEqual(swipe.direction, .right)
        XCTAssertTrue(screen.allowsBackSwipe)
        screen.viewModel.beginEditing("own")
        XCTAssertFalse(screen.allowsBackSwipe)
        XCTAssertFalse(navigation.gestureRecognizerShouldBegin(try XCTUnwrap(navigation.interactivePopGestureRecognizer)))
        screen.viewModel.cancelEditing()
        XCTAssertTrue(screen.allowsBackSwipe)
        screen.viewModel.stop()
    }
    private func makeScreen(_ repository: CommentRepository) -> PostDetailHost {
        PostDetailHost(post: Post(id: "post", nickname: "Autore", campus: "UniBa", timeLabel: "ora", body: "Test", score: 42, commentsCount: 8), repository: repository)
    }
}

@MainActor
private final class DetailReloadCounter: NSObject, UITableViewDataSource {
    private let wrapped: UITableViewDataSource
    var sectionRequests = 0
    init(wrapping source: UITableViewDataSource) { wrapped = source; super.init() }
    func numberOfSections(in tableView: UITableView) -> Int {
        sectionRequests += 1
        return wrapped.numberOfSections?(in: tableView) ?? 1
    }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        wrapped.tableView(tableView, numberOfRowsInSection: section)
    }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        wrapped.tableView(tableView, cellForRowAt: indexPath)
    }
}


