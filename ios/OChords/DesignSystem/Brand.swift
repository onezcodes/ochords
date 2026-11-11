import SwiftUI
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

/// The O Chords mark: gold ring (a guitar sound hole) crossed by three ink strings.
struct LogoMark: View {
    var size: CGFloat = 30
    var stringColor: Color = OC.ink

    var body: some View {
        Canvas { ctx, sz in
            let s = sz.width / 100
            let ring = Path(ellipseIn: CGRect(x: 22 * s, y: 22 * s, width: 56 * s, height: 56 * s))
            ctx.stroke(ring, with: .color(OC.gold), lineWidth: 15 * s)
            for (x, w) in [(40.0, 4.6), (50.0, 4.2), (60.0, 3.8)] {
                var p = Path()
                p.move(to: CGPoint(x: x * s, y: 16 * s))
                p.addLine(to: CGPoint(x: x * s, y: 84 * s))
                ctx.stroke(p, with: .color(stringColor), style: StrokeStyle(lineWidth: w * s, lineCap: .round))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Mark + "Chords" wordmark (the mark reads as the "O").
struct Wordmark: View {
    var size: CGFloat = 30
    var color: Color = OC.ink
    var body: some View {
        HStack(spacing: size * 0.1) {
            LogoMark(size: size, stringColor: color)
            Text("Chords").font(.outfit(size * 0.57, .heavy)).tracking(-0.2).foregroundStyle(color)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("O Chords")
    }
}

/// App icon tile (white rounded square with the mark).
struct AppIconTile: View {
    var size: CGFloat = 84
    var body: some View {
        LogoMark(size: size * 0.86, stringColor: OC.inkFixed)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous).fill(.white))
            .overlay(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous).strokeBorder(Color.black.opacity(0.08)))
            .shadow(color: .black.opacity(0.12), radius: 14, y: 10)
    }
}

/// "POWERED BY" + Onez Codes logo (always on white).
struct PoweredByOnez: View {
    var compact = false
    var body: some View {
        VStack(spacing: 8) {
            Text("POWERED BY").font(.outfit(11, .semibold)).tracking(3).foregroundStyle(OC.faint)
            Image("OnezLogo")
                .resizable().scaledToFit()
                .frame(width: compact ? 130 : 200)
                .padding(compact ? 6 : 8)
                .background(RoundedRectangle(cornerRadius: 10).fill(.white))
                .accessibilityLabel("Onez Codes")
        }
    }
}

// MARK: - Ads

/// Adaptive banner — Library, Popular, Chords by key, Profile, Tiers. A real AdMob banner when the build has an
/// app ID (`AdService`); the design's placeholder otherwise (demo build, screenshots). Nothing on Pro.
struct BannerAdView: View {
    var onRemoveAds: () -> Void = {}
    @Environment(AppState.self) private var app
    @State private var failed = false
    @State private var height = AdService.bannerHeight(width: UIScreen.main.bounds.width - 40)

    var body: some View {
        if !app.isPro {
            if AdService.isConfigured && !failed {
                liveBanner
            } else {
                placeholder
            }
        }
    }

    private var liveBanner: some View {
        GeometryReader { geo in
            Group {
                #if canImport(GoogleMobileAds)
                AdMobBanner(width: geo.size.width, ads: app.ads) { loaded, adHeight in
                    if loaded { if adHeight > 0 { height = adHeight } } else { failed = true }
                }
                #endif
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityLabel("Advertisement")
    }

    private var placeholder: some View {
        Group {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(OC.softFill)
                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(OC.faint.opacity(0.5)))
                    .frame(width: 46, height: 46)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        AdBadge()
                        Text("[Banner ad headline]").font(.outfit(13, .bold)).lineLimit(1)
                    }
                    HStack(spacing: 0) {
                        Text("[Advertiser] · ").foregroundStyle(OC.muted)
                        Button("Remove ads", action: onRemoveAds).font(.outfit(11, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                    }
                    .font(.outfit(11))
                }
                Spacer(minLength: 0)
                Text("[Open]").font(.outfit(12, .bold)).padding(.horizontal, 10).frame(height: 30).background(Capsule().fill(OC.softFill))
            }
            .foregroundStyle(OC.ink)
            .padding(.leading, 6).padding(.trailing, 8)
            .frame(height: 58)
            .background(RoundedRectangle(cornerRadius: 16).fill(OC.surface))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(OC.border))
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Advertisement")
        }
    }
}

struct AdBadge: View {
    var body: some View {
        Text("Ad").font(.outfit(10, .heavy)).foregroundStyle(OC.goldText)
            .padding(.horizontal, 5).frame(height: 16)
            .background(RoundedRectangle(cornerRadius: 4).fill(OC.goldTint))
            .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(Color(hex: 0xB8860B)))
    }
}
