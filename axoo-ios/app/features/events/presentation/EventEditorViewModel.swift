import Foundation

@MainActor
final class EventEditorViewModel {
    private let events: EventsViewModel
    let event: CityEvent?
    private(set) var draft: EventDraft
    private let initial: EventDraft
    private(set) var preparingPhoto = false
    private(set) var saving = false
    private(set) var error: String?
    private var photoTask: Task<Void, Never>?
    var onChange: () -> Void = {}
    var onSaved: () -> Void = {}
    var hasUnsavedChanges: Bool { draft != initial }
    var busy: Bool { saving || preparingPhoto }

    init(events: EventsViewModel, event: CityEvent? = nil) {
        self.events = events; self.event = event
        let draft = EventDraft(event: event)
        initial = draft; self.draft = draft
    }
    func change(_ update: (inout EventDraft) -> Void) {
        guard !busy else { return }
        update(&draft); error = nil; onChange()
    }
    func preparePhoto(load: @escaping () async throws -> Data) {
        guard !busy else { return }
        preparingPhoto = true; error = nil; onChange()
        photoTask = Task { [weak self] in
            guard let self else { return }
            do {
                let data = try await load()
                try Task.checkCancellation()
                let photo = try await PhotoProcessor.prepare(data)
                try Task.checkCancellation()
                draft.photo = photo
            } catch {
                guard !Task.isCancelled else { return }
                self.error = "Impossibile aprire la locandina. Riprova."
            }
            preparingPhoto = false; photoTask = nil; onChange()
        }
    }
    func validate() -> Bool {
        error = draft.validationError(); onChange(); return error == nil
    }
    func save() {
        guard !busy, !events.saving, validate() else { return }
        saving = true; onChange()
        events.save(id: event?.id, draft: draft) { [weak self] success in
            guard let self else { return }
            saving = false
            error = success ? nil : "Salvataggio non riuscito. Controlla connessione e permesso admin, poi riprova."
            onChange()
            if success { onSaved() }
        }
    }
    func stop() { photoTask?.cancel(); photoTask = nil; onChange = {}; onSaved = {} }
}
