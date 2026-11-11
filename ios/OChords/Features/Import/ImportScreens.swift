import SwiftUI
import PhotosUI
import AVFoundation
import PDFKit
import UniformTypeIdentifiers

// Import group: 2 Scan & read sheet · 36b Scan failed · 2b/2c Zawgyi · 3 Review import ·
// 40a–d Listen · 49c–e Rewarded ads.

// MARK: - Shared helpers (group-private)

/// One-time permission primers for this session (camera → first scan, mic → first listen).
@MainActor private enum ImportSession {
    static var askedCamera = false
    static var askedMic = false
}

/// Fixed colours used on the always-dark Import / Listen / ad screens.
private enum ImportNight {
    static let text = OC.nightText
    static let muted = OC.nightMuted
    static let soft = Color(hex: 0xBDB9B3)
    static let teal = Color(hex: 0x5EEAD4)
    static let tealBg = Color(hex: 0x14B8A6, alpha: 0.18)
    static let goldText = Color(hex: 0xFFC940)
    static let red = Color(hex: 0xFCA5A5)
    static let redBg = Color(hex: 0xF87171, alpha: 0.08)
    static let blue = Color(hex: 0x93C5FD)
    static let blueBg = Color(hex: 0x3B82F6, alpha: 0.18)
    static let outline = Color(hex: 0x4A4743)

    static func confidenceColor(_ c: Int) -> Color { c >= 80 ? teal : c >= 70 ? goldText : red }
}


/// A drawn stand-in for the photographed chord sheet (paper with chord and lyric lines).
private struct ImportSheetArt: View {
    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width / 300
            ZStack(alignment: .topLeading) {
                Color(hex: 0xFBF8F1)
                VStack(alignment: .leading, spacing: 0) {
                    bar(0.45, 5 * s, dark: true).padding(.top, 16 * s)
                    ForEach(0..<6, id: \.self) { block in
                        VStack(alignment: .leading, spacing: 5 * s) {
                            bar(0.18, 4 * s, dark: true)
                            ForEach(0..<(block % 3 == 0 ? 1 : 3), id: \.self) { l in
                                HStack(spacing: 14 * s) {
                                    ForEach(0..<4, id: \.self) { _ in bar(0.04, 4 * s, gold: true) }
                                }
                                bar(l == 2 ? 0.55 : 0.85, 3 * s)
                            }
                        }
                        .padding(.top, 14 * s)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 18 * s)
            }
        }
    }

    private func bar(_ w: CGFloat, _ h: CGFloat, dark: Bool = false, gold: Bool = false) -> some View {
        GeometryReader { g in
            RoundedRectangle(cornerRadius: h / 2)
                .fill(gold ? Color(hex: 0x8F5E00, alpha: 0.55) : Color(hex: 0x28221A, alpha: dark ? 0.55 : 0.3))
                .frame(width: gold ? h * 3.5 : g.size.width * w, height: h)
        }
        .frame(height: h)
    }
}

// MARK: - Camera

/// Back camera for sheet photos: live preview, photo capture, torch.
private final class ImportCamera: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    enum Status { case waiting, running, unavailable, denied }

    @Published private(set) var status: Status = .waiting
    @Published private(set) var hasTorch = false
    @Published private(set) var torchOn = false

    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "ochords.scan-camera")
    private var device: AVCaptureDevice?
    private var configured = false
    private var pending: CheckedContinuation<Data?, Never>?

    /// Starts the preview if access is granted. Waits (no prompt) while access is undecided.
    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: break
        case .notDetermined: status = .waiting; return
        default: status = .denied; return
        }
        queue.async { [self] in
            if !configured { configure() }
            guard device != nil else { DispatchQueue.main.async { self.status = .unavailable }; return }
            if !session.isRunning { session.startRunning() }
            DispatchQueue.main.async { self.status = .running }
        }
    }

    func stop() {
        setTorch(false)
        queue.async { [self] in if session.isRunning { session.stopRunning() } }
    }

    private func configure() {
        configured = true
        guard let d = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: d) else { return }
        session.beginConfiguration()
        session.sessionPreset = .photo
        if session.canAddInput(input) { session.addInput(input) }
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
        if (try? d.lockForConfiguration()) != nil {
            if d.isFocusModeSupported(.continuousAutoFocus) { d.focusMode = .continuousAutoFocus }
            d.unlockForConfiguration()
        }
        device = d
        let torch = d.hasTorch
        DispatchQueue.main.async { self.hasTorch = torch }
    }

    func setTorch(_ on: Bool) {
        torchOn = on && hasTorch
        let want = torchOn
        queue.async { [self] in
            guard let d = device, d.hasTorch, (try? d.lockForConfiguration()) != nil else { return }
            d.torchMode = want ? .on : .off
            d.unlockForConfiguration()
        }
    }

    /// JPEG data of a full-resolution photo, or nil if the camera isn't running.
    func capture() async -> Data? {
        guard status == .running, pending == nil else { return nil }
        return await withCheckedContinuation { cont in
            pending = cont
            queue.async { [self] in output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self) }
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let data = error == nil ? photo.fileDataRepresentation() : nil
        DispatchQueue.main.async {
            self.pending?.resume(returning: data)
            self.pending = nil
        }
    }
}

private struct ImportCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.preview.session = session
        v.preview.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

// MARK: - 2 · Scan & read sheet (+ 36b Scan failed)

private enum ImportPhase { case viewfinder, crop, reading, done, failed }

struct ImportView: View {
    /// A photo or PDF picked before opening the screen: it's read straight away, without the camera.
    var picked: ScanSource? = nil

    @Environment(AppState.self) private var app
    @Environment(\.openURL) private var openURL
    @StateObject private var camera = ImportCamera()
    @State private var phase: ImportPhase = .viewfinder
    @State private var stage: ScanStage?
    @State private var progress: Double = 0
    @State private var result: ScanResult?
    @State private var source: ScanSource?
    @State private var pickedImage: UIImage?
    @State private var pickerItem: PhotosPickerItem?
    @State private var showPicker = false
    @State private var showFiles = false
    @State private var cropToPage = true
    @State private var scanTask: Task<Void, Never>?
    @State private var toast: Toast?
    @State private var startedPicked = false
    /// A picked photo waiting to be cropped (upright, at reading size).
    @State private var cropping: CGImage?

