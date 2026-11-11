import Foundation
import CoreGraphics

/// A draft song read from positioned text, plus what the user should check.
struct ParsedSheet {
    var song: Song
    var lowConfidence: [String]
    var chordNotes: [String: String]
    var needsLyrics: [ScanLineRef]
    var regions: [ScanRegion]
}

/// Turns OCR / PDF text lines into sections of ChordPro lines ("[C]lyric [G/B]lyric").
///
/// Layout rules: a row that is mostly chord names is a chord row; it pairs with the lyric row right below it,
/// and each chord lands on the syllable under its x position. Rows like "Chorus", "Verse 2:", "Intro: C G Am F"
/// start sections. Rows before the first section/chord row hold the title, artist and "Key / Capo / BPM".
enum ChordSheetParser {

    static func parse(_ lines: [ScanTextLine], readsMyanmar: Bool) -> ParsedSheet {
        let rows = sheetFurniture(separateInline(groupRows(lines.map(splittingGluedChords)).map(withoutStrayDots).map(classify)))
        var state = Builder(readsMyanmar: readsMyanmar)
        state.margin = rows.map(\.box.minX).min() ?? 0
        state.width = (rows.map(\.box.maxX).max() ?? 1) - state.margin
        var bodyStart = rows.firstIndex { if case .header = $0.kind { true } else if case .chords = $0.kind { true } else { false } } ?? rows.count
        // Only the first four rows can be title/credits/meta (a strumming pattern doesn't count towards them); later
        // rows before the first chord row are lyrics…
        var preambleRows = 4
        // …unless a section label near the top says where the song starts ("V1, …"): everything above it — with no
        // chords to play — is the song's details.
        if let label = rows.prefix(9).firstIndex(where: { if case .header = $0.kind { true } else { false } }),
           !rows[..<label].contains(where: { if case .chords = $0.kind { true } else { false } }) {
            bodyStart = label
            preambleRows = label
        } else if bodyStart < rows.count, case .chords = rows[bodyStart].kind,
                  let label = rows.prefix(9).firstIndex(where: { if case .header = $0.kind { true } else { false } }), label == bodyStart + 1 {
            // A chord row right above the opening label belongs to the song ("F … Em" over "V1, …").
            preambleRows = bodyStart
        }
        var split = 0, counted = 0
        while split < bodyStart, counted < preambleRows {
            if case .strum = rows[split].kind {} else { counted += 1 }
            split += 1
        }
        state.readPreamble(Array(rows[..<split]))
        state.readBody(Array(rows[split...]))
        return state.result()
    }

    // MARK: - Chords

