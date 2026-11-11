package com.onezcodes.ochords.services

import com.onezcodes.ochords.model.AppNotification
import com.onezcodes.ochords.model.CommunitySheet
import com.onezcodes.ochords.model.FlowStep
import com.onezcodes.ochords.model.LearnFret
import com.onezcodes.ochords.model.LearnPosition
import com.onezcodes.ochords.model.LessonQuestion
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.model.SongSource
import com.onezcodes.ochords.model.Tier
import com.onezcodes.ochords.model.UserSummary
import kotlinx.coroutines.delay
import java.util.UUID

/** Demo content. Text in [brackets] is a placeholder for real data, as on the design canvas. */
object MockData {
    val me = UserSummary(id = "me", name = "[Your name]", handle = "[username]", tier = Tier.ChordKeeper, sheets = 1, followers = 0, city = "Yangon")

    val profile = Profile(user = me, email = "you@example.com", points = 1240, grooveDays = 7, bestGroove = 12,
        dailyGoalMinutes = 10, todayMinutes = 6, week = listOf(12, 8, 15, 10, 4, 11, 6),
        restNotes = 1, songsLearned = 3, lessonsDone = 14)

    val uploader = UserSummary(id = "u1", name = "[username]", handle = "[username]", tier = Tier.SessionPlayer, sheets = 12, followers = 340, city = "[City]")

    /** စဉ်းစားပါ — the hero song used across the design. */
    val sinSarPar = Song(
        id = "sinsarpar", title = "စဉ်းစားပါ", artist = "မေ", key = "C", tempo = 76,
        sections = listOf(
            SongSection(kind = SectionKind.Music, label = "INTRO", note = "4 bars", bars = listOf("C", "G/B", "Am", "F")),
            SongSection(kind = SectionKind.Verse, label = "VERSE 1", lines = listOf(
                "[C]ငါ မကြားချင် ခဲ့သော စကား [G/B]မသိချင် ခဲ့ဆုံး [Am]အကြောင်းအရာ နင်ဟာ [F]ရက်စက်စွာ",
                "ပြောခဲ့ပြီ အခုတော့ ငါ့မှာ [C]အိပ်မက်တွေ ပြိုလွင့်ခဲ့ [G/B]ဆောက်တည်ရာမဲ့ [Am]မျက်ရည်",
                "[F]နေနိုင်လွန်းလိုက်တာ မင်းတစက်တောင် လှည့်မကြည့်တော့တာ",
            )),
            SongSection(kind = SectionKind.Chorus, label = "CHORUS", lines = listOf(
                "[C]လမ်းခွဲဖို့အတွက် မင်းဘက်က [G/B]အကြောင်းပြချက်တို့ သိပ်လွယ်ပ [Am]ငါ့ဘက်ကို",
                "ထည့် မစဉ်းစား [F]အတူတူ [C]ထားရှိခဲ့တဲ့ ကတိတို့က [G/B]အချည်းနှီး မဖြစ်ဖို့ ညှိလို့မရ",
                "[Am]ငါ့ဘက်က အားလုံး ရှုံး[F]နေတာ ဖေး ရေး",
            )),
            SongSection(kind = SectionKind.Verse, label = "VERSE 2", lines = listOf(
                "[C]နည်းနည်း စဉ်းစားပေးဦး [G/B]ချစ်သူရာ တို့နှစ်ဦး [Am]ပါတ်သက်ခဲ့တဲ့ နေ့ရက်များ [F]မခွဲဘူး",
                "အမြဲတမ်း ပြောတဲ့စကား [C]ချစ်ခဲ့တုန်းက အကောင်းဆုံး [G/B]အကြောင်းအရာများ",
                "ပြန်ပြီး[Am]လွမ်းတတ်ဖို့ [G]နင်သေချာ စဉ်း[F]စား",
            )),
            SongSection(kind = SectionKind.Music, label = "MUSIC", note = "repeat ×2", bars = listOf("C", "G/B", "Am", "F")),
            SongSection(kind = SectionKind.Verse, label = "VERSE 3", lines = listOf(
                "မိုးလင်းထိ [C]ကားလျှောက်မောင်းပြီး သီချင်းအော်ဟစ် ဆိုခဲ့တာတွေ",
                "ဝမ်းနည်းတိုင်း [G/B]ငါ့ရင်ခွင်မှာ ကလေးလို တိုးငိုခဲ့တာတွေ မင်း… [Am]မေ့ပြီး…",
                "ငါမသိ [F]ဖူးခဲ့တဲ့ သူစိမ်းတယောက်လို လှည့်တောင် မကြည့်",
            )),
            SongSection(kind = SectionKind.Flow, label = "THEN PLAY", note = "ends with fade out", flow = listOf(
                FlowStep(SectionKind.Chorus, "Chorus"), FlowStep(SectionKind.Verse, "Verse 2"),
                FlowStep(SectionKind.Music, "Music"), FlowStep(SectionKind.Chorus, "Chorus ×2"),
                FlowStep(SectionKind.Music, "Music · fade out"),
            )),
        ),
        source = SongSource.Imported, isMyanmar = true,
        myNote = "Slow down on the chorus — let G/B ring.",
    )

