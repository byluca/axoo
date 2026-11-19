import Foundation

struct EventDraft: Equatable {
    var title = ""
    var description = ""
    var coverUrl = ""
    var startsAt: Date
    var endsAt: Date
    var venue = ""
    var address = ""
    var entryInfo = ""
    var photo: PostEditorMedia?

    init(event: CityEvent? = nil, now: Date = Date()) {
        startsAt = APIJSON.date(event?.startsAt ?? "") ?? now.addingTimeInterval(24 * 3600)
        endsAt = APIJSON.date(event?.endsAt ?? "") ?? startsAt.addingTimeInterval(4 * 3600)
        if let event {
            title = event.title; description = event.description; coverUrl = event.coverUrl
            venue = event.venue; address = event.address; entryInfo = event.entryInfo
        }
    }

    func validationError(now: Date = Date()) -> String? {
        for (value, limit) in [(title, 120), (description, 10000), (venue, 160), (address, 300), (entryInfo, 160)] {
            if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "Compila tutti i campi." }
            if value.unicodeScalars.count > limit { return "Uno dei campi supera il limite di caratteri." }
        }
        if coverUrl.isEmpty && photo?.photoData == nil { return "Scegli una locandina." }
        if endsAt <= startsAt { return "La fine deve essere successiva all’inizio." }
        if endsAt <= now { return "L’evento è già concluso." }
        return nil
    }

    var payload: [String: Any] {
        let formatter = ISO8601DateFormatter()
        return ["title": title.trimmingCharacters(in: .whitespacesAndNewlines),
                "description": description.trimmingCharacters(in: .whitespacesAndNewlines), "cover_url": coverUrl,
                "starts_at": formatter.string(from: startsAt), "ends_at": formatter.string(from: endsAt),
                "venue": venue.trimmingCharacters(in: .whitespacesAndNewlines),
                "address": address.trimmingCharacters(in: .whitespacesAndNewlines),
                "entry_info": entryInfo.trimmingCharacters(in: .whitespacesAndNewlines)]
    }
}
