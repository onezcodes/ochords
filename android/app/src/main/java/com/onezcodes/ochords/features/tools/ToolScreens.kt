package com.onezcodes.ochords.features.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.onezcodes.ochords.R
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.features.library.BannerAdView
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.design.AdBadge
import com.onezcodes.ochords.design.Chip
import com.onezcodes.ochords.design.ChordDiagram
import com.onezcodes.ochords.design.GlassList
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.InkButton
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.PlaceholderBox
import com.onezcodes.ochords.design.Segmented
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.ToggleRow
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.model.ChordInfo
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.ChordVoicing
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.services.ChordLibrary
import com.onezcodes.ochords.services.ChordTheory
import com.onezcodes.ochords.services.MockData
import com.onezcodes.ochords.services.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

// MARK: - Shared helpers (tools group)

private object ToolsMusic {
    /** Flat spelling used by "Chords by key". */
    val flats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val stringNames = listOf("E", "A", "D", "G", "B", "e")
    val fingerNames = mapOf(1 to "Index", 2 to "Middle", 3 to "Ring", 4 to "Pinky")

    /** Transposes a chord (incl. slash chords) and spells the result with flats. */
    fun transposeFlat(chord: String, by: Int): String = chord.split("/").joinToString("/") { p ->
        var root = p.take(1)
        var rest = p.drop(1)
        val f = rest.firstOrNull()
        if (f == 'b' || f == '#') { root += f; rest = rest.drop(1) }
        val i = ChordTheory.index[root] ?: return@joinToString p
        flats[((i + by) % 12 + 12) % 12] + rest
    }
}

// Fixed colours from the always-dark screens.
private object ToolsNight {
    val border = hex(0x393733)
    val cell = hex(0x2A2825)
    val cellEmpty = hex(0x1E1D1B)
    val deepInk = hex(0x0F0E0D)
    val bar = hex(0x171614)
    val tealText = hex(0x5EEAD4)
    val redText = hex(0xFCA5A5)
    val softText = hex(0xBDB9B3)
}

/** Header with a leading icon button (close ✕ / back ‹), centred title and optional trailing view. */
@Composable
private fun ToolsBar(
    title: String,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
    label: String = "Back",
    color: Color = OC.ink,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = outfit(16, FontWeight.Bold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(Modifier.widthIn(min = 44.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** The system share sheet for a MediaStore item (image / PDF). */
private fun shareUri(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun ToolsSectionLabel(text: String, color: Color = OC.muted, modifier: Modifier = Modifier) {
    Text(text, style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = color, modifier = modifier)
}

/** Shows the microphone primer the first time a mic feature is used (permNote on the canvas). Used by Tuner / Chord check. */
@Suppress("unused")
private object ToolsMicPrimer {
    private const val KEY = "tools.micPrimerShown"
    fun showIfNeeded(context: Context, app: AppState) {
        val prefs = context.getSharedPreferences("ochords.prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        prefs.edit().putBoolean(KEY, true).apply()
        app.modal = Modal.Permission(PermissionKind.Microphone)
    }
}

private val errorToastIcon = Icons.Filled.Warning

// MARK: - 7 · Chord finger guide

@Composable
fun ChordGuideView(focus: String?, songChords: List<String>) {
    val app = LocalApp.current
    var selected by remember { mutableStateOf(focus ?: songChords.firstOrNull() ?: "C") }
    var variant by remember { mutableIntStateOf(0) }
    val commonChips = remember(songChords) { ChordLibrary.common.filter { it !in songChords } }
    val info = ChordLibrary.info(selected)
    val voicing = info?.voicings?.takeIf { it.isNotEmpty() }?.let { it[minOf(variant, it.size - 1)] }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Chord guide", backLabel = "Back") { LeftHandButton() }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 14.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (songChords.isNotEmpty()) {
                    ChipLabel("SONG")
                    songChords.forEach { GuideChip(it, it == selected) { selected = it; variant = 0 } }
                    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(OC.switchOff))
                }
                ChipLabel("COMMON")
                commonChips.forEach { GuideChip(it, it == selected) { selected = it; variant = 0 } }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                GuideHeader(selected, info, voicing, variant) { variant = it }
                if (voicing != null) {
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth().glass().padding(top = 14.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                        Box {
                            ChordDiagram(voicing, width = 260.dp, leftHanded = app.leftHanded)
                            if (voicing.baseFret == 1) ChordGuideFretNumbers()
                        }
                    }
                    ChordGuideFingerLegend(Modifier.padding(top = 10.dp))
                    ChordGuideSteps(voicing, Modifier.padding(top = 10.dp))
                    GuideTipCard(voicing, selected, Modifier.padding(top = 12.dp))
                } else {
                    PlaceholderBox("No diagram for $selected yet", Modifier.padding(top = 12.dp), height = 220.dp)
                    GuideTipCard(null, selected, Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun LeftHandButton() {
    val app = LocalApp.current
    val on = app.leftHanded
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.padding(end = 6.dp).heightIn(min = 44.dp).plainClickable { app.leftHanded = !on }.semantics { contentDescription = "Left-handed view" }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(36.dp).clip(shape).background(if (on) OC.ink else OC.glass).border(1.dp, if (on) OC.ink else OC.border, shape).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Left hand", style = outfit(12, FontWeight.Bold), color = if (on) OC.background else OC.ink) }
    }
}

@Composable
private fun ChipLabel(t: String) {
    Text(t, style = outfit(11, FontWeight.Heavy, letterSpacing = 0.5.sp), color = OC.muted, modifier = Modifier.padding(end = 2.dp))
}

@Composable
private fun GuideChip(name: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) { Chip(name, isOn = on, mono = true, height = 40.dp, onClick = onClick) }
}

@Composable
private fun GuideHeader(selected: String, info: ChordInfo?, voicing: ChordVoicing?, variant: Int, onVariant: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(selected, style = mono(40, FontWeight.Heavy), color = OC.goldText)
            if (voicing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${info?.full ?: selected} · ", style = outfit(14), color = OC.muted)
                    Text(voicing.pattern.toCharArray().joinToString(" "), style = mono(14), color = OC.ink)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (info != null && info.voicings.size > 1) VariantPicker(info, variant, onVariant)
    }
}

@Composable
private fun VariantPicker(info: ChordInfo, variant: Int, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(OC.softFill).padding(horizontal = 3.dp).semantics { contentDescription = "Shape" }, verticalAlignment = Alignment.CenterVertically) {
        info.voicings.forEachIndexed { i, v ->
            val on = i == minOf(variant, info.voicings.size - 1)
            Box(Modifier.heightIn(min = 44.dp).plainClickable { onPick(i) }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.height(36.dp)
                        .then(if (on) Modifier.shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f)).clip(shape).background(OC.surface) else Modifier)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(v.label ?: "Shape ${i + 1}", style = outfit(13, FontWeight.Bold), color = if (on) OC.ink else OC.muted) }
            }
        }
    }
}

@Composable
private fun GuideTipCard(v: ChordVoicing?, selected: String, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(OC.goldTint).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = OC.goldText, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (v != null) {
                Text(strumText(v), style = outfit(13, FontWeight.Bold), color = OC.goldText)
                if (v.tip.isNotEmpty()) Text(v.tip, style = outfit(13).copy(lineHeight = 17.sp), color = OC.ink)
            }
            Row(Modifier.heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Check it with the mic ›", style = outfit(13, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable { app.push(Route.Practice(selected)) })
                Text("Save as image ›", style = outfit(13, FontWeight.Bold), color = OC.goldText, modifier = Modifier.plainClickable { app.push(Route.SaveImage(selected)) })
            }
        }
    }
}

private fun strumText(v: ChordVoicing): String {
    val first = v.pattern.indexOfFirst { it != 'x' }.let { if (it < 0) 0 else it }
    return "Strum from string ${6 - first} (${ToolsMusic.stringNames[first]})" + (if (first == 0) " — all six" else "")
}

/** Fret numbers 1–4 down the left of the diagram (design p07); grid starts 28pt below the top at width 260. */
@Composable
private fun ChordGuideFretNumbers() {
    Column(Modifier.offset(x = (-34).dp, y = 28.dp)) {
        for (f in 1..4) Box(Modifier.width(20.dp).height(46.dp), contentAlignment = Alignment.Center) { Text("$f", style = mono(12), color = OC.muted) }
    }
}

@Composable
private fun ChordGuideFingerLegend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for (f in 1..4) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(OC.finger[f]!!), contentAlignment = Alignment.Center) {
                    Text("$f", style = outfit(11, FontWeight.Bold), color = Color.White)
                }
                Text(ToolsMusic.fingerNames[f]!!, style = outfit(12, FontWeight.Bold), color = OC.ink)
            }
        }
    }
}

private class GuideStep(val id: Int, val icon: String, val fill: Color, val fg: Color, val ring: Color, val text: String, val strong: Boolean)

@Composable
private fun ChordGuideSteps(voicing: ChordVoicing, modifier: Modifier = Modifier) {
    val glass = OC.glass; val muted = OC.muted; val switchOff = OC.switchOff; val ink = OC.ink
    val steps = remember(voicing, glass, muted, switchOff, ink) {
        val pat = voicing.pattern
        val b = voicing.barre
        (0 until minOf(6, pat.length)).mapNotNull { i ->
            val dot = voicing.dots.firstOrNull { it.string == i }
            val inBarre = b != null && i >= minOf(b.from, b.to) && i <= maxOf(b.from, b.to)
            when {
                pat[i] == 'x' -> GuideStep(i, "×", glass, muted, switchOff, "Don’t play — mute it", false)
                pat[i] == '0' -> GuideStep(i, "", glass, ink, ink, "Play open", false)
                dot != null -> {
                    val c = OC.finger[dot.finger] ?: OC.inkFixed
                    GuideStep(i, "${dot.finger}", c, Color.White, c, "${ToolsMusic.fingerNames[dot.finger] ?: "Finger"} finger · fret ${dot.fret}", true)
                }
                inBarre && b != null -> GuideStep(i, "1", OC.finger[1]!!, Color.White, OC.finger[1]!!, "Index barre · fret ${b.fret}", true)
                else -> null
            }
        }
    }
    val line = OC.line
    Column(modifier.fillMaxWidth()) {
        steps.forEach { st ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp).drawBehind { drawRect(line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx())) },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${6 - st.id} · ${ToolsMusic.stringNames[st.id]}", style = mono(12), color = OC.muted, modifier = Modifier.width(40.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(st.fill).border(1.5.dp, st.ring, CircleShape), contentAlignment = Alignment.Center) {
                    Text(st.icon, style = outfit(11, FontWeight.Heavy), color = st.fg)
                }
                Text(st.text, style = outfit(14, if (st.strong) FontWeight.Bold else FontWeight.Medium), color = if (st.icon == "×") OC.muted else OC.ink)
            }
        }
    }
}

// MARK: - 8 · Chords by key

private enum class KeysQuality { Major, Minor, Dim }

@Composable
private fun keysBg(q: KeysQuality): Color = when (q) { KeysQuality.Major -> OC.goldTint; KeysQuality.Minor -> OC.blueTint; KeysQuality.Dim -> OC.softFill }

