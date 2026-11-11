package com.onezcodes.ochords.features.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max as maxDp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.tabBarInset
import com.onezcodes.ochords.design.GlassList
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Hairline
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.services.ChordCheck
import com.onezcodes.ochords.services.ChordLibrary
import com.onezcodes.ochords.services.MicInput
import com.onezcodes.ochords.services.PitchDetector
import com.onezcodes.ochords.services.PitchTracker
import com.onezcodes.ochords.services.StrumListener
import com.onezcodes.ochords.services.TunerChime
import com.onezcodes.ochords.services.TuningSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// MARK: - Shared helpers (microphone screens)

/** Seconds on a monotonic clock (the tuning session and the trails are timed with it). */
private fun nowSeconds(): Double = System.nanoTime() / 1e9

private val tunerStringNames = listOf("E", "A", "D", "G", "B", "e")
private val tunerNightBorder = hex(0x393733)

/** Shows the microphone primer the first time a mic feature is used (permNote on the canvas). Same flag as the other tools. */
private object TunerMicPrimer {
    private const val KEY = "tools.micPrimerShown"
    /** True when the primer was put up (it asks for the permission itself). */
    fun showIfNeeded(context: Context, app: AppState): Boolean {
        val prefs = context.getSharedPreferences("ochords.prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return false
        prefs.edit().putBoolean(KEY, true).apply()
        app.modal = Modal.Permission(PermissionKind.Microphone)
        return true
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/** "Microphone is off for O Chords" card with a Settings link. */
@Composable
private fun MicOffCard(message: String, padding: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().glass(radius).padding(padding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(OC.redTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MicOff, contentDescription = null, tint = OC.red, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Microphone is off for O Chords", style = outfit(15, FontWeight.Bold), color = OC.ink)
            Text(message, style = outfit(12), color = OC.muted)
        }
        Box(Modifier.heightIn(min = 44.dp).plainClickable { openAppSettings(context) }, contentAlignment = Alignment.Center) {
            Text("Settings", style = outfit(14, FontWeight.Bold), color = OC.goldText)
        }
    }
}

// MARK: - 23 · Mic chord check (ready → listening → result)

private data class PracticeChord(
    val name: String,
    /** Six characters, low E to high e: "x" muted, "0" open, digits = fret. */
    val pattern: String,
)

private const val practiceFreeChecks = 5
private val practiceLabels = listOf("6 · E", "5 · A", "4 · D", "3 · G", "2 · B", "1 · e")
private val practiceHeights = listOf(8, 14, 22, 30, 18, 36, 26, 40, 20, 32, 16, 38, 28, 34, 12, 24, 30, 18, 10, 22, 14, 8)
private val practiceLibrary = listOf(
    PracticeChord("C", "x32010"),
    PracticeChord("G/B", "x20003"),
    PracticeChord("Am", "x02210"),
    PracticeChord("F", "xx3211"),
)

@Composable
fun PracticeView(chord: String) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val chords = remember(chord) {
        val i = practiceLibrary.indexOfFirst { it.name == chord }
        if (i >= 0) practiceLibrary else {
            val pattern = ChordLibrary.info(chord)?.voicings?.firstOrNull()?.pattern ?: "x32010"
            listOf(PracticeChord(chord, pattern)) + practiceLibrary.take(3)
        }
    }
    var c by remember(chord) { mutableIntStateOf(max(0, practiceLibrary.indexOfFirst { it.name == chord })) }
    /** What each string did on the last strum (null = nothing checked yet for this chord). */
    var verdicts by remember { mutableStateOf<List<ChordCheck.Verdict>?>(null) }
    var listening by remember { mutableStateOf(false) }
    var heardNothing by remember { mutableStateOf(false) }
    var micOff by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    var used by remember { mutableIntStateOf(0) }
    /** Whether this screen has already put up the system microphone prompt. */
    var askedSystem by remember { mutableStateOf(false) }
    val listener = remember { StrumListener() }

    val current = chords[c]
    val hasResult = verdicts != null && !listening
    val problems = verdicts?.let { v -> v.indices.filter { !v[it].isGood } } ?: emptyList()
    val clean = problems.isEmpty()
    val freeLeft = max(0, practiceFreeChecks - used)

    fun check(strum: StrumListener.Strum, pattern: String) {
        scope.launch {
            val found = withContext(Dispatchers.Default) { ChordCheck.verdicts(strum.samples, strum.sampleRate, pattern) }
            verdicts = found
            val good = found.all { it.isGood }
            run = if (good) run + 1 else 0
            if (!app.isPro) used = min(practiceFreeChecks, used + 1)
            haptic.performHapticFeedback(if (good) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        }
    }

    lateinit var listen: () -> Unit
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else micOff = true
    }
    listen = fun() {
        if (listening) return
        if (!app.isPro && freeLeft == 0) { app.modal = Modal.Pro; return }
        if (!MicInput.hasPermission(context)) {
            if (!askedSystem) { askedSystem = true; permission.launch(Manifest.permission.RECORD_AUDIO) } else micOff = true
            return
        }
        micOff = false
        heardNothing = false
        listening = true
        val pattern = current.pattern
        scope.launch {
            val strum: StrumListener.Strum?
            try {
                strum = listener.next()
            } catch (_: MicInput.Failure) {
                micOff = true; listening = false; return@launch
            }
            listening = false
            if (strum == null || chords[c].pattern != pattern) { heardNothing = strum == null; return@launch }
            check(strum, pattern)
        }
    }

    LaunchedEffect(Unit) { TunerMicPrimer.showIfNeeded(context, app) }
    DisposableEffect(Unit) { onDispose { listener.cancel(); listener.close() } }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Chord check", backLabel = "Back")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PracticeChips(chords, c) { i -> listener.cancel(); c = i; verdicts = null; heardNothing = false }
                if (micOff) MicOffCard("Chord check needs it to hear your strum. Sound stays on your phone.", padding = 14.dp, radius = OC.rCard)
                PracticeListenCard(current, verdicts != null, listening, heardNothing, hasResult, clean, problems.size, listen)
                AnimatedVisibility(hasResult, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    verdicts?.let { PracticeResult(current, it, problems, clean) }
                }
                PracticeFooter(run, freeLeft)
            }
        }
    }
}

