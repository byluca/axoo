import XCTest
import UIKit
@testable import Axoo

@MainActor
final class EventsTests: XCTestCase {
    func testReadOnlyUsersCannotPublishOrDelete() async {
        let source = EventSourceStub()
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        var saved = true
        model.save(id: nil, draft: draft()) { saved = $0 }; model.delete(id: "one")
        await model.saveTask?.value
        XCTAssertFalse(model.canManage)
        XCTAssertEqual(source.writes, 0)
        XCTAssertFalse(saved)
    }
    func testPaginationKeepsExistingEventsOnFailureAndRemovesDuplicates() async {
        let source = EventSourceStub()
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        source.failure = true
        model.loadMore(); await model.loadTask?.value
        XCTAssertTrue(model.error)
        XCTAssertEqual(model.items.map(\.id), ["one"])
        source.failure = false
        source.response = EventsPage(events: [source.event, CampusEvent(id: "two", title: "Altro", location: "Campus", description: "", startsAt: "2027-01-01T12:00:00Z")], nextCursor: nil, canManage: false)
        model.loadMore(); await model.loadTask?.value
        XCTAssertEqual(model.items.map(\.id), ["one", "two"])
        XCTAssertNil(model.cursor)
    }
    func testPermissionRevocationRejectsCreationAndRemovesControls() async {
        let source = EventSourceStub()
        source.response = EventsPage(events: [], nextCursor: nil, canManage: true)
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        source.forbidden = true
        model.save(id: nil, draft: draft()) { _ in }; await model.saveTask?.value
        XCTAssertFalse(model.canManage)
        XCTAssertFalse(model.saving)
        XCTAssertNotNil(model.saveError)
    }
    func testInvalidDraftDoesNotSendAndRepeatedSaveIsIgnored() async {
        let source = EventSourceStub()
        source.response = EventsPage(events: [], nextCursor: nil, canManage: true)
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        var saved = true
        model.save(id: nil, draft: EventDraft()) { saved = $0 }
        XCTAssertFalse(saved); XCTAssertEqual(source.writes, 0)
        model.save(id: nil, draft: draft()) { saved = $0 }
        model.save(id: nil, draft: draft()) { _ in }
        await model.saveTask?.value
        XCTAssertEqual(source.writes, 1); XCTAssertTrue(saved)
        model.stop()
    }
    func testPosterMetadataIsDecodedAndOptionalForOlderEvents() throws {
        let data = Data(#"{"id":"event","title":"Evento","location":"Campus","description":"","starts_at":"2027-01-01T12:00:00Z","poster":{"url":"https://example.test/flyer.jpg","width":800,"height":1200,"type":"photo","mime_type":"image/jpeg"}}"#.utf8)
        let event = try APIJSON.decode(CampusEvent.self, from: data)
        XCTAssertEqual(event.poster?.height, 1200)
        XCTAssertEqual(event.poster?.mimeType, "image/jpeg")
        let old = Data(#"{"id":"event","title":"Evento","location":"Campus","description":"","starts_at":"2027-01-01T12:00:00Z"}"#.utf8)
        XCTAssertNil(try APIJSON.decode(CampusEvent.self,from:old).poster)
    }
    func testPublishingRetryReusesUploadedPoster() async throws {
        let source = EventSourceStub()
        source.response = EventsPage(events: [], nextCursor: nil, canManage: true)
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        let image = UIGraphicsImageRenderer(size: CGSize(width: 80, height: 120)).image { context in
            UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 80, height: 120))
        }
        let data = try XCTUnwrap(image.jpegData(compressionQuality: 0.85))
        var selected = draft(); selected.photo = try await PhotoProcessor.prepare(data)
        source.createFails = true
        var saved = true
        model.save(id: nil, draft: selected) { saved = $0 }; await model.saveTask?.value
        XCTAssertFalse(saved); XCTAssertEqual(source.uploads, 1)
        source.createFails = false
        model.save(id: nil, draft: selected) { saved = $0 }; await model.saveTask?.value
        XCTAssertTrue(saved); XCTAssertEqual(source.uploads, 1); XCTAssertNotNil(source.lastPoster)
        model.stop()
    }
    func testOriginalEditorUpdatesAllFieldsAndKeepsExistingPoster() async {
        let source = EventSourceStub()
        source.response = EventsPage(events: [], nextCursor: nil, canManage: true)
        let model = EventsViewModel(source: source)
        model.refresh(); await model.loadTask?.value
        var draft = EventDraft()
        draft.title = "Titolo"; draft.description = "Descrizione"; draft.venue = "Locale"
        draft.address = "Via Roma 1"; draft.entryInfo = "Gratuito"; draft.coverUrl = source.existingPoster.url
        var saved = false
        model.save(id: "one", draft: draft) { saved = $0 }; await model.saveTask?.value
        XCTAssertTrue(saved)
        XCTAssertEqual(source.updatedID, "one")
        XCTAssertEqual(source.lastInput?.poster, source.existingPoster)
        XCTAssertEqual(source.lastInput?.address, "Via Roma 1")
        XCTAssertEqual(source.lastInput?.endsAt, draft.endsAt)
        XCTAssertEqual(source.lastInput?.entryInfo, "Gratuito")
        XCTAssertEqual(source.uploads, 0)
        model.stop()
    }
    func testLegacyEventDoesNotInventPublisher() {
        let source = EventSourceStub()
        let event = source.event.cityEvent()
        XCTAssertNil(event.organizerId)
        XCTAssertEqual(event.organizerName, "Organizzatore")
        XCTAssertEqual(event.endsAt, "")
    }
    func testEditorKeepsPublishVisibleAndLastFieldReachableOnSmallScreens() throws {
        for size in [CGSize(width: 320, height: 568), CGSize(width: 390, height: 844)] {
            let source = EventSourceStub()
            let events = EventsViewModel(source: source)
            let editor = EventEditorHost(events: events)
            let window = UIWindow(frame: CGRect(origin: .zero, size: size))
            window.rootViewController = AxooNavigationController(rootViewController: editor)
            window.makeKeyAndVisible()
            defer { window.isHidden = true; editor.model.stop(); events.stop() }
            window.layoutIfNeeded(); editor.view.layoutIfNeeded(); editor.view.layoutIfNeeded()
            let views = descendants(editor.view)
            let publish = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "event-save" })
            let footer = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "event-editor-footer" })
            let entry = try XCTUnwrap(views.first { $0.accessibilityIdentifier == "event-entry" })
            let originalFrame = publish.convert(publish.bounds, to: editor.view)
            XCTAssertTrue(editor.view.bounds.contains(originalFrame))
            XCTAssertFalse(publish.isDescendant(of: editor.scrollView))
            let maxOffset = max(0, editor.scrollView.contentSize.height + editor.scrollView.contentInset.bottom - editor.scrollView.bounds.height)
            editor.scrollView.setContentOffset(CGPoint(x: 0, y: maxOffset), animated: false)
            editor.view.layoutIfNeeded()
            XCTAssertEqual(publish.convert(publish.bounds, to: editor.view).minY, originalFrame.minY, accuracy: 0.5)
            XCTAssertLessThanOrEqual(entry.convert(entry.bounds, to: editor.view).maxY, footer.frame.minY + 1)
            XCTAssertEqual(source.writes, 0)
        }
    }
    func testEmptyPosterUsesSharedTemplateIcon() throws {
        let events = EventsViewModel(source: EventSourceStub())
        let editor = EventEditorHost(events: events)
        editor.loadViewIfNeeded()
        defer { editor.model.stop(); events.stop() }
        let cover = try XCTUnwrap(descendants(editor.view).first { $0.accessibilityIdentifier == "event-cover-preview" })
        let icon = try XCTUnwrap(descendants(cover).compactMap { $0 as? AxooIconView }.first)
        let image = try XCTUnwrap(icon.image)
        XCTAssertEqual(image.renderingMode, .alwaysTemplate)
        XCTAssertEqual(icon.contentMode, .scaleAspectFit)
        XCTAssertEqual(icon.tintColor, AxooTheme.textSecondary)
    }
    func testSelectedPosterAppearsBeforePublishing() async throws {
        let source = EventSourceStub()
        let events = EventsViewModel(source: source)
        let editor = EventEditorHost(events: events)
        editor.loadViewIfNeeded()
        defer { editor.model.stop(); events.stop() }
        let data = try XCTUnwrap(UIGraphicsImageRenderer(size: CGSize(width: 80, height: 120)).image { context in
            UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 80, height: 120))
        }.jpegData(compressionQuality: 0.85))
        let selected = try await PhotoProcessor.prepare(data)
        editor.model.change { $0.photo = selected }
        let cover = try XCTUnwrap(descendants(editor.view).first { $0.accessibilityIdentifier == "event-cover-preview" })
        let image = try XCTUnwrap(descendants(cover).compactMap { $0 as? PostMediaImageView }.first)
        XCTAssertFalse(image.isHidden); XCTAssertNotNil(image.image)
        XCTAssertEqual(source.uploads, 0); XCTAssertEqual(source.writes, 0)
    }
    private func descendants(_ view: UIView) -> [UIView] { view.subviews.flatMap { [$0] + descendants($0) } }
    private func draft() -> EventDraft {
        var value = EventDraft()
        value.title = "Evento"; value.description = "Contatti"; value.venue = "Campus"
        value.address = "Via Roma"; value.entryInfo = "Libero"; value.coverUrl = "https://example.test/poster.jpg"
        return value
    }
}

