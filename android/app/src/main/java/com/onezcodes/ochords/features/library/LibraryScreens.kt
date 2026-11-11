package com.onezcodes.ochords.features.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.RewardKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.app.tabBarInset
import com.onezcodes.ochords.design.AdBadge
import com.onezcodes.ochords.design.CircleIconButton
import com.onezcodes.ochords.design.GlassButton
import com.onezcodes.ochords.design.GlassList
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.GuestAccountCard
import com.onezcodes.ochords.design.Hairline
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.ListRow
import com.onezcodes.ochords.design.LogoMark
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.PillTone
import com.onezcodes.ochords.design.Segmented
import com.onezcodes.ochords.design.StatusPill
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.Wordmark
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.model.AppNotification
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSource
import com.onezcodes.ochords.model.Tier
import com.onezcodes.ochords.model.UserSummary
import com.onezcodes.ochords.services.AdService
import com.onezcodes.ochords.services.MockData
import com.onezcodes.ochords.services.ScanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Shared helpers (library group)

/** Whether a scan can start: free users out of scans get the rewarded-ad offer instead. */
private fun libraryCanScan(app: AppState): Boolean {
    if (!(app.freeScansLeft <= 0 && !app.isPro)) return true
    app.modal = Modal.Reward(RewardKind.Scan)
    return false
}

/** Scan entry point: the camera. */
private fun libraryStartScan(app: AppState) {
    if (libraryCanScan(app)) app.push(Route.ImportSheet)
}

/** The skeleton is shown once per launch, on the Library's first appearance. */
private object LibraryLoadOnce { var done = false }

/** Text on an ink-filled surface (paper in light, ink in dark). */
private val libraryOnInk: Color @Composable get() = if (isDark) hex(0x171614) else hex(0xFAFAF8)

private fun formatPoints(points: Int): String = String.format(Locale.UK, "%,d", points)

/** Gold-tint key tile ("C", "G/B"…). */
@Composable
private fun LibraryKeyTile(key: String, size: Dp = 52.dp, fill: Color = OC.goldTint, fg: Color = OC.goldText) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(fill), contentAlignment = Alignment.Center) {
        Text(key, style = mono(size.value * (if (key.length > 2) 0.3f else 0.38f)), color = fg)
    }
}

/** Song title in Myanmar or Outfit depending on content. */
@Composable
private fun LibrarySongTitle(song: Song, size: Int = 17) {
    Text(
        song.title,
        style = if (song.isMyanmar && !song.title.startsWith("[")) myanmar(size, FontWeight.SemiBold) else outfit(size, FontWeight.SemiBold),
        color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Teal switch used on Profile (matches the design's #0F766E knob switch). */
@Composable
private fun LibraryTealSwitch(isOn: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val thumb by animateFloatAsState(if (isOn) 20f else 0f, spring(), label = "tealSwitch")
    Box(Modifier.heightIn(min = 44.dp).plainClickable(role = Role.Switch) { onChange(!isOn) }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(if (isOn) OC.teal else OC.switchOff).padding(3.dp)) {
            Box(Modifier.offset(x = thumb.dp).size(26.dp).shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f)).clip(CircleShape).background(Color.White))
        }
    }
}

/** Small shield badge with the tier's roman numeral (design: "III" / "IV" inside the shield). */
@Composable
private fun LibraryTierShield(tier: Tier, fill: Color, numeral: Color) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = fill, modifier = Modifier.size(19.dp))
        Text(tier.roman, style = outfit(7, FontWeight.Heavy), color = numeral, modifier = Modifier.offset(y = (-1).dp))
    }
}

@Composable
private fun LibrarySectionLabel(text: String) {
    Text(text, style = outfit(13, FontWeight.Bold, letterSpacing = 0.6.sp), color = OC.muted, modifier = Modifier.fillMaxWidth())
}

// MARK: - Banner ad

/**
 * Adaptive banner — Library, Popular, Chords by key, Profile, Tiers. A real AdMob banner when the build has an
 * app ID (`AdService`); the design's placeholder otherwise (demo build, screenshots). Nothing on Pro.
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier, onRemoveAds: () -> Unit = {}) {
    val app = LocalApp.current
    if (app.isPro) return
    var failed by remember { mutableStateOf(false) }
    val unit = AdService.units?.banner
    // The demo build never loads real ads, as on iOS.
    if (AdService.isConfigured && unit != null && !failed && !app.usesDemoData) {
        LiveBanner(unit, modifier) { failed = true }
    } else {
        BannerAdPlaceholder(modifier, onRemoveAds)
    }
}

@Composable
private fun LiveBanner(unit: String, modifier: Modifier, onFailed: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = "Advertisement" }) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
        var height by remember { mutableStateOf(AdService.bannerHeight(context, widthDp)) }
        val adView = remember {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = unit
            }
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(adView) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() { if (adSize.height > 0) height = adSize.height; loaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { onFailed() }
            }
            app.ads.start()
            adView.loadAd(app.ads.request())
            // No answer from the ad network (no Play services, offline): the placeholder instead of an empty slot.
            kotlinx.coroutines.delay(8000)
            if (!loaded) onFailed()
        }
        Box(Modifier.fillMaxWidth()) {
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(16.dp)))
            if (!loaded) BannerAdPlaceholder(Modifier.fillMaxWidth(), onRemoveAds = { app.modal = Modal.Pro })
        }
    }
}

/** The design's banner placeholder (also used when a real ad fails to load). */
@Composable
private fun BannerAdPlaceholder(modifier: Modifier = Modifier, onRemoveAds: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val dash = OC.faint.copy(alpha = 0.5f)
    Row(
        modifier.fillMaxWidth().height(58.dp).clip(shape).background(OC.surface).border(1.dp, OC.border, shape)
            .padding(start = 6.dp, end = 8.dp).semantics { contentDescription = "Advertisement" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(OC.softFill).drawBehind {
                drawRoundRect(dash, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))), cornerRadius = CornerRadius(10.dp.toPx()))
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdBadge()
                Text("[Banner ad headline]", style = outfit(13, FontWeight.Bold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("[Advertiser] · ", style = outfit(11), color = OC.muted)
                Text("Remove ads", style = outfit(11, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable(onClick = onRemoveAds))
            }
        }
        Box(Modifier.height(30.dp).clip(CircleShape).background(OC.softFill).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text("[Open]", style = outfit(12, FontWeight.Bold), color = OC.ink)
        }
    }
}

