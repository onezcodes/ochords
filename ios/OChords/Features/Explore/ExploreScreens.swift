import SwiftUI

// MARK: - Shared helpers (private to the Explore group)

private enum ExploreFilter: Int, CaseIterable, Identifiable {
    case all, myanmar, english, easy
    var id: Int { rawValue }
    var label: String { ["All", "Myanmar", "English", "Easy"][rawValue] }

    func matches(_ sheet: CommunitySheet) -> Bool {
        switch self {
        case .all: true
        case .myanmar: sheet.song.isMyanmar
        case .english: !sheet.song.isMyanmar
        case .easy: sheet.difficulty == "Beginner"
        }
    }
}

/// Text on an ink-filled surface (paper in light, ink in dark).
private let exploreOnInk = Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614))
/// Soft gold border used for the user's own sheets (#FFE3A3).
private let exploreMineBorder = Color(light: UIColor(hex: 0xFFE3A3), dark: UIColor(hex: 0xFFB800, alpha: 0.35))

private extension Song {
    /// Sheets the user uploaded themselves have no uploader.
    var isMine: Bool { uploader == nil }
    var usesMyanmarFont: Bool { isMyanmar && !title.hasPrefix("[") }
    var titleFont: Font { usesMyanmarFont ? .myanmar(16, .semibold) : .outfit(16, .semibold) }
    func titleFont(_ size: CGFloat, _ weight: Font.Weight) -> Font { usesMyanmarFont ? .myanmar(size, weight) : .outfit(size, weight) }
    var artistFont: Font { usesMyanmarFont ? .myanmar(12) : .outfit(12) }
    var byName: String { uploader?.name ?? "You" }
    var byInitial: String { isMine ? "Y" : "?" }
    var tier: Tier { uploader?.tier ?? MockData.me.tier }
    var metaLine: String { versionName ?? (isMine ? "Imported from photo" : source.label) }

    /// Route used when a row is opened: own sheets open the song, others the shared preview.
    var exploreRoute: Route { isMine ? .song(self) : .shared(self) }

    /// A copy moved by `t` semitones (key + every chord).
    func transposed(by t: Int) -> Song {
        guard t != 0 else { return self }
        var s = self
        s.key = ChordTheory.transpose(key, by: t)
        s.sections = sections.map { sec in
            var sec = sec
            sec.lines = sec.lines.map { MockData.transposeLine($0, by: t) }
            sec.bars = sec.bars.map { ChordTheory.transpose($0, by: t) }
            return sec
        }
        return s
    }
}

private let exploreAvatarColors: [UInt32] = [0x0F766E, 0x6D28D9, 0xFFB800, 0x4A4743, 0x1E3A8A]

private func exploreAvatarColor(_ song: Song) -> Color {
    if song.isMine { return Color(hex: 0x1E3A8A) }
    let h = song.id.unicodeScalars.reduce(0) { $0 + Int($1.value) }
    return Color(hex: exploreAvatarColors[h % exploreAvatarColors.count])
}

/// Ink-selected filter chip (Explore / Popular).
private struct ExploreFilterChip: View {
    let text: String
    let isOn: Bool
    var height: CGFloat = 36
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.outfit(13, .bold))
                .foregroundStyle(isOn ? exploreOnInk : OC.ink)
                .padding(.horizontal, 12)
                .frame(minWidth: 44, minHeight: height)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isOn ? OC.ink : OC.glass))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isOn ? OC.ink : OC.border))
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

private struct ExploreFilterBar: View {
    @Binding var filter: ExploreFilter
    var height: CGFloat = 36
    var body: some View {
        HStack(spacing: 6) {
            ForEach(ExploreFilter.allCases) { f in
                ExploreFilterChip(text: f.label, isOn: f == filter, height: height) {
                    withAnimation(.snappy(duration: 0.2)) { filter = f }
                }
            }
            Spacer(minLength: 0)
        }
    }
}

/// Key tile (48pt rounded square with the key in mono).
private struct ExploreKeyTile: View {
    let key: String
    let mine: Bool
    var size: CGFloat = 48
    var body: some View {
        Text(key)
            .font(.mono(size > 44 ? 17 : 15))
            .foregroundStyle(mine ? OC.goldText : OC.ink)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(mine ? OC.goldTint : OC.line))
    }
}

private struct ExploreTierShield: View {
    let tier: Tier
    var body: some View {
        Image(systemName: "shield.fill")
            .font(.system(size: 11))
            .foregroundStyle(Color(hex: tier.colorHex))
            .accessibilityLabel("\(tier.title) tier")
    }
}

// MARK: - Explore (tab root)

struct ExploreView: View {
    @Environment(AppState.self) private var app
    @State private var query = ""
    @State private var filter: ExploreFilter = .all
    @State private var results: [CommunitySheet] = []
    /// False until the first community fetch completes (shows the skeleton state).
    @State private var loaded = false
    @State private var popular: [CommunitySheet] = []
    @State private var toast: Toast?
    @FocusState private var searchFocused: Bool

