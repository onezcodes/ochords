import AVFoundation
import Synchronization

/// The microphone, delivered as the newest window of sound every 20 ms on a background queue — shared by the tuner
/// and the chord follower. Start and stop on the main thread.
///
/// Audio comes from an `AVAudioSinkNode` (every ≈ 10 ms) rather than an input tap, which iOS delivers in ≈ 100 ms
/// chunks whatever buffer size is asked for — too slow to follow a string or a strum.
final class MicInput {
    enum Failure: Error { case noMicrophoneAccess, audioUnavailable }

    /// Called on the analysis queue with the newest `windowLength` samples (fewer just after starting), the samples
    /// that arrived since the last call, and the sample rate.
    var onWindow: ((_ window: [Float], _ elapsed: Int, _ sampleRate: Double) -> Void)?
    /// Called on the analysis queue instead of `onWindow` while the app's own sound is being ignored.
    var onIgnored: (() -> Void)?

    let windowLength: Int
    private let engine = AVAudioEngine()
    private var sink: AVAudioSinkNode?
    private var timer: DispatchSourceTimer?
    private let ring = SampleRing()
    private let analysis: DispatchQueue
    /// Only touched on `analysis`.
    private var window: [Float] = []
    private var lastWritten = 0
    private var ignoreUntil = 0
    private(set) var isRunning = false

    private static let interval = DispatchTimeInterval.milliseconds(20)
    /// A window only when at least ≈ 10 ms of new sound has arrived.
    private static let minimumNewSamples = 480

    static var hasPermission: Bool { AVAudioApplication.shared.recordPermission == .granted }
    static var permissionUndecided: Bool { AVAudioApplication.shared.recordPermission == .undetermined }
    static func requestPermission() async -> Bool { await AVAudioApplication.requestRecordPermission() }

    init(windowLength: Int, label: String) {
        precondition(windowLength <= SampleRing.capacity / 2)
        self.windowLength = windowLength
        analysis = DispatchQueue(label: label, qos: .userInteractive)
    }

    func start() throws {
        guard !isRunning else { return }
        guard Self.hasPermission else { throw Failure.noMicrophoneAccess }

        let session = AVAudioSession.sharedInstance()
        // .measurement turns off the voice processing (gain control, filters) that would colour the sound; playback
        // is allowed (through the loudspeaker) for the app's own chimes.
        try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothA2DP])
        // Small hardware buffers so sound reaches the sink quickly (the system may round this).
        try? session.setPreferredIOBufferDuration(0.005)
        try session.setActive(true)
        if session.isInputGainSettable { try? session.setInputGain(1) }

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { throw Failure.audioUnavailable }
        let sampleRate = format.sampleRate

        // Runs on the real-time audio thread: only copies into the ring, no locks or allocation.
        let ring = ring
        let sink = AVAudioSinkNode { _, frames, audio in
            let buffers = UnsafeMutableAudioBufferListPointer(UnsafeMutablePointer(mutating: audio))
            if let data = buffers.first?.mData {
                ring.write(data.assumingMemoryBound(to: Float.self), count: Int(frames))
            }
            return noErr
        }
        engine.attach(sink)
        engine.connect(input, to: sink, format: format)
        self.sink = sink

        let timer = DispatchSource.makeTimerSource(queue: analysis)
        timer.schedule(deadline: .now() + Self.interval, repeating: Self.interval, leeway: .milliseconds(2))
        timer.setEventHandler { [weak self] in self?.tick(sampleRate: sampleRate) }
        self.timer = timer

        engine.prepare()
        do {
            try engine.start()
        } catch {
            teardown()
            throw error
        }
        timer.resume()
        isRunning = true
    }

    func stop() {
        guard isRunning else { return }
        engine.stop()
        teardown()
        isRunning = false
        analysis.async { [weak self] in self?.window.removeAll() }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// Runs `work` on the analysis queue, in order with the windows (for state the consumer keeps there).
    func perform(_ work: @escaping () -> Void) { analysis.async(execute: work) }

    /// Skips the sound for `seconds` — while the app plays its own sound — plus long enough for it to leave the
    /// window, so it's never analysed.
    func ignore(for seconds: TimeInterval) {
        analysis.async { [weak self] in
            guard let self else { return }
            let rate = AVAudioSession.sharedInstance().sampleRate
            ignoreUntil = ring.written + Int((seconds + 0.2) * rate) + windowLength
        }
    }

    private func teardown() {
        timer?.cancel()
        timer = nil
        if let sink {
            engine.disconnectNodeInput(sink)
            engine.detach(sink)
        }
        sink = nil
    }

    /// On the analysis queue, every 20 ms.
    private func tick(sampleRate: Double) {
        let written = ring.written
        let elapsed = written - lastWritten
        guard elapsed >= Self.minimumNewSamples else { return }
        lastWritten = written
        guard written >= ignoreUntil else {
            onIgnored?()
            return
        }
        ring.latest(min(written, windowLength), into: &window)
        onWindow?(window, elapsed, sampleRate)
    }
}

/// Single-writer ring of the latest microphone samples: the audio thread writes, the analysis queue reads.
/// It holds at least two full windows, so a read can't be overtaken by the writer in the few microseconds it takes.
private final class SampleRing: @unchecked Sendable {
    static let capacity = 1 << 15
    private let storage = UnsafeMutablePointer<Float>.allocate(capacity: capacity)
    private let count = Atomic<Int>(0)

    init() { storage.initialize(repeating: 0, count: Self.capacity) }
    deinit { storage.deallocate() }

    /// Samples written since the start.
    var written: Int { count.load(ordering: .acquiring) }

    /// Audio thread only.
    func write(_ samples: UnsafePointer<Float>, count frames: Int) {
        let start = count.load(ordering: .relaxed)
        for i in 0..<frames { storage[(start + i) & (Self.capacity - 1)] = samples[i] }
        count.store(start + frames, ordering: .releasing)
    }

    /// Copies the newest `n` samples into `out`, oldest first; returns how many were copied.
    @discardableResult
    func latest(_ n: Int, into out: inout [Float]) -> Int {
        let end = written
        let n = min(n, end, Self.capacity)
        out.removeAll(keepingCapacity: true)
        out.reserveCapacity(n)
        for i in (end - n)..<end { out.append(storage[i & (Self.capacity - 1)]) }
        return n
    }
}
