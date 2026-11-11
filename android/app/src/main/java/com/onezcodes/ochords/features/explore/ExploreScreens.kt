package com.onezcodes.ochords.features.explore

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.app.tabBarInset
import com.onezcodes.ochords.design.AdBadge
import com.onezcodes.ochords.design.BottomBar
import com.onezcodes.ochords.design.GlassButton
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.Segmented
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.features.library.BannerAdView
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.CommunitySheet
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.model.Tier
import com.onezcodes.ochords.services.ChordTheory
import com.onezcodes.ochords.services.MockData
import kotlinx.coroutines.delay
import java.util.UUID

// MARK: - Shared helpers (private to the Explore group)

private enum class ExploreFilter(val label: String) {
    All("All"), Myanmar("Myanmar"), English("English"), Easy("Easy");

    fun matches(sheet: CommunitySheet): Boolean = when (this) {
        All -> true
        Myanmar -> sheet.song.isMyanmar
        English -> !sheet.song.isMyanmar
        Easy -> sheet.difficulty == "Beginner"
    }
}

/** Text on an ink-filled surface (paper in light, ink in dark). */
private val exploreOnInk: Color @Composable get() = if (isDark) hex(0x171614) else hex(0xFAFAF8)

/** Soft gold border used for the user's own sheets (#FFE3A3). */
private val exploreMineBorder: Color @Composable get() = if (isDark) hex(0xFFB800, 0.35f) else hex(0xFFE3A3)

/** Sheets the user uploaded themselves have no uploader. */
private val Song.isMine: Boolean get() = uploader == null
private val Song.usesMyanmarFont: Boolean get() = isMyanmar && !title.startsWith("[")
private val Song.titleFont: TextStyle get() = titleFont(16, FontWeight.SemiBold)
private fun Song.titleFont(size: Int, weight: FontWeight): TextStyle = if (usesMyanmarFont) myanmar(size, weight) else outfit(size, weight)
private val Song.artistFont: TextStyle get() = if (usesMyanmarFont) myanmar(12) else outfit(12)
private val Song.byName: String get() = uploader?.name ?: "You"
private val Song.byInitial: String get() = if (isMine) "Y" else "?"
private val Song.tier: Tier get() = uploader?.tier ?: MockData.me.tier
private val Song.metaLine: String get() = versionName ?: (if (isMine) "Imported from photo" else source.label)

/** Route used when a row is opened: own sheets open the song, others the shared preview. */
private val Song.exploreRoute: Route get() = if (isMine) Route.SongPage(this) else Route.Shared(this)

/** A copy moved by `t` semitones (key + every chord). */
private fun Song.transposed(t: Int): Song {
    if (t == 0) return this
    return copy(
        key = ChordTheory.transpose(key, t),
        sections = sections.map { sec ->
            sec.copy(lines = sec.lines.map { ChordTheory.transposeLine(it, t) }, bars = sec.bars.map { ChordTheory.transpose(it, t) })
        },
    )
}

private val exploreAvatarColors = listOf(0x0F766EL, 0x6D28D9L, 0xFFB800L, 0x4A4743L, 0x1E3A8AL)

private fun exploreAvatarColor(song: Song): Color {
    if (song.isMine) return hex(0x1E3A8A)
    val h = song.id.sumOf { it.code }
    return hex(exploreAvatarColors[h % exploreAvatarColors.size])
}

/** Ink-selected filter chip (Explore / Popular). */
@Composable
private fun ExploreFilterChip(text: String, isOn: Boolean, height: Dp = 36.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onClick).semantics { contentDescription = if (isOn) "$text, selected" else text }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.defaultMinSize(minWidth = 44.dp, minHeight = height).clip(shape).background(if (isOn) OC.ink else OC.glass)
                .border(1.dp, if (isOn) OC.ink else OC.border, shape).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = outfit(13, FontWeight.Bold), color = if (isOn) exploreOnInk else OC.ink) }
    }
}

@Composable
private fun ExploreFilterBar(filter: ExploreFilter, height: Dp = 36.dp, onSelect: (ExploreFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ExploreFilter.entries.forEach { f -> ExploreFilterChip(f.label, isOn = f == filter, height = height) { onSelect(f) } }
    }
}

/** Key tile (48dp rounded square with the key in mono). */
@Composable
private fun ExploreKeyTile(key: String, mine: Boolean, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(if (mine) OC.goldTint else OC.line), contentAlignment = Alignment.Center) {
        Text(key, style = mono(if (size > 44.dp) 17 else 15), color = if (mine) OC.goldText else OC.ink)
    }
}

@Composable
private fun ExploreTierShield(tier: Tier) {
    Icon(Icons.Filled.Shield, contentDescription = "${tier.title} tier", tint = hex(tier.colorHex), modifier = Modifier.size(12.dp))
}

/** Dashed placeholder square used by the ad cards. */
@Composable
private fun DashedTile(size: Dp, radius: Dp) {
    val dash = OC.faint.copy(alpha = 0.5f)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(OC.softFill).drawBehind {
            drawRoundRect(dash, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))), cornerRadius = CornerRadius(radius.toPx()))
        },
    )
}

