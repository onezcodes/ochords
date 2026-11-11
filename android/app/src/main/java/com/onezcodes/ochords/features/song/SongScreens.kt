package com.onezcodes.ochords.features.song

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.design.ChordDiagram
import com.onezcodes.ochords.design.Eyebrow
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.OCSwitch
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.design.sectionColors
import com.onezcodes.ochords.features.shared.LyricLineEditorSheet
import com.onezcodes.ochords.model.ChordInfo
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.ChordVoicing
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.model.SongSource
import com.onezcodes.ochords.services.ChordFollower
import com.onezcodes.ochords.services.ChordLibrary
import com.onezcodes.ochords.services.ChordListener
import com.onezcodes.ochords.services.ChordSheetParser
import com.onezcodes.ochords.services.ChordTheory
import com.onezcodes.ochords.services.HandsFreeMode
import com.onezcodes.ochords.services.HeadNodWatcher
import com.onezcodes.ochords.services.MicInput
import com.onezcodes.ochords.services.NodGesture
import com.onezcodes.ochords.services.PedalKeys
import com.onezcodes.ochords.services.PedalStatus
import com.onezcodes.ochords.services.ScanResult
import com.onezcodes.ochords.services.VoiceCommands
import com.onezcodes.ochords.services.Zawgyi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Song group: Song view (4a) with Key & capo (4b), Easy chords (4c) and More (4f) sheets,
// Stage mode, Warm up (+ drill) and Hands-free (pedal / head nod / auto / voice).

// MARK: - Shared per-song state

/**
 * Key, capo, easy chords, view prefs, private notes and known chords.
 * Kept in memory for the demo ("saved per song, per user" on the canvas note) so Song, Warm up and Stage agree.
 */
private object SongPlayStore {
    data class Settings(
        /** Sounding shift in semitones, 0–11. */
        val transpose: Int = 0,
        val capo: Int = 0,
        val easy: Boolean = false,
        /** Text size index 0–2. */
        val size: Int = 1,
        val showChords: Boolean = true,
        val scrollSpeed: Double = 1.0,
        val stageSpeed: Double = 1.0,
    )

    private val settings = mutableStateMapOf<String, Settings>()
    val notes = mutableStateMapOf<String, String>()
    /** Chords the player ticked "Got it" on in Warm up (per user, across songs). */
    var knownChords by mutableStateOf(setOf("C", "G/B"))
    var askedCamera = false
    var askedMic = false

    private var prefs: SharedPreferences? = null
    private val handsFreeState = mutableStateOf(HandsFreeMode.Pedal)
    private val nodDownState = mutableDoubleStateOf(1.0)
    private val followState = mutableStateOf(false)

    /** The hands-free mode the player chose (remembered, like iOS's UserDefaults). */
    var handsFree: HandsFreeMode
        get() = handsFreeState.value
        set(v) { handsFreeState.value = v; prefs?.edit()?.putString("handsFree.mode", v.key)?.apply() }

    /** Which way of turning the head is a nod down for this player (learnt in Hands-free setup). */
    var nodDownSign: Double
        get() = nodDownState.doubleValue
        set(v) { nodDownState.doubleValue = v; prefs?.edit()?.putFloat("handsFree.nodDown", v.toFloat())?.apply() }

    /** Stage: Follow me instead of auto-scroll (remembered). */
    var followMode: Boolean
        get() = followState.value
        set(v) { followState.value = v; prefs?.edit()?.putBoolean("stage.followMe", v)?.apply() }

    fun attach(context: Context): SongPlayStore {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences("ochords.prefs", Context.MODE_PRIVATE)
            handsFreeState.value = HandsFreeMode.from(p.getString("handsFree.mode", null))
            nodDownState.doubleValue = p.getFloat("handsFree.nodDown", 1f).toDouble()
            followState.value = p.getBoolean("stage.followMe", false)
            prefs = p
        }
        return this
    }

    fun settings(id: String): Settings = settings[id] ?: Settings()

    fun update(id: String, change: (Settings) -> Settings) { settings[id] = change(settings(id)) }
}

@Composable
private fun rememberSongPlayStore(): SongPlayStore {
    val context = LocalContext.current
    return remember { SongPlayStore.attach(context) }
}

/** How a song looks with the player's current key / capo / easy settings. */
private class SongDisplay(val song: Song, val s: SongPlayStore.Settings) {
    fun shown(chord: String): ChordTheory.Shown = ChordTheory.shown(chord, s.transpose, s.capo, s.easy)

    val soundKey: String get() = ChordTheory.transposeRoot(song.key, s.transpose)
    val shapeKey: String get() = ChordTheory.transposeRoot(song.key, s.transpose - s.capo)

    /** Unique chords as the fingers play them, in order of first appearance. */
    val usedShown: List<ChordTheory.Shown> by lazy {
        val seen = HashSet<String>()
        val out = ArrayList<ChordTheory.Shown>()
        for (c in song.usedChords) {
            val sh = shown(c)
            if (seen.add(sh.name)) out.add(sh)
        }
        out
    }

    /** Every chord in playing order (lines and bars), as shown. */
    val sequence: List<String> by lazy {
        song.sections.flatMap { it.bars + it.lines.flatMap { l -> ChordLine.chords(l) } }.map { shown(it).name }
    }

    val modeNote: String?
        get() {
            val parts = ArrayList<String>()
            if (s.capo > 0) parts.add("Capo ${s.capo} · $shapeKey shapes, sounds in $soundKey")
            if (s.easy) {
                val swaps = usedShown.mapNotNull { sh -> sh.from?.let { "$it → ${sh.name}" } }
                parts.add(if (swaps.isEmpty()) "Easy chords on" else "Easy chords: " + swaps.joinToString(", "))
            }
            return if (parts.isEmpty()) null else parts.joinToString(" · ")
        }

    val shiftNote: String
        get() {
            val t = s.transpose
            if (t == 0) return "Original key · ${song.key}"
            return "Original ${song.key} · " + (if (t <= 6) "+$t" else "−${12 - t}") + " semitones"
        }
}

private val songOnInk: Color @Composable get() = if (isDark) hex(0x171614) else hex(0xFAFAF8)
private val songNoteBorder: Color @Composable get() = if (isDark) hex(0xFFB800, 0.3f) else hex(0xFFE3A3)

private fun Song.titleStyle(size: Int) = if (isMyanmar) myanmar(size, FontWeight.Bold) else outfit(size, FontWeight.Bold)

private fun formatSpeed(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", v).trimEnd('0')
    return if (s.endsWith(".")) s + "0" else s
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/** Whether the screen is in front (iOS's scenePhase == .active). */
@Composable
private fun rememberIsActive(): Boolean {
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> active = true
                Lifecycle.Event.ON_PAUSE -> active = false
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return active
}

// MARK: - Mini chord diagram (single-colour dots, as in the design's chord strips)

/** Compact fretboard: thick nut, 4 frets, one-colour dots and barre. Optional ×/○ markers. */
@Composable
private fun SongMiniDiagram(
    voicing: ChordVoicing,
    width: Dp = 52.dp,
    leftHanded: Boolean = false,
    dark: Boolean = false,
    showMarkers: Boolean = false,
    dotColor: Color = OC.gold,
) {
    val gridH = width * 0.92f
    val markH = if (showMarkers) 11.dp else 0.dp
    val ink = if (dark) hex(0xF4F1EA) else hex(0x1A1917)
    val line = if (dark) Color.White.copy(alpha = 0.35f) else hex(0x8E8A84)
    val fretLabel = if (dark) OC.nightMuted else OC.muted
    Box(Modifier.width(width).height(gridH + markH + 4.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 4.dp.toPx()
            val gw = size.width - pad * 2
            val top = markH.toPx() + 2.dp.toPx()
            val fretH = gridH.toPx() / 4
            fun sx(i: Int): Float { val k = if (leftHanded) 5 - i else i; return pad + gw * k / 5 }
            val pattern = voicing.pattern
            if (showMarkers) {
                for (i in 0 until min(6, pattern.length)) {
                    val c = pattern[i]
                    if (c != 'x' && c != '0') continue
                    val r = Rect(Offset(sx(i) - 3.dp.toPx(), 1.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
                    if (c == '0') {
                        drawOval(ink, topLeft = r.topLeft, size = r.size, style = Stroke(1.dp.toPx()))
                    } else {
                        drawLine(ink.copy(alpha = 0.7f), Offset(r.left, r.top), Offset(r.right, r.bottom), 1.dp.toPx())
                        drawLine(ink.copy(alpha = 0.7f), Offset(r.right, r.top), Offset(r.left, r.bottom), 1.dp.toPx())
                    }
                }
            }
            for (f in 0..4) {
                val y = top + f * fretH
                val h = if (f == 0 && voicing.baseFret <= 1) 3.dp.toPx() else 1.dp.toPx()
                drawRect(if (f == 0 && h > 1.dp.toPx()) ink else line, topLeft = Offset(pad, y - h / 2), size = Size(gw, h))
            }
            for (i in 0 until 6) drawRect(line, topLeft = Offset(sx(i) - 0.5f, top), size = Size(1f, gridH.toPx()))
            val dot = max(5.dp.toPx(), width.toPx() * 0.13f)
            voicing.barre?.let { b ->
                val a = min(sx(b.from), sx(b.to)); val z = max(sx(b.from), sx(b.to))
                val y = top + (b.fret - 0.5f) * fretH
                drawRoundRect(dotColor, topLeft = Offset(a - dot / 2, y - dot / 2), size = Size(z - a + dot, dot), cornerRadius = CornerRadius(dot / 2))
            }
            for (d in voicing.dots) {
                drawCircle(dotColor, radius = dot / 2, center = Offset(sx(d.string), top + (d.fret - 0.5f) * fretH))
            }
        }
        if (voicing.baseFret > 1) {
            Text("${voicing.baseFret}", style = mono(8), color = fretLabel, modifier = Modifier.offset(x = (-8).dp, y = markH))
        }
    }
}

/** Foot-pedal glyph (design draws a pedal: rounded base with a raised tread). */
@Composable
private fun SongPedalGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val stroke = max(1.4f, size.value * 0.1f)
    Canvas(modifier.size(size * 1.15f)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(w * 0.08f, h * 0.55f), Size(w * 0.84f, h * 0.3f)), CornerRadius(w * 0.08f)))
            moveTo(w * 0.28f, h * 0.55f)
            lineTo(w * 0.38f, h * 0.2f)
            lineTo(w * 0.62f, h * 0.2f)
            lineTo(w * 0.72f, h * 0.55f)
        }
        drawPath(p, color, style = Stroke(width = stroke.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val HandsFreeMode.icon: ImageVector
    get() = when (this) {
        HandsFreeMode.Pedal -> Icons.Outlined.TouchApp
        HandsFreeMode.Nod -> Icons.Outlined.Face
        HandsFreeMode.Auto -> Icons.Outlined.ArrowDownward
        HandsFreeMode.Voice -> Icons.Outlined.Mic
    }

@Composable
private fun SongHandsFreeIcon(mode: HandsFreeMode, size: Dp = 16.dp, tint: Color, modifier: Modifier = Modifier) {
    if (mode == HandsFreeMode.Pedal) SongPedalGlyph(size, tint, modifier)
    else Icon(mode.icon, contentDescription = null, tint = tint, modifier = modifier.size(size * 1.2f))
}

// MARK: - Chord-over-lyric flow layout

private class SongGapData(val gap: Dp) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@SongGapData
}

/** The gap after this item in a `SongWrapLayout`. */
private fun Modifier.songGap(gap: Dp): Modifier = this.then(SongGapData(gap))

/**
 * Wraps segments like CSS `flex-wrap: wrap; align-items: flex-end`. The gap after each item
 * comes from `songGap`, so row ends never carry a trailing space.
 */
@Composable
private fun SongWrapLayout(modifier: Modifier = Modifier, rowSpacing: Dp = 2.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val spacing = rowSpacing.roundToPx()
        val gaps = measurables.map { ((it.parentData as? SongGapData)?.gap ?: 0.dp).roundToPx() }
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxW)) }
        class Row { val items = ArrayList<Int>(); var width = 0; var height = 0 }
        val rows = arrayListOf(Row())
        var x = 0
        placeables.forEachIndexed { i, p ->
            if (x > 0 && maxW != Constraints.Infinity && x + p.width > maxW) { rows.add(Row()); x = 0 }
            val row = rows.last()
            row.items.add(i)
            row.width = x + p.width
            row.height = max(row.height, p.height)
            x += p.width + gaps[i]
        }
        val height = rows.sumOf { it.height } + spacing * max(0, rows.size - 1)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (rows.maxOfOrNull { it.width } ?: 0)
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            for (row in rows) {
                var rx = 0
                for (i in row.items) {
                    val p = placeables[i]
                    p.placeRelative(rx, y + row.height - p.height)
                    rx += p.width + gaps[i]
                }
                y += row.height + spacing
            }
        }
    }
}

