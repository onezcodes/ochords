import SwiftUI

// MARK: - Backgrounds

/// Warm #F6F4EF page with soft gold light (radial gold 30% top-right, amber 22% left, orange 14% bottom-right).
/// In dark mode: #0B0A09 with a 22% gold glow.
struct WarmBackground: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                OC.background
                if scheme == .dark {
                    RadialGradient(colors: [OC.gold.opacity(0.22), .clear], center: UnitPoint(x: 1, y: -0.05), startRadius: 0, endRadius: w * 1.1)
                    RadialGradient(colors: [Color(hex: 0xFF7828).opacity(0.12), .clear], center: UnitPoint(x: -0.1, y: 0.7), startRadius: 0, endRadius: w * 0.9)
                } else {
                    RadialGradient(colors: [OC.gold.opacity(0.30), .clear], center: UnitPoint(x: 1, y: -0.05), startRadius: 0, endRadius: max(w * 1.1, h * 0.5))
                    RadialGradient(colors: [Color(hex: 0xFFD678).opacity(0.22), .clear], center: UnitPoint(x: -0.1, y: 0.55), startRadius: 0, endRadius: w * 0.9)
                    RadialGradient(colors: [Color(hex: 0xFF9646).opacity(0.14), .clear], center: UnitPoint(x: 1.1, y: 1.05), startRadius: 0, endRadius: w * 0.8)
                }
            }
        }
        .ignoresSafeArea()
    }
}

/// Always-dark background used by Stage, Session recorded, Listen, Chord Rush, ads.
struct NightBackground: View {
    var glowCenter: UnitPoint = UnitPoint(x: 1, y: -0.05)
    var body: some View {
        GeometryReader { geo in
            ZStack {
                OC.night
                RadialGradient(colors: [OC.gold.opacity(0.26), .clear], center: glowCenter, startRadius: 0, endRadius: geo.size.width * 1.0)
                RadialGradient(colors: [Color(hex: 0xFF7828).opacity(0.14), .clear], center: UnitPoint(x: -0.1, y: 0.8), startRadius: 0, endRadius: geo.size.width * 0.9)
            }
        }
        .ignoresSafeArea()
    }
}

// MARK: - Surfaces

struct GlassModifier: ViewModifier {
    var radius: CGFloat = OC.rCard
    var padding: CGFloat? = nil
    func body(content: Content) -> some View {
        content
            .padding(padding ?? 0)
            .background {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(OC.glass))
            }
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(OC.glassBorder, lineWidth: 1))
            .shadow(color: Color.black.opacity(0.07), radius: 15, y: 10)
    }
}

struct InkGlassModifier: ViewModifier {
    var radius: CGFloat = OC.rCard
    func body(content: Content) -> some View {
        content
            .foregroundStyle(OC.paper)
            .background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(OC.inkGlass))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Color.white.opacity(0.10), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.22), radius: 17, y: 14)
    }
}

struct NightGlassModifier: ViewModifier {
    var radius: CGFloat = 18
    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(OC.nightGlass))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(OC.nightBorder, lineWidth: 1))
    }
}

extension View {
    /// White 58% glass + blur + 1px white border + soft shadow.
    func glass(radius: CGFloat = OC.rCard, padding: CGFloat? = nil) -> some View {
        modifier(GlassModifier(radius: radius, padding: padding))
    }
    /// Ink 86% hero card.
    func inkGlass(radius: CGFloat = OC.rCard) -> some View { modifier(InkGlassModifier(radius: radius)) }
    /// White 7% on dark screens.
    func nightGlass(radius: CGFloat = 18) -> some View { modifier(NightGlassModifier(radius: radius)) }

    /// Standard page scaffold: warm background, hidden system nav bar.
    func ocPage() -> some View {
        self.background(WarmBackground()).toolbar(.hidden, for: .navigationBar)
    }
}

// MARK: - Buttons

struct GoldButtonStyle: ButtonStyle {
    var height: CGFloat = 54
    var fullWidth = true
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(16, .bold))
            .foregroundStyle(OC.inkFixed)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: height)
            .padding(.horizontal, fullWidth ? 0 : 18)
            .background(
                RoundedRectangle(cornerRadius: OC.rButton, style: .continuous)
                    .fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom))
            )
            .overlay(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).strokeBorder(Color.white.opacity(0.35), lineWidth: 0.5))
            .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
    }
}

struct InkButtonStyle: ButtonStyle {
    var height: CGFloat = 54
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(16, .bold))
            .foregroundStyle(Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614)))
            .frame(maxWidth: .infinity, minHeight: height)
            .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.ink))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct GlassButtonStyle: ButtonStyle {
    var height: CGFloat = 50
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(15, .bold))
            .foregroundStyle(OC.ink)
            .frame(maxWidth: .infinity, minHeight: height)
            .glass(radius: OC.rButton)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct DestructiveButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(16, .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.red))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