// MARK: - Explore (tab root)

@Composable
fun ExploreView() {
    val app = LocalApp.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ExploreFilter.All) }
    var results by remember { mutableStateOf<List<CommunitySheet>>(emptyList()) }
    /** False until the first community fetch completes (shows the skeleton state). */
    var loaded by remember { mutableStateOf(false) }
    var popular by remember { mutableStateOf<List<CommunitySheet>>(emptyList()) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val trimmed = query.trim()
    val filtered = results.filter(filter::matches)
    val showNoResults = trimmed.isNotEmpty() && results.isEmpty()

    LaunchedEffect(query) {
        if (trimmed.isNotEmpty()) delay(300)
        val r = runCatching { app.backend.explore(query) }.getOrNull() ?: return@LaunchedEffect
        results = r
        loaded = true
    }
    LaunchedEffect(Unit) {
        if (popular.isEmpty()) runCatching { app.backend.popular() }.getOrNull()?.let { popular = it.take(4) }
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(if (showNoResults) 16.dp else 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Explore", style = outfit(36, FontWeight.Heavy, letterSpacing = (-1).sp), color = OC.ink)
                        if (loaded && !showNoResults) {
                            Text("Chord sheets shared by the O Chords community", style = outfit(13), color = OC.muted)
                        }
                    }
                    ExploreSearchField(
                        query = query, onQuery = { query = it }, highlighted = showNoResults || searchFocused,
                        focusRequester = focusRequester, onFocus = { searchFocused = it },
                    )
                    if (!loaded) {
                        Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(64, 80, 58, 72).forEach { w -> ExploreSkeletonBar(width = w.dp, height = 34.dp, radius = OC.rChip) }
                        }
                    } else if (!showNoResults) {
                        ExploreFilterBar(filter) { filter = it }
                    }
                }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when {
                        !loaded -> ExploreLoadingSkeleton()
                        showNoResults -> ExploreNoResults(
                            onUpload = { app.push(Route.ImportSheet) },
                            onRequest = { toast = Toast("Request sent to the community") },
                            onSpelling = { focusRequester.requestFocus() },
                            onArtist = { query = ""; focusRequester.requestFocus() },
                            onPopular = { app.push(Route.Popular) },
                        )
                        else -> ExploreFeed(trimmed = trimmed, filtered = filtered, popular = popular)
                    }
                    Spacer(Modifier.height(tabBarInset))
                }
            }
        }
    }
}

@Composable
private fun ExploreSearchField(query: String, onQuery: (String) -> Unit, highlighted: Boolean, focusRequester: FocusRequester, onFocus: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(OC.rButton)
    Row(
        Modifier.fillMaxWidth().height(46.dp).glass(OC.rButton)
            .then(if (highlighted) Modifier.border(1.dp, OC.gold, shape) else Modifier)
            .padding(start = 14.dp, end = if (query.isEmpty()) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query, onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = "Search shared chords" },
            textStyle = outfit(16).copy(color = OC.ink), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Song, artist or uploader…", style = outfit(16), color = OC.muted, maxLines = 1)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(Modifier.size(44.dp).plainClickable { onQuery("") }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(OC.softFill), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = OC.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// MARK: Feed

@Composable
private fun ExploreFeed(trimmed: String, filtered: List<CommunitySheet>, popular: List<CommunitySheet>) {
    val app = LocalApp.current
    val h = Modifier.padding(horizontal = 20.dp)

    fun versionCount(song: Song): Int =
        (MockData.explore.map { it.song } + app.library).filter { it.title == song.title && !it.title.startsWith("[") }.map { it.id }.toSet().size

    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (trimmed.isEmpty() && popular.isNotEmpty()) {
            ExplorePopularStrip(popular, Modifier.padding(bottom = 8.dp))
        }
        Row(h.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(if (trimmed.isEmpty()) "RECENTLY SHARED" else "${filtered.size} RESULTS", style = outfit(13, FontWeight.Bold, letterSpacing = 0.5.sp), color = OC.muted)
            Spacer(Modifier.weight(1f))
            Text("Newest first", style = outfit(12), color = OC.muted)
        }

        filtered.forEachIndexed { i, sheet ->
            androidx.compose.runtime.key(sheet.id) { ExploreSheetRow(sheet, versions = versionCount(sheet.song), modifier = h) }
            if (!app.isPro && filtered.size > 2 && (i == 1 || (i > 1 && (i - 1) % 8 == 0)) && i < filtered.size - 1) {
                ExploreNativeAd(h)
            }
        }

        if (filtered.isEmpty()) {
            Column(h.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No shared chords match yet", style = outfit(16, FontWeight.Bold), color = OC.ink, textAlign = TextAlign.Center)
                Text("Be the first — scan the sheet and share it.", style = outfit(13), color = OC.muted, textAlign = TextAlign.Center)
            }
        }

        ExploreShareCard(h.padding(top = 4.dp))
    }
}

@Composable
private fun ExplorePopularStrip(popular: List<CommunitySheet>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = OC.gold, modifier = Modifier.size(18.dp))
                Text("Popular this week", style = outfit(17, FontWeight.Heavy), color = OC.ink)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 44.dp).plainClickable { app.push(Route.Popular) }, contentAlignment = Alignment.Center) {
                Text("See chart ›", style = outfit(13, FontWeight.Bold), color = OC.goldText)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            popular.forEachIndexed { i, sheet -> ExplorePopularCard(rank = i + 1, song = sheet.song) { app.push(sheet.song.exploreRoute) } }
        }
    }
}

