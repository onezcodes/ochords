import XCTest
@testable import OChords

final class PitchDetectorTests: XCTestCase {
    private let rate = 48_000.0

    /// A plucked-string-like tone: fundamental plus fading overtones.
    private func tone(hz: Double, seconds: Double, amplitude: Double = 0.1, harmonics: Int = 5) -> [Float] {
        let n = Int(seconds * rate)
        return (0..<n).map { i in
            let t = Double(i) / rate
            var v = 0.0
            for k in 1...harmonics {
                v += sin(2 * .pi * hz * Double(k) * t) / Double(k * k)
            }
            return Float(amplitude * v)
        }
    }

    private func cents(_ a: Double, _ b: Double) -> Double { 1200 * log2(a / b) }

    /// A deterministic "hiss" phase for the quiet room.
    private func i2(_ i: Int) -> Double { Double(i * 7919 % 1000) / 1000.0 * 2 * .pi }

    func testYinFinds110HzWithinOneCent() {
        let r = YIN.pitch(tone(hz: 110.0, seconds: 0.1), sampleRate: rate)!
        XCTAssertLessThan(r.aperiodicity, 0.15, "aperiodicity \(r.aperiodicity)")
        XCTAssertLessThan(abs(cents(r.hz, 110.0)), 1, "read \(r.hz) Hz")
    }

    func testYinHasNoOctaveErrorWhenSecondHarmonicIsLouder() {
        let n = 4096
        let samples: [Float] = (0..<n).map { i in
            let t = Double(i) / rate
            return Float(
                0.05 * sin(2 * .pi * 82.41 * t)
                    + 0.1 * sin(2 * .pi * 164.82 * t)
                    + 0.03 * sin(2 * .pi * 247.23 * t)
            )
        }
        let r = YIN.pitch(samples, sampleRate: rate)!
        XCTAssertLessThan(abs(cents(r.hz, 82.41)), 3, "read \(r.hz) Hz")
    }

    func testRefineStaysWithinOneCent() {
        let hz = YIN.refine(tone(hz: 196.0, seconds: 0.17), near: 196.5, sampleRate: rate)!
        XCTAssertLessThan(abs(cents(hz, 196.0)), 1, "refined to \(hz)")
    }

    func testTrackerLocksOnANoteAndGoesQuietInSilence() {
        var tracker = PitchTracker()
        let quiet: [Float] = (0..<Int(0.5 * rate)).map { Float(0.0002 * sin(i2($0))) }
        let signal = quiet + tone(hz: 110.0, seconds: 1.0)
        let hop = Int(0.02 * rate)
        var readings: [Double] = []
        var end = PitchTracker.shortWindow
        while end <= signal.count {
            let start = max(0, end - PitchTracker.longWindow)
            if let r = tracker.read(Array(signal[start..<end]), elapsed: hop, sampleRate: rate) {
                readings.append(r.hz)
            }
            end += hop
        }
        XCTAssertGreaterThan(readings.count, 30, "got \(readings.count) readings")
        let within = readings.filter { abs(cents($0, 110.0)) < 5 }.count
        XCTAssertGreaterThanOrEqual(
            Double(within),
            Double(readings.count) * 0.95,
            "\(within) of \(readings.count) within 5 cents"
        )

        let silence = [Float](repeating: 0, count: PitchTracker.longWindow)
        var silent = 0
        for _ in 0..<30 {
            if tracker.read(silence, elapsed: hop, sampleRate: rate) == nil { silent += 1 }
        }
        XCTAssertEqual(silent, 30)
    }

    func testManualTargetIgnoresADistantString() {
        var tracker = PitchTracker()
        tracker.target = 146.83
        let signal = tone(hz: 82.41, seconds: 0.5)
        let hop = Int(0.02 * rate)
        var accepted = 0
        var end = PitchTracker.shortWindow
        while end <= signal.count {
            let start = max(0, end - PitchTracker.longWindow)
            if tracker.read(Array(signal[start..<end]), elapsed: hop, sampleRate: rate) != nil {
                accepted += 1
            }
            end += hop
        }
        XCTAssertEqual(accepted, 0)
    }
}
