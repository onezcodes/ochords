import SwiftUI
import UIKit
import AVFoundation

// MARK: - Shared helpers (tools group)

fileprivate enum ToolsMusic {
    /// Flat spelling used by "Chords by key".
    static let flats = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"]
    static let stringNames = ["E", "A", "D", "G", "B", "e"]
    static let fingerNames = [1: "Index", 2: "Middle", 3: "Ring", 4: "Pinky"]

    /// Transposes a chord (incl. slash chords) and spells the result with flats.
    static func transposeFlat(_ chord: String, by t: Int) -> String {
        chord.split(separator: "/").map { part -> String in
            let p = String(part)
            var root = String(p.prefix(1))
            var rest = String(p.dropFirst())
            if let f = rest.first, f == "b" || f == "#" { root.append(f); rest.removeFirst() }
            guard let i = ChordTheory.index[root] else { return p }
            return flats[((i + t) % 12 + 12) % 12] + rest
        }.joined(separator: "/")
    }
}

// Fixed colours from the always-dark screens.
fileprivate enum ToolsNight {
    static let border = Color(hex: 0x393733)
    static let cell = Color(hex: 0x2A2825)
    static let cellEmpty = Color(hex: 0x1E1D1B)
    static let deepInk = Color(hex: 0x0F0E0D)
    static let bar = Color(hex: 0x171614)
    static let tealText = Color(hex: 0x5EEAD4)
    static let redText = Color(hex: 0xFCA5A5)
    static let softText = Color(hex: 0xBDB9B3)
}

/// Wrapping row layout (chips, chord-over-lyric segments).
fileprivate struct ToolsFlowLayout: Layout {
    var spacing: CGFloat = 6
    var lineSpacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, width: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > maxW { y += rowH + lineSpacing; x = 0; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
            width = max(width, x - spacing)
        }
        return CGSize(width: proposal.width ?? width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + sz.width > bounds.maxX { y += rowH + lineSpacing; x = bounds.minX; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}

/// Header with a leading icon button (close ✕ / back ‹), centred title and optional trailing view.
fileprivate struct ToolsBar<Trailing: View>: View {
    var title: String
    var systemImage = "chevron.left"
    var label = "Back"
    var color: Color = OC.ink
    let action: () -> Void
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 0) {
            Button(action: action) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(color)
            .accessibilityLabel(label)
            Spacer(minLength: 0)
            Text(title).font(.outfit(16, .bold)).foregroundStyle(color).lineLimit(1)
            Spacer(minLength: 0)
            trailing().frame(minWidth: 44, alignment: .trailing)
        }
        .padding(.horizontal, 10)
        .frame(height: 44)
    }
}

extension ToolsBar where Trailing == EmptyView {
    fileprivate init(title: String, systemImage: String = "chevron.left", label: String = "Back", color: Color = OC.ink, action: @escaping () -> Void) {
        self.init(title: title, systemImage: systemImage, label: label, color: color, action: action, trailing: { EmptyView() })
    }
}

fileprivate struct ToolsShareItem: Identifiable {
    let id = UUID()
    let items: [Any]
}

fileprivate struct ToolsActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

fileprivate struct ToolsSectionLabel: View {
    let text: String
    var color: Color = OC.muted
    var body: some View {
        Text(text).font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(color)
    }
}

/// Shows the microphone primer the first time a mic feature is used (permNote on the canvas).
fileprivate struct ToolsMicPrimer {
    static let key = "tools.micPrimerShown"
    @MainActor static func showIfNeeded(_ app: AppState) {
        let d = UserDefaults.standard
        guard !d.bool(forKey: key) else { return }
        d.set(true, forKey: key)
        app.modal = .permission(.microphone)
    }
}

// MARK: - 7 · Chord finger guide

struct ChordGuideView: View {
    let focus: String?
    let songChords: [String]

    @Environment(AppState.self) private var app
    @State private var selected: String
    @State private var variant = 0

    init(focus: String?, songChords: [String]) {
        self.focus = focus
        self.songChords = songChords
        _selected = State(initialValue: focus ?? songChords.first ?? "C")
    }

    private var commonChips: [String] { ChordLibrary.common.filter { !songChords.contains($0) } }
    private var info: ChordInfo? { ChordLibrary.info(selected) }
    private var voicing: ChordVoicing? {
        guard let info, !info.voicings.isEmpty else { return nil }
        return info.voicings[min(variant, info.voicings.count - 1)]
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Chord guide", backLabel: "Back") { leftHandButton }
            chipRow
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header.padding(.top, 14)
                    if let voicing {
                        ChordDiagram(voicing: voicing, width: 260, leftHanded: app.leftHanded)
                            .overlay(alignment: .topLeading) {
                                if voicing.baseFret == 1 { ChordGuideFretNumbers() }
                            }
                            .padding(.top, 14).padding(.bottom, 12)
                            .frame(maxWidth: .infinity)
                            .glass()
                            .padding(.top, 12)
                            .animation(.snappy(duration: 0.3), value: app.leftHanded)
                        ChordGuideFingerLegend().padding(.top, 10)
                        ChordGuideSteps(voicing: voicing).padding(.top, 10)
                        tipCard(voicing).padding(.top, 12)
                    } else {
                        PlaceholderBox(text: "No diagram for \(selected) yet", height: 220).padding(.top, 12)
                        tipCard(nil).padding(.top, 12)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
        }
        .ocPage()
    }

    private var leftHandButton: some View {
        let on = app.leftHanded
        return Button { app.leftHanded.toggle() } label: {
            Text("Left hand")
                .font(.outfit(12, .bold))
                .foregroundStyle(on ? OC.background : OC.ink)
                .padding(.horizontal, 10)
                .frame(height: 36)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.ink : OC.glass))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.ink : OC.border))
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.trailing, 6)
        .accessibilityLabel("Left-handed view")
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var chipRow: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 6) {
                if !songChords.isEmpty {
                    chipLabel("SONG")
                    ForEach(songChords, id: \.self) { chip($0) }
                    Rectangle().fill(OC.switchOff).frame(width: 1, height: 24).padding(.horizontal, 4)
                }
                chipLabel("COMMON")
                ForEach(commonChips, id: \.self) { chip($0) }
            }
            .padding(.leading, 20).padding(.trailing, 14)
            .padding(.top, 6)
        }
        .scrollIndicators(.hidden)
    }

    private func chipLabel(_ t: String) -> some View {
        Text(t).font(.outfit(11, .heavy)).tracking(0.5).foregroundStyle(OC.muted).padding(.trailing, 2)
    }

    private func chip(_ name: String) -> some View {
        Chip(text: name, isOn: name == selected, mono: true, height: 40) {
            withAnimation(.snappy(duration: 0.2)) { selected = name; variant = 0 }
        }
        .frame(minHeight: 44)
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text(selected).font(.mono(40, .heavy)).foregroundStyle(OC.goldText)
                if let voicing {
                    HStack(spacing: 0) {
                        Text("\(info?.full ?? selected) · ").foregroundStyle(OC.muted)
                        Text(voicing.pattern.map(String.init).joined(separator: " ")).font(.mono(14)).foregroundStyle(OC.ink)
                    }
                    .font(.outfit(14))
                }
            }
            Spacer(minLength: 8)
            if let info, info.voicings.count > 1 { variantPicker(info) }
        }
    }

    private func variantPicker(_ info: ChordInfo) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(info.voicings.enumerated()), id: \.offset) { i, v in
                let on = i == min(variant, info.voicings.count - 1)
                Button { withAnimation(.snappy(duration: 0.2)) { variant = i } } label: {
                    Text(v.label ?? "Shape \(i + 1)")
                        .font(.outfit(13, .bold))
                        .foregroundStyle(on ? OC.ink : OC.muted)
                        .padding(.horizontal, 10)
                        .frame(height: 36)
                        .background {
                            if on { RoundedRectangle(cornerRadius: 14, style: .continuous).fill(OC.surface).shadow(color: .black.opacity(0.06), radius: 3, y: 1) }
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(.horizontal, 3)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(OC.softFill).padding(.vertical, 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Shape")
    }

    private func tipCard(_ v: ChordVoicing?) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "lightbulb")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(OC.goldText)
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: 2) {
                if let v {
                    Text(strumText(v)).font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                    if !v.tip.isEmpty { Text(v.tip).font(.outfit(13)).foregroundStyle(OC.ink) }
                }
                HStack(spacing: 14) {
                    Button("Check it with the mic ›") { app.push(.practice(chord: selected)) }
                    Button("Save as image ›") { app.push(.saveImage(chord: selected)) }
                }
                .font(.outfit(13, .bold))
                .foregroundStyle(OC.goldText)
                .buttonStyle(.plain)
                .frame(minHeight: 32)
            }
            .lineSpacing(2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(OC.goldTint))
    }

    private func strumText(_ v: ChordVoicing) -> String {
        let first = Array(v.pattern).firstIndex { $0 != "x" } ?? 0
        return "Strum from string \(6 - first) (\(ToolsMusic.stringNames[first]))" + (first == 0 ? " — all six" : "")
    }
}

/// Fret numbers 1–4 down the left of the diagram (design p07); grid starts 28pt below the top at width 260.
fileprivate struct ChordGuideFretNumbers: View {
    var body: some View {
        VStack(spacing: 0) {
            ForEach(1...4, id: \.self) { f in
                Text("\(f)").font(.mono(12)).foregroundStyle(OC.muted).frame(width: 20, height: 46)
            }
        }
        .offset(x: -34, y: 28)
        .accessibilityHidden(true)
    }
}