private data class SongLineStyle(
    val chordSize: Int,
    val lyricSize: Int,
    val chordColor: Color,
    val easyColor: Color,
    val lyricColor: Color,
    val showChords: Boolean = true,
    val myanmar: Boolean = true,
)

@Composable
private fun songLineStyle(chordSize: Int, lyricSize: Int, showChords: Boolean = true, myanmar: Boolean = true) =
    SongLineStyle(chordSize, lyricSize, OC.goldText, OC.teal, OC.ink, showChords, myanmar)

/** One ChordPro line: chords in mono above Myanmar lyrics, wrapping word by word. */
@Composable
private fun SongLyricLine(line: String, style: SongLineStyle, shown: (String) -> ChordTheory.Shown, modifier: Modifier = Modifier) {
    val segs = remember(line) { ChordLine.parse(line) }
    val label = segs.joinToString(" ") { seg -> (seg.chord?.let { if (style.showChords) "${shown(it).name}, " else "" } ?: "") + seg.text }
    SongWrapLayout(modifier.fillMaxWidth().semantics { contentDescription = label }, rowSpacing = 2.dp) {
        segs.forEach { seg ->
            Column(Modifier.songGap(if (seg.trailingSpace) (style.lyricSize * 0.42f).dp else 0.dp)) {
                if (style.showChords) {
                    val sh = seg.chord?.let(shown)
                    Box(Modifier.heightIn(min = (style.chordSize * 1.2f).dp), contentAlignment = Alignment.BottomStart) {
                        Text(sh?.name ?: " ", style = mono(style.chordSize), color = if (sh?.isEasy == true) style.easyColor else style.chordColor, softWrap = false)
                    }
                }
                Box(Modifier.heightIn(min = (style.lyricSize * 1.85f).dp), contentAlignment = Alignment.BottomStart) {
                    Text(seg.text.ifEmpty { " " }, style = if (style.myanmar) myanmar(style.lyricSize) else outfit(style.lyricSize), color = style.lyricColor)
                }
            }
        }
    }
}

/** VERSE / CHORUS / MUSIC pill as drawn in the song view (rounded, 12pt heavy). */
@Composable
private fun SongSectionPill(label: String, kind: SectionKind, size: Int = 12) {
    val (bg, fg) = sectionColors(kind)
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(label.uppercase(), style = outfit(size, FontWeight.Heavy, 0.5.sp), color = fg)
    }
}

@Composable
private fun SongIconButton(icon: ImageVector, label: String, tint: Color = OC.ink, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// MARK: - Song view (4a)

private enum class SongMoreAction { Details, Lyrics, Note, Print, Image, Jam, Save, Report }

/** A line in a section (null: a new line at the end). */
private data class SongLineRef(val sectionID: String, val line: Int?)

@Composable
fun SongView(song: Song) {
    val app = LocalApp.current
    val store = rememberSongPlayStore()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    /** The song as opened; once it's in the library, the library's copy is shown (so edits appear straight away). */
    val opened = song
    val current = app.library.firstOrNull { it.id == opened.id } ?: opened
    /** Your own sheets (scanned or listened) can be renamed; community sheets belong to their uploader. */
    val canEditDetails = app.isInLibrary(current.id) && (current.source == SongSource.Imported || current.source == SongSource.Listened)

    var showKeySheet by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var editingNote by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    val noteFocus = remember { FocusRequester() }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var editingDetails by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var artistDraft by remember { mutableStateOf("") }
    /** Edit-lyrics mode: lines are tappable; `lineEdit` is the line open in the editor. */
    var editingLyrics by remember { mutableStateOf(false) }
    var lineEdit by remember { mutableStateOf<SongLineRef?>(null) }
    /** A better reading of the sheet the scanner found, offered because the player had edited the song. */
    var offered by remember { mutableStateOf<Song?>(null) }

    val settings = store.settings(current.id)
    val d = remember(current, settings) { SongDisplay(current, settings) }
    val note = store.notes[current.id] ?: current.myNote ?: ""

    // MARK: Actions

    fun shift(by: Int) = store.update(current.id) { it.copy(transpose = (it.transpose + by + 12) % 12, capo = 0) }

    fun setSpeed(v: Double) = store.update(current.id) { it.copy(scrollSpeed = min(2.0, max(0.5, v))) }

    fun goStage() {
        playing = false
        app.push(if (app.alwaysSkipWarmup) Route.Stage(current) else Route.Warmup(current))
    }

    fun beginNote() {
        noteDraft = note
        editingNote = true
        scope.launch { scrollState.animateScrollTo(0); delay(250); runCatching { noteFocus.requestFocus() } }
    }

    fun saveNote() {
        val text = noteDraft.trim()
        store.notes[current.id] = text
        if (app.library.any { it.id == current.id }) {
            app.library = app.library.map { if (it.id == current.id) it.copy(myNote = text.ifEmpty { null }) else it }
        }
        editingNote = false
        toast = Toast(if (text.isEmpty()) "Note removed" else "Note saved · only you can see it")
    }

    fun decideOffer(better: Song?) {
        if (better != null) {
            app.addToLibrary(current.copy(sections = better.sections))
            toast = Toast("Updated with the new reading")
        }
        app.scanArchive.entry(current.id)?.let { app.scanArchive.save(it.copy(offered = null), current.id) }
        offered = null
    }

    /** Saves an edited (or new) line; syncs like any library change. */
    fun saveLine(text: String, ref: SongLineRef) {
        lineEdit = null
        val s = current.sections.indexOfFirst { it.id == ref.sectionID }
        if (s < 0) return
        val trimmed = text.trim(' ', '\t')
        val lines = current.sections[s].lines.toMutableList()
        val i = ref.line
        if (i != null && i in lines.indices) {
            if (trimmed == lines[i]) return
            lines[i] = trimmed
        } else {
            if (trimmed.isEmpty()) return
            lines.add(trimmed)
        }
        val sections = current.sections.toMutableList().apply { set(s, current.sections[s].copy(lines = lines)) }
        app.addToLibrary(current.copy(sections = sections, isMyanmar = current.isMyanmar || ChordSheetParser.isMyanmar(trimmed)))
        toast = Toast("Line saved")
    }

    fun deleteLine(ref: SongLineRef) {
        lineEdit = null
        val i = ref.line ?: return
        val s = current.sections.indexOfFirst { it.id == ref.sectionID }
        if (s < 0 || i !in current.sections[s].lines.indices) return
        val lines = current.sections[s].lines.toMutableList().apply { removeAt(i) }
        val sections = current.sections.toMutableList().apply { set(s, current.sections[s].copy(lines = lines)) }
        app.addToLibrary(current.copy(sections = sections))
        toast = Toast("Line deleted")
    }

    fun beginDetails() {
        titleDraft = current.title
        artistDraft = current.artist
        editingDetails = true
    }

    /** Saves a new title and artist (Zawgyi typed in is converted, as in Review); syncs like any library change. */
    fun saveDetails() {
        val title = Zawgyi.normalised(titleDraft.trim())
        val artist = Zawgyi.normalised(artistDraft.trim())
        if (title.isEmpty()) {
            toast = Toast("A song needs a title", Icons.Filled.Warning)
            return
        }
        editingDetails = false
        if (title == current.title && artist == current.artist) return
        app.addToLibrary(current.copy(title = title, artist = artist, isMyanmar = current.isMyanmar || ChordSheetParser.isMyanmar(title + artist)))
        toast = Toast("Title & artist updated")
    }

    fun handleMore(action: SongMoreAction) {
        showMore = false
        when (action) {
            SongMoreAction.Details -> beginDetails()
            SongMoreAction.Lyrics -> editingLyrics = true
            SongMoreAction.Note -> beginNote()
            SongMoreAction.Print -> app.push(Route.PrintPdf(current))
            SongMoreAction.Image -> app.push(Route.SaveImage(d.usedShown.firstOrNull()?.name ?: current.key, current))
            SongMoreAction.Jam -> app.push(Route.Jam(current))
            SongMoreAction.Report -> app.push(Route.Report(current))
            SongMoreAction.Save -> {
                app.addToLibrary(if (note.isNotEmpty()) current.copy(myNote = note) else current)
                toast = Toast("Saved to your library")
            }
        }
    }

    LaunchedEffect(current.id) { offered = app.scanArchive.entry(current.id)?.offered }

    /** Scrolls the sheet slowly (~22 dp/s at 1×) until the end or until paused. */
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (scrollState.value >= scrollState.maxValue - 1) { scrollState.scrollTo(0); delay(400) }
        var last = System.nanoTime()
        var y = scrollState.value.toFloat()
        while (isActive && playing) {
            delay(16)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9
            last = now
            if (abs(scrollState.value - y) > 3 * density.density) y = scrollState.value.toFloat() // the player dragged
            y += (22 * density.density * store.settings(current.id).scrollSpeed * dt).toFloat()
            if (y >= scrollState.maxValue) {
                scrollState.scrollTo(scrollState.maxValue)
                playing = false
                return@LaunchedEffect
            }
            scrollState.scrollTo(y.roundToInt())
        }
    }

    OCPage {
        ToastHost(toast, { toast = null }, bottomPadding = 40.dp) {
            Column(Modifier.fillMaxSize()) {
                // Top
                TopBar(backLabel = "Back to library", onBack = { app.pop() }) {
                    SongIconButton(Icons.Outlined.Group, "Start a jam room") { app.push(Route.Jam(current)) }
                    SongIconButton(Icons.Outlined.GridOn, "Chords by key") { app.push(Route.Keys) }
                    SongIconButton(Icons.Outlined.CenterFocusWeak, "Warm up, then stage mode") { goStage() }
                    SongIconButton(Icons.Outlined.Image, "Save as image") {
                        app.push(Route.SaveImage(d.usedShown.firstOrNull()?.name ?: current.key, current))
                    }
                    SongIconButton(Icons.Outlined.MoreHoriz, "More") { showMore = true }
                }
                SongHeader(current)
                SongControls(d, settings, playing, onShift = ::shift, onKey = { showKeySheet = true },
                    onSize = { store.update(current.id) { it.copy(size = (it.size + 1) % 3) } },
                    onChords = { store.update(current.id) { it.copy(showChords = !it.showChords) } },
                    onPlay = { playing = !playing })
                SongChordStrip(d, onChord = { app.push(Route.Chords(it, d.usedShown.map { s -> s.name })) }, onWarmup = ::goStage)
                AnimatedVisibility(visible = d.modeNote != null, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
                    SongModeBanner(d.modeNote ?: "") { showKeySheet = true }
                }

                // Sheet body
                val sizes = listOf(13 to 15, 15 to 18, 17 to 21)[settings.size]
                val style = songLineStyle(sizes.first, sizes.second, showChords = settings.showChords, myanmar = current.isMyanmar)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)
                        .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = if (playing) 100.dp else 48.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    val better = offered
                    if (better != null && canEditDetails) SongOfferBanner(onUse = { decideOffer(better) }, onKeep = { decideOffer(null) })
                    if (editingLyrics) SongEditBanner { editingLyrics = false }
                    if (note.isNotEmpty() || editingNote) {
                        SongNoteCard(note, editingNote, noteDraft, { noteDraft = it }, noteFocus, onEdit = ::beginNote, onSave = ::saveNote)
                    }
                    current.sections.forEach { sec ->
                        SongSectionView(
                            section = sec, style = style, shown = d::shown, editing = editingLyrics,
                            onEditLine = { lineEdit = SongLineRef(sec.id, it) },
                            onAddLine = { lineEdit = SongLineRef(sec.id, null) },
                            onStrum = { app.push(Route.Strum) },
                        )
                    }
                    SongFooter(current)
                }
            }
            AnimatedVisibility(
                visible = playing, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            ) {
                SongSpeedPill(settings.scrollSpeed, onChange = ::setSpeed)
            }
        }
    }

    if (showKeySheet) SongKeySheet(current, store) { showKeySheet = false }
    if (showMore) {
        SongMoreSheet(
            song = current, keyName = d.soundKey, hasNote = note.isNotEmpty(), inLibrary = app.isInLibrary(current.id),
            canEditDetails = canEditDetails, onAction = ::handleMore, onDismiss = { showMore = false },
        )
    }
    lineEdit?.let { ref ->
        val section = current.sections.firstOrNull { it.id == ref.sectionID }
        val line = ref.line?.let { i -> section?.lines?.getOrNull(i) } ?: ""
        LyricLineEditorSheet(
            sectionLabel = section?.label?.replaceFirstChar { it.uppercase() } ?: "",
            line = line,
            onDismiss = { lineEdit = null },
            onSave = { saveLine(it, ref) },
            onDelete = { deleteLine(ref) },
        )
    }
    if (editingDetails) {
        SongDetailsDialog(titleDraft, artistDraft, { titleDraft = it }, { artistDraft = it }, onSave = ::saveDetails, onCancel = { editingDetails = false })
    }
}