@Composable
private fun keysFg(q: KeysQuality): Color = when (q) { KeysQuality.Major -> OC.goldText; KeysQuality.Minor -> OC.blue; KeysQuality.Dim -> OC.muted }

private object KeysData {
    val steps = listOf(0, 2, 4, 5, 7, 9, 11, 12)
    val quality = listOf("", "m", "m", "", "", "m", "dim", "")
    val roman = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°", "I")
    val sol = listOf("ဒို", "ရေ", "မီ", "ဖာ", "ဆို", "လာ", "တီ", "ဒိုး")
    val used = setOf(0, 3, 4, 5, 7)
    val round = listOf(0, 4, 5, 2, 3, 1, 0, 4)
    val rowKeys = listOf(0, 2, 4, 5, 7, 9, 11)
    val song = listOf("C" to "I", "G/B" to "V/3", "Am" to "vi", "F" to "IV", "G" to "V")

    fun chordAt(k: Int, d: Int): String = ToolsMusic.flats[(k + steps[d]) % 12] + quality[d]
    fun qual(d: Int): KeysQuality = when (quality[d]) { "m" -> KeysQuality.Minor; "dim" -> KeysQuality.Dim; else -> KeysQuality.Major }
}

private class KeysRow(val key: Int, val cells: List<Pair<String, KeysQuality>>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeysView() {
    val app = LocalApp.current
    var key by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var bar by remember { mutableIntStateOf(0) }
    val keyName = ToolsMusic.flats[key]
    val songInKey = remember(key) { KeysData.song.map { ToolsMusic.transposeFlat(it.first, key) } }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) { delay(1500); bar = (bar + 1) % 8 }
    }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Chords by key", backLabel = "Back") {
                Box(Modifier.size(44.dp).plainClickable { app.push(Route.Chords(null, songInKey)) }.semantics { contentDescription = "Finger guide" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 14.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 12) {
                        Box(Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Key ${ToolsMusic.flats[i]}" }, contentAlignment = Alignment.Center) {
                            Chip(ToolsMusic.flats[i], isOn = i == key, mono = true, height = 40.dp) { key = i }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Key of ", style = outfit(28, FontWeight.Heavy, letterSpacing = (-0.6).sp), color = OC.ink, modifier = Modifier.alignByBaseline())
                        Text(keyName, style = mono(28), color = OC.goldText, modifier = Modifier.alignByBaseline())
                        Spacer(Modifier.weight(1f))
                        Text("Major scale", style = outfit(13), color = OC.muted, modifier = Modifier.alignByBaseline())
                    }
                    KeysDegreeGrid(key, songInKey, Modifier.padding(top = 12.dp))
                    KeysLegend(Modifier.padding(top = 10.dp))
                    KeysSongCard(key, keyName, Modifier.padding(top = 16.dp))
                    BannerAdView(Modifier.padding(top = 16.dp)) { app.push(Route.AdChoices) }
                    KeysHeading("Round chord · $keyName family", "8 bars", Modifier.padding(top = 22.dp))
                    KeysRoundCard(key, playing, bar, songInKey, Modifier.padding(top = 8.dp)) { playing = !playing; if (playing) bar = 0 }
                    KeysHeading("Round chord · every family", "Tap a row to practise it", Modifier.padding(top = 22.dp))
                    KeysTable(
                        header = KeysData.round.map { KeysData.roman[it] }, headerIsMyanmar = false,
                        rows = KeysData.rowKeys.map { rk -> KeysRow(rk, KeysData.round.map { KeysData.chordAt(rk, it) to KeysData.qual(it) }) },
                        selected = key, smallFont = 10, modifier = Modifier.padding(top = 8.dp),
                    ) { rk -> key = rk; bar = 0 }
                    KeysHeading("All keys", "Tap a row to open that key", Modifier.padding(top = 22.dp))
                    KeysTable(
                        header = KeysData.sol, headerIsMyanmar = true,
                        rows = KeysData.rowKeys.map { rk -> KeysRow(rk, (0 until 8).map { KeysData.chordAt(rk, it) to KeysData.qual(it) }) },
                        selected = key, smallFont = 9, modifier = Modifier.padding(top = 8.dp),
                    ) { rk -> key = rk }
                }
            }
        }
    }
}

@Composable
private fun KeysDegreeGrid(key: Int, songInKey: List<String>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until 8).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    val name = KeysData.chordAt(key, d)
                    val q = KeysData.qual(d)
                    val used = d in KeysData.used
                    val fg = keysFg(q)
                    Box(
                        Modifier.weight(1f).height(88.dp).clip(shape).background(keysBg(q)).border(2.dp, if (used) OC.ink else Color.Transparent, shape)
                            .plainClickable { app.push(Route.Chords(name, songInKey)) }
                            .semantics { contentDescription = "${KeysData.sol[d]}, $name, ${KeysData.roman[d]}${if (used) ", used in this song" else ""}" },
                    ) {
                        Column(Modifier.fillMaxSize().padding(top = 6.dp, bottom = 8.dp, start = 4.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(KeysData.sol[d], style = myanmar(14, FontWeight.SemiBold), color = fg)
                            Spacer(Modifier.weight(1f))
                            Text(name, style = mono(if (name.length > 4) 16 else if (name.length > 3) 19 else 22), color = fg, maxLines = 1, softWrap = false)
                            Spacer(Modifier.weight(1f))
                            Text(KeysData.roman[d], style = outfit(11, FontWeight.Bold), color = fg, modifier = Modifier.alpha(0.8f))
                        }
                        if (used) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(OC.ink))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeysLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeysLegendItem("Major", keysBg(KeysQuality.Major), OC.gold.copy(alpha = 0.4f))
        KeysLegendItem("Minor", keysBg(KeysQuality.Minor), OC.blue.copy(alpha = 0.3f))
        KeysLegendItem("Diminished", keysBg(KeysQuality.Dim), OC.switchOff)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(OC.ink))
            Text("Used in this song", style = outfit(12, FontWeight.Bold), color = OC.muted)
        }
    }
}

@Composable
private fun KeysLegendItem(t: String, fill: Color, stroke: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(fill).border(1.dp, stroke, CircleShape))
        Text(t, style = outfit(12, FontWeight.Bold), color = OC.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeysSongCard(key: Int, keyName: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().inkGlass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(MockData.sinSarPar.title, style = myanmar(14, FontWeight.Bold), color = OC.nightText, modifier = Modifier.alignByBaseline())
            Text(" in $keyName", style = outfit(14, FontWeight.Bold), color = OC.nightText, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text("original ${MockData.sinSarPar.key}", style = outfit(12), color = ToolsNight.softText, modifier = Modifier.alignByBaseline())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeysData.song.forEach { s ->
                Column(
                    Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 50.dp).clip(RoundedCornerShape(20.dp)).background(ToolsNight.cellEmpty).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(ToolsMusic.transposeFlat(s.first, key), style = mono(15), color = OC.gold)
                    Text(s.second, style = outfit(10, FontWeight.Bold), color = ToolsNight.softText)
                }
            }
        }
    }
}


