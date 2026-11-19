import Foundation

/// Owns the comment thread, draft and request lifecycle independently of UIKit.
@MainActor
final class CommentsViewModel {
    enum Event { case focusComposer, commentSent, editSubmitted, sendFailed }
    var onChange: () -> Void = {}
    var onEvent: (Event) -> Void = { _ in }
    private(set) var state: PostDetailState { didSet { onChange() } }
    var onPostChange: (Post) -> Void = { _ in }
    var onPostDeleted: (String) -> Void = { _ in }
    private let repository: CommentRepository
    var access: PostDetailAccess { repository.access }
    var isPreview: Bool { repository.isPreview }
    var ownsPost: Bool { access.userID == access.postAuthorID }
    private var previousDraft: CommentDraft?
    private var started = false
    private var generation = UUID()
    private var revisions: [String: Int] = [:]
    private var sendTokens: [String: UUID] = [:]
    private var mutationTokens: [String: UUID] = [:]
    private var voteBackups: [String: Comment] = [:]
    private var editToken = UUID()
    private var actionToken = UUID()
    private var deletedIDs = Set<String>()
    private var blockedUsers = Set<String>()
    private(set) var loadTask: Task<Void, Never>?
    private(set) var sends: [String: Task<Void, Never>] = [:]
    private(set) var mutations: [String: Task<Void, Never>] = [:]
    private(set) var editTask: Task<Void, Never>?
    private(set) var actionTask: Task<Void, Never>?
    private var reportTask: Task<Void, Error>?
    private var postVoteTask: Task<Void, Never>?
    private var pollTask: Task<Void, Never>?
    private var postRevision = 0
    private var postVoteBackup: Post?
    private var postVoteToken = UUID()

    init(post: Post, repository: CommentRepository) {
        self.repository = repository
        self.state = PostDetailState(post: post)
    }

    func start() {
        guard !started, !state.isPostDeleted else { return }
        started = true
        refresh()
        for item in state.items where item.delivery == .sending { submit(item.comment) }
    }
    func stop() {
        started = false
        generation = UUID()
        loadTask?.cancel()
        loadTask = nil
        sends.values.forEach { $0.cancel() }
        sends.removeAll()
        sendTokens.removeAll()
        mutationTokens.removeAll()
        for (id, original) in voteBackups {
            replace(id) { $0.comment.score = original.score; $0.comment.vote = original.vote }
        }
        voteBackups.removeAll()
        editToken = UUID()
        actionToken = UUID()
        mutations.values.forEach { $0.cancel() }
        mutations.removeAll()
        editTask?.cancel()
        actionTask?.cancel()
        reportTask?.cancel()
        reportTask = nil
        postVoteTask?.cancel()
        pollTask?.cancel()
        postVoteToken = UUID()
        postVoteTask = nil
        if let original = postVoteBackup {
            state.post.score = original.score; state.post.vote = original.vote; state.post.isVoting = false
            onPostChange(state.post)
        }
        postVoteBackup = nil
        postRevision += 1
        state.items = state.items.map {
            var item = $0
            item.isBusy = false
            item.isVoting = false
            return item
        }
        state.isSaving = false
        state.isPerformingAction = false
        state.isRefreshing = false
        state.isLoading = false
    }

