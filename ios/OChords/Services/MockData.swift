import Foundation

/// Demo content. Text in [brackets] is a placeholder for real data, as on the design canvas.
enum MockData {
    static let me = UserSummary(id: "me", name: "[Your name]", handle: "[username]", tier: .chordKeeper, sheets: 1, followers: 0, city: "Yangon")

    static let profile = Profile(user: me, email: "you@example.com", points: 1240, grooveDays: 7, bestGroove: 12,
                                 dailyGoalMinutes: 10, todayMinutes: 6, week: [12, 8, 15, 10, 4, 11, 6],
                                 restNotes: 1, songsLearned: 3, lessonsDone: 14)

    static let uploader = UserSummary(id: "u1", name: "[username]", handle: "[username]", tier: .sessionPlayer, sheets: 12, followers: 340, city: "[City]")

    /// စဉ်းစားပါ — the hero song used across the design.
    static let sinSarPar = Song(
        id: "sinsarpar", title: "စဉ်းစားပါ", artist: "မေ", key: "C", tempo: 76,
        sections: [
            SongSection(kind: .music, label: "INTRO", note: "4 bars", bars: ["C", "G/B", "Am", "F"]),
            SongSection(kind: .verse, label: "VERSE 1", lines: [
                "[C]ငါ မကြားချင် ခဲ့သော စကား [G/B]မသိချင် ခဲ့ဆုံး [Am]အကြောင်းအရာ နင်ဟာ [F]ရက်စက်စွာ",
                "ပြောခဲ့ပြီ အခုတော့ ငါ့မှာ [C]အိပ်မက်တွေ ပြိုလွင့်ခဲ့ [G/B]ဆောက်တည်ရာမဲ့ [Am]မျက်ရည်",
                "[F]နေနိုင်လွန်းလိုက်တာ မင်းတစက်တောင် လှည့်မကြည့်တော့တာ"
            ]),
            SongSection(kind: .chorus, label: "CHORUS", lines: [
                "[C]လမ်းခွဲဖို့အတွက် မင်းဘက်က [G/B]အကြောင်းပြချက်တို့ သိပ်လွယ်ပ [Am]ငါ့ဘက်ကို",
                "ထည့် မစဉ်းစား [F]အတူတူ [C]ထားရှိခဲ့တဲ့ ကတိတို့က [G/B]အချည်းနှီး မဖြစ်ဖို့ ညှိလို့မရ",
                "[Am]ငါ့ဘက်က အားလုံး ရှုံး[F]နေတာ ဖေး ရေး"
            ]),
            SongSection(kind: .verse, label: "VERSE 2", lines: [
                "[C]နည်းနည်း စဉ်းစားပေးဦး [G/B]ချစ်သူရာ တို့နှစ်ဦး [Am]ပါတ်သက်ခဲ့တဲ့ နေ့ရက်များ [F]မခွဲဘူး",
                "အမြဲတမ်း ပြောတဲ့စကား [C]ချစ်ခဲ့တုန်းက အကောင်းဆုံး [G/B]အကြောင်းအရာများ",
                "ပြန်ပြီး[Am]လွမ်းတတ်ဖို့ [G]နင်သေချာ စဉ်း[F]စား"
            ]),
            SongSection(kind: .music, label: "MUSIC", note: "repeat ×2", bars: ["C", "G/B", "Am", "F"]),
            SongSection(kind: .verse, label: "VERSE 3", lines: [
                "မိုးလင်းထိ [C]ကားလျှောက်မောင်းပြီး သီချင်းအော်ဟစ် ဆိုခဲ့တာတွေ",
                "ဝမ်းနည်းတိုင်း [G/B]ငါ့ရင်ခွင်မှာ ကလေးလို တိုးငိုခဲ့တာတွေ မင်း… [Am]မေ့ပြီး…",
                "ငါမသိ [F]ဖူးခဲ့တဲ့ သူစိမ်းတယောက်လို လှည့်တောင် မကြည့်"
            ]),
            SongSection(kind: .flow, label: "THEN PLAY", note: "ends with fade out", flow: [
                FlowStep(kind: .chorus, label: "Chorus"), FlowStep(kind: .verse, label: "Verse 2"),
                FlowStep(kind: .music, label: "Music"), FlowStep(kind: .chorus, label: "Chorus ×2"),
                FlowStep(kind: .music, label: "Music · fade out")
            ])
        ],
        source: .imported, isMyanmar: true,
        myNote: "Slow down on the chorus — let G/B ring."
    )

    static let sinSarParEasy: Song = {
        var s = sinSarPar
        s.id = "sinsarpar-easy"
        s.key = "G"
        s.source = .community
        s.uploader = uploader
        s.versionName = "Simplified chords"
        s.rating = 4.8
        s.plays = 1200
        s.myNote = nil
        s.sections = s.sections.map { sec in
            var sec = sec
            sec.lines = sec.lines.map { transposeLine($0, by: 7) }
            sec.bars = sec.bars.map { ChordTheory.transpose($0, by: 7) }
            return sec
        }
        return s
    }()