@Composable
private fun KeysHeading(t: String, sub: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text(t, style = outfit(18, FontWeight.Bold), color = OC.ink, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.weight(1f).width(8.dp))
        Text(sub, style = outfit(12), color = OC.muted, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun KeysRoundCard(key: Int, playing: Boolean, bar: Int, songInKey: List<String>, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Column(modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(KeysData.round.joinToString(" → ") { KeysData.chordAt(key, it) }, style = mono(15), color = OC.goldText)
            Text(KeysData.round.joinToString(" – ") { KeysData.roman[it] }, style = mono(12), color = OC.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeysData.round.withIndex().chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (i, d) -> KeysRoundBar(i, d, key, playing && bar == i, songInKey, Modifier.weight(1f)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (playing) OC.inkFixed else OC.gold).plainClickable(onClick = onPlay)
                    .semantics { contentDescription = if (playing) "Pause round" else "Play round" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = if (playing) Color.White else OC.inkFixed, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Strum ↓ ↓ ↑ ↑ ↓ ↑ · 4 beats a bar", style = outfit(13, FontWeight.Bold), color = OC.ink)
                Text(if (playing) "Looping · change on bar ${bar + 1}" else "Tap play to loop all 8 bars", style = outfit(12), color = OC.muted)
            }
        }
    }
}

@Composable
private fun KeysRoundBar(i: Int, d: Int, key: Int, live: Boolean, songInKey: List<String>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val name = KeysData.chordAt(key, d)
    val q = KeysData.qual(d)
    val fg = if (live) OC.gold else keysFg(q)
    val scale by animateFloatAsState(if (live) 1.04f else 1f, label = "bar")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(76.dp).scale(scale).clip(shape).background(if (live) OC.inkFixed else keysBg(q)).border(2.dp, if (live) OC.gold else Color.Transparent, shape)
            .plainClickable { app.push(Route.Chords(name, songInKey)) }.semantics { contentDescription = "Bar ${i + 1}, $name" }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BAR ${i + 1}", style = outfit(10, FontWeight.Heavy), color = fg, modifier = Modifier.alpha(0.75f))
        Spacer(Modifier.weight(1f))
        Text(name, style = mono(if (name.length > 4) 15 else if (name.length > 3) 18 else 22), color = fg, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Text(KeysData.sol[d], style = myanmar(12, FontWeight.SemiBold), color = fg)
    }
}

/** Key × chord table (Round chord · every family / All keys). */
@Composable
private fun KeysTable(header: List<String>, headerIsMyanmar: Boolean, rows: List<KeysRow>, selected: Int, smallFont: Int, modifier: Modifier = Modifier, pick: (Int) -> Unit) {
    val line = OC.line
    Column(modifier.fillMaxWidth().glass()) {
        Row(Modifier.fillMaxWidth().height(if (headerIsMyanmar) 34.dp else 30.dp).background(OC.softFill), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            header.forEach { h ->
                Text(h, style = if (headerIsMyanmar) myanmar(12, FontWeight.Bold) else mono(10), color = if (headerIsMyanmar) OC.goldText else OC.muted,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        rows.forEach { r ->
            val on = r.key == selected
            Row(
                Modifier.fillMaxWidth().height(36.dp).background(if (on) OC.ink else Color.Transparent)
                    .drawBehind { drawRect(line, size = size.copy(height = 1.dp.toPx())) }
                    .plainClickable { pick(r.key) }
                    .semantics { contentDescription = "Key ${ToolsMusic.flats[r.key]}: ${r.cells.joinToString(", ") { it.first }}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ToolsMusic.flats[r.key], style = outfit(12, FontWeight.Heavy), color = if (on) OC.background else OC.ink, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                r.cells.forEach { c ->
                    val size = if (c.first.length > 4) smallFont else if (c.first.length > 3) minOf(11, smallFont + 1) else 12
                    Text(c.first, style = mono(size), color = if (on) OC.background else keysFg(c.second), maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// MARK: - 19 · Strum trainer

private class StrumPattern(val name: String, val p: List<String>)

private object StrumData {
    val patterns = listOf(
        StrumPattern("Four downs", listOf("D", "", "D", "", "D", "", "D", "")),
        StrumPattern("Folk", listOf("D", "", "D", "U", "", "U", "D", "U")),
        StrumPattern("Ballad", listOf("D", "", "D", "", "D", "U", "D", "U")),
        StrumPattern("Eighths", listOf("D", "U", "D", "U", "D", "U", "D", "U")),
    )
    val progs = listOf(listOf("C", "G", "Am", "F"), listOf("C", "G", "Am", "Em", "F", "Dm", "C", "G"), listOf("C", "Am", "F", "G"))
    val progLabels = listOf("C G Am F", "Round", "C Am F G")
    val count = listOf("1", "&", "2", "&", "3", "&", "4", "&")
}

/** Short synthesised metronome click (an accented one on beat 1), played through a static AudioTrack. */
private class MetronomeClick {
    private val rate = 44100

    private fun make(freq: Double): AudioTrack {
        val n = rate * 30 / 1000
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (sin(2 * PI * freq * t) * exp(-t * 90) * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            .also { it.write(pcm, 0, n) }
    }

    private val accent = make(1600.0)
    private val tick = make(1000.0)

    fun play(accented: Boolean) {
        val t = if (accented) accent else tick
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }

    fun release() { accent.release(); tick.release() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrumTrainerView() {
    val app = LocalApp.current
    var pat by remember { mutableIntStateOf(1) }
    var prog by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var slot by remember { mutableIntStateOf(0) }
    var bar by remember { mutableIntStateOf(0) }
    var bpm by remember { mutableIntStateOf(70) }
    val pattern = StrumData.patterns[pat].p
    val chords = StrumData.progs[prog]
    val click = remember { MetronomeClick() }
    DisposableEffect(Unit) { onDispose { click.release() } }

    // Half-beats at the chosen tempo; the metronome clicks on each beat (accent on 1).
    LaunchedEffect(playing, bpm) {
        if (!playing) return@LaunchedEffect
        val ms = (30000.0 / bpm).roundToInt().toLong()
        if (slot % 2 == 0) click.play(slot == 0)
        while (true) {
            delay(ms)
            val n = (slot + 1) % 8
            slot = n
            if (n == 0) bar += 1
            if (n % 2 == 0) click.play(n == 0)
        }
    }

    OCPage(night = true) {
        Column(Modifier.fillMaxSize()) {
            ToolsBar(title = "Strum trainer", color = OC.nightText, onClick = { app.pop() })
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StrumData.patterns.forEachIndexed { i, p ->
                        val on = i == pat
                        val shape = RoundedCornerShape(14.dp)
                        Box(Modifier.heightIn(min = 44.dp).plainClickable { pat = i }, contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.height(38.dp).clip(shape).background(if (on) OC.nightText else Color.Transparent).border(1.dp, if (on) OC.nightText else ToolsNight.border, shape).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(p.name, style = outfit(13, FontWeight.Bold), color = if (on) ToolsNight.deepInk else OC.nightText) }
                        }
                    }
                }
                Column(Modifier.padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NOW PLAYING", style = outfit(12, FontWeight.Heavy, letterSpacing = 0.6.sp), color = OC.nightMuted)
                    Text(chords[bar % chords.size], style = mono(72), color = OC.gold)
                    Text("next · ${chords[(bar + 1) % chords.size]}", style = mono(14, FontWeight.SemiBold), color = OC.nightMuted)
                }
                StrumSlots(pattern, playing, slot, Modifier.padding(top = 24.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↓ down · ↑ up · empty = miss the strings", style = outfit(12), color = OC.nightMuted)
                    Spacer(Modifier.weight(1f).width(6.dp))
                    Text(pattern.joinToString("") { it.ifEmpty { "·" } }, style = outfit(12), color = OC.nightMuted)
                }
                Column(Modifier.fillMaxWidth().padding(top = 18.dp).nightGlass(20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CHORDS · ONE BAR EACH", style = outfit(12, FontWeight.Heavy, letterSpacing = 0.6.sp), color = OC.nightMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StrumData.progLabels.forEachIndexed { i, l ->
                            val on = i == prog
                            val shape = RoundedCornerShape(14.dp)
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(shape).background(if (on) OC.gold else Color.Transparent).border(1.dp, if (on) OC.gold else ToolsNight.border, shape)
                                    .plainClickable { prog = i; bar = 0 },
                                contentAlignment = Alignment.Center,
                            ) { Text(l, style = mono(12), color = if (on) ToolsNight.deepInk else OC.nightText) }
                        }
                    }
                }
            }
            StrumControls(playing, bpm, onPlay = { if (playing) playing = false else { slot = 0; bar = 0; playing = true } }, onBpm = { bpm = it })
        }
    }
}

@Composable
private fun StrumSlots(pattern: List<String>, playing: Boolean, slot: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().nightGlass(20.dp).padding(vertical = 16.dp, horizontal = 10.dp)
            .semantics { contentDescription = "Pattern " + pattern.joinToString(", ") { if (it == "D") "down" else if (it == "U") "up" else "miss" } },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until 8) {
            val a = pattern[i]
            val live = playing && slot == i
            val scale by animateFloatAsState(if (live) 1.06f else 1f, label = "slot")
            val lift by animateFloatAsState(if (live) (if (a == "U") -4f else 4f) else 0f, label = "lift")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(92.dp).offset(y = lift.dp).scale(scale).clip(RoundedCornerShape(20.dp))
                        .background(if (live) (if (a.isEmpty()) ToolsNight.border else OC.gold) else (if (a.isEmpty()) Color.Transparent else ToolsNight.cell)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (a == "D") "↓" else if (a == "U") "↑" else "", style = outfit(34, FontWeight.Heavy), color = if (live) ToolsNight.deepInk else if (a == "D") OC.nightText else OC.gold)
                }
                Text(StrumData.count[i], style = mono(13), color = if (live) OC.gold else OC.faint)
            }
        }
    }
}

@Composable
private fun StrumControls(playing: Boolean, bpm: Int, onPlay: () -> Unit, onBpm: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ToolsNight.bar).drawBehind { drawRect(ToolsNight.cell, size = size.copy(height = 1.dp.toPx())) }
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 14.dp).navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(OC.gold).plainClickable(onClick = onPlay).semantics { contentDescription = if (playing) "Pause" else "Play" }, contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = ToolsNight.deepInk, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tempo", style = outfit(12), color = OC.nightMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StrumTempoButton("−", "Slower") { onBpm(maxOf(40, bpm - 5)) }
                Text("$bpm BPM", style = mono(20), color = OC.nightText, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                StrumTempoButton("+", "Faster") { onBpm(minOf(160, bpm + 5)) }
            }
        }
    }
}

@Composable
private fun StrumTempoButton(t: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).border(1.dp, ToolsNight.border, RoundedCornerShape(14.dp)).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(t, style = outfit(20), color = OC.nightText)
    }
}

// MARK: - 23 · Mic chord check — ported after the audio services land.


// MARK: - Off-screen drawing (Save as image, Print): the app fonts on an android Canvas

private fun argb(hex: Long, alpha: Float = 1f): Int =
    android.graphics.Color.argb((alpha * 255).roundToInt(), ((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())

private const val WHITE = -1

private class ToolsFonts(context: Context) {
    val outfit: Typeface = ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.DEFAULT
    val mono: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    val myanmar: Typeface = ResourcesCompat.getFont(context, R.font.noto_sans_myanmar) ?: Typeface.DEFAULT

    /** `tracking` is in ems (iOS points ÷ size). */
    fun paint(face: Typeface, size: Float, weight: Int = 400, color: Int = argb(0x171614), tracking: Float = 0f): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).also {
            it.font(face, size, weight)
            it.color = color
            it.letterSpacing = tracking
        }
}

/** The variable font at a weight (`wght` axis), like the Compose `outfit()` / `mono()` / `myanmar()` builders. */
private fun Paint.font(face: Typeface, size: Float, weight: Int) {
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(face, weight, false)
    else Typeface.create(face, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    fontVariationSettings = "'wght' $weight"
    textSize = size
}

private class FontSpan(private val face: Typeface, private val size: Float, private val weight: Int, private val color: Int? = null) : MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) { p.font(face, size, weight) }
    override fun updateDrawState(p: TextPaint) { p.font(face, size, weight); color?.let { p.color = it } }
}

private fun Paint.lineHeight(): Float = fontMetrics.let { it.descent - it.ascent }

private fun layout(text: CharSequence, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, extraLineSpacing: Float = 0f): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, ceil(width).toInt()))
        .setAlignment(align).setLineSpacing(extraLineSpacing, 1f).setIncludePad(false).build()

private fun Canvas.draw(l: StaticLayout, x: Float, y: Float) { save(); translate(x, y); l.draw(this); restore() }

