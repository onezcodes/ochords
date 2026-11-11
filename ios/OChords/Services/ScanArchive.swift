import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

/// What the reader saw for a scanned song, kept on the phone so the song can be read again when the scanner
/// improves — without the photo leaving the phone again or another call to the server's reader.
struct ScanReading: Codable, Hashable {
    enum Kind: String, Codable {
        /// The server's words (before the phone fills in chords it skipped) plus the page they were read from.
        case server
        /// Read on the phone: the page is read again with the phone's (improved) reader.
        case device
        /// A typed PDF's own text, or a multi-page scan: the lines are parsed again as they are.
        case lines
    }
    var kind: Kind
    var lines: [ScanTextLine]
    var readsMyanmar: Bool
    var zawgyi: Bool
    /// The page as read (levelled, cropped), JPEG. Not stored for `.lines`.
    var page: Data?
}

/// The parts of a song the scanner produces, to tell what the player changed afterwards.
struct ScannedParts: Codable, Hashable {
    var title: String
    var artist: String
    var key: String
    var sections: [Section]

    /// A section without its id (ids are new each time a sheet is read).
    struct Section: Codable, Hashable {
        var kind: SectionKind
        var label: String
        var note: String?
        var lines: [String]
        var bars: [String]
        var strumPattern: String?
    }

    init(_ song: Song) {
        title = song.title
        artist = song.artist
        key = song.key
        sections = song.sections.filter { $0.kind != .flow }.map {
            Section(kind: $0.kind, label: $0.label, note: $0.note, lines: $0.lines, bars: $0.bars, strumPattern: $0.strumPattern)
        }
    }
}

/// Reading a song's sheet again with the current scanner, and folding the result into the song the player has.
enum Rescan {
    /// The scanner's reading logic version. **Raise it whenever reading or parsing improves** so stored readings are
    /// read again (see `ScanArchive`).
    static let scannerVersion = 2

    /// The sheet as the current scanner reads it, or nil if it finds no chords.
    static func read(_ reading: ScanReading) -> Song? {
        var lines = reading.lines
        if let data = reading.page, let page = image(data) {
            switch reading.kind {
            case .server: lines = VisionOCREngine.addMissedChords(to: lines, in: page)
            case .device: lines = (try? VisionOCREngine.read(page)) ?? lines
            case .lines: break
            }
        }
        // Decided again rather than trusting `reading.zawgyi`: an earlier detector took some Unicode pages for Zawgyi.
        if Zawgyi.isZawgyi(lines.map(\.text).joined(separator: "\n")) {
            lines = lines.map(OnDeviceScanService.unicodeLine)
        }
        let song = ChordSheetParser.parse(lines, readsMyanmar: reading.readsMyanmar).song
        return song.usedChords.isEmpty ? nil : song
    }

    struct Outcome: Equatable {
        /// The song with the new reading applied where the player hadn't changed it (nil: nothing to apply).
        var updated: Song?
        /// The new lyrics and chords, offered because the player had changed theirs (nil: nothing to offer).
        var offered: Song?
    }

    /// Folds a new reading into the player's song, part by part: a part the player left as the scanner produced it
    /// (`scanned`) takes the new reading; a part they changed is kept. If they changed the lyrics or chords, the new
    /// sections aren't applied but offered, so no edit is ever lost.
    static func merge(_ new: Song, into current: Song, scanned: ScannedParts) -> Outcome {
        let mine = ScannedParts(current), fresh = ScannedParts(new)
        var song = current
        if mine.title == scanned.title, !new.title.isEmpty { song.title = new.title }
        if mine.artist == scanned.artist { song.artist = new.artist }
        if mine.key == scanned.key { song.key = new.key }
        var offered: Song?
        if fresh.sections != mine.sections {
            if mine.sections == scanned.sections {
                song.sections = new.sections
            } else {
                offered = song
                offered?.sections = new.sections
            }
        }
        return Outcome(updated: ScannedParts(song) == mine ? nil : song, offered: offered)
    }

    static func image(_ data: Data) -> CGImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    static func jpeg(_ image: CGImage) -> Data? {
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        return CGImageDestinationFinalize(dest) ? data as Data : nil
    }
}

/// Each scanned song's reading, on this phone only (Application Support/Scans, file protection on). Deleted with the
/// song, and with an account's songs when the player signs out. Never uploaded or backed up.
struct ScanArchive {
    struct Entry: Codable, Hashable {
        var reading: ScanReading
        /// What the scanner produced last time, to tell the player's edits apart.
        var scanned: ScannedParts
        var version: Int
        /// A better reading the player hasn't decided on (they had edited the song).
        var offered: Song?
    }

    private var directory: URL { URL.applicationSupportDirectory.appending(path: "Scans", directoryHint: .isDirectory) }
    private func file(_ songID: String) -> URL {
        directory.appending(path: songID.replacingOccurrences(of: "/", with: "_") + ".json")
    }

    func save(_ entry: Entry, for songID: String) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? folder.setResourceValues(values)
            try APIClient.encoder.encode(entry).write(to: file(songID), options: [.atomic, .completeFileProtection])
        } catch {
            assertionFailure("Couldn’t keep the scan reading: \(error)")
        }
    }

    func entry(for songID: String) -> Entry? {
        guard let data = try? Data(contentsOf: file(songID)) else { return nil }
        return try? APIClient.decoder.decode(Entry.self, from: data)
    }

    /// Song IDs with a stored reading.
    var songIDs: [String] {
        ((try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? [])
            .filter { $0.pathExtension == "json" }.map { $0.deletingPathExtension().lastPathComponent }
    }

    func remove(_ songID: String) { try? FileManager.default.removeItem(at: file(songID)) }

    /// Signing out: an account's readings go; a guest's (still on the phone) stay.
    func removeAll(except keep: Set<String>) {
        for id in songIDs where !keep.contains(id) { remove(id) }
    }
}
