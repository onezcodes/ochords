import Foundation

/// Estimates the chords of music playing nearby (a speaker, the radio, a band): the chord now, the changes heard so
/// far, and at the end one chord per bar with the key, tempo and a capo suggestion. Pure logic — chromas and loudness
/// in, estimates out — so it can be tested on synthesised music.
struct ChordEstimator {
    /// A stretch of one chord, as heard live.
    struct Segment: Equatable {
        var chord: String
        var start: Double
        /// 0–100: how clearly the sound matched this chord.
        var confidence: Int
    }

    struct Bar: Equatable {
        var chord: String
        var confidence: Int
    }

    struct Summary: Equatable {
        /// "C", "Am" …; empty until enough has been heard.
        var key: String
        var keyConfidence: Int
        var bpm: Int?
        /// The capo that makes the most chords open shapes (0: none needed).
        var capo: Int
        var bars: [Bar]
    }

    static let roots = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    /// Major, minor and dominant 7th on every root: what popular songs are mostly made of.
    static let names: [String] = ["", "m", "7"].flatMap { q in roots.map { $0 + q } }
    private static let templates = names.map { ChordRecognizer.template($0)! }
    /// A 7th chord holds the triad's notes (and a sung melody often touches the 7th), so it has to match clearly
    /// better before it's named.
    private static let bias: [Double] = names.map { $0.hasSuffix("7") ? -0.06 : 0 }

    /// The least a sound must fit its best chord to count as one (music measures 0.84–0.96).
    private static let chordLike = 0.8
    /// Seconds between chromas (`hear(chroma:at:)`).
    static let frameInterval = 0.1
    /// Loudness readings per second (`hear(loudness:)`).
    static let envelopeRate = 50.0
    /// Frames smoothed together for the live chord, and readings a new chord must lead for before it's shown.
    private static let smoothing = 3
    private static let switchAfter = 2

    private struct Frame {
        var time: Double
        /// Match with each of `names`; empty while it's quiet.
        var scores: [Double]
    }

    private var frames: [Frame] = []
    private var envelope: [Double] = []
    private(set) var segments: [Segment] = []
    private var challenger: (index: Int, count: Int)?
    private var segmentStart = 0

    /// Seconds of sound heard.
    var heard: Double { frames.last.map { $0.time + Self.frameInterval } ?? 0 }

    // MARK: Input

    /// A chroma every `frameInterval` (nil while it's too quiet to read).
    mutating func hear(chroma: [Double]?, at time: Double) {
        var scores = chroma.map { c in zip(Self.templates, Self.bias).map { ChordRecognizer.similarity(c, $0) + $1 } } ?? []
        // Room noise, talk and drums alone fit every chord a little (≈ 0.75) and none well: not a chord.
        if (scores.max() ?? 0) < Self.chordLike { scores = [] }
        frames.append(Frame(time: time, scores: scores))
        updateSegments()
    }

    /// The loudness (RMS) of the newest `1 / envelopeRate` s of sound.
    mutating func hear(loudness: Double) { envelope.append(loudness) }

    // MARK: Live

    private mutating func updateSegments() {
        let recent = frames.suffix(Self.smoothing).filter { !$0.scores.isEmpty }
        // The first chord only once the sound is steady, not on the edge of the first strum.
        guard recent.count >= (segments.isEmpty ? Self.smoothing : 2) else { return }
        let best = Self.argmax(Self.sum(recent.map(\.scores)))
        guard let current = segments.last, let index = Self.names.firstIndex(of: current.chord) else {
            segmentStart = frames.count - recent.count
            segments.append(Segment(chord: Self.names[best], start: frames[segmentStart].time, confidence: 0))
            refreshConfidence()
            return
        }
        if best == index {
            challenger = nil
        } else if challenger?.index == best {
            challenger?.count += 1
        } else {
            challenger = (best, 1)
        }
        if let c = challenger, c.count >= Self.switchAfter {
            challenger = nil
            segmentStart = frames.count - Self.switchAfter
            segments.append(Segment(chord: Self.names[c.index], start: frames[segmentStart].time, confidence: 0))
        }
        refreshConfidence()
    }

