import Foundation

enum AuthScreen: Equatable {
    case checking, intro, email, code, recovery, signingOut, signOutFailed, account(AccountAccess)
}

@MainActor
final class LoginViewModel {
    private let operations: AuthOperations
    private let api: APIClient?
    var onChange: () -> Void = {}
    var onFeedback: () -> Void = {}
    private(set) var moderationNotice: ModerationNotice?
    private var noticeTask: Task<Void, Never>?
    private var operationID = UUID()
    private(set) var operation: Task<Void, Never>?
    private var started = false
    private(set) var screen: AuthScreen = .checking
    private var pendingTerms: AccountAccess?
    private(set) var termsAccepted = false
    var requiresTermsOnly: Bool { pendingTerms != nil }
    private(set) var email = ""
    private(set) var code = ""
    private(set) var isBusy = false
    private(set) var errorMessage: String?
    private(set) var resendAvailableAt = Date.distantPast

    init(operations: AuthOperations, api: APIClient? = nil) {
        self.operations = operations
        self.api = api
    }
    func start() {
        guard !started else { return }
        started = true
        restore()
    }
    var passwordRequired: Bool { operations.passwordRequired }
    var canSubmit: Bool {
        !isBusy && (screen == .code ? (passwordRequired ? !code.isEmpty : code.count == 6) : termsAccepted && (requiresTermsOnly || !email.isEmpty && Date() >= resendAvailableAt))
    }
    func setTermsAccepted(_ accepted: Bool) {
        guard !isBusy else { return }
        termsAccepted = accepted
        onChange()
    }
    func setInput(_ text: String) {
        guard !isBusy else { return }
        if screen == .code {
            code = passwordRequired ? String(text.prefix(128)) : LoginInput.code(text)
            if !passwordRequired && code.count == 6 { verifyCode() }
        } else { email = text }
    }
    func beginLogin() {
        guard !isBusy else { return }
        errorMessage = nil
        transition(.email)
    }
    func back() {
        guard !isBusy else { return }
        if requiresTermsOnly { signOut(); return }
        errorMessage = nil
        code = ""
        transition(screen == .code ? .email : .intro)
    }
    func sendCode() {
        if requiresTermsOnly { acceptTerms(); return }
        guard !isBusy, termsAccepted, Date() >= resendAvailableAt else { return }
        let input = email
        run {
            do {
                let address = try await self.operations.requestCode(email: input)
                try Task.checkCancellation()
                self.email = address
                self.code = ""
                self.resendAvailableAt = Date().addingTimeInterval(30)
                self.onFeedback()
                self.transition(.code)
            } catch {
                try Task.checkCancellation()
                if !(error is AuthValidationError) { self.resendAvailableAt = Date().addingTimeInterval(5) }
                throw error
            }
        }
    }
    func verifyCode() {
        guard !isBusy else { return }
        let address = email, token = code
        run {
            let access = try await self.operations.verify(email: address, code: token)
            try Task.checkCancellation()
            self.code = ""
            self.onFeedback()
            if access.status == .active && access.termsRequired {
                self.pendingTerms = access
                guard self.termsAccepted else { self.transition(.email); return }
                let updated = try await self.operations.acceptTerms(version: access.termsVersion)
                try Task.checkCancellation()
                self.pendingTerms = nil
                self.transition(.account(updated))
            } else { self.transition(.account(access)) }
        }
    }
    func restore() {
        guard !isBusy else { return }
        run {
            let access = try await self.operations.restore()
            try Task.checkCancellation()
            if let access, access.status == .active && access.termsRequired {
                self.pendingTerms = access
                self.termsAccepted = false
                self.transition(.email)
            } else { self.transition(access.map(AuthScreen.account) ?? .intro) }
        }
    }
    func acceptTerms() {
        guard let access = pendingTerms, termsAccepted else { return }
        run {
            let updated = try await self.operations.acceptTerms(version: access.termsVersion)
            try Task.checkCancellation()
            self.pendingTerms = nil
            self.transition(.account(updated))
        }
    }
    func signOut() {
        guard screen != .signingOut || !isBusy else { return }
        stop()
        isBusy = true
        errorMessage = nil
        transition(.signingOut)
        let id = operationID
        operation = Task { [weak self] in
            guard let self else { return }
            defer { if operationID == id { isBusy = false; operation = nil; onChange() } }
            do {
                try await operations.signOut()
                try Task.checkCancellation()
                email = ""
                code = ""
                pendingTerms = nil
                termsAccepted = false
                resendAvailableAt = .distantPast
                transition(.intro)
            } catch {
                guard !Task.isCancelled else { return }
                errorMessage = "Non è stato possibile cancellare i dati dell'account da questo dispositivo. Riprova per completare l'uscita."
                transition(.signOutFailed)
            }
        }
    }
    private func run(_ action: @escaping @MainActor () async throws -> Void) {
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        onChange()
        let id = UUID()
        let feedbackOnError = screen == .email || screen == .code
        operationID = id
        operation = Task { [weak self] in
            guard let self else { return }
            defer {
                if operationID == id { isBusy = false; operation = nil; onChange() }
            }
            do {
                try await action()
                try Task.checkCancellation()
            } catch {
                guard operationID == id, !Task.isCancelled else { return }
                if feedbackOnError { onFeedback() }
                errorMessage = message(for: error)
                if requiresTermsOnly { transition(.email); return }
                // The OTP may have been consumed while account bootstrap failed.
                do {
                    if try operations.hasSession() { transition(.recovery) }
                    else if screen == .checking || screen == .recovery || isAccountScreen { transition(.email) }
                } catch { transition(.recovery) }
            }
        }
    }
    private var isAccountScreen: Bool {
        if case .account = screen { return true }
        return false
    }
    private func message(for error: Error) -> String {
        if let error = error as? URLError {
            return error.code == .timedOut
                ? "La richiesta ha impiegato troppo tempo. Riprova."
                : "Non riusciamo a connetterci. Controlla la connessione e riprova."
        }
        if let error = error as? LocalizedError, let description = error.errorDescription { return description }
        return "Non riusciamo a completare l'accesso. Riprova tra poco."
    }

    private func transition(_ next: AuthScreen) {
        screen = next
        if case .account(let access) = next { moderationNotice = access.moderationNotice }
        else { moderationNotice = nil }
        onChange()
    }
    func acknowledgeNotice(_ id: String, completion: @escaping (Bool) -> Void) {
        guard let api else { return }
        let expected = operationID
        noticeTask?.cancel()
        noticeTask = Task { [weak self] in
            do {
                _ = try await api.request("POST", path: "users/me/moderation-notices/\(try APIIdentifier.path(id))/seen")
                try Task.checkCancellation()
                guard let self, operationID == expected else { return }
                if moderationNotice?.id == id { moderationNotice = nil }
                completion(true)
            } catch {
                guard let self, !Task.isCancelled, operationID == expected else { return }
                completion(false)
            }
        }
    }
    func stop() {
        operationID = UUID()
        operation?.cancel()
        operation = nil
        noticeTask?.cancel()
        noticeTask = nil
        isBusy = false
    }
}
