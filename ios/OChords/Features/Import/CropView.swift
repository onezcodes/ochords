import SwiftUI

/// Crop a picked photo to the sheet before it's read: four corners to drag (starting on the page edges the app
/// detects), a quarter-turn for sideways photos, and the result straightened like a scan.
struct ImportCropView: View {
    let onCancel: () -> Void
    /// The straightened sheet.
    let onDone: (CGImage) -> Void

    @State private var image: CGImage
    @State private var corners: ScanImage.PageCorners
    @State private var detected: ScanImage.PageCorners?
    /// The corner being dragged (for its larger handle).
    @State private var dragging: Int?

    private static let handle: CGFloat = 26
    private static let names = ["Top-left", "Top-right", "Bottom-right", "Bottom-left"]

    init(image: CGImage, onCancel: @escaping () -> Void, onDone: @escaping (CGImage) -> Void) {
        self.onCancel = onCancel
        self.onDone = onDone
        let page = ScanImage.detectPage(image)
        _image = State(initialValue: image)
        _detected = State(initialValue: page)
        _corners = State(initialValue: page ?? Self.inset)
    }

    /// Without a detected page: nearly the whole photo, so the corners are easy to grab.
    private static let inset = ScanImage.PageCorners(topLeft: CGPoint(x: 0.04, y: 0.04), topRight: CGPoint(x: 0.96, y: 0.04),
                                                     bottomRight: CGPoint(x: 0.96, y: 0.96), bottomLeft: CGPoint(x: 0.04, y: 0.96))

    private var isWhole: Bool { corners == .whole || corners == Self.inset }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("Cancel", action: onCancel)
                    .font(.outfit(16, .semibold)).foregroundStyle(OC.nightText)
                    .frame(minWidth: 44, minHeight: 44)
                Spacer()
                Text("Crop the sheet").font(.outfit(17, .bold)).foregroundStyle(OC.nightText)
                Spacer()
                Button {
                    withAnimation(.snappy) { corners = isWhole ? (detected ?? Self.inset) : .whole }
                } label: {
                    Text(isWhole ? (detected == nil ? "Reset" : "Detect page") : "Whole photo")
                }
                .font(.outfit(14, .semibold)).foregroundStyle(OC.gold)
                .frame(minWidth: 44, minHeight: 44)
            }
            .padding(.horizontal, 16)

            Text(detected == nil ? "Drag the corners to the edges of the sheet" : "We found the page — drag a corner to adjust")
                .font(.outfit(13)).foregroundStyle(OC.nightMuted)
                .padding(.top, 2)

            GeometryReader { geo in
                let frame = fitted(in: geo.size)
                ZStack(alignment: .topLeading) {
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .frame(width: frame.width, height: frame.height)
                        .offset(x: frame.minX, y: frame.minY)
                    outline(in: frame)
                    ForEach(0..<4, id: \.self) { i in handle(i, in: frame) }
                }
                .coordinateSpace(name: "crop")
            }
            .padding(24)

            HStack(spacing: 12) {
                Button(action: rotate) {
                    Label("Rotate", systemImage: "rotate.right")
                        .font(.outfit(15, .semibold)).foregroundStyle(OC.nightText)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0x4A4743)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button(action: finish) { Label("Scan", systemImage: "text.viewfinder") }
                    .buttonStyle(.gold(height: 52))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
        .background(NightBackground())
        .environment(\.colorScheme, .dark)
        #if DEBUG
        // -demoCropAuto YES presses Scan after a moment (simulator checks of the whole crop → read path).
        .task {
            guard UserDefaults.standard.bool(forKey: "demoCropAuto") else { return }
            try? await Task.sleep(for: .seconds(2))
            finish()
        }
        #endif
    }

    // MARK: Pieces

    /// Where the photo sits on screen (aspect-fit).
    private func fitted(in size: CGSize) -> CGRect {
        let w = CGFloat(image.width), h = CGFloat(image.height)
        let scale = min(size.width / w, size.height / h)
        let fit = CGSize(width: w * scale, height: h * scale)
        return CGRect(x: (size.width - fit.width) / 2, y: (size.height - fit.height) / 2, width: fit.width, height: fit.height)
    }

    private func onScreen(_ p: CGPoint, _ frame: CGRect) -> CGPoint {
        CGPoint(x: frame.minX + p.x * frame.width, y: frame.minY + p.y * frame.height)
    }

    /// The sheet outlined in gold, everything outside it dimmed.
    private func outline(in frame: CGRect) -> some View {
        let points = corners.all.map { onScreen($0, frame) }
        var sheet = Path()
        sheet.addLines(points)
        sheet.closeSubpath()
        var outside = Path(frame)
        outside.addPath(sheet)
        return ZStack {
            outside.fill(Color.black.opacity(0.55), style: FillStyle(eoFill: true))
            sheet.stroke(OC.gold, lineWidth: 2)
        }
        .allowsHitTesting(false)
    }

    private func handle(_ i: Int, in frame: CGRect) -> some View {
        let p = onScreen(corners.all[i], frame)
        let size = dragging == i ? Self.handle * 1.4 : Self.handle
        return Circle()
            .fill(OC.gold)
            .overlay(Circle().strokeBorder(Color.white, lineWidth: 2))
            .frame(width: size, height: size)
            .shadow(color: .black.opacity(0.4), radius: 4)
            .frame(width: 44, height: 44)
            .contentShape(Circle())
            .position(p)
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("crop"))
                    .onChanged { v in
                        dragging = i
                        move(i, to: CGPoint(x: (v.location.x - frame.minX) / frame.width, y: (v.location.y - frame.minY) / frame.height))
                    }
                    .onEnded { _ in dragging = nil }
            )
            .accessibilityElement()
            .accessibilityLabel("\(Self.names[i]) corner")
            .accessibilityHint("Drag to the corner of the sheet")
    }

    // MARK: Actions

    /// Moves a corner, staying on the photo and keeping the outline a proper four-sided shape.
    private func move(_ i: Int, to p: CGPoint) {
        let clamped = CGPoint(x: min(1, max(0, p.x)), y: min(1, max(0, p.y)))
        var next = corners
        switch i {
        case 0: next.topLeft = clamped
        case 1: next.topRight = clamped
        case 2: next.bottomRight = clamped
        default: next.bottomLeft = clamped
        }
        if next.isConvex { corners = next }
    }

    /// A quarter turn clockwise; the page is looked for again in the turned photo.
    private func rotate() {
        guard let turned = ScanImage.rotatedClockwise(image) else { return }
        image = turned
        detected = ScanImage.detectPage(turned)
        withAnimation(.snappy) { corners = detected ?? Self.inset }
    }

    private func finish() {
        // The whole photo needs no straightening; otherwise flatten what's inside the corners.
        let result = corners == .whole ? image : (ScanImage.flatten(image, to: corners) ?? image)
        onDone(result)
    }
}