extension ButtonStyle where Self == GoldButtonStyle {
    static var gold: GoldButtonStyle { GoldButtonStyle() }
    static func gold(height: CGFloat, fullWidth: Bool = true) -> GoldButtonStyle { GoldButtonStyle(height: height, fullWidth: fullWidth) }
}
extension ButtonStyle where Self == InkButtonStyle { static var ink: InkButtonStyle { InkButtonStyle() } }
extension ButtonStyle where Self == GlassButtonStyle { static var glassButton: GlassButtonStyle { GlassButtonStyle() } }
extension ButtonStyle where Self == DestructiveButtonStyle { static var destructive: DestructiveButtonStyle { DestructiveButtonStyle() } }

/// 44pt round glass icon button (bell, profile, close…).
struct CircleIconButton: View {
    let systemName: String
    var label: String
    var badge: Int? = nil
    var size: CGFloat = 44
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(OC.ink)
                .frame(width: size, height: size)
                .glass(radius: size / 2)
                .overlay(alignment: .topTrailing) {
                    if let badge, badge > 0 {
                        Text("\(badge)")
                            .font(.outfit(11, .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .frame(minWidth: 18, minHeight: 18)
                            .background(Capsule().fill(OC.red))
                            .overlay(Capsule().strokeBorder(OC.background, lineWidth: 2))
                            .offset(x: 2, y: -2)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Top bar

/// Back chevron · centred title · optional trailing view. Matches the 54pt-top header on most screens.
struct TopBar<Trailing: View>: View {
    var title: String = ""
    var backLabel = "Back"
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        HStack(spacing: 0) {
            Button {
                if let onBack { onBack() } else { dismiss() }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(OC.ink)
            .accessibilityLabel(backLabel)
            Spacer(minLength: 0)
            Text(title).font(.outfit(16, .bold)).foregroundStyle(OC.ink).lineLimit(1)
            Spacer(minLength: 0)
            trailing().frame(minWidth: 44, alignment: .trailing)
        }
        .padding(.horizontal, 10)
        .frame(height: 44)
    }
}

extension TopBar where Trailing == EmptyView {
    init(title: String = "", backLabel: String = "Back", onBack: (() -> Void)? = nil) {
        self.init(title: title, backLabel: backLabel, onBack: onBack, trailing: { EmptyView() })
    }
}

// MARK: - Small pieces

/// Uppercase eyebrow with a short gold rule: "── ADS KEEP O CHORDS FREE"
struct Eyebrow: View {
    let text: String
    var color: Color = OC.muted
    var body: some View {
        HStack(spacing: 8) {
            Rectangle().fill(OC.gold).frame(width: 18, height: 2)
            Text(text.uppercased()).font(.outfit(12, .semibold)).tracking(1.9).foregroundStyle(color)
        }
    }
}

enum SectionKind: String, Codable, Hashable { case verse, chorus, music, flow, bridge, intro }

/// VERSE / CHORUS / MUSIC tags.
struct SectionTag: View {
    let label: String
    let kind: SectionKind
    var body: some View {
        let (bg, fg) = Self.colors(kind)
        Text(label.uppercased())
            .font(.outfit(11, .heavy)).tracking(1)
            .foregroundStyle(fg)
            .padding(.horizontal, 8).frame(height: 22)
            .background(RoundedRectangle(cornerRadius: 6).fill(bg))
    }
    static func colors(_ k: SectionKind) -> (Color, Color) {
        switch k {
        case .verse, .intro: (OC.blueTint, OC.blue)
        case .chorus: (OC.gold, OC.inkFixed)
        case .music: (OC.tealTint, OC.tealText)
        case .bridge: (Color(hex: 0x6D28D9, alpha: 0.14), OC.purple)
        case .flow: (OC.softFill, OC.muted)
        }
    }
}

enum PillTone { case teal, gold, red, neutral, blue, ink }

struct StatusPill: View {
    let text: String
    var tone: PillTone = .teal
    var body: some View {
        let (bg, fg): (Color, Color) = switch tone {
        case .teal: (OC.tealTint, OC.tealText)
        case .gold: (OC.goldTint, OC.goldText)
        case .red: (OC.redTint, OC.red)
        case .neutral: (OC.softFill, OC.muted)
        case .blue: (OC.blueTint, OC.blue)
        case .ink: (OC.ink, OC.background)
        }
        Text(text).font(.outfit(12, .bold)).foregroundStyle(fg)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Capsule().fill(bg))
    }
}

/// Selectable chip (filters, chord chips).
struct Chip: View {
    let text: String
    var isOn: Bool
    var mono = false
    var height: CGFloat = 36
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text)
                .font(mono ? .mono(15) : .outfit(14, .semibold))
                .foregroundStyle(isOn ? OC.inkFixed : OC.ink)
                .padding(.horizontal, 12)
                .frame(minWidth: 44, minHeight: height)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isOn ? OC.gold : OC.glass))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isOn ? OC.gold : OC.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// Ink-on / grey-off switch (gold when dark).
struct OCToggleStyle: ToggleStyle {
    @Environment(\.colorScheme) private var scheme
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            configuration.label
            Spacer(minLength: 8)
            Button { withAnimation(.snappy(duration: 0.2)) { configuration.isOn.toggle() } } label: {
                Capsule()
                    .fill(configuration.isOn ? (scheme == .dark ? OC.gold : OC.inkFixed) : OC.switchOff)
                    .frame(width: 52, height: 32)
                    .overlay(alignment: configuration.isOn ? .trailing : .leading) {
                        Circle().fill(.white).frame(width: 26, height: 26).padding(3)
                            .shadow(color: .black.opacity(0.2), radius: 3, y: 2)
                    }
            }
            .buttonStyle(.plain)
        }
    }
}

