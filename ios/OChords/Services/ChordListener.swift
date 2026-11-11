import Foundation

/// Listens to the player strumming and follows them through a song's chords (see `ChordFollower`), ten readings a
/// second. Start and stop on the main thread; moves arrive on the main thread.
final class ChordListener {
    typealias Failure = MicInput.Failure

    /// The player moved on to this chord.
    var onMove: ((ChordFollower.Step) -> Void)?

    private let mic = MicInput(windowLength: ChordRecognizer.window, label: "ochords.follow")
    /// Only touched on the mic's analysis queue.
    private var follower: ChordFollower?
    private var sinceReading = 0

    /// A reading every 0.1 s: chords change at most a few times a second, and each reading looks at 0.25 s of sound.
    private static let readingSeconds = 0.1

    var isRunning: Bool { mic.isRunning }

    init() {
        mic.onWindow = { [weak self] window, elapsed, sampleRate in self?.read(window, elapsed: elapsed, sampleRate: sampleRate) }
    }

    /// - Parameter steps: the song's chords in play order, as they sound.
    func start(steps: [ChordFollower.Step], resumeAt: (item: Int, line: Int?)? = nil) throws {
        mic.perform { [weak self] in
            var f = ChordFollower(steps: steps)
            if let resumeAt { f.resync(item: resumeAt.item, line: resumeAt.line) }
            self?.follower = f
            self?.sinceReading = 0
        }
        try mic.start()
    }

    func stop() { mic.stop() }

    /// The player tapped a line or changed section: follow on from there.
    func resync(item: Int, line: Int?) {
        mic.perform { [weak self] in self?.follower?.resync(item: item, line: line) }
    }

    /// On the analysis queue.
    private func read(_ window: [Float], elapsed: Int, sampleRate: Double) {
        sinceReading += elapsed
        let hop = Int(sampleRate * Self.readingSeconds)
        guard sinceReading >= hop, window.count >= hop * 2, var f = follower else { return }
        sinceReading = 0
        let recent = window.suffix(hop)
        let level = sqrt(recent.reduce(Float(0)) { $0 + $1 * $1 } / Float(recent.count))
        let moved = f.hear(ChordRecognizer.chroma(window, sampleRate: sampleRate), level: level)
        follower = f
        if moved, let step = f.current {
            DispatchQueue.main.async { [weak self] in self?.onMove?(step) }
        }
    }
}
