import Foundation

// Only the most recent 36 IDs are stored on this device.
enum RecentEmoji {
    private static let key = "emoji-picker-recent"
    static func items(in catalog: AxoojiCatalog) -> [Axooji] {
        let ids = UserDefaults.standard.array(forKey: key) as? [Int] ?? []
        var seen = Set<Int>()
        return Array(ids.compactMap { catalog.byID[$0] }.filter {
            $0.pickerHidden != true && seen.insert($0.id).inserted
        }.prefix(36))
    }
    static func record(_ id: Int) {
        let previous = UserDefaults.standard.array(forKey: key) as? [Int] ?? []
        UserDefaults.standard.set(Array(([id] + previous.filter { $0 != id }).prefix(36)), forKey: key)
    }
}