    var body: some View {
        ZStack {
            if phase == .failed {
                ImportFailedView(
                    photo: pickedImage,
                    onClose: { app.pop() },
                    onRetake: retake,
                    onTypeIn: typeItIn,
                    onChooseAnother: { showPicker = true }
                )
                .transition(.opacity)
            } else if phase == .crop, let cropping {
                ImportCropView(image: cropping, onCancel: cancelCrop, onDone: cropped)
                    .transition(.opacity)
            } else {
                darkScreen.transition(.opacity)
            }
        }
        .background { if phase == .failed { WarmBackground() } else { NightBackground() } }
        .toolbar(.hidden, for: .navigationBar)
        .photosPicker(isPresented: $showPicker, selection: $pickerItem, matching: .images)
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.pdf, .image], onCompletion: importFile)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                pickerItem = nil
                guard let data else { toast = Toast(text: "Couldn’t open that photo", systemImage: "exclamationmark.triangle"); return }
                read(.image(data))
            }
        }
        .onAppear {
            #if DEBUG
            if let path = app.demoScanFile, let data = FileManager.default.contents(atPath: path) {
                app.demoScanFile = nil
                if path.lowercased().hasSuffix(".pdf") {
                    pickedImage = PDFDocument(data: data)?.page(at: 0).flatMap { ScanPDF.render($0, maxSide: 1200) }.map(UIImage.init(cgImage:))
                    run(.pdf(data))
                } else {
                    pickedImage = UIImage(data: data)
                    run(.image(data))
                }
                return
            }
            #endif
            if let picked, !startedPicked {
                startedPicked = true
                read(picked)
                return
            }
            if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined && !ImportSession.askedCamera {
                ImportSession.askedCamera = true
                app.modal = .permission(.camera)
            } else {
                camera.start()
            }
        }
        // The camera primer is a full-screen modal; start once it closes (access granted or not).
        .onChange(of: app.modal) { _, m in if m == nil && phase == .viewfinder { camera.start() } }
        .onChange(of: phase) { _, p in if p == .viewfinder { camera.start() } else { camera.stop() } }
        .onDisappear { scanTask?.cancel(); camera.stop() }
        .toast($toast)
    }

    // MARK: Dark camera / reading screen

    private var darkScreen: some View {
        VStack(spacing: 0) {
            HStack {
                Button("Cancel") { scanTask?.cancel(); app.pop() }
                    .font(.outfit(16, .semibold)).foregroundStyle(OC.gold)
                    .padding(.horizontal, 10).frame(minHeight: 44)
                Spacer()
                Text(phase == .viewfinder ? "Scan sheet" : "Reading sheet").font(.outfit(16, .bold)).foregroundStyle(OC.paper)
                Spacer()
                Button(action: toggleCrop) {
                    Image(systemName: cropToPage ? "crop" : "rectangle.dashed").font(.system(size: 19, weight: .medium)).frame(width: 44, height: 44)
                }
                .foregroundStyle(OC.paper)
                .opacity(canToggleCrop ? 1 : 0)
                .disabled(!canToggleCrop)
                .accessibilityLabel(cropToPage ? "Use the whole photo" : "Crop to the page")
            }
            .padding(.horizontal, 12)

            if phase == .viewfinder { viewfinder } else { reading }
        }
    }

    private var viewfinder: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(LinearGradient(colors: [Color(hex: 0x3A332A), Color(hex: 0x14120F)], startPoint: .topLeading, endPoint: .bottomTrailing))
                switch camera.status {
                case .running:
                    ImportCameraPreview(session: camera.session)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .accessibilityLabel("Camera preview")
                    guide
                case .waiting:
                    ProgressView().tint(OC.paper)
                case .unavailable:
                    cameraMessage(icon: "camera.badge.ellipsis", title: "No camera here",
                                  text: "Choose a photo or PDF of the sheet instead.", action: ("Choose a photo", { showPicker = true }))
                case .denied:
                    cameraMessage(icon: "video.slash", title: "Camera is off for O Chords",
                                  text: "Turn it on in Settings to scan paper sheets, or choose a photo instead.",
                                  action: ("Open Settings", { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            if !app.isPro { freeScansLink.padding(.top, 14) }

            HStack {
                Menu {
                    Button { showPicker = true } label: { Label("Photo library", systemImage: "photo.on.rectangle") }
                    Button { showFiles = true } label: { Label("Files — PDF or image", systemImage: "doc") }
                } label: {
                    Image(systemName: "photo.on.rectangle").font(.system(size: 20, weight: .medium))
                        .frame(width: 52, height: 52).nightGlass(radius: 26)
                }
                .foregroundStyle(OC.paper)
                .accessibilityLabel("Choose a photo or PDF")
                Spacer()
                Button(action: shutter) {
                    Circle().fill(.white).frame(width: 64, height: 64)
                        .padding(5).overlay(Circle().strokeBorder(.white, lineWidth: 3))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(camera.status == .running ? "Take photo" : "Choose a photo")
                Spacer()
                Button { camera.setTorch(!camera.torchOn) } label: {
                    Image(systemName: camera.torchOn ? "bolt.fill" : "bolt.slash")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 52, height: 52)
                        .background(Circle().fill(camera.torchOn ? OC.gold.opacity(0.3) : OC.nightGlass))
                        .overlay(Circle().strokeBorder(camera.torchOn ? OC.gold : OC.nightBorder))
                }
                .foregroundStyle(camera.torchOn ? OC.gold : OC.paper)
                .opacity(camera.hasTorch && camera.status == .running ? 1 : 0)
                .disabled(!(camera.hasTorch && camera.status == .running))
                .accessibilityLabel("Flash")
                .accessibilityValue(camera.torchOn ? "On" : "Off")
            }
            .padding(.horizontal, 32)
            .padding(.top, 14)
            .padding(.bottom, 32)
        }
    }

    private var guide: some View {
        ZStack {
            ImportGuideFrame().padding(28)
            VStack {
                Spacer()
                Text("Fit the whole page inside the gold frame")
                    .font(.outfit(13, .semibold)).foregroundStyle(OC.paper)
                    .padding(.horizontal, 12).frame(height: 32)
                    .background(Capsule().fill(Color.black.opacity(0.55)))
                    .padding(.bottom, 14)
            }
        }
        .allowsHitTesting(false)
    }

    private func cameraMessage(icon: String, title: String, text: String, action: (String, () -> Void)) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 30)).foregroundStyle(OC.gold)
            Text(title).font(.outfit(18, .bold)).foregroundStyle(OC.paper)
            Text(text).font(.outfit(14)).foregroundStyle(OC.nightMuted).multilineTextAlignment(.center)
            Button(action.0, action: action.1).buttonStyle(.gold(height: 44)).frame(width: 200).padding(.top, 6)
        }
        .padding(28)
    }

    private var freeScansLink: some View {
        Button { app.modal = .reward(.scan) } label: {
            Text("\(app.freeScansLeft) of 10 free scans left today")
                .font(.outfit(12, .semibold)).foregroundStyle(OC.nightMuted)
                .frame(minHeight: 28)
        }
        .buttonStyle(.plain)
    }

    private var reading: some View {
        VStack(spacing: 0) {
            ImportReadingSheet(
                progress: progress,
                image: result?.thumbnail.flatMap(UIImage.init(data:)) ?? pickedImage,
                regions: result?.regions ?? [],
                key: result?.song.key
            )
            .padding(.top, 6)
            .padding(.horizontal, 24)
            .layoutPriority(-1)

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text(headline)
                        .font(.outfit(20, .bold)).foregroundStyle(OC.paper)
                        .contentTransition(.opacity)
                    Spacer()
                    Text(result.map { "\($0.song.usedChords.count) chords · \($0.song.isMyanmar ? "Myanmar" : "English")" } ?? "\(Int(progress * 100))%")
                        .font(.outfit(13)).foregroundStyle(ImportNight.soft)
                        .monospacedDigit()
                }
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(OC.nightGlass).overlay(Capsule().strokeBorder(OC.nightBorder))
                        Capsule().fill(OC.gold).frame(width: g.size.width * progress)
                    }
                }
                .frame(height: 6)
                VStack(alignment: .leading, spacing: 9) {
                    ImportStepRow(text: stepText(.page), state: stepState(.page))
                    ImportStepRow(text: stepText(.text), state: stepState(.text))
                    ImportStepRow(text: stepText(.chords), state: stepState(.chords))
                    ImportStepRow(text: stepText(.song), state: stepState(.song))
                    if result?.detectedZawgyi == true {
                        ImportStepRow(text: "Typed in Zawgyi — converted to Unicode", state: .done)
                    }
                    if let n = result?.needsLyrics.count, n > 0 {
                        ImportStepRow(text: lyricsToTypeText(n), state: .todo)
                    }
                }
            }
            .frame(maxWidth: 342)
            .padding(.horizontal, 24)
            .padding(.top, 18)

            Spacer(minLength: 8)

            if !app.isPro { freeScansLink.padding(.bottom, 8) }

            HStack(spacing: 12) {
                Button("Retake", action: retake)
                    .font(.outfit(16, .semibold)).foregroundStyle(OC.paper)
                    .frame(width: 120, height: 52)
                    .background(RoundedRectangle(cornerRadius: OC.rButton).fill(OC.nightGlass))
                    .overlay(RoundedRectangle(cornerRadius: OC.rButton).strokeBorder(ImportNight.outline))
                Button("Review import", action: continueToReview)
                    .buttonStyle(.gold(height: 52))
                    .disabled(phase != .done)
                    .opacity(phase == .done ? 1 : 0.5)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
    }

    private var headline: String {
        if let r = result { return "Found \(r.song.sectionCount) section\(r.song.sectionCount == 1 ? "" : "s")" }
        switch stage {
        case .page, nil: return "Finding the page…"
        case .text: return "Reading the sheet…"
        case .chords: return "Finding chords…"
        case .song: return "Building the song…"
        }
    }

    private func stepText(_ s: ScanStage) -> String {
        switch s {
        case .page:
            if case .pdf = source { return "Opened the PDF" }
            guard let r = result else { return cropToPage ? "Finding the page edges" : "Using the whole photo" }
            return r.croppedToPage ? "Found the page and straightened it" : "Using the whole photo"
        case .text:
            guard let r = result else { return "Reading text" }
            return "Read \(r.linesRead) line\(r.linesRead == 1 ? "" : "s") of text" + (r.readOnServer ? ", Myanmar included" : "")
        case .chords:
            guard let r = result else { return "Finding chords above the lyrics" }
            let chords = r.song.usedChords
            return "Read chords — " + chords.prefix(6).joined(separator: " · ") + (chords.count > 6 ? " …" : "")
        case .song:
            guard let r = result else { return "Laying out sections" }
            return "Arrangement — " + r.song.sections.filter { $0.kind != .flow }.map(\.label.capitalized).prefix(5).joined(separator: " · ")
        }
    }

    private func lyricsToTypeText(_ n: Int) -> String {
        let lines = "\(n) lyric line\(n == 1 ? "" : "s") to type in"
        if result?.readOnServer == true { return lines + " — they weren’t clear enough to read" }
        return lines + (app.isSignedIn ? " — Myanmar reading isn’t available right now" : " — sign in to read Myanmar lyrics")
    }

    private func stepState(_ s: ScanStage) -> ImportStepRow.State {
        if phase == .done { return .done }
        guard let stage else { return .pending }
        return s < stage ? .done : (s == stage ? .active : .pending)
    }

    private var canToggleCrop: Bool {
        guard phase != .viewfinder, case .image = source else { return false }
        return true
    }

    // MARK: Actions

    private func shutter() {
        guard camera.status == .running else { showPicker = true; return }
        Task {
            guard let data = await camera.capture() else { toast = Toast(text: "Couldn’t take the photo", systemImage: "exclamationmark.triangle"); return }
            pickedImage = UIImage(data: data)
            run(.image(data))
        }
    }

    private func importFile(_ picked: Result<URL, Error>) {
        guard case .success(let url) = picked else { return }
        guard let source = ScanSource.file(url) else { toast = Toast(text: "Couldn’t open that file", systemImage: "exclamationmark.triangle"); return }
        read(source)
    }

    /// Reads a picked photo or PDF (showing it behind the progress). A photo is cropped first.
    private func read(_ source: ScanSource) {
        switch source {
        case .image(let data):
            guard let upright = ScanImage.prepare(data)?.cgImage else {
                toast = Toast(text: "Couldn’t open that photo", systemImage: "exclamationmark.triangle")
                return
            }
            cropping = upright
            withAnimation { phase = .crop }
        case .pdf(let data):
            pickedImage = PDFDocument(data: data)?.page(at: 0).flatMap { ScanPDF.render($0, maxSide: 1200) }.map(UIImage.init(cgImage:))
            run(source)
        }
    }

    /// The sheet as cropped: read it as it is (the automatic page crop would trim it again).
    private func cropped(_ sheet: CGImage) {
        cropping = nil
        cropToPage = false
        let image = UIImage(cgImage: sheet)
        pickedImage = image
        guard let data = image.jpegData(compressionQuality: 0.92) else { return }
        run(.image(data))
    }

    /// Back to where the photo came from: the Library (picked there) or the camera.
    private func cancelCrop() {
        cropping = nil
        if picked != nil { app.pop() } else { withAnimation { phase = .viewfinder } }
    }

    private func run(_ src: ScanSource) {
        guard app.isPro || app.freeScansLeft > 0 else { app.modal = .reward(.scan); return }
        scanTask?.cancel()
        source = src
        withAnimation { phase = .reading; progress = 0.04; result = nil; stage = nil }
        let scanner = app.scanner
        (scanner as? OnDeviceScanService)?.cropToPage = cropToPage
        scanTask = Task { @MainActor in
            do {
                let r = try await scanner.scan(src) { s in
                    stage = s
                    withAnimation(.easeOut(duration: 0.5)) { progress = [0.15, 0.55, 0.8, 0.92][s.rawValue] }
                }
                try Task.checkCancellation()
                if !app.isPro { app.freeScansLeft = max(0, app.freeScansLeft - 1) }
                withAnimation(.snappy) { progress = 1; result = r; phase = .done }
                #if DEBUG
                if UserDefaults.standard.bool(forKey: "demoAutoReview") {
                    try? await Task.sleep(for: .seconds(1.5))
                    continueToReview()
                }
                #endif
            } catch is CancellationError {
            } catch {
                guard !Task.isCancelled else { return }
                withAnimation { phase = .failed }
            }
        }
    }

    private func retake() {
        scanTask?.cancel()
        withAnimation { phase = .viewfinder; progress = 0; stage = nil; result = nil; pickedImage = nil; source = nil }
    }

    private func toggleCrop() {
        guard let source else { return }
        cropToPage.toggle()
        toast = Toast(text: cropToPage ? "Cropped to the page edges" : "Using the whole photo", systemImage: "crop")
        // Re-reading the same photo doesn't use up another free scan.
        if phase == .done && !app.isPro { app.freeScansLeft += 1 }
        run(source)
    }

    private func continueToReview() {
        guard let result else { return }
        if result.detectedZawgyi && !app.alwaysConvertZawgyi {
            app.push(.zawgyi(result))
        } else {
            app.push(.review(result))
        }
    }

    private func typeItIn() {
        let blank = Song(id: UUID().uuidString, title: "", artist: "", key: "C", sections: [
            SongSection(kind: .verse, label: "VERSE 1", lines: ["[C]"])
        ])
        app.push(.review(ScanResult(song: blank, detectedZawgyi: false, lowConfidence: [])))
    }
}