@Composable
private fun SongHeader(song: Song) {
    val app = LocalApp.current
    val muted = OC.muted
    val ink = OC.ink
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(song.title, style = song.titleStyle(28), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val artistStyle = if (song.isMyanmar) myanmar(14, FontWeight.SemiBold) else outfit(14, FontWeight.SemiBold)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("Sung by ") }
                withStyle(SpanStyle(color = ink, fontFamily = artistStyle.fontFamily, fontWeight = FontWeight.SemiBold)) { append(song.artist) }
                withStyle(SpanStyle(color = muted)) { append(" · Original key ${song.key} · 4/4") }
            },
            style = outfit(14),
        )
        // Shared line
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val u = song.uploader
            val name = u?.name ?: "you"
            Box(Modifier.size(20.dp).clip(CircleShape).background(hex(u?.tier?.colorHex ?: 0x1E3A8A)), contentAlignment = Alignment.Center) {
                Text(u?.initials?.take(1) ?: "Y", style = outfit(10, FontWeight.Heavy), color = Color.White)
            }
            val sharedBy = buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("Shared by ") }
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append(name) }
            }
            if (u != null) {
                Text(sharedBy, style = outfit(12), maxLines = 1, modifier = Modifier.plainClickable { app.push(Route.User(u)) })
            } else {
                Text(sharedBy, style = outfit(12), maxLines = 1)
                Text("·", style = outfit(12), color = muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.Public, contentDescription = null, tint = OC.tealText, modifier = Modifier.size(13.dp))
                    Text("Public", style = outfit(12, FontWeight.Bold), color = OC.tealText)
                }
            }
            Text("·", style = outfit(12), color = muted)
            Text("Other versions", style = outfit(12, FontWeight.Bold), color = OC.goldText, maxLines = 1,
                modifier = Modifier.plainClickable { app.switchTo(MainTab.Explore) })
        }
    }
}

@Composable
private fun SongControls(
    d: SongDisplay, s: SongPlayStore.Settings, playing: Boolean,
    onShift: (Int) -> Unit, onKey: () -> Unit, onSize: () -> Unit, onChords: () -> Unit, onPlay: () -> Unit,
) {
    val ink = OC.ink
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(142.dp).height(48.dp).glass(14.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).plainClickable { onShift(-1) }.semantics { contentDescription = "Transpose down" }, contentAlignment = Alignment.Center) {
                Text("−", style = outfit(22, FontWeight.SemiBold), color = ink)
            }
            Column(
                Modifier.weight(1f).heightIn(min = 44.dp).plainClickable(onClick = onKey)
                    .semantics { contentDescription = "Key and capo, key ${d.soundKey}${if (s.capo > 0) ", capo ${s.capo}" else ""}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(if (s.capo > 0) "CAPO ${s.capo}" else "KEY", style = outfit(10, FontWeight.Bold, 0.5.sp), color = OC.muted, softWrap = false)
                Text(d.soundKey, style = mono(17), color = OC.goldText)
            }
            Box(Modifier.size(44.dp).plainClickable { onShift(1) }.semantics { contentDescription = "Transpose up" }, contentAlignment = Alignment.Center) {
                Text("+", style = outfit(22, FontWeight.SemiBold), color = ink)
            }
        }

        Box(
            Modifier.size(48.dp).glass(14.dp).plainClickable(onClick = onSize)
                .semantics { contentDescription = "Text size, " + listOf("Small", "Medium", "Large")[s.size] },
            contentAlignment = Alignment.Center,
        ) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append("A") }
                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append("a") }
            }, style = outfit(16), color = ink)
        }

        val chordShape = RoundedCornerShape(14.dp)
        Box(
            Modifier.size(48.dp).clip(chordShape).background(if (s.showChords) OC.goldTint else OC.glass)
                .border(1.dp, if (s.showChords) songNoteBorder else OC.border, chordShape)
                .plainClickable(onClick = onChords).semantics { contentDescription = "Show chords"; selected = s.showChords },
            contentAlignment = Alignment.Center,
        ) { Text("Am", style = mono(14), color = if (s.showChords) OC.goldText else OC.muted) }

        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (playing) OC.gold else ink)
                .plainClickable(onClick = onPlay).semantics { contentDescription = if (playing) "Pause auto-scroll" else "Auto-scroll" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (playing) OC.inkFixed else songOnInk
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text("Scroll", style = outfit(14, FontWeight.Bold), color = fg)
        }
    }
}

@Composable
private fun SongChordStrip(d: SongDisplay, onChord: (String) -> Unit, onWarmup: () -> Unit) {
    val border = OC.border
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).drawBehind { drawRect(border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("CHORDS", style = outfit(12, FontWeight.Bold), color = OC.muted, modifier = Modifier.padding(end = 2.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            d.usedShown.forEach { sh ->
                Box(Modifier.heightIn(min = 44.dp).plainClickable { onChord(sh.name) }.semantics { contentDescription = "Review chord ${sh.name}" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.glass(20.dp).padding(horizontal = 9.dp, vertical = 5.dp)) {
                        Text(sh.name, style = mono(13), color = if (sh.isEasy) OC.teal else OC.goldText)
                    }
                }
            }
        }
        Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onWarmup), contentAlignment = Alignment.Center) {
            Box(Modifier.height(32.dp).clip(CircleShape).background(OC.ink).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                Text("Warm up", style = outfit(13, FontWeight.Bold), color = songOnInk)
            }
        }
    }
}

@Composable
private fun SongModeBanner(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp).clip(shape).background(OC.tealTint.copy(alpha = 0.85f))
            .border(1.dp, OC.teal.copy(alpha = 0.25f), shape).plainClickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Tune, contentDescription = null, tint = OC.tealText, modifier = Modifier.size(16.dp))
        Text(text, style = outfit(13, FontWeight.SemiBold), color = OC.tealText, modifier = Modifier.weight(1f))
        Text("Edit", style = outfit(13, FontWeight.Heavy), color = OC.tealText)
    }
}

@Composable
private fun SongNoteCard(
    note: String, editing: Boolean, draft: String, onDraft: (String) -> Unit, focus: FocusRequester,
    onEdit: () -> Unit, onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val rotation by animateFloatAsState(if (editing) 0f else -0.6f, label = "note")
    Row(
        Modifier.fillMaxWidth().rotate(rotation).clip(shape).background(OC.goldTint).border(1.dp, songNoteBorder, shape).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = OC.goldText, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("MY NOTE · ONLY YOU", style = outfit(11, FontWeight.Heavy, 1.sp), color = OC.goldText)
            if (editing) {
                val style = outfit(14).copy(color = OC.ink)
                BasicTextField(
                    value = draft, onValueChange = onDraft, textStyle = style, cursorBrush = SolidColor(OC.gold), maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        Box { if (draft.isEmpty()) Text("Capo, tempo, what to skip…", style = style, color = OC.faint); inner() }
                    },
                )
            } else {
                Text(note, style = outfit(14).copy(lineHeight = 19.sp), color = OC.ink)
            }
        }
        Box(
            Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 28.dp).plainClickable { if (editing) onSave() else onEdit() }
                .semantics { contentDescription = if (editing) "Save note" else "Edit note" },
            contentAlignment = Alignment.Center,
        ) { Text(if (editing) "Done" else "Edit", style = outfit(13, FontWeight.Bold), color = OC.goldText) }
    }
}

@Composable
private fun SongFooter(song: Song) {
    val app = LocalApp.current
    when (song.source) {
        SongSource.Imported -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
            Text("Imported from a photo ·", style = outfit(13), color = OC.muted)
            Text("View original", style = outfit(13, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable {
                app.push(Route.Review(ScanResult(song = song, detectedZawgyi = false, lowConfidence = emptyList())))
            })
        }
        SongSource.Listened -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
            Text("Made by listening · chords are a best guess", style = outfit(13), color = OC.muted)
        }
        SongSource.Community, SongSource.Shared -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Group, contentDescription = null, tint = OC.muted, modifier = Modifier.size(17.dp))
            Text("Community sheet ·", style = outfit(13), color = OC.muted)
            Text("Report a problem", style = outfit(13, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable { app.push(Route.Report(song)) })
        }
    }
}

@Composable
private fun SongSpeedPill(speed: Double, onChange: (Double) -> Unit) {
    Row(
        Modifier.padding(bottom = 28.dp).shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(CircleShape).background(OC.inkFixed).height(44.dp).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Speed", style = outfit(13, FontWeight.SemiBold), color = OC.paper)
        Box(Modifier.size(34.dp).clip(CircleShape).background(hex(0x2A2825)).plainClickable { onChange(speed - 0.25) }.semantics { contentDescription = "Slower" }, contentAlignment = Alignment.Center) {
            Text("−", style = outfit(18), color = OC.paper)
        }
        Text(formatSpeed(speed) + "×", style = mono(13, FontWeight.SemiBold), color = OC.paper, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 38.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(hex(0x2A2825)).plainClickable { onChange(speed + 0.25) }.semantics { contentDescription = "Faster" }, contentAlignment = Alignment.Center) {
            Text("+", style = outfit(18), color = OC.paper)
        }
    }
}

@Composable
private fun SongEditBanner(onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OC.goldTint).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(18.dp))
        Text("Editing lyrics — tap a line to change it", style = outfit(14, FontWeight.SemiBold), color = OC.ink, modifier = Modifier.weight(1f))
        Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onDone), contentAlignment = Alignment.Center) {
            Text("Done", style = outfit(14, FontWeight.Bold), color = OC.goldText)
        }
    }
}

/** The scanner reads this sheet better now, but the player had changed the song: their choice. */
@Composable
private fun SongOfferBanner(onUse: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OC.goldTint).padding(horizontal = 14.dp).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = OC.ink, modifier = Modifier.size(16.dp))
            Text("The scanner reads this sheet better now", style = outfit(14, FontWeight.Bold), color = OC.ink)
        }
        Text("You changed this song, so it wasn’t updated. Use the new reading to replace the lyrics and chords — your notes stay.", style = outfit(13), color = OC.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onUse), contentAlignment = Alignment.Center) {
                Text("Use the new reading", style = outfit(14, FontWeight.Bold), color = OC.goldText)
            }
            Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onKeep), contentAlignment = Alignment.Center) {
                Text("Keep mine", style = outfit(14, FontWeight.Bold), color = OC.muted)
            }
        }
    }
}

/** "Title & artist" alert: two fields, Save / Cancel. */
@Composable
private fun SongDetailsDialog(title: String, artist: String, onTitle: (String) -> Unit, onArtist: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Title & artist", style = outfit(18, FontWeight.Bold), color = OC.ink)
            Text("Fix what the scan read.", style = outfit(13), color = OC.muted)
            SongDetailsField(title, "Title", onTitle)
            SongDetailsField(artist, "Artist", onArtist)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onCancel).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", style = outfit(15, FontWeight.SemiBold), color = OC.muted)
                }
                Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onSave).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Save", style = outfit(15, FontWeight.Bold), color = OC.goldText)
                }
            }
        }
    }
}

@Composable
private fun SongDetailsField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val style = myanmar(16).copy(color = OC.ink)
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = style, cursorBrush = SolidColor(OC.gold), singleLine = true,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OC.surface).border(1.dp, OC.border, RoundedCornerShape(12.dp)).padding(12.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = style, color = OC.faint); inner() } },
    )
}

// MARK: Section

