import SwiftUI

@main
struct OChordsApp: App {
    @State private var app = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .preferredColorScheme(app.appearance.colorScheme)
                .tint(OC.goldText)
                .onOpenURL { url in
                    if !SocialSignIn.handle(url) { app.handle(url: url) }
                }
                #if DEBUG
                .onAppear { app.applyDemoLaunchScreen(); app.applyDemoGuest() }
                .task { await app.applyDemoSignIn() }
                #endif
        }
    }
}

struct RootView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        @Bindable var app = app
        ZStack {
            switch app.phase {
            case .splash:
                SplashView().transition(.opacity)
            case .onboarding:
                OnboardingView().transition(.opacity)
            case .welcome:
                NavigationStack(path: $app.authPath) {
                    WelcomeView().navigationDestination(for: Route.self) { RouteView(route: $0) }
                }
                .transition(.opacity)
            case .main:
                MainTabView().transition(.opacity)
            }
        }
        .fullScreenCover(item: $app.modal) { modal in
            ModalView(modal: modal).environment(app)
        }
    }
}

struct ModalView: View {
    let modal: Modal
    var body: some View {
        switch modal {
        case .pro: ProView()
        case .permission(let k): PermissionPrimerView(kind: k)
        case .reward(let k): RewardAdView(kind: k)
        case .interstitial: InterstitialAdView()
        case .adConsent: AdConsentView()
        }
    }
}

struct MainTabView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        ZStack(alignment: .bottom) {
            ForEach(MainTab.allCases) { tab in
                NavigationStack(path: app.path(for: tab)) {
                    root(for: tab).navigationDestination(for: Route.self) { RouteView(route: $0) }
                }
                .opacity(app.tab == tab ? 1 : 0)
                .allowsHitTesting(app.tab == tab)
            }
            if app.isTabBarVisible {
                FloatingTabBar().transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy(duration: 0.25), value: app.isTabBarVisible)
    }

    @ViewBuilder private func root(for tab: MainTab) -> some View {
        switch tab {
        case .library: LibraryView()
        case .explore: ExploreView()
        case .setlists: SetlistsView()
        case .learn: LearnView()
        case .tuner: TunerView()
        }
    }
}

/// Floating pill tab bar: 66pt high, 14pt from the edges, radius 33, white 62% glass; active tab = gold 28% pill.
struct FloatingTabBar: View {
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTab.allCases) { tab in
                let on = app.tab == tab
                Button {
                    if app.tab == tab { app.popToRoot() } else { app.tab = tab }
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: tab.systemImage).font(.system(size: 20, weight: on ? .semibold : .regular)).frame(height: 24)
                        Text(tab.title).font(.outfit(11, on ? .bold : .semibold))
                    }
                    .foregroundStyle(on ? OC.ink : OC.muted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background {
                        if on {
                            Capsule().fill(OC.gold.opacity(0.28))
                                .overlay(Capsule().strokeBorder(OC.gold.opacity(0.35)))
                        }
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(5)
        .frame(height: 66)
        .background(Capsule().fill(.ultraThinMaterial).overlay(Capsule().fill(OC.glass)))
        .overlay(Capsule().strokeBorder(OC.glassBorder))
        .shadow(color: .black.opacity(0.14), radius: 20, y: 16)
        .padding(.horizontal, 14)
        .padding(.bottom, 4)
    }
}

extension View {
    /// Space at the bottom of tab-root scroll views so content clears the floating tab bar.
    func tabBarInset() -> some View { safeAreaPadding(.bottom, 84) }
}