// MARK: - Library (tab root)

/** Reads a picked document's bytes off the main thread; null when it can't be opened. */
private suspend fun readBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
}

private fun isPdf(context: Context, uri: Uri): Boolean =
    context.contentResolver.getType(uri) == "application/pdf" || uri.lastPathSegment?.lowercase()?.endsWith(".pdf") == true

@Composable
fun LibraryView() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(!LibraryLoadOnce.done) }
    var showAll by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val profile: Profile = app.displayProfile
    val filtered: List<Song> = remember(query, app.library) {
        val q = query.trim()
        if (q.isEmpty()) app.library else app.library.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) || it.key.contains(q, ignoreCase = true)
        }
    }

    // Adding a song from a photo or file already on the phone, without the camera.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = readBytes(context, uri)
            if (data == null) toast = Toast("Couldn’t open that photo", Icons.Outlined.WarningAmber)
            else app.push(Route.ImportPicked(ScanSource.Image(data)))
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = readBytes(context, uri)
            if (data == null) toast = Toast("Couldn’t open that file", Icons.Outlined.WarningAmber)
            else app.push(Route.ImportPicked(if (isPdf(context, uri)) ScanSource.Pdf(data) else ScanSource.Image(data)))
        }
    }

    /** Add a song from a photo already on the phone — straight to reading it, no camera. */
    fun pickPhoto() { if (libraryCanScan(app)) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    /** Add a song from a PDF or image in Files. */
    fun pickFile() { if (libraryCanScan(app)) filePicker.launch(arrayOf("application/pdf", "image/*")) }

    fun remove(song: Song) {
        app.removeFromLibrary(song.id)
        toast = Toast("Removed from library", Icons.Filled.Delete)
    }

    LaunchedEffect(app.rescanNotice) {
        val notice = app.rescanNotice ?: return@LaunchedEffect
        toast = Toast(notice, Icons.Outlined.AutoAwesome)
        app.rescanNotice = null
    }
    LaunchedEffect(Unit) {
        if (!isLoading) return@LaunchedEffect
        delay(900)
        LibraryLoadOnce.done = true
        isLoading = false
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LibraryHeader(profile, query, onQuery = { query = it }, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))

                when {
                    isLoading -> LibrarySkeleton(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp))
                    app.library.isEmpty() -> LibraryEmptyState(
                        onScan = { libraryStartScan(app) }, onPhotos = ::pickPhoto, onBrowse = { app.switchTo(MainTab.Explore) },
                        modifier = Modifier.padding(horizontal = 28.dp).padding(top = 36.dp),
                    )
                    app.isOffline -> LibraryOfflineContent(filtered)
                    else -> LibraryOnlineContent(
                        query = query, filtered = filtered, showAll = showAll,
                        onToggleAll = {
                            if (app.library.size > 4) showAll = !showAll
                            else toast = Toast("That’s your whole library", Icons.Outlined.QueueMusic)
                        },
                        onScan = { libraryStartScan(app) }, onPhotos = ::pickPhoto, onFiles = ::pickFile,
                        onListen = { app.push(Route.Listen) }, onRemove = ::remove,
                    )
                }
                Spacer(Modifier.height(24.dp + tabBarInset))
            }
        }
    }
}

// MARK: Header

@Composable
private fun LibraryHeader(profile: Profile, query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(size = 30.dp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(44.dp).glass(22.dp).plainClickable { app.push(Route.Streak) }
                        .semantics { contentDescription = "Groove: ${profile.grooveDays} days" }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(16.dp))
                    Text("${profile.grooveDays}", style = outfit(16, FontWeight.Heavy), color = OC.ink)
                }
                CircleIconButton(
                    Icons.Outlined.NotificationsNone,
                    label = if (app.unreadNotifications > 0) "Notifications, ${app.unreadNotifications} new" else "Notifications",
                    badge = app.unreadNotifications,
                ) { app.push(Route.Notifications) }
                CircleIconButton(Icons.Outlined.PersonOutline, label = "Profile") { app.push(Route.Profile) }
            }
        }

        Text("Library", style = outfit(36, FontWeight.Heavy, letterSpacing = (-1).sp), color = OC.ink)

        Row(
            Modifier.fillMaxWidth().height(46.dp).glass(14.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Search songs" },
                textStyle = outfit(16).copy(color = OC.ink), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Songs, artists, သီချင်း…", style = outfit(16), color = OC.muted.copy(alpha = 0.8f), maxLines = 1)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.width(32.dp).height(44.dp).plainClickable { onQuery("") }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// MARK: Online

@Composable
private fun LibraryOnlineContent(
    query: String, filtered: List<Song>, showAll: Boolean, onToggleAll: () -> Unit,
    onScan: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit, onListen: () -> Unit, onRemove: (Song) -> Unit,
) {
    val app = LocalApp.current
    Column(Modifier.fillMaxWidth()) {
        if (query.isEmpty()) {
            LibraryAddSongCard(onScan = onScan, onPhotos = onPhotos, onListen = onListen, onFiles = onFiles,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp))
            BannerAdView(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), onRemoveAds = { app.push(Route.AdChoices) })
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isEmpty()) "Recently added" else "Results", style = outfit(20, FontWeight.Bold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                if (query.isEmpty()) {
                    Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onToggleAll), contentAlignment = Alignment.Center) {
                        Text(if (showAll && app.library.size > 4) "Show less" else "See all", style = outfit(14, FontWeight.SemiBold), color = OC.goldText)
                    }
                }
            }

            val songs = if (query.isEmpty() && !showAll) filtered.take(4) else filtered
            if (songs.isEmpty()) {
                Text("No songs match “$query”.", style = outfit(15), color = OC.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp))
            } else {
                songs.forEach { song ->
                    androidx.compose.runtime.key(song.id) {
                        LibrarySwipeRow(onTap = { app.push(Route.SongPage(song)) }, onDelete = { onRemove(song) }) {
                            LibrarySongRowContent(song)
                        }
                    }
                }
            }
        }
    }
}

// MARK: Offline