@Composable
private fun SongSectionView(
    section: SongSection,
    style: SongLineStyle,
    shown: (String) -> ChordTheory.Shown,
    /** Edit-lyrics mode: each line is a button that opens the editor, plus "Add line". */
    editing: Boolean = false,
    onEditLine: (Int) -> Unit = {},
    onAddLine: () -> Unit = {},
    onStrum: () -> Unit,
) {
    val isChorus = section.kind == SectionKind.Chorus
    val border = OC.border
    Column(
        Modifier.fillMaxWidth()
            .then(if (isChorus) Modifier.clip(RoundedCornerShape(20.dp)).background(OC.goldTint).padding(14.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongSectionPill(section.label, section.kind)
            section.note?.let { Text(it, style = outfit(13, FontWeight.SemiBold), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
            Spacer(Modifier.weight(1f))
            section.strum?.let { strum ->
                Box(
                    Modifier.heightIn(min = 44.dp).plainClickable(onClick = onStrum)
                        .semantics { contentDescription = "Strumming pattern " + strum.replace("↓", "down ").replace("↑", "up ") },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.height(28.dp).clip(CircleShape).background(OC.glass).border(1.dp, OC.border, CircleShape).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("STRUM", style = outfit(10, FontWeight.Heavy, 0.6.sp), color = OC.muted)
                        Text(strum, style = mono(13).copy(letterSpacing = 1.sp), color = OC.ink)
                    }
                }
            }
        }
        if (section.bars.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                section.bars.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { bar ->
                            val sh = shown(bar)
                            Column(
                                Modifier.weight(1f).height(52.dp).glass(14.dp).padding(horizontal = 8.dp, vertical = 6.dp)
                                    .semantics { contentDescription = "Bar, ${sh.name}, 4 beats" },
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (style.showChords) sh.name else " ", style = mono(15), color = if (sh.isEasy) style.easyColor else style.chordColor, maxLines = 1)
                                Text("/ / / /", style = outfit(11, FontWeight.Normal, 3.sp), color = OC.faint)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (section.lines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                section.lines.forEachIndexed { i, line ->
                    if (editing) {
                        Row(
                            Modifier.fillMaxWidth()
                                .drawBehind {
                                    drawRoundRect(border, cornerRadius = CornerRadius(12.dp.toPx()),
                                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                                }
                                .plainClickable { onEditLine(i) }.semantics { contentDescription = "Edit this line" }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SongLyricLine(line, style, shown, Modifier.weight(1f))
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        SongLyricLine(line, style, shown)
                    }
                }
            }
        }
        if (editing && section.kind != SectionKind.Flow) {
            Row(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onAddLine), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(16.dp))
                Text("Add line", style = outfit(14, FontWeight.Bold), color = OC.goldText)
            }
        }
        if (section.flow.isNotEmpty()) {
            SongWrapLayout(Modifier.fillMaxWidth(), rowSpacing = 6.dp) {
                section.flow.forEach { step ->
                    val (bg, fg) = sectionColors(step.kind)
                    Box(Modifier.songGap(6.dp).clip(CircleShape).background(bg).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text(step.label, style = outfit(13, FontWeight.Bold), color = fg)
                    }
                }
            }
        }
    }
}

// MARK: - Key & capo sheet (4b / 4c)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongKeySheet(song: Song, store: SongPlayStore, onDismiss: () -> Unit) {
    val s = store.settings(song.id)
    val d = SongDisplay(song, s)
    val options = ChordTheory.capoOptions(song.usedChords, song.key, s.transpose, s.capo)
    val fewest = options.minOfOrNull { it.barres } ?: 0
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun shift(by: Int) = store.update(song.id) { it.copy(transpose = (it.transpose + by + 12) % 12, capo = 0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = OC.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 30.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Key & capo", style = outfit(22, FontWeight.Bold, (-0.4).sp), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(36.dp).clip(CircleShape).background(OC.ink).plainClickable(onClick = onDismiss).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Done", style = outfit(14, FontWeight.Bold), color = songOnInk)
                }
            }

            val cardShape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().clip(cardShape).background(OC.surface).border(1.dp, OC.border, cardShape).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sounds in", style = outfit(15, FontWeight.Bold), color = OC.ink)
                    Text(d.shiftNote, style = outfit(12), color = OC.muted)
                }
                SongStepButton("−", "Key down") { shift(-1) }
                Text(d.soundKey, style = mono(22), color = OC.goldText, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                SongStepButton("+", "Key up") { shift(1) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLAY IT WITH", style = outfit(12, FontWeight.Bold, 1.sp), color = OC.muted)
                options.forEach { o ->
                    SongCapoRow(o, on = o.capo == s.capo, best = o.barres == fewest) { store.update(song.id) { it.copy(capo = o.capo) } }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(cardShape).background(OC.surface).border(1.dp, OC.border, cardShape).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Easy chords", style = outfit(15, FontWeight.Bold), color = OC.ink)
                    Text("Swap barre chords for simpler shapes (F → Fmaj7, Bm → Bm7). Same feel, fewer fingers.", style = outfit(12).copy(lineHeight = 16.sp), color = OC.muted)
                }
                OCSwitch(s.easy, Modifier.semantics { contentDescription = "Easy chords" }) { v -> store.update(song.id) { it.copy(easy = v) } }
            }
        }
    }
}

@Composable
private fun SongStepButton(text: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(44.dp).clip(shape).background(OC.background).border(1.dp, OC.border, shape).plainClickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(20), color = OC.ink) }
}

@Composable
private fun SongCapoRow(o: ChordTheory.CapoOption, on: Boolean, best: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) OC.goldTint else OC.surface).border(if (on) 2.dp else 1.dp, if (on) OC.gold else OC.border, shape)
            .plainClickable(onClick = onClick).semantics { selected = on }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).border(2.dp, if (on) OC.ink else hex(0xC9C6C0), CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(OC.ink))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (o.capo > 0) "Capo ${o.capo} · ${o.shapeKey} shapes" else "No capo · ${o.shapeKey} shapes", style = outfit(15, FontWeight.Bold), color = OC.ink)
            Text(o.shapes.joinToString("  "), style = mono(12), color = OC.goldText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(CircleShape).background(if (best) OC.tealTint else OC.softFill).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(if (best) "Easiest" else "${o.barres} barre", style = outfit(11, FontWeight.Heavy), color = if (best) OC.tealText else OC.muted, softWrap = false)
        }
    }
}

// MARK: - More sheet (4f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMoreSheet(
    song: Song, keyName: String, hasNote: Boolean, inLibrary: Boolean, canEditDetails: Boolean,
    onAction: (SongMoreAction) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareURL = "https://ochords.app/s/" + Uri.encode(song.id)

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist} · Key $keyName · on O Chords\n$shareURL")
        }
        context.startActivity(Intent.createChooser(send, "Share link"))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = OC.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 4.dp).padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(song.title, style = song.titleStyle(18), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("Key $keyName", style = outfit(13), color = OC.muted)
            }
            var i = 0
            if (canEditDetails) {
                SongMoreRow(i++, Icons.Outlined.TextFields, "Title & artist", "Fix what the scan read") { onAction(SongMoreAction.Details) }
                SongMoreRow(i++, Icons.Outlined.EditNote, "Edit lyrics", "Change words; chords stay with them") { onAction(SongMoreAction.Lyrics) }
            }
            SongMoreRow(i++, Icons.Outlined.Edit, if (hasNote) "Edit my note" else "Add my note", "Only you can see it") { onAction(SongMoreAction.Note) }
            SongMoreRow(i++, Icons.Outlined.Print, "Print or save PDF", "A4 or US Letter, 1 or 2 columns") { onAction(SongMoreAction.Print) }
            SongMoreRow(i++, Icons.Outlined.Image, "Save as image", "For Instagram, Facebook or chat") { onAction(SongMoreAction.Image) }
            SongMoreRow(i++, Icons.Outlined.Share, "Share link", "Anyone with O Chords can open it") { share() }
            if (!inLibrary) {
                SongMoreRow(i++, Icons.Outlined.SaveAlt, "Save to my library", "Keep it offline with your key and notes") { onAction(SongMoreAction.Save) }
            }
            SongMoreRow(i, Icons.Outlined.Flag, "Report a problem", "Wrong chords, copyright or spam", destructive = true) { onAction(SongMoreAction.Report) }
        }
    }
}

@Composable
private fun SongMoreRow(i: Int, icon: ImageVector, title: String, subtitle: String, destructive: Boolean = false, onClick: () -> Unit) {
    val line = OC.line
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .drawBehind { if (i > 0) drawRect(line, size = Size(size.width, 1.dp.toPx())) }
            .plainClickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (destructive) OC.redTint else OC.softFill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (destructive) OC.red else OC.ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = outfit(15, FontWeight.Bold), color = if (destructive) OC.red else OC.ink)
            Text(subtitle, style = outfit(12), color = OC.muted)
        }
    }
}

// MARK: - Stage mode

private data class SongStageItem(val id: Int, val section: SongSection, val extra: String?, val beats: Int)

/** Play order: every section once, then the "Then play" flow (e.g. Chorus → Verse 2 → Music → Chorus ×2). */
private object SongStagePlan {
    fun make(song: Song): List<SongStageItem> {
        val playable = song.sections.filter { it.kind != SectionKind.Flow }
        val out = ArrayList<SongStageItem>()

        fun beats(s: SongSection, times: Int): Int {
            // Lines marked "( … ) x2" are played twice.
            val lineBars = ChordFollower.playOrder(s.lines).sumOf { max(1, ChordLine.chords(s.lines[it.line]).size) }
            val bars = if (s.bars.isEmpty()) lineBars else s.bars.size
            return max(4, bars * 4 * times)
        }
        fun times(text: String): Pair<String, Int> {
            val r = text.indexOf("×")
            if (r < 0) return text to 1
            val n = text.substring(r + 1).takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            return text.substring(0, r).trim() to n
        }

        for (s in playable) {
            out.add(SongStageItem(out.size, s, s.note, beats(s, times(s.note ?: "").second)))
        }
        for (f in song.sections.filter { it.kind == SectionKind.Flow }) {
            for (step in f.flow) {
                val parts = step.label.split("·", limit = 2).map { it.trim() }
                val (base, n) = times(parts.firstOrNull() ?: step.label)
                val sec = playable.firstOrNull { it.label.equals(base, ignoreCase = true) } ?: playable.firstOrNull { it.kind == step.kind } ?: continue
                val extras = ArrayList<String>()
                if (n > 1) extras.add("×$n")
                if (parts.size > 1) extras.add(parts[1])
                out.add(SongStageItem(out.size, sec, if (extras.isEmpty()) null else extras.joinToString(" · "), beats(sec, n)))
            }
        }
        return out
    }
}

private fun ordinal(n: Int): String = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

private val stageRepeats = Regex("""×\s*(\d+)""")

