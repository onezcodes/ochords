import StoreKit
import SwiftUI

enum AppPhase: Equatable { case splash, onboarding, welcome, main }

enum MainTab: String, CaseIterable, Identifiable {
    case library, explore, setlists, learn, tuner
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var systemImage: String {
        switch self {
        case .library: "books.vertical"
        case .explore: "magnifyingglass"
        case .setlists: "list.bullet"
        case .learn: "graduationcap"
        case .tuner: "slider.horizontal.3"
        }
    }
}

enum PermissionKind: String, Identifiable { case camera, microphone, notifications; var id: String { rawValue } }
enum RewardKind: String, Identifiable { case scan, strings; var id: String { rawValue } }

/// Screens presented full-screen over everything.
enum Modal: Identifiable, Equatable {
    case pro
    case permission(PermissionKind)
    case reward(RewardKind)
    case interstitial
    case adConsent
    var id: String {
        switch self {
        case .pro: "pro"
        case .permission(let k): "perm-\(k.rawValue)"
        case .reward(let k): "reward-\(k.rawValue)"
        case .interstitial: "interstitial"
        case .adConsent: "adConsent"
        }
    }
}

/// Global app state: session, preferences and navigation.
@Observable
final class AppState {
    /// The O Chords API when `OChordsAPIBaseURL` is set (Debug: the local Laravel server), otherwise demo data.
    let backend: AppBackend = AppState.makeBackend()
    var usesDemoData: Bool { backend is MockBackend }
    /// Sheet reading (camera photos, images, PDFs): the server for Myanmar lyrics when signed in, else on the phone.
    let scanner: ScanService

    // Session
    var phase: AppPhase = .splash
    /// Remembered across launches, so onboarding shows once.
    var hasOnboarded = false { didSet { if !usesDemoData { UserDefaults.standard.set(hasOnboarded, forKey: Self.onboardedKey) } } }
    var profile: Profile? = nil
    /// Using the app without an account (remembered across launches; songs are kept on the phone).
    var isGuest = false { didSet { if !usesDemoData { UserDefaults.standard.set(isGuest, forKey: Self.guestKey) } } }
    /// O Chords Pro: what the App Store says on this phone, or what the account last synced from another device.
    private(set) var isPro = false
    /// StoreKit 2 (products, entitlement, purchases) and AdMob.
    let store = StoreService()
    let ads = AdService()
    var isOffline = false

    // Preferences
    var appearance: Appearance = .system
    var leftHanded = false
    var sharpNames = true
    var alwaysConvertZawgyi = false
    var alwaysSkipWarmup = false
    var personalisedAds = true { didSet { ads.personalised = personalisedAds } }
    var language = "မြန်မာ"

    // Usage counters (free plan)
    var freeScansLeft = 3
    var lives = 6
    var unreadNotifications = 0

    // Content (loaded from the backend after sign-in; demo data fills it in straight away)
    var library: [Song] = [] { didSet { if keepsGuestStore { guestStore.save(library: library) } } }
    var setlists: [Setlist] = [] { didSet { if keepsGuestStore { guestStore.save(setlists: setlists) } } }
    /// Only a guest's own songs go to the phone's guest store — never an account's.
    private var keepsGuestStore: Bool { isGuest && profile == nil && !usesDemoData }
    private let guestStore = GuestStore()
    /// Each scanned song's reading (on this phone), so it can be read again when the scanner improves.
    let scanArchive = ScanArchive()
    /// "Re-read 3 songs with the improved scanner", shown once on the Library.
    var rescanNotice: String?
    private var rescanning = false
    private static let onboardedKey = "hasOnboarded"
    private static let guestKey = "isGuest"
    var notifications: [AppNotification] = []
    /// Songs in setlists that aren't in the library (e.g. other players' sheets), by ID.
    var setlistSongs: [String: Song] = [:]
    private var refreshTask: Task<Void, Never>?

