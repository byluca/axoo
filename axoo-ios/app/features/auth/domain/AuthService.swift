import Foundation

struct ModerationNotice: Equatable {
    let id: String
    let targetType: String
    let reason: String
}

struct AccountAccess: Equatable {
    enum Status: String { case active, suspended, banned }
    let status: Status
    let reason: String
    let suspendedUntil: String?
    var moderationNotice: ModerationNotice? = nil
    var termsRequired = false
    var termsVersion = "2026-10-07"
}

enum LoginInput {
    static let reviewEmail = "apple@studenti.uniba.it"
    static func email(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    static func isValidEmail(_ value: String) -> Bool {
        let parts = value.split(separator: "@", omittingEmptySubsequences: false)
        return parts.count == 2 && !parts[0].isEmpty && parts[1].contains(".")
            && !parts[1].hasPrefix(".") && !parts[1].hasSuffix(".")
            && !value.contains(where: { $0.isWhitespace })
    }

    static func code(_ value: String) -> String {
        String(value.filter { "0123456789".contains($0) }.prefix(6))
    }
}

enum AuthFailure: LocalizedError {
    case configuration, ineligible, invalidCode, expiredSession, invalidResponse
    case rateLimited, unavailable, invalidPassword

    var errorDescription: String? {
        switch self {
        case .configuration: return "L'accesso non è ancora configurato in questa versione dell'app."
        case .ineligible: return "Questa email non è abilitata. Usa l'indirizzo del tuo campus."
        case .invalidPassword: return "Email o password non validi."
        case .invalidCode: return "Il codice non è valido o è scaduto. Controllalo oppure richiedine uno nuovo."
        case .expiredSession: return "La sessione è scaduta. Accedi di nuovo."
        case .invalidResponse: return "Non riusciamo a verificare l'accesso. Riprova tra poco."
        case .rateLimited: return "Hai fatto troppi tentativi. Attendi un minuto prima di riprovare."
        case .unavailable: return "Il servizio non è disponibile al momento. Riprova tra poco."
        }
    }
}

@MainActor
protocol AuthService {
    var passwordRequired: Bool { get }
    func restore() async throws -> AccountAccess?
    func requestCode(email: String) async throws
    func verify(email: String, code: String) async throws -> AccountAccess
    func acceptTerms(version: String) async throws -> AccountAccess
    func hasSession() throws -> Bool
    func signOut() throws
}

extension AuthService {
    var passwordRequired: Bool { false }
    func acceptTerms(version: String) async throws -> AccountAccess { throw AuthFailure.unavailable }
}