@Composable
private fun LibraryOfflineContent(filtered: List<Song>) {
    val app = LocalApp.current
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(OC.inkGlass).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = OC.gold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("You’re offline", style = outfit(15, FontWeight.Bold), color = OC.paper)
                Text("${app.library.size} downloaded ${if (app.library.size == 1) "song" else "songs"}, tuner and chord guide still work.",
                    style = outfit(13), color = hex(0xBDB9B3))
            }
            Box(Modifier.heightIn(min = 44.dp).plainClickable { app.isOffline = false }, contentAlignment = Alignment.Center) {
                Box(Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Retry", style = outfit(13, FontWeight.Bold), color = OC.paper)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text("Available offline", style = outfit(20, FontWeight.Bold), color = OC.ink)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} ${if (filtered.size == 1) "song" else "songs"} · ${String.format(Locale.UK, "%.1f", filtered.size * 0.34)} MB",
                style = outfit(13), color = OC.muted)
        }

        filtered.forEach { song ->
            LibraryOfflineRow(title = song.title, isMyanmar = song.isMyanmar, sub = "${song.artist} · Key ${song.key}", key = song.key, available = true,
                modifier = Modifier.plainClickable { app.push(Route.SongPage(song)) })
        }

        Text("Not downloaded", style = outfit(20, FontWeight.Bold), color = OC.ink, modifier = Modifier.padding(top = 12.dp))
        LibraryOfflineRow(title = "[Song title]", isMyanmar = false, sub = "From Explore · Key Am", key = "Am", available = false,
            modifier = Modifier.alpha(0.45f).semantics { contentDescription = "Needs internet" })

        Text("Changes you make offline sync when you’re back online.", style = outfit(13), color = OC.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LibraryAddSongCard(onScan: () -> Unit, onPhotos: () -> Unit, onListen: () -> Unit, onFiles: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().inkGlass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(hex(0x2A2825)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = OC.gold, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add a song", style = outfit(19, FontWeight.Bold), color = OC.paper)
                Text("Photo, PDF — or listen to a song", style = outfit(13), color = hex(0xBDB9B3))
            }
        }
        Text("Scan a handwritten or printed sheet, or let us listen to a song playing nearby and estimate its chords.",
            style = outfit(14).copy(lineHeight = 20.sp), color = hex(0xD6D4CF))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GoldButton("Scan", Modifier.weight(1f), height = 48.dp, leading = {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp))
            }, onClick = onScan)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, hex(0x4A4743), RoundedCornerShape(14.dp)).plainClickable(onClick = onPhotos)
                    .semantics { contentDescription = "Choose a sheet from Photos" },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = OC.paper, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Photos", style = outfit(15, FontWeight.SemiBold), color = OC.paper)
            }
            AddSongIconButton(Icons.Outlined.Mic, label = "Listen to a song", onClick = onListen)
            AddSongIconButton(Icons.Outlined.Description, label = "Open a PDF or image from Files", onClick = onFiles)
        }
    }
}

@Composable
private fun AddSongIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).border(1.dp, hex(0x4A4743), RoundedCornerShape(14.dp)).plainClickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = OC.paper, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun LibrarySongRowContent(song: Song) {
    Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LibraryKeyTile(song.key)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LibrarySongTitle(song)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, style = if (song.isMyanmar) myanmar(13) else outfit(13), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" · Key ${song.key} · ${song.sectionCount} sections", style = outfit(13), color = OC.muted, maxLines = 1)
            }
        }
        StatusPill(song.source.label, tone = when (song.source) { SongSource.Imported -> PillTone.Teal; SongSource.Community -> PillTone.Gold; else -> PillTone.Blue })
    }
}