@Composable
private fun PracticeChips(chords: List<PracticeChord>, c: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chords.forEachIndexed { i, ch ->
            val on = i == c
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.weight(1f).height(44.dp).clip(shape).background(if (on) OC.gold else OC.glass)
                    .border(1.dp, if (on) OC.gold else OC.border, shape).plainClickable { onPick(i) }
                    .semantics { contentDescription = ch.name + if (on) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(ch.name, style = mono(15), color = if (on) OC.inkFixed else OC.ink) }
        }
    }
}

@Composable
private fun PracticeListenCard(
    current: PracticeChord, checked: Boolean, listening: Boolean, heardNothing: Boolean, hasResult: Boolean, clean: Boolean, problems: Int,
    onListen: () -> Unit,
) {
    val statusText = when {
        listening -> "Listening… strum once"
        heardNothing -> "Didn’t hear a strum — try closer to the phone"
        !hasResult -> "Ready when you are"
        clean -> "Every string is clean"
        problems == 1 -> "1 string needs work"
        else -> "$problems strings need work"
    }
    val statusColor = when {
        listening -> OC.gold
        heardNothing -> OC.nightMuted
        hasResult -> if (clean) hex(0x5EEAD4) else hex(0xFCA5A5)
        else -> OC.nightMuted
    }
    Column(
        Modifier.fillMaxWidth().inkGlass().padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HOLD THE SHAPE, THEN STRUM ONCE", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.nightMuted)
        Text(current.name, style = mono(56), color = OC.gold)
        PracticeWave(listening, hasResult)
        Text(statusText, style = outfit(14, FontWeight.SemiBold), color = statusColor, textAlign = TextAlign.Center)
        GoldButton(
            if (!checked) "Listen" else "Strum again", height = 52.dp, fullWidth = false, enabled = !listening,
            modifier = Modifier.semantics { contentDescription = "Listen for a strum" },
            leading = { Icon(Icons.Outlined.Mic, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(16.dp)) },
            onClick = onListen,
        )
    }
}

