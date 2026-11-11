package com.onezcodes.ochords.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

// Domain models. Serializable so they decode from the API (JSON shapes match the iOS `Codable` models exactly).

/** ISO-8601 UTC dates, as the API sends them. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias IsoInstant = @Serializable(with = InstantSerializer::class) Instant

@Serializable
enum class SectionKind { @SerialName("verse") Verse, @SerialName("chorus") Chorus, @SerialName("music") Music,
    @SerialName("flow") Flow, @SerialName("bridge") Bridge, @SerialName("intro") Intro;

    val raw: String get() = name.lowercase()
}

@Serializable
enum class SongSource(val label: String) {
    @SerialName("imported") Imported("Imported"),
    @SerialName("community") Community("Community"),
    @SerialName("listened") Listened("Listened"),
    @SerialName("shared") Shared("Shared"),
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    /** Original key, e.g. "C". */
    val key: String,
    val tempo: Int = 80,
    val sections: List<SongSection>,
    val source: SongSource = SongSource.Imported,
    val uploader: UserSummary? = null,
    val versionName: String? = null,
    val rating: Double? = null,
    val plays: Int? = null,
    val isMyanmar: Boolean = true,
    val addedAt: IsoInstant = Instant.now(),
    val myNote: String? = null,
    /** Shared in Explore. Only known for your own sheets; null leaves the server's setting unchanged. */
    val isPublic: Boolean? = null,
) {
    /** Unique chords in order of first appearance. */
    val usedChords: List<String>
        get() {
            val seen = LinkedHashSet<String>()
            for (s in sections) {
                for (c in s.bars + s.lines.flatMap { ChordLine.chords(it) }) seen.add(c)
            }
            return seen.toList()
        }

    val sectionCount: Int get() = sections.count { it.kind != SectionKind.Flow }
}

@Serializable
data class SongSection(
    val id: String = UUID.randomUUID().toString(),
    val kind: SectionKind,
    val label: String,
    val note: String? = null,
    /** ChordPro-style lines: "[C]lyric [G/B]lyric". */
    val lines: List<String> = emptyList(),
    /** Instrumental bars. */
    val bars: List<String> = emptyList(),
    /** "Then play" flow. */
    val flow: List<FlowStep> = emptyList(),
    /** Strumming pattern written on the sheet ("↓ ↓ ↓ ↑ ↓"), if it gave one. */
    @SerialName("strum") val strumPattern: String? = null,
) {
    /** The sheet's pattern, or a common one for the kind of section. */
    val strum: String?
        get() = strumPattern ?: when (kind) {
            SectionKind.Verse, SectionKind.Chorus, SectionKind.Bridge -> "↓ ↓↑ ↑↓↑"
            SectionKind.Music, SectionKind.Intro -> "↓ ↓ ↓ ↓"
            SectionKind.Flow -> null
        }
}

@Serializable
data class FlowStep(val kind: SectionKind, val label: String)

@Serializable
data class UserSummary(
    val id: String,
    val name: String,
    val handle: String,
    val tier: Tier = Tier.OpenString,
    val sheets: Int = 0,
    val followers: Int = 0,
    val city: String? = null,
) {
    val initials: String get() = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().toString() }
}

@Serializable(with = TierSerializer::class)
enum class Tier(val rawValue: Int, val title: String, val roman: String, val minPoints: Int, val colorHex: Long) {
    OpenString(1, "Open String", "I", 0, 0x8E8A84),
    Strummer(2, "Strummer", "II", 300, 0x0F766E),
    ChordKeeper(3, "Chord Keeper", "III", 1000, 0xFFB800),
    SessionPlayer(4, "Session Player", "IV", 3000, 0x1E3A8A),
    Maestro(5, "Maestro", "V", 8000, 0x6D28D9);

    val perks: List<String>
        get() = when (this) {
            OpenString -> listOf("Scan and save unlimited sheets", "Full learning path and tools")
            Strummer -> listOf("Share sheets publicly in Explore", "Suggest corrections on other sheets", "Follow uploaders")
            ChordKeeper -> listOf("“Trusted” mark on your uploads", "Your uploads skip the review queue", "1 free rest note each week")
            SessionPlayer -> listOf("Approve corrections on community sheets", "Public setlists can be featured", "Custom profile colour")
            Maestro -> listOf("Help moderate reports", "Early access to new features", "Maestro badge next to your name")
        }
    val next: Tier? get() = entries.firstOrNull { it.rawValue == rawValue + 1 }

    companion object {
        fun forPoints(p: Int): Tier = entries.lastOrNull { p >= it.minPoints } ?: OpenString
        fun of(raw: Int): Tier = entries.firstOrNull { it.rawValue == raw } ?: OpenString
    }
}

object TierSerializer : KSerializer<Tier> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Tier", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Tier) = encoder.encodeInt(value.rawValue)
    override fun deserialize(decoder: Decoder): Tier = Tier.of(decoder.decodeInt())
}

