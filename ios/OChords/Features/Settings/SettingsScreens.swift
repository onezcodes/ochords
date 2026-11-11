import StoreKit
import SwiftUI
import PhotosUI

// MARK: - Shared helpers (file-private)

fileprivate enum SettingsAppInfo {
    static var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0" }
    static var build: String { Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1" }
}

/// Body copy colour (#3A3835 light) used on legal / FAQ text.
fileprivate let settingsBodyText = Color(light: UIColor(hex: 0x3A3835), dark: UIColor(hex: 0xD6D2CB))

extension EnvironmentValues {
    /// True when a legal page is shown in a sheet (e.g. from the Pro paywall) — hides cross-links.
    @Entry var settingsLegalInSheet = false
}

/// "── ACCOUNT" group header (11pt, 14pt gold rule).
fileprivate struct SettingsGroupHeader: View {
    let title: String
    var body: some View {
        HStack(spacing: 8) {
            Rectangle().fill(OC.gold).frame(width: 14, height: 2)
            Text(title.uppercased()).font(.outfit(11, .semibold)).tracking(1.8).foregroundStyle(OC.muted)
        }
        .accessibilityAddTraits(.isHeader)
    }
}

/// Tappable list row (wraps `ListRow`).
fileprivate struct SettingsLinkRow: View {
    let title: String
    var value: String? = nil
    var tint: Color = OC.ink
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ListRow(title: title, value: value, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

/// Title + subtitle with an OC switch.
fileprivate struct SettingsToggleRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool
    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.outfit(15, .semibold)).foregroundStyle(OC.ink)
                if let subtitle {
                    Text(subtitle).font(.outfit(12)).foregroundStyle(OC.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .toggleStyle(.oc)
        .padding(.horizontal, 14).padding(.vertical, 8)
        .frame(minHeight: 52)
        .accessibilityRepresentation {
            Toggle(title, isOn: $isOn).accessibilityHint(subtitle ?? "")
        }
    }
}

/// Close (X) header with centred title and optional trailing view.
fileprivate struct SettingsCloseBar<Trailing: View>: View {
    var title: String = ""
    let onClose: () -> Void
    @ViewBuilder var trailing: () -> Trailing
    var body: some View {
        HStack(spacing: 0) {
            Button(action: onClose) {
                Image(systemName: "xmark").font(.system(size: 18, weight: .semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain).foregroundStyle(OC.ink)
            .accessibilityLabel("Close")
            Spacer(minLength: 0)
            Text(title).font(.outfit(16, .bold)).foregroundStyle(OC.ink)
            Spacer(minLength: 0)
            trailing().frame(minWidth: 44, alignment: .trailing)
        }
        .padding(.horizontal, 10)
        .frame(height: 44)
    }
}

/// Frosted bottom action bar (Feedback, Delete account, Pro).
fileprivate struct SettingsBottomBar<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(spacing: 8, content: content)
            .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 12)
            .frame(maxWidth: .infinity)
            .background {
                Rectangle().fill(.ultraThinMaterial)
                    .overlay(Rectangle().fill(OC.glass))
                    .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
                    .shadow(color: .black.opacity(0.05), radius: 12, y: -8)
                    .ignoresSafeArea(edges: .bottom)
            }
    }
}

/// Selectable card (gold border / tint when on).
fileprivate struct SettingsChoiceCard<Label: View>: View {
    let isOn: Bool
    var minHeight: CGFloat = 60
    let action: () -> Void
    @ViewBuilder var label: () -> Label
    var body: some View {
        Button(action: action) {
            label()
                .foregroundStyle(OC.ink)
                .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(isOn ? OC.goldTint : OC.glass))
                .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(isOn ? OC.gold : OC.border, lineWidth: 2))
                .contentShape(RoundedRectangle(cornerRadius: OC.rCard))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// Radio dot used by plan / option pickers.
fileprivate struct SettingsRadioDot: View {
    let isOn: Bool
    var color: Color = OC.gold
    var body: some View {
        Circle().strokeBorder(isOn ? color : OC.border, lineWidth: 2)
            .frame(width: 22, height: 22)
            .overlay(Circle().fill(isOn ? color : .clear).frame(width: 10, height: 10))
    }
}

/// Primary button that looks disabled (grey) until `enabled`.
fileprivate struct SettingsGatedButtonStyle: ButtonStyle {
    let enabled: Bool
    var onColor: Color = OC.gold
    var onText: Color = OC.inkFixed
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.outfit(16, .bold))
            .foregroundStyle(enabled ? onText : OC.faint)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(enabled ? onColor : OC.border))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - Settings

/// First letter of the display name, skipping placeholder brackets ("[Your name]" → "Y").
fileprivate func settingsInitial(_ name: String?) -> String {
    guard let ch = name?.first(where: { $0.isLetter }) else { return "Y" }
    return String(ch).uppercased()
}

struct SettingsView: View {
    @Environment(AppState.self) private var app
    @Environment(\.colorScheme) private var colorScheme
    @State private var showSignOut = false
    @State private var toast: Toast?

    // Local-only demo preferences
    @State private var reminders = true
    @State private var community = true
    @State private var offlineSongs = false
    @State private var cellular = false

    var body: some View {
        @Bindable var app = app
        ZStack {
            VStack(spacing: 0) {
                TopBar(title: "Settings", backLabel: "Back to profile") { app.pop() }
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        profileCard
                        accountGroup
                        subscriptionGroup
                        playingGroup($app)
                        importGroup($app)
                        notificationsGroup
                        storageGroup
                        adsGroup
                        supportGroup
                        aboutGroup
                        demoGroup($app)
                        footer
                    }
                    .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 32)
                }
                .scrollIndicators(.hidden)
            }
            if showSignOut { signOutSheet }
        }
        .animation(.snappy(duration: 0.25), value: showSignOut)
        .toast($toast)
        .ocPage()
    }

    // MARK: Pieces

    /// A guest using the real backend has no account to show.
    private var isGuest: Bool { !app.isSignedIn && !app.usesDemoData }

    @ViewBuilder private var profileCard: some View {
        if isGuest { GuestAccountCard() } else { accountCard }
    }

    private var accountCard: some View {
        let p = app.profile
        return Button { app.push(.profile) } label: {
            HStack(spacing: 12) {
                Text(settingsInitial(p?.user.name))
                    .font(.outfit(20, .heavy)).foregroundStyle(.white)
                    .frame(width: 52, height: 52)
                    .background(Circle().fill(Color(hex: 0x1E3A8A)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(p?.user.name ?? "[Your name]").font(.outfit(17, .bold)).foregroundStyle(OC.ink)
                    Text("\(p?.email ?? "[email]") · \(p?.user.tier.title ?? "Chord Keeper")")
                        .font(.outfit(13)).foregroundStyle(OC.muted).lineLimit(1)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(OC.faint)
            }
            .padding(14)
            .glass()
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Edit profile")
    }

    private func group<C: View>(_ title: String, @ViewBuilder _ content: @escaping () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsGroupHeader(title: title)
            GlassList(content: content)
        }
    }

    @ViewBuilder private var accountGroup: some View {
        if !isGuest { signedInAccountGroup }
    }

    private var signedInAccountGroup: some View {
        group("Account") {
            SettingsLinkRow(title: "Edit profile") { app.push(.profile) }
            SettingsLinkRow(title: "Email", value: app.profile?.email ?? "[email]") { toast = Toast(text: "Email changes come with the server", systemImage: "envelope.fill") }
            SettingsLinkRow(title: "Password", value: "Change") { toast = Toast(text: "Reset link sent to your email", systemImage: "envelope.fill") }
            SettingsLinkRow(title: "Sign-in methods", value: "Apple") { toast = Toast(text: "Signed in with Apple", systemImage: "apple.logo") }
        }
    }

    private var subscriptionGroup: some View {
        group("Subscription") {
            SettingsLinkRow(title: "O Chords Pro", value: app.isPro ? "Pro" : "Free plan") { app.modal = .pro }
            SettingsLinkRow(title: "Restore purchases") {
                Task {
                    await app.restorePurchases()
                    toast = app.isPro ? Toast(text: "Pro is active") : Toast(text: "No purchases to restore", systemImage: "info.circle.fill")
                }
            }
        }
    }

    private func playingGroup(_ app: Bindable<AppState>) -> some View {
        group("Playing") {
            SettingsLinkRow(title: "Chord names", value: self.app.sharpNames ? "C♯" : "D♭") { self.app.push(.profile) }
            SettingsLinkRow(title: "Default tuning", value: "Standard") { self.app.switchTo(.tuner) }
            SettingsLinkRow(title: "App language", value: self.app.language) { toast = Toast(text: "More languages coming soon", systemImage: "globe") }
            SettingsToggleRow(title: "Dark mode", subtitle: "Easier on stage at night", isOn: Binding(
                get: { self.app.appearance == .dark || (self.app.appearance == .system && colorScheme == .dark) },
                set: { self.app.appearance = $0 ? .dark : .light }
            ))
            SettingsToggleRow(title: "Left-handed", subtitle: "Mirror chord diagrams", isOn: app.leftHanded)
        }
    }

    private func importGroup(_ app: Bindable<AppState>) -> some View {
        group("Scanning & practice") {
            SettingsToggleRow(title: "Always convert Zawgyi", subtitle: "Fix Zawgyi lyrics to Unicode automatically", isOn: app.alwaysConvertZawgyi)
            SettingsToggleRow(title: "Always skip warm up", subtitle: "Go straight to the song", isOn: app.alwaysSkipWarmup)
        }
    }

    private var notificationsGroup: some View {
        group("Notifications") {
            SettingsToggleRow(title: "Practice reminders", subtitle: "Every day at 8:00 PM", isOn: $reminders)
            SettingsToggleRow(title: "Community activity", subtitle: "Saves, fixes and new followers", isOn: $community)
            SettingsLinkRow(title: "Notification permission", value: "Set up") { app.modal = .permission(.notifications) }
        }
    }

    private var storageGroup: some View {
        group("Storage") {
            SettingsToggleRow(title: "Offline songs", subtitle: "Pro · keeps saved songs on this phone", isOn: Binding(
                get: { offlineSongs },
                set: { v in if v && !app.isPro { app.modal = .pro } else { offlineSongs = v } }
            ))
            SettingsToggleRow(title: "Download on cellular", isOn: $cellular)
            SettingsLinkRow(title: "Clear cache", value: "[size]") { toast = Toast(text: "Cache cleared", systemImage: "trash.fill") }
        }
    }

    private var adsGroup: some View {
        group("Ads") {
            SettingsLinkRow(title: "Ad choices", value: app.personalisedAds ? "Ads that fit me" : "General ads") { app.push(.adChoices) }
            SettingsLinkRow(title: "Remove ads", value: app.isPro ? "Removed" : "Pro") { app.modal = .pro }
        }
    }

    private var supportGroup: some View {
        group("Support") {
            SettingsLinkRow(title: "Help center") { app.push(.help) }
            SettingsLinkRow(title: "Send feedback") { app.push(.feedback) }
            SettingsLinkRow(title: "Rate O Chords") { toast = Toast(text: "Thanks for the love!", systemImage: "star.fill") }
        }
    }

    private var aboutGroup: some View {
        group("About") {
            SettingsLinkRow(title: "About O Chords") { app.push(.about) }
            SettingsLinkRow(title: "Terms & Conditions") { app.push(.terms) }
            SettingsLinkRow(title: "Privacy Policy") { app.push(.privacy) }
        }
    }

    private func demoGroup(_ app: Bindable<AppState>) -> some View {
        group("Demo") {
            SettingsToggleRow(title: "Simulate offline (demo)", subtitle: "Shows the offline states across the app", isOn: app.isOffline)
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            if !isGuest {
                Button("Sign out") { showSignOut = true }
                    .buttonStyle(GlassButtonStyle(height: 52))
                Button { app.push(.deleteAccount) } label: {
                    Text("Delete account").font(.outfit(14, .bold)).foregroundStyle(OC.red)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            VStack(spacing: 4) {
                Text("O Chords \(SettingsAppInfo.version) (\(SettingsAppInfo.build))").font(.outfit(12)).foregroundStyle(OC.faint)
                Button { app.push(.about) } label: {
                    VStack(spacing: 6) {
                        Text("POWERED BY").font(.outfit(10, .semibold)).tracking(3).foregroundStyle(OC.faint)
                        Image("OnezLogo").resizable().scaledToFit().frame(width: 128)
                    }
                    .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 12)
                    .background(RoundedRectangle(cornerRadius: 16).fill(.white))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xECEAE6)))
                }
                .buttonStyle(.plain)
                .padding(.top, 6)
                .accessibilityLabel("Powered by Onez Codes")
                .accessibilityHint("About O Chords")
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var signOutSheet: some View {
        ZStack(alignment: .bottom) {
            Color(hex: 0x171614, alpha: 0.45).ignoresSafeArea()
                .onTapGesture { showSignOut = false }
                .accessibilityHidden(true)
                .transition(.opacity)
            VStack(alignment: .leading, spacing: 12) {
                Text("Sign out of O Chords?").font(.outfit(20, .bold)).foregroundStyle(OC.ink)
                Text("Your library, uploads and groove are saved to your account. Offline songs will be removed from this phone.")
                    .font(.outfit(14)).lineSpacing(3).foregroundStyle(OC.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Sign out") {
                    showSignOut = false
                    app.signOut()
                }
                .buttonStyle(InkButtonStyle(height: 52))
                Button("Cancel") { showSignOut = false }
                    .buttonStyle(GlassButtonStyle(height: 48))
            }
            .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 12)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous)
                    .fill(OC.surface).ignoresSafeArea(edges: .bottom)
            )
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityLabel("Sign out")
            .transition(.move(edge: .bottom))
        }
        .zIndex(1)
    }
}

