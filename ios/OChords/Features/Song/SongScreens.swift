import SwiftUI
import UIKit
import GameController

// Song group: Song view (4a) with Key & capo (4b), Easy chords (4c) and More (4f) sheets,
// Stage mode, Warm up (+ drill) and Hands-free (pedal / head nod / auto / voice).

// MARK: - Shared per-song state

/// Key, capo, easy chords, view prefs, private notes and known chords.
/// Kept in memory for the demo ("saved per song, per user" on the canvas note) so Song, Warm up and Stage agree.
@Observable
fileprivate final class SongPlayStore {
    static let shared = SongPlayStore()

    struct Settings: Hashable {
        /// Sounding shift in semitones, 0–11.
        var transpose = 0
        var capo = 0
        var easy = false
        /// Text size index 0–2.
        var size = 1
        var showChords = true
        var scrollSpeed = 1.0
        var stageSpeed = 1.0
    }

    var settings: [String: Settings] = [:]
    var notes: [String: String] = [:]
    /// Chords the player ticked "Got it" on in Warm up (per user, across songs).
    var knownChords: Set<String> = ["C", "G/B"]
    var handsFree: SongHandsFreeMode = SongHandsFreeMode(rawValue: UserDefaults.standard.string(forKey: "handsFree.mode") ?? "") ?? .pedal {
        didSet { UserDefaults.standard.set(handsFree.rawValue, forKey: "handsFree.mode") }
    }
    /// Which way of turning the head is a nod down for this player (learnt in Hands-free setup).
    var nodDownSign: Double = UserDefaults.standard.object(forKey: "handsFree.nodDown") as? Double ?? 1 {
        didSet { UserDefaults.standard.set(nodDownSign, forKey: "handsFree.nodDown") }
    }
    var askedCamera = false
    var askedMic = false

    func settings(for id: String) -> Settings { settings[id] ?? Settings() }

    func binding(for id: String) -> Binding<Settings> {
        Binding(get: { self.settings(for: id) }, set: { self.settings[id] = $0 })
    }
}

fileprivate enum SongHandsFreeMode: String, CaseIterable, Identifiable {
    case pedal, nod, auto, voice
    var id: String { rawValue }
    var title: String {
        switch self {
        case .pedal: "Foot pedal"
        case .nod: "Head nod"
        case .auto: "Auto-scroll"
        case .voice: "Voice"
        }
    }
    var subtitle: String {
        switch self {
        case .pedal: "Bluetooth page-turner"
        case .nod: "Front camera"
        case .auto: "Synced to tempo"
        case .voice: "“Next”, “chorus”…"
        }
    }
    var systemImage: String {
        switch self {
        case .pedal: "pedal.accelerator"
        case .nod: "face.smiling"
        case .auto: "arrow.down"
        case .voice: "mic"
        }
    }
}

/// How a song looks with the player's current key / capo / easy settings.
fileprivate struct SongDisplay {
    let song: Song
    let s: SongPlayStore.Settings

    func shown(_ chord: String) -> ChordTheory.Shown {
        ChordTheory.shown(chord, transpose: s.transpose, capo: s.capo, easy: s.easy)
    }

    var soundKey: String { ChordTheory.transposeRoot(song.key, by: s.transpose) }
    var shapeKey: String { ChordTheory.transposeRoot(song.key, by: s.transpose - s.capo) }

    /// Unique chords as the fingers play them, in order of first appearance.
    var usedShown: [ChordTheory.Shown] {
        var seen = Set<String>(), out: [ChordTheory.Shown] = []
        for c in song.usedChords {
            let sh = shown(c)
            if seen.insert(sh.name).inserted { out.append(sh) }
        }
        return out
    }

    /// Every chord in playing order (lines and bars), as shown.
    var sequence: [String] {
        song.sections.flatMap { $0.bars + $0.lines.flatMap(ChordLine.chords(in:)) }.map { shown($0).name }
    }

    var modeNote: String? {
        var parts: [String] = []
        if s.capo > 0 { parts.append("Capo \(s.capo) · \(shapeKey) shapes, sounds in \(soundKey)") }
        if s.easy {
            let swaps = usedShown.compactMap { sh in sh.from.map { "\($0) → \(sh.name)" } }
            parts.append(swaps.isEmpty ? "Easy chords on" : "Easy chords: " + swaps.joined(separator: ", "))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    var shiftNote: String {
        let t = s.transpose
        if t == 0 { return "Original key · \(song.key)" }
        return "Original \(song.key) · " + (t <= 6 ? "+\(t)" : "−\(12 - t)") + " semitones"
    }
}

fileprivate let songOnInk = Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614))
fileprivate let songNoteBorder = Color(light: UIColor(hex: 0xFFE3A3), dark: UIColor(hex: 0xFFB800, alpha: 0.3))

fileprivate extension Song {
    var titleFont: (CGFloat) -> Font { { size in isMyanmar ? .myanmar(size, .bold) : .outfit(size, .bold) } }
}

// MARK: - Mini chord diagram (single-colour dots, as in the design's chord strips)

/// Compact fretboard: thick nut, 4 frets, one-colour dots and barre. Optional ×/○ markers.
fileprivate struct SongMiniDiagram: View {
    let voicing: ChordVoicing
    var width: CGFloat = 52
    var leftHanded = false
    var dark = false
    var showMarkers = false
    var dotColor: Color = OC.gold

    var body: some View {
        let gridH = width * 0.92
        let markH: CGFloat = showMarkers ? 11 : 0
        Canvas { ctx, size in
            let pad: CGFloat = 4
            let gw = size.width - pad * 2
            let top = markH + 2
            let fretH = gridH / 4
            let ink = dark ? Color(hex: 0xF4F1EA) : Color(hex: 0x1A1917)
            let line = dark ? Color.white.opacity(0.35) : Color(hex: 0x8E8A84)
            func sx(_ i: Int) -> CGFloat {
                let k = CGFloat(leftHanded ? 5 - i : i)
                return pad + gw * k / 5
            }
            let pattern = Array(voicing.pattern)
            if showMarkers {
                for i in 0..<6 where i < pattern.count {
                    let c = pattern[i]
                    guard c == "x" || c == "0" else { continue }
                    let r = CGRect(x: sx(i) - 3, y: 1, width: 6, height: 6)
                    if c == "0" {
                        ctx.stroke(Path(ellipseIn: r), with: .color(ink), lineWidth: 1)
                    } else {
                        var p = Path()
                        p.move(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
                        p.move(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
                        ctx.stroke(p, with: .color(ink.opacity(0.7)), lineWidth: 1)
                    }
                }
            }
            for f in 0...4 {
                let y = top + CGFloat(f) * fretH
                let h: CGFloat = f == 0 && voicing.baseFret <= 1 ? 3 : 1
                ctx.fill(Path(CGRect(x: pad, y: y - h / 2, width: gw, height: h)), with: .color(f == 0 && h > 1 ? ink : line))
            }
            for i in 0..<6 {
                ctx.fill(Path(CGRect(x: sx(i) - 0.5, y: top, width: 1, height: gridH)), with: .color(line))
            }
            let dot = max(5, width * 0.13)
            if let b = voicing.barre {
                let a = min(sx(b.from), sx(b.to)), z = max(sx(b.from), sx(b.to))
                let y = top + (CGFloat(b.fret) - 0.5) * fretH
                ctx.fill(Path(roundedRect: CGRect(x: a - dot / 2, y: y - dot / 2, width: z - a + dot, height: dot), cornerRadius: dot / 2), with: .color(dotColor))
            }
            for d in voicing.dots {
                let c = CGPoint(x: sx(d.string), y: top + (CGFloat(d.fret) - 0.5) * fretH)
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - dot / 2, y: c.y - dot / 2, width: dot, height: dot)), with: .color(dotColor))
            }
        }
        .frame(width: width, height: gridH + markH + 4)
        .overlay(alignment: .topLeading) {
            if voicing.baseFret > 1 {
                Text("\(voicing.baseFret)").font(.mono(8)).foregroundStyle(dark ? OC.nightMuted : OC.muted).offset(x: -8, y: markH)
            }
        }
        .accessibilityHidden(true)
    }
}

/// Foot-pedal glyph (design draws a pedal: rounded base with a raised tread).
fileprivate struct SongPedalGlyph: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let w = r.width, h = r.height
        p.addRoundedRect(in: CGRect(x: r.minX + w * 0.08, y: r.minY + h * 0.55, width: w * 0.84, height: h * 0.3), cornerSize: CGSize(width: w * 0.08, height: w * 0.08))
        p.move(to: CGPoint(x: r.minX + w * 0.28, y: r.minY + h * 0.55))
        p.addLine(to: CGPoint(x: r.minX + w * 0.38, y: r.minY + h * 0.2))
        p.addLine(to: CGPoint(x: r.minX + w * 0.62, y: r.minY + h * 0.2))
        p.addLine(to: CGPoint(x: r.minX + w * 0.72, y: r.minY + h * 0.55))
        return p
    }
}

fileprivate struct SongHandsFreeIcon: View {
    let mode: SongHandsFreeMode
    var size: CGFloat = 16
    var body: some View {
        if mode == .pedal {
            SongPedalGlyph()
                .stroke(style: StrokeStyle(lineWidth: max(1.4, size * 0.1), lineCap: .round, lineJoin: .round))
                .frame(width: size * 1.15, height: size * 1.15)
        } else {
            Image(systemName: mode.systemImage).font(.system(size: size, weight: .regular))
        }
    }
}

// MARK: - Chord-over-lyric flow layout

fileprivate struct SongGapKey: LayoutValueKey { static let defaultValue: CGFloat = 0 }