/// Gold corner brackets marking where the page should sit.
private struct ImportGuideFrame: View {
    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height, l: CGFloat = 28
            Path { p in
                p.move(to: CGPoint(x: 0, y: l)); p.addLine(to: .zero); p.addLine(to: CGPoint(x: l, y: 0))
                p.move(to: CGPoint(x: w - l, y: 0)); p.addLine(to: CGPoint(x: w, y: 0)); p.addLine(to: CGPoint(x: w, y: l))
                p.move(to: CGPoint(x: w, y: h - l)); p.addLine(to: CGPoint(x: w, y: h)); p.addLine(to: CGPoint(x: w - l, y: h))
                p.move(to: CGPoint(x: l, y: h)); p.addLine(to: CGPoint(x: 0, y: h)); p.addLine(to: CGPoint(x: 0, y: h - l))
            }
            .stroke(OC.gold, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(OC.gold.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [6, 5]))
        }
    }
}

/// The sheet being read: a gold scan line while reading, then the sections found, outlined on the page.
private struct ImportReadingSheet: View {
    let progress: Double
    let image: UIImage?
    let regions: [ScanRegion]
    let key: String?

    private func colors(_ kind: SectionKind) -> (border: Color, fill: Color) {
        switch kind {
        case .verse: (Color(hex: 0x1D4ED8), Color(hex: 0x93C5FD, alpha: 0.2))
        case .chorus: (Color(hex: 0xC28C00), Color(hex: 0xFDBA74, alpha: 0.24))
        case .bridge: (Color(hex: 0x6D28D9), Color(hex: 0xC4B5FD, alpha: 0.22))
        case .music, .intro, .flow: (Color(hex: 0x0F766E), Color(hex: 0x5EEAD4, alpha: 0.18))
        }
    }

    var body: some View {
        page
            .overlay {
                GeometryReader { g in
                    ForEach(Array(regions.enumerated()), id: \.offset) { _, r in
                        let c = colors(r.kind)
                        RoundedRectangle(cornerRadius: 6)
                            .fill(c.fill)
                            .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(c.border, lineWidth: 2))
                            .frame(width: (r.width * g.size.width) + 8, height: (r.height * g.size.height) + 6)
                            .offset(x: r.x * g.size.width - 4, y: r.y * g.size.height - 3)
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if progress < 1 {
                        Rectangle().fill(OC.gold).frame(height: 3)
                            .shadow(color: OC.gold.opacity(0.8), radius: 6)
                            .offset(y: g.size.height * progress)
                    }
                }
            }
            .overlay(alignment: .topTrailing) {
                if let key {
                    Text("Key \(key)").font(.outfit(11, .bold)).foregroundStyle(OC.inkFixed)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(RoundedRectangle(cornerRadius: 8).fill(OC.gold))
                        .padding(8)
                        .transition(.opacity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .frame(maxWidth: 300)
            .animation(.snappy, value: regions)
            .accessibilityElement()
            .accessibilityLabel(regions.isEmpty ? "Photo of the chord sheet, \(Int(progress * 100)) percent read"
                                                : "Photo of the chord sheet with \(regions.count) sections outlined")
    }

    @ViewBuilder private var page: some View {
        if let image {
            Image(uiImage: image).resizable().aspectRatio(contentMode: .fit).opacity(0.85)
        } else {
            ImportSheetArt().aspectRatio(300 / 424, contentMode: .fit).opacity(0.55)
        }
    }
}

private struct ImportStepRow: View {
    enum State { case done, active, pending, todo }
    let text: String
    let state: State
    @SwiftUI.State private var spin = false

    var body: some View {
        HStack(spacing: 10) {
            Group {
                switch state {
                case .done:
                    Image(systemName: "checkmark").font(.system(size: 13, weight: .heavy)).foregroundStyle(ImportNight.teal)
                case .active:
                    Circle().trim(from: 0, to: 0.75)
                        .stroke(OC.gold, style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
                        .frame(width: 15, height: 15)
                        .rotationEffect(.degrees(spin ? 360 : 0))
                        .animation(.linear(duration: 0.9).repeatForever(autoreverses: false), value: spin)
                        .onAppear { spin = true }
                case .pending:
                    Circle().stroke(OC.faint, lineWidth: 2).frame(width: 14, height: 14)
                case .todo:
                    Image(systemName: "pencil").font(.system(size: 13, weight: .heavy)).foregroundStyle(ImportNight.goldText)
                }
            }
            .frame(width: 18, height: 18)
            Text(text).font(.outfit(14)).foregroundStyle(state == .pending ? OC.faint : OC.paper)
        }
    }
}

/// 36b · Scan failed.
private struct ImportFailedView: View {
    let photo: UIImage?
    let onClose: () -> Void
    let onRetake: () -> Void
    let onTypeIn: () -> Void
    let onChooseAnother: () -> Void

    private let tips = [
        ("Too blurry", "Hold still for a second, or rest your phone on the table."),
        ("Too dark", "Move near a window or turn on the flash."),
        ("Page is cut off", "Fit the whole page inside the gold frame.")
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
                }
                .foregroundStyle(OC.ink).accessibilityLabel("Close")
                Spacer()
                Text("Scan sheet").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    blurredPhoto
                    VStack(alignment: .leading, spacing: 6) {
                        Eyebrow(text: "Scan failed")
                        Text("We couldn’t read this sheet").font(.outfit(26, .bold)).tracking(-0.6).foregroundStyle(OC.ink)
                        Text("We didn’t find any chords. Try one of these and scan again.").font(.outfit(15)).foregroundStyle(OC.muted)
                    }
                    ForEach(tips, id: \.0) { tip in
                        HStack(alignment: .top, spacing: 12) {
                            Text("!").font(.outfit(14, .heavy)).foregroundStyle(OC.red)
                                .frame(width: 26, height: 26).background(Circle().fill(OC.redTint))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(tip.0).font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                                Text(tip.1).font(.outfit(13)).foregroundStyle(OC.muted)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .glass(radius: 16)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 12)
            }

            VStack(spacing: 8) {
                Button(action: onRetake) { Label("Retake photo", systemImage: "camera") }.buttonStyle(.gold)
                HStack(spacing: 10) {
                    Button("Type it in", action: onTypeIn).buttonStyle(GlassButtonStyle(height: 48))
                    Button("Choose another", action: onChooseAnother).buttonStyle(GlassButtonStyle(height: 48))
                }
            }
            .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 16)
        }
    }

    private var blurredPhoto: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0x3A332A), Color(hex: 0x1E1B17)], startPoint: .topLeading, endPoint: .bottomTrailing)
            if let photo {
                Image(uiImage: photo).resizable().scaledToFill().blur(radius: 2.5).opacity(0.8)
            } else {
                drawnSheet
            }
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(OC.gold.opacity(0.7), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
                .padding(22)
        }
        .frame(height: 190)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(alignment: .topLeading) {
            Text("0 CHORDS FOUND").font(.outfit(12, .heavy)).tracking(1).foregroundStyle(.white)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: 0xB91C1C, alpha: 0.92)))
                .padding(12)
        }
        .accessibilityElement()
        .accessibilityLabel("Your photo, no chords found")
    }

    private var drawnSheet: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach([0.7, 0.9, 0.55, 0.85, 0.6, 0.8], id: \.self) { w in
                RoundedRectangle(cornerRadius: 3).fill(Color(hex: 0x28221A, alpha: 0.45))
                    .frame(width: 138 * w, height: 5)
            }
        }
        .padding(16)
        .frame(width: 170, height: 150, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 6).fill(LinearGradient(colors: [Color(hex: 0xD8D0BF), Color(hex: 0xBFB6A3)], startPoint: .top, endPoint: .bottom)))
        .rotationEffect(.degrees(-7))
        .blur(radius: 2.5)
    }
}

// MARK: - 2b / 2c · Zawgyi → Unicode

struct ZawgyiView: View {
    let result: ScanResult
    @Environment(AppState.self) private var app
    @State private var view: ZawgyiMode = .unicode

    private enum ZawgyiMode: Hashable { case unicode, scanned }

    // The scanned (Zawgyi) text is shown in a Unicode font, so it looks broken — which is the point.
    private var lines: [(key: String, unicode: String, zawgyi: String)] {
        (result.zawgyi?.preview ?? []).map { ($0.label, $0.converted, $0.scanned) }
    }

    private var wordsConverted: Int { result.zawgyi?.wordsConverted ?? 0 }

    /// The review step, with the song as scanned (unconverted) or converted.
    private func review(converted: Bool) -> Route {
        guard !converted, let scanned = result.zawgyi?.scannedSong else { return .review(result) }
        var asScanned = result
        asScanned.song = scanned
        asScanned.needsLyrics = []
        asScanned.zawgyi = nil
        return .review(asScanned)
    }

