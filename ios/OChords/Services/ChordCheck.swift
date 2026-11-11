import Foundation
import Accelerate
import AVFoundation

/// Which strings of a strummed chord rang. A chord's notes overlap — in C, the B string's C4 is the octave of the
/// A string's C3 — so "is there energy at C4" can't tell a ringing B string from the A string's overtone.
/// Instead the spectrum is explained as a sum of six strings, each a fundamental plus its overtones, and a
/// non-negative least-squares fit finds how loud each string must have been.
enum ChordCheck {
    enum Verdict: Equatable {
        /// Fretted or open string that rang.
        case clear
        /// Should ring but barely did (muffled by a finger, not pressed down).
        case dead
        /// Should be muted (x) and is.
        case muted
        /// Should be muted (x) but rang.
        case ringing
        /// Its sound sits under other strings' overtones (an octave of a lower string), so it can't be judged.
        case unclear

        var isGood: Bool { self != .dead && self != .ringing }
    }

    /// Open strings, low E to high e, at A4 = 440 Hz.
    static let openStrings: [Double] = [82.41, 110.0, 146.83, 196.0, 246.94, 329.63]

    /// A string counts as ringing at this share of the loudest string's strength…
    static let ringsAt = 0.12
    /// …and a muted string as wrongly ringing above this (muted strings still pick up a little sympathetic hum).
    static let leaksAt = 0.22

    /// A string's own note must be at least this share of the strength the fit gives it (phone mics lose some bass).
    static let fundamentalAtLeast = 0.4
    /// Overtones considered per string.
    private static let overtones = 10
    /// Two overtone shapes per string, bright and mellow (fundamental = 1); the fit mixes them, which covers how
    /// differently strings, guitars and pick positions sound.
    private static let shapes: [[Double]] = [0.5, 1.4].map { slope in (1...overtones).map { pow(Double($0), -slope) } }

    /// Frequency each string should sound in a shape like "x32010" (nil = muted).
    static func notes(of pattern: String, a4: Double = 440) -> [Double?] {
        Array(pattern.prefix(6)).enumerated().map { i, c in
            guard let fret = c.wholeNumberValue else { return nil }
            return openStrings[i] * pow(2, Double(fret) / 12) * a4 / 440
        }
    }

    /// - Parameters:
    ///   - samples: mono audio of the chord just after the strum (≈ 0.5 s).
    ///   - pattern: six characters, low E to high e: "x" muted, "0" open, digits = fret.
    static func verdicts(_ samples: [Float], sampleRate: Double, pattern: String) -> [Verdict] {
        let expected = notes(of: pattern)
        // Muted strings are modelled at their open pitch — that's what they sound if they ring.
        let model = expected.enumerated().map { i, f in f ?? openStrings[i] }
        let spectrum = Spectrum(samples, sampleRate: sampleRate)
        let close = { (a: Double, b: Double) in abs(1200 * log2(a / b)) < 25 }

        // One measurement per overtone of every string; overtones that land on the same frequency are merged.
        var points: [Double] = []
        for f in model {
            for k in 1...overtones {
                let hz = f * Double(k)
                if hz < 4000, !points.contains(where: { close($0, hz) }) { points.append(hz) }
            }
        }
        let measured = points.map { spectrum.peak(near: $0, cents: 25) }
        func shape(_ f: Double, _ weights: [Double]) -> [Double] {
            points.map { hz in
                let k = (hz / f).rounded()
                guard k >= 1, Int(k) <= overtones, close(hz, f * k) else { return 0 }
                return weights[Int(k) - 1]
            }
        }
        // Two columns per string (bright, mellow); a string's strength is the sum of its two gains.
        var templates = model.flatMap { f in shapes.map { shape(f, $0) } }
        // How far each string's note stands above the room's rumble just beside it: a quarter-tone either side
        // (where no note sits), but at least 8 Hz away so it's clear of the note's own spread in a half-second window.
        let fundamentals = model.map { f in
            let gap = max(f * 0.029, 8)
            let beside = (spectrum.peak(near: f - gap, cents: 10) + spectrum.peak(near: f + gap, cents: 10)) / 2
            return max(0, spectrum.peak(near: f, cents: 25) - 2 * beside)
        }
        var strength = [Double](repeating: 0, count: model.count)
        // A ringing string always sounds its own note. When the fit credits a string with much more than its note
        // shows, it is explaining other strings' overtones (a muted low E "ringing" from the E3 and E4 in a C) — so
        // that string is dropped and the rest refitted.
        for _ in 0..<3 {
            let gains = nonNegativeLeastSquares(templates, measured)
            strength = model.indices.map { i in gains[2 * i] + gains[2 * i + 1] }
            let invented = model.indices.filter { i in strength[i] > 0 && fundamentals[i] < strength[i] * Self.fundamentalAtLeast }
            guard !invented.isEmpty else { break }
            for i in invented { templates[2 * i] = templates[2 * i].map { _ in 0 }; templates[2 * i + 1] = templates[2 * i] }
        }
        let loudest = strength.max() ?? 0
        guard loudest > 0 else { return expected.map { $0 == nil ? .muted : .dead } }

        // Muted strings that rang count as sounding too: their overtones can hide the strings above them.
        let ringing = expected.indices.filter { expected[$0] == nil && strength[$0] / loudest > leaksAt }
        let sounding = expected.indices.map { i in ringing.contains(i) ? model[i] : expected[i] }
        return expected.indices.map { i in
            if expected[i] == nil { return ringing.contains(i) ? .ringing : .muted }
            guard identifiable(i, model: model, sounding: sounding, close: close) else { return .unclear }
            return strength[i] / loudest >= ringsAt ? .clear : .dead
        }
    }