@Composable
fun StageView(song: Song) {
    val app = LocalApp.current
    val store = rememberSongPlayStore()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val active = rememberIsActive()
    val view = LocalView.current

    var index by remember { mutableIntStateOf(0) }
    var progress by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focus = remember { FocusRequester() }
    /** Follow me: the lyrics move as the player strums, instead of at a set tempo. */
    val followMode = store.followMode
    val listener = remember { ChordListener() }
    /** The chord the player is on (follow mode), null until the first chord is heard. */
    var followed by remember { mutableStateOf<ChordFollower.Step?>(null) }
    var micOff by remember { mutableStateOf(false) }
    /** Hands-free page turns by nodding or speaking (a pedal is a keyboard: see the key handler). */
    var nod by remember { mutableStateOf<HeadNodWatcher?>(null) }
    val voice = remember { VoiceCommands(context) }
    val pedal = remember { PedalKeys() }
    /** Where each lyric line sits in the scroll content, to keep the followed line in view. */
    val lineTops = remember { mutableStateMapOf<Int, Float>() }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var askedSystemMic by remember { mutableStateOf(false) }
    var micRequest by remember { mutableIntStateOf(0) }

    val items = remember(song) { SongStagePlan.make(song) }
    val settings = store.settings(song.id)
    val d = remember(song, settings) { SongDisplay(song, settings) }
    val cur = if (items.isEmpty()) null else items[min(index, items.size - 1)]

    // MARK: Follow me

    fun following(it: SongStageItem) = followMode && followed?.item == it.id

    /** A chord as the player's fingers see it (after capo and easy swaps), from how it sounds. */
    fun shownName(step: ChordFollower.Step): String {
        val s = store.settings(song.id)
        return ChordTheory.shown(step.chord, 0, s.capo, s.easy).name
    }

    /** Every chord in play order — sections repeated as the plan says (×2) — as it sounds after transposing. */
    fun followSteps(): List<ChordFollower.Step> {
        val t = store.settings(song.id).transpose
        return items.flatMap { it ->
            val repeats = it.extra?.let { e -> stageRepeats.find(e)?.groupValues?.get(1)?.toIntOrNull() } ?: 1
            val once = it.section.bars.mapIndexed { bi, b -> ChordFollower.Step(ChordTheory.transpose(b, t), it.id, line = null, bar = bi) } +
                ChordFollower.playOrder(it.section.lines).flatMap { play ->
                    ChordLine.chords(it.section.lines[play.line]).map { c ->
                        ChordFollower.Step(ChordTheory.transpose(c, t), it.id, line = play.line, pass = play.pass, passes = play.passes)
                    }
                }
            List(max(1, repeats)) { once }.flatten()
        }
    }

    fun scrollToLine(line: Int) {
        val top = lineTops[line] ?: return
        val target = (top - viewportHeight * 0.35f).roundToInt().coerceIn(0, max(0, scrollState.maxValue))
        scope.launch { scrollState.animateScrollTo(target, tween(350)) }
    }

    /** The player is on `step`: show its section, highlight its line and keep it in view. */
    fun follow(step: ChordFollower.Step) {
        followed = step
        if (step.item != index) {
            index = step.item
            scope.launch { scrollState.scrollTo(0) }
        }
        val lines = items.firstOrNull { it.id == step.item }?.section?.lines?.size ?: 0
        val line = step.line
        if (line != null && lines > 0) {
            progress = (line + 1).toDouble() / lines
            scrollToLine(line)
        }
    }

    /** Carry on following from a line the player tapped (or the start of a section). */
    fun resume(item: Int, line: Int?) {
        listener.resync(item, line)
        followed = line?.let { ChordFollower.Step(followed?.chord ?: "", item, it) }
    }

    // MARK: Playback

    fun jump(to: Int) {
        if (to == index) { progress = 0.0; return }
        index = to
        progress = 0.0
        scope.launch { scrollState.scrollTo(0) }
        // The player moved by hand: follow on from the start of that section.
        if (followMode) resume(to, null)
    }

    fun step(by: Int) {
        val n = items.size
        if (n == 0) return
        jump(min(max(0, index + by), n - 1))
    }

    fun obey(command: VoiceCommands.Command) {
        when (command) {
            VoiceCommands.Command.Next -> step(1)
            VoiceCommands.Command.Back -> step(-1)
            VoiceCommands.Command.Stop -> playing = false
            VoiceCommands.Command.Play -> playing = true
            VoiceCommands.Command.Chorus -> {
                // The next chorus (or the first, from the last one).
                val later = items.firstOrNull { it.id > index && it.section.kind == SectionKind.Chorus } ?: items.firstOrNull { it.section.kind == SectionKind.Chorus }
                if (later != null) jump(later.id)
            }
        }
    }

    // MARK: Hands-free

    /** Runs the camera or speech watcher for the chosen hands-free mode while Stage is in front. */
    fun updateHandsFree() {
        val mode = store.handsFree
        if (mode == HandsFreeMode.Nod && active && HeadNodWatcher.hasPermission(context)) {
            if (nod == null) {
                nod = HeadNodWatcher(context, lifecycleOwner, store.nodDownSign).apply {
                    onMove = { move -> step(if (move == NodGesture.Move.Down) 1 else -1) }
                }
            }
            nod?.start()
        } else {
            nod?.stop()
        }
        // Follow me listens to the guitar on the same microphone, so voice turns wait while it's on.
        if (mode == HandsFreeMode.Voice && active && !followMode && VoiceCommands.hasPermission(context)) {
            voice.onCommand = { obey(it) }
            runCatching { voice.start() }
        } else {
            voice.stop()
        }
    }

    /** Listens while Follow me is on, playing and the app is in front. */
    fun updateFollowing() {
        if (!followMode || !playing || !active || items.isEmpty()) { listener.stop(); return }
        if (listener.isRunning) return
        if (!MicInput.hasPermission(context)) {
            // First the app's primer (it asks the system), then the system prompt once more; after that it's off.
            if (!store.askedMic) { store.askedMic = true; app.modal = Modal.Permission(PermissionKind.Microphone); return }
            if (!askedSystemMic) { askedSystemMic = true; micRequest++; return }
            micOff = true; playing = false
            return
        }
        micOff = false
        listener.onMove = { follow(it) }
        val resumeAt = followed?.let { it.item to it.line } ?: (if (index > 0) index to null else null)
        try {
            listener.start(followSteps(), resumeAt)
        } catch (e: MicInput.Failure) {
            micOff = true; playing = false
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updateFollowing() else { micOff = true; playing = false }
    }
    LaunchedEffect(micRequest) { if (micRequest > 0) micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // Keep the screen on and take hardware key events (pedals) while Stage is open.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            listener.stop(); listener.close()
            nod?.close()
            voice.stop()
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(playing, followMode, active) { updateFollowing() }
    LaunchedEffect(store.handsFree, active, followMode) { updateHandsFree() }
    // Back from the permission primer.
    val modal = app.modal
    var modalWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(modal) {
        if (modal != null) modalWasUp = true
        else if (modalWasUp) { modalWasUp = false; updateFollowing(); updateHandsFree() }
    }
    var lastFollow by remember { mutableStateOf(followMode) }
    LaunchedEffect(followMode) {
        if (lastFollow != followMode) {
            lastFollow = followMode
            playing = false
            followed = null
            listener.stop()
        }
    }
    var lastIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) { if (lastIndex != index) { lastIndex = index; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }

    /** Advances section by section at the song's tempo (4 beats per bar), scrolling long sections along. */
    LaunchedEffect(playing, followMode) {
        if (!playing || followMode || items.isEmpty()) return@LaunchedEffect
        if (index == items.size - 1 && progress >= 1) { index = 0; progress = 0.0 }
        var last = System.nanoTime()
        while (isActive && playing) {
            delay(50)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9
            last = now
            val bpm = max(30, song.tempo) * store.settings(song.id).stageSpeed
            val duration = items[min(index, items.size - 1)].beats * 60 / bpm
            progress = min(1.0, progress + dt / duration)
            val maxOffset = scrollState.maxValue
            if (maxOffset > 0) scrollState.scrollTo((maxOffset * max(0.0, (progress - 0.25) / 0.75)).roundToInt())
            if (progress >= 1) {
                if (index < items.size - 1) jump(index + 1) else { playing = false; return@LaunchedEffect }
            }
        }
    }

    // Page-turn pedals send arrow or page keys: right / down / page down = next, left / up / page up = back,
    // holding the right pedal starts or stops the scroll.
    OCPage(
        Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            when (pedal.handle(native)) {
                PedalKeys.Action.Next -> step(1)
                PedalKeys.Action.Back -> step(-1)
                PedalKeys.Action.ToggleScroll -> playing = !playing
                null -> {}
            }
            pedal.handles(native.keyCode)
        },
        night = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            StageHeader(song, d, store.handsFree, onExit = { app.pop() }, onHandsFree = { playing = false; app.push(Route.HandsFree) }, onLock = { locked = true })
            StageProgressBar(items, index, progress.toFloat(), onJump = ::jump, onStep = ::step)
            var dragX by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { viewportTop = it.positionInRoot().y }
                    .onSizeChanged { viewportHeight = it.height }
                    .pointerInput(items) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onDragEnd = { if (abs(dragX) > 60.dp.toPx()) { if (dragX < 0) step(1) else step(-1) } },
                            onHorizontalDrag = { _, dx -> dragX += dx },
                        )
                    },
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    if (cur != null) {
                        AnimatedContent(
                            targetState = index,
                            transitionSpec = { (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150)) },
                            label = "section",
                        ) { i ->
                            val item = items[min(i, items.size - 1)]
                            StageCurrent(
                                item = item, next = items.getOrNull(i + 1), d = d, song = song, followMode = followMode,
                                followed = if (following(item)) followed else null,
                                onLineTap = { li -> if (followMode) resume(item.id, li) },
                                onLinePlaced = { li, rootY -> lineTops[li] = rootY - viewportTop + scrollState.value },
                                onNext = { step(1) },
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 24.dp),
                            )
                        }
                    }
                }
            }
            StageBottomPanel(
                d = d, cur = cur, song = song, followMode = followMode, followed = followed, playing = playing, micOff = micOff,
                shownName = ::shownName, speed = settings.stageSpeed,
                onSpeed = { v -> store.update(song.id) { it.copy(stageSpeed = v) } },
                onPlay = { playing = !playing },
                onMode = { store.followMode = it },
            )
        }
        if (locked) StageLockOverlay { locked = false }
    }
}

// MARK: Pieces

