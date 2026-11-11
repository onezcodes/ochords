package com.onezcodes.ochords.services

import android.content.Context
import com.onezcodes.ochords.model.AppNotification
import com.onezcodes.ochords.model.CommunitySheet
import com.onezcodes.ochords.model.LearnPosition
import com.onezcodes.ochords.model.LessonQuestion
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.UserSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl

/** The O Chords API (Laravel, `api/` in this repo). Listen runs on the phone, and sheet scanning mostly does too. */
class RemoteBackend(baseURL: HttpUrl, context: Context) : AppBackend {
    private val api = APIClient(baseURL, context)
    /** Every song loaded so far, for `song(id)`. Requests finish on any thread, hence the lock. */
    private val songs = HashMap<String, Song>()
    private val lock = Any()
    private val strings = MapSerializer(String.serializer(), String.serializer())

    override fun song(id: String): Song? = synchronized(lock) { songs[id] }

    override val serverOCR: OCREngine get() = ServerOCREngine(api)

    private fun remember(list: List<Song>): List<Song> { synchronized(lock) { list.forEach { songs[it.id] = it } }; return list }
    private fun forget(id: String? = null) { synchronized(lock) { if (id != null) songs.remove(id) else songs.clear() } }

    // MARK: Auth

    @Serializable private data class Session(val token: String, val profile: Profile)

    override suspend fun restoreSession(): Profile? {
        if (!api.hasToken) return null
        return try { api.get("me", Profile.serializer()) } catch (e: ServiceException) { if (e.kind == ServiceException.Kind.SignedOut) null else throw e }
    }

    override suspend fun signIn(email: String, password: String): Profile {
        val s = api.send("POST", "auth/sign-in", mapOf("email" to email, "password" to password, "device_name" to deviceName), strings, Session.serializer())
        api.tokens.save(s.token)
        return s.profile
    }

    override suspend fun signUp(name: String, email: String, password: String): Profile {
        val s = api.send("POST", "auth/sign-up", mapOf("name" to name, "email" to email, "password" to password, "device_name" to deviceName), strings, Session.serializer())
        api.tokens.save(s.token)
        return s.profile
    }

    override suspend fun sendReset(email: String) = api.perform("POST", "auth/forgot-password", mapOf("email" to email), strings)

    override suspend fun resetPassword(email: String, token: String, password: String) =
        api.perform("POST", "auth/reset-password", mapOf("email" to email, "token" to token, "password" to password), strings)

    override suspend fun signInWithGoogle(idToken: String): Profile {
        val s = api.send("POST", "auth/google", mapOf("idToken" to idToken, "device_name" to deviceName), strings, Session.serializer())
        api.tokens.save(s.token)
        return s.profile
    }

    /** null = no active subscription found, which clears Pro on the account. */
    override suspend fun syncSubscription(purchaseToken: String?, productID: String?): Profile {
        if (purchaseToken != null) {
            return api.send("PUT", "me/subscription", mapOf("purchaseToken" to purchaseToken, "productId" to (productID ?: ""), "platform" to "android"), strings, Profile.serializer())
        }
        api.perform("DELETE", "me/subscription")
        return api.get("me", Profile.serializer())
    }

    override suspend fun signOut() {
        runCatching { api.perform("POST", "auth/sign-out") }
        api.tokens.clear()
        forget()
    }

    override suspend fun deleteAccount(reason: String) {
        api.perform("DELETE", "me", mapOf("reason" to reason), strings)
        api.tokens.clear()
    }

    private val deviceName = "O Chords Android"

    // MARK: Songs

    override suspend fun library(): List<Song> = remember(api.get("songs", ListSerializer(Song.serializer())))

    override suspend fun explore(query: String): List<CommunitySheet> {
        val q = query.trim()
        val sheets = api.get("explore", ListSerializer(CommunitySheet.serializer()), if (q.isEmpty()) emptyMap() else mapOf("q" to q))
        remember(sheets.map { it.song })
        return sheets
    }