    private var trimmed: String { query.trimmingCharacters(in: .whitespaces) }
    private var filtered: [CommunitySheet] { results.filter(filter.matches) }
    private var showNoResults: Bool { !trimmed.isEmpty && results.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                if !loaded {
                    ExploreLoadingSkeleton()
                } else if showNoResults {
                    ExploreNoResults(
                        onUpload: { app.push(.importSheet) },
                        onRequest: { toast = Toast(text: "Request sent to the community") },
                        onSpelling: { searchFocused = true },
                        onArtist: { query = ""; searchFocused = true },
                        onPopular: { app.push(.popular) }
                    )
                } else {
                    feed
                }
            }
            .scrollDismissesKeyboard(.immediately)
            .tabBarInset()
        }
        .ocPage()
        .toast($toast)
        .task(id: query) {
            if !trimmed.isEmpty { try? await Task.sleep(for: .milliseconds(300)) }
            guard !Task.isCancelled else { return }
            if let r = try? await app.backend.explore(query: query), !Task.isCancelled {
                withAnimation(.snappy) { results = r; loaded = true }
            }
        }
        .task {
            if popular.isEmpty, let p = try? await app.backend.popular() { popular = Array(p.prefix(4)) }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: showNoResults ? 16 : 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Explore").font(.outfit(36, .heavy)).tracking(-1).foregroundStyle(OC.ink)
                if loaded && !showNoResults {
                    Text("Chord sheets shared by the O Chords community").font(.outfit(13)).foregroundStyle(OC.muted)
                }
            }
            searchField
            if !loaded {
                HStack(spacing: 10) {
                    ForEach([64, 80, 58, 72], id: \.self) { w in
                        ExploreSkeletonBar(width: CGFloat(w), height: 34, radius: OC.rChip)
                    }
                }
                .frame(minHeight: 44)
                .accessibilityHidden(true)
            } else if !showNoResults {
                ExploreFilterBar(filter: $filter)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").font(.system(size: 16, weight: .semibold)).foregroundStyle(OC.muted)
            TextField("", text: $query, prompt: Text("Song, artist or uploader…").foregroundStyle(OC.muted))
                .font(.outfit(16))
                .foregroundStyle(OC.ink)
                .focused($searchFocused)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .accessibilityLabel("Search shared chords")
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .heavy))
                        .foregroundStyle(OC.muted)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(OC.softFill))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, query.isEmpty ? 14 : 2)
        .frame(height: 46)
        .glass(radius: OC.rButton)
        .overlay {
            if showNoResults || searchFocused {
                RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).strokeBorder(OC.gold, lineWidth: 1)
            }
        }
    }

    // MARK: Feed

    private var feed: some View {
        LazyVStack(alignment: .leading, spacing: 10) {
            if trimmed.isEmpty && !popular.isEmpty {
                popularStrip.padding(.bottom, 8)
            }
            HStack(alignment: .firstTextBaseline) {
                Text(trimmed.isEmpty ? "RECENTLY SHARED" : "\(filtered.count) RESULTS")
                    .font(.outfit(13, .bold)).tracking(0.5).foregroundStyle(OC.muted)
                Spacer()
                Text("Newest first").font(.outfit(12)).foregroundStyle(OC.muted)
            }

            ForEach(Array(filtered.enumerated()), id: \.element.id) { i, sheet in
                ExploreSheetRow(sheet: sheet, versions: versionCount(sheet.song))
                if !app.isPro, filtered.count > 2, (i == 1 || (i > 1 && (i - 1) % 8 == 0)), i < filtered.count - 1 {
                    ExploreNativeAd()
                }
            }

            if filtered.isEmpty {
                VStack(spacing: 6) {
                    Text("No shared chords match yet").font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                    Text("Be the first — scan the sheet and share it.").font(.outfit(13)).foregroundStyle(OC.muted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .multilineTextAlignment(.center)
            }

            shareCard.padding(.top, 4)
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 16)
    }

    private func versionCount(_ song: Song) -> Int {
        (MockData.explore.map(\.song) + app.library).reduce(into: Set<String>()) { set, s in
            if s.title == song.title && !s.title.hasPrefix("[") { set.insert(s.id) }
        }.count
    }

    private var popularStrip: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Label {
                    Text("Popular this week").font(.outfit(17, .heavy)).foregroundStyle(OC.ink)
                } icon: {
                    Image(systemName: "flame.fill").font(.system(size: 16)).foregroundStyle(OC.gold)
                }
                .labelStyle(ExploreTightLabelStyle())
                Spacer()
                Button("See chart ›") { app.push(.popular) }
                    .font(.outfit(13, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                    .frame(minHeight: 44)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Array(popular.enumerated()), id: \.element.id) { i, sheet in
                        ExplorePopularCard(rank: i + 1, song: sheet.song) { app.push(sheet.song.exploreRoute) }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 4)
            }
            .padding(.horizontal, -20)
        }
    }

    private var shareCard: some View {
        Button { app.push(.importSheet) } label: {
            HStack(spacing: 12) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(OC.gold)
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0x2A2825)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Share your chord sheets").font(.outfit(15, .bold))
                    Text("Songs you upload as Public show up here for everyone.")
                        .font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .inkGlass()
        }
        .buttonStyle(.plain)
    }
}

private struct ExploreTightLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 6) { configuration.icon; configuration.title }
    }
}