@Composable
private fun StageHeader(song: Song, d: SongDisplay, mode: HandsFreeMode, onExit: () -> Unit, onHandsFree: () -> Unit, onLock: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(88.dp)) {
            Box(Modifier.size(44.dp).plainClickable(onClick = onExit).semantics { contentDescription = "Exit stage mode" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(song.title, style = if (song.isMyanmar) myanmar(15, FontWeight.SemiBold) else outfit(15, FontWeight.SemiBold), color = OC.nightText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Key ${d.soundKey} · Capo ${d.s.capo}${if (d.s.easy) " · Easy" else ""}", style = outfit(12), color = OC.nightMuted)
        }
        Row(Modifier.width(88.dp), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(44.dp).plainClickable(onClick = onHandsFree).semantics { contentDescription = "Hands-free page turns" }, contentAlignment = Alignment.Center) {
                SongHandsFreeIcon(mode, 19.dp, OC.gold)
            }
            Box(Modifier.size(44.dp).plainClickable(onClick = onLock).semantics { contentDescription = "Lock screen" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StageProgressBar(items: List<SongStageItem>, index: Int, progress: Float, onJump: (Int) -> Unit, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 20.dp).padding(top = 0.dp)
            .semantics { contentDescription = "Section ${index + 1} of ${items.size}" },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { it ->
            Box(Modifier.weight(max(1f, it.beats.toFloat())).fillMaxHeight().plainClickable { onJump(it.id) }, contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(if (it.id < index) OC.gold else if (it.id == index) hex(0x7A5A1C) else OC.nightGlass)
                        .then(if (it.id > index) Modifier.border(1.dp, OC.nightBorder, CircleShape) else Modifier),
                ) {
                    if (it.id == index) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(OC.gold))
                }
            }
        }
    }
}

@Composable
private fun StageCurrent(
    item: SongStageItem, next: SongStageItem?, d: SongDisplay, song: Song, followMode: Boolean, followed: ChordFollower.Step?,
    onLineTap: (Int) -> Unit, onLinePlaced: (Int, Float) -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier,
) {
    val style = SongLineStyle(chordSize = 19, lyricSize = 24, chordColor = OC.gold, easyColor = OC.teal, lyricColor = OC.nightText, myanmar = song.isMyanmar)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongSectionPill(item.section.label, item.section.kind, size = 13)
            item.extra?.let { Text(it, style = outfit(14, FontWeight.SemiBold), color = OC.nightMuted) }
        }
        if (item.section.bars.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.section.bars.chunked(4).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { k, b ->
                            val bi = rowIndex * 4 + k
                            val sh = d.shown(b)
                            Column(
                                Modifier.weight(1f).heightIn(min = 78.dp).nightGlass(16.dp)
                                    .then(if (followed?.bar == bi) Modifier.border(2.dp, OC.gold, RoundedCornerShape(16.dp)) else Modifier)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(sh.name, style = mono(24), color = if (sh.isEasy) OC.teal else OC.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("/ / / /", style = outfit(12, FontWeight.Normal, 3.sp), color = OC.nightMuted)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val here = followed?.line
            item.section.lines.forEachIndexed { li, line ->
                val alpha by animateFloatAsState(if (here == null || here == li) 1f else 0.45f, tween(250), label = "dim")
                Box(
                    Modifier.fillMaxWidth().alpha(alpha)
                        .onGloballyPositioned { onLinePlaced(li, it.positionInRoot().y) }
                        .plainClickable(role = null) { onLineTap(li) }
                        .semantics { selected = here == li },
                ) {
                    SongLyricLine(line, style, d::shown, Modifier.padding(start = if (followMode) 12.dp else 0.dp))
                    if (here == li) {
                        Box(Modifier.matchParentSize().padding(vertical = 4.dp)) {
                            Box(Modifier.fillMaxHeight().width(4.dp).clip(CircleShape).background(OC.gold))
                        }
                        // Which time through a repeated line ("( … ) x2").
                        if (followed != null && followed.passes > 1) {
                            Box(Modifier.align(Alignment.TopEnd).height(22.dp).clip(CircleShape).background(OC.gold).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text("${ordinal(followed.pass)} time · ×${followed.passes}", style = outfit(12, FontWeight.Heavy), color = OC.inkFixed)
                            }
                        }
                    }
                }
            }
        }
        StageNextPreview(next, d, song, Modifier.padding(top = 8.dp).plainClickable(role = null, onClick = onNext))
    }
}

@Composable
private fun StageNextPreview(next: SongStageItem?, d: SongDisplay, song: Song, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = next?.let { "NEXT · ${it.section.label.uppercase()}" + (it.extra?.let { e -> " · ${e.uppercase()}" } ?: "") } ?: "LAST SECTION"
        Box(Modifier.border(1.dp, hex(0x393733), CircleShape).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(label, style = outfit(12, FontWeight.Heavy, 0.6.sp), color = OC.nightMuted)
        }
        if (next != null) {
            val first = next.section.lines.firstOrNull()
            if (first != null) {
                SongLyricLine(first, SongLineStyle(15, 18, chordColor = hex(0xA07A2E), easyColor = OC.teal.copy(alpha = 0.7f), lyricColor = OC.faint, myanmar = song.isMyanmar), d::shown)
            } else if (next.section.bars.isNotEmpty()) {
                Text(next.section.bars.joinToString("   ") { d.shown(it).name }, style = mono(15), color = hex(0xA07A2E))
            }
        }
    }
}

@Composable
private fun StageBottomPanel(
    d: SongDisplay, cur: SongStageItem?, song: Song, followMode: Boolean, followed: ChordFollower.Step?, playing: Boolean, micOff: Boolean,
    shownName: (ChordFollower.Step) -> String, speed: Double, onSpeed: (Double) -> Unit, onPlay: () -> Unit, onMode: (Boolean) -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val sectionChords = cur?.let { c -> (c.section.bars + c.section.lines.flatMap { ChordLine.chords(it) }).map { d.shown(it).name }.toSet() } ?: emptySet()
    // Following: light up the chord being played; otherwise the current section's chords.
    val active = if (followMode) (followed?.let { setOf(shownName(it)) } ?: emptySet()) else sectionChords
    Column(
        Modifier.fillMaxWidth().background(hex(0x0B0A09, 0.92f))
            .drawBehind { drawRect(Color.White.copy(alpha = 0.1f), size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val needed = 64.dp * d.usedShown.size + 4.dp * max(0, d.usedShown.size - 1)
            if (needed <= maxWidth) StageChordRow(d.usedShown, active, app.leftHanded, Modifier.fillMaxWidth())
            else StageChordRow(d.usedShown, active, app.leftHanded, Modifier.horizontalScroll(rememberScrollState()))
        }
        // Auto-scroll (set tempo) or Follow me (listens to the strumming).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false, true).forEach { follow ->
                val on = followMode == follow
                Box(Modifier.weight(1f).heightIn(min = 44.dp).plainClickable { onMode(follow) }.semantics { selected = on }, contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxWidth().height(34.dp).clip(CircleShape).background(if (on) OC.gold else Color.White.copy(alpha = 0.08f)),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val fg = if (on) OC.inkFixed else OC.nightText
                        Icon(if (follow) Icons.Outlined.GraphicEq else Icons.Outlined.VerticalAlignBottom, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
                        Text(if (follow) "Follow me" else "Auto-scroll", style = outfit(13, FontWeight.Bold), color = fg)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(52.dp).shadow(10.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
                    .clip(CircleShape).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))).plainClickable(onClick = onPlay)
                    .semantics { contentDescription = if (followMode) (if (playing) "Stop listening" else "Start listening") else (if (playing) "Pause auto-scroll" else "Start auto-scroll") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(24.dp))
            }
            if (followMode) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (micOff) "Microphone is off" else if (!playing) "Follow me" else if (followed == null) "Listening…" else "Following you",
                        style = outfit(14, FontWeight.Bold), color = if (micOff) OC.red else OC.nightText,
                    )
                    if (micOff) {
                        Text("Allow it in Settings ›", style = outfit(12), color = OC.gold, modifier = Modifier.plainClickable { openAppSettings(context) })
                    } else {
                        Text(
                            if (!playing) "Tap ▶ and play — the lyrics follow your chords"
                            else if (followed == null) "Strum the first chord · tap a line to start there"
                            else "Playing ${shownName(followed)} · tap a line if it loses you",
                            style = outfit(12), color = OC.nightMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Auto-scroll", style = outfit(12), color = OC.nightMuted)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(java.util.Locale.US, "%.1f× · %d BPM", speed, (song.tempo * speed).roundToInt()), style = mono(12, FontWeight.SemiBold), color = OC.nightMuted)
                    }
                    SongSpeedBar(speed, onSpeed, fill = hex(0xD6D4CF))
                }
            }
        }
    }
}

@Composable
private fun StageChordRow(chords: List<ChordTheory.Shown>, active: Set<String>, leftHanded: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        chords.forEach { sh ->
            val alpha by animateFloatAsState(if (active.isEmpty() || sh.name in active) 1f else 0.35f, tween(300), label = "chord")
            Column(Modifier.width(64.dp).alpha(alpha).semantics { contentDescription = "Chord ${sh.name}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sh.name, style = mono(13), color = if (sh.isEasy) OC.teal else OC.gold)
                ChordLibrary.info(sh.name)?.voicings?.firstOrNull()?.let { v ->
                    SongMiniDiagram(v, width = 54.dp, leftHanded = leftHanded, dark = true, showMarkers = true, dotColor = if (sh.isEasy) OC.teal else OC.gold)
                }
            }
        }
    }
}

@Composable
private fun StageLockOverlay(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.001f))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) }
            .semantics { contentDescription = "Screen locked" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier.padding(bottom = 200.dp).height(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(14.dp))
            Text("Locked · hold to unlock", style = outfit(13, FontWeight.SemiBold), color = OC.nightText)
        }
    }
}

/** Thin speed bar, 0.5× … 1.5×, draggable. */
@Composable
private fun SongSpeedBar(value: Double, onChange: (Double) -> Unit, fill: Color = OC.gold) {
    val frac = ((value - 0.5) / 1.0).toFloat().coerceIn(0f, 1f)
    var width by remember { mutableIntStateOf(0) }
    fun set(x: Float) {
        val f = (x / max(1, width)).coerceIn(0f, 1f)
        onChange(((0.5 + f) * 10).roundToInt() / 10.0)
    }
    Box(
        Modifier.fillMaxWidth().height(24.dp).onSizeChanged { width = it.width }
            .pointerInput(Unit) { detectTapGestures { set(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> set(change.position.x) } }
            .semantics { contentDescription = "Scroll speed, ${String.format(java.util.Locale.US, "%.1f", value)} times" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, OC.nightBorder, CircleShape))
        Box(Modifier.width(maxOf(6.dp, with(LocalDensity.current) { (width * frac).toDp() })).height(6.dp).clip(CircleShape).background(fill))
    }
}

// MARK: - Warm up (4d) + drill

private data class SongChange(val a: String, val b: String, val why: String) {
    val id: String get() = "$a→$b"
}

@Composable
fun WarmupView(song: Song) {
    val app = LocalApp.current
    val store = rememberSongPlayStore()
    val haptics = LocalHapticFeedback.current

    var selected by remember { mutableStateOf<String?>(null) }
    var drill by remember { mutableStateOf<SongChange?>(null) }
    var beat by remember { mutableIntStateOf(0) }
    var doneDrills by remember { mutableStateOf(setOf<String>()) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val settings = store.settings(song.id)
    val d = remember(song, settings) { SongDisplay(song, settings) }
    val chords = d.usedShown.map { it.name }
    val changes = remember(d) { trickyChanges(d) }
    val sel = selected?.takeIf { it in chords } ?: chords.firstOrNull() ?: song.key
    val known = store.knownChords
    val ready = chords.count { it in known }

    fun goStage() {
        drill = null
        app.pop()
        app.push(Route.Stage(song))
    }

    fun toggleKnown(name: String) {
        if (name in store.knownChords) {
            store.knownChords = store.knownChords - name
        } else {
            store.knownChords = store.knownChords + name
            chords.firstOrNull { it !in store.knownChords }?.let { selected = it }
        }
    }

    LaunchedEffect(drill) {
        val ch = drill ?: return@LaunchedEffect
        val interval = 60.0 / max(30, song.tempo)
        val total = (30 / interval).roundToInt()
        beat = 0
        while (isActive && beat < total) {
            delay((interval * 1000).toLong())
            if (!isActive || drill != ch) return@LaunchedEffect
            beat += 1
        }
        if (!isActive) return@LaunchedEffect
        doneDrills = doneDrills + ch.id
        toast = Toast("${ch.a} → ${ch.b} · ${beat / 4} switches")
        drill = null
    }
    LaunchedEffect(beat) {
        if (drill != null && beat > 0) haptics.performHapticFeedback(if (beat % 4 == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
    }

    OCPage {
        ToastHost(toast, { toast = null }, bottomPadding = 130.dp) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "Warm up", backLabel = "Back to song", onBack = { app.pop() }) {
                    Box(Modifier.heightIn(min = 44.dp).plainClickable { goStage() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Skip", style = outfit(14, FontWeight.Bold), color = OC.muted)
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                    // Intro
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("Before you play")
                        val ink = OC.ink
                        Text(buildAnnotatedString {
                            append("Review the chords in ")
                            withStyle(SpanStyle(fontFamily = song.titleStyle(26).fontFamily, fontSize = if (song.isMyanmar) 24.sp else 26.sp, fontWeight = FontWeight.Bold)) { append(song.title) }
                        }, style = outfit(26, FontWeight.Bold, (-0.6).sp), color = ink)
                        Text(
                            "${chords.size} chords · ${changes.size} tricky changes · Key ${d.soundKey}${if (d.s.capo > 0) " · Capo ${d.s.capo}" else ""} · about ${max(1, (chords.size * 15 + changes.size * 30) / 60)} min",
                            style = outfit(13), color = OC.muted,
                        )
                    }
                    WarmupCards(d, sel, known, app.leftHanded, Modifier.padding(top = 14.dp)) { selected = it }
                    val dr = drill
                    if (dr != null) {
                        WarmupDrillPanel(dr, beat, song.tempo, Modifier.padding(top = 12.dp)) { drill = null; beat = 0 }
                    } else {
                        WarmupDetail(sel, d, known.contains(sel), app.leftHanded, Modifier.padding(top = 12.dp),
                            onKnown = { toggleKnown(sel) },
                            onEasy = { store.update(song.id) { it.copy(easy = true) }; toast = Toast("Easy chords on") },
                            onGuide = { app.push(Route.Chords(sel, chords)) },
                            onPractice = { app.push(Route.Practice(sel)) })
                        WarmupChangeList(changes, doneDrills, Modifier.padding(top = 14.dp)) { ch -> beat = 0; selected = ch.b; drill = ch }
                    }
                }
                WarmupFooter(ready, chords.size, onStage = ::goStage)
            }
        }
    }
}

@Composable
private fun WarmupCards(d: SongDisplay, sel: String, known: Set<String>, leftHanded: Boolean, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        d.usedShown.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { sh ->
                    val on = sh.name == sel
                    val isKnown = sh.name in known
                    val barre = ChordLibrary.info(sh.name)?.voicings?.firstOrNull()?.isBarre ?: ChordTheory.isBarre(sh.name)
                    val shape = RoundedCornerShape(16.dp)
                    val glow = OC.gold.copy(alpha = 0.18f)
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier.fillMaxWidth().height(112.dp)
                                .then(if (on) Modifier.drawBehind {
                                    // Soft gold halo 4dp outside the selected card.
                                    val inset = 4.dp.toPx()
                                    drawRoundRect(glow, topLeft = Offset(-inset, -inset), size = Size(size.width + inset * 2, size.height + inset * 2), cornerRadius = CornerRadius(20.dp.toPx()))
                                } else Modifier.shadow(9.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f)))
                                .clip(shape).background(if (on) OC.goldTint else OC.glass).border(if (on) 2.dp else 1.dp, if (on) OC.gold else OC.glassBorder, shape)
                                .plainClickable { onSelect(sh.name) }
                                .semantics { contentDescription = "${sh.name}, ${if (isKnown) "got it" else "not reviewed"}"; selected = on }
                                .padding(top = 8.dp, bottom = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(sh.name, style = mono(14), color = if (sh.isEasy) OC.teal else OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            ChordLibrary.info(sh.name)?.voicings?.firstOrNull()?.let { v ->
                                SongMiniDiagram(v, width = 46.dp, leftHanded = leftHanded, dotColor = if (sh.isEasy) OC.teal else OC.gold)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp).clip(CircleShape)
                                    .background(if (isKnown) OC.teal else if (barre) OC.redTint else OC.softFill).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (isKnown) "✓" else if (barre) "BARRE" else "·", style = outfit(10, FontWeight.Heavy),
                                    color = if (isKnown) Color.White else if (barre) hex(0x991B1B) else OC.muted)
                            }
                        }
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WarmupDetail(
    name: String, d: SongDisplay, known: Boolean, leftHanded: Boolean, modifier: Modifier = Modifier,
    onKnown: () -> Unit, onEasy: () -> Unit, onGuide: () -> Unit, onPractice: () -> Unit,
) {
    val info = ChordLibrary.info(name)
    val voicing = info?.voicings?.firstOrNull()
    val uses = d.sequence.count { it == name }
    val hasEasy = !d.s.easy && ChordTheory.easySwaps[name.split("/").first()] != null
    Row(modifier.fillMaxWidth().glass().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp)) {
            if (voicing != null) ChordDiagram(voicing, width = 104.dp, leftHanded = leftHanded, showFingers = true, showStringNames = false)
            else Box(Modifier.width(104.dp).height(110.dp), contentAlignment = Alignment.Center) { Text("?", style = mono(28), color = OC.faint) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = mono(26), color = OC.goldText)
                Text(fullName(name, info), style = outfit(12), color = OC.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("${uses}× on this sheet", style = outfit(12, FontWeight.Bold), color = OC.muted)
            Text(voicing?.tip ?: "Look it up in the finger guide.", style = outfit(13).copy(lineHeight = 18.sp), color = OC.ink)
            if (hasEasy) {
                Box(Modifier.heightIn(min = 30.dp).plainClickable(onClick = onEasy), contentAlignment = Alignment.CenterStart) {
                    Text("Hard? Turn on Easy chords", style = outfit(13, FontWeight.Bold), color = OC.tealText)
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (known) OC.tealTint else OC.ink)
                        .plainClickable(onClick = onKnown).semantics { selected = known },
                    contentAlignment = Alignment.Center,
                ) { Text(if (known) "✓ Got it" else "I can play this", style = outfit(13, FontWeight.Bold), color = if (known) OC.tealText else songOnInk) }
                WarmupSquareButton(Icons.Outlined.TouchApp, "Full finger guide", onGuide)
                WarmupSquareButton(Icons.Outlined.Mic, "Check with the mic", onPractice)
            }
        }
    }
}

