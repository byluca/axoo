
import XCTest
@testable import Axoo

@MainActor
final class CommentsViewModelTests: XCTestCase {
    func testSentStickerCannotEnterEditingOrReplaceTheTextDraft() async throws {
        let repository = InMemoryCommentRepository(delay: 0)
        let model = makeModel(repository)
        model.start()
        await model.loadTask?.value
        let sticker = PostEditorMedia(kind: .sticker, url: "https://media.giphy.com/a.gif", previewURL: "https://media.giphy.com/a.gif",
            width: 200, height: 100, mimeType: "image/gif", provider: "giphy", providerID: "a", photoData: nil, id: "a")
        model.sendMedia(sticker)
        await model.sends.values.first?.value
        let sent = try XCTUnwrap(model.state.items.last { $0.comment.media == sticker })
        model.updateDraft(text: "Bozza da conservare", selection: NSRange(location: 3, length: 0))
        XCTAssertFalse(sent.comment.allowsEditing)
        model.beginEditing(sent.comment.id)
        XCTAssertNil(model.state.editingID)
        XCTAssertEqual(model.state.draft.text, "Bozza da conservare")
        var gif = sent.comment
        gif.media = PostEditorMedia(kind: .gif, url: sticker.url, previewURL: sticker.previewURL,
            width: sticker.width, height: sticker.height, mimeType: sticker.mimeType, provider: sticker.provider,
            providerID: sticker.providerID, photoData: nil, id: sticker.id)
        XCTAssertTrue(gif.allowsEditing)
        model.stop()
    }

    func testMediaSendsImmediatelyPreservesTextAndRetriesOneComment() async throws {
        let repository = InMemoryCommentRepository(scenario: .sendError, delay: 0)
        let model = makeModel(repository)
        model.start()
        await model.loadTask?.value
        model.updateDraft(text: "Bozza da conservare", selection: NSRange(location: 3, length: 0))
        model.reply(to: "root")
        let media = PostEditorMedia(kind: .sticker, url: "https://media.giphy.com/a.gif", previewURL: "https://media.giphy.com/a.gif",
            width: 200, height: 100, mimeType: "image/gif", provider: "giphy", providerID: "a", photoData: nil, id: "a")
        model.sendMedia(media)
        let pending = try XCTUnwrap(model.state.items.last)
        XCTAssertEqual(pending.comment.body, "")
        XCTAssertEqual(pending.comment.media, media)
        XCTAssertEqual(pending.comment.parentID, "root")
        XCTAssertEqual(model.state.draft.text, "Bozza da conservare")
        XCTAssertEqual(model.state.draft.selection.location, 3)
        XCTAssertNil(model.state.draft.replyID)
        let send = try XCTUnwrap(model.sends.values.first)
        await send.value
        let confirmed = try XCTUnwrap(model.state.items.first { $0.stableID == pending.stableID })
        XCTAssertEqual(confirmed.delivery, .sent)
        XCTAssertEqual(confirmed.comment.media, media)
        XCTAssertEqual(model.state.items.filter { $0.comment.media == media }.count, 1)
        model.stop()
    }

    func testPermanentSendRejectionPreservesCurrentDraft() async throws {
        for hasNewDraft in [false, true] {
            let repository = ControlledCommentRepository()
            repository.holdCreates = true
            let model = makeModel(repository)
            model.start(); await model.loadTask?.value
            model.updateDraft(text: "Un commento", selection: NSRange(location: 0, length: 0))
            model.send()
            let request = try XCTUnwrap(model.sends.values.first)
            await waitFor { repository.creates.count == 1 }
            if hasNewDraft { model.updateDraft(text: "Nuova bozza", selection: NSRange(location: 0, length: 0)) }
            repository.creates[0].resume(throwing: HTTPFailure(status: 403))
            await request.value
            XCTAssertEqual(model.state.draft.text, hasNewDraft ? "Nuova bozza" : "")
            XCTAssertNil(model.state.notice)
            XCTAssertTrue(model.state.items.isEmpty)
            model.stop()
        }
    }

