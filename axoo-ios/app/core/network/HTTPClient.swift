import Foundation

struct HTTPFailure: Error {
    let status: Int
    var isPermanentClientError: Bool {
        (400..<500).contains(status) && ![408, 425, 429].contains(status)
    }
}

@MainActor
protocol APIClient: AnyObject {
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data
    func uploadPhoto(_ jpeg: Data) async throws -> JSONValue
    func uploadSticker(_ png: Data) async throws -> JSONValue
}

extension APIClient {
    func uploadSticker(_ png: Data) async throws -> JSONValue { throw HTTPFailure(status: 501) }
    func uploadPhoto(_ jpeg: Data) async throws -> JSONValue { throw HTTPFailure(status: 501) }
    func request(_ method: String, path: String, body: Data? = nil) async throws -> Data {
        try await request(method, path: path, query: [], body: body)
    }
}

struct HTTPClient {
    let session: URLSession

    init(session: URLSession = .shared) { self.session = session }

    func post(_ url: URL, body: [String: String], headers: [String: String] = [:]) async throws -> Data {
        try await post(url, json: JSONSerialization.data(withJSONObject: body), headers: headers)
    }

    func post(_ url: URL, json: Data, headers: [String: String] = [:]) async throws -> Data {
        try await request("POST", url: url, json: json, headers: headers)
    }

    func request(_ method: String, url: URL, json: Data? = nil, headers: [String: String] = [:], timeout: TimeInterval = 20) async throws -> Data {
        try await request(method, url: url, body: json, contentType: "application/json", headers: headers, timeout: timeout)
    }

    func request(_ method: String, url: URL, body: Data?, contentType: String, headers: [String: String] = [:], timeout: TimeInterval = 20) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = timeout
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.httpBody = body
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard (200..<300).contains(response.statusCode) else {
            throw HTTPFailure(status: response.statusCode)
        }
        return data
    }
}
