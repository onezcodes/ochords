import Foundation

/// Local chord shape library (from 7 · Chord finger guide). Strings: 0 = low E … 5 = high e.
enum ChordLibrary {
    private static func d(_ s: Int, _ f: Int, _ fi: Int) -> ChordDot { ChordDot(string: s, fret: f, finger: fi) }

    static let all: [String: ChordInfo] = {
        var m: [String: ChordInfo] = [:]
        func add(_ name: String, _ full: String, _ v: [ChordVoicing]) { m[name] = ChordInfo(name: name, full: full, voicings: v) }
        add("C", "C major", [ChordVoicing(pattern: "x32010", dots: [d(1, 3, 3), d(2, 2, 2), d(4, 1, 1)], tip: "Arch your ring finger so the open G string still rings.")])
        add("G/B", "G with B in the bass", [ChordVoicing(pattern: "x20003", dots: [d(1, 2, 2), d(5, 3, 3)], tip: "Walks the bass down C → G/B → Am. Let the tip of your middle finger touch the low E to mute it.")])
        add("Am", "A minor", [ChordVoicing(pattern: "x02210", dots: [d(2, 2, 2), d(3, 2, 3), d(4, 1, 1)], tip: "From C, keep index and middle where they are and move only your ring finger to the G string.")])
        add("F", "F major", [
            ChordVoicing(label: "Easy", pattern: "xx3211", dots: [d(2, 3, 3), d(3, 2, 2)], barre: Barre(fret: 1, from: 4, to: 5), tip: "Lay your index finger flat to press both the B and high e strings at fret 1."),
            ChordVoicing(label: "Full barre", pattern: "133211", dots: [d(1, 3, 3), d(2, 3, 4), d(3, 2, 2)], barre: Barre(fret: 1, from: 0, to: 5), tip: "Press the barre with the bony side of your index finger, thumb low behind the neck.")
        ])
        add("Fmaj7", "F major 7", [ChordVoicing(pattern: "xx3210", dots: [d(2, 3, 3), d(3, 2, 2), d(4, 1, 1)], tip: "The easy stand-in for F — no barre, the high e rings open.")])
        add("G", "G major", [ChordVoicing(pattern: "320003", dots: [d(0, 3, 2), d(1, 2, 1), d(5, 3, 3)], tip: "Keep fingers arched so the open D, G and B strings ring clearly.")])
        add("Em", "E minor", [ChordVoicing(pattern: "022000", dots: [d(1, 2, 2), d(2, 2, 3)], tip: "The easiest chord to start with — strum all six strings.")])
        add("D", "D major", [ChordVoicing(pattern: "xx0232", dots: [d(3, 2, 1), d(4, 3, 3), d(5, 2, 2)], tip: "Your three fingers make a small triangle. Skip the two lowest strings.")])
        add("Dm", "D minor", [ChordVoicing(pattern: "xx0231", dots: [d(3, 2, 2), d(4, 3, 3), d(5, 1, 1)], tip: "Like D, but your index drops to fret 1 on the high e.")])
        add("A", "A major", [ChordVoicing(pattern: "x02220", dots: [d(2, 2, 1), d(3, 2, 2), d(4, 2, 3)], tip: "Three fingers side by side on fret 2 — angle them so they fit.")])
        add("E", "E major", [ChordVoicing(pattern: "022100", dots: [d(1, 2, 2), d(2, 2, 3), d(3, 1, 1)], tip: "Same shape as Am, moved one string lower.")])
        add("E7", "E dominant 7", [ChordVoicing(pattern: "020100", dots: [d(1, 2, 2), d(3, 1, 1)], tip: "E with the ring finger lifted.")])
        add("A7", "A dominant 7", [ChordVoicing(pattern: "x02020", dots: [d(2, 2, 2), d(4, 2, 3)], tip: "A with the G string left open.")])
        add("D7", "D dominant 7", [ChordVoicing(pattern: "xx0212", dots: [d(3, 2, 2), d(4, 1, 1), d(5, 2, 3)], tip: "A little upside-down triangle.")])
        add("G7", "G dominant 7", [ChordVoicing(pattern: "320001", dots: [d(0, 3, 3), d(1, 2, 2), d(5, 1, 1)], tip: "Index on the high e at fret 1.")])
        add("C7", "C dominant 7", [ChordVoicing(pattern: "x32310", dots: [d(1, 3, 3), d(2, 2, 2), d(3, 3, 4), d(4, 1, 1)], tip: "C with the pinky added on the G string.")])
        add("B7", "B dominant 7", [ChordVoicing(pattern: "x21202", dots: [d(1, 2, 2), d(2, 1, 1), d(3, 2, 3), d(5, 2, 4)], tip: "The easy stand-in for B.")])
        add("Bm7", "B minor 7", [ChordVoicing(pattern: "x20202", dots: [d(1, 2, 1), d(3, 2, 2), d(5, 2, 3)], tip: "The easy stand-in for Bm — no barre.")])
        add("Bm", "B minor", [ChordVoicing(pattern: "x24432", dots: [d(2, 4, 3), d(3, 4, 4), d(4, 3, 2)], barre: Barre(fret: 2, from: 1, to: 5), tip: "Barre fret 2 from the A string.")])
        add("B", "B major", [ChordVoicing(pattern: "x24442", dots: [d(2, 4, 2), d(3, 4, 3), d(4, 4, 4)], barre: Barre(fret: 2, from: 1, to: 5), tip: "A-shape barre at fret 2.")])
        add("F#m", "F-sharp minor", [ChordVoicing(pattern: "244222", dots: [d(1, 4, 3), d(2, 4, 4)], barre: Barre(fret: 2, from: 0, to: 5), tip: "Em shape barred at fret 2.")])
        add("C#m", "C-sharp minor", [ChordVoicing(pattern: "x46654", dots: [d(2, 6, 3), d(3, 6, 4), d(4, 5, 2)], barre: Barre(fret: 4, from: 1, to: 5), tip: "Am shape barred at fret 4.")])
        add("Bb", "B-flat major", [ChordVoicing(pattern: "x13331", dots: [d(2, 3, 2), d(3, 3, 3), d(4, 3, 4)], barre: Barre(fret: 1, from: 1, to: 5), tip: "A-shape barre at fret 1.")])
        add("Cadd9", "C add 9", [ChordVoicing(pattern: "x32033", dots: [d(1, 3, 2), d(2, 2, 1), d(4, 3, 3), d(5, 3, 4)], tip: "Pairs beautifully with G.")])
        add("Asus2", "A suspended 2", [ChordVoicing(pattern: "x02200", dots: [d(2, 2, 1), d(3, 2, 2)], tip: "A with the B string open.")])
        add("Dsus4", "D suspended 4", [ChordVoicing(pattern: "xx0233", dots: [d(3, 2, 1), d(4, 3, 3), d(5, 3, 4)], tip: "D with the pinky on the high e.")])
        add("D/F#", "D with F-sharp in the bass", [ChordVoicing(pattern: "200232", dots: [d(0, 2, 1), d(3, 2, 1), d(4, 3, 3), d(5, 2, 2)], tip: "Wrap the thumb over the top for the bass note.")])
        return m
    }()

    /// Look up a chord; unknown chords fall back to their root triad when possible.
    static func info(_ name: String) -> ChordInfo? {
        if let c = all[name] { return c }
        let root = String(name.split(separator: "/").first ?? "")
        return all[root]
    }

    static let common = ["Em", "D", "Dm", "A", "E", "G", "C", "Am"]

    /// Diatonic chords per key (8 · Chords by key).
    static func chords(inKey key: String, minor: Bool = false) -> [(degree: String, chord: String)] {
        let majorSteps = [(0, ""), (2, "m"), (4, "m"), (5, ""), (7, ""), (9, "m"), (11, "dim")]
        let degrees = ["I", "ii", "iii", "IV", "V", "vi", "vii°"]
        return zip(degrees, majorSteps).map { deg, step in
            (deg, ChordTheory.transposeRoot(key, by: step.0) + step.1)
        }
    }
}
