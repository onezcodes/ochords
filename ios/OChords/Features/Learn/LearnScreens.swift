import SwiftUI

// MARK: - Learn group helpers

private extension AppState {
    /// Signed-in profile, or the demo profile for guests.
    var learnProfile: Profile { displayProfile }
}

/// Wood / fret / string colours of the guitar neck — fixed in both themes.
private enum LearnWood {
    static let dark = Color(hex: 0x3A2A1A)
    static let mid = Color(hex: 0x5A4128)
    static let end = Color(hex: 0x4A3420)
    static let nut = Color(hex: 0xEDE6D8)
    static let fretTop = Color(hex: 0xE8E6E1)
    static let fretBottom = Color(hex: 0x9D9A94)
    static let stringLight = Color(hex: 0xF2EEE6)
    static let stringDark = Color(hex: 0xB8B2A6)
}

/// Forces text (not emoji) presentation for the ↔ arrow used in fret titles.
fileprivate extension String {
    var learnGlyphs: String { replacingOccurrences(of: "\u{2194}", with: "\u{2194}\u{FE0E}") }
}

/// Small "── LABEL" eyebrow used across the learning screens.
private struct LearnEyebrow: View {
    let text: String
    var color: Color = OC.muted
    var body: some View {
        HStack(spacing: 8) {
            Rectangle().fill(OC.gold).frame(width: 14, height: 2)
            Text(text).font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(color)
        }
    }
}

/// The guitar-pick glyph used on the current fret.
private struct LearnPickShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: p(12, 21))
        path.addCurve(to: p(5, 9.7), control1: p(8.8, 18.6), control2: p(5, 13.9))
        path.addCurve(to: p(12, 3), control1: p(5, 6), control2: p(8.1, 3))
        path.addCurve(to: p(19, 9.7), control1: p(15.9, 3), control2: p(19, 6))
        path.addCurve(to: p(12, 21), control1: p(19, 13.9), control2: p(15.2, 18.6))
        path.closeSubpath()
        return path
    }
}

/// Tier shield with the roman numeral.
private struct LearnTierShield: View {
    let tier: Tier
    var width: CGFloat = 28
    var body: some View {
        let color = Color(hex: tier.colorHex)
        ZStack {
            LearnShieldShape().fill(color)
            Text(tier.roman)
                .font(.outfit(width * 0.34, .heavy))
                .foregroundStyle(tier == .chordKeeper ? OC.inkFixed : .white)
                .offset(y: -width * 0.04)
        }
        .frame(width: width, height: width * 26 / 24)
        .accessibilityHidden(true)
    }
}

private struct LearnShieldShape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24, sy = rect.height / 26
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        path.move(to: p(12, 1))
        path.addLine(to: p(22, 4.6))
        path.addLine(to: p(22, 11.8))
        path.addCurve(to: p(12, 25), control1: p(22, 18), control2: p(17.7, 22.8))
        path.addCurve(to: p(2, 11.8), control1: p(6.3, 22.8), control2: p(2, 18))
        path.addLine(to: p(2, 4.6))
        path.closeSubpath()
        return path
    }
}

/// Small stat tile on the always-dark screens.
private struct LearnNightStat: View {
    let label: String
    let value: String
    var valueColor: Color = OC.nightText
    var mono = false
    var note: String? = nil
    var centered = false
    var body: some View {
        VStack(alignment: centered ? .center : .leading, spacing: 3) {
            Text(label).font(.outfit(10, .heavy)).tracking(1.1).foregroundStyle(OC.nightMuted)
            Text(value).font(mono ? .mono(20) : .outfit(centered ? 24 : 22, .bold)).foregroundStyle(valueColor)
                .lineLimit(1).minimumScaleFactor(0.7)
                .frame(minHeight: 30)
            if let note { Text(note).font(.outfit(11)).foregroundStyle(OC.nightMuted) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: centered ? .center : .topLeading)
        .padding(.vertical, 12).padding(.horizontal, centered ? 8 : 12)
        .nightGlass()
    }
}

/// Outlined button on dark screens.
private struct LearnNightOutlineStyle: ButtonStyle {
    var height: CGFloat = 52
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(15, .bold))
            .foregroundStyle(OC.nightText)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(configuration.isPressed ? 0.08 : 0.001)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.white.opacity(0.16)))
    }
}

// MARK: - Learn (fretboard path)

private struct LearnFretMeta {
    let short: String
    let sub: String
    let icon: Icon
    enum Icon { case check, pick, lock, star, strum, trophy }
}

struct LearnView: View {
    @Environment(AppState.self) private var app
    @State private var positions: [LearnPosition] = MockData.learnPath
    @State private var selected: Int? = nil

    private var position: LearnPosition {
        positions.first { !$0.frets.isEmpty && $0.frets.contains { $0.state != .done } } ?? MockData.learnPath[1]
    }
    private var frets: [LearnFret] { position.frets }
    private var selIndex: Int {
        let i = selected ?? frets.firstIndex { $0.state == .current } ?? 0
        return min(max(0, i), max(0, frets.count - 1))
    }
    private var previous: LearnPosition? { positions.last { $0.number == position.number - 1 } }
    private var nextPosition: LearnPosition? { positions.first { $0.number == position.number + 1 } }

    var body: some View {
        VStack(spacing: 0) {
            LearnHeader()
            ScrollView {
                VStack(spacing: 14) {
                    heroCard
                    gameCard
                    LearnNeck(frets: frets, selected: selIndex) { i in
                        withAnimation(.snappy(duration: 0.2)) { selected = i }
                    }
                    .padding(.vertical, 4)
                    if let nextPosition { lockedPosition(nextPosition) }
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 150)
            }
            .scrollIndicators(.hidden)
            .tabBarInset()
        }
        .overlay(alignment: .bottom) {
            if frets.indices.contains(selIndex) {
                LearnFretDetail(fret: frets[selIndex], number: selIndex + 1) {
                    app.push(.lesson(fretID: frets[selIndex].id))
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 80)
            }
        }
        .ocPage()
        .task {
            if let p = try? await app.backend.path() { positions = p }
        }
    }

    private var heroCard: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text("POSITION \(position.number) · \(position.subtitle.uppercased())")
                    .font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.gold)
                Text(position.title).font(.outfit(19, .bold)).lineSpacing(2)
                if let previous {
                    Text("Position \(previous.number) “\(previous.title)” · \(previous.subtitle.lowercased())")
                        .font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
                }
            }
            Spacer(minLength: 0)
            Button {
                app.push(.chords(focus: nil, songChords: ["C", "G/B", "Am", "F"]))
            } label: {
                Image(systemName: "book.closed")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(OC.gold)
                    .frame(width: 46, height: 46)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.1)))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.white.opacity(0.14)))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Position guidebook")
        }
        .padding(16)
        .inkGlass()
    }

    private var gameCard: some View {
        Button { app.push(.game) } label: {
            HStack(spacing: 12) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(OC.gold)
                    .frame(width: 50, height: 50)
                    .background(RoundedRectangle(cornerRadius: 16).fill(OC.inkFixed))
                    .shadow(color: OC.inkFixed.opacity(0.2), radius: 9, y: 8)
                VStack(alignment: .leading, spacing: 2) {
                    Text("DAILY GAME · 30 SEC").font(.outfit(11, .bold)).tracking(1.4).foregroundStyle(OC.goldText)
                    Text("Chord Rush").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                    Text("Name today’s 8 shapes · best 180").font(.outfit(12)).foregroundStyle(OC.muted)
                }
                Spacer(minLength: 0)
                Text("Play")
                    .font(.outfit(13, .heavy)).foregroundStyle(OC.inkFixed)
                    .padding(.horizontal, 12).frame(height: 34)
                    .background(Capsule().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
            }
            .padding(14)
            .glass()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Chord Rush, daily game, 30 seconds. Best 180. Play")
    }

    private func lockedPosition(_ p: LearnPosition) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "lock").font(.system(size: 17, weight: .semibold))
            Text("Position \(p.number) · \(p.title)").font(.outfit(14, .semibold))
            Spacer(minLength: 0)
        }
        .foregroundStyle(OC.muted)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 20).fill(OC.glass))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(OC.ink.opacity(0.2)))
    }

    fileprivate static func meta(for fret: LearnFret) -> LearnFretMeta {
        switch fret.id {
        case "f1": LearnFretMeta(short: "Anchor fingers", sub: "Mastered · replay for +5 beats", icon: .check)
        case "f2": LearnFretMeta(short: "C ↔ Am", sub: "Mastered · replay for +5 beats", icon: .check)
        case "f3": LearnFretMeta(short: "C ↔ G/B", sub: "5 takes · about 4 min", icon: fret.state == .done ? .check : .pick)
        case "f4": LearnFretMeta(short: "Am ↔ F", sub: "Clear the frets above to unlock", icon: .lock)
        case "f5": LearnFretMeta(short: "Encore", sub: "Bonus beats and a rest note", icon: .star)
        case "f6": LearnFretMeta(short: "Folk strum", sub: "Practice with the strum trainer", icon: .strum)
        case "f7": LearnFretMeta(short: "The round", sub: "C G Am Em F Dm C G", icon: .lock)
        case "f8": LearnFretMeta(short: "Soundcheck", sub: "Pass it to master this position", icon: .trophy)
        default: fallbackMeta(for: fret)
        }
    }

    private static func fallbackMeta(for fret: LearnFret) -> LearnFretMeta {
        do {
            let short = fret.title.components(separatedBy: " · ").first ?? fret.title
            let icon: LearnFretMeta.Icon = switch fret.state {
            case .done: .check
            case .current: .pick
            case .locked: fret.kind == .encore ? .star : fret.kind == .soundcheck ? .trophy : .lock
            }
            let sub = switch fret.state {
            case .done: "Mastered · replay for +5 beats"
            case .current: "5 takes · about 4 min"
            case .locked: "Clear the frets above to unlock"
            }
            return LearnFretMeta(short: short, sub: sub, icon: icon)
        }
    }
}

