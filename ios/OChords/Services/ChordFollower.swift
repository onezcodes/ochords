import Foundation
import Accelerate

/// Which of a few known chords is sounding. Works on a "chroma" — how much of each of the 12 notes is in the sound —
/// compared with each chord's expected chroma. The expected chroma includes a guitar string's overtones (the 3rd
/// harmonic of C is a G, the 5th an E), so the overtones in a real strum count for the right chord instead of
/// blurring major and minor.
enum ChordRecognizer {
    /// Samples analysed per reading: ≈ 0.25 s at 48 kHz — long enough to tell neighbouring notes apart in the bass,
    /// short enough to follow a change of chord.
    static let window = 12288
    private static let fftSize = 16384
    /// Overtones per note in a chord's expected chroma, and how fast they fade.
    private static let overtones = 6
    private static let overtoneFade = 0.7

    /// The 12-note profile of a window of sound (C = 0 … B = 11), normalised to length 1; nil if it's silent.
    static func chroma(_ samples: [Float], sampleRate: Double) -> [Double]? {
        let n = min(samples.count, fftSize)
        guard n > 1024 else { return nil }
        var input = [Float](repeating: 0, count: fftSize)
        var hann = [Float](repeating: 0, count: n)
        vDSP_hann_window(&hann, vDSP_Length(n), Int32(vDSP_HANN_NORM))
        vDSP_vmul(Array(samples.suffix(n)), 1, hann, 1, &input, 1, vDSP_Length(n))

        let half = fftSize / 2
        var real = [Float](repeating: 0, count: half), imaginary = [Float](repeating: 0, count: half)
        var mags = [Float](repeating: 0, count: half)
        let log2n = vDSP_Length(log2(Double(fftSize)))
        guard let setup = vDSP_create_fftsetup(log2n, FFTRadix(kFFTRadix2)) else { return nil }
        defer { vDSP_destroy_fftsetup(setup) }
        real.withUnsafeMutableBufferPointer { re in
            imaginary.withUnsafeMutableBufferPointer { im in
                var split = DSPSplitComplex(realp: re.baseAddress!, imagp: im.baseAddress!)
                input.withUnsafeBufferPointer { inp in
                    inp.baseAddress!.withMemoryRebound(to: DSPComplex.self, capacity: half) {
                        vDSP_ctoz($0, 2, &split, 1, vDSP_Length(half))
                    }
                }
                vDSP_fft_zrip(setup, &split, 1, log2n, FFTDirection(FFT_FORWARD))
                vDSP_zvabs(&split, 1, &mags, 1, vDSP_Length(half))
            }
        }

        // Spectral peaks from low E (≈ 75 Hz) to ≈ 2 kHz, each credited to its nearest note. Only peaks count, so
        // the noise floor between notes doesn't smear into every note; square-root compression keeps one loud
        // string from drowning the rest of the chord.
        let binHz = sampleRate / Double(fftSize)
        let lo = Int(75 / binHz), hi = min(half - 2, Int(2000 / binHz))
        guard lo > 1, hi > lo else { return nil }
        var chroma = [Double](repeating: 0, count: 12)
        let floor = (mags[lo...hi].max() ?? 0) * 0.02
        for b in lo...hi where mags[b] > floor && mags[b] >= mags[b - 1] && mags[b] >= mags[b + 1] {
            let hz = Double(b) * binHz
            let note = Int((12 * log2(hz / 440)).rounded()) + 9
            chroma[((note % 12) + 12) % 12] += sqrt(Double(mags[b]))
        }
        return normalised(chroma)
    }

    /// The chroma a guitar playing `chord` would show (nil for a name that isn't a chord).
    static func template(_ chord: String) -> [Double]? {
        guard let (root, intervals, bass) = parse(chord) else { return nil }
        var t = [Double](repeating: 0, count: 12)
        var notes = intervals.map { (root + $0) % 12 }
        if let bass { notes.append(bass) }
        for (i, note) in notes.enumerated() {
            // The bass of a slash chord rings as strongly as the root; added colour notes a little less.
            let weight = i == 0 || (bass != nil && i == notes.count - 1) ? 1.0 : i < 3 ? 0.9 : 0.6
            for k in 1...overtones {
                let shift = Int((12 * log2(Double(k))).rounded())
                t[(note + shift) % 12] += weight * pow(overtoneFade, Double(k - 1))
            }
        }
        return normalised(t)
    }

    /// How alike two chromas are: 1 = the same.
    static func similarity(_ a: [Double], _ b: [Double]) -> Double { zip(a, b).reduce(0) { $0 + $1.0 * $1.1 } }