@MainActor
private final class EventSourceStub: EventsSource {
    let event = CampusEvent(id: "one", title: "Evento", location: "Campus", description: "", startsAt: "2027-01-01T12:00:00Z")
    let existingPoster = EventPoster(url: "https://example.test/old.jpg", width: 80, height: 120, type: "photo", mimeType: "image/jpeg")
    var lastInput: EventInput?
    var updatedID: String?
    func event(id: String) async throws -> CampusEvent {
        var item = event; item.poster = existingPoster; return item
    }
    func update(id: String, input: EventInput) async throws -> CampusEvent {
        updatedID = id; lastInput = input; return event
    }
    var response: EventsPage?
    var failure = false
    var forbidden = false
    var writes = 0
    var uploads = 0
    var createFails = false
    var lastPoster: EventPoster?
    func page(cursor: String?) async throws -> EventsPage {
        if failure { throw URLError(.notConnectedToInternet) }
        return response ?? EventsPage(events: [event], nextCursor: "next", canManage: false)
    }
    func create(_ input: EventInput) async throws -> CampusEvent {
        if forbidden { throw HTTPFailure(status: 403) }
        if createFails { throw URLError(.notConnectedToInternet) }
        writes += 1
        lastPoster = input.poster
        return event
    }
    func uploadPoster(_ data: Data) async throws -> EventPoster {
        uploads += 1
        return EventPoster(url: "https://example.test/poster.jpg", width: 80, height: 120, type: "photo", mimeType: "image/jpeg")
    }
    func delete(id: String) async throws { writes += 1 }
}
