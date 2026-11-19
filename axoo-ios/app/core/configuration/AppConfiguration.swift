import Foundation

struct AppConfiguration {
    let apiURL: URL
    let authURL: URL
    let publishableKey: String

    static let current = AppConfiguration(
        apiURL: baseURL(
            key: "AxooAPIBaseURL",
            fallback: "https://axoo-api-796038905520.europe-west9.run.app/api/v1/"
        ),
        authURL: baseURL(
            key: "AxooSupabaseURL",
            fallback: "https://cyrfznxopjeeidcmfgze.supabase.co/"
        ).appendingPathComponent("auth").appendingPathComponent("v1", isDirectory: true),
        publishableKey: Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String ?? ""
    )

    private static func baseURL(key: String, fallback: String) -> URL {
        let setting = (Bundle.main.object(forInfoDictionaryKey: key) as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let value = setting.isEmpty || setting.hasPrefix("$(") ? fallback : setting
        let normalized = value.hasSuffix("/") ? value : value + "/"
        guard let components = URLComponents(string: normalized),
              components.scheme == "https", components.host?.isEmpty == false,
              components.user == nil, components.password == nil,
              components.query == nil, components.fragment == nil,
              let url = components.url else {
            preconditionFailure("Imposta un URL HTTPS valido per " + key + " in Local.xcconfig")
        }
        return url
    }

    var isConfigured: Bool {
        !publishableKey.isEmpty && !publishableKey.contains("$(")
    }
}