@Composable
private fun ExploreShareCard(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Row(
        modifier.fillMaxWidth().inkGlass().plainClickable { app.push(Route.ImportSheet) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(hex(0x2A2825)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Share, contentDescription = null, tint = OC.gold, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Share your chord sheets", style = outfit(15, FontWeight.Bold), color = OC.paper)
            Text("Songs you upload as Public show up here for everyone.", style = outfit(12), color = hex(0xBDB9B3))
        }
    }
}

/** Pulsing grey placeholder bar used by the loading skeleton. */
@Composable
private fun ExploreSkeletonBar(width: Dp? = null, height: Dp = 12.dp, radius: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "skeletonBar")
    val dim by transition.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dim")
    Box(
        Modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(height)
            .alpha(dim).clip(RoundedCornerShape(radius)).background(OC.ink.copy(alpha = 0.08f)),
    )
}

/** Explore — first load of community sheets. */
@Composable
private fun ExploreLoadingSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp).semantics { contentDescription = "Loading community chords" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 150.dp).glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            ExploreSkeletonBar(width = 170.dp, height = 18.dp, radius = 9.dp)
            ExploreSkeletonBar(width = 110.dp, height = 12.dp)
        }
        repeat(3) {
            Row(Modifier.fillMaxWidth().glass().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ExploreSkeletonBar(width = 52.dp, height = 52.dp, radius = OC.rButton)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExploreSkeletonBar(width = 160.dp, height = 14.dp, radius = 7.dp)
                    ExploreSkeletonBar(width = 104.dp, height = 10.dp, radius = 5.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = OC.gold, strokeWidth = 2.dp)
            Text("Loading community chords…", style = outfit(14, FontWeight.Bold), color = OC.muted)
        }
    }
}