    var body: some View {
        @Bindable var app = app
        let uni = view == .unicode
        VStack(spacing: 0) {
            TopBar(title: "Myanmar text", backLabel: "Back to scan", onBack: { app.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Eyebrow(text: "Zawgyi detected")
                        Text("We fixed the Myanmar text").font(.outfit(28, .bold)).tracking(-0.7).foregroundStyle(OC.ink)
                        Text("This sheet was typed in Zawgyi, an older Myanmar font. We converted it to Unicode so the lyrics show, search and share correctly on every phone.")
                            .font(.outfit(15)).foregroundStyle(OC.muted).lineSpacing(3)
                    }

                    Segmented(options: [ZawgyiMode.unicode, .scanned], selection: $view) { $0 == .unicode ? "Unicode (fixed)" : "As scanned" }

                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text(uni ? "UNICODE PREVIEW" : "AS SCANNED").font(.outfit(12, .bold)).tracking(1)
                                .foregroundStyle(uni ? OC.tealText : OC.red)
                            Spacer()
                            StatusPill(text: uni ? "Reads correctly" : "Breaks on most phones", tone: uni ? .teal : .red)
                        }
                        ForEach(lines, id: \.key) { l in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(l.key).font(.outfit(11, .bold)).foregroundStyle(OC.muted)
                                Text(uni ? l.unicode : l.zawgyi).font(.myanmar(18)).lineSpacing(8)
                                    .foregroundStyle(uni ? OC.ink : OC.red)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .glass()
                    .overlay {
                        if !uni { RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(OC.red.opacity(0.35)) }
                    }
                    .animation(.snappy(duration: 0.2), value: view)

                    HStack(spacing: 8) {
                        stat("\(wordsConverted)", wordsConverted == 1 ? "word converted" : "words converted", OC.ink)
                        stat("\(Int(((result.zawgyi?.clean ?? 1) * 100).rounded()))%", "converted cleanly", OC.teal)
                        stat("\(result.lowConfidence.count + result.needsLyrics.count)", "to check", OC.goldText)
                    }

                    Toggle(isOn: $app.alwaysConvertZawgyi) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Always convert automatically").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                            Text("Skip this step next time").font(.outfit(12)).foregroundStyle(OC.muted)
                        }
                    }
                    .toggleStyle(.oc)
                    .accessibilityLabel("Always convert automatically")
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .glass(radius: 16)
                }
                .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            }
            VStack(spacing: 6) {
                Button("Continue with Unicode") { app.push(review(converted: true)) }.buttonStyle(.gold)
                Button("Keep as scanned") { app.push(review(converted: false)) }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.muted).frame(maxWidth: .infinity, minHeight: 44)
            }
            .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 8)
        }
        .ocPage()
    }

    private func stat(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.outfit(20, .heavy)).foregroundStyle(color)
            Text(label).font(.outfit(12)).foregroundStyle(OC.muted).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .glass(radius: 16)
    }
}

// MARK: - 3 · Review import

struct ReviewView: View {
    let result: ScanResult
    @Environment(AppState.self) private var app

    @State private var title: String
    @State private var artist: String
    @State private var key: String
    @State private var sections: [SongSection]
    @State private var checks: [String]
    @State private var confirmed: Set<String> = []
    /// Lines whose lyrics the scan couldn't read, still to be typed in.
    @State private var needs: [ScanLineRef]
    @State private var editing: ScanLineRef?
    @State private var isPublic = true
    @State private var changing: String?
    @State private var changeText = ""
    @State private var flashSection: String?

    init(result: ScanResult) {
        self.result = result
        _title = State(initialValue: result.song.title)
        _artist = State(initialValue: result.song.artist)
        _key = State(initialValue: result.song.key)
        _sections = State(initialValue: result.song.sections)
        _checks = State(initialValue: result.lowConfidence)
        _needs = State(initialValue: result.needsLyrics)
    }

    private var openChecks: Int {
        checks.filter { !confirmed.contains($0) }.count + (needs.isEmpty ? 0 : 1)
    }

