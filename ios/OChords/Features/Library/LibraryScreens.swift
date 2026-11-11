import SwiftUI
import PhotosUI

// MARK: - Shared helpers (library group)

/// Whether a scan can start: free users out of scans get the rewarded-ad offer instead.
@MainActor
private func libraryCanScan(_ app: AppState) -> Bool {
    guard app.freeScansLeft <= 0 && !app.isPro else { return true }
    app.modal = .reward(.scan)
    return false
}

/// Scan entry point: the camera.
@MainActor
private func libraryStartScan(_ app: AppState) {
    if libraryCanScan(app) { app.push(.importSheet) }
}

/// The skeleton is shown once per launch, on the Library's first appearance.
@MainActor
private enum LibraryLoadOnce { static var done = false }

/// Gold-tint key tile ("C", "G/B"…).
private struct LibraryKeyTile: View {
    let key: String
    var size: CGFloat = 52
    var fill: Color = OC.goldTint
    var fg: Color = OC.goldText
    var body: some View {
        Text(key)
            .font(.mono(key.count > 2 ? size * 0.3 : size * 0.38))
            .foregroundStyle(fg)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(fill))
    }
}

/// Song title in Myanmar or Outfit depending on content.
private struct LibrarySongTitle: View {
    let song: Song
    var size: CGFloat = 17
    var body: some View {
        Text(song.title)
            .font(song.isMyanmar && !song.title.hasPrefix("[") ? .myanmar(size, .semibold) : .outfit(size, .semibold))
            .foregroundStyle(OC.ink)
            .lineLimit(1)
    }
}

/// Teal switch used on Profile (matches the design's #0F766E knob switch).
private struct LibraryTealSwitch: View {
    @Binding var isOn: Bool
    let label: String
    var body: some View {
        Button { withAnimation(.snappy(duration: 0.2)) { isOn.toggle() } } label: {
            Capsule()
                .fill(isOn ? OC.teal : OC.switchOff)
                .frame(width: 52, height: 32)
                .overlay(alignment: isOn ? .trailing : .leading) {
                    Circle().fill(.white).frame(width: 26, height: 26).padding(3)
                        .shadow(color: .black.opacity(0.2), radius: 3, y: 2)
                }
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(isOn ? "On" : "Off")
        .accessibilityAddTraits(.isToggle)
    }
}

/// Small shield badge with the tier's roman numeral (design: "III" / "IV" inside the shield).
private struct LibraryTierShield: View {
    let tier: Tier
    let fill: Color
    let numeral: Color
    var body: some View {
        Image(systemName: "shield.fill")
            .font(.system(size: 19))
            .foregroundStyle(fill)
            .overlay {
                Text(tier.roman).font(.outfit(7, .heavy)).foregroundStyle(numeral).offset(y: -1)
            }
            .accessibilityHidden(true)
    }
}

private struct LibrarySectionLabel: View {
    let text: String
    var body: some View {
        Text(text).font(.outfit(13, .bold)).tracking(0.6).foregroundStyle(OC.muted)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Library (tab root)

struct LibraryView: View {
    @Environment(AppState.self) private var app
    @State private var query = ""
    @State private var isLoading = !LibraryLoadOnce.done
    @State private var showAll = false
    @State private var toast: Toast?
    /// Adding a song from a photo or file already on the phone, without the camera.
    @State private var showPhotos = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showFiles = false

    private var profile: Profile { app.displayProfile }

    private var filtered: [Song] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return app.library }
        return app.library.filter {
            $0.title.localizedCaseInsensitiveContains(q) || $0.artist.localizedCaseInsensitiveContains(q) || $0.key.localizedCaseInsensitiveContains(q)
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                if isLoading {
                    LibrarySkeleton().padding(.horizontal, 20).padding(.top, 20)
                } else if app.library.isEmpty {
                    LibraryEmptyState(onScan: { libraryStartScan(app) }, onPhotos: pickPhoto, onBrowse: { app.switchTo(.explore) })
                        .padding(.horizontal, 28).padding(.top, 36)
                } else if app.isOffline {
                    offlineContent
                } else {
                    onlineContent
                }
            }
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .tabBarInset()
        .ocPage()
        .toast($toast)
        .onAppear(perform: showRescanNotice)
        .onChange(of: app.rescanNotice) { _, _ in showRescanNotice() }
        .photosPicker(isPresented: $showPhotos, selection: $photoItem, matching: .images)
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.pdf, .image]) { picked in
            guard case .success(let url) = picked else { return }
            guard let source = ScanSource.file(url) else {
                toast = Toast(text: "Couldn’t open that file", systemImage: "exclamationmark.triangle")
                return
            }
            app.push(.importPicked(source))
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                photoItem = nil
                guard let data else {
                    toast = Toast(text: "Couldn’t open that photo", systemImage: "exclamationmark.triangle")
                    return
                }
                app.push(.importPicked(.image(data)))
            }
        }
        .task {
            guard isLoading else { return }
            try? await Task.sleep(for: .milliseconds(900))
            LibraryLoadOnce.done = true
            withAnimation(.easeOut(duration: 0.25)) { isLoading = false }
        }
    }

    private func showRescanNotice() {
        guard let notice = app.rescanNotice else { return }
        toast = Toast(text: notice, systemImage: "sparkles")
        app.rescanNotice = nil
    }

    /// Add a song from a photo already on the phone — straight to reading it, no camera.
    private func pickPhoto() { if libraryCanScan(app) { showPhotos = true } }

    /// Add a song from a PDF or image in Files.
    private func pickFile() { if libraryCanScan(app) { showFiles = true } }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Wordmark(size: 30)
                Spacer()
                HStack(spacing: 8) {
                    Button { app.push(.streak) } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "waveform").font(.system(size: 15, weight: .bold)).foregroundStyle(OC.goldText)
                            Text("\(profile.grooveDays)").font(.outfit(16, .heavy)).foregroundStyle(OC.ink)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 44)
                        .glass(radius: 22)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Groove: \(profile.grooveDays) days")

                    CircleIconButton(systemName: "bell", label: app.unreadNotifications > 0 ? "Notifications, \(app.unreadNotifications) new" : "Notifications", badge: app.unreadNotifications) {
                        app.push(.notifications)
                    }
                    CircleIconButton(systemName: "person", label: "Profile") { app.push(.profile) }
                }
            }

            Text("Library").font(.outfit(36, .heavy)).tracking(-1).foregroundStyle(OC.ink)

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").font(.system(size: 16, weight: .semibold)).foregroundStyle(OC.muted)
                TextField("Songs, artists, သီချင်း…", text: $query)
                    .font(.outfit(16))
                    .foregroundStyle(OC.ink)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                    .accessibilityLabel("Search songs")
                if !query.isEmpty {
                    Button { query = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(OC.faint).frame(width: 32, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 46)
            .glass(radius: 14)
        }
    }

    // MARK: Online

    private var onlineContent: some View {
        VStack(spacing: 0) {
            if query.isEmpty {
                LibraryAddSongCard(
                    onScan: { libraryStartScan(app) },
                    onPhotos: pickPhoto,
                    onListen: { app.push(.listen) },
                    onFiles: pickFile
                )
                .padding(.horizontal, 20).padding(.top, 20)

                BannerAdView(onRemoveAds: { app.push(.adChoices) })
                    .padding(.horizontal, 20).padding(.top, 12)
            }

            VStack(spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text(query.isEmpty ? "Recently added" : "Results").font(.outfit(20, .bold)).foregroundStyle(OC.ink)
                    Spacer()
                    if query.isEmpty {
                        Button(showAll && app.library.count > 4 ? "Show less" : "See all") {
                            if app.library.count > 4 {
                                withAnimation(.snappy) { showAll.toggle() }
                            } else {
                                toast = Toast(text: "That’s your whole library", systemImage: "music.note.list")
                            }
                        }
                            .font(.outfit(14, .semibold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                            .frame(minHeight: 44)
                    }
                }

                let songs = query.isEmpty && !showAll ? Array(filtered.prefix(4)) : filtered
                if songs.isEmpty {
                    Text("No songs match “\(query)”.")
                        .font(.outfit(15)).foregroundStyle(OC.muted)
                        .frame(maxWidth: .infinity).padding(.vertical, 30)
                } else {
                    ForEach(songs) { song in
                        LibrarySwipeRow(onTap: { app.push(.song(song)) }, onDelete: { remove(song) }) {
                            LibrarySongRowContent(song: song)
                        }
                        .transition(.asymmetric(insertion: .opacity, removal: .move(edge: .leading).combined(with: .opacity)))
                    }
                }
            }
            .padding(.horizontal, 20).padding(.top, 18)
        }
    }

    private func remove(_ song: Song) {
        withAnimation(.snappy) { app.removeFromLibrary(song.id) }
        Task { try? await app.backend.delete(songID: song.id) }
        toast = Toast(text: "Removed from library", systemImage: "trash.fill")
    }

    // MARK: Offline

    private var offlineContent: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "wifi.slash").font(.system(size: 20, weight: .semibold)).foregroundStyle(OC.gold)
                VStack(alignment: .leading, spacing: 1) {
                    Text("You’re offline").font(.outfit(15, .bold))
                    Text("\(app.library.count) downloaded \(app.library.count == 1 ? "song" : "songs"), tuner and chord guide still work.")
                        .font(.outfit(13)).foregroundStyle(Color(hex: 0xBDB9B3))
                }
                Spacer(minLength: 4)
                Button("Retry") {
                    withAnimation { app.isOffline = false }
                }
                .font(.outfit(13, .bold)).foregroundStyle(OC.paper)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.12)).frame(height: 36))
                .buttonStyle(.plain)
            }
            .foregroundStyle(OC.paper)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(OC.inkGlass))
            .padding(.top, 16)

            HStack(alignment: .firstTextBaseline) {
                Text("Available offline").font(.outfit(20, .bold))
                Spacer()
                Text("\(filtered.count) \(filtered.count == 1 ? "song" : "songs") · \(String(format: "%.1f", Double(filtered.count) * 0.34)) MB")
                    .font(.outfit(13)).foregroundStyle(OC.muted)
            }
            .foregroundStyle(OC.ink)
            .padding(.top, 10)

            ForEach(filtered) { song in
                Button { app.push(.song(song)) } label: {
                    LibraryOfflineRow(title: song.title, isMyanmar: song.isMyanmar, sub: "\(song.artist) · Key \(song.key)", key: song.key, available: true)
                }
                .buttonStyle(.plain)
            }

            Text("Not downloaded").font(.outfit(20, .bold)).foregroundStyle(OC.ink).padding(.top, 12)
            LibraryOfflineRow(title: "[Song title]", isMyanmar: false, sub: "From Explore · Key Am", key: "Am", available: false)
                .opacity(0.45)
                .accessibilityHint("Needs internet")

            Text("Changes you make offline sync when you’re back online.")
                .font(.outfit(13)).foregroundStyle(OC.muted).padding(.top, 2)
        }
        .padding(.horizontal, 20)
    }
}

