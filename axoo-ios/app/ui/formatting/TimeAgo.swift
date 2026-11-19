import Foundation

enum TimeAgo {
    static func format(_ date: Date) -> String {
        let duration = abs(date.timeIntervalSinceNow)
        if duration < 60 { return "adesso" }
        let minutes = Int(duration / 60)
        if minutes < 60 { return "\(minutes)m" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h" }
        let days = hours / 24
        if days < 30 { return "\(days)g" }
        if days < 365 { return "\(days / 30)mo" }
        return "\(days / 365)a"
    }
}
