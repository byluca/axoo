import XCTest
@testable import Axoo

@MainActor
final class PostEditorViewModelTests: XCTestCase {
    func testPollCreationLimitsAndValidation() {
        let editor = PostEditorViewModel(service: EditorServiceSpy(), mode: .create)
        editor.setBody("Domanda")
        editor.togglePoll()
        XCTAssertFalse(editor.canPublish)
        XCTAssertEqual(editor.state.draft.pollOptions?.count, 2)
        editor.removePollOption(0)
        XCTAssertEqual(editor.state.draft.pollOptions?.count, 2)
        editor.setPollOption(0, text: "Prima", entities: [])
        editor.setPollOption(1, text: "Seconda", entities: [])
        XCTAssertTrue(editor.canPublish)
        for _ in 0..<4 { editor.addPollOption() }
        XCTAssertEqual(editor.state.draft.pollOptions?.count, 4)
        XCTAssertFalse(editor.canPublish)
        editor.togglePoll()
        XCTAssertNil(editor.state.draft.pollOptions)
        XCTAssertTrue(editor.canPublish)
    }
    func testPublishedPollCannotBeEditedOrPublishedAgain() throws {
        let post = Post(id: "poll-post", nickname: "Alice", campus: "", timeLabel: "ora", body: "Domanda",
                                   poll: try PollFixtures.poll(voted: false))
        let service = EditorServiceSpy()
        let editor = PostEditorViewModel(service: service, post: post)
        editor.setBody("Modifica")
        editor.togglePoll()
        editor.publishDraft()
        XCTAssertFalse(editor.canPublish)
        XCTAssertTrue(service.requests.isEmpty)
        XCTAssertEqual(PostEditorResult(postID: post.id, draft: PostEditorDraft(body: "Modifica")).applying(to: post), post)
    }
    func testInvalidDraftNeverReachesTheService() async {
        let service = EditorServiceSpy()
        let editor = PostEditorViewModel(service: service, mode: .create)
        for text in ["", " \n ", String(repeating: "a", count: 501)] {
            editor.setBody(text)
            editor.publishDraft()
            await editor.saveTask?.value
            XCTAssertEqual(editor.state.draft.body, text)
            XCTAssertNil(editor.state.result)
            XCTAssertFalse(editor.state.isSaving)
        }
        XCTAssertTrue(service.requests.isEmpty)
    }