/** The bars dance while listening (20 fps), settle to a still shape with a result, and lie flat otherwise. */
@Composable
private fun PracticeWave(listening: Boolean, hasResult: Boolean) {
    var t by remember { mutableStateOf(nowSeconds()) }
    LaunchedEffect(listening) {
        while (listening) { t = nowSeconds(); delay(50) }
    }
    Row(Modifier.width(220.dp).height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        practiceHeights.forEachIndexed { i, h ->
            val hh = when {
                listening -> h * (0.45 + 0.55 * abs(sin(t * 7 + i * 0.7)))
                hasResult -> max(4.0, (h * 0.35).roundToInt().toDouble())
                else -> 4.0
            }
            Box(Modifier.width(5.dp).height(max(4.0, hh).dp).background(if (listening) OC.gold else tunerNightBorder, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun PracticeResult(current: PracticeChord, verdicts: List<ChordCheck.Verdict>, problems: List<Int>, clean: Boolean) {
    val judged = verdicts.count { it != ChordCheck.Verdict.Unclear }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (clean) "Perfect ${current.name}!" else "Almost there", style = outfit(18, FontWeight.Heavy), color = OC.ink)
            Spacer(Modifier.weight(1f))
            Text("${judged - problems.size} / $judged strings", style = mono(13), color = OC.muted)
        }
        GlassList {
            current.pattern.take(6).forEachIndexed { i, v ->
                if (i > 0) Hairline()
                PracticeStringRow(i, v, verdicts.getOrNull(i) ?: ChordCheck.Verdict.Unclear)
            }
        }
        Text(
            practiceTip(current, verdicts, problems), style = outfit(14).copy(lineHeight = 20.sp), color = OC.ink,
            modifier = Modifier.fillMaxWidth().background(if (clean) OC.tealTint else OC.goldTint, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        )
        if (verdicts.contains(ChordCheck.Verdict.Unclear)) {
            Text(
                "“Can’t tell” strings play the same note as an overtone of a lower string, so the mic can’t hear them apart.",
                style = outfit(12).copy(lineHeight = 17.sp), color = OC.muted,
            )
        }
    }
}

/** Advice for the first string that went wrong (low strings first — they're usually the cause). */
private fun practiceTip(current: PracticeChord, verdicts: List<ChordCheck.Verdict>, problems: List<Int>) = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val i = problems.firstOrNull()
    val v = i?.let { verdicts.getOrNull(it) }
    if (i == null || v == null) {
        withStyle(bold) { append("Nice.") }
        append(" Now try switching to the next chord without looking.")
        return@buildAnnotatedString
    }
    val name = if (tunerStringNames[i] == "e") "High e" else tunerStringNames[i]
    val fret = current.pattern[i]
    when {
        v == ChordCheck.Verdict.Ringing -> {
            withStyle(bold) { append("$name string should be muted.") }
            append(" Let a fretting finger rest lightly against it, or start your strum on the next string.")
        }
        fret == '0' -> {
            withStyle(bold) { append("$name string isn’t ringing.") }
            append(" A finger is touching it — arch your fingers so the open string can ring.")
        }
        else -> {
            withStyle(bold) { append("$name string isn’t ringing.") }
            append(" Press just behind the fret wire and keep the fingertip upright so it doesn’t touch the next string.")
        }
    }
}

@Composable
private fun PracticeStringRow(i: Int, v: Char, verdict: ChordCheck.Verdict) {
    val expect = when (v) { 'x' -> "Should be muted"; '0' -> "Open string"; else -> "Fret $v" }
    val (word, fg, bg) = when (verdict) {
        ChordCheck.Verdict.Clear -> Triple("Clear ✓", OC.tealText, OC.tealTint)
        ChordCheck.Verdict.Muted -> Triple("Muted ✓", OC.tealText, OC.tealTint)
        ChordCheck.Verdict.Dead -> Triple("Not ringing", OC.red, OC.redTint)
        ChordCheck.Verdict.Ringing -> Triple("Ringing", OC.red, OC.redTint)
        ChordCheck.Verdict.Unclear -> Triple("Can’t tell", OC.muted, OC.glass)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp).semantics { contentDescription = "${practiceLabels[i]}, $expect, $word" },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(practiceLabels[i], style = mono(13), color = OC.muted, modifier = Modifier.width(44.dp))
        Text(expect, style = outfit(14), color = OC.ink)
        Spacer(Modifier.weight(1f))
        Text(word, style = outfit(13, FontWeight.Heavy), color = fg, modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun PracticeFooter(run: Int, freeLeft: Int) {
    val app = LocalApp.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("Clean in a row: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = OC.ink)) { append("$run") }
                },
                style = outfit(13), color = OC.muted,
            )
            Spacer(Modifier.weight(1f))
            if (app.isPro) {
                Text("Pro · unlimited checks", style = outfit(13, FontWeight.Bold), color = OC.goldText)
            } else {
                Box(Modifier.heightIn(min = 44.dp).plainClickable { app.modal = Modal.Pro }, contentAlignment = Alignment.Center) {
                    Text("$freeLeft free checks left today · Pro ›", style = outfit(13, FontWeight.Bold), color = OC.goldText)
                }
            }
        }
        Text(
            "Chord check listens through your microphone on this phone only. Nothing is recorded or uploaded.",
            style = outfit(12).copy(lineHeight = 17.sp), color = OC.muted,
        )
    }
}

// MARK: - 6 · Guitar tuner (tab root)

private data class TunerNote(val note: String, val octave: Int, val hz: Double)

private class TunerPreset(val name: String, val notes: List<TunerNote>) {
    companion object {
        val all = listOf(
            TunerPreset("Standard", listOf(TunerNote("E", 2, 82.41), TunerNote("A", 2, 110.0), TunerNote("D", 3, 146.83), TunerNote("G", 3, 196.0), TunerNote("B", 3, 246.94), TunerNote("E", 4, 329.63))),
            TunerPreset("Drop D", listOf(TunerNote("D", 2, 73.42), TunerNote("A", 2, 110.0), TunerNote("D", 3, 146.83), TunerNote("G", 3, 196.0), TunerNote("B", 3, 246.94), TunerNote("E", 4, 329.63))),
            TunerPreset("½ step ↓", listOf(TunerNote("E♭", 2, 77.78), TunerNote("A♭", 2, 103.83), TunerNote("D♭", 3, 138.59), TunerNote("G♭", 3, 185.0), TunerNote("B♭", 3, 233.08), TunerNote("E♭", 4, 311.13))),
            TunerPreset("Open G", listOf(TunerNote("D", 2, 73.42), TunerNote("G", 2, 98.0), TunerNote("D", 3, 146.83), TunerNote("G", 3, 196.0), TunerNote("B", 3, 246.94), TunerNote("D", 4, 293.66))),
        )
    }
}

private const val tunerSoundsKey = "tuner.sounds"

@Composable
fun TunerView() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("ochords.prefs", Context.MODE_PRIVATE) }

    var preset by remember { mutableIntStateOf(0) }
    var a4 by remember { mutableIntStateOf(440) }
    /** Which string is showing, how far off it is and which strings are done (see `TuningSession`). */
    val session = remember { TuningSession(TunerPreset.all[0].notes.map { it.hz }) }
    val detector = remember { PitchDetector() }
    var micOff by remember { mutableStateOf(false) }
    val trail = remember { TunerTrail() }
    val chime = remember { TunerChime() }
    var sounds by remember { mutableStateOf(prefs.getBoolean(tunerSoundsKey, true)) }
    /** Goes up each time a string comes into tune (drives the ring burst). */
    var tunedPulse by remember { mutableIntStateOf(0) }
    /** When all six strings were last tuned (drives the celebration), null when it's not showing. */
    var celebratedAt by remember { mutableStateOf<Double?>(null) }
    /** The app is in front (between onResume and onPause). */
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    /** Whether this screen has already put up the system microphone prompt (the primer counts). */
    var askedSystem by remember { mutableStateOf(false) }

    val tolerance = TuningSession.tolerance
    val notes = TunerPreset.all[preset].notes
    val sel = session.selected
    val auto = session.auto
    val tuned = session.tuned
    val cents = session.cents
    val c = max(-50.0, min(50.0, cents[sel] ?: 0.0))
    val playing = session.isPlaying
    val inTune = playing && abs(c) <= tolerance
    fun target(i: Int): Double = notes[i].hz * a4 / 440.0
    val hz = session.heard ?: target(sel)

    /** With a string picked by hand, the detector listens only around that string's note. */
    fun syncTarget() { detector.setTarget(if (session.auto) null else target(session.selected)) }

    /** A new tuning or reference pitch: every string needs checking again. */
    fun retune() {
        session.reset(notes.indices.map { target(it) })
        celebratedAt = null
        trail.clear()
        syncTarget()
    }

    /** A string just came into tune: ding, ring burst and a tap — or, for the last string, the full celebration. */
    fun stringTuned(all: Boolean) {
        tunedPulse += 1
        if (sounds) chime.play(if (all) TunerChime.Kind.AllStrings else TunerChime.Kind.OneString, detector)
        if (all) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            val now = nowSeconds()
            celebratedAt = now
            scope.launch {
                delay((tunerCelebrationDuration * 1000).toLong())
                if (celebratedAt == now) celebratedAt = null
            }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun hear(reading: PitchTracker.Reading?) {
        val wasPlaying = session.isPlaying
        val events = session.hear(reading?.hz, nowSeconds())
        if (events.any { it is TuningSession.Event.Switched }) trail.clear()
        if (session.isPlaying) trail.add(session.cents[session.selected]) else if (wasPlaying) trail.add(null)
        for (event in events) if (event is TuningSession.Event.Tuned) stringTuned(event.all)
    }

    /** Goes up on each answer to the system prompt, so listening is retried. */
    var permissionAnswers by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) micOff = true
        permissionAnswers += 1
    }

    /** The mic runs only while the Tuner tab is on screen, the app is in front and no sheet covers it. */
    fun updateListening() {
        val wanted = app.tab == MainTab.Tuner && resumed && app.modal == null
        if (!wanted) { chime.stop(); detector.stop(); return }
        if (detector.isRunning) return
        if (!MicInput.hasPermission(context)) {
            if (!askedSystem) { askedSystem = true; permission.launch(Manifest.permission.RECORD_AUDIO) } else micOff = true
            return
        }
        micOff = false
        syncTarget()
        detector.onReading = { hear(it) }
        try { detector.start() } catch (_: MicInput.Failure) { micOff = true }
    }

    // The tab is built only when it's opened, so ask for the mic as soon as it appears.
    LaunchedEffect(Unit) { if (app.tab == MainTab.Tuner && TunerMicPrimer.showIfNeeded(context, app)) askedSystem = true }
    LaunchedEffect(app.tab, app.modal, resumed, permissionAnswers) { updateListening() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chime.stop()
            detector.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { detector.close(); chime.close() } }

    OCPage {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 12.dp + tabBarInset),
        ) {
            TunerHeader(
                subtitle = if (micOff) "Microphone needed" else if (auto) "Listening — pluck any string" else "Play the ${notes[sel].note} string",
                sounds = sounds, auto = auto,
                onSounds = { sounds = !sounds; prefs.edit().putBoolean(tunerSoundsKey, sounds).apply() },
                onAuto = { session.auto = !session.auto; syncTarget() },
            )
            if (micOff) MicOffCard("The tuner needs it to hear your strings. Sound stays on your phone.", padding = 12.dp, radius = 16.dp, modifier = Modifier.padding(top = 14.dp))
            TunerPresets(preset, Modifier.padding(top = 14.dp)) { preset = it; retune() }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).glass()) {
                TunerTrace(
                    trail = trail, note = notes[sel].note, octave = notes[sel].octave,
                    cents = cents[sel]?.let { max(-50.0, min(50.0, it)) }, live = playing, tolerance = tolerance,
                    isTuned = sel in tuned, pulse = tunedPulse,
                )
                TunerCelebration(celebratedAt, Modifier.matchParentSize())
            }
            TunerStatusRow(playing, cents[sel], c, inTune, hz, Modifier.padding(top = 12.dp))
            TunerStrings(notes, sel, tuned, Modifier.padding(top = 18.dp)) { session.select(it); syncTarget(); trail.clear() }
            TunerFooter(a4, tuned.size, Modifier.padding(top = 2.dp)) { a4 = it; retune() }
        }
    }
}

