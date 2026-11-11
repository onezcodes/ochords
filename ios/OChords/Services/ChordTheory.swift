import Foundation

/// Transposition, capo and easy-chord rules from the "KEY · CAPO · EASY CHORDS" note.
enum ChordTheory {
    static let names = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    static let index: [String: Int] = ["C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6,
                                       "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11]

    /// Transpose a single note-rooted symbol ("Am7" by +2 → "Bm7").
    static func transposeRoot(_ chord: String, by t: Int) -> String {
        guard let first = chord.first, "ABCDEFG".contains(first) else { return chord }
        var root = String(first)
        var rest = chord.dropFirst()
        if let acc = rest.first, acc == "#" || acc == "b" { root.append(acc); rest = rest.dropFirst() }
        guard let i = index[root] else { return chord }
        return names[((i + t) % 12 + 12) % 12] + rest
    }

    /// Transpose a full chord including slash bass ("G/B").
    static func transpose(_ chord: String, by t: Int) -> String {
        guard t % 12 != 0 else { return chord }
        var parts = chord.split(separator: "/").map { transposeRoot(String($0), by: t) }
        // Spell the bass with sharps in sharp-side keys (E/G#, not E/Ab).
        let sharp = ["Ab": "G#", "Eb": "D#", "Bb": "A#"]
        if parts.count > 1, ["E", "A", "B", "D", "F#", "C#"].contains(where: { parts[0].hasPrefix($0) }), let s = sharp[parts[1]] {
            parts[1] = s
        }
        return parts.joined(separator: "/")
    }

    static let easySwaps: [String: String] = ["F": "Fmaj7", "Bm": "Bm7", "B": "B7", "F#m": "F#m7", "Bb": "Bbmaj7", "C#m": "C#m7", "Fm": "Fm7"]

    static let openShapes: Set<String> = ["C", "D", "E", "G", "A", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7", "Fmaj7", "Bm7", "Cadd9", "Asus2", "Dsus4"]

    static func isBarre(_ chord: String) -> Bool {
        !openShapes.contains(String(chord.split(separator: "/").first ?? ""))
    }

    struct Shown: Hashable {
        var name: String
        /// Set when the easy swap replaced the chord.
        var from: String?
        var isEasy: Bool { from != nil }
    }

    /// Chord as the player's fingers see it: sounding shift minus capo, then the easy swap.
    static func shown(_ chord: String, transpose t: Int, capo: Int, easy: Bool) -> Shown {
        let raw = transpose(chord, by: t - capo)
        guard easy else { return Shown(name: raw) }
        let parts = raw.split(separator: "/", maxSplits: 1).map(String.init)
        if let e = easySwaps[parts[0]] { return Shown(name: e, from: raw) }
        return Shown(name: raw)
    }

    struct CapoOption: Hashable, Identifiable {
        var id: Int { capo }
        var capo: Int
        var shapeKey: String
        var shapes: [String]
        var barres: Int
    }

    /// Capo 0–7 options whose shapes are C/G/D/A/E; ranked by barre count.
    static func capoOptions(chords: [String], songKey: String, transpose t: Int, current: Int) -> [CapoOption] {
        let all = [0, 1, 2, 3, 4, 5, 7].map { cp -> CapoOption in
            let shapes = chords.map { transpose($0, by: t - cp) }
            return CapoOption(capo: cp, shapeKey: transposeRoot(songKey, by: t - cp), shapes: shapes, barres: shapes.filter(isBarre).count)
        }
        return Array(all.filter { $0.capo == 0 || $0.capo == current || ["C", "G", "D", "A", "E"].contains($0.shapeKey) }.prefix(4))
    }
}

/// Parses "[C]lyric [G/B]more" into chord-over-word segments.
enum ChordLine {
    struct Segment: Hashable {
        var chord: String?
        var text: String
        /// Whether a space follows this segment.
        var trailingSpace: Bool
    }

    static func chords(in line: String) -> [String] {
        var out: [String] = []
        var i = line.startIndex
        while let open = line[i...].firstIndex(of: "["), let close = line[open...].firstIndex(of: "]") {
            out.append(String(line[line.index(after: open)..<close]))
            i = line.index(after: close)
        }
        return out
    }

    static func parse(_ line: String) -> [Segment] {
        var segs: [Segment] = []
        var pending: String? = nil
        var buffer = ""
        var i = line.startIndex

        func flushText() {
            guard !buffer.isEmpty else { return }
            let startsWithSpace = buffer.first?.isWhitespace == true
            if startsWithSpace, !segs.isEmpty { segs[segs.count - 1].trailingSpace = true }
            let words = buffer.split(whereSeparator: \.isWhitespace).map(String.init)
            let endSpace = buffer.last?.isWhitespace == true
            for (n, w) in words.enumerated() {
                segs.append(Segment(chord: n == 0 ? pending : nil, text: w, trailingSpace: n < words.count - 1 || endSpace))
                if n == 0 { pending = nil }
            }
            buffer = ""
        }

        while i < line.endIndex {
            let c = line[i]
            if c == "[", let close = line[i...].firstIndex(of: "]") {
                flushText()
                if let p = pending { segs.append(Segment(chord: p, text: "", trailingSpace: true)) }
                pending = String(line[line.index(after: i)..<close])
                i = line.index(after: close)
            } else {
                buffer.append(c)
                i = line.index(after: i)
            }
        }
        flushText()
        if let p = pending { segs.append(Segment(chord: p, text: "", trailingSpace: false)) }
        return segs
    }
}
