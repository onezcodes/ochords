package com.onezcodes.ochords.features.importsheet

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.NightBackground
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.model.CommunitySheet
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.model.SongSource
import com.onezcodes.ochords.services.ChordEstimator
import com.onezcodes.ochords.services.MicInput
import com.onezcodes.ochords.services.ScanResult
import com.onezcodes.ochords.services.SongListener
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private enum class ListenPhase { Ready, Listening, Live, Done }

/** The mic primer shows once per session, before the first listen. */
private object ListenSession {
    var askedMic = false
}

/** Fixed colours of the always-dark Listen screen (the Import screens keep their own copy). */
private object ListenNight {
    val text = OC.nightText
    val muted = OC.nightMuted
    val soft = hex(0xD6D4CF)
    val teal = hex(0x5EEAD4)
    val tealBg = hex(0x14B8A6, 0.18f)
    val goldText = hex(0xFFC940)
    val red = hex(0xFCA5A5)
    val redBg = hex(0xF87171, 0.08f)
    val blue = hex(0x93C5FD)
    val blueBg = hex(0x3B82F6, 0.18f)

    fun confidenceColor(c: Int): Color = if (c >= 80) teal else if (c >= 70) goldText else red
}

/** How long "Identifying the song" shows before the chords take over (s), if the identifier hasn't answered. */
private const val identifyFor = 12.0

private fun isMyanmarText(text: String): Boolean = text.any { it.code in 0x1000..0x109F }

