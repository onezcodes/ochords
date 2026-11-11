package com.onezcodes.ochords.services

import android.graphics.Bitmap
import com.onezcodes.ochords.model.AppNotification
import com.onezcodes.ochords.model.CommunitySheet
import com.onezcodes.ochords.model.LearnPosition
import com.onezcodes.ochords.model.LessonQuestion
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.UserSummary

// Service boundaries. The demo ships `MockBackend`; `RemoteBackend` talks to the Laravel API in ../api.

interface SongService {
    suspend fun library(): List<Song>
    suspend fun explore(query: String): List<CommunitySheet>
    suspend fun popular(): List<CommunitySheet>
    suspend fun save(song: Song)
    suspend fun delete(songID: String)
}

/** What the scan screen hands the reader: a photo/image file or a PDF, as bytes. */
sealed class ScanSource {
    data class Image(val data: ByteArray) : ScanSource() {
        override fun equals(other: Any?) = other is Image && other.data.contentEquals(data)
        override fun hashCode() = data.contentHashCode()
    }
    data class Pdf(val data: ByteArray) : ScanSource() {
        override fun equals(other: Any?) = other is Pdf && other.data.contentEquals(data)
        override fun hashCode() = data.contentHashCode()
    }
}

enum class ScanStage { Page, Text, Chords, Song }

interface ScanService {
    /** Read a chord sheet (photo or PDF) into a draft song, reporting each stage as it starts. */
    suspend fun scan(source: ScanSource, progress: (ScanStage) -> Unit): ScanResult
}

data class ScanResult(
    val song: Song,
    val detectedZawgyi: Boolean,
    val lowConfidence: List<String>,
    /** Why a chord needs checking, e.g. "Superscript bass note read as a slash chord". */
    val chordNotes: Map<String, String> = emptyMap(),
    /** Lines whose lyrics couldn't be read (e.g. Myanmar script in a photo) — the user types them in Review. */
    val needsLyrics: List<ScanLineRef> = emptyList(),
    /** Small JPEG of the page as read (upright, cropped). */
    val thumbnail: ByteArray? = null,
    /** Where each section sits on the first page, for the reading overlay. */
    val regions: List<ScanRegion> = emptyList(),
    val linesRead: Int = 0,
    /** Whether the photo was cropped and flattened to the page edges. */
    val croppedToPage: Boolean = false,
    /** Read by the server (Myanmar lyrics included) rather than on the phone. */
    val readOnServer: Boolean = false,
    /** When `detectedZawgyi`: what was converted, for the "We fixed the Myanmar text" screen. */
    val zawgyi: ZawgyiReport? = null,
    /** What the reader saw, kept with the song so it can be read again when the scanner improves. */
    val reading: ScanReading? = null,
)

/** A sheet typed in the Zawgyi font, converted to Unicode. `song` on the result is the converted one. */
data class ZawgyiReport(
    /** The song exactly as scanned, for "Keep as scanned". */
    val scannedSong: Song,
    val wordsConverted: Int,
    /** Share of converted words that came out as well-formed Unicode (0–1). */
    val clean: Double,
    val preview: List<Line>,
) {
    data class Line(val label: String, val scanned: String, val converted: String)
}

data class ScanLineRef(val sectionID: String, val line: Int) {
    val id: String get() = "$sectionID#$line"
}

/** A section's area on the page, normalised 0–1 with the origin top-left. */
data class ScanRegion(val kind: SectionKind, val x: Double, val y: Double, val width: Double, val height: Double)

interface AuthService {
    /** The signed-in profile from a saved session, or null when there isn't one. */
    suspend fun restoreSession(): Profile?
    suspend fun signIn(email: String, password: String): Profile
    suspend fun signUp(name: String, email: String, password: String): Profile
    suspend fun sendReset(email: String)
    /** Second step of "Forgot password": the token comes from the `ochords://reset-password` link in the email. */
    suspend fun resetPassword(email: String, token: String, password: String)
    /** Sign in with Google: the ID token from Credential Manager. */
    suspend fun signInWithGoogle(idToken: String): Profile
    /** Tells the account about a Play Billing purchase (the purchase token), so Pro follows the player across devices. */
    suspend fun syncSubscription(purchaseToken: String?, productID: String?): Profile
    suspend fun signOut()
    suspend fun deleteAccount(reason: String)
}