    func testCommentHapticEventOccursOncePerAcceptedSend() async throws {
        let model = makeModel(InMemoryCommentRepository(scenario: .sendError, delay: 0))
        model.start()
        await model.loadTask?.value
        var sentEvents = 0
        model.onEvent = { if case .commentSent = $0 { sentEvents += 1 } }
        model.send() // Empty input.
        model.updateDraft(text: String(repeating: "a", count: 501), selection: NSRange(location: 0, length: 0))
        model.send() // Invalid input.
        XCTAssertEqual(sentEvents, 0)
        model.updateDraft(text: "Un commento", selection: NSRange(location: 0, length: 0))
        model.send()
        model.send() // The input was cleared by the accepted send.
        XCTAssertEqual(sentEvents, 1)
        let pending = try XCTUnwrap(model.state.items.last)
        XCTAssertEqual(model.state.followCommentID, pending.stableID)
        await model.sends.values.first?.value
        XCTAssertEqual(model.state.items.last?.delivery, .sent)
        XCTAssertEqual(sentEvents, 1, "Network retries must not repeat the send haptic")
        model.stop()
    }
    func testSendRetriesAutomaticallyWithOneStableIdentity() async throws {
        let repository = InMemoryCommentRepository(scenario: .sendError, delay: 0)
        let screen = makeModel(repository)
        screen.start()
        await screen.loadTask?.value
        screen.reply(to: "root")
        screen.updateDraft(text: "Una risposta 👩🏽‍💻", selection: NSRange(location: 0, length: 0))
        screen.send()
        let pending = try XCTUnwrap(screen.state.items.last)
        XCTAssertEqual(pending.comment.parentID, "root")
        XCTAssertNil(screen.state.followCommentID, "Replies must preserve the current scroll position")
        XCTAssertEqual(pending.delivery, .sending)
        XCTAssertEqual(screen.state.draft.text, "")
        XCTAssertEqual(screen.state.post.commentsCount, 8)
        let send = try XCTUnwrap(screen.sends.values.first)
        screen.refresh()
        await screen.loadTask?.value
        XCTAssertTrue(screen.state.items.contains { $0.stableID == pending.stableID })
        await send.value
        let confirmed = try XCTUnwrap(screen.state.items.first { $0.stableID == pending.stableID })
        XCTAssertEqual(confirmed.delivery, .sent)
        XCTAssertNotEqual(confirmed.comment.id, pending.comment.id)
        XCTAssertEqual(confirmed.comment.body, pending.comment.body)
        XCTAssertEqual(confirmed.comment.clientID, pending.comment.clientID)
        XCTAssertEqual(screen.state.post.commentsCount, 9)
        screen.refresh()
        await screen.loadTask?.value
        XCTAssertEqual(screen.state.items.filter { $0.stableID == pending.stableID }.count, 1)
        XCTAssertTrue(screen.sends.isEmpty)
        screen.stop()
    }
    func testAutomaticSendRetryKeepsTrimmedEntities() async throws {
        let screen = makeModel(InMemoryCommentRepository(scenario: .sendError, delay: 0))
        screen.start()
        await screen.loadTask?.value
        screen.updateDraft(text: "  👩🏽‍💻 😀  ", selection: NSRange(location: 0, length: 0),
                           entities: [EmojiEntity(offset: 7, length: 1, id: 1218)])
        screen.send()
        let pending = try XCTUnwrap(screen.state.items.last)
        let expected = [EmojiEntity(offset: 5, length: 1, id: 1218)]
        XCTAssertEqual(pending.comment.entities, expected)
        await screen.sends.values.first?.value
        XCTAssertEqual(screen.state.items.first { $0.stableID == pending.stableID }?.delivery, .sent)
        XCTAssertEqual(screen.state.items.first { $0.stableID == pending.stableID }?.comment.entities, expected)
        screen.stop()
    }
    func testEditingRestoresDraftReplyAndSelectionAfterCancelAndSave() async {
        let screen = makeModel(InMemoryCommentRepository(delay: 0))
        screen.start()
        await screen.loadTask?.value
        screen.reply(to: "root")
        screen.updateDraft(text: "La mia bozza", selection: NSRange(location: 3, length: 2))
        let draft = screen.state.draft
        screen.beginEditing("own")
        XCTAssertEqual(screen.state.editingID, "own")
        screen.updateDraft(text: "Una modifica annullata", selection: NSRange(location: 0, length: 0))
        screen.cancelEditing()
        XCTAssertEqual(screen.state.draft, draft)
        screen.beginEditing("own")
        screen.updateDraft(text: "Nuovo testo **letterale**", selection: NSRange(location: 0, length: 0))
        screen.send()
        await screen.editTask?.value
        XCTAssertNil(screen.state.editingID)
        XCTAssertEqual(screen.state.draft, draft)
        XCTAssertEqual(screen.item("own")?.comment.body, "Nuovo testo **letterale**")
        XCTAssertEqual(screen.item("own")?.comment.isEdited, true)
        XCTAssertEqual(screen.state.post.commentsCount, 8)
        screen.stop()
    }
    func testRefreshRecoversCommittedCommentAfterLostSendResponse() async throws {
        let repository = ControlledCommentRepository()
        repository.holdCreates = true
        let screen = makeModel(repository)
        screen.start(); await screen.loadTask?.value
        screen.updateDraft(text: "Salvato prima del timeout", selection: NSRange(location: 0, length: 0))
        screen.send()
        let pending = try XCTUnwrap(screen.state.items.last)
        let send = try XCTUnwrap(screen.sends.values.first)
        await waitFor { repository.creates.count == 1 }
        repository.creates[0].resume(throwing: URLError(.timedOut))
        XCTAssertEqual(screen.state.items.last?.delivery, .sending)

        var confirmed = testComment("server")
        confirmed.clientID = pending.comment.clientID
        confirmed.body = pending.comment.body
        repository.serverComments = [confirmed]
        var post = screen.state.post
        post.commentsCount = 9
        repository.serverPost = post
        screen.refresh(); await screen.loadTask?.value
        await send.value
        XCTAssertEqual(screen.state.items.map { $0.comment.id }, ["server"])
        XCTAssertEqual(screen.state.items.first?.stableID, pending.stableID)
        XCTAssertEqual(screen.state.items.first?.delivery, .sent)
        XCTAssertEqual(screen.state.post.commentsCount, 9)
        XCTAssertTrue(screen.sends.isEmpty)
        screen.refresh(); await screen.loadTask?.value
        XCTAssertEqual(screen.state.post.commentsCount, 9)
        screen.stop()
    }