/// Wraps segments like CSS `flex-wrap: wrap; align-items: flex-end`. The gap after each item
/// comes from `SongGapKey`, so row ends never carry a trailing space.
fileprivate struct SongWrapLayout: Layout {
    var rowSpacing: CGFloat = 2

    private struct Row { var items: [(index: Int, size: CGSize)] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func rows(maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows = [Row()]
        var x: CGFloat = 0
        for (i, sv) in subviews.enumerated() {
            var size = sv.sizeThatFits(.unspecified)
            if size.width > maxWidth { size = sv.sizeThatFits(ProposedViewSize(width: maxWidth, height: nil)) }
            if x > 0, x + size.width > maxWidth + 0.5 {
                rows.append(Row())
                x = 0
            }
            rows[rows.count - 1].items.append((i, size))
            rows[rows.count - 1].width = x + size.width
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
            x += size.width + sv[SongGapKey.self]
        }
        return rows
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rs = rows(maxWidth: proposal.width ?? .infinity, subviews: subviews)
        let height = rs.reduce(0) { $0 + $1.height } + rowSpacing * CGFloat(max(0, rs.count - 1))
        return CGSize(width: proposal.width ?? (rs.map(\.width).max() ?? 0), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(maxWidth: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for item in row.items {
                subviews[item.index].place(at: CGPoint(x: x, y: y + row.height - item.size.height), proposal: ProposedViewSize(item.size))
                x += item.size.width + subviews[item.index][SongGapKey.self]
            }
            y += row.height + rowSpacing
        }
    }
}

fileprivate struct SongLineStyle {
    var chordSize: CGFloat
    var lyricSize: CGFloat
    var chordColor: Color = OC.goldText
    var easyColor: Color = OC.teal
    var lyricColor: Color = OC.ink
    var showChords = true
    var myanmar = true
}

/// One ChordPro line: chords in mono above Myanmar lyrics, wrapping word by word.
fileprivate struct SongLyricLine: View {
    let line: String
    let style: SongLineStyle
    let shown: (String) -> ChordTheory.Shown

    var body: some View {
        let segs = ChordLine.parse(line)
        SongWrapLayout(rowSpacing: 2) {
            ForEach(Array(segs.enumerated()), id: \.offset) { _, seg in
                segment(seg)
                    .layoutValue(key: SongGapKey.self, value: seg.trailingSpace ? style.lyricSize * 0.42 : 0)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(segs.map { seg in
            (seg.chord.map { style.showChords ? "\(shown($0).name), " : "" } ?? "") + seg.text
        }.joined(separator: " "))
    }

    @ViewBuilder private func segment(_ seg: ChordLine.Segment) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if style.showChords {
                let sh = seg.chord.map(shown)
                Text(sh?.name ?? " ")
                    .font(.mono(style.chordSize))
                    .foregroundStyle(sh?.isEasy == true ? style.easyColor : style.chordColor)
                    .frame(minHeight: style.chordSize * 1.2, alignment: .bottom)
            }
            Text(seg.text.isEmpty ? " " : seg.text)
                .font(style.myanmar ? .myanmar(style.lyricSize) : .outfit(style.lyricSize))
                .foregroundStyle(style.lyricColor)
                .frame(minHeight: style.lyricSize * 1.85)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// VERSE / CHORUS / MUSIC pill as drawn in the song view (rounded, 12pt heavy).
fileprivate struct SongSectionPill: View {
    let label: String
    let kind: SectionKind
    var size: CGFloat = 12
    var body: some View {
        let (bg, fg) = SectionTag.colors(kind)
        Text(label.uppercased())
            .font(.outfit(size, .heavy)).tracking(0.5)
            .foregroundStyle(fg)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(Capsule().fill(bg))
    }
}

fileprivate struct SongIconButton: View {
    let systemName: String
    let label: String
    var tint: Color = OC.ink
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 19, weight: .regular))
                .foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Song view (4a)

struct SongView: View {
    /// The song as opened; once it's in the library, the library's copy is shown (so edits appear straight away).
    private let opened: Song
    @Environment(AppState.self) private var app

    init(song: Song) { opened = song }

    private var song: Song { app.library.first { $0.id == opened.id } ?? opened }
    /// Your own sheets (scanned or listened) can be renamed; community sheets belong to their uploader.
    private var canEditDetails: Bool { app.isInLibrary(song.id) && (song.source == .imported || song.source == .listened) }
    private let store = SongPlayStore.shared

    @State private var showKeySheet = false
    @State private var showMore = false
    @State private var playing = false
    @State private var position = ScrollPosition(edge: .top)
    @State private var offsetY: CGFloat = 0
    @State private var maxOffset: CGFloat = 0
    @State private var editingNote = false
    @State private var noteDraft = ""
    @FocusState private var noteFocused: Bool
    @State private var toast: Toast?
    @State private var editingDetails = false
    @State private var titleDraft = ""
    @State private var artistDraft = ""
    /// Edit-lyrics mode: lines are tappable; `lineEdit` is the line open in the editor.
    @State private var editingLyrics = false
    @State private var lineEdit: SongLineRef?
    /// A better reading of the sheet the scanner found, offered because the player had edited the song.
    @State private var offered: Song?

    private var settings: Binding<SongPlayStore.Settings> { store.binding(for: song.id) }
    private var display: SongDisplay { SongDisplay(song: song, s: store.settings(for: song.id)) }
    private var note: String { store.notes[song.id] ?? song.myNote ?? "" }

    var body: some View {
        let d = display
        VStack(spacing: 0) {
            topBar(d)
            header
            controls(d)
            chordStrip(d)
            if let mode = d.modeNote { modeBanner(mode) }
            sheetBody(d)
        }
        .overlay(alignment: .bottom) {
            if playing { speedPill.transition(.move(edge: .bottom).combined(with: .opacity)) }
        }
        .animation(.snappy(duration: 0.25), value: playing)
        .ocPage()
        .sheet(isPresented: $showKeySheet) { SongKeySheet(song: song) }
        .sheet(isPresented: $showMore) {
            SongMoreSheet(song: song, keyName: d.soundKey, hasNote: !note.isEmpty,
                          inLibrary: app.isInLibrary(song.id), canEditDetails: canEditDetails, onAction: handleMore)
        }
        .sheet(item: $lineEdit) { ref in
            let section = song.sections.first { $0.id == ref.sectionID }
            LyricLineEditor(
                sectionLabel: section?.label.capitalized ?? "",
                line: ref.line.flatMap { i in section?.lines.indices.contains(i) == true ? section?.lines[i] : nil } ?? "",
                onSave: { saveLine($0, at: ref) },
                onDelete: { deleteLine(ref) }
            )
            .presentationDetents([.medium, .large])
        }
        .alert("Title & artist", isPresented: $editingDetails) {
            TextField("Title", text: $titleDraft)
            TextField("Artist", text: $artistDraft)
            Button("Save", action: saveDetails)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Fix what the scan read.")
        }
        #if DEBUG
        // -demoEditDetails YES opens the title & artist editor (simulator screenshots).
        .task { if UserDefaults.standard.bool(forKey: "demoEditDetails"), canEditDetails { try? await Task.sleep(for: .seconds(1)); beginDetails() } }
        // -demoEditLyrics YES turns on edit-lyrics mode; -demoEditLine YES also opens the first verse line.
        .task {
            guard UserDefaults.standard.bool(forKey: "demoEditLyrics"), canEditDetails else { return }
            try? await Task.sleep(for: .seconds(1))
            editingLyrics = true
            if UserDefaults.standard.bool(forKey: "demoEditLine"), let verse = song.sections.first(where: { !$0.lines.isEmpty }) {
                lineEdit = SongLineRef(sectionID: verse.id, line: 0)
            }
        }
        #endif
        .toast($toast)
        .task(id: song.id) { offered = app.scanArchive.entry(for: song.id)?.offered }
        .task(id: playing) { await autoScroll() }
        .onDisappear { playing = false }
    }

    // MARK: Top

    private func topBar(_ d: SongDisplay) -> some View {
        TopBar(backLabel: "Back to library", onBack: { app.pop() }) {
            HStack(spacing: 0) {
                SongIconButton(systemName: "person.2", label: "Start a jam room") { app.push(.jam(song)) }
                SongIconButton(systemName: "squareshape.split.3x3", label: "Chords by key") { app.push(.keys) }
                SongIconButton(systemName: "viewfinder", label: "Warm up, then stage mode") { goStage() }
                SongIconButton(systemName: "photo", label: "Save as image") {
                    app.push(.saveImage(chord: d.usedShown.first?.name ?? song.key, song: song))
                }
                SongIconButton(systemName: "ellipsis", label: "More") { showMore = true }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(song.title)
                .font(song.titleFont(28))
                .foregroundStyle(OC.ink)
                .lineSpacing(song.isMyanmar ? 6 : 0)
                .lineLimit(2)
            (Text("Sung by ").foregroundStyle(OC.muted)
             + Text(song.artist).font(song.isMyanmar ? .myanmar(14, .semibold) : .outfit(14, .semibold)).foregroundStyle(OC.ink)
             + Text(" · Original key \(song.key) · 4/4").foregroundStyle(OC.muted))
                .font(.outfit(14))
            sharedLine.padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
    }

    private var sharedLine: some View {
        HStack(spacing: 6) {
            let name = song.uploader?.name ?? "you"
            Text(song.uploader.map { String($0.initials.prefix(1)) } ?? "Y")
                .font(.outfit(10, .heavy)).foregroundStyle(.white)
                .frame(width: 20, height: 20)
                .background(Circle().fill(Color(hex: song.uploader?.tier.colorHex ?? 0x1E3A8A)))
            if let u = song.uploader {
                Button { app.push(.user(u)) } label: {
                    (Text("Shared by ").foregroundStyle(OC.muted) + Text(name).fontWeight(.bold).foregroundStyle(OC.ink))
                }
                .buttonStyle(.plain)
            } else {
                Text("Shared by ").foregroundStyle(OC.muted) + Text(name).fontWeight(.bold).foregroundStyle(OC.ink)
                Text("·").foregroundStyle(OC.muted)
                Label("Public", systemImage: "globe")
                    .labelStyle(SongTightLabelStyle())
                    .font(.outfit(12, .bold)).foregroundStyle(OC.tealText)
            }
            Text("·").foregroundStyle(OC.muted)
            Button("Other versions") { app.switchTo(.explore) }
                .font(.outfit(12, .bold)).foregroundStyle(OC.goldText)
                .buttonStyle(.plain)
        }
        .font(.outfit(12))
        .lineLimit(1)
        .minimumScaleFactor(0.85)
    }

    private func controls(_ d: SongDisplay) -> some View {
        let s = settings.wrappedValue
        return HStack(spacing: 8) {
            HStack(spacing: 0) {
                Button { shift(-1) } label: {
                    Text("−").font(.outfit(22, .semibold)).frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .accessibilityLabel("Transpose down")
                Button { showKeySheet = true } label: {
                    VStack(spacing: 0) {
                        Text(s.capo > 0 ? "CAPO \(s.capo)" : "KEY")
                            .font(.outfit(10, .bold)).tracking(0.5).foregroundStyle(OC.muted)
                            .fixedSize()
                        Text(d.soundKey).font(.mono(17)).foregroundStyle(OC.goldText)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
                }
                .accessibilityLabel("Key and capo, key \(d.soundKey)\(s.capo > 0 ? ", capo \(s.capo)" : "")")
                Button { shift(1) } label: {
                    Text("+").font(.outfit(22, .semibold)).frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .accessibilityLabel("Transpose up")
            }
            .buttonStyle(.plain)
            .foregroundStyle(OC.ink)
            .padding(.horizontal, 2)
            .frame(width: 142, height: 48)
            .glass(radius: 14)

            Button { withAnimation(.snappy) { settings.wrappedValue.size = (s.size + 1) % 3 } } label: {
                (Text("A").font(.outfit(16, .bold)) + Text("a").font(.outfit(12, .bold)))
                    .foregroundStyle(OC.ink)
                    .frame(width: 48, height: 48)
                    .glass(radius: 14)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Text size")
            .accessibilityValue(["Small", "Medium", "Large"][s.size])

            Button { withAnimation(.snappy) { settings.wrappedValue.showChords.toggle() } } label: {
                Text("Am")
                    .font(.mono(14))
                    .foregroundStyle(s.showChords ? OC.goldText : OC.muted)
                    .frame(width: 48, height: 48)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(s.showChords ? OC.goldTint : OC.glass))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(s.showChords ? songNoteBorder : OC.border, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show chords")
            .accessibilityAddTraits(s.showChords ? .isSelected : [])

            Button { playing.toggle() } label: {
                HStack(spacing: 6) {
                    Image(systemName: playing ? "pause.fill" : "play.fill").font(.system(size: 14))
                    Text("Scroll").font(.outfit(14, .bold))
                }
                .foregroundStyle(playing ? OC.inkFixed : songOnInk)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(playing ? OC.gold : OC.ink))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(playing ? "Pause auto-scroll" : "Auto-scroll")
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
    }

    private func chordStrip(_ d: SongDisplay) -> some View {
        HStack(spacing: 6) {
            Text("CHORDS").font(.outfit(12, .bold)).foregroundStyle(OC.muted).padding(.trailing, 2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(d.usedShown, id: \.name) { sh in
                        Button {
                            app.push(.chords(focus: sh.name, songChords: d.usedShown.map(\.name)))
                        } label: {
                            Text(sh.name)
                                .font(.mono(13))
                                .foregroundStyle(sh.isEasy ? OC.teal : OC.goldText)
                                .padding(.horizontal, 9).padding(.vertical, 5)
                                .glass(radius: 20)
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Review chord \(sh.name)")
                    }
                }
                .padding(.vertical, 2)
            }
            .scrollClipDisabled()
            Button { goStage() } label: {
                Text("Warm up")
                    .font(.outfit(13, .bold))
                    .foregroundStyle(songOnInk)
                    .padding(.horizontal, 11)
                    .frame(height: 32)
                    .background(Capsule().fill(OC.ink))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .fixedSize()
        }
        .padding(.horizontal, 20)
        .padding(.top, 4)
        .overlay(alignment: .bottom) { Rectangle().fill(OC.border).frame(height: 1) }
    }

    private func modeBanner(_ text: String) -> some View {
        Button { showKeySheet = true } label: {
            HStack(spacing: 8) {
                Image(systemName: "slider.horizontal.3").font(.system(size: 14, weight: .semibold))
                Text(text).frame(maxWidth: .infinity, alignment: .leading).multilineTextAlignment(.leading)
                Text("Edit").fontWeight(.heavy)
            }
            .font(.outfit(13, .semibold))
            .foregroundStyle(OC.tealText)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(minHeight: 40)
            .background(RoundedRectangle(cornerRadius: 12).fill(OC.tealTint.opacity(0.85)))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(OC.teal.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    // MARK: Sheet body

    private func sheetBody(_ d: SongDisplay) -> some View {
        let s = d.s
        let sizes: (CGFloat, CGFloat) = [(13, 15), (15, 18), (17, 21)][s.size]
        let style = SongLineStyle(chordSize: sizes.0, lyricSize: sizes.1, showChords: s.showChords, myanmar: song.isMyanmar)
        return ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if let offered, canEditDetails { offerBanner(offered) }
                if editingLyrics { editBanner }
                if !note.isEmpty || editingNote { noteCard }
                ForEach(song.sections) { sec in
                    SongSectionView(section: sec, style: style, shown: d.shown, editing: editingLyrics,
                                    onEditLine: { lineEdit = SongLineRef(sectionID: sec.id, line: $0) },
                                    onAddLine: { lineEdit = SongLineRef(sectionID: sec.id, line: nil) }) { app.push(.strum) }
                }
                footer
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, playing ? 100 : 48)
            .animation(.snappy(duration: 0.25), value: s)
        }
        .scrollPosition($position)
        .onScrollGeometryChange(for: CGPoint.self) { geo in
            CGPoint(x: geo.contentOffset.y, y: geo.contentSize.height - geo.containerSize.height + geo.contentInsets.bottom)
        } action: { _, v in
            offsetY = v.x
            maxOffset = max(0, v.y)
        }
    }

    private var noteCard: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "pencil").font(.system(size: 15, weight: .medium)).foregroundStyle(OC.goldText).padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text("MY NOTE · ONLY YOU").font(.outfit(11, .heavy)).tracking(1).foregroundStyle(OC.goldText)
                if editingNote {
                    TextField("Capo, tempo, what to skip…", text: $noteDraft, axis: .vertical)
                        .font(.outfit(14))
                        .foregroundStyle(OC.ink)
                        .lineLimit(1...6)
                        .focused($noteFocused)
                        .submitLabel(.done)
                        .onSubmit(saveNote)
                } else {
                    Text(note).font(.outfit(14)).foregroundStyle(OC.ink).lineSpacing(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(editingNote ? "Done" : "Edit") { editingNote ? saveNote() : beginNote() }
                .font(.outfit(13, .bold))
                .foregroundStyle(OC.goldText)
                .buttonStyle(.plain)
                .frame(minWidth: 44, minHeight: 28)
                .accessibilityLabel(editingNote ? "Save note" : "Edit note")
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.goldTint))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(songNoteBorder, lineWidth: 1))
        .rotationEffect(.degrees(editingNote ? 0 : -0.6))
        .animation(.snappy, value: editingNote)
    }

    @ViewBuilder private var footer: some View {
        switch song.source {
        case .imported:
            HStack(spacing: 10) {
                Image(systemName: "doc").font(.system(size: 16))
                Text("Imported from a photo ·")
                Button("View original") {
                    app.push(.review(ScanResult(song: song, detectedZawgyi: false, lowConfidence: [])))
                }
                .fontWeight(.bold).foregroundStyle(OC.goldText).buttonStyle(.plain)
            }
            .font(.outfit(13)).foregroundStyle(OC.muted)
        case .listened:
            Label("Made by listening · chords are a best guess", systemImage: "waveform")
                .font(.outfit(13)).foregroundStyle(OC.muted)
        case .community, .shared:
            HStack(spacing: 10) {
                Image(systemName: "person.2").font(.system(size: 15))
                Text("Community sheet ·")
                Button("Report a problem") { app.push(.report(song)) }
                    .fontWeight(.bold).foregroundStyle(OC.goldText).buttonStyle(.plain)
            }
            .font(.outfit(13)).foregroundStyle(OC.muted)
        }
    }

    private var speedPill: some View {
        let speed = settings.wrappedValue.scrollSpeed
        return HStack(spacing: 10) {
            Text("Speed").font(.outfit(13, .semibold))
            Button { setSpeed(speed - 0.25) } label: {
                Text("−").font(.outfit(18)).frame(width: 34, height: 34).background(Circle().fill(Color(hex: 0x2A2825)))
            }
            .accessibilityLabel("Slower")
            Text(speed.formatted(.number.precision(.fractionLength(1...2))) + "×")
                .font(.mono(13, .semibold))
                .frame(minWidth: 38)
            Button { setSpeed(speed + 0.25) } label: {
                Text("+").font(.outfit(18)).frame(width: 34, height: 34).background(Circle().fill(Color(hex: 0x2A2825)))
            }
            .accessibilityLabel("Faster")
        }
        .buttonStyle(.plain)
        .foregroundStyle(OC.paper)
        .padding(.leading, 16).padding(.trailing, 6)
        .frame(height: 44)
        .background(Capsule().fill(OC.inkFixed))
        .shadow(color: .black.opacity(0.2), radius: 12, y: 6)
        .padding(.bottom, 28)
    }

    // MARK: Actions

    private func shift(_ by: Int) {
        withAnimation(.snappy) {
            settings.wrappedValue.transpose = (settings.wrappedValue.transpose + by + 12) % 12
            settings.wrappedValue.capo = 0
        }
    }

    private func setSpeed(_ v: Double) {
        settings.wrappedValue.scrollSpeed = min(2, max(0.5, v))
    }

    private func goStage() {
        playing = false
        app.push(app.alwaysSkipWarmup ? .stage(song) : .warmup(song))
    }

    private func beginNote() {
        noteDraft = note
        editingNote = true
        position.scrollTo(edge: .top)
        Task { try? await Task.sleep(for: .milliseconds(250)); noteFocused = true }
    }

    private func saveNote() {
        let text = noteDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        store.notes[song.id] = text
        if let i = app.library.firstIndex(where: { $0.id == song.id }) {
            app.library[i].myNote = text.isEmpty ? nil : text
        }
        noteFocused = false
        editingNote = false
        toast = Toast(text: text.isEmpty ? "Note removed" : "Note saved · only you can see it")
    }

    private var editBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "text.cursor").font(.system(size: 15, weight: .semibold)).foregroundStyle(OC.goldText)
            Text("Editing lyrics — tap a line to change it").font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
            Spacer(minLength: 4)
            Button("Done") { withAnimation(.snappy) { editingLyrics = false } }
                .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
        }
        .padding(.horizontal, 14)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.goldTint))
    }

    /// The scanner reads this sheet better now, but the player had changed the song: their choice.
    private func offerBanner(_ better: Song) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("The scanner reads this sheet better now", systemImage: "sparkles")
                .font(.outfit(14, .bold)).foregroundStyle(OC.ink)
            Text("You changed this song, so it wasn’t updated. Use the new reading to replace the lyrics and chords — your notes stay.")
                .font(.outfit(13)).foregroundStyle(OC.muted).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 16) {
                Button("Use the new reading") { decideOffer(use: better) }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
                Button("Keep mine") { decideOffer(use: nil) }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.muted).frame(minHeight: 44)
            }
        }
        .padding(.horizontal, 14).padding(.top, 12)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.goldTint))
    }

    private func decideOffer(use better: Song?) {
        if let better {
            var updated = song
            updated.sections = better.sections
            app.addToLibrary(updated)
            toast = Toast(text: "Updated with the new reading")
        }
        if var entry = app.scanArchive.entry(for: song.id) {
            entry.offered = nil
            app.scanArchive.save(entry, for: song.id)
        }
        withAnimation(.snappy) { offered = nil }
    }

    /// Saves an edited (or new) line; syncs like any library change.
    private func saveLine(_ text: String, at ref: SongLineRef) {
        lineEdit = nil
        var updated = song
        guard let s = updated.sections.firstIndex(where: { $0.id == ref.sectionID }) else { return }
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        if let i = ref.line, updated.sections[s].lines.indices.contains(i) {
            guard trimmed != updated.sections[s].lines[i] else { return }
            updated.sections[s].lines[i] = trimmed
        } else {
            guard !trimmed.isEmpty else { return }
            updated.sections[s].lines.append(trimmed)
        }
        updated.isMyanmar = updated.isMyanmar || ChordSheetParser.isMyanmar(trimmed)
        app.addToLibrary(updated)
        toast = Toast(text: "Line saved")
    }

    private func deleteLine(_ ref: SongLineRef) {
        lineEdit = nil
        guard let i = ref.line else { return }
        var updated = song
        guard let s = updated.sections.firstIndex(where: { $0.id == ref.sectionID }), updated.sections[s].lines.indices.contains(i) else { return }
        updated.sections[s].lines.remove(at: i)
        app.addToLibrary(updated)
        toast = Toast(text: "Line deleted")
    }

    private func beginDetails() {
        titleDraft = song.title
        artistDraft = song.artist
        editingDetails = true
    }

    /// Saves a new title and artist (Zawgyi typed in is converted, as in Review); syncs like any library change.
    private func saveDetails() {
        let title = Zawgyi.normalised(titleDraft.trimmingCharacters(in: .whitespacesAndNewlines))
        let artist = Zawgyi.normalised(artistDraft.trimmingCharacters(in: .whitespacesAndNewlines))
        guard !title.isEmpty else {
            toast = Toast(text: "A song needs a title", systemImage: "exclamationmark.triangle")
            return
        }
        guard title != song.title || artist != song.artist else { return }
        var updated = song
        updated.title = title
        updated.artist = artist
        updated.isMyanmar = updated.isMyanmar || ChordSheetParser.isMyanmar(title + artist)
        app.addToLibrary(updated)
        toast = Toast(text: "Title & artist updated")
    }

    private func handleMore(_ action: SongMoreAction) {
        showMore = false
        switch action {
        case .details: beginDetails()
        case .lyrics: withAnimation(.snappy) { editingLyrics = true }
        case .note: beginNote()
        case .print: app.push(.printPdf(song))
        case .image: app.push(.saveImage(chord: display.usedShown.first?.name ?? song.key, song: song))
        case .jam: app.push(.jam(song))
        case .report: app.push(.report(song))
        case .save:
            var s = song
            if !note.isEmpty { s.myNote = note }
            app.addToLibrary(s)
            toast = Toast(text: "Saved to your library")
        }
    }

    /// Scrolls the sheet slowly (~22 pt/s at 1×) until the end or until paused.
    private func autoScroll() async {
        guard playing else { return }
        if offsetY >= maxOffset - 1 { position.scrollTo(edge: .top); try? await Task.sleep(for: .milliseconds(400)) }
        var last = Date.now
        var y = offsetY
        while !Task.isCancelled, playing {
            try? await Task.sleep(for: .milliseconds(16))
            let now = Date.now
            let dt = now.timeIntervalSince(last)
            last = now
            if abs(offsetY - y) > 3 { y = offsetY } // the player dragged
            y += CGFloat(22 * settings.wrappedValue.scrollSpeed * dt)
            if y >= maxOffset {
                position.scrollTo(y: maxOffset)
                playing = false
                return
            }
            position.scrollTo(y: y)
        }
    }
}