@Composable
private fun WarmupSquareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OC.softFill).plainClickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = OC.ink, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun WarmupChangeList(changes: List<SongChange>, doneDrills: Set<String>, modifier: Modifier = Modifier, onDrill: (SongChange) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("TRICKY CHANGES IN THIS SONG", style = outfit(12, FontWeight.Bold, 1.sp), color = OC.muted)
            Spacer(Modifier.weight(1f))
            Text("30 s each", style = outfit(12), color = OC.muted)
        }
        if (changes.isEmpty()) Text("No tricky changes — this one’s all open chords.", style = outfit(13), color = OC.muted)
        changes.forEach { ch ->
            val shape = RoundedCornerShape(16.dp)
            val faint = OC.faint
            Row(
                Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                    .clip(shape).background(OC.glass).border(1.dp, OC.glassBorder, shape).padding(start = 12.dp, end = 8.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(buildAnnotatedString {
                    append(ch.a)
                    withStyle(SpanStyle(color = faint)) { append(" → ") }
                    append(ch.b)
                }, style = mono(15), color = OC.ink, softWrap = false)
                Text(ch.why, style = outfit(12).copy(lineHeight = 15.sp), color = OC.muted, modifier = Modifier.weight(1f))
                if (ch.id in doneDrills) {
                    Box(Modifier.clip(CircleShape).background(OC.tealTint).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("DONE", style = outfit(11, FontWeight.Heavy), color = OC.tealText)
                    }
                }
                Box(Modifier.heightIn(min = 44.dp).plainClickable { onDrill(ch) }.semantics { contentDescription = "Drill ${ch.a} to ${ch.b}" }, contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(OC.ink).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = songOnInk, modifier = Modifier.size(14.dp))
                        Text("Drill", style = outfit(13, FontWeight.Bold), color = songOnInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun WarmupDrillPanel(ch: SongChange, beat: Int, tempo: Int, modifier: Modifier = Modifier, onStop: () -> Unit) {
    val interval = 60.0 / max(30, tempo)
    val left = max(0, 30 - (beat * interval).toInt())
    val onA = beat % 8 < 4
    val dim = hex(0x6B6660)
    Column(
        modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = OC.inkFixed.copy(alpha = 0.25f), spotColor = OC.inkFixed.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(24.dp)).background(OC.inkFixed.copy(alpha = 0.92f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DRILL · ${ch.a} → ${ch.b}", style = outfit(12, FontWeight.Heavy, 1.4.sp), color = hex(0xFFC940))
            Spacer(Modifier.weight(1f))
            Text(String.format(java.util.Locale.US, "0:%02d", left), style = mono(14, FontWeight.SemiBold), color = hex(0xBDB9B3))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 200.dp).semantics { contentDescription = "Play ${if (onA) ch.a else ch.b}" },
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ch.a, style = if (onA) outfit(72, FontWeight.Bold) else mono(36), color = if (onA) OC.gold else dim, maxLines = 1)
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = dim, modifier = Modifier.size(26.dp))
            Text(ch.b, style = if (onA) mono(36) else outfit(72, FontWeight.Bold), color = if (onA) dim else OC.gold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            for (k in 0 until 4) {
                val on = k == beat % 4
                Box(Modifier.size(12.dp).scale(if (on) 1.2f else 1f).clip(CircleShape).background(if (on) OC.gold else Color.White.copy(alpha = 0.18f)))
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Change on beat 1. " + ch.why, style = outfit(13).copy(lineHeight = 18.sp), color = hex(0xD6D4CF), modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${beat / 4}", style = outfit(22, FontWeight.Heavy), color = OC.gold)
                Text("SWITCHES", style = outfit(10, FontWeight.Bold), color = OC.nightMuted)
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).plainClickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Text("Stop drill", style = outfit(15, FontWeight.Bold), color = OC.paper) }
    }
}

@Composable
private fun WarmupFooter(ready: Int, total: Int, onStage: () -> Unit) {
    val glassBorder = OC.glassBorder
    Column(
        Modifier.fillMaxWidth().background(OC.surface.copy(alpha = 0.7f)).background(OC.glass)
            .drawBehind { drawRect(glassBorder, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val frac by animateFloatAsState(ready.toFloat() / max(1, total), label = "ready")
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(OC.softFill)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(OC.teal))
            }
            Text("$ready of $total chords ready", style = outfit(13, FontWeight.Bold), color = OC.ink, softWrap = false)
        }
        GoldButton(
            if (ready == total) "All set — start stage mode" else "Start stage mode",
            leading = { Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp)) },
            onClick = onStage,
        )
    }
}

// MARK: Logic

private fun fullName(name: String, info: ChordInfo?): String {
    if (info == null) return ""
    if (info.name == name) return info.full
    val parts = name.split("/")
    return if (parts.size > 1) "${info.full}, ${parts[1]} in the bass" else info.full
}

/** Most frequent chord pairs plus any barre change, in order of first appearance (max 3). */
private fun trickyChanges(d: SongDisplay): List<SongChange> {
    val seq = d.sequence
    data class Count(val a: String, val b: String, val n: Int, val first: Int)
    val counts = LinkedHashMap<String, Count>()
    for (i in 0 until seq.size - 1) {
        if (seq[i] == seq[i + 1]) continue
        val k = seq[i] + "→" + seq[i + 1]
        val c = counts[k]
        counts[k] = if (c != null) c.copy(n = c.n + 1) else Count(seq[i], seq[i + 1], 1, i)
    }
    fun barre(c: String): Boolean = ChordLibrary.info(c)?.voicings?.firstOrNull()?.isBarre ?: ChordTheory.isBarre(c)
    val all = counts.values.sortedWith(compareByDescending<Count> { it.n }.thenBy { it.first })
    val picked = all.filter { barre(it.a) || barre(it.b) }.take(2).toMutableList()
    for (c in all) if (picked.size < 3 && picked.none { it.a == c.a && it.b == c.b }) picked.add(c)
    val known = mapOf(
        "C→G/B" to "Bass walks down — keep the ring finger moving only one string.",
        "Am→F" to "Barre change, the slowest in this song.",
        "F→C" to "Back to the top of every line.",
        "Am→Fmaj7" to "Keep index and middle down — just slide the ring finger over.",
    )
    return picked.sortedBy { it.first }.map { c ->
        val why = known[c.a + "→" + c.b]
            ?: when {
                barre(c.b) -> "Barre change — set the index first, then the rest."
                barre(c.a) -> "Leaving the barre — lift everything at once."
                c.b.contains("/") || c.a.contains("/") -> "The bass moves — watch the lowest string."
                else -> "Comes up ${c.n}× — move the fingers that change, keep the rest."
            }
        SongChange(c.a, c.b, why)
    }
}

// MARK: - Hands-free (pedal / head nod / auto / voice)