private fun fontFor(text: String, size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
    if (isMyanmarText(text)) myanmar(size, weight) else outfit(size, weight)

/** Digits that don't shift the text as the clock runs. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private fun clockText(seconds: Double): String {
    val s = seconds.toInt()
    return "${s / 60}:" + "%02d".format(s % 60)
}

private fun folded(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private data class ResultSection(val name: String, val fg: Color, val bg: Color, val range: IntRange)

@Composable
fun ListenView() {
    val app = LocalApp.current
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(ListenPhase.Ready) }
    val listener = remember { SongListener() }
    var paused by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    var micOff by remember { mutableStateOf(false) }
    var askedSystem by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ChordEstimator.Summary?>(null) }
    /** A sheet of the matched song shared by the community, if there is one. */
    var community by remember { mutableStateOf<CommunitySheet?>(null) }

    val live = listener.live
    val match = listener.match
    val clock = clockText(live.seconds)
    val found = match as? SongListener.Match.Found

    // MARK: Result derivations

    val bars = summary?.bars ?: emptyList()
    val overallConfidence = if (bars.isEmpty()) 0 else bars.sumOf { it.confidence } / bars.size
    val lowChords = bars.filter { it.confidence < 70 }.map { it.chord }.distinct()

    /** The parts heard (A, B, C …), coloured so repeats are easy to spot. */
    val resultSections: List<ResultSection> = remember(bars) {
        val colours = listOf(ListenNight.teal to ListenNight.tealBg, OC.inkFixed to OC.gold, ListenNight.blue to ListenNight.blueBg)
        val parts = ChordEstimator.parts(bars.map { it.chord })
        if (parts.size == 1) listOf(ResultSection("CHORDS", ListenNight.teal, ListenNight.tealBg, parts[0].range))
        else parts.map { part ->
            val c = colours[(part.letter - 'A') % colours.size]
            ResultSection("PART ${part.letter}", c.first, c.second, part.range)
        }
    }

    val keyLine = summary?.takeIf { it.key.isNotEmpty() }?.let { s ->
        val key = if (s.key.endsWith("m")) "${s.key.dropLast(1)} minor" else "${s.key} major"
        " · Key $key" + (s.bpm?.let { " · $it BPM" } ?: "") + (if (s.capo == 0) " · No capo" else " · Capo ${s.capo}")
    } ?: ""

    // MARK: Actions

    fun startListening() {
        try {
            listener.start()
            micOff = false
            paused = false
            phase = ListenPhase.Listening
        } catch (e: MicInput.Failure) {
            micOff = true
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else micOff = true
    }

    fun begin() {
        if (!MicInput.hasPermission(context)) {
            if (!askedSystem) { askedSystem = true; permission.launch(Manifest.permission.RECORD_AUDIO) } else micOff = true
            return
        }
        startListening()
    }

    fun start() {
        if (!ListenSession.askedMic) {
            ListenSession.askedMic = true
            pendingStart = true
            app.modal = Modal.Permission(PermissionKind.Microphone)
            return
        }
        begin()
    }

    fun togglePause() {
        if (paused) {
            try { listener.start(); paused = false } catch (e: MicInput.Failure) { micOff = true }
        } else {
            listener.pause()
            paused = true
        }
    }

    fun restart() {
        listener.reset()
        summary = null
        community = null
        paused = false
        phase = ListenPhase.Ready
    }

    fun stop() {
        if (phase == ListenPhase.Listening && live.heard.isEmpty()) {
            restart()
            return
        }
        scope.launch {
            summary = listener.finish()
            phase = ListenPhase.Done
        }
    }

    /** A community sheet of the same song, to compare with the estimate. */
    suspend fun lookUpCommunity(title: String) {
        val sheets = runCatching { app.backend.explore(title) }.getOrDefault(emptyList())
        val same = folded(title)
        community = sheets.firstOrNull { folded(it.song.title) == same }
    }

    fun estimateSong(): Song {
        val secs = resultSections.map { s ->
            val label = s.name.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            SongSection(kind = SectionKind.Music, label = label, note = "${s.range.count()} bars", bars = s.range.map { bars[it].chord })
        }
        val title = found?.title ?: ""
        return Song(
            id = UUID.randomUUID().toString(), title = title, artist = found?.artist ?: "",
            key = summary?.key?.ifEmpty { "C" } ?: "C", tempo = summary?.bpm ?: 80,
            sections = secs, source = SongSource.Listened, isMyanmar = isMyanmarText(title),
        )
    }

    fun fixInReview() {
        app.push(Route.Review(ScanResult(song = estimateSong(), detectedZawgyi = false, lowConfidence = lowChords)))
    }

    fun saveAsSong() {
        // Not recognised: name it in Review first.
        val song = if (found != null) estimateSong() else { fixInReview(); return }
        app.addToLibrary(song)
        app.push(Route.SongPage(song))
    }

    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    // MARK: Effects

    // The mic primer is a full-screen modal that asks the system itself; carry on once it closes.
    val modal = app.modal
    LaunchedEffect(modal) {
        if (pendingStart && modal == null) { pendingStart = false; askedSystem = true; begin() }
    }
    LaunchedEffect(match) {
        if (phase == ListenPhase.Listening && match != SongListener.Match.Searching) phase = ListenPhase.Live
        if (match is SongListener.Match.Found) lookUpCommunity(match.title)
    }
    LaunchedEffect(live.seconds) {
        if (phase == ListenPhase.Listening && live.seconds >= identifyFor && live.chord != null) phase = ListenPhase.Live
    }
    // The microphone stops in the background: pause, and let the player carry on.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && listener.isRunning && (phase == ListenPhase.Listening || phase == ListenPhase.Live)) {
                listener.pause()
                paused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            listener.pause()
            listener.close()
        }
    }
    // Keep the screen on while listening.
    val listening = (phase == ListenPhase.Listening || phase == ListenPhase.Live) && !paused
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize()) {
        NightBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ListenTopBar(phase, paused) { listener.pause(); app.pop() }
            Crossfade(targetState = phase, animationSpec = tween(300), label = "listenPhase", modifier = Modifier.weight(1f)) { p ->
                when (p) {
                    ListenPhase.Ready -> ListenReady(micOff, onStart = ::start, onOpenSettings = ::openSettings)
                    ListenPhase.Listening -> ListenIdentifying(live, clock, onStop = ::stop)
                    ListenPhase.Live -> ListenLive(live, match, clock, paused, onTogglePause = ::togglePause, onStop = ::stop)
                    ListenPhase.Done -> ListenResult(
                        bars = bars, clock = clock, found = found, keyLine = keyLine, overallConfidence = overallConfidence,
                        sections = resultSections, community = community,
                        onCommunity = { c -> app.push(Route.Shared(c.song)) },
                        onSave = ::saveAsSong, onFix = ::fixInReview, onRestart = ::restart,
                    )
                }
            }
        }
    }
}