fileprivate struct ChordGuideFingerLegend: View {
    var body: some View {
        HStack {
            ForEach(1...4, id: \.self) { f in
                HStack(spacing: 5) {
                    Text("\(f)").font(.outfit(11, .bold)).foregroundStyle(.white)
                        .frame(width: 18, height: 18)
                        .background(Circle().fill(OC.finger[f]!))
                    Text(ToolsMusic.fingerNames[f]!).font(.outfit(12, .bold)).foregroundStyle(OC.ink)
                }
                if f < 4 { Spacer(minLength: 4) }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

fileprivate struct ChordGuideSteps: View {
    let voicing: ChordVoicing

    private struct Step: Identifiable {
        let id: Int
        let icon: String
        let fill: Color
        let fg: Color
        let ring: Color
        let text: String
        let strong: Bool
    }

    private var steps: [Step] {
        let pat = Array(voicing.pattern)
        var out: [Step] = []
        for i in 0..<min(6, pat.count) {
            let dot = voicing.dots.first { $0.string == i }
            let b = voicing.barre
            let inBarre = b.map { i >= min($0.from, $0.to) && i <= max($0.from, $0.to) } ?? false
            if pat[i] == "x" {
                out.append(Step(id: i, icon: "×", fill: OC.glass, fg: OC.muted, ring: OC.switchOff, text: "Don’t play — mute it", strong: false))
            } else if pat[i] == "0" {
                out.append(Step(id: i, icon: "", fill: OC.glass, fg: OC.ink, ring: OC.ink, text: "Play open", strong: false))
            } else if let d = dot {
                let c = OC.finger[d.finger] ?? OC.inkFixed
                out.append(Step(id: i, icon: "\(d.finger)", fill: c, fg: .white, ring: c, text: "\(ToolsMusic.fingerNames[d.finger] ?? "Finger") finger · fret \(d.fret)", strong: true))
            } else if inBarre, let b {
                out.append(Step(id: i, icon: "1", fill: OC.finger[1]!, fg: .white, ring: OC.finger[1]!, text: "Index barre · fret \(b.fret)", strong: true))
            }
        }
        return out
    }

    var body: some View {
        VStack(spacing: 0) {
            ForEach(steps) { st in
                HStack(spacing: 10) {
                    Text("\(6 - st.id) · \(ToolsMusic.stringNames[st.id])")
                        .font(.mono(12)).foregroundStyle(OC.muted)
                        .frame(width: 40, alignment: .leading)
                    Text(st.icon).font(.outfit(11, .heavy)).foregroundStyle(st.fg)
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(st.fill))
                        .overlay(Circle().strokeBorder(st.ring, lineWidth: 1.5))
                    Text(st.text)
                        .font(.outfit(14, st.strong ? .bold : .medium))
                        .foregroundStyle(st.icon == "×" ? OC.muted : OC.ink)
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 32)
                .overlay(alignment: .bottom) { Rectangle().fill(OC.line).frame(height: 1) }
                .accessibilityElement(children: .combine)
            }
        }
    }
}

// MARK: - 8 · Chords by key

fileprivate enum KeysQuality {
    case major, minor, dim
    var bg: Color {
        switch self {
        case .major: OC.goldTint
        case .minor: OC.blueTint
        case .dim: OC.softFill
        }
    }
    var fg: Color {
        switch self {
        case .major: OC.goldText
        case .minor: OC.blue
        case .dim: OC.muted
        }
    }
}

struct KeysView: View {
    @Environment(AppState.self) private var app
    @State private var key = 0
    @State private var playing = false
    @State private var bar = 0

    private static let steps = [0, 2, 4, 5, 7, 9, 11, 12]
    private static let quality = ["", "m", "m", "", "", "m", "dim", ""]
    private static let roman = ["I", "ii", "iii", "IV", "V", "vi", "vii°", "I"]
    private static let sol = ["ဒို", "ရေ", "မီ", "ဖာ", "ဆို", "လာ", "တီ", "ဒိုး"]
    private static let used: Set<Int> = [0, 3, 4, 5, 7]
    private static let round = [0, 4, 5, 2, 3, 1, 0, 4]
    private static let rowKeys = [0, 2, 4, 5, 7, 9, 11]
    private static let song: [(String, String)] = [("C", "I"), ("G/B", "V/3"), ("Am", "vi"), ("F", "IV"), ("G", "V")]

    private func chordAt(_ k: Int, _ d: Int) -> String { ToolsMusic.flats[(k + Self.steps[d]) % 12] + Self.quality[d] }
    private func qual(_ d: Int) -> KeysQuality { Self.quality[d] == "m" ? .minor : Self.quality[d] == "dim" ? .dim : .major }
    private var keyName: String { ToolsMusic.flats[key] }
    private var songInKey: [String] { Self.song.map { ToolsMusic.transposeFlat($0.0, by: key) } }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Chords by key", backLabel: "Back") {
                Button { app.push(.chords(focus: nil, songChords: songInKey)) } label: {
                    Image(systemName: "hand.point.up.left")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(OC.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Finger guide")
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    keyChips
                    Group {
                        HStack(alignment: .firstTextBaseline) {
                            (Text("Key of ") + Text(keyName).font(.mono(28)).foregroundColor(OC.goldText))
                                .font(.outfit(28, .heavy)).tracking(-0.6).foregroundStyle(OC.ink)
                            Spacer()
                            Text("Major scale").font(.outfit(13)).foregroundStyle(OC.muted)
                        }
                        .padding(.top, 16)
                        degreeGrid.padding(.top, 12)
                        legend.padding(.top, 10)
                        songCard.padding(.top, 16)
                        BannerAdView(onRemoveAds: { app.push(.adChoices) }).padding(.top, 16)
                        heading("Round chord · \(keyName) family", "8 bars").padding(.top, 22)
                        roundCard.padding(.top, 8)
                        heading("Round chord · every family", "Tap a row to practise it").padding(.top, 22)
                        KeysTable(header: Self.round.map { Self.roman[$0] }, headerIsMyanmar: false, rows: Self.rowKeys.map { rk in
                            KeysTable.Row(key: rk, cells: Self.round.map { (chordAt(rk, $0), qual($0)) })
                        }, selected: key, smallFont: 10) { rk in
                            withAnimation(.snappy) { key = rk; bar = 0 }
                        }
                        .padding(.top, 8)
                        heading("All keys", "Tap a row to open that key").padding(.top, 22)
                        KeysTable(header: Self.sol, headerIsMyanmar: true, rows: Self.rowKeys.map { rk in
                            KeysTable.Row(key: rk, cells: (0..<8).map { (chordAt(rk, $0), qual($0)) })
                        }, selected: key, smallFont: 9) { rk in
                            withAnimation(.snappy) { key = rk }
                        }
                        .padding(.top, 8)
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
        }
        .ocPage()
        .task(id: playing) {
            guard playing else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1.5))
                if Task.isCancelled { break }
                withAnimation(.snappy) { bar = (bar + 1) % 8 }
            }
        }
    }

    private var keyChips: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 6) {
                ForEach(0..<12, id: \.self) { i in
                    Chip(text: ToolsMusic.flats[i], isOn: i == key, mono: true, height: 40) {
                        withAnimation(.snappy) { key = i }
                    }
                    .frame(minHeight: 44)
                    .accessibilityLabel("Key \(ToolsMusic.flats[i])")
                }
            }
            .padding(.leading, 20).padding(.trailing, 14).padding(.top, 4)
        }
        .scrollIndicators(.hidden)
    }

    private var degreeGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
            ForEach(0..<8, id: \.self) { d in
                let name = chordAt(key, d)
                let q = qual(d)
                let used = Self.used.contains(d)
                Button { app.push(.chords(focus: name, songChords: songInKey)) } label: {
                    VStack(spacing: 0) {
                        Text(Self.sol[d]).font(.myanmar(14, .semibold))
                        Spacer(minLength: 0)
                        Text(name).font(.mono(name.count > 4 ? 16 : name.count > 3 ? 19 : 22)).lineLimit(1).minimumScaleFactor(0.6)
                        Spacer(minLength: 0)
                        Text(Self.roman[d]).font(.outfit(11, .bold)).opacity(0.8)
                    }
                    .foregroundStyle(q.fg)
                    .padding(.top, 6).padding(.bottom, 8).padding(.horizontal, 4)
                    .frame(maxWidth: .infinity)
                    .frame(height: 88)
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(q.bg))
                    .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(used ? OC.ink : .clear, lineWidth: 2))
                    .overlay(alignment: .topTrailing) {
                        if used { Circle().fill(OC.ink).frame(width: 8, height: 8).padding(8) }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(Self.sol[d]), \(name), \(Self.roman[d])\(used ? ", used in this song" : "")")
            }
        }
    }

    private var legend: some View {
        ToolsFlowLayout(spacing: 14, lineSpacing: 6) {
            legendItem("Major", KeysQuality.major.bg, OC.gold.opacity(0.4))
            legendItem("Minor", KeysQuality.minor.bg, OC.blue.opacity(0.3))
            legendItem("Diminished", KeysQuality.dim.bg, OC.switchOff)
            HStack(spacing: 5) {
                Circle().fill(OC.ink).frame(width: 8, height: 8)
                Text("Used in this song")
            }
        }
        .font(.outfit(12, .bold))
        .foregroundStyle(OC.muted)
    }

    private func legendItem(_ t: String, _ fill: Color, _ stroke: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(fill).overlay(Circle().strokeBorder(stroke)).frame(width: 12, height: 12)
            Text(t)
        }
    }

    private var songCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                (Text(MockData.sinSarPar.title).font(.myanmar(14, .bold)) + Text(" in \(keyName)"))
                    .font(.outfit(14, .bold))
                Spacer()
                Text("original \(MockData.sinSarPar.key)").font(.outfit(12)).foregroundStyle(ToolsNight.softText)
            }
            ToolsFlowLayout(spacing: 6, lineSpacing: 6) {
                ForEach(Array(Self.song.enumerated()), id: \.offset) { _, s in
                    VStack(spacing: 2) {
                        Text(ToolsMusic.transposeFlat(s.0, by: key)).font(.mono(15)).foregroundStyle(OC.gold)
                        Text(s.1).font(.outfit(10, .bold)).foregroundStyle(ToolsNight.softText)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .frame(minWidth: 52, minHeight: 50)
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(ToolsNight.cellEmpty))
                }
            }
        }
        .padding(14)
        .inkGlass()
    }

    private func heading(_ t: String, _ sub: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(t).font(.outfit(18, .bold)).foregroundStyle(OC.ink)
            Spacer(minLength: 8)
            Text(sub).font(.outfit(12)).foregroundStyle(OC.muted)
        }
    }

    private var roundCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.round.map { chordAt(key, $0) }.joined(separator: " → ")).font(.mono(15)).foregroundStyle(OC.goldText)
                Text(Self.round.map { Self.roman[$0] }.joined(separator: " – ")).font(.mono(12)).foregroundStyle(OC.muted)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
                ForEach(Array(Self.round.enumerated()), id: \.offset) { i, d in
                    roundBar(i, d)
                }
            }
            HStack(spacing: 10) {
                Button { playing.toggle(); if playing { bar = 0 } } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(playing ? Color.white : OC.inkFixed)
                        .frame(width: 48, height: 48)
                        .background(Circle().fill(playing ? OC.inkFixed : OC.gold))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(playing ? "Pause round" : "Play round")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Strum ↓ ↓ ↑ ↑ ↓ ↑ · 4 beats a bar").font(.outfit(13, .bold)).foregroundStyle(OC.ink)
                    Text(playing ? "Looping · change on bar \(bar + 1)" : "Tap play to loop all 8 bars").font(.outfit(12)).foregroundStyle(OC.muted)
                }
            }
        }
        .padding(14)
        .glass()
    }

    private func roundBar(_ i: Int, _ d: Int) -> some View {
        let name = chordAt(key, d)
        let q = qual(d)
        let live = playing && bar == i
        return Button { app.push(.chords(focus: name, songChords: songInKey)) } label: {
            VStack(spacing: 0) {
                Text("BAR \(i + 1)").font(.outfit(10, .heavy)).opacity(0.75)
                Spacer(minLength: 0)
                Text(name).font(.mono(name.count > 4 ? 15 : name.count > 3 ? 18 : 22)).lineLimit(1).minimumScaleFactor(0.6)
                Spacer(minLength: 0)
                Text(Self.sol[d]).font(.myanmar(12, .semibold))
            }
            .foregroundStyle(live ? OC.gold : q.fg)
            .padding(.vertical, 6).padding(.horizontal, 4)
            .frame(maxWidth: .infinity)
            .frame(height: 76)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(live ? OC.inkFixed : q.bg))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(live ? OC.gold : .clear, lineWidth: 2))
            .scaleEffect(live ? 1.04 : 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Bar \(i + 1), \(name)")
    }
}

