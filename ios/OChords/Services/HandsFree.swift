import AVFoundation
import GameController
import Observation
import Speech
import Vision

// Hands-free page turns in Stage: a Bluetooth pedal (it's a keyboard to iOS), a nod at the front camera, or a spoken
// word. Each watcher runs only while Stage (or the Hands-free screen) is open. Start and stop on the main thread;
// callbacks arrive on the main thread.

/// What a page-turn pedal is: iOS sees it as a keyboard sending arrow / page keys.
enum PedalStatus {
    /// The connected pedal's name, or nil when none is connected.
    static var connectedName: String? {
        guard let keyboard = GCKeyboard.coalesced else { return nil }
        return keyboard.vendorName ?? "Pedal"
    }
}

// MARK: - Head nod

/// Tells a nod down and a tilt up from the face's pitch: a quick turn away from where the head usually rests, and
/// back. Pure logic, so it can be tested on recorded angles.
struct NodGesture {
    enum Move: Equatable { case down, up }

    /// Which way of turning (+1 or −1 pitch) is a nod down — learnt from the player's first nod in setup, as the
    /// camera's sign depends on how the phone is held.
    var downSign: Double = 1
    /// How far (radians, ≈ 13°) the head must turn, and how soon it must come back (s).
    static let threshold = 0.22
    static let returnWithin = 1.0
    /// No second move this soon after one (s).
    static let refractory = 0.6

    private var rest: Double?
    private var away: (sign: Double, since: Double)?
    private var lastMove = -Double.infinity

    /// The face's pitch at `time`; returns a move when one completes (head back near rest).
    mutating func hear(pitch: Double, at time: Double) -> Move? {
        guard let rest else { self.rest = pitch; return nil }
        let offset = pitch - rest
        if let a = away {
            if abs(offset) < Self.threshold / 2 {
                away = nil
                guard time - a.since <= Self.returnWithin, time - lastMove >= Self.refractory else { return nil }
                lastMove = time
                return a.sign == downSign ? .down : .up
            }
            // Held away (looking at the floor, not nodding): that's the new resting place.
            if time - a.since > Self.returnWithin { away = nil; self.rest = pitch }
            return nil
        }
        if abs(offset) >= Self.threshold {
            away = (offset > 0 ? 1 : -1, time)
        } else {
            // Drift slowly with the player's posture.
            self.rest = rest + (pitch - rest) * 0.05
        }
        return nil
    }

    /// The sign of the move just made, for learning `downSign` ("nod down once").
    mutating func learnDown(pitch: Double, at time: Double) -> Double? {
        guard let rest else { self.rest = pitch; return nil }
        let offset = pitch - rest
        if abs(offset) >= Self.threshold { return offset > 0 ? 1 : -1 }
        self.rest = rest + (pitch - rest) * 0.05
        return nil
    }
}

