import SwiftUI

/// Every pushable screen. Add a case here, then map it in `RouteView`.
enum Route: Hashable {
    // Auth
    case signIn, signUp
    /// From the `ochords://reset-password?token=…` link in the reset email.
    case resetPassword(token: String)

    // Library & account
    case notifications, profile, user(UserSummary), widgets, liveActivity

    // Import & listen
    case importSheet, review(ScanResult), zawgyi(ScanResult), listen
    /// Scan a photo or PDF already picked (from the Library), without opening the camera.
    case importPicked(ScanSource)

    // Song & tools
    case song(Song), stage(Song), warmup(Song), handsFree, printPdf(Song), report(Song), jam(Song)
    case chords(focus: String?, songChords: [String]), keys, strum, practice(chord: String), saveImage(chord: String, song: Song? = nil)

    // Community
    case shared(Song), popular

    // Learn
    case lesson(fretID: String), complete, streak, tiers, game

    // Settings & support
    case settings, help, feedback, about, terms, privacy, deleteAccount, adChoices
}

struct RouteView: View {
    let route: Route

    var body: some View {
        switch route {
        case .signIn: SignInView()
        case .signUp: SignUpView()
        case .resetPassword(let token): ResetPasswordView(token: token)
        case .notifications: NotificationsView()
        case .profile: ProfileView()
        case .user(let u): UserProfileView(user: u)
        case .widgets: WidgetsPreviewView()
        case .liveActivity: LiveActivityPreviewView()
        case .importSheet: ImportView()
        case .importPicked(let source): ImportView(picked: source)
        case .review(let r): ReviewView(result: r)
        case .zawgyi(let r): ZawgyiView(result: r)
        case .listen: ListenView()
        case .song(let s): SongView(song: s)
        case .stage(let s): StageView(song: s)
        case .warmup(let s): WarmupView(song: s)
        case .handsFree: HandsFreeView()
        case .printPdf(let s): PrintPdfView(song: s)
        case .report(let s): ReportView(song: s)
        case .jam(let s): JamView(song: s)
        case .chords(let f, let c): ChordGuideView(focus: f, songChords: c)
        case .keys: KeysView()
        case .strum: StrumTrainerView()
        case .practice(let c): PracticeView(chord: c)
        case .saveImage(let c, let s): SaveImageView(chord: c, song: s)
        case .shared(let s): SharedSheetView(song: s)
        case .popular: PopularView()
        case .lesson(let id): LessonView(fretID: id)
        case .complete: CompleteView()
        case .streak: StreakView()
        case .tiers: TiersView()
        case .game: GameView()
        case .settings: SettingsView()
        case .help: HelpView()
        case .feedback: FeedbackView()
        case .about: AboutView()
        case .terms: TermsView()
        case .privacy: PrivacyView()
        case .deleteAccount: DeleteAccountView()
        case .adChoices: AdChoicesView()
        }
    }
}
