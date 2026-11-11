import SwiftUI
import AVFoundation
import UserNotifications

// MARK: - Splash

/// Launch screen: six-string mark + wordmark, tagline, "Powered by Onez Codes". Auto-advances after ~1.6s.
struct SplashView: View {
    @Environment(AppState.self) private var app
    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 14) {
                HStack(spacing: 8) {
                    OnboardingSplashMark()
                        .frame(width: 64, height: 64)
                    Text("Chords")
                        .font(.outfit(52, .heavy))
                        .tracking(-0.5)
                        .foregroundStyle(OC.ink)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("O Chords")
                Text("EVERY CHORD SHEET, READY TO PLAY")
                    .font(.outfit(11, .semibold))
                    .tracking(2)
                    .foregroundStyle(OC.goldText)
            }
            .scaleEffect(appeared ? 1 : 0.94)
            .opacity(appeared ? 1 : 0)
            Spacer(minLength: 0)
            Button { finish() } label: {
                VStack(spacing: 10) {
                    Text("POWERED BY").font(.outfit(11, .semibold)).tracking(3).foregroundStyle(OC.faint)
                    Image("OnezLogo")
                        .resizable().scaledToFit()
                        .frame(width: 168)
                        .padding(6)
                        .background(RoundedRectangle(cornerRadius: 10).fill(.white))
                        .accessibilityLabel("Onez Codes")
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Continue")
            .padding(.bottom, 46)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(light: .white, dark: UIColor(hex: 0x0B0A09)).ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task {
            withAnimation(.easeOut(duration: 0.5)) { appeared = true }
            // A returning player's saved session loads while the logo shows.
            async let restored: Void = app.restoreSession()
            try? await Task.sleep(for: .seconds(1.6))
            await restored
            finish()
        }
    }

    private func finish() {
        guard app.phase == .splash else { return }
        app.finishSplash()
    }
}

/// The launch-screen mark: gold ring with six strings.
private struct OnboardingSplashMark: View {
    var body: some View {
        Canvas { ctx, sz in
            let s = sz.width / 100
            let ring = Path(ellipseIn: CGRect(x: 22 * s, y: 22 * s, width: 56 * s, height: 56 * s))
            ctx.stroke(ring, with: .color(OC.gold), lineWidth: 15 * s)
            let strings: [(CGFloat, CGFloat)] = [(35, 2.6), (41, 2.3), (47, 2.0), (53, 1.8), (59, 1.6), (65, 1.4)]
            for (x, w) in strings {
                var p = Path()
                p.move(to: CGPoint(x: x * s, y: 12 * s))
                p.addLine(to: CGPoint(x: x * s, y: 88 * s))
                ctx.stroke(p, with: .color(OC.ink), style: StrokeStyle(lineWidth: w * s, lineCap: .round))
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - Onboarding tour (Onboard · OnboardPlay · OnboardLearn)

struct OnboardingView: View {
    @Environment(AppState.self) private var app
    @State private var page = 0

    private let pages: [(title: String, sub: String)] = [
        ("Snap any chord sheet", "Printed, handwritten or a screenshot — Myanmar or English. We turn it into a clean song in seconds."),
        ("Play it your way", "Change the key, add a capo, swap hard chords for easy ones, then go full-screen on stage."),
        ("Learn and keep your groove", "Short daily sessions on a guitar-neck path. Earn O Points and climb the tiers."),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Wordmark(size: 28).padding(.leading, 12)
                Spacer()
                Button("Skip") { app.finishOnboarding() }
                    .font(.outfit(14, .bold))
                    .foregroundStyle(OC.muted)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 44)
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, 10)
            .padding(.top, 10)

            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { i in
                    OnboardingTourPage(index: i, title: pages[i].title, sub: pages[i].sub).tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            VStack(spacing: 18) {
                HStack(spacing: 6) {
                    ForEach(pages.indices, id: \.self) { i in
                        Capsule()
                            .fill(i == page ? OC.gold : OC.ink.opacity(0.15))
                            .frame(width: i == page ? 24 : 8, height: 8)
                    }
                }
                .animation(.snappy, value: page)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(page + 1) of \(pages.count)")

                if page < pages.count - 1 {
                    Button("Next") { withAnimation(.snappy) { page += 1 } }
                        .buttonStyle(.gold)
                } else {
                    Button("Get started") { app.finishOnboarding() }
                        .buttonStyle(.gold)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 20)
        }
        .ocPage()
    }
}

private struct OnboardingTourPage: View {
    let index: Int
    let title: String
    let sub: String

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            Group {
                switch index {
                case 0: OnboardingScanArt()
                case 1: OnboardingPlayArt()
                default: OnboardingGrooveArt()
                }
            }
            .frame(width: 300, height: 330)
            .accessibilityHidden(true)
            Spacer(minLength: 0)
            VStack(spacing: 10) {
                Text("\(index + 1) OF 3")
                    .font(.outfit(12, .bold)).tracking(1.8)
                    .foregroundStyle(OC.goldText)
                Text(title)
                    .font(.outfit(32, .heavy)).tracking(-1)
                    .foregroundStyle(OC.ink)
                    .frame(minHeight: 70, alignment: .bottom)
                Text(sub)
                    .font(.outfit(16))
                    .lineSpacing(4)
                    .foregroundStyle(OC.muted)
                    .frame(minHeight: 72, alignment: .top)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 28)
        }
    }
}

private struct OnboardingChordChip: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.mono(15))
            .foregroundStyle(Color(hex: 0x8F5E00))
            .padding(.horizontal, 12)
            .frame(height: 36)
            .background(RoundedRectangle(cornerRadius: 10).fill(.white))
            .shadow(color: Color.black.opacity(0.12), radius: 12, y: 10)
    }
}