    static func placeholderSong(_ id: String, key: String, myanmar: Bool, meta: String) -> Song {
        Song(id: id, title: "[Song title]", artist: "[Artist]", key: key, sections: [
            SongSection(kind: .verse, label: "VERSE 1", lines: ["[\(key)][Lyrics line one] [\(ChordTheory.transposeRoot(key, by: 5))][lyrics]", "[\(ChordTheory.transposeRoot(key, by: 7))][Lyrics line two]"]),
            SongSection(kind: .chorus, label: "CHORUS", lines: ["[\(key)][Chorus line] [\(ChordTheory.transposeRoot(key, by: 7))][lyrics]"])
        ], source: .community, uploader: uploader, versionName: meta, rating: 4.5, plays: nil, isMyanmar: myanmar)
    }

    static let library: [Song] = [sinSarPar]

    static let explore: [CommunitySheet] = [
        CommunitySheet(song: sinSarPar, votes: 24, comments: 3, difficulty: "Intermediate"),
        CommunitySheet(song: sinSarParEasy, votes: 58, comments: 7, difficulty: "Beginner"),
        CommunitySheet(song: placeholderSong("c", key: "D", myanmar: true, meta: "Capo 2"), votes: 12, comments: 1, difficulty: "Beginner"),
        CommunitySheet(song: placeholderSong("d", key: "Am", myanmar: false, meta: "Fingerstyle"), votes: 9, comments: 0, difficulty: "Intermediate"),
        CommunitySheet(song: placeholderSong("e", key: "E", myanmar: false, meta: "4 chords"), votes: 31, comments: 4, difficulty: "Beginner"),
        CommunitySheet(song: placeholderSong("f", key: "F", myanmar: true, meta: "Barre chords"), votes: 6, comments: 2, difficulty: "Advanced")
    ]

    static let setlists: [Setlist] = [
        Setlist(id: "s1", name: "[Setlist name]", note: "Sunday set", songIDs: ["sinsarpar", "sinsarpar-easy", "c", "d"], date: "[Date]"),
        Setlist(id: "s2", name: "[Setlist name]", note: "Acoustic night", songIDs: ["e", "f"], date: "[Date]")
    ]

    static let notifications: [AppNotification] = [
        AppNotification(id: "n1", category: .community, title: "[Aung]", body: "saved your sheet စဉ်းစားပါ. 24 players have saved it now.", time: "5 min ago", unread: true, systemImage: "heart.fill"),
        AppNotification(id: "n2", category: .community, title: "[Thiri]", body: "suggested a chord fix in Verse 2, bar 3: G → G/B.", time: "1 h ago", unread: true, systemImage: "pencil"),
        AppNotification(id: "n3", category: .learning, title: "Your groove is at risk.", body: "10 minutes today keeps day 8 going.", time: "2 h ago", unread: true, systemImage: "waveform"),
        AppNotification(id: "n4", category: .community, title: "[Band leader]", body: "invited you to a Jam room · Sunday set (8 songs).", time: "Yesterday", unread: false, systemImage: "music.note"),
        AppNotification(id: "n5", category: .learning, title: "New tier!", body: "You’re now a Chord Keeper · +40 O Points.", time: "Tue", unread: false, systemImage: "shield.fill"),
        AppNotification(id: "n6", category: .system, title: "Report update:", body: "the sheet you reported was removed. Thanks for keeping O Chords tidy.", time: "Mon", unread: false, systemImage: "checkmark"),
        AppNotification(id: "n7", category: .learning, title: "Chord Rush:", body: "new best score — 180 → 205.", time: "Sun", unread: false, systemImage: "bolt.fill")
    ]

    static let learnPath: [LearnPosition] = [
        LearnPosition(id: "p1", number: 1, title: "First chords", subtitle: "Mastered", frets: []),
        LearnPosition(id: "p2", number: 2, title: "Switch chords without stopping", subtitle: "Frets 1–8", frets: [
            LearnFret(id: "f1", title: "Anchor fingers", state: .done, stars: 3),
            LearnFret(id: "f2", title: "C ↔ Am", state: .done, stars: 3),
            LearnFret(id: "f3", title: "C ↔ G/B · walk the bass", state: .current),
            LearnFret(id: "f4", title: "Am ↔ F", state: .locked),
            LearnFret(id: "f5", title: "Encore bonus", state: .locked, kind: .encore),
            LearnFret(id: "f6", title: "Strum it: Folk pattern", state: .locked),
            LearnFret(id: "f7", title: "The round chord", state: .locked),
            LearnFret(id: "f8", title: "Position 2 soundcheck", state: .locked, kind: .soundcheck)
        ]),
        LearnPosition(id: "p3", number: 3, title: "Your first song — စဉ်းစားပါ", subtitle: "Locked", frets: [])
    ]

