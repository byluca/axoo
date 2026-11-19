import Foundation

@MainActor
final class StickerCreatorViewModel {
    var onChange: () -> Void = {}
    var onCreated: (PostEditorMedia) -> Void = { _ in }
    var onError: (String) -> Void = { _ in }
    private(set) var isSaving = false
    private let repository: PersonalStickerRepository
    private var task: Task<Void, Never>?
    init(api: APIClient) { repository = PersonalStickerRepository(api: api) }
    deinit { task?.cancel() }

    func createSticker(_ png: Data) { perform { [repository] in try await repository.create(png) } }
    func saveSticker(_ sticker: PostEditorMedia) { perform { [repository] in try await repository.save(sticker) } }

    private func perform(_ operation: @escaping @MainActor () async throws -> PostEditorMedia) {
        guard !isSaving else { return }
        isSaving = true
        onChange()
        task = Task { [weak self] in
            do {
                let sticker = try await operation()
                guard !Task.isCancelled, let self else { return }
                isSaving = false
                onChange()
                onCreated(sticker)
            } catch {
                guard !Task.isCancelled, let self else { return }
                isSaving = false
                onChange()
                onError((error as? HTTPFailure)?.status == 409 ? "La tua raccolta contiene già 100 sticker" : "Sticker non salvato, riprova")
            }
        }
    }
    func cancel() { task?.cancel() }
}