/** Swipe left to reveal a delete action; tap opens the song. Long-press offers the same action in a menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySwipeRow(onTap: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val revealWidth = 84.dp
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    fun close() { scope.launch { offset.animateTo(0f, spring()) } }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min), contentAlignment = Alignment.CenterEnd) {
        if (offset.value < 0f) {
            Column(
                Modifier.width(revealWidth - 8.dp).fillMaxHeight().clip(RoundedCornerShape(OC.rCard)).background(OC.red)
                    .plainClickable(onClick = onDelete).semantics { contentDescription = "Remove from library" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text("Remove", style = outfit(12, FontWeight.Bold), color = Color.White)
            }
        }
        Box(
            Modifier.fillMaxWidth().offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { offset.animateTo(if (offset.value < -revealPx / 2) -revealPx else 0f, spring()) }
                        },
                        onDragCancel = { close() },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-revealPx - 30f, 0f)) }
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { if (offset.value != 0f) close() else onTap() },
                    onLongClick = { menu = true },
                ),
        ) {
            content()
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from library", style = outfit(15, FontWeight.Medium), color = OC.red) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = OC.red) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun LibraryOfflineRow(title: String, isMyanmar: Boolean, sub: String, key: String, available: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LibraryKeyTile(key, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = if (isMyanmar) myanmar(16, FontWeight.SemiBold) else outfit(16, FontWeight.SemiBold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = outfit(13), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (available) {
            Row(
                Modifier.clip(CircleShape).background(OC.tealTint).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = OC.tealText, modifier = Modifier.size(11.dp))
                Text("On device", style = outfit(12, FontWeight.Bold), color = OC.tealText)
            }
        } else {
            StatusPill("Needs internet", tone = PillTone.Neutral)
        }
    }
}

@Composable
private fun LibraryEmptyState(onScan: () -> Unit, onPhotos: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        LibraryEmptyIllustration()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your library is empty", style = outfit(26, FontWeight.Bold, letterSpacing = (-0.6).sp), color = OC.ink)
            Text("Snap a photo of any chord sheet — printed or handwritten — and we’ll turn it into a clean, playable song.",
                style = outfit(15).copy(lineHeight = 21.sp), color = OC.muted, textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton("Scan your first sheet", leading = {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp))
            }, onClick = onScan)
            GlassButton("Choose from Photos", height = 54.dp, leading = {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = OC.ink, modifier = Modifier.size(18.dp))
            }, onClick = onPhotos)
            GlassButton("Browse community chords", height = 54.dp, onClick = onBrowse)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("✓ Myanmar & English", style = outfit(13), color = OC.muted)
            Text("✓ Works offline", style = outfit(13), color = OC.muted)
        }
    }
}

@Composable
private fun LibraryEmptyIllustration() {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.width(200.dp).height(156.dp)) {
        Box(Modifier.offset(16.dp, 28.dp).rotate(-9f).size(112.dp, 120.dp).clip(shape).background(OC.glass).border(1.dp, OC.glassBorder, shape))
        Column(
            Modifier.offset(64.dp, 6.dp).size(118.dp, 132.dp).shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(shape).background(OC.surface.copy(alpha = 0.85f)).border(1.dp, OC.glassBorder, shape).padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text("C", style = mono(13), color = OC.goldText); Text("G/B", style = mono(13), color = OC.goldText) }
            Box(Modifier.width(82.dp).height(6.dp).clip(CircleShape).background(OC.softFill))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text("Am", style = mono(13), color = OC.goldText); Text("F", style = mono(13), color = OC.goldText) }
            Box(Modifier.width(64.dp).height(6.dp).clip(CircleShape).background(OC.softFill))
            Box(Modifier.width(76.dp).height(6.dp).clip(CircleShape).background(OC.softFill))
        }
        Box(
            Modifier.offset(144.dp, 102.dp).size(52.dp).shadow(10.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
                .clip(CircleShape).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(1.2f, 2.2f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "shimmer")
    val base = OC.softFill
    val shimmer = base.copy(alpha = (base.alpha * phase).coerceAtMost(1f))

    @Composable
    fun bar(width: Float, height: Dp) {
        Box(Modifier.fillMaxWidth(width).height(height).clip(RoundedCornerShape(8.dp)).background(shimmer))
    }

    Column(modifier.fillMaxWidth().semantics { contentDescription = "Loading your library" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().height(150.dp).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            bar(0.55f, 18.dp)
            bar(0.35f, 12.dp)
        }
        repeat(3) {
            Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(shimmer))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bar(0.62f, 14.dp)
                    bar(0.4f, 11.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = OC.gold, strokeWidth = 2.dp)
            Text("Loading your library…", style = outfit(13, FontWeight.SemiBold), color = OC.muted)
        }
    }
}

// MARK: - Notifications

private enum class LibraryNotifTab(val label: String) { All("All"), Community("Community"), Learning("Learning") }

private enum class LibraryNotifAction { Review, Practise, Join, PlayAgain }

/** Presentation details from the design, keyed by notification id. */
private data class LibraryNotifStyle(
    val group: String,
    val icon: ImageVector? = null,
    val glyph: String? = null,
    val bg: Color,
    val fg: Color,
    val action: Pair<String, LibraryNotifAction>? = null,
    val secondary: String? = null,
) {
    companion object {
        /** Look and action follow the notification's icon; the group follows when it happened. */
        @Composable
        fun of(n: AppNotification): LibraryNotifStyle {
            val group = groupName(n)
            return when (n.systemImage) {
                "heart.fill" -> LibraryNotifStyle(group, icon = Icons.Filled.Favorite, bg = OC.redTint, fg = OC.red)
                "pencil" -> LibraryNotifStyle(group, icon = Icons.Outlined.Edit, bg = OC.blueTint, fg = OC.blue, action = "Review" to LibraryNotifAction.Review, secondary = "Dismiss")
                "waveform" -> LibraryNotifStyle(group, glyph = "▮▮", bg = OC.goldTint, fg = OC.goldText, action = "Practise now" to LibraryNotifAction.Practise)
                "music.note" -> LibraryNotifStyle(group, glyph = "♪", bg = OC.gold, fg = OC.inkFixed, action = "Join" to LibraryNotifAction.Join)
                "shield.fill" -> LibraryNotifStyle(group, icon = Icons.Filled.Shield, bg = hex(0x6D28D9, 0.14f), fg = OC.purple)
                "checkmark" -> LibraryNotifStyle(group, glyph = "✓", bg = OC.tealTint, fg = OC.tealText)
                "bolt.fill" -> LibraryNotifStyle(group, glyph = "⚡", bg = OC.goldTint, fg = OC.goldText, action = "Play again" to LibraryNotifAction.PlayAgain)
                else -> LibraryNotifStyle(group, glyph = n.systemImage.ifEmpty { "•" }, bg = OC.softFill, fg = OC.muted)
            }
        }

        fun groupName(n: AppNotification): String {
            val date = n.createdAt
            if (date != null) {
                if (date.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()) return "TODAY"
                return if (date.isAfter(Instant.now().minus(7, ChronoUnit.DAYS))) "THIS WEEK" else "EARLIER"
            }
            // Demo data: "5 min ago", "1 h ago" are today.
            return if (n.time.contains("min") || n.time.contains(" h ")) "TODAY" else "THIS WEEK"
        }
    }
}