/// Pulsing grey placeholder bar used by the loading skeleton.
private struct ExploreSkeletonBar: View {
    var width: CGFloat? = nil
    var height: CGFloat = 12
    var radius: CGFloat = 6
    @State private var dim = false

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(OC.ink.opacity(0.08))
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
            .opacity(dim ? 0.55 : 1)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { dim = true }
            }
    }
}

/// Explore — first load of community sheets.
private struct ExploreLoadingSkeleton: View {
    var body: some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                Spacer(minLength: 0)
                ExploreSkeletonBar(width: 170, height: 18, radius: 9)
                ExploreSkeletonBar(width: 110, height: 12)
            }
            .padding(20)
            .frame(maxWidth: .infinity, minHeight: 150, alignment: .leading)
            .glass()

            ForEach(0..<3, id: \.self) { _ in
                HStack(spacing: 14) {
                    ExploreSkeletonBar(width: 52, height: 52, radius: OC.rButton)
                    VStack(alignment: .leading, spacing: 10) {
                        ExploreSkeletonBar(width: 160, height: 14, radius: 7)
                        ExploreSkeletonBar(width: 104, height: 10, radius: 5)
                    }
                    Spacer(minLength: 0)
                }
                .padding(13)
                .glass()
            }

            HStack(spacing: 10) {
                ProgressView().tint(OC.gold)
                Text("Loading community chords…").font(.outfit(14, .bold)).foregroundStyle(OC.muted)
            }
            .padding(.top, 6)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 16)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading community chords")
    }
}

private struct ExplorePopularCard: View {
    let rank: Int
    let song: Song
    let action: () -> Void

    var body: some View {
        let (bg, fg, keyBg): (Color, Color, Color) =
            rank == 1 ? (OC.inkFixed, OC.paper, Color(hex: 0x393733))
            : song.isMine ? (OC.goldTint, OC.goldText, OC.surface)
            : (OC.surface, OC.ink, OC.line)
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("\(rank)").font(.outfit(26, .heavy))
                    Spacer()
                    Text(song.key).font(.mono(13)).padding(.horizontal, 7).padding(.vertical, 3)
                        .background(Capsule().fill(keyBg))
                }
                Text(song.title).font(song.titleFont(15, .bold)).lineLimit(1)
                Spacer(minLength: 0)
                HStack(spacing: 0) {
                    Text(song.artist).font(song.artistFont)
                    Text(" · [n] plays").font(.outfit(12))
                }
                .lineLimit(1).opacity(0.8)
            }
            .foregroundStyle(fg)
            .padding(12)
            .frame(width: 138, height: 124, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(bg))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Number \(rank) this week, \(song.title)")
        .accessibilityAddTraits(.isButton)
    }
}

private struct ExploreSheetRow: View {
    @Environment(AppState.self) private var app
    let sheet: CommunitySheet
    let versions: Int

    private var song: Song { sheet.song }

    var body: some View {
        let saved = app.isInLibrary(song.id)
        HStack(spacing: 12) {
            Button { app.push(song.exploreRoute) } label: {
                HStack(spacing: 12) {
                    ExploreKeyTile(key: song.key, mine: song.isMine)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(song.title).font(song.titleFont).foregroundStyle(OC.ink).lineLimit(1)
                            if versions > 1 {
                                Text("\(versions) versions")
                                    .font(.outfit(11, .bold)).foregroundStyle(OC.blue)
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(OC.blueTint))
                                    .fixedSize()
                            }
                        }
                        HStack(spacing: 0) {
                            Text(song.artist).font(song.artistFont)
                            Text(" · \(song.metaLine)").font(.outfit(12))
                        }
                        .foregroundStyle(OC.muted).lineLimit(1)
                        HStack(spacing: 6) {
                            Text(song.byInitial)
                                .font(.outfit(10, .heavy)).foregroundStyle(.white)
                                .frame(width: 18, height: 18)
                                .background(Circle().fill(exploreAvatarColor(song)))
                            Text(song.byName).font(.outfit(12, .bold)).foregroundStyle(OC.ink)
                            ExploreTierShield(tier: song.tier)
                            Text("· \(song.isMine ? "just now" : "[time ago]")").font(.outfit(12)).foregroundStyle(OC.muted)
                        }
                        .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(song.title) by \(song.artist), key \(song.key), shared by \(song.byName)")
            .accessibilityAddTraits(.isButton)

            Button {
                withAnimation(.snappy(duration: 0.2)) {
                    if saved { app.removeFromLibrary(song.id) } else { app.addToLibrary(song) }
                }
            } label: {
                Image(systemName: saved ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(saved ? OC.gold : OC.ink)
                    .frame(width: 44, height: 44)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(saved ? OC.inkFixed : OC.glass))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(OC.border))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(saved ? "Remove from library" : "Save to library")
            .accessibilityAddTraits(saved ? .isSelected : [])
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.surface))
        .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(song.isMine ? exploreMineBorder : OC.border))
    }
}