    private mutating func refreshConfidence() {
        guard let last = segments.last, let index = Self.names.firstIndex(of: last.chord) else { return }
        segments[segments.count - 1].confidence = Self.confidence(of: index, in: frames[segmentStart...].map(\.scores))
    }

    /// The chord most often heard right after `chord` so far — "next likely" while it's playing.
    func likelyNext(after chord: String) -> String? {
        var counts: [String: Int] = [:]
        for (a, b) in zip(segments, segments.dropFirst()) where a.chord == chord { counts[b.chord, default: 0] += 1 }
        return counts.max { $0.value < $1.value || ($0.value == $1.value && $0.key > $1.key) }?.key
    }

    // MARK: Summary

    /// Beats per minute from the rhythm of the loudness (strums, drums), or nil if there's no steady beat yet.
    var bpm: Double? { Self.tempo(Array(envelope.suffix(Int(20 * Self.envelopeRate)))) }

    /// One chord per bar of 4 beats, with key, tempo and capo. Quiet bars at either end are left out.
    func summary() -> Summary {
        let beat = bpm.map { 60 / $0 } ?? 0.5
        let phase = Self.beatPhase(envelope, period: beat)
        // Each beat's chord, then the bar line where the chord changes most often.
        var beats: [(scores: [Double], voiced: Double)] = []
        var t = phase
        while t < heard {
            let inBeat = frames.filter { $0.time >= t && $0.time < t + beat }
            let voiced = inBeat.filter { !$0.scores.isEmpty }
            beats.append((voiced.isEmpty ? [] : Self.sum(voiced.map(\.scores)), inBeat.isEmpty ? 0 : Double(voiced.count) / Double(inBeat.count)))
            t += beat
        }
        let labels = beats.map { $0.scores.isEmpty ? -1 : Self.argmax($0.scores) }
        let downbeat = (0..<4).max { a, b in Self.changes(labels, onBeat: a) < Self.changes(labels, onBeat: b) } ?? 0

        // Bars of 4 beats from the bar line; the beats before it (if any) are a pickup bar.
        let lastStrum = Self.lastOnset(envelope)
        var bars: [Bar] = []
        var start = 0, end = min(beats.count, downbeat == 0 ? 4 : downbeat)
        while start < beats.count {
            let from = phase + Double(start) * beat, to = phase + Double(end) * beat
            // The last chord ringing on after the playing stops isn't another bar.
            if from > lastStrum { break }
            let span = beats[start..<end]
            let voiced = span.reduce(0) { $0 + $1.voiced } / Double(span.count)
            let barFrames = frames.filter { $0.time >= from && $0.time < to }.map(\.scores)
            if voiced >= 0.3, let index = Self.argmaxOptional(Self.sum(barFrames.filter { !$0.isEmpty })) {
                bars.append(Bar(chord: Self.names[index], confidence: Self.confidence(of: index, in: barFrames)))
            } else if !bars.isEmpty {
                bars.append(Bar(chord: "", confidence: 0))
            }
            start = end
            end = min(beats.count, end + 4)
        }
        while bars.last?.chord == "" { bars.removeLast() }
        // A pickup on the same chord as the first full bar is part of it.
        if downbeat != 0, bars.count > 1, bars[0].chord == bars[1].chord { bars.removeFirst() }
        // A quiet bar in the middle keeps the chord around it.
        for i in bars.indices where bars[i].chord.isEmpty { bars[i] = Bar(chord: bars[i - 1].chord, confidence: min(50, bars[i - 1].confidence)) }

        let (key, keyConfidence) = Self.key(of: bars.map(\.chord))
        return Summary(key: key, keyConfidence: keyConfidence, bpm: bpm.map { Int($0.rounded()) }, capo: Self.capo(for: bars.map(\.chord)), bars: bars)
    }

