import Foundation

@MainActor
final class AvatarEditorViewModel {
    private let seed: String
    private let initial: [String: String]
    private let onSave: ([String: String]) -> Void
    private(set) var draft: [String: String]
    private(set) var isSaving = false
    var onChange: () -> Void = {}
    var hasChanges: Bool { draft != initial }
    var canSave: Bool { hasChanges && !isSaving }
    var preview: AvatarIdentity { AvatarIdentity(seed: seed, configuration: AvatarConfiguration(values: draft)) }
    init(identity: AvatarIdentity, onSave: @escaping ([String: String]) -> Void) {
        seed = identity.seed
        initial = identity.parameters.filter { AvatarIdentity.options[$0.key] != nil }
        draft = initial
        self.onSave = onSave
    }
    func select(_ key: String, value: String?) {
        guard !isSaving else { return }
        draft[key] = value
        onChange()
    }
    func save() {
        guard canSave else { return }
        isSaving = true
        onChange()
        onSave(draft)
    }

    func saveFailed() {
        guard isSaving else { return }
        isSaving = false
        onChange()
    }
}
