import XCTest
@testable import OChords

final class ChordFollowerTests: XCTestCase {
    func testRepeatCountReadsEveryWrittenForm() {
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud x2"), 2)
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud (x2)"), 2)
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud x 3"), 3)
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud ×2"), 2)
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud 2x"), 2)
        XCTAssertEqual(ChordFollower.repeatCount("Sing it loud 2 times"), 2)
        XCTAssertEqual(ChordFollower.repeatCount(") x4"), 4)
        XCTAssertNil(ChordFollower.repeatCount("Sing it loud"))
        XCTAssertNil(ChordFollower.repeatCount("Track 2"))
        XCTAssertNil(ChordFollower.repeatCount("x1"))
    }

    func testPlayOrderRepeatsALineAndABracketedGroup() {
        let lines = ["[C]One line", "[G]Again x2", "( [Am]Open", "[F]Close ) x2", "[C]Last"]
        let order = ChordFollower.playOrder(lines: lines)
        let expected = [
            [0, 1, 1],
            [1, 1, 2], [1, 2, 2],
            [2, 1, 2], [3, 1, 2],
            [2, 2, 2], [3, 2, 2],
            [4, 1, 1],
        ]
        XCTAssertEqual(order.map { [$0.line, $0.pass, $0.passes] }, expected)
    }

    func testBracketWithoutACountIsJustAnAside() {
        let order = ChordFollower.playOrder(lines: ["(spoken)", "[C]Verse"])
        XCTAssertEqual(order.map { [$0.line, $0.pass, $0.passes] }, [[0, 1, 1], [1, 1, 1]])
    }

    func testFollowerAdvancesAfterTwoReadingsOfTheNextChord() {
        let steps = [
            ChordFollower.Step(chord: "C", item: 0, line: 0),
            ChordFollower.Step(chord: "G", item: 0, line: 0),
            ChordFollower.Step(chord: "Am", item: 0, line: 1),
            ChordFollower.Step(chord: "F", item: 0, line: 1),
        ]
        var f = ChordFollower(steps: steps)
        let c = ChordRecognizer.template("C")!
        let g = ChordRecognizer.template("G")!
        XCTAssertFalse(f.hear(c, level: 0.05))
        XCTAssertTrue(f.hear(c, level: 0.05))
        XCTAssertEqual(f.current?.chord, "C")
        XCTAssertFalse(f.hear(c, level: 0.05))
        XCTAssertFalse(f.hear(g, level: 0.05))
        XCTAssertTrue(f.hear(g, level: 0.05))
        XCTAssertEqual(f.current?.chord, "G")
        for _ in 0..<5 {
            XCTAssertFalse(f.hear(ChordRecognizer.template("Am"), level: 0.0001))
        }
        XCTAssertEqual(f.position, 1)
    }

    func testIndistinctNeighboursAreFoldedIntoOneStep() {
        let f = ChordFollower(steps: [
            ChordFollower.Step(chord: "C", item: 0, line: 0),
            ChordFollower.Step(chord: "C", item: 0, line: 0),
            ChordFollower.Step(chord: "Cmaj7", item: 0, line: 1),
            ChordFollower.Step(chord: "G", item: 0, line: 1),
        ])
        XCTAssertEqual(f.steps.map(\.chord), ["C", "G"])
    }

    func testResyncWaitsForTheTappedLine() {
        var f = ChordFollower(steps: [
            ChordFollower.Step(chord: "C", item: 0, line: 0),
            ChordFollower.Step(chord: "G", item: 0, line: 1),
            ChordFollower.Step(chord: "Am", item: 1, line: 0),
        ])
        f.resync(item: 1, line: 0)
        XCTAssertEqual(f.position, 1)
        let am = ChordRecognizer.template("Am")!
        let moved = f.hear(am, level: 0.05) || f.hear(am, level: 0.05)
        XCTAssertTrue(moved)
        XCTAssertEqual(f.current?.chord, "Am")
    }

    func testChromaOfASynthesisedChordMatchesItsTemplateBest() {
        let rate = 48_000.0
        let notes = [130.81, 164.81, 196.0, 261.63, 329.63] // C3 E3 G3 C4 E4
        let samples: [Float] = (0..<ChordRecognizer.window).map { i in
            let t = Double(i) / rate
            var v = 0.0
            for hz in notes {
                for k in 1...4 {
                    v += sin(2 * .pi * hz * Double(k) * t) / Double(k)
                }
            }
            return Float(0.05 * v)
        }
        let chroma = ChordRecognizer.chroma(samples, sampleRate: rate)!
        let candidates = ["C", "Am", "F", "G", "Em", "Dm", "C7"]
        let best = candidates.max { a, b in
            ChordRecognizer.similarity(chroma, ChordRecognizer.template(a)!)
                < ChordRecognizer.similarity(chroma, ChordRecognizer.template(b)!)
        }
        XCTAssertEqual(best, "C")
    }
}