@Composable
private fun TunerHeader(subtitle: String, sounds: Boolean, auto: Boolean, onSounds: () -> Unit, onAuto: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Tuner", style = outfit(36, FontWeight.Heavy, letterSpacing = (-1).sp), color = OC.ink)
            Text(subtitle, style = outfit(13), color = OC.muted)
        }
        Box(
            Modifier.size(44.dp).background(OC.glass, CircleShape).border(1.dp, OC.border, CircleShape).plainClickable(onClick = onSounds)
                .semantics { contentDescription = if (sounds) "Tuning sounds on" else "Tuning sounds off" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (sounds) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null,
                tint = if (sounds) OC.ink else OC.muted, modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(if (auto) OC.ink else OC.glass).border(1.dp, if (auto) OC.ink else OC.border, CircleShape)
                .plainClickable(onClick = onAuto).semantics { contentDescription = "Auto detect string" + if (auto) ", selected" else "" }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (auto) OC.background else OC.ink
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(if (auto) "Auto" else "Manual", style = outfit(14, FontWeight.Bold), color = fg)
        }
    }
}

@Composable
private fun TunerPresets(preset: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TunerPreset.all.forEachIndexed { i, p ->
            val on = i == preset
            val shape = RoundedCornerShape(14.dp)
            Box(Modifier.weight(1f).height(44.dp).plainClickable { onPick(i) }.semantics { contentDescription = p.name + if (on) ", selected" else "" }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(36.dp).clip(shape).background(if (on) OC.ink else OC.glass).border(1.dp, if (on) OC.ink else OC.border, shape).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.name, style = outfit(12, FontWeight.Bold), color = if (on) OC.background else OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TunerStatusRow(playing: Boolean, offset: Double?, c: Double, inTune: Boolean, hz: Double, modifier: Modifier = Modifier) {
    val status = when {
        !playing -> if (offset == null) "Pluck a string" else "Pluck again"
        inTune -> "In tune"
        c < 0 -> "Too low · tighten ↑"
        else -> "Too high · loosen ↓"
    }
    val fg = if (!playing) OC.muted else if (inTune) OC.tealText else OC.goldText
    val bg = if (!playing) OC.softFill else if (inTune) OC.tealTint else OC.goldTint
    // The true offset (the bubble stops at ±50 ¢, the number doesn't).
    val offsetText = offset?.let { off ->
        val r = off.roundToInt()
        (if (r > 0) "+" else if (r < 0) "−" else "±") + "${abs(r)}¢"
    } ?: "–"
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = "$status, $offsetText, ${"%.1f".format(Locale.ROOT, hz)} Hz" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(34.dp).background(bg, CircleShape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(status, style = outfit(14, FontWeight.Heavy), color = fg)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(offsetText, style = mono(13), color = if (playing) OC.ink else OC.muted)
            Text("%.1f Hz".format(Locale.ROOT, hz), style = mono(13), color = OC.muted)
        }
    }
}

@Composable
private fun TunerStrings(notes: List<TunerNote>, sel: Int, tuned: Set<Int>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { for (i in listOf(2, 1, 0)) TunerStringButton(notes[i], i, i == sel, i in tuned) { onSelect(i) } }
        TunerHeadstock(Modifier.size(120.dp, 230.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { for (i in listOf(3, 4, 5)) TunerStringButton(notes[i], i, i == sel, i in tuned) { onSelect(i) } }
    }
}

@Composable
private fun TunerStringButton(n: TunerNote, i: Int, on: Boolean, tuned: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).plainClickable(onClick = onClick)
            .semantics { contentDescription = "String ${6 - i}, ${n.note}${n.octave}" + (if (tuned) ", tuned" else "") + (if (on) ", selected" else "") },
    ) {
        Box(
            Modifier.matchParentSize().background(if (on) OC.gold else OC.glass, CircleShape).border(2.dp, if (on) OC.gold else OC.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(n.note, style = outfit(20, FontWeight.Heavy), color = if (on) OC.inkFixed else OC.ink) }
        AnimatedVisibility(
            tuned, modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut(),
        ) { TunerTick(18.dp, 8.dp) }
    }
}

/** Teal tick badge with a paper ring. */
@Composable
private fun TunerTick(size: Dp, icon: Dp) {
    Box(Modifier.size(size).background(OC.teal, CircleShape).border(2.dp, OC.paper, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(icon))
    }
}

@Composable
private fun TunerFooter(a4: Int, tunedCount: Int, modifier: Modifier = Modifier, onA4: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A4 =", style = outfit(13), color = OC.muted)
            TunerA4Button("−", "Lower reference pitch") { onA4(max(430, a4 - 1)) }
            Text("$a4 Hz", style = mono(13), color = OC.ink)
            TunerA4Button("+", "Raise reference pitch") { onA4(min(450, a4 + 1)) }
        }
        Spacer(Modifier.weight(1f))
        Text("$tunedCount / 6 tuned", style = outfit(13, FontWeight.Bold), color = OC.tealText)
    }
}