/// Native ad card in the Explore feed (placeholder until AdMob is wired).
private struct ExploreNativeAd: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 14)
                    .fill(OC.softFill)
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(OC.faint.opacity(0.5)))
                    .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        AdBadge()
                        Text("[Advertiser]").font(.outfit(12)).foregroundStyle(OC.muted)
                    }
                    Text("[Native ad headline]").font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                    Text("[Short ad body, up to 90 characters]").font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(2)
                }
                Spacer(minLength: 0)
                Button { app.push(.adChoices) } label: {
                    Text("i")
                        .font(.outfit(11, .heavy)).foregroundStyle(OC.muted)
                        .frame(width: 18, height: 18)
                        .overlay(Circle().strokeBorder(Color(hex: 0xC9C6C0)))
                        .frame(width: 32, height: 32, alignment: .topTrailing)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Why this ad")
            }
            HStack {
                Spacer()
                Text("[Call to action]")
                    .font(.outfit(13, .bold)).foregroundStyle(exploreOnInk)
                    .padding(.horizontal, 14).frame(height: 34)
                    .background(RoundedRectangle(cornerRadius: 12).fill(OC.ink))
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.surface))
        .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(OC.border))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Advertisement")
    }
}

/// Explore — no results for the search.
private struct ExploreNoResults: View {
    let onUpload: () -> Void
    let onRequest: () -> Void
    let onSpelling: () -> Void
    let onArtist: () -> Void
    let onPopular: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(OC.goldText)
                .frame(width: 104, height: 104)
                .background(RoundedRectangle(cornerRadius: 34, style: .continuous).fill(OC.goldTint.opacity(0.85)))
                .overlay(RoundedRectangle(cornerRadius: 34, style: .continuous).strokeBorder(OC.glassBorder))
                .shadow(color: .black.opacity(0.07), radius: 15, y: 10)

            VStack(spacing: 8) {
                Text("No one has shared this yet").font(.outfit(24, .bold)).tracking(-0.5).foregroundStyle(OC.ink)
                (Text("Be the first — upload your sheet and earn ")
                    + Text("+50 O Points").bold().foregroundColor(OC.ink)
                    + Text(", or ask the community for it."))
                    .font(.outfit(15)).foregroundStyle(OC.muted).lineSpacing(3)
            }
            .multilineTextAlignment(.center)

