import Foundation

struct EventPoster: Codable, Equatable {
    let url: String
    let width: Int
    let height: Int
    let type: String
    let mimeType: String?
}

struct CampusEvent: Decodable, Equatable, Identifiable {
    let id: String
    let title: String
    let location: String
    let description: String
    let startsAt: String
    var poster: EventPoster? = nil
    var organizerId: String? = nil
    var organizerName: String? = nil
    var organizerAvatarConfig: AvatarConfiguration? = nil
    var endsAt: String? = nil
    var address: String? = nil
    var entryInfo: String? = nil
    var canManage: Bool? = nil
}

extension CampusEvent {
    func cityEvent(canManage: Bool? = nil) -> CityEvent {
        CityEvent(id: id, title: title, description: description, coverUrl: poster?.url ?? "",
            organizerName: organizerName.flatMap { $0.isEmpty ? nil : $0 } ?? "Organizzatore",
            organizerLogoUrl: "", startsAt: startsAt, endsAt: endsAt ?? "", venue: location,
            address: address ?? "", entryInfo: entryInfo ?? "", organizerId: organizerId,
            organizerAvatarConfig: organizerAvatarConfig, canManage: canManage ?? self.canManage ?? false, cancelled: false)
    }
}

struct EventsPage: Decodable {
    let events: [CampusEvent]
    let nextCursor: String?
    let canManage: Bool
}

struct EventInput {
    let title: String
    let location: String
    let description: String
    let startsAt: Date
    var poster: EventPoster? = nil

    var endsAt: Date? = nil
    var address = ""
    var entryInfo = ""

    var validationError: String? {
        if title.isEmpty || title.unicodeScalars.count > 120 { return "Inserisci un titolo di massimo 120 caratteri." }
        if location.isEmpty || location.unicodeScalars.count > 200 { return "Inserisci un luogo di massimo 200 caratteri." }
        if description.unicodeScalars.count > 10000 { return "La descrizione può contenere al massimo 10000 caratteri." }
        if let endsAt {
            if endsAt <= startsAt || endsAt <= Date() { return "Controlla inizio e fine dell’evento." }
        } else if startsAt <= Date() { return "Scegli una data e un'ora future." }
        return nil
    }
}

@MainActor
protocol EventsSource {
    func page(cursor: String?) async throws -> EventsPage
    func create(_ input: EventInput) async throws -> CampusEvent
    func delete(id: String) async throws
    func event(id: String) async throws -> CampusEvent
    func update(id: String, input: EventInput) async throws -> CampusEvent
    func uploadPoster(_ data: Data) async throws -> EventPoster
}

extension EventsSource {
    func uploadPoster(_ data: Data) async throws -> EventPoster { throw HTTPFailure(status: 501) }
}

@MainActor
final class EventsRepository: EventsSource {
    private let api: APIClient
    init(api: APIClient) { self.api = api }
    func page(cursor: String?) async throws -> EventsPage {
        let query = cursor.map { [URLQueryItem(name: "cursor", value: $0)] } ?? []
        let data = try await api.request("GET", path: "events", query: query, body: nil)
        return try APIJSON.decode(EventsPage.self, from: data)
    }
    func event(id: String) async throws -> CampusEvent {
        let data = try await api.request("GET", path: "events/\(id)")
        return try APIJSON.decode(CampusEvent.self, from: data)
    }
    func create(_ input: EventInput) async throws -> CampusEvent {
        try await save(input, method: "POST", path: "events")
    }
    func update(id: String, input: EventInput) async throws -> CampusEvent {
        try await save(input, method: "PATCH", path: "events/\(id)")
    }
    private func save(_ input: EventInput, method: String, path: String) async throws -> CampusEvent {
        struct Payload: Encodable {
            let title: String
            let location: String
            let description: String
            let startsAt: String
            let poster: EventPoster?
            let endsAt: String?
            let address: String
            let entryInfo: String
        }
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let body = try encoder.encode(Payload(title: input.title, location: input.location,
            description: input.description, startsAt: ISO8601DateFormatter().string(from: input.startsAt), poster: input.poster,
            endsAt: input.endsAt.map { ISO8601DateFormatter().string(from: $0) }, address: input.address, entryInfo: input.entryInfo))
        let data = try await api.request(method, path: path, body: body)
        return try APIJSON.decode(CampusEvent.self, from: data)
    }
    func uploadPoster(_ data: Data) async throws -> EventPoster {
        let uploaded = try await api.uploadPhoto(data)
        return try APIJSON.decode(EventPoster.self, from: JSONEncoder().encode(uploaded))
    }
    func delete(id: String) async throws { _ = try await api.request("DELETE", path: "events/\(id)") }
}