/// Key × chord table (Round chord · every family / All keys).
fileprivate struct KeysTable: View {
    struct Row { let key: Int; let cells: [(String, KeysQuality)] }
    let header: [String]
    let headerIsMyanmar: Bool
    let rows: [Row]
    let selected: Int
    let smallFont: CGFloat
    let pick: (Int) -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Color.clear.frame(width: 40)
                ForEach(Array(header.enumerated()), id: \.offset) { _, h in
                    Text(h)
                        .font(headerIsMyanmar ? .myanmar(12, .bold) : .mono(10))
                        .foregroundStyle(headerIsMyanmar ? OC.goldText : OC.muted)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: headerIsMyanmar ? 34 : 30)
            .background(OC.softFill)
            ForEach(rows, id: \.key) { r in
                let on = r.key == selected
                Button { pick(r.key) } label: {
                    HStack(spacing: 0) {
                        Text(ToolsMusic.flats[r.key]).font(.outfit(12, .heavy)).frame(width: 40)
                        ForEach(Array(r.cells.enumerated()), id: \.offset) { _, c in
                            Text(c.0)
                                .font(.mono(c.0.count > 4 ? smallFont : c.0.count > 3 ? min(11, smallFont + 1) : 12))
                                .foregroundStyle(on ? OC.background : c.1.fg)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .foregroundStyle(on ? OC.background : OC.ink)
                    .frame(height: 36)
                    .frame(maxWidth: .infinity)
                    .background(on ? OC.ink : Color.clear)
                    .overlay(alignment: .top) { Rectangle().fill(OC.line).frame(height: 1) }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Key \(ToolsMusic.flats[r.key]): \(r.cells.map(\.0).joined(separator: ", "))")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .glass()
    }
}

// MARK: - 19 · Strum trainer

struct StrumTrainerView: View {
    @Environment(AppState.self) private var app
    @State private var pat = 1
    @State private var prog = 0
    @State private var playing = false
    @State private var slot = 0
    @State private var bar = 0
    @State private var bpm = 70

    private static let patterns: [(name: String, p: [String])] = [
        ("Four downs", ["D", "", "D", "", "D", "", "D", ""]),
        ("Folk", ["D", "", "D", "U", "", "U", "D", "U"]),
        ("Ballad", ["D", "", "D", "", "D", "U", "D", "U"]),
        ("Eighths", ["D", "U", "D", "U", "D", "U", "D", "U"])
    ]
    private static let progs = [["C", "G", "Am", "F"], ["C", "G", "Am", "Em", "F", "Dm", "C", "G"], ["C", "Am", "F", "G"]]
    private static let progLabels = ["C G Am F", "Round", "C Am F G"]
    private static let count = ["1", "&", "2", "&", "3", "&", "4", "&"]

    private var pattern: [String] { Self.patterns[pat].p }
    private var chords: [String] { Self.progs[prog] }

    var body: some View {
        VStack(spacing: 0) {
            ToolsBar(title: "Strum trainer", color: OC.nightText) { app.pop() }
            ScrollView {
                VStack(spacing: 0) {
                    patternChips.padding(.top, 10)
                    nowPlaying.padding(.top, 22)
                    slots.padding(.top, 24)
                    HStack {
                        Text("↓ down · ↑ up · empty = miss the strings")
                        Spacer(minLength: 6)
                        Text(pattern.map { $0.isEmpty ? "·" : $0 }.joined())
                    }
                    .font(.outfit(12)).foregroundStyle(OC.nightMuted)
                    .padding(.top, 10)
                    progCard.padding(.top, 18)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            .scrollIndicators(.hidden)
            controls
        }
        .foregroundStyle(OC.nightText)
        .background(NightBackground())
        .toolbar(.hidden, for: .navigationBar)
        .task(id: "\(playing)-\(bpm)") {
            guard playing else { return }
            let ms = Int((30000.0 / Double(bpm)).rounded())
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(ms))
                if Task.isCancelled { break }
                let n = (slot + 1) % 8
                withAnimation(.easeOut(duration: 0.12)) {
                    slot = n
                    if n == 0 { bar += 1 }
                }
            }
        }
    }

    private var patternChips: some View {
        ToolsFlowLayout(spacing: 6, lineSpacing: 6) {
            ForEach(Array(Self.patterns.enumerated()), id: \.offset) { i, p in
                let on = i == pat
                Button { pat = i } label: {
                    Text(p.name).font(.outfit(13, .bold))
                        .foregroundStyle(on ? ToolsNight.deepInk : OC.nightText)
                        .padding(.horizontal, 12).frame(height: 38)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.nightText : .clear))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.nightText : ToolsNight.border))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var nowPlaying: some View {
        VStack(spacing: 4) {
            Text("NOW PLAYING").font(.outfit(12, .heavy)).tracking(0.6).foregroundStyle(OC.nightMuted)
            Text(chords[bar % chords.count])
                .font(.mono(72)).foregroundStyle(OC.gold)
                .contentTransition(.numericText())
            Text("next · \(chords[(bar + 1) % chords.count])").font(.mono(14, .semibold)).foregroundStyle(OC.nightMuted)
        }
        .accessibilityElement(children: .combine)
    }

    private var slots: some View {
        HStack(spacing: 4) {
            ForEach(0..<8, id: \.self) { i in
                let a = pattern[i]
                let live = playing && slot == i
                VStack(spacing: 8) {
                    Text(a == "D" ? "↓" : a == "U" ? "↑" : "")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundStyle(live ? ToolsNight.deepInk : a == "D" ? OC.nightText : OC.gold)
                        .frame(maxWidth: .infinity)
                        .frame(height: 92)
                        .background(RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(live ? (a.isEmpty ? ToolsNight.border : OC.gold) : (a.isEmpty ? Color.clear : ToolsNight.cell)))
                        .scaleEffect(live ? 1.06 : 1)
                        .offset(y: live ? (a == "U" ? -4 : 4) : 0)
                    Text(Self.count[i]).font(.mono(13)).foregroundStyle(live ? OC.gold : OC.faint)
                }
            }
        }
        .padding(.vertical, 16).padding(.horizontal, 10)
        .nightGlass(radius: 20)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Pattern \(pattern.map { $0 == "D" ? "down" : $0 == "U" ? "up" : "miss" }.joined(separator: ", "))")
    }

    private var progCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("CHORDS · ONE BAR EACH").font(.outfit(12, .heavy)).tracking(0.6).foregroundStyle(OC.nightMuted)
            HStack(spacing: 6) {
                ForEach(Array(Self.progLabels.enumerated()), id: \.offset) { i, l in
                    let on = i == prog
                    Button { prog = i; bar = 0 } label: {
                        Text(l).font(.mono(12))
                            .foregroundStyle(on ? ToolsNight.deepInk : OC.nightText)
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.gold : .clear))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.gold : ToolsNight.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .nightGlass(radius: 20)
    }

    private var controls: some View {
        HStack(spacing: 12) {
            Button {
                if playing { playing = false } else { slot = 0; bar = 0; playing = true }
            } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(ToolsNight.deepInk)
                    .frame(width: 64, height: 64)
                    .background(Circle().fill(OC.gold))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(playing ? "Pause" : "Play")
            VStack(alignment: .leading, spacing: 6) {
                Text("Tempo").font(.outfit(12)).foregroundStyle(OC.nightMuted)
                HStack(spacing: 6) {
                    tempoButton("−", "Slower") { bpm = max(40, bpm - 5) }
                    Text("\(bpm) BPM").font(.mono(20)).frame(maxWidth: .infinity)
                        .contentTransition(.numericText())
                    tempoButton("+", "Faster") { bpm = min(160, bpm + 5) }
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 14)
        .background(ToolsNight.bar.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(ToolsNight.cell).frame(height: 1) }
    }

    private func tempoButton(_ t: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy) { action() } } label: {
            Text(t).font(.system(size: 20))
                .foregroundStyle(OC.nightText)
                .frame(width: 44, height: 44)
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(ToolsNight.border))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - 23 · Mic chord check (ready → listening → result)

fileprivate struct PracticeChord: Hashable {
    let name: String
    /// Six characters, low E to high e: "x" muted, "0" open, digits = fret.
    let pattern: String
}

struct PracticeView: View {
    let chord: String

    @Environment(AppState.self) private var app
    @Environment(\.openURL) private var openURL
    @State private var chords: [PracticeChord]
    @State private var c: Int
    /// What each string did on the last strum (nil = nothing checked yet for this chord).
    @State private var verdicts: [ChordCheck.Verdict]?
    @State private var listening = false
    @State private var heardNothing = false
    @State private var micOff = false
    @State private var run = 0
    @State private var used = 0
    @State private var listener = StrumListener()

    private static let freeChecks = 5
    private static let labels = ["6 · E", "5 · A", "4 · D", "3 · G", "2 · B", "1 · e"]
    private static let heights: [CGFloat] = [8, 14, 22, 30, 18, 36, 26, 40, 20, 32, 16, 38, 28, 34, 12, 24, 30, 18, 10, 22, 14, 8]
    private static let library: [PracticeChord] = [
        PracticeChord(name: "C", pattern: "x32010"),
        PracticeChord(name: "G/B", pattern: "x20003"),
        PracticeChord(name: "Am", pattern: "x02210"),
        PracticeChord(name: "F", pattern: "xx3211")
    ]

    init(chord: String) {
        self.chord = chord
        var list = Self.library
        if let i = list.firstIndex(where: { $0.name == chord }) {
            _c = State(initialValue: i)
        } else {
            let pattern = ChordLibrary.info(chord)?.voicings.first?.pattern ?? "x32010"
            list = [PracticeChord(name: chord, pattern: pattern)] + Array(list.prefix(3))
            _c = State(initialValue: 0)
        }
        _chords = State(initialValue: list)
    }

    private var current: PracticeChord { chords[c] }
    private var hasResult: Bool { verdicts != nil && !listening }
    private var problems: [Int] { verdicts?.indices.filter { !verdicts![$0].isGood } ?? [] }
    private var clean: Bool { problems.isEmpty }
    private var freeLeft: Int { max(0, Self.freeChecks - used) }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Chord check", backLabel: "Back")
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    chips
                    if micOff { micOffCard }
                    listenCard
                    if hasResult {
                        result.transition(.opacity.combined(with: .move(edge: .bottom)))
                    }
                    footer
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
        }
        .ocPage()
        .animation(.snappy, value: hasResult)
        .animation(.snappy, value: listening)
        .onAppear {
            ToolsMicPrimer.showIfNeeded(app)
            #if DEBUG
            if UserDefaults.standard.bool(forKey: "demoAutoListen") { listen() }
            #endif
        }
        .onDisappear { listener.cancel() }
    }

    private var chips: some View {
        HStack(spacing: 6) {
            ForEach(Array(chords.enumerated()), id: \.offset) { i, ch in
                let on = i == c
                Button {
                    listener.cancel()
                    c = i; verdicts = nil; heardNothing = false
                } label: {
                    Text(ch.name).font(.mono(15)).foregroundStyle(on ? OC.inkFixed : OC.ink)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.gold : OC.glass))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.gold : OC.border))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private var statusText: String {
        if listening { return "Listening… strum once" }
        if heardNothing { return "Didn’t hear a strum — try closer to the phone" }
        guard hasResult else { return "Ready when you are" }
        if clean { return "Every string is clean" }
        return problems.count == 1 ? "1 string needs work" : "\(problems.count) strings need work"
    }
    private var statusColor: Color {
        listening ? OC.gold : heardNothing ? OC.nightMuted : hasResult ? (clean ? ToolsNight.tealText : ToolsNight.redText) : OC.nightMuted
    }

    private var listenCard: some View {
        VStack(spacing: 12) {
            Text("HOLD THE SHAPE, THEN STRUM ONCE").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.nightMuted)
            Text(current.name).font(.mono(56)).foregroundStyle(OC.gold)
            wave
            Text(statusText).font(.outfit(14, .semibold)).foregroundStyle(statusColor)
                .multilineTextAlignment(.center)
                .contentTransition(.opacity)
            Button(action: listen) {
                HStack(spacing: 8) {
                    Image(systemName: "mic").font(.system(size: 16, weight: .bold))
                    Text(verdicts == nil ? "Listen" : "Strum again").font(.outfit(16, .heavy))
                }
            }
            .buttonStyle(.gold(height: 52, fullWidth: false))
            .disabled(listening)
            .opacity(listening ? 0.6 : 1)
            .accessibilityLabel("Listen for a strum")
        }
        .padding(.vertical, 20).padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
        .inkGlass()
    }

    private var wave: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 20, paused: !listening)) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            HStack(spacing: 0) {
                ForEach(Array(Self.heights.enumerated()), id: \.offset) { i, h in
                    let hh: CGFloat = listening
                        ? h * (0.45 + 0.55 * CGFloat(abs(sin(t * 7 + Double(i) * 0.7))))
                        : hasResult ? max(4, (h * 0.35).rounded()) : 4
                    RoundedRectangle(cornerRadius: 2)
                        .fill(listening ? OC.gold : ToolsNight.border)
                        .frame(width: 5, height: max(4, hh))
                    if i < Self.heights.count - 1 { Spacer(minLength: 0) }
                }
            }
            .frame(width: 220, height: 44)
        }
        .accessibilityHidden(true)
    }

    private var micOffCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "mic.slash").font(.system(size: 18, weight: .semibold)).foregroundStyle(OC.red)
                .frame(width: 40, height: 40).background(Circle().fill(OC.redTint))
            VStack(alignment: .leading, spacing: 2) {
                Text("Microphone is off for O Chords").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                Text("Chord check needs it to hear your strum. Sound stays on your phone.").font(.outfit(12)).foregroundStyle(OC.muted)
            }
            Spacer(minLength: 4)
            Button("Settings") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
        }
        .padding(14)
        .glass()
    }

    private var result: some View {
        let judged = verdicts?.filter { $0 != .unclear }.count ?? 0
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(clean ? "Perfect \(current.name)!" : "Almost there").font(.outfit(18, .heavy)).foregroundStyle(OC.ink)
                Spacer()
                Text("\(judged - problems.count) / \(judged) strings").font(.mono(13)).foregroundStyle(OC.muted)
            }
            GlassList {
                ForEach(Array(current.pattern.prefix(6)).indices, id: \.self) { i in
                    stringRow(i)
                }
            }
            tip
                .font(.outfit(14)).foregroundStyle(OC.ink).lineSpacing(3)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(clean ? OC.tealTint : OC.goldTint))
            if verdicts?.contains(.unclear) == true {
                Text("“Can’t tell” strings play the same note as an overtone of a lower string, so the mic can’t hear them apart.")
                    .font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(3)
            }
        }
    }

    /// Advice for the first string that went wrong (low strings first — they're usually the cause).
    private var tip: Text {
        guard let i = problems.first, let v = verdicts?[i] else {
            return Text("Nice.").bold() + Text(" Now try switching to the next chord without looking.")
        }
        let name = ToolsMusic.stringNames[i] == "e" ? "High e" : "\(ToolsMusic.stringNames[i])"
        let fret = Array(current.pattern)[i]
        if v == .ringing {
            return Text("\(name) string should be muted.").bold()
                + Text(" Let a fretting finger rest lightly against it, or start your strum on the next string.")
        }
        if fret == "0" {
            return Text("\(name) string isn’t ringing.").bold()
                + Text(" A finger is touching it — arch your fingers so the open string can ring.")
        }
        return Text("\(name) string isn’t ringing.").bold()
            + Text(" Press just behind the fret wire and keep the fingertip upright so it doesn’t touch the next string.")
    }

    private func stringRow(_ i: Int) -> some View {
        let v = Array(current.pattern)[i]
        let expect = v == "x" ? "Should be muted" : v == "0" ? "Open string" : "Fret \(v)"
        let verdict = verdicts?[i] ?? .unclear
        let (word, fg, bg): (String, Color, Color) = switch verdict {
        case .clear: ("Clear ✓", OC.tealText, OC.tealTint)
        case .muted: ("Muted ✓", OC.tealText, OC.tealTint)
        case .dead: ("Not ringing", OC.red, OC.redTint)
        case .ringing: ("Ringing", OC.red, OC.redTint)
        case .unclear: ("Can’t tell", OC.muted, OC.glass)
        }
        return HStack(spacing: 12) {
            Text(Self.labels[i]).font(.mono(13)).foregroundStyle(OC.muted).frame(width: 44, alignment: .leading)
            Text(expect).font(.outfit(14)).foregroundStyle(OC.ink)
            Spacer(minLength: 6)
            Text(word).font(.outfit(13, .heavy))
                .foregroundStyle(fg)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(RoundedRectangle(cornerRadius: 8).fill(bg))
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 44)
        .accessibilityElement(children: .combine)
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                (Text("Clean in a row: ") + Text("\(run)").bold().foregroundColor(OC.ink))
                    .foregroundStyle(OC.muted)
                Spacer(minLength: 6)
                if app.isPro {
                    Text("Pro · unlimited checks").font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                } else {
                    Button("\(freeLeft) free checks left today · Pro ›") { app.modal = .pro }
                        .font(.outfit(13, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                        .frame(minHeight: 44)
                }
            }
            .font(.outfit(13))
            Text("Chord check listens through your microphone on this phone only. Nothing is recorded or uploaded.")
                .font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(3)
        }
    }

    private func listen() {
        guard !listening else { return }
        if !app.isPro && freeLeft == 0 { app.modal = .pro; return }
        if PitchDetector.permissionUndecided {
            Task { @MainActor in
                if await AVAudioApplication.requestRecordPermission() { listen() } else { micOff = true }
            }
            return
        }
        guard PitchDetector.hasPermission else { micOff = true; return }
        micOff = false
        heardNothing = false
        listening = true
        let pattern = current.pattern
        Task { @MainActor in
            defer { listening = false }
            let strum: StrumListener.Strum?
            do { strum = try await listener.next() } catch { micOff = true; return }
            guard let strum, current.pattern == pattern else { heardNothing = strum == nil; return }
            let found = await Task.detached(priority: .userInitiated) {
                ChordCheck.verdicts(strum.samples, sampleRate: strum.sampleRate, pattern: pattern)
            }.value
            verdicts = found
            run = found.allSatisfy(\.isGood) ? run + 1 : 0
            if !app.isPro { used = min(Self.freeChecks, used + 1) }
            UINotificationFeedbackGenerator().notificationOccurred(found.allSatisfy(\.isGood) ? .success : .warning)
        }
    }
}

// MARK: - 34 · Save as image

fileprivate struct SaveImageFormat {
    let label: String
    let w: CGFloat
    let h: CGFloat
    let pad: CGFloat
    let title: CGFloat
    static let all = [
        SaveImageFormat(label: "Story 9:16", w: 208, h: 370, pad: 16, title: 17),
        SaveImageFormat(label: "Square", w: 300, h: 300, pad: 16, title: 17),
        SaveImageFormat(label: "Sheet A4", w: 262, h: 370, pad: 18, title: 18)
    ]
}

