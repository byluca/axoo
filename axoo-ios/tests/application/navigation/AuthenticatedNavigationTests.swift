import XCTest
import UIKit
@testable import Axoo

@MainActor
final class AuthenticatedNavigationTests: XCTestCase {
    func testProfileLoadFailureCanRetryWithoutSigningOut() async throws {
        let api = NavigationAPI()
        api.profileFailuresRemaining = 1
        var signOutCount = 0
        let root = AppRootView(api: api, onSignOut: { signOutCount += 1 })
        root.loadViewIfNeeded()
        defer { root.stop() }
        let retry = try XCTUnwrap(find("campus-retry", in: root.view) as? UIButton)
        await waitUntil { retry.isEnabled }
        XCTAssertTrue(root.children.isEmpty)
        XCTAssertEqual(api.profileRequests, 1)

        retry.sendActions(for: .touchUpInside)
        XCTAssertFalse(retry.isEnabled)
        await waitUntil { root.children.first is HomeTabBarController }
        XCTAssertEqual(api.profileRequests, 2)
        XCTAssertEqual(signOutCount, 0)
    }

    func testCustomArrowKeepsNativeReturnAndRejectsRoot() throws {
        let navigation = AxooNavigationController(rootViewController: AxooViewController())
        navigation.loadViewIfNeeded()
        let gesture = try XCTUnwrap(navigation.interactivePopGestureRecognizer)
        XCTAssertTrue(gesture.delegate === navigation)
        XCTAssertFalse(navigation.gestureRecognizerShouldBegin(gesture))
        let child = AxooViewController()
        child.installArrowBackButton(testID: "test-back")
        navigation.pushViewController(child, animated: false)
        XCTAssertTrue(navigation.gestureRecognizerShouldBegin(gesture))
        XCTAssertTrue(child.navigationItem.leftBarButtonItem?.customView is ArrowBackButton)
        let locked = LockedSwipeScreen()
        navigation.pushViewController(locked, animated: false)
        XCTAssertFalse(navigation.gestureRecognizerShouldBegin(gesture))
    }
    func testTabsRetainSeparateStacksAndCreationReturnsToItsOrigin() async throws {
        let root = AppRootView(api: NavigationAPI(), onSignOut: {})
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer { root.stop(); window.isHidden = true }
        await waitUntil { root.children.first is HomeTabBarController }
        let tabs = try XCTUnwrap(root.children.first as? HomeTabBarController)
        let controllers = try XCTUnwrap(tabs.viewControllers)
        XCTAssertEqual(controllers.count, 4)
        XCTAssertEqual(controllers.map { $0.tabBarItem.accessibilityIdentifier },
                       ["navigation-home", "navigation-create", "navigation-notifications", "navigation-profile"])
        let homeStack = try XCTUnwrap(controllers[0] as? UINavigationController)
        let creationTab = controllers[1]
        let profileStack = try XCTUnwrap(controllers[3] as? UINavigationController)
        let home = try XCTUnwrap(homeStack.viewControllers.first as? HomeHost)
        select(profileStack, in: tabs)
        XCTAssertFalse(profileStack === homeStack)
        XCTAssertTrue(profileStack.viewControllers.first is ProfileHost)
        XCTAssertEqual(profileStack.viewControllers.count, 1, "Profilo is a tab root")
        let profile = try XCTUnwrap(profileStack.viewControllers.first as? ProfileHost)
        profile.view.layoutIfNeeded()
        XCTAssertNil(find("profile-leaderboard", in: profile.view))
        let myPosts = try XCTUnwrap(find("profile-my-posts", in: profile.view))
        XCTAssertEqual(myPosts.bounds.width, profile.view.safeAreaLayoutGuide.layoutFrame.width - 40, accuracy: 1)
        XCTAssertTrue(homeStack.viewControllers.first === home)

        let notificationsStack = try XCTUnwrap(controllers[2] as? UINavigationController)
        tabs.bindNotificationsCount(2)
        XCTAssertEqual(notificationsStack.tabBarItem.accessibilityValue, "2 nuove")
        XCTAssertNil(profileStack.tabBarItem.accessibilityValue)
        XCTAssertTrue(notificationsStack.viewControllers.first is NotificationsHost)
        let inbox = try XCTUnwrap(notificationsStack.viewControllers.first as? NotificationsHost)
        inbox.loadViewIfNeeded()
        let notificationTable = try XCTUnwrap(inbox.view.subviews.compactMap { $0 as? UITableView }.first)
        XCTAssertNil(notificationTable.refreshControl)
        for origin in [profileStack, notificationsStack, homeStack] {
            select(origin, in: tabs)
            select(creationTab, in: tabs)
            await waitUntil { root.presentedViewController is UINavigationController }
            let creation = try XCTUnwrap(root.presentedViewController as? UINavigationController)
            let editor = try XCTUnwrap(creation.topViewController as? PostEditorHost)
            select(creationTab, in: tabs)
            XCTAssertTrue(root.presentedViewController === creation, "Repeated creation cannot open a second editor")
            XCTAssertTrue(tabs.selectedViewController === origin)
            XCTAssertEqual(profileStack.viewControllers.count, 1)
            XCTAssertEqual(homeStack.viewControllers.count, 1)
            let back = try XCTUnwrap(find("editor-back", in: editor.view) as? UIButton)
            try? await Task.sleep(nanoseconds: 400_000_000)
            back.sendActions(for: .touchUpInside)
            await waitUntil { root.presentedViewController == nil }
            XCTAssertTrue(tabs.selectedViewController === origin)
        }

        // Retained destinations must remain in their originating stack.
        let profileChild = UIViewController()
        profileStack.pushViewController(profileChild, animated: false)
        XCTAssertTrue(profileChild.hidesBottomBarWhenPushed)
        select(homeStack, in: tabs)
        XCTAssertTrue(tabs.selectedViewController === homeStack)
        let homeChild = UIViewController()
        homeStack.pushViewController(homeChild, animated: false)
        select(profileStack, in: tabs)
        XCTAssertTrue(tabs.selectedViewController === profileStack)
        XCTAssertTrue(profileStack.topViewController === profileChild)
        XCTAssertTrue(homeStack.topViewController === homeChild)
    }
    private func select(_ controller: UIViewController, in tabs: HomeTabBarController) {
        if tabs.tabBarController(tabs, shouldSelect: controller) {
            tabs.selectedViewController = controller
        }
    }
    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<100 {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertTrue(condition())
    }
    private func find(_ id: String, in view: UIView) -> UIView? {
        if view.accessibilityIdentifier == id { return view }
        for child in view.subviews { if let match = find(id, in: child) { return match } }
        return nil
    }
}

@MainActor
private final class LockedSwipeScreen: AxooViewController {
    override var allowsBackSwipe: Bool { false }
}

@MainActor
private final class NavigationAPI: APIClient {
    var profileFailuresRemaining = 0
    private(set) var profileRequests = 0

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        switch path {
        case "users/me":
            profileRequests += 1
            if profileFailuresRemaining > 0 {
                profileFailuresRemaining -= 1
                throw URLError(.notConnectedToInternet)
            }
            return Data(#"{"id":"me","nickname":"student","university_id":"campus","university_name":"Campus"}"#.utf8)
        case "users/blocks": return Data(#"{"users":[]}"#.utf8)
        case "notifications": return Data(#"{"notifications":[],"next_cursor":"","seen_through":"","unread_count":0}"#.utf8)
        case "notifications/unread-count", "notifications/seen": return Data(#"{"unread_count":0}"#.utf8)
        case "posts": return Data(#"{"posts":[],"next_cursor":null}"#.utf8)
        default: return Data("{}".utf8)
        }
    }
}
