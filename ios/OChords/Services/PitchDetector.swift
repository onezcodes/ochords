import AVFoundation
import Accelerate

/// Fundamental frequency of a sound, by the YIN algorithm (de Cheveigné & Kawahara, 2002) — robust to the strong
/// overtones of a plucked string, which fool plain autocorrelation into octave errors.
enum YIN {
    struct Result {
        var hz: Double
        /// 0 = perfectly periodic … 1 = noise. Readings above ~0.2 aren't a clear note.
        var aperiodicity: Double
    }

    /// - Parameters:
    ///   - samples: mono audio, a few periods of the lowest note long (≥ 2048 samples at 44.1/48 kHz for low D).
    ///   - range: frequencies to look for. Guitar: low D (73 Hz) on a dropped string up to high frets.
    static func pitch(_ samples: [Float], sampleRate: Double, range: ClosedRange<Double> = 60...1100, threshold: Double = 0.15) -> Result? {
        let maxLag = min(samples.count / 2, Int(sampleRate / range.lowerBound))
        let minLag = max(2, Int(sampleRate / range.upperBound))
        guard maxLag > minLag + 2 else { return nil }
        let window = samples.count - maxLag

        // Difference function d(τ) = Σx² + Σx²(shifted) − 2·Σx·x(shifted): the two energies come from a running sum
        // of squares and the cross term is one vectorised dot product per lag.
        var d = [Double](repeating: 0, count: maxLag + 1)
        samples.withUnsafeBufferPointer { x in
            guard let base = x.baseAddress else { return }
            var squares = [Double](repeating: 0, count: samples.count + 1)
            for i in 0..<samples.count { squares[i + 1] = squares[i] + Double(x[i] * x[i]) }
            let head = squares[window]
            for lag in 1...maxLag {
                var cross: Float = 0
                vDSP_dotpr(base, 1, base + lag, 1, &cross, vDSP_Length(window))
                d[lag] = max(0, head + (squares[lag + window] - squares[lag]) - 2 * Double(cross))
            }
        }
        var normalised = [Double](repeating: 1, count: maxLag + 1)
        var running = 0.0
        for lag in 1...maxLag {
            running += d[lag]
            normalised[lag] = running > 0 ? d[lag] * Double(lag) / running : 1
        }

        // The first dip under the threshold is the period (later dips are its multiples — lower octaves).
        var lag = minLag
        while lag < maxLag {
            if normalised[lag] < threshold {
                while lag + 1 < maxLag && normalised[lag + 1] < normalised[lag] { lag += 1 }
                break
            }
            lag += 1
        }
        if lag >= maxLag {
            // No clear dip: take the deepest one, and let the caller reject it by its aperiodicity.
            guard let best = (minLag..<maxLag).min(by: { normalised[$0] < normalised[$1] }) else { return nil }
            lag = best
        }

        // Octave check: on real, noisy sound a multiple of the period can dip under the threshold first. If a nearly
        // as deep dip sits at ½, ⅓ or ¼ of it, that shorter period is the note (a string's own odd overtones keep
        // the half-period dip shallow, so this doesn't push notes an octave up).
        let picked = normalised[lag]
        for divisor in [4, 3, 2] {
            let centre = lag / divisor
            let lo = max(minLag, Int(Double(centre) * 0.97)), hi = min(maxLag - 1, Int((Double(centre) * 1.03).rounded(.up)))
            guard lo < hi, let dip = (lo...hi).min(by: { normalised[$0] < normalised[$1] }) else { continue }
            if dip > lo && dip < hi && normalised[dip] < 0.3 && normalised[dip] < picked + 0.2 {
                lag = dip
                break
            }
        }

        // Parabolic interpolation between neighbouring lags for sub-sample precision (≈ 0.1 cent).
        var period = Double(lag)
        if lag > 1 && lag < maxLag {
            let a = normalised[lag - 1], b = normalised[lag], c = normalised[lag + 1]
            let denominator = a - 2 * b + c
            if abs(denominator) > 1e-12 { period += 0.5 * (a - c) / denominator }
        }
        return Result(hz: sampleRate / period, aperiodicity: normalised[lag])
    }

    /// Re-measures a note from its fundamental and 2nd harmonic only. A plucked string's higher overtones run sharp
    /// (stiff, wound strings most) and are loudest just after the pluck, which pulls a plain reading several cents
    /// high — the "too high, then too low" a player chases. The 2nd harmonic is within half a cent of true and much
    /// stronger than the fundamental on low strings, so keeping it steadies the reading.
    static func refine(_ samples: [Float], near hz: Double, sampleRate: Double) -> Double? {
        let range = (hz * 0.92)...(hz * 1.08)
        let band = isolate(samples, from: hz * 0.75, to: hz * 2.2, sampleRate: sampleRate)
        guard let r = pitch(band, sampleRate: sampleRate, range: range, threshold: 0.3), range.contains(r.hz) else { return nil }
        return r.hz
    }