fileprivate struct SaveImageTheme {
    let label: String
    let isDark: Bool
    let ink: Color
    let muted: Color
    let chord: Color
    let keyBg: Color
    let keyFg: Color
    let chipBg: Color
    let chipFg: Color
    let barBg: Color

    static let all = [
        SaveImageTheme(label: "Light", isDark: false, ink: Color(hex: 0x171614), muted: Color(hex: 0x6B6660), chord: Color(hex: 0x8F5E00),
                       keyBg: Color(hex: 0xFFB800), keyFg: Color(hex: 0x171614), chipBg: Color(hex: 0xFFF4D6), chipFg: Color(hex: 0x6B4600), barBg: Color(hex: 0x171614, alpha: 0.06)),
        SaveImageTheme(label: "Dark", isDark: true, ink: Color(hex: 0xFAFAF8), muted: Color(hex: 0xA8A49E), chord: Color(hex: 0xFFB800),
                       keyBg: Color(hex: 0xFFB800), keyFg: Color(hex: 0x171614), chipBg: Color.white.opacity(0.1), chipFg: Color(hex: 0xFFB800), barBg: Color.white.opacity(0.08)),
        SaveImageTheme(label: "Gold", isDark: false, ink: Color(hex: 0x171614), muted: Color(hex: 0x5A4300), chord: Color(hex: 0x171614),
                       keyBg: Color(hex: 0x171614), keyFg: Color(hex: 0xFFB800), chipBg: Color(hex: 0x171614, alpha: 0.12), chipFg: Color(hex: 0x171614), barBg: Color(hex: 0x171614, alpha: 0.1))
    ]

    @ViewBuilder var background: some View {
        switch label {
        case "Dark":
            ZStack {
                Color(hex: 0x0B0A09)
                RadialGradient(colors: [Color(hex: 0xFFB800).opacity(0.28), .clear], center: .topTrailing, startRadius: 0, endRadius: 260)
            }
        case "Gold":
            LinearGradient(colors: [Color(hex: 0xFFD15A), Color(hex: 0xFFB300)], startPoint: .topLeading, endPoint: .bottomTrailing)
        default:
            LinearGradient(colors: [.white, Color(hex: 0xF6F4EF)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

/// How much of the song the image holds.
fileprivate enum SaveImagePart: CaseIterable {
    case chorus, verseChorus, whole

    var label: String {
        switch self {
        case .chorus: "Chorus"
        case .verseChorus: "Verse + chorus"
        case .whole: "Whole song"
        }
    }
}

fileprivate struct SaveImageOptions: Equatable {
    var fmt = 0
    var theme = 0
    var lyrics = true
    var diagrams = true
    var part = SaveImagePart.whole
    var mark = true
}

/// A single-chord card (opened from the chord guide or a lesson). Fixed colours (no materials) so ImageRenderer
/// output matches the preview.
fileprivate struct SaveImageCard: View {
    let chord: String
    let o: SaveImageOptions
    var leftHanded = false

    private var f: SaveImageFormat { SaveImageFormat.all[o.fmt] }
    private var t: SaveImageTheme { SaveImageTheme.all[o.theme] }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            let info = ChordLibrary.info(chord)
            VStack(alignment: .leading, spacing: 0) {
                Text(chord).font(.mono(f.title * 1.6, .bold)).foregroundStyle(t.chord)
                Text(info?.full ?? "Chord").font(.outfit(11)).foregroundStyle(t.muted)
            }
            if let v = info?.voicings.first {
                ChordDiagram(voicing: v, width: min(f.w - f.pad * 2, f.h * 0.55), leftHanded: leftHanded, dark: t.isDark)
                    .frame(maxWidth: .infinity)
            }
            Spacer(minLength: 0)
            if o.mark { SaveImageMark(t: t, size: 9) }
        }
        .padding(f.pad)
        .frame(width: f.w, height: f.h, alignment: .topLeading)
        .background(t.background)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .environment(\.colorScheme, t.isDark ? .dark : .light)
    }
}

fileprivate struct SaveImageMark: View {
    let t: SaveImageTheme
    let size: CGFloat
    var body: some View {
        HStack(spacing: size * 0.6) {
            Rectangle().fill(Color(hex: 0xFFB800)).frame(width: size * 1.3, height: 2)
            Text("MADE WITH O CHORDS").font(.outfit(size, .bold)).tracking(size * 0.13)
        }
        .foregroundStyle(t.muted)
    }
}

/// A song as a chord sheet image, always 1080 × 1648 px (the size Myanmar chord sheets are shared at): title and
/// artist, chord diagrams top right, section labels in a left gutter ("V1.", "Cho."), chords over the lyrics,
/// instrumental parts as chord lines. The text is sized to fill the page — larger for a short song, smaller for a
/// long one — so the whole song always fits.
fileprivate struct SaveImageSheet: View {
    let song: Song
    let o: SaveImageOptions
    var leftHanded = false
    /// Text size relative to the design size (see `fitting`).
    var scale: CGFloat = 1
    /// Measuring: natural height, no fixed page.
    var measuring = false

    /// The page in points; rendered at 3× for 1080 × 1648 px.
    static let size = CGSize(width: 360, height: 1648.0 / 3)
    static let renderScale: CGFloat = 3
    private static let padX: CGFloat = 20, padTop: CGFloat = 18, padBottom: CGFloat = 14

    private var t: SaveImageTheme { SaveImageTheme.all[o.theme] }

    /// The largest text size at which the chosen part of the song fits the page.
    @MainActor static func fitting(_ song: Song, _ o: SaveImageOptions, leftHanded: Bool) -> CGFloat {
        func height(_ s: CGFloat) -> CGFloat {
            UIHostingController(rootView: SaveImageSheet(song: song, o: o, leftHanded: leftHanded, scale: s, measuring: true))
                .sizeThatFits(in: CGSize(width: size.width, height: .greatestFiniteMagnitude)).height
        }
        var lo: CGFloat = 0.35, hi: CGFloat = 1.8
        if height(hi) <= size.height { return hi }
        for _ in 0..<12 {
            let mid = (lo + hi) / 2
            if height(mid) <= size.height { lo = mid } else { hi = mid }
        }
        return lo
    }

    /// The sections shown. Scanned and typed-in songs don't always mark a chorus (or a verse), so each choice falls
    /// back to what the song does have rather than leaving the image empty.
    static func shown(_ song: Song, _ part: SaveImagePart) -> [SongSection] {
        let sung = song.sections.filter { $0.kind != .flow && (!$0.lines.isEmpty || !$0.bars.isEmpty) }
        let chorus = sung.first { $0.kind == .chorus }
        let verse = sung.first { $0.kind == .verse } ?? sung.first { $0.kind != .chorus }
        switch part {
        case .whole: return sung
        case .chorus: return [chorus ?? verse ?? sung.first].compactMap { $0 }
        case .verseChorus:
            let picked = [verse, chorus].compactMap { $0 }
            return picked.isEmpty ? Array(sung.prefix(2)) : picked
        }
    }

    var body: some View {
        let sections = Self.shown(song, o.part)
        let gutter = gutterLabels(sections)
        VStack(alignment: .leading, spacing: 12 * scale) {
            header
            VStack(alignment: .leading, spacing: 9 * scale) {
                ForEach(Array(sections.enumerated()), id: \.offset) { i, section in
                    sectionView(section, label: gutter[i])
                }
            }
            if !measuring { Spacer(minLength: 0) }
            if o.mark { SaveImageMark(t: t, size: 7.5).frame(maxWidth: .infinity) }
        }
        .padding(.horizontal, Self.padX).padding(.top, Self.padTop).padding(.bottom, Self.padBottom)
        .frame(width: Self.size.width, alignment: .topLeading)
        .frame(height: measuring ? nil : Self.size.height, alignment: .top)
        .background(t.background)
        .environment(\.colorScheme, t.isDark ? .dark : .light)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 2 * scale) {
                Text(song.title).font(titleFont(26 * scale, .bold)).foregroundStyle(t.ink).lineSpacing(song.isMyanmar ? 4 * scale : 0)
                if !song.artist.isEmpty {
                    Text(song.artist).font(titleFont(14 * scale, .semibold)).foregroundStyle(t.muted)
                }
                Text("Key \(song.key)").font(.outfit(10 * scale, .semibold)).foregroundStyle(t.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if o.diagrams {
                let w = 34 * min(scale, 1.3)
                HStack(alignment: .top, spacing: 5 * min(scale, 1.3)) {
                    ForEach(Array(song.usedChords.prefix(5)), id: \.self) { c in
                        VStack(spacing: 1) {
                            Text(c).font(.mono(8 * min(scale, 1.3), .bold)).foregroundStyle(t.ink)
                            if let v = ChordLibrary.info(c)?.voicings.first {
                                ChordDiagram(voicing: v, width: w, leftHanded: leftHanded, showFingers: false, showStringNames: false, dark: t.isDark)
                            }
                        }
                    }
                }
            }
        }
    }

    /// Instrumental parts: one chord line ("Music - C / D / Em / G"); sung parts: a gutter label and the lines.
    @ViewBuilder private func sectionView(_ section: SongSection, label: String) -> some View {
        if section.lines.isEmpty {
            (Text("\(label) – ").font(.outfit(11 * scale, .bold)) + Text(section.bars.joined(separator: " / ")).font(.mono(11 * scale, .bold)))
                .foregroundStyle(t.chord)
        } else {
            HStack(alignment: .top, spacing: 6 * scale) {
                Text(label).font(.outfit(11 * scale, .heavy)).foregroundStyle(t.ink)
                    .frame(width: 34 * scale, alignment: .leading)
                    .padding(.top, o.lyrics ? 11 * scale : 0)
                VStack(alignment: .leading, spacing: 2 * scale) {
                    if !section.bars.isEmpty {
                        Text(section.bars.joined(separator: " / ")).font(.mono(10.5 * scale, .bold)).foregroundStyle(t.chord)
                    }
                    ForEach(Array(section.lines.enumerated()), id: \.offset) { _, line in lineView(line) }
                }
            }
        }
    }

    @ViewBuilder private func lineView(_ line: String) -> some View {
        if o.lyrics {
            ToolsFlowLayout(spacing: 0, lineSpacing: 0) {
                ForEach(Array(ChordLine.parse(line).enumerated()), id: \.offset) { _, seg in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(seg.chord ?? " ").font(.mono(10 * scale, .bold)).foregroundStyle(t.chord)
                        Text(seg.text.isEmpty ? " " : seg.text).font(titleFont(12 * scale, .semibold)).foregroundStyle(t.ink)
                    }
                    .padding(.trailing, seg.trailingSpace ? 4 * scale : 0)
                }
            }
        } else {
            Text(ChordLine.chords(in: line).joined(separator: "   ")).font(.mono(11 * scale, .bold)).foregroundStyle(t.chord)
        }
    }

    private func titleFont(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        song.isMyanmar ? .myanmar(size, weight) : .outfit(size, weight)
    }

    /// Short labels as chord sheets write them: "V1.", "V2.", "Cho.", "Cho 2." (when the chorus comes back), "Br.",
    /// and the section's own name for anything else ("Intro", "Music").
    private func gutterLabels(_ sections: [SongSection]) -> [String] {
        let choruses = sections.filter { $0.kind == .chorus }.count
        var chorusSeen = 0
        return sections.map { s in
            switch s.kind {
            case .verse:
                let n = s.label.filter(\.isNumber)
                return "V\(n.isEmpty ? "" : n)."
            case .chorus:
                chorusSeen += 1
                return choruses > 1 ? "Cho \(chorusSeen)." : "Cho."
            case .bridge:
                return "Br."
            default:
                let name = s.label.isEmpty ? s.kind.rawValue : s.label
                return name.prefix(1).uppercased() + name.dropFirst().lowercased()
            }
        }
    }
}

struct SaveImageView: View {
    let chord: String
    var song: Song? = nil

    @Environment(AppState.self) private var app
    @State private var o = SaveImageOptions()
    @State private var toast: Toast?
    @State private var share: ToolsShareItem?
    /// Song images: the text size that fits the fixed page (recomputed when the content options change).
    @State private var scale: CGFloat = 1

    private var card: SaveImageCard { SaveImageCard(chord: chord, o: o, leftHanded: app.leftHanded) }
    private func sheet(_ song: Song) -> SaveImageSheet { SaveImageSheet(song: song, o: o, leftHanded: app.leftHanded, scale: scale) }
    private static let previewHeight: CGFloat = 392

    #if DEBUG
    /// -demoSaveImageChart YES opens with lyrics off (the chord-chart card).
    init(chord: String, song: Song? = nil) {
        self.chord = chord
        self.song = song
        if UserDefaults.standard.bool(forKey: "demoSaveImageChart") { _o = State(initialValue: SaveImageOptions(lyrics: false)) }
    }
    #endif

    var body: some View {
        VStack(spacing: 0) {
            ToolsBar(title: "Save as image", systemImage: "xmark", label: "Close") { app.pop() }
            Group {
                if let song {
                    // The fixed 1080 × 1648 page, shown scaled down.
                    let k = (Self.previewHeight - 24) / SaveImageSheet.size.height
                    sheet(song)
                        .clipShape(RoundedRectangle(cornerRadius: 16 / k, style: .continuous))
                        .scaleEffect(k)
                        .frame(width: SaveImageSheet.size.width * k, height: SaveImageSheet.size.height * k)
                } else {
                    card.overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.white.opacity(0.6)))
                }
            }
            .shadow(color: OC.inkFixed.opacity(0.22), radius: 25, y: 24)
            .frame(maxWidth: .infinity)
                .frame(height: Self.previewHeight)
                .onAppear(perform: refit)
                .onChange(of: o) { old, new in
                    // The style doesn't change the text's size; everything else can.
                    var a = old, b = new
                    a.theme = 0; b.theme = 0
                    if a != b { refit() }
                }
                #if DEBUG
                // -demoSaveImageExport YES writes the rendered image to tmp/save-image.png (checking long images).
                .task {
                    guard UserDefaults.standard.bool(forKey: "demoSaveImageExport") else { return }
                    try? await Task.sleep(for: .seconds(1))
                    try? render()?.pngData()?.write(to: URL.temporaryDirectory.appending(path: "save-image.png"))
                }
                #endif
                .animation(.snappy, value: o)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Image preview")
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if song == nil {
                        optionGroup("SIZE") {
                            Segmented(options: Array(0..<SaveImageFormat.all.count), selection: $o.fmt) { SaveImageFormat.all[$0].label }
                        }
                    }
                    optionGroup("STYLE") { styleButtons }
                    toggles
                }
                .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            bottomBar
        }
        .ocPage()
        .toast($toast)
        .sheet(item: $share) { ToolsActivityView(items: $0.items).presentationDetents([.medium, .large]) }
    }

    private func optionGroup<C: View>(_ title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ToolsSectionLabel(text: title)
            content()
        }
    }

    private var styleButtons: some View {
        HStack(spacing: 8) {
            ForEach(Array(SaveImageTheme.all.enumerated()), id: \.offset) { i, th in
                let on = i == o.theme
                Button { withAnimation(.snappy) { o.theme = i } } label: {
                    Text(th.label).font(.outfit(13, .bold)).foregroundStyle(th.isDark ? Color(hex: 0xFAFAF8) : Color(hex: 0x171614))
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(th.background.clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous)))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.gold : OC.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(th.label) style")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private var toggles: some View {
        GlassList {
            if song != nil {
                toggleRow("Lyrics", "Off = chord chart only", $o.lyrics)
                toggleRow("Chords used", "Show the chord list on top", $o.diagrams)
                VStack(alignment: .leading, spacing: 8) {
                    Text("How much of the song").font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
                    Segmented(options: SaveImagePart.allCases, selection: $o.part) { $0.label }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
            }
            toggleRow("Watermark", "Pro can remove it", Binding(get: { o.mark }, set: { v in
                if !v && !app.isPro { app.modal = .pro } else { o.mark = v }
            }))
        }
    }

    private func toggleRow(_ title: String, _ sub: String, _ b: Binding<Bool>) -> some View {
        Toggle(isOn: b) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
                Text(sub).font(.outfit(12)).foregroundStyle(OC.muted)
            }
        }
        .toggleStyle(.oc)
        .padding(.horizontal, 14)
        .frame(minHeight: 50)
        .accessibilityLabel(title)
    }

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Button { if let img = render() { share = ToolsShareItem(items: [img]) } } label: {
                Image(systemName: "square.and.arrow.up").font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(OC.ink)
                    .frame(width: 54, height: 54)
                    .glass(radius: 14)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share image")
            Button(action: save) {
                Label("Save to Photos", systemImage: "arrow.down.to.line")
            }
            .buttonStyle(.gold)
        }
        .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10)
        .background(Rectangle().fill(.ultraThinMaterial).overlay(Rectangle().fill(OC.glass)).ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
    }

    private func refit() {
        guard let song else { return }
        scale = SaveImageSheet.fitting(song, o, leftHanded: app.leftHanded)
    }

    /// Songs: always 1080 × 1648 px. Single chords: the chosen format at 3×.
    @MainActor private func render() -> UIImage? {
        if let song {
            let r = ImageRenderer(content: sheet(song))
            r.scale = SaveImageSheet.renderScale
            return r.uiImage
        }
        let r = ImageRenderer(content: card)
        r.scale = 3
        return r.uiImage
    }

    private func save() {
        guard let img = render() else { toast = Toast(text: "Couldn’t render the image", systemImage: "exclamationmark.triangle.fill"); return }
        UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
        toast = Toast(text: "Saved to Photos")
    }
}