@Composable
private fun ExplorePopularCard(rank: Int, song: Song, onClick: () -> Unit) {
    val (bg, fg, keyBg) = when {
        rank == 1 -> Triple(OC.inkFixed, OC.paper, hex(0x393733))
        song.isMine -> Triple(OC.goldTint, OC.goldText, OC.surface)
        else -> Triple(OC.surface, OC.ink, OC.line)
    }
    Column(
        Modifier.size(138.dp, 124.dp).clip(RoundedCornerShape(OC.rCard)).background(bg).plainClickable(onClick = onClick)
            .semantics { contentDescription = "Number $rank this week, ${song.title}" }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = outfit(26, FontWeight.Heavy), color = fg)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(CircleShape).background(keyBg).padding(horizontal = 7.dp, vertical = 3.dp)) { Text(song.key, style = mono(13), color = fg) }
        }
        Text(song.title, style = song.titleFont(15, FontWeight.Bold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(Modifier.alpha(0.8f), verticalAlignment = Alignment.CenterVertically) {
            Text(song.artist, style = song.artistFont, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" · [n] plays", style = outfit(12), color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun ExploreSheetRow(sheet: CommunitySheet, versions: Int, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val song = sheet.song
    val saved = app.isInLibrary(song.id)
    val shape = RoundedCornerShape(OC.rCard)
    Row(
        modifier.fillMaxWidth().clip(shape).background(OC.surface).border(1.dp, if (song.isMine) exploreMineBorder else OC.border, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.weight(1f).plainClickable { app.push(song.exploreRoute) }
                .semantics { contentDescription = "${song.title} by ${song.artist}, key ${song.key}, shared by ${song.byName}" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExploreKeyTile(song.key, mine = song.isMine)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song.title, style = song.titleFont, color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (versions > 1) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(OC.blueTint).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("$versions versions", style = outfit(11, FontWeight.Bold), color = OC.blue, maxLines = 1)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(song.artist, style = song.artistFont, color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" · ${song.metaLine}", style = outfit(12), color = OC.muted, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(exploreAvatarColor(song)), contentAlignment = Alignment.Center) {
                        Text(song.byInitial, style = outfit(10, FontWeight.Heavy), color = Color.White)
                    }
                    Text(song.byName, style = outfit(12, FontWeight.Bold), color = OC.ink, maxLines = 1)
                    ExploreTierShield(song.tier)
                    Text("· ${if (song.isMine) "just now" else "[time ago]"}", style = outfit(12), color = OC.muted, maxLines = 1)
                }
            }
        }

        val bookmarkShape = RoundedCornerShape(14.dp)
        Box(
            Modifier.size(44.dp).clip(bookmarkShape).background(if (saved) OC.inkFixed else OC.glass).border(1.dp, OC.border, bookmarkShape)
                .plainClickable { if (saved) app.removeFromLibrary(song.id) else app.addToLibrary(song) }
                .semantics { contentDescription = if (saved) "Remove from library" else "Save to library" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null, tint = if (saved) OC.gold else OC.ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** Native ad card in the Explore feed (placeholder until AdMob native ads are wired). */
@Composable
private fun ExploreNativeAd(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(OC.rCard)
    Column(
        modifier.fillMaxWidth().clip(shape).background(OC.surface).border(1.dp, OC.border, shape).padding(12.dp).semantics { contentDescription = "Advertisement" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashedTile(48.dp, 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdBadge()
                    Text("[Advertiser]", style = outfit(12), color = OC.muted)
                }
                Text("[Native ad headline]", style = outfit(15, FontWeight.Bold), color = OC.ink)
                Text("[Short ad body, up to 90 characters]", style = outfit(12).copy(lineHeight = 16.sp), color = OC.muted)
            }
            Box(Modifier.size(32.dp).plainClickable { app.push(Route.AdChoices) }.semantics { contentDescription = "Why this ad" }, contentAlignment = Alignment.TopEnd) {
                Box(Modifier.size(18.dp).border(1.dp, hex(0xC9C6C0), CircleShape), contentAlignment = Alignment.Center) {
                    Text("i", style = outfit(11, FontWeight.Heavy), color = OC.muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(OC.ink).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("[Call to action]", style = outfit(13, FontWeight.Bold), color = exploreOnInk)
            }
        }
    }
}

/** Explore — no results for the search. */
@Composable
private fun ExploreNoResults(onUpload: () -> Unit, onRequest: () -> Unit, onSpelling: () -> Unit, onArtist: () -> Unit, onPopular: () -> Unit) {
    val badge = RoundedCornerShape(34.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 30.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.size(104.dp).shadow(15.dp, badge, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
                .clip(badge).background(OC.goldTint.copy(alpha = 0.85f)).border(1.dp, OC.glassBorder, badge),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Search, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(44.dp)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No one has shared this yet", style = outfit(24, FontWeight.Bold, letterSpacing = (-0.5).sp), color = OC.ink, textAlign = TextAlign.Center)
            val ink = OC.ink
            Text(
                buildAnnotatedString {
                    append("Be the first — upload your sheet and earn ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append("+50 O Points") }
                    append(", or ask the community for it.")
                },
                style = outfit(15).copy(lineHeight = 21.sp), color = OC.muted, textAlign = TextAlign.Center,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton("Upload this song", leading = {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp))
            }, onClick = onUpload)
            GlassButton("Request it from the community", height = 54.dp, onClick = onRequest)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TRY INSTEAD", style = outfit(13, FontWeight.Bold), color = OC.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TryChip("Check the spelling", onSpelling)
                TryChip("Search by artist", onArtist)
            }
            TryChip("Popular this week", onPopular)
        }
    }
}

@Composable
private fun TryChip(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).glass(OC.rChip).plainClickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = outfit(14, FontWeight.SemiBold), color = OC.ink)
    }
}

// MARK: - Shared sheet (someone else's version)

@Composable
fun SharedSheetView(song: Song) {
    val app = LocalApp.current
    val context = LocalContext.current
    var current by remember { mutableStateOf(song) }
    var following by remember { mutableStateOf(false) }
    var showSetlistPicker by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    /** Every known version of this song (same title). */
    val versions: List<Song> = remember(song, app.library) {
        if (song.title.startsWith("[")) listOf(song) else {
            val seen = HashSet<String>(); val out = ArrayList<Song>()
            for (s in app.library + MockData.explore.map { it.song }) {
                if (s.title == song.title && seen.add(s.id)) out.add(s)
            }
            if (seen.add(song.id)) out.add(song)
            out.filter { it.isMine } + out.filter { !it.isMine }
        }
    }

    val chorus: SongSection? = current.sections.firstOrNull { it.kind == SectionKind.Chorus } ?: current.sections.firstOrNull { it.lines.isNotEmpty() }
    val chorusChords: List<String> = (chorus?.lines ?: emptyList()).flatMap { ChordLine.chords(it) }.distinct()

    fun add(list: Setlist) {
        val target = app.setlists.firstOrNull { it.id == list.id } ?: return
        if (current.id in target.songIDs) {
            toast = Toast("Already in ${target.note}", Icons.Filled.Info)
        } else {
            app.saveSetlist(target.copy(songIDs = target.songIDs + current.id))
            toast = Toast("Added to ${target.note.ifEmpty { target.name }}")
        }
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${current.title} — ${current.artist} · Key ${current.key} on O Chords")
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }, bottomPadding = 140.dp) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.padding(top = 10.dp)) {
                    TopBar(backLabel = "Back to Explore", onBack = { app.pop() }) {
                        Box(Modifier.size(44.dp).plainClickable { share() }.semantics { contentDescription = "Share link" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                SharedHeader(current, versions, following, onFollow = { following = !following }, onVersion = { current = it },
                    modifier = Modifier.padding(horizontal = 20.dp))

                SharedPreview(current, chorus, chorusChords, Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 14.dp))

                SharedBottomBar(
                    current = current, showSetlistPicker = showSetlistPicker, onPicker = { showSetlistPicker = it }, onAdd = ::add,
                    onSaved = { toast = Toast("Saved to your library") },
                    onSuggest = { toast = Toast("Thanks — the uploader will review it", Icons.Outlined.Edit) },
                )
            }
        }
    }
}

@Composable
private fun SharedHeader(current: Song, versions: List<Song>, following: Boolean, onFollow: () -> Unit, onVersion: (Song) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Myanmar glyphs carry tall line metrics; pull the meta line up to match the design.
        Column(verticalArrangement = Arrangement.spacedBy(if (current.usesMyanmarFont) (-10).dp else 0.dp)) {
            Text(current.title, style = current.titleFont(28, FontWeight.Bold), color = OC.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.artist, style = if (current.usesMyanmarFont) myanmar(14, FontWeight.SemiBold) else outfit(14, FontWeight.SemiBold), color = OC.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" · Key ${current.key} · ${current.versionName?.lowercase() ?: "from a photo"}", style = outfit(14), color = OC.muted, maxLines = 1)
            }
        }

        SharedUploaderCard(current, following, onFollow)

        if (versions.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("VERSIONS", style = outfit(12, FontWeight.Bold, letterSpacing = 0.5.sp), color = OC.muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    versions.forEach { v ->
                        val on = v.id == current.id
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(if (on) OC.ink else OC.glass).border(1.dp, if (on) OC.ink else OC.border, shape)
                                .plainClickable { onVersion(v) }.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (v.isMine) "Yours · Key ${v.key}" else "${v.byName} · Key ${v.key}", style = outfit(13, FontWeight.Bold),
                                color = if (on) exploreOnInk else OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedUploaderCard(current: Song, following: Boolean, onFollow: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().glass().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).plainClickable { current.uploader?.let { app.push(Route.User(it)) } ?: app.push(Route.Profile) }
                .semantics { contentDescription = if (current.isMine) "Your profile" else "Uploader ${current.byName}" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(exploreAvatarColor(current)), contentAlignment = Alignment.Center) {
                Text(current.byInitial, style = outfit(14, FontWeight.Heavy), color = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(current.byName, style = outfit(15, FontWeight.Bold), color = OC.ink)
                    ExploreTierShield(current.tier)
                }
                Text(current.uploader?.let { "${it.sheets} public uploads" } ?: "Public", style = outfit(12), color = OC.muted)
            }
        }
        if (!current.isMine) {
            Box(
                Modifier.heightIn(min = 44.dp).plainClickable(onClick = onFollow).semantics { contentDescription = if (following) "Following, selected" else "Follow" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(if (following) OC.glass else OC.ink).border(1.dp, if (following) OC.border else OC.ink, CircleShape).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (following) "Following" else "Follow", style = outfit(13, FontWeight.Bold), color = if (following) OC.ink else exploreOnInk) }
            }
        }
    }
}

@Composable
private fun SharedPreview(current: Song, chorus: SongSection?, chorusChords: List<String>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(OC.goldTint)
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(OC.gold).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text("${if (chorus?.kind == SectionKind.Chorus) "CHORUS" else (chorus?.label ?: "PREVIEW")} · PREVIEW", style = outfit(12, FontWeight.Heavy, letterSpacing = 0.5.sp), color = OC.inkFixed)
            }
            Spacer(Modifier.weight(1f))
            Text(chorusChords.joinToString(" "), style = mono(13), color = OC.goldText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        (chorus?.lines ?: emptyList()).forEach { line -> ExploreChordLineView(line, myanmar = current.isMyanmar) }
        Box(Modifier.heightIn(min = 44.dp).plainClickable { app.push(Route.SongPage(current)) }, contentAlignment = Alignment.CenterStart) {
            Text("Open full song ›", style = outfit(14, FontWeight.Bold), color = OC.goldText)
        }
    }
}