    func synchronize(post: Post) {
        guard post.id == state.post.id, post != state.post else { return }
        state.post = post
        postRevision += 1
    }
    func applyEditorResult(_ result: PostEditorResult) {
        guard !state.isPostDeleted, state.post.poll == nil else { return }
        let updated = result.applying(to: state.post)
        synchronize(post: updated)
        onPostChange(updated)
    }
    func applyPoll(_ poll: PostPoll, postID: String) {
        guard state.post.id == postID, !state.isPostDeleted, state.post.poll != poll else { return }
        state.post.poll = poll
        state.post.canEdit = false
        postRevision += 1
        onPostChange(state.post)
    }
    func votePoll(_ optionID: String) {
        guard pollTask == nil, !state.isPostDeleted, let original = state.post.poll,
              !original.hasVoted, !original.isSending, original.options.contains(where: { $0.id == optionID }) else { return }
        let id = state.post.id
        applyPoll(original.selecting(optionID), postID: id)
        pollTask = Task {
            defer { pollTask = nil }
            do {
                let confirmed = try await repository.votePoll(postID: id, optionID: optionID)
                applyPoll(confirmed, postID: id)
            } catch {
                if state.post.poll?.isSending == true { applyPoll(original, postID: id) }
                guard !Task.isCancelled else { return }
                state.notice = "Voto non salvato. Riprova."
            }
        }
    }
    func votePost(_ direction: Post.Vote) {
        guard state.editingID == nil, !state.isPostDeleted, !state.post.isVoting else { return }
        let original = state.post
        var post = state.post
        post.toggleVote(direction)
        state.post = post
        onPostChange(post)
        postRevision += 1
        let token = UUID()
        postVoteToken = token
        postVoteBackup = original
        state.post.isVoting = true
        onPostChange(state.post)
        postVoteTask = Task {
            do {
                let result = try await repository.votePost(post, value: post.vote)
                try Task.checkCancellation()
                guard postVoteToken == token else { return }
                state.post.score = result.score; state.post.vote = result.vote
            } catch {
                guard !Task.isCancelled, postVoteToken == token else { return }
                state.post.score = original.score; state.post.vote = original.vote
                state.notice = "Voto non salvato. Riprova."
            }
            state.post.isVoting = false
            postVoteBackup = nil
            postRevision += 1
            onPostChange(state.post)
        }
    }

    func refresh() {
        guard state.editingID == nil, !state.isPostDeleted else { return }
        loadTask?.cancel()
        let request = UUID()
        generation = request
        let versions = revisions
        let postVersion = postRevision
        state.isLoading = state.items.isEmpty
        state.isRefreshing = !state.items.isEmpty
        state.loadError = nil
        loadTask = Task {
            defer { if generation == request { state.isLoading = false; state.isRefreshing = false } }
            do {
                async let updatedPost = repository.post(postID: state.post.id)
                let fetched = try await repository.comments(postID: state.post.id)
                let refreshedPost = try await updatedPost
                try Task.checkCancellation()
                guard generation == request else { return }
                var current: [String: CommentItem] = [:]
                state.items.forEach { current[$0.stableID] = $0 }
                var seen = Set<String>()
                var merged: [CommentItem] = []
                var confirmedCount = 0
                for comment in fetched where seen.insert(comment.stableID).inserted {
                    if let local = current[comment.stableID], local.delivery != .sent {
                        // The server may have committed an interrupted send. Match Android's
                        // client_id reconciliation instead of retaining the local copy.
                        merged.append(CommentItem(comment: comment))
                        if !comment.isDeleted { confirmedCount += 1 }
                    } else if let local = current[comment.stableID],
                              revisions[comment.stableID, default: 0] != versions[comment.stableID, default: 0] || local.isBusy {
                        merged.append(local)
                    } else { merged.append(CommentItem(comment: comment)) }
                }
                for local in state.items where !seen.contains(local.stableID) {
                    if local.delivery != .sent || local.isBusy || revisions[local.stableID, default: 0] != versions[local.stableID, default: 0] {
                        merged.append(local)
                    }
                }
                let blocked = Set(merged.filter { blockedUsers.contains($0.comment.userID) }.map { $0.comment.id })
                state.items = CommentThread.removing(deletedIDs.union(blocked), from: merged)
                if let post = refreshedPost, postRevision == postVersion, !state.post.isVoting, sends.isEmpty, mutations.isEmpty {
                    state.post = post
                    onPostChange(post)
                } else if confirmedCount > 0 {
                    changeCount(by: confirmedCount)
                }
            } catch {
                guard generation == request, !Task.isCancelled else { return }
                state.loadError = "Non riusciamo a caricare i commenti. Riprova."
            }
        }
    }

