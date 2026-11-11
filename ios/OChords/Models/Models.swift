import Foundation

// Domain models. Codable so they can be decoded from the API later.

struct Song: Identifiable, Hashable, Codable {
    var id: String
    var title: String
    var artist: String
    /// Original key, e.g. "C".
    var key: String
    var tempo: Int = 80
    var sections: [SongSection]
    var source: SongSource = .imported
    var uploader: UserSummary? = nil
    var versionName: String? = nil
    var rating: Double? = nil
    var plays: Int? = nil
    var isMyanmar: Bool = true
    var addedAt: Date = .now
    var myNote: String? = nil
    /// Shared in Explore. Only known for your own sheets; nil leaves the server's setting unchanged.
    var isPublic: Bool? = nil

    /// Unique chords in order of first appearance.
    var usedChords: [String] {
        var seen = Set<String>(), out: [String] = []
        for s in sections {
            for c in s.bars + s.lines.flatMap(ChordLine.chords(in:)) where !seen.contains(c) {
                seen.insert(c); out.append(c)
            }
        }
        return out
    }

    var sectionCount: Int { sections.filter { $0.kind != .flow }.count }
}

enum SongSource: String, Codable, Hashable {
    case imported, community, listened, shared
    var label: String {
        switch self {
        case .imported: "Imported"
        case .community: "Community"
        case .listened: "Listened"
        case .shared: "Shared"
        }
    }
}

struct SongSection: Identifiable, Hashable, Codable {
    var id = UUID().uuidString
    var kind: SectionKind
    var label: String
    var note: String? = nil
    /// ChordPro-style lines: "[C]lyric [G/B]lyric".
    var lines: [String] = []
    /// Instrumental bars.
    var bars: [String] = []
    /// "Then play" flow: (kind, label).
    var flow: [FlowStep] = []
    /// Strumming pattern written on the sheet ("↓ ↓ ↓ ↑ ↓"), if it gave one.
    var strumPattern: String? = nil

    private enum CodingKeys: String, CodingKey {
        case id, kind, label, note, lines, bars, flow
        case strumPattern = "strum"
    }

    /// The sheet's pattern, or a common one for the kind of section.
    var strum: String? {
        if let strumPattern { return strumPattern }
        switch kind {
        case .verse, .chorus, .bridge: return "↓ ↓↑ ↑↓↑"
        case .music, .intro: return "↓ ↓ ↓ ↓"
        case .flow: return nil
        }
    }
}

struct FlowStep: Hashable, Codable {
    var kind: SectionKind
    var label: String
}

struct UserSummary: Identifiable, Hashable, Codable {
    var id: String
    var name: String
    var handle: String
    var initials: String { String(name.split(separator: " ").compactMap(\.first).prefix(2)) }
    var tier: Tier = .openString
    var sheets: Int = 0
    var followers: Int = 0
    var city: String? = nil
}

enum Tier: Int, Codable, CaseIterable, Hashable {
    case openString = 1, strummer, chordKeeper, sessionPlayer, maestro
    var title: String { ["Open String", "Strummer", "Chord Keeper", "Session Player", "Maestro"][rawValue - 1] }
    var roman: String { ["I", "II", "III", "IV", "V"][rawValue - 1] }
    var minPoints: Int { [0, 300, 1000, 3000, 8000][rawValue - 1] }
    var colorHex: UInt32 { [0x8E8A84, 0x0F766E, 0xFFB800, 0x1E3A8A, 0x6D28D9][rawValue - 1] }
    var perks: [String] {
        switch self {
        case .openString: ["Scan and save unlimited sheets", "Full learning path and tools"]
        case .strummer: ["Share sheets publicly in Explore", "Suggest corrections on other sheets", "Follow uploaders"]
        case .chordKeeper: ["“Trusted” mark on your uploads", "Your uploads skip the review queue", "1 free rest note each week"]
        case .sessionPlayer: ["Approve corrections on community sheets", "Public setlists can be featured", "Custom profile colour"]
        case .maestro: ["Help moderate reports", "Early access to new features", "Maestro badge next to your name"]
        }
    }
    var next: Tier? { Tier(rawValue: rawValue + 1) }
    static func forPoints(_ p: Int) -> Tier { allCases.last { p >= $0.minPoints } ?? .openString }
}

struct Profile: Hashable, Codable {
    var user: UserSummary
    var email: String
    var points: Int
    var grooveDays: Int
    var bestGroove: Int
    var dailyGoalMinutes: Int
    var todayMinutes: Int
    /// Minutes practised for each of the last 7 days (oldest first).
    var week: [Int]
    var restNotes: Int
    var songsLearned: Int
    var lessonsDone: Int
    /// O Chords Pro, as the account last heard from the App Store (`AppState.store` keeps it current on this phone).
    var isPro: Bool = false
}

struct Setlist: Identifiable, Hashable, Codable {
    var id: String
    var name: String
    var note: String
    var songIDs: [String]
    var date: String? = nil
}

struct CommunitySheet: Identifiable, Hashable, Codable {
    var id: String { song.id }
    var song: Song
    var votes: Int
    var comments: Int
    var difficulty: String
}

struct AppNotification: Identifiable, Hashable, Codable {
    enum Category: String, Codable, CaseIterable { case community, learning, system }
    var id: String
    var category: Category
    var title: String
    var body: String
    var time: String
    var unread: Bool
    var systemImage: String
    /// When it happened (from the API); demo data only has `time`.
    var createdAt: Date? = nil
}

// MARK: - Learning

struct LearnPosition: Identifiable, Hashable, Codable {
    var id: String
    var number: Int
    var title: String
    var subtitle: String
    var frets: [LearnFret]
}

struct LearnFret: Identifiable, Hashable, Codable {
    enum State: String, Codable { case done, current, locked }
    enum Kind: String, Codable { case lesson, encore, soundcheck }
    var id: String
    var title: String
    var state: State
    var kind: Kind = .lesson
    var stars: Int = 0
}

struct LessonQuestion: Identifiable, Hashable, Codable {
    enum Kind: String, Codable { case pickChord, buildBar, listen }
    var id: String
    var kind: Kind
    var prompt: String
    var detail: String
    var options: [String]
    var answer: String
}

// MARK: - Chords

struct ChordVoicing: Hashable, Codable {
    var label: String? = nil
    /// Six characters low E → high e: x = mute, 0 = open, n = fret.
    var pattern: String
    /// (string 0–5, fret, finger 1–4)
    var dots: [ChordDot]
    var barre: Barre? = nil
    var tip: String = ""
    var baseFret: Int = 1

    var isBarre: Bool { barre != nil }
}

struct ChordDot: Hashable, Codable { var string: Int; var fret: Int; var finger: Int }
struct Barre: Hashable, Codable { var fret: Int; var from: Int; var to: Int }

struct ChordInfo: Hashable, Codable {
    var name: String
    var full: String
    var voicings: [ChordVoicing]
}