    // Navigation
    var tab: MainTab = .library
    var paths: [MainTab: [Route]] = Dictionary(uniqueKeysWithValues: MainTab.allCases.map { ($0, []) })
    var authPath: [Route] = []
    var modal: Modal? = nil

    #if DEBUG
    /// Debug-only: an image file the scan screen reads on open (`-demoScanFile <path>`).
    var demoScanFile: String? = nil
    #endif

    var isSignedIn: Bool { profile != nil }

    /// The profile screens show: the account's, a blank guest one, or the demo player's.
    var displayProfile: Profile { profile ?? (usesDemoData ? MockData.profile : .guest) }

    init() {
        scanner = OnDeviceScanService(server: backend.serverOCR)
        if usesDemoData {
            library = MockData.library
            setlists = MockData.setlists
            notifications = MockData.notifications
            unreadNotifications = MockData.notifications.filter(\.unread).count
        } else {
            let d = UserDefaults.standard
            hasOnboarded = d.bool(forKey: Self.onboardedKey)
            isGuest = d.bool(forKey: Self.guestKey)
            if isGuest {
                library = guestStore.library
                setlists = guestStore.setlists
                Task { await rescanImproved() }
            }
        }
        store.onChange = { [weak self] _ in
            guard let self else { return }
            updatePro()
            Task { await self.syncSubscription() }
        }
        Task { await store.start(); updatePro() }
    }

    // MARK: Pro

    private func updatePro() { isPro = store.isPro || (profile?.isPro ?? false) }

    /// Tells the account what the App Store says (so Pro follows the player to other devices), and takes the
    /// account's word when this phone has no subscription of its own (bought on another device).
    func syncSubscription() async {
        guard isSignedIn, !usesDemoData else { return }
        if store.isPro || profile?.isPro == true {
            if let p = try? await backend.syncSubscription(transactionJWS: store.transactionJWS) { profile = p }
        }
        updatePro()
    }

    /// Buys a Pro plan. True when the player is now on Pro (false when they cancelled or the purchase awaits approval).
    func purchasePro(_ product: Product?) async throws -> StoreService.PurchaseOutcome {
        if usesDemoData {
            try? await Task.sleep(for: .seconds(1.2))
            isPro = true
            return .purchased
        }
        guard let product else { throw StoreService.StoreError.unavailable }
        let outcome = try await store.purchase(product)
        updatePro()
        await syncSubscription()
        return outcome
    }

    func restorePurchases() async {
        guard !usesDemoData else { return }
        await store.restore()
        updatePro()
        await syncSubscription()
    }

    #if DEBUG
    /// Screenshot harness only.
    func setDemoPro(_ on: Bool) { isPro = on }
    #endif

    /// After a finished session on the free plan: AdMob's interstitial when one is loaded, the design's placeholder
    /// in builds without AdMob, nothing at all when AdMob has no ad ready.
    func showInterstitial() {
        guard !isPro else { return }
        if AdService.isConfigured { MainActor.assumeIsolated { _ = ads.showInterstitial() } } else { modal = .interstitial }
    }

    // MARK: Links