@Composable
private fun TunerA4Button(s: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).glass(14.dp), contentAlignment = Alignment.Center) {
            Text(s, style = outfit(16), color = OC.ink)
        }
    }
}

/** Recent tuner readings for the pitch trail, newest last. Compose state, so a reading redraws only the trail. */
private class TunerTrail {
    class Point(
        val time: Double,
        /** Offset from the target string in cents; null marks a break (the note stopped). */
        val cents: Double?,
    )

    var points: List<Point> by mutableStateOf(emptyList())
        private set

    fun add(cents: Double?) {
        val now = nowSeconds()
        var next = points + Point(now, cents)
        val first = next.indexOfFirst { now - it.time < span }
        if (first > 0) next = next.drop(first)
        points = next
    }

    fun clear() { points = emptyList() }

    companion object {
        /** How much history the trail shows. */
        const val span = 2.5
    }
}

private const val traceRange = 50.0
private val traceBubble = 64.dp
private val traceTop = 34.dp
/** Dp the trail scrolls down per second. */
private val traceSpeed = 80.dp

/**
 * Live pitch trail: the note bubble slides towards ♭ or ♯ as the string is off, and the line under it shows where
 * the pitch has been over the last couple of seconds — so turning the peg the wrong way is obvious at a glance.
 */