// MARK: - 42 · Print or PDF

fileprivate struct PrintOptions: Equatable {
    var paper = 0          // 0 A4 · 1 US Letter
    var layout = 1         // 0 one column · 1 two columns
    var diagrams = true
    var lyrics = true
    var large = false
    var breaks = 0         // 0 auto · 1 each section · 2 manual
    /// Manual mode: sections (by index) that start a new page.
    var manualBreaks: Set<Int> = []

    var pageSize: CGSize { paper == 0 ? CGSize(width: 595, height: 842) : CGSize(width: 612, height: 792) }
    var columns: Int { layout == 1 ? 2 : 1 }
    /// Page margins: 40 pt each side, 42 pt top and bottom.
    static let marginX: CGFloat = 40, marginY: CGFloat = 42, columnGap: CGFloat = 24, spacing: CGFloat = 16
    var columnWidth: CGFloat { (pageSize.width - Self.marginX * 2 - (columns == 2 ? Self.columnGap : 0)) / CGFloat(columns) }
    var sectionSpacing: CGFloat { large ? 16 : 12 }
}

/// A section, or the part of one that fits: some of its lines (a section too long for the space continues).
fileprivate struct PrintBlock: Hashable {
    let section: Int
    let lines: Range<Int>
    let continued: Bool
}

/// Lays the whole song out page by page — every line of it — honouring the page-break choice. Sections are measured
/// at the real column width; one that doesn't fit the space left moves to the next column or page, and one taller
/// than a whole column is split between its lines.
@MainActor
fileprivate enum PrintPaginator {
    /// Pages → columns → blocks.
    static func layout(_ song: Song, _ o: PrintOptions) -> [[[PrintBlock]]] {
        let sections = PrintPage.sections(song)
        let width = o.columnWidth
        func measure(_ view: some View, width w: CGFloat = width) -> CGFloat {
            UIHostingController(rootView: view.environment(\.colorScheme, .light))
                .sizeThatFits(in: CGSize(width: w, height: .greatestFiniteMagnitude)).height.rounded(.up)
        }
        let inner = o.pageSize.width - PrintOptions.marginX * 2
        let footer = measure(PrintFooter(page: 0, pageCount: 1), width: inner)
        // Below the columns: a gap, the spacer, a gap and the footer. A little slack for rounding.
        let body = o.pageSize.height - PrintOptions.marginY * 2 - footer - PrintOptions.spacing * 2 - 6
        var top = measure(PrintHeader(song: song, o: o), width: inner) + PrintOptions.spacing
        if o.diagrams { top += measure(PrintDiagrams(song: song), width: inner) + PrintOptions.spacing }
        func capacity(_ page: Int) -> CGFloat { page == 0 ? body - top : body }

        var pages: [[[PrintBlock]]] = [[[]]]
        var used: CGFloat = 0
        var columnEmpty: Bool { pages[pages.count - 1][pages[pages.count - 1].count - 1].isEmpty }
        func nextColumn() {
            if pages[pages.count - 1].count < o.columns { pages[pages.count - 1].append([]) } else { pages.append([[]]) }
            used = 0
        }
        func nextPage() {
            pages.append([[]])
            used = 0
        }
        func place(_ block: PrintBlock, height: CGFloat) {
            used += (columnEmpty ? 0 : o.sectionSpacing) + height
            pages[pages.count - 1][pages[pages.count - 1].count - 1].append(block)
        }

        for (i, section) in sections.enumerated() {
            let pageEmpty = pages[pages.count - 1].allSatisfy(\.isEmpty)
            let forced = i > 0 && (o.breaks == 1 || (o.breaks == 2 && o.manualBreaks.contains(i)))
            if forced && !pageEmpty { nextPage() }

            let all = 0..<section.lines.count
            let full = measure(PrintSectionView(section: section, lines: all, continued: false, o: o))
            let gap: CGFloat = columnEmpty ? 0 : o.sectionSpacing
            if used + gap + full <= capacity(pages.count - 1) {
                place(PrintBlock(section: i, lines: all, continued: false), height: full)
                continue
            }
            if !columnEmpty, full <= capacity(pages.count) {
                nextColumn()
                place(PrintBlock(section: i, lines: all, continued: false), height: full)
                continue
            }
            // Longer than the space: split between lines, continuing in the next column or page.
            let head = measure(PrintSectionView(section: section, lines: 0..<0, continued: false, o: o))
            let lineHeights = section.lines.map { measure(PrintLineView(line: $0, o: o)) }
            var start = 0
            while start < section.lines.count {
                let g: CGFloat = columnEmpty ? 0 : o.sectionSpacing
                var k = 0, height = head
                while start + k < section.lines.count {
                    let next = height + 4 + lineHeights[start + k]
                    if used + g + next > capacity(pages.count - 1) { break }
                    height = next
                    k += 1
                }
                if k == 0 {
                    if columnEmpty { k = 1; height = head + 4 + lineHeights[start] } else { nextColumn(); continue }
                }
                place(PrintBlock(section: i, lines: start..<(start + k), continued: start > 0), height: height)
                start += k
                if start < section.lines.count { nextColumn() }
            }
        }
        // Drop a trailing empty column or page left by a break.
        while pages.count > 1, pages[pages.count - 1].allSatisfy(\.isEmpty) { pages.removeLast() }
        return pages
    }
}

/// One printable page at real PDF point size. Fixed light colours (paper is white).
fileprivate struct PrintPage: View {
    let song: Song
    let o: PrintOptions
    /// This page's columns.
    let columns: [[PrintBlock]]
    let page: Int
    let pageCount: Int

    static func sections(_ song: Song) -> [SongSection] { song.sections.filter { $0.kind != .flow } }

    var body: some View {
        let sections = Self.sections(song)
        VStack(alignment: .leading, spacing: PrintOptions.spacing) {
            if page == 0 { PrintHeader(song: song, o: o) }
            if page == 0 && o.diagrams { PrintDiagrams(song: song) }
            HStack(alignment: .top, spacing: PrintOptions.columnGap) {
                ForEach(0..<o.columns, id: \.self) { c in
                    VStack(alignment: .leading, spacing: o.sectionSpacing) {
                        ForEach(c < columns.count ? columns[c] : [], id: \.self) { block in
                            PrintSectionView(section: sections[block.section], lines: block.lines, continued: block.continued, o: o)
                        }
                    }
                    .frame(width: o.columnWidth, alignment: .topLeading)
                }
            }
            Spacer(minLength: 0)
            PrintFooter(page: page, pageCount: pageCount)
        }
        .padding(.horizontal, PrintOptions.marginX).padding(.vertical, PrintOptions.marginY)
        .frame(width: o.pageSize.width, height: o.pageSize.height, alignment: .topLeading)
        .background(Color.white)
        .environment(\.colorScheme, .light)
        .clipped()
    }

    // Fixed light-mode tag colours (paper).
    static func tagColors(_ k: SectionKind) -> (Color, Color) {
        switch k {
        case .verse: (Color(hex: 0xE3E8F6), Color(hex: 0x1E3A8A))
        case .chorus: (Color(hex: 0xFFB800), Color(hex: 0x171614))
        case .intro, .music: (Color(hex: 0xDDEFEA), Color(hex: 0x0F5B53))
        case .bridge: (Color(hex: 0x6D28D9, alpha: 0.14), Color(hex: 0x6D28D9))
        case .flow: (Color(hex: 0x171614, alpha: 0.05), Color(hex: 0x6B6660))
        }
    }
}

private let printInk = Color(hex: 0x171614)
private let printMuted = Color(hex: 0x6B6660)
private let printGold = Color(hex: 0x8F5E00)

fileprivate struct PrintHeader: View {
    let song: Song
    let o: PrintOptions
    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text(song.title).font(song.isMyanmar ? .myanmar(o.large ? 32 : 24, .bold) : .outfit(o.large ? 32 : 24, .bold)).foregroundStyle(printInk)
                (Text(song.artist).font(.myanmar(11)) + Text(" · Key \(song.key) · 4/4 · \(song.tempo) BPM"))
                    .font(.outfit(11)).foregroundStyle(printMuted)
            }
            Spacer()
            Text("O CHORDS").font(.outfit(9, .heavy)).tracking(1.6).foregroundStyle(printGold)
        }
    }
}

fileprivate struct PrintDiagrams: View {
    let song: Song
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ForEach(Array(song.usedChords.prefix(8)), id: \.self) { c in
                VStack(spacing: 2) {
                    Text(c).font(.mono(11)).foregroundStyle(printInk)
                    if let v = ChordLibrary.info(c)?.voicings.first {
                        ChordDiagram(voicing: v, width: 44, showFingers: false, showStringNames: false)
                    }
                }
            }
        }
    }
}

fileprivate struct PrintFooter: View {
    let page: Int
    let pageCount: Int
    var body: some View {
        Text("Shared on O Chords · \(Date.now.formatted(date: .abbreviated, time: .omitted)) · page \(page + 1) of \(pageCount)")
            .font(.outfit(9)).foregroundStyle(Color(hex: 0x8E8A84))
    }
}

/// A section's tag and some (or all) of its lines; "(cont.)" when it carries on from the previous column or page.
fileprivate struct PrintSectionView: View {
    let section: SongSection
    let lines: Range<Int>
    let continued: Bool
    let o: PrintOptions

    var body: some View {
        let (bg, fg) = PrintPage.tagColors(section.kind)
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text(section.label.uppercased() + (continued ? " (CONT.)" : "")).font(.outfit(8, .heavy)).tracking(0.6)
                    .foregroundStyle(fg)
                    .padding(.horizontal, 5).padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 4).fill(bg))
                if !continued, let n = section.note { Text(n).font(.outfit(8)).foregroundStyle(printMuted) }
            }
            if !continued, !section.bars.isEmpty {
                Text("| " + section.bars.joined(separator: "  |  ") + " |").font(.mono(o.large ? 13 : 10)).foregroundStyle(printGold)
            }
            ForEach(Array(lines), id: \.self) { i in PrintLineView(line: section.lines[i], o: o) }
        }
    }
}

fileprivate struct PrintLineView: View {
    let line: String
    let o: PrintOptions
    var body: some View {
        if o.lyrics {
            ToolsFlowLayout(spacing: 0, lineSpacing: 0) {
                ForEach(Array(ChordLine.parse(line).enumerated()), id: \.offset) { _, seg in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(seg.chord ?? " ").font(.mono(o.large ? 12 : 9)).foregroundStyle(printGold)
                        Text(seg.text.isEmpty ? " " : seg.text).font(.myanmar(o.large ? 14 : 10.5)).foregroundStyle(printInk)
                    }
                    .padding(.trailing, seg.trailingSpace ? 4 : 0)
                }
            }
        } else {
            Text(ChordLine.chords(in: line).joined(separator: "    ")).font(.mono(o.large ? 13 : 10)).foregroundStyle(printGold)
        }
    }
}

struct PrintPdfView: View {
    let song: Song

    @Environment(AppState.self) private var app
    @State private var o = PrintOptions()
    @State private var previewPage = 0
    @State private var share: ToolsShareItem?
    @State private var toast: Toast?
    /// The laid-out pages (columns of blocks), redone whenever an option changes.
    @State private var pages: [[[PrintBlock]]] = [[[]]]