    func testRefreshAcknowledgementSurvivesLateSendSuccessOrFailureWithoutDoubleCounting() async throws {
        for fails in [false, true] {
            let repository = ControlledCommentRepository()
            repository.holdCreates = true
            let screen = makeModel(repository)
            screen.start(); await screen.loadTask?.value
            screen.updateDraft(text: "Inviato", selection: NSRange(location: 0, length: 0))
            screen.send()
            let pending = try XCTUnwrap(screen.state.items.last)
            let send = try XCTUnwrap(screen.sends.values.first)
            await waitFor { repository.creates.count == 1 }
            var confirmed = testComment("server")
            confirmed.clientID = pending.comment.clientID
            confirmed.body = "Testo aggiornato sul server"
            confirmed.score = 4
            repository.serverComments = [confirmed]
            screen.refresh(); await screen.loadTask?.value
            XCTAssertEqual(screen.state.items.first?.delivery, .sent)
            XCTAssertEqual(screen.state.post.commentsCount, 9)
            if fails { repository.creates[0].resume(throwing: URLError(.networkConnectionLost)) }
            else { repository.creates[0].resume(returning: testComment("server")) }
            await send.value
            XCTAssertEqual(screen.state.items.count, 1)
            XCTAssertEqual(screen.state.items.first?.comment, confirmed)
            XCTAssertEqual(screen.state.items.first?.delivery, .sent)
            XCTAssertEqual(screen.state.post.commentsCount, 9)
            screen.refresh(); await screen.loadTask?.value
            XCTAssertEqual(screen.state.post.commentsCount, 9)
            screen.stop()
        }
    }
    func testEditFailureRetainsEditedTextAndCanRetry() async {
        let screen = makeModel(InMemoryCommentRepository(scenario: .editError, delay: 0))
        screen.start(); await screen.loadTask?.value
        screen.beginEditing("own")
        screen.updateDraft(text: "Modifica da conservare", selection: NSRange(location: 4, length: 0))
        screen.send(); await screen.editTask?.value
        XCTAssertEqual(screen.state.editingID, "own")
        XCTAssertEqual(screen.state.draft.text, "Modifica da conservare")
        XCTAssertFalse(screen.state.isSaving)
        XCTAssertNotNil(screen.state.notice)
        screen.send(); await screen.editTask?.value
        XCTAssertNil(screen.state.editingID)
        XCTAssertEqual(screen.item("own")?.comment.body, "Modifica da conservare")
        screen.stop()
    }
    func testFailedVoteRollsBackAndOwnCommentDeletionPreservesReplies() async throws {
        let screen = makeModel(InMemoryCommentRepository(scenario: .voteError, delay: 0))
        screen.start(); await screen.loadTask?.value
        let original = try XCTUnwrap(screen.item("root"))
        screen.voteComment("root", direction: .up)
        XCTAssertEqual(screen.item("root")?.comment.score, original.comment.score + 1)
        await screen.mutations["root"]?.value
        XCTAssertEqual(screen.item("root")?.comment.vote, original.comment.vote)
        XCTAssertEqual(screen.item("root")?.comment.score, original.comment.score)
        screen.deleteComment("root")
        XCTAssertNil(screen.mutations["root"], "Cannot delete another user's comment")
        screen.deleteComment("own"); await screen.mutations["own"]?.value
        XCTAssertEqual(screen.item("own")?.comment.isDeleted, true)
        XCTAssertEqual(screen.item("own")?.comment.body, "")
        XCTAssertNotNil(screen.item("own-child"))
        XCTAssertEqual(screen.state.post.commentsCount, 7)
        screen.stop()
    }
    func testLateRefreshCannotReplaceTheLatestResponse() async {
        let repository = ControlledCommentRepository()
        repository.holdLoads = true
        let screen = makeModel(repository)
        screen.start()
        let hotTask = screen.loadTask
        await waitFor { repository.loads.count == 1 }
        screen.refresh()
        let newTask = screen.loadTask
        await waitFor { repository.loads.count == 2 }
        repository.loads[1].resume(returning: [testComment("new")])
        await newTask?.value
        repository.loads[0].resume(returning: [testComment("hot")])
        await hotTask?.value
        XCTAssertEqual(screen.state.items.map { $0.comment.id }, ["new"])
        screen.stop()
    }
    func testRefreshStartedBeforeSendCannotRemoveAcknowledgedComment() async throws {
        let repository = ControlledCommentRepository()
        let screen = makeModel(repository)
        screen.start(); await screen.loadTask?.value
        repository.holdLoads = true
        screen.refresh()
        let refresh = screen.loadTask
        await waitFor { repository.loads.count == 1 }
        screen.updateDraft(text: "Appena inviato", selection: NSRange(location: 0, length: 0))
        screen.send()
        let stableID = try XCTUnwrap(screen.state.items.last?.stableID)
        await screen.sends.values.first?.value
        repository.loads[0].resume(returning: [])
        await refresh?.value
        XCTAssertEqual(screen.state.items.filter { $0.stableID == stableID }.count, 1)
        XCTAssertEqual(screen.state.items.last?.delivery, .sent)
        screen.stop()
    }
    func testStoppedLoadCannotRepopulateScreenAndDraftSurvives() async {
        let repository = ControlledCommentRepository()
        repository.holdLoads = true
        let screen = makeModel(repository)
        screen.start()
        let task = screen.loadTask
        await waitFor { repository.loads.count == 1 }
        screen.updateDraft(text: "Bozza", selection: NSRange(location: 2, length: 0))
        screen.stop()
        repository.loads[0].resume(returning: [testComment("late")])
        await task?.value
        XCTAssertTrue(screen.state.items.isEmpty)
        XCTAssertEqual(screen.state.draft.text, "Bozza")
    }
    func testReturningResumesPendingSendAndIgnoresCanceledResponse() async throws {
        let repository = ControlledCommentRepository()
        repository.holdCreates = true
        let screen = makeModel(repository)
        screen.start(); await screen.loadTask?.value
        screen.updateDraft(text: "Riprova dopo il ritorno", selection: NSRange(location: 0, length: 0))
        screen.send()
        let stableID = try XCTUnwrap(screen.state.items.last?.stableID)
        let old = try XCTUnwrap(screen.sends.values.first)
        await waitFor { repository.creates.count == 1 }
        screen.stop()
        XCTAssertEqual(screen.state.items.last?.delivery, .sending)
        screen.start()
        let retry = try XCTUnwrap(screen.sends.values.first)
        await waitFor { repository.creates.count == 2 }
        let response = testComment("server")
        repository.creates[0].resume(returning: response)
        await old.value
        XCTAssertEqual(screen.sends.count, 1)
        repository.creates[1].resume(returning: response)
        await retry.value
        XCTAssertEqual(screen.state.items.last?.stableID, stableID)
        XCTAssertEqual(screen.state.items.last?.delivery, .sent)
        XCTAssertEqual(screen.state.post.commentsCount, 9)
        screen.stop()
    }
    func testPostChangesReachOwnerAndBlockedParentKeepsOtherReplies() async {
        let screen = makeModel(InMemoryCommentRepository(delay: 0))
        screen.start(); await screen.loadTask?.value
        var changed: Post?
        screen.onPostChange = { changed = $0 }
        screen.votePost(.up)
        XCTAssertEqual(changed?.score, 43)
        screen.block("root-user"); await screen.actionTask?.value
        XCTAssertEqual(screen.item("root")?.comment.isDeleted, true)
        XCTAssertNotNil(screen.item("reply"))
        screen.refresh(); await screen.loadTask?.value
        XCTAssertEqual(screen.item("root")?.comment.isDeleted, true)
        screen.stop()
    }
    private func makeModel(_ repository: CommentRepository) -> CommentsViewModel {
        CommentsViewModel(post: Post(id: "post", nickname: "Autore", campus: "UniBa", timeLabel: "ora", body: "Test", score: 42, commentsCount: 8), repository: repository)
    }
    private func waitFor(_ predicate: () -> Bool) async {
        for _ in 0..<100 {
            if predicate() { return }
            await Task.yield()
        }
        XCTAssertTrue(predicate())
    }
}