@Composable
private fun TunerTrace(
    trail: TunerTrail,
    note: String,
    octave: Int,
    /** Latest offset in cents (null before the string is first heard). */
    cents: Double?,
    /** The reading is current; otherwise it's the last one, shown greyed until the string is plucked again. */
    live: Boolean,
    tolerance: Double,
    /** This string has been tuned (the bubble shows a tick). */
    isTuned: Boolean,
    /** Changes each time a string comes into tune: a ring bursts out of the bubble. */
    pulse: Int,
) {
    val inTune = live && (cents?.let { abs(it) <= tolerance } ?: false)
    val tint = if (!live) OC.muted else if (inTune) OC.teal else OC.gold
    val teal = OC.teal
    val gold = OC.gold

    // The trail scrolls every frame while there is one to show.
    var frame by remember { mutableLongStateOf(0L) }
    val hasTrail = trail.points.isNotEmpty()
    LaunchedEffect(hasTrail) { while (hasTrail) withFrameNanos { frame = it } }

    // Ring burst and bubble pop when a string comes into tune.
    val ring = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        launch { ring.snapTo(0.001f); ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        launch { pop.animateTo(1.18f, tween(150)); pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
    }

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(210.dp).semantics {
            contentDescription = cents?.let { "$note$octave, ${it.roundToInt()} cents" } ?: "$note$octave, waiting for a note"
        },
    ) {
        val w = maxWidth
        val center = w / 2
        val half = w / 2 - traceBubble / 2 - 8.dp
        fun x(cents: Double): Dp = center + half * max(-1.0, min(1.0, cents / traceRange)).toFloat()
        val bandWidth = maxDp(12.dp, half * 2 * (tolerance / traceRange).toFloat())
        val bubbleX = cents?.let { x(it) } ?: center

        // In-tune band and the centre line.
        Box(Modifier.offset(x = center - bandWidth / 2).width(bandWidth).fillMaxHeight().background(OC.tealTint))
        Box(Modifier.offset(x = center - 0.75.dp).width(1.5.dp).fillMaxHeight().background(teal.copy(alpha = 0.5f)))

        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame
            val now = nowSeconds()
            val start = (traceTop + traceBubble).toPx()
            val speed = traceSpeed.toPx()
            val points = trail.points
            // Draw each segment between consecutive readings, fading with age.
            for (k in 1 until points.size) {
                val a = points[k - 1]
                val b = points[k]
                val ca = a.cents ?: continue
                val cb = b.cents ?: continue
                val ya = start + ((now - a.time) * speed).toFloat()
                val yb = start + ((now - b.time) * speed).toFloat()
                if (ya >= size.height + 4) continue
                val fade = max(0f, 1 - (ya - start) / (size.height - start))
                val good = abs(ca) <= tolerance && abs(cb) <= tolerance
                drawLine(
                    (if (good) teal else gold).copy(alpha = fade),
                    Offset(x(ca).toPx(), ya), Offset(x(cb).toPx(), yb),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }

        val labelY = traceTop + traceBubble / 2
        TraceLabel("♭", outfit(26, FontWeight.Bold), OC.muted, 22.dp, labelY)
        TraceLabel("♯", outfit(26, FontWeight.Bold), OC.muted, w - 22.dp, labelY)
        TraceLabel("TOO LOW", outfit(10, FontWeight.Bold, letterSpacing = 1.2.sp), OC.faint, 40.dp, 18.dp)
        TraceLabel("TOO HIGH", outfit(10, FontWeight.Bold, letterSpacing = 1.2.sp), OC.faint, w - 42.dp, 18.dp)

        val t = ring.value
        if (t > 0f && t < 1f) {
            Box(
                Modifier.offset(x = bubbleX - traceBubble / 2, y = traceTop).size(traceBubble)
                    .scale(1 + t * 1.6f).alpha(1 - t).border(3.dp, teal, CircleShape),
            )
        }

        TunerBubble(note, octave, live, inTune, tint, isTuned, Modifier.offset(x = bubbleX - traceBubble / 2, y = traceTop).scale(pop.value))
    }
}

/** Text centred on a point (SwiftUI's `.position`). */
@Composable
private fun TraceLabel(text: String, style: TextStyle, color: Color, cx: Dp, cy: Dp) {
    Text(
        text, style = style, color = color, maxLines = 1,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                placeable.place(cx.roundToPx() - placeable.width / 2, cy.roundToPx() - placeable.height / 2)
            }
        },
    )
}