/// Glass header: title, groove / beats / strings pills, and today's set bar.
private struct LearnHeader: View {
    @Environment(AppState.self) private var app

    var body: some View {
        let p = app.learnProfile
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("LEARN · GUITAR").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.muted)
                    Text("Fretboard").font(.outfit(22, .semibold)).tracking(-0.8).foregroundStyle(OC.ink)
                }
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    Button { app.push(.streak) } label: {
                        pill { Image(systemName: "waveform").foregroundStyle(Color(hex: 0xC28C00)); Text("\(p.grooveDays)") }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Groove: \(p.grooveDays) days")
                    Button { app.push(.tiers) } label: {
                        pill { Image(systemName: "music.note").foregroundStyle(OC.blue); Text("240") }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("240 beats. Your tier")
                    pill { Image(systemName: "line.3.horizontal").rotationEffect(.degrees(90)).foregroundStyle(OC.red); Text("\(app.lives)") }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(app.lives) strings left")
                }
            }
            Button { app.push(.streak) } label: {
                HStack(spacing: 10) {
                    Text("TODAY’S SET").font(.outfit(11, .bold)).tracking(1.2).foregroundStyle(OC.muted).fixedSize()
                    HStack(spacing: 3) {
                        ForEach(0..<10, id: \.self) { i in
                            let filled = Double(i) < Double(p.todayMinutes) / Double(max(1, p.dailyGoalMinutes)) * 10
                            RoundedRectangle(cornerRadius: 3).fill(filled ? OC.gold : OC.ink.opacity(0.1)).frame(height: 10)
                        }
                    }
                    Text("\(p.todayMinutes)/\(p.dailyGoalMinutes) min").font(.mono(12)).foregroundStyle(OC.ink).fixedSize()
                }
                .frame(minHeight: 28)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Today’s set: \(p.todayMinutes) of \(p.dailyGoalMinutes) minutes")
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 10)
        .background {
            Rectangle().fill(.ultraThinMaterial)
                .overlay(Rectangle().fill(OC.glass.opacity(0.7)))
                .ignoresSafeArea(edges: .top)
        }
        .overlay(alignment: .bottom) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
    }

    private func pill<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        HStack(spacing: 5) { content() }
            .font(.outfit(15, .heavy))
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(OC.ink)
            .padding(.horizontal, 10)
            .frame(minWidth: 44, minHeight: 38)
            .background(Capsule().fill(OC.glass))
            .overlay(Capsule().strokeBorder(OC.glassBorder))
    }
}

/// The vertical guitar neck with one node per fret; labels alternate right / left.
private struct LearnNeck: View {
    let frets: [LearnFret]
    let selected: Int
    let onSelect: (Int) -> Void

    private let fretGap: CGFloat = 92
    private let width: CGFloat = 350
    private let stringWidths: [CGFloat] = [2.6, 2.2, 1.8, 1.5, 1.2, 1]

    var body: some View {
        let neckH = CGFloat(frets.count) * fretGap + 20
        ZStack(alignment: .topLeading) {
            UnevenRoundedRectangle(topLeadingRadius: 10, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 10)
                .fill(LinearGradient(stops: [.init(color: LearnWood.dark, location: 0), .init(color: LearnWood.mid, location: 0.45), .init(color: LearnWood.end, location: 1)], startPoint: .leading, endPoint: .trailing))
                .overlay(UnevenRoundedRectangle(topLeadingRadius: 10, bottomLeadingRadius: 18, bottomTrailingRadius: 18, topTrailingRadius: 10).strokeBorder(Color.white.opacity(0.06)))
                .shadow(color: Color(hex: 0x3C2814).opacity(0.28), radius: 20, y: 18)
                .frame(width: 120, height: neckH)
                .position(x: 175, y: neckH / 2)
            RoundedRectangle(cornerRadius: 6).fill(LearnWood.nut)
                .shadow(color: .black.opacity(0.25), radius: 2, y: 2)
                .frame(width: 128, height: 10)
                .position(x: 175, y: 5)
            ForEach(frets.indices, id: \.self) { i in
                Rectangle()
                    .fill(LinearGradient(colors: [LearnWood.fretTop, LearnWood.fretBottom], startPoint: .top, endPoint: .bottom))
                    .frame(width: 120, height: 3)
                    .position(x: 175, y: 10 + CGFloat(i + 1) * fretGap + 1.5)
            }
            ForEach(0..<6, id: \.self) { s in
                Rectangle()
                    .fill(LinearGradient(colors: [LearnWood.stringLight, LearnWood.stringDark], startPoint: .leading, endPoint: .trailing))
                    .opacity(0.85)
                    .frame(width: stringWidths[s], height: neckH)
                    .position(x: 125 + CGFloat(s) * 20 + stringWidths[s] / 2, y: neckH / 2)
            }
            ForEach(Array(frets.enumerated()), id: \.element.id) { i, fret in
                let top = 10 + CGFloat(i) * fretGap + (fretGap - 64) / 2
                let right = i % 2 == 0
                LearnFretNode(fret: fret, number: i + 1, isSelected: i == selected) { onSelect(i) }
                    .position(x: 175, y: top + 32)
                label(fret: fret, number: i + 1, right: right)
                    .position(x: right ? 246 + 52 : 52, y: top + 30)
            }
        }
        .frame(width: width, height: neckH)
        .frame(maxWidth: .infinity)
    }

    private func label(fret: LearnFret, number: Int, right: Bool) -> some View {
        let meta = LearnView.meta(for: fret)
        return VStack(alignment: right ? .leading : .trailing, spacing: 1) {
            Text("FRET \(number)").font(.outfit(10, .heavy)).tracking(1)
                .foregroundStyle(fret.state == .current ? OC.goldText : OC.faint)
            Text(meta.short.learnGlyphs).font(.outfit(13, .bold)).lineSpacing(1)
                .foregroundStyle(fret.state == .locked ? OC.faint : OC.ink)
                .multilineTextAlignment(right ? .leading : .trailing)
        }
        .frame(width: 104, height: 40, alignment: right ? .topLeading : .topTrailing)
        .accessibilityHidden(true)
    }
}

