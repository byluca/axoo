import Foundation

@MainActor
enum AppDependencies {
    static func authService() -> AuthService {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-test-signed-out") {
            return AuthRepository(configuration: .current, store: InMemorySessionStore())
        }
        #endif

        return AuthRepository(configuration: .current, store: KeychainSessionStore())
    }
}

#if DEBUG
@MainActor
private final class InMemorySessionStore: SessionStore {
    private var session: StoredSession?

    func read() throws -> StoredSession? { session }
    func save(_ session: StoredSession) throws { self.session = session }
    func clear() throws { session = nil }
}
#endif