    /// Whether string `i`'s own note is free of other strings' overtones. Only a lower string's overtone can land
    /// on it (an octave or a twelfth above), and then a dead string and a ringing one sound almost the same.
    private static func identifiable(_ i: Int, model: [Double], sounding: [Double?], close: (Double, Double) -> Bool) -> Bool {
        !sounding.indices.contains { j in
            guard j != i, let f = sounding[j], f < model[i] * 0.97 else { return false }
            let k = (model[i] / f).rounded()
            return k >= 2 && Int(k) <= overtones && close(model[i], f * k)
        }
    }

    /// min ‖Σ gᵢ·tᵢ − y‖² with every gᵢ ≥ 0, by coordinate descent (six unknowns — converges in a few dozen sweeps).
    private static func nonNegativeLeastSquares(_ templates: [[Double]], _ y: [Double]) -> [Double] {
        var g = [Double](repeating: 0, count: templates.count)
        var residual = y
        let norms = templates.map { t in t.reduce(0) { $0 + $1 * $1 } }
        for _ in 0..<200 {
            var change = 0.0
            for i in templates.indices where norms[i] > 0 {
                let t = templates[i]
                var dot = 0.0
                for b in t.indices { dot += t[b] * residual[b] }
                let next = max(0, g[i] + dot / norms[i])
                let step = next - g[i]
                if step != 0 {
                    for b in t.indices { residual[b] -= step * t[b] }
                    g[i] = next
                    change = max(change, abs(step))
                }
            }
            if change < 1e-9 { break }
        }
        return g
    }
}

/// Magnitude spectrum of a short recording (Hann window, zero-padded FFT, ≈ 0.7 Hz per bin at 48 kHz).
struct Spectrum {
    private let magnitudes: [Float]
    private let binHz: Double

    init(_ samples: [Float], sampleRate: Double) {
        let n = 1 << 16
        var input = [Float](repeating: 0, count: n)
        let count = min(samples.count, n)
        var window = [Float](repeating: 0, count: count)
        vDSP_hann_window(&window, vDSP_Length(count), Int32(vDSP_HANN_NORM))
        vDSP_vmul(samples, 1, window, 1, &input, 1, vDSP_Length(count))

        var real = [Float](repeating: 0, count: n / 2)
        var imaginary = [Float](repeating: 0, count: n / 2)
        var mags = [Float](repeating: 0, count: n / 2)
        let log2n = vDSP_Length(16)
        guard let setup = vDSP_create_fftsetup(log2n, FFTRadix(kFFTRadix2)) else {
            magnitudes = mags; binHz = sampleRate / Double(n); return
        }
        real.withUnsafeMutableBufferPointer { re in
            imaginary.withUnsafeMutableBufferPointer { im in
                var split = DSPSplitComplex(realp: re.baseAddress!, imagp: im.baseAddress!)
                input.withUnsafeBufferPointer { inp in
                    inp.baseAddress!.withMemoryRebound(to: DSPComplex.self, capacity: n / 2) {
                        vDSP_ctoz($0, 2, &split, 1, vDSP_Length(n / 2))
                    }
                }
                vDSP_fft_zrip(setup, &split, 1, log2n, FFTDirection(FFT_FORWARD))
                vDSP_zvabs(&split, 1, &mags, 1, vDSP_Length(n / 2))
            }
        }
        vDSP_destroy_fftsetup(setup)
        magnitudes = mags
        binHz = sampleRate / Double(n)
    }