    val sinSarParEasy: Song = sinSarPar.copy(
        id = "sinsarpar-easy", key = "G", source = SongSource.Community, uploader = uploader, versionName = "Simplified chords",
        rating = 4.8, plays = 1200, myNote = null,
        sections = sinSarPar.sections.map { sec ->
            sec.copy(lines = sec.lines.map { ChordTheory.transposeLine(it, 7) }, bars = sec.bars.map { ChordTheory.transpose(it, 7) })
        },
    )

    fun placeholderSong(id: String, key: String, myanmar: Boolean, meta: String): Song = Song(
        id = id, title = "[Song title]", artist = "[Artist]", key = key, sections = listOf(
            SongSection(kind = SectionKind.Verse, label = "VERSE 1", lines = listOf(
                "[$key][Lyrics line one] [${ChordTheory.transposeRoot(key, 5)}][lyrics]", "[${ChordTheory.transposeRoot(key, 7)}][Lyrics line two]")),
            SongSection(kind = SectionKind.Chorus, label = "CHORUS", lines = listOf("[$key][Chorus line] [${ChordTheory.transposeRoot(key, 7)}][lyrics]")),
        ), source = SongSource.Community, uploader = uploader, versionName = meta, rating = 4.5, plays = null, isMyanmar = myanmar,
    )

    val library: List<Song> = listOf(sinSarPar)

    val explore: List<CommunitySheet> = listOf(
        CommunitySheet(sinSarPar, votes = 24, comments = 3, difficulty = "Intermediate"),
        CommunitySheet(sinSarParEasy, votes = 58, comments = 7, difficulty = "Beginner"),
        CommunitySheet(placeholderSong("c", "D", true, "Capo 2"), votes = 12, comments = 1, difficulty = "Beginner"),
        CommunitySheet(placeholderSong("d", "Am", false, "Fingerstyle"), votes = 9, comments = 0, difficulty = "Intermediate"),
        CommunitySheet(placeholderSong("e", "E", false, "4 chords"), votes = 31, comments = 4, difficulty = "Beginner"),
        CommunitySheet(placeholderSong("f", "F", true, "Barre chords"), votes = 6, comments = 2, difficulty = "Advanced"),
    )

    val setlists: List<Setlist> = listOf(
        Setlist("s1", "[Setlist name]", "Sunday set", listOf("sinsarpar", "sinsarpar-easy", "c", "d"), "[Date]"),
        Setlist("s2", "[Setlist name]", "Acoustic night", listOf("e", "f"), "[Date]"),
    )

    val notifications: List<AppNotification> = listOf(
        AppNotification("n1", AppNotification.Category.Community, "[Aung]", "saved your sheet စဉ်းစားပါ. 24 players have saved it now.", "5 min ago", true, "heart.fill"),
        AppNotification("n2", AppNotification.Category.Community, "[Thiri]", "suggested a chord fix in Verse 2, bar 3: G → G/B.", "1 h ago", true, "pencil"),
        AppNotification("n3", AppNotification.Category.Learning, "Your groove is at risk.", "10 minutes today keeps day 8 going.", "2 h ago", true, "waveform"),
        AppNotification("n4", AppNotification.Category.Community, "[Band leader]", "invited you to a Jam room · Sunday set (8 songs).", "Yesterday", false, "music.note"),
        AppNotification("n5", AppNotification.Category.Learning, "New tier!", "You’re now a Chord Keeper · +40 O Points.", "Tue", false, "shield.fill"),
        AppNotification("n6", AppNotification.Category.System, "Report update:", "the sheet you reported was removed. Thanks for keeping O Chords tidy.", "Mon", false, "checkmark"),
        AppNotification("n7", AppNotification.Category.Learning, "Chord Rush:", "new best score — 180 → 205.", "Sun", false, "bolt.fill"),
    )

    val learnPath: List<LearnPosition> = listOf(
        LearnPosition("p1", 1, "First chords", "Mastered", emptyList()),
        LearnPosition("p2", 2, "Switch chords without stopping", "Frets 1–8", listOf(
            LearnFret("f1", "Anchor fingers", LearnFret.State.Done, stars = 3),
            LearnFret("f2", "C ↔ Am", LearnFret.State.Done, stars = 3),
            LearnFret("f3", "C ↔ G/B · walk the bass", LearnFret.State.Current),
            LearnFret("f4", "Am ↔ F", LearnFret.State.Locked),
            LearnFret("f5", "Encore bonus", LearnFret.State.Locked, kind = LearnFret.Kind.Encore),
            LearnFret("f6", "Strum it: Folk pattern", LearnFret.State.Locked),
            LearnFret("f7", "The round chord", LearnFret.State.Locked),
            LearnFret("f8", "Position 2 soundcheck", LearnFret.State.Locked, kind = LearnFret.Kind.Soundcheck),
        )),
        LearnPosition("p3", 3, "Your first song — စဉ်းစားပါ", "Locked", emptyList()),
    )

