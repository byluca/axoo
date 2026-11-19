import Foundation

/// One model shared by profile, nickname editor and account settings.
@MainActor
final class ProfileViewModel {
    enum NicknameStatus { case neutral, available, error }
    struct State {
        var isLoading = false
        var isSavingAvatar = false
        var pendingProfilePublic: Bool?
        var isDeletingAccount = false
        var nickname = ""
        var nicknameMessage = ""
        var nicknameStatus: NicknameStatus = .neutral
        var isSavingNickname = false
        var bio = EmojiDocument(text: "")
        var bioError: String?
        var isSavingBio = false
        var blocked: [AccountProfile] = []
        var isLoadingBlocked = false
        var isUnblocking = false
        var notice: String?
    }
    private let repository: ProfileRepository
    private(set) var state = State()
    var user: AccountProfile? { repository.currentUser }
    // The visible profile/settings screen binds this callback in viewWillAppear.
    var onChange: () -> Void = {}
    var onUserChanged: () -> Void = {}
    var onUnblocked: (String) -> Void = { _ in }
    private var active = true
    private var checkedNickname: String?
    private var originalBio: EmojiDocument?
    private(set) var bioTask: Task<Void, Never>?
    private(set) var loadTask: Task<Void, Never>?
    private var privacyTask: Task<Void, Never>?
    private(set) var avatarTask: Task<Void, Never>?
    private(set) var checkTask: Task<Void, Never>?
    private(set) var nicknameTask: Task<Void, Never>?
    private(set) var deleteTask: Task<Void, Never>?
    private(set) var blockedTask: Task<Void, Never>?
    private(set) var unblockTask: Task<Void, Never>?

    init(repository: ProfileRepository) {
        self.repository = repository
        repository.onChange = { [weak self] in
            self?.onChange()
            self?.onUserChanged()
        }
    }

    func load(forceRefresh: Bool = false) {
        guard active, loadTask == nil else { return }
        state.isLoading = true
        onChange()
        loadTask = Task {
            defer { if !Task.isCancelled { state.isLoading = false; loadTask = nil; onChange() } }
            do { _ = try await repository.getMe(forceRefresh: forceRefresh); try Task.checkCancellation() }
            catch { if !Task.isCancelled { state.notice = "Caricamento non riuscito. Riprova." } }
        }
    }

    func setProfilePublic(_ isPublic: Bool) {
        guard active, user != nil, state.pendingProfilePublic == nil else { return }
        loadTask?.cancel(); loadTask = nil; state.isLoading = false
        state.pendingProfilePublic = isPublic
        onChange()
        privacyTask = Task {
            defer { if !Task.isCancelled { state.pendingProfilePublic = nil; privacyTask = nil; onChange() } }
            do { _ = try await repository.updateProfilePrivacy(isPublic: isPublic) }
            catch {
                guard !Task.isCancelled else { return }
                state.notice = "Modifica non confermata. Riprova."
                load(forceRefresh: true)
            }
        }
    }

    func makeModerationViewModel() -> ModerationViewModel {
        ModerationViewModel(repository: ModerationRepository(profile: repository))
    }

    var canSaveBio: Bool {
        active && !state.isSavingBio && originalBio != nil
            && state.bio.normalized != originalBio?.normalized
            && AccountProfile.bioValidationError(state.bio) == nil
    }

    func beginBioEditing() {
        cancelBioEditing()
        originalBio = user?.bioDocument
        state.bio = originalBio ?? EmojiDocument(text: "")
        state.bioError = nil
        onChange()
    }

    func changeBio(_ document: EmojiDocument) {
        guard active, !state.isSavingBio else { return }
        state.bio = document
        state.bioError = AccountProfile.bioValidationError(document)
        onChange()
    }

    func saveBio(onSaved: @escaping () -> Void) {
        guard canSaveBio else { return }
        let draft = state.bio
        state.isSavingBio = true
        state.bioError = nil
        onChange()
        bioTask = Task {
            defer { if !Task.isCancelled { state.isSavingBio = false; bioTask = nil; onChange() } }
            do {
                _ = try await repository.updateBio(draft)
                try Task.checkCancellation()
                originalBio = user?.bioDocument
                onSaved()
            } catch {
                guard !Task.isCancelled else { return }
                state.bioError = "Impossibile salvare la bio. Riprova."
            }
        }
    }

    func cancelBioEditing() {
        bioTask?.cancel(); bioTask = nil
        originalBio = nil
        state.isSavingBio = false
    }

    func updateAvatar(_ configuration: [String: String], completion: @escaping (Bool) -> Void) {
        guard active, !state.isSavingAvatar else { return }
        state.isSavingAvatar = true
        onChange()
        avatarTask = Task {
            defer { if !Task.isCancelled { state.isSavingAvatar = false; avatarTask = nil; onChange() } }
            do {
                try await repository.updateAvatar(configuration)
                try Task.checkCancellation()
                completion(true)
            } catch {
                guard !Task.isCancelled else { return }
                completion(false)
            }
        }
    }

    var canSaveNickname: Bool {
        active && !state.isSavingNickname && state.nickname != user?.nickname
            && checkedNickname == state.nickname && checkedNickname != nil
    }