private struct LearnFretNode: View {
    let fret: LearnFret
    let number: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        let double = fret.kind == .encore
        Button(action: action) {
            HStack(spacing: 6) {
                ForEach(0..<(double ? 2 : 1), id: \.self) { _ in dot }
            }
            .frame(width: double ? 110 : 56, height: 56)
            .scaleEffect(isSelected ? 1.08 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Fret \(number), \(fret.title), \(stateText)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var stateText: String {
        switch fret.state {
        case .done: "mastered"
        case .current: "current lesson"
        case .locked: "locked"
        }
    }

    private var dot: some View {
        let (bg, fg, ring): (Color, Color, Color) = switch fret.state {
        case .done: (OC.gold, OC.inkFixed, Color(hex: 0xFFE3A3))
        case .current: (OC.inkFixed, OC.gold, OC.gold)
        case .locked: (Color(light: UIColor(white: 1, alpha: 0.85), dark: UIColor(hex: 0x2A2825)), OC.faint, Color(light: UIColor(white: 1, alpha: 0.9), dark: UIColor(white: 1, alpha: 0.14)))
        }
        return ZStack {
            if fret.state == .current {
                Circle().fill(OC.gold.opacity(0.28)).frame(width: 62, height: 62)
            }
            Circle().fill(bg)
                .overlay(Circle().strokeBorder(ring, lineWidth: 2))
                .frame(width: 50, height: 50)
                .shadow(color: fret.state == .current ? Color(hex: 0xFFAA00).opacity(0.45) : Color.black.opacity(0.18), radius: fret.state == .current ? 12 : 7, y: fret.state == .current ? 10 : 6)
            icon.foregroundStyle(fg)
        }
    }

    @ViewBuilder private var icon: some View {
        switch LearnView.meta(for: fret).icon {
        case .check: Image(systemName: "checkmark").font(.system(size: 19, weight: .heavy))
        case .pick: LearnPickShape().frame(width: 22, height: 22)
        case .lock: Image(systemName: "lock").font(.system(size: 18, weight: .bold))
        case .star: Image(systemName: "star.fill").font(.system(size: 19, weight: .bold))
        case .strum: Image(systemName: "arrow.up.arrow.down").font(.system(size: 18, weight: .bold))
        case .trophy: Image(systemName: "trophy").font(.system(size: 18, weight: .bold))
        }
    }
}

/// Floating card for the selected fret, with its CTA.
private struct LearnFretDetail: View {
    let fret: LearnFret
    let number: Int
    let play: () -> Void

    var body: some View {
        let meta = LearnView.meta(for: fret)
        let (badgeBg, badgeFg): (Color, Color) = switch fret.state {
        case .locked: (OC.softFill, OC.faint)
        case .current: (OC.inkFixed, OC.gold)
        case .done: (OC.gold, OC.inkFixed)
        }
        VStack(spacing: 10) {
            HStack(spacing: 12) {
                VStack(spacing: 1) {
                    Text("FRET").font(.outfit(9, .heavy)).tracking(1)
                    Text("\(number)").font(.mono(18))
                }
                .foregroundStyle(badgeFg)
                .frame(width: 46, height: 46)
                .background(RoundedRectangle(cornerRadius: 14).fill(badgeBg))
                VStack(alignment: .leading, spacing: 2) {
                    Text(fret.title.learnGlyphs).font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                    Text(meta.sub).font(.outfit(12)).foregroundStyle(OC.muted)
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
            if fret.state != .locked {
                Button(action: play) {
                    Label(fret.state == .current ? "Play lesson · +10 beats" : "Replay · +5 beats", systemImage: "play.fill")
                        .font(.outfit(15, .bold))
                }
                .buttonStyle(.gold(height: 48))
            }
        }
        .padding(14)
        .glass(radius: 24)
        .shadow(color: .black.opacity(0.1), radius: 20, y: 14)
    }
}

// MARK: - Lesson

struct LessonView: View {
    let fretID: String
    @Environment(AppState.self) private var app

    @State private var questions: [LessonQuestion] = LessonView.learnStaged(MockData.lessonQuestions)
    @State private var index = 0
    @State private var picked: String? = nil
    @State private var built: [Int] = []
    @State private var checked = false
    @State private var right = false
    @State private var run = 0
    @State private var results: [Bool] = []
    @State private var listening = false

    private var q: LessonQuestion { questions[min(index, questions.count - 1)] }
    private var isLast: Bool { index >= questions.count - 1 }
    private var answerParts: [String] { q.answer.split(separator: " ").map(String.init) }

    private var ready: Bool {
        switch q.kind {
        case .pickChord: picked != nil
        case .buildBar: built.count == answerParts.count
        case .listen: !listening
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        LearnEyebrow(text: "TAKE \(index + 1) OF \(questions.count) · \(kindLabel)")
                        Spacer()
                        if run >= 2 {
                            Text("LOCKED IN ×\(run)")
                                .font(.outfit(11, .heavy)).tracking(1).foregroundStyle(OC.inkFixed)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 8).fill(OC.gold))
                                .transition(.scale.combined(with: .opacity))
                        }
                    }
                    Text(q.prompt)
                        .font(.outfit(25, .semibold)).tracking(-0.6).lineSpacing(4)
                        .foregroundStyle(OC.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    content
                    if checked { feedback.transition(.move(edge: .bottom).combined(with: .opacity)) }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 20)
                .id(q.id)
            }
            .scrollIndicators(.hidden)
            bottomBar
        }
        .ocPage()
        .sensoryFeedback(trigger: results.count) { _, _ in
            guard let last = results.last else { return nil }
            return last ? .success : .error
        }
        .task {
            if let qs = try? await app.backend.lesson(fretID: fretID), !qs.isEmpty { questions = Self.learnStaged(qs) }
            if app.lives == 0 { app.modal = .reward(.strings) }
        }
    }

    /// Stages the demo takes to match the design flow: name a shape, pick the bass string, arrange the bars.
    fileprivate static func learnStaged(_ qs: [LessonQuestion]) -> [LessonQuestion] {
        qs.map { q in
            var q = q
            switch q.id {
            case "q1":
                q.prompt = "Which chord is this shape?"; q.detail = "G/B"
                q.options = ["G", "G/B", "Em", "C"]; q.answer = "G/B"
            case "q2":
                q.prompt = "In G/B, which string plays the bass note B?"; q.detail = ""
                q.options = ["Low E string", "A string, fret 2", "D string, open", "B string, open"]; q.answer = "A string, fret 2"
            case "q3":
                q.prompt = "Lay out the intro of စဉ်းစားပါ, bar by bar"
                q.options = ["Am", "C", "F", "G/B", "Em"]; q.answer = "C G/B Am F"
            default: break
            }
            return q
        }
    }

    /// A pick-the-chord take whose detail is itself a chord: show its shape.
    private var shapeVoicing: ChordVoicing? {
        q.kind == .pickChord ? ChordLibrary.info(q.detail)?.voicings.first : nil
    }

    private var kindLabel: String {
        switch q.kind {
        case .pickChord: isChordGrid ? "NAME THE CHORD" : q.id == "q2" ? "LISTEN FOR THE BASS" : "PICK ONE"
        case .buildBar: "ARRANGE THE BARS"
        case .listen: "PLAY IT"
        }
    }