/// Phone scanning a paper sheet, with floating chord chips.
private struct OnboardingScanArt: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            // Phone
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: 0x3A332A), Color(hex: 0x1E1B17)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay {
                    GeometryReader { g in
                        ZStack(alignment: .topLeading) {
                            // Paper
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach([0.7, 0.9, 0.55, 0.85, 0.6, 0.8, 0.5], id: \.self) { w in
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(Color(hex: 0x28221A, alpha: 0.5))
                                        .frame(width: (g.size.width - 56) * w, height: 4)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 10).padding(.vertical, 12)
                            .frame(width: g.size.width - 36, height: g.size.height - 100, alignment: .topLeading)
                            .background(RoundedRectangle(cornerRadius: 4).fill(Color(hex: 0xE9E2D2)))
                            .rotationEffect(.degrees(-4))
                            .offset(x: 18, y: 40)
                            // Scan glow + line
                            LinearGradient(colors: [OC.gold.opacity(0), OC.gold.opacity(0.35)], startPoint: .top, endPoint: .bottom)
                                .frame(width: g.size.width - 20, height: 34)
                                .offset(x: 10, y: 88)
                            Rectangle()
                                .fill(OC.gold)
                                .frame(width: g.size.width - 20, height: 2)
                                .shadow(color: OC.gold.opacity(0.6), radius: 8)
                                .offset(x: 10, y: 120)
                            // Shutter
                            Circle()
                                .strokeBorder(.white, lineWidth: 3.5)
                                .frame(width: 46, height: 46)
                                .offset(x: g.size.width / 2 - 23, y: g.size.height - 60)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .padding(10)
                .frame(width: 160, height: 300)
                .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(OC.inkFixed))
                .shadow(color: OC.inkFixed.opacity(0.3), radius: 25, y: 24)
                .offset(x: 70, y: 10)

            OnboardingChordChip(text: "C").rotationEffect(.degrees(-8)).offset(x: 10, y: 60)
            OnboardingChordChip(text: "G/B").rotationEffect(.degrees(6)).offset(x: 220, y: 40)
            OnboardingChordChip(text: "Am").rotationEffect(.degrees(5)).offset(x: 0, y: 200)
            OnboardingChordChip(text: "F").rotationEffect(.degrees(-6)).offset(x: 240, y: 220)
        }
        .frame(width: 300, height: 330, alignment: .topLeading)
    }
}

/// Transpose card, tool chips and a lyric line.
private struct OnboardingPlayArt: View {
    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Text("−").font(.outfit(28, .semibold))
                Spacer()
                VStack(spacing: 0) {
                    Text("SOUNDS IN").font(.outfit(11, .bold)).foregroundStyle(OC.muted)
                    Text("D").font(.mono(34)).foregroundStyle(OC.goldText)
                }
                Spacer()
                Text("+").font(.outfit(28, .semibold))
            }
            .foregroundStyle(OC.ink)
            .padding(16)
            .frame(width: 250)
            .glass(radius: 22)

            HStack(spacing: 8) {
                Text("Capo 2")
                    .foregroundStyle(OC.inkFixed)
                    .padding(.horizontal, 14).frame(height: 40)
                    .background(RoundedRectangle(cornerRadius: 12).fill(OC.gold))
                Text("Easy chords")
                    .foregroundStyle(OC.ink)
                    .padding(.horizontal, 14).frame(height: 40)
                    .glass(radius: 12)
                Text("Stage")
                    .foregroundStyle(OC.paper)
                    .padding(.horizontal, 14).frame(height: 40)
                    .background(RoundedRectangle(cornerRadius: 12).fill(OC.inkFixed))
            }
            .font(.outfit(14, .bold))

            VStack(alignment: .leading, spacing: 4) {
                Text("C       G/B").font(.mono(14)).foregroundStyle(OC.gold)
                Text("ငါ မကြားချင် ခဲ့သော စကား").font(.myanmar(15)).lineSpacing(6).foregroundStyle(OC.paper)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(width: 250, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(OC.inkFixed))
        }
    }
}

/// Weekly groove chart + fret path progress.
private struct OnboardingGrooveArt: View {
    private let bars: [CGFloat] = [30, 52, 40, 70, 46, 84, 20]

    var body: some View {
        VStack(spacing: 18) {
            VStack(spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Your groove").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                    Spacer()
                    Text("7 days").font(.outfit(26, .heavy)).foregroundStyle(OC.goldText)
                }
                HStack(alignment: .bottom) {
                    ForEach(bars.indices, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 5)
                            .fill(i == bars.count - 1 ? OC.ink.opacity(0.12) : OC.gold)
                            .frame(width: 18, height: bars[i])
                        if i < bars.count - 1 { Spacer(minLength: 0) }
                    }
                }
                .frame(height: 90, alignment: .bottom)
                HStack {
                    ForEach(Array(["M", "T", "W", "T", "F", "S", "S"].enumerated()), id: \.offset) { i, d in
                        Text(d).frame(width: 18)
                        if i < 6 { Spacer(minLength: 0) }
                    }
                }
                .font(.outfit(11, .bold))
                .foregroundStyle(OC.muted)
            }
            .padding(18)
            .frame(width: 250)
            .glass(radius: 22)

            HStack(spacing: 8) {
                ForEach(0..<2, id: \.self) { _ in
                    Image(systemName: "checkmark")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(Color(hex: 0x0F766E)))
                }
                Text("3").font(.outfit(16, .heavy)).foregroundStyle(OC.inkFixed)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(OC.gold))
                    .background(Circle().fill(OC.gold.opacity(0.25)).padding(-6))
                Text("4").font(.outfit(16, .heavy)).foregroundStyle(OC.muted)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(OC.ink.opacity(0.08)))
            }
        }
    }
}

