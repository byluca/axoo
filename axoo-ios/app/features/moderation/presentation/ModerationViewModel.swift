import Foundation

@MainActor
final class ModerationViewModel {
    private let repository: ModerationRepository
    private(set) var items: [ModerationItem] = []
    private(set) var isLoading = false
    private(set) var isDeciding = false
    private(set) var error: String?
    private(set) var loadTask: Task<Void, Never>?
    private(set) var decisionTask: Task<Void, Never>?
    private var active = true
    var onChange: () -> Void = {}
    var isBusy: Bool { isLoading || isDeciding }

    init(repository: ModerationRepository) { self.repository = repository }

    func load() {
        guard active, !isBusy else { return }
        isLoading = true
        error = nil
        onChange()
        loadTask = Task {
            defer { if active { isLoading = false; loadTask = nil; onChange() } }
            do {
                let result = try await repository.queue()
                try Task.checkCancellation()
                guard active else { return }
                items = result
            } catch {
                guard active, !Task.isCancelled, !(error is CancellationError) else { return }
                record(error, fallback: "Impossibile caricare le segnalazioni. Tocca Aggiorna per riprovare.")
            }
        }
    }

    func decide(_ decision: ModerationDecision, item: ModerationItem, reason: AxooReportReason? = nil) {
        guard active, !isBusy, items.contains(where: { $0.id == item.id }),
              decision != .hide || (reason != nil && !item.isDeleted) else { return }
        isDeciding = true
        error = nil
        onChange()
        decisionTask = Task {
            defer { if active { isDeciding = false; decisionTask = nil; onChange() } }
            do {
                try await repository.decide(decision, item: item, reason: reason)
                try Task.checkCancellation()
                guard active else { return }
                items.removeAll { $0.id == item.id }
            } catch {
                guard active, !Task.isCancelled, !(error is CancellationError) else { return }
                record(error, fallback: "Decisione non salvata. Riprova.")
            }
        }
    }

    private func record(_ failure: Error, fallback: String) {
        if (failure as? HTTPFailure)?.status == 403 {
            items = []
            error = "Accesso riservato ai moderatori."
        } else { error = fallback }
    }

    func stop() {
        active = false
        loadTask?.cancel(); loadTask = nil
        decisionTask?.cancel(); decisionTask = nil
        isLoading = false; isDeciding = false
        items = []
        onChange = {}
    }
}