@MainActor
private final class ControlledCommentRepository: CommentRepository {
    let access = PostDetailAccess(userID: "me", postAuthorID: "other")
    let backing = InMemoryCommentRepository(scenario: .empty, delay: 0)
    var holdLoads = false
    var holdCreates = false
    var serverComments: [Comment]?
    var serverPost: Post?
    var loads: [CheckedContinuation<[Comment], Never>] = []
    var creates: [CheckedContinuation<Comment, Error>] = []
    func post(postID: String) async throws -> Post? { serverPost }
    func comments(postID: String) async throws -> [Comment] {
        if holdLoads { return await withCheckedContinuation { loads.append($0) } }
        if let serverComments = serverComments { return serverComments }
        return try await backing.comments(postID: postID)
    }
    func create(postID: String, clientID: String, body: String, parentID: String?, entities: [EmojiEntity] = [], media: PostEditorMedia? = nil) async throws -> Comment {
        if holdCreates { return try await withCheckedThrowingContinuation { creates.append($0) } }
        return try await backing.create(postID: postID, clientID: clientID, body: body, parentID: parentID, entities: entities, media: media)
    }
    func edit(postID: String, commentID: String, body: String, entities: [EmojiEntity] = []) async throws -> Comment { try await backing.edit(postID: postID, commentID: commentID, body: body, entities: entities) }
    func delete(postID: String, commentID: String) async throws { try await backing.delete(postID: postID, commentID: commentID) }
    func vote(postID: String, commentID: String, vote: Comment.Vote) async throws -> CommentVoteResult { try await backing.vote(postID: postID, commentID: commentID, vote: vote) }
    func report(postID: String, target: PostReportTarget, reason: String) async throws {}
    func block(userID: String) async throws {}
    func deletePost(postID: String) async throws {}
}

private func testComment(_ id: String) -> Comment {
    Comment(id: id, postID: "post", userID: "me", nickname: "Tu", body: "Test", createdAt: Date())
}