private struct LibraryAddSongCard: View {
    let onScan: () -> Void
    let onPhotos: () -> Void
    let onListen: () -> Void
    let onFiles: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: "text.viewfinder")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(OC.gold)
                    .frame(width: 44, height: 44)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0x2A2825)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Add a song").font(.outfit(19, .bold))
                    Text("Photo, PDF — or listen to a song").font(.outfit(13)).foregroundStyle(Color(hex: 0xBDB9B3))
                }
            }
            Text("Scan a handwritten or printed sheet, or let us listen to a song playing nearby and estimate its chords.")
                .font(.outfit(14)).lineSpacing(3).foregroundStyle(Color(hex: 0xD6D4CF))
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Button(action: onScan) { Label("Scan", systemImage: "camera") }
                    .buttonStyle(.gold(height: 48))
                Button(action: onPhotos) {
                    Label("Photos", systemImage: "photo.on.rectangle")
                        .font(.outfit(15, .semibold)).foregroundStyle(OC.paper)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color(hex: 0x4A4743)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Choose a sheet from Photos")
                iconButton("mic", label: "Listen to a song", action: onListen)
                iconButton("doc", label: "Open a PDF or image from Files", action: onFiles)
            }
        }
        .padding(20)
        .inkGlass()
    }

    private func iconButton(_ systemImage: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold)).foregroundStyle(OC.paper)
                .frame(width: 48, height: 48)
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color(hex: 0x4A4743)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct LibrarySongRowContent: View {
    let song: Song
    var body: some View {
        HStack(spacing: 14) {
            LibraryKeyTile(key: song.key)
            VStack(alignment: .leading, spacing: 2) {
                LibrarySongTitle(song: song)
                HStack(spacing: 0) {
                    Text(song.artist).font(song.isMyanmar ? .myanmar(13) : .outfit(13))
                    Text(" · Key \(song.key) · \(song.sectionCount) sections").font(.outfit(13))
                }
                .foregroundStyle(OC.muted)
                .lineLimit(1)
            }
            Spacer(minLength: 6)
            StatusPill(text: song.source.label, tone: song.source == .imported ? .teal : (song.source == .community ? .gold : .blue))
        }
        .padding(12)
        .glass()
    }
}

/// Swipe left to reveal a delete action; tap opens the song.
private struct LibrarySwipeRow<Content: View>: View {
    let onTap: () -> Void
    let onDelete: () -> Void
    @ViewBuilder var content: () -> Content
    @State private var offset: CGFloat = 0
    @State private var dragStart: CGFloat = 0
    private let revealWidth: CGFloat = 84