    /// Keeps only the band `from`–`to` Hz (fourth-order Butterworth high- and low-pass); the filters' start-up is
    /// dropped.
    static func isolate(_ samples: [Float], from low: Double, to high: Double, sampleRate: Double) -> [Float] {
        var x = samples
        for q in [0.5412, 1.3066] {
            biquad(&x, cutoff: low, q: q, highPass: true, sampleRate: sampleRate)
            biquad(&x, cutoff: high, q: q, highPass: false, sampleRate: sampleRate)
        }
        return Array(x.dropFirst(min(512, x.count / 8)))
    }

    /// RBJ-cookbook biquad low- or high-pass, in place.
    private static func biquad(_ x: inout [Float], cutoff: Double, q: Double, highPass: Bool, sampleRate: Double) {
        let w = 2 * Double.pi * cutoff / sampleRate
        let alpha = sin(w) / (2 * q)
        let a0 = 1 + alpha
        let b0 = (highPass ? (1 + cos(w)) : (1 - cos(w))) / 2 / a0
        let b1 = (highPass ? -(1 + cos(w)) : (1 - cos(w))) / a0, b2 = b0
        let a1 = -2 * cos(w) / a0, a2 = (1 - alpha) / a0
        var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        for i in x.indices {
            let x0 = Double(x[i])
            let y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
            x[i] = Float(y0)
        }
    }
}

/// Turns windows of microphone audio into tuner readings: the loudness gate, the lock on the note being tuned and
/// octave handling. Pure logic (no audio APIs) so it can be tested with recorded or synthesised sound.
struct PitchTracker {
    struct Reading {
        var hz: Double
        /// Loudness (RMS) of the window.
        var level: Float
    }

    /// Samples analysed at once: ≈ 85 ms at 48 kHz — several periods of a dropped low D — so the needle follows a
    /// turning peg quickly…
    static let shortWindow = 4096
    /// …and ≈ 170 ms once a locked note fades close to the room's noise: twice the cycles average the noise out.
    static let longWindow = 8192
    /// A locked note counts as fading below this many times the noise floor.
    private static let fadingBelow: Float = 5
    /// A new note must be this many times louder than the room…
    private static let aboveNoise: Float = 2.5
    /// …never quieter than this (a dead-silent room would otherwise make hiss look like a note)…
    private static let quietest: Float = 0.0006
    /// …and never needs to be louder than this, however noisy the room (a soft pluck near the phone).
    private static let loudestGate: Float = 0.004
    /// A locked note is followed until it fades to this many times the room's noise (below it, readings jitter).
    private static let followAbove: Float = 1.6
    /// The room's noise is the quietest moment of the last few seconds (≈ 150 readings).
    private static let noiseMemory = 150
    /// A new note must be clearly periodic — under YIN's threshold, so it's the first clear dip and not the deepest
    /// one, which is often a lower octave; the note already locked on may get noisier as it fades.
    private static let newNoteAperiodicity = 0.15
    private static let lockedAperiodicity = 0.35
    /// Readings within this many cents of the locked note keep the lock.
    private static let lockCents = 60.0
    /// The lock is let go after this long without the note…
    private static let lockSeconds = 0.4
    /// …or at once when a new pluck makes the sound jump this much within 100 ms (5 readings), so a string just
    /// plucked takes over from one still ringing.
    private static let pluckJump: Float = 1.8
    private static let pluckLookback = 5
    /// Octave and twelfth errors (in cents) a fading note can slip into.
    private static let octaveSlips: [Double] = [-1902, -1200, 1200, 1902]

    /// Recent loudness, newest last, for the noise floor.
    private var levels: [Float] = []
    private var noiseFloor: Float { levels.min() ?? 0.001 }
    private var locked: Double?

    /// The note being tuned when the player picked a string (manual mode): only pitches within a fifth of it are
    /// looked for, so other strings still ringing — or an octave of the note — can't be taken for it.
    var target: Double?
    /// Samples since the locked note was last heard.
    private var sinceLocked = 0