@Composable
private fun SharedBottomBar(current: Song, showSetlistPicker: Boolean, onPicker: (Boolean) -> Unit, onAdd: (Setlist) -> Unit, onSaved: () -> Unit, onSuggest: () -> Unit) {
    val app = LocalApp.current
    val saved = app.isInLibrary(current.id)
    BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(OC.rButton)
            Row(
                Modifier.weight(1f).heightIn(min = 50.dp).clip(shape).background(if (saved) OC.inkFixed else OC.gold)
                    .plainClickable { if (saved) app.removeFromLibrary(current.id) else { app.addToLibrary(current); onSaved() } }
                    .semantics { contentDescription = if (saved) "Saved to library, selected" else "Save to library" },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null, tint = if (saved) OC.gold else OC.inkFixed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (saved) "Saved to library" else "Save to library", style = outfit(15, FontWeight.Bold), color = if (saved) OC.gold else OC.inkFixed)
            }
            Box {
                Box(
                    Modifier.height(50.dp).clip(shape).background(OC.glass).border(1.dp, OC.border, shape).plainClickable { onPicker(true) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+ Setlist", style = outfit(14, FontWeight.Bold), color = OC.ink) }
                DropdownMenu(expanded = showSetlistPicker, onDismissRequest = { onPicker(false) }) {
                    Text("Add to setlist", style = outfit(13, FontWeight.Bold), color = OC.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    if (app.setlists.isEmpty()) {
                        Text("No setlists yet", style = outfit(14), color = OC.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    app.setlists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text("${list.name} · ${list.note}", style = outfit(15, FontWeight.Medium), color = OC.ink) },
                            onClick = { onPicker(false); onAdd(list) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Suggest a correction", style = outfit(13, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable(onClick = onSuggest))
            Spacer(Modifier.weight(1f))
            Text("Report", style = outfit(13, FontWeight.SemiBold), color = OC.muted, modifier = Modifier.plainClickable { app.push(Route.Report(current)) })
        }
    }
}

/** Chord-over-lyric line using `ChordLine.parse`, wrapping word by word. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreChordLineView(line: String, myanmar: Boolean) {
    FlowRow(Modifier.fillMaxWidth()) {
        ChordLine.parse(line).forEach { seg ->
            Column(Modifier.padding(end = if (seg.trailingSpace) 7.dp else 0.dp)) {
                Text(seg.chord ?: " ", style = mono(15), color = OC.goldText, maxLines = 1)
                Text(seg.text.ifEmpty { " " }, style = if (myanmar) myanmar(17) else outfit(17), color = OC.ink, maxLines = 1)
            }
        }
    }
}

// MARK: - Popular

private enum class PopularPeriod(val label: String) {
    Week("This week"), Month("This month"), AllTime("All time");

    /** Index permutation applied to the backend's ranking, so each period reads differently. */
    fun order(n: Int): List<Int> {
        val base = when (this) {
            Week -> listOf(2, 3, 0, 4, 1, 5)
            Month -> listOf(3, 2, 4, 5, 0, 1)
            AllTime -> listOf(5, 3, 1, 2, 4, 0)
        }
        val valid = base.filter { it < n }
        return valid + (0 until n).filter { it !in valid }
    }

    val moves: List<String>
        get() = when (this) {
            Week -> listOf("", "▲ 2", "NEW", "▼ 1", "▲ 5", "—", "▼ 2", "▲ 1", "NEW", "▼ 3")
            Month -> listOf("", "▲ 1", "—", "▲ 3", "▼ 2", "—", "▲ 4", "▼ 1", "—", "▼ 2")
            AllTime -> listOf("", "—", "—", "▲ 1", "▼ 1", "—", "—", "▲ 2", "▼ 1", "—")
        }
}

private data class Progression(val chords: String, val roman: String, val bg: Color, val fg: Color)

@Composable
fun PopularView() {
    val app = LocalApp.current
    var period by remember { mutableStateOf(PopularPeriod.Week) }
    var filter by remember { mutableStateOf(ExploreFilter.All) }
    var sheets by remember { mutableStateOf<List<CommunitySheet>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val ranked: List<CommunitySheet> = period.order(sheets.size).map { sheets[it] }.filter(filter::matches)
    val progressions = listOf(
        Progression("C G Am F", "I – V – vi – IV", OC.goldTint, OC.goldText),
        Progression("C Am F G", "I – vi – IV – V", OC.blueTint, OC.blue),
        Progression("C G Am Em F Dm C G", "The round chord", OC.tealTint, OC.tealText),
    )

    LaunchedEffect(Unit) {
        runCatching { app.backend.popular() }.getOrNull()?.let { sheets = it }
        loading = false
    }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(top = 10.dp)) { TopBar(title = "Popular on the community", backLabel = "Back to Explore", onBack = { app.pop() }) }

            Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(PopularPeriod.entries, period, label = { it.label }) { period = it }
                ExploreFilterBar(filter, height = 34.dp) { filter = it }
            }

            val h = Modifier.padding(horizontal = 20.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val top = ranked.firstOrNull()
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = OC.gold, strokeWidth = 2.dp)
                    }
                    top != null -> {
                        PopularTopCard(top.song, period, h)
                        ranked.drop(1).forEachIndexed { i, sheet ->
                            PopularRow(rank = i + 2, move = period.moves.getOrNull(i + 1) ?: "—", sheet = sheet, modifier = h)
                        }
                    }
                    else -> Text("No songs match this filter yet.", style = outfit(14), color = OC.muted, textAlign = TextAlign.Center,
                        modifier = h.fillMaxWidth().padding(vertical = 28.dp))
                }

                Row(h.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Popular chord progressions", style = outfit(17, FontWeight.Heavy), color = OC.ink)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.heightIn(min = 44.dp).plainClickable { app.push(Route.Keys) }, contentAlignment = Alignment.Center) {
                        Text("All keys ›", style = outfit(13, FontWeight.Bold), color = OC.goldText)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 4.dp).height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    progressions.forEach { p ->
                        Column(
                            Modifier.width(150.dp).fillMaxHeight().clip(RoundedCornerShape(OC.rCard)).background(p.bg).plainClickable { app.push(Route.Keys) }.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(p.chords, style = mono(14), color = p.fg, maxLines = 2)
                            Text(p.roman, style = outfit(12, FontWeight.Bold), color = p.fg.copy(alpha = 0.85f))
                            Text("in [n] songs", style = outfit(12), color = p.fg.copy(alpha = 0.85f))
                        }
                    }
                }

                Text("Ranked by plays, saves and setlist adds from the community. Private sheets never count.",
                    style = outfit(12).copy(lineHeight = 16.sp), color = OC.muted, modifier = h)

                BannerAdView(h.padding(top = 6.dp), onRemoveAds = { app.push(Route.AdChoices) })
            }
        }
    }
}