// MARK: - Help center

fileprivate struct HelpFAQ: Identifiable {
    let topic: Int
    let q: String
    let a: String
    var id: String { q }
}

struct HelpView: View {
    @Environment(AppState.self) private var app
    @State private var query = ""
    @State private var topic = 0
    @State private var open: String? = HelpView.faqs.first?.q

    fileprivate static let topics: [(label: String, icon: String)] = [
        ("Scanning", "camera"),
        ("Playing", "music.note"),
        ("Sharing", "square.and.arrow.up"),
        ("Tiers & points", "shield"),
        ("Pro & billing", "creditcard"),
        ("Account", "person")
    ]

    fileprivate static let faqs: [HelpFAQ] = [
        .init(topic: 0, q: "Why did the scan get some chords wrong?", a: "Blurry photos and small handwriting are the usual cause. Hold the phone flat, fill the frame with the sheet and use good light. You can fix any chord on the Review screen before saving."),
        .init(topic: 0, q: "Can it read Myanmar lyrics?", a: "Yes — Myanmar and English lyrics are both supported. Words the app isn’t sure about are highlighted so you can check them."),
        .init(topic: 1, q: "How do I change the key?", a: "Open a song and use − and + next to KEY. Every chord changes together, and the original sheet stays the same."),
        .init(topic: 1, q: "What does a capo do to the chords?", a: "A capo raises the pitch. O Chords can suggest a capo so you can play easier shapes in the same key."),
        .init(topic: 2, q: "Who can see my uploads?", a: "Only you, until you choose Public. You can switch any upload back to Only me from your profile."),
        .init(topic: 3, q: "How do I reach the next tier?", a: "Earn O Points by learning (lesson beats, your groove, positions) and contributing (public sheets, accepted fixes, saves). Pro doesn’t change your tier."),
        .init(topic: 4, q: "How do I cancel Pro?", a: "Go to iPhone Settings › your name › Subscriptions › O Chords, at least 24 hours before it renews."),
        .init(topic: 5, q: "How do I delete my account?", a: "Settings › Delete account. You can choose to keep your public sheets up without your name.")
    ]

