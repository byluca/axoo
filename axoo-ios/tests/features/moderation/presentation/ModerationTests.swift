import XCTest
import UIKit
@testable import Axoo

@MainActor
final class ModerationTests: XCTestCase {
    func testSettingsOnlyExposeModerationForTheModeratorFlag() throws {
        for flags in [(false, false), (false, true), (true, false), (true, true)] {
            let api = ModerationAPI()
            let profile = try makeProfile(api, moderator: flags.0, admin: flags.1)
            let screen = ProfileSettingsHost(viewModel: ProfileViewModel(repository: profile), onSignOut: {})
            screen.loadViewIfNeeded()
            XCTAssertEqual(rowTitles(screen).contains("Moderazione"), flags.0)
        }
    }

    func testOpeningSettingsRefreshesRolesInTheExistingSession() async throws {
        let api = ModerationAPI()
        let profile = try makeProfile(api, moderator: false)
        let model = ProfileViewModel(repository: profile)
        let screen = ProfileSettingsHost(viewModel: model, onSignOut: {})
        screen.loadViewIfNeeded()
        api.moderator = true
        screen.viewWillAppear(false)
        await model.loadTask?.value
        XCTAssertTrue(rowTitles(screen).contains("Moderazione"))
        api.moderator = false
        screen.viewWillAppear(false)
        await model.loadTask?.value
        XCTAssertFalse(rowTitles(screen).contains("Moderazione"))
        XCTAssertEqual(api.calls.map(\.path), ["users/me", "users/me"])
        model.stop()
    }

    func testAdminWithoutModeratorCannotLoadOrAct() async throws {
        let api = ModerationAPI()
        let repository = ModerationRepository(profile: try makeProfile(api, moderator: false, admin: true))
        do {
            _ = try await repository.queue()
            XCTFail("A non-moderator must not reach the queue")
        } catch let error as HTTPFailure { XCTAssertEqual(error.status, 403) }
        let item = try sampleItem()
        do {
            try await repository.decide(.keep, item: item, reason: nil)
            XCTFail("A non-moderator must not submit a decision")
        } catch let error as HTTPFailure { XCTAssertEqual(error.status, 403) }
        XCTAssertTrue(api.calls.isEmpty)
    }