@Composable
private fun PopularTopCard(song: Song, period: PopularPeriod, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Row(
        modifier.fillMaxWidth().inkGlass().plainClickable { app.push(song.exploreRoute) }
            .semantics { contentDescription = "Number 1, ${song.title} by ${song.artist}" }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("1", style = outfit(52, FontWeight.Heavy), color = OC.gold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("MOST PLAYED ${period.label.uppercase()}", style = outfit(11, FontWeight.Heavy, letterSpacing = 0.6.sp), color = OC.gold)
            Text(song.title, style = song.titleFont(20, FontWeight.Bold), color = OC.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, style = if (song.usesMyanmarFont) myanmar(13) else outfit(13), color = hex(0xBDB9B3), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" · Key ${song.key} · [n] plays", style = outfit(13), color = hex(0xBDB9B3), maxLines = 1)
            }
        }
        Box(
            Modifier.size(48.dp).shadow(8.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
                .clip(CircleShape).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun PopularRow(rank: Int, move: String, sheet: CommunitySheet, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val song = sheet.song
    val moveColor = when {
        move.startsWith("▲") -> OC.teal
        move.startsWith("▼") -> OC.red
        move == "NEW" -> OC.goldText
        else -> OC.faint
    }
    val shape = RoundedCornerShape(OC.rCard)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(OC.surface).border(1.dp, if (song.isMine) exploreMineBorder else OC.border, shape)
            .plainClickable { app.push(song.exploreRoute) }
            .semantics { contentDescription = "Number $rank, ${song.title} by ${song.artist}${if (move.isEmpty() || move == "—") "" else ", $move"}" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("$rank", style = outfit(17, FontWeight.Heavy), color = OC.ink)
            Text(move, style = outfit(10, FontWeight.Heavy), color = moveColor, maxLines = 1)
        }
        ExploreKeyTile(song.key, mine = song.isMine, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(song.title, style = song.titleFont(15, FontWeight.Bold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, style = song.artistFont, color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" · ${sheet.difficulty} · [n] plays", style = outfit(12), color = OC.muted, maxLines = 1)
            }
        }
    }
}

// MARK: - Setlists (tab root)

@Composable
fun SetlistsView() {
    val app = LocalApp.current
    var currentID by remember { mutableStateOf<String?>(null) }
    /** Per-setlist key changes: "setlistID/songID" → semitones. */
    val shifts = remember { mutableStateMapOf<String, Int>() }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val current: Setlist? = app.setlists.firstOrNull { it.id == currentID } ?: app.setlists.firstOrNull()

    fun createSetlist() {
        val name = newName.trim()
        val list = Setlist(id = UUID.randomUUID().toString(), name = name.ifEmpty { "New setlist" }, note = "", songIDs = emptyList(), date = "Today")
        app.saveSetlist(list)
        currentID = list.id
        toast = Toast("Setlist created")
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Setlists", style = outfit(36, FontWeight.Heavy, letterSpacing = (-1).sp), color = OC.ink)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.height(44.dp).clip(CircleShape).background(OC.ink).plainClickable { newName = ""; showNew = true }
                            .semantics { contentDescription = "New setlist" }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ New", style = outfit(14, FontWeight.Bold), color = exploreOnInk) }
                }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 16.dp + tabBarInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (current != null) {
                        SetlistCurrentCard(
                            list = current, shifts = shifts,
                            onDelete = { app.deleteSetlist(current.id); currentID = null; toast = Toast("Setlist deleted") },
                        )
                    } else {
                        SetlistEmptyState()
                    }

                    val others = app.setlists.filter { it.id != current?.id }
                    if (others.isNotEmpty()) {
                        Text("OTHER SETLISTS", style = outfit(13, FontWeight.Bold, letterSpacing = 0.6.sp), color = OC.muted, modifier = Modifier.padding(top = 6.dp))
                        others.forEach { list ->
                            Row(
                                Modifier.fillMaxWidth().glass().plainClickable { currentID = list.id }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(OC.blueTint), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.FormatListBulleted, contentDescription = null, tint = OC.blue, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(list.name, style = outfit(16, FontWeight.Bold), color = OC.ink)
                                    Text(
                                        listOf(if (list.songIDs.size == 1) "1 song" else "${list.songIDs.size} songs", list.note).filter { it.isNotEmpty() }.joinToString(" · "),
                                        style = outfit(12), color = OC.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewSetlistDialog(name = newName, onName = { newName = it }, onCancel = { showNew = false }, onCreate = { showNew = false; createSetlist() })
    }
}

@Composable
private fun NewSetlistDialog(name: String, onName: (String) -> Unit, onCancel: () -> Unit, onCreate: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("New setlist", style = outfit(18, FontWeight.Bold), color = OC.ink)
            Text("Add songs from any sheet with “+ Setlist”.", style = outfit(13), color = OC.muted)
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(OC.rButton)).background(OC.softFill).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = outfit(16).copy(color = OC.ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Setlist name", style = outfit(16), color = OC.muted)
                        inner()
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Cancel", Modifier.weight(1f), height = 48.dp, onClick = onCancel)
                GoldButton("Create", Modifier.weight(1f), height = 48.dp, onClick = onCreate)
            }
        }
    }
}

@Composable
private fun SetlistEmptyState() {
    Column(Modifier.fillMaxWidth().glass().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No setlists yet", style = outfit(18, FontWeight.Bold), color = OC.ink)
        Text("Create one for your next gig, rehearsal or Sunday set.", style = outfit(13), color = OC.muted, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetlistCurrentCard(list: Setlist, shifts: MutableMap<String, Int>, onDelete: () -> Unit) {
    val app = LocalApp.current
    var menu by remember { mutableStateOf(false) }
    val songs: List<Pair<String, Song>> = list.songIDs.mapNotNull { id -> app.song(id)?.let { id to it } }
    fun shiftKey(songID: String) = "${list.id}/$songID"

    Box {
        Column(
            Modifier.fillMaxWidth().glass()
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}, onLongClick = { menu = true })
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(list.name, style = outfit(20, FontWeight.Heavy), color = OC.ink)
                    Text("${list.date ?: list.note} · ${songs.size} songs", style = outfit(13), color = OC.muted)
                }
                GoldButton("Play all", height = 40.dp, fullWidth = false, enabled = songs.isNotEmpty(), leading = {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp))
                }) {
                    songs.firstOrNull()?.let { (id, song) -> app.push(Route.Stage(song.transposed(shifts[shiftKey(id)] ?: 0))) }
                }
            }

            if (songs.isEmpty()) {
                Text("No songs yet — open any sheet and tap “+ Setlist”.", style = outfit(13), color = OC.muted, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Column {
                    songs.forEachIndexed { i, (id, song) ->
                        androidx.compose.runtime.key(id) {
                            SetlistSongRow(
                                number = i + 1, song = song, shift = shifts[shiftKey(id)] ?: 0,
                                onShift = { d -> shifts[shiftKey(id)] = ((shifts[shiftKey(id)] ?: 0) + d).coerceIn(-11, 11) },
                                onOpen = { app.push(song.exploreRoute) },
                                onRemove = { app.saveSetlist(list.copy(songIDs = list.songIDs.filter { it != id })) },
                            )
                        }
                    }
                }
            }

            Text("Keys changed here only affect this setlist — the original sheets stay the same.", style = outfit(12), color = OC.muted)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete setlist", style = outfit(15, FontWeight.Medium), color = OC.red) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = OC.red) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetlistSongRow(number: Int, song: Song, shift: Int, onShift: (Int) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val subtitle = buildString {
        when {
            song.isMine -> append("${song.artist} · original ${song.key}")
            song.uploader != null && !song.title.startsWith("[") -> append("${song.uploader.name}’s ${song.versionName?.lowercase() ?: "shared"} version")
            else -> append(song.artist)
        }
        if (shift != 0) append(" · ${if (shift > 0) "+" else ""}$shift")
    }
    val key = ChordTheory.transpose(song.key, shift)
    val line = OC.line

    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).drawBehind { drawRect(line, size = size.copy(height = 1.dp.toPx())) }
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen, onLongClick = { menu = true }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$number", style = mono(13), color = OC.faint, modifier = Modifier.width(22.dp))
            Column(Modifier.weight(1f).heightIn(min = 44.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(song.title, style = song.titleFont(15, FontWeight.SemiBold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = if (song.usesMyanmarFont && song.isMine) myanmar(12) else outfit(12), color = OC.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                StepButton(Icons.Filled.Remove, label = "Lower key of song $number") { onShift(-1) }
                Text(key, style = mono(14), color = OC.goldText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
                StepButton(Icons.Filled.Add, label = "Raise key of song $number") { onShift(1) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from setlist", style = outfit(15, FontWeight.Medium), color = OC.red) },
                leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = OC.red) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.width(40.dp).height(44.dp).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = OC.ink, modifier = Modifier.size(18.dp))
    }
}