    private var trimmed: String { query.trimmingCharacters(in: .whitespaces).lowercased() }

    private var results: [HelpFAQ] {
        trimmed.isEmpty
            ? Self.faqs.filter { $0.topic == topic }
            : Self.faqs.filter { ($0.q + " " + $0.a).lowercased().contains(trimmed) }
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Help center", backLabel: "Back to settings")
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("How can we help?").font(.outfit(30, .semibold)).tracking(-1.2).foregroundStyle(OC.ink)
                    searchField
                    if trimmed.isEmpty { topicGrid }
                    faqList
                    stillStuck
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 32)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .ocPage()
        .onChange(of: query) { open = results.first?.q }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").font(.system(size: 16, weight: .medium)).foregroundStyle(OC.muted)
            TextField("Search questions", text: $query)
                .font(.outfit(16)).foregroundStyle(OC.ink)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .accessibilityLabel("Search help")
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(OC.faint).frame(width: 32, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 48)
        .glass(radius: OC.rButton)
    }

    private var topicGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
            ForEach(Array(Self.topics.enumerated()), id: \.offset) { i, t in
                let on = i == topic
                Button {
                    withAnimation(.snappy(duration: 0.2)) { topic = i; open = Self.faqs.first { $0.topic == i }?.q }
                } label: {
                    VStack(alignment: .leading) {
                        Image(systemName: t.icon).font(.system(size: 18, weight: .medium)).foregroundStyle(OC.goldText)
                        Spacer(minLength: 0)
                        Text(t.label).font(.outfit(13, .bold)).foregroundStyle(OC.ink).multilineTextAlignment(.leading).lineLimit(2)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, minHeight: 76, maxHeight: 76, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(on ? OC.goldTint : OC.glass))
                    .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(on ? OC.gold : OC.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    private var faqList: some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsGroupHeader(title: trimmed.isEmpty ? Self.topics[topic].label : "\(results.count) results")
            if results.isEmpty {
                Text("No answers match. Ask us directly below.")
                    .font(.outfit(14)).foregroundStyle(OC.muted).padding(.vertical, 8)
            } else {
                GlassList {
                    ForEach(results) { f in faqRow(f) }
                }
            }
        }
    }

    private func faqRow(_ f: HelpFAQ) -> some View {
        let isOpen = open == f.q
        return VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.snappy(duration: 0.22)) { open = isOpen ? nil : f.q }
            } label: {
                HStack(spacing: 10) {
                    Text(f.q).font(.outfit(15, .semibold)).foregroundStyle(OC.ink)
                        .multilineTextAlignment(.leading).lineSpacing(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: isOpen ? "minus" : "plus")
                        .font(.system(size: 15, weight: .bold)).foregroundStyle(OC.goldText)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .frame(minHeight: 54)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(isOpen ? "Expanded" : "Collapsed")
            if isOpen {
                Text(f.a).font(.outfit(14)).lineSpacing(4).foregroundStyle(settingsBodyText)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 14).padding(.bottom, 14)
                    .transition(.opacity)
            }
        }
    }

    private var stillStuck: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Still stuck?").font(.outfit(16, .bold))
            Text("Message the Onez Codes support team. We usually reply within [n] hours.")
                .font(.outfit(14)).lineSpacing(3).foregroundStyle(Color(hex: 0xBDB9B3))
                .fixedSize(horizontal: false, vertical: true)
            Button("Contact support") { app.push(.feedback) }
                .buttonStyle(.gold(height: 48))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .inkGlass()
    }
}

