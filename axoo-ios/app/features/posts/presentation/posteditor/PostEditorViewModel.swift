import Foundation

/// Owns the draft and publishing requests; the host renders changes.
@MainActor
final class PostEditorViewModel {
    var onChange: () -> Void = {}
    var onPublish: () -> Void = {}
    private let service: PostEditorService
    var author: PostEditorAuthor? { service.author }
    var mediaAPI: APIClient? { service.mediaAPI }
    let mode: PostEditorMode
    private(set) var state: PostEditorState
    private let initial: PostEditorDraft
    private var isImmutable = false
    private var clientID = UUID().uuidString
    private var submittedDraft: PostEditorDraft?
    private var generation = UUID()
    private(set) var photoTask: Task<Void, Never>?
    private var photoGeneration = UUID()
    private(set) var saveTask: Task<Void, Never>?

    init(service: PostEditorService, mode: PostEditorMode, initial: PostEditorDraft = .init()) {
        self.service = service
        self.mode = mode
        self.initial = initial
        self.state = PostEditorState(draft: initial)
        self.isImmutable = mode != .create && initial.pollOptions != nil
    }

    convenience init(service: PostEditorService, post: Post) {
        self.init(service: service, mode: .edit(postID: post.id), initial:
            PostEditorDraft(body: post.body, entities: post.entities, category: post.category, media: service.mediaForEditing(post.id)))
        isImmutable = post.poll != nil
    }
    var hasUnsavedChanges: Bool {
        if state.result != nil { return false }
        return mode == .create ? state.draft.hasContent : state.draft != initial
    }
    var isBusy: Bool { state.isSaving || state.isPreparingPhoto }
    var canPublish: Bool {
        !isImmutable && state.draft.isValid && !isBusy && state.result == nil && (mode == .create || hasUnsavedChanges)
    }
    func togglePoll() {
        guard mode == .create, !isBusy else { return }
        state.draft.pollOptions = state.draft.pollOptions == nil ? [PollOptionInput(), PollOptionInput()] : nil
        state.error = nil
        onChange()
    }
    func setPollOption(_ index: Int, text: String, entities: [EmojiEntity]) {
        guard mode == .create, !isBusy, let options = state.draft.pollOptions, options.indices.contains(index) else { return }
        state.draft.pollOptions?[index] = PollOptionInput(text: text, entities: entities)
        state.error = nil
        onChange()
    }
    func addPollOption() {
        guard mode == .create, !isBusy, let options = state.draft.pollOptions, options.count < 4 else { return }
        state.draft.pollOptions?.append(PollOptionInput())
        onChange()
    }
    func removePollOption(_ index: Int) {
        guard mode == .create, !isBusy, let options = state.draft.pollOptions, options.count > 2, options.indices.contains(index) else { return }
        state.draft.pollOptions?.remove(at: index)
        onChange()
    }
    func setBody(_ text: String, entities: [EmojiEntity] = []) {
        guard !isBusy else { return }
        state.draft.body = text
        state.draft.entities = entities
        state.error = nil
        onChange()
    }
    func setCategory(_ value: String) {
        guard !isBusy, value.isEmpty || PostEditorDraft.categories.contains(value) else { return }
        state.draft.category = value
        state.error = nil
        onChange()
    }
    func setMedia(_ media: PostEditorMedia?) {
        guard !isBusy else { return }
        if state.draft.media != nil && media == nil { state.draft.mediaRemoved = true }
        if media != nil { state.draft.mediaRemoved = false }
        state.draft.media = media
        state.error = nil
        onChange()
    }
    func preparePhoto(load: @escaping () async throws -> Data) {
        guard !state.isSaving else { return }
        photoTask?.cancel()
        let token = UUID(); photoGeneration = token
        state.isPreparingPhoto = true
        state.error = nil
        onChange()
        photoTask = Task { [weak self] in
            guard let self else { return }
            defer {
                if photoGeneration == token { state.isPreparingPhoto = false; photoTask = nil; onChange() }
            }
            do {
                let data = try await load()
                try Task.checkCancellation()
                let photo = try await PhotoProcessor.prepare(data)
                try Task.checkCancellation()
                guard photoGeneration == token else { return }
                state.draft.media = photo
                state.draft.mediaRemoved = false
            } catch {
                guard photoGeneration == token, !Task.isCancelled else { return }
                state.error = (error as? PhotoFailure)?.errorDescription ?? "Impossibile aprire la foto selezionata."
            }
        }
    }
    func publishDraft() {
        guard canPublish else { return }
        onPublish()
        let draft = state.draft.normalized
        if let submittedDraft, submittedDraft != draft { clientID = UUID().uuidString }
        submittedDraft = draft
        let requestID = clientID
        let token = UUID(); generation = token
        state.isSaving = true
        state.error = nil
        onChange()
        saveTask = Task { [weak self] in
            guard let self else { return }
            defer {
                if generation == token { state.isSaving = false; saveTask = nil; onChange() }
            }
            do {
                guard draft.isValid else { throw PostEditorValidationError.invalidBody }
                let result = try await service.save(draft, mode: mode, clientID: requestID)
                try Task.checkCancellation()
                guard generation == token else { return }
                state.result = result
            } catch {
                guard generation == token, !Task.isCancelled else { return }
                state.error = "Salvataggio non riuscito. La bozza è ancora qui: riprova."
            }
        }
    }
    func stop() {
        generation = UUID()
        photoGeneration = UUID()
        photoTask?.cancel()
        photoTask = nil
        state.isPreparingPhoto = false
        saveTask?.cancel()
        saveTask = nil
        state.isSaving = false
    }
}