/// The front camera, watching the player's head for nods. Frames are analysed on the phone and never kept.
final class HeadNodWatcher: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onMove: ((NodGesture.Move) -> Void)?
    var onFace: ((Bool) -> Void)?
    /// Set while learning which way is down; called once with the sign.
    var onLearnt: ((Double) -> Void)?

    static var hasCamera: Bool { AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) != nil }
    static var permission: AVAuthorizationStatus { AVCaptureDevice.authorizationStatus(for: .video) }

    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "com.onezcodes.ochords.nod", qos: .userInitiated)
    /// Only touched on `queue`.
    private var gesture = NodGesture()
    private var learning = false
    private var sawFace: Bool?
    private var configured = false

    init(downSign: Double) {
        super.init()
        gesture.downSign = downSign
    }

    func start() {
        queue.async { [self] in
            if !configured { configure() }
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        queue.async { [self] in if session.isRunning { session.stopRunning() } }
    }

    /// The next clear turn of the head is a nod down.
    func learnDown() { queue.async { [self] in learning = true; gesture = NodGesture() } }

    private func configure() {
        configured = true
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: camera) else { return }
        session.beginConfiguration()
        session.sessionPreset = .vga640x480
        if session.canAddInput(input) { session.addInput(input) }
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        if session.canAddOutput(output) { session.addOutput(output) }
        // 15 frames a second is plenty for a nod, and easy on the battery.
        try? camera.lockForConfiguration()
        camera.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 15)
        camera.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: 15)
        camera.unlockForConfiguration()
        session.commitConfiguration()
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let request = VNDetectFaceRectanglesRequest()
        request.revision = VNDetectFaceRectanglesRequestRevision3
        // The front camera's frames are sideways and mirrored for a phone held upright.
        try? VNImageRequestHandler(cvPixelBuffer: pixels, orientation: .leftMirrored).perform([request])
        let face = request.results?.max { $0.boundingBox.width < $1.boundingBox.width }
        let found = face != nil
        if found != sawFace {
            sawFace = found
            DispatchQueue.main.async { self.onFace?(found) }
        }
        guard let pitch = face?.pitch?.doubleValue else { return }
        let time = CMSampleBufferGetPresentationTimeStamp(sampleBuffer).seconds
        if learning {
            if let sign = gesture.learnDown(pitch: pitch, at: time) {
                learning = false
                gesture = NodGesture()
                gesture.downSign = sign
                DispatchQueue.main.async { self.onLearnt?(sign) }
            }
            return
        }
        if let move = gesture.hear(pitch: pitch, at: time) {
            DispatchQueue.main.async { self.onMove?(move) }
        }
    }
}

// MARK: - Voice

/// Spoken page turns: "next", "back", "chorus", "stop", "play". English words, understood on the phone when it can.
@Observable
final class VoiceCommands {
    enum Command: String, CaseIterable { case next, back, chorus, stop, play }

    var onCommand: ((Command) -> Void)?
    var onHeard: ((String) -> Void)?

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    /// Words of the current result already acted on.
    private var handled = 0
    private(set) var isRunning = false

    /// Other words people say for the same thing.
    private static let words: [String: Command] = [
        "next": .next, "forward": .next, "down": .next,
        "back": .back, "previous": .back, "up": .back,
        "chorus": .chorus,
        "stop": .stop, "pause": .stop,
        "play": .play, "start": .play, "go": .play,
    ]

    static var available: Bool { SFSpeechRecognizer(locale: Locale(identifier: "en-US"))?.isAvailable == true }
    static var permissionUndecided: Bool { SFSpeechRecognizer.authorizationStatus() == .notDetermined }
    static var hasPermission: Bool { SFSpeechRecognizer.authorizationStatus() == .authorized && MicInput.hasPermission }

    static func requestPermission() async -> Bool {
        let speech = await withCheckedContinuation { done in SFSpeechRecognizer.requestAuthorization { done.resume(returning: $0) } }
        guard speech == .authorized else { return false }
        return MicInput.hasPermission ? true : await MicInput.requestPermission()
    }

    func start() throws {
        guard !isRunning else { return }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothA2DP])
        try session.setActive(true)
        let input = engine.inputNode
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { [weak self] buffer, _ in
            self?.request?.append(buffer)
        }
        engine.prepare()
        try engine.start()
        isRunning = true
        listen()
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        task?.cancel()
        task = nil
        request = nil
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// One recognition task; a new one starts when it ends (they're limited to about a minute).
    private func listen() {
        guard isRunning, let recognizer else { return }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .confirmation
        request.contextualStrings = Command.allCases.map(\.rawValue)
        if recognizer.supportsOnDeviceRecognition { request.requiresOnDeviceRecognition = true }
        self.request = request
        handled = 0
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async { self?.received(result, ended: error != nil || result?.isFinal == true) }
        }
    }

    private func received(_ result: SFSpeechRecognitionResult?, ended: Bool) {
        if let result {
            let words = result.bestTranscription.segments.map { $0.substring.lowercased().trimmingCharacters(in: .punctuationCharacters) }
            for word in words.dropFirst(handled) {
                if let command = Self.words[word] {
                    onHeard?(command.rawValue.capitalized)
                    onCommand?(command)
                }
            }
            handled = max(handled, words.count)
        }
        if ended, isRunning {
            task = nil
            request?.endAudio()
            listen()
        }
    }
}