    /// Root note (0–11), intervals above it, and slash bass note, from a chord name.
    static func parse(_ chord: String) -> (root: Int, intervals: [Int], bass: Int?)? {
        let parts = chord.split(separator: "/", maxSplits: 1).map(String.init)
        guard let main = parts.first, let root = note(main) else { return nil }
        let quality = String(main.dropFirst(main.dropFirst().first.map { "#b".contains($0) } == true ? 2 : 1))
        let bass = parts.count > 1 ? note(parts[1]) : nil
        return (root, intervals(quality), bass)
    }

    private static func note(_ name: String) -> Int? {
        guard let first = name.first, "ABCDEFG".contains(first) else { return nil }
        var key = String(first)
        if let acc = name.dropFirst().first, acc == "#" || acc == "b" { key.append(acc) }
        return ChordTheory.index[key]
    }

    /// Chord tones (semitones above the root) for a chord quality ("", "m", "7", "maj7", "sus4", "m7b5" …).
    static func intervals(_ q: String) -> [Int] {
        let minor = q.hasPrefix("m") && !q.hasPrefix("maj")
        var tones = [0, minor ? 3 : 4, 7]
        if q.contains("sus2") { tones[1] = 2 } else if q.contains("sus") { tones[1] = 5 }
        if q.contains("dim") || q.contains("b5") || q.contains("°") || q.contains("ø") { tones[1] = 3; tones[2] = 6 }
        if q.contains("aug") || q.contains("+") || q.contains("#5") { tones[2] = 8 }
        if q == "5" { tones = [0, 7] }
        if q.contains("maj7") || q.contains("M7") { tones.append(11) }
        else if q.contains("dim7") || q.contains("°7") { tones.append(9) }
        else if q.contains("7") || q.contains("9") || q.contains("11") || q.contains("13") || q.contains("ø") { tones.append(10) }
        if q.contains("6") { tones.append(9) }
        if q.contains("9") || q.contains("add2") { tones.append(2) }
        return tones
    }

    private static func normalised(_ v: [Double]) -> [Double]? {
        let length = sqrt(v.reduce(0) { $0 + $1 * $1 })
        return length > 0 ? v.map { $0 / length } : nil
    }
}

/// Follows a player through a song's chords: listens for the next chord in the song and moves on when it's clearly
/// being played. Only ever looks a couple of chords ahead, so repeated progressions (C G Am F, C G Am F…) are
/// followed in order, and a missed or skipped chord is caught up. Pure logic: chromas in, positions out.
struct ChordFollower {
    struct Step: Equatable {
        /// The chord as it sounds (after any transposition).
        var chord: String
        /// Where it is in the song: which stage item, and which lyric line or instrumental bar.
        var item: Int
        var line: Int?
        var bar: Int? = nil
        /// Which time through a repeated line ("( … ) x2"): pass 1 of 2, 2 of 2.
        var pass = 1
        var passes = 1
    }

    /// The order a section's lines are played in, honouring repeat marks written in the lyrics:
    /// "…lyrics… x2" or "( … ) x 2" at the end of a line repeats that line; a "(" opening one line and ") x2" closing
    /// a later one repeats the lines in between. Also "×2", "(x2)", "2x". A ")" without a count just closes the group.
    static func playOrder(lines: [String]) -> [(line: Int, pass: Int, passes: Int)] {
        let texts = lines.map { line in
            ChordLine.parse(line).map { $0.text + ($0.trailingSpace ? " " : "") }.joined().trimmingCharacters(in: .whitespaces)
        }
        var out: [(Int, Int, Int)] = []
        func play(_ range: ClosedRange<Int>, times: Int) {
            for pass in 1...times { for l in range { out.append((l, pass, times)) } }
        }
        var i = 0
        while i < texts.count {
            // A group opened here and closed (with a count) on this or a later line.
            if texts[i].hasPrefix("(") || texts[i].hasPrefix("（") {
                var j = i
                var closed: (end: Int, times: Int)?
                while j < texts.count, j - i < 12 {
                    if let n = repeatCount(texts[j]) { closed = (j, n); break }
                    // Closed without a count: just a bracketed aside.
                    if texts[j].contains(")") || texts[j].contains("）") { break }
                    j += 1
                }
                if let closed {
                    play(i...closed.end, times: closed.times)
                    i = closed.end + 1
                    continue
                }
            }
            play(i...i, times: repeatCount(texts[i]) ?? 1)
            i += 1
        }
        return out
    }