// MARK: - Feedback

struct FeedbackView: View {
    @Environment(AppState.self) private var app
    @State private var kind = 0
    @State private var text = ""
    @State private var deviceInfo = true
    @State private var photo: PhotosPickerItem?
    @State private var sending = false
    @State private var sent = false
    @FocusState private var editorFocused: Bool

    private static let kinds: [(label: String, sub: String, placeholder: String)] = [
        ("Something’s broken", "A bug or crash", "What happened, and what did you expect?"),
        ("Wrong chords", "In a song or scan", "Which song and which line?"),
        ("Idea", "A feature you want", "What would make O Chords better for you?"),
        ("Other", "Anything else", "Tell us anything.")
    ]

    private var canSend: Bool { text.trimmingCharacters(in: .whitespacesAndNewlines).count >= 5 && !sending }

    var body: some View {
        VStack(spacing: 0) {
            SettingsCloseBar(title: "Send feedback", onClose: { app.pop() }) { EmptyView() }
            if sent { sentState.transition(.opacity) } else { form }
        }
        .animation(.snappy, value: sent)
        .ocPage()
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("What is it about?").font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                        ForEach(Array(Self.kinds.enumerated()), id: \.offset) { i, k in
                            SettingsChoiceCard(isOn: i == kind, action: { kind = i }) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(k.label).font(.outfit(15, .bold)).lineLimit(1).minimumScaleFactor(0.85)
                                    Text(k.sub).font(.outfit(12)).foregroundStyle(OC.muted)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 8)
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Tell us more").font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
                    ZStack(alignment: .topLeading) {
                        TextEditor(text: Binding(get: { text }, set: { text = String($0.prefix(1000)) }))
                            .focused($editorFocused)
                            .scrollContentBackground(.hidden)
                            .font(.outfit(15)).foregroundStyle(OC.ink)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .accessibilityLabel("Tell us more")
                        if text.isEmpty {
                            Text(Self.kinds[kind].placeholder).font(.outfit(15)).foregroundStyle(OC.faint)
                                .padding(.horizontal, 14).padding(.vertical, 14)
                                .allowsHitTesting(false)
                                .accessibilityHidden(true)
                        }
                    }
                    .frame(height: 150)
                    .glass()
                    Text("\(text.count) / 1000").font(.outfit(12, .medium)).foregroundStyle(OC.muted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                PhotosPicker(selection: $photo, matching: .screenshots) {
                    HStack(spacing: 8) {
                        Image(systemName: photo == nil ? "photo" : "checkmark.circle.fill")
                            .foregroundStyle(photo == nil ? OC.ink : OC.teal)
                        Text(photo == nil ? "Attach a screenshot" : "Screenshot attached")
                    }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.ink)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(RoundedRectangle(cornerRadius: OC.rButton).fill(OC.surface))
                    .overlay(RoundedRectangle(cornerRadius: OC.rButton).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(OC.switchOff))
                }
                .buttonStyle(.plain)
                SettingsToggleRow(title: "Include device info", subtitle: "App version, iOS version, phone model", isOn: $deviceInfo)
                    .glass()
                Button { app.push(.help) } label: {
                    Text("Looking for an answer? Try the Help center ›").font(.outfit(14, .bold)).foregroundStyle(OC.goldText)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            SettingsBottomBar {
                Button { send() } label: {
                    HStack(spacing: 8) {
                        if sending { ProgressView().tint(OC.inkFixed) }
                        Text(sending ? "Sending…" : "Send")
                    }
                }
                .buttonStyle(SettingsGatedButtonStyle(enabled: canSend || sending))
                .disabled(!canSend)
            }
        }
    }

    private func send() {
        guard canSend else { return }
        editorFocused = false
        sending = true
        let kindLabel = Self.kinds[kind].label
        let message = text + (deviceInfo ? "\n\n[O Chords \(SettingsAppInfo.version) (\(SettingsAppInfo.build)) · iOS \(UIDevice.current.systemVersion)]" : "")
        Task {
            try? await app.backend.sendFeedback(kind: kindLabel, message: message)
            sending = false
            sent = true
        }
    }