    /// - Parameters:
    ///   - window: the newest audio, oldest first: at least `shortWindow` samples, up to `longWindow`.
    ///   - elapsed: samples since the last reading.
    mutating func read(_ window: [Float], elapsed: Int, sampleRate: Double) -> Reading? {
        let recent = Array(window.suffix(Self.shortWindow))
        var meanSquare: Float = 0
        vDSP_measqv(recent, 1, &meanSquare, vDSP_Length(recent.count))
        let level = sqrt(meanSquare)
        levels.append(level)
        if levels.count > Self.noiseMemory { levels.removeFirst(levels.count - Self.noiseMemory) }
        sinceLocked += elapsed
        if Double(sinceLocked) > Self.lockSeconds * sampleRate { locked = nil }

        if locked != nil, levels.count > Self.pluckLookback,
           level > levels[levels.count - 1 - Self.pluckLookback] * Self.pluckJump {
            locked = nil
        }

        let fading = locked != nil && level < noiseFloor * Self.fadingBelow
        let samples = fading ? window : recent
        // A locked note is followed down to the quietest level; a new one must stand out from the room.
        let gate = max(Self.quietest, locked != nil ? noiseFloor * Self.followAbove : min(Self.loudestGate, noiseFloor * Self.aboveNoise))
        let range = target.map { ($0 * pow(2, -7.0 / 12))...($0 * pow(2, 7.0 / 12)) } ?? 60...1100
        // With a string chosen, listen only to the band around it (about ±5 semitones, so its neighbours drop out).
        let heard = target.map { YIN.isolate(samples, from: $0 * 0.75, to: $0 * 1.33, sampleRate: sampleRate) } ?? samples
        let found = level >= gate ? YIN.pitch(heard, sampleRate: sampleRate, range: range) : nil
        let accepted = found.flatMap { found -> Double? in
            if let locked {
                let cents = 1200 * log2(found.hz / locked)
                if abs(cents) < Self.lockCents { return found.aperiodicity <= Self.lockedAperiodicity ? found.hz : nil }
                // A fading string can read an octave (or a twelfth) off; that's the same note, not a new one.
                if Self.octaveSlips.contains(where: { abs(cents - $0) < Self.lockCents }) { return nil }
            }
            // A chosen string can't be confused with an octave (the search stays near it), so a reading muddied by
            // other strings still ringing is good enough to start on.
            let limit = target == nil ? Self.newNoteAperiodicity : Self.lockedAperiodicity
            return found.aperiodicity <= limit ? found.hz : nil
        }
        guard let rough = accepted else { return nil }
        let hz = YIN.refine(samples, near: rough, sampleRate: sampleRate) ?? rough
        locked = hz
        sinceLocked = 0
        return Reading(hz: hz, level: level)
    }
}

/// Listens to the microphone and reports the pitch being played, 50 times a second.
/// Start and stop on the main thread; readings arrive on the main thread.
///
/// Like dedicated tuner apps it follows a note for as long as the string rings: the loudness gate adapts to the
/// room instead of a fixed level, and once a note is found it stays locked while the fading string gets noisier.
final class PitchDetector {
    typealias Reading = PitchTracker.Reading
    typealias Failure = MicInput.Failure

    /// Each clear note, or nil while it's quiet or noisy.
    var onReading: ((Reading?) -> Void)?

    private let mic = MicInput(windowLength: PitchTracker.longWindow, label: "ochords.pitch")
    /// Only touched on the mic's analysis queue.
    private var tracker = PitchTracker()
    private var target: Double?

    var isRunning: Bool { mic.isRunning }
    static var hasPermission: Bool { MicInput.hasPermission }
    static var permissionUndecided: Bool { MicInput.permissionUndecided }

    init() {
        mic.onWindow = { [weak self] window, elapsed, sampleRate in
            guard let self, window.count >= PitchTracker.shortWindow else { return }
            let reading = tracker.read(window, elapsed: elapsed, sampleRate: sampleRate)
            DispatchQueue.main.async { [weak self] in self?.onReading?(reading) }
        }
        mic.onIgnored = { [weak self] in
            DispatchQueue.main.async { [weak self] in self?.onReading?(nil) }
        }
    }

    func start() throws { try mic.start() }

    func stop() {
        guard mic.isRunning else { return }
        mic.stop()
        mic.perform { [weak self] in
            guard let self else { return }
            tracker = PitchTracker()
            tracker.target = target
        }
    }

    /// Listen only around this note (the string the player picked), or anywhere (nil, auto mode).
    func setTarget(_ hz: Double?) {
        mic.perform { [weak self] in
            self?.target = hz
            self?.tracker.target = hz
        }
    }

    /// Stops reporting for `seconds` — while the app plays its own sound — so it's never read as a string.
    func ignore(for seconds: TimeInterval) { mic.ignore(for: seconds) }
}