    func updateDraft(text: String, selection: NSRange, entities: [EmojiEntity] = []) {
        guard !state.isSaving else { return }
        let count = text.utf16.count
        let start = min(max(0, selection.location), count)
        let range = NSRange(location: start, length: min(max(0, selection.length), count - start))
        guard state.draft.text != text || state.draft.entities != entities || state.draft.selection != range else { return }
        state.draft.text = text
        state.draft.entities = entities
        state.draft.selection = range
    }
    func reply(to id: String) {
        guard state.editingID == nil, let item = item(id), item.canReply else { return }
        state.draft.replyID = id
        onEvent(.focusComposer)
    }
    func cancelReply() { state.draft.replyID = nil }
    func beginEditing(_ id: String) {
        guard state.editingID == nil, let item = item(id), item.canInteract,
              item.comment.allowsEditing, item.comment.userID == repository.access.userID else { return }
        previousDraft = state.draft
        state.editingID = id
        state.draft = CommentDraft(text: item.comment.body, entities: item.comment.entities, selection: NSRange(location: item.comment.body.utf16.count, length: 0))
        onEvent(.focusComposer)
    }
    func cancelEditing() { guard !state.isSaving else { return }; finishEditing() }
    private func finishEditing() {
        state.editingID = nil
        state.draft = previousDraft ?? CommentDraft()
        previousDraft = nil
        state.isSaving = false
    }
    func send() {
        guard state.canSend else { return }
        let document = EmojiDocument(text: state.draft.text, entities: state.draft.entities).normalized
        let body = document.text
        if let id = state.editingID { saveEdit(id, body: body, entities: document.entities); return }
        if let parent = state.draft.replyID, item(parent)?.canInteract != true {
            state.notice = "Il commento a cui rispondevi non è più disponibile."
            return
        }
        let clientID = UUID().uuidString
        let comment = Comment(id: "local-" + clientID, postID: state.post.id, clientID: clientID,
                              parentID: state.draft.replyID, userID: repository.access.userID,
                              nickname: "Tu", body: body, entities: document.entities, createdAt: Date(), avatarConfig: repository.currentUserAvatar)
        state.items.append(CommentItem(comment: comment, delivery: .sending))
        state.draft = CommentDraft()
        state.followCommentID = comment.parentID == nil ? comment.stableID : nil
        onEvent(.commentSent)
        submit(comment)
    }
    func sendMedia(_ media: PostEditorMedia) {
        guard state.editingID == nil, !state.isPostDeleted, !state.isPerformingAction,
              media.kind == .gif || media.kind == .sticker else { return }
        if let parent = state.draft.replyID, item(parent)?.canInteract != true {
            state.notice = "Il commento a cui rispondevi non è più disponibile."
            return
        }
        let clientID = UUID().uuidString
        let comment = Comment(id: "local-" + clientID, postID: state.post.id, clientID: clientID,
            parentID: state.draft.replyID, userID: access.userID, nickname: "Tu", body: "", createdAt: Date(),
            avatarConfig: repository.currentUserAvatar, media: media)
        state.items.append(CommentItem(comment: comment, delivery: .sending))
        state.draft.replyID = nil
        state.followCommentID = comment.parentID == nil ? comment.stableID : nil
        onEvent(.commentSent)
        submit(comment)
    }