    private var sentState: some View {
        VStack(spacing: 14) {
            Image(systemName: "checkmark").font(.system(size: 32, weight: .bold)).foregroundStyle(OC.inkFixed)
                .frame(width: 76, height: 76)
                .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
                .accessibilityHidden(true)
            Text("Thanks — we got it").font(.outfit(28, .semibold)).tracking(-1).foregroundStyle(OC.ink)
            Text("The Onez Codes team reads every message. If you reported a chord mistake that we fix, you’ll earn O Points.")
                .font(.outfit(15)).lineSpacing(3).foregroundStyle(OC.muted).multilineTextAlignment(.center)
            Spacer()
            Button("Done") { app.pop() }.buttonStyle(.ink)
        }
        .padding(.horizontal, 28).padding(.top, 96).padding(.bottom, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - About

struct AboutView: View {
    @Environment(AppState.self) private var app
    @State private var toast: Toast?

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "About", backLabel: "Back to settings")
            ScrollView {
                VStack(spacing: 20) {
                    VStack(spacing: 10) {
                        AppIconTile(size: 84)
                        Text("O Chords").font(.outfit(26, .semibold)).tracking(-0.8).foregroundStyle(OC.ink)
                        Text("Version \(SettingsAppInfo.version) (\(SettingsAppInfo.build))").font(.mono(13, .semibold)).foregroundStyle(OC.muted)
                    }
                    .padding(.vertical, 10)
                    Text("O Chords turns any chord sheet — handwritten, printed or screenshotted — into a clean song you can transpose, practise and share. Built for Myanmar players first, open to everyone.")
                        .font(.outfit(16)).lineSpacing(5).foregroundStyle(OC.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    onezCard
                    GlassList {
                        SettingsLinkRow(title: "Help center") { app.push(.help) }
                        SettingsLinkRow(title: "Send feedback") { app.push(.feedback) }
                        SettingsLinkRow(title: "Terms & Conditions") { app.push(.terms) }
                        SettingsLinkRow(title: "Privacy Policy") { app.push(.privacy) }
                        SettingsLinkRow(title: "Open-source licences") { toast = Toast(text: "No third-party packages — all native", systemImage: "swift") }
                    }
                    Text("Songs and lyrics belong to their writers and rights holders. Chord sheets are shared by the O Chords community.\n© [year] Onez Codes")
                        .font(.outfit(12)).lineSpacing(3).foregroundStyle(OC.faint).multilineTextAlignment(.center)
                }
                .padding(.horizontal, 22).padding(.top, 16).padding(.bottom, 32)
            }
        }
        .toast($toast)
        .ocPage()
    }

    private var onezCard: some View {
        Link(destination: URL(string: "https://www.onezcodes.com/")!) {
            VStack(spacing: 12) {
                Text("POWERED BY").font(.outfit(11, .semibold)).tracking(3).foregroundStyle(Color(hex: 0x8E8A84))
                Image("OnezLogo").resizable().scaledToFit().frame(width: 250)
                    .accessibilityLabel("Onez Codes — Tech solutions · Software house")
                Text("Designed and built by Onez Codes, a software house for web and mobile apps.")
                    .font(.outfit(14)).lineSpacing(3).foregroundStyle(Color(hex: 0x6B6660)).multilineTextAlignment(.center)
                Text("onezcodes.com ›").font(.outfit(14, .bold)).foregroundStyle(Color(hex: 0x8F5E00))
            }
            .padding(18)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(.white))
            .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(Color(hex: 0xECEAE6)))
            .shadow(color: .black.opacity(0.07), radius: 15, y: 10)
        }
        .accessibilityHint("Opens onezcodes.com")
    }
}

// MARK: - Legal (Terms & Privacy)

fileprivate struct SettingsLegalPage<Intro: View>: View {
    let title: String
    let heading: String
    let crossLink: (label: String, route: Route)
    let notice: String
    let sections: [(h: String, p: String)]
    @ViewBuilder var intro: () -> Intro
    @Environment(AppState.self) private var app
    @Environment(\.settingsLegalInSheet) private var inSheet

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: title, backLabel: inSheet ? "Close" : "Back to settings") {
                if !inSheet {
                    Button(crossLink.label) { app.push(crossLink.route) }
                        .font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                        .frame(minHeight: 44).padding(.horizontal, 8)
                        .buttonStyle(.plain)
                }
            }
            .padding(.bottom, 6)
            .overlay(alignment: .bottom) { Rectangle().fill(OC.border).frame(height: 1) }
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Eyebrow(text: "Last updated [DATE]")
                        Text(heading).font(.outfit(28, .semibold)).tracking(-1).foregroundStyle(OC.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    intro()
                    (Text("Draft template. ").bold() + Text(notice))
                        .font(.outfit(13)).lineSpacing(3).foregroundStyle(OC.ink)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.goldTint))
                    ForEach(sections, id: \.h) { s in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(s.h).font(.outfit(17, .bold)).foregroundStyle(OC.ink).accessibilityAddTraits(.isHeader)
                            Text(s.p).font(.outfit(15)).lineSpacing(5).foregroundStyle(settingsBodyText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.horizontal, 22).padding(.top, 18).padding(.bottom, 36)
            }
        }
        .ocPage()
    }
}

struct TermsView: View {
    private static let sections: [(h: String, p: String)] = [
        ("1. Who we are", "O Chords is made and run by Onez Codes ([legal entity name], [registered address]). By creating an account or using the app you agree to these terms."),
        ("2. Your account", "You must be at least [minimum age] to create an account. Keep your sign-in details private — you are responsible for activity on your account."),
        ("3. Content you upload", "You keep ownership of the chord sheets, arrangements and photos you upload. When you make a sheet public, you let O Chords show, store and adapt its format (for example transposing) for other users. Only upload material you have the right to share."),
        ("4. Community rules", "No spam, harassment, hate, or misleading content. Don’t copy other people’s sheets and claim them as your own. We may hide or remove content that breaks these rules."),
        ("5. Copyright and takedowns", "Lyrics and songs belong to their writers and rights holders. If you believe a sheet infringes your rights, contact [copyright contact email] and we will review and remove it where appropriate."),
        ("6. O Chords Pro", "Pro is billed through your App Store account. It renews automatically until you cancel in Settings at least 24 hours before renewal. Refunds follow the App Store’s policies."),
        ("7. Tiers and O Points", "O Points and tiers reward learning and contributing. They have no cash value, cannot be bought or transferred, and we may adjust how they are earned."),
        ("8. Ending your account", "You can delete your account at any time from Settings. We may suspend accounts that seriously or repeatedly break these terms."),
        ("9. Our responsibility", "We work to keep the app accurate and available, but chord sheets come from the community and may contain mistakes. The app is provided as-is to the extent allowed by law in [governing jurisdiction]."),
        ("10. Changes and contact", "If we change these terms we will tell you in the app before the change takes effect. Questions: [support email].")
    ]

    var body: some View {
        SettingsLegalPage(
            title: "Terms & Conditions",
            heading: "Terms of using O Chords",
            crossLink: ("Privacy", .privacy),
            notice: "Plain-language outline for Onez Codes to complete and have reviewed by a lawyer before launch.",
            sections: Self.sections
        ) { EmptyView() }
    }
}