    func testQueueShowsBackendReportsAndTranslatedReasons() async throws {
        let api = ModerationAPI()
        let model = try makeModel(api)
        let screen = ModerationHost(viewModel: model)
        screen.loadViewIfNeeded()
        await model.loadTask?.value
        XCTAssertEqual(model.items.count, 1)
        XCTAssertEqual(model.items.first?.targetId, ModerationAPI.targetID)
        XCTAssertEqual(api.calls.first?.query, [URLQueryItem(name: "status", value: "open")])
        let cell = screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: 0, section: 0))
        XCTAssertEqual(cell.textLabel?.text, "Post · @student\nTesto segnalato")
        XCTAssertEqual(cell.detailTextLabel?.text, "2 segnalazioni · Spam o pubblicità")
        XCTAssertTrue(screen.navigationItem.leftBarButtonItem?.customView is ArrowBackButton)
        model.stop()
    }

    func testRemovalRequiresReasonAndSuccessClosesOnlyTheSelectedItem() async throws {
        let api = ModerationAPI()
        api.queueData = Data("{\"items\":[\(ModerationAPI.itemJSON),\(ModerationAPI.itemJSON.replacingOccurrences(of: ModerationAPI.targetID, with: "22222222-2222-4222-8222-222222222222"))]}".utf8)
        let model = try makeModel(api)
        model.load()
        await model.loadTask?.value
        let item = try XCTUnwrap(model.items.first)
        model.decide(.hide, item: item)
        XCTAssertNil(model.decisionTask)
        XCTAssertEqual(api.calls.count, 1)
        model.decide(.hide, item: item, reason: .spam)
        model.decide(.hide, item: item, reason: .spam)
        await model.decisionTask?.value
        XCTAssertEqual(model.items.count, 1)
        XCTAssertFalse(model.items.contains(item))
        let call = try XCTUnwrap(api.calls.last)
        XCTAssertEqual(call.method, "POST")
        XCTAssertEqual(call.path, "moderation/actions")
        let payload = try JSONSerialization.jsonObject(with: XCTUnwrap(call.body)) as? [String: String]
        XCTAssertEqual(payload, ["action": "hide", "target_type": "post", "target_id": item.targetId, "reason": "spam"])
        XCTAssertEqual(api.calls.count, 2)
        XCTAssertFalse(model.isBusy)
        model.stop()
    }

    func testFailedDecisionKeepsReportAndCanBeRetried() async throws {
        let api = ModerationAPI()
        let model = try makeModel(api)
        model.load()
        await model.loadTask?.value
        let item = try XCTUnwrap(model.items.first)
        api.failedPath = "moderation/actions"
        model.decide(.keep, item: item)
        await model.decisionTask?.value
        XCTAssertEqual(model.items, [item])
        XCTAssertNotNil(model.error)
        XCTAssertFalse(model.isBusy)
        api.failedPath = nil
        model.decide(.keep, item: item)
        await model.decisionTask?.value
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertNil(model.error)
        let payload = try JSONSerialization.jsonObject(with: XCTUnwrap(api.calls.last?.body)) as? [String: String]
        XCTAssertEqual(payload?["action"], "keep")
        XCTAssertEqual(payload?["reason"], "")
        model.stop()
    }

    func testRevokedAccessClearsPreviouslyLoadedReports() async throws {
        let api = ModerationAPI()
        let model = try makeModel(api)
        model.load()
        await model.loadTask?.value
        XCTAssertFalse(model.items.isEmpty)
        api.failedPath = "moderation/queue"
        api.failure = HTTPFailure(status: 403)
        model.load()
        await model.loadTask?.value
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertEqual(model.error, "Accesso riservato ai moderatori.")
        model.stop()
    }

    func testFailedQueueCanRetryAndKeepAComment() async throws {
        let api = ModerationAPI()
        api.failedPath = "moderation/queue"
        let model = try makeModel(api)
        model.load()
        await model.loadTask?.value
        XCTAssertNotNil(model.error)
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertFalse(model.isBusy)
        api.failedPath = nil
        api.queueData = Data("{\"items\":[\(ModerationAPI.itemJSON.replacingOccurrences(of: "\"post\"", with: "\"comment\""))]}".utf8)
        model.load()
        await model.loadTask?.value
        XCTAssertNil(model.error)
        let item = try XCTUnwrap(model.items.first)
        XCTAssertEqual(item.targetType, .comment)
        model.decide(.keep, item: item)
        await model.decisionTask?.value
        let payload = try JSONSerialization.jsonObject(with: XCTUnwrap(api.calls.last?.body)) as? [String: String]
        XCTAssertEqual(payload?["target_type"], "comment")
        XCTAssertTrue(model.items.isEmpty)
        model.stop()
    }

    func testLogoutDiscardsAnInFlightQueueResponse() async throws {
        let api = ModerationAPI()
        let profile = try makeProfile(api)
        let model = ModerationViewModel(repository: ModerationRepository(profile: profile))
        api.suspendQueue = true
        model.load()
        let task = model.loadTask
        for _ in 0..<1000 {
            if api.pending != nil { break }
            await Task.yield()
        }
        let pending = try XCTUnwrap(api.pending)
        profile.stop()
        pending.resume(returning: api.queueData)
        await task?.value
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertNil(model.error)
        model.stop()
    }

    func testLeavingScreenDiscardsPendingLoadAndPreventsNewRequests() async throws {
        let api = ModerationAPI()
        let model = try makeModel(api)
        api.suspendQueue = true
        model.load()
        let task = model.loadTask
        for _ in 0..<1000 {
            if api.pending != nil { break }
            await Task.yield()
        }
        let pending = try XCTUnwrap(api.pending)
        var changesAfterStop = 0
        model.stop()
        model.onChange = { changesAfterStop += 1 }
        pending.resume(returning: api.queueData)
        await task?.value
        model.load()
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertEqual(changesAfterStop, 0)
        XCTAssertEqual(api.calls.count, 1)
        XCTAssertFalse(model.isBusy)
    }

    private func makeProfile(_ api: ModerationAPI, moderator: Bool = true, admin: Bool = false) throws -> ProfileRepository {
        let data = Data("{\"id\":\"me\",\"nickname\":\"reviewer\",\"university_id\":\"campus\",\"is_moderator\":\(moderator),\"is_admin\":\(admin)}".utf8)
        return ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: data))
    }
    private func makeModel(_ api: ModerationAPI) throws -> ModerationViewModel {
        ModerationViewModel(repository: ModerationRepository(profile: try makeProfile(api)))
    }
    private func sampleItem() throws -> ModerationItem {
        try APIJSON.decode(ModerationItem.self, from: Data(ModerationAPI.itemJSON.utf8))
    }
    private func rowTitles(_ screen: ProfileSettingsHost) -> [String] {
        (0..<screen.numberOfSections(in: screen.tableView)).flatMap { section in
            (0..<screen.tableView(screen.tableView, numberOfRowsInSection: section)).compactMap { row in
                screen.tableView(screen.tableView, cellForRowAt: IndexPath(row: row, section: section)).textLabel?.text
            }
        }
    }
}

@MainActor
private final class ModerationAPI: APIClient {
    static let targetID = "11111111-1111-4111-8111-111111111111"
    static let itemJSON = #"{"target_type":"post","target_id":"11111111-1111-4111-8111-111111111111","author_nickname":"student","body":"Testo segnalato","report_count":2,"reasons":["spam"],"is_hidden":false,"is_deleted":false}"#
    struct Call { let method: String; let path: String; let query: [URLQueryItem]; let body: Data? }
    var calls: [Call] = []
    var queueData = Data("{\"items\":[\(ModerationAPI.itemJSON)]}".utf8)
    var moderator = false
    var failedPath: String?
    var failure: Error = URLError(.notConnectedToInternet)
    var suspendQueue = false
    var pending: CheckedContinuation<Data, Never>?

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        calls.append(Call(method: method, path: path, query: query, body: body))
        if path == failedPath { throw failure }
        if path == "users/me" {
            return Data("{\"id\":\"me\",\"nickname\":\"reviewer\",\"university_id\":\"campus\",\"is_moderator\":\(moderator)}".utf8)
        }
        if path == "moderation/queue" {
            if suspendQueue { return await withCheckedContinuation { pending = $0 } }
            return queueData
        }
        return Data()
    }
}