fileprivate struct SongTightLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 3) { configuration.icon.imageScale(.small); configuration.title }
    }
}

// MARK: Section

fileprivate struct SongSectionView: View {
    let section: SongSection
    let style: SongLineStyle
    let shown: (String) -> ChordTheory.Shown
    /// Edit-lyrics mode: each line is a button that opens the editor, plus "Add line".
    var editing = false
    var onEditLine: (Int) -> Void = { _ in }
    var onAddLine: () -> Void = {}
    let onStrum: () -> Void

    var body: some View {
        let isChorus = section.kind == .chorus
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                SongSectionPill(label: section.label, kind: section.kind)
                if let n = section.note { Text(n).font(.outfit(13, .semibold)).foregroundStyle(OC.muted).lineLimit(1) }
                Spacer(minLength: 4)
                if let strum = section.strum { strumChip(strum) }
            }
            if !section.bars.isEmpty { bars }
            if !section.lines.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(section.lines.enumerated()), id: \.offset) { i, line in
                        if editing {
                            Button { onEditLine(i) } label: {
                                HStack(alignment: .center, spacing: 8) {
                                    SongLyricLine(line: line, style: style, shown: shown)
                                    Image(systemName: "pencil").font(.system(size: 14, weight: .semibold)).foregroundStyle(OC.goldText)
                                }
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 12).strokeBorder(OC.border, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityHint("Edit this line")
                        } else {
                            SongLyricLine(line: line, style: style, shown: shown)
                        }
                    }
                }
            }
            if editing && section.kind != .flow {
                Button(action: onAddLine) {
                    Label("Add line", systemImage: "plus").font(.outfit(14, .bold)).foregroundStyle(OC.goldText)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            if !section.flow.isEmpty { flow }
        }
        .padding(isChorus ? 14 : 0)
        .background {
            if isChorus { RoundedRectangle(cornerRadius: 20, style: .continuous).fill(OC.goldTint) }
        }
    }

    private func strumChip(_ strum: String) -> some View {
        Button(action: onStrum) {
            HStack(spacing: 6) {
                Text("STRUM").font(.outfit(10, .heavy)).tracking(0.6).foregroundStyle(OC.muted)
                Text(strum).font(.mono(13)).tracking(1).foregroundStyle(OC.ink)
            }
            .padding(.horizontal, 10)
            .frame(height: 28)
            .background(Capsule().fill(OC.glass))
            .overlay(Capsule().strokeBorder(OC.border, lineWidth: 1))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .fixedSize()
        .accessibilityLabel("Strumming pattern " + strum.replacingOccurrences(of: "↓", with: "down ").replacingOccurrences(of: "↑", with: "up "))
        .padding(.vertical, -8)
    }

    private var bars: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
            ForEach(Array(section.bars.enumerated()), id: \.offset) { _, bar in
                let sh = shown(bar)
                VStack(alignment: .leading) {
                    Text(style.showChords ? sh.name : " ").font(.mono(15)).foregroundStyle(sh.isEasy ? style.easyColor : style.chordColor)
                    Spacer(minLength: 0)
                    Text("/ / / /").font(.outfit(11)).tracking(3).foregroundStyle(OC.faint)
                }
                .padding(.horizontal, 8).padding(.vertical, 6)
                .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52, alignment: .leading)
                .glass(radius: 14)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Bar, \(sh.name), 4 beats")
            }
        }
    }

    private var flow: some View {
        SongWrapLayout(rowSpacing: 6) {
            ForEach(Array(section.flow.enumerated()), id: \.offset) { _, step in
                let (bg, fg) = SectionTag.colors(step.kind)
                Text(step.label)
                    .font(.outfit(13, .bold))
                    .foregroundStyle(fg)
                    .padding(.horizontal, 11).padding(.vertical, 7)
                    .background(Capsule().fill(bg))
                    .layoutValue(key: SongGapKey.self, value: 6)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Key & capo sheet (4b / 4c)

fileprivate struct SongKeySheet: View {
    let song: Song
    @Environment(\.dismiss) private var dismiss
    private let store = SongPlayStore.shared

    var body: some View {
        let bind = store.binding(for: song.id)
        let s = bind.wrappedValue
        let d = SongDisplay(song: song, s: s)
        let options = ChordTheory.capoOptions(chords: song.usedChords, songKey: song.key, transpose: s.transpose, current: s.capo)
        let fewest = options.map(\.barres).min() ?? 0

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Key & capo").font(.outfit(22, .bold)).tracking(-0.4).foregroundStyle(OC.ink)
                    Spacer()
                    Button("Done") { dismiss() }
                        .font(.outfit(14, .bold))
                        .foregroundStyle(songOnInk)
                        .padding(.horizontal, 14).frame(height: 36)
                        .background(Capsule().fill(OC.ink))
                        .buttonStyle(.plain)
                }

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Sounds in").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                        Text(d.shiftNote).font(.outfit(12)).foregroundStyle(OC.muted)
                    }
                    Spacer(minLength: 0)
                    stepButton("−", label: "Key down") { shift(bind, -1) }
                    Text(d.soundKey).font(.mono(22)).foregroundStyle(OC.goldText).frame(width: 48)
                        .contentTransition(.numericText())
                    stepButton("+", label: "Key up") { shift(bind, 1) }
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 16).fill(OC.surface))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(OC.border, lineWidth: 1))

                VStack(alignment: .leading, spacing: 8) {
                    Text("PLAY IT WITH").font(.outfit(12, .bold)).tracking(1).foregroundStyle(OC.muted)
                    ForEach(options) { o in
                        capoRow(o, on: o.capo == s.capo, best: o.barres == fewest) {
                            withAnimation(.snappy) { bind.wrappedValue.capo = o.capo }
                        }
                    }
                }

                Toggle(isOn: Binding(get: { bind.wrappedValue.easy }, set: { v in withAnimation(.snappy) { bind.wrappedValue.easy = v } })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Easy chords").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                        Text("Swap barre chords for simpler shapes (F → Fmaj7, Bm → Bm7). Same feel, fewer fingers.")
                            .font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(1.5)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .toggleStyle(.oc)
                .accessibilityLabel("Easy chords")
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 16).fill(OC.surface))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(OC.border, lineWidth: 1))
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 30)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(620), .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(OC.background)
    }

    private func shift(_ bind: Binding<SongPlayStore.Settings>, _ by: Int) {
        withAnimation(.snappy) {
            bind.wrappedValue.transpose = (bind.wrappedValue.transpose + by + 12) % 12
            bind.wrappedValue.capo = 0
        }
    }

    private func stepButton(_ text: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.outfit(20)).foregroundStyle(OC.ink)
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(OC.background))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(OC.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func capoRow(_ o: ChordTheory.CapoOption, on: Bool, best: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Circle()
                    .strokeBorder(on ? OC.ink : Color(hex: 0xC9C6C0), lineWidth: 2)
                    .frame(width: 22, height: 22)
                    .overlay(Circle().fill(on ? OC.ink : .clear).frame(width: 10, height: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(o.capo > 0 ? "Capo \(o.capo) · \(o.shapeKey) shapes" : "No capo · \(o.shapeKey) shapes")
                        .font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                    Text(o.shapes.joined(separator: "  "))
                        .font(.mono(12)).foregroundStyle(OC.goldText).lineLimit(1).minimumScaleFactor(0.8)
                }
                Spacer(minLength: 4)
                Text(best ? "Easiest" : "\(o.barres) barre")
                    .font(.outfit(11, .heavy))
                    .foregroundStyle(best ? OC.tealText : OC.muted)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Capsule().fill(best ? OC.tealTint : OC.softFill))
                    .fixedSize()
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 16).fill(on ? OC.goldTint : OC.surface))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(on ? OC.gold : OC.border, lineWidth: on ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

// MARK: - More sheet (4f)

fileprivate enum SongMoreAction { case details, lyrics, note, print, image, jam, save, report }

/// A line in a section (nil: a new line at the end).
fileprivate struct SongLineRef: Identifiable, Hashable {
    let sectionID: String
    let line: Int?
    var id: String { "\(sectionID)-\(line.map(String.init) ?? "new")" }
}

fileprivate struct SongMoreSheet: View {
    let song: Song
    let keyName: String
    let hasNote: Bool
    let inLibrary: Bool
    let canEditDetails: Bool
    let onAction: (SongMoreAction) -> Void

    private var shareURL: URL {
        let id = song.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "song"
        return URL(string: "https://ochords.app/s/\(id)") ?? URL(string: "https://ochords.app")!
    }

    private var rowCount: Int { (inLibrary ? 5 : 6) + (canEditDetails ? 2 : 0) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Text(song.title).font(song.titleFont(18)).foregroundStyle(OC.ink).lineLimit(1)
                Text("Key \(keyName)").font(.outfit(13)).foregroundStyle(OC.muted)
            }
            .padding(.horizontal, 4).padding(.top, 6).padding(.bottom, 8)

            if canEditDetails {
                row(0, icon: "character.cursor.ibeam", title: "Title & artist", subtitle: "Fix what the scan read") { onAction(.details) }
                row(1, icon: "text.cursor", title: "Edit lyrics", subtitle: "Change words; chords stay with them") { onAction(.lyrics) }
            }
            row(canEditDetails ? 2 : 0, icon: "pencil", title: hasNote ? "Edit my note" : "Add my note", subtitle: "Only you can see it") { onAction(.note) }
            row(1, icon: "printer", title: "Print or save PDF", subtitle: "A4 or US Letter, 1 or 2 columns") { onAction(.print) }
            row(2, icon: "photo", title: "Save as image", subtitle: "For Instagram, Facebook or chat") { onAction(.image) }
            ShareLink(item: shareURL, subject: Text(song.title),
                      message: Text("\(song.title) — \(song.artist) · Key \(keyName) · on O Chords")) {
                rowLabel(3, icon: "square.and.arrow.up", title: "Share link", subtitle: "Anyone with O Chords can open it")
            }
            .buttonStyle(.plain)
            if !inLibrary {
                row(5, icon: "tray.and.arrow.down", title: "Save to my library", subtitle: "Keep it offline with your key and notes") { onAction(.save) }
            }
            row(6, icon: "flag", title: "Report a problem", subtitle: "Wrong chords, copyright or spam", destructive: true) { onAction(.report) }
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 20)
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.height(CGFloat(92 + rowCount * 59))])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(OC.background)
    }

    private func row(_ i: Int, icon: String, title: String, subtitle: String, destructive: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(i, icon: icon, title: title, subtitle: subtitle, destructive: destructive) }
            .buttonStyle(.plain)
    }

    private func rowLabel(_ i: Int, icon: String, title: String, subtitle: String, destructive: Bool = false) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(destructive ? OC.red : OC.ink)
                .frame(width: 38, height: 38)
                .background(RoundedRectangle(cornerRadius: 12).fill(destructive ? OC.redTint : OC.softFill))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.outfit(15, .bold)).foregroundStyle(destructive ? OC.red : OC.ink)
                Text(subtitle).font(.outfit(12)).foregroundStyle(OC.muted)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
        .frame(minHeight: 58)
        .contentShape(Rectangle())
        .overlay(alignment: .top) { if i > 0 { Rectangle().fill(OC.line).frame(height: 1) } }
    }
}