    private var pageCount: Int { max(1, pages.count) }
    private func columns(_ page: Int) -> [[PrintBlock]] { pages.indices.contains(page) ? pages[page] : [] }
    private var sectionLabels: [String] { PrintPage.sections(song).map { $0.label.isEmpty ? $0.kind.rawValue.capitalized : $0.label } }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Print or PDF", backLabel: "Back to song")
            ScrollView {
                VStack(spacing: 16) {
                    preview.padding(.top, 12)
                    VStack(spacing: 10) {
                        segRow("Paper", options: ["A4", "US Letter"], selection: $o.paper)
                        segRow("Layout", options: ["1 column", "2 columns"], selection: $o.layout)
                        segRow("Breaks", options: ["Auto", "Each section", "Manual"], selection: $o.breaks)
                        if o.breaks == 2 {
                            GlassList {
                                ForEach(Array(sectionLabels.enumerated().dropFirst()), id: \.offset) { i, label in
                                    toggleRow("New page before \(label.uppercased())", Binding(
                                        get: { o.manualBreaks.contains(i) },
                                        set: { on in if on { o.manualBreaks.insert(i) } else { o.manualBreaks.remove(i) } }
                                    ))
                                }
                            }
                        }
                        GlassList {
                            toggleRow("Chord diagrams at the top", $o.diagrams)
                            toggleRow("Include lyrics", $o.lyrics)
                            toggleRow("Large print", $o.large)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            HStack(spacing: 10) {
                Button(action: printPDF) { Label("Print", systemImage: "printer") }.buttonStyle(.ink)
                Button(action: sharePDF) { Label("Share PDF", systemImage: "square.and.arrow.up") }.buttonStyle(.gold)
            }
            .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 10)
        }
        .ocPage()
        .toast($toast)
        .onAppear {
            #if DEBUG
            // -demoPrintBreaks N / -demoPrintLayout N / -demoPrintLarge YES set the options; -demoPrintExport YES
            // writes the PDF to tmp/print.pdf (checking pagination on the simulator).
            let d = UserDefaults.standard
            if d.object(forKey: "demoPrintBreaks") != nil { o.breaks = d.integer(forKey: "demoPrintBreaks") }
            if d.object(forKey: "demoPrintLayout") != nil { o.layout = d.integer(forKey: "demoPrintLayout") }
            if d.bool(forKey: "demoPrintLarge") { o.large = true }
            #endif
            pages = PrintPaginator.layout(song, o)
            #if DEBUG
            if d.bool(forKey: "demoPrintExport"), let url = makePDF() {
                try? FileManager.default.removeItem(at: URL.temporaryDirectory.appending(path: "print.pdf"))
                try? FileManager.default.copyItem(at: url, to: URL.temporaryDirectory.appending(path: "print.pdf"))
            }
            #endif
        }
        .onChange(of: o) { _, new in pages = PrintPaginator.layout(song, new) }
        .onChange(of: pageCount) { _, n in if previewPage >= n { previewPage = 0 } }
        .sheet(item: $share) { ToolsActivityView(items: $0.items).presentationDetents([.medium, .large]) }
    }

    private var preview: some View {
        let size = o.pageSize
        let pw: CGFloat = o.paper == 0 ? 232 : 240
        let ph = pw * size.height / size.width
        return VStack(spacing: 8) {
            Button {
                if pageCount > 1 { withAnimation(.snappy) { previewPage = (previewPage + 1) % pageCount } }
            } label: {
                PrintPage(song: song, o: o, columns: columns(previewPage), page: previewPage, pageCount: pageCount)
                    .scaleEffect(pw / size.width, anchor: .topLeading)
                    .frame(width: pw, height: ph, alignment: .topLeading)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .shadow(color: OC.inkFixed.opacity(0.16), radius: 20, y: 18)
                    .shadow(color: OC.inkFixed.opacity(0.08), radius: 3, y: 2)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Page preview, page \(previewPage + 1) of \(pageCount)")
            if pageCount > 1 {
                Text("Page \(previewPage + 1) of \(pageCount) · tap to flip").font(.outfit(12)).foregroundStyle(OC.muted)
            }
        }
        .animation(.snappy, value: o)
    }

    private func segRow(_ label: String, options: [String], selection: Binding<Int>) -> some View {
        HStack(spacing: 10) {
            Text(label).font(.outfit(13, .bold)).foregroundStyle(OC.muted).frame(width: 64, alignment: .leading)
            Segmented(options: Array(options.indices), selection: selection) { options[$0] }
        }
    }

    private func toggleRow(_ title: String, _ b: Binding<Bool>) -> some View {
        Toggle(isOn: b) { Text(title).font(.outfit(15, .medium)).foregroundStyle(OC.ink) }
            .toggleStyle(.oc)
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .accessibilityLabel(title)
    }

    @MainActor private func makePDF() -> URL? {
        let name = "O Chords – \(song.title)".replacingOccurrences(of: "/", with: "-")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(name).pdf")
        var box = CGRect(origin: .zero, size: o.pageSize)
        guard let ctx = CGContext(url as CFURL, mediaBox: &box, nil) else { return nil }
        for p in 0..<pageCount {
            let r = ImageRenderer(content: PrintPage(song: song, o: o, columns: columns(p), page: p, pageCount: pageCount))
            r.proposedSize = ProposedViewSize(o.pageSize)
            r.render { _, draw in
                ctx.beginPDFPage(nil)
                draw(ctx)
                ctx.endPDFPage()
            }
        }
        ctx.closePDF()
        return url
    }

    private func sharePDF() {
        guard let url = makePDF() else { toast = Toast(text: "Couldn’t create the PDF", systemImage: "exclamationmark.triangle.fill"); return }
        share = ToolsShareItem(items: [url])
    }

    private func printPDF() {
        guard let url = makePDF() else { toast = Toast(text: "Couldn’t create the PDF", systemImage: "exclamationmark.triangle.fill"); return }
        let info = UIPrintInfo(dictionary: nil)
        info.outputType = .general
        info.jobName = song.title
        let pc = UIPrintInteractionController.shared
        pc.printInfo = info
        pc.printingItem = url
        pc.present(animated: true)
    }
}

// MARK: - 43 · Report a sheet (+ sent)

struct ReportView: View {
    let song: Song

    @Environment(AppState.self) private var app
    @State private var reason: Int?
    @State private var detail = ""
    @State private var sending = false
    @State private var sent = false
    @State private var error: String?

    private static let reasons: [(String, String)] = [
        ("Wrong chords or lyrics", "Chords don’t match the song, or lines are missing"),
        ("Copyright", "You own this song and didn’t allow it to be shared"),
        ("Spam or advertising", "Not a real chord sheet"),
        ("Offensive content", "Hate, harassment or adult content"),
        ("Something else", "Tell us below")
    ]

    var body: some View {
        VStack(spacing: 0) {
            ToolsBar(title: "Report", systemImage: "xmark", label: "Close") { app.pop() }
            if sent {
                sentView.transition(.opacity.combined(with: .scale(scale: 0.98)))
            } else {
                formView.transition(.opacity)
            }
        }
        .ocPage()
        .animation(.snappy, value: sent)
    }

    // MARK: Form

    private var formView: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    songCard
                    Text("What’s wrong with this sheet?").font(.outfit(24, .heavy)).tracking(-0.5).foregroundStyle(OC.ink)
                    VStack(spacing: 8) {
                        ForEach(Array(Self.reasons.enumerated()), id: \.offset) { i, r in reasonRow(i, r) }
                    }
                    .accessibilityElement(children: .contain)
                    if reason == 0 {
                        Button {
                            app.push(.review(ScanResult(song: song, detectedZawgyi: false, lowConfidence: [])))
                        } label: {
                            Text("Faster: suggest a fix instead — the uploader gets it straight away ›")
                                .font(.outfit(13, .bold)).foregroundStyle(OC.tealText)
                                .multilineTextAlignment(.leading)
                                .padding(.horizontal, 12).padding(.vertical, 10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(OC.tealTint))
                        }
                        .buttonStyle(.plain)
                        .transition(.opacity)
                    }
                    TextField("", text: $detail, prompt: Text("Anything else we should know? (optional)").foregroundStyle(OC.muted), axis: .vertical)
                        .lineLimit(3...6)
                        .font(.outfit(15))
                        .foregroundStyle(OC.ink)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .frame(minHeight: 70, alignment: .topLeading)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(OC.surface))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(OC.border))
                    if let error {
                        Text(error).font(.outfit(13, .semibold)).foregroundStyle(OC.red)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 12)
                .animation(.snappy, value: reason)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            VStack(spacing: 6) {
                sendButton
                Text("Reports are private. The uploader won’t see your name.")
                    .font(.outfit(12)).foregroundStyle(OC.muted).multilineTextAlignment(.center)
            }
            .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10)
        }
    }

    private var songCard: some View {
        HStack(spacing: 12) {
            Text(song.key).font(.mono(17)).foregroundStyle(OC.goldText)
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(OC.goldTint))
            VStack(alignment: .leading, spacing: 0) {
                Text(song.title).font(song.isMyanmar ? .myanmar(16, .bold) : .outfit(16, .semibold)).foregroundStyle(OC.ink)
                Text("Shared by \(song.uploader?.name ?? "[uploader]") · public sheet").font(.outfit(12)).foregroundStyle(OC.muted)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .glass(radius: 16)
    }

    private func reasonRow(_ i: Int, _ r: (String, String)) -> some View {
        let on = reason == i
        return Button { reason = i; error = nil } label: {
            HStack(spacing: 12) {
                Circle().strokeBorder(on ? OC.ink : OC.switchOff, lineWidth: 2)
                    .frame(width: 22, height: 22)
                    .overlay(Circle().fill(on ? OC.ink : .clear).frame(width: 10, height: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(r.0).font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                    Text(r.1).font(.outfit(12)).foregroundStyle(OC.muted).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(on ? OC.goldTint : OC.glass))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? OC.gold : OC.border, lineWidth: on ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? [.isSelected] : [])
        .accessibilityHint(r.1)
    }

    @ViewBuilder private var sendButton: some View {
        if reason != nil {
            Button(action: send) {
                if sending { ProgressView().tint(OC.inkFixed) } else { Text("Send report") }
            }
            .buttonStyle(.gold)
            .disabled(sending)
        } else {
            Text("Send report")
                .font(.outfit(16, .bold)).foregroundStyle(OC.faint)
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.softFill))
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Choose a reason first")
        }
    }

    private func send() {
        guard let reason, !sending else { return }
        // Reports carry the reporter's account, so moderators can follow up and to deter abuse.
        guard app.isSignedIn || app.usesDemoData else {
            error = "Log in or create an account to report a sheet."
            return
        }
        sending = true
        error = nil
        Task { @MainActor in
            do {
                try await app.backend.report(songID: song.id, reason: Self.reasons[reason].0, detail: detail)
                sent = true
            } catch {
                self.error = "Couldn’t send the report. Try again."
            }
            sending = false
        }
    }

    // MARK: Sent

    private var sentView: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 22) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 30, weight: .bold))
                        .foregroundStyle(OC.inkFixed)
                        .frame(width: 96, height: 96)
                        .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                        .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
                        .accessibilityHidden(true)
                    VStack(spacing: 8) {
                        Text("Thanks — we’ll take a look").font(.outfit(26, .bold)).tracking(-0.6).foregroundStyle(OC.ink)
                        Text("Our team reviews reports within [48 hours]. We’ll let you know in Notifications.")
                            .font(.outfit(15)).foregroundStyle(OC.muted).lineSpacing(3)
                    }
                    .multilineTextAlignment(.center)
                    VStack(spacing: 12) {
                        stepRow("✓", "Received", "Now", done: true)
                        stepRow("2", "Reviewed by our team", "≤ [48 h]", done: false)
                        stepRow("3", "Decision sent to you", "", done: false)
                    }
                    .padding(14)
                    .glass(radius: 18)
                    if let reason {
                        (Text("Reported: ") + Text(Self.reasons[reason].0).bold().foregroundColor(OC.ink))
                            .font(.outfit(13)).foregroundStyle(OC.muted)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 40)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
            .defaultScrollAnchor(.center)
            Button("Done") { app.pop() }
                .buttonStyle(.ink)
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10)
        }
    }

    private func stepRow(_ i: String, _ t: String, _ when: String, done: Bool) -> some View {
        HStack(spacing: 12) {
            Text(i).font(.outfit(12, .heavy)).foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(Circle().fill(done ? OC.teal : Color(hex: 0xC9C6C0)))
            Text(t).font(.outfit(15, done ? .bold : .medium)).foregroundStyle(done ? OC.ink : OC.muted)
            Spacer(minLength: 6)
            Text(when).font(.outfit(12)).foregroundStyle(OC.muted)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - 22 · Jam room (synced)

fileprivate struct JamMember: Identifiable {
    let id: Int
    let initial: String
    let name: String
    let role: String
    var status: String
    let bg: Color
    let fg: Color
    var statusColor: Color
}

struct JamView: View {
    let song: Song

    @Environment(AppState.self) private var app
    @State private var t = 0
    @State private var sec: Int
    @State private var bar = 0
    @State private var playing = false
    @State private var members: [JamMember] = [
        JamMember(id: 0, initial: "Y", name: "You", role: "Leader · guitar · chords view", status: "Leading", bg: OC.gold, fg: OC.inkFixed, statusColor: OC.gold),
        JamMember(id: 1, initial: "?", name: "[username]", role: "Vocals · lyrics only", status: "Synced", bg: Color(hex: 0x1E3A8A), fg: .white, statusColor: ToolsNight.tealText),
        JamMember(id: 2, initial: "?", name: "[username]", role: "Bass · root notes view", status: "Synced", bg: Color(hex: 0x0F766E), fg: .white, statusColor: ToolsNight.tealText),
        JamMember(id: 3, initial: "?", name: "[username]", role: "Keys · chords view", status: "Joining…", bg: Color(hex: 0x6D28D9), fg: .white, statusColor: OC.nightMuted)
    ]

    private static let roomCode = "482 913"

    init(song: Song) {
        self.song = song
        let n = song.sections.filter { $0.kind != .flow }.count
        _sec = State(initialValue: min(2, max(0, n - 1)))
    }

    private var sections: [SongSection] { song.sections.filter { $0.kind != .flow } }
    private var current: SongSection? { sections.indices.contains(sec) ? sections[sec] : nil }

    private func chords(_ s: SongSection) -> [String] {
        let raw = s.bars.isEmpty ? s.lines.flatMap(ChordLine.chords(in:)) : s.bars
        var bars = Array(raw.prefix(8))
        // A repeated 4-bar phrase is shown once (design p27 shows one row of four).
        if bars.count == 8, Array(bars[0..<4]) == Array(bars[4..<8]) { bars = Array(bars[0..<4]) }
        return bars.map { ChordTheory.transpose($0, by: t) }
    }

    private var keyName: String { ChordTheory.transposeRoot(song.key, by: t) }
    private var allSynced: Bool { !members.contains { $0.status == "Joining…" } }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    roomCard
                    nowPlaying
                    if let current { sectionCard(current) }
                    people
                }
                .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 20)
            }
            .scrollIndicators(.hidden)
            bottomBar
        }
        .foregroundStyle(OC.nightText)
        .background(NightBackground())
        .toolbar(.hidden, for: .navigationBar)
        .task {
            try? await Task.sleep(for: .seconds(3))
            withAnimation(.snappy) {
                members[3].status = "Synced"
                members[3].statusColor = ToolsNight.tealText
            }
        }
        .task(id: playing) {
            guard playing else { return }
            let barSeconds = 4 * 60.0 / Double(max(40, song.tempo))
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(barSeconds))
                if Task.isCancelled { break }
                advanceBar()
            }
        }
    }

    private func advanceBar() {
        guard let current else { return }
        let n = max(1, chords(current).count)
        withAnimation(.snappy) {
            if bar + 1 < n {
                bar += 1
            } else if sec + 1 < sections.count {
                sec += 1; bar = 0
            } else {
                bar = 0; playing = false
            }
        }
    }

    private var header: some View {
        HStack {
            Button("Leave") { app.pop() }
                .font(.outfit(15, .bold)).foregroundStyle(OC.gold)
                .padding(.horizontal, 10).frame(minWidth: 44, minHeight: 44)
                .buttonStyle(.plain)
            Spacer()
            VStack(spacing: 0) {
                Text("Jam room").font(.outfit(16, .bold))
                HStack(spacing: 5) {
                    Circle().fill(allSynced ? ToolsNight.tealText : OC.gold).frame(width: 7, height: 7)
                    Text("\(members.count) in room · \(allSynced ? "all synced" : "syncing…")")
                }
                .font(.outfit(12)).foregroundStyle(allSynced ? ToolsNight.tealText : OC.gold)
            }
            .accessibilityElement(children: .combine)
            Spacer()
            ShareLink(item: "Join my O Chords jam room — code \(Self.roomCode)") {
                Image(systemName: "person.badge.plus").font(.system(size: 19, weight: .medium))
                    .foregroundStyle(OC.nightText)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Invite")
            .padding(.trailing, 6)
        }
        .padding(.horizontal, 4)
        .frame(height: 50)
    }

    private var roomCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                ToolsSectionLabel(text: "ROOM CODE", color: OC.nightMuted)
                Text(Self.roomCode).font(.mono(26)).tracking(3).foregroundStyle(OC.gold)
            }
            Spacer()
            HStack(spacing: -8) {
                ForEach(members) { m in
                    Text(m.initial).font(.outfit(13, .heavy)).foregroundStyle(m.fg)
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(m.bg))
                        .overlay(Circle().strokeBorder(ToolsNight.bar, lineWidth: 2))
                }
            }
            .accessibilityHidden(true)
        }
        .padding(14)
        .nightGlass(radius: 20)
    }

    private var nowPlaying: some View {
        VStack(alignment: .leading, spacing: 4) {
            ToolsSectionLabel(text: "NOW PLAYING · YOU LEAD", color: OC.nightMuted)
            HStack {
                Text(song.title).font(song.isMyanmar ? .myanmar(24, .bold) : .outfit(24, .bold)).lineLimit(1)
                Spacer(minLength: 8)
                HStack(spacing: 2) {
                    keyButton("−", "Key down for everyone") { t = (t + 11) % 12 }
                    VStack(spacing: 0) {
                        Text("KEY").font(.outfit(9, .bold)).tracking(1).foregroundStyle(OC.nightMuted)
                        Text(keyName).font(.mono(16)).foregroundStyle(OC.gold).contentTransition(.numericText())
                    }
                    .frame(minWidth: 44)
                    keyButton("+", "Key up for everyone") { t = (t + 1) % 12 }
                }
                .padding(2)
                .nightGlass(radius: 20)
            }
            Text("Key, section and scroll changes reach everyone in the room.").font(.outfit(12)).foregroundStyle(OC.nightMuted)
        }
    }

    private func keyButton(_ s: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy) { action() } } label: {
            Text(s).font(.system(size: 20)).foregroundStyle(OC.nightText).frame(width: 40, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func sectionCard(_ s: SongSection) -> some View {
        let cs = chords(s)
        let nextLabel = sections.isEmpty ? "" : sections[(sec + 1) % sections.count].label.capitalized
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(s.label.uppercased()) · \(allSynced ? "EVERYONE IS HERE" : "SYNCING")").font(.outfit(12, .heavy)).tracking(1.4).foregroundStyle(OC.gold)
                Spacer()
                Text("next · \(nextLabel)").font(.outfit(12)).foregroundStyle(OC.nightMuted)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
                ForEach(Array(cs.enumerated()), id: \.offset) { i, c in
                    let live = i == bar
                    Text(c).font(.mono(18)).lineLimit(1).minimumScaleFactor(0.6)
                        .foregroundStyle(live ? OC.inkFixed : OC.nightText)
                        .frame(maxWidth: .infinity).frame(height: 58)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(live ? OC.gold : ToolsNight.cell))
                        .scaleEffect(live && playing ? 1.04 : 1)
                }
            }
            HStack(spacing: 8) {
                Button {
                    withAnimation(.snappy) { sec = max(0, sec - 1); bar = 0 }
                } label: {
                    Text("‹ Previous").font(.outfit(14, .bold)).foregroundStyle(OC.nightText)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(ToolsNight.border))
                }
                .buttonStyle(.plain)
                Button("Next section ›") {
                    withAnimation(.snappy) { sec = min(sections.count - 1, sec + 1); bar = 0 }
                }
                .font(.outfit(14, .bold))
                .buttonStyle(.gold(height: 44))
            }
        }
        .padding(18)
        .nightGlass(radius: 20)
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(OC.gold))
    }

    private var people: some View {
        VStack(alignment: .leading, spacing: 8) {
            ToolsSectionLabel(text: "IN THE ROOM", color: OC.nightMuted)
            ForEach(members) { m in
                HStack(spacing: 12) {
                    Text(m.initial).font(.outfit(13, .heavy)).foregroundStyle(m.fg)
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(m.bg))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(m.name).font(.outfit(14, .bold))
                        Text(m.role).font(.outfit(12)).foregroundStyle(OC.nightMuted)
                    }
                    Spacer(minLength: 6)
                    Text(m.status).font(.outfit(12, .bold)).foregroundStyle(m.statusColor)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .frame(minHeight: 56)
                .nightGlass(radius: 20)
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            Button { playing.toggle() } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(OC.inkFixed)
                    .frame(width: 58, height: 58)
                    .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                    .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(playing ? "Pause synced scroll" : "Start synced scroll")
            VStack(alignment: .leading, spacing: 2) {
                Text(playing ? "Scrolling for everyone" : "Start synced scroll").font(.outfit(15, .bold))
                Text("\(song.tempo) BPM · count-in 1 bar").font(.mono(12, .semibold)).foregroundStyle(OC.nightMuted)
            }
            Spacer(minLength: 6)
            Text("PRO HOST").font(.outfit(11, .heavy)).tracking(1).foregroundStyle(OC.inkFixed)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 8).fill(OC.gold))
        }
        .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 10)
        .background(Color.white.opacity(0.06).ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1) }
    }
}