struct PrivacyView: View {
    private static let sections: [(h: String, p: String)] = [
        ("What we collect", "Account details (name, email, sign-in method), the songs, setlists and photos you save, your learning progress, and basic app usage and device information to keep the app working."),
        ("Camera and photos", "We only access images you choose to scan. Chords and English are read on your phone. When you're signed in, the photo is also sent to our server, which uses Google Cloud Vision to read Myanmar lyrics; it is not stored on our servers. Only the song you save is kept."),
        ("Microphone", "The tuner, chord check and Listen analyse audio on your phone and never record or store it. To name a song, Listen sends only an audio fingerprint — not a recording — to our song-recognition provider ([provider])."),
        ("Ads", "The free plan shows ads from Google AdMob. With your permission (our Ad choices screen and the iOS tracking prompt) Google may use an advertising ID and app activity to choose ads; otherwise ads are not personalised. We never share your songs, lyrics or recordings with advertisers. Pro has no ads. Change this any time in Settings › Ad choices."),
        ("What others can see", "Songs are private until you choose Public. Public sheets show your display name, tier and uploads. Your email is never shown."),
        ("How we use your data", "To run your account, sync your library across devices, rank popular songs, calculate your groove and O Points, and fix problems."),
        ("Who we share with", "Service providers that host and run the app for us ([hosting provider], [analytics provider]) under contract, and authorities only when the law requires it. We do not sell personal data."),
        ("Your choices", "Make any upload private, turn off notifications, download a copy of your data, or delete your account from Settings."),
        ("How long we keep it", "Until you delete it or your account. Backups are cleared within [n] days. If you choose, public sheets can stay up without your name."),
        ("Contact", "Questions or requests: [privacy email]. Onez Codes, [registered address].")
    ]

    private static let promises: [(t: String, s: String)] = [
        ("No selling", "We never sell your data"),
        ("Mic stays local", "Audio is never recorded or stored"),
        ("You decide", "Private by default until you share")
    ]