private fun SpannableStringBuilder.add(text: String, span: Any): SpannableStringBuilder = append(text, span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

private fun chordDiagramHeight(width: Float, showStringNames: Boolean): Float = (22 + 6 + 46 * 4 + (if (showStringNames) 24 else 0)) * width / 260f

/** Same geometry as `design/ChordDiagram.kt`, on an android Canvas. `px` is one screen pixel in canvas units (hairlines). */
private fun drawChordDiagram(
    c: Canvas, v: ChordVoicing, left: Float, top: Float, width: Float, px: Float, fonts: ToolsFonts,
    leftHanded: Boolean = false, showFingers: Boolean = true, showStringNames: Boolean = true, dark: Boolean = false,
) {
    val sp = width / 260f
    val ink = if (dark) argb(0xF4F1EA) else argb(0x171614)
    val fretLine = if (dark) argb(0xFFFFFF, 0.18f) else argb(0xD6D4CF)
    val stringCol = if (dark) argb(0xFFFFFF, 0.5f) else argb(0x8E8A84)
    val mutedCol = if (dark) argb(0xA8A49E) else argb(0x6B6660)
    val faint = argb(0x8E8A84)
    val fingers = mapOf(1 to argb(0x8F5E00), 2 to argb(0x1E3A8A), 3 to argb(0x0F766E), 4 to argb(0x6D28D9))
    fun x(i: Int) = left + (if (leftHanded) (250 - 48 * i) else (10 + 48 * i)) * sp
    val fretH = 46 * sp
    val gridTop = top + 28 * sp
    val gridH = fretH * 4
    val dot = 32 * sp
    val hairline = 0.75f * px
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    fun text(t: String, cx: Float, cy: Float, size: Float, color: Int, face: Typeface) {
        tp.font(face, size, 700); tp.color = color
        c.drawText(t, cx, cy - (tp.descent() + tp.ascent()) / 2, tp)
    }

    // open / muted markers
    val pattern = v.pattern
    for (i in 0 until 6) {
        val ch = if (i < pattern.length) pattern[i] else '0'
        val mark = when (ch) { 'x' -> "×"; '0' -> "○"; else -> "" }
        if (mark.isNotEmpty()) text(mark, x(i), top + 10 * sp, 15 * sp, if (ch == 'x') faint else ink, fonts.outfit)
    }
    // nut
    fill.color = if (v.baseFret > 1) fretLine else ink
    c.drawRoundRect(RectF(left + 9 * sp, gridTop - 3 * sp, left + width - 9 * sp, gridTop - 3 * sp + maxOf(6 * sp, 1.5f * px)), 8 * sp, 8 * sp, fill)
    fill.color = fretLine
    for (f in 1..4) {
        val h = maxOf(2 * sp, hairline)
        val y = gridTop + f * fretH - (if (f == 4) sp else 0f) - h / 2
        c.drawRect(left + 10 * sp, y, left + width - 10 * sp, y + h, fill)
    }
    val widths = listOf(3f, 2.6f, 2.2f, 1.8f, 1.4f, 1.2f)
    fill.color = stringCol
    for (i in 0 until 6) {
        val sw = maxOf(widths[i] * sp, hairline)
        c.drawRect(x(i) - sw / 2, gridTop, x(i) + sw / 2, gridTop + gridH, fill)
    }
    v.barre?.let { b ->
        val a = minOf(x(b.from), x(b.to)); val z = maxOf(x(b.from), x(b.to))
        val cy = gridTop + (b.fret - 0.5f) * fretH
        fill.color = fingers[1]!!
        c.drawRoundRect(RectF(a - dot / 2, cy - dot / 2, z + dot / 2, cy + dot / 2), dot / 2, dot / 2, fill)
        if (showFingers) text("1", (a + z) / 2, cy, 15 * sp, WHITE, fonts.outfit)
    }
    for (d in v.dots) {
        val cx = x(d.string); val cy = gridTop + (d.fret - 0.5f) * fretH
        fill.color = if (dark) argb(0x0B0A09) else WHITE
        c.drawCircle(cx, cy, dot / 2, fill)
        fill.color = fingers[d.finger] ?: argb(0x171614)
        c.drawCircle(cx, cy, dot / 2 - 3 * sp, fill)
        if (showFingers) text("${d.finger}", cx, cy, 15 * sp, WHITE, fonts.outfit)
    }
    if (v.baseFret > 1) text("${v.baseFret}fr", left + 4 * sp, gridTop + 15 * sp, 11 * sp, faint, fonts.mono)
    if (showStringNames) {
        val ny = gridTop + gridH + 15 * sp
        for (i in 0 until 6) text(ToolsMusic.stringNames[i], x(i), ny, 12 * sp, mutedCol, fonts.mono)
    }
}

/** Chord-over-word segments wrapped at `width` (the iOS `ToolsFlowLayout` with no spacing). Returns the height. */
private fun flowSegments(c: Canvas?, segs: List<ChordLine.Segment>, x0: Float, y0: Float, width: Float, chordP: TextPaint, textP: TextPaint, trailing: Float): Float {
    if (segs.isEmpty()) return 0f
    val ch = chordP.lineHeight(); val th = textP.lineHeight(); val rowH = ch + th
    var x = 0f; var y = 0f
    for (seg in segs) {
        val chord = seg.chord ?: " "
        val text = seg.text.ifEmpty { " " }
        val w = maxOf(chordP.measureText(chord), textP.measureText(text)) + (if (seg.trailingSpace) trailing else 0f)
        if (x > 0 && x + w > width) { x = 0f; y += rowH }
        if (c != null) {
            c.drawText(chord, x0 + x, y0 + y - chordP.ascent(), chordP)
            c.drawText(text, x0 + x, y0 + y + ch - textP.ascent(), textP)
        }
        x += w
    }
    return y + rowH
}

// MARK: - 34 · Save as image

private class SaveImageFormat(val label: String, val w: Float, val h: Float, val pad: Float, val title: Float) {
    companion object {
        val all = listOf(
            SaveImageFormat("Story 9:16", 208f, 370f, 16f, 17f),
            SaveImageFormat("Square", 300f, 300f, 16f, 17f),
            SaveImageFormat("Sheet A4", 262f, 370f, 18f, 18f),
        )
    }
}

/** Fixed colours (no adaptive tokens) so the rendered image matches the preview. */
private class SaveImageTheme(
    val label: String, val isDark: Boolean, val ink: Int, val muted: Int, val chord: Int,
    val keyBg: Int, val keyFg: Int, val chipBg: Int, val chipFg: Int, val barBg: Int,
) {
    companion object {
        val all = listOf(
            SaveImageTheme("Light", false, argb(0x171614), argb(0x6B6660), argb(0x8F5E00),
                argb(0xFFB800), argb(0x171614), argb(0xFFF4D6), argb(0x6B4600), argb(0x171614, 0.06f)),
            SaveImageTheme("Dark", true, argb(0xFAFAF8), argb(0xA8A49E), argb(0xFFB800),
                argb(0xFFB800), argb(0x171614), argb(0xFFFFFF, 0.1f), argb(0xFFB800), argb(0xFFFFFF, 0.08f)),
            SaveImageTheme("Gold", false, argb(0x171614), argb(0x5A4300), argb(0x171614),
                argb(0x171614), argb(0xFFB800), argb(0x171614, 0.12f), argb(0x171614), argb(0x171614, 0.1f)),
        )
    }

    /** `unit` scales the dark glow's 260pt radius to the canvas. */
    fun drawBackground(c: Canvas, w: Float, h: Float, unit: Float) {
        val p = Paint()
        when (label) {
            "Dark" -> {
                p.color = argb(0x0B0A09); c.drawRect(0f, 0f, w, h, p)
                p.shader = RadialGradient(w, 0f, 260 * unit, intArrayOf(argb(0xFFB800, 0.28f), argb(0xFFB800, 0f)), null, Shader.TileMode.CLAMP)
            }
            "Gold" -> p.shader = LinearGradient(0f, 0f, w, h, argb(0xFFD15A), argb(0xFFB300), Shader.TileMode.CLAMP)
            else -> p.shader = LinearGradient(0f, 0f, w, h, WHITE, argb(0xF6F4EF), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, p)
    }
}

/** The theme's background for the style buttons (same gradients as the image). */
@Composable
private fun SaveImageThemeBackground(t: SaveImageTheme, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        when (t.label) {
            "Dark" -> {
                drawRect(hex(0x0B0A09))
                drawRect(Brush.radialGradient(listOf(hex(0xFFB800, 0.28f), Color.Transparent), center = Offset(size.width, 0f), radius = 260.dp.toPx()))
            }
            "Gold" -> drawRect(Brush.linearGradient(listOf(hex(0xFFD15A), hex(0xFFB300)), start = Offset.Zero, end = Offset(size.width, size.height)))
            else -> drawRect(Brush.linearGradient(listOf(Color.White, hex(0xF6F4EF)), start = Offset.Zero, end = Offset(size.width, size.height)))
        }
    }
}

/** How much of the song the image holds. */
private enum class SaveImagePart(val label: String) { Chorus("Chorus"), VerseChorus("Verse + chorus"), Whole("Whole song") }

private data class SaveImageOptions(
    val fmt: Int = 0,
    val theme: Int = 0,
    val lyrics: Boolean = true,
    val diagrams: Boolean = true,
    val part: SaveImagePart = SaveImagePart.Whole,
    val mark: Boolean = true,
)

/** "── MADE WITH O CHORDS". `size` and `unit` (one point) are in canvas units. Returns the height. */
private fun drawMark(c: Canvas?, fonts: ToolsFonts, color: Int, size: Float, unit: Float, x: Float, y: Float, width: Float, centred: Boolean): Float {
    val p = fonts.paint(fonts.outfit, size, 700, color, tracking = 0.13f)
    val text = "MADE WITH O CHORDS"
    val tw = p.measureText(text); val rule = size * 1.3f; val gap = size * 0.6f
    val h = p.lineHeight()
    if (c != null) {
        val left = if (centred) x + (width - (rule + gap + tw)) / 2 else x
        c.drawRect(left, y + h / 2 - unit, left + rule, y + h / 2 + unit, Paint().apply { this.color = argb(0xFFB800) })
        c.drawText(text, left + rule + gap, y - p.ascent(), p)
    }
    return h
}

/** A single-chord card (opened from the chord guide or a lesson), the chosen format at 3×. */
private fun renderChordCard(fonts: ToolsFonts, chord: String, o: SaveImageOptions, leftHanded: Boolean): Bitmap {
    val f = SaveImageFormat.all[o.fmt]; val t = SaveImageTheme.all[o.theme]
    val u = 3f
    val w = (f.w * u).roundToInt(); val h = (f.h * u).roundToInt(); val pad = f.pad * u
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16 * u, 16 * u, Path.Direction.CW) })
    t.drawBackground(c, w.toFloat(), h.toFloat(), u)
    val info = ChordLibrary.info(chord)
    var y = pad
    val name = layout(chord, fonts.paint(fonts.mono, f.title * 1.6f * u, 700, t.chord), w - 2 * pad)
    c.draw(name, pad, y); y += name.height
    val full = layout(info?.full ?: "Chord", fonts.paint(fonts.outfit, 11 * u, 400, t.muted), w - 2 * pad)
    c.draw(full, pad, y); y += full.height + 8 * u
    info?.voicings?.firstOrNull()?.let { v ->
        val dw = minOf(f.w - f.pad * 2, f.h * 0.55f) * u
        drawChordDiagram(c, v, (w - dw) / 2, y, dw, u, fonts, leftHanded = leftHanded, dark = t.isDark)
    }
    if (o.mark) {
        val mh = drawMark(null, fonts, t.muted, 9 * u, u, 0f, 0f, 0f, false)
        drawMark(c, fonts, t.muted, 9 * u, u, pad, h - pad - mh, w - 2 * pad, false)
    }
    return bmp
}

/**
 * A song as a chord sheet image, always 1080 × 1648 px (the size Myanmar chord sheets are shared at): title and
 * artist, chord diagrams top right, section labels in a left gutter ("V1.", "Cho."), chords over the lyrics,
 * instrumental parts as chord lines. The text is sized to fill the page — larger for a short song, smaller for a
 * long one — so the whole song always fits.
 */