@Composable
fun NotificationsView() {
    val app = LocalApp.current
    var tab by remember { mutableStateOf(LibraryNotifTab.All) }
    var allRead by remember { mutableStateOf(app.unreadNotifications == 0) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val visible = app.notifications.filter { n ->
        if (n.id in hidden) return@filter false
        when (tab) {
            LibraryNotifTab.All -> true
            LibraryNotifTab.Community -> n.category == AppNotification.Category.Community
            LibraryNotifTab.Learning -> n.category == AppNotification.Category.Learning
        }
    }

    fun perform(action: LibraryNotifAction) {
        when (action) {
            LibraryNotifAction.Review -> toast = Toast("Fix sent to review · G → G/B", Icons.Outlined.Edit)
            LibraryNotifAction.Practise -> app.switchTo(MainTab.Learn)
            LibraryNotifAction.Join -> app.push(Route.Jam(MockData.sinSarPar))
            LibraryNotifAction.PlayAgain -> app.push(Route.Game)
        }
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "Notifications", backLabel = "Back to library", onBack = { app.pop() }) {
                    Box(
                        Modifier.heightIn(min = 44.dp).alpha(if (allRead) 0.5f else 1f)
                            .plainClickable(enabled = !allRead) { allRead = true; app.markAllNotificationsRead() }.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Mark all read", style = outfit(13, FontWeight.Bold), color = OC.goldText) }
                }

                Segmented(LibraryNotifTab.entries, tab, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), label = { it.label }) { tab = it }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEachIndexed { i, n ->
                        val style = LibraryNotifStyle.of(n)
                        if (i == 0 || LibraryNotifStyle.groupName(visible[i - 1]) != style.group) {
                            Text(style.group, style = outfit(12, FontWeight.Bold, letterSpacing = 1.sp), color = OC.muted, modifier = Modifier.padding(top = 6.dp))
                        }
                        androidx.compose.runtime.key(n.id) {
                            LibraryNotifCard(n, style, unread = n.unread && !allRead, onAction = ::perform, onDismiss = { hidden = hidden + n.id })
                        }
                    }
                    if (visible.isEmpty()) {
                        Text("You’re all caught up.", style = outfit(15), color = OC.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp))
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 6.dp).plainClickable { app.push(Route.Settings) }, contentAlignment = Alignment.Center) {
                        Text("Notification settings ›", style = outfit(13, FontWeight.Bold), color = OC.goldText)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryNotifCard(n: AppNotification, style: LibraryNotifStyle, unread: Boolean, onAction: (LibraryNotifAction) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val dark = isDark
    val fill = if (unread) Color.White.copy(alpha = if (dark) 0.12f else 0.85f) else Color.White.copy(alpha = if (dark) 0.05f else 0.5f)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(fill).border(1.dp, OC.glassBorder, shape)
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}, onLongClick = { menu = true })
                .padding(12.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(style.bg), contentAlignment = Alignment.Center) {
                if (style.icon != null) Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(16.dp))
                else Text(style.glyph ?: "•", style = outfit(if ((style.glyph?.length ?: 0) > 2) 12 else 15, FontWeight.Heavy), color = style.fg, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(n.title) }
                        append(" ")
                        append(n.body)
                    },
                    style = outfit(14).copy(lineHeight = 20.sp), color = OC.ink,
                )
                style.action?.let { (label, kind) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(OC.ink).plainClickable { onAction(kind) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = outfit(13, FontWeight.Bold), color = libraryOnInk)
                        }
                        style.secondary?.let { secondary ->
                            Box(
                                Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(OC.surface).border(1.dp, OC.border, RoundedCornerShape(10.dp))
                                    .plainClickable(onClick = onDismiss).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(secondary, style = outfit(13, FontWeight.Bold), color = OC.ink) }
                        }
                    }
                }
                Text(n.time, style = outfit(12), color = OC.muted)
            }
            if (unread) {
                Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(OC.gold).semantics { contentDescription = "Unread" })
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Dismiss", style = outfit(15, FontWeight.Medium), color = OC.red) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, tint = OC.red) },
                onClick = { menu = false; onDismiss() },
            )
        }
    }
}

// MARK: - Profile

@Composable
fun ProfileView() {
    val app = LocalApp.current
    var isPublic by remember { mutableStateOf(true) }
    val profile = app.displayProfile

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Profile", backLabel = "Back to library", onBack = { app.pop() }) {
                Box(Modifier.size(44.dp).plainClickable { app.push(Route.Settings) }.semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!app.isSignedIn && !app.usesDemoData) GuestAccountCard()
                ProfileIdentity(profile)
                ProfileNextTierCard(profile)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat(if (isPublic) "1" else "0", "Public uploads", Modifier.weight(1f))
                    ProfileStat("[n]", "Followers", Modifier.weight(1f))
                    ProfileStat("[n]", "Saves of your sheets", Modifier.weight(1f))
                }
                if (!app.isPro) ProfileGoPro()
                ProfileUploads(isPublic) { isPublic = it }
                ProfilePreferences(sharp = app.sharpNames, lefty = app.leftHanded, onSharp = { app.sharpNames = it }, onLefty = { app.leftHanded = it })
                ProfilePreviews()
                GlassButton("All settings", height = 48.dp) { app.push(Route.Settings) }
            }
        }
    }
}

@Composable
private fun ProfileIdentity(profile: Profile) {
    val app = LocalApp.current
    val initial = (profile.user.name.firstOrNull { it.isLetter() } ?: '?').uppercaseChar().toString()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(hex(0x1E3A8A)), contentAlignment = Alignment.Center) {
            Text(initial, style = outfit(28, FontWeight.Heavy), color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.user.name, style = outfit(22, FontWeight.Heavy), color = OC.ink)
            Text("@${profile.user.handle} · Guitar", style = outfit(14), color = OC.muted)
            Row(
                Modifier.padding(top = 2.dp).height(30.dp).clip(CircleShape).background(OC.goldTint).plainClickable { app.push(Route.Tiers) }
                    .padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryTierShield(profile.user.tier, fill = hex(profile.user.tier.colorHex), numeral = OC.inkFixed)
                Text("${profile.user.tier.title} · ${formatPoints(profile.points)} pts ›", style = outfit(13, FontWeight.Heavy),
                    color = if (isDark) hex(0xFFC940) else hex(0x6B4600))
            }
        }
    }
}

@Composable
private fun ProfileNextTierCard(profile: Profile) {
    val app = LocalApp.current
    val tier = profile.user.tier
    val next = tier.next
    val progress: Float = next?.let { (profile.points - tier.minPoints).toFloat() / (it.minPoints - tier.minPoints) } ?: 1f
    Column(
        Modifier.fillMaxWidth().inkGlass().plainClickable { app.push(Route.Tiers) }.semantics { contentDescription = "Opens tiers" }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(next?.let { "Next tier: ${it.title}" } ?: "Top tier reached", style = outfit(13, FontWeight.Bold), color = OC.paper)
            Spacer(Modifier.weight(1f))
            if (next != null) Text("${formatPoints(next.minPoints - profile.points)} pts to go", style = outfit(13, FontWeight.Bold), color = OC.gold)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(hex(0x2A2825))) {
            Box(Modifier.width(maxOf(8.dp, maxWidth * progress.coerceIn(0f, 1f))).fillMaxHeight().clip(CircleShape).background(OC.gold))
        }
        Text("Learning 860 · Contributing 380 — both count toward your tier", style = outfit(12), color = hex(0xBDB9B3))
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = 84.dp).glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = outfit(22, FontWeight.Heavy), color = OC.ink)
        Text(label, style = outfit(12), color = OC.muted)
    }
}

@Composable
private fun ProfileGoPro() {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.gold).plainClickable { app.modal = Modal.Pro }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Go Pro", style = outfit(16, FontWeight.Heavy), color = OC.inkFixed)
            Text("Offline songs, host jam rooms, unlimited chord checks", style = outfit(13), color = OC.inkFixed)
        }
        Text("7 days free ›", style = outfit(13, FontWeight.Heavy), color = OC.inkFixed, maxLines = 1)
    }
}

