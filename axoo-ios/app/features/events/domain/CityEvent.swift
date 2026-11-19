import Foundation

struct CityEvent: Decodable, Equatable, Identifiable {
    let id: String
    let title: String
    let description: String
    let coverUrl: String
    let organizerName: String
    let organizerLogoUrl: String
    let startsAt: String
    let endsAt: String
    let venue: String
    let address: String
    let entryInfo: String
    let organizerId: String?
    let organizerAvatarConfig: AvatarConfiguration?
    let canManage: Bool
    let cancelled: Bool

    var hasEnded: Bool { (APIJSON.date(endsAt) ?? .distantPast) <= Date() }
    static func formattedDate(_ value: String) -> String {
        guard let date = APIJSON.date(value) else { return "Data da confermare" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "it_IT")
        formatter.timeZone = TimeZone(identifier: "Europe/Rome")
        formatter.dateFormat = "EEE d MMM · HH:mm"
        return formatter.string(from: date)
    }
}