// MARK: - Stage mode

fileprivate struct SongStageItem: Identifiable {
    let id: Int
    let section: SongSection
    let extra: String?
    let beats: Int
}

/// Play order: every section once, then the "Then play" flow (e.g. Chorus → Verse 2 → Music → Chorus ×2).
fileprivate enum SongStagePlan {
    static func make(_ song: Song) -> [SongStageItem] {
        let playable = song.sections.filter { $0.kind != .flow }
        var out: [SongStageItem] = []

        func beats(_ s: SongSection, times: Int) -> Int {
            // Lines marked "( … ) x2" are played twice.
            let lineBars = ChordFollower.playOrder(lines: s.lines).reduce(0) { $0 + max(1, ChordLine.chords(in: s.lines[$1.line]).count) }
            let bars = s.bars.isEmpty ? lineBars : s.bars.count
            return max(4, bars * 4 * times)
        }
        func times(_ text: String) -> (base: String, n: Int) {
            guard let r = text.range(of: "×") else { return (text, 1) }
            let n = Int(text[r.upperBound...].prefix(while: \.isNumber)) ?? 1
            return (text[..<r.lowerBound].trimmingCharacters(in: .whitespaces), n)
        }

        for s in playable {
            out.append(SongStageItem(id: out.count, section: s, extra: s.note, beats: beats(s, times: times(s.note ?? "").n)))
        }
        for f in song.sections where f.kind == .flow {
            for step in f.flow {
                let parts = step.label.split(separator: "·", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
                let (base, n) = times(parts.first ?? step.label)
                guard let sec = playable.first(where: { $0.label.caseInsensitiveCompare(base) == .orderedSame })
                        ?? playable.first(where: { $0.kind == step.kind }) else { continue }
                var extras: [String] = []
                if n > 1 { extras.append("×\(n)") }
                if parts.count > 1 { extras.append(parts[1]) }
                out.append(SongStageItem(id: out.count, section: sec, extra: extras.isEmpty ? nil : extras.joined(separator: " · "), beats: beats(sec, times: n)))
            }
        }
        return out
    }
}

struct StageView: View {
    let song: Song
    @Environment(AppState.self) private var app
    private let store = SongPlayStore.shared

    @State private var index = 0
    @State private var progress: Double = 0
    @State private var playing = true
    @State private var locked = false
    @State private var position = ScrollPosition(edge: .top)
    @State private var maxOffset: CGFloat = 0
    @FocusState private var focused: Bool
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    /// Follow me: the lyrics move as the player strums, instead of at a set tempo.
    @AppStorage("stage.followMe") private var followMode = false
    @State private var listener = ChordListener()
    /// The chord the player is on (follow mode), nil until the first chord is heard.
    @State private var followed: ChordFollower.Step?
    @State private var micOff = false
    /// Hands-free page turns by nodding or speaking (a pedal is a keyboard: see the key handlers).
    @State private var nod: HeadNodWatcher?
    @State private var voice = VoiceCommands()
    /// When the right pedal went down, to tell a hold (start / stop) from a press (next).
    @State private var pedalDown: Date?

    private var plan: [SongStageItem] { SongStagePlan.make(song) }
    private var settings: Binding<SongPlayStore.Settings> { store.binding(for: song.id) }
    private var display: SongDisplay { SongDisplay(song: song, s: store.settings(for: song.id)) }

    var body: some View {
        let items = plan
        let d = display
        let cur = items.isEmpty ? nil : items[min(index, items.count - 1)]
        VStack(spacing: 0) {
            header(d)
            progressBar(items).padding(.horizontal, 20).padding(.top, 12)
            ScrollView {
                if let cur {
                    current(cur, next: index + 1 < items.count ? items[index + 1] : nil, d: d)
                        .padding(.horizontal, 20)
                        .padding(.top, 22)
                        .padding(.bottom, 24)
                        .id(cur.id)
                        .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .opacity))
                }
            }
            .scrollPosition($position)
            .onScrollGeometryChange(for: CGFloat.self) { max(0, $0.contentSize.height - $0.containerSize.height) } action: { _, v in maxOffset = v }
            .contentShape(Rectangle())
            .simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { v in
                guard abs(v.translation.width) > abs(v.translation.height), abs(v.translation.width) > 60 else { return }
                v.translation.width < 0 ? step(1) : step(-1)
            })
            bottomPanel(d, cur: cur)
        }
        .background(NightBackground())
        .overlay { if locked { lockOverlay } }
        .environment(\.colorScheme, .dark)
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden(locked)
        .focusable()
        .focused($focused)
        .focusEffectDisabled()
        // Page-turn pedals send arrow or page keys: right / down / page down = next, left / up / page up = back,
        // holding the right pedal starts or stops the scroll.
        .onKeyPress(keys: [.rightArrow, .downArrow, .pageDown], phases: [.down, .repeat, .up]) { press in
            pedalNext(press.phase)
            return .handled
        }
        .onKeyPress(keys: [.leftArrow, .upArrow, .pageUp], phases: .down) { _ in step(-1); return .handled }
        .onKeyPress(.space) { playing.toggle(); return .handled }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
            focused = true
            updateFollowing()
            updateHandsFree()
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            listener.stop()
            nod?.stop()
            voice.stop()
        }
        .task(id: playing) { await run() }
        .onChange(of: playing) { _, _ in updateFollowing() }
        .onChange(of: scenePhase) { _, _ in updateFollowing(); updateHandsFree() }
        .onChange(of: followMode) { _, _ in
            playing = false
            followed = nil
            listener.stop()
            updateHandsFree()
        }
        .sensoryFeedback(.selection, trigger: index)
    }

    // MARK: Pieces

    private func header(_ d: SongDisplay) -> some View {
        HStack(spacing: 0) {
            Button { app.pop() } label: {
                Image(systemName: "xmark").font(.system(size: 19, weight: .semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .accessibilityLabel("Exit stage mode")
            .frame(width: 88, alignment: .leading)
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                Text(song.title).font(song.isMyanmar ? .myanmar(15, .semibold) : .outfit(15, .semibold)).lineLimit(1)
                Text("Key \(d.soundKey) · Capo \(d.s.capo)\(d.s.easy ? " · Easy" : "")").font(.outfit(12)).foregroundStyle(OC.nightMuted)
            }
            Spacer(minLength: 0)
            HStack(spacing: 0) {
                Button { playing = false; app.push(.handsFree) } label: {
                    SongHandsFreeIcon(mode: store.handsFree, size: 19)
                        .foregroundStyle(OC.gold)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .accessibilityLabel("Hands-free page turns")
                Button { withAnimation { locked = true } } label: {
                    Image(systemName: "lock").font(.system(size: 18))
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .accessibilityLabel("Lock screen")
            }
            .frame(width: 88, alignment: .trailing)
        }
        .buttonStyle(.plain)
        .foregroundStyle(OC.nightText)
        .padding(.horizontal, 10)
    }

    private func progressBar(_ items: [SongStageItem]) -> some View {
        GeometryReader { geo in
            let total = max(1, items.reduce(0) { $0 + $1.beats })
            let gaps = CGFloat(max(0, items.count - 1)) * 4
            HStack(spacing: 4) {
                ForEach(items) { it in
                    let w = max(4, (geo.size.width - gaps) * CGFloat(it.beats) / CGFloat(total))
                    Capsule()
                        .fill(it.id < index ? OC.gold : it.id == index ? Color(hex: 0x7A5A1C) : OC.nightGlass)
                        .overlay(alignment: .leading) {
                            if it.id == index { Capsule().fill(OC.gold).frame(width: w * progress) }
                        }
                        .overlay { if it.id > index { Capsule().strokeBorder(OC.nightBorder, lineWidth: 1) } }
                        .frame(width: w, height: 4)
                        .frame(height: 20)
                        .contentShape(Rectangle())
                        .onTapGesture { jump(to: it.id) }
                }
            }
        }
        .frame(height: 20)
        .accessibilityElement()
        .accessibilityLabel("Section \(index + 1) of \(items.count)")
        .accessibilityAdjustableAction { dir in step(dir == .increment ? 1 : -1) }
    }

    private func current(_ it: SongStageItem, next: SongStageItem?, d: SongDisplay) -> some View {
        let style = SongLineStyle(chordSize: 19, lyricSize: 24, chordColor: OC.gold, easyColor: OC.teal, lyricColor: OC.nightText, myanmar: song.isMyanmar)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                SongSectionPill(label: it.section.label, kind: it.section.kind, size: 13)
                if let e = it.extra { Text(e).font(.outfit(14, .semibold)).foregroundStyle(OC.nightMuted) }
            }
            if !it.section.bars.isEmpty {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                    ForEach(Array(it.section.bars.enumerated()), id: \.offset) { bi, b in
                        let sh = d.shown(b)
                        VStack(alignment: .leading, spacing: 8) {
                            Text(sh.name).font(.mono(24)).foregroundStyle(sh.isEasy ? OC.teal : OC.gold).minimumScaleFactor(0.6).lineLimit(1)
                            Text("/ / / /").font(.outfit(12)).tracking(3).foregroundStyle(OC.nightMuted)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, minHeight: 78, alignment: .leading)
                        .nightGlass(radius: 16)
                        .overlay {
                            if following(it), followed?.bar == bi {
                                RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(OC.gold, lineWidth: 2)
                            }
                        }
                    }
                }
            }
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(it.section.lines.enumerated()), id: \.offset) { li, line in
                    let here = following(it) ? followed?.line : nil
                    SongLyricLine(line: line, style: style, shown: d.shown)
                        .padding(.leading, followMode ? 12 : 0)
                        .overlay(alignment: .leading) {
                            if here == li { Capsule().fill(OC.gold).frame(width: 4).padding(.vertical, 4) }
                        }
                        .overlay(alignment: .topTrailing) {
                            // Which time through a repeated line ("( … ) x2").
                            if here == li, let f = followed, f.passes > 1 {
                                Text("\(Self.ordinal(f.pass)) time · ×\(f.passes)")
                                    .font(.outfit(12, .heavy)).foregroundStyle(OC.inkFixed)
                                    .padding(.horizontal, 8).frame(height: 22)
                                    .background(Capsule().fill(OC.gold))
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                        .opacity(here == nil || here == li ? 1 : 0.45)
                        .animation(.easeInOut(duration: 0.25), value: here)
                        .id("line-\(li)")
                        .contentShape(Rectangle())
                        .onTapGesture { if followMode { resume(item: it.id, line: li) } }
                        .accessibilityAddTraits(here == li ? .isSelected : [])
                }
            }
            nextPreview(next, d: d).padding(.top, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private func nextPreview(_ next: SongStageItem?, d: SongDisplay) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(next.map { "NEXT · \($0.section.label.uppercased())" + ($0.extra.map { " · \($0.uppercased())" } ?? "") } ?? "LAST SECTION")
                .font(.outfit(12, .heavy)).tracking(0.6)
                .foregroundStyle(OC.nightMuted)
                .padding(.horizontal, 9).padding(.vertical, 4)
                .overlay(Capsule().strokeBorder(Color(hex: 0x393733), lineWidth: 1))
            if let n = next {
                if let first = n.section.lines.first {
                    SongLyricLine(line: first,
                                  style: SongLineStyle(chordSize: 15, lyricSize: 18, chordColor: Color(hex: 0xA07A2E), easyColor: OC.teal.opacity(0.7), lyricColor: OC.faint, myanmar: song.isMyanmar),
                                  shown: d.shown)
                } else if !n.section.bars.isEmpty {
                    Text(n.section.bars.map { d.shown($0).name }.joined(separator: "   "))
                        .font(.mono(15)).foregroundStyle(Color(hex: 0xA07A2E))
                }
            }
        }
        .onTapGesture { step(1) }
    }

    private func bottomPanel(_ d: SongDisplay, cur: SongStageItem?) -> some View {
        let sectionChords = Set((cur.map { $0.section.bars + $0.section.lines.flatMap(ChordLine.chords(in:)) } ?? []).map { d.shown($0).name })
        // Following: light up the chord being played; otherwise the current section's chords.
        let active = followMode ? Set(followed.map { [shownName($0)] } ?? []) : sectionChords
        let speed = settings.wrappedValue.stageSpeed
        return VStack(spacing: 14) {
            ViewThatFits(in: .horizontal) {
                chordRow(d.usedShown, active: active)
                ScrollView(.horizontal, showsIndicators: false) { chordRow(d.usedShown, active: active) }
            }
            modeSwitch
            HStack(spacing: 10) {
                Button { playing.toggle() } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.system(size: 19))
                        .foregroundStyle(OC.inkFixed)
                        .frame(width: 52, height: 52)
                        .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                        .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(followMode ? (playing ? "Stop listening" : "Start listening") : (playing ? "Pause auto-scroll" : "Start auto-scroll"))
                if followMode {
                    followStatus
                } else {
                    VStack(spacing: 6) {
                        HStack {
                            Text("Auto-scroll")
                            Spacer()
                            Text(String(format: "%.1f× · %d BPM", speed, Int((Double(song.tempo) * speed).rounded()))).font(.mono(12, .semibold))
                        }
                        .font(.outfit(12)).foregroundStyle(OC.nightMuted)
                        SongSpeedBar(value: Binding(get: { settings.wrappedValue.stageSpeed }, set: { settings.wrappedValue.stageSpeed = $0 }),
                                     fill: Color(hex: 0xD6D4CF))
                    }
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 16)
        .background(Color(hex: 0x0B0A09).opacity(0.92).ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1) }
    }

    /// Auto-scroll (set tempo) or Follow me (listens to the strumming).
    private var modeSwitch: some View {
        HStack(spacing: 6) {
            ForEach([false, true], id: \.self) { follow in
                let on = followMode == follow
                Button { followMode = follow } label: {
                    Label(follow ? "Follow me" : "Auto-scroll", systemImage: follow ? "waveform" : "arrow.down.to.line")
                        .font(.outfit(13, .bold))
                        .foregroundStyle(on ? OC.inkFixed : OC.nightText)
                        .frame(maxWidth: .infinity).frame(height: 34)
                        .background(Capsule().fill(on ? OC.gold : Color.white.opacity(0.08)))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private var followStatus: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(micOff ? "Microphone is off" : !playing ? "Follow me" : followed == nil ? "Listening…" : "Following you")
                .font(.outfit(14, .bold)).foregroundStyle(micOff ? OC.red : OC.nightText)
            Group {
                if micOff {
                    Button("Allow it in Settings ›") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                        .buttonStyle(.plain).foregroundStyle(OC.gold)
                } else if !playing {
                    Text("Tap ▶ and play — the lyrics follow your chords")
                } else if followed == nil {
                    Text("Strum the first chord · tap a line to start there")
                } else {
                    Text("Playing \(followed.map(shownName) ?? "") · tap a line if it loses you")
                }
            }
            .font(.outfit(12)).foregroundStyle(OC.nightMuted).lineLimit(1).minimumScaleFactor(0.85)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func chordRow(_ chords: [ChordTheory.Shown], active: Set<String>) -> some View {
        HStack(spacing: 4) {
            ForEach(chords, id: \.name) { sh in
                VStack(spacing: 2) {
                    Text(sh.name).font(.mono(13)).foregroundStyle(sh.isEasy ? OC.teal : OC.gold)
                    if let v = ChordLibrary.info(sh.name)?.voicings.first {
                        SongMiniDiagram(voicing: v, width: 54, leftHanded: app.leftHanded, dark: true, showMarkers: true,
                                        dotColor: sh.isEasy ? OC.teal : OC.gold)
                    }
                }
                .frame(width: 64)
                .opacity(active.isEmpty || active.contains(sh.name) ? 1 : 0.35)
                .animation(.easeInOut(duration: 0.3), value: active)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Chord \(sh.name)")
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var lockOverlay: some View {
        Color.black.opacity(0.001)
            .ignoresSafeArea()
            .overlay(alignment: .bottom) {
                Label("Locked · hold to unlock", systemImage: "lock.fill")
                    .font(.outfit(13, .semibold))
                    .foregroundStyle(OC.nightText)
                    .padding(.horizontal, 16).frame(height: 40)
                    .background(Capsule().fill(Color.white.opacity(0.12)))
                    .padding(.bottom, 200)
            }
            .onLongPressGesture(minimumDuration: 0.8) { withAnimation { locked = false } }
            .accessibilityElement()
            .accessibilityLabel("Screen locked")
            .accessibilityAction(named: "Unlock") { locked = false }
    }

    // MARK: Playback

    private func step(_ by: Int) {
        let n = plan.count
        guard n > 0 else { return }
        jump(to: min(max(0, index + by), n - 1))
    }

    private func jump(to i: Int) {
        guard i != index else { progress = 0; return }
        withAnimation(.snappy(duration: 0.3)) { index = i }
        progress = 0
        position.scrollTo(edge: .top)
        // The player moved by hand: follow on from the start of that section.
        if followMode { resume(item: i, line: nil) }
    }

    // MARK: Hands-free

    private func pedalNext(_ phase: KeyPress.Phases) {
        switch phase {
        case .down:
            pedalDown = .now
        case .repeat:
            // Held: start or stop the scroll, once per hold.
            if let down = pedalDown, Date.now.timeIntervalSince(down) > 0.6 { pedalDown = nil; playing.toggle() }
        default:
            if pedalDown != nil { step(1) }
            pedalDown = nil
        }
    }

    /// Runs the camera or speech watcher for the chosen hands-free mode while Stage is in front.
    private func updateHandsFree() {
        let mode = store.handsFree, active = scenePhase == .active
        if mode == .nod, active, HeadNodWatcher.permission == .authorized {
            if nod == nil {
                let watcher = HeadNodWatcher(downSign: store.nodDownSign)
                watcher.onMove = { move in step(move == .down ? 1 : -1) }
                nod = watcher
            }
            nod?.start()
        } else {
            nod?.stop()
        }
        // Follow me listens to the guitar on the same microphone, so voice turns wait while it's on.
        if mode == .voice, active, !followMode, VoiceCommands.hasPermission {
            voice.onCommand = { command in obey(command) }
            try? voice.start()
        } else {
            voice.stop()
        }
    }

    private func obey(_ command: VoiceCommands.Command) {
        switch command {
        case .next: step(1)
        case .back: step(-1)
        case .stop: playing = false
        case .play: playing = true
        case .chorus:
            // The next chorus (or the first, from the last one).
            let items = plan
            let later = items.first { $0.id > index && $0.section.kind == .chorus } ?? items.first { $0.section.kind == .chorus }
            if let later { jump(to: later.id) }
        }
    }

    // MARK: Follow me

    private func following(_ it: SongStageItem) -> Bool { followMode && followed?.item == it.id }

    private static func ordinal(_ n: Int) -> String { n == 1 ? "1st" : n == 2 ? "2nd" : n == 3 ? "3rd" : "\(n)th" }

    /// A chord as the player's fingers see it (after capo and easy swaps), from how it sounds.
    private func shownName(_ step: ChordFollower.Step) -> String {
        let s = settings.wrappedValue
        return ChordTheory.shown(step.chord, transpose: 0, capo: s.capo, easy: s.easy).name
    }

    /// Every chord in play order — sections repeated as the plan says (×2) — as it sounds after transposing.
    private func followSteps(_ items: [SongStageItem]) -> [ChordFollower.Step] {
        let t = settings.wrappedValue.transpose
        return items.flatMap { it -> [ChordFollower.Step] in
            let repeats = it.extra.flatMap { e in e.firstMatch(of: /×\s*(\d+)/).flatMap { Int($0.1) } } ?? 1
            let once = it.section.bars.enumerated().map { ChordFollower.Step(chord: ChordTheory.transpose($0.element, by: t), item: it.id, line: nil, bar: $0.offset) }
                + ChordFollower.playOrder(lines: it.section.lines).flatMap { li, pass, passes in
                    ChordLine.chords(in: it.section.lines[li]).map {
                        ChordFollower.Step(chord: ChordTheory.transpose($0, by: t), item: it.id, line: li, pass: pass, passes: passes)
                    }
                }
            return Array(repeating: once, count: max(1, repeats)).flatMap { $0 }
        }
    }

    /// Listens while Follow me is on, playing and the app is in front.
    private func updateFollowing() {
        let items = plan
        guard followMode, playing, scenePhase == .active, !items.isEmpty else { listener.stop(); return }
        guard !listener.isRunning else { return }
        if MicInput.permissionUndecided {
            Task { @MainActor in
                if await MicInput.requestPermission() { updateFollowing() } else { micOff = true; playing = false }
            }
            return
        }
        guard MicInput.hasPermission else { micOff = true; playing = false; return }
        micOff = false
        listener.onMove = { step in follow(to: step) }
        let resume = followed.map { ($0.item, $0.line) } ?? (index > 0 ? (index, nil) : nil)
        do { try listener.start(steps: followSteps(items), resumeAt: resume) } catch { micOff = true; playing = false }
    }

    /// The player is on `step`: show its section, highlight its line and keep it in view.
    private func follow(to step: ChordFollower.Step) {
        followed = step
        if step.item != index {
            withAnimation(.snappy(duration: 0.3)) { index = step.item }
            position.scrollTo(edge: .top)
        }
        let lines = plan.first { $0.id == step.item }?.section.lines.count ?? 0
        if let line = step.line, lines > 0 {
            progress = Double(line + 1) / Double(lines)
            withAnimation(.easeInOut(duration: 0.35)) { position.scrollTo(id: "line-\(line)", anchor: UnitPoint(x: 0.5, y: 0.35)) }
        }
    }

    /// Carry on following from a line the player tapped (or the start of a section).
    private func resume(item: Int, line: Int?) {
        listener.resync(item: item, line: line)
        followed = line.map { ChordFollower.Step(chord: followed?.chord ?? "", item: item, line: $0) }
    }

    /// Advances section by section at the song's tempo (4 beats per bar), scrolling long sections along.
    private func run() async {
        guard playing, !followMode else { return }
        let items = plan
        guard !items.isEmpty else { return }
        if index == items.count - 1, progress >= 1 { index = 0; progress = 0 }
        var last = Date.now
        while !Task.isCancelled, playing {
            try? await Task.sleep(for: .milliseconds(50))
            let now = Date.now
            let dt = now.timeIntervalSince(last)
            last = now
            let bpm = Double(max(30, song.tempo)) * settings.wrappedValue.stageSpeed
            let duration = Double(items[index].beats) * 60 / bpm
            progress = min(1, progress + dt / duration)
            if maxOffset > 0 { position.scrollTo(y: maxOffset * max(0, (progress - 0.25) / 0.75)) }
            if progress >= 1 {
                if index < items.count - 1 {
                    jump(to: index + 1)
                } else {
                    playing = false
                    return
                }
            }
        }
    }
}

/// Thin speed bar, 0.5× … 1.5×, draggable.
fileprivate struct SongSpeedBar: View {
    @Binding var value: Double
    var fill: Color = OC.gold

    var body: some View {
        GeometryReader { geo in
            let frac = CGFloat((value - 0.5) / 1.0)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.08)).overlay(Capsule().strokeBorder(OC.nightBorder, lineWidth: 1))
                Capsule().fill(fill).frame(width: max(6, geo.size.width * frac))
            }
            .frame(height: 6)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                let f = min(1, max(0, g.location.x / max(1, geo.size.width)))
                value = ((0.5 + Double(f)) * 10).rounded() / 10
            })
        }
        .frame(height: 24)
        .accessibilityElement()
        .accessibilityLabel("Scroll speed")
        .accessibilityValue(String(format: "%.1f times", value))
        .accessibilityAdjustableAction { dir in
            value = min(1.5, max(0.5, value + (dir == .increment ? 0.1 : -0.1)))
        }
    }
}

// MARK: - Warm up (4d) + drill

fileprivate struct SongChange: Identifiable, Hashable {
    var id: String { a + "→" + b }
    let a: String
    let b: String
    let why: String
}

struct WarmupView: View {
    let song: Song
    @Environment(AppState.self) private var app
    private let store = SongPlayStore.shared

    @State private var selected: String?
    @State private var drill: SongChange?
    @State private var beat = 0
    @State private var doneDrills: Set<String> = []
    @State private var toast: Toast?

    private var display: SongDisplay { SongDisplay(song: song, s: store.settings(for: song.id)) }

    var body: some View {
        let d = display
        let chords = d.usedShown.map(\.name)
        let changes = trickyChanges(d)
        let sel = selected.flatMap { chords.contains($0) ? $0 : nil } ?? chords.first ?? song.key
        let ready = chords.filter { store.knownChords.contains($0) }.count

        VStack(spacing: 0) {
            TopBar(title: "Warm up", backLabel: "Back to song", onBack: { app.pop() }) {
                Button("Skip") { goStage() }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.muted)
                    .padding(.horizontal, 10).frame(minHeight: 44)
                    .buttonStyle(.plain)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    intro(d, chords: chords.count, changes: changes.count)
                    cards(d, selected: sel).padding(.top, 14)
                    if let dr = drill {
                        drillPanel(dr).padding(.top, 12)
                            .transition(.scale(scale: 0.96).combined(with: .opacity))
                    } else {
                        detail(sel, d: d).padding(.top, 12)
                        changeList(changes).padding(.top, 14)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                .animation(.snappy(duration: 0.3), value: drill)
            }
            footer(ready: ready, total: chords.count)
        }
        .ocPage()
        .toast($toast)
        .task(id: drill) { await runDrill() }
        .sensoryFeedback(trigger: beat) { _, new in
            drill == nil || new == 0 ? nil : (new % 4 == 0 ? .impact(weight: .medium) : .impact(weight: .light, intensity: 0.4))
        }
    }

    // MARK: Pieces

    private func intro(_ d: SongDisplay, chords: Int, changes: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Eyebrow(text: "Before you play")
            (Text("Review the chords in ") + Text(song.title).font(song.isMyanmar ? .myanmar(24, .bold) : .outfit(26, .bold)))
                .font(.outfit(26, .bold)).tracking(-0.6)
                .foregroundStyle(OC.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text("\(chords) chords · \(changes) tricky changes · Key \(d.soundKey)\(d.s.capo > 0 ? " · Capo \(d.s.capo)" : "") · about \(max(1, (chords * 15 + changes * 30) / 60)) min")
                .font(.outfit(13)).foregroundStyle(OC.muted)
        }
    }

    private func cards(_ d: SongDisplay, selected sel: String) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
            ForEach(d.usedShown, id: \.name) { sh in
                let on = sh.name == sel
                let known = store.knownChords.contains(sh.name)
                let barre = ChordLibrary.info(sh.name)?.voicings.first?.isBarre ?? ChordTheory.isBarre(sh.name)
                Button { withAnimation(.snappy) { selected = sh.name } } label: {
                    VStack(spacing: 4) {
                        Text(sh.name).font(.mono(14)).foregroundStyle(sh.isEasy ? OC.teal : OC.ink).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer(minLength: 0)
                        if let v = ChordLibrary.info(sh.name)?.voicings.first {
                            SongMiniDiagram(voicing: v, width: 46, leftHanded: app.leftHanded,
                                            dotColor: sh.isEasy ? OC.teal : OC.gold)
                        }
                        Spacer(minLength: 0)
                        Text(known ? "✓" : barre ? "BARRE" : "·")
                            .font(.outfit(10, .heavy))
                            .foregroundStyle(known ? .white : barre ? Color(hex: 0x991B1B) : OC.muted)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 18, minHeight: 18)
                            .background(Capsule().fill(known ? OC.teal : barre ? OC.redTint : OC.softFill))
                    }
                    .padding(.top, 8).padding(.bottom, 6)
                    .frame(maxWidth: .infinity, minHeight: 112, maxHeight: 112)
                    .background {
                        if on {
                            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.goldTint)
                        } else {
                            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.glass)
                        }
                    }
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? OC.gold : OC.glassBorder, lineWidth: on ? 2 : 1))
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(on ? OC.gold.opacity(0.18) : .clear).padding(-4))
                    .shadow(color: .black.opacity(on ? 0 : 0.06), radius: 9, y: 8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(sh.name), \(known ? "got it" : "not reviewed")")
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private func detail(_ name: String, d: SongDisplay) -> some View {
        let info = ChordLibrary.info(name)
        let voicing = info?.voicings.first
        let known = store.knownChords.contains(name)
        let uses = d.sequence.filter { $0 == name }.count
        let hasEasy = !d.s.easy && ChordTheory.easySwaps[String(name.split(separator: "/").first ?? "")] != nil
        return HStack(alignment: .top, spacing: 14) {
            Group {
                if let voicing {
                    ChordDiagram(voicing: voicing, width: 104, leftHanded: app.leftHanded, showFingers: true, showStringNames: false)
                } else {
                    Text("?").font(.mono(28)).foregroundStyle(OC.faint).frame(width: 104, height: 110)
                }
            }
            .padding(.top, 4)
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(name).font(.mono(26)).foregroundStyle(OC.goldText)
                    Text(fullName(name, info: info)).font(.outfit(12)).foregroundStyle(OC.muted).lineLimit(2)
                }
                Text("\(uses)× on this sheet").font(.outfit(12, .bold)).foregroundStyle(OC.muted)
                Text(voicing?.tip ?? "Look it up in the finger guide.")
                    .font(.outfit(13)).foregroundStyle(OC.ink).lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
                if hasEasy {
                    Button("Hard? Turn on Easy chords") {
                        withAnimation(.snappy) { store.binding(for: song.id).wrappedValue.easy = true }
                        toast = Toast(text: "Easy chords on")
                    }
                    .font(.outfit(13, .bold)).foregroundStyle(OC.tealText).buttonStyle(.plain)
                    .frame(minHeight: 30)
                }
                HStack(spacing: 6) {
                    Button { toggleKnown(name, d: d) } label: {
                        Text(known ? "✓ Got it" : "I can play this")
                            .font(.outfit(13, .bold))
                            .foregroundStyle(known ? OC.tealText : songOnInk)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(RoundedRectangle(cornerRadius: 12).fill(known ? OC.tealTint : OC.ink))
                    }
                    .accessibilityAddTraits(known ? .isSelected : [])
                    squareButton("hand.point.up.left", label: "Full finger guide") {
                        app.push(.chords(focus: name, songChords: d.usedShown.map(\.name)))
                    }
                    squareButton("mic", label: "Check with the mic") { app.push(.practice(chord: name)) }
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .glass()
    }

    private func squareButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(OC.ink)
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(OC.softFill))
        }
        .accessibilityLabel(label)
    }

    private func changeList(_ changes: [SongChange]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("TRICKY CHANGES IN THIS SONG").font(.outfit(12, .bold)).tracking(1).foregroundStyle(OC.muted)
                Spacer()
                Text("30 s each").font(.outfit(12)).foregroundStyle(OC.muted)
            }
            if changes.isEmpty {
                Text("No tricky changes — this one’s all open chords.").font(.outfit(13)).foregroundStyle(OC.muted)
            }
            ForEach(changes) { ch in
                HStack(spacing: 10) {
                    (Text(ch.a) + Text(" → ").foregroundStyle(OC.faint) + Text(ch.b))
                        .font(.mono(15)).foregroundStyle(OC.ink).fixedSize()
                    Text(ch.why).font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                    if doneDrills.contains(ch.id) {
                        Text("DONE").font(.outfit(11, .heavy)).foregroundStyle(OC.tealText)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(OC.tealTint))
                    }
                    Button { beat = 0; selected = ch.b; drill = ch } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "play.fill").font(.system(size: 10))
                            Text("Drill")
                        }
                        .font(.outfit(13, .bold))
                        .foregroundStyle(songOnInk)
                        .padding(.horizontal, 12).frame(height: 36)
                        .background(RoundedRectangle(cornerRadius: 12).fill(OC.ink))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Drill \(ch.a) to \(ch.b)")
                }
                .padding(.leading, 12).padding(.trailing, 8).padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 16).fill(OC.glass))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(OC.glassBorder, lineWidth: 1))
                .shadow(color: .black.opacity(0.05), radius: 10, y: 8)
            }
        }
    }

    private func drillPanel(_ ch: SongChange) -> some View {
        let interval = 60 / Double(max(30, song.tempo))
        let left = max(0, 30 - Int(Double(beat) * interval))
        let onA = beat % 8 < 4
        return VStack(spacing: 14) {
            HStack {
                Text("DRILL · \(ch.a) → \(ch.b)").font(.outfit(12, .heavy)).tracking(1.4).foregroundStyle(Color(hex: 0xFFC940))
                Spacer()
                Text(String(format: "0:%02d", left)).font(.mono(14, .semibold)).foregroundStyle(Color(hex: 0xBDB9B3))
                    .contentTransition(.numericText(countsDown: true))
            }
            HStack(spacing: 18) {
                Text(ch.a).font(onA ? .outfit(72, .bold) : .mono(36)).foregroundStyle(onA ? OC.gold : Color(hex: 0x6B6660))
                Image(systemName: "arrow.left.arrow.right").font(.system(size: 22, weight: .semibold)).foregroundStyle(Color(hex: 0x6B6660))
                Text(ch.b).font(onA ? .mono(36) : .outfit(72, .bold)).foregroundStyle(onA ? Color(hex: 0x6B6660) : OC.gold)
            }
            .lineLimit(1).minimumScaleFactor(0.5)
            .frame(maxWidth: .infinity, minHeight: 200)
            .animation(.spring(duration: 0.25), value: onA)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Play \(onA ? ch.a : ch.b)")
            HStack(spacing: 8) {
                ForEach(0..<4, id: \.self) { k in
                    Circle().fill(k == beat % 4 ? OC.gold : Color.white.opacity(0.18)).frame(width: 12, height: 12)
                        .scaleEffect(k == beat % 4 ? 1.2 : 1)
                }
            }
            .animation(.easeOut(duration: 0.1), value: beat)
            .accessibilityHidden(true)
            HStack(spacing: 10) {
                Text("Change on beat 1. " + ch.why).font(.outfit(13)).foregroundStyle(Color(hex: 0xD6D4CF)).lineSpacing(1.5)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(beat / 4)").font(.outfit(22, .heavy)).foregroundStyle(OC.gold).contentTransition(.numericText())
                    Text("SWITCHES").font(.outfit(10, .bold)).foregroundStyle(OC.nightMuted)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.07)))
            Button { drill = nil; beat = 0 } label: {
                Text("Stop drill").font(.outfit(15, .bold)).foregroundStyle(OC.paper)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.white.opacity(0.16), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(OC.inkFixed.opacity(0.92)))
        .shadow(color: OC.inkFixed.opacity(0.25), radius: 20, y: 18)
    }

    private func footer(ready: Int, total: Int) -> some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                GeometryReader { geo in
                    Capsule().fill(OC.softFill)
                        .overlay(alignment: .leading) {
                            Capsule().fill(OC.teal).frame(width: geo.size.width * CGFloat(ready) / CGFloat(max(1, total)))
                        }
                }
                .frame(height: 6)
                .animation(.snappy, value: ready)
                Text("\(ready) of \(total) chords ready").font(.outfit(13, .bold)).foregroundStyle(OC.ink).fixedSize()
            }
            Button { goStage() } label: {
                Label(ready == total ? "All set — start stage mode" : "Start stage mode", systemImage: "viewfinder")
            }
            .buttonStyle(.gold)
        }
        .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 12)
        .background(OC.glass.background(.ultraThinMaterial).ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
    }

    // MARK: Logic

    private func fullName(_ name: String, info: ChordInfo?) -> String {
        guard let info else { return "" }
        if info.name == name { return info.full }
        let parts = name.split(separator: "/")
        return parts.count > 1 ? "\(info.full), \(parts[1]) in the bass" : info.full
    }

    private func toggleKnown(_ name: String, d: SongDisplay) {
        withAnimation(.snappy) {
            if store.knownChords.contains(name) {
                store.knownChords.remove(name)
            } else {
                store.knownChords.insert(name)
                if let next = d.usedShown.map(\.name).first(where: { !store.knownChords.contains($0) }) { selected = next }
            }
        }
    }

    /// Most frequent chord pairs plus any barre change, in order of first appearance (max 3).
    private func trickyChanges(_ d: SongDisplay) -> [SongChange] {
        let seq = d.sequence
        var counts: [String: (a: String, b: String, n: Int, first: Int)] = [:]
        for i in seq.indices.dropLast() where seq[i] != seq[i + 1] {
            let k = seq[i] + "→" + seq[i + 1]
            if let c = counts[k] { counts[k] = (c.a, c.b, c.n + 1, c.first) } else { counts[k] = (seq[i], seq[i + 1], 1, i) }
        }
        func barre(_ c: String) -> Bool { ChordLibrary.info(c)?.voicings.first?.isBarre ?? ChordTheory.isBarre(c) }
        let all = counts.values.sorted { $0.n != $1.n ? $0.n > $1.n : $0.first < $1.first }
        var picked = all.filter { barre($0.a) || barre($0.b) }.prefix(2).map { $0 }
        for c in all where picked.count < 3 && !picked.contains(where: { $0.a == c.a && $0.b == c.b }) { picked.append(c) }
        let known: [String: String] = [
            "C→G/B": "Bass walks down — keep the ring finger moving only one string.",
            "Am→F": "Barre change, the slowest in this song.",
            "F→C": "Back to the top of every line.",
            "Am→Fmaj7": "Keep index and middle down — just slide the ring finger over."
        ]
        return picked.sorted { $0.first < $1.first }.map { c in
            let k = c.a + "→" + c.b
            let why = known[k]
                ?? (barre(c.b) ? "Barre change — set the index first, then the rest."
                    : barre(c.a) ? "Leaving the barre — lift everything at once."
                    : c.b.contains("/") || c.a.contains("/") ? "The bass moves — watch the lowest string."
                    : "Comes up \(c.n)× — move the fingers that change, keep the rest.")
            return SongChange(a: c.a, b: c.b, why: why)
        }
    }

    private func runDrill() async {
        guard let ch = drill else { return }
        let interval = 60 / Double(max(30, song.tempo))
        let total = Int((30 / interval).rounded())
        beat = 0
        while !Task.isCancelled, beat < total {
            try? await Task.sleep(for: .seconds(interval))
            guard !Task.isCancelled, drill == ch else { return }
            beat += 1
        }
        guard !Task.isCancelled else { return }
        doneDrills.insert(ch.id)
        toast = Toast(text: "\(ch.a) → \(ch.b) · \(beat / 4) switches")
        drill = nil
    }

    private func goStage() {
        drill = nil
        app.pop()
        app.push(.stage(song))
    }
}