    /// `ochords://reset-password?token=…` from the reset email. Other links (terms, privacy) are handled where shown.
    func handle(url: URL) {
        guard url.scheme == "ochords", url.host == "reset-password",
              let token = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "token" })?.value,
              !token.isEmpty else { return }
        // Someone already signed in doesn't need a new password; the link is for the welcome screens.
        guard !isSignedIn else { return }
        hasOnboarded = true
        modal = nil
        paths = Dictionary(uniqueKeysWithValues: MainTab.allCases.map { ($0, []) })
        authPath = [.signIn, .resetPassword(token: token)]
        withAnimation { phase = .welcome }
    }

    private static func makeBackend() -> AppBackend {
        #if DEBUG
        // Screenshots and offline demos run on demo data.
        let d = UserDefaults.standard
        let signsIn = d.string(forKey: "demoSignInEmail") != nil || d.bool(forKey: "demoGuest")
        if d.string(forKey: "demoScreen") != nil || (d.string(forKey: "demoScanFile") != nil && !signsIn) || d.bool(forKey: "useDemoData") {
            return MockBackend()
        }
        #endif
        return APIConfig.baseURL.map { RemoteBackend(baseURL: $0) } ?? MockBackend()
    }

    func path(for tab: MainTab) -> Binding<[Route]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }

    /// Push onto the current tab (or the auth stack before sign-in).
    func push(_ route: Route) {
        if phase == .main { paths[tab, default: []].append(route) } else { authPath.append(route) }
    }

    func pop() {
        if phase == .main { _ = paths[tab]?.popLast() } else { _ = authPath.popLast() }
    }

    func popToRoot() {
        if phase == .main { paths[tab] = [] } else { authPath = [] }
    }

    /// Switch tab and reset its stack.
    func switchTo(_ t: MainTab, then route: Route? = nil) {
        tab = t
        paths[t] = route.map { [$0] } ?? []
    }

    var isTabBarVisible: Bool { phase == .main && (paths[tab] ?? []).isEmpty }

    // MARK: Session actions

    func finishSplash() { withAnimation(.easeInOut) { phase = hasOnboarded ? (isSignedIn || isGuest ? .main : .welcome) : .onboarding } }
    func finishOnboarding() { hasOnboarded = true; withAnimation { phase = .welcome } }

    func signedIn(_ p: Profile) {
        // A guest's songs and setlists come along into the new account.
        let carried = isGuest && !usesDemoData ? (library, setlists) : ([], [])
        profile = p
        updatePro()
        isGuest = false
        authPath = []
        withAnimation { phase = .main }
        modal = .adConsent
        Task {
            await moveToAccount(songs: carried.0, setlists: carried.1)
            await refresh()
            await syncSubscription()
        }
    }

    /// Uploads a guest's songs, then their setlists, then clears them from the phone. What fails to upload stays
    /// on the phone (a setlist whose songs didn't all make it is kept too, so nothing is lost).
    private func moveToAccount(songs: [Song], setlists lists: [Setlist]) async {
        guard !songs.isEmpty || !lists.isEmpty else { return }
        var songsLeft: [Song] = []
        for song in songs {
            do { try await backend.save(song) } catch { songsLeft.append(song) }
        }
        let failedIDs = Set(songsLeft.map(\.id))
        var listsLeft: [Setlist] = []
        for list in lists {
            guard list.songIDs.allSatisfy({ !failedIDs.contains($0) }) else { listsLeft.append(list); continue }
            do { _ = try await backend.saveSetlist(list) } catch { listsLeft.append(list) }
        }
        if songsLeft.isEmpty && listsLeft.isEmpty {
            guestStore.clear()
        } else {
            guestStore.save(library: songsLeft)
            guestStore.save(setlists: listsLeft)
        }
    }

    /// Picks up a saved session at launch (runs during the splash). Skips onboarding for returning players.
    func restoreSession() async {
        guard let p = try? await backend.restoreSession() else { return }
        // A saved account session wins over guest mode (the guest's songs stay in the phone's store).
        isGuest = false
        profile = p
        updatePro()
        hasOnboarded = true
        await refresh()
        await syncSubscription()
    }

    /// Reloads the player's library, setlists and notifications. Keeps what's shown if a request fails.
    /// Calls made while a refresh is running wait for that one instead of starting another.
    func refresh() async {
        guard isSignedIn, !usesDemoData else { return }
        if let running = refreshTask { return await running.value }
        let task = Task { await loadAccountData() }
        refreshTask = task
        await task.value
        refreshTask = nil
    }

    private func loadAccountData() async {
        async let songs = backend.library()
        async let lists = backend.setlists()
        async let notes = backend.notifications()
        if let songs = try? await songs { library = songs }
        if let lists = try? await lists {
            setlistSongs = lists.flatMap(\.songIDs).reduce(into: [:]) { found, id in found[id] = backend.song(id: id) }
            setlists = lists
        }
        if let notes = try? await notes {
            notifications = notes
            unreadNotifications = notes.filter(\.unread).count
        }
        await rescanImproved()
    }

    /// A song by ID from the library, setlists or anything the backend has loaded.
    func song(id: String) -> Song? {
        library.first { $0.id == id } ?? setlistSongs[id] ?? backend.song(id: id)
    }

    func markAllNotificationsRead() {
        for i in notifications.indices { notifications[i].unread = false }
        unreadNotifications = 0
        Task { try? await backend.markAllNotificationsRead() }
    }

    func continueAsGuest() {
        isGuest = true
        if !usesDemoData {
            library = guestStore.library
            setlists = guestStore.setlists
            Task { await rescanImproved() }
        }
        authPath = []
        withAnimation { phase = .main }
    }

    /// From guest mode to the sign-up / log-in screens. Their songs stay on the phone until they sign in.
    func showAccountOptions(signUp: Bool) {
        paths = Dictionary(uniqueKeysWithValues: MainTab.allCases.map { ($0, []) })
        authPath = [signUp ? .signUp : .signIn]
        withAnimation { phase = .welcome }
    }

    func signOut() {
        Task { await backend.signOut() }
        profile = nil
        isGuest = false
        updatePro()
        if !usesDemoData {
            // The account's scan readings go with it; a guest's songs (still on the phone) keep theirs.
            scanArchive.removeAll(except: Set(guestStore.library.map(\.id)))
            library = []
            setlists = []
            setlistSongs = [:]
            notifications = []
            unreadNotifications = 0
        }
        paths = Dictionary(uniqueKeysWithValues: MainTab.allCases.map { ($0, []) })
        tab = .library
        withAnimation { phase = .welcome }
    }

    /// Signed in: saved to the account. Guest: kept on the phone (see `library`).
    func addToLibrary(_ song: Song) {
        if let i = library.firstIndex(where: { $0.id == song.id }) { library[i] = song } else { library.insert(song, at: 0) }
        if isSignedIn || usesDemoData { Task { try? await backend.save(song) } }
    }

    func removeFromLibrary(_ id: String) {
        library.removeAll { $0.id == id }
        scanArchive.remove(id)
    }

    // MARK: Setlists

    /// Creates or updates a setlist. Signed in: saved to the account (the server's copy replaces ours when it
    /// answers, e.g. with its date). Guest: kept on the phone (see `setlists`).
    func saveSetlist(_ list: Setlist) {
        if let i = setlists.firstIndex(where: { $0.id == list.id }) { setlists[i] = list } else { setlists.insert(list, at: 0) }
        guard isSignedIn || usesDemoData else { return }
        Task {
            guard let saved = try? await backend.saveSetlist(list),
                  let i = setlists.firstIndex(where: { $0.id == saved.id }), setlists[i] == list else { return }
            setlists[i] = saved
        }
    }

    func deleteSetlist(_ id: String) {
        setlists.removeAll { $0.id == id }
        if isSignedIn || usesDemoData { Task { try? await backend.deleteSetlist(id: id) } }
    }

    /// Keeps what the reader saw for a newly scanned song (`scanned`: the song as the scanner produced it).
    func keepScan(_ reading: ScanReading, scanned: Song, for songID: String) {
        scanArchive.save(ScanArchive.Entry(reading: reading, scanned: ScannedParts(scanned), version: Rescan.scannerVersion, offered: nil),
                         for: songID)
    }

    /// Reads scanned songs again when the scanner has improved since. Songs the player hasn't changed are updated;
    /// if they changed the lyrics or chords, the new reading is offered on the song page instead.
    func rescanImproved() async {
        guard !rescanning else { return }
        rescanning = true
        defer { rescanning = false }
        var updated = 0
        for id in scanArchive.songIDs {
            guard var entry = scanArchive.entry(for: id), entry.version < Rescan.scannerVersion,
                  library.contains(where: { $0.id == id }) else { continue }
            let reading = entry.reading
            let new = await Task.detached(priority: .utility) { Rescan.read(reading) }.value
            entry.version = Rescan.scannerVersion
            if let new, let current = library.first(where: { $0.id == id }) {
                let outcome = Rescan.merge(new, into: current, scanned: entry.scanned)
                if let song = outcome.updated {
                    addToLibrary(song)
                    updated += 1
                }
                entry.offered = outcome.offered
                entry.scanned = ScannedParts(new)
            }
            scanArchive.save(entry, for: id)
        }
        if updated > 0 {
            rescanNotice = "Re-read \(updated) \(updated == 1 ? "song" : "songs") with the improved scanner"
        }
    }

    func isInLibrary(_ id: String) -> Bool { library.contains { $0.id == id } }
}