// MARK: - Welcome (always dark)

struct WelcomeView: View {
    @Environment(AppState.self) private var app
    @State private var appleLoading = false
    @State private var toast: Toast?

    private let subText = Color(hex: 0xBDB9B3)

    var body: some View {
        OnboardingFillScroll {
            VStack(alignment: .leading, spacing: 0) {
                header
                VStack(alignment: .leading, spacing: 6) {
                    Text("C · G/B · Am · F")
                        .font(.mono(18)).tracking(1)
                        .foregroundStyle(OC.gold)
                    Text("Every chord sheet, ready to play.")
                        .font(.outfit(40, .bold)).tracking(-1.5)
                        .lineSpacing(-4)
                        .foregroundStyle(OC.nightText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 26)

                VStack(alignment: .leading, spacing: 14) {
                    feature("camera", "Scan any sheet", "Handwritten or printed — Myanmar and English lyrics.")
                    feature("arrow.up.arrow.down", "Transpose, tune and learn", "Change key in a tap, with finger guides for every chord.")
                    feature("globe", "Share with the community", "Find sheets others uploaded, and share your own.")
                }
                .padding(.top, 22)

                Spacer(minLength: 28)
                actions
            }
            .padding(.horizontal, 24)
            .padding(.top, 20)
            .padding(.bottom, 20)
        }
        .background(NightBackground())
        .environment(\.colorScheme, .dark)
        .toolbar(.hidden, for: .navigationBar)
        .toast($toast)
    }

    private var header: some View {
        HStack {
            Wordmark(size: 32, color: .white)
            Spacer()
            HStack(spacing: 0) {
                langButton("မြန်မာ", font: .myanmar(13, .semibold))
                langButton("English", font: .outfit(13, .bold))
            }
            .padding(3)
            .nightGlass(radius: 20)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Language")
        }
    }

    private func langButton(_ lang: String, font: Font) -> some View {
        let on = app.language == lang
        return Button { withAnimation(.snappy(duration: 0.2)) { app.language = lang } } label: {
            Text(lang)
                .font(font)
                .foregroundStyle(on ? Color(hex: 0x0F0E0D) : subText)
                .padding(.horizontal, 10)
                .frame(minWidth: 44, minHeight: 36)
                .background(RoundedRectangle(cornerRadius: 14).fill(on ? OC.paper : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func feature(_ icon: String, _ title: String, _ sub: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(OC.gold)
                .frame(width: 40, height: 40)
                .nightGlass(radius: 14)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.outfit(16, .bold)).foregroundStyle(OC.nightText)
                Text(sub).font(.outfit(14)).lineSpacing(2).foregroundStyle(subText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 10) {
            Button {
                guard !appleLoading else { return }
                Task {
                    appleLoading = true
                    defer { appleLoading = false }
                    do {
                        if app.usesDemoData {
                            try? await Task.sleep(for: .milliseconds(800))
                            app.signedIn(MockData.profile)
                        } else {
                            let apple = try await SocialSignIn.apple()
                            app.signedIn(try await app.backend.signInWithApple(identityToken: apple.identityToken, name: apple.name))
                        }
                    } catch SocialSignIn.Failure.cancelled {
                    } catch {
                        toast = Toast(text: error.localizedDescription, systemImage: "exclamationmark.triangle.fill")
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    if appleLoading { ProgressView().tint(Color(hex: 0x0F0E0D)) } else { Image(systemName: "apple.logo").font(.system(size: 18)) }
                    Text("Continue with Apple")
                }
                .font(.outfit(16, .bold))
                .foregroundStyle(Color(hex: 0x0F0E0D))
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.paper))
            }
            .buttonStyle(.plain)
            .disabled(appleLoading)

            Button { app.push(.signUp) } label: {
                Text("Sign up with email")
                    .font(.outfit(16, .bold))
                    .foregroundStyle(OC.paper)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .overlay(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).strokeBorder(Color(hex: 0x4A4743)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button("I already have an account · Log in") { app.push(.signIn) }
                .font(.outfit(15, .bold))
                .foregroundStyle(OC.paper)
                .frame(maxWidth: .infinity, minHeight: 44)
                .buttonStyle(.plain)

            Button("Browse shared chords first") {
                app.continueAsGuest()
                app.switchTo(.explore)
            }
            .font(.outfit(15, .bold))
            .foregroundStyle(Color(hex: 0xFFC940))
            .frame(maxWidth: .infinity, minHeight: 44)
            .buttonStyle(.plain)

            HStack(spacing: 10) {
                Text("POWERED BY").font(.outfit(10, .semibold)).tracking(2.4).foregroundStyle(OC.nightMuted)
                Image("OnezLogo")
                    .resizable().scaledToFit()
                    .frame(width: 92)
                    .padding(.horizontal, 8).padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 10).fill(.white))
                    .accessibilityLabel("Onez Codes")
            }

            OnboardingLegalLine(prefix: "By continuing you agree to the ", terms: "Terms", privacy: "Privacy Policy", suffix: ".",
                                baseColor: OC.faint, linkColor: Color(hex: 0xFFC940), bold: false)
                .font(.outfit(12))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }
}

/// Inline legal sentence where "Terms" and "Privacy Policy" open their screens.
private struct OnboardingLegalLine: View {
    @Environment(AppState.self) private var app
    let prefix: String
    let terms: String
    let privacy: String
    let suffix: String
    var middle = " and "
    var baseColor: Color = OC.ink
    var linkColor: Color = OC.goldText
    var bold = true

    var body: some View {
        Text(attributed)
            .environment(\.openURL, OpenURLAction { url in
                switch url.host() {
                case "terms": app.push(.terms)
                case "privacy": app.push(.privacy)
                default: break
                }
                return .handled
            })
    }

    private var attributed: AttributedString {
        var s = AttributedString(prefix)
        s.foregroundColor = baseColor
        var t = AttributedString(terms)
        t.link = URL(string: "ochords://terms")
        t.foregroundColor = linkColor
        t.underlineStyle = .single
        if bold { t.font = .outfit(14, .bold) }
        var m = AttributedString(middle)
        m.foregroundColor = baseColor
        var p = AttributedString(privacy)
        p.link = URL(string: "ochords://privacy")
        p.foregroundColor = linkColor
        p.underlineStyle = .single
        if bold { p.font = .outfit(14, .bold) }
        var e = AttributedString(suffix)
        e.foregroundColor = baseColor
        return s + t + m + p + e
    }
}

// MARK: - Sign in (log in → forgot → sent)

struct SignInView: View {
    @Environment(AppState.self) private var app

    private enum Mode { case signIn, forgot, sent }
    private enum Field { case email, password, resetEmail }

    @State private var mode: Mode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var busy = false
    @State private var socialBusy: String? = nil
    @State private var error: String? = nil
    @FocusState private var focus: Field?

    var body: some View {
        VStack(spacing: 0) {
            TopBar(onBack: { app.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    switch mode {
                    case .signIn: signInForm
                    case .forgot: forgotForm
                    case .sent: sentState
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 20)
                .animation(.snappy, value: error)
            }
            .scrollDismissesKeyboard(.interactively)

            HStack(spacing: 4) {
                Text("New to O Chords?").foregroundStyle(OC.muted)
                Button("Create an account") { goToSignUp() }
                    .fontWeight(.bold)
                    .foregroundStyle(OC.goldText)
                    .buttonStyle(.plain)
                    .frame(minHeight: 44)
            }
            .font(.outfit(14))
            .padding(.bottom, 12)
        }
        .ocPage()
    }

    // Log in
    @ViewBuilder private var signInForm: some View {
        OnboardingHeading(eyebrow: "Welcome back", title: "Log in")
        OnboardingField(label: "Email") {
            TextField("you@example.com", text: $email, prompt: onboardingPrompt("you@example.com"))
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .email)
                .submitLabel(.next)
                .onSubmit { focus = .password }
        }
        OnboardingField(label: "Password", trailingPadding: 4) {
            HStack(spacing: 0) {
                Group {
                    if showPassword {
                        TextField("Your password", text: $password, prompt: onboardingPrompt("Your password"))
                    } else {
                        SecureField("Your password", text: $password, prompt: onboardingPrompt("Your password"))
                    }
                }
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .password)
                .submitLabel(.go)
                .onSubmit { logIn() }
                Button(showPassword ? "Hide" : "Show") { showPassword.toggle() }
                    .font(.outfit(13, .bold))
                    .foregroundStyle(OC.goldText)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 44)
                    .buttonStyle(.plain)
                    .accessibilityLabel(showPassword ? "Hide password" : "Show password")
            }
        }
        HStack {
            if let error { OnboardingErrorText(text: error) }
            Spacer(minLength: 8)
            Button("Forgot password?") {
                error = nil
                withAnimation(.snappy) { mode = .forgot }
            }
            .font(.outfit(14, .bold))
            .foregroundStyle(OC.goldText)
            .frame(minHeight: 32)
            .buttonStyle(.plain)
        }
        .padding(.vertical, -6)
        Button { logIn() } label: {
            OnboardingBusyLabel(title: "Log in", busy: busy)
        }
        .buttonStyle(.gold)
        .disabled(busy)

        HStack(spacing: 12) {
            Rectangle().fill(OC.border).frame(height: 1)
            Text("or").font(.outfit(12)).foregroundStyle(OC.faint)
            Rectangle().fill(OC.border).frame(height: 1)
        }

        VStack(spacing: 10) {
            Button { social("apple") } label: {
                HStack(spacing: 8) {
                    if socialBusy == "apple" { ProgressView() } else { Image(systemName: "apple.logo").font(.system(size: 16)) }
                    Text("Continue with Apple")
                }
                .font(.outfit(15, .bold))
                .foregroundStyle(Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614)))
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.ink))
            }
            .buttonStyle(.plain)
            Button { social("google") } label: {
                HStack(spacing: 8) {
                    if socialBusy == "google" { ProgressView() }
                    Text("Continue with Google")
                }
                .font(.outfit(15, .bold))
                .foregroundStyle(OC.ink)
                .frame(maxWidth: .infinity, minHeight: 50)
                .overlay(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).strokeBorder(OC.ink))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .disabled(socialBusy != nil)
    }

    // Reset password
    @ViewBuilder private var forgotForm: some View {
        OnboardingHeading(eyebrow: "Reset password", title: "Forgot your password?",
                          sub: "Enter the email on your account and we’ll send a reset link.")
        OnboardingField(label: "Email") {
            TextField("you@example.com", text: $email, prompt: onboardingPrompt("you@example.com"))
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .resetEmail)
                .submitLabel(.send)
                .onSubmit { sendReset() }
        }
        if let error { OnboardingErrorText(text: error) }
        Button { sendReset() } label: {
            OnboardingBusyLabel(title: "Send reset link", busy: busy)
        }
        .buttonStyle(.gold)
        .disabled(busy)
        Button("Back to log in") { backToLogIn() }
            .font(.outfit(14, .bold))
            .foregroundStyle(OC.goldText)
            .frame(maxWidth: .infinity, minHeight: 44)
            .buttonStyle(.plain)
    }

    // Check your email
    @ViewBuilder private var sentState: some View {
        VStack(spacing: 14) {
            Image(systemName: "envelope")
                .font(.system(size: 30, weight: .medium))
                .foregroundStyle(OC.goldText)
                .frame(width: 72, height: 72)
                .background(Circle().fill(OC.goldTint))
            Text("Check your email")
                .font(.outfit(28, .bold)).tracking(-1)
                .foregroundStyle(OC.ink)
            Text("If an account exists for that address, a reset link is on its way. It expires in 30 minutes.")
                .font(.outfit(15)).lineSpacing(3)
                .foregroundStyle(OC.muted)
            Button("Back to log in") { backToLogIn() }
                .buttonStyle(.ink)
                .padding(.top, 10)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.top, 48)
        .transition(.opacity)
    }

    // MARK: Actions

    private func logIn() {
        let e = email.trimmingCharacters(in: .whitespaces)
        guard e.contains("@") else { error = "Enter the email on your account."; focus = .email; return }
        guard !password.isEmpty else { error = "Enter your password."; focus = .password; return }
        focus = nil
        error = nil
        busy = true
        Task {
            defer { busy = false }
            do {
                let profile = try await app.backend.signIn(email: e, password: password)
                app.signedIn(profile)
            } catch ServiceError.offline {
                self.error = "You’re offline. Check your connection and try again."
            } catch let failure as ServiceError {
                self.error = failure.errorDescription
            } catch {
                self.error = "That email and password don’t match. Try again."
            }
        }
    }

    private func sendReset() {
        let e = email.trimmingCharacters(in: .whitespaces)
        guard e.contains("@") else { error = "Enter a valid email address."; return }
        focus = nil
        error = nil
        busy = true
        Task {
            defer { busy = false }
            do {
                try await app.backend.sendReset(email: e)
                withAnimation(.snappy) { mode = .sent }
            } catch {
                self.error = "Couldn’t send the link. Check your connection and try again."
            }
        }
    }

    /// Apple's or Google's sheet, then the API (`auth/apple` / `auth/google`). The demo build fakes it.
    private func social(_ provider: String) {
        guard socialBusy == nil else { return }
        focus = nil
        error = nil
        socialBusy = provider
        Task {
            defer { socialBusy = nil }
            do {
                if app.usesDemoData {
                    try? await Task.sleep(for: .milliseconds(800))
                    app.signedIn(MockData.profile)
                } else if provider == "apple" {
                    let apple = try await SocialSignIn.apple()
                    app.signedIn(try await app.backend.signInWithApple(identityToken: apple.identityToken, name: apple.name))
                } else {
                    let token = try await SocialSignIn.google()
                    app.signedIn(try await app.backend.signInWithGoogle(idToken: token))
                }
            } catch SocialSignIn.Failure.cancelled {
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    private func backToLogIn() {
        error = nil
        withAnimation(.snappy) { mode = .signIn }
    }

    private func goToSignUp() {
        if app.authPath.dropLast().last == .signUp { app.pop() } else { app.push(.signUp) }
    }
}

// MARK: - Reset password (from the email link)

/// Opened by `ochords://reset-password?token=…`. The email isn't in the link, so the player types it again:
/// the API checks both before changing the password and signs out every other device.
struct ResetPasswordView: View {
    let token: String
    @Environment(AppState.self) private var app

    private enum Field { case email, password }

    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var busy = false
    @State private var done = false
    @State private var error: String? = nil
    @FocusState private var focus: Field?

    var body: some View {
        VStack(spacing: 0) {
            TopBar(onBack: { app.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if done { doneState } else { form }
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 20)
                .animation(.snappy, value: error)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .ocPage()
    }

    @ViewBuilder private var form: some View {
        OnboardingHeading(eyebrow: "Reset password", title: "Choose a new password",
                          sub: "Enter the email on your account and a new password. You’ll be logged out everywhere else.")
        OnboardingField(label: "Email") {
            TextField("you@example.com", text: $email, prompt: onboardingPrompt("you@example.com"))
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .email)
                .submitLabel(.next)
                .onSubmit { focus = .password }
        }
        OnboardingField(label: "New password", trailingPadding: 4) {
            HStack(spacing: 0) {
                Group {
                    if showPassword {
                        TextField("At least 8 characters", text: $password, prompt: onboardingPrompt("At least 8 characters"))
                    } else {
                        SecureField("At least 8 characters", text: $password, prompt: onboardingPrompt("At least 8 characters"))
                    }
                }
                .textContentType(.newPassword)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focus, equals: .password)
                .submitLabel(.go)
                .onSubmit { submit() }
                Button(showPassword ? "Hide" : "Show") { showPassword.toggle() }
                    .font(.outfit(13, .bold))
                    .foregroundStyle(OC.goldText)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 44)
                    .buttonStyle(.plain)
                    .accessibilityLabel(showPassword ? "Hide password" : "Show password")
            }
        }
        if let error { OnboardingErrorText(text: error) }
        Button { submit() } label: {
            OnboardingBusyLabel(title: "Save new password", busy: busy)
        }
        .buttonStyle(.gold)
        .disabled(busy)
    }

    private var doneState: some View {
        VStack(spacing: 14) {
            Image(systemName: "checkmark")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(OC.goldText)
                .frame(width: 72, height: 72)
                .background(Circle().fill(OC.goldTint))
            Text("Password changed")
                .font(.outfit(28, .bold)).tracking(-1)
                .foregroundStyle(OC.ink)
            Text("Log in with your new password.")
                .font(.outfit(15)).lineSpacing(3)
                .foregroundStyle(OC.muted)
            Button("Log in") { app.authPath = [.signIn] }
                .buttonStyle(.ink)
                .padding(.top, 10)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.top, 48)
        .transition(.opacity)
    }

    private func submit() {
        let e = email.trimmingCharacters(in: .whitespaces)
        guard e.contains("@") else { error = "Enter a valid email address."; return }
        guard password.count >= 8 else { error = "Use at least 8 characters."; return }
        focus = nil
        error = nil
        busy = true
        Task {
            defer { busy = false }
            do {
                try await app.backend.resetPassword(email: e, token: token, password: password)
                withAnimation(.snappy) { done = true }
            } catch let failure as ServiceError {
                self.error = failure == .offline ? "You’re offline. Check your connection and try again." : "That link has expired or doesn’t match this email. Ask for a new one."
            } catch {
                self.error = "That link has expired or doesn’t match this email. Ask for a new one."
            }
        }
    }
}

// MARK: - Sign up

struct SignUpView: View {
    @Environment(AppState.self) private var app

    private enum Field { case name, email, password }

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var instruments: Set<String> = ["Guitar"]
    @State private var agree = false
    @State private var busy = false
    @State private var error: String? = nil
    @FocusState private var focus: Field?

    private let allInstruments = ["Guitar", "Ukulele", "Bass", "Keys", "Vocals"]

    private var rules: [(String, Bool)] {
        [("At least 8 characters", password.count >= 8),
         ("A number", password.contains(where: \.isNumber)),
         ("An uppercase letter", password.contains(where: \.isUppercase))]
    }
    private var score: Int { rules.filter(\.1).count + (password.count >= 12 ? 1 : 0) }
    private var ready: Bool { agree && rules.allSatisfy(\.1) }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(onBack: { app.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    OnboardingHeading(eyebrow: "Join the community", title: "Create account")
                    OnboardingField(label: "Display name") {
                        TextField("How others see you", text: $name, prompt: onboardingPrompt("How others see you"))
                            .textContentType(.nickname)
                            .focused($focus, equals: .name)
                            .submitLabel(.next)
                            .onSubmit { focus = .email }
                    }
                    OnboardingField(label: "Email") {
                        TextField("you@example.com", text: $email, prompt: onboardingPrompt("you@example.com"))
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focus, equals: .email)
                            .submitLabel(.next)
                            .onSubmit { focus = .password }
                    }
                    OnboardingField(label: "Password") {
                        SecureField("At least 8 characters", text: $password, prompt: onboardingPrompt("At least 8 characters"))
                            .textContentType(.newPassword)
                            .focused($focus, equals: .password)
                            .submitLabel(.done)
                    }
                    passwordStrength
                    instrumentPicker
                    agreement
                    if let error { OnboardingErrorText(text: error) }
                }
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, 20)
                .animation(.snappy, value: password)
            }
            .scrollDismissesKeyboard(.interactively)
            footer
        }
        .ocPage()
    }

    private var passwordStrength: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(0..<4, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(i < score ? (score >= 3 ? OC.teal : OC.gold) : OC.ink.opacity(0.09))
                        .frame(height: 4)
                }
            }
            .accessibilityHidden(true)
            ForEach(rules, id: \.0) { rule in
                HStack(spacing: 8) {
                    Text(rule.1 ? "✓" : "·").font(.outfit(13, .heavy)).frame(width: 16)
                    Text(rule.0).font(.outfit(13))
                }
                .foregroundStyle(rule.1 ? OC.tealText : OC.muted)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(rule.0), \(rule.1 ? "done" : "not yet")")
            }
        }
    }

    private var instrumentPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            (Text("I play ").foregroundStyle(OC.ink) + Text("(pick any)").font(.outfit(13, .medium)).foregroundStyle(OC.muted))
                .font(.outfit(13, .semibold))
            OnboardingFlowLayout(spacing: 6) {
                ForEach(allInstruments, id: \.self) { inst in
                    let on = instruments.contains(inst)
                    Button {
                        if on { instruments.remove(inst) } else { instruments.insert(inst) }
                    } label: {
                        Text(inst)
                            .font(.outfit(14, .semibold))
                            .foregroundStyle(OC.ink)
                            .padding(.horizontal, 12)
                            .frame(minWidth: 44, minHeight: 38)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.goldTint : OC.glass))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.gold : OC.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    private var agreement: some View {
        HStack(alignment: .top, spacing: 10) {
            Button { agree.toggle() } label: {
                RoundedRectangle(cornerRadius: 8)
                    .fill(agree ? OC.gold : OC.glass)
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(agree ? OC.gold : OC.faint, lineWidth: 2))
                    .overlay {
                        if agree { Image(systemName: "checkmark").font(.system(size: 12, weight: .heavy)).foregroundStyle(OC.inkFixed) }
                    }
                    .frame(width: 22, height: 22)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-11)
            .accessibilityLabel("I agree to the Terms and Privacy Policy")
            .accessibilityAddTraits(agree ? .isSelected : [])
            OnboardingLegalLine(prefix: "I agree to the ", terms: "Terms & Conditions", privacy: "Privacy Policy", suffix: ".",
                                middle: " and have read the ")
                .font(.outfit(14))
                .lineSpacing(3)
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            Button { create() } label: {
                OnboardingBusyLabel(title: "Create account", busy: busy)
                    .font(.outfit(16, .bold))
                    .foregroundStyle(ready ? OC.inkFixed : OC.faint)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(ready ? OC.gold : OC.ink.opacity(0.08)))
            }
            .buttonStyle(.plain)
            .disabled(!ready || busy)
            HStack(spacing: 4) {
                Text("Already have an account?").foregroundStyle(OC.muted)
                Button("Log in") { goToSignIn() }
                    .fontWeight(.bold)
                    .foregroundStyle(OC.goldText)
                    .buttonStyle(.plain)
                    .frame(minHeight: 44)
            }
            .font(.outfit(14))
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background {
            Rectangle().fill(.ultraThinMaterial)
                .overlay(Rectangle().fill(OC.glass))
                .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
                .shadow(color: .black.opacity(0.05), radius: 12, y: -8)
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private func create() {
        let e = email.trimmingCharacters(in: .whitespaces)
        guard e.contains("@") else { error = "Enter a valid email address."; focus = .email; return }
        focus = nil
        error = nil
        busy = true
        Task {
            defer { busy = false }
            do {
                let profile = try await app.backend.signUp(name: name.trimmingCharacters(in: .whitespaces), email: e, password: password)
                app.signedIn(profile)
            } catch ServiceError.offline {
                self.error = "You’re offline. Check your connection and try again."
            } catch ServiceError.server(let message) {
                // e.g. "An account with this email already exists."
                self.error = message
            } catch {
                self.error = "Couldn’t create your account. Try again in a moment."
            }
        }
    }

    private func goToSignIn() {
        if app.authPath.dropLast().last == .signIn { app.pop() } else { app.push(.signIn) }
    }
}

// MARK: - Ad consent (modal)

struct AdConsentView: View {
    @Environment(AppState.self) private var app
    @State private var personalised = true

    private let options: [(title: String, sub: String, personalised: Bool)] = [
        ("Ads that fit me", "Google may use app activity to show ads you’re more likely to care about, like guitar gear.", true),
        ("General ads only", "Same number of ads, not based on your activity.", false),
    ]

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 24) {
                    AppIconTile(size: 104)
                    VStack(spacing: 10) {
                        Eyebrow(text: "Ads keep O Chords free")
                        Text("Choose how ads work for you")
                            .font(.outfit(28, .bold)).tracking(-0.7)
                            .foregroundStyle(OC.ink)
                        Text("The free plan shows a few ads from Google. Never while you play, tune or take a lesson.")
                            .font(.outfit(15)).lineSpacing(3)
                            .foregroundStyle(OC.muted)
                    }
                    .multilineTextAlignment(.center)
                    VStack(spacing: 8) {
                        ForEach(options, id: \.title) { o in option(o) }
                    }
                }
                .padding(.horizontal, 28)
                .padding(.top, 40)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)

            VStack(spacing: 6) {
                Button("Continue") {
                    app.personalisedAds = personalised
                    app.modal = nil
                    // iOS's tracking prompt only when they chose ads that fit them; then AdMob can start.
                    Task {
                        if personalised, !app.usesDemoData { _ = await app.ads.requestTracking() }
                        await app.ads.start()
                        app.ads.preloadInterstitial()
                    }
                }
                .buttonStyle(.gold)
                Button("No ads at all with Pro") { app.modal = .pro }
                    .font(.outfit(14, .bold))
                    .foregroundStyle(OC.ink)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .buttonStyle(.plain)
                Text("\(personalised ? "Next, iOS will ask to allow tracking." : "iOS won’t ask about tracking.") Change any time in Settings › Ad choices.")
                    .font(.outfit(12)).lineSpacing(2)
                    .foregroundStyle(OC.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 16)
        }
        .ocPage()
        .onAppear { personalised = app.personalisedAds }
    }

    private func option(_ o: (title: String, sub: String, personalised: Bool)) -> some View {
        let on = personalised == o.personalised
        return Button { withAnimation(.snappy(duration: 0.2)) { personalised = o.personalised } } label: {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .strokeBorder(on ? OC.ink : Color(hex: 0xC9C6C0), lineWidth: 2)
                    .overlay { if on { Circle().fill(OC.ink).frame(width: 10, height: 10) } }
                    .frame(width: 22, height: 22)
                    .padding(.top, 1)
                VStack(alignment: .leading, spacing: 3) {
                    Text(o.title).font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                    Text(o.sub).font(.outfit(13)).lineSpacing(2).foregroundStyle(OC.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .multilineTextAlignment(.leading)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(on ? OC.goldTint : OC.glass))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? OC.gold : OC.border, lineWidth: on ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

// MARK: - Permission primers (camera · microphone · notifications)

struct PermissionPrimerView: View {
    let kind: PermissionKind
    @Environment(AppState.self) private var app
    @State private var requesting = false

    private struct Content {
        let icon: String
        let eyebrow: String
        let title: String
        let points: [(String, String)]
        let cta: String
        let fine: String
    }

    private var content: Content {
        switch kind {
        case .camera:
            Content(icon: "camera", eyebrow: "Camera", title: "Scan chord sheets with your camera",
                    points: [("Scan paper sheets", "Point at a songbook or handwritten page — we read it in seconds."),
                             ("Only when you tap Scan", "The camera never runs in the background."),
                             ("Photos stay private", "Read to find chords and Myanmar lyrics, never stored, and only shared if you make a song public.")],
                    cta: "Allow camera",
                    fine: "Next, iOS will ask you to confirm. You can change this any time in Settings.")
        case .microphone:
            Content(icon: "mic", eyebrow: "Microphone", title: "Let O Chords hear your guitar",
                    points: [("Tune by ear", "The tuner listens to each string and shows how far off it is."),
                             ("Check your chords", "Hear which string buzzes or is muted."),
                             ("Never recorded", "Sound is analysed on your phone and never uploaded.")],
                    cta: "Allow microphone",
                    fine: "Next, iOS will ask you to confirm. You can change this any time in Settings.")
        case .notifications:
            Content(icon: "bell", eyebrow: "Notifications", title: "Keep your groove going",
                    points: [("One daily reminder", "At the time you choose — we’ll suggest 7:30 pm."),
                             ("Groove at risk", "A nudge before your streak ends, not after."),
                             ("Community news", "When someone saves or improves your sheet.")],
                    cta: "Turn on notifications",
                    fine: "You can pick which ones you get in Settings › Notifications.")
        }
    }

    var body: some View {
        let c = content
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { app.modal = nil } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(OC.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            .padding(.horizontal, 10)

            ScrollView {
                VStack(spacing: 26) {
                    Image(systemName: c.icon)
                        .font(.system(size: 34, weight: .regular))
                        .foregroundStyle(OC.goldText)
                        .frame(width: 104, height: 104)
                        .background(RoundedRectangle(cornerRadius: 34, style: .continuous).fill(OC.goldTint.opacity(0.85)))
                        .overlay(RoundedRectangle(cornerRadius: 34, style: .continuous).strokeBorder(OC.glassBorder))
                        .shadow(color: Color(hex: 0xFFAA00).opacity(0.18), radius: 20, y: 16)
                        .accessibilityHidden(true)
                    VStack(spacing: 10) {
                        Eyebrow(text: c.eyebrow)
                        Text(c.title)
                            .font(.outfit(30, .bold)).tracking(-0.8)
                            .foregroundStyle(OC.ink)
                            .multilineTextAlignment(.center)
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(c.points, id: \.0) { p in
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 12, weight: .heavy))
                                    .foregroundStyle(OC.tealText)
                                    .frame(width: 26, height: 26)
                                    .background(Circle().fill(OC.tealTint))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(p.0).font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                                    Text(p.1).font(.outfit(14)).lineSpacing(2).foregroundStyle(OC.muted)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                Spacer(minLength: 0)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                    .padding(18)
                    .glass()
                }
                .padding(.horizontal, 28)
                .padding(.top, 20)
            }
            .scrollBounceBehavior(.basedOnSize)
            .defaultScrollAnchor(.center)

            VStack(spacing: 6) {
                Button { allow() } label: {
                    OnboardingBusyLabel(title: c.cta, busy: requesting)
                }
                .buttonStyle(.gold)
                .disabled(requesting)
                Button("Not now") { app.modal = nil }
                    .font(.outfit(15, .bold))
                    .foregroundStyle(OC.muted)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .buttonStyle(.plain)
                Text(c.fine)
                    .font(.outfit(12)).lineSpacing(2)
                    .foregroundStyle(OC.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 16)
        }
        .ocPage()
    }

    private func allow() {
        requesting = true
        Task {
            switch kind {
            case .camera:
                _ = await AVCaptureDevice.requestAccess(for: .video)
            case .microphone:
                _ = await AVCaptureDevice.requestAccess(for: .audio)
            case .notifications:
                _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
            }
            requesting = false
            app.modal = nil
        }
    }
}

// MARK: - Shared helpers (onboarding group)

/// Eyebrow + large title (+ optional sub) used on the auth screens.
private struct OnboardingHeading: View {
    let eyebrow: String
    let title: String
    var sub: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: eyebrow)
            Text(title)
                .font(.outfit(34, .bold)).tracking(-1.4)
                .foregroundStyle(OC.ink)
                .fixedSize(horizontal: false, vertical: true)
            if let sub {
                Text(sub).font(.outfit(15)).lineSpacing(3).foregroundStyle(OC.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// Placeholder in the design's muted-grey (darker than the system default).
private func onboardingPrompt(_ text: String) -> Text {
    Text(text).foregroundStyle(OC.faint)
}

/// Labelled 50pt glass input.
private struct OnboardingField<Input: View>: View {
    let label: String
    var trailingPadding: CGFloat = 14
    @ViewBuilder var input: () -> Input
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
            input()
                .font(.outfit(16))
                .foregroundStyle(OC.ink)
                .padding(.leading, 14)
                .padding(.trailing, trailingPadding)
                .frame(minHeight: 50)
                .glass(radius: 14)
                .accessibilityLabel(label)
        }
    }
}

private struct OnboardingErrorText: View {
    let text: String
    var body: some View {
        Label(text, systemImage: "exclamationmark.circle.fill")
            .font(.outfit(13, .semibold))
            .foregroundStyle(OC.red)
            .fixedSize(horizontal: false, vertical: true)
            .transition(.opacity)
    }
}

/// Button label that swaps to a spinner while busy.
private struct OnboardingBusyLabel: View {
    let title: String
    let busy: Bool
    var body: some View {
        ZStack {
            Text(title).opacity(busy ? 0 : 1)
            if busy { ProgressView().tint(OC.inkFixed) }
        }
    }
}

/// ScrollView whose content is at least as tall as the viewport (so Spacers work, but small screens can scroll).
private struct OnboardingFillScroll<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        GeometryReader { geo in
            ScrollView {
                content().frame(minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

/// Simple wrapping layout for chips.
private struct OnboardingFlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, widest: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0, x + sz.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > bounds.minX, x + sz.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}

// MARK: - Previews

#Preview("Splash") { SplashView().environment(AppState()) }
#Preview("Onboarding") { OnboardingView().environment(AppState()) }
#Preview("Welcome") { NavigationStack { WelcomeView() }.environment(AppState()) }
#Preview("Sign in") { NavigationStack { SignInView() }.environment(AppState()) }
#Preview("Sign up") { NavigationStack { SignUpView() }.environment(AppState()) }
#Preview("Ad consent") { AdConsentView().environment(AppState()) }
#Preview("Permission · camera") { PermissionPrimerView(kind: .camera).environment(AppState()) }
#Preview("Permission · notifications") { PermissionPrimerView(kind: .notifications).environment(AppState()) }