    val lessonQuestions: List<LessonQuestion> = listOf(
        LessonQuestion("q1", LessonQuestion.Kind.PickChord, "Which chord walks the bass down from C?", "C → ? → Am", listOf("G/B", "G", "Em", "F"), "G/B"),
        LessonQuestion("q2", LessonQuestion.Kind.PickChord, "Which finger stays put from C to G/B?", "Think about the A string", listOf("None", "Index", "Middle", "Ring"), "None"),
        LessonQuestion("q3", LessonQuestion.Kind.BuildBar, "Build the intro bar", "C · G/B · Am · F", listOf("C", "G/B", "Am", "F", "G"), "C G/B Am F"),
        LessonQuestion("q4", LessonQuestion.Kind.Listen, "Play C, then G/B", "We’ll listen for the bass note", emptyList(), ""),
        LessonQuestion("q5", LessonQuestion.Kind.PickChord, "What comes after Am in the verse?", "C → G/B → Am → ?", listOf("F", "G", "Dm", "E"), "F"),
    )
}

/** In-memory implementation of every service, with small artificial delays. */
class MockBackend : AppBackend, ScanService {
    private var songs = MockData.library.toMutableList()
    private suspend fun pause(ms: Long = 350) = delay(ms)

    override suspend fun library(): List<Song> { pause(); return songs.toList() }
    override suspend fun explore(query: String): List<CommunitySheet> {
        pause(250)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return MockData.explore
        return MockData.explore.filter { (it.song.title + " " + it.song.artist + " " + (it.song.uploader?.name ?: "")).lowercase().contains(q) }
    }
    override suspend fun popular(): List<CommunitySheet> { pause(); return MockData.explore.sortedByDescending { it.votes } }
    override suspend fun save(song: Song) {
        pause(200)
        val i = songs.indexOfFirst { it.id == song.id }
        if (i >= 0) songs[i] = song else songs.add(0, song)
    }
    override suspend fun delete(songID: String) { songs.removeAll { it.id == songID } }

    override suspend fun scan(source: ScanSource, progress: (ScanStage) -> Unit): ScanResult {
        for (stage in ScanStage.entries) { progress(stage); pause(400) }
        val s = MockData.sinSarPar.copy(id = UUID.randomUUID().toString(), myNote = null)
        return ScanResult(song = s, detectedZawgyi = true, lowConfidence = listOf("G/B"))
    }

    override suspend fun restoreSession(): Profile? = null
    override suspend fun signIn(email: String, password: String): Profile {
        pause(700)
        if (email.isEmpty() || password.length < 4) throw ServiceException.invalidCredentials
        return MockData.profile.copy(email = email)
    }
    override suspend fun signUp(name: String, email: String, password: String): Profile {
        pause(900)
        return MockData.profile.copy(user = MockData.me.copy(name = name.ifEmpty { "[Your name]" }), email = email)
    }
    override suspend fun sendReset(email: String) { pause(600) }
    override suspend fun signOut() { pause(200) }
    override suspend fun deleteAccount(reason: String) { pause(800) }
    override suspend fun resetPassword(email: String, token: String, password: String) { pause(800) }
    override suspend fun signInWithGoogle(idToken: String): Profile { pause(800); return MockData.profile }
    override suspend fun syncSubscription(purchaseToken: String?, productID: String?): Profile {
        pause(200)
        return MockData.profile.copy(isPro = purchaseToken != null)
    }

    override suspend fun setlists(): List<Setlist> { pause(); return MockData.setlists }
    override suspend fun saveSetlist(list: Setlist): Setlist { pause(200); return list }
    override suspend fun deleteSetlist(id: String) { pause(200) }
    override suspend fun notifications(): List<AppNotification> { pause(); return MockData.notifications }
    override suspend fun markAllNotificationsRead() { pause(200) }
    override suspend fun user(id: String): UserSummary { pause(); return MockData.uploader }
    override suspend fun report(songID: String, reason: String, detail: String) { pause(600) }
    override suspend fun sendFeedback(kind: String, message: String) { pause(600) }

    override suspend fun path(): List<LearnPosition> { pause(); return MockData.learnPath }
    override suspend fun lesson(fretID: String): List<LessonQuestion> { pause(200); return MockData.lessonQuestions }

    override val serverOCR: OCREngine? get() = null

    /** All songs the demo knows about, for lookups by ID. */
    override fun song(id: String): Song? = (songs + MockData.explore.map { it.song }).firstOrNull { it.id == id }
}