    /// Chord-name options go in a 2×2 grid; longer answers in a list.
    private var isChordGrid: Bool { q.options.allSatisfy { ChordLibrary.info($0) != nil } }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: 8) {
            Button { app.pop() } label: {
                Image(systemName: "xmark").font(.system(size: 19, weight: .bold))
                    .foregroundStyle(OC.muted)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Leave lesson")
            HStack(spacing: 5) {
                ForEach(questions.indices, id: \.self) { k in
                    let r: Bool? = k < results.count ? results[k] : nil
                    let cur = k == index
                    Capsule()
                        .fill(r == true ? OC.teal : r == false ? OC.red : cur ? OC.gold : OC.ink.opacity(0.1))
                        .frame(height: 10)
                        .background(Capsule().fill(cur ? OC.gold.opacity(0.25) : .clear).padding(-3))
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Take \(index + 1) of \(questions.count)")
            LearnLivesPill(lives: app.lives)
        }
        .padding(.leading, 8).padding(.trailing, 16).padding(.top, 6)
    }

    // MARK: Question content

    @ViewBuilder private var content: some View {
        switch q.kind {
        case .pickChord:
            if let v = shapeVoicing {
                ChordDiagram(voicing: v, width: 116, showStringNames: false)
                    .padding(.horizontal, 32).padding(.vertical, 20)
                    .glass()
                    .frame(maxWidth: .infinity)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(q.options, id: \.self) { optionButton($0, grid: true) }
                }
            } else if isChordGrid {
                Text(q.detail).font(.mono(17)).foregroundStyle(OC.ink)
                    .padding(.horizontal, 16).frame(height: 44)
                    .glass(radius: 14)
                    .frame(maxWidth: .infinity)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(q.options, id: \.self) { optionButton($0, grid: true) }
                }
            } else {
                if !q.detail.isEmpty {
                    Text(q.detail).font(.outfit(15)).foregroundStyle(OC.muted)
                }
                VStack(spacing: 10) {
                    ForEach(q.options, id: \.self) { optionButton($0, grid: false) }
                }
            }
        case .buildBar:
            buildArea
        case .listen:
            listenArea
        }
    }

    private func optionButton(_ text: String, grid: Bool) -> some View {
        let on = picked == text
        var border = on ? OC.gold : OC.glassBorder
        var bg = on ? OC.goldTint : OC.glass
        var fg = OC.ink
        if checked && text == q.answer { border = OC.teal; bg = OC.tealTint; fg = OC.tealText }
        else if checked && on { border = OC.red; bg = OC.redTint; fg = OC.red }
        return Button {
            guard !checked else { return }
            withAnimation(.snappy(duration: 0.15)) { picked = text }
        } label: {
            HStack(spacing: 10) {
                Text(text).font(.outfit(grid ? 20 : 16, .bold))
                if !grid { Spacer(minLength: 0) }
            }
            .foregroundStyle(fg)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: grid ? 80 : 56, alignment: grid ? .center : .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.ultraThinMaterial).overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(bg)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(border, lineWidth: 2))
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(on ? OC.gold.opacity(0.28) : .clear).padding(-4))
            .shadow(color: .black.opacity(0.06), radius: 10, y: 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var buildArea: some View {
        VStack(spacing: 18) {
            HStack(spacing: 8) {
                ForEach(answerParts.indices, id: \.self) { pos in
                    let has = pos < built.count
                    let name = has ? q.options[built[pos]] : "·"
                    Button {
                        guard has, !checked else { return }
                        withAnimation(.snappy(duration: 0.15)) { _ = built.remove(at: pos) }
                    } label: {
                        VStack(spacing: 1) {
                            Text("BAR \(pos + 1)").font(.outfit(9, .heavy)).foregroundStyle(OC.faint)
                            Text(name).font(.mono(17)).foregroundStyle(OC.ink).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .frame(width: 64, height: 48)
                        .background(RoundedRectangle(cornerRadius: 14).fill(has ? OC.surface.opacity(0.85) : .clear))
                        .shadow(color: .black.opacity(has ? 0.06 : 0), radius: 6, y: 4)
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(has ? OC.glassBorder : OC.ink.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(has ? "Remove \(name) from bar \(pos + 1)" : "Empty bar \(pos + 1)")
                    .accessibilityHint(has ? "Clears this bar" : "Tap the chords below in order to fill the bars")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .frame(minHeight: 72)
            .background(RoundedRectangle(cornerRadius: 20).fill(OC.softFill))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [6, 4])).foregroundStyle(OC.ink.opacity(0.14)))

            LearnFlowLayout(spacing: 10) {
                ForEach(q.options.indices, id: \.self) { bi in
                    let used = built.contains(bi)
                    Button {
                        guard !used, !checked, built.count < answerParts.count else { return }
                        withAnimation(.snappy(duration: 0.15)) { built.append(bi) }
                    } label: {
                        Text(q.options[bi]).font(.mono(17))
                            .foregroundStyle(used ? .clear : OC.ink)
                            .padding(.horizontal, 14)
                            .frame(minWidth: 60, minHeight: 50)
                            .background(RoundedRectangle(cornerRadius: 14).fill(used ? OC.softFill : OC.glass))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(used ? .clear : OC.glassBorder))
                            .shadow(color: .black.opacity(used ? 0 : 0.08), radius: 9, y: 8)
                    }
                    .buttonStyle(.plain)
                    .disabled(used)
                    .accessibilityLabel(used ? "\(q.options[bi]), placed" : "Add \(q.options[bi])")
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var listenArea: some View {
        VStack(spacing: 14) {
            Text(q.detail).font(.outfit(15)).foregroundStyle(OC.muted)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 14) {
                ForEach(["C", "G/B"], id: \.self) { name in
                    ChordCard(name: name, width: 110)
                }
            }
            .frame(maxWidth: .infinity)
            HStack(spacing: 10) {
                Image(systemName: listening ? "waveform" : "mic.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(listening ? OC.goldText : OC.muted)
                    .symbolEffect(.variableColor.iterative, isActive: listening)
                Text(listening ? "Listening for the bass note…" : checked ? "Heard it — B on the A string." : "Tap “Play it” and strum when ready.")
                    .font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
                Spacer(minLength: 0)
            }
            .padding(14)
            .glass(radius: 16)
        }
    }

    // MARK: Feedback

    private var feedback: some View {
        let fg = right ? OC.teal : OC.red
        let answerText = q.kind == .buildBar ? answerParts.joined(separator: " → ") : q.answer
        return HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 4) {
                LearnTuneGauge(inTune: right, color: fg).frame(width: 56, height: 30)
                Text(right ? "IN TUNE" : "OUT OF TUNE")
                    .font(.outfit(10, .heavy)).tracking(1).foregroundStyle(fg)
                    .multilineTextAlignment(.center)
            }
            .frame(width: 64)
            VStack(alignment: .leading, spacing: 4) {
                Text(right ? "Clean take!" : "A string snapped — answer: \(answerText)")
                    .font(.outfit(16, .bold)).foregroundStyle(fg)
                Text(explanation).font(.outfit(14)).lineSpacing(3).foregroundStyle(OC.ink)
            }
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 20).fill(right ? OC.tealTint : OC.redTint))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(fg.opacity(0.3)))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }

    private var explanation: String {
        switch q.id {
        case "q1": "A G shape without the low E: the A string at fret 2 (B) becomes the lowest note — so it’s G/B."
        case "q2": "The lowest string you play sets the bass. Here that’s the A string at fret 2 — the note B."
        case "q3": "C → G/B → Am → F. The bass walks down C, B, A, then lands on F."
        case "q4": "The bass note B rang clean on the A string, fret 2 — that’s the walk-down."
        case "q5": "F closes the loop: C → G/B → Am → F, then back to C for the next line."
        default: ChordLibrary.info(q.answer)?.voicings.first?.tip ?? q.detail
        }
    }

    // MARK: Bottom bar

    private var bottomBar: some View {
        Group {
            if !checked {
                if q.kind == .listen {
                    Button(action: listen) {
                        HStack(spacing: 8) {
                            if listening { ProgressView().tint(OC.inkFixed) } else { Image(systemName: "mic.fill") }
                            Text(listening ? "Listening…" : "Play it — we’ll listen")
                        }
                    }
                    .buttonStyle(.gold)
                    .disabled(listening)
                } else if ready {
                    Button("Check this take", action: check).buttonStyle(.gold)
                } else {
                    Text("Check this take")
                        .font(.outfit(16, .bold)).foregroundStyle(OC.faint)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(RoundedRectangle(cornerRadius: 16).fill(OC.softFill))
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHint("Choose an answer first")
                }
            } else if !isLast {
                Button("Next take →", action: next).buttonStyle(.ink)
            } else {
                Button("Finish the session →") { app.push(.complete) }.buttonStyle(.ink)
            }
        }
        .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 8)
        .background {
            Rectangle().fill(.ultraThinMaterial)
                .overlay(Rectangle().fill(OC.glass.opacity(0.8)))
                .ignoresSafeArea(edges: .bottom)
        }
        .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
    }

    private func check() {
        guard ready, !checked else { return }
        let ok: Bool = switch q.kind {
        case .pickChord: picked == q.answer
        case .buildBar: built.map { q.options[$0] } == answerParts
        case .listen: true
        }
        grade(ok)
    }

    private func listen() {
        guard !listening, !checked else { return }
        listening = true
        Task {
            try? await Task.sleep(for: .seconds(1.8))
            listening = false
            grade(true)
        }
    }

    private func grade(_ ok: Bool) {
        withAnimation(.snappy(duration: 0.25)) {
            checked = true
            right = ok
            run = ok ? run + 1 : 0
            results.append(ok)
            if !ok { app.lives = max(0, app.lives - 1) }
        }
        if !ok && app.lives == 0 {
            Task {
                try? await Task.sleep(for: .seconds(0.9))
                app.modal = .reward(.strings)
            }
        }
    }

    private func next() {
        if app.lives == 0 { app.modal = .reward(.strings); return }
        withAnimation(.snappy(duration: 0.25)) {
            index += 1
            picked = nil
            built = []
            checked = false
            right = false
        }
    }
}

/// Six tiny strings; snapped ones turn red with a break in the middle.
private struct LearnLivesPill: View {
    let lives: Int
    var body: some View {
        HStack(spacing: 6) {
            HStack(spacing: 2) {
                ForEach(0..<6, id: \.self) { k in
                    let ok = k < lives
                    VStack(spacing: ok ? 0 : 5) {
                        RoundedRectangle(cornerRadius: 1).fill(ok ? OC.ink : OC.red).frame(width: 2)
                        RoundedRectangle(cornerRadius: 1).fill(ok ? OC.ink : OC.red).frame(width: 2)
                    }
                    .frame(height: 18)
                }
            }
            Text("\(lives)").font(.outfit(14, .heavy)).foregroundStyle(OC.ink).contentTransition(.numericText())
        }
        .padding(.horizontal, 10).frame(height: 34)
        .background(Capsule().fill(OC.glass))
        .overlay(Capsule().strokeBorder(OC.glassBorder))
        .animation(.spring(duration: 0.35, bounce: 0.5), value: lives)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(lives) strings left")
    }
}

/// Mini tuner gauge: needle straight up when in tune, leaning flat when not.
private struct LearnTuneGauge: View {
    let inTune: Bool
    let color: Color
    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 56
            var arc = Path()
            arc.addArc(center: CGPoint(x: 28 * s, y: 28 * s), radius: 24 * s, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
            ctx.stroke(arc, with: .color(OC.ink.opacity(0.15)), style: StrokeStyle(lineWidth: 4 * s, lineCap: .round))
            var needle = Path()
            needle.move(to: CGPoint(x: 28 * s, y: 28 * s))
            needle.addLine(to: CGPoint(x: (inTune ? 28 : 12) * s, y: 8 * s))
            ctx.stroke(needle, with: .color(color), style: StrokeStyle(lineWidth: 3 * s, lineCap: .round))
            ctx.fill(Path(ellipseIn: CGRect(x: 25 * s, y: 25 * s, width: 6 * s, height: 6 * s)), with: .color(color))
        }
        .accessibilityHidden(true)
    }
}