    var body: some View {
        ZStack(alignment: .trailing) {
            if offset < 0 {
                Button(action: onDelete) {
                    VStack(spacing: 4) {
                        Image(systemName: "trash").font(.system(size: 18, weight: .semibold))
                        Text("Remove").font(.outfit(12, .bold))
                    }
                    .foregroundStyle(.white)
                    .frame(width: revealWidth - 8)
                    .frame(maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.red))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove from library")
            }
            Button {
                if offset != 0 { close() } else { onTap() }
            } label: { content() }
                .buttonStyle(.plain)
                .offset(x: offset)
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 20)
                .onChanged { v in
                    guard abs(v.translation.width) > abs(v.translation.height) else { return }
                    offset = min(0, max(-revealWidth - 30, dragStart + v.translation.width))
                }
                .onEnded { v in
                    withAnimation(.snappy) {
                        offset = (dragStart + v.translation.width) < -revealWidth / 2 ? -revealWidth : 0
                    }
                    dragStart = offset
                }
        )
        .contextMenu {
            Button(role: .destructive, action: onDelete) { Label("Remove from library", systemImage: "trash") }
        }
        .accessibilityAction(named: "Remove from library", onDelete)
    }

    private func close() { withAnimation(.snappy) { offset = 0 }; dragStart = 0 }
}

private struct LibraryOfflineRow: View {
    let title: String
    let isMyanmar: Bool
    let sub: String
    let key: String
    let available: Bool

    var body: some View {
        HStack(spacing: 14) {
            LibraryKeyTile(key: key, size: 48)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(isMyanmar ? .myanmar(16, .semibold) : .outfit(16, .semibold)).foregroundStyle(OC.ink).lineLimit(1)
                Text(sub).font(.outfit(13)).foregroundStyle(OC.muted).lineLimit(1)
            }
            Spacer(minLength: 6)
            if available {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark").font(.system(size: 10, weight: .heavy))
                    Text("On device")
                }
                .font(.outfit(12, .bold)).foregroundStyle(OC.tealText)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Capsule().fill(OC.tealTint))
            } else {
                StatusPill(text: "Needs internet", tone: .neutral).fixedSize()
            }
        }
        .padding(12)
        .glass()
    }
}

private struct LibraryEmptyState: View {
    let onScan: () -> Void
    let onPhotos: () -> Void
    let onBrowse: () -> Void

    var body: some View {
        VStack(spacing: 22) {
            illustration
            VStack(spacing: 8) {
                Text("Your library is empty").font(.outfit(26, .bold)).tracking(-0.6).foregroundStyle(OC.ink)
                Text("Snap a photo of any chord sheet — printed or handwritten — and we’ll turn it into a clean, playable song.")
                    .font(.outfit(15)).lineSpacing(3).foregroundStyle(OC.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            VStack(spacing: 10) {
                Button(action: onScan) { Label("Scan your first sheet", systemImage: "camera") }
                    .buttonStyle(.gold)
                Button(action: onPhotos) { Label("Choose from Photos", systemImage: "photo.on.rectangle") }
                    .buttonStyle(GlassButtonStyle(height: 54))
                Button("Browse community chords", action: onBrowse)
                    .buttonStyle(GlassButtonStyle(height: 54))
            }
            HStack(spacing: 16) {
                Text("✓ Myanmar & English")
                Text("✓ Works offline")
            }
            .font(.outfit(13)).foregroundStyle(OC.muted)
        }
    }

    private var illustration: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(OC.glass)
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(OC.glassBorder))
                .frame(width: 112, height: 120)
                .rotationEffect(.degrees(-9))
                .offset(x: 16, y: 28)
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 10) { Text("C"); Text("G/B") }.font(.mono(13)).foregroundStyle(OC.goldText)
                Capsule().fill(OC.softFill).frame(width: 82, height: 6)
                HStack(spacing: 10) { Text("Am"); Text("F") }.font(.mono(13)).foregroundStyle(OC.goldText)
                Capsule().fill(OC.softFill).frame(width: 64, height: 6)
                Capsule().fill(OC.softFill).frame(width: 76, height: 6)
            }
            .padding(.horizontal, 14).padding(.vertical, 16)
            .frame(width: 118, height: 132, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(OC.surface.opacity(0.85)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(OC.glassBorder))
            .shadow(color: .black.opacity(0.08), radius: 14, y: 10)
            .offset(x: 64, y: 6)
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(OC.inkFixed)
                .frame(width: 52, height: 52)
                .background(Circle().fill(LinearGradient(colors: [OC.goldTop, OC.goldBottom], startPoint: .top, endPoint: .bottom)))
                .shadow(color: Color(hex: 0xFFAA00).opacity(0.35), radius: 10, y: 8)
                .offset(x: 144, y: 102)
        }
        .frame(width: 200, height: 156, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

private struct LibrarySkeleton: View {
    @State private var phase = false

    var body: some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                Spacer()
                bar(width: 0.55, height: 18)
                bar(width: 0.35, height: 12)
            }
            .padding(18)
            .frame(height: 150)
            .frame(maxWidth: .infinity)
            .glass()

            ForEach(0..<3, id: \.self) { _ in
                HStack(spacing: 14) {
                    RoundedRectangle(cornerRadius: 14).fill(shimmer).frame(width: 52, height: 52)
                    VStack(alignment: .leading, spacing: 8) {
                        bar(width: 0.62, height: 14)
                        bar(width: 0.4, height: 11)
                    }
                }
                .padding(12)
                .glass()
            }

            HStack(spacing: 8) {
                ProgressView().controlSize(.small).tint(OC.gold)
                Text("Loading your library…").font(.outfit(13, .semibold)).foregroundStyle(OC.muted)
            }
            .padding(.top, 2)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) { phase = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading your library")
    }

    private var shimmer: Color { OC.softFill.opacity(phase ? 2.2 : 1.2) }

    private func bar(width: CGFloat, height: CGFloat) -> some View {
        GeometryReader { geo in
            RoundedRectangle(cornerRadius: 8).fill(shimmer).frame(width: geo.size.width * width, height: height)
        }
        .frame(height: height)
    }
}

// MARK: - Notifications

private enum LibraryNotifTab: String, CaseIterable { case all = "All", community = "Community", learning = "Learning" }

private enum LibraryNotifAction { case review, practise, join, playAgain }

/// Presentation details from the design, keyed by notification id.
private struct LibraryNotifStyle {
    var group: String
    var glyph: String
    var bg: Color
    var fg: Color
    var action: (label: String, kind: LibraryNotifAction)? = nil
    var secondary: String? = nil