// MARK: - 6 · Guitar tuner (tab root)

fileprivate struct TunerPreset {
    let name: String
    let notes: [(note: String, octave: Int, hz: Double)]
    static let all: [TunerPreset] = [
        TunerPreset(name: "Standard", notes: [("E", 2, 82.41), ("A", 2, 110.0), ("D", 3, 146.83), ("G", 3, 196.0), ("B", 3, 246.94), ("E", 4, 329.63)]),
        TunerPreset(name: "Drop D", notes: [("D", 2, 73.42), ("A", 2, 110.0), ("D", 3, 146.83), ("G", 3, 196.0), ("B", 3, 246.94), ("E", 4, 329.63)]),
        TunerPreset(name: "½ step ↓", notes: [("E♭", 2, 77.78), ("A♭", 2, 103.83), ("D♭", 3, 138.59), ("G♭", 3, 185.0), ("B♭", 3, 233.08), ("E♭", 4, 311.13)]),
        TunerPreset(name: "Open G", notes: [("D", 2, 73.42), ("G", 2, 98.0), ("D", 3, 146.83), ("G", 3, 196.0), ("B", 3, 246.94), ("D", 4, 293.66)])
    ]
}

struct TunerView: View {
    @Environment(AppState.self) private var app
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @State private var preset = 0
    @State private var a4 = 440
    /// Which string is showing, how far off it is and which strings are done (see `TuningSession`).
    @State private var session = TuningSession(targets: TunerPreset.all[0].notes.map(\.hz))
    @State private var detector = PitchDetector()
    @State private var micOff = false
    @State private var trail = TunerTrail()
    @State private var chime = TunerChime()
    @AppStorage("tuner.sounds") private var sounds = true
    /// Goes up each time a string comes into tune (drives the ring burst).
    @State private var tunedPulse = 0
    /// When all six strings were last tuned (drives the celebration), nil when it's not showing.
    @State private var celebratedAt: Date?

    private static let tolerance = TuningSession.tolerance

    private var notes: [(note: String, octave: Int, hz: Double)] { TunerPreset.all[preset].notes }
    private var sel: Int { session.selected }
    private var auto: Bool { session.auto }
    private var tuned: Set<Int> { session.tuned }
    private var cents: [Double?] { session.cents }
    private var c: Double { max(-50, min(50, cents[sel] ?? 0)) }
    private var playing: Bool { session.isPlaying }
    private var inTune: Bool { playing && abs(c) <= Self.tolerance }
    private var hz: Double { session.heard ?? target(sel) }
    private var tunedCount: Int { tuned.count }

