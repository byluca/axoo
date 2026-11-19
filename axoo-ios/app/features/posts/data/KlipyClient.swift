import Foundation

enum KlipyMedia {
    static func isMediaURL(_ url: URL) -> Bool {
        url.scheme == "https" && url.user == nil && url.password == nil && url.port == nil &&
            ["static.klipy.com", "static1.klipy.com", "static2.klipy.com"].contains(url.host ?? "")
    }
}

/// Direct client requests, with no Axoo credentials, HTTP logging or persistent cache.
@MainActor
final class KlipyClient: APIClient {
    static let shared = KlipyClient()
    private let key: String
    private let customerID: String
    private let http: HTTPClient
    private var selections: [String: String] = [:]

    init(key: String = Bundle.main.object(forInfoDictionaryKey: "KlipyAPIKey") as? String ?? "",
         defaults: UserDefaults = .standard, session: URLSession? = nil) {
        self.key = key
        let customerID = defaults.string(forKey: "klipyCustomerID") ?? UUID().uuidString
        defaults.set(customerID, forKey: "klipyCustomerID")
        self.customerID = customerID
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.http = HTTPClient(session: session ?? URLSession(configuration: configuration))
    }

    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        guard !key.isEmpty, key.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }) else {
            throw HTTPFailure(status: 503)
        }
        var url = URLComponents(string: "https://api.klipy.com/api/v1/\(key)/\(path)")!
        url.queryItems = query + [URLQueryItem(name: "customer_id", value: customerID)]
        return try await http.request(method, url: url.url!, json: body)
    }

    func rememberSelection(_ media: PostEditorMedia) {
        guard media.provider == "klipy", let slug = media.providerID else { return }
        if selections.count >= 100 { selections.removeAll() }
        selections[slug] = media.searchQuery
    }

    func reportShare(_ media: PostEditorMedia?) {
        guard let media, media.provider == "klipy", let slug = media.providerID,
              let query = selections.removeValue(forKey: slug) else { return }
        Task {
            guard !slug.isEmpty, slug.utf8.count <= 512,
                  slug.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }),
                  let body = try? APIJSON.body(["customer_id": customerID, "q": query]) else { return }
            // A failed analytics request must not change a successful publication.
            _ = try? await request("POST", path: "\(media.kind == .meme ? "static-memes" : (media.kind == .sticker ? "stickers" : "gifs"))/share/\(slug)", query: [], body: body)
        }
    }
}
