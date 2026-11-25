import Foundation

/// Owns search, pagination and requests, independently of the collection view.
@MainActor
final class RemoteMediaViewModel {
    enum Change { case reset, append(Range<Int>), status, library }
    var onChange: (Change) -> Void = { _ in }
    let kind: PostEditorMedia.Kind
    private let repository: RemoteMediaRepository?
    private let personalRepository: PersonalStickerRepository?
    private(set) var personalItems: [PostEditorMedia] = []
    private var libraryTask: Task<Void, Never>?
    private(set) var items: [PostEditorMedia] = []
    private(set) var query = ""
    private(set) var isLoading = false
    private(set) var hasError = false
    private var nextOffset: Int? = 0
    private var generation = UUID()
    private var active = true
    private(set) var searchTask: Task<Void, Never>?
    private(set) var loadTask: Task<Void, Never>?

    init(repository: RemoteMediaRepository?, kind: PostEditorMedia.Kind, personalRepository: PersonalStickerRepository? = nil) {
        self.repository = repository
        self.personalRepository = personalRepository
        self.kind = kind
    }

    func loadPersonalStickers() {
        guard kind == .sticker, let personalRepository else { return }
        libraryTask?.cancel()
        libraryTask = Task { [weak self] in
            do {
                let values = try await personalRepository.list()
                guard !Task.isCancelled, let self, active else { return }
                personalItems = values
                onChange(.library)
            } catch { /* AUP and KLIPY remain available when the personal library is offline. */ }
        }
    }

    func search(_ text: String, immediately: Bool = false) {
        guard active else { return }
        searchTask?.cancel()
        searchTask = nil
        let value = text
        if immediately { applySearch(value); return }
        // Reject the old response as soon as the user changes the search.
        loadTask?.cancel()
        generation = UUID()
        isLoading = false
        searchTask = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: 350_000_000) }
            catch { return }
            guard let self, active, !Task.isCancelled else { return }
            searchTask = nil
            applySearch(value)
        }
    }

    private func applySearch(_ value: String) {
        query = value
        resetAndLoad()
    }

    private func resetAndLoad() {
        loadTask?.cancel()
        generation = UUID()
        isLoading = false
        hasError = false
        items.removeAll()
        nextOffset = 0
        onChange(.reset)
        loadNextPage()
    }

    func loadNextPage() {
        guard active, searchTask == nil,
              !isLoading, let offset = nextOffset, let repository else { return }
        isLoading = true
        hasError = false
        onChange(.status)
        let token = generation
        let search = query
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await repository.load(kind: kind, query: search, offset: offset)
                guard active, !Task.isCancelled, generation == token else { return }
                let first = items.count
                items.append(contentsOf: page.items)
                nextOffset = page.nextOffset
                isLoading = false
                loadTask = nil
                onChange(.append(first..<items.count))
            } catch {
                guard active, !Task.isCancelled, generation == token else { return }
                isLoading = false
                hasError = true
                loadTask = nil
                onChange(.status)
            }
        }
    }

    func stop() {
        active = false
        generation = UUID()
        searchTask?.cancel()
        loadTask?.cancel()
        libraryTask?.cancel()
        searchTask = nil
        loadTask = nil
        isLoading = false
        onChange = { _ in }
    }

    func rememberSelection(_ media: PostEditorMedia) { repository?.rememberSelection(media) }
}