    func beginNicknameEditing() {
        cancelNicknameEditing()
        changeNickname(user?.nickname ?? "")
    }

    func changeNickname(_ text: String) {
        guard active, !state.isSavingNickname else { return }
        checkTask?.cancel()
        checkedNickname = nil
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        state.nickname = value
        state.nicknameStatus = .neutral
        if let error = AccountProfile.nicknameValidationError(value) {
            state.nicknameMessage = error
            state.nicknameStatus = .error
        } else if value == user?.nickname {
            state.nicknameMessage = "Questo è il tuo nickname attuale"
        } else {
            state.nicknameMessage = "Verifica disponibilità…"
            checkTask = Task {
                do {
                    try await Task.sleep(nanoseconds: 400_000_000)
                    let available = try await repository.nicknameAvailable(value)
                    try Task.checkCancellation()
                    guard state.nickname == value else { return }
                    checkedNickname = available ? value : nil
                    state.nicknameMessage = available ? "Nickname disponibile" : "Nickname già in uso"
                    state.nicknameStatus = available ? .available : .error
                } catch {
                    guard !Task.isCancelled else { return }
                    state.nicknameMessage = "Verifica non riuscita. Modifica il nickname per riprovare."
                    state.nicknameStatus = .error
                }
                onChange()
            }
        }
        onChange()
    }

    func saveNickname(onSaved: @escaping () -> Void) {
        guard canSaveNickname else { return }
        checkTask?.cancel()
        let value = state.nickname
        state.isSavingNickname = true
        state.nicknameMessage = "Salvataggio…"
        state.nicknameStatus = .neutral
        onChange()
        nicknameTask = Task {
            defer { if !Task.isCancelled { state.isSavingNickname = false; nicknameTask = nil; onChange() } }
            do {
                _ = try await repository.updateNickname(value)
                try Task.checkCancellation()
                onSaved()
            } catch {
                guard !Task.isCancelled else { return }
                state.nicknameMessage = "Impossibile salvare. Riprova."
                state.nicknameStatus = .error
            }
        }
    }

    func cancelNicknameEditing() {
        checkTask?.cancel(); checkTask = nil
        nicknameTask?.cancel(); nicknameTask = nil
        checkedNickname = nil
        state.isSavingNickname = false
    }

    func deleteAccount(confirmation: String = "ELIMINA", onDeleted: @escaping () -> Void) {
        guard active, !state.isDeletingAccount else { return }
        guard confirmation.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "ELIMINA" else {
            state.notice = AxooOverlayCopy.deleteAccountInvalid
            onChange()
            return
        }
        state.isDeletingAccount = true
        onChange()
        deleteTask = Task {
            defer { if !Task.isCancelled { state.isDeletingAccount = false; deleteTask = nil; onChange() } }
            do {
                try await repository.deleteAccount()
                try Task.checkCancellation()
                onDeleted()
            } catch {
                guard !Task.isCancelled else { return }
                state.notice = AxooOverlayCopy.deleteError
            }
        }
    }

    func loadBlocked() {
        guard active, !state.isUnblocking else { return }
        blockedTask?.cancel()
        state.isLoadingBlocked = true
        onChange()
        blockedTask = Task {
            defer { if !Task.isCancelled { state.isLoadingBlocked = false; blockedTask = nil; onChange() } }
            do {
                let users = try await repository.blockedUsers()
                try Task.checkCancellation()
                state.blocked = users
            } catch {
                guard !Task.isCancelled else { return }
                state.notice = "Caricamento non riuscito. Riprova."
            }
        }
    }

    func unblock(_ id: String, completion: @escaping (Bool) -> Void = { _ in }) {
        guard active, !state.isUnblocking else { completion(false); return }
        blockedTask?.cancel(); blockedTask = nil
        state.isLoadingBlocked = false
        state.isUnblocking = true
        onChange()
        unblockTask = Task {
            defer { if !Task.isCancelled { state.isUnblocking = false; unblockTask = nil; onChange() } }
            do {
                try await repository.unblock(id)
                try Task.checkCancellation()
                state.blocked.removeAll { $0.id == id }
                onUnblocked(id)
                completion(true)
            } catch {
                guard !Task.isCancelled else { completion(false); return }
                state.notice = "Impossibile sbloccare l'utente."
                completion(false)
            }
        }
    }

    func stopBlocked() {
        blockedTask?.cancel(); blockedTask = nil
        unblockTask?.cancel(); unblockTask = nil
        state.isLoadingBlocked = false
        state.isUnblocking = false
    }

    func clearNotice() { state.notice = nil }

    func stop() {
        active = false
        loadTask?.cancel(); loadTask = nil
        avatarTask?.cancel(); avatarTask = nil
        privacyTask?.cancel(); privacyTask = nil
        deleteTask?.cancel(); deleteTask = nil
        cancelNicknameEditing()
        cancelBioEditing()
        stopBlocked()
        state.isLoading = false
        state.isSavingAvatar = false
        state.pendingProfilePublic = nil
        state.isDeletingAccount = false
        repository.onChange = {}
        onChange = {}; onUserChanged = {}; onUnblocked = { _ in }
    }
}