/// Simple wrapping layout for chord banks and chip rows.
private struct LearnFlowLayout: Layout {
    var spacing: CGFloat = 8
    var centered = true

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(for: proposal.width ?? .infinity, subviews: subviews)
        let h = rows.reduce(0) { $0 + $1.height } + CGFloat(max(0, rows.count - 1)) * spacing
        let w = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? w, height: h)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(for: bounds.width, subviews: subviews) {
            var x = centered ? bounds.minX + (bounds.width - row.width) / 2 : bounds.minX
            for i in row.items {
                let sz = subviews[i].sizeThatFits(.unspecified)
                subviews[i].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
                x += sz.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var items: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func rows(for maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [], cur = Row()
        for (i, v) in subviews.enumerated() {
            let sz = v.sizeThatFits(.unspecified)
            let add = cur.items.isEmpty ? sz.width : cur.width + spacing + sz.width
            if add > maxWidth && !cur.items.isEmpty {
                rows.append(cur); cur = Row()
            }
            cur.width = cur.items.isEmpty ? sz.width : cur.width + spacing + sz.width
            cur.height = max(cur.height, sz.height)
            cur.items.append(i)
        }
        if !cur.items.isEmpty { rows.append(cur) }
        return rows
    }
}

// MARK: - Complete (session recorded)

struct CompleteView: View {
    @Environment(AppState.self) private var app
    @State private var recorded = false

    var body: some View {
        let p = app.learnProfile
        let tier = Tier.forPoints(p.points)
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 0) {
                    LearnRecordDisc(fret: 3, label: "C↔G/B".learnGlyphs)
                        .frame(width: 210, height: 210)
                        .padding(.top, 26)
                    HStack(spacing: 8) {
                        Rectangle().fill(OC.gold).frame(width: 14, height: 2)
                        Text("SESSION RECORDED").font(.outfit(11, .bold)).tracking(1.8).foregroundStyle(OC.nightMuted)
                    }
                    .padding(.top, 18)
                    Text("You can walk the bass from C to Am.")
                        .font(.outfit(32, .semibold)).tracking(-1)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(OC.nightText)
                        .padding(.top, 6)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: 8) {
                        LearnNightStat(label: "BEATS", value: "+15", valueColor: OC.gold, centered: true)
                        LearnNightStat(label: "IN TUNE", value: "4/5", valueColor: Color(hex: 0x5EEAD4), centered: true)
                        LearnNightStat(label: "TAKE TIME", value: "2:40", mono: true, centered: true)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 22)

                    Button { app.push(.streak) } label: {
                        HStack(spacing: 12) {
                            HStack(alignment: .bottom, spacing: 3) {
                                ForEach([12, 22, 16, 28, 20], id: \.self) { h in
                                    RoundedRectangle(cornerRadius: 2).fill(OC.gold).frame(width: 5, height: CGFloat(h))
                                }
                            }
                            .frame(height: 30, alignment: .bottom)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Today’s set done · groove on").font(.outfit(15, .bold))
                                Text("\(p.grooveDays + 1)-day groove · \(p.todayMinutes + 6) / \(p.dailyGoalMinutes) min today").font(.outfit(12)).foregroundStyle(OC.nightMuted)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(OC.nightMuted)
                        }
                        .foregroundStyle(OC.nightText)
                        .padding(14)
                        .nightGlass()
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)

                    Button { app.push(.tiers) } label: {
                        HStack(spacing: 12) {
                            LearnTierShield(tier: tier, width: 28)
                            VStack(alignment: .leading, spacing: 5) {
                                HStack {
                                    Text("+15 O Points")
                                    Spacer()
                                    if let next = tier.next {
                                        Text("\((next.minPoints - p.points).formatted()) to \(next.title)").foregroundStyle(OC.nightMuted)
                                    }
                                }
                                .font(.outfit(13, .bold))
                                LearnProgressBar(value: LearnTiersMath.progress(points: p.points), track: Color.white.opacity(0.12), height: 6)
                            }
                        }
                        .foregroundStyle(OC.nightText)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .nightGlass()
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 8)
                }
                .padding(.horizontal, 22)
                .padding(.bottom, 20)
            }
            .scrollIndicators(.hidden)

            VStack(spacing: 10) {
                Button("Keep the groove going") {
                    app.popToRoot()
                    app.showInterstitial()
                }
                .buttonStyle(.gold)
                Button("Play it back") {
                    app.popToRoot()
                    app.push(.lesson(fretID: "f3"))
                }
                .buttonStyle(LearnNightOutlineStyle(height: 48))
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 12)
        }
        .background(NightBackground(glowCenter: UnitPoint(x: 0.5, y: 0.18)))
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarBackButtonHidden()
        .environment(\.colorScheme, .dark)
        .onAppear {
            guard !recorded else { return }
            recorded = true
            app.profile?.points += 15
            app.profile?.lessonsDone += 1
        }
    }
}

/// The "record" disc on Session recorded: grooves around a gold label.
private struct LearnRecordDisc: View {
    let fret: Int
    let label: String
    var body: some View {
        ZStack {
            Circle().fill(Color(hex: 0x141312)).frame(width: 200, height: 200)
            ForEach([184, 164, 144, 124], id: \.self) { d in
                Circle().strokeBorder(Color.white.opacity(0.065), lineWidth: 1).frame(width: CGFloat(d), height: CGFloat(d))
            }
            Circle()
                .trim(from: 0.58, to: 0.70)
                .stroke(Color.white.opacity(0.18), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .frame(width: 160, height: 160)
            Circle().fill(OC.gold).frame(width: 84, height: 84)
            Circle().fill(OC.night).frame(width: 10, height: 10)
            Text("FRET \(fret)").font(.outfit(9, .heavy)).tracking(1.5).foregroundStyle(OC.inkFixed).offset(y: -14)
            Text(label).font(.mono(12)).foregroundStyle(OC.inkFixed).offset(y: 18)
        }
        .accessibilityHidden(true)
    }
}

private struct LearnProgressBar: View {
    let value: Double
    var track: Color = OC.softFill
    var fill: Color = OC.gold
    var height: CGFloat = 6
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(fill).frame(width: max(height, g.size.width * min(1, max(0, value))))
            }
        }
        .frame(height: height)
    }
}

private enum LearnTiersMath {
    static func progress(points: Int) -> Double {
        let t = Tier.forPoints(points)
        guard let n = t.next else { return 1 }
        return Double(points - t.minPoints) / Double(n.minPoints - t.minPoints)
    }
}

// MARK: - Streak (groove)

struct StreakView: View {
    @Environment(AppState.self) private var app
    @State private var goal = 10
    @State private var remind = true

    // September 2026 practice log (minutes per day). Today = 25th, current groove from the 18th.
    private let minutes: [Int: Int] = [2: 6, 3: 12, 4: 8, 8: 10, 9: 5, 11: 14, 14: 7, 15: 9, 18: 10, 19: 6, 20: 15, 21: 11, 22: 8, 23: 13, 24: 10, 25: 12]
    private let today = 25
    private let grooveFrom = 18
    private let goals: [(name: String, mins: Int)] = [("Quick", 5), ("Standard", 10), ("Serious", 20), ("Pro", 30)]

    var body: some View {
        let groove = today - grooveFrom + 1
        let restNotes = app.learnProfile.restNotes
        VStack(spacing: 0) {
            TopBar(title: "Your groove", backLabel: "Back to Learn") {
                Button { app.push(.saveImage(chord: "C")) } label: {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(OC.ink).frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Share your groove")
            }
            ScrollView {
                VStack(spacing: 14) {
                    heroCard(groove: groove)
                    calendarCard
                    HStack(spacing: 12) {
                        LearnRestGlyph()
                            .stroke(OC.gold, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                            .frame(width: 24, height: 24)
                            .frame(width: 46, height: 46)
                            .background(RoundedRectangle(cornerRadius: 14).fill(OC.inkFixed))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(restNotes) rest note\(restNotes == 1 ? "" : "s") saved").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                            Text("Like a rest in music — skip one day and your groove keeps going.").font(.outfit(13)).foregroundStyle(OC.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                    .glass()
                    .accessibilityElement(children: .combine)

                    goalPicker

                    Toggle(isOn: $remind) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Practice reminder").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                            Text("Every day at 8:00 PM").font(.outfit(12)).foregroundStyle(OC.muted)
                        }
                    }
                    .toggleStyle(.oc)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 60)
                    .glass()

                    Button("Back to the fretboard") { app.popToRoot() }
                        .buttonStyle(.gold)
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
        }
        .ocPage()
        .onAppear { goal = app.learnProfile.dailyGoalMinutes }
        .onChange(of: goal) { _, g in app.profile?.dailyGoalMinutes = g }
    }

    private func heroCard(groove: Int) -> some View {
        let week = ["M", "T", "W", "T", "F", "S", "S"]
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("IN THE GROOVE").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.gold)
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(groove)").font(.outfit(64, .bold)).tracking(-2)
                        Text("days").font(.outfit(18, .semibold))
                    }
                }
                Spacer()
                Text("Play tomorrow\nto make it \(groove + 1)")
                    .font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
                    .multilineTextAlignment(.trailing).lineSpacing(3)
            }
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(0..<7, id: \.self) { i in
                    let d = 21 + i
                    let m = d <= today ? minutes[d] ?? 0 : 0
                    let isToday = d == today
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(isToday
                                  ? AnyShapeStyle(LinearGradient(colors: [Color(hex: 0xFFD15A), OC.goldBottom], startPoint: .top, endPoint: .bottom))
                                  : AnyShapeStyle(m > 0 ? OC.gold.opacity(0.7) : Color.white.opacity(0.12)))
                            .frame(height: m > 0 ? CGFloat(m) * 4.2 : 6)
                            .shadow(color: isToday ? OC.gold.opacity(0.55) : .clear, radius: 9)
                        Text(week[i]).font(.outfit(11, .heavy)).foregroundStyle(isToday ? OC.gold : OC.nightMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(d > today ? "Upcoming" : "\(m) minutes") on \(week[i])")
                }
            }
            .frame(height: 92, alignment: .bottom)
            Text("Bar height = minutes played that day").font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
        }
        .padding(18)
        .inkGlass(radius: 24)
    }

    private var calendarCard: some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("September 2026").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                Spacer()
                Text("\(minutes.count) days played").font(.outfit(12, .bold)).foregroundStyle(OC.muted)
            }
            LazyVGrid(columns: cols, spacing: 0) {
                ForEach(Array(["M", "T", "W", "T", "F", "S", "S"].enumerated()), id: \.offset) { _, d in
                    Text(d).font(.outfit(11, .heavy)).foregroundStyle(OC.faint)
                }
            }
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(0..<35, id: \.self) { k in dayCell(k) }
            }
            HStack(spacing: 6) {
                Rectangle().fill(OC.gold).frame(width: 18, height: 2)
                Text("Gold line = your current groove").font(.outfit(12)).foregroundStyle(OC.muted)
            }
            .padding(.top, 4)
        }
        .padding(14)
        .glass()
    }

    @ViewBuilder private func dayCell(_ d: Int) -> some View {
        if d < 1 || d > 30 {
            Color.clear.frame(height: 38)
        } else {
            let m = minutes[d] ?? 0
            let inRun = d >= grooveFrom && d <= today
            VStack(spacing: 3) {
                Spacer(minLength: 0)
                RoundedRectangle(cornerRadius: 3)
                    .fill(inRun ? OC.gold : m > 0 ? OC.gold.opacity(0.35) : .clear)
                    .frame(width: 18, height: m > 0 ? max(4, CGFloat(m) * 1.4) : 0)
                Text("\(d)").font(.outfit(11, d == today ? .heavy : .semibold))
                    .foregroundStyle(d == today ? OC.goldText : d > today ? OC.faint.opacity(0.7) : OC.muted)
            }
            .frame(maxWidth: .infinity, minHeight: 38, maxHeight: 38)
            .overlay(alignment: .bottom) {
                Rectangle().fill(inRun ? OC.gold : .clear).frame(height: 2).offset(y: 4)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("September \(d)\(m > 0 ? ", \(m) minutes" : "")\(inRun ? ", in your groove" : "")")
        }
    }

    private var goalPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("DAILY SET LENGTH").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.muted)
            HStack(spacing: 0) {
                ForEach(goals, id: \.mins) { g in
                    let on = g.mins == goal
                    Button { withAnimation(.snappy(duration: 0.2)) { goal = g.mins } } label: {
                        VStack(spacing: 1) {
                            Text("\(g.mins)′").font(.mono(15)).foregroundStyle(OC.ink)
                            Text(g.name).font(.outfit(11, .semibold)).foregroundStyle(OC.muted)
                        }
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background {
                            if on {
                                RoundedRectangle(cornerRadius: 16).fill(OC.surface.opacity(0.92))
                                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(OC.gold, lineWidth: 2))
                                    .shadow(color: .black.opacity(0.08), radius: 6, y: 4)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(g.name), \(g.mins) minutes")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(4)
            .background(RoundedRectangle(cornerRadius: 20).fill(OC.softFill))
        }
    }
}

/// Quarter-rest glyph for the "rest note" card.
private struct LearnRestGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: p(9, 2.5))
        path.addLine(to: p(14.5, 8.5))
        path.addLine(to: p(10, 12.5))
        path.addLine(to: p(15, 17.5))
        path.addQuadCurve(to: p(10, 17.5), control: p(12, 15.5))
        path.addQuadCurve(to: p(12.5, 21.5), control: p(8, 19.5))
        return path
    }
}