@Composable
fun HandsFreeView() {
    val app = LocalApp.current
    val store = rememberSongPlayStore()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val followMode = store.followMode

    var pedalName by remember { mutableStateOf(PedalStatus.connectedName()) }
    /** The last pedal press, to show that it works. */
    var lastPedal by remember { mutableStateOf<String?>(null) }
    var nod by remember { mutableStateOf<HeadNodWatcher?>(null) }
    var faceFound by remember { mutableStateOf(false) }
    var learningNod by remember { mutableStateOf(false) }
    var lastNod by remember { mutableStateOf<NodGesture.Move?>(null) }
    val voice = remember { VoiceCommands(context) }
    var heard by remember { mutableStateOf<String?>(null) }
    var localSpeed by remember { mutableDoubleStateOf(1.0) }

    /** The song open in Stage underneath, for tempo and speed. */
    val stageSong: Song? = (app.paths[app.tab] ?: emptyList()).asReversed().firstOrNull { it is Route.Stage }?.let { (it as Route.Stage).song }
    val mode = store.handsFree
    val hasCamera = HeadNodWatcher.hasCamera(context)
    val cameraAllowed = HeadNodWatcher.hasPermission(context)
    val micAllowed = VoiceCommands.hasPermission(context)

    fun pedalPressed(what: String) {
        pedalName = PedalStatus.connectedName() ?: pedalName
        lastPedal = what
    }

    /** Runs the real watcher for the chosen mode here too, so the player can try it before going back to the song. */
    fun watch(m: HandsFreeMode) {
        if (m == HandsFreeMode.Nod && HeadNodWatcher.hasPermission(context)) {
            if (nod == null) {
                nod = HeadNodWatcher(context, lifecycleOwner, store.nodDownSign).apply {
                    onFace = { faceFound = it }
                    onMove = { move ->
                        lastNod = move
                        scope.launch { delay(1200); if (lastNod == move) lastNod = null }
                    }
                }
            }
            nod?.start()
        } else {
            nod?.stop()
        }
        if (m != HandsFreeMode.Voice || followMode) { voice.stop(); return }
        if (!VoiceCommands.hasPermission(context)) {
            if (!store.askedMic) { store.askedMic = true; app.modal = Modal.Permission(PermissionKind.Microphone) }
            return
        }
        voice.onHeard = { word ->
            heard = word
            scope.launch { delay(800); if (heard == word) heard = null }
        }
        runCatching { voice.start() }
    }

    // Learning which way is down re-creates the watcher with the new sign (the callback needs `watch`).
    LaunchedEffect(nod) {
        nod?.onLearnt = { sign ->
            store.nodDownSign = sign
            learningNod = false
            nod?.close()
            nod = null
            watch(HandsFreeMode.Nod)
        }
    }

    fun select(m: HandsFreeMode) {
        store.handsFree = m
        if (m == HandsFreeMode.Nod && !store.askedCamera) { store.askedCamera = true; app.modal = Modal.Permission(PermissionKind.Camera) }
        if (m == HandsFreeMode.Voice && !store.askedMic) { store.askedMic = true; app.modal = Modal.Permission(PermissionKind.Microphone) }
    }

    val nodStatus = when {
        !hasCamera -> "NO FRONT CAMERA"
        !cameraAllowed -> "CAMERA IS OFF"
        learningNod -> "NOD DOWN NOW…"
        faceFound -> "FACE FOUND"
        else -> "LOOKING FOR YOUR FACE…"
    }
    val voiceNote = when {
        followMode -> "Voice turns pause while Follow me listens to your guitar — switch Stage to Auto-scroll to use them."
        !VoiceCommands.available(context) -> "Speech recognition isn’t available on this phone right now."
        else -> "In English. Understood on the phone where it can be. Works best between songs or in quiet parts."
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(mode, followMode) { watch(mode) }
    // Back from the permission prompt.
    val modal = app.modal
    var modalWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(modal) {
        if (modal != null) modalWasUp = true
        else if (modalWasUp) { modalWasUp = false; watch(store.handsFree) }
    }
    DisposableEffect(Unit) {
        val input = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { pedalName = PedalStatus.connectedName() }
            override fun onInputDeviceRemoved(deviceId: Int) { pedalName = PedalStatus.connectedName() }
            override fun onInputDeviceChanged(deviceId: Int) { pedalName = PedalStatus.connectedName() }
        }
        input?.registerInputDeviceListener(listener, null)
        onDispose {
            input?.unregisterInputDeviceListener(listener)
            nod?.close()
            voice.stop()
        }
    }

    OCPage(
        Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val press = native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0
            when {
                native.keyCode in PedalKeys.nextKeys -> { if (press) pedalPressed("Next section"); true }
                native.keyCode in PedalKeys.backKeys -> { if (press) pedalPressed("Previous section"); true }
                else -> false
            }
        },
        night = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).plainClickable { app.pop() }.semantics { contentDescription = "Back to stage" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Hands-free", style = outfit(16, FontWeight.Bold), color = OC.nightText)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Keep both hands on the guitar", color = OC.nightMuted)
                    Text("Turn pages without touching", style = outfit(26, FontWeight.Bold, (-0.6).sp), color = OC.nightText)
                }

                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HandsFreeMode.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m -> HandsFreeModeCard(m, on = m == mode, Modifier.weight(1f)) { select(m) } }
                        }
                    }
                }

                AnimatedContent(targetState = mode, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "panel", modifier = Modifier.padding(top = 12.dp)) { m ->
                    when (m) {
                        HandsFreeMode.Pedal -> HandsFreePedalPanel(pedalName, lastPedal)
                        HandsFreeMode.Nod -> HandsFreeNodPanel(
                            faceFound = faceFound, status = nodStatus, lastNod = lastNod, hasCamera = hasCamera, allowed = cameraAllowed, learning = learningNod,
                            onSettings = { openAppSettings(context) },
                            onLearn = { learningNod = true; nod?.learnDown() },
                        )
                        HandsFreeMode.Auto -> {
                            val speed = stageSong?.let { store.settings(it.id).stageSpeed } ?: localSpeed
                            HandsFreeAutoPanel(tempo = stageSong?.tempo ?: 76, speed = speed) { v ->
                                if (stageSong != null) store.update(stageSong.id) { it.copy(stageSpeed = v) } else localSpeed = v
                            }
                        }
                        HandsFreeMode.Voice -> HandsFreeVoicePanel(
                            listening = voice.isRunning, heard = heard, note = voiceNote,
                            showSettings = store.askedMic && !micAllowed,
                            onSettings = { openAppSettings(context) },
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GoldButton("Back to the song") { app.pop() }
                Text("Tip: you can use a pedal and nods together", style = outfit(12), color = OC.nightMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HandsFreeModeCard(m: HandsFreeMode, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.heightIn(min = 86.dp).clip(shape).background(if (on) OC.gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .border(if (on) 2.dp else 1.dp, if (on) OC.gold else OC.nightBorder, shape).plainClickable(onClick = onClick).semantics { selected = on }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (on) OC.gold else Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                SongHandsFreeIcon(m, 16.dp, if (on) OC.inkFixed else OC.nightText)
            }
            Spacer(Modifier.weight(1f))
            if (on) Text("IN USE", style = outfit(10, FontWeight.Heavy, 0.8.sp), color = hex(0x5EEAD4))
        }
        Text(m.title, style = outfit(15, FontWeight.Bold), color = OC.nightText)
        Text(m.subtitle, style = outfit(12), color = OC.nightMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HandsFreePanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().nightGlass(20.dp).padding(14.dp), content = content)
}

@Composable
private fun HandsFreePedalPanel(pedalName: String?, lastPedal: String?) {
    HandsFreePanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(hex(0x14B8A6, 0.18f)), contentAlignment = Alignment.Center) {
                    SongHandsFreeIcon(HandsFreeMode.Pedal, 18.dp, hex(0x5EEAD4))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(pedalName ?: "No pedal connected", style = outfit(15, FontWeight.Bold), color = OC.nightText)
                    Text(
                        lastPedal?.let { "Heard: $it" } ?: (if (pedalName == null) "Pair it in Settings › Bluetooth, then press a pedal" else "Connected · press a pedal to test"),
                        style = outfit(12), color = if (pedalName == null && lastPedal == null) OC.nightMuted else hex(0x5EEAD4),
                    )
                }
            }
            listOf("Right pedal" to "Next section", "Left pedal" to "Previous section", "Hold right" to "Start / stop scroll").forEach { (what, does) ->
                Row(
                    Modifier.fillMaxWidth().drawBehind { drawRect(Color.White.copy(alpha = 0.08f), size = Size(size.width, 1.dp.toPx())) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(what, style = outfit(14), color = hex(0xD6D4CF))
                    Spacer(Modifier.weight(1f))
                    Text(does, style = outfit(14, FontWeight.Bold), color = OC.nightText)
                }
            }
        }
    }
}

@Composable
private fun HandsFreeNodPanel(
    faceFound: Boolean, status: String, lastNod: NodGesture.Move?, hasCamera: Boolean, allowed: Boolean, learning: Boolean,
    onSettings: () -> Unit, onLearn: () -> Unit,
) {
    HandsFreePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val ring by animateColorAsState(if (faceFound) OC.gold else Color.White.copy(alpha = 0.2f), label = "ring")
            val glow by animateFloatAsState(if (faceFound) 0.3f else 0f, label = "glow")
            Box(
                Modifier.width(104.dp).height(128.dp)
                    .then(if (glow > 0f) Modifier.shadow(15.dp, CircleShape, ambientColor = OC.gold.copy(alpha = glow), spotColor = OC.gold.copy(alpha = glow)) else Modifier)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(hex(0x3A332A), hex(0x1B1916)), center = Offset(52f * 3, 51f * 3), radius = 70f * 3))
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SongFaceOutline(OC.nightText.copy(alpha = 0.55f), Modifier.width(60.dp).height(76.dp).offset(y = 8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).then(if (faceFound) Modifier.background(hex(0x5EEAD4)) else Modifier.border(1.5.dp, OC.nightMuted, CircleShape)))
                    Text(status, style = outfit(12, FontWeight.Heavy, 1.sp), color = if (faceFound) hex(0x5EEAD4) else OC.nightMuted)
                }
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Nod down") }
                    append(if (lastNod == NodGesture.Move.Down) " → next section ✓\n" else " → next section\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Tilt up") }
                    append(if (lastNod == NodGesture.Move.Up) " → back one ✓" else " → back one")
                }, style = outfit(14).copy(lineHeight = 20.sp), color = OC.nightText)
                if (!allowed) {
                    Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onSettings), contentAlignment = Alignment.CenterStart) {
                        Text("Allow the camera in Settings ›", style = outfit(13, FontWeight.Bold), color = hex(0xFFC940))
                    }
                } else if (hasCamera) {
                    Box(Modifier.heightIn(min = 44.dp).alpha(if (faceFound) 1f else 0.5f).plainClickable(enabled = faceFound, onClick = onLearn), contentAlignment = Alignment.CenterStart) {
                        Text(if (learning) "Nod down now…" else "Set up: nod down once", style = outfit(13, FontWeight.Bold), color = hex(0xFFC940))
                    }
                }
                Text("Uses the front camera on your phone only. Nothing is recorded.", style = outfit(12).copy(lineHeight = 16.sp), color = OC.nightMuted)
            }
        }
    }
}

@Composable
private fun HandsFreeAutoPanel(tempo: Int, speed: Double, onSpeed: (Double) -> Unit) {
    HandsFreePanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Follow the song’s tempo", style = outfit(15, FontWeight.Bold), color = OC.nightText)
                Spacer(Modifier.weight(1f))
                Text("${(tempo * speed).roundToInt()} BPM", style = mono(20), color = OC.gold)
            }
            Text("Scrolls one bar at a time, 4 beats per bar. Tap the screen twice to pause.", style = outfit(13).copy(lineHeight = 18.sp), color = hex(0xD6D4CF))
            SongSpeedBar(speed, onSpeed)
            Row(Modifier.fillMaxWidth()) {
                Text("0.5×", style = outfit(12), color = OC.nightMuted)
                Spacer(Modifier.weight(1f))
                Text("1.0×", style = outfit(12), color = OC.nightMuted)
                Spacer(Modifier.weight(1f))
                Text("1.5×", style = outfit(12), color = OC.nightMuted)
            }
        }
    }
}

@Composable
private fun HandsFreeVoicePanel(listening: Boolean, heard: String?, note: String, showSettings: Boolean, onSettings: () -> Unit) {
    HandsFreePanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Say one of these", style = outfit(15, FontWeight.Bold), color = OC.nightText)
                Spacer(Modifier.weight(1f))
                if (listening) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = hex(0x5EEAD4), modifier = Modifier.size(14.dp))
                        Text("Listening", style = outfit(12, FontWeight.Bold), color = hex(0x5EEAD4))
                    }
                }
            }
            SongWrapLayout(Modifier.fillMaxWidth(), rowSpacing = 6.dp) {
                listOf("Next", "Back", "Chorus", "Stop").forEach { w ->
                    val on = heard == w
                    val bg by animateColorAsState(if (on) OC.gold else OC.gold.copy(alpha = 0.16f), label = "word")
                    Box(Modifier.songGap(6.dp).height(34.dp).clip(CircleShape).background(bg).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text("“$w”", style = outfit(14, FontWeight.Bold), color = if (on) OC.inkFixed else hex(0xFFC940))
                    }
                }
            }
            Text(note, style = outfit(12).copy(lineHeight = 16.sp), color = OC.nightMuted)
            if (showSettings) {
                Box(Modifier.heightIn(min = 44.dp).plainClickable(onClick = onSettings), contentAlignment = Alignment.CenterStart) {
                    Text("Allow speech recognition in Settings ›", style = outfit(13, FontWeight.Bold), color = hex(0xFFC940))
                }
            }
        }
    }
}

/** Line drawing of a smiling head and shoulders for the head-nod camera preview. */
@Composable
private fun SongFaceOutline(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val head = Rect(Offset(w * 0.22f, 0f), Size(w * 0.56f, w * 0.56f))
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawOval(color, topLeft = head.topLeft, size = head.size, style = stroke)
        val eye = 3.dp.toPx()
        drawOval(color, topLeft = Offset(head.left + head.width * 0.3f - eye / 2, head.top + head.height * 0.38f), size = Size(eye, eye), style = stroke)
        drawOval(color, topLeft = Offset(head.left + head.width * 0.7f - eye / 2, head.top + head.height * 0.38f), size = Size(eye, eye), style = stroke)
        val r = head.width * 0.18f
        val c = Offset(head.center.x, head.top + head.height * 0.55f)
        drawArc(color, startAngle = 25f, sweepAngle = 130f, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke)
        val shoulders = Path().apply {
            moveTo(0f, h)
            quadraticTo(w / 2, head.bottom + (h - head.bottom) * -0.2f, w, h)
        }
        drawPath(shoulders, color, style = stroke)
    }
}
