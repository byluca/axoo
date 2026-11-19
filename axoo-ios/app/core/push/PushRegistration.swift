import Foundation

/// Serializes uploads and logout cleanup; delayed work cannot attach an old account.
@MainActor
final class PushRegistration {
    private let api: APIClient
    private let defaults: UserDefaults
    private let remove: (String, String, String) async throws -> Void
    private let register: () async throws -> String
    private let invalidate: () async throws -> Void
    private var generation = UUID()
    private var upload: Task<Void, Never>?
    private var cleanup: Task<Void, Never>?
    private var uploadID = UUID()
    private var cleanupID = UUID()
    private(set) var userID: String?

    init(api: APIClient, defaults: UserDefaults = .standard,
         remove: @escaping (String, String, String) async throws -> Void,
         register: @escaping () async throws -> String,
         invalidate: @escaping () async throws -> Void) {
        self.api = api; self.defaults = defaults
        self.remove = remove; self.register = register; self.invalidate = invalidate
    }

    func activate(userID: String) {
        if self.userID != userID { generation = UUID() }
        self.userID = userID
    }
    func stop() { userID = nil; generation = UUID() }

    func prepare() async -> Bool {
        let expected = generation
        if cleanup == nil && defaults.bool(forKey: "push_reset_pending") { scheduleCleanup(accessToken: nil) }
        await cleanup?.value
        return userID != nil && generation == expected && !defaults.bool(forKey: "push_reset_pending")
    }

    func synchronize(_ currentRegistration: String? = nil) {
        guard let userID, cleanup == nil, !defaults.bool(forKey: "push_reset_pending") else { return }
        let expected = generation, previous = upload, id = UUID()
        uploadID = id
        upload = Task {
            await previous?.value
            defer { if uploadID == id { upload = nil } }
            guard self.userID == userID, generation == expected else { return }
            do {
                let registration: String
                if let currentRegistration { registration = currentRegistration }
                else { registration = try await register() }
                guard !registration.isEmpty, self.userID == userID, generation == expected else { return }
                let installationID = defaults.string(forKey: "push_installation_id") ?? UUID().uuidString.lowercased()
                defaults.set(installationID, forKey: "push_installation_id")
                // A lost HTTP response may still commit. Remember attempted bindings.
                defaults.set(registration, forKey: "push_registration")
                _ = try await api.request("PUT", path: "users/me/push-installations/\(installationID)",
                    body: APIJSON.body(["registration": registration]))
            } catch { NSLog("Push: registrazione rimandata alla prossima apertura") }
        }
    }

    func signedOut(accessToken: String?) {
        stop()
        if accessToken == nil && cleanup != nil { return }
        defaults.set(true, forKey: "push_reset_pending")
        scheduleCleanup(accessToken: accessToken)
    }

    private func scheduleCleanup(accessToken: String?) {
        let pendingUpload = upload, previous = cleanup, id = UUID()
        cleanupID = id
        cleanup = Task {
            defer { if cleanupID == id { cleanup = nil } }
            await previous?.value
            await pendingUpload?.value
            if let token = accessToken,
               let id = defaults.string(forKey: "push_installation_id"),
               let registration = defaults.string(forKey: "push_registration") {
                try? await remove(id, registration, token)
            }
            do {
                try await invalidate()
                defaults.removeObject(forKey: "push_registration")
                defaults.removeObject(forKey: "push_reset_pending")
            } catch { NSLog("Push: pulizia Firebase rimandata alla prossima apertura") }
        }
    }

    var hasSavedRegistration: Bool {
        defaults.string(forKey: "push_registration") != nil || defaults.bool(forKey: "push_reset_pending")
    }
}