// MARK: - Tiers

struct TiersView: View {
    @Environment(AppState.self) private var app
    @State private var tab = 0
    @State private var open: Tier? = .chordKeeper

    private let earn: [(title: String, color: Color, rows: [(String, String, String)])] = [
        ("LEARNING", OC.goldText, [
            ("Earn lesson beats", "1 point per beat", "+1 / beat"),
            ("Master a position", "Includes its soundcheck", "+50"),
            ("Keep a 7-day groove", "Every 7 days in a row", "+30"),
            ("Reach a 30-day groove", "Once per groove", "+150")
        ]),
        ("CONTRIBUTING", OC.blue, [
            ("Share a sheet publicly", "After 24 hours without reports", "+40"),
            ("First sheet for a song", "No other version on the community yet", "+60"),
            ("Someone saves your sheet", "Up to 50 points per sheet", "+5"),
            ("Your correction is accepted", "On anyone’s sheet", "+25"),
            ("Report that gets actioned", "Wrong chords, spam, duplicates", "+10")
        ])
    ]

    private let recent: [(String, String, String, Color)] = [
        ("Lesson complete: C ↔ G/B", "Today", "+15", OC.gold),
        ("7-day groove bonus", "Yesterday", "+30", OC.gold),
        ("Shared စဉ်းစားပါ publicly", "This week", "+40", OC.blue),
        ("Your sheet was saved", "[n] times this week", "+5 each", OC.blue)
    ]

    var body: some View {
        let points = app.learnProfile.points
        VStack(spacing: 0) {
            TopBar(title: "Your tier")
            ScrollView {
                VStack(spacing: 16) {
                    hero(points: points)
                    HStack(spacing: 10) {
                        splitCard(icon: "graduationcap", title: "LEARNING", color: OC.goldText, value: "860", note: "Beats, groove and positions") { app.popToRoot(); app.tab = .learn }
                        splitCard(icon: "square.and.arrow.up", title: "CONTRIBUTING", color: OC.blue, value: "380", note: "Uploads, fixes and saves") { app.switchTo(.explore) }
                    }
                    Segmented(options: [0, 1], selection: $tab) { $0 == 0 ? "How to earn" : "Tiers & perks" }
                    if tab == 0 { earnSection } else { tiersSection(points: points) }
                    recentSection
                    BannerAdView { app.modal = .pro }
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
        }
        .ocPage()
    }

    private func hero(points: Int) -> some View {
        let tier = Tier.forPoints(points)
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                LearnTierShield(tier: tier, width: 76)
                VStack(alignment: .leading, spacing: 2) {
                    Text("TIER \(tier.rawValue) OF \(Tier.allCases.count)").font(.outfit(12, .heavy)).tracking(0.6).foregroundStyle(OC.gold)
                    Text(tier.title).font(.outfit(26, .heavy))
                    Text("\(points.formatted()) O Points").font(.mono(15)).foregroundStyle(Color(hex: 0xD6D4CF))
                }
            }
            VStack(spacing: 6) {
                LearnProgressBar(value: LearnTiersMath.progress(points: points), track: Color(hex: 0x2A2825), height: 12)
                HStack {
                    Text(tier.minPoints.formatted())
                    Spacer()
                    if let next = tier.next {
                        Text("\((next.minPoints - points).formatted()) points to \(next.title)").font(.outfit(12, .bold)).foregroundStyle(OC.paper)
                        Spacer()
                        Text(next.minPoints.formatted())
                    } else {
                        Text("Top tier reached").font(.outfit(12, .bold)).foregroundStyle(OC.paper)
                    }
                }
                .font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
            }
        }
        .padding(20)
        .inkGlass()
    }