    /// Look and action follow the notification's icon; the group follows when it happened.
    static func of(_ n: AppNotification) -> LibraryNotifStyle {
        let group = groupName(n)
        switch n.systemImage {
        case "heart.fill": return .init(group: group, glyph: "heart.fill", bg: OC.redTint, fg: OC.red)
        case "pencil": return .init(group: group, glyph: "pencil.line", bg: OC.blueTint, fg: OC.blue, action: ("Review", .review), secondary: "Dismiss")
        case "waveform": return .init(group: group, glyph: "▮▮", bg: OC.goldTint, fg: OC.goldText, action: ("Practise now", .practise))
        case "music.note": return .init(group: group, glyph: "♪", bg: OC.gold, fg: OC.inkFixed, action: ("Join", .join))
        case "shield.fill": return .init(group: group, glyph: "shield.fill", bg: Color(hex: 0x6D28D9, alpha: 0.14), fg: OC.purple)
        case "checkmark": return .init(group: group, glyph: "✓", bg: OC.tealTint, fg: OC.tealText)
        case "bolt.fill": return .init(group: group, glyph: "⚡", bg: OC.goldTint, fg: OC.goldText, action: ("Play again", .playAgain))
        default: return .init(group: group, glyph: n.systemImage.isEmpty ? "•" : n.systemImage, bg: OC.softFill, fg: OC.muted)
        }
    }

    private static func groupName(_ n: AppNotification) -> String {
        if let date = n.createdAt {
            if Calendar.current.isDateInToday(date) { return "TODAY" }
            return date > .now.addingTimeInterval(-7 * 24 * 3600) ? "THIS WEEK" : "EARLIER"
        }
        // Demo data: "5 min ago", "1 h ago" are today.
        return n.time.contains("min") || n.time.contains(" h ") ? "TODAY" : "THIS WEEK"
    }
}

struct NotificationsView: View {
    @Environment(AppState.self) private var app
    @State private var tab: LibraryNotifTab = .all
    @State private var allRead = false
    @State private var hidden: Set<String> = []
    @State private var toast: Toast?

    private var visible: [AppNotification] {
        app.notifications.filter { n in
            guard !hidden.contains(n.id) else { return false }
            switch tab {
            case .all: return true
            case .community: return n.category == .community
            case .learning: return n.category == .learning
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Notifications", backLabel: "Back to library", onBack: { app.pop() }) {
                Button("Mark all read") {
                    withAnimation { allRead = true }
                    app.markAllNotificationsRead()
                }
                .font(.outfit(13, .bold)).foregroundStyle(OC.goldText)
                .padding(.horizontal, 6).frame(minHeight: 44)
                .buttonStyle(.plain)
                .disabled(allRead)
                .opacity(allRead ? 0.5 : 1)
            }

            Segmented(options: LibraryNotifTab.allCases, selection: $tab) { $0.rawValue }
                .padding(.horizontal, 20).padding(.top, 8)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    let items = visible
                    ForEach(Array(items.enumerated()), id: \.element.id) { i, n in
                        let style = LibraryNotifStyle.of(n)
                        if i == 0 || LibraryNotifStyle.of(items[i - 1]).group != style.group {
                            Text(style.group).font(.outfit(12, .bold)).tracking(1).foregroundStyle(OC.muted)
                                .padding(.top, 6)
                        }
                        LibraryNotifCard(
                            n: n, style: style, unread: n.unread && !allRead,
                            onAction: { perform($0) },
                            onDismiss: { withAnimation(.snappy) { _ = hidden.insert(n.id) } }
                        )
                        .transition(.opacity.combined(with: .move(edge: .trailing)))
                    }
                    if items.isEmpty {
                        Text("You’re all caught up.")
                            .font(.outfit(15)).foregroundStyle(OC.muted)
                            .frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                    Button("Notification settings ›") { app.push(.settings) }
                        .font(.outfit(13, .bold)).foregroundStyle(OC.goldText).buttonStyle(.plain)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.top, 6)
                }
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 30)
            }
        }
        .ocPage()
        .toast($toast)
        .onAppear { allRead = app.unreadNotifications == 0 }
        .refreshable { await app.refresh() }
    }

    private func perform(_ action: LibraryNotifAction) {
        switch action {
        case .review: toast = Toast(text: "Fix sent to review · G → G/B", systemImage: "pencil.circle.fill")
        case .practise: app.switchTo(.learn)
        case .join: app.push(.jam(MockData.sinSarPar))
        case .playAgain: app.push(.game)
        }
    }
}

private struct LibraryNotifCard: View {
    let n: AppNotification
    let style: LibraryNotifStyle
    let unread: Bool
    let onAction: (LibraryNotifAction) -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Group {
                if style.glyph.contains(".") {
                    Image(systemName: style.glyph).font(.system(size: 14, weight: .bold))
                } else {
                    Text(style.glyph)
                        .font(.outfit(style.glyph.count > 2 ? 12 : 15, .heavy))
                        .minimumScaleFactor(0.7)
                }
            }
                .foregroundStyle(style.fg)
                .frame(width: 38, height: 38)
                .background(Circle().fill(style.bg))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                (Text(n.title).font(.outfit(14, .bold)) + Text(" ") + Text(n.body).font(.outfit(14)))
                    .foregroundStyle(OC.ink)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                if let action = style.action {
                    HStack(spacing: 6) {
                        Button(action.label) { onAction(action.kind) }
                            .font(.outfit(13, .bold))
                            .foregroundStyle(Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614)))
                            .padding(.horizontal, 12).frame(height: 32)
                            .background(RoundedRectangle(cornerRadius: 10).fill(OC.ink))
                            .frame(minHeight: 44)
                            .buttonStyle(.plain)
                        if let secondary = style.secondary {
                            Button(secondary, action: onDismiss)
                                .font(.outfit(13, .bold)).foregroundStyle(OC.ink)
                                .padding(.horizontal, 12).frame(height: 32)
                                .background(RoundedRectangle(cornerRadius: 10).fill(OC.surface))
                                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(OC.border))
                                .frame(minHeight: 44)
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, -6)
                }
                Text(n.time).font(.outfit(12)).foregroundStyle(OC.muted)
            }
            Spacer(minLength: 0)
            if unread {
                Circle().fill(OC.gold).frame(width: 9, height: 9).padding(.top, 6)
                    .accessibilityLabel("Unread")
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(unread ? Color(light: UIColor(white: 1, alpha: 0.85), dark: UIColor(white: 1, alpha: 0.12))
                             : Color(light: UIColor(white: 1, alpha: 0.5), dark: UIColor(white: 1, alpha: 0.05)))
        )
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(OC.glassBorder))
        .contextMenu {
            Button(role: .destructive, action: onDismiss) { Label("Dismiss", systemImage: "xmark") }
        }
        .accessibilityAction(named: "Dismiss", onDismiss)
    }
}

// MARK: - Profile

struct ProfileView: View {
    @Environment(AppState.self) private var app
    @State private var isPublic = true

    private var profile: Profile { app.displayProfile }