// MARK: - Hands-free (pedal / head nod / auto / voice)

struct HandsFreeView: View {
    @Environment(AppState.self) private var app
    private let store = SongPlayStore.shared

    @Environment(\.openURL) private var openURL
    @AppStorage("stage.followMe") private var followMode = false
    @State private var pedalName = PedalStatus.connectedName
    /// The last pedal press, to show that it works.
    @State private var lastPedal: String?
    @State private var nod: HeadNodWatcher?
    @State private var faceFound = false
    @State private var learningNod = false
    @State private var lastNod: NodGesture.Move?
    @State private var voice = VoiceCommands()
    @State private var heard: String?
    @State private var localSpeed = 1.0
    @FocusState private var focused: Bool

    /// The song open in Stage underneath, for tempo and speed.
    private var stageSong: Song? {
        for r in (app.paths[app.tab] ?? []).reversed() { if case .stage(let s) = r { return s } }
        return nil
    }

    var body: some View {
        let mode = store.handsFree
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button { app.pop() } label: {
                    Image(systemName: "xmark").font(.system(size: 19, weight: .semibold))
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to stage")
                Spacer()
                Text("Hands-free").font(.outfit(16, .bold))
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .foregroundStyle(OC.nightText)
            .padding(.horizontal, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 4) {
                        Eyebrow(text: "Keep both hands on the guitar", color: OC.nightMuted)
                        Text("Turn pages without touching").font(.outfit(26, .bold)).tracking(-0.6).foregroundStyle(OC.nightText)
                    }
                    .padding(.top, 6)

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                        ForEach(SongHandsFreeMode.allCases) { m in modeCard(m, on: m == mode) }
                    }
                    .padding(.top, 14)

                    Group {
                        switch mode {
                        case .pedal: pedalPanel
                        case .nod: nodPanel
                        case .auto: autoPanel
                        case .voice: voicePanel
                        }
                    }
                    .padding(.top, 12)
                    .transition(.opacity)
                    .id(mode)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
                .animation(.snappy(duration: 0.25), value: mode)
            }