            VStack(spacing: 10) {
                Button(action: onUpload) {
                    Label("Upload this song", systemImage: "camera")
                }
                .buttonStyle(.gold)
                Button("Request it from the community", action: onRequest)
                    .buttonStyle(GlassButtonStyle(height: 54))
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("TRY INSTEAD").font(.outfit(13, .bold)).foregroundStyle(OC.muted)
                HStack(spacing: 8) {
                    tryChip("Check the spelling", onSpelling)
                    tryChip("Search by artist", onArtist)
                }
                tryChip("Popular this week", onPopular)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 24)
        .padding(.top, 30)
        .padding(.bottom, 24)
    }

    private func tryChip(_ text: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.outfit(14, .semibold)).foregroundStyle(OC.ink)
                .padding(.horizontal, 14).frame(minHeight: 44)
                .glass(radius: OC.rChip)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Shared sheet (someone else's version)

struct SharedSheetView: View {
    let song: Song
    @Environment(AppState.self) private var app
    @State private var current: Song
    @State private var following = false
    @State private var showSetlistPicker = false
    @State private var toast: Toast?

    init(song: Song) {
        self.song = song
        _current = State(initialValue: song)
    }

    /// Every known version of this song (same title).
    private var versions: [Song] {
        guard !song.title.hasPrefix("[") else { return [song] }
        var seen = Set<String>(), out: [Song] = []
        for s in app.library + MockData.explore.map(\.song) where s.title == song.title && !seen.contains(s.id) {
            seen.insert(s.id); out.append(s)
        }
        if !seen.contains(song.id) { out.append(song) }
        return out.filter(\.isMine) + out.filter { !$0.isMine }
    }

    private var chorus: SongSection? {
        current.sections.first { $0.kind == .chorus } ?? current.sections.first { !$0.lines.isEmpty }
    }

    private var chorusChords: [String] {
        var seen = Set<String>(), out: [String] = []
        for c in (chorus?.lines ?? []).flatMap(ChordLine.chords(in:)) where !seen.contains(c) { seen.insert(c); out.append(c) }
        return out
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(backLabel: "Back to Explore", onBack: { app.pop() }) {
                ShareLink(item: "\(current.title) — \(current.artist) · Key \(current.key) on O Chords") {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(OC.ink)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Share link")
            }
            .padding(.top, 10)

            header.padding(.horizontal, 20)

            preview
                .padding(.horizontal, 20)
                .padding(.top, 14)

            bottomBar
        }
        .ocPage()
        .toast($toast)
        .confirmationDialog("Add to setlist", isPresented: $showSetlistPicker, titleVisibility: .visible) {
            ForEach(app.setlists) { list in
                Button("\(list.name) · \(list.note)") { add(to: list) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Myanmar glyphs carry tall line metrics; pull the meta line up to match the design.
            VStack(alignment: .leading, spacing: current.usesMyanmarFont ? -10 : 0) {
                Text(current.title).font(current.titleFont(28, .bold)).foregroundStyle(OC.ink).lineLimit(2)
                HStack(spacing: 0) {
                    Text(current.artist).font(current.usesMyanmarFont ? .myanmar(14, .semibold) : .outfit(14, .semibold)).foregroundStyle(OC.ink)
                    Text(" · Key \(current.key) · \(current.versionName?.lowercased() ?? "from a photo")").font(.outfit(14)).foregroundStyle(OC.muted)
                }
                .lineLimit(1)
            }

            uploaderCard

            if versions.count > 1 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("VERSIONS").font(.outfit(12, .bold)).tracking(0.5).foregroundStyle(OC.muted)
                    HStack(spacing: 6) {
                        ForEach(versions) { v in
                            let on = v.id == current.id
                            Button { withAnimation(.snappy(duration: 0.2)) { current = v } } label: {
                                Text(v.isMine ? "Yours · Key \(v.key)" : "\(v.byName) · Key \(v.key)")
                                    .font(.outfit(13, .bold)).lineLimit(1).minimumScaleFactor(0.8)
                                    .foregroundStyle(on ? exploreOnInk : OC.ink)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .padding(.horizontal, 6)
                                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? OC.ink : OC.glass))
                                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OC.ink : OC.border))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }
            }
        }
    }

    private var uploaderCard: some View {
        HStack(spacing: 10) {
            Button {
                if let u = current.uploader { app.push(.user(u)) } else { app.push(.profile) }
            } label: {
                HStack(spacing: 10) {
                    Text(current.byInitial)
                        .font(.outfit(14, .heavy)).foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(exploreAvatarColor(current)))
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 5) {
                            Text(current.byName).font(.outfit(15, .bold)).foregroundStyle(OC.ink)
                            ExploreTierShield(tier: current.tier)
                        }
                        Text(current.uploader.map { "\($0.sheets) public uploads" } ?? "Public")
                            .font(.outfit(12)).foregroundStyle(OC.muted)
                    }
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(current.isMine ? "Your profile" : "Uploader \(current.byName)")

            if !current.isMine {
                Button { withAnimation(.snappy(duration: 0.2)) { following.toggle() } } label: {
                    Text(following ? "Following" : "Follow")
                        .font(.outfit(13, .bold))
                        .foregroundStyle(following ? OC.ink : exploreOnInk)
                        .padding(.horizontal, 14).frame(height: 36)
                        .background(Capsule().fill(following ? OC.glass : OC.ink))
                        .overlay(Capsule().strokeBorder(following ? OC.border : OC.ink))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(following ? .isSelected : [])
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .glass()
    }

    // MARK: Preview

    private var preview: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("\(chorus?.kind == .chorus ? "CHORUS" : (chorus?.label ?? "PREVIEW")) · PREVIEW")
                        .font(.outfit(12, .heavy)).tracking(0.5).foregroundStyle(OC.inkFixed)
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(Capsule().fill(OC.gold))
                    Spacer()
                    Text(chorusChords.joined(separator: " ")).font(.mono(13)).foregroundStyle(OC.goldText).lineLimit(1)
                }
                ForEach(Array((chorus?.lines ?? []).enumerated()), id: \.offset) { _, line in
                    ExploreChordLineView(line: line, myanmar: current.isMyanmar)
                }
                Button("Open full song ›") { app.push(.song(current)) }
                    .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                    .frame(minHeight: 44)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18).fill(OC.goldTint))
    }

    // MARK: Bottom bar

    private var bottomBar: some View {
        let saved = app.isInLibrary(current.id)
        return VStack(spacing: 10) {
            HStack(spacing: 8) {
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        if saved { app.removeFromLibrary(current.id) } else {
                            app.addToLibrary(current)
                            toast = Toast(text: "Saved to your library")
                        }
                    }
                } label: {
                    Label(saved ? "Saved to library" : "Save to library", systemImage: saved ? "bookmark.fill" : "bookmark")
                        .font(.outfit(15, .bold))
                        .foregroundStyle(saved ? OC.gold : OC.inkFixed)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(saved ? OC.inkFixed : OC.gold))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(saved ? .isSelected : [])

                Button { showSetlistPicker = true } label: {
                    Text("+ Setlist").font(.outfit(14, .bold)).foregroundStyle(OC.ink)
                        .padding(.horizontal, 16).frame(height: 50)
                        .background(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).fill(OC.glass))
                        .overlay(RoundedRectangle(cornerRadius: OC.rButton, style: .continuous).strokeBorder(OC.border))
                }
                .buttonStyle(.plain)
            }
            HStack {
                Button("Suggest a correction") { toast = Toast(text: "Thanks — the uploader will review it", systemImage: "pencil") }
                    .font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                Spacer()
                Button("Report") { app.push(.report(current)) }
                    .font(.outfit(13, .semibold)).foregroundStyle(OC.muted)
            }
            .buttonStyle(.plain)
            .frame(minHeight: 44)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
        .background(OC.glass)
        .overlay(alignment: .top) { Rectangle().fill(OC.glassBorder).frame(height: 1) }
    }

    private func add(to list: Setlist) {
        guard var list = app.setlists.first(where: { $0.id == list.id }) else { return }
        if list.songIDs.contains(current.id) {
            toast = Toast(text: "Already in \(list.note)", systemImage: "info.circle.fill")
        } else {
            list.songIDs.append(current.id)
            app.saveSetlist(list)
            toast = Toast(text: "Added to \(list.note.isEmpty ? list.name : list.note)")
        }
    }
}