    private var unresolvedChords: Set<String> { Set(checks.filter { !confirmed.contains($0) }) }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Review import", backLabel: "Back to scan", onBack: { app.pop() })
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        header
                        arrangement
                        checksBlock(proxy)
                        visibility
                        sectionList
                    }
                    .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 20)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            VStack(spacing: 8) {
                Button(isPublic ? "Save & share to O Chords" : "Save to my library", action: save)
                    .buttonStyle(.gold)
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                Text(isPublic ? "Shared under your name. You can make it private any time." : "Only you will see this song.")
                    .font(.outfit(12)).foregroundStyle(OC.muted)
            }
            .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 8)
        }
        .ocPage()
        .alert("Change chord", isPresented: Binding(get: { changing != nil }, set: { if !$0 { changing = nil } })) {
            TextField("Chord", text: $changeText).textInputAutocapitalization(.never).autocorrectionDisabled()
            Button("Cancel", role: .cancel) { changing = nil }
            Button("Apply") { applyChange() }
        } message: {
            Text("Replace \(changing ?? "") everywhere in this song.")
        }
        .sheet(item: $editing) { ref in
            if let line = line(ref) {
                LyricLineEditor(
                    sectionLabel: sections.first { $0.id == ref.sectionID }?.label.capitalized ?? "",
                    line: line,
                    missingLyrics: needs.contains(ref),
                    onSave: { save($0, at: ref) },
                    onDelete: { deleteLine(ref) }
                )
                .presentationDetents([.medium, .large])
            }
        }
    }

    // MARK: Pieces

    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            Group {
                if let img = result.thumbnail.flatMap(UIImage.init(data:)) {
                    Image(uiImage: img).resizable().scaledToFill()
                } else {
                    ImportSheetArt()
                }
            }
                .frame(width: 84, height: 119)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(OC.border))
                .accessibilityLabel("Original sheet")
            VStack(spacing: 8) {
                field("Title", text: $title, font: .myanmar(16, .semibold), color: OC.ink)
                HStack(spacing: 8) {
                    field("Artist", text: $artist, font: .myanmar(15), color: OC.ink)
                    field("Key", text: $key, font: .mono(15), color: OC.goldText)
                }
            }
        }
    }

    private func field(_ label: String, text: Binding<String>, font: Font, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.outfit(12, .semibold)).foregroundStyle(OC.muted)
            TextField(label, text: text, prompt: Text("[\(label)]"))
                .font(font).foregroundStyle(color)
                .padding(.horizontal, 10)
                .frame(height: 38)
                .glass(radius: 14)
        }
    }

    private var arrangementChips: [(String, SectionKind)] {
        func short(_ label: String, _ kind: SectionKind, note: String?) -> String {
            let up = label.uppercased()
            var out: String
            if up.hasPrefix("VERSE") { out = "V" + up.dropFirst(5).trimmingCharacters(in: .whitespaces) }
            else { out = label.capitalized.replacingOccurrences(of: "Fade Out", with: "fade") }
            if let note, note.contains("×2") { out += " ×2" }
            return out
        }
        var out: [(String, SectionKind)] = []
        for s in sections {
            if s.kind == .flow {
                out += s.flow.map { (short($0.label, $0.kind, note: nil), $0.kind) }
            } else {
                out.append((short(s.label, s.kind, note: s.note), s.kind))
            }
        }
        return out
    }

    private var arrangement: some View {
        VStack(alignment: .leading, spacing: 10) {
            label("ARRANGEMENT")
            WrapFlow(spacing: 6, lineSpacing: 6) {
                ForEach(Array(arrangementChips.enumerated()), id: \.offset) { _, chip in
                    let (bg, fg) = SectionTag.colors(chip.1)
                    Text(chip.0).font(.outfit(13, .bold)).foregroundStyle(fg)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Capsule().fill(bg))
                }
            }
        }
    }

    @ViewBuilder private func checksBlock(_ proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            label(openChecks == 0 ? "ALL CHECKED" : "CHECK \(openChecks) THING\(openChecks == 1 ? "" : "S")")
            ForEach(checks, id: \.self) { chord in
                if confirmed.contains(chord) {
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(OC.teal)
                        Text("\(chord) confirmed").font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
                        Spacer()
                        Button("Undo") { withAnimation(.snappy) { _ = confirmed.remove(chord) } }
                            .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
                    }
                    .padding(.horizontal, 14)
                    .glass()
                } else {
                    chordCheck(chord)
                }
            }
            if let first = needs.first {
                HStack(spacing: 12) {
                    Image(systemName: "character.cursor.ibeam")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x92400E))
                        .frame(width: 36, height: 36)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0xFEF3C7)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Type \(needs.count) lyric line\(needs.count == 1 ? "" : "s")").font(.outfit(14, .bold)).foregroundStyle(OC.ink)
                        Text(result.readOnServer
                             ? "Chords are in place. These lines weren’t clear enough to read."
                             : "Chords are in place. Myanmar lyrics couldn’t be read this time — type them in.")
                            .font(.outfit(13)).foregroundStyle(OC.muted)
                    }
                    Spacer(minLength: 4)
                    Button("Type") {
                        withAnimation { proxy.scrollTo(first.sectionID, anchor: .top) }
                        flashSection = first.sectionID
                        editing = first
                        Task { try? await Task.sleep(for: .seconds(1.4)); withAnimation { flashSection = nil } }
                    }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.goldText)
                    .frame(minWidth: 44, minHeight: 44)
                }
                .padding(14)
                .glass()
            }
        }
    }

    private func chordCheck(_ chord: String) -> some View {
        let parts = chord.split(separator: "/", maxSplits: 1).map(String.init)
        let places = occurrences(of: chord)
        let note = result.chordNotes[chord] ?? "The scan wasn’t sure about this chord"
        let superscript = note.hasPrefix("Superscript") && parts.count > 1
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                HStack(alignment: .top, spacing: 0) {
                    if superscript {
                        Text(parts[0]).font(.mono(15))
                        Text(parts[1]).font(.mono(10)).baselineOffset(6)
                        Text(" → \(chord)").font(.mono(15))
                    } else {
                        Text(chord).font(.mono(15))
                    }
                }
                .foregroundStyle(OC.goldText)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Capsule().fill(OC.goldTint))
                Text("\(note) (\(places) place\(places == 1 ? "" : "s")).")
                    .font(.outfit(14)).foregroundStyle(OC.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 8) {
                Button("Looks right") { withAnimation(.snappy) { _ = confirmed.insert(chord) } }
                    .buttonStyle(InkButtonStyle(height: 44)).font(.outfit(14, .bold))
                Button("Change") { changeText = chord; changing = chord }
                    .buttonStyle(GlassButtonStyle(height: 44))
            }
        }
        .padding(14)
        .glass()
    }

    private var sectionList: some View {
        VStack(alignment: .leading, spacing: 10) {
            label("SECTIONS")
            ForEach(sections.filter { $0.kind != .flow }) { s in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        SectionTag(label: s.label, kind: s.kind)
                        if let note = s.note { Text(note).font(.outfit(12)).foregroundStyle(OC.muted) }
                    }
                    if !s.bars.isEmpty {
                        WrapFlow(spacing: 6) {
                            ForEach(Array(s.bars.enumerated()), id: \.offset) { _, c in chordChip(c, big: true) }
                        }
                    }
                    ForEach(Array(s.lines.enumerated()), id: \.offset) { i, line in
                        let ref = ScanLineRef(sectionID: s.id, line: i)
                        let missing = needs.contains(ref)
                        Button { editing = ref } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                WrapFlow(spacing: 5, lineSpacing: 4) {
                                    ForEach(Array(ChordLine.parse(line).enumerated()), id: \.offset) { _, seg in
                                        VStack(alignment: .leading, spacing: 0) {
                                            if let c = seg.chord { chordChip(c, big: false) } else { Color.clear.frame(width: 1, height: 20) }
                                            Text(seg.text.isEmpty ? " " : seg.text).font(.myanmar(15)).foregroundStyle(OC.ink)
                                        }
                                    }
                                }
                                if missing {
                                    Label("Tap to type the lyrics", systemImage: "pencil")
                                        .font(.outfit(12, .semibold)).foregroundStyle(OC.goldText)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(missing ? 8 : 0)
                            .background {
                                if missing {
                                    RoundedRectangle(cornerRadius: 10)
                                        .strokeBorder(OC.gold.opacity(0.7), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(missing ? "\(s.label.capitalized) line \(i + 1), lyrics missing" : "\(s.label.capitalized) line \(i + 1)")
                        .accessibilityHint("Edit this line")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .glass()
                .overlay {
                    if flashSection == s.id {
                        RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(OC.gold, lineWidth: 2)
                    }
                }
                .id(s.id)
            }
        }
    }

    @ViewBuilder private func chordChip(_ c: String, big: Bool) -> some View {
        let low = unresolvedChords.contains(c)
        Text(c)
            .font(.mono(big ? 15 : 13))
            .foregroundStyle(low ? OC.red : OC.goldText)
            .padding(.horizontal, low || big ? 6 : 0)
            .frame(minHeight: 20)
            .padding(.vertical, big ? 6 : 0)
            .background {
                if low {
                    RoundedRectangle(cornerRadius: 6).fill(OC.redTint)
                        .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(OC.red.opacity(0.6), style: StrokeStyle(lineWidth: 1, dash: [3, 2])))
                } else if big {
                    RoundedRectangle(cornerRadius: 8).fill(OC.goldTint)
                }
            }
            .accessibilityLabel(low ? "\(c), low confidence" : c)
    }

    private var visibility: some View {
        VStack(alignment: .leading, spacing: 10) {
            label("WHO CAN SEE IT")
            HStack(spacing: 8) {
                visibilityOption(on: isPublic, icon: "globe", title: "Public", sub: "Anyone can find it in Explore", onBorder: OC.gold, onFill: OC.goldTint) { isPublic = true }
                visibilityOption(on: !isPublic, icon: "lock", title: "Only me", sub: "Stays in your library", onBorder: OC.ink, onFill: OC.line) { isPublic = false }
            }
        }
    }

    private func visibilityOption(on: Bool, icon: String, title: String, sub: String, onBorder: Color, onFill: Color, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy(duration: 0.2)) { action() } } label: {
            VStack(alignment: .leading, spacing: 2) {
                Label(title, systemImage: icon).font(.outfit(14, .heavy)).foregroundStyle(OC.ink)
                Text(sub).font(.outfit(12)).foregroundStyle(OC.muted).lineLimit(2).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .padding(.horizontal, 12)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(on ? onFill : OC.glass))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(on ? onBorder : OC.border, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func label(_ t: String) -> some View {
        Text(t).font(.outfit(13, .bold)).tracking(0.6).foregroundStyle(OC.muted)
    }

    // MARK: Actions

    private func occurrences(of chord: String) -> Int {
        sections.reduce(0) { $0 + $1.bars.filter { $0 == chord }.count + $1.lines.flatMap(ChordLine.chords(in:)).filter { $0 == chord }.count }
    }

    private func applyChange() {
        guard let old = changing else { return }
        let new = changeText.trimmingCharacters(in: .whitespaces)
        changing = nil
        guard !new.isEmpty, new != old else { return }
        sections = sections.map { s in
            var s = s
            s.bars = s.bars.map { $0 == old ? new : $0 }
            s.lines = s.lines.map { $0.replacingOccurrences(of: "[\(old)]", with: "[\(new)]") }
            return s
        }
        withAnimation(.snappy) {
            checks = checks.map { $0 == old ? new : $0 }
            confirmed.insert(new)
        }
    }

    private func line(_ ref: ScanLineRef) -> String? {
        guard let s = sections.first(where: { $0.id == ref.sectionID }), s.lines.indices.contains(ref.line) else { return nil }
        return s.lines[ref.line]
    }

    private func save(_ text: String, at ref: ScanLineRef) {
        guard let si = sections.firstIndex(where: { $0.id == ref.sectionID }), sections[si].lines.indices.contains(ref.line) else { return }
        sections[si].lines[ref.line] = text
        if !ChordLine.parse(text).allSatisfy({ $0.text.isEmpty }) { needs.removeAll { $0 == ref } }
        editing = nil
    }

    private func deleteLine(_ ref: ScanLineRef) {
        guard let si = sections.firstIndex(where: { $0.id == ref.sectionID }), sections[si].lines.indices.contains(ref.line) else { return }
        withAnimation(.snappy) {
            sections[si].lines.remove(at: ref.line)
            // Later lines in this section move up one.
            needs = needs.compactMap { n in
                guard n.sectionID == ref.sectionID else { return n }
                if n.line == ref.line { return nil }
                return n.line > ref.line ? ScanLineRef(sectionID: n.sectionID, line: n.line - 1) : n
            }
        }
        editing = nil
    }

    private func save() {
        var song = result.song
        // Typed on a Zawgyi keyboard? Store Unicode, like everything else.
        song.title = Zawgyi.normalised(title.trimmingCharacters(in: .whitespaces))
        song.artist = Zawgyi.normalised(artist.trimmingCharacters(in: .whitespaces))
        song.key = key.trimmingCharacters(in: .whitespaces).isEmpty ? "C" : key.trimmingCharacters(in: .whitespaces)
        // Drop lines left completely empty and sections with nothing in them.
        song.sections = sections.compactMap { s in
            var s = s
            s.lines = s.lines.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            return s.lines.isEmpty && s.bars.isEmpty && s.flow.isEmpty ? nil : s
        }
        song.isMyanmar = result.song.isMyanmar || ChordSheetParser.isMyanmar(song.title + song.sections.flatMap(\.lines).joined())
        song.isPublic = isPublic
        song.addedAt = .now
        app.addToLibrary(song)
        if let reading = result.reading { app.keepScan(reading, scanned: result.song, for: song.id) }
        app.popToRoot()
        app.push(.song(song))
    }
}

// MARK: - 40a–d · Listen

private enum ListenPhase { case ready, listening, live, done }

struct ListenView: View {
    @Environment(AppState.self) private var app
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var phase: ListenPhase = .ready
    @State private var listener = SongListener()
    @State private var paused = false
    @State private var pendingStart = false
    @State private var micOff = false
    @State private var summary: ChordEstimator.Summary?
    /// A sheet of the matched song shared by the community, if there is one.
    @State private var community: CommunitySheet?

    /// How long "Identifying the song" shows before the chords take over (s), if ShazamKit hasn't answered.
    private static let identifyFor = 12.0

    private var live: SongListener.Live { listener.live }
    private var clock: String { let s = Int(live.seconds); return "\(s / 60):" + String(format: "%02d", s % 60) }
    private var found: (title: String, artist: String)? {
        if case .found(let title, let artist) = listener.match { return (title, artist) }
        return nil
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            switch phase {
            case .ready: ready.transition(.opacity)
            case .listening: listening.transition(.opacity)
            case .live: liveView.transition(.opacity)
            case .done: result.transition(.opacity)
            }
        }
        .foregroundStyle(ImportNight.text)
        .background(NightBackground())
        .toolbar(.hidden, for: .navigationBar)
        .animation(.easeInOut(duration: 0.3), value: phase)
        .onChange(of: app.modal) { _, m in
            if pendingStart && m == nil { pendingStart = false; begin() }
        }
        .onChange(of: listener.match) { _, match in
            if phase == .listening, match != .searching { withAnimation { phase = .live } }
            if case .found(let title, _) = match { Task { await lookUpCommunity(title) } }
        }
        .onChange(of: live.seconds) { _, s in
            if phase == .listening, s >= Self.identifyFor, live.chord != nil { withAnimation { phase = .live } }
        }
        .onChange(of: scenePhase) { _, p in
            // The microphone stops in the background: pause, and let the player carry on.
            if p != .active, listener.isRunning, phase == .listening || phase == .live { listener.pause(); paused = true }
        }
        .onDisappear { listener.pause() }
        #if DEBUG
        // -demoListenAuto <seconds> starts listening and stops after that long (simulator checks against music
        // played through the Mac's speakers).
        .task {
            let seconds = UserDefaults.standard.double(forKey: "demoListenAuto")
            guard seconds > 0 else { return }
            ImportSession.askedMic = true
            begin()
            try? await Task.sleep(for: .seconds(seconds))
            stop()
        }
        #endif
    }

    private var topBar: some View {
        HStack {
            Button { listener.pause(); app.pop() } label: {
                Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
            }
            .accessibilityLabel("Close")
            Spacer()
            Text(phase == .live ? "Live chords" : phase == .done ? "Estimated chords" : "Listen").font(.outfit(16, .bold))
            Spacer()
            ZStack {
                if phase == .live && !paused {
                    HStack(spacing: 5) {
                        Circle().fill(Color(hex: 0xEF4444)).frame(width: 8, height: 8)
                            .background(Circle().fill(Color(hex: 0xEF4444, alpha: 0.25)).frame(width: 16, height: 16))
                        Text("LIVE").font(.outfit(12, .heavy)).tracking(1).foregroundStyle(ImportNight.red)
                    }
                    .fixedSize()
                }
            }
            .frame(width: 44)
        }
        .padding(.horizontal, 10)
    }

    // MARK: Ready

    private var ready: some View {
        VStack(spacing: 0) {
            Spacer()
            Button(action: start) {
                Image(systemName: "mic").font(.system(size: 56, weight: .medium)).foregroundStyle(OC.inkFixed)
                    .frame(width: 176, height: 176)
                    .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                    .background(Circle().fill(OC.gold.opacity(0.10)).padding(-18))
                    .background(Circle().fill(OC.gold.opacity(0.05)).padding(-40))
                    .shadow(color: Color(hex: 0xFFAA00).opacity(0.45), radius: 25, y: 20)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Start listening")
            VStack(spacing: 8) {
                Text("Tap to listen").font(.outfit(30, .bold)).tracking(-0.8)
                Text("Play a song near your phone — from a speaker, the radio or a live band. We’ll name it and estimate the chords as it plays.")
                    .font(.outfit(15)).foregroundStyle(ImportNight.muted).multilineTextAlignment(.center).lineSpacing(3)
            }
            .padding(.top, 64)
            if micOff {
                VStack(spacing: 6) {
                    Text("The microphone is off for O Chords").font(.outfit(14, .bold)).foregroundStyle(ImportNight.red)
                    Button("Allow it in Settings ›") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                        .font(.outfit(14, .bold)).foregroundStyle(ImportNight.goldText).frame(minHeight: 44)
                }
                .padding(.top, 24)
            } else {
                WrapFlow(spacing: 8) {
                    ForEach(["Names the song", "Chords live", "Key & tempo"], id: \.self) { f in
                        HStack(spacing: 6) {
                            Text("✓").foregroundStyle(ImportNight.teal)
                            Text(f)
                        }
                        .font(.outfit(13, .semibold))
                        .padding(.horizontal, 12).frame(height: 34)
                        .background(Capsule().fill(OC.nightGlass)).overlay(Capsule().strokeBorder(OC.nightBorder))
                    }
                }
                .fixedSize()
                .padding(.top, 28)
            }
            Spacer()
            Text("Audio is analysed on your phone and never recorded. Only an audio fingerprint is sent to identify the song.")
                .font(.outfit(12)).foregroundStyle(ImportNight.muted).multilineTextAlignment(.center)
                .padding(.horizontal, 32).padding(.bottom, 24)
        }
        .padding(.horizontal, 28)
    }

    // MARK: Identifying

    private var listening: some View {
        VStack(spacing: 30) {
            Spacer()
            ZStack {
                Circle().strokeBorder(OC.gold.opacity(0.14)).frame(width: 250, height: 250)
                Circle().strokeBorder(OC.gold.opacity(0.24)).frame(width: 200, height: 200)
                Circle().fill(OC.gold.opacity(0.12)).overlay(Circle().strokeBorder(OC.gold.opacity(0.45)))
                    .frame(width: 150, height: 150).shadow(color: OC.gold.opacity(0.35), radius: 30)
                HStack(spacing: 4) {
                    ForEach(Array([14.0, 26, 40, 30, 52, 34, 44, 22, 16].enumerated()), id: \.offset) { i, h in
                        Capsule().fill(OC.gold)
                            .frame(width: 5, height: max(8, h * (0.55 + 0.45 * abs(sin((live.seconds + 1) * 1.3 + Double(i))))))
                    }
                }
                .animation(.easeInOut(duration: 0.25), value: live.seconds)
            }
            .accessibilityHidden(true)
            VStack(spacing: 8) {
                Text("Listening…").font(.outfit(28, .bold)).tracking(-0.6)
                Text("Identifying the song · \(clock)").font(.outfit(15)).foregroundStyle(ImportNight.muted).monospacedDigit()
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("ALREADY HEARING").font(.outfit(12, .bold)).tracking(1.2).foregroundStyle(ImportNight.muted)
                HStack(spacing: 8) {
                    ForEach(Array(live.heard.suffix(3).enumerated()), id: \.offset) { _, segment in
                        Text(segment.chord).font(.mono(17)).foregroundStyle(ImportNight.goldText)
                            .padding(.horizontal, 14).frame(height: 40)
                            .background(RoundedRectangle(cornerRadius: 10).fill(OC.gold.opacity(0.16)))
                    }
                    Text("?").font(.mono(17)).foregroundStyle(ImportNight.muted)
                        .padding(.horizontal, 12).frame(height: 40)
                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.white.opacity(0.25), style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                }
                .animation(.snappy(duration: 0.25), value: live.heard)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .nightGlass(radius: 20)
            Spacer()
            VStack(spacing: 6) {
                Button("Stop", action: stop).buttonStyle(ListenGhostButton(height: 54))
                Text("Tip: 1–2 m from the speaker works best").font(.outfit(12)).foregroundStyle(ImportNight.muted)
            }
            .padding(.bottom, 16)
        }
        .padding(.horizontal, 20)
    }

    // MARK: Live

    private var liveView: some View {
        let color = ImportNight.confidenceColor(live.confidence)
        return VStack(spacing: 14) {
            matchedCard
            HStack(spacing: 8) {
                estimateTile("KEY", live.key.isEmpty ? "–" : live.key,
                             live.key.isEmpty ? "listening…" : "\(live.key.hasSuffix("m") ? "minor" : "major") · \(live.keyConfidence)%")
                estimateTile("TEMPO", live.bpm.map(String.init) ?? "–", live.bpm == nil ? "finding the beat" : "BPM")
                estimateTile("CAPO", live.key.isEmpty ? "–" : "\(live.capo)", live.capo == 0 ? "none needed" : "for open shapes")
            }
            ZStack {
                RoundedRectangle(cornerRadius: 24).fill(OC.gold.opacity(0.08))
                    .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(OC.gold.opacity(0.28)))
                VStack(spacing: 10) {
                    Text(live.chord ?? "–").font(.mono(96)).foregroundStyle(live.chord == nil ? ImportNight.muted : OC.gold)
                        .shadow(color: OC.gold.opacity(0.35), radius: 20)
                        .minimumScaleFactor(0.5).lineLimit(1)
                        .contentTransition(.numericText())
                        .id(live.heard.count)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                    VStack(spacing: 6) {
                        if live.chord == nil {
                            Text(paused ? "Paused" : "Waiting for music…").font(.outfit(13, .semibold)).foregroundStyle(ImportNight.muted)
                        } else {
                            GeometryReader { g in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(Color.white.opacity(0.1))
                                    Capsule().fill(color).frame(width: g.size.width * Double(live.confidence) / 100)
                                }
                            }
                            .frame(width: 180, height: 6)
                            Text("\(live.confidence)% sure" + (live.confidence < 70 ? " · check later" : ""))
                                .font(.outfit(13, .semibold)).foregroundStyle(color)
                        }
                    }
                }
                .animation(.snappy(duration: 0.25), value: live.heard.count)
            }
            .overlay(alignment: .topLeading) {
                Text("NOW").font(.outfit(12, .heavy)).tracking(1.4).foregroundStyle(ImportNight.goldText).padding(.leading, 16).padding(.top, 14)
            }
            .overlay(alignment: .topTrailing) {
                if let next = live.next {
                    (Text("Next likely ").foregroundStyle(ImportNight.muted) + Text(next).font(.mono(13)).foregroundStyle(ImportNight.text))
                        .font(.outfit(13)).padding(.trailing, 16).padding(.top, 12)
                }
            }
            .frame(maxHeight: .infinity)
            .accessibilityElement(children: .combine)

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Text("HEARD SO FAR").font(.outfit(12, .bold)).tracking(1.2)
                    Spacer()
                    Text("dashed = check this one").font(.outfit(12))
                }
                .foregroundStyle(ImportNight.muted)
                HStack(spacing: 6) {
                    let shown = Array(live.heard.suffix(6))
                    ForEach(Array(shown.enumerated()), id: \.offset) { i, segment in
                        ListenChordCell(chord: segment.chord, confidence: segment.confidence, current: i == shown.count - 1, showPercent: false)
                    }
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 44)
                .animation(.snappy(duration: 0.25), value: live.heard)
            }
            HStack(spacing: 10) {
                Button(action: togglePause) {
                    Image(systemName: paused ? "play.fill" : "pause.fill").font(.system(size: 18, weight: .semibold))
                }
                .buttonStyle(ListenGhostButton(height: 54, width: 54))
                .accessibilityLabel(paused ? "Resume" : "Pause")
                Button("Stop & review chords", action: stop).buttonStyle(.gold)
            }
            .padding(.bottom, 16)
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
    }

    private var matchedCard: some View {
        HStack(spacing: 12) {
            Text(live.key.isEmpty ? "?" : live.key).font(.mono(18)).foregroundStyle(OC.inkFixed)
                .minimumScaleFactor(0.6).lineLimit(1)
                .frame(width: 48, height: 48)
                .background(RoundedRectangle(cornerRadius: 14).fill(LinearGradient(colors: [OC.goldTop, Color(hex: 0xE08A00)], startPoint: .topLeading, endPoint: .bottomTrailing)))
            VStack(alignment: .leading, spacing: 1) {
                switch listener.match {
                case .found(let title, let artist):
                    Text(title).font(Self.font(for: title, 17, .semibold)).lineLimit(1)
                    (Text(artist).font(Self.font(for: artist, 13)) + Text(" · \(clock)").font(.outfit(13)))
                        .foregroundStyle(ImportNight.muted).monospacedDigit().lineLimit(1)
                case .searching:
                    Text("Naming the song…").font(.outfit(17, .semibold))
                    Text("Listening · \(clock)").font(.outfit(13)).foregroundStyle(ImportNight.muted).monospacedDigit()
                case .notFound:
                    Text("Song not recognised").font(.outfit(17, .semibold))
                    Text("You can name it when you save · \(clock)").font(.outfit(13)).foregroundStyle(ImportNight.muted).monospacedDigit()
                }
            }
            Spacer(minLength: 4)
            if found != nil {
                Text("Matched").font(.outfit(12, .bold)).foregroundStyle(ImportNight.teal)
                    .padding(.horizontal, 9).padding(.vertical, 4).background(Capsule().fill(ImportNight.tealBg))
            } else if listener.match == .searching {
                ProgressView().tint(OC.gold)
            }
        }
        .padding(12)
        .nightGlass(radius: 20)
    }

    private func estimateTile(_ k: String, _ v: String, _ s: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).font(.outfit(11, .bold)).tracking(1).foregroundStyle(ImportNight.muted)
            Text(v).font(.outfit(20, .heavy)).lineLimit(1).minimumScaleFactor(0.7)
            Text(s).font(.outfit(11)).foregroundStyle(ImportNight.muted).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .nightGlass(radius: 16)
    }

    // MARK: Result

    private var bars: [ChordEstimator.Bar] { summary?.bars ?? [] }

    private var overallConfidence: Int {
        bars.isEmpty ? 0 : bars.reduce(0) { $0 + $1.confidence } / bars.count
    }

    private var lowChords: [String] {
        var seen = Set<String>(), out: [String] = []
        for b in bars where b.confidence < 70 && seen.insert(b.chord).inserted { out.append(b.chord) }
        return out
    }

    /// The parts heard (A, B, C …), coloured so repeats are easy to spot.
    private var resultSections: [(name: String, fg: Color, bg: Color, range: Range<Int>)] {
        let colours: [(Color, Color)] = [(ImportNight.teal, ImportNight.tealBg), (OC.inkFixed, OC.gold), (ImportNight.blue, ImportNight.blueBg)]
        let parts = ChordEstimator.parts(bars.map(\.chord))
        if parts.count == 1 { return [("CHORDS", ImportNight.teal, ImportNight.tealBg, parts[0].range)] }
        return parts.map { part in
            let c = colours[Int(part.letter.asciiValue! - 65) % colours.count]
            return ("PART \(part.letter)", c.0, c.1, part.range)
        }
    }

    private var keyLine: String {
        guard let s = summary, !s.key.isEmpty else { return "" }
        let key = s.key.hasSuffix("m") ? "\(s.key.dropLast()) minor" : "\(s.key) major"
        return " · Key \(key)" + (s.bpm.map { " · \($0) BPM" } ?? "") + (s.capo == 0 ? " · No capo" : " · Capo \(s.capo)")
    }

    private var result: some View {
        let lowCount = bars.filter { $0.confidence < 70 }.count
        return VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Rectangle().fill(OC.gold).frame(width: 18, height: 2)
                            Text("ESTIMATED FROM \(clock) OF AUDIO").font(.outfit(12, .semibold)).tracking(1.9).foregroundStyle(ImportNight.muted)
                        }
                        let title = found?.title ?? "Song not recognised"
                        Text(title).font(Self.font(for: title, 28, .bold))
                        (Text(found?.artist ?? "Name it when you save").font(Self.font(for: found?.artist ?? "", 14)) + Text(keyLine).font(.outfit(14)))
                            .foregroundStyle(ImportNight.muted)
                    }
                    if bars.isEmpty {
                        Text("No chords were clear enough to read. Hold the phone 1–2 m from the speaker, turn the music up a little, and listen again.")
                            .font(.outfit(14)).foregroundStyle(Color(hex: 0xD6D4CF)).lineSpacing(2)
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.05)))
                    } else {
                        HStack(spacing: 10) {
                            Text("\(overallConfidence)%").font(.outfit(26, .heavy)).foregroundStyle(ImportNight.confidenceColor(overallConfidence))
                            Group {
                                if lowCount > 0 {
                                    Text("overall confidence. ") + Text("\(lowCount) chord\(lowCount == 1 ? "" : "s")").bold().foregroundStyle(ImportNight.text) + Text(" \(lowCount == 1 ? "is" : "are") worth a quick check before you save.")
                                } else {
                                    Text("overall confidence. Every chord looks solid.")
                                }
                            }
                            .font(.outfit(13)).foregroundStyle(Color(hex: 0xD6D4CF))
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.05)))
                    }

                    ForEach(resultSections, id: \.range.lowerBound) { sec in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(sec.name).font(.outfit(12, .heavy)).tracking(1).foregroundStyle(sec.fg)
                                .padding(.horizontal, 9).padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 8).fill(sec.bg))
                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
                                ForEach(Array(sec.range), id: \.self) { i in
                                    ListenChordCell(chord: bars[i].chord, confidence: bars[i].confidence, current: false, showPercent: true)
                                }
                            }
                        }
                    }

                    if let community {
                        Button { app.push(.shared(community.song)) } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "person.2").font(.system(size: 15, weight: .semibold)).foregroundStyle(ImportNight.goldText)
                                    .frame(width: 36, height: 36).background(Circle().fill(OC.gold.opacity(0.16)))
                                VStack(alignment: .leading, spacing: 1) {
                                    (Text("A community sheet exists · ") + Text("\(community.votes) votes").bold()).font(.outfit(14))
                                    Text("Compare with our estimate").font(.outfit(14)).foregroundStyle(ImportNight.muted)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(ImportNight.muted)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .nightGlass(radius: 16)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 12)
            }
            VStack(spacing: 8) {
                if !bars.isEmpty {
                    Button(found == nil ? "Name it & save" : "Save to library", action: saveAsSong).buttonStyle(.gold)
                }
                HStack(spacing: 10) {
                    if !bars.isEmpty { Button("Fix chords", action: fixInReview).buttonStyle(ListenGhostButton(height: 48)) }
                    Button("Listen again", action: restart).buttonStyle(ListenGhostButton(height: 48))
                }
            }
            .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 12)
            .background(Color(hex: 0x0B0A09, alpha: 0.7).background(.ultraThinMaterial).environment(\.colorScheme, .dark).ignoresSafeArea(edges: .bottom))
            .overlay(alignment: .top) { Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1) }
        }
    }

    private static func isMyanmar(_ text: String) -> Bool { text.unicodeScalars.contains { (0x1000...0x109F).contains($0.value) } }

    private static func font(for text: String, _ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        isMyanmar(text) ? .myanmar(size, weight) : .outfit(size, weight)
    }

    // MARK: Actions

    private func start() {
        if !ImportSession.askedMic {
            ImportSession.askedMic = true
            pendingStart = true
            app.modal = .permission(.microphone)
            return
        }
        begin()
    }

    private func begin() {
        if MicInput.permissionUndecided {
            Task { @MainActor in
                if await MicInput.requestPermission() { begin() } else { micOff = true }
            }
            return
        }
        do {
            try listener.start()
            micOff = false
            paused = false
            phase = .listening
        } catch {
            micOff = true
        }
    }

    private func togglePause() {
        if paused {
            do { try listener.start(); paused = false } catch { micOff = true }
        } else {
            listener.pause()
            paused = true
        }
    }

    private func stop() {
        if phase == .listening && live.heard.isEmpty {
            restart()
            return
        }
        Task { @MainActor in
            summary = await listener.finish()
            phase = .done
        }
    }

    private func restart() {
        listener.pause()
        listener = SongListener()
        summary = nil
        community = nil
        paused = false
        phase = .ready
    }

    /// A community sheet of the same song, to compare with the estimate.
    private func lookUpCommunity(_ title: String) async {
        let sheets = (try? await app.backend.explore(query: title)) ?? []
        let same = title.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
        community = sheets.first { $0.song.title.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil) == same }
    }

    private func estimateSong() -> Song {
        let secs = resultSections.map { s in
            SongSection(kind: .music, label: s.name.capitalized, note: "\(s.range.count) bars", bars: s.range.map { bars[$0].chord })
        }
        let title = found?.title ?? ""
        return Song(id: UUID().uuidString, title: title, artist: found?.artist ?? "",
                    key: summary.map { $0.key.isEmpty ? "C" : $0.key } ?? "C", tempo: summary?.bpm ?? 80,
                    sections: secs, source: .listened, isMyanmar: Self.isMyanmar(title))
    }

    private func saveAsSong() {
        // Not recognised: name it in Review first.
        guard found != nil else { fixInReview(); return }
        let song = estimateSong()
        app.addToLibrary(song)
        app.push(.song(song))
    }

    private func fixInReview() {
        app.push(.review(ScanResult(song: estimateSong(), detectedZawgyi: false, lowConfidence: lowChords)))
    }
}

