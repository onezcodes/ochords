package com.onezcodes.ochords.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.onezcodes.ochords.design.Appearance
import com.onezcodes.ochords.model.AppNotification
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.services.APIConfig
import com.onezcodes.ochords.services.AdService
import com.onezcodes.ochords.services.AppBackend
import com.onezcodes.ochords.services.GuestStore
import com.onezcodes.ochords.services.MockBackend
import com.onezcodes.ochords.services.MockData
import com.onezcodes.ochords.services.OnDeviceScanService
import com.onezcodes.ochords.services.RemoteBackend
import com.onezcodes.ochords.services.Rescan
import com.onezcodes.ochords.services.ScanArchive
import com.onezcodes.ochords.services.ScanReading
import com.onezcodes.ochords.services.ScanService
import com.onezcodes.ochords.services.ScannedParts
import com.onezcodes.ochords.services.StoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val LocalApp = staticCompositionLocalOf<AppState> { error("No AppState") }

/** A Compose state whose new value is also handed to `save` (preferences, the guest store). */
private fun <T> saved(initial: T, save: (T) -> Unit): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private val state = mutableStateOf(initial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { state.value = value; save(value) }
}


/** Global app state: session, preferences and navigation. Mirrors the iOS `AppState`. */
class AppState(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("ochords.prefs", Context.MODE_PRIVATE)

    /** The O Chords API when `API_BASE_URL` is set (Debug: the local Laravel server), otherwise demo data. */
    val backend: AppBackend = makeBackend(context)
    val usesDemoData: Boolean get() = backend is MockBackend
    /** Sheet reading (camera photos, images, PDFs): the server for Myanmar lyrics when signed in, else on the phone. */
    val scanner: ScanService = if (backend is MockBackend) backend else OnDeviceScanService(context, backend.serverOCR)

    // Session
    var phase by mutableStateOf(AppPhase.Splash)
    /** Remembered across launches, so onboarding shows once. */
    var hasOnboarded by saved(false) { v -> if (!usesDemoData) prefs.edit().putBoolean("hasOnboarded", v).apply() }
    var profile by mutableStateOf<Profile?>(null)
    /** Using the app without an account (remembered across launches; songs are kept on the phone). */
    var isGuest by saved(false) { v -> if (!usesDemoData) prefs.edit().putBoolean("isGuest", v).apply() }
    /** O Chords Pro: what Google Play says on this phone, or what the account last synced from another device. */
    var isPro by mutableStateOf(false)
        private set
    /** Play Billing (products, entitlement, purchases) and AdMob. */
    val store = StoreService(context)
    val ads = AdService(context)
    var isOffline by mutableStateOf(false)

    // Preferences (remembered)
    var appearance by saved(Appearance.from(prefs.getString("appearance", null))) { v -> prefs.edit().putString("appearance", v.name).apply() }
    var leftHanded by saved(prefs.getBoolean("leftHanded", false)) { v -> prefs.edit().putBoolean("leftHanded", v).apply() }
    var sharpNames by saved(prefs.getBoolean("sharpNames", true)) { v -> prefs.edit().putBoolean("sharpNames", v).apply() }
    var alwaysConvertZawgyi by saved(prefs.getBoolean("alwaysConvertZawgyi", false)) { v -> prefs.edit().putBoolean("alwaysConvertZawgyi", v).apply() }
    var alwaysSkipWarmup by saved(prefs.getBoolean("alwaysSkipWarmup", false)) { v -> prefs.edit().putBoolean("alwaysSkipWarmup", v).apply() }
    var personalisedAds by saved(prefs.getBoolean("personalisedAds", true)) { v -> ads.personalised = v; prefs.edit().putBoolean("personalisedAds", v).apply() }
    var language by mutableStateOf("မြန်မာ")

    // Usage counters (free plan)
    var freeScansLeft by mutableStateOf(3)
    var lives by mutableStateOf(6)
    var unreadNotifications by mutableStateOf(0)

    // Content (loaded from the backend after sign-in; demo data fills it in straight away)
    var library by saved<List<Song>>(emptyList()) { v -> if (keepsGuestStore) guestStore.saveLibrary(v) }
    var setlists by saved<List<Setlist>>(emptyList()) { v -> if (keepsGuestStore) guestStore.saveSetlists(v) }
    /** Only a guest's own songs go to the phone's guest store — never an account's. */
    private val keepsGuestStore: Boolean get() = isGuest && profile == null && !usesDemoData
    private val guestStore = GuestStore(context)
    /** Each scanned song's reading (on this phone), so it can be read again when the scanner improves. */
    val scanArchive = ScanArchive(context)
    /** "Re-read 3 songs with the improved scanner", shown once on the Library. */
    var rescanNotice by mutableStateOf<String?>(null)
    private var rescanning = false
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
    /** Songs in setlists that aren't in the library (e.g. other players' sheets), by ID. */
    var setlistSongs by mutableStateOf<Map<String, Song>>(emptyMap())
    private var refreshJob: Job? = null

    // Navigation
    var tab by mutableStateOf(MainTab.Library)
    val paths = mutableStateMapOf<MainTab, List<Route>>().apply { MainTab.entries.forEach { put(it, emptyList()) } }
    var authPath by mutableStateOf<List<Route>>(emptyList())
    var modal by mutableStateOf<Modal?>(null)

    /** The foreground activity, for Play Billing and ad presentation. */
    private var activityRef: WeakReference<Activity>? = null
    val activity: Activity? get() = activityRef?.get()
    fun attach(a: Activity) { activityRef = WeakReference(a) }
    fun detach(a: Activity) { if (activityRef?.get() === a) activityRef = null }

    val isSignedIn: Boolean get() = profile != null

    /** The profile screens show: the account's, a blank guest one, or the demo player's. */
    val displayProfile: Profile get() = profile ?: (if (usesDemoData) MockData.profile else Profile.guest)

    init {
        if (usesDemoData) {
            library = MockData.library
            setlists = MockData.setlists
            notifications = MockData.notifications
            unreadNotifications = MockData.notifications.count { it.unread }
        } else {
            hasOnboarded = prefs.getBoolean("hasOnboarded", false)
            isGuest = prefs.getBoolean("isGuest", false)
            if (isGuest) {
                library = guestStore.library
                setlists = guestStore.setlists
                scope.launch { rescanImproved() }
            }
        }
        ads.personalised = personalisedAds
        store.onChange = { updatePro(); scope.launch { syncSubscription() } }
        scope.launch { store.start(); updatePro() }
    }

    // MARK: Pro

    private fun updatePro() { isPro = store.isPro || (profile?.isPro ?: false) }

    /** Tells the account what Google Play says, and takes the account's word when this phone has no subscription of its own. */
    suspend fun syncSubscription() {
        if (!isSignedIn || usesDemoData) return
        if (store.isPro || profile?.isPro == true) {
            runCatching { backend.syncSubscription(store.purchaseToken, store.activeProductID) }.getOrNull()?.let { profile = it }
        }
        updatePro()
    }

    /** Buys a Pro plan. */
    suspend fun purchasePro(product: StoreService.Plan?): StoreService.PurchaseOutcome {
        if (usesDemoData) {
            delay(1200)
            isPro = true
            return StoreService.PurchaseOutcome.Purchased
        }
        val a = activity ?: throw StoreService.StoreException.unavailable
        val outcome = store.purchase(a, product ?: throw StoreService.StoreException.unavailable)
        updatePro()
        syncSubscription()
        return outcome
    }

    suspend fun restorePurchases() {
        if (usesDemoData) return
        store.restore()
        updatePro()
        syncSubscription()
    }

    fun setDemoPro(on: Boolean) { isPro = on }

    /** After a finished session on the free plan: AdMob's interstitial when one is loaded, the design's placeholder otherwise. */
    fun showInterstitial() {
        if (isPro) return
        if (AdService.isConfigured) { activity?.let { ads.showInterstitial(it) } } else modal = Modal.Interstitial
    }

    // MARK: Links

    /** `ochords://reset-password?token=…` from the reset email. */
    fun handleUrl(url: Uri) {
        if (url.scheme != "ochords" || url.host != "reset-password") return
        val token = url.getQueryParameter("token")?.takeIf { it.isNotEmpty() } ?: return
        if (isSignedIn) return
        hasOnboarded = true
        modal = null
        MainTab.entries.forEach { paths[it] = emptyList() }
        authPath = listOf(Route.SignIn, Route.ResetPassword(token))
        phase = AppPhase.Welcome
    }

    private fun makeBackend(context: Context): AppBackend {
        val url = APIConfig.baseURL ?: return MockBackend()
        return RemoteBackend(url, context)
    }

    /** Push onto the current tab (or the auth stack before sign-in). */
    fun push(route: Route) {
        if (phase == AppPhase.Main) paths[tab] = (paths[tab] ?: emptyList()) + route else authPath = authPath + route
    }

    fun pop() {
        if (phase == AppPhase.Main) paths[tab] = (paths[tab] ?: emptyList()).dropLast(1) else authPath = authPath.dropLast(1)
    }

    fun popToRoot() { if (phase == AppPhase.Main) paths[tab] = emptyList() else authPath = emptyList() }

    /** Replace the top screen (e.g. Review after a scan). */
    fun replaceTop(route: Route) {
        if (phase == AppPhase.Main) paths[tab] = (paths[tab] ?: emptyList()).dropLast(1) + route else authPath = authPath.dropLast(1) + route
    }

    /** Switch tab and reset its stack. */
    fun switchTo(t: MainTab, then: Route? = null) {
        tab = t
        paths[t] = then?.let { listOf(it) } ?: emptyList()
    }

    val isTabBarVisible: Boolean get() = phase == AppPhase.Main && (paths[tab] ?: emptyList()).isEmpty()

    // MARK: Session actions

    fun finishSplash() { phase = if (hasOnboarded) (if (isSignedIn || isGuest) AppPhase.Main else AppPhase.Welcome) else AppPhase.Onboarding }
    fun finishOnboarding() { hasOnboarded = true; phase = AppPhase.Welcome }

    fun signedIn(p: Profile) {
        // A guest's songs and setlists come along into the new account.
        val carried = if (isGuest && !usesDemoData) library to setlists else emptyList<Song>() to emptyList<Setlist>()
        profile = p
        updatePro()
        isGuest = false
        authPath = emptyList()
        phase = AppPhase.Main
        modal = Modal.AdConsent
        scope.launch {
            moveToAccount(carried.first, carried.second)
            refresh()
            syncSubscription()
        }
    }

    /** Uploads a guest's songs, then their setlists, then clears them from the phone. What fails to upload stays. */
    private suspend fun moveToAccount(songs: List<Song>, lists: List<Setlist>) {
        if (songs.isEmpty() && lists.isEmpty()) return
        val songsLeft = ArrayList<Song>()
        for (song in songs) runCatching { backend.save(song) }.onFailure { songsLeft.add(song) }
        val failedIDs = songsLeft.map { it.id }.toSet()
        val listsLeft = ArrayList<Setlist>()
        for (list in lists) {
            if (list.songIDs.any { it in failedIDs }) { listsLeft.add(list); continue }
            runCatching { backend.saveSetlist(list) }.onFailure { listsLeft.add(list) }
        }
        if (songsLeft.isEmpty() && listsLeft.isEmpty()) guestStore.clear() else { guestStore.saveLibrary(songsLeft); guestStore.saveSetlists(listsLeft) }
    }

    /** Picks up a saved session at launch (runs during the splash). Skips onboarding for returning players. */
    suspend fun restoreSession() {
        val p = runCatching { backend.restoreSession() }.getOrNull() ?: return
        isGuest = false
        profile = p
        updatePro()
        hasOnboarded = true
        refresh()
        syncSubscription()
    }

    /** Reloads the player's library, setlists and notifications. Keeps what's shown if a request fails. */
    suspend fun refresh() {
        if (!isSignedIn || usesDemoData) return
        refreshJob?.let { it.join(); return }
        val job = scope.launch { loadAccountData() }
        refreshJob = job
        job.join()
        refreshJob = null
    }

    private suspend fun loadAccountData() {
        val songs = scope.async { runCatching { backend.library() }.getOrNull() }
        val lists = scope.async { runCatching { backend.setlists() }.getOrNull() }
        val notes = scope.async { runCatching { backend.notifications() }.getOrNull() }
        songs.await()?.let { library = it }
        lists.await()?.let { l ->
            setlistSongs = l.flatMap { it.songIDs }.mapNotNull { id -> backend.song(id)?.let { id to it } }.toMap()
            setlists = l
        }
        notes.await()?.let { notifications = it; unreadNotifications = it.count { n -> n.unread } }
        rescanImproved()
    }

    /** A song by ID from the library, setlists or anything the backend has loaded. */
    fun song(id: String): Song? = library.firstOrNull { it.id == id } ?: setlistSongs[id] ?: backend.song(id)

    fun markAllNotificationsRead() {
        notifications = notifications.map { it.copy(unread = false) }
        unreadNotifications = 0
        scope.launch { runCatching { backend.markAllNotificationsRead() } }
    }

    fun continueAsGuest() {
        isGuest = true
        if (!usesDemoData) {
            library = guestStore.library
            setlists = guestStore.setlists
            scope.launch { rescanImproved() }
        }
        authPath = emptyList()
        phase = AppPhase.Main
    }

    /** From guest mode to the sign-up / log-in screens. Their songs stay on the phone until they sign in. */
    fun showAccountOptions(signUp: Boolean) {
        MainTab.entries.forEach { paths[it] = emptyList() }
        authPath = listOf(if (signUp) Route.SignUp else Route.SignIn)
        phase = AppPhase.Welcome
    }

    fun signOut() {
        scope.launch { backend.signOut() }
        profile = null
        isGuest = false
        updatePro()
        if (!usesDemoData) {
            scanArchive.removeAll(except = guestStore.library.map { it.id }.toSet())
            library = emptyList()
            setlists = emptyList()
            setlistSongs = emptyMap()
            notifications = emptyList()
            unreadNotifications = 0
        }
        MainTab.entries.forEach { paths[it] = emptyList() }
        tab = MainTab.Library
        phase = AppPhase.Welcome
    }

    /** Signed in: saved to the account. Guest: kept on the phone (see `library`). */
    fun addToLibrary(song: Song) {
        val i = library.indexOfFirst { it.id == song.id }
        library = if (i >= 0) library.toMutableList().apply { set(i, song) } else listOf(song) + library
        if (isSignedIn || usesDemoData) scope.launch { runCatching { backend.save(song) } }
    }

    fun removeFromLibrary(id: String) {
        library = library.filter { it.id != id }
        scanArchive.remove(id)
        if (isSignedIn || usesDemoData) scope.launch { runCatching { backend.delete(id) } }
    }

    // MARK: Setlists

    fun saveSetlist(list: Setlist) {
        val i = setlists.indexOfFirst { it.id == list.id }
        setlists = if (i >= 0) setlists.toMutableList().apply { set(i, list) } else listOf(list) + setlists
        if (!(isSignedIn || usesDemoData)) return
        scope.launch {
            val saved = runCatching { backend.saveSetlist(list) }.getOrNull() ?: return@launch
            val j = setlists.indexOfFirst { it.id == saved.id }
            if (j >= 0 && setlists[j] == list) setlists = setlists.toMutableList().apply { set(j, saved) }
        }
    }

    fun deleteSetlist(id: String) {
        setlists = setlists.filter { it.id != id }
        if (isSignedIn || usesDemoData) scope.launch { runCatching { backend.deleteSetlist(id) } }
    }

    /** Keeps what the reader saw for a newly scanned song. */
    fun keepScan(reading: ScanReading, scanned: Song, songID: String) {
        scanArchive.save(ScanArchive.Entry(reading, ScannedParts(scanned), Rescan.scannerVersion, null), songID)
    }

    /** Reads scanned songs again when the scanner has improved since. */
    suspend fun rescanImproved() {
        if (rescanning) return
        rescanning = true
        try {
            var updated = 0
            for (id in scanArchive.songIDs) {
                var entry = scanArchive.entry(id) ?: continue
                if (entry.version >= Rescan.scannerVersion || library.none { it.id == id }) continue
                val new = withContext(Dispatchers.Default) { Rescan.read(context, entry.reading) }
                entry = entry.copy(version = Rescan.scannerVersion)
                val current = library.firstOrNull { it.id == id }
                if (new != null && current != null) {
                    val outcome = Rescan.merge(new, current, entry.scanned)
                    outcome.updated?.let { addToLibrary(it); updated++ }
                    entry = entry.copy(offered = outcome.offered, scanned = ScannedParts(new))
                }
                scanArchive.save(entry, id)
            }
            if (updated > 0) rescanNotice = "Re-read $updated ${if (updated == 1) "song" else "songs"} with the improved scanner"
        } finally { rescanning = false }
    }

    fun isInLibrary(id: String): Boolean = library.any { it.id == id }

    fun newID(): String = UUID.randomUUID().toString()
}
