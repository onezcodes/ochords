import AVFoundation
import Observation
import ShazamKit

/// Listen: names the song playing nearby (ShazamKit, from an audio fingerprint) and estimates its chords as it plays
/// (`ChordEstimator`), both from one microphone stream. The sound is analysed on the phone and never recorded; only
/// ShazamKit's fingerprint leaves it. Start, pause and stop on the main thread.
@Observable
final class SongListener {
    struct Live: Equatable {
        var chord: String?
        var confidence = 0
        var next: String?
        /// The latest chord changes, oldest first.
        var heard: [ChordEstimator.Segment] = []
        var seconds: Double = 0
        var key = ""
        var keyConfidence = 0
        var bpm: Int?
        var capo = 0
    }

    enum Match: Equatable {
        case searching
        case found(title: String, artist: String)
        /// ShazamKit didn't know it (or couldn't be reached).
        case notFound
    }

    private(set) var live = Live()
    private(set) var match: Match = .searching
    var isRunning: Bool { mic.isRunning }

    private let mic = MicInput(windowLength: ChordRecognizer.window, label: "com.onezcodes.ochords.listen")
    /// Only touched on the mic's analysis queue.
    private var analysis = Analysis()
    private let identifier = SongIdentifier()

    /// Changes shown under "Heard so far".
    private static let heardShown = 8
    /// How often the key, tempo and capo are worked out again (s).
    private static let summaryEvery = 2.0
    /// Quieter than this (RMS) is silence.
    private static let quietest = 0.002

    init() {
        identifier.onResult = { [weak self] result in
            DispatchQueue.main.async { self?.match = result }
        }
    }

    func start() throws {
        mic.onWindow = { [weak self] window, elapsed, rate in self?.hear(window, new: elapsed, sampleRate: rate) }
        try mic.start()
    }

    /// Stops listening; what's been heard is kept (start again to carry on).
    func pause() { mic.stop() }

    /// Stops and gives the estimate for everything heard.
    func finish() async -> ChordEstimator.Summary {
        mic.stop()
        identifier.stop()
        return await withCheckedContinuation { done in
            mic.perform { [self] in done.resume(returning: analysis.estimator.summary()) }
        }
    }

    // MARK: On the analysis queue

    private struct Analysis {
        var estimator = ChordEstimator()
        /// Samples heard (while listening), for the estimator's clock.
        var samples = 0
        var pending: [Float] = []
        var nextFrame = 0.0
        var nextSummary = summaryEvery
        var summary: ChordEstimator.Summary?
    }

    private func hear(_ window: [Float], new count: Int, sampleRate: Double) {
        let fresh = Array(window.suffix(min(count, window.count)))
        identifier.feed(fresh, sampleRate: sampleRate)

        // Loudness every 20 ms of sound, for the beat.
        analysis.pending += fresh
        let hop = Int(sampleRate / ChordEstimator.envelopeRate)
        while analysis.pending.count >= hop {
            let chunk = analysis.pending.prefix(hop)
            analysis.estimator.hear(loudness: sqrt(chunk.reduce(0) { $0 + Double($1 * $1) } / Double(hop)))
            analysis.pending.removeFirst(hop)
        }
        analysis.samples += fresh.count
        let now = Double(analysis.samples) / sampleRate

        // A chroma every 0.1 s of sound, for the chords.
        guard now >= analysis.nextFrame else { return }
        let rms = sqrt(window.reduce(0) { $0 + Double($1 * $1) } / Double(max(1, window.count)))
        let chroma = rms >= Self.quietest ? ChordRecognizer.chroma(window, sampleRate: sampleRate) : nil
        analysis.estimator.hear(chroma: chroma, at: analysis.nextFrame)
        analysis.nextFrame += ChordEstimator.frameInterval
        if now >= analysis.nextSummary {
            analysis.summary = analysis.estimator.summary()
            analysis.nextSummary = now + Self.summaryEvery
        }

        let estimator = analysis.estimator
        let current = estimator.segments.last
        var next = Live(chord: current?.chord, confidence: current?.confidence ?? 0,
                        next: current.flatMap { estimator.likelyNext(after: $0.chord) },
                        heard: Array(estimator.segments.suffix(Self.heardShown)), seconds: now)
        if let s = analysis.summary {
            next.key = s.key
            next.keyConfidence = s.keyConfidence
            next.bpm = s.bpm
            next.capo = s.capo
        }
        DispatchQueue.main.async { [weak self] in
            if self?.live != next { self?.live = next }
        }
    }
}

/// Names a song from the microphone with ShazamKit: fingerprints the sound (on the phone) and matches it against
/// Shazam's catalogue. Gives up after `searchFor` seconds.
private final class SongIdentifier: NSObject, SHSessionDelegate {
    var onResult: ((SongListener.Match) -> Void)?

    private let session = SHSession()
    private let lock = NSLock()
    private var fed = 0.0
    private var done = false
    private static let searchFor = 30.0

    override init() {
        super.init()
        session.delegate = self
    }

    /// New microphone samples (any thread).
    func feed(_ samples: [Float], sampleRate: Double) {
        lock.lock()
        let finished = done
        if !finished { fed += Double(samples.count) / sampleRate }
        let gaveUp = !finished && fed > Self.searchFor
        if gaveUp { done = true }
        lock.unlock()
        if gaveUp { onResult?(.notFound) }
        guard !finished, !gaveUp, !samples.isEmpty,
              let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1),
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)) else { return }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { buffer.floatChannelData![0].update(from: $0.baseAddress!, count: samples.count) }
        session.matchStreamingBuffer(buffer, at: nil)
    }

    func stop() {
        lock.lock()
        done = true
        lock.unlock()
    }

    func session(_ session: SHSession, didFind match: SHMatch) {
        guard let item = match.mediaItems.first, let title = item.title else { return }
        lock.lock()
        let first = !done
        done = true
        lock.unlock()
        if first { onResult?(.found(title: title, artist: item.artist ?? "")) }
    }

    func session(_ session: SHSession, didNotFindMatchFor signature: SHSignature, error: (any Error)?) {
        // Not yet: keeps listening until `searchFor` runs out. A missing ShazamKit service (no App ID setup, no
        // network) shows up here as an error — treated the same, so Listen still estimates the chords.
        guard error != nil else { return }
        lock.lock()
        let first = !done
        done = true
        lock.unlock()
        if first { onResult?(.notFound) }
    }
}