/// Chord cell with confidence colouring: ≥80 teal · 70–79 gold · <70 red dashed "?".
private struct ListenChordCell: View {
    let chord: String
    let confidence: Int
    let current: Bool
    let showPercent: Bool

    var body: some View {
        let low = confidence < 70
        let color = ImportNight.confidenceColor(confidence)
        let fg = current ? ImportNight.goldText : (low ? ImportNight.red : ImportNight.text)
        Group {
            if showPercent {
                VStack(alignment: .leading, spacing: 0) {
                    Text(chord + (low ? "?" : "")).font(.mono(15)).foregroundStyle(fg)
                    Spacer(minLength: 0)
                    Text("\(confidence)%").font(.outfit(10, .bold)).foregroundStyle(color)
                }
                .padding(.horizontal, 8).padding(.vertical, 5)
                .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44, alignment: .leading)
            } else {
                Text(chord + (low && !current ? "?" : "")).font(.mono(15)).foregroundStyle(fg)
                    .padding(.horizontal, 8)
                    .frame(minWidth: 48, minHeight: 44)
            }
        }
        .background(RoundedRectangle(cornerRadius: 10).fill(current ? OC.gold.opacity(0.18) : (low ? ImportNight.redBg : OC.nightGlass)))
        .overlay {
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(borderColor(low: low, color: color),
                              style: StrokeStyle(lineWidth: 1, dash: low && !current ? [4, 3] : []))
        }
        .transition(.opacity.combined(with: .move(edge: .trailing)))
        .accessibilityElement()
        .accessibilityLabel("\(chord), \(confidence) percent sure")
    }

    private func borderColor(low: Bool, color: Color) -> Color {
        if current { return OC.gold }
        if low { return ImportNight.red.opacity(0.6) }
        return showPercent ? color.opacity(0.35) : OC.nightBorder
    }
}

