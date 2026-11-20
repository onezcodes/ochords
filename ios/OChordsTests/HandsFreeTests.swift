import XCTest
@testable import OChords

final class HandsFreeTests: XCTestCase {
    func testOneNodDownCountsOnce() {
        var g = NodGesture()
        var moves: [NodGesture.Move] = []
        // Resting, a quick turn of 0.3 rad, and back.
        let pitches = [0.0, 0.0, 0.0, 0.1, 0.3, 0.3, 0.1, 0.0, 0.0, 0.0]
        for (i, p) in pitches.enumerated() {
            if let m = g.hear(pitch: p, at: Double(i) * 0.066) { moves.append(m) }
        }
        XCTAssertEqual(moves, [.down])
    }

    func testTiltUpIsTheOtherMoveAndRespectsTheRefractoryGap() {
        var g = NodGesture()
        var moves: [NodGesture.Move] = []
        let pitches = [0.0, 0.0, -0.3, -0.3, 0.0, 0.0, -0.3, 0.0]
        for (i, p) in pitches.enumerated() {
            if let m = g.hear(pitch: p, at: Double(i) * 0.066) { moves.append(m) }
        }
        // The second tilt comes back 0.26 s after the first move: too soon.
        XCTAssertEqual(moves, [.up])
    }

    func testLookingDownForAWhileBecomesTheNewRest() {
        var g = NodGesture()
        var t = 0.0
        _ = g.hear(pitch: 0.0, at: t)
        // Head down for 1.5 s, then back up: not a nod, and the return isn't a tilt up either.
        var moves = 0
        while t < 1.5 {
            t += 0.066
            if g.hear(pitch: 0.3, at: t) != nil { moves += 1 }
        }
        t += 0.066
        if g.hear(pitch: 0.0, at: t) != nil { moves += 1 }
        XCTAssertEqual(moves, 0)
    }

    func testLearnsWhichWayIsDown() {
        var g = NodGesture()
        XCTAssertNil(g.learnDown(pitch: 0.1, at: 0.0))
        XCTAssertNil(g.learnDown(pitch: 0.12, at: 0.1))
        XCTAssertEqual(g.learnDown(pitch: -0.2, at: 0.2)!, -1.0, accuracy: 0.0)
    }
}