private class SaveImageSheet(private val fonts: ToolsFonts, private val song: Song, private val o: SaveImageOptions, private val leftHanded: Boolean, private val scale: Float) {
    companion object {
        /** The page: 360 × 549⅓ pt rendered at 3×. */
        const val W = 1080f
        const val H = 1648f
        const val U = 3f
        const val PT_W = W / U
        const val PT_H = H / U
        private const val PAD_X = 20 * U
        private const val PAD_TOP = 18 * U
        private const val PAD_BOTTOM = 14 * U

        /** The largest text size at which the chosen part of the song fits the page. */
        fun fitting(fonts: ToolsFonts, song: Song, o: SaveImageOptions, leftHanded: Boolean): Float {
            fun height(s: Float) = SaveImageSheet(fonts, song, o, leftHanded, s).render(null, measuring = true)
            var lo = 0.35f; var hi = 1.8f
            if (height(hi) <= H) return hi
            repeat(12) {
                val mid = (lo + hi) / 2
                if (height(mid) <= H) lo = mid else hi = mid
            }
            return lo
        }

        /** The sections shown. Scanned and typed-in songs don't always mark a chorus (or a verse), so each choice falls
         *  back to what the song does have rather than leaving the image empty. */
        fun shown(song: Song, part: SaveImagePart): List<SongSection> {
            val sung = song.sections.filter { it.kind != SectionKind.Flow && (it.lines.isNotEmpty() || it.bars.isNotEmpty()) }
            val chorus = sung.firstOrNull { it.kind == SectionKind.Chorus }
            val verse = sung.firstOrNull { it.kind == SectionKind.Verse } ?: sung.firstOrNull { it.kind != SectionKind.Chorus }
            return when (part) {
                SaveImagePart.Whole -> sung
                SaveImagePart.Chorus -> listOfNotNull(chorus ?: verse ?: sung.firstOrNull())
                SaveImagePart.VerseChorus -> listOfNotNull(verse, chorus).ifEmpty { sung.take(2) }
            }
        }

        /** Short labels as chord sheets write them: "V1.", "V2.", "Cho.", "Cho 2." (when the chorus comes back), "Br.",
         *  and the section's own name for anything else ("Intro", "Music"). */
        fun gutterLabels(sections: List<SongSection>): List<String> {
            val choruses = sections.count { it.kind == SectionKind.Chorus }
            var chorusSeen = 0
            return sections.map { s ->
                when (s.kind) {
                    SectionKind.Verse -> "V${s.label.filter { it.isDigit() }}."
                    SectionKind.Chorus -> { chorusSeen += 1; if (choruses > 1) "Cho $chorusSeen." else "Cho." }
                    SectionKind.Bridge -> "Br."
                    else -> { val name = s.label.ifEmpty { s.kind.raw }; name.take(1).uppercase() + name.drop(1).lowercase() }
                }
            }
        }
    }

    private val t = SaveImageTheme.all[o.theme]
    private val s = scale
    private val contentW = W - 2 * PAD_X
    private val titleFace = if (song.isMyanmar) fonts.myanmar else fonts.outfit

    fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        render(Canvas(bmp), measuring = false)
        return bmp
    }

    /** Lays the sheet out, drawing it when `c` is given. Returns the natural height (the fixed page when not measuring). */
    fun render(c: Canvas?, measuring: Boolean): Float {
        if (c != null) t.drawBackground(c, W, H, U)
        var y = PAD_TOP
        y += header(c, y)
        val sections = shown(song, o.part)
        val gutter = gutterLabels(sections)
        y += 12 * s * U
        sections.forEachIndexed { i, sec ->
            if (i > 0) y += 9 * s * U
            y += section(c, sec, gutter[i], y)
        }
        if (o.mark) {
            val mh = drawMark(null, fonts, t.muted, 7.5f * U, U, 0f, 0f, 0f, true)
            if (measuring) {
                y += 12 * s * U + mh
            } else {
                drawMark(c, fonts, t.muted, 7.5f * U, U, PAD_X, H - PAD_BOTTOM - mh, contentW, true)
            }
        }
        return if (measuring) y + PAD_BOTTOM else H
    }

    private fun header(c: Canvas?, y: Float): Float {
        val k = minOf(s, 1.3f)
        val chords = if (o.diagrams) song.usedChords.take(5) else emptyList()
        val w = 34 * k * U; val gap = 5 * k * U
        val labelP = fonts.paint(fonts.mono, 8 * k * U, 700, t.ink).apply { textAlign = Paint.Align.CENTER }
        var diagW = 0f; var diagH = 0f
        if (chords.isNotEmpty()) {
            diagW = chords.size * w + (chords.size - 1) * gap
            diagH = labelP.lineHeight() + U + chordDiagramHeight(w, false)
        }
        val leftW = contentW - (if (chords.isNotEmpty()) 10 * U + diagW else 0f)
        val title = layout(song.title, fonts.paint(titleFace, 26 * s * U, 700, t.ink), leftW, extraLineSpacing = if (song.isMyanmar) 4 * s * U else 0f)
        val artist = if (song.artist.isNotEmpty()) layout(song.artist, fonts.paint(titleFace, 14 * s * U, 600, t.muted), leftW) else null
        val key = layout("Key ${song.key}", fonts.paint(fonts.outfit, 10 * s * U, 600, t.muted), leftW)
        var ly = y
        c?.draw(title, PAD_X, ly); ly += title.height + 2 * s * U
        artist?.let { c?.draw(it, PAD_X, ly); ly += it.height + 2 * s * U }
        c?.draw(key, PAD_X, ly); ly += key.height
        if (c != null && chords.isNotEmpty()) {
            var x = PAD_X + contentW - diagW
            for (ch in chords) {
                c.drawText(ch, x + w / 2, y - labelP.ascent(), labelP)
                ChordLibrary.info(ch)?.voicings?.firstOrNull()?.let { v ->
                    drawChordDiagram(c, v, x, y + labelP.lineHeight() + U, w, U, fonts, leftHanded, showFingers = false, showStringNames = false, dark = t.isDark)
                }
                x += w + gap
            }
        }
        return maxOf(ly - y, diagH)
    }

    /** Instrumental parts: one chord line ("Music – C / D / Em / G"); sung parts: a gutter label and the lines. */
    private fun section(c: Canvas?, sec: SongSection, label: String, y: Float): Float {
        if (sec.lines.isEmpty()) {
            val sb = SpannableStringBuilder()
                .add("$label – ", FontSpan(fonts.outfit, 11 * s * U, 700))
                .add(sec.bars.joinToString(" / "), FontSpan(fonts.mono, 11 * s * U, 700))
            val l = layout(sb, fonts.paint(fonts.outfit, 11 * s * U, 700, t.chord), contentW)
            c?.draw(l, PAD_X, y)
            return l.height.toFloat()
        }
        val labelW = 34 * s * U
        val labelTop = if (o.lyrics) 11 * s * U else 0f
        val labelL = layout(label, fonts.paint(fonts.outfit, 11 * s * U, 800, t.ink), labelW)
        c?.draw(labelL, PAD_X, y + labelTop)
        val bx = PAD_X + labelW + 6 * s * U
        val bw = contentW - labelW - 6 * s * U
        var by = y
        var first = true
        fun gap() { if (!first) by += 2 * s * U; first = false }
        if (sec.bars.isNotEmpty()) {
            gap()
            val l = layout(sec.bars.joinToString(" / "), fonts.paint(fonts.mono, 10.5f * s * U, 700, t.chord), bw)
            c?.draw(l, bx, by); by += l.height
        }
        for (line in sec.lines) { gap(); by += lineView(c, line, bx, by, bw) }
        return maxOf(labelTop + labelL.height, by - y)
    }

    private fun lineView(c: Canvas?, line: String, x: Float, y: Float, width: Float): Float {
        if (!o.lyrics) {
            val l = layout(ChordLine.chords(line).joinToString("   "), fonts.paint(fonts.mono, 11 * s * U, 700, t.chord), width)
            c?.draw(l, x, y)
            return l.height.toFloat()
        }
        val chordP = fonts.paint(fonts.mono, 10 * s * U, 700, t.chord)
        val textP = fonts.paint(titleFace, 12 * s * U, 600, t.ink)
        return flowSegments(c, ChordLine.parse(line), x, y, width, chordP, textP, 4 * s * U)
    }
}

/** Saves a PNG to Pictures/O Chords through MediaStore; null when it couldn't be written. */
private fun savePng(context: Context, bitmap: Bitmap, name: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/O Chords")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return null
    return runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("No output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        uri
    }.getOrElse { resolver.delete(uri, null, null); null }
}

private fun fileSafe(name: String): String = name.replace("/", "-")

@Composable
fun SaveImageView(chord: String, song: Song?) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember { ToolsFonts(context) }
    var o by remember { mutableStateOf(SaveImageOptions()) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val leftHanded = app.leftHanded
    val previewHeight = 392.dp

    // Song images: the text size that fits the fixed page (the style doesn't change the text's size; everything else can).
    val fitKey = o.copy(theme = 0)
    var scale by remember { mutableStateOf(1f) }
    var fitted by remember { mutableStateOf<Pair<SaveImageOptions, Boolean>?>(null) }
    LaunchedEffect(o, leftHanded) {
        image = withContext(Dispatchers.Default) {
            if (song != null) {
                if (fitted != fitKey to leftHanded) {
                    scale = SaveImageSheet.fitting(fonts, song, o, leftHanded)
                    fitted = fitKey to leftHanded
                }
                SaveImageSheet(fonts, song, o, leftHanded, scale).render()
            } else renderChordCard(fonts, chord, o, leftHanded)
        }
    }

    fun export(share: Boolean) {
        val bmp = image ?: run { toast = Toast("Couldn’t render the image", errorToastIcon); return }
        if (busy) return
        busy = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) { savePng(context, bmp, fileSafe("O Chords – ${song?.title ?: chord}")) }
            busy = false
            when {
                uri == null -> toast = Toast("Couldn’t save the image", errorToastIcon)
                share -> shareUri(context, uri, "image/png")
                else -> toast = Toast("Saved to Photos")
            }
        }
    }

    OCPage {
        ToastHost(toast, { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                ToolsBar(title = "Save as image", icon = Icons.Filled.Close, label = "Close", onClick = { app.pop() })
                Box(Modifier.fillMaxWidth().height(previewHeight).semantics { contentDescription = "Image preview" }, contentAlignment = Alignment.Center) {
                    val (w, h) = if (song != null) {
                        val k = (previewHeight.value - 24) / SaveImageSheet.PT_H
                        (SaveImageSheet.PT_W * k).dp to (SaveImageSheet.PT_H * k).dp
                    } else {
                        val f = SaveImageFormat.all[o.fmt]
                        f.w.dp to f.h.dp
                    }
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.size(w, h).shadow(25.dp, shape, ambientColor = OC.inkFixed.copy(alpha = 0.22f), spotColor = OC.inkFixed.copy(alpha = 0.22f)).clip(shape)
                            .then(if (song == null) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = image
                        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Box(Modifier.fillMaxSize().background(OC.softFill), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = OC.gold, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (song == null) {
                        SaveImageOptionGroup("SIZE") {
                            Segmented(SaveImageFormat.all.indices.toList(), o.fmt, label = { SaveImageFormat.all[it].label }) { o = o.copy(fmt = it) }
                        }
                    }
                    SaveImageOptionGroup("STYLE") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SaveImageTheme.all.forEachIndexed { i, th ->
                                val on = i == o.theme
                                val shape = RoundedCornerShape(14.dp)
                                Box(
                                    Modifier.weight(1f).height(52.dp).clip(shape).border(2.dp, if (on) OC.gold else OC.border, shape).plainClickable { o = o.copy(theme = i) }
                                        .semantics { contentDescription = "${th.label} style" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SaveImageThemeBackground(th, Modifier.fillMaxSize())
                                    Text(th.label, style = outfit(13, FontWeight.Bold), color = if (th.isDark) hex(0xFAFAF8) else hex(0x171614))
                                }
                            }
                        }
                    }
                    GlassList {
                        if (song != null) {
                            ToggleRow("Lyrics", o.lyrics, subtitle = "Off = chord chart only") { o = o.copy(lyrics = it) }
                            ToggleRow("Chords used", o.diagrams, subtitle = "Show the chord list on top") { o = o.copy(diagrams = it) }
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("How much of the song", style = outfit(14, FontWeight.SemiBold), color = OC.ink)
                                Segmented(SaveImagePart.entries, o.part, label = { it.label }) { o = o.copy(part = it) }
                            }
                        }
                        ToggleRow("Watermark", o.mark, subtitle = "Pro can remove it") { v -> if (!v && !app.isPro) app.modal = Modal.Pro else o = o.copy(mark = v) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(OC.surface.copy(alpha = 0.7f)).background(OC.glass)
                        .drawBehind { drawRect(Color.White.copy(alpha = 0.6f), size = size.copy(height = 1.dp.toPx())) }
                        .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(54.dp).glass(14.dp).plainClickable { export(share = true) }.semantics { contentDescription = "Share image" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                    }
                    GoldButton("Save to Photos", Modifier.weight(1f), enabled = !busy, leading = { Icon(Icons.Outlined.Download, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(20.dp)) }) { export(share = false) }
                }
            }
        }
    }
}

@Composable
private fun SaveImageOptionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolsSectionLabel(title)
        content()
    }
}