    static let lessonQuestions: [LessonQuestion] = [
        LessonQuestion(id: "q1", kind: .pickChord, prompt: "Which chord walks the bass down from C?", detail: "C → ? → Am", options: ["G/B", "G", "Em", "F"], answer: "G/B"),
        LessonQuestion(id: "q2", kind: .pickChord, prompt: "Which finger stays put from C to G/B?", detail: "Think about the A string", options: ["None", "Index", "Middle", "Ring"], answer: "None"),
        LessonQuestion(id: "q3", kind: .buildBar, prompt: "Build the intro bar", detail: "C · G/B · Am · F", options: ["C", "G/B", "Am", "F", "G"], answer: "C G/B Am F"),
        LessonQuestion(id: "q4", kind: .listen, prompt: "Play C, then G/B", detail: "We’ll listen for the bass note", options: [], answer: ""),
        LessonQuestion(id: "q5", kind: .pickChord, prompt: "What comes after Am in the verse?", detail: "C → G/B → Am → ?", options: ["F", "G", "Dm", "E"], answer: "F")
    ]

    static func transposeLine(_ line: String, by t: Int) -> String {
        var out = ""
        var i = line.startIndex
        while i < line.endIndex {
            if line[i] == "[", let close = line[i...].firstIndex(of: "]") {
                out += "[" + ChordTheory.transpose(String(line[line.index(after: i)..<close]), by: t) + "]"
                i = line.index(after: close)
            } else {
                out.append(line[i]); i = line.index(after: i)
            }
        }
        return out
    }
}

/// In-memory implementation of every service, with small artificial delays.
final class MockBackend: AppBackend, ScanService {
    private var songs = MockData.library
    private func pause(_ ms: Int = 350) async { try? await Task.sleep(for: .milliseconds(ms)) }

    func library() async throws -> [Song] { await pause(); return songs }
    func explore(query: String) async throws -> [CommunitySheet] {
        await pause(250)
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return MockData.explore }
        return MockData.explore.filter { ($0.song.title + " " + $0.song.artist + " " + ($0.song.uploader?.name ?? "")).lowercased().contains(q) }
    }
    func popular() async throws -> [CommunitySheet] { await pause(); return MockData.explore.sorted { $0.votes > $1.votes } }
    func save(_ song: Song) async throws {
        await pause(200)
        if let i = songs.firstIndex(where: { $0.id == song.id }) { songs[i] = song } else { songs.insert(song, at: 0) }
    }
    func delete(songID: String) async throws { songs.removeAll { $0.id == songID } }

    func scan(_ source: ScanSource, progress: @escaping @MainActor (ScanStage) -> Void) async throws -> ScanResult {
        for stage in [ScanStage.page, .text, .chords, .song] {
            await progress(stage)
            await pause(400)
        }
        var s = MockData.sinSarPar
        s.id = UUID().uuidString
        s.myNote = nil
        return ScanResult(song: s, detectedZawgyi: true, lowConfidence: ["G/B"])
    }

    func restoreSession() async throws -> Profile? { nil }
    func signIn(email: String, password: String) async throws -> Profile {
        await pause(700)
        guard !email.isEmpty, password.count >= 4 else { throw ServiceError.invalidCredentials }
        var p = MockData.profile; p.email = email; return p
    }
    func signUp(name: String, email: String, password: String) async throws -> Profile {
        await pause(900)
        var p = MockData.profile
        p.user.name = name.isEmpty ? "[Your name]" : name
        p.email = email
        return p
    }
    func sendReset(email: String) async throws { await pause(600) }
    func signOut() async { await pause(200) }
    func deleteAccount(reason: String) async throws { await pause(800) }
    func resetPassword(email: String, token: String, password: String) async throws { await pause(800) }
    func signInWithApple(identityToken: String, name: String?) async throws -> Profile { await pause(800); return MockData.profile }
    func signInWithGoogle(idToken: String) async throws -> Profile { await pause(800); return MockData.profile }
    func syncSubscription(transactionJWS: String?) async throws -> Profile {
        await pause(200)
        var p = MockData.profile
        p.isPro = transactionJWS != nil
        return p
    }

    func setlists() async throws -> [Setlist] { await pause(); return MockData.setlists }
    func saveSetlist(_ list: Setlist) async throws -> Setlist { await pause(200); return list }
    func deleteSetlist(id: String) async throws { await pause(200) }
    func notifications() async throws -> [AppNotification] { await pause(); return MockData.notifications }
    func markAllNotificationsRead() async throws { await pause(200) }
    func user(id: String) async throws -> UserSummary { await pause(); return MockData.uploader }
    func report(songID: String, reason: String, detail: String) async throws { await pause(600) }
    func sendFeedback(kind: String, message: String) async throws { await pause(600) }

    func path() async throws -> [LearnPosition] { await pause(); return MockData.learnPath }
    func lesson(fretID: String) async throws -> [LessonQuestion] { await pause(200); return MockData.lessonQuestions }

    var serverOCR: OCREngine? { nil }

    /// All songs the demo knows about, for lookups by ID.
    func song(id: String) -> Song? {
        (songs + MockData.explore.map(\.song)).first { $0.id == id }
    }
}