            VStack(spacing: 6) {
                Button("Back to the song") { app.pop() }.buttonStyle(.gold)
                Text("Tip: you can use a pedal and nods together").font(.outfit(12)).foregroundStyle(OC.nightMuted)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 12)
        }
        .background(NightBackground())
        .environment(\.colorScheme, .dark)
        .toolbar(.hidden, for: .navigationBar)
        .focusable()
        .focused($focused)
        .focusEffectDisabled()
        .onKeyPress(keys: [.rightArrow, .downArrow, .pageDown]) { _ in pedalPressed("Next section"); return .handled }
        .onKeyPress(keys: [.leftArrow, .upArrow, .pageUp]) { _ in pedalPressed("Previous section"); return .handled }
        .onAppear { focused = true; watch(mode) }
        .onDisappear { nod?.stop(); voice.stop() }
        .onChange(of: store.handsFree) { _, m in watch(m) }
        // Back from the permission prompt.
        .onChange(of: app.modal) { _, m in if m == nil { watch(store.handsFree) } }
        .onReceive(NotificationCenter.default.publisher(for: .GCKeyboardDidConnect)) { _ in pedalName = PedalStatus.connectedName }
        .onReceive(NotificationCenter.default.publisher(for: .GCKeyboardDidDisconnect)) { _ in pedalName = PedalStatus.connectedName }
    }

    private func modeCard(_ m: SongHandsFreeMode, on: Bool) -> some View {
        Button { select(m) } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    SongHandsFreeIcon(mode: m, size: 16)
                        .foregroundStyle(on ? OC.inkFixed : OC.nightText)
                        .frame(width: 34, height: 34)
                        .background(RoundedRectangle(cornerRadius: 11).fill(on ? OC.gold : Color.white.opacity(0.08)))
                    Spacer()
                    if on { Text("IN USE").font(.outfit(10, .heavy)).tracking(0.8).foregroundStyle(Color(hex: 0x5EEAD4)) }
                }
                Text(m.title).font(.outfit(15, .bold)).foregroundStyle(OC.nightText)
                Text(m.subtitle).font(.outfit(12)).foregroundStyle(OC.nightMuted).lineLimit(2)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 86, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(on ? OC.gold.opacity(0.12) : Color.white.opacity(0.05)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(on ? OC.gold : OC.nightBorder, lineWidth: on ? 2 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func panel<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        content()
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .nightGlass(radius: 20)
    }

    private var pedalPanel: some View {
        panel {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    SongHandsFreeIcon(mode: .pedal, size: 18)
                        .foregroundStyle(Color(hex: 0x5EEAD4))
                        .frame(width: 40, height: 40)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0x14B8A6, alpha: 0.18)))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(pedalName ?? "No pedal connected").font(.outfit(15, .bold)).foregroundStyle(OC.nightText)
                        Text(lastPedal.map { "Heard: \($0)" } ?? (pedalName == nil ? "Pair it in Settings › Bluetooth, then press a pedal" : "Connected · press a pedal to test"))
                            .font(.outfit(12)).foregroundStyle(pedalName == nil && lastPedal == nil ? OC.nightMuted : Color(hex: 0x5EEAD4))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                }
                ForEach([("Right pedal", "Next section"), ("Left pedal", "Previous section"), ("Hold right", "Start / stop scroll")], id: \.0) { r in
                    HStack {
                        Text(r.0).foregroundStyle(Color(hex: 0xD6D4CF))
                        Spacer()
                        Text(r.1).fontWeight(.bold).foregroundStyle(OC.nightText)
                    }
                    .font(.outfit(14))
                    .padding(.vertical, 8)
                    .overlay(alignment: .top) { Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1) }
                }
            }
        }
    }

    private var nodPanel: some View {
        panel {
            HStack(spacing: 14) {
                Capsule()
                    .fill(RadialGradient(colors: [Color(hex: 0x3A332A), Color(hex: 0x1B1916)], center: UnitPoint(x: 0.5, y: 0.4), startRadius: 0, endRadius: 70))
                    .frame(width: 104, height: 128)
                    .overlay(Capsule().strokeBorder(faceFound ? OC.gold : Color.white.opacity(0.2), lineWidth: 2))
                    .shadow(color: OC.gold.opacity(faceFound ? 0.3 : 0), radius: 15)
                    .overlay(SongFaceOutline().stroke(OC.nightText.opacity(0.55), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                        .frame(width: 60, height: 76).offset(y: 8))
                    .clipShape(Capsule())
                    .animation(.easeInOut, value: faceFound)
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        Circle().fill(faceFound ? Color(hex: 0x5EEAD4) : .clear)
                            .overlay(Circle().strokeBorder(faceFound ? .clear : OC.nightMuted, lineWidth: 1.5))
                            .frame(width: 9, height: 9)
                        Text(nodStatus)
                    }
                    .font(.outfit(12, .heavy)).tracking(1)
                    .foregroundStyle(faceFound ? Color(hex: 0x5EEAD4) : OC.nightMuted)
                    (Text("Nod down").bold() + Text(lastNod == .down ? " → next section ✓\n" : " → next section\n")
                        + Text("Tilt up").bold() + Text(lastNod == .up ? " → back one ✓" : " → back one"))
                        .font(.outfit(14)).foregroundStyle(OC.nightText).lineSpacing(3)
                        .animation(.snappy, value: lastNod)
                    if HeadNodWatcher.permission == .denied {
                        Button("Allow the camera in Settings ›") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                            .font(.outfit(13, .bold)).foregroundStyle(Color(hex: 0xFFC940)).buttonStyle(.plain).frame(minHeight: 44)
                    } else if HeadNodWatcher.hasCamera {
                        Button(learningNod ? "Nod down now…" : "Set up: nod down once") { learningNod = true; nod?.learnDown() }
                            .font(.outfit(13, .bold)).foregroundStyle(Color(hex: 0xFFC940)).buttonStyle(.plain).frame(minHeight: 44)
                            .disabled(!faceFound)
                    }
                    Text("Uses the front camera on your phone only. Nothing is recorded.")
                        .font(.outfit(12)).foregroundStyle(OC.nightMuted).lineSpacing(1.5)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var autoPanel: some View {
        let speed = Binding<Double>(
            get: { stageSong.map { store.settings(for: $0.id).stageSpeed } ?? localSpeed },
            set: { v in if let s = stageSong { store.binding(for: s.id).wrappedValue.stageSpeed = v } else { localSpeed = v } }
        )
        let tempo = stageSong?.tempo ?? 76
        return panel {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Follow the song’s tempo").font(.outfit(15, .bold)).foregroundStyle(OC.nightText)
                    Spacer()
                    Text("\(Int((Double(tempo) * speed.wrappedValue).rounded())) BPM").font(.mono(20)).foregroundStyle(OC.gold)
                }
                Text("Scrolls one bar at a time, 4 beats per bar. Tap the screen twice to pause.")
                    .font(.outfit(13)).foregroundStyle(Color(hex: 0xD6D4CF)).lineSpacing(1.5)
                    .fixedSize(horizontal: false, vertical: true)
                SongSpeedBar(value: speed)
                HStack {
                    Text("0.5×"); Spacer(); Text("1.0×"); Spacer(); Text("1.5×")
                }
                .font(.outfit(12)).foregroundStyle(OC.nightMuted)
            }
        }
    }

    private var voicePanel: some View {
        panel {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Say one of these").font(.outfit(15, .bold)).foregroundStyle(OC.nightText)
                    Spacer()
                    if voice.isRunning {
                        Label("Listening", systemImage: "waveform")
                            .font(.outfit(12, .bold)).foregroundStyle(Color(hex: 0x5EEAD4))
                            .symbolEffect(.variableColor.iterative, options: .repeating)
                    }
                }
                SongWrapLayout(rowSpacing: 6) {
                    ForEach(["Next", "Back", "Chorus", "Stop"], id: \.self) { w in
                        Text("“\(w)”")
                            .font(.outfit(14, .bold))
                            .foregroundStyle(heard == w ? OC.inkFixed : Color(hex: 0xFFC940))
                            .padding(.horizontal, 12).frame(height: 34)
                            .background(Capsule().fill(heard == w ? OC.gold : OC.gold.opacity(0.16)))
                            .layoutValue(key: SongGapKey.self, value: 6)
                            .animation(.snappy, value: heard)
                    }
                }
                Text(voiceNote)
                    .font(.outfit(12)).foregroundStyle(OC.nightMuted).lineSpacing(1.5)
                    .fixedSize(horizontal: false, vertical: true)
                if VoiceCommands.permissionUndecided == false && !VoiceCommands.hasPermission {
                    Button("Allow speech recognition in Settings ›") { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
                        .font(.outfit(13, .bold)).foregroundStyle(Color(hex: 0xFFC940)).buttonStyle(.plain).frame(minHeight: 44)
                }
            }
        }
    }

    // MARK: Logic

    private func select(_ m: SongHandsFreeMode) {
        store.handsFree = m
        if m == .nod, !store.askedCamera { store.askedCamera = true; app.modal = .permission(.camera) }
        if m == .voice, !store.askedMic { store.askedMic = true; app.modal = .permission(.microphone) }
    }

    private var nodStatus: String {
        if !HeadNodWatcher.hasCamera { return "NO FRONT CAMERA" }
        if HeadNodWatcher.permission == .denied { return "CAMERA IS OFF" }
        if learningNod { return "NOD DOWN NOW…" }
        return faceFound ? "FACE FOUND" : "LOOKING FOR YOUR FACE…"
    }

    private var voiceNote: String {
        if followMode { return "Voice turns pause while Follow me listens to your guitar — switch Stage to Auto-scroll to use them." }
        if !VoiceCommands.available { return "Speech recognition isn’t available on this phone right now." }
        return "In English. Understood on the phone where it can be. Works best between songs or in quiet parts."
    }

    private func pedalPressed(_ what: String) {
        pedalName = PedalStatus.connectedName ?? pedalName
        withAnimation { lastPedal = what }
    }

    /// Runs the real watcher for the chosen mode here too, so the player can try it before going back to the song.
    private func watch(_ mode: SongHandsFreeMode) {
        if mode == .nod, HeadNodWatcher.permission == .authorized {
            if nod == nil {
                let watcher = HeadNodWatcher(downSign: store.nodDownSign)
                watcher.onFace = { faceFound = $0 }
                watcher.onMove = { move in
                    withAnimation { lastNod = move }
                    Task { try? await Task.sleep(for: .seconds(1.2)); if lastNod == move { withAnimation { lastNod = nil } } }
                }
                watcher.onLearnt = { sign in store.nodDownSign = sign; learningNod = false; nod = nil; watch(.nod) }
                nod = watcher
            }
            nod?.start()
        } else {
            nod?.stop()
        }
        guard mode == .voice, !followMode else { voice.stop(); return }
        if VoiceCommands.permissionUndecided {
            Task { @MainActor in if await VoiceCommands.requestPermission() { watch(store.handsFree) } }
            return
        }
        guard VoiceCommands.hasPermission else { return }
        voice.onHeard = { word in
            heard = word
            Task { try? await Task.sleep(for: .seconds(0.8)); if heard == word { heard = nil } }
        }
        try? voice.start()
    }
}