private struct ListenGhostButton: ButtonStyle {
    var height: CGFloat = 54
    var width: CGFloat? = nil
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(height > 50 ? 16 : 15, .bold))
            .foregroundStyle(ImportNight.text)
            .frame(maxWidth: width ?? .infinity, minHeight: height)
            .frame(width: width)
            .background(RoundedRectangle(cornerRadius: OC.rButton).fill(OC.nightGlass))
            .overlay(RoundedRectangle(cornerRadius: OC.rButton).strokeBorder(Color.white.opacity(0.14)))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

// MARK: - 49c–e · Rewarded ad

struct RewardAdView: View {
    let kind: RewardKind
    @Environment(AppState.self) private var app
    @State private var stage: Stage = .offer
    @State private var elapsed: Double = 0

    private enum Stage { case offer, playing, granted }
    private let adLength: Double = 5

    var body: some View {
        ZStack {
            switch stage {
            case .offer:
                if kind == .scan { scanOffer } else { stringsOffer }
            case .playing, .granted:
                playing
            }
        }
        .animation(.easeInOut(duration: 0.25), value: stage)
        .task(id: stage) {
            guard stage == .playing else { return }
            if AdService.isConfigured {
                // A real rewarded ad from AdMob; the reward only when they watched it through.
                if await app.ads.showRewarded() { grant() } else { close() }
                return
            }
            elapsed = 0
            while elapsed < adLength {
                try? await Task.sleep(for: .milliseconds(100))
                if Task.isCancelled { return }
                elapsed = min(adLength, elapsed + 0.1)
            }
            grant()
        }
    }