interface CommunityService {
    suspend fun setlists(): List<Setlist>
    /** Creates or updates one of the player's setlists (by the app's ID). Returns the saved copy. */
    suspend fun saveSetlist(list: Setlist): Setlist
    suspend fun deleteSetlist(id: String)
    suspend fun notifications(): List<AppNotification>
    suspend fun markAllNotificationsRead()
    suspend fun user(id: String): UserSummary
    suspend fun report(songID: String, reason: String, detail: String)
    suspend fun sendFeedback(kind: String, message: String)
}

interface LearnService {
    suspend fun path(): List<LearnPosition>
    suspend fun lesson(fretID: String): List<LessonQuestion>
}

/** Everything the screens need from a backend: the demo `MockBackend` or the API (`RemoteBackend`). */
interface AppBackend : SongService, AuthService, CommunityService, LearnService {
    /** A song this backend has already loaded (library, Explore, setlists), for screens that look songs up by ID. */
    fun song(id: String): Song?
    /** Reads sheet photos on the server (Myanmar lyrics); null when there is no server. */
    val serverOCR: OCREngine?
}

// MARK: - OCR types (shared by the on-device reader, the server reader and the parser)

/** One line of text. Boxes are normalised with the origin top-left; page n spans y = n…n+1. */
@kotlinx.serialization.Serializable
data class ScanTextLine(val words: List<ScanWord>, val confidence: Float) {
    val text: String get() = words.joinToString(" ") { it.text }
    val box: Box get() = words.drop(1).fold(words.firstOrNull()?.box ?: Box.zero) { acc, w -> acc.union(w.box) }
}

@kotlinx.serialization.Serializable
data class ScanWord(val text: String, val box: Box)

/** A rectangle in normalised page coordinates (origin top-left). */
@kotlinx.serialization.Serializable
data class Box(val x: Double, val y: Double, val width: Double, val height: Double) {
    val minX get() = x
    val minY get() = y
    val maxX get() = x + width
    val maxY get() = y + height
    val midX get() = x + width / 2
    val midY get() = y + height / 2
    val isEmpty get() = width <= 0 || height <= 0
    fun union(o: Box): Box {
        if (isEmpty) return o
        if (o.isEmpty) return this
        val x0 = minOf(minX, o.minX); val y0 = minOf(minY, o.minY)
        return Box(x0, y0, maxOf(maxX, o.maxX) - x0, maxOf(maxY, o.maxY) - y0)
    }
    fun intersection(o: Box): Box {
        val x0 = maxOf(minX, o.minX); val y0 = maxOf(minY, o.minY)
        val x1 = minOf(maxX, o.maxX); val y1 = minOf(maxY, o.maxY)
        return if (x1 <= x0 || y1 <= y0) zero else Box(x0, y0, x1 - x0, y1 - y0)
    }
    fun offset(dx: Double = 0.0, dy: Double = 0.0) = Box(x + dx, y + dy, width, height)
    companion object { val zero = Box(0.0, 0.0, 0.0, 0.0) }
}

/** Placeholder for a word that is on the page but couldn't be read (e.g. Myanmar script). */
const val UNREADABLE_TEXT = "�"

interface OCREngine {
    /** Whether lyrics in Myanmar script come back as text. ML Kit on device: no. */
    val readsMyanmar: Boolean
    suspend fun recognize(image: Bitmap): List<ScanTextLine>
}

/** What the reader saw for a scanned song, kept on the phone so the song can be read again when the scanner improves. */
@kotlinx.serialization.Serializable
data class ScanReading(
    val kind: Kind,
    val lines: List<ScanTextLine>,
    val readsMyanmar: Boolean,
    val zawgyi: Boolean,
    /** The page as read (levelled, cropped), JPEG, base64. Not stored for `Lines`. */
    val page: ByteArray? = null,
) {
    @kotlinx.serialization.Serializable
    enum class Kind { Server, Device, Lines }
}

class ServiceException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { Offline, InvalidCredentials, ScanFailed, SignedOut, Server, Cancelled }

    companion object {
        val offline get() = ServiceException(Kind.Offline, "You’re offline.")
        val invalidCredentials get() = ServiceException(Kind.InvalidCredentials, "That email and password don’t match.")
        val scanFailed get() = ServiceException(Kind.ScanFailed, "We couldn’t read that sheet.")
        val signedOut get() = ServiceException(Kind.SignedOut, "Your session ended. Log in again.")
        val cancelled get() = ServiceException(Kind.Cancelled, "")
        /** A message from the server that can be shown as is. */
        fun server(message: String) = ServiceException(Kind.Server, message)
    }
}

/** A message the screens can show for any failure. */
val Throwable.userMessage: String
    get() = (this as? ServiceException)?.message?.takeIf { it.isNotEmpty() } ?: message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