    var body: some View {
        @Bindable var app = app
        VStack(spacing: 0) {
            TopBar(title: "Profile", backLabel: "Back to library", onBack: { app.pop() }) {
                Button { app.push(.settings) } label: {
                    Image(systemName: "gearshape").font(.system(size: 19, weight: .medium)).foregroundStyle(OC.ink)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }

            ScrollView {
                VStack(spacing: 18) {
                    if !app.isSignedIn && !app.usesDemoData { GuestAccountCard() }
                    identity
                    nextTierCard
                    stats
                    if !app.isPro { goPro }
                    uploads
                    preferences(sharp: $app.sharpNames, lefty: $app.leftHanded)
                    previews
                    Button("All settings") { app.push(.settings) }
                        .buttonStyle(GlassButtonStyle(height: 48))
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 32)
            }
        }
        .ocPage()
    }

    private var initial: String {
        let c = profile.user.name.first(where: \.isLetter) ?? "?"
        return String(c).uppercased()
    }

    private var identity: some View {
        HStack(spacing: 14) {
            Text(initial)
                .font(.outfit(28, .heavy)).foregroundStyle(.white)
                .frame(width: 72, height: 72)
                .background(Circle().fill(Color(hex: 0x1E3A8A)))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(profile.user.name).font(.outfit(22, .heavy)).foregroundStyle(OC.ink)
                Text("@\(profile.user.handle) · Guitar").font(.outfit(14)).foregroundStyle(OC.muted)
                Button { app.push(.tiers) } label: {
                    HStack(spacing: 6) {
                        LibraryTierShield(tier: profile.user.tier, fill: Color(hex: profile.user.tier.colorHex), numeral: OC.inkFixed)
                        Text("\(profile.user.tier.title) · \(profile.points.formatted()) pts ›")
                    }
                    .font(.outfit(13, .heavy))
                    .foregroundStyle(Color(light: UIColor(hex: 0x6B4600), dark: UIColor(hex: 0xFFC940)))
                    .padding(.leading, 6).padding(.trailing, 10).frame(height: 30)
                    .background(Capsule().fill(OC.goldTint))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.vertical, -4)
            }
            Spacer(minLength: 0)
        }
    }