    private func close() { app.modal = nil }

    private func grant() {
        switch kind {
        case .scan: app.freeScansLeft += 1
        case .strings: app.lives = 6
        }
        withAnimation(.snappy) { stage = .granted }
    }

    // MARK: 49c · Out of free scans

    private var scanOffer: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                HStack {
                    Button(action: close) {
                        Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
                    }
                    .foregroundStyle(OC.ink).accessibilityLabel("Close")
                    Spacer()
                    Text("Scan sheet").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                    Spacer()
                    Color.clear.frame(width: 44, height: 44)
                }
                .padding(.horizontal, 10)
                VStack(spacing: 10) {
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 20).fill(OC.glass).frame(height: 60)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 10)
                .blur(radius: 1).opacity(0.6)
                Spacer()
            }
            .background(WarmBackground())

            Color(hex: 0x171614, alpha: 0.35).ignoresSafeArea().onTapGesture(perform: close)

            VStack(alignment: .leading, spacing: 14) {
                Capsule().fill(OC.ink.opacity(0.18)).frame(width: 40, height: 5).frame(maxWidth: .infinity)
                VStack(alignment: .leading, spacing: 6) {
                    Eyebrow(text: "10 of 10 free scans used today")
                    Text("Want one more scan?").font(.outfit(24, .bold)).tracking(-0.5).foregroundStyle(OC.ink)
                    Text("Your free scans reset at midnight. You can also get one now.").font(.outfit(14)).foregroundStyle(OC.muted)
                }
                Button { stage = .playing } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "play.fill").font(.system(size: 17, weight: .bold)).foregroundStyle(OC.inkFixed)
                            .frame(width: 44, height: 44)
                            .background(RoundedRectangle(cornerRadius: 14).fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                            .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Watch a short ad").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                            Text("About 30 seconds · +1 scan now").font(.outfit(13)).foregroundStyle(OC.muted)
                        }
                        Spacer(minLength: 4)
                        Text("2 left today").font(.outfit(12, .heavy)).foregroundStyle(OC.goldText)
                    }
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 18).fill(OC.goldTint))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(OC.gold, lineWidth: 2))
                }
                .buttonStyle(.plain)
                Button { app.modal = .pro } label: {
                    HStack(spacing: 12) {
                        Text("PRO").font(.outfit(13, .heavy)).foregroundStyle(OC.gold)
                            .frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.1)))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Unlimited scans, no ads").font(.outfit(16, .bold)).foregroundStyle(OC.paper)
                            Text("7-day free trial").font(.outfit(13)).foregroundStyle(ImportNight.soft)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(OC.faint)
                    }
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 18).fill(OC.inkFixed))
                }
                .buttonStyle(.plain)
                Button("Wait until tomorrow", action: close)
                    .font(.outfit(14, .bold)).foregroundStyle(OC.muted).frame(maxWidth: .infinity, minHeight: 44)
            }
            .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 16)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                    .fill(OC.surface.opacity(0.97))
                    .shadow(color: Color.black.opacity(0.18), radius: 25, y: -20)
                    .ignoresSafeArea(edges: .bottom)
            )
            .transition(.move(edge: .bottom))
        }
    }

    // MARK: 49e · Out of strings

    private var stringsOffer: some View {
        VStack(spacing: 0) {
            HStack {
                Button { close(); app.pop() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
                }
                .foregroundStyle(OC.ink).accessibilityLabel("Leave lesson")
                Spacer()
                Text("Take 4 of 5").font(.outfit(13, .bold)).foregroundStyle(OC.muted)
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, 10)
            Spacer()
            VStack(spacing: 22) {
                HStack(spacing: 10) {
                    ForEach([-8.0, 6, -4, 9, -7, 5], id: \.self) { deg in
                        RoundedRectangle(cornerRadius: 2).fill(OC.red).frame(width: 4, height: 44).rotationEffect(.degrees(deg))
                    }
                }
                .padding(.horizontal, 26).padding(.vertical, 18)
                .glass(radius: 24)
                .accessibilityHidden(true)
                VStack(spacing: 8) {
                    Text("All six strings snapped").font(.outfit(28, .bold)).tracking(-0.7).foregroundStyle(OC.ink)
                    Text("You were one take away. Restring now to keep your progress in this lesson.")
                        .font(.outfit(15)).foregroundStyle(OC.muted).lineSpacing(3)
                }
                .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)
            Spacer()
            VStack(spacing: 10) {
                Button { stage = .playing } label: {
                    HStack(spacing: 10) { Image(systemName: "play.fill").font(.system(size: 12, weight: .bold)); Text("Watch an ad to restring") }
                }.buttonStyle(.gold)
                Button("Unlimited strings with Pro") { app.modal = .pro }
                    .font(.outfit(16, .bold)).foregroundStyle(OC.paper)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(RoundedRectangle(cornerRadius: OC.rButton).fill(OC.inkFixed))
                Button("Strings come back in 30 min") { close(); app.pop() }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.muted).frame(maxWidth: .infinity, minHeight: 44)
            }
            .padding(.horizontal, 20).padding(.bottom, 16)
        }
        .background(WarmBackground())
    }

    // MARK: 49d · Ad playing → reward granted

    private var playing: some View {
        let granted = stage == .granted
        let left = max(0, Int((adLength - elapsed).rounded(.up)))
        let reward = kind == .scan ? "+1 scan" : "6 new strings"
        return VStack(spacing: 0) {
            HStack {
                HStack(spacing: 8) {
                    Text("Ad").font(.outfit(10, .heavy)).tracking(0.5).foregroundStyle(ImportNight.goldText)
                        .padding(.horizontal, 6).frame(height: 18)
                        .overlay(RoundedRectangle(cornerRadius: 5).strokeBorder(Color(hex: 0xFFC940, alpha: 0.7)))
                    Text("[Advertiser]").font(.outfit(13)).foregroundStyle(ImportNight.muted)
                }
                Spacer()
                HStack(spacing: 6) {
                    if granted {
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .heavy)).foregroundStyle(ImportNight.teal)
                        Text("Reward earned")
                    } else {
                        Text("Reward in")
                        Text("0:" + String(format: "%02d", left)).font(.mono(13)).foregroundStyle(OC.gold).monospacedDigit()
                    }
                }
                .font(.outfit(13, .bold)).foregroundStyle(ImportNight.text)
                .padding(.horizontal, 12).frame(height: 32)
                .background(Capsule().fill(Color.white.opacity(0.1)))
            }
            .padding(.horizontal, 16).padding(.top, 8)

            PlaceholderBox(text: "[Ad creative from Google]", height: 420, dark: true)
                .frame(maxHeight: .infinity)
                .padding(.horizontal, 16).padding(.vertical, 24)

            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.12))
                    Capsule().fill(granted ? ImportNight.teal : OC.gold).frame(width: g.size.width * (granted ? 1 : elapsed / adLength))
                        .animation(.linear(duration: 0.1), value: elapsed)
                }
            }
            .frame(height: 4)
            .padding(.horizontal, 16).padding(.bottom, 16)

            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: granted ? "checkmark.circle.fill" : "checkmark").font(.system(size: granted ? 17 : 13, weight: .bold)).foregroundStyle(ImportNight.teal)
                    Group {
                        if granted {
                            Text("Reward granted: ") + Text(reward).bold() + Text(kind == .scan ? ". You can scan another sheet now." : ". Back to your lesson.")
                        } else {
                            Text("Reward: ") + Text(reward).bold() + Text(kind == .scan ? ". You’ll be back on the scan screen after the ad." : ". You’ll be back in your lesson after the ad.")
                        }
                    }
                    .font(.outfit(14, .semibold)).foregroundStyle(ImportNight.text)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: 0x14B8A6, alpha: 0.16)))

                if granted {
                    Button(kind == .scan ? "Back to scan" : "Back to lesson", action: close).buttonStyle(.gold)
                } else {
                    Button("Close ad and skip reward", action: close)
                        .font(.outfit(16, .bold)).foregroundStyle(ImportNight.muted)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(RoundedRectangle(cornerRadius: OC.rButton).fill(Color.white.opacity(0.08)))
                        .overlay(RoundedRectangle(cornerRadius: OC.rButton).strokeBorder(Color.white.opacity(0.14)))
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 16)
        }
        .background(NightBackground())
    }
}

// MARK: - Previews

#Preview("Import") { NavigationStack { ImportView() }.environment(AppState()) }
#Preview("Zawgyi") {
    NavigationStack { ZawgyiView(result: ScanResult(song: MockData.sinSarPar, detectedZawgyi: true, lowConfidence: ["G/B"])) }.environment(AppState())
}
#Preview("Review") {
    NavigationStack { ReviewView(result: ScanResult(song: MockData.sinSarPar, detectedZawgyi: true, lowConfidence: ["G/B"])) }.environment(AppState())
}
#Preview("Listen") { NavigationStack { ListenView() }.environment(AppState()) }
#Preview("Reward · scan") { RewardAdView(kind: .scan).environment(AppState()) }
#Preview("Reward · strings") { RewardAdView(kind: .strings).environment(AppState()) }

extension ScanSource {
    /// A PDF or image file chosen in Files (a security-scoped URL); nil if it can't be read.
    static func file(_ url: URL) -> ScanSource? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UTType(filenameExtension: url.pathExtension)?.conforms(to: .pdf) == true ? .pdf(data) : .image(data)
    }
}