    private static let chordRegex = try! Regex(#"^[A-G][#b]?(?:maj|min|dim|aug|sus|add|m|M|°|ø|\+)?\d{0,2}(?:(?:maj|sus|add|b|#|\+|-)\d{1,2})*(?:\([^)]*\))?(?:/[A-G][#b]?)?$"#)
    private static let neutralTokens: Set<String> = ["x2", "x3", "x4", "×2", "×3", "×4", "(x2)", "(x3)", "(x4)", "n.c.", "nc", "n.c", "…", "..."]

    static func isChord(_ s: String) -> Bool { (try? chordRegex.wholeMatch(in: s)) != nil }

    /// Characters a reader makes out of bar lines and brackets around chords: "|", "I", "l", "1", "!".
    private static let barMarks = Set("|Il1!()[]")

    /// A word in a chord row read with bar lines in mind: only bar marks ("||", "1)", "(I") is a bar line; a chord
    /// with bar marks stuck to it ("(IC", "Am|", "G1)") is that chord.
    static func barToken(_ text: String) -> Token {
        if text.allSatisfy({ barMarks.contains($0) || $0 == ":" || $0 == "/" }) { return .neutral }
        var t = Substring(text)
        while let f = t.first, barMarks.contains(f) { t = t.dropFirst() }
        if case .chord(let c, _) = token(String(t)) { return .chord(c, note: "Bar line read as a letter — please check") }
        while let l = t.last, barMarks.contains(l), t.count > 1 {
            t = t.dropLast()
            if case .chord(let c, _) = token(String(t)) { return .chord(c, note: "Bar line read as a letter — please check") }
        }
        return .word
    }

    /// A lone "." or "," in a Myanmar line is a speck the reader picked up (often in the gap before a chord written
    /// at the line's end), not part of the lyric. An ellipsis ("…", "....") is kept.
    static func withoutStrayDots(_ row: Row) -> Row {
        guard row.words.contains(where: { isMyanmar($0.text) }) else { return row }
        var r = row
        r.words.removeAll { $0.text == "." || $0.text == "," || $0.text == "·" }
        return r
    }

    /// Close chords that the reader ran together into one word ("AmEm", "CG/B", "Am7F"), as separate chords:
    /// each part a chord, at least one longer than a letter (so a word like "CAB" or "BAD" isn't split).
    static func gluedChords(_ text: String) -> [String]? {
        guard !isChord(text), !isMyanmar(text), text.count >= 3 else { return nil }
        let chars = Array(text)
        // Fewest parts that cover the word, each a chord.
        var best = [[String]?](repeating: nil, count: chars.count + 1)
        best[0] = []
        for end in 1...chars.count {
            for start in 0..<end {
                guard let before = best[start] else { continue }
                let part = String(chars[start..<end])
                if isChord(part), best[end] == nil || best[end]!.count > before.count + 1 { best[end] = before + [part] }
            }
        }
        guard let parts = best[chars.count], parts.count >= 2, parts.contains(where: { $0.count > 1 }) else { return nil }
        return parts
    }

    /// Splits glued chords into words, each placed across the original word's width by its length.
    static func splittingGluedChords(_ line: ScanTextLine) -> ScanTextLine {
        var words: [ScanWord] = []
        for w in line.words {
            guard let parts = gluedChords(w.text) else { words.append(w); continue }
            let total = CGFloat(parts.reduce(0) { $0 + $1.count })
            var at: CGFloat = 0
            for part in parts {
                let len = CGFloat(part.count)
                words.append(ScanWord(text: part, box: CGRect(x: w.box.minX + w.box.width * at / total, y: w.box.minY,
                                                              width: w.box.width * len / total, height: w.box.height)))
                at += len
            }
        }
        return ScanTextLine(words: words, confidence: line.confidence)
    }

    enum Token: Equatable {
        case chord(String, note: String?)
        case neutral
        case word
    }

    /// Classifies one token, fixing common OCR slips ("am" → Am, "Arn" → Am, "G/8" → G/B, "GB" → G/B).
    static func token(_ raw: String) -> Token {
        if raw == unreadableText || neutralTokens.contains(raw.lowercased()) || raw.allSatisfy({ "|/-–—.:*%".contains($0) }) { return .neutral }
        var t = raw.replacingOccurrences(of: "♯", with: "#").replacingOccurrences(of: "♭", with: "b")
        t = t.trimmingCharacters(in: CharacterSet(charactersIn: "|[],.:;"))
        // Hold marks after a chord ("D-", "D…", "G~") — the chord is still D or G.
        if t.count > 1, let first = t.first, "ABCDEFG".contains(first) {
            t = String(t.reversed().drop { "-–—~…".contains($0) }.reversed())
        }
        if t.hasPrefix("("), t.hasSuffix(")"), t.filter({ $0 == "(" }).count == 1 { t = String(t.dropFirst().dropLast()) }
        guard !t.isEmpty else { return .neutral }
        if isChord(t) { return .chord(t, note: nil) }

        var f = t
        if let c = f.first, "abcdefg".contains(c) { f = c.uppercased() + f.dropFirst() }
        f = f.replacingOccurrences(of: "rn", with: "m")
        if let slash = f.firstIndex(of: "/") {
            let bass = f[f.index(after: slash)...].replacingOccurrences(of: "8", with: "B").replacingOccurrences(of: "0", with: "D")
            f = String(f[...slash]) + bass
        }
        if isChord(f) { return .chord(f, note: "Unclear chord — please check it") }

        // A small raised bass note ("Gᴮ") often reads as two capitals run together.
        if let m = f.wholeMatch(of: /([A-G][#b]?(?:m7|maj7|m|7|sus2|sus4)?)([A-G])/) {
            let slash = "\(m.1)/\(m.2)"
            if isChord(slash) { return .chord(slash, note: "Superscript bass note read as a slash chord") }
        }
        return .word
    }

    // MARK: - Rows

    struct PlacedChord {
        var name: String
        var x: CGFloat
        var note: String?
    }

    enum RowKind {
        case header(SectionKind, label: String?, number: Int?, note: String?, chords: [PlacedChord])
        case chords([PlacedChord])
        case meta
        case lyric
        /// A strumming pattern written as arrows ("↓ ↓ ↓ ↑ ↓").
        case strum(String)
    }

    struct Row {
        var words: [ScanWord]
        var confidence: Float
        var kind: RowKind = .lyric
        var text: String { words.map(\.text).joined(separator: " ") }
        var box: CGRect { words.dropFirst().reduce(words.first?.box ?? .zero) { $0.union($1.box) } }
        /// Words that were actually read (no placeholders).
        var readWords: [ScanWord] { words.filter { $0.text != unreadableText } }
        var readText: String { readWords.map(\.text).joined(separator: " ") }
    }

    /// Joins pieces that sit on the same baseline (OCR often splits a spaced-out chord row).
    static func groupRows(_ lines: [ScanTextLine]) -> [Row] {
        var rows: [Row] = []
        for line in lines.sorted(by: { $0.box.midY < $1.box.midY }) where !line.words.isEmpty {
            let b = line.box
            if let last = rows.last {
                let lb = last.box
                let overlap = min(lb.maxY, b.maxY) - max(lb.minY, b.minY)
                if overlap > 0.5 * min(lb.height, b.height) {
                    // A second OCR pass can re-read words already on this row; keep the first reading.
                    let fresh = line.words.filter { w in
                        !last.words.contains { o in
                            let i = o.box.intersection(w.box)
                            return !i.isNull && i.width * i.height > 0.5 * min(o.box.width * o.box.height, w.box.width * w.box.height)
                        }
                    }
                    guard !fresh.isEmpty else { continue }
                    let unreadable = fresh.allSatisfy { $0.text == unreadableText }
                    rows[rows.count - 1].words = (last.words + fresh).sorted { $0.box.minX < $1.box.minX }
                    if !unreadable { rows[rows.count - 1].confidence = min(last.confidence, line.confidence) }
                    continue
                }
            }
            rows.append(Row(words: line.words.sorted { $0.box.minX < $1.box.minX }, confidence: line.confidence))
        }
        return rows
    }

    private static let headerRegex = try! Regex(
        #"^[\[\(]?\s*(intro|verse|chorus|refrain|hook|pre[- ]?chorus|bridge|outro|ending|coda|music|interlude|instrumental|solo|break|tag|v|ch|cho)\.?\s*(\d+)?\s*[\]\)]?\s*(:)?\s*((?:x|×)\s*\d|\((?:x|×)\s*\d\))?"#
    ).ignoresCase()

    private static let metaRegex = try! Regex(#"\b(key|capo|tempo|bpm|time\s*sig|strum)\b"#).ignoresCase()

    static func classify(_ row: Row) -> Row {
        var row = row
        let text = row.text

        if let m = try? headerRegex.prefixMatch(in: text), let nameRange = m.output[1].range {
            let name = text[nameRange].lowercased()
            let number = m.output[2].range.flatMap { Int(text[$0]) }
            let hasColon = m.output[3].range != nil
            let repeatNote = m.output[4].range.map { "×" + text[$0].filter(\.isNumber) }
            let short = ["v", "ch", "cho"].contains(name)
            // Words that end past the matched header ("Verses", "Chorus of angels") are the rest of the row.
            let consumed = text.distance(from: text.startIndex, to: m.range.upperBound)
            var pos = 0
            var restWords: [ScanWord] = []
            for w in row.words {
                if pos + w.text.count > consumed { restWords.append(w) }
                pos += w.text.count + 1
            }
            // The rest must be empty or chords — "Chorus of angels" is a lyric.
            let restTokens = restWords.map { (token($0.text), $0) }
            let restIsChords = restTokens.allSatisfy { if case .word = $0.0 { false } else { true } }
            let restHasChord = restTokens.contains { if case .chord = $0.0 { true } else { false } }
            if restIsChords, !short || number != nil || hasColon || restHasChord {
                let (kind, label) = sectionKind(name)
                let chords = restTokens.compactMap { t, w -> PlacedChord? in
                    if case .chord(let c, let note) = t { PlacedChord(name: c, x: w.box.minX, note: note) } else { nil }
                }
                row.kind = .header(kind, label: label, number: number, note: repeatNote, chords: chords)
                return row
            }
        }

        var tokens = row.words.map { (token($0.text), $0) }
        // A row that already has a couple of clean chords is a chord row; read the rest of it as one, with bar lines
        // the reader turned into letters ("(|C" → "(IC", "|)" → "1)") taken as bar lines.
        if tokens.filter({ if case .chord = $0.0 { true } else { false } }).count >= 2 {
            tokens = tokens.map { t, w in
                if case .word = t { (barToken(w.text), w) } else { (t, w) }
            }
        }
        let counted = tokens.filter { $0.0 != .neutral }
        let chords = tokens.compactMap { t, w -> PlacedChord? in
            if case .chord(let c, let note) = t { PlacedChord(name: c, x: w.box.minX, note: note) } else { nil }
        }
        if !chords.isEmpty, Double(chords.count) >= 0.75 * Double(counted.count) {
            row.kind = .chords(chords)
        } else if text.count < 48, text.contains(metaRegex) {
            row.kind = .meta
        } else {
            row.kind = .lyric
        }
        return row
    }

    // MARK: - Chord diagrams and strumming patterns

    /// A mark from a chord diagram: string markers and fret numbers ("x", "o", "0", "23", "020", "x32010").
    static func isDiagramWord(_ text: String) -> Bool {
        text.wholeMatch(of: /[xXoO0-9○●•×]+/) != nil && text.wholeMatch(of: /(?i)[x×][2-9]/) == nil
    }

    private static let arrows: Set<Character> = ["↓", "↑", "⇩", "⇧", "⬇", "⬆", "🡓", "🡑"]
    static func isArrowWord(_ text: String) -> Bool { !text.isEmpty && text.allSatisfy { arrows.contains($0) } }

    /// Things printed on a sheet that aren't the song itself:
    /// - chord diagrams: rows of string markers and fret numbers ("x o X X 0 0", "23 020", "x32010"), and the row of
    ///   chord names next to them (a legend, not chords to play);
    /// - strumming patterns written as arrows, which become `.strum` rows — split off any credit sharing the line
    ///   ("တေးရေး - ဝင့်နိုင်  ↓ ↓ ↓ ↑ ↓") and joined with a pattern continuing on the next line.
    static func sheetFurniture(_ rows: [Row]) -> [Row] {
        func isDiagram(_ row: Row) -> Bool {
            let words = row.readWords.map(\.text)
            return !words.isEmpty && words.allSatisfy(isDiagramWord) && (words.count >= 3 || words.contains { $0.count >= 3 })
        }
        func near(_ a: Row, _ b: Row) -> Bool { abs(a.box.midY - b.box.midY) < max(a.box.height, b.box.height) * 2.5 }

        /// A chord row at the top of the page whose chords all sit past the end of the line below can't be that line's
        /// chords: it's the sheet's chord legend ("F Em Am Dm" top right, beside the title).
        func isLegend(_ i: Int) -> Bool {
            guard i < 4, case .chords(let chords) = rows[i].kind, i + 1 < rows.count, case .lyric = rows[i + 1].kind,
                  // Above the line, not chords written at its end (those share its baseline).
                  rows[i].box.maxY <= rows[i + 1].box.minY + rows[i + 1].box.height * 0.3 else { return false }
            let end = rows[i + 1].box.maxX
            return chords.allSatisfy { $0.x > end }
        }

        var out: [Row] = []
        for (i, row) in rows.enumerated() {
            if isDiagram(row) || isLegend(i) { continue }
            if case .chords = row.kind,
               (i > 0 && isDiagram(rows[i - 1]) && near(rows[i - 1], row)) || (i + 1 < rows.count && isDiagram(rows[i + 1]) && near(row, rows[i + 1])) {
                continue
            }
            let arrowWords = row.words.filter { isArrowWord($0.text) }
            guard !arrowWords.isEmpty, arrowWords.count == row.words.count || arrowWords.count >= 3 else {
                out.append(row)
                continue
            }
            let rest = row.words.filter { !isArrowWord($0.text) }
            if !rest.isEmpty { out.append(classify(Row(words: rest, confidence: row.confidence))) }
            let pattern = arrowWords.map(\.text).joined(separator: " ")
            if let last = out.last, case .strum(let before) = last.kind, near(last, row) {
                out[out.count - 1].words += arrowWords
                out[out.count - 1].kind = .strum(before + " " + pattern)
            } else {
                out.append(Row(words: arrowWords, confidence: row.confidence, kind: .strum(pattern)))
            }
        }
        return out
    }

    // MARK: - Title and credits

    /// Words that introduce a credit, singer first. Myanmar: တေးဆို (singer), ဆိုသူ, တေးသံရှင်, သီဆို, အဆို;
    /// တေးရေး (songwriter), ရေးစပ်, ရေးသူ, သံစဉ် (melody), စာသား (lyrics), ဂီတ/တီးလုံး (music).
    private static let singerWords = ["တေးဆိုရှင်", "တေးဆို", "ဆိုသူ", "တေးသံရှင်", "သီဆို", "အဆို",
                                      "sung by", "singer", "vocals", "vocal", "artist", "by"]
    private static let writerWords = ["တေးရေးဆရာ", "တေးရေး", "ရေးစပ်", "ရေးသူ", "သံစဉ်", "စာသား", "ဂီတ", "တီးလုံး",
                                      "written by", "words by", "lyrics", "lyric", "music", "composed by", "composer",
                                      "arranged by", "arr.", "chords by", "original", "cover"]

    /// A credit line ("by The Wanderers", "တေးရေး-ကန် တေးဆို-မျိုးကြီး", "တေးရေး၊တေးဆို-မာရဂ္ဂ") and the names in it.
    static func credit(_ text: String) -> (singer: String?, writer: String?)? {
        let lower = text.lowercased()
        // Every keyword occurrence, earliest first (longer keywords win where they overlap).
        var hits: [(range: Range<String.Index>, singer: Bool)] = []
        for (words, singer) in [(singerWords, true), (writerWords, false)] {
            for w in words.sorted(by: { $0.count > $1.count }) {
                var from = lower.startIndex
                while let r = lower.range(of: w, range: from..<lower.endIndex) {
                    // Latin keywords must be whole words ("by", not "baby").
                    let isLatin = w.first?.isASCII == true
                    let before = r.lowerBound == lower.startIndex ? nil : lower[lower.index(before: r.lowerBound)]
                    let after = r.upperBound == lower.endIndex ? nil : lower[r.upperBound]
                    // Myanmar ones must be followed by a separator or space ("တေးဆို-…", "ဆိုသူ …"), so a title that
                    // merely contains one ("ဂီတချစ်သူ") isn't a credit.
                    let whole = isLatin
                        ? (before.map { !$0.isLetter } ?? true) && (after.map { !$0.isLetter } ?? true)
                        : after.map { " -–—:=၊။/|,()".contains($0) || $0.isWhitespace } ?? true
                    if whole, !hits.contains(where: { $0.range.overlaps(r) }) { hits.append((r, singer)) }
                    from = r.upperBound
                }
            }
        }
        guard !hits.isEmpty else { return nil }
        // An English keyword only makes a credit at the start of the line ("by …", "Lyrics: …"), not mid-lyric.
        hits.sort { $0.range.lowerBound < $1.range.lowerBound }
        let leadingLatin = lower[..<hits[0].range.lowerBound].trimmingCharacters(in: .whitespaces).isEmpty
        if hits.allSatisfy({ lower[$0.range].first?.isASCII == true }), !leadingLatin { return nil }

        var singer: String?, writer: String?
        let separators = CharacterSet(charactersIn: " -–—:=၊။/|,()[]").union(.whitespaces)
        for (i, hit) in hits.enumerated() {
            let end = i + 1 < hits.count ? hits[i + 1].range.lowerBound : text.endIndex
            let name = String(text[hit.range.upperBound..<end]).trimmingCharacters(in: separators)
            guard !name.isEmpty else { continue }
            if hit.singer { singer = singer ?? name } else { writer = writer ?? name }
        }
        return (singer, writer)
    }

    /// A title as written: without quotes, a "Title:" / "ခေါင်းစဉ်:" label or a trailing full stop.
    static func cleanTitle(_ text: String) -> String {
        var t = text.trimmingCharacters(in: .whitespaces)
        if let m = t.firstMatch(of: /(?i)^(?:title|song|ခေါင်းစဉ်|သီချင်းအမည်|သီချင်း)\s*[:\-–]\s*/) { t = String(t[m.range.upperBound...]) }
        // A time signature written before the title ("6/8 beat Mary").
        if let m = t.firstMatch(of: /(?i)^\d{1,2}\s*\/\s*\d{1,2}\s*(?:beat|time)?\s*[:\-–]?\s*/), m.range.upperBound < t.endIndex {
            t = String(t[m.range.upperBound...])
        }
        t = t.trimmingCharacters(in: CharacterSet(charactersIn: "\"“”'‘’「」『』«»*").union(.whitespaces))
        t = t.trimmingCharacters(in: CharacterSet(charactersIn: ".။"))
        return t
    }

    /// A key/capo/tempo row without its key/capo/tempo words ("ညီလေး   Key-G" → "ညီလေး").
    static func withoutMeta(_ row: Row) -> Row {
        var kept: [ScanWord] = []
        var skipValue = false
        for w in row.words {
            if skipValue { skipValue = false; continue }
            if let m = w.text.firstMatch(of: /(?i)^(key|capo|tempo|bpm)\b[:=\-]?(.*)$/) {
                // "Key" / "Key:" alone: its value is the next word.
                if m.2.isEmpty { skipValue = true }
                continue
            }
            if w.text.wholeMatch(of: /(?i)\d{2,3}\s*bpm|[:=\-]/) != nil { continue }
            kept.append(w)
        }
        var r = row
        r.words = kept
        r.kind = .lyric
        return r
    }

    // MARK: - Chords and labels inside Myanmar lines

    /// A section label written at the start of a lyric line: "V1", "V1,", "Verse 2", "Ch", "C1", "R", "Bridge:", "(Pre)".
    /// One-letter forms need a number ("C1", "B2") — bare C or B is a chord.
    private static let inlineLabelRegex = try! Regex(
        #"^[\[\(]?(verse|v|chorus|cho|ch|c|refrain|ref|r|pre[- ]?chorus|pre|bridge|b|intro|int|outro|solo|music)\.?(\d+)?[\]\)]?[:.,\-–]?$"#
    ).ignoresCase()

    private static func inlineLabel(_ text: String) -> (kind: SectionKind, label: String?, number: Int?)? {
        guard let m = try? inlineLabelRegex.wholeMatch(in: text), let nameRange = m.output[1].range else { return nil }
        let name = text[nameRange].lowercased()
        let number = m.output[2].range.flatMap { Int(text[$0]) }
        if ["v", "c", "r", "b"].contains(name), number == nil { return nil }
        let canonical = ["c": "chorus", "r": "refrain", "ref": "refrain", "b": "bridge", "int": "intro", "pre": "pre-chorus"][name] ?? name
        let (kind, label) = sectionKind(canonical)
        return (kind, label, number)
    }

    /// Myanmar sheets often put chords on the lyric line itself — at the end ("…တာတွေ  Bm"), or merged into it when
    /// the chord row sits tight above tall Myanmar letters — and a section label at its start ("V1 မသေချာ…").
    /// Myanmar lyrics never contain Latin chord names, so in a Myanmar line those words are chords: they're lifted
    /// into a chord row over the line (joining the chord row just above, if there is one), and a leading label starts
    /// its section. English lines are left alone ("A", "Am" can be words).
    static func separateInline(_ rows: [Row]) -> [Row] {
        var out: [Row] = []
        for row in rows {
            // "Ch  G  Em" over a lyric line: the chords are that line's, not instrumental bars (only an intro, solo
            // or other music section plays its chords as bars).
            if case .header(let kind, let label, let number, let note, let chords) = row.kind, kind != .music, !chords.isEmpty {
                let chordWords = row.words.filter { w in chords.contains { $0.x == w.box.minX } }
                out.append(Row(words: row.words.filter { w in !chordWords.contains { $0.box == w.box } }, confidence: row.confidence,
                               kind: .header(kind, label: label, number: number, note: note, chords: [])))
                out.append(Row(words: chordWords, confidence: row.confidence, kind: .chords(chords)))
                continue
            }
            guard case .lyric = row.kind, isMyanmar(row.text) else { out.append(row); continue }
            var words = row.words
            var header: Row?
            if let first = words.first, !isMyanmar(first.text), words.count > 1, let label = inlineLabel(first.text) {
                header = Row(words: [first], confidence: row.confidence,
                             kind: .header(label.kind, label: label.label, number: label.number, note: nil, chords: []))
                words.removeFirst()
            }
            let chordWords = words.filter { w in
                guard !isMyanmar(w.text), case .chord = token(w.text) else { return false }
                return true
            }
            let lyricWords = words.filter { w in !chordWords.contains { $0.box == w.box && $0.text == w.text } }
            guard header != nil || !chordWords.isEmpty, lyricWords.contains(where: { isMyanmar($0.text) }) else {
                out.append(row)
                continue
            }
            let lyric = Row(words: lyricWords, confidence: row.confidence, kind: .lyric)
            // The chord row right above (if any) belongs to this line too.
            let aboveIndex: Int? = {
                guard let last = out.last, case .chords = last.kind,
                      lyric.box.minY - last.box.maxY < max(last.box.height, lyric.box.height) * 1.6 else { return nil }
                return out.count - 1
            }()
            if let header {
                if let aboveIndex { out.insert(header, at: aboveIndex) } else { out.append(header) }
            }
            if !chordWords.isEmpty {
                let placed = chordWords.compactMap { w -> PlacedChord? in
                    if case .chord(let c, let note) = token(w.text) { PlacedChord(name: c, x: w.box.minX, note: note) } else { nil }
                }
                if let i = aboveIndex.map({ header == nil ? $0 : $0 + 1 }), case .chords(let existing) = out[i].kind {
                    out[i].words += chordWords
                    out[i].kind = .chords((existing + placed).sorted { $0.x < $1.x })
                } else {
                    // Its own chord row, at the lyric's height (so it reads as directly above the line).
                    out.append(Row(words: chordWords, confidence: row.confidence, kind: .chords(placed)))
                }
            }
            out.append(lyric)
        }
        return out
    }

    private static func sectionKind(_ name: String) -> (SectionKind, String?) {
        switch name {
        case "intro": (.music, "INTRO")
        case "verse", "v": (.verse, nil)
        case "chorus", "ch", "cho", "refrain", "hook": (.chorus, "CHORUS")
        case "bridge": (.bridge, "BRIDGE")
        case "outro", "ending", "coda": (.music, "OUTRO")
        case "tag": (.music, "TAG")
        default:
            name.hasPrefix("pre") ? (.bridge, "PRE-CHORUS") : (.music, name == "solo" ? "SOLO" : "MUSIC")
        }
    }

    // MARK: - Lyrics

    static func isMyanmar(_ s: String) -> Bool { s.unicodeScalars.contains { (0x1000...0x109F).contains($0.value) } }

    /// An English-looking word ("Morning", "shine,", "don’t", "2"), as opposed to OCR noise like "ccosg:l" or "8/9".
    static func isWordLike(_ s: String) -> Bool {
        s.wholeMatch(of: /["“(]?(?:[A-Za-z][a-z]*(?:['’][a-z]+)?|[A-Z]{2,})[,.!?;:"”)]*|[0-9]+[,.]?|&|-|—/) != nil
    }

    /// Whether OCR gave us real words (Vision turns Myanmar script into low-confidence Latin noise).
    static func isReadable(_ row: Row, readsMyanmar: Bool) -> Bool {
        // Mostly placeholders: the line is in a script we couldn't read.
        guard row.readWords.count * 2 > row.words.count else { return false }
        let chars = row.readText.filter { !$0.isWhitespace }
        guard !chars.isEmpty else { return false }
        if isMyanmar(row.text) { return readsMyanmar }
        guard row.confidence >= 0.45 else { return false }
        // Vision's reading of Myanmar script looks like "ccosg:l :co:ces cuçuccio" — letters, but not words.
        let words = row.readWords.map(\.text)
        let wordLike = words.filter(isWordLike).count
        let vowels = chars.filter { "aeiouyAEIOUY".contains($0) }.count
        return Double(wordLike) >= 0.75 * Double(words.count) && vowels * 6 >= chars.count
    }

    /// Indexes (in characters) where a Myanmar syllable starts: a consonant, independent vowel or digit
    /// that isn't a killed final (with ်) and isn't stacked under the previous letter (after ္).
    static func myanmarSyllableStarts(_ chars: [Character]) -> [Int] {
        chars.indices.filter { i in
            guard let first = chars[i].unicodeScalars.first?.value,
                  (0x1000...0x102A).contains(first) || (0x1040...0x1049).contains(first) || first == 0x103F else { return false }
            if chars[i].unicodeScalars.contains(where: { $0.value == 0x103A }) { return false }
            if i > 0, chars[i - 1].unicodeScalars.last?.value == 0x1039 { return false }
            return true
        }
    }

    /// Puts each chord on the syllable under it: "[C]lyric wo[G/B]rds".
    static func merge(_ chords: [PlacedChord], over lyric: Row) -> String {
        let words = lyric.readWords
        var chars: [Character] = []
        var starts: [Int] = []
        for (k, w) in words.enumerated() {
            if k > 0 { chars.append(" ") }
            starts.append(chars.count)
            chars += Array(w.text)
        }
        var inserts: [(offset: Int, order: Int, text: String)] = []
        for (n, c) in chords.enumerated() {
            if let k = words.firstIndex(where: { c.x < $0.box.maxX }) {
                let w = words[k]
                let frac = (c.x - w.box.minX) / max(w.box.width, 0.0001)
                let count = w.text.count
                var within = frac <= 0.34 ? 0 : min(count - 1, Int((frac * CGFloat(count)).rounded()))
                // Myanmar: never split a syllable (e.g. the killed final in စက်), only start one.
                if within > 0, isMyanmar(w.text) {
                    let syllables = myanmarSyllableStarts(Array(w.text))
                    within = syllables.min { abs($0 - within) < abs($1 - within) } ?? 0
                }
                inserts.append((starts[k] + within, n, "[\(c.name)]"))
            } else {
                inserts.append((chars.count, n, " [\(c.name)]"))
            }
        }
        for ins in inserts.sorted(by: { ($0.offset, $0.order) > ($1.offset, $1.order) }) {
            chars.insert(contentsOf: Array(ins.text), at: ins.offset)
        }
        return String(chars).trimmingCharacters(in: .whitespaces)
    }

    /// Width, in characters, that chord-only lines are laid out across.
    static let columns = 48

    /// "[C]          [G/B]      " — spaces keep each chord's place across the line, and trailing spaces mark where
    /// the unread lyric line ended on the page, so typed lyrics can be lined up later.
    static func chordsOnly(_ chords: [PlacedChord], margin: CGFloat, width: CGFloat, lyricEnd: CGFloat? = nil) -> String {
        func column(_ x: CGFloat) -> Int { Int(((x - margin) / max(width, 0.0001)) * CGFloat(columns)) }
        var out = "", col = 0
        for c in chords.sorted(by: { $0.x < $1.x }) {
            let target = max(col, column(c.x))
            out += String(repeating: " ", count: target - col) + "[\(c.name)]"
            col = target
        }
        if let lyricEnd, column(lyricEnd) > col { out += String(repeating: " ", count: column(lyricEnd) - col) }
        return out
    }

    /// Lines up the chords of a chord-only line over newly typed lyrics, each on the nearest word start.
    /// The words of a "[C]lyric [G]line" without its chords.
    static func words(of line: String) -> String {
        ChordLine.parse(line).map { $0.text + ($0.trailingSpace ? " " : "") }.joined().trimmingCharacters(in: .whitespaces)
    }

    /// The line with its words replaced by `newWords`, each chord kept with its word. The old and new words are
    /// lined up (longest common subsequence): a chord on a word that's still there stays on it, at the same place in
    /// the word; a chord on a changed word goes to the matching spot between the unchanged words around it (a typo
    /// fixed keeps its chord exactly); a chord after the last word stays at the end.
    static func reword(_ line: String, with newWords: String) -> String {
        // Each chord's column in the plain text.
        var chords: [(col: Int, name: String)] = []
        var plain: [Character] = []
        var i = line.startIndex
        while i < line.endIndex {
            if line[i] == "[", let close = line[i...].firstIndex(of: "]") {
                chords.append((plain.count, String(line[line.index(after: i)..<close])))
                i = line.index(after: close)
            } else {
                plain.append(line[i])
                i = line.index(after: i)
            }
        }
        let new = Array(newWords.trimmingCharacters(in: .whitespaces))
        guard !chords.isEmpty else { return String(new) }
        guard !new.isEmpty else { return chords.map { "[\($0.name)]" }.joined(separator: " ") }

        func words(_ text: [Character]) -> [(start: Int, word: String)] {
            text.indices.filter { text[$0] != " " && ($0 == 0 || text[$0 - 1] == " ") }.map { s in
                (s, String(text[s..<(text[s...].firstIndex(of: " ") ?? text.count)]))
            }
        }
        let old = words(plain), now = words(new)
        let anchors = commonWords(old.map(\.word), now.map(\.word))
        let oldEnd = plain.lastIndex { $0 != " " }.map { $0 + 1 } ?? 0

        var placed: [(at: Int, name: String)] = []
        for c in chords {
            var at = new.count                                      // after the last word
            if c.col < oldEnd, !now.isEmpty {
                let w = old.lastIndex { $0.start <= c.col } ?? 0
                let inWord = c.col - (old.isEmpty ? 0 : old[w].start)
                let prev = anchors.last { $0.old <= w }, next = anchors.first { $0.old > w }
                if let prev, prev.old == w {
                    // The chord's word is still there.
                    let word = now[prev.new]
                    at = word.start + min(inWord, max(0, word.word.count - 1))
                } else {
                    // Between the unchanged words around it: the old words there, and what replaced them.
                    let lo = prev.map { ($0.old, $0.new) } ?? (-1, -1)
                    let hi = next.map { ($0.old, $0.new) } ?? (old.count, now.count)
                    let oldGap = hi.0 - lo.0 - 1, newGap = hi.1 - lo.1 - 1
                    if newGap <= 0 {
                        // Its words were deleted: the next word that's left, or the end of the line.
                        at = next.map { now[$0.new].start } ?? new.count
                    } else {
                        let position = Double(w - lo.0 - 1) / Double(max(1, oldGap - 1))
                        let target = lo.1 + 1 + Int((position * Double(newGap - 1)).rounded())
                        let word = now[min(max(0, target), now.count - 1)]
                        // A word swapped for another (the gaps match) keeps its place in the word.
                        at = word.start + (oldGap == newGap ? min(inWord, max(0, word.word.count - 1)) : 0)
                    }
                }
            }
            if let last = placed.last?.at, at < last { at = last }  // keep the chords in order
            placed.append((at, c.name))
        }
        var out = new
        for p in placed.reversed() {
            out.insert(contentsOf: Array(p.at >= new.count ? " [\(p.name)]" : "[\(p.name)]"), at: min(p.at, out.count))
        }
        return String(out)
    }

    /// Pairs of indexes (old, new) of the words two lists have in common, in order (longest common subsequence).
    private static func commonWords(_ a: [String], _ b: [String]) -> [(old: Int, new: Int)] {
        guard !a.isEmpty, !b.isEmpty else { return [] }
        var len = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in (0..<a.count).reversed() {
            for j in (0..<b.count).reversed() {
                len[i][j] = a[i] == b[j] ? len[i + 1][j + 1] + 1 : max(len[i + 1][j], len[i][j + 1])
            }
        }
        var pairs: [(Int, Int)] = [], i = 0, j = 0
        while i < a.count, j < b.count {
            if a[i] == b[j] { pairs.append((i, j)); i += 1; j += 1 } else if len[i + 1][j] >= len[i][j + 1] { i += 1 } else { j += 1 }
        }
        return pairs
    }

    static func place(chordsOf line: String, onto lyric: String) -> String {
        var chords: [(col: Int, name: String)] = []
        var col = 0
        var i = line.startIndex
        while i < line.endIndex {
            if line[i] == "[", let close = line[i...].firstIndex(of: "]") {
                chords.append((col, String(line[line.index(after: i)..<close])))
                i = line.index(after: close)
            } else {
                col += 1
                i = line.index(after: i)
            }
        }
        let text = Array(lyric.trimmingCharacters(in: .whitespaces))
        guard !text.isEmpty else { return line }
        let wordStarts = text.indices.filter { $0 == 0 || (text[$0 - 1] == " " && text[$0] != " ") }
        // Where the lyric ended on the page (trailing spaces), or else one chord-gap past the last chord.
        let last = Double(chords.last?.col ?? 0)
        let extent = line.hasSuffix(" ") ? Double(col) : chords.count > 1 ? last + last / Double(chords.count - 1) : last * 2
        var offsets: [Int] = []
        for c in chords {
            let target = Int((extent > 0 ? Double(c.col) / extent : 0) * Double(text.count))
            var best = wordStarts.min { abs($0 - target) < abs($1 - target) } ?? 0
            if let prev = offsets.last, best <= prev { best = min(text.count, max(prev + 1, target)) }
            offsets.append(best)
        }
        var out = text
        for (k, c) in chords.enumerated().reversed() {
            let at = min(offsets[k], out.count)
            out.insert(contentsOf: Array(at == text.count ? " [\(c.name)]" : "[\(c.name)]"), at: at)
        }
        return String(out)
    }

    // MARK: - Key

    /// Best-fitting major key (or its relative minor when the song starts and ends there).
    static func guessKey(_ chords: [String]) -> String {
        func parts(_ c: String) -> (root: Int, minor: Bool)? {
            guard let first = c.first else { return nil }
            var root = String(first)
            let rest = c.dropFirst()
            if let acc = rest.first, acc == "#" || acc == "b" { root.append(acc) }
            guard let i = ChordTheory.index[root] else { return nil }
            let quality = c.dropFirst(root.count).split(separator: "/").first.map(String.init) ?? ""
            return (i, quality.hasPrefix("m") && !quality.hasPrefix("maj"))
        }
        let parsed = chords.compactMap(parts)
        guard let first = parsed.first, let last = parsed.last else { return "C" }
        let degrees: [(Int, Bool)] = [(0, false), (2, true), (4, true), (5, false), (7, false), (9, true)]
        var best = (tonic: 0, score: -1)
        for tonic in 0..<12 {
            var score = parsed.filter { p in degrees.contains { ($0.0 + tonic) % 12 == p.root && $0.1 == p.minor } }.count * 2
            if first.root == tonic && !first.minor { score += 3 }
            if last.root == tonic && !last.minor { score += 3 }
            if score > best.score { best = (tonic, score) }
        }
        let relMinor = (best.tonic + 9) % 12
        if first.minor && first.root == relMinor && last.root == relMinor { return ChordTheory.names[relMinor] + "m" }
        return ChordTheory.names[best.tonic]
    }

    // MARK: - Building

    private struct Builder {
        let readsMyanmar: Bool
        /// Left edge and width of the text on the page, for laying out chord-only lines.
        var margin: CGFloat = 0
        var width: CGFloat = 1
        var title = ""
        var artist = ""
        var key: String?
        var tempo: Int?
        var capo: Int?
        var sections: [SongSection] = []
        var sectionBoxes: [CGRect] = []
        var needs: [ScanLineRef] = []
        var verseCount = 0
        var lowConfidence: [String] = []
        var notes: [String: String] = [:]
        var readableLyrics = 0
        var sawMyanmar = false
        /// A strumming pattern for the whole song (written with the title), and one waiting for the next section.
        var songStrum: String?
        var pendingStrum: String?

        init(readsMyanmar: Bool) { self.readsMyanmar = readsMyanmar }

        mutating func readMeta(_ row: Row) {
            let text = row.text
            if key == nil, let m = text.firstMatch(of: /(?i)key\s*(?:of)?\s*[:=\-]?\s*([A-Ga-g][#b]?m?)\b/) {
                key = m.1.prefix(1).uppercased() + m.1.dropFirst()
            }
            if capo == nil, let m = text.firstMatch(of: /(?i)capo\s*[:=\-]?\s*(\d{1,2})/) { capo = Int(m.1) }
            if tempo == nil, let m = text.firstMatch(of: /(?i)(\d{2,3})\s*bpm|tempo\s*[:=\-]?\s*(\d{2,3})/) {
                tempo = Int(m.1 ?? m.2 ?? "")
            }
        }

        /// The lines above the first chords: title, credits ("by …", "တေးရေး-… တေးဆို-…") and key/capo/tempo.
        mutating func readPreamble(_ rows: [Row]) {
            var candidates: [Row] = []
            var writer: String?
            for row in rows {
                if case .strum(let pattern) = row.kind {
                    songStrum = pattern
                    continue
                }
                if case .meta = row.kind {
                    readMeta(row)
                    // "ညီလေး   Key-G": what's left after the key/capo/tempo is still a title candidate.
                    let rest = ChordSheetParser.withoutMeta(row)
                    if !rest.words.isEmpty { candidates.append(rest) }
                    continue
                }
                if let credit = ChordSheetParser.credit(row.readText) {
                    // The singer is the artist; a songwriter only if no singer turns up.
                    if artist.isEmpty, let name = credit.singer { artist = name }
                    writer = writer ?? credit.writer
                    if isMyanmar(row.text) { sawMyanmar = true }
                    continue
                }
                // Fret numbers from a chord diagram printed beside the title ("…ရင်  23 020").
                var row = row
                if row.words.contains(where: { !ChordSheetParser.isDiagramWord($0.text) }) {
                    row.words.removeAll { ChordSheetParser.isDiagramWord($0.text) }
                }
                candidates.append(row)
            }
            defer { if artist.isEmpty, let writer { artist = writer } }
            let readable = candidates.filter { isReadable($0, readsMyanmar: readsMyanmar) && !ChordSheetParser.cleanTitle($0.readText).isEmpty }
            if candidates.contains(where: { isMyanmar($0.text) }) { sawMyanmar = true }
            guard let best = readable.max(by: { titleScore($0, among: readable) < titleScore($1, among: readable) }) else { return }

            var text = ChordSheetParser.cleanTitle(best.readText)
            // "ညီလေး - မျိုးကြီး": title, then the singer.
            if artist.isEmpty, let m = text.firstMatch(of: /^(.+?)\s+[-–—]\s+(.+)$/) {
                text = String(m.1)
                artist = String(m.2)
            }
            title = text
            // A short readable line right under the title is usually the artist ("The Wanderers").
            if artist.isEmpty, let i = readable.firstIndex(where: { $0.box == best.box }), i + 1 < readable.count {
                let next = readable[i + 1]
                if next.readText.count <= 40, next.box.height <= best.box.height { artist = ChordSheetParser.cleanTitle(next.readText) }
            }
        }

        /// How title-like a line is: mostly its type size, plus a little for being centred and near the top, minus
        /// for being long like a lyric.
        private func titleScore(_ row: Row, among rows: [Row]) -> Double {
            let tallest = rows.map(\.box.height).max() ?? 1
            var score = Double(row.box.height / max(tallest, 0.0001))
            if abs(row.box.midX - (margin + width / 2)) < width * 0.12 { score += 0.15 }
            if row.box == rows.first?.box { score += 0.1 }
            if ChordSheetParser.cleanTitle(row.readText).count > 32 { score -= 0.3 }
            return score
        }

        private mutating func start(_ kind: SectionKind, label: String?, number: Int? = nil, note: String? = nil) {
            var label = label ?? ""
            if kind == .verse {
                verseCount = number ?? (verseCount + 1)
                label = "VERSE \(verseCount)"
            }
            sections.append(SongSection(kind: kind, label: label, note: note, strumPattern: pendingStrum))
            pendingStrum = nil
            sectionBoxes.append(.null)
        }

        /// The section new lyric lines go into.
        private mutating func lyricSection() {
            if sections.last == nil || sections.last?.kind == .music { start(.verse, label: nil) }
        }

        private mutating func track(_ row: Row) {
            guard !sectionBoxes.isEmpty else { return }
            sectionBoxes[sectionBoxes.count - 1] = sectionBoxes[sectionBoxes.count - 1].union(row.box)
        }

        private mutating func note(_ chords: [PlacedChord], confidence: Float) {
            for c in chords {
                if let n = c.note { notes[c.name] = n }
                if (c.note != nil || confidence < 0.5), !lowConfidence.contains(c.name) { lowConfidence.append(c.name) }
            }
        }

        private mutating func addLine(_ text: String, missingLyrics: Bool) {
            sections[sections.count - 1].lines.append(text)
            if missingLyrics { needs.append(ScanLineRef(sectionID: sections[sections.count - 1].id, line: sections[sections.count - 1].lines.count - 1)) }
        }

        mutating func readBody(_ rows: [Row]) {
            var i = 0
            while i < rows.count {
                let row = rows[i]
                let next = i + 1 < rows.count ? rows[i + 1] : nil
                let gapBelow = next.map { $0.box.minY - row.box.maxY } ?? .infinity

                switch row.kind {
                case .header(let kind, let label, let number, let note, let chords):
                    start(kind, label: label, number: number, note: note)
                    track(row)
                    if !chords.isEmpty {
                        sections[sections.count - 1].bars += chords.map(\.name)
                        self.note(chords, confidence: row.confidence)
                    }

                case .chords(let chords):
                    self.note(chords, confidence: row.confidence)
                    // Chords in brackets or between bar lines ("( | C | Am | Dm | G | )") are played on their own,
                    // not over the line below.
                    let text = row.text.trimmingCharacters(in: .whitespaces)
                    let instrumental = (text.hasPrefix("(") && text.hasSuffix(")")) || text.contains("|")
                    let lyricBelow: Row? = {
                        guard !instrumental, let next, case .lyric = next.kind, gapBelow < max(row.box.height, next.box.height) * 1.6 else { return nil }
                        return next
                    }()
                    if let lyric = lyricBelow {
                        lyricSection()
                        track(row); track(lyric)
                        if isMyanmar(lyric.text) { sawMyanmar = true }
                        if isReadable(lyric, readsMyanmar: readsMyanmar) {
                            readableLyrics += 1
                            addLine(merge(chords, over: lyric), missingLyrics: false)
                        } else {
                            addLine(chordsOnly(chords, margin: margin, width: width, lyricEnd: lyric.box.maxX), missingLyrics: true)
                        }
                        i += 2
                        continue
                    }
                    // No lyric right below. A wide gap means OCR skipped a line it couldn't read.
                    let skippedLine = !readsMyanmar && gapBelow > row.box.height * 1.2 && gapBelow != .infinity
                    if sections.last == nil {
                        if skippedLine { start(.verse, label: nil) } else { start(.music, label: "INTRO") }
                    }
                    track(row)
                    if sections[sections.count - 1].kind == .music && !skippedLine {
                        sections[sections.count - 1].bars += chords.map(\.name)
                    } else {
                        lyricSection()
                        addLine(chordsOnly(chords, margin: margin, width: width), missingLyrics: !readsMyanmar)
                    }

                case .lyric:
                    // A thin unread strip right under the previous line is that line's lower marks, not a line.
                    let readable = isReadable(row, readsMyanmar: readsMyanmar)
                    if !readable, i > 0, row.box.minY - rows[i - 1].box.maxY < row.box.height * 0.3,
                       row.box.height < rows[i - 1].box.height * 0.7 {
                        track(row)
                        break
                    }
                    // Unread ink with nothing near it (the page edge, a smudge) isn't a lyric line.
                    let gapAbove = i > 0 ? row.box.minY - rows[i - 1].box.maxY : .infinity
                    if !readable, gapAbove > row.box.height * 2.5, gapBelow > row.box.height * 2.5 { break }
                    lyricSection()
                    track(row)
                    if isMyanmar(row.text) { sawMyanmar = true }
                    if readable {
                        readableLyrics += 1
                        addLine(row.readText, missingLyrics: false)
                    } else {
                        addLine("", missingLyrics: true)
                    }

                case .meta:
                    readMeta(row)

                case .strum(let pattern):
                    // Right under a section's heading it's that section's pattern; before any section it's the
                    // song's; after a section's lines, it's for the next one.
                    if let last = sections.last, last.lines.isEmpty, last.bars.isEmpty {
                        sections[sections.count - 1].strumPattern = pattern
                    } else if sections.isEmpty {
                        songStrum = songStrum ?? pattern
                    } else {
                        pendingStrum = pattern
                    }
                }
                i += 1
            }
        }

        func result() -> ParsedSheet {
            var sections = sections.filter { !$0.lines.isEmpty || !$0.bars.isEmpty }
            if let songStrum {
                for i in sections.indices where sections[i].strumPattern == nil && sections[i].kind != .music { sections[i].strumPattern = songStrum }
            }
            for i in sections.indices where sections[i].kind == .music && sections[i].note == nil && !sections[i].bars.isEmpty {
                sections[i].note = "\(sections[i].bars.count) bars"
            }
            let keptIDs = Set(sections.map(\.id))
            var song = Song(id: UUID().uuidString, title: title, artist: artist, key: "C", tempo: tempo ?? 80, sections: sections)
            song.key = key ?? Self.keyGuess(song)
            song.isMyanmar = sawMyanmar || (!needs.isEmpty && needs.count >= readableLyrics)
            if let capo, capo > 0 { song.myNote = "Capo \(capo)" }

            let regions = zip(self.sections, sectionBoxes).compactMap { s, box -> ScanRegion? in
                guard keptIDs.contains(s.id), !box.isNull, box.minY < 1 else { return nil }
                return ScanRegion(kind: s.kind, x: box.minX, y: box.minY, width: box.width, height: min(box.height, 1 - box.minY))
            }
            return ParsedSheet(song: song, lowConfidence: Array(lowConfidence.prefix(5)), chordNotes: notes,
                               needsLyrics: needs.filter { keptIDs.contains($0.sectionID) }, regions: regions)
        }

        private static func keyGuess(_ song: Song) -> String {
            ChordSheetParser.guessKey(song.sections.flatMap { $0.bars + $0.lines.flatMap(ChordLine.chords(in:)) })
        }
    }
}