@Composable
private fun TunerBubble(note: String, octave: Int, live: Boolean, inTune: Boolean, tint: Color, isTuned: Boolean, modifier: Modifier = Modifier) {
    // The stale bubble sits on fixed paper, so its ink is fixed too.
    val fg = if (!live) OC.inkFixed else if (inTune) Color.White else OC.inkFixed
    val fill = if (!live) OC.paper else tint
    val stroke = if (!live) OC.border else tint
    Column(modifier.offset(y = 4.dp).height(traceBubble), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(traceBubble)) {
            Box(
                Modifier.matchParentSize()
                    .then(if (live) Modifier.shadow(10.dp, CircleShape, ambientColor = tint.copy(alpha = 0.35f), spotColor = tint.copy(alpha = 0.35f)) else Modifier)
                    .background(fill, CircleShape).border(3.dp, stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Top) {
                    Text(note, style = outfit(26, FontWeight.Heavy), color = fg)
                    Text("$octave", style = outfit(11, FontWeight.Bold), color = fg, modifier = Modifier.padding(top = 4.dp))
                }
            }
            TunerBubbleTick(isTuned, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        }
        // Small downward pointer under the bubble.
        Canvas(Modifier.size(14.dp, 8.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(p, stroke)
        }
    }
}

/** The bubble's tick, popping in when the string is tuned. */
@Composable
private fun TunerBubbleTick(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.2f) + fadeIn(), exit = scaleOut(targetScale = 0.2f) + fadeOut(),
    ) { TunerTick(22.dp, 10.dp) }
}

/** "All 6 strings in tune": a confetti burst from the bubble and a banner, shown for a few seconds. */
private const val tunerCelebrationDuration = 2.8