    override suspend fun popular(): List<CommunitySheet> {
        val sheets = api.get("explore/popular", ListSerializer(CommunitySheet.serializer()))
        remember(sheets.map { it.song })
        return sheets
    }

    override suspend fun save(song: Song) {
        remember(listOf(api.send("PUT", "songs/${song.id}", song, Song.serializer(), Song.serializer())))
    }

    override suspend fun delete(songID: String) { api.perform("DELETE", "songs/$songID"); forget(songID) }

    // MARK: Community

    override suspend fun setlists(): List<Setlist> {
        val lists = api.get("setlists", ListSerializer(Setlist.serializer()))
        for (id in lists.flatMap { it.songIDs }.toSet()) if (song(id) == null) {
            runCatching { api.get("songs/$id", Song.serializer()) }.getOrNull()?.let { remember(listOf(it)) }
        }
        return lists
    }

    @Serializable private data class SetlistBody(val name: String, val note: String, val songIDs: List<String>)

    override suspend fun saveSetlist(list: Setlist): Setlist =
        api.send("PUT", "setlists/${list.id}", SetlistBody(list.name, list.note, list.songIDs), SetlistBody.serializer(), Setlist.serializer())

    override suspend fun deleteSetlist(id: String) = api.perform("DELETE", "setlists/$id")
    override suspend fun notifications(): List<AppNotification> = api.get("notifications", ListSerializer(AppNotification.serializer()))
    override suspend fun markAllNotificationsRead() = api.perform("POST", "notifications/read-all")
    override suspend fun user(id: String): UserSummary = api.get("users/$id", UserSummary.serializer())

    /** The Report screen passes its labels; the API takes stable codes. */
    override suspend fun report(songID: String, reason: String, detail: String) {
        val codes = mapOf("Wrong chords or lyrics" to "wrong_chords", "Copyright" to "copyright", "Spam or advertising" to "spam",
            "Offensive content" to "offensive", "Something else" to "other")
        api.perform("POST", "songs/$songID/reports", mapOf("reason" to (codes[reason] ?: "other"), "detail" to detail), strings)
    }

    override suspend fun sendFeedback(kind: String, message: String) {
        val codes = mapOf("Something’s broken" to "broken", "Wrong chords" to "wrong_chords", "Idea" to "idea", "Other" to "other")
        api.perform("POST", "feedback", mapOf("kind" to (codes[kind] ?: "other"), "message" to message), strings)
    }

    // MARK: Learn

    override suspend fun path(): List<LearnPosition> = api.get("learn/path", ListSerializer(LearnPosition.serializer()))
    override suspend fun lesson(fretID: String): List<LessonQuestion> = api.get("learn/frets/$fretID/questions", ListSerializer(LessonQuestion.serializer()))
}

/** Reads a page on the O Chords server (Google Cloud Vision), which — unlike the phone — reads Myanmar script. */
class ServerOCREngine(private val api: APIClient) : OCREngine {
    override val readsMyanmar: Boolean get() = true

    @Serializable private data class Reply(val engine: String, val lines: List<Line>) {
        @Serializable data class Line(val confidence: Float, val words: List<Word>)
        @Serializable data class Word(val text: String, val x: Double, val y: Double, val width: Double, val height: Double)
    }

    override suspend fun recognize(image: android.graphics.Bitmap): List<ScanTextLine> {
        if (!api.hasToken) throw ServiceException.signedOut
        val jpeg = ScanImage.jpeg(image, maxSide = 2000, quality = 80) ?: throw ServiceException.scanFailed
        val reply = api.upload("scans", jpeg, "image", "sheet.jpg", "image/jpeg", Reply.serializer())
        return reply.lines.map { l -> ScanTextLine(l.words.map { ScanWord(it.text, Box(it.x, it.y, it.width, it.height)) }, l.confidence) }
    }
}