    private var nextTierCard: some View {
        let tier = profile.user.tier
        let next = tier.next
        let progress: Double = next.map { Double(profile.points - tier.minPoints) / Double($0.minPoints - tier.minPoints) } ?? 1
        return Button { app.push(.tiers) } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(next.map { "Next tier: \($0.title)" } ?? "Top tier reached")
                    Spacer()
                    if let next {
                        Text("\((next.minPoints - profile.points).formatted()) pts to go").foregroundStyle(OC.gold)
                    }
                }
                .font(.outfit(13, .bold))
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(hex: 0x2A2825))
                        Capsule().fill(OC.gold).frame(width: max(8, geo.size.width * min(max(progress, 0), 1)))
                    }
                }
                .frame(height: 8)
                Text("Learning 860 · Contributing 380 — both count toward your tier")
                    .font(.outfit(12)).foregroundStyle(Color(hex: 0xBDB9B3))
                    .multilineTextAlignment(.leading)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .inkGlass()
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens tiers")
    }

    private var stats: some View {
        HStack(spacing: 8) {
            stat("\(isPublic ? 1 : 0)", "Public uploads")
            stat("[n]", "Followers")
            stat("[n]", "Saves of your sheets")
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.outfit(22, .heavy)).foregroundStyle(OC.ink).contentTransition(.numericText())
            Text(label).font(.outfit(12)).foregroundStyle(OC.muted).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 84, alignment: .topLeading)
        .glass()
        .accessibilityElement(children: .combine)
    }

    private var goPro: some View {
        Button { app.modal = .pro } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Go Pro").font(.outfit(16, .heavy))
                    Text("Offline songs, host jam rooms, unlimited chord checks").font(.outfit(13))
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 4)
                Text("7 days free ›").font(.outfit(13, .heavy)).fixedSize()
            }
            .foregroundStyle(OC.inkFixed)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: OC.rCard, style: .continuous).fill(OC.gold))
        }
        .buttonStyle(.plain)
    }

    private var uploads: some View {
        VStack(spacing: 10) {
            LibrarySectionLabel(text: "YOUR UPLOADS")
            HStack(spacing: 12) {
                Button { app.push(.song(MockData.sinSarPar)) } label: {
                    HStack(spacing: 12) {
                        LibraryKeyTile(key: MockData.sinSarPar.key, size: 48)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(MockData.sinSarPar.title).font(.myanmar(16, .semibold)).foregroundStyle(OC.ink)
                            Text(isPublic ? "Public · visible in Explore" : "Only me")
                                .font(.outfit(12, .bold))
                                .foregroundStyle(isPublic ? OC.tealText : OC.muted)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                LibraryTealSwitch(isOn: $isPublic, label: "Public")
            }
            .padding(12)
            .glass()

            Button { libraryStartScan(app) } label: {
                Text("+ Scan another sheet")
                    .font(.outfit(14, .bold)).foregroundStyle(OC.goldText)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(OC.switchOff))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func preferences(sharp: Binding<Bool>, lefty: Binding<Bool>) -> some View {
        VStack(spacing: 10) {
            LibrarySectionLabel(text: "PLAYING PREFERENCES")
            GlassList {
                HStack {
                    Text("Chord names").font(.outfit(15, .semibold))
                    Spacer()
                    HStack(spacing: 0) {
                        accidental("C♯", on: sharp.wrappedValue) { sharp.wrappedValue = true }
                        accidental("D♭", on: !sharp.wrappedValue) { sharp.wrappedValue = false }
                    }
                    .padding(3)
                    .background(Capsule().fill(OC.softFill))
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel("Chord names")
                }
                .foregroundStyle(OC.ink)
                .padding(.horizontal, 14).frame(minHeight: 56)

                HStack {
                    Text("Left-handed diagrams").font(.outfit(15, .semibold)).foregroundStyle(OC.ink)
                    Spacer()
                    LibraryTealSwitch(isOn: lefty, label: "Left-handed diagrams")
                }
                .padding(.horizontal, 14).frame(minHeight: 56)

                Button { app.switchTo(.tuner) } label: {
                    HStack {
                        Text("Default tuning").font(.outfit(15, .semibold)).foregroundStyle(OC.ink)
                        Spacer()
                        Text("Standard ›").font(.outfit(14, .bold)).foregroundStyle(OC.goldText)
                    }
                    .padding(.horizontal, 14).frame(minHeight: 56).contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Button { app.push(.settings) } label: {
                    HStack {
                        Text("App language").font(.outfit(15, .semibold)).foregroundStyle(OC.ink)
                        Spacer()
                        Text(app.language).font(.myanmar(14)).foregroundStyle(OC.muted)
                        Text("›").font(.outfit(14)).foregroundStyle(OC.muted)
                    }
                    .padding(.horizontal, 14).frame(minHeight: 56).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func accidental(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.snappy(duration: 0.2)) { action() } } label: {
            Text(label).font(.mono(13)).foregroundStyle(OC.ink)
                .padding(.horizontal, 10).frame(height: 34)
                .background(Capsule().fill(on ? OC.surface.opacity(0.8) : .clear))
                .shadow(color: .black.opacity(on ? 0.06 : 0), radius: 3, y: 1)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var previews: some View {
        VStack(spacing: 10) {
            LibrarySectionLabel(text: "WIDGETS & LIVE ACTIVITY PREVIEW")
            GlassList {
                Button { app.push(.widgets) } label: {
                    ListRow(title: "Home Screen widgets", subtitle: "Groove · Chord of the day", systemImage: "square.grid.2x2")
                }
                .buttonStyle(.plain)
                Button { app.push(.liveActivity) } label: {
                    ListRow(title: "Lock Screen Live Activity", subtitle: "Jam room · now / next chord", systemImage: "lock.iphone")
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Another user's profile

struct UserProfileView: View {
    let user: UserSummary
    @Environment(AppState.self) private var app
    @State private var following = false
    @State private var tab = 0
    @State private var showMore = false
    @State private var toast: Toast?

    private struct Item: Identifiable {
        let id: String
        let title: String
        let myanmar: Bool
        let sub: String
        let key: String
        let open: () -> Void
    }

    private var uploads: [Item] {
        let easy = MockData.sinSarParEasy
        let capo = MockData.explore.first { $0.id == "c" }?.song
        let four = MockData.explore.first { $0.id == "e" }?.song
        var out = [Item(id: easy.id, title: easy.title, myanmar: true, sub: "\(easy.artist) · simplified chords", key: easy.key, open: { app.push(.shared(easy)) })]
        if let capo { out.append(Item(id: capo.id, title: capo.title, myanmar: false, sub: "\(capo.artist) · Capo 2", key: capo.key, open: { app.push(.shared(capo)) })) }
        if let four { out.append(Item(id: four.id, title: four.title, myanmar: false, sub: "\(four.artist) · 4 chords", key: four.key, open: { app.push(.shared(four)) })) }
        return out
    }

    private var setlists: [Item] {
        [Item(id: "set", title: "[Setlist name]", myanmar: false, sub: "[n] songs · public setlist", key: "≡", open: { app.switchTo(.setlists) })]
    }

    private var avatar: String {
        user.name.hasPrefix("[") ? "?" : String(user.name.first(where: \.isLetter) ?? "?").uppercased()
    }

    private var tierNote: String {
        switch user.tier {
        case .openString: "new player"
        case .strummer: "shares sheets"
        case .chordKeeper: "trusted uploads"
        case .sessionPlayer: "can approve fixes"
        case .maestro: "helps moderate"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(onBack: { app.pop() }) {
                Button { showMore = true } label: {
                    Image(systemName: "ellipsis").font(.system(size: 18, weight: .semibold)).foregroundStyle(OC.ink)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("More")
            }

            ScrollView {
                VStack(spacing: 0) {
                    header.padding(.horizontal, 20).padding(.top, 4)

                    HStack(spacing: 6) {
                        tabButton("Uploads", 0)
                        tabButton("Setlists", 1)
                    }
                    .padding(.horizontal, 20).padding(.top, 20)

                    VStack(spacing: 10) {
                        ForEach(tab == 0 ? uploads : setlists) { it in
                            Button(action: it.open) {
                                HStack(spacing: 12) {
                                    Text(it.key).font(.mono(17)).foregroundStyle(OC.ink)
                                        .frame(width: 48, height: 48)
                                        .background(RoundedRectangle(cornerRadius: 14).fill(OC.softFill))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(it.title).font(it.myanmar ? .myanmar(16, .semibold) : .outfit(16, .semibold)).foregroundStyle(OC.ink).lineLimit(1)
                                        Text(it.sub).font(it.myanmar ? .myanmar(12) : .outfit(12)).foregroundStyle(OC.muted).lineLimit(1)
                                    }
                                    Spacer(minLength: 6)
                                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(OC.faint)
                                }
                                .padding(12)
                                .glass()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 32)
                }
            }
        }
        .ocPage()
        .toast($toast)
        .confirmationDialog("\(user.name)", isPresented: $showMore, titleVisibility: .visible) {
            Button("Share profile") { toast = Toast(text: "Profile link copied", systemImage: "link") }
            Button("Report user", role: .destructive) { toast = Toast(text: "Thanks — we’ll take a look", systemImage: "flag.fill") }
            Button("Block", role: .destructive) { toast = Toast(text: "\(user.name) blocked", systemImage: "hand.raised.fill") }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            Text(avatar)
                .font(.outfit(34, .heavy)).foregroundStyle(.white)
                .frame(width: 88, height: 88)
                .background(Circle().fill(Color(hex: 0x0F766E)))
                .accessibilityHidden(true)
            VStack(spacing: 2) {
                Text(user.name).font(.outfit(24, .heavy)).foregroundStyle(OC.ink)
                Text("[Instrument] · \(user.city ?? "[City]")").font(.outfit(14)).foregroundStyle(OC.muted)
                Button { app.push(.tiers) } label: {
                    let tierColor = Color(hex: user.tier.colorHex)
                    HStack(spacing: 6) {
                        LibraryTierShield(tier: user.tier, fill: user.tier == .sessionPlayer ? OC.blue : tierColor, numeral: .white)
                        Text("\(user.tier.title) · \(tierNote)")
                    }
                    .font(.outfit(13, .heavy))
                    .foregroundStyle(user.tier == .sessionPlayer ? OC.blue : tierColor)
                    .padding(.leading, 6).padding(.trailing, 10).frame(height: 30)
                    .background(Capsule().fill(user.tier == .sessionPlayer ? OC.blueTint : tierColor.opacity(0.14)))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
            HStack(spacing: 22) {
                count("\(user.sheets)", "uploads")
                count("\(user.followers + (following ? 1 : 0))", "followers")
                count("[n]", "saves")
            }
            Button {
                withAnimation(.snappy) { following.toggle() }
                if following { toast = Toast(text: "Following \(user.name)", systemImage: "person.fill.checkmark") }
            } label: {
                Text(following ? "Following" : "Follow")
                    .font(.outfit(15, .bold))
                    .foregroundStyle(following ? OC.ink : Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614)))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(RoundedRectangle(cornerRadius: 14).fill(following ? OC.glass : OC.ink))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(following ? OC.border : OC.ink))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(following ? .isSelected : [])
            .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
    }

    private func count(_ value: String, _ label: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(value).font(.outfit(17, .heavy)).foregroundStyle(OC.ink).contentTransition(.numericText())
            Text(label).font(.outfit(13)).foregroundStyle(OC.muted)
        }
        .accessibilityElement(children: .combine)
    }

    private func tabButton(_ label: String, _ i: Int) -> some View {
        let on = tab == i
        return Button { withAnimation(.snappy(duration: 0.2)) { tab = i } } label: {
            Text(label).font(.outfit(13, .bold))
                .foregroundStyle(on ? Color(light: UIColor(hex: 0xFAFAF8), dark: UIColor(hex: 0x171614)) : OC.ink)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 14).fill(on ? OC.ink : OC.glass))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(on ? OC.ink : OC.border))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

// MARK: - Device mock pieces (Widgets / Live Activity)

private struct LibraryDeviceBackground: View {
    var topGlow: Color = Color(hex: 0x6B4A1E)
    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient(colors: [Color(hex: 0x2A1F14), Color(hex: 0x14100C)], startPoint: .top, endPoint: .bottom)
                RadialGradient(colors: [topGlow, topGlow.opacity(0)], center: UnitPoint(x: 0.2, y: 0.1), startRadius: 0, endRadius: geo.size.width * 0.85)
                RadialGradient(colors: [Color(hex: 0x3B2A57), Color(hex: 0x3B2A57, alpha: 0)], center: UnitPoint(x: 0.9, y: 0.9), startRadius: 0, endRadius: geo.size.width * 0.8)
            }
        }
        .ignoresSafeArea()
    }
}

/// Fake 9:41 status bar + close button for the device mock screens.
private struct LibraryDeviceChrome: View {
    let onClose: () -> Void
    var showTime = true
    var body: some View {
        ZStack {
            if showTime {
                HStack {
                    Text("9:41").font(.system(size: 15, weight: .semibold))
                    Spacer()
                    HStack(spacing: 5) {
                        Image(systemName: "cellularbars")
                        Image(systemName: "wifi")
                        Image(systemName: "battery.100")
                    }
                    .font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 30)
                .accessibilityHidden(true)
            }
        }
        .frame(height: 44)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .topTrailing) {
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(Color.white.opacity(0.16)))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close preview")
            .padding(.trailing, showTime ? 6 : 14)
            .offset(y: showTime ? 40 : 0)
        }
    }
}

private struct LibraryAppIcon: View {
    let color: Color
    var label: String? = "[App]"
    var isOChords = false
    var body: some View {
        VStack(spacing: 5) {
            Group {
                if isOChords {
                    LogoMark(size: 50, stringColor: OC.inkFixed)
                        .frame(width: 62, height: 62)
                        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.white))
                } else {
                    RoundedRectangle(cornerRadius: 16, style: .continuous).fill(color).frame(width: 62, height: 62)
                }
            }
            if let label {
                Text(label).font(.system(size: 11)).foregroundStyle(.white.opacity(0.9)).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(!isOChords)
        .accessibilityLabel(isOChords ? "O Chords" : "")
    }
}

private struct LibraryWidgetSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.ultraThinMaterial).environment(\.colorScheme, .dark))
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Color(hex: 0x1E1A16, alpha: 0.55)))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color.white.opacity(0.14)))
    }
}

// MARK: - Widgets preview

struct WidgetsPreviewView: View {
    @Environment(AppState.self) private var app
    private var profile: Profile { app.displayProfile }

    private let row2: [UInt32] = [0x3B3530, 0x2E3A4A, 0x44392B, 0x2B3F3A, 0x3A2E4A, 0x4A2E33, 0x2E3A4A, 0x3B3530]
    private let dock: [UInt32] = [0x3B3530, 0x2E3A4A, 0x44392B, 0x2B3F3A]

    var body: some View {
        VStack(spacing: 0) {
            LibraryDeviceChrome(onClose: { app.pop() }, showTime: false)

            ScrollView {
                VStack(spacing: 18) {
                    HStack(alignment: .top, spacing: 18) {
                        grooveWidget
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 18) {
                            LibraryAppIcon(color: .white, label: "O Chords", isOChords: true)
                            LibraryAppIcon(color: Color(hex: 0x3B3530))
                            LibraryAppIcon(color: Color(hex: 0x2E3A4A))
                            LibraryAppIcon(color: Color(hex: 0x3A2E4A))
                        }
                        .frame(maxWidth: .infinity)
                    }
                    chordOfTheDay
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 18) {
                        ForEach(Array(row2.enumerated()), id: \.offset) { _, hex in LibraryAppIcon(color: Color(hex: hex)) }
                    }
                    .padding(.top, 8)
                }
                .padding(.horizontal, 22).padding(.top, 8)
            }
            .scrollBounceBehavior(.basedOnSize)

            HStack {
                ForEach(Array(dock.enumerated()), id: \.offset) { _, hex in LibraryAppIcon(color: Color(hex: hex), label: nil) }
            }
            .frame(height: 92)
            .background(RoundedRectangle(cornerRadius: 32, style: .continuous).fill(Color.white.opacity(0.14)))
            .padding(.horizontal, 12).padding(.bottom, 12)
        }
        .background(LibraryDeviceBackground())
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden()
    }

    private var grooveWidget: some View {
        let heights: [CGFloat] = [14, 26, 20, 34, 22, 40, 8]
        return Button { app.push(.streak) } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("GROOVE").font(.outfit(12, .bold)).foregroundStyle(Color(hex: 0xFFC940))
                    Spacer()
                    RoundedRectangle(cornerRadius: 5).fill(OC.gold).frame(width: 18, height: 18)
                }
                Spacer(minLength: 0)
                (Text("\(profile.grooveDays)").font(.outfit(36, .heavy))
                 + Text(" days").font(.outfit(15, .semibold)).foregroundColor(.white.opacity(0.7)))
                Spacer(minLength: 0)
                HStack(alignment: .bottom, spacing: 5) {
                    ForEach(Array(heights.enumerated()), id: \.offset) { i, h in
                        RoundedRectangle(cornerRadius: 3).fill(i == heights.count - 1 ? Color.white.opacity(0.2) : OC.gold).frame(width: 9, height: h)
                    }
                }
                .frame(height: 40, alignment: .bottom)
                Spacer(minLength: 0)
                Text("\(profile.todayMinutes) of \(profile.dailyGoalMinutes) min today").font(.outfit(12)).foregroundStyle(.white.opacity(0.75))
            }
            .foregroundStyle(.white)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 166)
            .modifier(LibraryWidgetSurface())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("O Chords groove widget, \(profile.grooveDays) days")
    }

    private var chordOfTheDay: some View {
        Button { app.push(.chords(focus: "G/B", songChords: MockData.sinSarPar.usedChords)) } label: {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("CHORD OF THE DAY").font(.outfit(12, .bold)).foregroundStyle(Color(hex: 0xFFC940))
                    Text("G/B").font(.mono(40)).foregroundStyle(OC.gold)
                    HStack(spacing: 0) {
                        Text("Used in 9 lines of ").font(.outfit(13))
                        Text("စဉ်းစားပါ").font(.myanmar(13))
                    }
                    .foregroundStyle(.white.opacity(0.8))
                    Text("Practise 2 min").font(.outfit(12, .heavy)).foregroundStyle(OC.inkFixed)
                        .padding(.horizontal, 12).frame(height: 28)
                        .background(Capsule().fill(OC.gold))
                }
                Spacer(minLength: 0)
                LibraryMiniFretboard()
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(height: 166)
            .modifier(LibraryWidgetSurface())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Chord of the day widget: G/B")
    }
}

