import XCTest
@testable import OChords

final class TuningSessionTests: XCTestCase {
    private let standard = [82.41, 110.0, 146.83, 196.0, 246.94, 329.63]

    func testStringCountsAsTunedAfterHoldingWithinTolerance() {
        var session = TuningSession(targets: standard, selected: 1)
        var tunedAt: Double?
        var t = 0.0
        while t < 1.0 {
            let events = session.hear(110.1, at: t)
            if events.contains(where: { if case .tuned = $0 { return true }; return false }) {
                tunedAt = t
                break
            }
            t += 0.02
        }
        XCTAssertNotNil(tunedAt)
        XCTAssertGreaterThanOrEqual(tunedAt!, TuningSession.holdToTune - 1e-9)
        XCTAssertTrue(session.tuned.contains(1))
        XCTAssertFalse(session.allTuned)
    }

    func testAutoModeSwitchesAfterThreeConsistentReadings() {
        var session = TuningSession(targets: standard, selected: 1)
        var events: [TuningSession.Event] = []
        for i in 0..<3 {
            events += session.hear(196.0, at: Double(i) * 0.02)
        }
        XCTAssertEqual(events, [.switched(to: 3)])
        XCTAssertEqual(session.selected, 3)
    }

    func testOneStrayReadingDoesNotSwitch() {
        var session = TuningSession(targets: standard, selected: 1)
        _ = session.hear(110.0, at: 0.0)
        _ = session.hear(196.0, at: 0.02)
        _ = session.hear(110.0, at: 0.04)
        XCTAssertEqual(session.selected, 1)
    }

    func testReadingGoesStaleAfterHoldAfterNote() {
        var session = TuningSession(targets: standard, selected: 1)
        _ = session.hear(110.0, at: 0.0)
        _ = session.hear(nil, at: 0.1)
        XCTAssertEqual(session.heard!, 110.0, accuracy: 1e-9)
        _ = session.hear(nil, at: 0.5)
        XCTAssertNil(session.heard)
    }

    func testTunedStringLosesTickOnlyAfterClearlyOutForLong() {
        var session = TuningSession(targets: standard, selected: 1)
        var t = 0.0
        while t <= 0.5 {
            _ = session.hear(110.0, at: t)
            t += 0.02
        }
        XCTAssertTrue(session.tuned.contains(1))
        _ = session.hear(nil, at: 0.9)
        var untuned = false
        t = 1.0
        while t < 4.0 {
            if session.hear(108.1, at: t).contains(where: { if case .untuned = $0 { return true }; return false }) {
                untuned = true
                break
            }
            t += 0.02
        }
        XCTAssertTrue(untuned)
        XCTAssertGreaterThanOrEqual(t, 1.0 + TuningSession.attack + TuningSession.untuneAfter - 0.05)
    }
}