// MARK: Top bar

@Composable
private fun ListenTopBar(phase: ListenPhase, paused: Boolean, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = ListenNight.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            when (phase) { ListenPhase.Live -> "Live chords"; ListenPhase.Done -> "Estimated chords"; else -> "Listen" },
            style = outfit(16, FontWeight.Bold), color = ListenNight.text,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            if (phase == ListenPhase.Live && !paused) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(hex(0xEF4444, 0.25f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(hex(0xEF4444)))
                    }
                    Text("LIVE", style = outfit(12, FontWeight.Heavy, letterSpacing = 1.sp), color = ListenNight.red, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// MARK: Ready

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListenReady(micOff: Boolean, onStart: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(256.dp).clip(CircleShape).background(OC.gold.copy(alpha = 0.05f)))
            Box(Modifier.size(212.dp).clip(CircleShape).background(OC.gold.copy(alpha = 0.10f)))
            Box(
                Modifier.size(176.dp)
                    .shadow(25.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.45f), spotColor = hex(0xFFAA00, 0.45f))
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom)))
                    .plainClickable(onClick = onStart).semantics { contentDescription = "Start listening" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Mic, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(64.dp)) }
        }
        Column(Modifier.padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tap to listen", style = outfit(30, FontWeight.Bold, letterSpacing = (-0.8).sp), color = ListenNight.text)
            Text(
                "Play a song near your phone — from a speaker, the radio or a live band. We’ll estimate the chords, key and tempo as it plays, and you name it when you save.",
                style = outfit(15).copy(lineHeight = 21.sp), color = ListenNight.muted, textAlign = TextAlign.Center,
            )
        }
        if (micOff) {
            Column(Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The microphone is off for O Chords", style = outfit(14, FontWeight.Bold), color = ListenNight.red)
                Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onOpenSettings), contentAlignment = Alignment.Center) {
                    Text("Allow it in Settings ›", style = outfit(14, FontWeight.Bold), color = ListenNight.goldText)
                }
            }
        } else {
            FlowRow(
                Modifier.padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (f in listOf("Chords live", "Key & tempo", "Capo tip")) {
                    Row(
                        Modifier.height(34.dp).clip(CircleShape).background(OC.nightGlass).border(1.dp, OC.nightBorder, CircleShape).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("✓", style = outfit(13, FontWeight.SemiBold), color = ListenNight.teal)
                        Text(f, style = outfit(13, FontWeight.SemiBold), color = ListenNight.text)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Audio is analysed on your phone and never recorded. Nothing leaves the phone.",
            style = outfit(12), color = ListenNight.muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        )
    }
}

// MARK: Identifying

private val waveHeights = listOf(14.0, 26.0, 40.0, 30.0, 52.0, 34.0, 44.0, 22.0, 16.0)

@Composable
private fun ListenIdentifying(live: SongListener.Live, clock: String, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(250.dp).border(1.dp, OC.gold.copy(alpha = 0.14f), CircleShape))
            Box(Modifier.size(200.dp).border(1.dp, OC.gold.copy(alpha = 0.24f), CircleShape))
            Box(
                Modifier.size(150.dp).shadow(30.dp, CircleShape, ambientColor = OC.gold.copy(alpha = 0.35f), spotColor = OC.gold.copy(alpha = 0.35f))
                    .clip(CircleShape).background(OC.gold.copy(alpha = 0.12f)).border(1.dp, OC.gold.copy(alpha = 0.45f), CircleShape),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                waveHeights.forEachIndexed { i, h ->
                    val target = max(8.0, h * (0.55 + 0.45 * abs(sin((live.seconds + 1) * 1.3 + i))))
                    val height by animateDpAsState(target.dp, tween(250), label = "wave$i")
                    Box(Modifier.width(5.dp).height(height).clip(CircleShape).background(OC.gold))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Listening…", style = outfit(28, FontWeight.Bold, letterSpacing = (-0.6).sp), color = ListenNight.text)
            Text("Identifying the song · $clock", style = outfit(15).tabular(), color = ListenNight.muted)
        }
        Column(Modifier.fillMaxWidth().nightGlass(20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ALREADY HEARING", style = outfit(12, FontWeight.Bold, letterSpacing = 1.2.sp), color = ListenNight.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (segment in live.heard.takeLast(3)) {
                    Box(Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(OC.gold.copy(alpha = 0.16f)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(segment.chord, style = mono(17), color = ListenNight.goldText)
                    }
                }
                Box(Modifier.height(40.dp).dashedOutline(Color.White.copy(alpha = 0.25f), 10.dp, dashed = true).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("?", style = mono(17), color = ListenNight.muted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ListenGhostButton("Stop", height = 54.dp, onClick = onStop)
            Text("Tip: 1–2 m from the speaker works best", style = outfit(12), color = ListenNight.muted)
        }
    }
}

// MARK: Live

@Composable
private fun ListenLive(live: SongListener.Live, match: SongListener.Match, clock: String, paused: Boolean, onTogglePause: () -> Unit, onStop: () -> Unit) {
    val color = ListenNight.confidenceColor(live.confidence)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MatchedCard(live, match, clock)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EstimateTile("KEY", live.key.ifEmpty { "–" },
                if (live.key.isEmpty()) "listening…" else "${if (live.key.endsWith("m")) "minor" else "major"} · ${live.keyConfidence}%")
            EstimateTile("TEMPO", live.bpm?.toString() ?: "–", if (live.bpm == null) "finding the beat" else "BPM")
            EstimateTile("CAPO", if (live.key.isEmpty()) "–" else "${live.capo}", if (live.capo == 0) "none needed" else "for open shapes")
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(OC.gold.copy(alpha = 0.08f))
                .border(1.dp, OC.gold.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedContent(
                    targetState = live.heard.size, label = "chord",
                    transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.85f)) togetherWith fadeOut(tween(150)) },
                ) { _ ->
                    val chord = live.chord ?: "–"
                    val size = if (chord.length <= 3) 96 else max(48, 96 * 3 / chord.length)
                    Text(
                        chord, maxLines = 1, softWrap = false,
                        style = mono(size).copy(shadow = Shadow(OC.gold.copy(alpha = 0.35f), blurRadius = 20f)),
                        color = if (live.chord == null) ListenNight.muted else OC.gold,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (live.chord == null) {
                        Text(if (paused) "Paused" else "Waiting for music…", style = outfit(13, FontWeight.SemiBold), color = ListenNight.muted)
                    } else {
                        Box(Modifier.width(180.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(live.confidence / 100f).height(6.dp).clip(CircleShape).background(color))
                        }
                        Text("${live.confidence}% sure" + (if (live.confidence < 70) " · check later" else ""), style = outfit(13, FontWeight.SemiBold), color = color)
                    }
                }
            }
            Text("NOW", style = outfit(12, FontWeight.Heavy, letterSpacing = 1.4.sp), color = ListenNight.goldText,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp))
            live.next?.let { next ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ListenNight.muted)) { append("Next likely ") }
                        withStyle(SpanStyle(fontFamily = mono(13).fontFamily, fontWeight = FontWeight.Bold, color = ListenNight.text)) { append(next) }
                    },
                    style = outfit(13), modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("HEARD SO FAR", style = outfit(12, FontWeight.Bold, letterSpacing = 1.2.sp), color = ListenNight.muted)
                Spacer(Modifier.weight(1f))
                Text("dashed = check this one", style = outfit(12), color = ListenNight.muted)
            }
            val shown = live.heard.takeLast(6)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                shown.forEachIndexed { i, segment ->
                    ListenChordCell(chord = segment.chord, confidence = segment.confidence, current = i == shown.size - 1, showPercent = false)
                }
            }
        }
        Row(Modifier.padding(bottom = 16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ListenGhostButton(height = 54.dp, width = 54.dp, onClick = onTogglePause, label = if (paused) "Resume" else "Pause") {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = ListenNight.text, modifier = Modifier.size(22.dp))
            }
            GoldButton("Stop & review chords", modifier = Modifier.weight(1f), onClick = onStop)
        }
    }
}

