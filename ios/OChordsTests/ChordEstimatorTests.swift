import XCTest
@testable import OChords

final class ChordEstimatorTests: XCTestCase {
    func testNamesASynthesisedCMajorChroma() {
        var estimator = ChordEstimator()
        let chroma = ChordRecognizer.template("C")!
        for i in 0..<10 {
            estimator.hear(chroma: chroma, at: Double(i) * ChordEstimator.frameInterval)
        }
        let last = estimator.segments.last!
        XCTAssertEqual(last.chord, "C")
        XCTAssertGreaterThanOrEqual(last.confidence, 90, "confidence \(last.confidence)")
    }

    func testFollowsAChangeAndPredictsTheNextChord() {
        var estimator = ChordEstimator()
        let c = ChordRecognizer.template("C")!
        let g = ChordRecognizer.template("G")!
        var t = 0.0
        for _ in 0..<2 {
            for _ in 0..<8 {
                estimator.hear(chroma: c, at: t)
                t += ChordEstimator.frameInterval
            }
            for _ in 0..<8 {
                estimator.hear(chroma: g, at: t)
                t += ChordEstimator.frameInterval
            }
        }
        XCTAssertEqual(estimator.segments.map(\.chord), ["C", "G", "C", "G"])
        XCTAssertEqual(estimator.likelyNext(after: "C"), "G")
    }

    func testNoiseThatFitsNoChordIsSilence() {
        var estimator = ChordEstimator()
        let flat = [Double](repeating: 1 / sqrt(12.0), count: 12)
        for i in 0..<10 {
            estimator.hear(chroma: flat, at: Double(i) * ChordEstimator.frameInterval)
        }
        XCTAssertTrue(estimator.segments.isEmpty)
    }

    func testKeyOfCommonProgressionIsC() {
        let (key, confidence) = ChordEstimator.key(of: ["C", "G", "Am", "F", "C", "G", "F", "C"])
        XCTAssertEqual(key, "C")
        XCTAssertTrue((30...99).contains(confidence))
    }

    func testCapoSuggestedOnlyWhenItOpensTwoMoreShapes() {
        XCTAssertEqual(ChordEstimator.capo(for: ["C", "G", "Am", "F"]), 0)
        // F# B C#m: capo 4 makes D G Am.
        XCTAssertEqual(ChordEstimator.capo(for: ["F#", "B", "C#m"]), 4)
    }

    func testTempoFromAPulsedEnvelope() {
        // A strum every 0.5 s (120 BPM) for 20 s, at 50 readings a second.
        var envelope: [Double] = []
        for i in 0..<1000 {
            let inBeat = i % 25
            envelope.append(inBeat == 0 ? 0.2 : 0.05 * exp(-Double(inBeat) / 10.0) + 0.01)
        }
        let bpm = ChordEstimator.tempo(envelope)!
        XCTAssertLessThan(abs(bpm - 120), 1, "bpm \(bpm)")
    }

    func testPartsLetterRepeatedPhrases() {
        let bars = ["C", "G", "Am", "F", "C", "G", "Am", "F", "D", "A", "Bm", "G", "C", "G", "Am", "F"]
        let parts = ChordEstimator.parts(bars)
        XCTAssertEqual(parts.map(\.letter), ["A", "B", "A"])
        XCTAssertEqual(parts[0].range, 0..<8)
        XCTAssertEqual(parts[1].range, 8..<12)
        XCTAssertEqual(parts[2].range, 12..<16)
    }

    func testSummaryGivesOneChordPerBar() {
        var estimator = ChordEstimator()
        let c = ChordRecognizer.template("C")!
        let g = ChordRecognizer.template("G")!
        let seconds = 8.5
        var t = 0.0
        while t < seconds - 1e-9 {
            estimator.hear(chroma: t < 0.5 ? nil : (t < 4.5 ? c : g), at: t)
            t += ChordEstimator.frameInterval
        }
        for i in 0..<Int(seconds * ChordEstimator.envelopeRate) {
            let inBeat = i % 25
            let loudness: Double
            if i < 25 {
                loudness = 0.001
            } else if inBeat == 0 {
                loudness = 0.2
            } else {
                loudness = 0.05 * exp(-Double(inBeat) / 10.0) + 0.01
            }
            estimator.hear(loudness: loudness)
        }
        let s = estimator.summary()
        XCTAssertEqual(s.bars.map(\.chord), ["C", "C", "G", "G"])
        // Two bars each of C and G is ambiguous between the two keys; either is right.
        XCTAssertTrue(s.key == "C" || s.key == "G", "key \(s.key)")
        XCTAssertNotNil(s.bpm)
        XCTAssertLessThanOrEqual(abs(s.bpm! - 120), 1, "bpm \(String(describing: s.bpm))")
    }
}
