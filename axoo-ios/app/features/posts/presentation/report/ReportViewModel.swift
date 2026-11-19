import Foundation

enum AxooReportReason: String, CaseIterable {
    case spam, harassment, hate, sexualContent = "sexual_content", violence, personalInformation = "personal_information", other
    var title: String {
        switch self {
        case .spam: return "Spam o pubblicità"
        case .harassment: return "Bullismo o molestie"
        case .hate: return "Odio o discriminazione"
        case .sexualContent: return "Contenuto sessuale"
        case .violence: return "Violenza o minacce"
        case .personalInformation: return "Informazioni personali"
        case .other: return "Altro"
        }
    }
}

@MainActor
final class ReportViewModel {
    private let submit: (AxooReportReason) async throws -> Void
    private(set) var selected: AxooReportReason?
    private(set) var isSubmitting = false
    private(set) var error: String?
    private(set) var isComplete = false
    private(set) var task: Task<Void, Never>?
    var onChange: () -> Void = {}
    private var generation = UUID()
    init(submit: @escaping (AxooReportReason) async throws -> Void) { self.submit = submit }
    func select(_ reason: AxooReportReason) {
        guard !isSubmitting, !isComplete else { return }
        selected = reason
        onChange()
    }
    @discardableResult
    func send() -> Bool {
        guard !isSubmitting, !isComplete, let selected else { return false }
        let token = UUID(); generation = token
        isSubmitting = true; error = nil
        onChange()
        task = Task { [weak self] in
            guard let self else { return }
            defer { if generation == token { isSubmitting = false; task = nil; onChange() } }
            do {
                try await submit(selected)
                try Task.checkCancellation()
                guard generation == token else { return }
                isComplete = true
            } catch {
                guard !Task.isCancelled, generation == token else { return }
                self.error = AxooOverlayCopy.reportError
            }
        }
        return true
    }
    func stop() { generation = UUID(); task?.cancel(); task = nil; isSubmitting = false }
}