@Composable
private fun ProfileUploads(isPublic: Boolean, onPublic: (Boolean) -> Unit) {
    val app = LocalApp.current
    val song = MockData.sinSarPar
    val dash = OC.switchOff
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibrarySectionLabel("YOUR UPLOADS")
        Row(Modifier.fillMaxWidth().glass().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).plainClickable { app.push(Route.SongPage(song)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryKeyTile(song.key, size = 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(song.title, style = myanmar(16, FontWeight.SemiBold), color = OC.ink)
                    Text(if (isPublic) "Public · visible in Explore" else "Only me", style = outfit(12, FontWeight.Bold), color = if (isPublic) OC.tealText else OC.muted)
                }
            }
            LibraryTealSwitch(isPublic, label = "Public", onChange = onPublic)
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).drawBehind {
                drawRoundRect(dash, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))), cornerRadius = CornerRadius(14.dp.toPx()))
            }.plainClickable { libraryStartScan(app) },
            contentAlignment = Alignment.Center,
        ) { Text("+ Scan another sheet", style = outfit(14, FontWeight.Bold), color = OC.goldText) }
    }
}

@Composable
private fun ProfilePreferences(sharp: Boolean, lefty: Boolean, onSharp: (Boolean) -> Unit, onLefty: (Boolean) -> Unit) {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibrarySectionLabel("PLAYING PREFERENCES")
        GlassList {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chord names", style = outfit(15, FontWeight.SemiBold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(OC.softFill).padding(3.dp).semantics { contentDescription = "Chord names" }) {
                    Accidental("C♯", on = sharp) { onSharp(true) }
                    Accidental("D♭", on = !sharp) { onSharp(false) }
                }
            }
            Hairline()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Left-handed diagrams", style = outfit(15, FontWeight.SemiBold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                LibraryTealSwitch(lefty, label = "Left-handed diagrams", onChange = onLefty)
            }
            Hairline()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).plainClickable { app.switchTo(MainTab.Tuner) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Default tuning", style = outfit(15, FontWeight.SemiBold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Text("Standard ›", style = outfit(14, FontWeight.Bold), color = OC.goldText)
            }
            Hairline()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).plainClickable { app.push(Route.Settings) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("App language", style = outfit(15, FontWeight.SemiBold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Text(app.language, style = myanmar(14), color = OC.muted)
                Text("›", style = outfit(14), color = OC.muted)
            }
        }
    }
}

@Composable
private fun Accidental(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp)
            .then(if (on) Modifier.shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f)).clip(CircleShape).background(OC.surface.copy(alpha = 0.8f)) else Modifier)
            .plainClickable(onClick = onClick).semantics { contentDescription = if (on) "$label, selected" else label }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = mono(13), color = OC.ink) }
}

/** Android has no WidgetKit / Live Activities; these open design previews of a home-screen widget and an ongoing notification. */
@Composable
private fun ProfilePreviews() {
    val app = LocalApp.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibrarySectionLabel("WIDGET & NOTIFICATION PREVIEW")
        GlassList {
            ListRow("Home screen widgets", subtitle = "Groove · Chord of the day", icon = Icons.Outlined.GridView) { app.push(Route.Widgets) }
            Hairline()
            ListRow("Ongoing notification", subtitle = "Jam room · now / next chord", icon = Icons.Outlined.Notifications) { app.push(Route.LiveActivity) }
        }
    }
}

// MARK: - Another user's profile

private data class UserItem(val id: String, val title: String, val myanmar: Boolean, val sub: String, val key: String, val open: () -> Unit)

@Composable
fun UserProfileView(user: UserSummary) {
    val app = LocalApp.current
    var following by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showMore by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val uploads: List<UserItem> = remember {
        val easy = MockData.sinSarParEasy
        val capo = MockData.explore.firstOrNull { it.id == "c" }?.song
        val four = MockData.explore.firstOrNull { it.id == "e" }?.song
        buildList {
            add(UserItem(easy.id, easy.title, true, "${easy.artist} · simplified chords", easy.key) { app.push(Route.Shared(easy)) })
            if (capo != null) add(UserItem(capo.id, capo.title, false, "${capo.artist} · Capo 2", capo.key) { app.push(Route.Shared(capo)) })
            if (four != null) add(UserItem(four.id, four.title, false, "${four.artist} · 4 chords", four.key) { app.push(Route.Shared(four)) })
        }
    }
    val setlists: List<UserItem> = remember {
        listOf(UserItem("set", "[Setlist name]", false, "[n] songs · public setlist", "≡") { app.switchTo(MainTab.Setlists) })
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(onBack = { app.pop() }) {
                    Box {
                        Box(Modifier.size(44.dp).plainClickable { showMore = true }.semantics { contentDescription = "More" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            Text(user.name, style = outfit(13, FontWeight.Bold), color = OC.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            DropdownMenuItem(text = { Text("Share profile", style = outfit(15, FontWeight.Medium), color = OC.ink) },
                                onClick = { showMore = false; toast = Toast("Profile link copied", Icons.Filled.Link) })
                            DropdownMenuItem(text = { Text("Report user", style = outfit(15, FontWeight.Medium), color = OC.red) },
                                onClick = { showMore = false; toast = Toast("Thanks — we’ll take a look", Icons.Filled.Flag) })
                            DropdownMenuItem(text = { Text("Block", style = outfit(15, FontWeight.Medium), color = OC.red) },
                                onClick = { showMore = false; toast = Toast("${user.name} blocked", Icons.Outlined.Block) })
                        }
                    }
                }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UserHeader(user, following, onFollow = {
                        following = !following
                        if (following) toast = Toast("Following ${user.name}", Icons.Filled.HowToReg)
                    }, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))

                    Row(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UserTabButton("Uploads", on = tab == 0, Modifier.weight(1f)) { tab = 0 }
                        UserTabButton("Setlists", on = tab == 1, Modifier.weight(1f)) { tab = 1 }
                    }

                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (if (tab == 0) uploads else setlists).forEach { it ->
                            Row(
                                Modifier.fillMaxWidth().glass().plainClickable(onClick = it.open).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(OC.softFill), contentAlignment = Alignment.Center) {
                                    Text(it.key, style = mono(17), color = OC.ink)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(it.title, style = if (it.myanmar) myanmar(16, FontWeight.SemiBold) else outfit(16, FontWeight.SemiBold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(it.sub, style = if (it.myanmar) myanmar(12) else outfit(12), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val Tier.note: String
    get() = when (this) {
        Tier.OpenString -> "new player"
        Tier.Strummer -> "shares sheets"
        Tier.ChordKeeper -> "trusted uploads"
        Tier.SessionPlayer -> "can approve fixes"
        Tier.Maestro -> "helps moderate"
    }

@Composable
private fun UserHeader(user: UserSummary, following: Boolean, onFollow: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val avatar = if (user.name.startsWith("[")) "?" else (user.name.firstOrNull { it.isLetter() } ?: '?').uppercaseChar().toString()
    val tierColor = hex(user.tier.colorHex)
    val session = user.tier == Tier.SessionPlayer
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(hex(0x0F766E)), contentAlignment = Alignment.Center) {
            Text(avatar, style = outfit(34, FontWeight.Heavy), color = Color.White)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.name, style = outfit(24, FontWeight.Heavy), color = OC.ink, textAlign = TextAlign.Center)
            Text("[Instrument] · ${user.city ?: "[City]"}", style = outfit(14), color = OC.muted, textAlign = TextAlign.Center)
            Row(
                Modifier.padding(top = 2.dp).height(30.dp).clip(CircleShape).background(if (session) OC.blueTint else tierColor.copy(alpha = 0.14f))
                    .plainClickable { app.push(Route.Tiers) }.padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryTierShield(user.tier, fill = if (session) OC.blue else tierColor, numeral = Color.White)
                Text("${user.tier.title} · ${user.tier.note}", style = outfit(13, FontWeight.Heavy), color = if (session) OC.blue else tierColor)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            UserCount("${user.sheets}", "uploads")
            UserCount("${user.followers + (if (following) 1 else 0)}", "followers")
            UserCount("[n]", "saves")
        }
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clip(shape).background(if (following) OC.glass else OC.ink)
                .border(1.dp, if (following) OC.border else OC.ink, shape).plainClickable(onClick = onFollow)
                .semantics { contentDescription = if (following) "Following, selected" else "Follow" },
            contentAlignment = Alignment.Center,
        ) { Text(if (following) "Following" else "Follow", style = outfit(15, FontWeight.Bold), color = if (following) OC.ink else libraryOnInk) }
    }
}

@Composable
private fun UserCount(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = outfit(17, FontWeight.Heavy), color = OC.ink)
        Text(label, style = outfit(13), color = OC.muted)
    }
}

@Composable
private fun UserTabButton(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).background(if (on) OC.ink else OC.glass).border(1.dp, if (on) OC.ink else OC.border, shape)
            .plainClickable(onClick = onClick).semantics { contentDescription = if (on) "$label, selected" else label },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = outfit(13, FontWeight.Bold), color = if (on) libraryOnInk else OC.ink) }
}