private class ConfettiPiece(val angle: Double, val speed: Double, val spin: Double, val size: Float, val color: Int, val delay: Double)

/** The same pieces every time (a fixed pseudo-random sequence), so the burst doesn't reshuffle on redraw. */
private val confettiPieces: List<ConfettiPiece> = run {
    var seed = 0x0C402D5EL
    fun next(): Double {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (seed ushr 11).toDouble() / (1L shl 53).toDouble()
    }
    (0 until 44).map { i ->
        ConfettiPiece(
            angle = -PI / 2 + (next() - 0.5) * 2.4, speed = 260 + next() * 300, spin = (next() - 0.5) * 14,
            size = (5 + next() * 6).toFloat(), color = i % 5, delay = next() * 0.08,
        )
    }
}

@Composable
private fun TunerCelebration(start: Double?, modifier: Modifier = Modifier) {
    val colors = listOf(OC.gold, OC.teal, OC.goldText, OC.tealText, OC.red)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(start) { while (start != null) withFrameNanos { frame = it } }
    Box(modifier) {
        if (start != null) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") frame
                val t = nowSeconds() - start
                val origin = Offset(size.width / 2, 74.dp.toPx())
                val unit = 1.dp.toPx()
                for (p in confettiPieces) {
                    val age = t - p.delay
                    if (age <= 0 || age >= 1.8) continue
                    // Thrown outwards, slowed by air, pulled down by gravity.
                    val drag = (1 - exp(-age * 2.2)) / 2.2
                    val x = origin.x + (cos(p.angle) * p.speed * drag).toFloat() * unit
                    val y = origin.y + (sin(p.angle) * p.speed * drag + 260 * age * age).toFloat() * unit
                    val alpha = max(0.0, 1 - age / 1.8).toFloat()
                    translate(x, y) {
                        rotate((p.spin * age * 180 / PI).toFloat(), pivot = Offset.Zero) {
                            val s = p.size * unit
                            drawRoundRect(
                                colors[p.color].copy(alpha = alpha), topLeft = Offset(-s / 2, -s / 4), size = Size(s, s / 2),
                                cornerRadius = CornerRadius(unit),
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            start != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it } + fadeIn(),
            exit = slideOutVertically(tween(350)) { it } + fadeOut(tween(350)),
        ) {
            Row(
                Modifier.height(44.dp)
                    .shadow(12.dp, CircleShape, ambientColor = OC.teal.copy(alpha = 0.25f), spotColor = OC.teal.copy(alpha = 0.25f))
                    .background(OC.paper, CircleShape).border(1.5.dp, OC.teal.copy(alpha = 0.5f), CircleShape).padding(horizontal = 16.dp)
                    .semantics { contentDescription = "All 6 strings in tune" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = OC.teal, modifier = Modifier.size(18.dp))
                Text("All 6 strings in tune", style = outfit(16, FontWeight.Heavy), color = OC.inkFixed)
            }
        }
    }
}

/** Guitar headstock illustration (3 + 3 tuners), drawn in the iOS point grid (120 × 230) and scaled to density. */
@Composable
private fun TunerHeadstock(modifier: Modifier = Modifier) {
    val ink = OC.inkFixed
    Canvas(modifier) {
        val unit = 1.dp.toPx()
        scale(unit, pivot = Offset.Zero) {
            val head = Path().apply {
                moveTo(28f, 10f)
                quadraticTo(60f, -2f, 92f, 10f)
                lineTo(98f, 170f)
                quadraticTo(60f, 182f, 22f, 170f)
                close()
            }
            drawPath(head, ink)
            drawRect(tunerNightBorder, topLeft = Offset(36f, 170f), size = Size(48f, 60f))
            drawRoundRect(hex(0xF1F0ED), topLeft = Offset(34f, 168f), size = Size(52f, 5f), cornerRadius = CornerRadius(2f))
            for ((x, y) in listOf(40f to 42f, 40f to 92f, 40f to 142f, 80f to 42f, 80f to 92f, 80f to 142f)) {
                drawOval(hex(0xD6D4CF), topLeft = Offset(x - 6, y - 6), size = Size(12f, 12f))
            }
            val lines = listOf(
                floatArrayOf(40f, 142f, 44f, 1.2f), floatArrayOf(40f, 92f, 52f, 1.2f), floatArrayOf(40f, 42f, 60f, 1f),
                floatArrayOf(80f, 42f, 68f, 1f), floatArrayOf(80f, 92f, 76f, 1.2f), floatArrayOf(80f, 142f, 84f, 1.2f),
            )
            for (l in lines) drawLine(hex(0x8E8A84), Offset(l[0], l[1]), Offset(l[2], 230f), strokeWidth = l[3])
        }
    }
}