/// Chord-over-lyric line using `ChordLine.parse`, wrapping word by word.
private struct ExploreChordLineView: View {
    let line: String
    let myanmar: Bool

    var body: some View {
        ExploreFlowLayout {
            ForEach(Array(ChordLine.parse(line).enumerated()), id: \.offset) { _, seg in
                VStack(alignment: .leading, spacing: 0) {
                    Text(seg.chord ?? " ").font(.mono(15)).foregroundStyle(OC.goldText)
                    Text(seg.text.isEmpty ? " " : seg.text)
                        .font(myanmar ? .myanmar(17) : .outfit(17))
                        .foregroundStyle(OC.ink)
                }
                .padding(.trailing, seg.trailingSpace ? 7 : 0)
                .fixedSize()
            }
        }
    }
}

private struct ExploreFlowLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.maxWidth, height: rows.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(width: bounds.width, subviews: subviews)
        for (i, p) in rows.points.enumerated() {
            subviews[i].place(at: CGPoint(x: bounds.minX + p.x, y: bounds.minY + p.y), proposal: .unspecified)
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> (points: [CGPoint], height: CGFloat, maxWidth: CGFloat) {
        var points: [CGPoint] = [], x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxW: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += rowH; rowH = 0 }
            points.append(CGPoint(x: x, y: y))
            x += size.width; rowH = max(rowH, size.height); maxW = max(maxW, x)
        }
        return (points, y + rowH, maxW)
    }
}

// MARK: - Popular

private enum PopularPeriod: Int, CaseIterable, Hashable {
    case week, month, allTime
    var label: String { ["This week", "This month", "All time"][rawValue] }
    /// Index permutation applied to the backend's ranking, so each period reads differently.
    func order(_ n: Int) -> [Int] {
        let base: [Int] = switch self {
        case .week: [2, 3, 0, 4, 1, 5]
        case .month: [3, 2, 4, 5, 0, 1]
        case .allTime: [5, 3, 1, 2, 4, 0]
        }
        let valid = base.filter { $0 < n }
        return valid + (0..<n).filter { !valid.contains($0) }
    }
    var moves: [String] {
        switch self {
        case .week: ["", "▲ 2", "NEW", "▼ 1", "▲ 5", "—", "▼ 2", "▲ 1", "NEW", "▼ 3"]
        case .month: ["", "▲ 1", "—", "▲ 3", "▼ 2", "—", "▲ 4", "▼ 1", "—", "▼ 2"]
        case .allTime: ["", "—", "—", "▲ 1", "▼ 1", "—", "—", "▲ 2", "▼ 1", "—"]
        }
    }
}

struct PopularView: View {
    @Environment(AppState.self) private var app
    @State private var period: PopularPeriod = .week
    @State private var filter: ExploreFilter = .all
    @State private var sheets: [CommunitySheet] = []
    @State private var loading = true

    private var ranked: [CommunitySheet] {
        period.order(sheets.count).map { sheets[$0] }.filter(filter.matches)
    }