@Composable
private fun MatchedCard(live: SongListener.Live, match: SongListener.Match, clock: String) {
    Row(Modifier.fillMaxWidth().nightGlass(20.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val key = live.key.ifEmpty { "?" }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(OC.goldTop, hex(0xE08A00)))),
            contentAlignment = Alignment.Center,
        ) { Text(key, style = mono(if (key.length <= 2) 18 else 13), color = OC.inkFixed, maxLines = 1, softWrap = false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            when (match) {
                is SongListener.Match.Found -> {
                    Text(match.title, style = fontFor(match.title, 17, FontWeight.SemiBold), color = ListenNight.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildAnnotatedString {
                            withStyle(fontFor(match.artist, 13).toSpanStyle()) { append(match.artist) }
                            withStyle(outfit(13).toSpanStyle()) { append(" · $clock") }
                        },
                        style = outfit(13).tabular(), color = ListenNight.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                SongListener.Match.Searching -> {
                    Text("Naming the song…", style = outfit(17, FontWeight.SemiBold), color = ListenNight.text)
                    Text("Listening · $clock", style = outfit(13).tabular(), color = ListenNight.muted)
                }
                SongListener.Match.NotFound -> {
                    Text("Song not recognised", style = outfit(17, FontWeight.SemiBold), color = ListenNight.text)
                    Text("You can name it when you save · $clock", style = outfit(13).tabular(), color = ListenNight.muted)
                }
            }
        }
        when (match) {
            is SongListener.Match.Found -> Box(Modifier.clip(CircleShape).background(ListenNight.tealBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text("Matched", style = outfit(12, FontWeight.Bold), color = ListenNight.teal)
            }
            SongListener.Match.Searching -> CircularProgressIndicator(color = OC.gold, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            SongListener.Match.NotFound -> {}
        }
    }
}

@Composable
private fun RowScope.EstimateTile(k: String, v: String, s: String) {
    Column(Modifier.weight(1f).nightGlass(16.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(k, style = outfit(11, FontWeight.Bold, letterSpacing = 1.sp), color = ListenNight.muted)
        Text(v, style = outfit(20, FontWeight.Heavy), color = ListenNight.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(s, style = outfit(11), color = ListenNight.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: Result

@Composable
private fun ListenResult(
    bars: List<ChordEstimator.Bar>, clock: String, found: SongListener.Match.Found?, keyLine: String, overallConfidence: Int,
    sections: List<ResultSection>, community: CommunitySheet?,
    onCommunity: (CommunitySheet) -> Unit, onSave: () -> Unit, onFix: () -> Unit, onRestart: () -> Unit,
) {
    val lowCount = bars.count { it.confidence < 70 }
    val noteBg = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(18.dp).height(2.dp).background(OC.gold))
                    Text("ESTIMATED FROM $clock OF AUDIO", style = outfit(12, FontWeight.SemiBold, letterSpacing = 1.9.sp), color = ListenNight.muted)
                }
                val title = found?.title ?: "Song not recognised"
                Text(title, style = fontFor(title, 28, FontWeight.Bold), color = ListenNight.text)
                val artist = found?.artist ?: "Name it when you save"
                Text(
                    buildAnnotatedString {
                        withStyle(fontFor(found?.artist ?: "", 14).toSpanStyle()) { append(artist) }
                        withStyle(outfit(14).toSpanStyle()) { append(keyLine) }
                    },
                    style = outfit(14), color = ListenNight.muted,
                )
            }
            if (bars.isEmpty()) {
                Text(
                    "No chords were clear enough to read. Hold the phone 1–2 m from the speaker, turn the music up a little, and listen again.",
                    style = outfit(14).copy(lineHeight = 20.sp), color = ListenNight.soft,
                    modifier = Modifier.fillMaxWidth().clip(noteBg).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(noteBg).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("$overallConfidence%", style = outfit(26, FontWeight.Heavy), color = ListenNight.confidenceColor(overallConfidence))
                    Text(
                        buildAnnotatedString {
                            if (lowCount > 0) {
                                append("overall confidence. ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ListenNight.text)) { append("$lowCount chord${if (lowCount == 1) "" else "s"}") }
                                append(" ${if (lowCount == 1) "is" else "are"} worth a quick check before you save.")
                            } else {
                                append("overall confidence. Every chord looks solid.")
                            }
                        },
                        style = outfit(13), color = ListenNight.soft, modifier = Modifier.weight(1f),
                    )
                }
            }

            for (sec in sections) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(sec.bg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(sec.name, style = outfit(12, FontWeight.Heavy, letterSpacing = 1.sp), color = sec.fg)
                    }
                    for (rowIndices in sec.range.toList().chunked(4)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in rowIndices) {
                                ListenChordCell(chord = bars[i].chord, confidence = bars[i].confidence, current = false, showPercent = true, modifier = Modifier.weight(1f))
                            }
                            repeat(4 - rowIndices.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            if (community != null) {
                Row(
                    Modifier.fillMaxWidth().nightGlass(16.dp).plainClickable { onCommunity(community) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(OC.gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Group, contentDescription = null, tint = ListenNight.goldText, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            buildAnnotatedString {
                                append("A community sheet exists · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${community.votes} votes") }
                            },
                            style = outfit(14), color = ListenNight.text,
                        )
                        Text("Compare with our estimate", style = outfit(14), color = ListenNight.muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ListenNight.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(hex(0x0B0A09, 0.7f))
                .drawBehind { drawRect(Color.White.copy(alpha = 0.08f), size = size.copy(height = 1.dp.toPx())) }
                .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (bars.isNotEmpty()) GoldButton(if (found == null) "Name it & save" else "Save to library", onClick = onSave)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bars.isNotEmpty()) ListenGhostButton("Fix chords", height = 48.dp, modifier = Modifier.weight(1f), onClick = onFix)
                ListenGhostButton("Listen again", height = 48.dp, modifier = Modifier.weight(1f), onClick = onRestart)
            }
        }
    }
}

// MARK: Helpers

/** A 1dp rounded outline, dashed (4, 3) when `dashed`. */
private fun Modifier.dashedOutline(color: Color, radius: Dp, dashed: Boolean): Modifier = drawBehind {
    val stroke = Stroke(
        width = 1.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
    )
    val inset = 0.5.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(radius.toPx()), style = stroke,
    )
}

/** Chord cell with confidence colouring: ≥80 teal · 70–79 gold · <70 red dashed "?". */
@Composable
private fun ListenChordCell(chord: String, confidence: Int, current: Boolean, showPercent: Boolean, modifier: Modifier = Modifier) {
    val low = confidence < 70
    val color = ListenNight.confidenceColor(confidence)
    val fg = if (current) ListenNight.goldText else if (low) ListenNight.red else ListenNight.text
    val border = when {
        current -> OC.gold
        low -> ListenNight.red.copy(alpha = 0.6f)
        showPercent -> color.copy(alpha = 0.35f)
        else -> OC.nightBorder
    }
    val fill = if (current) OC.gold.copy(alpha = 0.18f) else if (low) ListenNight.redBg else OC.nightGlass
    val base = modifier.clip(RoundedCornerShape(10.dp)).background(fill).dashedOutline(border, 10.dp, dashed = low && !current)
        .semantics { contentDescription = "$chord, $confidence percent sure" }
    if (showPercent) {
        Column(base.height(44.dp).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(chord + (if (low) "?" else ""), style = mono(15), color = fg, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text("$confidence%", style = outfit(10, FontWeight.Bold), color = color)
        }
    } else {
        Box(base.defaultMinSize(minWidth = 48.dp, minHeight = 44.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(chord + (if (low && !current) "?" else ""), style = mono(15), color = fg, maxLines = 1, softWrap = false)
        }
    }
}

/** Translucent white button on the dark screen; a fixed `width` makes it square (icon buttons). */
@Composable
private fun ListenGhostButton(
    height: Dp = 54.dp, width: Dp? = null, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(OC.rButton)
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .heightIn(min = height).alpha(if (pressed) 0.75f else 1f)
            .clip(shape).background(OC.nightGlass).border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ListenGhostButton(text: String, height: Dp = 54.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListenGhostButton(height = height, modifier = modifier, onClick = onClick) {
        Text(text, style = outfit(if (height > 50.dp) 16 else 15, FontWeight.Bold), color = ListenNight.text)
    }
}