    /// The strongest bin within ±`cents` of `hz`.
    func peak(near hz: Double, cents: Double) -> Double {
        let lo = max(1, Int(hz * pow(2, -cents / 1200) / binHz))
        let hi = min(magnitudes.count - 1, Int((hz * pow(2, cents / 1200) / binHz).rounded(.up)))
        guard lo <= hi else { return 0 }
        return Double(magnitudes[lo...hi].max() ?? 0)
    }
}

/// Waits for a strum on the microphone and returns the half-second of chord just after it.
final class StrumListener {
    struct Strum {
        var samples: [Float]
        var sampleRate: Double
    }

    enum Failure: Error { case noMicrophoneAccess, audioUnavailable }

    private let engine = AVAudioEngine()
    private let queue = DispatchQueue(label: "ochords.strum")
    /// Only touched on `queue`.
    private var continuation: CheckedContinuation<[Float]?, Never>?
    private var noiseFloor: Float = 0.002
    private var captured: [Float] = []
    private var heardStrum = false
    private var skipped = 0
    private var warmedUp = 0
    private var sampleRate: Double = 48000

    /// A strum is a jump to this many times the room's noise, and at least `minimumStrum`.
    private static let jump: Float = 3
    private static let minimumStrum: Float = 0.01
    /// Skip the pick's click, then keep this much of the ringing chord.
    private static let skipSeconds = 0.05
    private static let keepSeconds = 0.5
    /// The mic's first moments include a start-up click; spend them learning how loud the room is.
    private static let warmUpSeconds = 0.3

    /// Listens until a strum (returns its audio) or `timeout` (returns nil).
    func next(timeout: Duration = .seconds(6)) async throws -> Strum? {
        guard AVAudioApplication.shared.recordPermission == .granted else { throw Failure.noMicrophoneAccess }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement)
        try session.setActive(true)
        defer { finish() }

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { throw Failure.audioUnavailable }
        let rate = format.sampleRate
        queue.sync { sampleRate = rate; noiseFloor = 0.002; captured = []; heardStrum = false; skipped = 0; warmedUp = 0 }

        let samples: [Float]? = await withCheckedContinuation { cont in
            queue.sync { continuation = cont }
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] pcm, _ in
                guard let channel = pcm.floatChannelData?[0] else { return }
                let chunk = Array(UnsafeBufferPointer(start: channel, count: Int(pcm.frameLength)))
                self?.queue.async { self?.take(chunk) }
            }
            do {
                engine.prepare()
                try engine.start()
            } catch {
                queue.async { self.resume(nil) }
                return
            }
            Task { [weak self] in
                try? await Task.sleep(for: timeout)
                self?.queue.async { self?.resume(nil) }
            }
        }
        return samples.map { Strum(samples: $0, sampleRate: rate) }
    }

    func cancel() { queue.async { [weak self] in self?.resume(nil) } }

    /// On `queue`: track the room noise until the level jumps, then collect the chord.
    private func take(_ chunk: [Float]) {
        guard continuation != nil else { return }
        if !heardStrum {
            let level = sqrt(chunk.reduce(0) { $0 + $1 * $1 } / Float(max(1, chunk.count)))
            if warmedUp < Int(Self.warmUpSeconds * sampleRate) {
                warmedUp += chunk.count
                noiseFloor = warmedUp == chunk.count ? level : (noiseFloor + level) / 2
                return
            }
            if level > max(Self.minimumStrum, noiseFloor * Self.jump) {
                heardStrum = true
            } else {
                // Falls quickly in a quiet moment but rises slowly, so talking or a fan doesn't drown out the strum.
                noiseFloor = level < noiseFloor ? (noiseFloor + level) / 2 : noiseFloor * 0.98 + level * 0.02
                return
            }
        }
        let skip = Int(Self.skipSeconds * sampleRate)
        if skipped < skip {
            let drop = min(skip - skipped, chunk.count)
            skipped += drop
            captured.append(contentsOf: chunk.dropFirst(drop))
        } else {
            captured.append(contentsOf: chunk)
        }
        if captured.count >= Int(Self.keepSeconds * sampleRate) {
            resume(Array(captured.prefix(Int(Self.keepSeconds * sampleRate))))
        }
    }

    private func resume(_ samples: [Float]?) {
        guard let cont = continuation else { return }
        continuation = nil
        cont.resume(returning: samples)
    }

    private func finish() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