// MARK: - 42 · Print or PDF

private data class PrintOptions(
    val paper: Int = 0,          // 0 A4 · 1 US Letter
    val layout: Int = 1,         // 0 one column · 1 two columns
    val diagrams: Boolean = true,
    val lyrics: Boolean = true,
    val large: Boolean = false,
    val breaks: Int = 0,         // 0 auto · 1 each section · 2 manual
    /** Manual mode: sections (by index) that start a new page. */
    val manualBreaks: Set<Int> = emptySet(),
) {
    /** PDF points. */
    val pageW: Float get() = if (paper == 0) 595f else 612f
    val pageH: Float get() = if (paper == 0) 842f else 792f
    val columns: Int get() = if (layout == 1) 2 else 1
    val columnWidth: Float get() = (pageW - MARGIN_X * 2 - (if (columns == 2) COLUMN_GAP else 0f)) / columns
    val sectionSpacing: Float get() = if (large) 16f else 12f

    companion object {
        /** Page margins: 40 pt each side, 42 pt top and bottom. */
        const val MARGIN_X = 40f
        const val MARGIN_Y = 42f
        const val COLUMN_GAP = 24f
        const val SPACING = 16f
    }
}

/** A section, or the part of one that fits: some of its lines (a section too long for the space continues). */
private data class PrintBlock(val section: Int, val lines: IntRange, val continued: Boolean)

/** Draws (or, with a null canvas, measures) the printable pieces at real PDF point size. Fixed light colours (paper is white). */
private class PrintRenderer(private val fonts: ToolsFonts, private val song: Song, private val o: PrintOptions) {
    val sections: List<SongSection> = song.sections.filter { it.kind != SectionKind.Flow }
    private val ink = argb(0x171614)
    private val muted = argb(0x6B6660)
    private val gold = argb(0x8F5E00)
    private val inner = o.pageW - PrintOptions.MARGIN_X * 2

    // Fixed light-mode tag colours (paper).
    private fun tagColors(k: SectionKind): Pair<Int, Int> = when (k) {
        SectionKind.Verse -> argb(0xE3E8F6) to argb(0x1E3A8A)
        SectionKind.Chorus -> argb(0xFFB800) to argb(0x171614)
        SectionKind.Intro, SectionKind.Music -> argb(0xDDEFEA) to argb(0x0F5B53)
        SectionKind.Bridge -> argb(0x6D28D9, 0.14f) to argb(0x6D28D9)
        SectionKind.Flow -> argb(0x171614, 0.05f) to argb(0x6B6660)
    }

    fun header(c: Canvas?, y: Float): Float {
        val badge = fonts.paint(fonts.outfit, 9f, 800, gold, tracking = 1.6f / 9f)
        val badgeText = "O CHORDS"
        val badgeW = badge.measureText(badgeText)
        val leftW = inner - badgeW - 8
        val title = layout(song.title, fonts.paint(if (song.isMyanmar) fonts.myanmar else fonts.outfit, if (o.large) 32f else 24f, 700, ink), leftW)
        val sub = SpannableStringBuilder().add(song.artist, FontSpan(fonts.myanmar, 11f, 400)).append(" · Key ${song.key} · 4/4 · ${song.tempo} BPM")
        val subL = layout(sub, fonts.paint(fonts.outfit, 11f, 400, muted), leftW)
        if (c != null) {
            c.draw(title, PrintOptions.MARGIN_X, y)
            c.draw(subL, PrintOptions.MARGIN_X, y + title.height + 2)
            c.drawText(badgeText, PrintOptions.MARGIN_X + inner - badgeW, y - badge.ascent(), badge)
        }
        return title.height + 2f + subL.height
    }

    fun diagrams(c: Canvas?, y: Float): Float {
        val chords = song.usedChords.take(8)
        if (chords.isEmpty()) return 0f
        val p = fonts.paint(fonts.mono, 11f, 700, ink).apply { textAlign = Paint.Align.CENTER }
        val w = 44f
        var x = PrintOptions.MARGIN_X
        for (ch in chords) {
            if (c != null) {
                c.drawText(ch, x + w / 2, y - p.ascent(), p)
                ChordLibrary.info(ch)?.voicings?.firstOrNull()?.let { v -> drawChordDiagram(c, v, x, y + p.lineHeight() + 2, w, 1f, fonts, showFingers = false, showStringNames = false) }
            }
            x += w + 14
        }
        return p.lineHeight() + 2 + chordDiagramHeight(w, false)
    }

    fun footer(c: Canvas?, y: Float, page: Int, pageCount: Int): Float {
        val date = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val l = layout("Shared on O Chords · $date · page ${page + 1} of $pageCount", fonts.paint(fonts.outfit, 9f, 400, argb(0x8E8A84)), inner)
        c?.draw(l, PrintOptions.MARGIN_X, y)
        return l.height.toFloat()
    }