/// Tiny G/B diagram from the widget (6 strings × 4 frets, two dots).
private struct LibraryMiniFretboard: View {
    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = 4, top: CGFloat = 4, w: CGFloat = 50, h: CGFloat = 60
            for i in 0..<5 {
                let y = top + CGFloat(i) * 15
                ctx.fill(Path(CGRect(x: left, y: y, width: w, height: i == 0 ? 2 : 1)), with: .color(.white.opacity(i == 0 ? 1 : 0.3)))
            }
            for i in 0..<6 {
                let x = left + CGFloat(i) * 10
                ctx.fill(Path(CGRect(x: x, y: top, width: 1, height: h)), with: .color(.white.opacity(0.55)))
            }
            for (x, y) in [(10.0, 22.0), (50.0, 37.0)] {
                ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 9, height: 9)), with: .color(OC.gold))
            }
        }
        .frame(width: 60, height: 68)
        .accessibilityHidden(true)
    }
}

// MARK: - Live Activity preview

struct LiveActivityPreviewView: View {
    @Environment(AppState.self) private var app
    @State private var progress: CGFloat = 0.42

    private var dateLine: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.dateFormat = "EEEE d MMMM"
        return f.string(from: .now)
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Button { app.push(.jam(MockData.sinSarPar)) } label: {
                    HStack(spacing: 52) {
                        Text("G/B").font(.mono(13)).foregroundStyle(OC.gold)
                        Text("3/8").font(.outfit(12, .bold)).foregroundStyle(.white.opacity(0.8))
                    }
                    .padding(.horizontal, 14).frame(height: 36)
                    .background(Capsule().fill(.black))
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Jam room Live Activity, compact. Now G/B, song 3 of 8")
            }
            .frame(maxWidth: .infinity)
            .overlay(alignment: .trailing) { LibraryDeviceChrome(onClose: { app.pop() }, showTime: false).frame(width: 60) }
            .padding(.top, 8)