/// Line drawing of a smiling head and shoulders for the head-nod camera preview.
fileprivate struct SongFaceOutline: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let w = r.width, h = r.height
        let head = CGRect(x: r.minX + w * 0.22, y: r.minY, width: w * 0.56, height: w * 0.56)
        p.addEllipse(in: head)
        p.addEllipse(in: CGRect(x: head.minX + head.width * 0.3 - 1.5, y: head.minY + head.height * 0.38, width: 3, height: 3))
        p.addEllipse(in: CGRect(x: head.minX + head.width * 0.7 - 1.5, y: head.minY + head.height * 0.38, width: 3, height: 3))
        p.addArc(center: CGPoint(x: head.midX, y: head.minY + head.height * 0.55), radius: head.width * 0.18,
                 startAngle: .degrees(25), endAngle: .degrees(155), clockwise: false)
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        p.addQuadCurve(to: CGPoint(x: r.maxX, y: r.maxY), control: CGPoint(x: r.midX, y: head.maxY + (r.maxY - head.maxY) * -0.2))
        return p
    }
}

// MARK: - Previews

#Preview("Song") {
    NavigationStack { SongView(song: MockData.sinSarPar) }.environment(AppState())
}

#Preview("Song · dark") {
    NavigationStack { SongView(song: MockData.sinSarParEasy) }.environment(AppState()).preferredColorScheme(.dark)
}

#Preview("Stage") {
    NavigationStack { StageView(song: MockData.sinSarPar) }.environment(AppState())
}

#Preview("Warm up") {
    NavigationStack { WarmupView(song: MockData.sinSarPar) }.environment(AppState())
}

#Preview("Hands-free") {
    NavigationStack { HandsFreeView() }.environment(AppState())
}