// MARK: - Device mock pieces (Widgets / ongoing notification)

@Composable
private fun LibraryDeviceBackground(modifier: Modifier = Modifier, topGlow: Color = hex(0x6B4A1E)) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(hex(0x2A1F14), hex(0x14100C))))
        drawRect(Brush.radialGradient(listOf(topGlow, topGlow.copy(alpha = 0f)), center = Offset(0.2f * w, 0.1f * h), radius = w * 0.85f))
        drawRect(Brush.radialGradient(listOf(hex(0x3B2A57), hex(0x3B2A57, 0f)), center = Offset(0.9f * w, 0.9f * h), radius = w * 0.8f))
    }
}

/** Fake 9:41 status bar + close button for the device mock screens. */
@Composable
private fun LibraryDeviceChrome(onClose: () -> Unit, showTime: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(44.dp)) {
        if (showTime) {
            Row(Modifier.fillMaxSize().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("9:41", style = outfit(15, FontWeight.SemiBold), color = Color.White)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SignalCellular4Bar, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = if (showTime) 6.dp else 14.dp).offset(y = if (showTime) 40.dp else 0.dp)
                .size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Close preview" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LibraryAppIcon(color: Color, modifier: Modifier = Modifier, label: String? = "[App]", isOChords: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.semantics { contentDescription = if (isOChords) "O Chords" else "" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (isOChords) {
            Box(Modifier.size(62.dp).clip(shape).background(Color.White), contentAlignment = Alignment.Center) { LogoMark(50.dp, OC.inkFixed) }
        } else {
            Box(Modifier.size(62.dp).clip(shape).background(color))
        }
        if (label != null) Text(label, style = outfit(11), color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

private fun Modifier.widgetSurface(): Modifier {
    val shape = RoundedCornerShape(24.dp)
    return this.clip(shape).background(hex(0x1E1A16, 0.55f)).border(1.dp, Color.White.copy(alpha = 0.14f), shape)
}

// MARK: - Widgets preview

private val widgetRow2 = listOf(0x3B3530L, 0x2E3A4AL, 0x44392BL, 0x2B3F3AL, 0x3A2E4AL, 0x4A2E33L, 0x2E3A4AL, 0x3B3530L)
private val widgetDock = listOf(0x3B3530L, 0x2E3A4AL, 0x44392BL, 0x2B3F3AL)

/** Home-screen widget preview (Android: a design preview — there is no WidgetKit; a Glance widget would mirror this). */
@Composable
fun WidgetsPreviewView() {
    val app = LocalApp.current
    val profile = app.displayProfile
    Box(Modifier.fillMaxSize()) {
        LibraryDeviceBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LibraryDeviceChrome(onClose = { app.pop() }, showTime = false)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                    GrooveWidget(profile, Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LibraryAppIcon(Color.White, Modifier.weight(1f), label = "O Chords", isOChords = true)
                            LibraryAppIcon(hex(0x3B3530), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LibraryAppIcon(hex(0x2E3A4A), Modifier.weight(1f))
                            LibraryAppIcon(hex(0x3A2E4A), Modifier.weight(1f))
                        }
                    }
                }
                ChordOfTheDayWidget()
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    widgetRow2.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { LibraryAppIcon(hex(it), Modifier.weight(1f)) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).navigationBarsPadding().fillMaxWidth().height(92.dp)
                    .clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.14f)),
                verticalAlignment = Alignment.CenterVertically,
            ) { widgetDock.forEach { LibraryAppIcon(hex(it), Modifier.weight(1f), label = null) } }
        }
    }
}

