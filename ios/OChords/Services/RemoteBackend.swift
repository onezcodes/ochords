import Foundation

/// The O Chords API (Laravel, `api/` in this repo). Listen runs on the phone (`SongListener`), and sheet scanning
/// mostly does too (`OnDeviceScanService`).
final class RemoteBackend: AppBackend {
    private let api: APIClient
    /// Every song loaded so far, for `song(id:)`. Requests finish on any thread, hence the lock.
    private var songs: [String: Song] = [:]
    private let lock = NSLock()

    init(baseURL: URL) {
        api = APIClient(baseURL: baseURL)
    }

    func song(id: String) -> Song? { lock.withLock { songs[id] } }

    var serverOCR: OCREngine? { ServerOCREngine(api: api) }

    private func remember(_ list: [Song]) -> [Song] {
        lock.withLock { for s in list { songs[s.id] = s } }
        return list
    }

    private func forget(_ id: String? = nil) {
        lock.withLock { if let id { songs[id] = nil } else { songs = [:] } }
    }

    // MARK: Auth

    private struct Session: Decodable {
        var token: String
        var profile: Profile
    }

    func restoreSession() async throws -> Profile? {
        guard api.hasToken else { return nil }
        do {
            let profile: Profile = try await api.get("me")
            return profile
        } catch ServiceError.signedOut {
            return nil
        }
    }

    /// A wrong password comes back as the API's own "That email and password don’t match." message.
    func signIn(email: String, password: String) async throws -> Profile {
        let session: Session = try await api.send("POST", "auth/sign-in",
                                                  body: ["email": email, "password": password, "device_name": deviceName])
        api.tokens.save(session.token)
        return session.profile
    }

    func signUp(name: String, email: String, password: String) async throws -> Profile {
        let session: Session = try await api.send("POST", "auth/sign-up",
                                                  body: ["name": name, "email": email, "password": password, "device_name": deviceName])
        api.tokens.save(session.token)
        return session.profile
    }

    func sendReset(email: String) async throws {
        try await api.perform("POST", "auth/forgot-password", body: ["email": email])
    }

    func resetPassword(email: String, token: String, password: String) async throws {
        try await api.perform("POST", "auth/reset-password", body: ["email": email, "token": token, "password": password])
    }

    func signInWithApple(identityToken: String, name: String?) async throws -> Profile {
        var body = ["identityToken": identityToken, "device_name": deviceName]
        if let name, !name.isEmpty { body["name"] = name }
        let session: Session = try await api.send("POST", "auth/apple", body: body)
        api.tokens.save(session.token)
        return session.profile
    }

    func signInWithGoogle(idToken: String) async throws -> Profile {
        let session: Session = try await api.send("POST", "auth/google", body: ["idToken": idToken, "device_name": deviceName])
        api.tokens.save(session.token)
        return session.profile
    }

    /// nil = no active subscription found (e.g. after a cancelled trial), which clears Pro on the account.
    func syncSubscription(transactionJWS: String?) async throws -> Profile {
        if let transactionJWS {
            return try await api.send("PUT", "me/subscription", body: ["transactionJWS": transactionJWS])
        }
        try await api.perform("DELETE", "me/subscription")
        return try await api.get("me")
    }

    func signOut() async {
        try? await api.perform("POST", "auth/sign-out")
        api.tokens.clear()
        forget()
    }

    func deleteAccount(reason: String) async throws {
        try await api.perform("DELETE", "me", body: ["reason": reason])
        api.tokens.clear()
    }

    private var deviceName: String { "O Chords iOS" }

    // MARK: Songs

    func library() async throws -> [Song] { remember(try await api.get("songs")) }

    func explore(query: String) async throws -> [CommunitySheet] {
        let q = query.trimmingCharacters(in: .whitespaces)
        let sheets: [CommunitySheet] = try await api.get("explore", query: q.isEmpty ? [] : [URLQueryItem(name: "q", value: q)])
        _ = remember(sheets.map(\.song))
        return sheets
    }

    func popular() async throws -> [CommunitySheet] {
        let sheets: [CommunitySheet] = try await api.get("explore/popular")
        _ = remember(sheets.map(\.song))
        return sheets
    }

    func save(_ song: Song) async throws {
        let saved: Song = try await api.send("PUT", "songs/\(song.id)", body: song)
        _ = remember([saved])
    }

    func delete(songID: String) async throws {
        try await api.perform("DELETE", "songs/\(songID)")
        forget(songID)
    }

    // MARK: Community

    /// Also loads any setlist songs not seen yet, so the Setlists screen can show them.
    func setlists() async throws -> [Setlist] {
        let lists: [Setlist] = try await api.get("setlists")
        for id in Set(lists.flatMap(\.songIDs)) where song(id: id) == nil {
            if let found: Song = try? await api.get("songs/\(id)") { _ = remember([found]) }
        }
        return lists
    }

    func saveSetlist(_ list: Setlist) async throws -> Setlist {
        struct Body: Encodable { var name: String; var note: String; var songIDs: [String] }
        return try await api.send("PUT", "setlists/\(list.id)", body: Body(name: list.name, note: list.note, songIDs: list.songIDs))
    }

    func deleteSetlist(id: String) async throws { try await api.perform("DELETE", "setlists/\(id)") }

    func notifications() async throws -> [AppNotification] { try await api.get("notifications") }

    func markAllNotificationsRead() async throws { try await api.perform("POST", "notifications/read-all") }

    func user(id: String) async throws -> UserSummary { try await api.get("users/\(id)") }

    /// The Report screen passes its labels; the API takes stable codes.
    func report(songID: String, reason: String, detail: String) async throws {
        let codes = ["Wrong chords or lyrics": "wrong_chords", "Copyright": "copyright", "Spam or advertising": "spam",
                     "Offensive content": "offensive", "Something else": "other"]
        try await api.perform("POST", "songs/\(songID)/reports", body: ["reason": codes[reason] ?? "other", "detail": detail])
    }

    func sendFeedback(kind: String, message: String) async throws {
        let codes = ["Something’s broken": "broken", "Wrong chords": "wrong_chords", "Idea": "idea", "Other": "other"]
        try await api.perform("POST", "feedback", body: ["kind": codes[kind] ?? "other", "message": message])
    }

    // MARK: Learn

    func path() async throws -> [LearnPosition] { try await api.get("learn/path") }

    func lesson(fretID: String) async throws -> [LessonQuestion] { try await api.get("learn/frets/\(fretID)/questions") }
}