    /// The repeat count written at the end of a line ("x2", "x 2", "×2", "(x2)", "2x", "2 times"), if any (2–9).
    static func repeatCount(_ text: String) -> Int? {
        let t = text.trimmingCharacters(in: .whitespaces)
        let patterns = [
            /(?i)(?:^|[\s\)\]）])[\(\[]?\s*[x×]\s*([2-9])\s*[\)\]]?\s*$/,
            /(?i)(?:^|[\s\)\]）])[\(\[]?\s*([2-9])\s*(?:x|×|times)\s*[\)\]]?\s*$/,
        ]
        for p in patterns { if let m = t.firstMatch(of: p), let n = Int(m.1) { return n } }
        return nil
    }

    /// A chord must beat the one being played by this much…
    static let margin = 0.04
    /// …and resemble a chord at all this much…
    static let minimumMatch = 0.6
    /// …in this many readings in a row (≈ 0.2 s) to move on one chord; skipping one takes longer.
    static let readingsToAdvance = 2
    static let readingsToSkip = 4
    /// Skipping a chord needs a strong match: a wrong chord played in its place often resembles the one after it
    /// (Dm for F), and a false skip is worse than a late one — the next strum of the right chord catches up.
    static let skipMatch = 0.85
    /// A reading counts when it's this much louder than the room (the quietest moment of the last few seconds)…
    static let aboveRoom: Float = 2.5
    /// …and at least this loud, but never has to be louder than `loudestGate`, however noisy the room.
    static let quietest: Float = 0.003
    static let loudestGate: Float = 0.01
    /// Readings the room level is taken over (≈ 3 s).
    private static let roomMemory = 30
    /// Chords this alike differ by one note (C → Cmaj7, Am → Am7, G → G/B): a strum can't reliably tell them apart,
    /// so a change between them is followed as one chord. Genuinely different chords score 0.77 or less.
    static let indistinct = 0.9

    let steps: [Step]
    /// The chord being played (-1 = not started: waiting for the first chord).
    private(set) var position = -1
    private var templates: [String: [Double]] = [:]
    private var pending: (target: Int, count: Int)?
    private var levels: [Float] = []

    /// - Parameter steps: every chord in play order. A chord repeated back-to-back — or followed by one only a note
    ///   different — can't be heard changing, so it's folded into the first.
    init(steps: [Step]) {
        var templates: [String: [Double]] = [:]
        for s in steps where templates[s.chord] == nil { templates[s.chord] = ChordRecognizer.template(s.chord) }
        var folded: [Step] = []
        for s in steps {
            if let last = folded.last {
                if last.chord == s.chord { continue }
                if let a = templates[last.chord], let b = templates[s.chord], ChordRecognizer.similarity(a, b) > Self.indistinct { continue }
            }
            folded.append(s)
        }
        self.steps = folded
        self.templates = templates
    }

    var current: Step? { steps.indices.contains(position) ? steps[position] : nil }

    /// The player tapped a line (or moved to a section): wait for its first chord, looking from where they are so a
    /// repeated section resolves to the next time it comes round.
    mutating func resync(item: Int, line: Int?) {
        let matches = { (s: Step) in s.item == item && (line == nil || s.line == line) }
        let from = max(0, position)
        // Wait for that line's first chord (as if the chord before it were being played).
        if let i = steps[from...].firstIndex(where: matches) ?? steps.firstIndex(where: matches) { position = i - 1 }
        pending = nil
    }

    /// Feeds one reading. Returns true when the position moved.
    mutating func hear(_ chroma: [Double]?, level: Float) -> Bool {
        levels.append(level)
        if levels.count > Self.roomMemory { levels.removeFirst(levels.count - Self.roomMemory) }
        let room = levels.min() ?? 0
        let gate = max(Self.quietest, min(Self.loudestGate, room * Self.aboveRoom))
        guard let chroma, level >= gate, !steps.isEmpty else { return false }
        func score(_ i: Int) -> Double {
            guard steps.indices.contains(i), let t = templates[steps[i].chord] else { return -1 }
            return ChordRecognizer.similarity(chroma, t)
        }
        // Not started: the song's first chord — or the first sung line's, for a player who skips the intro. Starting
        // anywhere else is what tapping a line is for (a guess here can put the player a whole round out).
        let firstLine = steps.firstIndex { $0.line != nil } ?? 0
        let ahead = position < 0 ? Array(Set([0, firstLine])).sorted() : [position + 1, position + 2]
        let here = position < 0 ? -1 : score(position)
        guard let best = ahead.filter({ steps.indices.contains($0) }).max(by: { score($0) < score($1) }),
              score(best) >= Self.minimumMatch, score(best) >= here + Self.margin else {
            pending = nil
            return false
        }
        let count = pending?.target == best ? pending!.count + 1 : 1
        pending = (best, count)
        let skipping = position >= 0 && best > position + 1
        if skipping, score(best) < Self.skipMatch {
            pending = nil
            return false
        }
        guard count >= (skipping ? Self.readingsToSkip : Self.readingsToAdvance) else { return false }
        position = best
        pending = nil
        return true
    }
}