    private func splitCard(icon: String, title: String, color: Color, value: String, note: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Label(title, systemImage: icon).font(.outfit(12, .heavy)).tracking(0.5).foregroundStyle(color)
                Text(value).font(.outfit(24, .heavy)).foregroundStyle(OC.ink)
                Text(note).font(.outfit(12)).foregroundStyle(OC.muted).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glass()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var earnSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(earn, id: \.title) { g in
                VStack(alignment: .leading, spacing: 8) {
                    Text(g.title).font(.outfit(12, .heavy)).tracking(0.6).foregroundStyle(g.color)
                    GlassList {
                        ForEach(g.rows, id: \.0) { r in
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(r.0).font(.outfit(14, .bold)).foregroundStyle(OC.ink)
                                    Text(r.1).font(.outfit(12)).foregroundStyle(OC.muted)
                                }
                                Spacer(minLength: 8)
                                Text(r.2).font(.mono(14)).foregroundStyle(g.color).fixedSize()
                            }
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .frame(minHeight: 52)
                            .accessibilityElement(children: .combine)
                        }
                    }
                }
            }
            Text("Contribution points count only after a sheet is public for 24 hours and isn’t reported. Removed or duplicate uploads lose their points.")
                .font(.outfit(12)).lineSpacing(3).foregroundStyle(OC.muted)
        }
    }

    private func tiersSection(points: Int) -> some View {
        let mine = Tier.forPoints(points)
        return VStack(spacing: 8) {
            ForEach(Tier.allCases, id: \.self) { t in
                let isYou = t == mine
                let reached = t.rawValue < mine.rawValue
                let isOpen = open == t
                let color = Color(hex: t.colorHex)
                VStack(alignment: .leading, spacing: 0) {
                    Button {
                        withAnimation(.snappy(duration: 0.2)) { open = isOpen ? nil : t }
                    } label: {
                        HStack(spacing: 12) {
                            LearnTierShield(tier: t, width: 36)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(t.title).font(.outfit(16, .heavy)).foregroundStyle(OC.ink)
                                Text(range(t)).font(.mono(12, .semibold)).foregroundStyle(OC.muted)
                            }
                            Spacer(minLength: 8)
                            if isYou {
                                Text("YOU").font(.outfit(11, .heavy)).foregroundStyle(OC.inkFixed)
                                    .padding(.horizontal, 8).padding(.vertical, 4)
                                    .background(Capsule().fill(OC.gold))
                            }
                            if reached {
                                Image(systemName: "checkmark").font(.system(size: 15, weight: .heavy)).foregroundStyle(OC.teal)
                                    .accessibilityLabel("Reached")
                            }
                            Image(systemName: "chevron.down").font(.system(size: 12, weight: .bold)).foregroundStyle(OC.faint)
                                .rotationEffect(.degrees(isOpen ? 180 : 0))
                        }
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .frame(minHeight: 64)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(isOpen ? "Expanded" : "Collapsed")
                    if isOpen {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(t.perks, id: \.self) { perk in
                                HStack(alignment: .firstTextBaseline, spacing: 8) {
                                    Text("•").font(.outfit(13, .heavy)).foregroundStyle(color)
                                    Text(perk).font(.outfit(13)).lineSpacing(2).foregroundStyle(OC.ink)
                                }
                            }
                        }
                        .padding(.leading, 62).padding(.trailing, 14).padding(.bottom, 14)
                        .transition(.opacity)
                    }
                }
                .background(RoundedRectangle(cornerRadius: 20).fill(OC.surface))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(isYou ? color : OC.border, lineWidth: 2))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
    }

    private func range(_ t: Tier) -> String {
        if let n = t.next { return "\(t.minPoints.formatted()) – \((n.minPoints - 1).formatted()) points" }
        return "\(t.minPoints.formatted())+ points"
    }

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("RECENT POINTS").font(.outfit(12, .heavy)).tracking(0.6).foregroundStyle(OC.muted)
            GlassList {
                ForEach(recent, id: \.0) { r in
                    HStack(spacing: 12) {
                        Circle().fill(r.3).frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(r.0.learnGlyphs).font(.outfit(14, .bold)).foregroundStyle(OC.ink)
                            Text(r.1).font(.outfit(12)).foregroundStyle(OC.muted)
                        }
                        Spacer(minLength: 8)
                        Text(r.2).font(.mono(14)).foregroundStyle(OC.ink).fixedSize()
                    }
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .frame(minHeight: 52)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

// MARK: - Game (Chord Rush)

struct GameView: View {
    @Environment(AppState.self) private var app

    private enum Phase { case intro, play, done }

    private static let chordSet = ["C", "G", "Am", "F", "Em", "D", "Dm", "E"]
    private static let deck = ["Am", "D", "C", "Em", "G", "F", "Dm", "C", "E", "G", "Am", "D", "F", "Em", "C", "G", "Dm", "Am", "E", "D"]
    private static let duration = 30

    @State private var phase: Phase = .intro
    @State private var time = GameView.duration
    @State private var score = 0
    @State private var combo = 0
    @State private var bestCombo = 0
    @State private var q = 0
    @State private var answered = 0
    @State private var correct = 0
    @State private var missed: [String] = []
    @State private var flash = ""
    @State private var flashOK = true
    @State private var lock = false
    @State private var best = 180
    @State private var practice = false
    @State private var timer: Task<Void, Never>? = nil

    private var current: String { Self.deck[q % Self.deck.count] }

    /// "FRI 25 SEP" — weekday, day, month without commas.
    private static var dayLabel: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.setLocalizedDateFormatFromTemplate("EEE d MMM")
        return f.string(from: .now).replacingOccurrences(of: ",", with: "").uppercased()
    }

    private var options: [String] {
        let id = current
        let others = Self.chordSet.filter { $0 != id }
        let off = (q * 3) % others.count
        let opts = [id, others[off], others[(off + 2) % others.count], others[(off + 5) % others.count]]
        let orders = [[0, 1, 2, 3], [2, 0, 3, 1], [1, 3, 0, 2], [3, 2, 1, 0]]
        return orders[q % 4].map { opts[$0] }
    }

    private func multLabel(_ c: Int) -> String {
        let m = 1 + Double(min(c, 4)) * 0.5
        return m == m.rounded() ? String(Int(m)) : String(m)
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            switch phase {
            case .intro: intro
            case .play: play
            case .done: results
            }
        }
        .foregroundStyle(OC.nightText)
        .background(NightBackground())
        .toolbar(.hidden, for: .navigationBar)
        .environment(\.colorScheme, .dark)
        .onDisappear { timer?.cancel() }
        .sensoryFeedback(trigger: answered) { _, new in new == 0 ? nil : (flashOK ? .success : .error) }
    }

    private var header: some View {
        HStack {
            Button { timer?.cancel(); app.pop() } label: {
                Image(systemName: "chevron.left").font(.system(size: 20, weight: .semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to Learn")
            Spacer()
            VStack(spacing: 0) {
                Text("Chord Rush").font(.outfit(16, .bold))
                Text("DAILY GAME · \(Self.dayLabel)")
                    .font(.outfit(11)).tracking(1).foregroundStyle(OC.nightMuted)
            }
            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.horizontal, 10)
    }

    // MARK: Intro

    private var intro: some View {
        VStack(spacing: 16) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("HOW TO PLAY").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.gold)
                        Text("Name as many chord shapes as you can in 30 seconds.")
                            .font(.outfit(26, .semibold)).tracking(-0.8)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Right answers in a row build a combo — up to ×3 points. Wrong ones cost time, not lives.")
                            .font(.outfit(14)).lineSpacing(3).foregroundStyle(Color(hex: 0xBDB9B3))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .nightGlass(radius: 24)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("TODAY’S CHORDS").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.nightMuted)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                            ForEach(Self.chordSet, id: \.self) { c in
                                Text(c).font(.mono(16)).foregroundStyle(OC.gold)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(OC.gold.opacity(0.14)))
                                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(OC.gold.opacity(0.35)))
                            }
                        }
                        Text("Everyone plays the same set today — mixed from the chords you’ve learned.")
                            .font(.outfit(12)).foregroundStyle(OC.nightMuted)
                    }

                    HStack(spacing: 8) {
                        LearnNightStat(label: "YOUR BEST", value: "\(best)")
                        LearnNightStat(label: "DAYS PLAYED", value: "6")
                        LearnNightStat(label: "REWARD", value: "+12", valueColor: OC.gold)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 20)
            }
            .scrollIndicators(.hidden)
            Button { begin(practice: false) } label: {
                Label("Start today’s rush", systemImage: "play.fill").font(.outfit(18, .heavy))
            }
            .buttonStyle(.gold(height: 58))
            .padding(.horizontal, 20).padding(.bottom, 12)
        }
    }

    // MARK: Play

    private var play: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                LearnProgressBar(value: Double(time) / Double(Self.duration), track: Color.white.opacity(0.1), fill: time <= 5 ? Color(hex: 0xF87171) : OC.gold, height: 12)
                    .animation(.linear(duration: 1), value: time)
                Text("\(time)s").font(.mono(18)).foregroundStyle(time <= 5 ? Color(hex: 0xFCA5A5) : OC.nightText)
                    .frame(minWidth: 44, alignment: .trailing)
                    .contentTransition(.numericText(countsDown: true))
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(time) seconds left")
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("SCORE").font(.outfit(10, .heavy)).tracking(1).foregroundStyle(OC.nightMuted)
                    Text("\(score)").font(.outfit(30, .bold)).contentTransition(.numericText())
                }
                .accessibilityElement(children: .combine)
                Spacer()
                Text("COMBO ×\(multLabel(combo))")
                    .font(.outfit(14, .heavy))
                    .foregroundStyle(combo >= 2 ? OC.inkFixed : OC.nightMuted)
                    .padding(.horizontal, 12).frame(height: 34)
                    .background(Capsule().fill(combo >= 2 ? OC.gold : Color.white.opacity(0.08)))
            }

            VStack(spacing: 6) {
                if let v = ChordLibrary.info(current)?.voicings.first {
                    LearnGameDiagram(voicing: v)
                        .frame(width: 170, height: 196)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Chord shape, pattern \(v.pattern.map(String.init).joined(separator: " "))")
                }
                Text("What chord is this?").font(.outfit(12, .bold)).foregroundStyle(Color(hex: 0x6B6660))
            }
            .padding(.horizontal, 22).padding(.top, 16).padding(.bottom, 12)
            .background(RoundedRectangle(cornerRadius: 24).fill(OC.paper))
            .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(flash.isEmpty ? Color.white.opacity(0.4) : flashOK ? Color(hex: 0x5EEAD4) : Color(hex: 0xF87171), lineWidth: 2))
            .shadow(color: .black.opacity(0.35), radius: 20, y: 18)
            .environment(\.colorScheme, .light)
            .id(q)
            .transition(.asymmetric(insertion: .scale(scale: 0.92).combined(with: .opacity), removal: .opacity))

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(options, id: \.self) { name in
                    Button { pick(name) } label: {
                        Text(name).font(.mono(24)).foregroundStyle(OC.nightText)
                            .frame(maxWidth: .infinity, minHeight: 68)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.08)))
                            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.14), lineWidth: 2))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(flash).font(.outfit(13, .bold))
                .foregroundStyle(flashOK ? Color(hex: 0x5EEAD4) : Color(hex: 0xFCA5A5))
                .frame(minHeight: 18)
                .accessibilityAddTraits(.updatesFrequently)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20).padding(.top, 14)
    }

    // MARK: Results

    private var results: some View {
        VStack(spacing: 14) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(spacing: 6) {
                        Text(score >= best && score > 0 && !practice ? "NEW PERSONAL BEST" : practice ? "PRACTICE ROUND" : "TIME’S UP")
                            .font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.gold)
                        Text("\(score)").font(.outfit(64, .bold)).tracking(-2)
                        Text("\(correct) of \(answered) shapes · best combo ×\(multLabel(bestCombo))")
                            .font(.outfit(14)).foregroundStyle(Color(hex: 0xBDB9B3))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(22)
                    .nightGlass(radius: 24)
                    .accessibilityElement(children: .combine)

                    HStack(spacing: 8) {
                        LearnNightStat(label: "REWARD", value: practice ? "—" : "+12 beats", valueColor: OC.gold, note: "Counts toward today’s set")
                        LearnNightStat(label: "FRIENDS TODAY", value: "#[n] of [n]", note: "Resets at midnight")
                    }

                    if !missed.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("REVIEW THESE SHAPES").font(.outfit(11, .bold)).tracking(1.6).foregroundStyle(OC.nightMuted)
                            LearnFlowLayout(spacing: 8, centered: false) {
                                ForEach(missed, id: \.self) { m in
                                    Button { app.push(.chords(focus: m, songChords: missed)) } label: {
                                        Text("\(m) ›").font(.mono(15)).foregroundStyle(Color(hex: 0xFCA5A5))
                                            .padding(.horizontal, 12).frame(minWidth: 44, minHeight: 44)
                                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xFCA5A5).opacity(0.14)))
                                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(hex: 0xFCA5A5).opacity(0.35)))
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Review \(m)")
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20).padding(.top, 18)
            }
            .scrollIndicators(.hidden)

            VStack(spacing: 10) {
                ShareLink(item: "I scored \(score) in today’s Chord Rush on O Chords — \(correct) of \(answered) shapes!") {
                    Text("Share my score")
                }
                .buttonStyle(LearnNightOutlineStyle())
                Button("Practice round (no reward)") { begin(practice: true) }
                    .buttonStyle(LearnNightOutlineStyle())
                Button("Back to the fretboard") {
                    app.pop()
                    app.showInterstitial()
                }
                .buttonStyle(.gold(height: 56))
            }
            .padding(.horizontal, 20).padding(.bottom, 12)
        }
    }

    // MARK: Logic

    private func begin(practice: Bool) {
        timer?.cancel()
        self.practice = practice
        withAnimation(.snappy) {
            phase = .play
            time = Self.duration
            score = 0; combo = 0; bestCombo = 0; q = 0; answered = 0; correct = 0
            missed = []; flash = ""; lock = false
        }
        timer = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled || phase != .play { return }
                if time <= 1 {
                    finish()
                    return
                }
                withAnimation { time -= 1 }
            }
        }
    }

    private func finish() {
        timer?.cancel()
        withAnimation(.snappy) {
            time = 0
            phase = .done
            if !practice { best = max(best, score) }
        }
    }

    private func pick(_ name: String) {
        guard !lock, phase == .play else { return }
        let id = current
        let ok = name == id
        let gain = ok ? Int((10 * (1 + Double(min(combo, 4)) * 0.5)).rounded()) : 0
        let newCombo = ok ? combo + 1 : 0
        lock = true
        flashOK = ok
        flash = ok ? "+\(gain)\(newCombo >= 3 ? " · on a roll!" : "")" : "It was \(id) · −2s"
        withAnimation(.snappy(duration: 0.2)) {
            score += gain
            combo = newCombo
            bestCombo = max(bestCombo, newCombo)
            correct += ok ? 1 : 0
            if !ok && !missed.contains(id) { missed.append(id) }
            if !ok { time = max(1, time - 2) }
        }
        answered += 1
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(ok ? 350 : 800))
            guard phase == .play else { return }
            withAnimation(.snappy(duration: 0.2)) {
                q += 1
                flash = ""
            }
            lock = false
        }
    }
}