extension Profile {
    /// What a guest sees on profile screens: no name, no points yet.
    static let guest = Profile(
        user: UserSummary(id: "guest", name: "Guest", handle: "guest"),
        email: "", points: 0, grooveDays: 0, bestGroove: 0, dailyGoalMinutes: 10, todayMinutes: 0,
        week: Array(repeating: 0, count: 7), restNotes: 0, songsLearned: 0, lessonsDone: 0
    )
}

#if DEBUG
extension AppState {
    /// Debug-only: `-demoGuest YES` enters guest mode against the API; `-demoGuestSave YES` also saves a song
    /// as that guest — for checking that guest songs survive a relaunch and move into an account on sign-up.
    func applyDemoGuest() {
        let d = UserDefaults.standard
        guard d.bool(forKey: "demoGuest") else { return }
        hasOnboarded = true
        continueAsGuest()
        if d.bool(forKey: "demoGuestSave") {
            var song = MockData.sinSarPar
            song.id = UUID().uuidString
            song.myNote = "Saved as a guest"
            addToLibrary(song)
        }
        if let name = d.string(forKey: "demoTab"), let t = MainTab(rawValue: name) { tab = t }
        if d.bool(forKey: "demoSettings") { push(.settings) }
    }

    /// Debug-only: `-demoSignInEmail <email> -demoSignInPassword <password>` signs in through the API at launch,
    /// for checking the app against the local server without tapping through onboarding. Use the synthetic demo account.
    func applyDemoSignIn() async {
        let d = UserDefaults.standard
        guard let email = d.string(forKey: "demoSignInEmail"), let password = d.string(forKey: "demoSignInPassword") else { return }
        guard let p = try? await backend.signIn(email: email, password: password) else { return }
        hasOnboarded = true
        // Same path as a real sign-in (a guest's songs move into the account), minus the ad-consent sheet.
        signedIn(p)
        modal = nil
        await refresh()
        if let name = d.string(forKey: "demoTab"), let t = MainTab(rawValue: name) { tab = t }
        if d.bool(forKey: "demoNotifications") { push(.notifications) }
        if let file = d.string(forKey: "demoScanFile") {
            demoScanFile = file
            switchTo(.library, then: .importSheet)
        }
    }

