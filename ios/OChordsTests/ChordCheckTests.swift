import XCTest
@testable import OChords

final class ChordCheckTests: XCTestCase {
    private let rate = 48_000.0

    /// Half a second of the strings that ring: each a fundamental plus fading overtones.
    private func strum(_ hz: [Double]) -> [Float] {
        let n = Int(0.5 * rate)
        var out = [Float](repeating: 0, count: n)
        for (s, f) in hz.enumerated() {
            let phase = Double(s) * 0.7
            for i in 0..<n {
                let t = Double(i) / rate
                var v = 0.0
                for k in 1...8 {
                    v += sin(2 * .pi * f * Double(k) * t + phase * Double(k))
                        / (Double(k) * 1.3) * exp(-t * (1 + Double(k) * 0.5))
                }
                out[i] += Float(0.08 * v)
            }
        }
        return out
    }

    func testCleanEMajorIsClearWhereItCanBeJudged() {
        let notes = ChordCheck.notes(of: "022100").compactMap { $0 }
        let verdicts = ChordCheck.verdicts(strum(notes), sampleRate: rate, pattern: "022100")
        // E3, B3 and E4 sit on the octaves of lower strings, so they can't be told apart.
        XCTAssertEqual(verdicts, [.clear, .clear, .unclear, .clear, .unclear, .unclear])
        XCTAssertTrue(verdicts.allSatisfy(\.isGood))
    }

    func testADeadStringIsFound() {
        let notes = ChordCheck.notes(of: "022100").compactMap { $0 }
        let ringing = notes.enumerated().compactMap { i, f in i == 3 ? nil : f }
        let verdicts = ChordCheck.verdicts(strum(ringing), sampleRate: rate, pattern: "022100")
        XCTAssertEqual(verdicts[3], .dead)
        XCTAssertEqual(verdicts[0], .clear)
        XCTAssertEqual(verdicts[1], .clear)
    }

    func testAMutedStringThatRingsIsCaught() {
        let expected = ChordCheck.notes(of: "x02210")
        let clean = strum(expected.compactMap { $0 })
        XCTAssertEqual(ChordCheck.verdicts(clean, sampleRate: rate, pattern: "x02210")[0], .muted)
        let leaking = strum([ChordCheck.openStrings[0]] + expected.compactMap { $0 })
        XCTAssertEqual(ChordCheck.verdicts(leaking, sampleRate: rate, pattern: "x02210")[0], .ringing)
    }

    func testNotesOfAPattern() {
        let n = ChordCheck.notes(of: "x32010")
        XCTAssertNil(n[0])
        XCTAssertEqual(n[1]!, 130.81, accuracy: 0.05) // C3 on the A string
        XCTAssertEqual(n[5]!, 329.63, accuracy: 0.01)
    }
}