extension ToggleStyle where Self == OCToggleStyle { static var oc: OCToggleStyle { OCToggleStyle() } }

/// Pill segmented control on a soft fill.
struct Segmented<T: Hashable>: View {
    let options: [T]
    @Binding var selection: T
    let label: (T) -> String
    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { o in
                let on = o == selection
                Button { withAnimation(.snappy(duration: 0.2)) { selection = o } } label: {
                    Text(label(o))
                        .font(.outfit(13, .bold))
                        .foregroundStyle(on ? OC.ink : OC.muted)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background {
                            if on { RoundedRectangle(cornerRadius: 12).fill(OC.surface).shadow(color: .black.opacity(0.06), radius: 3, y: 1) }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 15).fill(OC.softFill))
    }
}

/// Glass list group with hairline dividers.
struct GlassList<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(spacing: 0) {
            Group(subviews: content()) { subs in
                ForEach(Array(subs.enumerated()), id: \.offset) { i, s in
                    if i > 0 { Rectangle().fill(OC.line).frame(height: 1) }
                    s
                }
            }
        }
        .glass()
    }
}

/// Row inside a GlassList: title (+subtitle) with a trailing chevron / value.
struct ListRow: View {
    let title: String
    var subtitle: String? = nil
    var value: String? = nil
    var systemImage: String? = nil
    var tint: Color = OC.ink
    var chevron = true
    var body: some View {
        HStack(spacing: 12) {
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: 16, weight: .medium)).foregroundStyle(tint).frame(width: 24)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.outfit(15, .medium)).foregroundStyle(tint)
                if let subtitle { Text(subtitle).font(.outfit(12)).foregroundStyle(OC.muted) }
            }
            Spacer(minLength: 8)
            if let value { Text(value).font(.outfit(14)).foregroundStyle(OC.muted) }
            if chevron { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(OC.faint) }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 52)
        .contentShape(Rectangle())
    }
}

/// Placeholder text in [brackets] — rendered as real text for the demo.
struct PlaceholderBox: View {
    let text: String
    var height: CGFloat = 120
    var dark = false
    var body: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(dark ? Color.white.opacity(0.06) : OC.softFill)
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [5, 4])).foregroundStyle(dark ? Color.white.opacity(0.25) : OC.faint.opacity(0.5)))
            .overlay(Text(text).font(.outfit(14, .bold)).foregroundStyle(dark ? OC.nightMuted : OC.muted))
            .frame(height: height)
    }
}

// MARK: - Toast

struct Toast: Equatable { var text: String; var systemImage: String = "checkmark.circle.fill" }

struct ToastModifier: ViewModifier {
    @Binding var toast: Toast?
    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let toast {
                HStack(spacing: 8) {
                    Image(systemName: toast.systemImage).foregroundStyle(OC.gold)
                    Text(toast.text).font(.outfit(14, .semibold)).foregroundStyle(OC.paper)
                }
                .padding(.horizontal, 16).frame(height: 44)
                .background(Capsule().fill(OC.inkGlass))
                .padding(.bottom, 110)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(for: .seconds(2))
                    withAnimation { self.toast = nil }
                }
            }
        }
        .animation(.snappy, value: toast)
    }
}

extension View {
    func toast(_ toast: Binding<Toast?>) -> some View { modifier(ToastModifier(toast: toast)) }
}

/// Shown to guests where account details would be: what an account adds, with Sign up / Log in.
struct GuestAccountCard: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "person.crop.circle.badge.plus")
                    .font(.system(size: 22, weight: .semibold)).foregroundStyle(OC.goldText)
                    .frame(width: 52, height: 52)
                    .background(Circle().fill(OC.goldTint))
                VStack(alignment: .leading, spacing: 2) {
                    Text("You’re using O Chords as a guest").font(.outfit(17, .bold)).foregroundStyle(OC.ink)
                    Text("Your songs are saved on this phone. Create an account to keep them safe, share sheets, earn O Points and read Myanmar lyrics when you scan.")
                        .font(.outfit(13)).foregroundStyle(OC.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack(spacing: 8) {
                Button("Create account") { app.showAccountOptions(signUp: true) }.buttonStyle(.gold(height: 44))
                Button("Log in") { app.showAccountOptions(signUp: false) }.buttonStyle(GlassButtonStyle(height: 44))
            }
            if !app.library.isEmpty {
                Text("Your \(app.library.count) song\(app.library.count == 1 ? "" : "s") will move into your account.")
                    .font(.outfit(12)).foregroundStyle(OC.muted)
            }
        }
        .padding(14)
        .glass()
    }
}