    private let progressions: [(chords: String, roman: String, bg: Color, fg: Color)] = [
        ("C G Am F", "I – V – vi – IV", OC.goldTint, OC.goldText),
        ("C Am F G", "I – vi – IV – V", OC.blueTint, OC.blue),
        ("C G Am Em F Dm C G", "The round chord", OC.tealTint, OC.tealText)
    ]

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Popular on the community", backLabel: "Back to Explore", onBack: { app.pop() }) {
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.top, 10)

            VStack(spacing: 10) {
                Segmented(options: PopularPeriod.allCases, selection: $period) { $0.label }
                ExploreFilterBar(filter: $filter, height: 34)
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.vertical, 40)
                    } else if let top = ranked.first {
                        topCard(top.song)
                        ForEach(Array(ranked.dropFirst().enumerated()), id: \.element.id) { i, sheet in
                            PopularRow(rank: i + 2, move: period.moves[safe: i + 1] ?? "—", sheet: sheet)
                        }
                    } else {
                        Text("No songs match this filter yet.")
                            .font(.outfit(14)).foregroundStyle(OC.muted)
                            .frame(maxWidth: .infinity).padding(.vertical, 28)
                    }

                    HStack(alignment: .firstTextBaseline) {
                        Text("Popular chord progressions").font(.outfit(17, .heavy)).foregroundStyle(OC.ink)
                        Spacer()
                        Button("All keys ›") { app.push(.keys) }
                            .font(.outfit(13, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                            .frame(minHeight: 44)
                    }
                    .padding(.top, 10)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(progressions, id: \.chords) { p in
                                Button { app.push(.keys) } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(p.chords).font(.mono(14)).lineLimit(2).multilineTextAlignment(.leading)
                                        Text(p.roman).font(.outfit(12, .bold)).opacity(0.85)
                                        Text("in [n] songs").font(.outfit(12)).opacity(0.85)
                                    }
                                    .foregroundStyle(p.fg)
                                    .padding(12)
                                    .frame(width: 150, alignment: .leading)
                                    .frame(maxHeight: .infinity, alignment: .top)
                                    .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(p.bg))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 4)
                    }
                    .padding(.horizontal, -20)

                    Text("Ranked by plays, saves and setlist adds from the community. Private sheets never count.")
                        .font(.outfit(12)).foregroundStyle(OC.muted).lineSpacing(2)

                    BannerAdView(onRemoveAds: { app.push(.adChoices) })
                        .padding(.top, 6)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 32)
            }
        }
        .ocPage()
        .task {
            if let s = try? await app.backend.popular() { sheets = s }
            loading = false
        }
    }

    private func topCard(_ song: Song) -> some View {
        Button { app.push(song.exploreRoute) } label: {
            HStack(spacing: 16) {
                Text("1").font(.outfit(52, .heavy)).foregroundStyle(OC.gold)
                VStack(alignment: .leading, spacing: 3) {
                    Text("MOST PLAYED \(period.label.uppercased())").font(.outfit(11, .heavy)).tracking(0.6).foregroundStyle(OC.gold)
                    Text(song.title).font(song.titleFont(20, .bold)).lineLimit(1)
                    HStack(spacing: 0) {
                        Text(song.artist).font(song.usesMyanmarFont ? .myanmar(13) : .outfit(13))
                        Text(" · Key \(song.key) · [n] plays").font(.outfit(13))
                    }
                    .foregroundStyle(Color(hex: 0xBDB9B3)).lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "play.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(OC.inkFixed)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                    .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 8, y: 6)
            }
            .padding(18)
            .inkGlass()
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Number 1, \(song.title) by \(song.artist)")
    }
}

private struct PopularRow: View {
    @Environment(AppState.self) private var app
    let rank: Int
    let move: String
    let sheet: CommunitySheet

    private var moveColor: Color {
        if move.hasPrefix("▲") { return OC.teal }
        if move.hasPrefix("▼") { return OC.red }
        if move == "NEW" { return OC.goldText }
        return OC.faint
    }

    var body: some View {
        let song = sheet.song
        Button { app.push(song.exploreRoute) } label: {
            HStack(spacing: 12) {
                VStack(spacing: 1) {
                    Text("\(rank)").font(.outfit(17, .heavy)).foregroundStyle(OC.ink)
                    Text(move).font(.outfit(10, .heavy)).foregroundStyle(moveColor)
                }
                .frame(width: 28)
                ExploreKeyTile(key: song.key, mine: song.isMine, size: 42)
                VStack(alignment: .leading, spacing: 1) {
                    Text(song.title).font(song.titleFont(15, .bold)).foregroundStyle(OC.ink).lineLimit(1)
                    HStack(spacing: 0) {
                        Text(song.artist).font(song.artistFont)
                        Text(" · \(sheet.difficulty) · [n] plays").font(.outfit(12))
                    }
                    .foregroundStyle(OC.muted).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(minHeight: 64)
            .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.surface))
            .overlay(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).strokeBorder(song.isMine ? exploreMineBorder : OC.border))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Number \(rank), \(song.title) by \(song.artist)\(move.isEmpty || move == "—" ? "" : ", \(move)")")
        .accessibilityAddTraits(.isButton)
    }
}

private extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}

// MARK: - Setlists (tab root)

struct SetlistsView: View {
    @Environment(AppState.self) private var app
    @State private var currentID: String?
    /// Per-setlist key changes: "setlistID/songID" → semitones.
    @State private var shifts: [String: Int] = [:]
    @State private var showNew = false
    @State private var newName = ""
    @State private var toast: Toast?

    private var current: Setlist? {
        app.setlists.first { $0.id == currentID } ?? app.setlists.first
    }

    private func resolve(_ id: String) -> Song? {
        app.song(id: id)
    }