@Serializable
data class Profile(
    val user: UserSummary,
    val email: String,
    val points: Int,
    val grooveDays: Int,
    val bestGroove: Int,
    val dailyGoalMinutes: Int,
    val todayMinutes: Int,
    /** Minutes practised for each of the last 7 days (oldest first). */
    val week: List<Int>,
    val restNotes: Int,
    val songsLearned: Int,
    val lessonsDone: Int,
    /** O Chords Pro, as the account last heard from the store. */
    val isPro: Boolean = false,
) {
    companion object {
        /** What a guest sees on profile screens: no name, no points yet. */
        val guest = Profile(
            user = UserSummary(id = "guest", name = "Guest", handle = "guest"),
            email = "", points = 0, grooveDays = 0, bestGroove = 0, dailyGoalMinutes = 10, todayMinutes = 0,
            week = List(7) { 0 }, restNotes = 0, songsLearned = 0, lessonsDone = 0,
        )
    }
}

@Serializable
data class Setlist(
    val id: String,
    val name: String,
    val note: String,
    val songIDs: List<String>,
    val date: String? = null,
)

@Serializable
data class CommunitySheet(
    val song: Song,
    val votes: Int,
    val comments: Int,
    val difficulty: String,
) {
    val id: String get() = song.id
}

@Serializable
data class AppNotification(
    val id: String,
    val category: Category,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean,
    val systemImage: String,
    /** When it happened (from the API); demo data only has `time`. */
    val createdAt: IsoInstant? = null,
) {
    @Serializable
    enum class Category { @SerialName("community") Community, @SerialName("learning") Learning, @SerialName("system") System }
}

// MARK: - Learning

@Serializable
data class LearnPosition(val id: String, val number: Int, val title: String, val subtitle: String, val frets: List<LearnFret>)

@Serializable
data class LearnFret(
    val id: String,
    val title: String,
    val state: State,
    val kind: Kind = Kind.Lesson,
    val stars: Int = 0,
) {
    @Serializable enum class State { @SerialName("done") Done, @SerialName("current") Current, @SerialName("locked") Locked }
    @Serializable enum class Kind { @SerialName("lesson") Lesson, @SerialName("encore") Encore, @SerialName("soundcheck") Soundcheck }
}

@Serializable
data class LessonQuestion(
    val id: String,
    val kind: Kind,
    val prompt: String,
    val detail: String,
    val options: List<String>,
    val answer: String,
) {
    @Serializable enum class Kind { @SerialName("pickChord") PickChord, @SerialName("buildBar") BuildBar, @SerialName("listen") Listen }
}

// MARK: - Chords

@Serializable
data class ChordVoicing(
    val label: String? = null,
    /** Six characters low E → high e: x = mute, 0 = open, n = fret. */
    val pattern: String,
    val dots: List<ChordDot>,
    val barre: Barre? = null,
    val tip: String = "",
    val baseFret: Int = 1,
) {
    val isBarre: Boolean get() = barre != null
}

@Serializable data class ChordDot(val string: Int, val fret: Int, val finger: Int)
@Serializable data class Barre(val fret: Int, val from: Int, val to: Int)
@Serializable data class ChordInfo(val name: String, val full: String, val voicings: List<ChordVoicing>)

/** Parses "[C]lyric [G/B]more" into chord-over-word segments. */
object ChordLine {
    data class Segment(val chord: String?, val text: String, val trailingSpace: Boolean)

    fun chords(line: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            val open = line.indexOf('[', i)
            if (open < 0) break
            val close = line.indexOf(']', open)
            if (close < 0) break
            out.add(line.substring(open + 1, close))
            i = close + 1
        }
        return out
    }

    fun parse(line: String): List<Segment> {
        val segs = ArrayList<Segment>()
        var pending: String? = null
        val buffer = StringBuilder()

        fun flushText() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            if (text.first().isWhitespace() && segs.isNotEmpty()) segs[segs.size - 1] = segs.last().copy(trailingSpace = true)
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val endSpace = text.last().isWhitespace()
            words.forEachIndexed { n, w ->
                segs.add(Segment(chord = if (n == 0) pending else null, text = w, trailingSpace = n < words.size - 1 || endSpace))
                if (n == 0) pending = null
            }
            buffer.clear()
        }

        var i = 0
        while (i < line.length) {
            val c = line[i]
            val close = if (c == '[') line.indexOf(']', i) else -1
            if (c == '[' && close >= 0) {
                flushText()
                pending?.let { segs.add(Segment(it, "", true)) }
                pending = line.substring(i + 1, close)
                i = close + 1
            } else {
                buffer.append(c)
                i++
            }
        }
        flushText()
        pending?.let { segs.add(Segment(it, "", false)) }
        return segs
    }
}
