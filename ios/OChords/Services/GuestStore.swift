import Foundation

/// A guest's songs and setlists, kept on this phone (Application Support) since there's no account to sync to.
/// When the guest signs up or logs in, their songs move to the account and this is cleared.
struct GuestStore {
    private static let folder = "Guest"

    var library: [Song] { load("library.json") ?? [] }
    var setlists: [Setlist] { load("setlists.json") ?? [] }

    func save(library: [Song]) { store(library, as: "library.json") }
    func save(setlists: [Setlist]) { store(setlists, as: "setlists.json") }

    func clear() { try? FileManager.default.removeItem(at: directory) }

    private var directory: URL {
        URL.applicationSupportDirectory.appending(path: Self.folder, directoryHint: .isDirectory)
    }

    private func load<T: Decodable>(_ name: String) -> T? {
        guard let data = try? Data(contentsOf: directory.appending(path: name)) else { return nil }
        return try? APIClient.decoder.decode(T.self, from: data)
    }

    private func store(_ value: some Encodable, as name: String) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try APIClient.encoder.encode(value).write(to: directory.appending(path: name), options: [.atomic, .completeFileProtection])
        } catch {
            assertionFailure("Couldn’t save the guest \(name): \(error)")
        }
    }
}
