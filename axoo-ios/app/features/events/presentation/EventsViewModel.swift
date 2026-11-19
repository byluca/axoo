import Foundation

@MainActor
final class EventsViewModel {
    private let source: EventsSource
    private(set) var detail: CityEvent?
    private(set) var detailError = false
    private var detailTask: Task<Void, Never>?
    private var draftUploadID: String?
    private var draftUpload: EventPoster?
    private(set) var items: [CampusEvent] = []
    private(set) var cursor: String?
    private(set) var canManage = false
    private(set) var loading = false
    private(set) var saving = false
    private(set) var error = false
    private(set) var saveError: String?
    private(set) var loadTask: Task<Void, Never>?
    private(set) var saveTask: Task<Void, Never>?
    var onChange: () -> Void = {}

    init(source: EventsSource) { self.source = source }
    func refresh() { load(cursor: nil) }
    func loadMore() { if !loading, let cursor { load(cursor: cursor) } }
    private func load(cursor: String?) {
        loadTask?.cancel()
        loading = true; error = false; saveError = nil
        onChange()
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await source.page(cursor: cursor)
                guard !Task.isCancelled else { return }
                var ids = Set<String>()
                let events: [CampusEvent] = cursor == nil ? page.events : items + page.events
                items = events.filter { ids.insert($0.id).inserted }
                self.cursor = page.nextCursor
                canManage = page.canManage
            } catch {
                guard !Task.isCancelled else { return }
                self.error = true
            }
            loading = false
            onChange()
        }
    }
    func open(id: String) {
        detailTask?.cancel()
        detail = items.first { $0.id == id }?.cityEvent(canManage: canManage); detailError = false; onChange()
        detailTask = Task { [weak self] in
            guard let self else { return }
            do {
                let event = try await source.event(id: id)
                try Task.checkCancellation()
                detail = event.cityEvent(); canManage = event.canManage ?? canManage
            } catch {
                guard !Task.isCancelled else { return }
                detail = nil; detailError = true
            }
            onChange()
        }
    }
    func save(id: String?, draft: EventDraft, completion: @escaping (Bool) -> Void) {
        guard canManage, !saving, draft.validationError() == nil else { completion(false); return }
        mutate(onResult: completion) { [self] in
            var poster: EventPoster?
            if let photo = draft.photo, let data = photo.photoData {
                if draftUploadID != photo.id || draftUpload == nil {
                    draftUpload = try await source.uploadPoster(data); draftUploadID = photo.id
                }
                poster = draftUpload
            } else if let id {
                poster = try await source.event(id: id).poster
            }
            try Task.checkCancellation()
            let payload = EventInput(title: draft.title.trimmingCharacters(in: .whitespacesAndNewlines),
                location: draft.venue.trimmingCharacters(in: .whitespacesAndNewlines),
                description: draft.description.trimmingCharacters(in: .whitespacesAndNewlines),
                startsAt: draft.startsAt, poster: poster, endsAt: draft.endsAt,
                address: draft.address.trimmingCharacters(in: .whitespacesAndNewlines),
                entryInfo: draft.entryInfo.trimmingCharacters(in: .whitespacesAndNewlines))
            if let id { _ = try await source.update(id: id, input: payload) }
            else { _ = try await source.create(payload) }
            draftUpload = nil; draftUploadID = nil
            refresh()
        }
    }
    func delete(id: String) {
        guard canManage, !saving else { return }
        mutate { [self] in try await source.delete(id: id); refresh() }
    }
    private func mutate(onResult: ((Bool) -> Void)? = nil, _ action: @escaping () async throws -> Void) {
        saving = true; saveError = nil
        onChange()
        saveTask = Task { [weak self] in
            guard let self else { return }
            var success = false
            do { try await action(); try Task.checkCancellation(); success = true }
            catch {
                guard !Task.isCancelled else { return }
                let forbidden = (error as? HTTPFailure)?.status == 403
                if forbidden { canManage = false }
                if (error as? HTTPFailure)?.status == 400 { draftUpload = nil; draftUploadID = nil }
                saveError = forbidden ? "Solo gli admin possono gestire gli eventi." : "Operazione non riuscita. Riprova."
            }
            saving = false
            onChange(); onResult?(success)
        }
    }
    func stop() { detailTask?.cancel(); loadTask?.cancel(); saveTask?.cancel(); onChange = {} }
}