    /** A section's tag and some (or all) of its lines; "(cont.)" when it carries on from the previous column or page. */
    fun section(c: Canvas?, sec: SongSection, lines: IntRange, continued: Boolean, x: Float, y: Float, width: Float): Float {
        val (bg, fg) = tagColors(sec.kind)
        val tagP = fonts.paint(fonts.outfit, 8f, 800, fg, tracking = 0.6f / 8f)
        val tag = sec.label.uppercase() + (if (continued) " (CONT.)" else "")
        val tagW = tagP.measureText(tag) + 10
        val tagH = tagP.lineHeight() + 4
        if (c != null) {
            c.drawRoundRect(RectF(x, y, x + tagW, y + tagH), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
            c.drawText(tag, x + 5, y + 2 - tagP.ascent(), tagP)
            if (!continued && sec.note != null) {
                val np = fonts.paint(fonts.outfit, 8f, 400, muted)
                c.drawText(sec.note, x + tagW + 6, y + (tagH - np.lineHeight()) / 2 - np.ascent(), np)
            }
        }
        var yy = y + tagH
        if (!continued && sec.bars.isNotEmpty()) {
            yy += 4
            val l = layout("| " + sec.bars.joinToString("  |  ") + " |", fonts.paint(fonts.mono, if (o.large) 13f else 10f, 700, gold), width)
            c?.draw(l, x, yy)
            yy += l.height
        }
        for (i in lines) {
            yy += 4
            yy += line(c, sec.lines[i], x, yy, width)
        }
        return yy - y
    }

    fun line(c: Canvas?, line: String, x: Float, y: Float, width: Float): Float {
        if (!o.lyrics) {
            val l = layout(ChordLine.chords(line).joinToString("    "), fonts.paint(fonts.mono, if (o.large) 13f else 10f, 700, gold), width)
            c?.draw(l, x, y)
            return l.height.toFloat()
        }
        val chordP = fonts.paint(fonts.mono, if (o.large) 12f else 9f, 700, gold)
        val textP = fonts.paint(fonts.myanmar, if (o.large) 14f else 10.5f, 400, ink)
        return flowSegments(c, ChordLine.parse(line), x, y, width, chordP, textP, 4f)
    }

    /** One printable page. */
    fun drawPage(c: Canvas, columns: List<List<PrintBlock>>, page: Int, pageCount: Int) {
        c.drawColor(WHITE)
        var y = PrintOptions.MARGIN_Y
        if (page == 0) {
            y += header(c, y) + PrintOptions.SPACING
            if (o.diagrams) y += diagrams(c, y) + PrintOptions.SPACING
        }
        for (ci in 0 until o.columns) {
            val x = PrintOptions.MARGIN_X + ci * (o.columnWidth + PrintOptions.COLUMN_GAP)
            var cy = y
            columns.getOrNull(ci)?.forEachIndexed { i, b ->
                if (i > 0) cy += o.sectionSpacing
                cy += section(c, sections[b.section], b.lines, b.continued, x, cy, o.columnWidth)
            }
        }
        val fh = footer(null, 0f, page, pageCount)
        footer(c, o.pageH - PrintOptions.MARGIN_Y - fh, page, pageCount)
    }

    /** The page as a bitmap for the on-screen preview. */
    fun renderPage(columns: List<List<PrintBlock>>, page: Int, pageCount: Int, scale: Float = 2f): Bitmap {
        val bmp = Bitmap.createBitmap((o.pageW * scale).roundToInt(), (o.pageH * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, columns, page, pageCount)
        return bmp
    }

    /** The PDF, one page per laid-out page, written to the cache. */
    fun makePdf(context: Context, pages: List<List<List<PrintBlock>>>): File {
        val doc = PdfDocument()
        pages.forEachIndexed { p, cols ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(o.pageW.roundToInt(), o.pageH.roundToInt(), p + 1).create())
            drawPage(page.canvas, cols, p, pages.size)
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, "${fileSafe("O Chords – ${song.title}")}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

/**
 * Lays the whole song out page by page — every line of it — honouring the page-break choice. Sections are measured
 * at the real column width; one that doesn't fit the space left moves to the next column or page, and one taller
 * than a whole column is split between its lines. Pages → columns → blocks.
 */
private object PrintPaginator {
    fun layout(r: PrintRenderer, o: PrintOptions): List<List<List<PrintBlock>>> {
        val sections = r.sections
        val width = o.columnWidth
        val footer = ceil(r.footer(null, 0f, 0, 1))
        // Below the columns: a gap, the spacer, a gap and the footer. A little slack for rounding.
        val body = o.pageH - PrintOptions.MARGIN_Y * 2 - footer - PrintOptions.SPACING * 2 - 6
        var top = ceil(r.header(null, 0f)) + PrintOptions.SPACING
        if (o.diagrams) top += ceil(r.diagrams(null, 0f)) + PrintOptions.SPACING
        fun capacity(page: Int) = if (page == 0) body - top else body

        val pages = mutableListOf(mutableListOf(mutableListOf<PrintBlock>()))
        var used = 0f
        fun columnEmpty() = pages.last().last().isEmpty()
        fun nextColumn() {
            if (pages.last().size < o.columns) pages.last().add(mutableListOf()) else pages.add(mutableListOf(mutableListOf()))
            used = 0f
        }
        fun nextPage() { pages.add(mutableListOf(mutableListOf())); used = 0f }
        fun place(block: PrintBlock, height: Float) {
            used += (if (columnEmpty()) 0f else o.sectionSpacing) + height
            pages.last().last().add(block)
        }
        fun measure(sec: SongSection, lines: IntRange, continued: Boolean) = ceil(r.section(null, sec, lines, continued, 0f, 0f, width))

        for ((i, section) in sections.withIndex()) {
            val pageEmpty = pages.last().all { it.isEmpty() }
            val forced = i > 0 && (o.breaks == 1 || (o.breaks == 2 && i in o.manualBreaks))
            if (forced && !pageEmpty) nextPage()

            val all = section.lines.indices
            val full = measure(section, all, false)
            val gap = if (columnEmpty()) 0f else o.sectionSpacing
            if (used + gap + full <= capacity(pages.size - 1)) {
                place(PrintBlock(i, all, false), full)
                continue
            }
            if (!columnEmpty() && full <= capacity(pages.size)) {
                nextColumn()
                place(PrintBlock(i, all, false), full)
                continue
            }
            // Longer than the space: split between lines, continuing in the next column or page.
            val head = measure(section, IntRange.EMPTY, false)
            val lineHeights = section.lines.map { ceil(r.line(null, it, 0f, 0f, width)) }
            var start = 0
            while (start < section.lines.size) {
                val g = if (columnEmpty()) 0f else o.sectionSpacing
                var k = 0
                var height = head
                while (start + k < section.lines.size) {
                    val next = height + 4 + lineHeights[start + k]
                    if (used + g + next > capacity(pages.size - 1)) break
                    height = next
                    k += 1
                }
                if (k == 0) {
                    if (columnEmpty()) { k = 1; height = head + 4 + lineHeights[start] } else { nextColumn(); continue }
                }
                place(PrintBlock(i, start until start + k, start > 0), height)
                start += k
                if (start < section.lines.size) nextColumn()
            }
        }
        // Drop a trailing empty column or page left by a break.
        while (pages.size > 1 && pages.last().all { it.isEmpty() }) pages.removeAt(pages.size - 1)
        return pages
    }
}

/** Hands a finished PDF file to the system print dialog. */
private class PdfPrintAdapter(private val file: File, private val name: String, private val pageCount: Int) : PrintDocumentAdapter() {
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount).build(), true)
    }

    override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
        try {
            FileInputStream(file).use { input -> FileOutputStream(dest.fileDescriptor).use { output -> input.copyTo(output) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

/** Copies the PDF into Downloads/O Chords through MediaStore so it can be shared without a FileProvider. */
private fun publishPdf(context: Context, file: File, name: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.pdf")
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/O Chords")
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "O Chords").apply { mkdirs() }
        @Suppress("DEPRECATION")
        values.put(MediaStore.MediaColumns.DATA, File(dir, "$name.pdf").absolutePath)
        MediaStore.Files.getContentUri("external")
    }
    val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
    return runCatching {
        resolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out) } } ?: error("No output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        uri
    }.getOrElse { resolver.delete(uri, null, null); null }
}

@Composable
fun PrintPdfView(song: Song) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember { ToolsFonts(context) }
    var o by remember { mutableStateOf(PrintOptions()) }
    var previewPage by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    /** The laid-out pages (columns of blocks), redone whenever an option changes. */
    var pages by remember { mutableStateOf<List<List<List<PrintBlock>>>>(listOf(listOf(emptyList()))) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val renderer = remember(o) { PrintRenderer(fonts, song, o) }
    val pageCount = maxOf(1, pages.size)
    val sectionLabels = renderer.sections.map { s -> s.label.ifEmpty { s.kind.raw.replaceFirstChar { it.uppercase() } } }

    LaunchedEffect(renderer) {
        val laid = withContext(Dispatchers.Default) { PrintPaginator.layout(renderer, o) }
        pages = laid
        if (previewPage >= laid.size) previewPage = 0
    }
    LaunchedEffect(renderer, pages, previewPage) {
        preview = withContext(Dispatchers.Default) { renderer.renderPage(pages.getOrElse(previewPage) { emptyList() }, previewPage, pageCount) }
    }

    fun export(print: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { renderer.makePdf(context, pages) }.getOrNull() }
            busy = false
            if (file == null) { toast = Toast("Couldn’t create the PDF", errorToastIcon); return@launch }
            if (print) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attrs = PrintAttributes.Builder().setMediaSize(if (o.paper == 0) PrintAttributes.MediaSize.ISO_A4 else PrintAttributes.MediaSize.NA_LETTER).build()
                runCatching { pm.print(song.title, PdfPrintAdapter(file, file.name, pages.size), attrs) }
                    .onFailure { toast = Toast("Couldn’t open the print dialog", errorToastIcon) }
            } else {
                val uri = withContext(Dispatchers.IO) { publishPdf(context, file, file.nameWithoutExtension) }
                if (uri == null) toast = Toast("Couldn’t create the PDF", errorToastIcon) else shareUri(context, uri, "application/pdf")
            }
        }
    }

    OCPage {
        ToastHost(toast, { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "Print or PDF", backLabel = "Back to song")
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrintPreview(o, preview, previewPage, pageCount, Modifier.padding(top = 12.dp)) { if (pageCount > 1) previewPage = (previewPage + 1) % pageCount }
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrintSegRow("Paper", listOf("A4", "US Letter"), o.paper) { o = o.copy(paper = it) }
                        PrintSegRow("Layout", listOf("1 column", "2 columns"), o.layout) { o = o.copy(layout = it) }
                        PrintSegRow("Breaks", listOf("Auto", "Each section", "Manual"), o.breaks) { o = o.copy(breaks = it) }
                        if (o.breaks == 2) {
                            GlassList {
                                sectionLabels.forEachIndexed { i, label ->
                                    if (i == 0) return@forEachIndexed
                                    ToggleRow("New page before ${label.uppercase()}", i in o.manualBreaks) { on ->
                                        o = o.copy(manualBreaks = if (on) o.manualBreaks + i else o.manualBreaks - i)
                                    }
                                }
                            }
                        }
                        GlassList {
                            ToggleRow("Chord diagrams at the top", o.diagrams) { o = o.copy(diagrams = it) }
                            ToggleRow("Include lyrics", o.lyrics) { o = o.copy(lyrics = it) }
                            ToggleRow("Large print", o.large) { o = o.copy(large = it) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 10.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkButton("Print", enabled = !busy) { export(print = true) }
                        Icon(Icons.Outlined.Print, contentDescription = null, tint = if (isDark) hex(0x171614) else hex(0xFAFAF8),
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp).size(18.dp))
                    }
                    GoldButton("Share PDF", Modifier.weight(1f), enabled = !busy, leading = { Icon(Icons.Outlined.Share, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp)) }) { export(print = false) }
                }
            }
        }
    }
}

@Composable
private fun PrintPreview(o: PrintOptions, preview: Bitmap?, previewPage: Int, pageCount: Int, modifier: Modifier = Modifier, onFlip: () -> Unit) {
    val pw = if (o.paper == 0) 232.dp else 240.dp
    val ph = pw * o.pageH / o.pageW
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(pw, ph)
                .shadow(20.dp, RoundedCornerShape(4.dp), ambientColor = OC.inkFixed.copy(alpha = 0.16f), spotColor = OC.inkFixed.copy(alpha = 0.16f))
                .clip(RoundedCornerShape(4.dp)).background(Color.White).plainClickable(onClick = onFlip)
                .semantics { contentDescription = "Page preview, page ${previewPage + 1} of $pageCount" },
        ) {
            if (preview != null) Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (pageCount > 1) Text("Page ${previewPage + 1} of $pageCount · tap to flip", style = outfit(12), color = OC.muted)
    }
}

@Composable
private fun PrintSegRow(label: String, options: List<String>, selection: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = outfit(13, FontWeight.Bold), color = OC.muted, modifier = Modifier.width(64.dp))
        Segmented(options.indices.toList(), selection, Modifier.weight(1f), label = { options[it] }, onSelect = onSelect)
    }
}

// MARK: - 43 · Report a sheet (+ sent)

private val reportReasons = listOf(
    "Wrong chords or lyrics" to "Chords don’t match the song, or lines are missing",
    "Copyright" to "You own this song and didn’t allow it to be shared",
    "Spam or advertising" to "Not a real chord sheet",
    "Offensive content" to "Hate, harassment or adult content",
    "Something else" to "Tell us below",
)

@Composable
fun ReportView(song: Song) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send() {
        val r = reason ?: return
        if (sending) return
        // Reports carry the reporter's account, so moderators can follow up and to deter abuse.
        if (!(app.isSignedIn || app.usesDemoData)) { error = "Log in or create an account to report a sheet."; return }
        sending = true
        error = null
        scope.launch {
            try {
                app.backend.report(song.id, reportReasons[r].first, detail)
                sent = true
            } catch (e: Exception) {
                error = "Couldn’t send the report. Try again."
            }
            sending = false
        }
    }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            ToolsBar(title = "Report", icon = Icons.Filled.Close, label = "Close", onClick = { app.pop() })
            Box(Modifier.fillMaxSize()) {
                androidx.compose.animation.AnimatedVisibility(visible = sent, enter = fadeIn(), exit = fadeOut()) { ReportSentView(reason) }
                androidx.compose.animation.AnimatedVisibility(visible = !sent, enter = fadeIn(), exit = fadeOut()) {
                    ReportForm(song, reason, detail, sending, error, onReason = { reason = it; error = null }, onDetail = { detail = it }, onSend = ::send)
                }
            }
        }
    }
}