    /// Debug-only: `-demoScreen <name>` (and optional `-demoDark YES`) jumps straight to a screen, for simulator screenshots.
    func applyDemoLaunchScreen() {
        let d = UserDefaults.standard
        if d.bool(forKey: "demoDark") { appearance = .dark }
        // With -demoSignInEmail the scan starts after signing in (applyDemoSignIn), so the server can read it.
        if let file = d.string(forKey: "demoScanFile"), d.string(forKey: "demoSignInEmail") == nil {
            hasOnboarded = true
            profile = MockData.profile
            phase = .main
            demoScanFile = file
            switchTo(.library, then: .importSheet)
            return
        }
        guard let name = d.string(forKey: "demoScreen") else { return }
        if name == "onboarding" { phase = .onboarding; return }
        if name == "welcome" { hasOnboarded = true; phase = .welcome; return }
        hasOnboarded = true
        profile = MockData.profile
        phase = .main
        let song = MockData.sinSarPar
        let scan = ScanResult(song: song, detectedZawgyi: true, lowConfidence: ["G/B"])
        // A verse with a repeated pair of lines, "( … ) x2", for Follow me.
        var repeated = song
        repeated.sections = [SongSection(kind: .verse, label: "VERSE 1", lines: [
            "([C]ငါ မကြားချင် ခဲ့သော [G/B]စကား",
            "[Am]မသိချင် ခဲ့ဆုံး [F]အကြောင်းအရာ) x2",
            "[G]နင်ဟာ ရက်စက်စွာ [C]ပြောခဲ့ပြီ",
        ])]
        // A scanned sheet without section headings comes out as one "VERSE 1" with no chorus.
        var scanned = song
        scanned.sections = [SongSection(kind: .verse, label: "VERSE 1", lines: song.sections.flatMap(\.lines))]
        if let t = MainTab(rawValue: name) { tab = t; return }
        let routes: [String: (MainTab, Route)] = [
            "song": (.library, .song(song)), "stage": (.library, .stage(song)), "warmup": (.library, .warmup(song)),
            "import": (.library, .importSheet), "listen": (.library, .listen), "notifications": (.library, .notifications),
            "profile": (.library, .profile), "settings": (.library, .settings), "chords": (.tuner, .chords(focus: nil, songChords: song.usedChords)),
            "strum": (.tuner, .strum), "keys": (.tuner, .keys), "jam": (.library, .jam(song)), "print": (.library, .printPdf(song)),
            "lesson": (.learn, .lesson(fretID: MockData.learnPath.first?.frets.first?.id ?? "")), "streak": (.learn, .streak), "game": (.learn, .game),
            "popular": (.explore, .popular), "help": (.library, .help),
            "user": (.explore, .user(MockData.uploader)), "widgets": (.library, .widgets), "liveActivity": (.library, .liveActivity),
            "review": (.library, .review(scan)), "zawgyi": (.library, .zawgyi(scan)), "handsFree": (.library, .handsFree),
            "report": (.library, .report(song)), "practice": (.tuner, .practice(chord: "C")), "saveImage": (.library, .saveImage(chord: "C", song: song)), "saveChord": (.tuner, .saveImage(chord: "Am")),
            "saveImageScanned": (.library, .saveImage(chord: "C", song: scanned)),
            "printScanned": (.library, .printPdf(scanned)),
            "stageRepeat": (.library, .stage(repeated)),
            "shared": (.explore, .shared(song)), "complete": (.learn, .complete), "tiers": (.library, .tiers),
            "feedback": (.library, .feedback), "about": (.library, .about), "terms": (.library, .terms), "privacy": (.library, .privacy),
            "deleteAccount": (.library, .deleteAccount), "adChoices": (.library, .adChoices),
        ]
        if let (t, r) = routes[name] { switchTo(t, then: r); return }
        // -demoScreen crop -demoCropFile <image>: a photo picked from the Library, at the crop step.
        if name == "crop", let path = UserDefaults.standard.string(forKey: "demoCropFile"), let data = FileManager.default.contents(atPath: path) {
            switchTo(.library, then: .importPicked(.image(data)))
            return
        }
        if name == "signIn" || name == "signUp" { profile = nil; phase = .welcome; authPath = [name == "signIn" ? .signIn : .signUp]; return }
        let modals: [String: Modal] = ["pro": .pro, "adConsent": .adConsent, "camera": .permission(.camera), "reward": .reward(.scan),
                                       "microphone": .permission(.microphone), "notifyPrimer": .permission(.notifications),
                                       "rewardStrings": .reward(.strings), "interstitial": .interstitial]
        if let m = modals[name] { modal = m }
    }
}
#endif