            VStack(spacing: 2) {
                Text(dateLine).font(.outfit(18, .semibold)).foregroundStyle(.white.opacity(0.85))
                Text("9:41").font(.outfit(96, .bold)).tracking(-3).foregroundStyle(.white)
            }
            .padding(.top, 30)

            Spacer(minLength: 20)

            VStack(spacing: 10) {
                Button { app.push(.jam(MockData.sinSarPar)) } label: { activityCard }
                    .buttonStyle(.plain)
                    .accessibilityLabel("O Chords Jam room Live Activity. Song 3 of 8, စဉ်းစားပါ, key D, capo 2. Now G/B, next Am.")

                HStack {
                    lockButton("flashlight.off.fill", "Flashlight")
                    Spacer()
                    lockButton("camera.fill", "Camera")
                }
                .padding(.horizontal, 34).padding(.top, 6)
            }
            .padding(.horizontal, 12).padding(.bottom, 24)
        }
        .background(LibraryDeviceBackground(topGlow: Color(hex: 0x4A3A2A)))
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden()
        .task {
            // Gently advance the song progress so the activity feels live.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1.5))
                withAnimation(.linear(duration: 1.5)) { progress = progress >= 0.95 ? 0.1 : progress + 0.03 }
            }
        }
    }

    private var activityCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                LogoMark(size: 24, stringColor: OC.inkFixed)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 9).fill(.white))
                VStack(alignment: .leading, spacing: 0) {
                    Text("Jam room · Sunday set").font(.outfit(13, .bold))
                    Text("4 players · led by [Band leader]").font(.outfit(12)).foregroundStyle(.white.opacity(0.65))
                }
                Spacer()
                HStack(spacing: 5) {
                    Circle().fill(Color(hex: 0xEF4444)).frame(width: 7, height: 7)
                    Text("LIVE").font(.outfit(11, .heavy)).foregroundStyle(Color(hex: 0xFCA5A5))
                }
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Song 3 of 8 · Key D · Capo 2 · Chorus").font(.outfit(12)).foregroundStyle(.white.opacity(0.65))
                    Text("စဉ်းစားပါ").font(.myanmar(20, .semibold))
                }
                Spacer()
                HStack(alignment: .lastTextBaseline, spacing: 10) {
                    VStack(spacing: 0) {
                        Text("NOW").font(.outfit(10, .heavy)).foregroundStyle(.white.opacity(0.6))
                        Text("G/B").font(.mono(30)).foregroundStyle(OC.gold)
                    }
                    VStack(spacing: 0) {
                        Text("NEXT").font(.outfit(10, .heavy)).foregroundStyle(.white.opacity(0.6))
                        Text("Am").font(.mono(20)).foregroundStyle(.white.opacity(0.8))
                    }
                }
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.15))
                    Capsule().fill(OC.gold).frame(width: geo.size.width * progress)
                }
            }
            .frame(height: 4)
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(.ultraThinMaterial).environment(\.colorScheme, .dark))
        .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(Color(hex: 0x14110E, alpha: 0.72)))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(Color.white.opacity(0.12)))
    }

    private func lockButton(_ symbol: String, _ label: String) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 19, weight: .medium)).foregroundStyle(.white)
            .frame(width: 50, height: 50)
            .background(Circle().fill(Color.white.opacity(0.14)))
            .accessibilityLabel(label)
    }
}

// MARK: - Previews

#Preview("Library") { NavigationStack { LibraryView() }.environment(AppState()) }
#Preview("Library · empty") {
    let app = AppState()
    app.library = []
    return NavigationStack { LibraryView() }.environment(app)
}
#Preview("Library · offline") {
    let app = AppState()
    app.isOffline = true
    return NavigationStack { LibraryView() }.environment(app)
}
#Preview("Notifications") { NavigationStack { NotificationsView() }.environment(AppState()) }
#Preview("Profile") { NavigationStack { ProfileView() }.environment(AppState()) }
#Preview("User") { NavigationStack { UserProfileView(user: MockData.uploader) }.environment(AppState()) }
#Preview("Widgets") { NavigationStack { WidgetsPreviewView() }.environment(AppState()) }
#Preview("Live Activity") { NavigationStack { LiveActivityPreviewView() }.environment(AppState()) }