@Composable
private fun ReportForm(song: Song, reason: Int?, detail: String, sending: Boolean, error: String?, onReason: (Int) -> Unit, onDetail: (String) -> Unit, onSend: () -> Unit) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().glass(16.dp).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OC.goldTint), contentAlignment = Alignment.Center) {
                    Text(song.key, style = mono(17), color = OC.goldText)
                }
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = if (song.isMyanmar) myanmar(16, FontWeight.Bold) else outfit(16, FontWeight.SemiBold), color = OC.ink)
                    Text("Shared by ${song.uploader?.name ?: "[uploader]"} · public sheet", style = outfit(12), color = OC.muted)
                }
            }
            Text("What’s wrong with this sheet?", style = outfit(24, FontWeight.Heavy, letterSpacing = (-0.5).sp), color = OC.ink)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reportReasons.forEachIndexed { i, r -> ReportReasonRow(r.first, r.second, reason == i) { onReason(i) } }
            }
            AnimatedVisibility(visible = reason == 0, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OC.tealTint)
                        .plainClickable { app.push(Route.Review(ScanResult(song = song, detectedZawgyi = false, lowConfidence = emptyList()))) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("Faster: suggest a fix instead — the uploader gets it straight away ›", style = outfit(13, FontWeight.Bold), color = OC.tealText)
                }
            }
            BasicTextField(
                value = detail, onValueChange = onDetail, textStyle = outfit(15).copy(color = OC.ink), minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).clip(RoundedCornerShape(14.dp)).background(OC.surface).border(1.dp, OC.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (detail.isEmpty()) Text("Anything else we should know? (optional)", style = outfit(15), color = OC.muted)
                        inner()
                    }
                },
            )
            if (error != null) Text(error, style = outfit(13, FontWeight.SemiBold), color = OC.red)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (reason != null) {
                GoldButton("Send report", enabled = !sending, leading = if (sending) ({ CircularProgressIndicator(color = OC.inkFixed, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }) else null, onClick = onSend)
            } else {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(OC.rButton)).background(OC.softFill).semantics { contentDescription = "Send report. Choose a reason first" },
                    contentAlignment = Alignment.Center,
                ) { Text("Send report", style = outfit(16, FontWeight.Bold), color = OC.faint) }
            }
            Text("Reports are private. The uploader won’t see your name.", style = outfit(12), color = OC.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ReportReasonRow(title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) OC.goldTint else OC.glass).border(if (on) 2.dp else 1.dp, if (on) OC.gold else OC.border, shape)
            .plainClickable(onClick = onClick).semantics { contentDescription = "$title. $sub${if (on) ", selected" else ""}" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(2.dp, if (on) OC.ink else OC.switchOff, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(OC.ink))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = outfit(15, FontWeight.Bold), color = OC.ink)
            Text(sub, style = outfit(12), color = OC.muted)
        }
    }
}

@Composable
private fun ReportSentView(reason: Int?) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(
                    Modifier.size(96.dp).shadow(10.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f)).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(36.dp)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thanks — we’ll take a look", style = outfit(26, FontWeight.Bold, letterSpacing = (-0.6).sp), color = OC.ink, textAlign = TextAlign.Center)
                    Text("Our team reviews reports within [48 hours]. We’ll let you know in Notifications.", style = outfit(15).copy(lineHeight = 21.sp), color = OC.muted, textAlign = TextAlign.Center)
                }
                Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportStepRow("✓", "Received", "Now", done = true)
                    ReportStepRow("2", "Reviewed by our team", "≤ [48 h]", done = false)
                    ReportStepRow("3", "Decision sent to you", "", done = false)
                }
                if (reason != null) {
                    Row {
                        Text("Reported: ", style = outfit(13), color = OC.muted)
                        Text(reportReasons[reason].first, style = outfit(13, FontWeight.Bold), color = OC.ink)
                    }
                }
            }
        }
        InkButton("Done", Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp).navigationBarsPadding()) { app.pop() }
    }
}

@Composable
private fun ReportStepRow(i: String, t: String, `when`: String, done: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) OC.teal else hex(0xC9C6C0)), contentAlignment = Alignment.Center) {
            Text(i, style = outfit(12, FontWeight.Heavy), color = Color.White)
        }
        Text(t, style = outfit(15, if (done) FontWeight.Bold else FontWeight.Medium), color = if (done) OC.ink else OC.muted, modifier = Modifier.weight(1f))
        Text(`when`, style = outfit(12), color = OC.muted)
    }
}

// MARK: - 22 · Jam room (synced)

private data class JamMember(val id: Int, val initial: String, val name: String, val role: String, val status: String, val bg: Color, val fg: Color, val statusColor: Color)

private const val JAM_ROOM_CODE = "482 913"

@Composable
fun JamView(song: Song) {
    val app = LocalApp.current
    val context = LocalContext.current
    val sections = remember(song) { song.sections.filter { it.kind != SectionKind.Flow } }
    var t by remember { mutableIntStateOf(0) }
    var sec by remember { mutableIntStateOf(minOf(2, maxOf(0, sections.size - 1))) }
    var bar by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val members = remember {
        mutableStateListOf(
            JamMember(0, "Y", "You", "Leader · guitar · chords view", "Leading", OC.gold, OC.inkFixed, OC.gold),
            JamMember(1, "?", "[username]", "Vocals · lyrics only", "Synced", hex(0x1E3A8A), Color.White, ToolsNight.tealText),
            JamMember(2, "?", "[username]", "Bass · root notes view", "Synced", hex(0x0F766E), Color.White, ToolsNight.tealText),
            JamMember(3, "?", "[username]", "Keys · chords view", "Joining…", hex(0x6D28D9), Color.White, OC.nightMuted),
        )
    }
    val current = sections.getOrNull(sec)
    val keyName = ChordTheory.transposeRoot(song.key, t)
    val allSynced = members.none { it.status == "Joining…" }

    fun chords(s: SongSection): List<String> {
        val raw = if (s.bars.isEmpty()) s.lines.flatMap { ChordLine.chords(it) } else s.bars
        var bars = raw.take(8)
        // A repeated 4-bar phrase is shown once (design p27 shows one row of four).
        if (bars.size == 8 && bars.subList(0, 4) == bars.subList(4, 8)) bars = bars.subList(0, 4)
        return bars.map { ChordTheory.transpose(it, t) }
    }

    fun advanceBar() {
        val c = current ?: return
        val n = maxOf(1, chords(c).size)
        when {
            bar + 1 < n -> bar += 1
            sec + 1 < sections.size -> { sec += 1; bar = 0 }
            else -> { bar = 0; playing = false }
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        members[3] = members[3].copy(status = "Synced", statusColor = ToolsNight.tealText)
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val barMs = (4 * 60_000.0 / maxOf(40, song.tempo)).toLong()
        while (true) { delay(barMs); advanceBar() }
    }

    OCPage(night = true) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).plainClickable { app.pop() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("Leave", style = outfit(15, FontWeight.Bold), color = OC.gold)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Jam room", style = outfit(16, FontWeight.Bold), color = OC.nightText)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (allSynced) ToolsNight.tealText else OC.gold))
                        Text("${members.size} in room · ${if (allSynced) "all synced" else "syncing…"}", style = outfit(12), color = if (allSynced) ToolsNight.tealText else OC.gold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.padding(end = 6.dp).size(44.dp).plainClickable { shareText(context, "Join my O Chords jam room — code $JAM_ROOM_CODE") }.semantics { contentDescription = "Invite" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.PersonAddAlt, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                JamRoomCard(members)
                // Now playing
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolsSectionLabel("NOW PLAYING · YOU LEAD", OC.nightMuted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(song.title, style = if (song.isMyanmar) myanmar(24, FontWeight.Bold) else outfit(24, FontWeight.Bold), color = OC.nightText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.nightGlass(20.dp).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            JamKeyButton("−", "Key down for everyone") { t = (t + 11) % 12 }
                            Column(Modifier.widthIn(min = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("KEY", style = outfit(9, FontWeight.Bold, letterSpacing = 1.sp), color = OC.nightMuted)
                                Text(keyName, style = mono(16), color = OC.gold)
                            }
                            JamKeyButton("+", "Key up for everyone") { t = (t + 1) % 12 }
                        }
                    }
                    Text("Key, section and scroll changes reach everyone in the room.", style = outfit(12), color = OC.nightMuted)
                }
                if (current != null) {
                    JamSectionCard(current, chords(current), bar, playing, allSynced, nextLabel = sections[(sec + 1) % sections.size].label.lowercase().replaceFirstChar { it.uppercase() },
                        onPrevious = { sec = maxOf(0, sec - 1); bar = 0 }, onNext = { sec = minOf(sections.size - 1, sec + 1); bar = 0 })
                }
                // People
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolsSectionLabel("IN THE ROOM", OC.nightMuted)
                    members.forEach { m ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).nightGlass(20.dp).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(m.bg), contentAlignment = Alignment.Center) { Text(m.initial, style = outfit(13, FontWeight.Heavy), color = m.fg) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(m.name, style = outfit(14, FontWeight.Bold), color = OC.nightText)
                                Text(m.role, style = outfit(12), color = OC.nightMuted)
                            }
                            Text(m.status, style = outfit(12, FontWeight.Bold), color = m.statusColor)
                        }
                    }
                }
            }
            // Bottom bar
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).drawBehind { drawRect(Color.White.copy(alpha = 0.1f), size = size.copy(height = 1.dp.toPx())) }
                    .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 10.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(58.dp).shadow(10.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f)).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))).plainClickable { playing = !playing }
                        .semantics { contentDescription = if (playing) "Pause synced scroll" else "Start synced scroll" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(24.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (playing) "Scrolling for everyone" else "Start synced scroll", style = outfit(15, FontWeight.Bold), color = OC.nightText)
                    Text("${song.tempo} BPM · count-in 1 bar", style = mono(12, FontWeight.SemiBold), color = OC.nightMuted)
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(OC.gold).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("PRO HOST", style = outfit(11, FontWeight.Heavy, letterSpacing = 1.sp), color = OC.inkFixed)
                }
            }
        }
    }
}

@Composable
private fun JamRoomCard(members: List<JamMember>) {
    Row(Modifier.fillMaxWidth().nightGlass(20.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToolsSectionLabel("ROOM CODE", OC.nightMuted)
            Text(JAM_ROOM_CODE, style = mono(26).copy(letterSpacing = 3.sp), color = OC.gold)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            members.forEach { m ->
                Box(Modifier.size(34.dp).clip(CircleShape).background(m.bg).border(2.dp, ToolsNight.bar, CircleShape), contentAlignment = Alignment.Center) {
                    Text(m.initial, style = outfit(13, FontWeight.Heavy), color = m.fg)
                }
            }
        }
    }
}

@Composable
private fun JamKeyButton(s: String, label: String, onClick: () -> Unit) {
    Box(Modifier.width(40.dp).height(44.dp).plainClickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(s, style = outfit(20), color = OC.nightText)
    }
}

@Composable
private fun JamSectionCard(s: SongSection, cs: List<String>, bar: Int, playing: Boolean, allSynced: Boolean, nextLabel: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().nightGlass(20.dp).border(1.dp, OC.gold, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.label.uppercase()} · ${if (allSynced) "EVERYONE IS HERE" else "SYNCING"}", style = outfit(12, FontWeight.Heavy, letterSpacing = 1.4.sp), color = OC.gold, modifier = Modifier.weight(1f))
            Text("next · $nextLabel", style = outfit(12), color = OC.nightMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cs.withIndex().chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (i, c) ->
                        val live = i == bar
                        val scale by animateFloatAsState(if (live && playing) 1.04f else 1f, label = "chord")
                        Box(Modifier.weight(1f).height(58.dp).scale(scale).clip(RoundedCornerShape(14.dp)).background(if (live) OC.gold else ToolsNight.cell), contentAlignment = Alignment.Center) {
                            Text(c, style = mono(18), color = if (live) OC.inkFixed else OC.nightText, maxLines = 1, softWrap = false)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(44.dp).border(1.dp, ToolsNight.border, RoundedCornerShape(14.dp)).plainClickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
                Text("‹ Previous", style = outfit(14, FontWeight.Bold), color = OC.nightText)
            }
            GoldButton("Next section ›", Modifier.weight(1f), height = 44.dp, onClick = onNext)
        }
    }
}

// MARK: - 6 · Guitar tuner (tab root) — ported after the audio services land.