    func testCodePointsAndTextRequirementMatchAndroid() {
        XCTAssertEqual(PostEditorDraft(body: "👨‍👩‍👧‍👦").characterCount, 7)
        XCTAssertEqual(PostEditorDraft(body: "🇮🇹").characterCount, 2)
        XCTAssertEqual(PostEditorDraft(body: "e\u{301}").characterCount, 2)
        XCTAssertTrue(PostEditorDraft(body: String(repeating: "a", count: 500)).isValid)
        XCTAssertFalse(PostEditorDraft(body: String(repeating: "a", count: 501)).isValid)
        XCTAssertFalse(PostEditorDraft(body: " \n", category: "Meme").isValid)
    }
    func testUnchangedEditIsDisabledButChangingOnlyCategoryCanSave() async {
        let service = EditorServiceSpy()
        let initial = PostEditorDraft(body: "Testo", category: "Meme")
        let editor = PostEditorViewModel(service: service, mode: .edit(postID: "post"), initial: initial)
        XCTAssertFalse(editor.hasUnsavedChanges)
        XCTAssertFalse(editor.canPublish)
        editor.setCategory("Esami")
        XCTAssertTrue(editor.canPublish)
        editor.publishDraft()
        await editor.saveTask?.value
        XCTAssertEqual(editor.state.result?.draft.category, "Esami")
        XCTAssertFalse(editor.hasUnsavedChanges)
    }
    func testRetryPreservesDraftAndReusesIDWhileDoubleTapIsIgnored() async {
        let service = EditorServiceSpy()
        service.fail = true
        let editor = PostEditorViewModel(service: service, mode: .create)
        editor.setBody("  Caffè ☕️  ")
        editor.publishDraft()
        editor.publishDraft()
        await editor.saveTask?.value
        XCTAssertEqual(service.requests.count, 1)
        XCTAssertNotNil(editor.state.error)
        XCTAssertEqual(editor.state.draft.body, "  Caffè ☕️  ")
        XCTAssertEqual(service.requests.first?.draft.body, "Caffè ☕️")
        service.fail = false
        editor.publishDraft()
        await editor.saveTask?.value
        XCTAssertEqual(service.requests.map(\.id), [service.requests[0].id, service.requests[0].id])
        XCTAssertNotNil(editor.state.result)
        XCTAssertFalse(editor.canPublish)
    }
    func testChangingFailedPayloadCreatesNewSubmissionIdentity() async {
        let service = EditorServiceSpy()
        service.fail = true
        let editor = PostEditorViewModel(service: service, mode: .create)
        editor.setBody("Prima")
        editor.publishDraft()
        await editor.saveTask?.value
        editor.setBody("Dopo")
        editor.publishDraft()
        await editor.saveTask?.value
        XCTAssertEqual(service.requests.count, 2)
        XCTAssertNotEqual(service.requests[0].id, service.requests[1].id)
    }
    func testCancelledLateResponseCannotPublishOrLoseDraft() async {
        let service = EditorServiceSpy()
        service.suspend = true
        let editor = PostEditorViewModel(service: service, mode: .create)
        editor.setBody("Bozza")
        editor.publishDraft()
        let operation = editor.saveTask
        for _ in 0..<100 { if service.continuation != nil { break }; await Task.yield() }
        XCTAssertNotNil(service.continuation)
        editor.stop()
        service.continuation?.resume()
        await operation?.value
        XCTAssertNil(editor.state.result)
        XCTAssertEqual(editor.state.draft.body, "Bozza")
        XCTAssertTrue(editor.canPublish)
    }
    func testPhotoLoadFailurePreservesExistingDraftAndAllowsRetry() async {
        let editor = PostEditorViewModel(service: EditorServiceSpy(), mode: .create)
        editor.setBody("La mia bozza")
        editor.preparePhoto { throw URLError(.cannotDecodeContentData) }
        XCTAssertTrue(editor.state.isPreparingPhoto)
        XCTAssertFalse(editor.canPublish)
        await editor.photoTask?.value
        XCTAssertEqual(editor.state.draft.body, "La mia bozza")
        XCTAssertNil(editor.state.draft.media)
        XCTAssertNotNil(editor.state.error)
        XCTAssertTrue(editor.canPublish)
    }

    func testPhotoResponseAfterCloseCannotChangeDraft() async {
        let editor = PostEditorViewModel(service: EditorServiceSpy(), mode: .create)
        editor.setBody("Bozza")
        var continuation: CheckedContinuation<Data, Never>?
        editor.preparePhoto { await withCheckedContinuation { continuation = $0 } }
        let task = editor.photoTask
        for _ in 0..<1000 { if continuation != nil { break }; await Task.yield() }
        XCTAssertNotNil(continuation)
        editor.stop()
        continuation?.resume(returning: Data([0]))
        await task?.value
        XCTAssertFalse(editor.state.isPreparingPhoto)
        XCTAssertNil(editor.state.error)
        XCTAssertNil(editor.state.draft.media)
        XCTAssertEqual(editor.state.draft.body, "Bozza")
    }

    func testApplyingEditPreservesConcurrentVotesAndComments() {
        let post = Post(id: "post", nickname: "Tu", campus: "", timeLabel: "ora", body: "Prima", score: 42, commentsCount: 8, vote: .up)
        let result = PostEditorResult(postID: "post", draft: PostEditorDraft(body: "Dopo", category: "Esami"))
        let updated = result.applying(to: post)
        XCTAssertEqual(updated.body, "Dopo")
        XCTAssertEqual(updated.score, 42)
        XCTAssertEqual(updated.commentsCount, 8)
        XCTAssertEqual(updated.vote, .up)
        XCTAssertTrue(updated.isEdited)
    }
}

@MainActor
private final class EditorServiceSpy: PostEditorService {
    struct Request { let id: String; let draft: PostEditorDraft }
    var requests: [Request] = []
    var fail = false
    var suspend = false
    var continuation: CheckedContinuation<Void, Never>?
    func save(_ draft: PostEditorDraft, mode: PostEditorMode, clientID: String) async throws -> PostEditorResult {
        requests.append(Request(id: clientID, draft: draft))
        if suspend { await withCheckedContinuation { continuation = $0 } }
        if fail { throw URLError(.notConnectedToInternet) }
        return PostEditorResult(postID: "post", draft: draft)
    }
}