    /// The song's parts as heard: bars in 4-bar phrases, a phrase that repeats an earlier one (3 of 4 bars alike) given
    /// the same letter, and neighbouring phrases with the same letter joined — A A B B A …
    static func parts(_ bars: [String]) -> [(letter: Character, range: Range<Int>)] {
        let phrases = stride(from: 0, to: bars.count, by: 4).map { $0..<min(bars.count, $0 + 4) }
        var shapes: [ArraySlice<String>] = []
        var parts: [(letter: Character, range: Range<Int>)] = []
        for phrase in phrases {
            let chords = bars[phrase]
            let alike = shapes.firstIndex { shape in
                let same = zip(shape, chords).filter { $0 == $1 }.count
                return same >= min(shape.count, chords.count) - (chords.count >= 3 ? 1 : 0)
            }
            let index = alike ?? shapes.count
            if alike == nil { shapes.append(chords) }
            let letter = Character(UnicodeScalar(UInt8(65 + min(index, 25))))
            if let last = parts.last, last.letter == letter {
                parts[parts.count - 1].range = last.range.lowerBound..<phrase.upperBound
            } else {
                parts.append((letter, phrase))
            }
        }
        return parts
    }

    // MARK: Pieces

    /// 0–100: how many readings picked this chord (a 7th and its triad half count for each other), and how well the
    /// sound fit it.
    private static func confidence(of index: Int, in scores: [[Double]]) -> Int {
        let voiced = scores.filter { !$0.isEmpty }
        guard !voiced.isEmpty else { return 0 }
        let agree = voiced.reduce(0.0) { sum, s in
            let best = argmax(s)
            return sum + (best == index ? 1 : best % 12 == index % 12 && (best / 12 != 1) == (index / 12 != 1) ? 0.5 : 0)
        } / Double(voiced.count)
        let fit = voiced.reduce(0.0) { $0 + $1[index] } / Double(voiced.count)
        let value = 0.6 * agree + 0.4 * min(1, max(0, (fit - 0.6) / 0.3))
        return Int((100 * value).rounded())
    }

    /// The beat period with the strongest repeating onsets, 60–180 BPM, leaning towards ≈ 100 BPM so half and
    /// double tempo don't win on a tie.
    static func tempo(_ envelope: [Double]) -> Double? {
        let onsets = Self.onsets(envelope)
        guard onsets.count >= Int(6 * envelopeRate) else { return nil }
        let mean = onsets.reduce(0, +) / Double(onsets.count)
        let o = onsets.map { $0 - mean }
        let energy = o.reduce(0) { $0 + $1 * $1 }
        guard energy > 0 else { return nil }
        let lags = Int(envelopeRate * 60 / 180)...Int(envelopeRate * 60 / 60)
        var best: (lag: Int, score: Double, raw: Double) = (0, -.infinity, 0)
        var ac = [Double](repeating: 0, count: lags.upperBound + 2)
        for lag in lags.lowerBound - 1...lags.upperBound + 1 {
            var s = 0.0
            for i in lag..<o.count { s += o[i] * o[i - lag] }
            ac[lag] = s / energy
        }
        for lag in lags {
            let bpm = 60 * envelopeRate / Double(lag)
            let prior = exp(-0.5 * pow(log2(bpm / 100) / 0.9, 2))
            let score = ac[lag] * prior
            if score > best.score { best = (lag, score, ac[lag]) }
        }
        guard best.raw > 0.1 else { return nil }
        // Between envelope samples: the peak of a parabola through the best lag and its neighbours.
        let (a, b, c) = (ac[best.lag - 1], ac[best.lag], ac[best.lag + 1])
        let shift = a - 2 * b + c == 0 ? 0 : 0.5 * (a - c) / (a - 2 * b + c)
        return 60 * envelopeRate / (Double(best.lag) + max(-0.5, min(0.5, shift)))
    }

    /// Rises in loudness (strums, hits), on a log scale so quiet and loud passages count alike.
    private static func onsets(_ envelope: [Double]) -> [Double] {
        zip(envelope, envelope.dropFirst()).map { max(0, log($1 + 1e-4) - log($0 + 1e-4)) }
    }

    /// When the last strum or hit was (s): the last onset at least half as strong as a typical strong one (the
    /// first sound out of silence jumps far more than any strum after it, so not the strongest).
    private static func lastOnset(_ envelope: [Double]) -> Double {
        let o = onsets(envelope)
        let sorted = o.sorted(), levels = envelope.sorted()
        guard !sorted.isEmpty else { return .infinity }
        let typical = sorted[Int(Double(sorted.count - 1) * 0.95)]
        let loud = levels[Int(Double(levels.count - 1) * 0.95)]
        // Flickers in a fading ring-out look like strums on a log scale: only a jump to a real playing level counts.
        guard typical > 0, let last = o.indices.last(where: { o[$0] >= typical / 2 && envelope[$0 + 1] >= loud * 0.3 }) else {
            return .infinity
        }
        return Double(last + 1) / envelopeRate
    }

