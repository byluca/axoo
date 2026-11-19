import Foundation

enum AuthValidationError: LocalizedError {
    case invalidEmail, invalidCode
    var errorDescription: String? {
        switch self {
        case .invalidEmail: return "Inserisci un indirizzo email valido."
        case .invalidCode: return "Inserisci le 6 cifre del codice."
        }
    }
}

/// The authentication operations have no screen or task state.
@MainActor
final class AuthOperations {
    private let service: AuthService
    init(service: AuthService) { self.service = service }

    var passwordRequired: Bool { service.passwordRequired }
    func restore() async throws -> AccountAccess? { try await service.restore() }
    func requestCode(email: String) async throws -> String {
        let address = LoginInput.email(email)
        guard LoginInput.isValidEmail(address) else { throw AuthValidationError.invalidEmail }
        try await service.requestCode(email: address)
        return address
    }
    func verify(email: String, code: String) async throws -> AccountAccess {
        let address = LoginInput.email(email)
        let token = passwordRequired ? code : LoginInput.code(code)
        guard LoginInput.isValidEmail(address) else { throw AuthValidationError.invalidEmail }
        guard passwordRequired ? !token.isEmpty && token.count <= 128 : token.count == 6 else { throw AuthValidationError.invalidCode }
        return try await service.verify(email: address, code: token)
    }
    func acceptTerms(version: String) async throws -> AccountAccess { try await service.acceptTerms(version: version) }
    func hasSession() throws -> Bool { try service.hasSession() }
    func signOut() async throws { try service.signOut() }
}
