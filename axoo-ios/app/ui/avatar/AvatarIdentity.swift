import Foundation

/// Only string options are meaningful to Android's avatar policy; ignore future metadata.
struct AvatarConfiguration: Decodable, Equatable {
    let values: [String: String]
    init(values: [String: String]) { self.values = values }
    init(from decoder: Decoder) throws {
        // Dictionary keys are server metadata and must bypass convertFromSnakeCase.
        let container = try decoder.singleValueContainer()
        self.values = try container.decode([String: OptionValue].self).mapValues(\.value)
    }
    private struct OptionValue: Decodable {
        let value: String
        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            value = (try? container.decode(String.self)) ?? ""
        }
    }
}

/// Mirrors AnonAvatar.kt: Kotlin Char hashing uses UTF-16, including surrogate pairs.
struct AvatarIdentity: Equatable {
    let seed: String
    var configuration: AvatarConfiguration?

    static let options: [String: [String]] = [
        "hairVariant": ["long01", "long02", "long03", "long04", "long05", "long07", "long08", "long09", "long10", "long11", "long12", "long13", "long14", "long15", "long16", "long19", "long20", "long21", "long23", "long24", "long25", "long26", "short01", "short02", "short03", "short04", "short05", "short06", "short07", "short08", "short09", "short10", "short11", "short12", "short13", "short14", "short15", "short16", "short17", "short18", "short19"],
        "hairColor": ["0e0e0e", "6a4e35", "562306", "cb6820", "b9a05f", "e5d7a3", "ac6511", "796a45", "ab2a18", "afafaf", "dba3be", "85c2c6", "3eac2c", "592454"],
        "eyesVariant": ["variant01", "variant02", "variant03", "variant05", "variant12", "variant14", "variant17", "variant20", "variant26", "variant04", "variant06", "variant07", "variant11", "variant13", "variant18", "variant19", "variant21", "variant22", "variant23", "variant24", "variant25"],
        "eyebrowsVariant": ["variant01", "variant02", "variant06", "variant07", "variant10", "variant12", "variant03", "variant05", "variant08", "variant09", "variant11", "variant13", "variant14", "variant15"],
        "mouthVariant": ["variant01", "variant02", "variant12", "variant22", "variant24", "variant25", "variant30", "variant15", "variant16", "variant17", "variant19", "variant21", "variant23", "variant26", "variant27", "variant28", "variant29"],
        "glassesVariant": ["variant01", "variant02", "variant03", "variant04", "variant05"],
        "earringsVariant": ["variant01", "variant02", "variant03", "variant04", "variant05", "variant06"],
        "detailsVariant": ["blush", "freckles", "birthmark", "mustache"],
        "skinColor": ["f2d3b1", "ecad80", "9e5622", "763900"],
        "backgroundColor": ["ff8fab", "ffb703", "4cc9a7", "4d96ff", "b57bff", "ff6b35", "00b4d8", "8ac926", "e63946", "6a4c93"],
    ]
    static let optional = ["glassesVariant", "earringsVariant", "detailsVariant"]

    private func pick(_ parameter: String, count: Int) -> Int {
        let hash = (seed + parameter).utf16.reduce(UInt32(0)) { (($0 &* 31) &+ UInt32($1)) & 0x7fffffff }
        return Int(hash) % count
    }
    var parameters: [String: String] {
        var result: [String: String] = [:]
        for key in ["hairVariant", "hairColor", "backgroundColor", "eyesVariant", "eyebrowsVariant", "mouthVariant"] {
            let values = Self.options[key]!
            result[key] = values[pick(key, count: values.count)]
        }
        result["skinColor"] = Self.options["skinColor"]!.first
        for (key, chance, selection) in [("earringsVariant", 8, "earringsPick"), ("glassesVariant", 5, "glassesPick"), ("detailsVariant", 10, "detailsPick")] {
            let values = Self.options[key]!
            if pick(key, count: 100) < chance { result[key] = values[pick(selection, count: values.count)] }
        }
        if let saved = configuration?.values, !saved.isEmpty {
            // Missing optional parts in a saved configuration explicitly mean "none".
            Self.optional.forEach { result.removeValue(forKey: $0) }
            for (key, value) in saved where Self.options[key]?.contains(value) == true { result[key] = value }
        }
        for key in Self.optional {
            result[key.replacingOccurrences(of: "Variant", with: "Probability")] = result[key] == nil ? "0" : "100"
        }
        return result
    }
    var imageURL: URL? { url(format: "png") }
    var svgURL: URL? { url(format: "svg") }

    private func url(format: String) -> URL? {
        if let url = configuration?.officialAvatarURL { return url }
        guard !seed.isEmpty else { return nil }
        var url = URLComponents(string: "https://api.dicebear.com/10.x/adventurer/\(format)")!
        let values = parameters.merging(["seed": seed, "size": "128"]) { _, next in next }
        url.queryItems = values.keys.sorted().map { URLQueryItem(name: $0, value: values[$0]) }
        return url.url
    }
}