    private func target(_ i: Int) -> Double { notes[i].hz * Double(a4) / 440 }
    private var targets: [Double] { notes.indices.map(target) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                if micOff { micOffCard.padding(.top, 14) }
                presets.padding(.top, 14)
                TunerTrace(trail: trail, note: notes[sel].note, octave: notes[sel].octave,
                           cents: cents[sel].map { max(-50, min(50, $0)) }, live: playing, tolerance: Self.tolerance,
                           isTuned: tuned.contains(sel), pulse: tunedPulse)
                    .glass()
                    .overlay { if let celebratedAt { TunerCelebration(start: celebratedAt) } }
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.top, 14)
                statusRow.padding(.top, 12)
                strings.padding(.top, 18)
                footer.padding(.top, 2)
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .padding(.bottom, 12)
        }
        .scrollIndicators(.hidden)
        .tabBarInset()
        .ocPage()
        // Every tab is built at launch, so wait until the Tuner tab is actually opened before asking for the mic.
        .onAppear {
            if app.tab == .tuner { ToolsMicPrimer.showIfNeeded(app) }
            #if DEBUG
            // -demoTunedStrings N starts with the first N strings ticked (to try the all-tuned celebration).
            let demoTuned = UserDefaults.standard.integer(forKey: "demoTunedStrings")
            if demoTuned > 0 { session.markTuned(Set(0..<min(demoTuned, notes.count))) }
            #endif
            updateListening()
        }
        .onChange(of: app.tab) { _, tab in if tab == .tuner { ToolsMicPrimer.showIfNeeded(app) }; updateListening() }
        .onChange(of: app.modal) { _, _ in updateListening() }
        .onChange(of: scenePhase) { _, _ in updateListening() }
        .onChange(of: session.auto) { _, _ in syncTarget() }
        .onChange(of: session.selected) { _, _ in syncTarget() }
        .onChange(of: preset) { _, _ in retune() }
        .onChange(of: a4) { _, _ in retune() }
        .onDisappear { detector.stop() }
    }

    // MARK: Listening

    /// The mic runs only while the Tuner tab is on screen, the app is in front and no sheet covers it.
    private func updateListening() {
        let wanted = app.tab == .tuner && scenePhase == .active && app.modal == nil
        guard wanted else { chime.stop(); detector.stop(); return }
        guard !detector.isRunning else { return }
        guard PitchDetector.hasPermission else {
            micOff = !PitchDetector.permissionUndecided
            return
        }
        micOff = false
        syncTarget()
        detector.onReading = { hear($0) }
        do { try detector.start() } catch { micOff = true }
    }

    /// With a string picked by hand, the detector listens only around that string's note.
    private func syncTarget() { detector.setTarget(auto ? nil : target(sel)) }

    /// A new tuning or reference pitch: every string needs checking again.
    private func retune() {
        session.reset(targets: targets)
        celebratedAt = nil
        trail.clear()
        syncTarget()
    }

    private func hear(_ reading: PitchDetector.Reading?) {
        var next = session
        let wasPlaying = next.isPlaying
        let events = next.hear(reading?.hz, at: Date.timeIntervalSinceReferenceDate)
        // Ticks pop with a spring; a string switch slides; plain readings aren't animated (the bubble follows directly).
        if events.isEmpty {
            session = next
        } else {
            withAnimation(.spring(duration: 0.45, bounce: 0.4)) { session = next }
        }
        if events.contains(where: { if case .switched = $0 { return true }; return false }) { trail.clear() }
        if next.isPlaying {
            trail.add(next.cents[next.selected])
        } else if wasPlaying {
            trail.add(nil)
        }
        for event in events {
            if case .tuned(_, let all) = event { stringTuned(all: all) }
        }
    }

    /// A string just came into tune: ding, ring burst and a tap — or, for the last string, the full celebration.
    private func stringTuned(all: Bool) {
        tunedPulse += 1
        if sounds {
            let kind: TunerChime.Kind = all ? .allStrings : .string
            detector.ignore(for: kind.duration)
            chime.play(kind)
        }
        if all {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            let now = Date.now
            withAnimation(.spring(duration: 0.5, bounce: 0.4)) { celebratedAt = now }
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(TunerCelebration.duration))
                if celebratedAt == now { withAnimation(.easeOut(duration: 0.35)) { celebratedAt = nil } }
            }
        } else {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }

    private var micOffCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "mic.slash").font(.system(size: 18, weight: .semibold)).foregroundStyle(OC.red)
                .frame(width: 40, height: 40).background(Circle().fill(OC.redTint))
            VStack(alignment: .leading, spacing: 2) {
                Text("Microphone is off for O Chords").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                Text("The tuner needs it to hear your strings. Sound stays on your phone.").font(.outfit(12)).foregroundStyle(OC.muted)
            }
            Spacer(minLength: 4)
            Button("Settings") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
        }
        .padding(12)
        .glass(radius: 16)
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Tuner").font(.outfit(36, .heavy)).tracking(-1).foregroundStyle(OC.ink)
                Text(micOff ? "Microphone needed" : auto ? "Listening — pluck any string" : "Play the \(notes[sel].note) string")
                    .font(.outfit(13)).foregroundStyle(OC.muted)
            }
            Spacer()
            Button { sounds.toggle() } label: {
                Image(systemName: sounds ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(sounds ? OC.ink : OC.muted)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(OC.glass))
                    .overlay(Circle().strokeBorder(OC.border))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(sounds ? "Tuning sounds on" : "Tuning sounds off")
            Button { withAnimation(.snappy) { session.auto.toggle() } } label: {
                HStack(spacing: 8) {
                    Image(systemName: "mic").font(.system(size: 14, weight: .bold))
                    Text(auto ? "Auto" : "Manual").font(.outfit(14, .bold))
                }
                .foregroundStyle(auto ? OC.background : OC.ink)
                .padding(.horizontal, 14).frame(height: 44)
                .background(Capsule().fill(auto ? OC.ink : OC.glass))
                .overlay(Capsule().strokeBorder(auto ? OC.ink : OC.border))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Auto detect string")
            .accessibilityAddTraits(auto ? .isSelected : [])
        }
    }

    private var presets: some View {
        HStack(spacing: 6) {
            ForEach(Array(TunerPreset.all.enumerated()), id: \.offset) { i, p in
                let on = i == preset
                Button {
                    preset = i
                } label: {
                    Text(p.name).font(.outfit(12, .bold)).lineLimit(1).minimumScaleFactor(0.8)
                        .foregroundStyle(on ? OC.background : OC.ink)
                        .padding(.horizontal, 4)
                        .frame(maxWidth: .infinity).frame(height: 36)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.ink : OC.glass))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.ink : OC.border))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private var statusRow: some View {
        HStack {
            Text(!playing ? (cents[sel] == nil ? "Pluck a string" : "Pluck again") : inTune ? "In tune" : c < 0 ? "Too low · tighten ↑" : "Too high · loosen ↓")
                .font(.outfit(14, .heavy))
                .foregroundStyle(!playing ? OC.muted : inTune ? OC.tealText : OC.goldText)
                .padding(.horizontal, 12).frame(height: 34)
                .background(Capsule().fill(!playing ? OC.softFill : inTune ? OC.tealTint : OC.goldTint))
                .contentTransition(.opacity)
            Spacer()
            HStack(spacing: 14) {
                // The true offset (the bubble stops at ±50 ¢, the number doesn't).
                Text(cents[sel].map { off in (off.rounded() > 0 ? "+" : off.rounded() < 0 ? "−" : "±") + "\(Int(abs(off).rounded()))¢" } ?? "–")
                    .foregroundStyle(playing ? OC.ink : OC.muted)
                Text(String(format: "%.1f Hz", hz)).foregroundStyle(OC.muted)
            }
            .font(.mono(13))
            .monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }

    private var strings: some View {
        HStack(spacing: 18) {
            VStack(spacing: 14) { ForEach([2, 1, 0], id: \.self) { stringButton($0) } }
            TunerHeadstock().frame(width: 120, height: 230).accessibilityHidden(true)
            VStack(spacing: 14) { ForEach([3, 4, 5], id: \.self) { stringButton($0) } }
        }
        .frame(maxWidth: .infinity)
    }

    private func stringButton(_ i: Int) -> some View {
        let n = notes[i]
        let on = i == sel
        let tuned = tuned.contains(i)
        return Button {
            withAnimation(.snappy) { session.select(i) }
            trail.clear()
        } label: {
            Text(n.note).font(.outfit(20, .heavy)).foregroundStyle(on ? OC.inkFixed : OC.ink)
                .frame(width: 58, height: 58)
                .background(Circle().fill(on ? OC.gold : OC.glass))
                .overlay(Circle().strokeBorder(on ? OC.gold : OC.border, lineWidth: 2))
                .overlay(alignment: .topTrailing) {
                    if tuned {
                        Image(systemName: "checkmark").font(.system(size: 8, weight: .black)).foregroundStyle(.white)
                            .frame(width: 18, height: 18)
                            .background(Circle().fill(OC.teal))
                            .overlay(Circle().strokeBorder(OC.paper, lineWidth: 2))
                            .offset(x: 2, y: -2)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("String \(6 - i), \(n.note)\(n.octave)\(tuned ? ", tuned" : "")")
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var footer: some View {
        HStack {
            HStack(spacing: 4) {
                Text("A4 =").foregroundStyle(OC.muted)
                a4Button("−", "Lower reference pitch") { a4 = max(430, a4 - 1) }
                Text("\(a4) Hz").font(.mono(13)).foregroundStyle(OC.ink).monospacedDigit()
                a4Button("+", "Raise reference pitch") { a4 = min(450, a4 + 1) }
            }
            Spacer()
            Text("\(tunedCount) / 6 tuned").font(.outfit(13, .bold)).foregroundStyle(OC.tealText)
        }
        .font(.outfit(13))
    }

    private func a4Button(_ s: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(s).font(.system(size: 16)).foregroundStyle(OC.ink)
                .frame(width: 36, height: 36)
                .glass(radius: 14)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Recent tuner readings for the pitch trail, newest last. A class so a reading redraws only the trail.
@Observable
fileprivate final class TunerTrail {
    struct Point {
        var time: TimeInterval
        /// Offset from the target string in cents; nil marks a break (the note stopped).
        var cents: Double?
    }

    private(set) var points: [Point] = []
    /// How much history the trail shows.
    static let span: TimeInterval = 2.5

    func add(_ cents: Double?) {
        let now = Date.timeIntervalSinceReferenceDate
        points.append(Point(time: now, cents: cents))
        if let first = points.firstIndex(where: { now - $0.time < Self.span }), first > 0 { points.removeFirst(first) }
    }

    func clear() { points.removeAll() }
}

/// Live pitch trail: the note bubble slides towards ♭ or ♯ as the string is off, and the line under it shows where
/// the pitch has been over the last couple of seconds — so turning the peg the wrong way is obvious at a glance.
fileprivate struct TunerTrace: View {
    let trail: TunerTrail
    let note: String
    let octave: Int
    /// Latest offset in cents (nil before the string is first heard).
    let cents: Double?
    /// The reading is current; otherwise it's the last one, shown greyed until the string is plucked again.
    let live: Bool
    let tolerance: Double
    /// This string has been tuned (the bubble shows a tick).
    let isTuned: Bool
    /// Changes each time a string comes into tune: a ring bursts out of the bubble.
    let pulse: Int

    private static let range = 50.0
    private static let bubble: CGFloat = 64
    private static let top: CGFloat = 34
    /// Points the trail scrolls down per second.
    private static let speed: CGFloat = 80

    private var inTune: Bool { live && (cents.map { abs($0) <= tolerance } ?? false) }
    private var tint: Color { !live ? OC.muted : inTune ? OC.teal : OC.gold }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let center = w / 2
            let half = w / 2 - Self.bubble / 2 - 8
            let x = { (cents: Double) in center + CGFloat(max(-1, min(1, cents / Self.range))) * half }
            let bandWidth = max(12, CGFloat(tolerance / Self.range) * half * 2)
            ZStack(alignment: .topLeading) {
                // In-tune band and the centre line.
                Rectangle().fill(OC.tealTint)
                    .frame(width: bandWidth)
                    .position(x: center, y: geo.size.height / 2)
                Rectangle().fill(OC.teal.opacity(0.5)).frame(width: 1.5)
                    .position(x: center, y: geo.size.height / 2)

                TimelineView(.animation(paused: trail.points.isEmpty)) { ctx in
                    let now = ctx.date.timeIntervalSinceReferenceDate
                    Canvas { g, size in
                        let start = Self.top + Self.bubble
                        // Draw each segment between consecutive readings, fading with age.
                        for (a, b) in zip(trail.points, trail.points.dropFirst()) {
                            guard let ca = a.cents, let cb = b.cents else { continue }
                            let ya = start + CGFloat(now - a.time) * Self.speed
                            let yb = start + CGFloat(now - b.time) * Self.speed
                            guard ya < size.height + 4 else { continue }
                            var p = Path()
                            p.move(to: CGPoint(x: x(ca), y: ya))
                            p.addLine(to: CGPoint(x: x(cb), y: yb))
                            let fade = max(0, 1 - Double(ya - start) / Double(size.height - start))
                            let good = abs(ca) <= tolerance && abs(cb) <= tolerance
                            g.stroke(p, with: .color((good ? OC.teal : OC.gold).opacity(fade)),
                                     style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        }
                    }
                }
                .accessibilityHidden(true)

                Text("♭").font(.outfit(26, .bold)).foregroundStyle(OC.muted).position(x: 22, y: Self.top + Self.bubble / 2)
                Text("♯").font(.outfit(26, .bold)).foregroundStyle(OC.muted).position(x: w - 22, y: Self.top + Self.bubble / 2)
                Text("TOO LOW").font(.outfit(10, .bold)).tracking(1.2).foregroundStyle(OC.faint).position(x: 40, y: 18)
                Text("TOO HIGH").font(.outfit(10, .bold)).tracking(1.2).foregroundStyle(OC.faint).position(x: w - 42, y: 18)

                Circle()
                    .strokeBorder(OC.teal, lineWidth: 3)
                    .frame(width: Self.bubble, height: Self.bubble)
                    .keyframeAnimator(initialValue: 0.0, trigger: pulse) { ring, t in
                        ring.scaleEffect(1 + t * 1.6).opacity(t == 0 ? 0 : 1 - t)
                    } keyframes: { _ in
                        LinearKeyframe(0.001, duration: 0.01)
                        CubicKeyframe(1, duration: 0.7)
                    }
                    .position(x: cents.map(x) ?? center, y: Self.top + Self.bubble / 2)
                    .allowsHitTesting(false)

                bubbleView
                    .keyframeAnimator(initialValue: 1.0, trigger: pulse) { bubble, scale in
                        bubble.scaleEffect(scale)
                    } keyframes: { _ in
                        SpringKeyframe(1.18, duration: 0.15)
                        SpringKeyframe(1, duration: 0.4, spring: .bouncy)
                    }
                    .position(x: cents.map(x) ?? center, y: Self.top + Self.bubble / 2)
            }
        }
        .frame(height: 210)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(cents.map { "\(note)\(octave), \(Int($0.rounded())) cents" } ?? "\(note)\(octave), waiting for a note")
    }

    private var bubbleView: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 1) {
                Text(note).font(.outfit(26, .heavy))
                Text("\(octave)").font(.outfit(11, .bold)).padding(.top, 4)
            }
            .foregroundStyle(!live ? OC.ink : inTune ? Color.white : OC.inkFixed)
            .frame(width: Self.bubble, height: Self.bubble)
            .background(Circle().fill(!live ? OC.paper : tint))
            .overlay(Circle().strokeBorder(!live ? OC.border : tint, lineWidth: 3))
            .shadow(color: tint.opacity(!live ? 0 : 0.35), radius: 10, y: 4)
            .overlay(alignment: .topTrailing) {
                if isTuned {
                    Image(systemName: "checkmark").font(.system(size: 10, weight: .black)).foregroundStyle(.white)
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(OC.teal))
                        .overlay(Circle().strokeBorder(OC.paper, lineWidth: 2))
                        .offset(x: 4, y: -4)
                        .transition(.scale(scale: 0.2).combined(with: .opacity))
                }
            }
            Triangle().fill(!live ? OC.border : tint).frame(width: 14, height: 8)
        }
        .frame(height: Self.bubble)
        .offset(y: 4)
    }
}

/// "All 6 strings in tune": a confetti burst from the bubble and a banner, shown for a few seconds.
fileprivate struct TunerCelebration: View {
    let start: Date
    static let duration: TimeInterval = 2.8

    private struct Piece {
        var angle: Double, speed: Double, spin: Double, size: CGFloat, color: Color, delay: Double
    }

    /// The same pieces every time (a fixed pseudo-random sequence), so the burst doesn't reshuffle on redraw.
    private static let pieces: [Piece] = {
        var seed: UInt64 = 0x0C40_2D5E
        func next() -> Double {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return Double(seed >> 11) / Double(1 << 53)
        }
        let colors = [OC.gold, OC.teal, OC.goldText, OC.tealText, OC.red]
        return (0..<44).map { i in
            Piece(angle: -.pi / 2 + (next() - 0.5) * 2.4, speed: 260 + next() * 300, spin: (next() - 0.5) * 14,
                  size: 5 + CGFloat(next()) * 6, color: colors[i % colors.count], delay: next() * 0.08)
        }
    }()

    var body: some View {
        ZStack(alignment: .bottom) {
            TimelineView(.animation) { ctx in
                let t = ctx.date.timeIntervalSince(start)
                Canvas { g, size in
                    let origin = CGPoint(x: size.width / 2, y: 74)
                    for p in Self.pieces {
                        let age = t - p.delay
                        guard age > 0, age < 1.8 else { continue }
                        // Thrown outwards, slowed by air, pulled down by gravity.
                        let drag = (1 - exp(-age * 2.2)) / 2.2
                        let x = origin.x + CGFloat(cos(p.angle) * p.speed * drag)
                        let y = origin.y + CGFloat(sin(p.angle) * p.speed * drag + 260 * age * age)
                        var piece = g
                        piece.opacity = max(0, 1 - age / 1.8)
                        piece.translateBy(x: x, y: y)
                        piece.rotate(by: .radians(p.spin * age))
                        piece.fill(Path(roundedRect: CGRect(x: -p.size / 2, y: -p.size / 4, width: p.size, height: p.size / 2), cornerRadius: 1),
                                   with: .color(p.color))
                    }
                }
            }
            .allowsHitTesting(false)

            HStack(spacing: 8) {
                Image(systemName: "checkmark.seal.fill").font(.system(size: 18, weight: .bold)).foregroundStyle(OC.teal)
                    .symbolEffect(.bounce, value: start)
                Text("All 6 strings in tune").font(.outfit(16, .heavy)).foregroundStyle(OC.ink)
            }
            .padding(.horizontal, 16).frame(height: 44)
            .background(Capsule().fill(OC.paper))
            .overlay(Capsule().strokeBorder(OC.teal.opacity(0.5), lineWidth: 1.5))
            .shadow(color: OC.teal.opacity(0.25), radius: 12, y: 4)
            .padding(.bottom, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("All 6 strings in tune")
    }
}

/// Small downward pointer under the tuner bubble.
fileprivate struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

/// Guitar headstock illustration (3 + 3 tuners).
fileprivate struct TunerHeadstock: View {
    var body: some View {
        Canvas { ctx, _ in
            var head = Path()
            head.move(to: CGPoint(x: 28, y: 10))
            head.addQuadCurve(to: CGPoint(x: 92, y: 10), control: CGPoint(x: 60, y: -2))
            head.addLine(to: CGPoint(x: 98, y: 170))
            head.addQuadCurve(to: CGPoint(x: 22, y: 170), control: CGPoint(x: 60, y: 182))
            head.closeSubpath()
            ctx.fill(head, with: .color(OC.inkFixed))
            ctx.fill(Path(CGRect(x: 36, y: 170, width: 48, height: 60)), with: .color(ToolsNight.border))
            ctx.fill(Path(roundedRect: CGRect(x: 34, y: 168, width: 52, height: 5), cornerRadius: 2), with: .color(Color(hex: 0xF1F0ED)))
            for (x, y) in [(40.0, 42.0), (40, 92), (40, 142), (80, 42), (80, 92), (80, 142)] {
                ctx.fill(Path(ellipseIn: CGRect(x: x - 6, y: y - 6, width: 12, height: 12)), with: .color(Color(hex: 0xD6D4CF)))
            }
            let lines: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [(40, 142, 44, 1.2), (40, 92, 52, 1.2), (40, 42, 60, 1), (80, 42, 68, 1), (80, 92, 76, 1.2), (80, 142, 84, 1.2)]
            for l in lines {
                var p = Path()
                p.move(to: CGPoint(x: l.0, y: l.1))
                p.addLine(to: CGPoint(x: l.2, y: 230))
                ctx.stroke(p, with: .color(Color(hex: 0x8E8A84)), lineWidth: l.3)
            }
        }
    }
}

// MARK: - Previews

#Preview("Chord guide") {
    NavigationStack { ChordGuideView(focus: "F", songChords: ["C", "G/B", "Am", "F", "G"]) }.environment(AppState())
}
#Preview("Chords by key") { NavigationStack { KeysView() }.environment(AppState()) }
#Preview("Strum trainer") { NavigationStack { StrumTrainerView() }.environment(AppState()) }
#Preview("Chord check") { NavigationStack { PracticeView(chord: "C") }.environment(AppState()) }
#Preview("Save image") { NavigationStack { SaveImageView(chord: "Am") }.environment(AppState()) }
#Preview("Print PDF") { NavigationStack { PrintPdfView(song: MockData.sinSarPar) }.environment(AppState()) }
#Preview("Report") { NavigationStack { ReportView(song: MockData.sinSarParEasy) }.environment(AppState()) }
#Preview("Jam") { NavigationStack { JamView(song: MockData.sinSarPar) }.environment(AppState()) }
#Preview("Tuner") { NavigationStack { TunerView() }.environment(AppState()) }