/// Plain black-dot chord shape for Chord Rush (no finger colours, so it can't give the answer away).
private struct LearnGameDiagram: View {
    let voicing: ChordVoicing
    var body: some View {
        Canvas { ctx, size in
            let ink = Color(hex: 0x1A1917)
            let line = Color(hex: 0x8E8A84)
            let left: CGFloat = 12, right = size.width - 12
            let gap = (right - left) / 5
            let top: CGFloat = 30, fretH = (size.height - top - 4) / 4
            let pattern = Array(voicing.pattern)
            func x(_ i: Int) -> CGFloat { left + CGFloat(i) * gap }
            // nut + frets
            ctx.fill(Path(roundedRect: CGRect(x: left - 2, y: top - 3, width: right - left + 4, height: 5), cornerRadius: 2),
                     with: .color(voicing.baseFret > 1 ? line : ink))
            for f in 1...4 {
                ctx.fill(Path(CGRect(x: left, y: top + CGFloat(f) * fretH - 1, width: right - left, height: 1.5)), with: .color(line.opacity(0.7)))
            }
            for i in 0..<6 {
                ctx.fill(Path(CGRect(x: x(i) - 0.75, y: top, width: 1.5, height: fretH * 4)), with: .color(line))
                let c = i < pattern.count ? pattern[i] : "0"
                let r: CGFloat = 6
                if c == "0" {
                    ctx.stroke(Path(ellipseIn: CGRect(x: x(i) - r, y: 8, width: r * 2, height: r * 2)), with: .color(ink), lineWidth: 2)
                } else if c == "x" {
                    var m = Path()
                    m.move(to: CGPoint(x: x(i) - 5, y: 9)); m.addLine(to: CGPoint(x: x(i) + 5, y: 19))
                    m.move(to: CGPoint(x: x(i) + 5, y: 9)); m.addLine(to: CGPoint(x: x(i) - 5, y: 19))
                    ctx.stroke(m, with: .color(Color(hex: 0x8E8A84)), lineWidth: 2)
                }
            }
            let dot: CGFloat = 26
            if let b = voicing.barre {
                let a = min(x(b.from), x(b.to)), z = max(x(b.from), x(b.to))
                let cy = top + (CGFloat(b.fret) - 0.5) * fretH
                ctx.fill(Path(roundedRect: CGRect(x: a - dot / 2, y: cy - dot / 2, width: z - a + dot, height: dot), cornerRadius: dot / 2), with: .color(ink))
            }
            for d in voicing.dots {
                let cy = top + (CGFloat(d.fret) - 0.5) * fretH
                ctx.fill(Path(ellipseIn: CGRect(x: x(d.string) - dot / 2, y: cy - dot / 2, width: dot, height: dot)), with: .color(ink))
            }
        }
    }
}

// MARK: - Interstitial ad

struct InterstitialAdView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: 8) {
                    Text("Ad").font(.outfit(10, .heavy)).tracking(0.5).foregroundStyle(Color(hex: 0xFFC940))
                        .padding(.horizontal, 6).frame(height: 18)
                        .overlay(RoundedRectangle(cornerRadius: 5).strokeBorder(Color(hex: 0xFFC940).opacity(0.7)))
                    Text("[Advertiser]").font(.outfit(13)).foregroundStyle(OC.nightMuted)
                }
                Spacer()
                Button { app.modal = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .bold))
                        .foregroundStyle(OC.nightText)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Color.white.opacity(0.12)))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close ad")
            }
            .padding(.horizontal, 12).padding(.top, 6)

            VStack(spacing: 16) {
                Spacer(minLength: 0)
                ZStack {
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color.white.opacity(0.05))
                    LearnStripes().clipShape(RoundedRectangle(cornerRadius: 18))
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                        .foregroundStyle(Color.white.opacity(0.25))
                    Text("[Ad creative from Google]").font(.outfit(14, .bold)).foregroundStyle(OC.nightMuted)
                }
                .frame(maxHeight: 440)
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.1)).frame(width: 48, height: 48)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("[Ad headline]").font(.outfit(16, .bold)).foregroundStyle(OC.nightText)
                        Text("[Advertiser] · [rating]").font(.outfit(13)).foregroundStyle(OC.nightMuted)
                    }
                    Spacer(minLength: 0)
                    Text("[Install]").font(.outfit(14, .bold)).foregroundStyle(OC.inkFixed)
                        .padding(.horizontal, 16).frame(height: 40)
                        .background(Capsule().fill(OC.nightText))
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16).padding(.top, 24)

            VStack(spacing: 6) {
                Text("Shown after a finished session · at most once every 3 sessions")
                    .font(.outfit(12)).foregroundStyle(OC.nightMuted)
                    .multilineTextAlignment(.center)
                Button { app.modal = .pro } label: {
                    Text("Remove ads with Pro ›").font(.outfit(13, .bold)).foregroundStyle(Color(hex: 0xFFC940))
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 8)
        }
        .background(NightBackground())
        .environment(\.colorScheme, .dark)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Advertisement")
    }
}

/// Diagonal placeholder stripes for the ad creative.
private struct LearnStripes: View {
    var body: some View {
        Canvas { ctx, size in
            let step: CGFloat = 24
            var x: CGFloat = -size.height
            while x < size.width {
                var p = Path()
                p.move(to: CGPoint(x: x, y: size.height))
                p.addLine(to: CGPoint(x: x + size.height, y: 0))
                ctx.stroke(p, with: .color(.white.opacity(0.05)), lineWidth: 12)
                x += step
            }
        }
    }
}

// MARK: - Previews

#Preview("Learn") { NavigationStack { LearnView() }.environment(AppState()) }
#Preview("Lesson") { NavigationStack { LessonView(fretID: "f3") }.environment(AppState()) }
#Preview("Complete") { NavigationStack { CompleteView() }.environment(AppState()) }
#Preview("Streak") { NavigationStack { StreakView() }.environment(AppState()) }
#Preview("Tiers") { NavigationStack { TiersView() }.environment(AppState()) }
#Preview("Chord Rush") { NavigationStack { GameView() }.environment(AppState()) }
#Preview("Interstitial") { InterstitialAdView().environment(AppState()) }
