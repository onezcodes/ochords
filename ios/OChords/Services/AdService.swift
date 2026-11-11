import SwiftUI
import AppTrackingTransparency
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

/// Google AdMob for the free plan: an adaptive banner, an interstitial after finished sessions, and rewarded ads
/// (an extra scan, a refill of strings). Ad unit IDs come from Info.plist (`OChordsAdMob*`, set per build
/// configuration in project.yml — Google's test units in Debug). With no app ID the app shows its own placeholders,
/// so the demo build and screenshots never load real ads. "Ads that fit me" off = non-personalised requests.
@Observable
final class AdService: @unchecked Sendable {
    struct Units {
        var appID: String
        var banner: String
        var interstitial: String
        var rewarded: String
    }

    static let units: Units? = {
        func key(_ name: String) -> String { (Bundle.main.object(forInfoDictionaryKey: name) as? String ?? "").trimmingCharacters(in: .whitespaces) }
        let u = Units(appID: key("OChordsAdMobAppID"), banner: key("OChordsAdMobBannerUnit"),
                      interstitial: key("OChordsAdMobInterstitialUnit"), rewarded: key("OChordsAdMobRewardedUnit"))
        return u.appID.isEmpty ? nil : u
    }()

    /// True when the SDK is in the build and an app ID is configured.
    static var isConfigured: Bool {
        #if canImport(GoogleMobileAds)
        return units != nil
        #else
        return false
        #endif
    }

    private(set) var started = false
    var personalised = true

    /// Starts the SDK once (after the ad-consent choice, so the first request already carries it).
    func start() async {
        #if canImport(GoogleMobileAds)
        guard Self.isConfigured, !started else { return }
        await MobileAds.shared.start()
        started = true
        #endif
    }

    /// The iOS tracking prompt; only meaningful when the player chose "Ads that fit me".
    func requestTracking() async -> Bool {
        let status = await ATTrackingManager.requestTrackingAuthorization()
        return status == .authorized
    }

    #if canImport(GoogleMobileAds)
    func request() -> Request {
        let r = Request()
        if !personalised {
            let extras = Extras()
            extras.additionalParameters = ["npa": "1"]
            r.register(extras)
        }
        return r
    }

    private var interstitial: InterstitialAd?
    private var rewarded: RewardedAd?

    /// Loads the next interstitial in the background so it shows without a wait.
    func preloadInterstitial() {
        guard Self.isConfigured, interstitial == nil, let unit = Self.units?.interstitial else { return }
        Task {
            await start()
            interstitial = try? await InterstitialAd.load(with: unit, request: request())
        }
    }

    /// Shows the loaded interstitial; false when none is ready (the caller shows nothing).
    @MainActor
    func showInterstitial() -> Bool {
        guard let ad = interstitial, let root = Self.topViewController() else { return false }
        interstitial = nil
        ad.present(from: root)
        preloadInterstitial()
        return true
    }

    /// Loads and shows a rewarded ad. Resolves true when the player earned the reward.
    @MainActor
    func showRewarded() async -> Bool {
        guard Self.isConfigured, let unit = Self.units?.rewarded else { return false }
        await start()
        if rewarded == nil { rewarded = try? await RewardedAd.load(with: unit, request: request()) }
        guard let ad = rewarded, let root = Self.topViewController() else { return false }
        rewarded = nil
        return await withCheckedContinuation { continuation in
            var earned = false
            let delegate = PresentDelegate { continuation.resume(returning: earned) }
            ad.fullScreenContentDelegate = delegate
            ad.present(from: root) { earned = true }
            Self.retained = delegate
        }
    }

    private static var retained: PresentDelegate?

    private final class PresentDelegate: NSObject, FullScreenContentDelegate {
        let done: () -> Void
        init(done: @escaping () -> Void) { self.done = done }
        func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) { done() }
        func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) { done() }
    }
    #else
    func preloadInterstitial() {}
    @MainActor func showInterstitial() -> Bool { false }
    @MainActor func showRewarded() async -> Bool { false }
    #endif

    /// The adaptive banner's height for a width (50–90 pt); the placeholder's height when the SDK isn't in the build.
    static func bannerHeight(width: CGFloat) -> CGFloat {
        #if canImport(GoogleMobileAds)
        let h = currentOrientationAnchoredAdaptiveBanner(width: width).size.height
        return h > 0 ? h : 58
        #else
        return 58
        #endif
    }

    @MainActor
    static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard var top = scenes.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController else { return nil }
        while let next = top.presentedViewController { top = next }
        return top
    }
}

#if canImport(GoogleMobileAds)
/// An adaptive AdMob banner the width of its container (about 50–60 pt high on phones). The ad is requested once
/// the container has a real width, so it comes back at the right size.
struct AdMobBanner: UIViewRepresentable {
    let width: CGFloat
    let ads: AdService
    /// Whether the ad came, and how tall it is.
    var onLoaded: (Bool, CGFloat) -> Void = { _, _ in }

    func makeCoordinator() -> Coordinator { Coordinator(onLoaded: onLoaded) }

    func makeUIView(context: Context) -> BannerView {
        let view = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: max(width, 320)))
        view.adUnitID = AdService.units?.banner
        view.rootViewController = AdService.topViewController()
        view.delegate = context.coordinator
        view.setContentHuggingPriority(.required, for: .horizontal)
        return view
    }

    func updateUIView(_ view: BannerView, context: Context) {
        guard width >= 100, !context.coordinator.requested else { return }
        context.coordinator.requested = true
        view.adSize = currentOrientationAnchoredAdaptiveBanner(width: width)
        Task { @MainActor in
            await ads.start()
            view.load(ads.request())
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: BannerView, context: Context) -> CGSize? {
        CGSize(width: width, height: uiView.adSize.size.height)
    }

    final class Coordinator: NSObject, BannerViewDelegate {
        let onLoaded: (Bool, CGFloat) -> Void
        var requested = false
        init(onLoaded: @escaping (Bool, CGFloat) -> Void) { self.onLoaded = onLoaded }
        func bannerViewDidReceiveAd(_ bannerView: BannerView) { onLoaded(true, bannerView.adSize.size.height) }
        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) { onLoaded(false, 0) }
    }
}
#endif