@Composable
private fun GrooveWidget(profile: Profile, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val heights = listOf(14, 26, 20, 34, 22, 40, 8)
    Column(
        modifier.height(166.dp).widgetSurface().plainClickable { app.push(Route.Streak) }
            .semantics { contentDescription = "O Chords groove widget, ${profile.grooveDays} days" }.padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("GROOVE", style = outfit(12, FontWeight.Bold), color = hex(0xFFC940))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(OC.gold))
        }
        Spacer(Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 36.sp, fontWeight = FontWeight.Heavy)) { append("${profile.grooveDays}") }
                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))) { append(" days") }
            },
            style = outfit(36, FontWeight.Heavy), color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            heights.forEachIndexed { i, h ->
                Box(Modifier.width(9.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(if (i == heights.lastIndex) Color.White.copy(alpha = 0.2f) else OC.gold))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${profile.todayMinutes} of ${profile.dailyGoalMinutes} min today", style = outfit(12), color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun ChordOfTheDayWidget() {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().height(166.dp).widgetSurface().plainClickable { app.push(Route.Chords(focus = "G/B", songChords = MockData.sinSarPar.usedChords)) }
            .semantics { contentDescription = "Chord of the day widget: G/B" }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CHORD OF THE DAY", style = outfit(12, FontWeight.Bold), color = hex(0xFFC940))
            Text("G/B", style = mono(40), color = OC.gold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Used in 9 lines of ", style = outfit(13), color = Color.White.copy(alpha = 0.8f))
                Text("စဉ်းစားပါ", style = myanmar(13), color = Color.White.copy(alpha = 0.8f))
            }
            Box(Modifier.height(28.dp).clip(CircleShape).background(OC.gold).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Practise 2 min", style = outfit(12, FontWeight.Heavy), color = OC.inkFixed)
            }
        }
        LibraryMiniFretboard()
    }
}

/** Tiny G/B diagram from the widget (6 strings × 4 frets, two dots). */
@Composable
private fun LibraryMiniFretboard() {
    Canvas(Modifier.width(60.dp).height(68.dp)) {
        val s = 1.dp.toPx()
        val left = 4 * s; val top = 4 * s; val w = 50 * s; val h = 60 * s
        for (i in 0 until 5) {
            val y = top + i * 15 * s
            drawRect(Color.White.copy(alpha = if (i == 0) 1f else 0.3f), topLeft = Offset(left, y), size = Size(w, if (i == 0) 2 * s else 1 * s))
        }
        for (i in 0 until 6) {
            drawRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(left + i * 10 * s, top), size = Size(1 * s, h))
        }
        for ((x, y) in listOf(10f to 22f, 50f to 37f)) {
            drawOval(OC.gold, topLeft = Offset(x * s, y * s), size = Size(9 * s, 9 * s))
        }
    }
}

// MARK: - Ongoing-notification preview (iOS: Live Activity)

/** Lock-screen / ongoing-notification preview (Android: a design preview — Live Activities don't exist here). */
@Composable
fun LiveActivityPreviewView() {
    val app = LocalApp.current
    var target by remember { mutableStateOf(0.42f) }
    val progress by animateFloatAsState(target, tween(1500, easing = LinearEasing), label = "jamProgress")
    val dateLine = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK).format(LocalDate.now()) }

    // Gently advance the song progress so the activity feels live.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            target = if (target >= 0.95f) 0.1f else target + 0.03f
        }
    }

    Box(Modifier.fillMaxSize()) {
        LibraryDeviceBackground(topGlow = hex(0x4A3A2A))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(
                    Modifier.align(Alignment.Center).heightIn(min = 44.dp).plainClickable { app.push(Route.Jam(MockData.sinSarPar)) }
                        .semantics { contentDescription = "Jam room ongoing notification, compact. Now G/B, song 3 of 8" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(Modifier.height(36.dp).clip(CircleShape).background(Color.Black).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(52.dp)) {
                        Text("G/B", style = mono(13), color = OC.gold)
                        Text("3/8", style = outfit(12, FontWeight.Bold), color = Color.White.copy(alpha = 0.8f))
                    }
                }
                LibraryDeviceChrome(onClose = { app.pop() }, showTime = false, modifier = Modifier.align(Alignment.CenterEnd).width(60.dp))
            }

            Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dateLine, style = outfit(18, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f))
                Text("9:41", style = outfit(96, FontWeight.Bold, letterSpacing = (-3).sp), color = Color.White)
            }

            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))

            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                JamActivityCard(progress, Modifier.plainClickable { app.push(Route.Jam(MockData.sinSarPar)) }
                    .semantics { contentDescription = "O Chords Jam room notification. Song 3 of 8, စဉ်းစားပါ, key D, capo 2. Now G/B, next Am." })
                Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp).padding(top = 6.dp)) {
                    LockButton(Icons.Filled.FlashlightOn, "Flashlight")
                    Spacer(Modifier.weight(1f))
                    LockButton(Icons.Filled.PhotoCamera, "Camera")
                }
            }
        }
    }
}

@Composable
private fun JamActivityCard(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(hex(0x14110E, 0.72f)).border(1.dp, Color.White.copy(alpha = 0.12f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White), contentAlignment = Alignment.Center) { LogoMark(24.dp, OC.inkFixed) }
            Column(Modifier.weight(1f)) {
                Text("Jam room · Sunday set", style = outfit(13, FontWeight.Bold), color = Color.White)
                Text("4 players · led by [Band leader]", style = outfit(12), color = Color.White.copy(alpha = 0.65f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(hex(0xEF4444)))
                Text("LIVE", style = outfit(11, FontWeight.Heavy), color = hex(0xFCA5A5))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Song 3 of 8 · Key D · Capo 2 · Chorus", style = outfit(12), color = Color.White.copy(alpha = 0.65f))
                Text("စဉ်းစားပါ", style = myanmar(20, FontWeight.SemiBold), color = Color.White)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW", style = outfit(10, FontWeight.Heavy), color = Color.White.copy(alpha = 0.6f))
                    Text("G/B", style = mono(30), color = OC.gold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NEXT", style = outfit(10, FontWeight.Heavy), color = Color.White.copy(alpha = 0.6f))
                    Text("Am", style = mono(20), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.width(maxWidth * progress).fillMaxHeight().clip(CircleShape).background(OC.gold))
        }
    }
}

@Composable
private fun LockButton(icon: ImageVector, label: String) {
    Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
