import Foundation

// Service boundaries. The demo ships `MockBackend`; a real API client
// can conform to the same protocols later without touching the UI.

protocol SongService {
    func library() async throws -> [Song]
    func explore(query: String) async throws -> [CommunitySheet]
    func popular() async throws -> [CommunitySheet]
    func save(_ song: Song) async throws
    func delete(songID: String) async throws
}

protocol ScanService {
    /// Read a chord sheet (photo or PDF) into a draft song, reporting each stage as it starts.
    func scan(_ source: ScanSource, progress: @escaping @MainActor (ScanStage) -> Void) async throws -> ScanResult
}

enum ScanSource: Hashable {
    case image(Data)
    case pdf(Data)
}

enum ScanStage: Int, Comparable {
    case page, text, chords, song
    static func < (a: ScanStage, b: ScanStage) -> Bool { a.rawValue < b.rawValue }
}

struct ScanResult: Hashable {
    var song: Song
    var detectedZawgyi: Bool
    var lowConfidence: [String]
    /// Why a chord needs checking, e.g. "Superscript bass note read as a slash chord".
    var chordNotes: [String: String] = [:]
    /// Lines whose lyrics couldn't be read (e.g. Myanmar script in a photo) — the user types them in Review.
    var needsLyrics: [ScanLineRef] = []
    /// Small JPEG of the page as read (upright, cropped).
    var thumbnail: Data? = nil
    /// Where each section sits on the first page, for the reading overlay.
    var regions: [ScanRegion] = []
    var linesRead = 0
    /// Whether the photo was cropped and flattened to the page edges.
    var croppedToPage = false
    /// Read by the server (Myanmar lyrics included) rather than on the phone.
    var readOnServer = false
    /// When `detectedZawgyi`: what was converted, for the "We fixed the Myanmar text" screen.
    var zawgyi: ZawgyiReport? = nil
    /// What the reader saw, kept with the song so it can be read again when the scanner improves.
    var reading: ScanReading? = nil
}

/// A sheet typed in the Zawgyi font, converted to Unicode. `song` on the result is the converted one.
struct ZawgyiReport: Hashable {
    struct Line: Hashable {
        var label: String
        var scanned: String
        var converted: String
    }
    /// The song exactly as scanned, for "Keep as scanned".
    var scannedSong: Song
    var wordsConverted: Int
    /// Share of converted words that came out as well-formed Unicode (0–1).
    var clean: Double
    var preview: [Line]
}

struct ScanLineRef: Hashable, Identifiable {
    var sectionID: String
    var line: Int
    var id: String { "\(sectionID)#\(line)" }
}

/// A section's area on the page, normalised 0–1 with the origin top-left.
struct ScanRegion: Hashable {
    var kind: SectionKind
    var x, y, width, height: Double
}

protocol AuthService {
    /// The signed-in profile from a saved session, or nil when there isn't one.
    func restoreSession() async throws -> Profile?
    func signIn(email: String, password: String) async throws -> Profile
    func signUp(name: String, email: String, password: String) async throws -> Profile
    func sendReset(email: String) async throws
    /// Second step of "Forgot password": the token comes from the `ochords://reset-password` link in the email.
    func resetPassword(email: String, token: String, password: String) async throws
    /// Sign in with Apple: the identity token from `ASAuthorizationAppleIDCredential`; name only on first sign-in.
    func signInWithApple(identityToken: String, name: String?) async throws -> Profile
    /// Sign in with Google: the ID token from the Google Sign-In SDK.
    func signInWithGoogle(idToken: String) async throws -> Profile
    /// Tells the account about a StoreKit purchase (the signed transaction), so Pro follows the player across devices.
    func syncSubscription(transactionJWS: String?) async throws -> Profile
    func signOut() async
    func deleteAccount(reason: String) async throws
}

protocol CommunityService {
    func setlists() async throws -> [Setlist]
    /// Creates or updates one of the player's setlists (by the app's ID). Returns the saved copy.
    func saveSetlist(_ list: Setlist) async throws -> Setlist
    func deleteSetlist(id: String) async throws
    func notifications() async throws -> [AppNotification]
    func markAllNotificationsRead() async throws
    func user(id: String) async throws -> UserSummary
    func report(songID: String, reason: String, detail: String) async throws
    func sendFeedback(kind: String, message: String) async throws
}

protocol LearnService {
    func path() async throws -> [LearnPosition]
    func lesson(fretID: String) async throws -> [LessonQuestion]
}

/// Everything the screens need from a backend: the demo `MockBackend` or the API (`RemoteBackend`).
/// Sheet scanning isn't here — it runs on the phone (`AppState.scanner`).
protocol AppBackend: AnyObject, SongService, AuthService, CommunityService, LearnService {
    /// A song this backend has already loaded (library, Explore, setlists), for screens that look songs up by ID.
    func song(id: String) -> Song?
    /// Reads sheet photos on the server (Myanmar lyrics); nil when there is no server.
    var serverOCR: OCREngine? { get }
}

enum ServiceError: LocalizedError, Equatable {
    case offline, invalidCredentials, scanFailed, signedOut
    /// A message from the server that can be shown as is (e.g. "An account with this email already exists.").
    case server(String)
    var errorDescription: String? {
        switch self {
        case .offline: "You’re offline."
        case .invalidCredentials: "That email and password don’t match."
        case .scanFailed: "We couldn’t read that sheet."
        case .signedOut: "Your session ended. Log in again."
        case .server(let message): message
        }
    }
}