    /// When in the beat the onsets fall: the offset (s) from the start whose beat grid catches the most onset.
    private static func beatPhase(_ envelope: [Double], period: Double) -> Double {
        let o = onsets(envelope)
        let step = Int((period * envelopeRate).rounded())
        guard step > 0, o.count > step else { return 0 }
        let best = (0..<step).max { a, b in
            stride(from: a, to: o.count, by: step).reduce(0) { $0 + o[$1] } < stride(from: b, to: o.count, by: step).reduce(0) { $0 + o[$1] }
        } ?? 0
        // Onsets are between envelope samples i and i+1.
        return Double(best + 1) / envelopeRate
    }

    /// Chord changes falling on beat `offset` of each 4, minus those elsewhere: high when the bar lines are right.
    private static func changes(_ labels: [Int], onBeat offset: Int) -> Int {
        var score = 0
        for i in 1..<max(1, labels.count) where labels[i] != labels[i - 1] && labels[i] >= 0 && labels[i - 1] >= 0 {
            score += (i - offset) % 4 == 0 ? 1 : -1
        }
        return score
    }

    private static let majorProfile = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
    private static let minorProfile = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]

    /// The key whose note profile (Krumhansl) best fits the chords heard, and how clearly it beats the next best.
    static func key(of chords: [String]) -> (String, Int) {
        var notes = [Double](repeating: 0, count: 12)
        for chord in chords {
            guard let (root, intervals, _) = ChordRecognizer.parse(chord) else { continue }
            for (i, interval) in intervals.enumerated() { notes[(root + interval) % 12] += i == 0 ? 1.5 : 1 }
        }
        guard notes.contains(where: { $0 > 0 }) else { return ("", 0) }
        var fits: [(name: String, r: Double)] = []
        for tonic in 0..<12 {
            for (profile, suffix) in [(majorProfile, ""), (minorProfile, "m")] {
                let rotated = (0..<12).map { profile[($0 - tonic + 12) % 12] }
                fits.append((roots[tonic] + suffix, correlation(notes, rotated)))
            }
        }
        fits.sort { $0.r > $1.r }
        let margin = fits[0].r - fits[1].r
        return (fits[0].name, Int(min(99, max(30, 55 + margin * 300)).rounded()))
    }

    /// Chords a beginner plays as open shapes.
    private static let openShapes: Set<String> = ["C", "D", "E", "F", "G", "A", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7"]

    /// The capo (0–7) that turns the most chords into open shapes — only for songs mostly in barre chords (a song in
    /// C already plays open), and only if it helps at least two chords.
    static func capo(for chords: [String]) -> Int {
        let unique = Set(chords.filter { !$0.isEmpty })
        func open(_ capo: Int) -> Int {
            unique.filter { openShapes.contains(ChordTheory.shown($0, transpose: 0, capo: capo, easy: false).name) }.count
        }
        guard Double(open(0)) < Double(unique.count) * 0.6 else { return 0 }
        let best = (0...7).max { open($0) < open($1) || (open($0) == open($1) && $0 > $1) } ?? 0
        return open(best) >= open(0) + 2 ? best : 0
    }

    private static func correlation(_ a: [Double], _ b: [Double]) -> Double {
        let ma = a.reduce(0, +) / 12, mb = b.reduce(0, +) / 12
        var num = 0.0, da = 0.0, db = 0.0
        for i in 0..<12 { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma) * (a[i] - ma); db += (b[i] - mb) * (b[i] - mb) }
        return da > 0 && db > 0 ? num / sqrt(da * db) : 0
    }

    private static func sum(_ rows: [[Double]]) -> [Double] {
        guard let first = rows.first else { return [] }
        var total = first
        for row in rows.dropFirst() { for i in total.indices { total[i] += row[i] } }
        return total
    }

    private static func argmax(_ v: [Double]) -> Int { v.indices.max { v[$0] < v[$1] } ?? 0 }
    private static func argmaxOptional(_ v: [Double]) -> Int? { v.isEmpty ? nil : argmax(v) }
}
