import Foundation

struct Axooji: Codable, Hashable {
    let id: Int
    let name: String
    let fallback: String
    var pickerHidden: Bool? = nil
}

struct AxoojiCategory: Decodable {
    let id: String
    let label: String
    let tabEmoji: String
    let icon: String
    let items: [String]
}

struct AxoojiCatalog {
    let emojis: [Axooji]
    let categories: [AxoojiCategory]
    let byID: [Int: Axooji]
    let byName: [String: Axooji]
    let root: URL?
    static let shared: AxoojiCatalog = (try? load()) ?? AxoojiCatalog(emojis: [], categories: [], byID: [:], byName: [:], root: nil)

    static func load(bundle: Bundle = .main) throws -> AxoojiCatalog {
        struct Source: Decodable {
            let name: String
            let schemaVersion: Int
            let emojis: [Axooji]
            let categories: [AxoojiCategory]
        }
        guard let url = bundle.url(forResource: "catalog", withExtension: "json", subdirectory: "axooji") else {
            throw RLottieError.invalidAsset
        }
        let source = try JSONDecoder().decode(Source.self, from: Data(contentsOf: url))
        let names = source.emojis.map(\.name)
        let members = source.categories.flatMap(\.items)
        guard source.name == "Axooji", source.schemaVersion == 1,
              Set(source.emojis.map(\.id)).count == source.emojis.count,
              Set(names).count == names.count, Set(members) == Set(names), members.count == names.count,
              source.categories.allSatisfy({ $0.items.contains($0.icon) }),
              source.emojis.allSatisfy({ $0.name.range(of: "^[a-z0-9-]+/[a-z0-9-]+$", options: .regularExpression) != nil }) else {
            throw RLottieError.invalidAsset
        }
        return AxoojiCatalog(emojis: source.emojis, categories: source.categories,
                             byID: Dictionary(uniqueKeysWithValues: source.emojis.map { ($0.id, $0) }),
                             byName: Dictionary(uniqueKeysWithValues: source.emojis.map { ($0.name, $0) }),
                             root: url.deletingLastPathComponent())
    }
    func animationURL(for emoji: Axooji) -> URL? { root?.appendingPathComponent("animations/\(emoji.name).tgs") }
}
