import Foundation
import Security

/// Where the API lives: `OChordsAPIBaseURL` in Info.plist (set per build configuration in project.yml).
enum APIConfig {
    static var baseURL: URL? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "OChordsAPIBaseURL") as? String,
              !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return URL(string: raw)
    }
}

/// The API token, kept in the Keychain (this device only, readable after first unlock).
struct TokenStore {
    private let service = "com.onezcodes.ochords.api"
    private let account = "token"

    func read() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Replaces the token in place, so a request reading it at the same moment never finds it missing.
    func save(_ token: String) {
        let data = Data(token.utf8)
        let status = SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        guard status == errSecItemNotFound else { return }
        var query = baseQuery
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    func clear() { SecItemDelete(baseQuery as CFDictionary) }

    private var baseQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }
}

/// JSON over HTTPS with the bearer token. Maps HTTP failures onto `ServiceError`.
final class APIClient {
    let baseURL: URL
    let tokens = TokenStore()
    private let session: URLSession

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    init(baseURL: URL) {
        self.baseURL = baseURL
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.waitsForConnectivity = false
        session = URLSession(configuration: config)
    }

    var hasToken: Bool { tokens.read() != nil }

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try Self.decoder.decode(T.self, from: try await request("GET", path, query: query))
    }

    func send<T: Decodable>(_ method: String, _ path: String, body: some Encodable) async throws -> T {
        try Self.decoder.decode(T.self, from: try await request(method, path, body: try Self.encoder.encode(body)))
    }

    /// For endpoints whose response body isn't needed (204s and acknowledgements).
    func perform(_ method: String, _ path: String) async throws {
        _ = try await request(method, path)
    }

    /// Uploads one file as multipart/form-data (field `name`) and decodes the reply.
    func upload<T: Decodable>(_ path: String, file: Data, name: String, filename: String, mimeType: String) async throws -> T {
        let boundary = "OChords-\(UUID().uuidString)"
        var body = Data()
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\nContent-Type: \(mimeType)\r\n\r\n".utf8))
        body.append(file)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let data = try await request("POST", path, body: body, contentType: "multipart/form-data; boundary=\(boundary)", timeout: 45)
        return try Self.decoder.decode(T.self, from: data)
    }

    func perform(_ method: String, _ path: String, body: some Encodable) async throws {
        _ = try await request(method, path, body: try Self.encoder.encode(body))
    }

    private func request(_ method: String, _ path: String, query: [URLQueryItem] = [], body: Data? = nil,
                         contentType: String = "application/json", timeout: TimeInterval? = nil) async throws -> Data {
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)
        if !query.isEmpty { components?.queryItems = query }
        guard let url = components?.url else { throw ServiceError.server("Bad request.") }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            urlRequest.httpBody = body
            urlRequest.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        if let timeout { urlRequest.timeoutInterval = timeout }
        let sentToken = tokens.read()
        if let sentToken { urlRequest.setValue("Bearer \(sentToken)", forHTTPHeaderField: "Authorization") }

        let data: Data, response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch {
            throw ServiceError.offline
        }
        guard let http = response as? HTTPURLResponse else { throw ServiceError.offline }
        guard !(200..<300).contains(http.statusCode) else { return data }
        throw failure(status: http.statusCode, data: data, sentToken: sentToken)
    }

    private struct ErrorBody: Decodable {
        var message: String?
        var errors: [String: [String]]?
    }

    private func failure(status: Int, data: Data, sentToken: String?) -> ServiceError {
        let body = try? Self.decoder.decode(ErrorBody.self, from: data)
        switch status {
        case 401:
            // Forget the token only if it's still the one this request used — a sign-in may have
            // saved a new one while this request was in flight.
            if let sentToken, tokens.read() == sentToken { tokens.clear() }
            return .signedOut
        case 422:
            // Show the first field error ("An account with this email already exists.").
            let first = body?.errors?.values.first?.first
            return .server(first ?? body?.message ?? "Please check what you entered.")
        case 429:
            return .server("Too many tries. Wait a minute and try again.")
        case 400..<500:
            return .server(body?.message ?? "That didn’t work. Try again.")
        default:
            return .server("Something went wrong on our side. Try again in a moment.")
        }
    }
}