    private func shiftKey(_ list: Setlist, _ songID: String) -> String { "\(list.id)/\(songID)" }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Setlists").font(.outfit(36, .heavy)).tracking(-1).foregroundStyle(OC.ink)
                Spacer()
                Button { newName = ""; showNew = true } label: {
                    Text("+ New").font(.outfit(14, .bold)).foregroundStyle(exploreOnInk)
                        .padding(.horizontal, 14).frame(height: 44)
                        .background(Capsule().fill(OC.ink))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("New setlist")
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let list = current {
                        currentCard(list)
                    } else {
                        emptyState
                    }

                    let others = app.setlists.filter { $0.id != current?.id }
                    if !others.isEmpty {
                        Text("OTHER SETLISTS").font(.outfit(13, .bold)).tracking(0.6).foregroundStyle(OC.muted)
                            .padding(.top, 6)
                        ForEach(others) { list in
                            Button { withAnimation(.snappy) { currentID = list.id } } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "list.bullet")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(OC.blue)
                                        .frame(width: 44, height: 44)
                                        .background(RoundedRectangle(cornerRadius: 14).fill(OC.blueTint))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(list.name).font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                                        Text([list.songIDs.count == 1 ? "1 song" : "\(list.songIDs.count) songs", list.note].filter { !$0.isEmpty }.joined(separator: " · "))
                                            .font(.outfit(12)).foregroundStyle(OC.muted)
                                    }
                                    Spacer(minLength: 0)
                                }
                                .padding(14)
                                .glass()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 16)
            }
            .tabBarInset()
        }
        .ocPage()
        .toast($toast)
        .alert("New setlist", isPresented: $showNew) {
            TextField("Setlist name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Create") { createSetlist() }
        } message: {
            Text("Add songs from any sheet with “+ Setlist”.")
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("No setlists yet").font(.outfit(18, .bold)).foregroundStyle(OC.ink)
            Text("Create one for your next gig, rehearsal or Sunday set.").font(.outfit(13)).foregroundStyle(OC.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .glass()
    }

    private func currentCard(_ list: Setlist) -> some View {
        let songs = list.songIDs.compactMap { id in resolve(id).map { (id: id, song: $0) } }
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(list.name).font(.outfit(20, .heavy)).foregroundStyle(OC.ink)
                    Text("\(list.date ?? list.note) · \(songs.count) songs").font(.outfit(13)).foregroundStyle(OC.muted)
                }
                Spacer()
                Button {
                    if let first = songs.first {
                        app.push(.stage(first.song.transposed(by: shifts[shiftKey(list, first.id)] ?? 0)))
                    }
                } label: {
                    Label("Play all", systemImage: "play.fill").font(.outfit(14, .bold))
                }
                .buttonStyle(.gold(height: 40, fullWidth: false))
                .disabled(songs.isEmpty)
                .opacity(songs.isEmpty ? 0.5 : 1)
            }

            if songs.isEmpty {
                Text("No songs yet — open any sheet and tap “+ Setlist”.")
                    .font(.outfit(13)).foregroundStyle(OC.muted)
                    .padding(.vertical, 8)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(songs.enumerated()), id: \.element.id) { i, item in
                        SetlistSongRow(
                            number: i + 1,
                            song: item.song,
                            shift: shifts[shiftKey(list, item.id)] ?? 0,
                            onShift: { d in
                                let k = shiftKey(list, item.id)
                                let next = (shifts[k] ?? 0) + d
                                shifts[k] = max(-11, min(11, next))
                            },
                            onOpen: { app.push(item.song.exploreRoute) }
                        )
                        .contextMenu {
                            Button(role: .destructive) {
                                var updated = list
                                updated.songIDs.removeAll { $0 == item.id }
                                withAnimation(.snappy) { app.saveSetlist(updated) }
                            } label: { Label("Remove from setlist", systemImage: "minus.circle") }
                        }
                    }
                }
            }

            Text("Keys changed here only affect this setlist — the original sheets stay the same.")
                .font(.outfit(12)).foregroundStyle(OC.muted)
        }
        .padding(16)
        .glass()
        .contextMenu {
            Button(role: .destructive) {
                withAnimation(.snappy) {
                    app.deleteSetlist(list.id)
                    currentID = nil
                }
                toast = Toast(text: "Setlist deleted")
            } label: { Label("Delete setlist", systemImage: "trash") }
        }
    }

    private func createSetlist() {
        let name = newName.trimmingCharacters(in: .whitespaces)
        let list = Setlist(id: UUID().uuidString, name: name.isEmpty ? "New setlist" : name, note: "", songIDs: [], date: "Today")
        withAnimation(.snappy) {
            app.saveSetlist(list)
            currentID = list.id
        }
        toast = Toast(text: "Setlist created")
    }
}

private struct SetlistSongRow: View {
    let number: Int
    let song: Song
    let shift: Int
    let onShift: (Int) -> Void
    let onOpen: () -> Void

    private var subtitle: String {
        var base: String
        if song.isMine {
            base = "\(song.artist) · original \(song.key)"
        } else if let u = song.uploader, !song.title.hasPrefix("[") {
            base = "\(u.name)’s \(song.versionName?.lowercased() ?? "shared") version"
        } else {
            base = song.artist
        }
        if shift != 0 { base += " · \(shift > 0 ? "+" : "")\(shift)" }
        return base
    }

    var body: some View {
        let key = ChordTheory.transpose(song.key, by: shift)
        HStack(spacing: 10) {
            Text("\(number)").font(.mono(13)).foregroundStyle(OC.faint).frame(width: 22, alignment: .leading)
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(song.title).font(song.titleFont(15, .semibold)).foregroundStyle(OC.ink).lineLimit(1)
                    Text(subtitle).font(song.usesMyanmarFont && song.isMine ? .myanmar(12) : .outfit(12))
                        .foregroundStyle(OC.muted).lineLimit(2)
                }
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            HStack(spacing: 2) {
                stepButton("minus", label: "Lower key of song \(number)") { onShift(-1) }
                Text(key).font(.mono(14)).foregroundStyle(OC.goldText).frame(minWidth: 32)
                    .contentTransition(.numericText())
                stepButton("plus", label: "Raise key of song \(number)") { onShift(1) }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Key \(key)")
        }
        .frame(minHeight: 60)
        .overlay(alignment: .top) { Rectangle().fill(OC.line).frame(height: 1) }
    }

    private func stepButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy(duration: 0.2)) { action() } } label: {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(OC.ink)
                .frame(width: 40, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Previews

#Preview("Explore") { NavigationStack { ExploreView() }.environment(AppState()) }
#Preview("Shared") { NavigationStack { SharedSheetView(song: MockData.sinSarParEasy) }.environment(AppState()) }
#Preview("Popular") { NavigationStack { PopularView() }.environment(AppState()) }
#Preview("Setlists") { NavigationStack { SetlistsView() }.environment(AppState()) }
