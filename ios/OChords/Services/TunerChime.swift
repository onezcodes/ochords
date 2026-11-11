import AVFoundation

/// Soft bell sounds for the tuner: a two-note "ding" when a string comes into tune and a rising arpeggio when all
/// six are. Synthesised on the fly, so there are no sound files to ship.
///
/// Every note is above 1.3 kHz — out of the tuner's 60–1100 Hz range — and the tuner ignores the microphone while
/// a chime plays (see `PitchDetector.ignore(for:)`), so the bell can't be mistaken for a string.
final class TunerChime {
    enum Kind {
        case string, allStrings

        /// (frequency, start in seconds) of each bell note: E6 → B6, then E6 G♯6 B6 E7.
        fileprivate var notes: [(hz: Double, at: Double)] {
            switch self {
            case .string: [(1318.5, 0), (1975.5, 0.09)]
            case .allStrings: [(1318.5, 0), (1661.2, 0.11), (1975.5, 0.22), (2637.0, 0.33)]
            }
        }

        /// How long the sound lasts, including its ring-out.
        var duration: TimeInterval { (notes.last?.at ?? 0) + Self.ring }
        fileprivate static let ring = 0.55
    }

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let format = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1)!
    private var buffers: [Kind: AVAudioPCMBuffer] = [:]

    init() {
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 0.7
    }

    /// Plays over the tuner's audio session (which must allow playback). Silently does nothing if audio isn't
    /// available — a missing ding shouldn't interrupt tuning.
    func play(_ kind: Kind) {
        if !engine.isRunning {
            engine.prepare()
            guard (try? engine.start()) != nil else { return }
        }
        let buffer = buffers[kind] ?? render(kind)
        buffers[kind] = buffer
        player.stop()
        player.scheduleBuffer(buffer)
        player.play()
    }

    func stop() {
        player.stop()
        engine.stop()
    }

    /// A bell note: the fundamental plus a quieter inharmonic partial, a 4 ms attack and an exponential ring-out.
    private func render(_ kind: Kind) -> AVAudioPCMBuffer {
        let rate = format.sampleRate
        let frames = AVAudioFrameCount(kind.duration * rate)
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames)!
        buffer.frameLength = frames
        let out = buffer.floatChannelData![0]
        for i in 0..<Int(frames) { out[i] = 0 }
        for note in kind.notes {
            let start = Int(note.at * rate)
            for i in start..<Int(frames) {
                let t = Double(i - start) / rate
                let envelope = min(1, t / 0.004) * exp(-t * 7)
                let tone = sin(2 * .pi * note.hz * t) + 0.25 * sin(2 * .pi * note.hz * 2.76 * t) * exp(-t * 10)
                out[i] += Float(0.22 * envelope * tone)
            }
        }
        return buffer
    }
}