    private func submit(_ pending: Comment) {
        guard let clientID = pending.clientID, sends[clientID] == nil else { return }
        let token = UUID()
        sendTokens[clientID] = token
        sends[clientID] = Task {
            defer { if sendTokens[clientID] == token { sends[clientID] = nil; sendTokens[clientID] = nil } }
            let retryDelays: [UInt64] = [500, 1_500, 3_000, 7_000, 15_000, 30_000]
            var attempt = 0
            while !Task.isCancelled {
                guard sendTokens[clientID] == token,
                      state.items.contains(where: { $0.stableID == pending.stableID && $0.delivery == .sending }) else { return }
                do {
                    guard pending.media != nil || CommentInput.isValid(pending.body) else { throw CommentValidationError.invalidBody }
                    var confirmed = try await repository.create(postID: pending.postID, clientID: clientID, body: pending.body, parentID: pending.parentID, entities: pending.entities, media: pending.media)
                    try Task.checkCancellation()
                    guard sendTokens[clientID] == token else { return }
                    // Retain the UI identity even if a response omitted client_id.
                    confirmed.clientID = clientID
                    guard let index = state.items.firstIndex(where: { $0.stableID == pending.stableID }),
                          state.items[index].delivery == .sending else { return }
                    state.items[index] = CommentItem(comment: confirmed)
                    touch(pending.stableID)
                    changeCount(by: 1)
                    return
                } catch {
                    guard !Task.isCancelled, !(error is CancellationError), sendTokens[clientID] == token,
                          state.items.contains(where: { $0.stableID == pending.stableID && $0.delivery == .sending }) else { return }
                    guard (error as? HTTPFailure)?.isPermanentClientError == true || error is CommentValidationError else {
                        do { try await Task.sleep(nanoseconds: retryDelays[attempt] * 1_000_000) }
                        catch { return }
                        attempt = min(attempt + 1, retryDelays.count - 1)
                        continue
                    }
                    onEvent(.sendFailed)
                    if pending.media != nil { state.notice = "Invio non riuscito. Riprova." }
                    state.items.removeAll { $0.stableID == pending.stableID }
                    return
                }
            }
        }
    }
    private func saveEdit(_ id: String, body: String, entities: [EmojiEntity]) {
        guard let target = item(id), target.canInteract, target.comment.allowsEditing else { return }
        if target.comment.body == body && target.comment.entities == entities { finishEditing(); return }
        onEvent(.editSubmitted)
        state.isSaving = true
        let token = UUID()
        editToken = token
        editTask = Task {
            do {
                guard CommentInput.isValid(body) else { throw CommentValidationError.invalidBody }
                let updated = try await repository.edit(postID: state.post.id, commentID: id, body: body, entities: entities)
                try Task.checkCancellation()
                guard editToken == token else { return }
                replace(id) { $0.comment = updated }
                touch(updated.stableID)
                finishEditing()
            } catch {
                guard !Task.isCancelled, editToken == token else { return }
                state.isSaving = false
                state.notice = AxooOverlayCopy.editError
            }
        }
    }
    func voteComment(_ id: String, direction: Comment.Vote) {
        guard direction != .none, state.editingID == nil, let original = item(id), original.canInteract else { return }
        var next = original.comment
        next.toggleVote(direction)
        replace(id) { $0.comment = next; $0.isBusy = true; $0.isVoting = true }
        touch(next.stableID)
        let token = UUID()
        mutationTokens[id] = token
        voteBackups[id] = original.comment
        mutations[id] = Task {
            defer {
                if mutationTokens[id] == token { mutations[id] = nil; mutationTokens[id] = nil; voteBackups[id] = nil }
            }
            do {
                let result = try await repository.vote(postID: state.post.id, commentID: id, vote: next.vote)
                try Task.checkCancellation()
                guard mutationTokens[id] == token else { return }
                replace(id) { $0.comment.score = result.score; $0.comment.vote = result.vote; $0.isBusy = false; $0.isVoting = false }
                touch(next.stableID)
            } catch {
                guard mutationTokens[id] == token else { return }
                // A canceled vote is restored locally; a later refresh obtains any committed server state.
                replace(id) { $0.comment.score = original.comment.score; $0.comment.vote = original.comment.vote; $0.isBusy = false; $0.isVoting = false }
                touch(next.stableID)
                if !Task.isCancelled { state.notice = "Voto non salvato. Riprova." }
            }
        }
    }
    func deleteComment(_ id: String) {
        guard state.editingID == nil, let target = item(id), target.canInteract,
              target.comment.userID == repository.access.userID else { return }
        replace(id) { $0.isBusy = true }
        let token = UUID()
        mutationTokens[id] = token
        mutations[id] = Task {
            defer { if mutationTokens[id] == token { mutations[id] = nil; mutationTokens[id] = nil } }
            do {
                try await repository.delete(postID: state.post.id, commentID: id)
                try Task.checkCancellation()
                guard mutationTokens[id] == token else { return }
                deletedIDs.insert(id)
                touch(target.stableID)
                state.items = CommentThread.removing([id], from: state.items)
                if state.draft.replyID == id { state.draft.replyID = nil }
                changeCount(by: -1)
            } catch {
                guard mutationTokens[id] == token else { return }
                replace(id) { $0.isBusy = false }
                if !Task.isCancelled { state.notice = AxooOverlayCopy.deleteError }
            }
        }
    }
    func submitReport(_ target: PostReportTarget, reason: String) async throws {
        try Task.checkCancellation()
        guard !state.isPerformingAction, state.editingID == nil, !state.isPostDeleted else { throw CancellationError() }
        let token = UUID()
        actionToken = token
        state.isPerformingAction = true
        let postID = state.post.id
        let operation = Task { [repository = self.repository] in
            try Task.checkCancellation()
            try await repository.report(postID: postID, target: target, reason: reason)
            try Task.checkCancellation()
        }
        reportTask = operation
        defer {
            if actionToken == token { state.isPerformingAction = false; reportTask = nil }
        }
        // Both the sheet's cancellation and stop() must invalidate the request.
        try await withTaskCancellationHandler(operation: {
            try await operation.value
        }, onCancel: {
            operation.cancel()
        })
        try Task.checkCancellation()
        guard actionToken == token else { throw CancellationError() }
    }
    func block(_ userID: String) {
        guard userID != repository.access.userID, !userID.isEmpty else { return }
        performAction {
            try await self.repository.block(userID: userID)
            try Task.checkCancellation()
            self.blockedUsers.insert(userID)
            let ids = Set(self.state.items.filter { $0.comment.userID == userID }.map { $0.comment.id })
            self.state.items = CommentThread.removing(ids, from: self.state.items)
            if userID == self.repository.access.postAuthorID { self.removePost() }
            else { self.state.notice = "Utente bloccato." }
        }
    }
    func hidePost() {
        guard !ownsPost else { return }
        performAction {
            try await self.repository.hidePost(postID: self.state.post.id)
            try Task.checkCancellation()
            self.removePost()
        }
    }
    func deletePost() {
        guard repository.access.postAuthorID == repository.access.userID else { return }
        performAction {
            try await self.repository.deletePost(postID: self.state.post.id)
            try Task.checkCancellation()
            self.removePost()
        }
    }
    private func performAction(_ action: @escaping @MainActor () async throws -> Void) {
        guard !state.isPerformingAction, state.editingID == nil else { return }
        state.isPerformingAction = true
        let token = UUID()
        actionToken = token
        actionTask = Task {
            defer { if actionToken == token { state.isPerformingAction = false } }
            do { try await action() }
            catch { if !Task.isCancelled && actionToken == token { state.notice = AxooOverlayCopy.operationError } }
        }
    }
    func clearNotice() { state.notice = nil }
    func item(_ id: String) -> CommentItem? { state.items.first { $0.comment.id == id } }
    private func replace(_ id: String, _ update: (inout CommentItem) -> Void) {
        guard let index = state.items.firstIndex(where: { $0.comment.id == id }) else { return }
        update(&state.items[index])
    }
    private func touch(_ key: String) { revisions[key, default: 0] += 1 }
    private func changeCount(by delta: Int) {
        postRevision += 1
        state.post.commentsCount = max(0, state.post.commentsCount + delta)
        onPostChange(state.post)
    }
    private func removePost() { state.isPostDeleted = true; onPostDeleted(state.post.id) }
}
