import XCTest
import UIKit
@testable import Axoo

@MainActor
final class ProfileSettingsHostTests: XCTestCase {
    func testProfileHeadingIsOnTheLeft() throws {
        let screen = ProfileHost(viewModel: ProfileViewModel(repository: ProfileRepository(api: SettingsAPI(), currentUser: try profile())), onSignOut: {})
        screen.loadViewIfNeeded()
        let headerContents = screen.view.subviews.flatMap(\.subviews)
        let heading = try XCTUnwrap(headerContents.first { $0.accessibilityIdentifier == "profile-heading" } as? UILabel)
        XCTAssertEqual(heading.text, "Profilo")
        XCTAssertEqual(heading.font.pointSize, 22)
        let menu = try XCTUnwrap(headerContents.first { $0.accessibilityIdentifier == "profile-menu" } as? UIButton)
        XCTAssertTrue(heading.superview === menu.superview)
        XCTAssertNotNil(menu.image(for: .normal))
        XCTAssertNil(screen.navigationItem.leftBarButtonItem)
        XCTAssertNil(screen.navigationItem.rightBarButtonItem)
    }
    func testSettingsUseGroupedCardsBackButtonOriginalIconsAndReadableVersion() throws {
        let api = SettingsAPI()
        let screen = ProfileSettingsHost(viewModel: ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try profile())), onSignOut: {})
        screen.loadViewIfNeeded()
        XCTAssertNil(screen.navigationItem.rightBarButtonItem)
        let back = try XCTUnwrap(screen.navigationItem.leftBarButtonItem?.customView as? ArrowBackButton)
        XCTAssertEqual(back.accessibilityIdentifier, "settings-back")
        XCTAssertNotNil(back.image(for: .normal))
        XCTAssertEqual(screen.tableView.style, .insetGrouped)
        XCTAssertEqual(screen.tableView.backgroundColor, AxooTheme.surface)
        XCTAssertEqual(screen.tableView.separatorStyle, .none)
        XCTAssertEqual(screen.tableView.rowHeight, UITableView.automaticDimension)
        for row in 0..<3 {
            let cell = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: row, section: 1))
            XCTAssertNotNil(cell.imageView?.image)
            XCTAssertEqual(cell.textLabel?.font.pointSize, 17)
        }
        for section in 0..<screen.numberOfSections(in: screen.tableView) {
            for row in 0..<screen.tableView(screen.tableView, numberOfRowsInSection: section) {
                let cell = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: row, section: section))
                XCTAssertEqual(cell.backgroundColor, AxooTheme.cardBackground)
                XCTAssertEqual(cell.accessoryType, .none)
            }
        }
        let version = try XCTUnwrap(screen.tableView.tableFooterView as? UILabel)
        let number = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.52"
        XCTAssertEqual(version.text, "Axoo · Versione \(number)")
    }

    func testBlockedMenuAndUnblockRemoveOnlyTheSelectedUser() async throws {
        let api = SettingsAPI()
        var unblocked: [String] = []
        let model = ProfileViewModel(repository: ProfileRepository(api: api))
        model.onUnblocked = { unblocked.append($0) }
        let screen = BlockedUsersHost(viewModel: model)
        screen.loadViewIfNeeded()
        await screen.viewModel.blockedTask?.value
        XCTAssertTrue(screen.navigationItem.leftBarButtonItem?.customView is ArrowBackButton)
        let cell = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: 0, section: 0))
        let menu = try XCTUnwrap(cell.accessoryView as? UIButton)
        XCTAssertEqual(menu.accessibilityIdentifier, "blocked-menu-first")
        XCTAssertEqual(menu.bounds.size, CGSize(width: 44, height: 44))
        XCTAssertNotNil(menu.image(for: .normal))
        screen.unblock("first")
        await screen.viewModel.unblockTask?.value
        XCTAssertEqual(api.deleted, ["users/blocks/first"])
        XCTAssertEqual(unblocked, ["first"])
        let remaining = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: 0, section: 0))
        XCTAssertEqual(remaining.textLabel?.text, "second")
    }

    func testUnblockFailureKeepsUserAndDoesNotNotifyFeed() async throws {
        let api = SettingsAPI()
        api.failDelete = true
        var notified = false
        let model = ProfileViewModel(repository: ProfileRepository(api: api))
        model.onUnblocked = { _ in notified = true }
        let screen = BlockedUsersHost(viewModel: model)
        screen.loadViewIfNeeded()
        await screen.viewModel.blockedTask?.value
        screen.unblock("first")
        await screen.viewModel.unblockTask?.value
        XCTAssertFalse(notified)
        let cell = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: 0, section: 0))
        XCTAssertEqual(cell.textLabel?.text, "first")
        XCTAssertNil(screen.viewModel.unblockTask)
    }

    private func profile() throws -> AccountProfile {
        try APIJSON.decode(AccountProfile.self, from: Data(#"{"id":"me","nickname":"student","university_id":"campus"}"#.utf8))
    }
}

@MainActor
private final class SettingsAPI: APIClient {
    var deleted: [String] = []
    var failDelete = false
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        if method == "DELETE" {
            if failDelete { throw URLError(.notConnectedToInternet) }
            deleted.append(path)
            return Data("{}".utf8)
        }
        return Data(#"{"users":[{"id":"first","nickname":"first","university_id":"campus"},{"id":"second","nickname":"second","university_id":"campus"}]}"#.utf8)
    }
}