    var body: some View {
        SettingsLegalPage(
            title: "Privacy Policy",
            heading: "How O Chords handles your data",
            crossLink: ("Terms", .terms),
            notice: "To be completed by Onez Codes and reviewed for the laws where O Chords is offered.",
            sections: Self.sections
        ) {
            HStack(alignment: .top, spacing: 8) {
                ForEach(Self.promises, id: \.t) { p in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(p.t).font(.outfit(13, .heavy)).foregroundStyle(OC.ink)
                        Text(p.s).font(.outfit(12)).lineSpacing(1).foregroundStyle(OC.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .glass()
                    .accessibilityElement(children: .combine)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Delete account

struct DeleteAccountView: View {
    private enum Phase { case form, deleting, done }

    @Environment(AppState.self) private var app
    @State private var keepSheets = 0
    @State private var typed = ""
    @State private var phase: Phase = .form
    @State private var toast: Toast?
    @FocusState private var fieldFocused: Bool

    private static let options: [(t: String, s: String)] = [
        ("Keep them up, without my name", "Other players keep the sheets they saved. Shown as “Former member”."),
        ("Delete them too", "Your sheets disappear from Explore and other people’s libraries.")
    ]

    private var ready: Bool { typed.trimmingCharacters(in: .whitespaces).uppercased() == "DELETE" }

    var body: some View {
        VStack(spacing: 0) {
            if phase == .done {
                TopBar(title: "Delete account", backLabel: "Close") { app.signOut() }
                doneState.transition(.opacity)
            } else {
                TopBar(title: "Delete account", backLabel: "Back to settings")
                form
            }
        }
        .animation(.snappy, value: phase)
        .toast($toast)
        .ocPage()
    }

    private var losses: [String] {
        let p = app.profile
        let groove = p.map { "\($0.grooveDays)-day" } ?? "8-day"
        let points = (p?.points ?? 1240).formatted(.number)
        let tier = p?.user.tier.title ?? "Chord Keeper"
        return [
            "Your profile, library, setlists and saved songs",
            "Learning progress, your \(groove) groove and \(points) O Points",
            "Your \(tier) tier and badges"
        ]
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("This can’t be undone").font(.outfit(28, .semibold)).tracking(-1).foregroundStyle(OC.ink)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Deleted permanently").font(.outfit(14, .bold)).foregroundStyle(Color(light: UIColor(hex: 0x991B1B), dark: UIColor(hex: 0xFCA5A5)))
                    ForEach(losses, id: \.self) { Text("•  \($0)").font(.outfit(14)).foregroundStyle(OC.ink) }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.redTint))
                .accessibilityElement(children: .combine)

                (Text("Have Pro? ").bold() + Text("Deleting your account doesn’t cancel the App Store subscription. Cancel it first in iPhone Settings › Subscriptions."))
                    .font(.outfit(14)).lineSpacing(3).foregroundStyle(OC.ink)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.goldTint))

                VStack(alignment: .leading, spacing: 8) {
                    Text("Your public sheets").font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
                    ForEach(Array(Self.options.enumerated()), id: \.offset) { i, o in
                        let on = i == keepSheets
                        Button { keepSheets = i } label: {
                            HStack(spacing: 12) {
                                SettingsRadioDot(isOn: on, color: OC.ink)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(o.t).font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                                    Text(o.s).font(.outfit(12)).foregroundStyle(OC.muted).fixedSize(horizontal: false, vertical: true)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .frame(minHeight: 60)
                            .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.surface))
                            .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(on ? OC.ink : OC.border, lineWidth: 2))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }

                Button { toast = Toast(text: "We’ll email a copy of your data", systemImage: "envelope.fill") } label: {
                    Text("Download a copy of my data first ›").font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Type DELETE to confirm").font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
                    TextField("DELETE", text: $typed)
                        .focused($fieldFocused)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .font(.mono(16, .semibold)).tracking(2).foregroundStyle(OC.ink)
                        .padding(.horizontal, 14).frame(height: 50)
                        .background(RoundedRectangle(cornerRadius: OC.rButton).fill(OC.surface))
                        .overlay(RoundedRectangle(cornerRadius: OC.rButton).strokeBorder(ready ? OC.red : OC.switchOff, lineWidth: 1))
                        .accessibilityLabel("Type DELETE to confirm")
                }
            }
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            SettingsBottomBar {
                Button { confirm() } label: {
                    HStack(spacing: 8) {
                        if phase == .deleting { ProgressView().tint(.white) }
                        Text(phase == .deleting ? "Deleting…" : "Delete my account")
                    }
                }
                .buttonStyle(SettingsGatedButtonStyle(enabled: ready, onColor: OC.red, onText: .white))
                .disabled(!ready || phase == .deleting)
                Button { app.pop() } label: {
                    Text("Keep my account").font(.outfit(15, .bold)).foregroundStyle(OC.ink).frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func confirm() {
        guard ready else { return }
        fieldFocused = false
        phase = .deleting
        let reason = Self.options[keepSheets].t
        Task {
            try? await app.backend.deleteAccount(reason: reason)
            phase = .done
        }
    }

    private var doneState: some View {
        VStack(spacing: 14) {
            Text("Your account is scheduled for deletion").font(.outfit(28, .semibold)).tracking(-1)
                .foregroundStyle(OC.ink).multilineTextAlignment(.center)
            Text("Everything will be removed within [n] days. Log back in before then if you change your mind.")
                .font(.outfit(15)).lineSpacing(3).foregroundStyle(OC.muted).multilineTextAlignment(.center)
            Spacer()
            Button("Close") { app.signOut() }.buttonStyle(.ink)
        }
        .padding(.horizontal, 28).padding(.top, 76).padding(.bottom, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Pro paywall

struct ProView: View {
    private enum LegalSheet: String, Identifiable { case terms, privacy; var id: String { rawValue } }

    @Environment(AppState.self) private var app
    @State private var plan = 0
    @State private var purchasing = false
    @State private var legal: LegalSheet?
    @State private var toast: Toast?
    @State private var showAllFeatures = false
    /// Whether the 7-day trial is still available to this player (the App Store's introductory offer).
    @State private var trialAvailable = true

    private struct Plan {
        var name: String
        var price: String
        var best: Bool
        var fine: String
        var product: Product?
    }

    /// The App Store's prices when loaded; the design's placeholders until then (and in the demo build).
    private var plans: [Plan] {
        let store = app.store
        let saving = store.yearlySavingPercent.map { "save \($0)%" } ?? "save [n]%"
        let yearly = store.yearly?.displayPrice ?? "[price]"
        let monthly = store.monthly?.displayPrice ?? "[price]"
        let trial = trialAvailable ? "Free for 7 days, then " : ""
        return [
            Plan(name: "Yearly", price: "\(yearly) / year · \(saving)", best: true,
                 fine: "\(trial)\(yearly) per year.", product: store.yearly),
            Plan(name: "Monthly", price: "\(monthly) / month", best: false,
                 fine: "\(trial)\(monthly) per month.", product: store.monthly),
        ]
    }

    private static let rows: [(f: String, free: String, pro: String)] = [
        ("No ads", "Ads shown", "✓"),
        ("Scan chord sheets", "10 a day", "Unlimited"),
        ("Offline songs & setlists", "—", "✓"),
        ("Jam rooms", "Join", "Host"),
        ("Mic chord check", "5 a day", "Unlimited"),
        ("Rest notes", "By tier", "+2 / week"),
        ("Advanced lessons", "Preview", "Full"),
        ("Print & PDF export", "—", "✓")
    ]

    var body: some View {
        VStack(spacing: 0) {
            SettingsCloseBar(onClose: { app.modal = nil }) {
                Button("Restore") { restore() }
                    .font(.outfit(14, .semibold)).foregroundStyle(OC.muted)
                    .frame(minHeight: 44).padding(.horizontal, 10)
                    .buttonStyle(.plain)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Eyebrow(text: "O Chords Pro")
                        Text("Play anywhere. Jam with anyone.").font(.outfit(32, .semibold)).tracking(-1.3).foregroundStyle(OC.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("No ads, offline songs, hosting jam rooms and unlimited chord checks — for players who use O Chords every week.")
                            .font(.outfit(15)).lineSpacing(3).foregroundStyle(OC.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    comparison
                    planPicker
                    Text("Tiers stay earned, not bought. Pro never raises your tier — learning and contributing still do.")
                        .font(.outfit(13)).lineSpacing(3).foregroundStyle(OC.ink)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.softFill))
                }
                .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomBar }
        }
        .toast($toast)
        .ocPage()
        .task { await loadTrialState() }
        .sheet(item: $legal) { which in
            Group {
                switch which {
                case .terms: TermsView()
                case .privacy: PrivacyView()
                }
            }
            .environment(\.settingsLegalInSheet, true)
            .environment(app)
        }
    }

    private var comparison: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer(minLength: 0)
                Text("FREE").frame(width: 76)
                Text("PRO").foregroundStyle(OC.inkFixed).frame(width: 76, height: 38).background(OC.gold)
            }
            .font(.outfit(12, .bold)).tracking(1).foregroundStyle(OC.muted)
            .padding(.horizontal, 12)
            .frame(height: 38)
            .background(OC.softFill)
            ForEach(showAllFeatures ? Self.rows : Array(Self.rows.prefix(3)), id: \.f) { r in
                Rectangle().fill(OC.line).frame(height: 1)
                HStack(spacing: 0) {
                    Text(r.f).font(.outfit(13, .semibold)).foregroundStyle(OC.ink)
                        .padding(.vertical, 6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(r.free).font(.outfit(13)).foregroundStyle(OC.muted).multilineTextAlignment(.center).frame(width: 76)
                    Text(r.pro).font(.outfit(13, .heavy)).foregroundStyle(OC.ink)
                        .frame(width: 76).frame(maxHeight: .infinity)
                        .background(OC.goldTint)
                }
                .padding(.horizontal, 12)
                .frame(minHeight: 46)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(r.f): free, \(r.free == "—" ? "not included" : r.free); Pro, \(r.pro == "✓" ? "included" : r.pro)")
            }
            Rectangle().fill(OC.line).frame(height: 1)
            Button {
                withAnimation(.snappy(duration: 0.25)) { showAllFeatures.toggle() }
            } label: {
                HStack(spacing: 4) {
                    Text(showAllFeatures ? "Show less" : "Compare all \(Self.rows.count) features")
                    Image(systemName: showAllFeatures ? "chevron.up" : "chevron.down").font(.system(size: 11, weight: .bold))
                }
                .font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .clipShape(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous))
        .glass()
    }

    private var planPicker: some View {
        VStack(spacing: 8) {
            ForEach(Array(plans.enumerated()), id: \.offset) { i, p in
                SettingsChoiceCard(isOn: i == plan, minHeight: 64, action: { withAnimation(.snappy(duration: 0.2)) { plan = i } }) {
                    HStack(spacing: 12) {
                        SettingsRadioDot(isOn: i == plan)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(p.name).font(.outfit(16, .bold))
                            Text(p.price).font(.outfit(13)).foregroundStyle(OC.muted)
                        }
                        Spacer(minLength: 0)
                        if p.best {
                            Text("BEST VALUE").font(.outfit(11, .heavy)).tracking(0.8).foregroundStyle(OC.gold)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 8).fill(OC.inkGlass))
                        }
                    }
                    .padding(.horizontal, 14)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Plan")
    }

    private var bottomBar: some View {
        SettingsBottomBar {
            Button { startTrial() } label: {
                HStack(spacing: 8) {
                    if purchasing { ProgressView().tint(OC.inkFixed) }
                    Text(app.isPro ? "You’re on Pro" : (purchasing ? (trialAvailable ? "Starting trial…" : "Buying…") : (trialAvailable ? "Start 7-day free trial" : "Go Pro")))
                }
            }
            .buttonStyle(.gold)
            .disabled(purchasing || app.isPro)
            Text(finePrint)
                .font(.outfit(11)).lineSpacing(2).foregroundStyle(OC.ink.opacity(0.8)).tint(OC.goldText)
                .multilineTextAlignment(.center)
                .environment(\.openURL, OpenURLAction { url in
                    legal = url.host == "privacy" ? .privacy : .terms
                    return .handled
                })
        }
    }

    /// Fine print with inline, underlined Terms · Privacy links (opened in a sheet).
    private var finePrint: AttributedString {
        var text = AttributedString("\(plans[plan].fine) Cancel anytime in Settings at least 24 hours before renewal. ")
        func link(_ label: String, _ host: String) -> AttributedString {
            var a = AttributedString(label)
            a.link = URL(string: "ochords-legal://\(host)")
            a.underlineStyle = .single
            a.foregroundColor = OC.goldText
            return a
        }
        text += link("Terms", "terms")
        text += AttributedString(" · ")
        text += link("Privacy", "privacy")
        return text
    }

    /// Buys the chosen plan through the App Store (the demo build just switches Pro on).
    private func startTrial() {
        guard !purchasing, !app.isPro else { return }
        let product = plans[plan].product
        guard product != nil || app.usesDemoData else {
            toast = Toast(text: "The App Store isn’t available right now. Try again in a moment.", systemImage: "wifi.exclamationmark")
            Task { await app.store.loadProducts() }
            return
        }
        purchasing = true
        Task {
            defer { purchasing = false }
            do {
                switch try await app.purchasePro(product) {
                case .purchased:
                    toast = Toast(text: "Welcome to O Chords Pro", systemImage: "crown.fill")
                    try? await Task.sleep(for: .seconds(0.9))
                    app.modal = nil
                case .pending:
                    toast = Toast(text: "Waiting for approval — Pro starts once it’s confirmed", systemImage: "hourglass")
                case .cancelled:
                    break
                }
            } catch {
                toast = Toast(text: error.localizedDescription, systemImage: "exclamationmark.triangle.fill")
            }
        }
    }

    private func restore() {
        Task {
            toast = Toast(text: "Checking the App Store…", systemImage: "arrow.clockwise")
            await app.restorePurchases()
            if app.usesDemoData { try? await Task.sleep(for: .seconds(1)) }
            toast = app.isPro ? Toast(text: "Pro is active") : Toast(text: "No purchases to restore", systemImage: "info.circle.fill")
        }
    }

    private func loadTrialState() async {
        await app.store.loadProducts()
        if let product = app.store.yearly { trialAvailable = await app.store.trialAvailable(for: product) }
    }
}

// MARK: - Ad choices

struct AdChoicesView: View {
    @Environment(AppState.self) private var app
    @State private var rewardAds = true
    @State private var toast: Toast?

    var body: some View {
        @Bindable var app = app
        VStack(spacing: 0) {
            TopBar(title: "Ad choices", backLabel: "Back to settings")
            ScrollView {
                VStack(spacing: 16) {
                    goProCard
                    GlassList {
                        SettingsToggleRow(title: "Ads that fit me", subtitle: "Uses app activity to choose ads. Off = general ads only.", isOn: $app.personalisedAds)
                            .frame(minHeight: 64)
                        SettingsToggleRow(title: "Offer reward ads", subtitle: "Watch an optional ad for an extra scan or to restring in a lesson.", isOn: $rewardAds)
                            .frame(minHeight: 64)
                    }
                    GlassList {
                        SettingsLinkRow(title: "Manage ad partners") { toast = Toast(text: "Google AdMob is our only ad partner", systemImage: "info.circle.fill") }
                        SettingsLinkRow(title: "iOS tracking permission", value: app.personalisedAds ? "Allowed" : "Off") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                        SettingsLinkRow(title: "How ads use data") { app.push(.privacy) }
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        Text("NEVER ADS HERE").font(.outfit(13, .heavy)).foregroundStyle(OC.tealText)
                        Text("Song view · Stage mode · Warm up · Tuner · Listen · Jam rooms · during a lesson")
                            .font(.outfit(13)).lineSpacing(3).foregroundStyle(OC.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.tealTint))
                    .accessibilityElement(children: .combine)
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 32)
            }
        }
        .toast($toast)
        .ocPage()
    }

    private var goProCard: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(app.isPro ? "Ads are off" : "Remove all ads").font(.outfit(17, .bold))
                Text(app.isPro ? "You’re on O Chords Pro — no ads anywhere." : "O Chords Pro has no ads, plus offline songs and hosting jam rooms.")
                    .font(.outfit(13)).lineSpacing(2).foregroundStyle(Color(hex: 0xBDB9B3))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            if !app.isPro {
                Button("Go Pro") { app.modal = .pro }
                    .buttonStyle(GoldButtonStyle(height: 44, fullWidth: false))
                    .fixedSize()
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .inkGlass()
    }
}

// MARK: - Previews

#Preview("Settings") { NavigationStack { SettingsView() }.environment(AppState()) }
#Preview("Settings · dark") { NavigationStack { SettingsView() }.environment(AppState()).preferredColorScheme(.dark) }
#Preview("Help") { NavigationStack { HelpView() }.environment(AppState()) }
#Preview("Feedback") { NavigationStack { FeedbackView() }.environment(AppState()) }
#Preview("About") { NavigationStack { AboutView() }.environment(AppState()) }
#Preview("Terms") { NavigationStack { TermsView() }.environment(AppState()) }
#Preview("Privacy") { NavigationStack { PrivacyView() }.environment(AppState()) }
#Preview("Delete account") { NavigationStack { DeleteAccountView() }.environment(AppState()) }
#Preview("Pro") { ProView().environment(AppState()) }
#Preview("Ad choices") { NavigationStack { AdChoicesView() }.environment(AppState()) }
