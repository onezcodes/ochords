@file:OptIn(ExperimentalLayoutApi::class)

package com.onezcodes.ochords.features.learn

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.features.library.BannerAdView
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.RewardKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.app.tabBarInset
import com.onezcodes.ochords.design.AdBadge
import com.onezcodes.ochords.design.ChordCard
import com.onezcodes.ochords.design.ChordDiagram
import com.onezcodes.ochords.design.GlassList
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.InkButton
import com.onezcodes.ochords.design.NightBackground
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.Segmented
import com.onezcodes.ochords.design.ToggleRow
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.WarmBackground
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.model.ChordVoicing
import com.onezcodes.ochords.model.LearnFret
import com.onezcodes.ochords.model.LearnPosition
import com.onezcodes.ochords.model.LessonQuestion
import com.onezcodes.ochords.model.Profile
import com.onezcodes.ochords.model.Tier
import com.onezcodes.ochords.services.ChordLibrary
import com.onezcodes.ochords.services.MockData
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

// MARK: - Learn group helpers

/** Signed-in profile, or the demo profile for guests. */
private val AppState.learnProfile: Profile get() = displayProfile

/** Wood / fret / string colours of the guitar neck — fixed in both themes. */
private object LearnWood {
    val dark = hex(0x3A2A1A)
    val mid = hex(0x5A4128)
    val end = hex(0x4A3420)
    val nut = hex(0xEDE6D8)
    val fretTop = hex(0xE8E6E1)
    val fretBottom = hex(0x9D9A94)
    val stringLight = hex(0xF2EEE6)
    val stringDark = hex(0xB8B2A6)
}

/** Forces text (not emoji) presentation for the ↔ arrow used in fret titles. */
private val String.learnGlyphs: String get() = replace("↔", "↔︎")

private fun Int.formatted(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)

/** Small "── LABEL" eyebrow used across the learning screens. */
@Composable
private fun LearnEyebrow(text: String, modifier: Modifier = Modifier, color: Color = OC.muted) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(14.dp).height(2.dp).background(OC.gold))
        Text(text, style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = color)
    }
}

/** The guitar-pick glyph used on the current fret. */
private object LearnPickShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = min(size.width, size.height) / 24f
        val path = Path().apply {
            moveTo(12 * s, 21 * s)
            cubicTo(8.8f * s, 18.6f * s, 5 * s, 13.9f * s, 5 * s, 9.7f * s)
            cubicTo(5 * s, 6 * s, 8.1f * s, 3 * s, 12 * s, 3 * s)
            cubicTo(15.9f * s, 3 * s, 19 * s, 6 * s, 19 * s, 9.7f * s)
            cubicTo(19 * s, 13.9f * s, 15.2f * s, 18.6f * s, 12 * s, 21 * s)
            close()
        }
        return Outline.Generic(path)
    }
}

private object LearnShieldShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val sx = size.width / 24f
        val sy = size.height / 26f
        val path = Path().apply {
            moveTo(12 * sx, 1 * sy)
            lineTo(22 * sx, 4.6f * sy)
            lineTo(22 * sx, 11.8f * sy)
            cubicTo(22 * sx, 18 * sy, 17.7f * sx, 22.8f * sy, 12 * sx, 25 * sy)
            cubicTo(6.3f * sx, 22.8f * sy, 2 * sx, 18 * sy, 2 * sx, 11.8f * sy)
            lineTo(2 * sx, 4.6f * sy)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Tier shield with the roman numeral. */
@Composable
private fun LearnTierShield(tier: Tier, width: Dp = 28.dp, modifier: Modifier = Modifier) {
    val color = hex(tier.colorHex)
    Box(modifier.width(width).height(width * 26f / 24f).background(color, LearnShieldShape), contentAlignment = Alignment.Center) {
        Text(
            tier.roman, style = outfit(width.value * 0.34f, FontWeight.Heavy),
            color = if (tier == Tier.ChordKeeper) OC.inkFixed else Color.White,
            modifier = Modifier.offset(y = -width * 0.04f),
        )
    }
}

/** Small stat tile on the always-dark screens. */
@Composable
private fun LearnNightStat(
    label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = OC.nightText,
    mono: Boolean = false, note: String? = null, centered: Boolean = false,
) {
    Column(
        modifier.fillMaxHeight().nightGlass().padding(vertical = 12.dp, horizontal = if (centered) 8.dp else 12.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp, if (centered) Alignment.CenterVertically else Alignment.Top),
    ) {
        Text(label, style = outfit(10, FontWeight.Heavy, letterSpacing = 1.1.sp), color = OC.nightMuted)
        Box(Modifier.heightIn(min = 30.dp), contentAlignment = Alignment.CenterStart) {
            Text(value, style = if (mono) mono(20) else outfit(if (centered) 24 else 22, FontWeight.Bold), color = valueColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        if (note != null) Text(note, style = outfit(11), color = OC.nightMuted)
    }
}

/** Outlined button on dark screens. */
@Composable
private fun LearnNightOutlineButton(text: String, modifier: Modifier = Modifier, height: Dp = 52.dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = if (pressed) 0.08f else 0.001f)).border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick).heightIn(min = height),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(15, FontWeight.Bold), color = OC.nightText) }
}

@Composable
private fun LearnProgressBar(value: Float, modifier: Modifier = Modifier, track: Color = OC.softFill, fill: Color = OC.gold, height: Dp = 6.dp) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().width(maxOf(height, maxWidth * value.coerceIn(0f, 1f))).clip(CircleShape).background(fill))
    }
}

private object LearnTiersMath {
    fun progress(points: Int): Float {
        val t = Tier.forPoints(points)
        val n = t.next ?: return 1f
        return (points - t.minPoints).toFloat() / (n.minPoints - t.minPoints).toFloat()
    }
}

// MARK: - Learn (fretboard path)

private data class LearnFretMeta(val short: String, val sub: String, val icon: Icon) {
    enum class Icon { Check, Pick, Lock, Star, Strum, Trophy }
}

private fun learnMeta(fret: LearnFret): LearnFretMeta = when (fret.id) {
    "f1" -> LearnFretMeta("Anchor fingers", "Mastered · replay for +5 beats", LearnFretMeta.Icon.Check)
    "f2" -> LearnFretMeta("C ↔ Am", "Mastered · replay for +5 beats", LearnFretMeta.Icon.Check)
    "f3" -> LearnFretMeta("C ↔ G/B", "5 takes · about 4 min", if (fret.state == LearnFret.State.Done) LearnFretMeta.Icon.Check else LearnFretMeta.Icon.Pick)
    "f4" -> LearnFretMeta("Am ↔ F", "Clear the frets above to unlock", LearnFretMeta.Icon.Lock)
    "f5" -> LearnFretMeta("Encore", "Bonus beats and a rest note", LearnFretMeta.Icon.Star)
    "f6" -> LearnFretMeta("Folk strum", "Practice with the strum trainer", LearnFretMeta.Icon.Strum)
    "f7" -> LearnFretMeta("The round", "C G Am Em F Dm C G", LearnFretMeta.Icon.Lock)
    "f8" -> LearnFretMeta("Soundcheck", "Pass it to master this position", LearnFretMeta.Icon.Trophy)
    else -> fallbackMeta(fret)
}

private fun fallbackMeta(fret: LearnFret): LearnFretMeta {
    val short = fret.title.split(" · ").firstOrNull() ?: fret.title
    val icon = when (fret.state) {
        LearnFret.State.Done -> LearnFretMeta.Icon.Check
        LearnFret.State.Current -> LearnFretMeta.Icon.Pick
        LearnFret.State.Locked -> when (fret.kind) {
            LearnFret.Kind.Encore -> LearnFretMeta.Icon.Star
            LearnFret.Kind.Soundcheck -> LearnFretMeta.Icon.Trophy
            else -> LearnFretMeta.Icon.Lock
        }
    }
    val sub = when (fret.state) {
        LearnFret.State.Done -> "Mastered · replay for +5 beats"
        LearnFret.State.Current -> "5 takes · about 4 min"
        LearnFret.State.Locked -> "Clear the frets above to unlock"
    }
    return LearnFretMeta(short, sub, icon)
}

@Composable
fun LearnView() {
    val app = LocalApp.current
    var positions by remember { mutableStateOf(MockData.learnPath) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val position = positions.firstOrNull { p -> p.frets.isNotEmpty() && p.frets.any { it.state != LearnFret.State.Done } } ?: MockData.learnPath[1]
    val frets = position.frets
    val selIndex = run {
        val i = selected ?: frets.indexOfFirst { it.state == LearnFret.State.Current }.takeIf { it >= 0 } ?: 0
        i.coerceIn(0, maxOf(0, frets.size - 1))
    }
    val previous = positions.lastOrNull { it.number == position.number - 1 }
    val nextPosition = positions.firstOrNull { it.number == position.number + 1 }

    LaunchedEffect(Unit) { runCatching { app.backend.path() }.getOrNull()?.let { positions = it } }

    // Custom scaffold so the glass header can run under the status bar, as on iOS.
    Box(Modifier.fillMaxSize()) {
        WarmBackground()
        Column(Modifier.fillMaxSize()) {
            LearnHeader()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 150.dp + tabBarInset),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LearnHeroCard(position, previous) { app.push(Route.Chords(focus = null, songChords = listOf("C", "G/B", "Am", "F"))) }
                LearnGameCard { app.push(Route.Game) }
                LearnNeck(frets, selIndex, Modifier.padding(vertical = 4.dp)) { selected = it }
                if (nextPosition != null) LearnLockedPosition(nextPosition)
            }
        }
        if (selIndex in frets.indices) {
            LearnFretDetail(frets[selIndex], selIndex + 1, Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp).padding(bottom = tabBarInset)) {
                app.push(Route.Lesson(frets[selIndex].id))
            }
        }
    }
}

@Composable
private fun LearnHeroCard(position: LearnPosition, previous: LearnPosition?, onGuidebook: () -> Unit) {
    Row(Modifier.fillMaxWidth().inkGlass().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("POSITION ${position.number} · ${position.subtitle.uppercase()}", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.gold)
            Text(position.title, style = outfit(19, FontWeight.Bold).copy(lineHeight = 25.sp), color = OC.paper)
            if (previous != null) {
                Text("Position ${previous.number} “${previous.title}” · ${previous.subtitle.lowercase()}", style = outfit(12), color = hex(0xBDB9B3))
            }
        }
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .plainClickable(onClick = onGuidebook).semantics { contentDescription = "Position guidebook" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Book, contentDescription = null, tint = OC.gold, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun LearnGameCard(onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass().plainClickable(onClick = onPlay).semantics { contentDescription = "Chord Rush, daily game, 30 seconds. Best 180. Play" }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).shadow(9.dp, RoundedCornerShape(16.dp), ambientColor = OC.inkFixed.copy(alpha = 0.2f), spotColor = OC.inkFixed.copy(alpha = 0.2f)).background(OC.inkFixed, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = OC.gold, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("DAILY GAME · 30 SEC", style = outfit(11, FontWeight.Bold, letterSpacing = 1.4.sp), color = OC.goldText)
            Text("Chord Rush", style = outfit(16, FontWeight.Bold), color = OC.ink)
            Text("Name today’s 8 shapes · best 180", style = outfit(12), color = OC.muted)
        }
        Box(
            Modifier.height(34.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Play", style = outfit(13, FontWeight.Heavy), color = OC.inkFixed) }
    }
}

@Composable
private fun LearnLockedPosition(p: LearnPosition) {
    val dash = OC.ink.copy(alpha = 0.2f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(OC.glass)
            .drawBehind {
                drawRoundRect(dash, topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()), size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
        Text("Position ${p.number} · ${p.title}", style = outfit(14, FontWeight.SemiBold), color = OC.muted)
    }
}

/** Glass header: title, groove / beats / strings pills, and today's set bar. */
@Composable
private fun LearnHeader() {
    val app = LocalApp.current
    val p = app.learnProfile
    val border = OC.glassBorder
    Column(
        Modifier.fillMaxWidth().background(OC.surface.copy(alpha = 0.5f)).background(OC.glass.copy(alpha = OC.glass.alpha * 0.7f))
            .drawBehind { drawRect(border, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .statusBarsPadding().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LEARN · GUITAR", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.muted)
                Text("Fretboard", style = outfit(22, FontWeight.SemiBold, letterSpacing = (-0.8).sp), color = OC.ink)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LearnHeaderPill(label = "Groove: ${p.grooveDays} days", onClick = { app.push(Route.Streak) }) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = hex(0xC28C00), modifier = Modifier.size(16.dp))
                    Text("${p.grooveDays}", style = outfit(15, FontWeight.Heavy), color = OC.ink, softWrap = false)
                }
                LearnHeaderPill(label = "240 beats. Your tier", onClick = { app.push(Route.Tiers) }) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OC.blue, modifier = Modifier.size(16.dp))
                    Text("240", style = outfit(15, FontWeight.Heavy), color = OC.ink, softWrap = false)
                }
                LearnHeaderPill(label = "${app.lives} strings left") {
                    Icon(Icons.Rounded.Menu, contentDescription = null, tint = OC.red, modifier = Modifier.size(16.dp).rotate(90f))
                    Text("${app.lives}", style = outfit(15, FontWeight.Heavy), color = OC.ink, softWrap = false)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp).plainClickable { app.push(Route.Streak) }
                .semantics { contentDescription = "Today’s set: ${p.todayMinutes} of ${p.dailyGoalMinutes} minutes" },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TODAY’S SET", style = outfit(11, FontWeight.Bold, letterSpacing = 1.2.sp), color = OC.muted, softWrap = false)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val empty = OC.ink.copy(alpha = 0.1f)
                repeat(10) { i ->
                    val filled = i.toDouble() < p.todayMinutes.toDouble() / maxOf(1, p.dailyGoalMinutes).toDouble() * 10
                    Box(Modifier.weight(1f).height(10.dp).background(if (filled) OC.gold else empty, RoundedCornerShape(3.dp)))
                }
            }
            Text("${p.todayMinutes}/${p.dailyGoalMinutes} min", style = mono(12), color = OC.ink, softWrap = false)
        }
    }
}

@Composable
private fun LearnHeaderPill(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.defaultMinSize(minWidth = 44.dp, minHeight = 38.dp).clip(CircleShape).background(OC.glass).border(1.dp, OC.glassBorder, CircleShape)
            .then(if (onClick != null) Modifier.plainClickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label }.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** The vertical guitar neck with one node per fret; labels alternate right / left. */
@Composable
private fun LearnNeck(frets: List<LearnFret>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val fretGap = 92.dp
    val width = 350.dp
    val stringWidths = listOf(2.6f, 2.2f, 1.8f, 1.5f, 1.2f, 1f)
    val neckH = fretGap * frets.size + 20.dp
    val neckShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(width).height(neckH)) {
            Box(
                Modifier.offset(x = 115.dp).width(120.dp).height(neckH)
                    .shadow(20.dp, neckShape, ambientColor = hex(0x3C2814, 0.28f), spotColor = hex(0x3C2814, 0.28f))
                    .background(Brush.horizontalGradient(0f to LearnWood.dark, 0.45f to LearnWood.mid, 1f to LearnWood.end), neckShape)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), neckShape),
            )
            // Nut, frets and strings
            Canvas(Modifier.fillMaxSize()) {
                val nutLeft = 111.dp.toPx()
                val nutSize = Size(128.dp.toPx(), 10.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(nutLeft, 2.dp.toPx()), size = nutSize, cornerRadius = CornerRadius(6.dp.toPx()))
                drawRoundRect(LearnWood.nut, topLeft = Offset(nutLeft, 0f), size = nutSize, cornerRadius = CornerRadius(6.dp.toPx()))
                for (i in frets.indices) {
                    val y = (10.dp + fretGap * (i + 1)).toPx()
                    drawRect(
                        Brush.verticalGradient(listOf(LearnWood.fretTop, LearnWood.fretBottom), startY = y, endY = y + 3.dp.toPx()),
                        topLeft = Offset(115.dp.toPx(), y), size = Size(120.dp.toPx(), 3.dp.toPx()),
                    )
                }
                for (s in 0 until 6) {
                    val x = (125.dp + 20.dp * s).toPx()
                    val w = stringWidths[s].dp.toPx()
                    drawRect(
                        Brush.horizontalGradient(listOf(LearnWood.stringLight, LearnWood.stringDark), startX = x, endX = x + w),
                        topLeft = Offset(x, 0f), size = Size(w, size.height), alpha = 0.85f,
                    )
                }
            }
            frets.forEachIndexed { i, fret ->
                val top = 10.dp + fretGap * i + (fretGap - 64.dp) / 2
                val right = i % 2 == 0
                val nodeW = if (fret.kind == LearnFret.Kind.Encore) 110.dp else 56.dp
                LearnFretNode(fret, i + 1, i == selected, Modifier.offset(x = 175.dp - nodeW / 2, y = top + 4.dp)) { onSelect(i) }
                LearnNeckLabel(fret, i + 1, right, Modifier.offset(x = if (right) 246.dp else 0.dp, y = top + 10.dp))
            }
        }
    }
}

@Composable
private fun LearnNeckLabel(fret: LearnFret, number: Int, right: Boolean, modifier: Modifier = Modifier) {
    val meta = learnMeta(fret)
    Column(modifier.width(104.dp).height(40.dp), horizontalAlignment = if (right) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("FRET $number", style = outfit(10, FontWeight.Heavy, letterSpacing = 1.sp), color = if (fret.state == LearnFret.State.Current) OC.goldText else OC.faint)
        Text(
            meta.short.learnGlyphs, style = outfit(13, FontWeight.Bold).copy(lineHeight = 16.sp),
            color = if (fret.state == LearnFret.State.Locked) OC.faint else OC.ink,
            textAlign = if (right) TextAlign.Start else TextAlign.End,
        )
    }
}

@Composable
private fun LearnFretNode(fret: LearnFret, number: Int, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val double = fret.kind == LearnFret.Kind.Encore
    val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "fretNode")
    val stateText = when (fret.state) {
        LearnFret.State.Done -> "mastered"
        LearnFret.State.Current -> "current lesson"
        LearnFret.State.Locked -> "locked"
    }
    Row(
        modifier.width(if (double) 110.dp else 56.dp).height(56.dp).scale(scale).plainClickable(onClick = onClick)
            .semantics { contentDescription = "Fret $number, ${fret.title}, $stateText"; if (isSelected) selected = true },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(if (double) 2 else 1) { LearnFretDot(fret) }
    }
}

@Composable
private fun LearnFretDot(fret: LearnFret) {
    val dark = isDark
    val current = fret.state == LearnFret.State.Current
    val (bg, fg, ring) = when (fret.state) {
        LearnFret.State.Done -> Triple(OC.gold, OC.inkFixed, hex(0xFFE3A3))
        LearnFret.State.Current -> Triple(OC.inkFixed, OC.gold, OC.gold)
        LearnFret.State.Locked -> Triple(
            if (dark) hex(0x2A2825) else Color.White.copy(alpha = 0.85f), OC.faint,
            if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.9f),
        )
    }
    val shadowColor = if (current) hex(0xFFAA00, 0.45f) else Color.Black.copy(alpha = 0.18f)
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        if (current) Box(Modifier.requiredSize(62.dp).background(OC.gold.copy(alpha = 0.28f), CircleShape))
        Box(Modifier.size(50.dp).shadow(if (current) 12.dp else 7.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor).background(bg, CircleShape).border(2.dp, ring, CircleShape))
        when (learnMeta(fret).icon) {
            LearnFretMeta.Icon.Check -> Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            LearnFretMeta.Icon.Pick -> Box(Modifier.size(22.dp).background(fg, LearnPickShape))
            LearnFretMeta.Icon.Lock -> Icon(Icons.Outlined.Lock, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            LearnFretMeta.Icon.Star -> Icon(Icons.Rounded.Star, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            LearnFretMeta.Icon.Strum -> Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            LearnFretMeta.Icon.Trophy -> Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

/** Floating card for the selected fret, with its CTA. */
@Composable
private fun LearnFretDetail(fret: LearnFret, number: Int, modifier: Modifier = Modifier, play: () -> Unit) {
    val meta = learnMeta(fret)
    val (badgeBg, badgeFg) = when (fret.state) {
        LearnFret.State.Locked -> OC.softFill to OC.faint
        LearnFret.State.Current -> OC.inkFixed to OC.gold
        LearnFret.State.Done -> OC.gold to OC.inkFixed
    }
    Column(modifier.fillMaxWidth().glass(24.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(46.dp).background(badgeBg, RoundedCornerShape(14.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)) {
                Text("FRET", style = outfit(9, FontWeight.Heavy, letterSpacing = 1.sp), color = badgeFg)
                Text("$number", style = mono(18), color = badgeFg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fret.title.learnGlyphs, style = outfit(16, FontWeight.Bold), color = OC.ink)
                Text(meta.sub, style = outfit(12), color = OC.muted)
            }
        }
        if (fret.state != LearnFret.State.Locked) {
            GoldButton(
                if (fret.state == LearnFret.State.Current) "Play lesson · +10 beats" else "Replay · +5 beats", height = 48.dp,
                leading = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp)) },
                onClick = play,
            )
        }
    }
}

// MARK: - Lesson

/** Stages the demo takes to match the design flow: name a shape, pick the bass string, arrange the bars. */
private fun learnStaged(qs: List<LessonQuestion>): List<LessonQuestion> = qs.map { q ->
    when (q.id) {
        "q1" -> q.copy(prompt = "Which chord is this shape?", detail = "G/B", options = listOf("G", "G/B", "Em", "C"), answer = "G/B")
        "q2" -> q.copy(prompt = "In G/B, which string plays the bass note B?", detail = "", options = listOf("Low E string", "A string, fret 2", "D string, open", "B string, open"), answer = "A string, fret 2")
        "q3" -> q.copy(prompt = "Lay out the intro of စဉ်းစားပါ, bar by bar", options = listOf("Am", "C", "F", "G/B", "Em"), answer = "C G/B Am F")
        else -> q
    }
}

private fun lessonExplanation(q: LessonQuestion): String = when (q.id) {
    "q1" -> "A G shape without the low E: the A string at fret 2 (B) becomes the lowest note — so it’s G/B."
    "q2" -> "The lowest string you play sets the bass. Here that’s the A string at fret 2 — the note B."
    "q3" -> "C → G/B → Am → F. The bass walks down C, B, A, then lands on F."
    "q4" -> "The bass note B rang clean on the A string, fret 2 — that’s the walk-down."
    "q5" -> "F closes the loop: C → G/B → Am → F, then back to C for the next line."
    else -> ChordLibrary.info(q.answer)?.voicings?.firstOrNull()?.tip ?: q.detail
}

@Composable
fun LessonView(fretID: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var questions by remember { mutableStateOf(learnStaged(MockData.lessonQuestions)) }
    var index by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var built by remember { mutableStateOf<List<Int>>(emptyList()) }
    var checked by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var listening by remember { mutableStateOf(false) }

    val q = questions[minOf(index, questions.size - 1)]
    val isLast = index >= questions.size - 1
    val answerParts = q.answer.split(" ").filter { it.isNotEmpty() }
    val ready = when (q.kind) {
        LessonQuestion.Kind.PickChord -> picked != null
        LessonQuestion.Kind.BuildBar -> built.size == answerParts.size
        LessonQuestion.Kind.Listen -> !listening
    }
    /** A pick-the-chord take whose detail is itself a chord: show its shape. */
    val shapeVoicing = if (q.kind == LessonQuestion.Kind.PickChord) ChordLibrary.info(q.detail)?.voicings?.firstOrNull() else null
    /** Chord-name options go in a 2×2 grid; longer answers in a list. */
    val isChordGrid = q.options.all { ChordLibrary.info(it) != null }
    val kindLabel = when (q.kind) {
        LessonQuestion.Kind.PickChord -> if (isChordGrid) "NAME THE CHORD" else if (q.id == "q2") "LISTEN FOR THE BASS" else "PICK ONE"
        LessonQuestion.Kind.BuildBar -> "ARRANGE THE BARS"
        LessonQuestion.Kind.Listen -> "PLAY IT"
    }

    fun grade(ok: Boolean) {
        checked = true
        right = ok
        run = if (ok) run + 1 else 0
        results = results + ok
        if (!ok) app.lives = maxOf(0, app.lives - 1)
        if (!ok && app.lives == 0) scope.launch { delay(900); app.modal = Modal.Reward(RewardKind.Strings) }
    }

    fun check() {
        if (!ready || checked) return
        val ok = when (q.kind) {
            LessonQuestion.Kind.PickChord -> picked == q.answer
            LessonQuestion.Kind.BuildBar -> built.map { q.options[it] } == answerParts
            LessonQuestion.Kind.Listen -> true
        }
        grade(ok)
    }

    // TODO: replace with the microphone chord check (`ChordCheck`, being ported separately) — for now the player confirms they played it.
    fun listen() {
        if (listening || checked) return
        listening = true
        scope.launch {
            delay(1800)
            listening = false
            grade(true)
        }
    }

    fun next() {
        if (app.lives == 0) { app.modal = Modal.Reward(RewardKind.Strings); return }
        index += 1
        picked = null
        built = emptyList()
        checked = false
        right = false
    }

    LaunchedEffect(fretID) {
        runCatching { app.backend.lesson(fretID) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { questions = learnStaged(it) }
        if (app.lives == 0) app.modal = Modal.Reward(RewardKind.Strings)
    }
    LaunchedEffect(results.size) {
        results.lastOrNull()?.let { haptic.performHapticFeedback(if (it) HapticFeedbackType.Confirm else HapticFeedbackType.Reject) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(q.id) { scroll.scrollTo(0) }

    OCPage {
        Column(Modifier.fillMaxSize()) {
            LessonTopBar(questions.size, index, results, app.lives) { app.pop() }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LearnEyebrow("TAKE ${index + 1} OF ${questions.size} · $kindLabel")
                    Spacer(Modifier.weight(1f))
                    if (run >= 2) {
                        Text("LOCKED IN ×$run", style = outfit(11, FontWeight.Heavy, letterSpacing = 1.sp), color = OC.inkFixed,
                            modifier = Modifier.background(OC.gold, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(q.prompt, style = outfit(25, FontWeight.SemiBold, letterSpacing = (-0.6).sp).copy(lineHeight = 33.sp), color = OC.ink)

                when (q.kind) {
                    LessonQuestion.Kind.PickChord -> {
                        if (shapeVoicing != null) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.glass().padding(horizontal = 32.dp, vertical = 20.dp)) { ChordDiagram(shapeVoicing, width = 116.dp, showStringNames = false) }
                            }
                            LessonOptionGrid(q.options, picked, checked, q.answer) { picked = it }
                        } else if (isChordGrid) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.height(44.dp).glass(14.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(q.detail, style = mono(17), color = OC.ink) }
                            }
                            LessonOptionGrid(q.options, picked, checked, q.answer) { picked = it }
                        } else {
                            if (q.detail.isNotEmpty()) Text(q.detail, style = outfit(15), color = OC.muted)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                q.options.forEach { o -> LessonOption(o, grid = false, on = picked == o, checked = checked, isAnswer = o == q.answer) { if (!checked) picked = o } }
                            }
                        }
                    }
                    LessonQuestion.Kind.BuildBar -> LessonBuildArea(q, answerParts, built, checked) { built = it }
                    LessonQuestion.Kind.Listen -> LessonListenArea(q.detail, listening, checked)
                }

                AnimatedVisibility(visible = checked, enter = slideInVertically { it / 2 } + fadeIn()) {
                    val answerText = if (q.kind == LessonQuestion.Kind.BuildBar) answerParts.joinToString(" → ") else q.answer
                    LessonFeedback(right, answerText, lessonExplanation(q))
                }
            }
            LessonBottomBar(
                checked = checked, isListen = q.kind == LessonQuestion.Kind.Listen, listening = listening, ready = ready, isLast = isLast,
                onListen = { listen() }, onCheck = { check() }, onNext = { next() }, onFinish = { app.push(Route.Complete) },
            )
        }
    }
}

@Composable
private fun LessonTopBar(count: Int, index: Int, results: List<Boolean>, lives: Int, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Leave lesson" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = OC.muted, modifier = Modifier.size(22.dp))
        }
        val empty = OC.ink.copy(alpha = 0.1f)
        val halo = OC.gold.copy(alpha = 0.25f)
        Row(Modifier.weight(1f).semantics { contentDescription = "Take ${index + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (k in 0 until count) {
                val r = results.getOrNull(k)
                val cur = k == index
                Box(
                    Modifier.weight(1f).height(10.dp)
                        .drawBehind {
                            if (cur) drawRoundRect(halo, topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()), size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), cornerRadius = CornerRadius(8.dp.toPx()))
                        }
                        .background(if (r == true) OC.teal else if (r == false) OC.red else if (cur) OC.gold else empty, CircleShape),
                )
            }
        }
        LearnLivesPill(lives)
    }
}

@Composable
private fun LessonOptionGrid(options: List<String>, picked: String?, checked: Boolean, answer: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { o -> LessonOption(o, grid = true, on = picked == o, checked = checked, isAnswer = o == answer, modifier = Modifier.weight(1f)) { if (!checked) onPick(o) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LessonOption(text: String, grid: Boolean, on: Boolean, checked: Boolean, isAnswer: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var border = if (on) OC.gold else OC.glassBorder
    var bg = if (on) OC.goldTint else OC.glass
    var fg = OC.ink
    if (checked && isAnswer) { border = OC.teal; bg = OC.tealTint; fg = OC.tealText }
    else if (checked && on) { border = OC.red; bg = OC.redTint; fg = OC.red }
    val shape = RoundedCornerShape(18.dp)
    val glow = OC.gold.copy(alpha = 0.28f)
    val surface = OC.surface.copy(alpha = 0.5f)
    Row(
        modifier.fillMaxWidth().heightIn(min = if (grid) 80.dp else 56.dp)
            .drawBehind {
                if (on) drawRoundRect(glow, topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()), size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .background(surface, shape).background(bg, shape).border(2.dp, border, shape)
            .plainClickable(onClick = onClick).semantics { if (on) selected = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = if (grid) Arrangement.Center else Arrangement.Start, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = outfit(if (grid) 20 else 16, FontWeight.Bold), color = fg)
    }
}

@Composable
private fun LessonBuildArea(q: LessonQuestion, answerParts: List<String>, built: List<Int>, checked: Boolean, onBuilt: (List<Int>) -> Unit) {
    val dash = OC.ink.copy(alpha = 0.14f)
    val slotBorderEmpty = OC.ink.copy(alpha = 0.12f)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).background(OC.softFill, RoundedCornerShape(20.dp))
                .drawBehind {
                    drawRoundRect(dash, topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            answerParts.indices.forEach { pos ->
                val has = pos < built.size
                val name = if (has) q.options[built[pos]] else "·"
                Column(
                    Modifier.size(64.dp, 48.dp)
                        .shadow(if (has) 6.dp else 0.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
                        .background(if (has) OC.surface.copy(alpha = 0.85f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .border(1.dp, if (has) OC.glassBorder else slotBorderEmpty, RoundedCornerShape(14.dp))
                        .plainClickable(enabled = has && !checked) { onBuilt(built.toMutableList().apply { removeAt(pos) }) }
                        .semantics { contentDescription = if (has) "Remove $name from bar ${pos + 1}" else "Empty bar ${pos + 1}" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Text("BAR ${pos + 1}", style = outfit(9, FontWeight.Heavy), color = OC.faint)
                    Text(name, style = mono(17), color = OC.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            q.options.forEachIndexed { bi, name ->
                val used = bi in built
                Box(
                    Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 50.dp)
                        .shadow(if (used) 0.dp else 9.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                        .background(if (used) OC.softFill else OC.glass, RoundedCornerShape(14.dp))
                        .border(1.dp, if (used) Color.Transparent else OC.glassBorder, RoundedCornerShape(14.dp))
                        .plainClickable(enabled = !used && !checked && built.size < answerParts.size) { onBuilt(built + bi) }
                        .semantics { contentDescription = if (used) "$name, placed" else "Add $name" }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(name, style = mono(17), color = if (used) Color.Transparent else OC.ink) }
            }
        }
    }
}

@Composable
private fun LessonListenArea(detail: String, listening: Boolean, checked: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(detail, style = outfit(15), color = OC.muted, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            ChordCard("C", width = 110.dp)
            ChordCard("G/B", width = 110.dp)
        }
        Row(Modifier.fillMaxWidth().glass(16.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (listening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, contentDescription = null, tint = if (listening) OC.goldText else OC.muted, modifier = Modifier.size(20.dp))
            Text(
                if (listening) "Listening for the bass note…" else if (checked) "Heard it — B on the A string." else "Strum it, then tap “I played it”.",
                style = outfit(14, FontWeight.SemiBold), color = OC.ink, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LessonFeedback(right: Boolean, answerText: String, explanation: String) {
    val fg = if (right) OC.teal else OC.red
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().background(if (right) OC.tealTint else OC.redTint, shape).border(1.dp, fg.copy(alpha = 0.3f), shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LearnTuneGauge(right, fg, Modifier.size(56.dp, 30.dp))
            Text(if (right) "IN TUNE" else "OUT OF TUNE", style = outfit(10, FontWeight.Heavy, letterSpacing = 1.sp), color = fg, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (right) "Clean take!" else "A string snapped — answer: $answerText", style = outfit(16, FontWeight.Bold), color = fg)
            Text(explanation, style = outfit(14).copy(lineHeight = 20.sp), color = OC.ink)
        }
    }
}

@Composable
private fun LessonBottomBar(
    checked: Boolean, isListen: Boolean, listening: Boolean, ready: Boolean, isLast: Boolean,
    onListen: () -> Unit, onCheck: () -> Unit, onNext: () -> Unit, onFinish: () -> Unit,
) {
    val border = OC.glassBorder
    Box(
        Modifier.fillMaxWidth().background(OC.surface.copy(alpha = 0.5f)).background(OC.glass.copy(alpha = OC.glass.alpha * 0.8f))
            .drawBehind { drawRect(border, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 8.dp).navigationBarsPadding(),
    ) {
        when {
            !checked && isListen -> GoldButton(
                if (listening) "Listening…" else "I played it", enabled = !listening,
                leading = {
                    if (listening) CircularProgressIndicator(Modifier.size(18.dp), color = OC.inkFixed, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Mic, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(18.dp))
                },
                onClick = onListen,
            )
            !checked && ready -> GoldButton("Check this take", onClick = onCheck)
            !checked -> Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).background(OC.softFill, RoundedCornerShape(16.dp)).semantics { contentDescription = "Check this take. Choose an answer first" },
                contentAlignment = Alignment.Center,
            ) { Text("Check this take", style = outfit(16, FontWeight.Bold), color = OC.faint) }
            !isLast -> InkButton("Next take →", onClick = onNext)
            else -> InkButton("Finish the session →", onClick = onFinish)
        }
    }
}

/** Six tiny strings; snapped ones turn red with a break in the middle. */
@Composable
private fun LearnLivesPill(lives: Int) {
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(OC.glass).border(1.dp, OC.glassBorder, CircleShape)
            .semantics { contentDescription = "$lives strings left" }.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(6) { k ->
                val ok = k < lives
                val color = if (ok) OC.ink else OC.red
                Column(Modifier.width(2.dp).height(18.dp), verticalArrangement = Arrangement.spacedBy(if (ok) 0.dp else 5.dp)) {
                    Box(Modifier.width(2.dp).weight(1f).background(color, RoundedCornerShape(1.dp)))
                    Box(Modifier.width(2.dp).weight(1f).background(color, RoundedCornerShape(1.dp)))
                }
            }
        }
        Text("$lives", style = outfit(14, FontWeight.Heavy), color = OC.ink)
    }
}

/** Mini tuner gauge: needle straight up when in tune, leaning flat when not. */
@Composable
private fun LearnTuneGauge(inTune: Boolean, color: Color, modifier: Modifier = Modifier) {
    val track = OC.ink.copy(alpha = 0.15f)
    Canvas(modifier) {
        val s = size.width / 56f
        drawArc(track, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(4 * s, 4 * s), size = Size(48 * s, 48 * s), style = Stroke(4 * s, cap = StrokeCap.Round))
        drawLine(color, Offset(28 * s, 28 * s), Offset((if (inTune) 28f else 12f) * s, 8 * s), strokeWidth = 3 * s, cap = StrokeCap.Round)
        drawCircle(color, radius = 3 * s, center = Offset(28 * s, 28 * s))
    }
}

// MARK: - Complete (session recorded)

@Composable
fun CompleteView() {
    val app = LocalApp.current
    val p = app.learnProfile
    val tier = Tier.forPoints(p.points)

    LaunchedEffect(Unit) {
        app.profile = app.profile?.let { it.copy(points = it.points + 15, lessonsDone = it.lessonsDone + 1) }
    }

    Box(Modifier.fillMaxSize()) {
        NightBackground(glowCenter = Offset(0.5f, 0.18f))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LearnRecordDisc(fret = 3, label = "C↔G/B".learnGlyphs, modifier = Modifier.padding(top = 26.dp).size(210.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp).height(2.dp).background(OC.gold))
                    Text("SESSION RECORDED", style = outfit(11, FontWeight.Bold, letterSpacing = 1.8.sp), color = OC.nightMuted)
                }
                Text(
                    "You can walk the bass from C to Am.", style = outfit(32, FontWeight.SemiBold, letterSpacing = (-1).sp), color = OC.nightText,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )

                Row(Modifier.fillMaxWidth().padding(top = 22.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearnNightStat("BEATS", "+15", Modifier.weight(1f), valueColor = OC.gold, centered = true)
                    LearnNightStat("IN TUNE", "4/5", Modifier.weight(1f), valueColor = hex(0x5EEAD4), centered = true)
                    LearnNightStat("TAKE TIME", "2:40", Modifier.weight(1f), mono = true, centered = true)
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).nightGlass().plainClickable { app.push(Route.Streak) }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        listOf(12, 22, 16, 28, 20).forEach { h -> Box(Modifier.width(5.dp).height(h.dp).background(OC.gold, RoundedCornerShape(2.dp))) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Today’s set done · groove on", style = outfit(15, FontWeight.Bold), color = OC.nightText)
                        Text("${p.grooveDays + 1}-day groove · ${p.todayMinutes + 6} / ${p.dailyGoalMinutes} min today", style = outfit(12), color = OC.nightMuted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = OC.nightMuted, modifier = Modifier.size(18.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).nightGlass().plainClickable { app.push(Route.Tiers) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    LearnTierShield(tier, width = 28.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("+15 O Points", style = outfit(13, FontWeight.Bold), color = OC.nightText)
                            Spacer(Modifier.weight(1f))
                            tier.next?.let { next -> Text("${(next.minPoints - p.points).formatted()} to ${next.title}", style = outfit(13, FontWeight.Bold), color = OC.nightMuted) }
                        }
                        LearnProgressBar(LearnTiersMath.progress(p.points), track = Color.White.copy(alpha = 0.12f), height = 6.dp)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton("Keep the groove going") {
                    app.popToRoot()
                    app.showInterstitial()
                }
                LearnNightOutlineButton("Play it back", height = 48.dp) {
                    app.popToRoot()
                    app.push(Route.Lesson(fretID = "f3"))
                }
            }
        }
    }
}

/** The "record" disc on Session recorded: grooves around a gold label. */
@Composable
private fun LearnRecordDisc(fret: Int, label: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(hex(0x141312), radius = 100.dp.toPx(), center = c)
            listOf(184, 164, 144, 124).forEach { d ->
                drawCircle(Color.White.copy(alpha = 0.065f), radius = d.dp.toPx() / 2 - 0.5.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            }
            val r = 80.dp.toPx()
            drawArc(
                Color.White.copy(alpha = 0.18f), startAngle = 0.58f * 360f, sweepAngle = 0.12f * 360f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(OC.gold, radius = 42.dp.toPx(), center = c)
            drawCircle(OC.night, radius = 5.dp.toPx(), center = c)
        }
        Text("FRET $fret", style = outfit(9, FontWeight.Heavy, letterSpacing = 1.5.sp), color = OC.inkFixed, modifier = Modifier.offset(y = (-14).dp))
        Text(label, style = mono(12), color = OC.inkFixed, modifier = Modifier.offset(y = 18.dp))
    }
}

// MARK: - Streak (groove)

// September 2026 practice log (minutes per day). Today = 25th, current groove from the 18th.
private val streakMinutes: Map<Int, Int> = mapOf(2 to 6, 3 to 12, 4 to 8, 8 to 10, 9 to 5, 11 to 14, 14 to 7, 15 to 9, 18 to 10, 19 to 6, 20 to 15, 21 to 11, 22 to 8, 23 to 13, 24 to 10, 25 to 12)
private const val streakToday = 25
private const val streakGrooveFrom = 18
private val streakGoals: List<Pair<String, Int>> = listOf("Quick" to 5, "Standard" to 10, "Serious" to 20, "Pro" to 30)

@Composable
fun StreakView() {
    val app = LocalApp.current
    var goal by remember { mutableIntStateOf(app.learnProfile.dailyGoalMinutes) }
    var remind by remember { mutableStateOf(true) }
    val groove = streakToday - streakGrooveFrom + 1
    val restNotes = app.learnProfile.restNotes

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Your groove", backLabel = "Back to Learn") {
                Box(Modifier.size(44.dp).plainClickable { app.push(Route.SaveImage(chord = "C")) }.semantics { contentDescription = "Share your groove" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null, tint = OC.ink, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StreakHeroCard(groove)
                StreakCalendarCard()
                Row(Modifier.fillMaxWidth().glass().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(OC.inkFixed, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        LearnRestGlyph(OC.gold, Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$restNotes rest note${if (restNotes == 1) "" else "s"} saved", style = outfit(15, FontWeight.Bold), color = OC.ink)
                        Text("Like a rest in music — skip one day and your groove keeps going.", style = outfit(13), color = OC.muted)
                    }
                }

                StreakGoalPicker(goal) { g ->
                    goal = g
                    app.profile = app.profile?.copy(dailyGoalMinutes = g)
                }

                Box(Modifier.fillMaxWidth().glass().heightIn(min = 60.dp), contentAlignment = Alignment.CenterStart) {
                    ToggleRow("Practice reminder", remind, subtitle = "Every day at 8:00 PM") { remind = it }
                }

                GoldButton("Back to the fretboard") { app.popToRoot() }
            }
        }
    }
}

@Composable
private fun StreakHeroCard(groove: Int) {
    val week = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(Modifier.fillMaxWidth().inkGlass(24.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("IN THE GROOVE", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.gold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("$groove", style = outfit(64, FontWeight.Bold, letterSpacing = (-2).sp), color = OC.paper)
                    Text("days", style = outfit(18, FontWeight.SemiBold), color = OC.paper, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Play tomorrow\nto make it ${groove + 1}", style = outfit(12).copy(lineHeight = 17.sp), color = hex(0xBDB9B3), textAlign = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until 7) {
                val d = 21 + i
                val m = if (d <= streakToday) streakMinutes[d] ?: 0 else 0
                val isToday = d == streakToday
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${if (d > streakToday) "Upcoming" else "$m minutes"} on ${week[i]}" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.fillMaxWidth().height(if (m > 0) (m * 4.2f).dp else 6.dp)
                            .then(if (isToday) Modifier.shadow(9.dp, shape, ambientColor = OC.gold.copy(alpha = 0.55f), spotColor = OC.gold.copy(alpha = 0.55f)) else Modifier)
                            .then(
                                if (isToday) Modifier.background(Brush.verticalGradient(listOf(hex(0xFFD15A), OC.goldBottom)), shape)
                                else Modifier.background(if (m > 0) OC.gold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f), shape),
                            ),
                    )
                    Text(week[i], style = outfit(11, FontWeight.Heavy), color = if (isToday) OC.gold else OC.nightMuted)
                }
            }
        }
        Text("Bar height = minutes played that day", style = outfit(12), color = hex(0xBDB9B3))
    }
}

@Composable
private fun StreakCalendarCard() {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("September 2026", style = outfit(16, FontWeight.Bold), color = OC.ink)
            Spacer(Modifier.weight(1f))
            Text("${streakMinutes.size} days played", style = outfit(12, FontWeight.Bold), color = OC.muted)
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(d, style = outfit(11, FontWeight.Heavy), color = OC.faint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 5) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) StreakDayCell(row * 7 + col, Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(2.dp).background(OC.gold))
            Text("Gold line = your current groove", style = outfit(12), color = OC.muted)
        }
    }
}

@Composable
private fun StreakDayCell(d: Int, modifier: Modifier = Modifier) {
    if (d < 1 || d > 30) {
        Spacer(modifier.height(38.dp))
        return
    }
    val m = streakMinutes[d] ?: 0
    val inRun = d >= streakGrooveFrom && d <= streakToday
    val barColor = if (inRun) OC.gold else if (m > 0) OC.gold.copy(alpha = 0.35f) else Color.Transparent
    val textColor = if (d == streakToday) OC.goldText else if (d > streakToday) OC.faint.copy(alpha = 0.7f) else OC.muted
    Column(
        modifier.height(38.dp)
            .drawBehind { if (inRun) drawRect(OC.gold, topLeft = Offset(0f, size.height + 2.dp.toPx()), size = Size(size.width, 2.dp.toPx())) }
            .semantics { contentDescription = "September $d${if (m > 0) ", $m minutes" else ""}${if (inRun) ", in your groove" else ""}" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
    ) {
        Box(Modifier.width(18.dp).height(if (m > 0) maxOf(4f, m * 1.4f).dp else 0.dp).background(barColor, RoundedCornerShape(3.dp)))
        Text("$d", style = outfit(11, if (d == streakToday) FontWeight.Heavy else FontWeight.SemiBold), color = textColor)
    }
}

@Composable
private fun StreakGoalPicker(goal: Int, onGoal: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DAILY SET LENGTH", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.muted)
        Row(Modifier.fillMaxWidth().background(OC.softFill, RoundedCornerShape(20.dp)).padding(4.dp)) {
            streakGoals.forEach { (name, mins) ->
                val on = mins == goal
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).heightIn(min = 54.dp)
                        .then(
                            if (on) Modifier.shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                                .background(OC.surface.copy(alpha = 0.92f), shape).border(2.dp, OC.gold, shape)
                            else Modifier,
                        )
                        .plainClickable { onGoal(mins) }
                        .semantics { contentDescription = "$name, $mins minutes"; if (on) selected = true },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Text("$mins′", style = mono(15), color = OC.ink)
                    Text(name, style = outfit(11, FontWeight.SemiBold), color = OC.muted)
                }
            }
        }
    }
}

/** Quarter-rest glyph for the "rest note" card. */
@Composable
private fun LearnRestGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height) / 24f
        val path = Path().apply {
            moveTo(9 * s, 2.5f * s)
            lineTo(14.5f * s, 8.5f * s)
            lineTo(10 * s, 12.5f * s)
            lineTo(15 * s, 17.5f * s)
            quadraticTo(12 * s, 15.5f * s, 10 * s, 17.5f * s)
            quadraticTo(8 * s, 19.5f * s, 12.5f * s, 21.5f * s)
        }
        drawPath(path, color, style = Stroke(2.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// MARK: - Tiers

private data class LearnEarnRow(val title: String, val note: String, val value: String)

@Composable
fun TiersView() {
    val app = LocalApp.current
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Tier?>(Tier.ChordKeeper) }
    val points = app.learnProfile.points

    OCPage {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Your tier")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TiersHero(points)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TiersSplitCard(Icons.Outlined.School, "LEARNING", OC.goldText, "860", "Beats, groove and positions", Modifier.weight(1f)) { app.popToRoot(); app.tab = MainTab.Learn }
                    TiersSplitCard(Icons.Outlined.IosShare, "CONTRIBUTING", OC.blue, "380", "Uploads, fixes and saves", Modifier.weight(1f)) { app.switchTo(MainTab.Explore) }
                }
                Segmented(listOf(0, 1), tab, label = { if (it == 0) "How to earn" else "Tiers & perks" }) { tab = it }
                if (tab == 0) TiersEarnSection() else TiersListSection(points, open) { open = it }
                TiersRecentSection()
                BannerAdView(onRemoveAds = { app.modal = Modal.Pro })
            }
        }
    }
}

@Composable
private fun TiersHero(points: Int) {
    val tier = Tier.forPoints(points)
    Column(Modifier.fillMaxWidth().inkGlass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LearnTierShield(tier, width = 76.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("TIER ${tier.rawValue} OF ${Tier.entries.size}", style = outfit(12, FontWeight.Heavy, letterSpacing = 0.6.sp), color = OC.gold)
                Text(tier.title, style = outfit(26, FontWeight.Heavy), color = OC.paper)
                Text("${points.formatted()} O Points", style = mono(15), color = hex(0xD6D4CF))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LearnProgressBar(LearnTiersMath.progress(points), track = hex(0x2A2825), height = 12.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tier.minPoints.formatted(), style = outfit(12), color = hex(0xBDB9B3))
                Spacer(Modifier.weight(1f))
                val next = tier.next
                if (next != null) {
                    Text("${(next.minPoints - points).formatted()} points to ${next.title}", style = outfit(12, FontWeight.Bold), color = OC.paper)
                    Spacer(Modifier.weight(1f))
                    Text(next.minPoints.formatted(), style = outfit(12), color = hex(0xBDB9B3))
                } else {
                    Text("Top tier reached", style = outfit(12, FontWeight.Bold), color = OC.paper)
                }
            }
        }
    }
}

@Composable
private fun TiersSplitCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, value: String, note: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().glass().plainClickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(title, style = outfit(12, FontWeight.Heavy, letterSpacing = 0.5.sp), color = color)
        }
        Text(value, style = outfit(24, FontWeight.Heavy), color = OC.ink)
        Text(note, style = outfit(12), color = OC.muted)
    }
}

@Composable
private fun TiersEarnSection() {
    val earn = listOf(
        Triple("LEARNING", OC.goldText, listOf(
            LearnEarnRow("Earn lesson beats", "1 point per beat", "+1 / beat"),
            LearnEarnRow("Master a position", "Includes its soundcheck", "+50"),
            LearnEarnRow("Keep a 7-day groove", "Every 7 days in a row", "+30"),
            LearnEarnRow("Reach a 30-day groove", "Once per groove", "+150"),
        )),
        Triple("CONTRIBUTING", OC.blue, listOf(
            LearnEarnRow("Share a sheet publicly", "After 24 hours without reports", "+40"),
            LearnEarnRow("First sheet for a song", "No other version on the community yet", "+60"),
            LearnEarnRow("Someone saves your sheet", "Up to 50 points per sheet", "+5"),
            LearnEarnRow("Your correction is accepted", "On anyone’s sheet", "+25"),
            LearnEarnRow("Report that gets actioned", "Wrong chords, spam, duplicates", "+10"),
        )),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        earn.forEach { (title, color, rows) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = outfit(12, FontWeight.Heavy, letterSpacing = 0.6.sp), color = color)
                GlassList {
                    rows.forEach { r ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(r.title, style = outfit(14, FontWeight.Bold), color = OC.ink)
                                Text(r.note, style = outfit(12), color = OC.muted)
                            }
                            Text(r.value, style = mono(14), color = color, softWrap = false)
                        }
                    }
                }
            }
        }
        Text(
            "Contribution points count only after a sheet is public for 24 hours and isn’t reported. Removed or duplicate uploads lose their points.",
            style = outfit(12).copy(lineHeight = 17.sp), color = OC.muted,
        )
    }
}

private fun tierRange(t: Tier): String {
    val n = t.next ?: return "${t.minPoints.formatted()}+ points"
    return "${t.minPoints.formatted()} – ${(n.minPoints - 1).formatted()} points"
}

@Composable
private fun TiersListSection(points: Int, open: Tier?, onOpen: (Tier?) -> Unit) {
    val mine = Tier.forPoints(points)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tier.entries.forEach { t ->
            val isYou = t == mine
            val reached = t.rawValue < mine.rawValue
            val isOpen = open == t
            val color = hex(t.colorHex)
            val shape = RoundedCornerShape(20.dp)
            val rotation by animateFloatAsState(if (isOpen) 180f else 0f, label = "chevron")
            Column(Modifier.fillMaxWidth().clip(shape).background(OC.surface).border(2.dp, if (isYou) color else OC.border, shape)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).plainClickable { onOpen(if (isOpen) null else t) }
                        .semantics { contentDescription = "${t.title}, ${if (isOpen) "Expanded" else "Collapsed"}" }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    LearnTierShield(t, width = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(t.title, style = outfit(16, FontWeight.Heavy), color = OC.ink)
                        Text(tierRange(t), style = mono(12, FontWeight.SemiBold), color = OC.muted)
                    }
                    if (isYou) {
                        Text("YOU", style = outfit(11, FontWeight.Heavy), color = OC.inkFixed, modifier = Modifier.background(OC.gold, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (reached) Icon(Icons.Rounded.Check, contentDescription = "Reached", tint = OC.teal, modifier = Modifier.size(18.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp).rotate(rotation))
                }
                AnimatedVisibility(visible = isOpen, enter = fadeIn(), exit = fadeOut()) {
                    Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.perks.forEach { perk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Text("•", style = outfit(13, FontWeight.Heavy), color = color)
                                Text(perk, style = outfit(13).copy(lineHeight = 18.sp), color = OC.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TiersRecentSection() {
    val recent = listOf(
        listOf("Lesson complete: C ↔ G/B", "Today", "+15") to OC.gold,
        listOf("7-day groove bonus", "Yesterday", "+30") to OC.gold,
        listOf("Shared စဉ်းစားပါ publicly", "This week", "+40") to OC.blue,
        listOf("Your sheet was saved", "[n] times this week", "+5 each") to OC.blue,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RECENT POINTS", style = outfit(12, FontWeight.Heavy, letterSpacing = 0.6.sp), color = OC.muted)
        GlassList {
            recent.forEach { (r, color) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(r[0].learnGlyphs, style = outfit(14, FontWeight.Bold), color = OC.ink)
                        Text(r[1], style = outfit(12), color = OC.muted)
                    }
                    Text(r[2], style = mono(14), color = OC.ink, softWrap = false)
                }
            }
        }
    }
}

// MARK: - Game (Chord Rush)

private enum class GamePhase { Intro, Play, Done }

private val gameChordSet = listOf("C", "G", "Am", "F", "Em", "D", "Dm", "E")
private val gameDeck = listOf("Am", "D", "C", "Em", "G", "F", "Dm", "C", "E", "G", "Am", "D", "F", "Em", "C", "G", "Dm", "Am", "E", "D")
private const val gameDuration = 30

private fun gameOptions(q: Int): List<String> {
    val id = gameDeck[q % gameDeck.size]
    val others = gameChordSet.filter { it != id }
    val off = (q * 3) % others.size
    val opts = listOf(id, others[off], others[(off + 2) % others.size], others[(off + 5) % others.size])
    val orders = listOf(listOf(0, 1, 2, 3), listOf(2, 0, 3, 1), listOf(1, 3, 0, 2), listOf(3, 2, 1, 0))
    return orders[q % 4].map { opts[it] }
}

private fun multLabel(c: Int): String {
    val m = 1 + minOf(c, 4) * 0.5
    return if (m == Math.rint(m)) m.toInt().toString() else m.toString()
}

/** "FRI 25 SEP" — weekday, day, month without commas. */
private fun gameDayLabel(): String = SimpleDateFormat("EEE d MMM", Locale.UK).format(Date()).replace(",", "").uppercase(Locale.UK)

@Composable
fun GameView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dayLabel = remember { gameDayLabel() }

    var phase by remember { mutableStateOf(GamePhase.Intro) }
    var time by remember { mutableIntStateOf(gameDuration) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var q by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf<List<String>>(emptyList()) }
    var flash by remember { mutableStateOf("") }
    var flashOK by remember { mutableStateOf(true) }
    var lock by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(180) }
    var practice by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }

    val current = gameDeck[q % gameDeck.size]

    fun finish() {
        time = 0
        phase = GamePhase.Done
        if (!practice) best = maxOf(best, score)
    }

    fun begin(isPractice: Boolean) {
        practice = isPractice
        phase = GamePhase.Play
        time = gameDuration
        score = 0; combo = 0; bestCombo = 0; q = 0; answered = 0; correct = 0
        missed = emptyList(); flash = ""; lock = false
        round += 1
    }

    fun pick(name: String) {
        if (lock || phase != GamePhase.Play) return
        val id = current
        val ok = name == id
        val gain = if (ok) (10 * (1 + minOf(combo, 4) * 0.5)).roundToInt() else 0
        val newCombo = if (ok) combo + 1 else 0
        lock = true
        flashOK = ok
        flash = if (ok) "+$gain${if (newCombo >= 3) " · on a roll!" else ""}" else "It was $id · −2s"
        score += gain
        combo = newCombo
        bestCombo = maxOf(bestCombo, newCombo)
        if (ok) correct += 1
        if (!ok && id !in missed) missed = missed + id
        if (!ok) time = maxOf(1, time - 2)
        answered += 1
        scope.launch {
            delay(if (ok) 350 else 800)
            if (phase != GamePhase.Play) return@launch
            q += 1
            flash = ""
            lock = false
        }
    }

    // The countdown: one tick per second while playing; cancelled when the screen leaves.
    LaunchedEffect(phase, round) {
        if (phase != GamePhase.Play) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            if (phase != GamePhase.Play) return@LaunchedEffect
            if (time <= 1) { finish(); return@LaunchedEffect }
            time -= 1
        }
    }
    LaunchedEffect(answered) {
        if (answered > 0) haptic.performHapticFeedback(if (flashOK) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }

    OCPage(night = true) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).plainClickable { app.pop() }.semantics { contentDescription = "Back to Learn" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(20.dp).offset(x = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chord Rush", style = outfit(16, FontWeight.Bold), color = OC.nightText)
                    Text("DAILY GAME · $dayLabel", style = outfit(11, letterSpacing = 1.sp), color = OC.nightMuted)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }
            when (phase) {
                GamePhase.Intro -> GameIntro(best) { begin(false) }
                GamePhase.Play -> GamePlay(time, score, combo, q, current, flash, flashOK) { pick(it) }
                GamePhase.Done -> GameResults(
                    score = score, best = best, practice = practice, correct = correct, answered = answered, bestCombo = bestCombo, missed = missed,
                    onShare = {
                        val text = "I scored $score in today’s Chord Rush on O Chords — $correct of $answered shapes!"
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onPractice = { begin(true) },
                    onReview = { m -> app.push(Route.Chords(focus = m, songChords = missed)) },
                    onBack = {
                        app.pop()
                        app.showInterstitial()
                    },
                )
            }
        }
    }
}

@Composable
private fun GameIntro(best: Int, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().nightGlass(24.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HOW TO PLAY", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.gold)
                Text("Name as many chord shapes as you can in 30 seconds.", style = outfit(26, FontWeight.SemiBold, letterSpacing = (-0.8).sp), color = OC.nightText)
                Text("Right answers in a row build a combo — up to ×3 points. Wrong ones cost time, not lives.", style = outfit(14).copy(lineHeight = 20.sp), color = hex(0xBDB9B3))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TODAY’S CHORDS", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.nightMuted)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    gameChordSet.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                Box(
                                    Modifier.weight(1f).heightIn(min = 44.dp).background(OC.gold.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).border(1.dp, OC.gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(c, style = mono(16), color = OC.gold) }
                            }
                        }
                    }
                }
                Text("Everyone plays the same set today — mixed from the chords you’ve learned.", style = outfit(12), color = OC.nightMuted)
            }

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnNightStat("YOUR BEST", "$best", Modifier.weight(1f))
                LearnNightStat("DAYS PLAYED", "6", Modifier.weight(1f))
                LearnNightStat("REWARD", "+12", Modifier.weight(1f), valueColor = OC.gold)
            }
        }
        GoldButton(
            "Start today’s rush", Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp).navigationBarsPadding(), height = 58.dp,
            leading = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(20.dp)) },
            onClick = onStart,
        )
    }
}

@Composable
private fun GamePlay(time: Int, score: Int, combo: Int, q: Int, current: String, flash: String, flashOK: Boolean, onPick: (String) -> Unit) {
    val progress by animateFloatAsState(time.toFloat() / gameDuration.toFloat(), animationSpec = tween(1000, easing = LinearEasing), label = "timer")
    val options = remember(q) { gameOptions(q) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "$time seconds left" },
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            LearnProgressBar(progress, Modifier.weight(1f), track = Color.White.copy(alpha = 0.1f), fill = if (time <= 5) hex(0xF87171) else OC.gold, height = 12.dp)
            Text("${time}s", style = mono(18), color = if (time <= 5) hex(0xFCA5A5) else OC.nightText, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 44.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SCORE", style = outfit(10, FontWeight.Heavy, letterSpacing = 1.sp), color = OC.nightMuted)
                Text("$score", style = outfit(30, FontWeight.Bold), color = OC.nightText)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(34.dp).clip(CircleShape).background(if (combo >= 2) OC.gold else Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("COMBO ×${multLabel(combo)}", style = outfit(14, FontWeight.Heavy), color = if (combo >= 2) OC.inkFixed else OC.nightMuted) }
        }

        val borderColor = if (flash.isEmpty()) Color.White.copy(alpha = 0.4f) else if (flashOK) hex(0x5EEAD4) else hex(0xF87171)
        AnimatedContent(
            targetState = q, label = "shape",
            transitionSpec = { (scaleIn(initialScale = 0.92f) + fadeIn()) togetherWith fadeOut() },
        ) { shown ->
            val name = gameDeck[shown % gameDeck.size]
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(OC.paper, shape).border(2.dp, borderColor, shape).padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChordLibrary.info(name)?.voicings?.firstOrNull()?.let { v ->
                    LearnGameDiagram(v, Modifier.size(170.dp, 196.dp).semantics { contentDescription = "Chord shape, pattern ${v.pattern.toCharArray().joinToString(" ")}" })
                }
                Text("What chord is this?", style = outfit(12, FontWeight.Bold), color = hex(0x6B6660))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { name ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 68.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).plainClickable { onPick(name) },
                            contentAlignment = Alignment.Center,
                        ) { Text(name, style = mono(24), color = OC.nightText) }
                    }
                }
            }
        }
        Text(flash, style = outfit(13, FontWeight.Bold), color = if (flashOK) hex(0x5EEAD4) else hex(0xFCA5A5), modifier = Modifier.heightIn(min = 18.dp))
    }
}

@Composable
private fun GameResults(
    score: Int, best: Int, practice: Boolean, correct: Int, answered: Int, bestCombo: Int, missed: List<String>,
    onShare: () -> Unit, onPractice: () -> Unit, onReview: (String) -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().nightGlass(24.dp).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (score >= best && score > 0 && !practice) "NEW PERSONAL BEST" else if (practice) "PRACTICE ROUND" else "TIME’S UP",
                    style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.gold,
                )
                Text("$score", style = outfit(64, FontWeight.Bold, letterSpacing = (-2).sp), color = OC.nightText)
                Text("$correct of $answered shapes · best combo ×${multLabel(bestCombo)}", style = outfit(14), color = hex(0xBDB9B3))
            }

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnNightStat("REWARD", if (practice) "—" else "+12 beats", Modifier.weight(1f), valueColor = OC.gold, note = "Counts toward today’s set")
                LearnNightStat("FRIENDS TODAY", "#[n] of [n]", Modifier.weight(1f), note = "Resets at midnight")
            }

            if (missed.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("REVIEW THESE SHAPES", style = outfit(11, FontWeight.Bold, letterSpacing = 1.6.sp), color = OC.nightMuted)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        missed.forEach { m ->
                            Box(
                                Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).background(hex(0xFCA5A5, 0.14f), RoundedCornerShape(12.dp))
                                    .border(1.dp, hex(0xFCA5A5, 0.35f), RoundedCornerShape(12.dp)).plainClickable { onReview(m) }
                                    .semantics { contentDescription = "Review $m" }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("$m ›", style = mono(15), color = hex(0xFCA5A5)) }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LearnNightOutlineButton("Share my score", onClick = onShare)
            LearnNightOutlineButton("Practice round (no reward)", onClick = onPractice)
            GoldButton("Back to the fretboard", height = 56.dp, onClick = onBack)
        }
    }
}

/** Plain black-dot chord shape for Chord Rush (no finger colours, so it can't give the answer away). */
@Composable
private fun LearnGameDiagram(voicing: ChordVoicing, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = hex(0x1A1917)
        val line = hex(0x8E8A84)
        val left = 12f
        val right = size.width - 12f
        val gap = (right - left) / 5f
        val top = 30f
        val fretH = (size.height - top - 4f) / 4f
        val pattern = voicing.pattern
        fun x(i: Int) = left + i * gap
        // nut + frets
        drawRoundRect(if (voicing.baseFret > 1) line else ink, topLeft = Offset(left - 2f, top - 3f), size = Size(right - left + 4f, 5f), cornerRadius = CornerRadius(2f))
        for (f in 1..4) drawRect(line.copy(alpha = 0.7f), topLeft = Offset(left, top + f * fretH - 1f), size = Size(right - left, 1.5f))
        for (i in 0 until 6) {
            drawRect(line, topLeft = Offset(x(i) - 0.75f, top), size = Size(1.5f, fretH * 4))
            val c = if (i < pattern.length) pattern[i] else '0'
            val r = 6f
            if (c == '0') {
                drawCircle(ink, radius = r, center = Offset(x(i), 8f + r), style = Stroke(2f))
            } else if (c == 'x') {
                drawLine(line, Offset(x(i) - 5f, 9f), Offset(x(i) + 5f, 19f), strokeWidth = 2f)
                drawLine(line, Offset(x(i) + 5f, 9f), Offset(x(i) - 5f, 19f), strokeWidth = 2f)
            }
        }
        val dot = 26f
        voicing.barre?.let { b ->
            val a = minOf(x(b.from), x(b.to))
            val z = maxOf(x(b.from), x(b.to))
            val cy = top + (b.fret - 0.5f) * fretH
            drawRoundRect(ink, topLeft = Offset(a - dot / 2, cy - dot / 2), size = Size(z - a + dot, dot), cornerRadius = CornerRadius(dot / 2))
        }
        for (d in voicing.dots) {
            val cy = top + (d.fret - 0.5f) * fretH
            drawCircle(ink, radius = dot / 2, center = Offset(x(d.string), cy))
        }
    }
}

// MARK: - Interstitial ad

@Composable
fun InterstitialAdView() {
    val app = LocalApp.current
    val accent = hex(0xFFC940)
    Box(Modifier.fillMaxSize().semantics { contentDescription = "Advertisement" }) {
        NightBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(18.dp).border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("Ad", style = outfit(10, FontWeight.Heavy, letterSpacing = 0.5.sp), color = accent)
                    }
                    Text("[Advertiser]", style = outfit(13), color = OC.nightMuted)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).plainClickable { app.modal = null }.semantics { contentDescription = "Close ad" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                val dash = Color.White.copy(alpha = 0.25f)
                Box(
                    Modifier.fillMaxWidth().weight(1f).heightIn(max = 440.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f))
                        .drawBehind {
                            // Diagonal placeholder stripes for the ad creative.
                            val step = 24.dp.toPx()
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(Color.White.copy(alpha = 0.05f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 12.dp.toPx())
                                x += step
                            }
                            drawRoundRect(dash, topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()), size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("[Ad creative from Google]", style = outfit(14, FontWeight.Bold), color = OC.nightMuted) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("[Ad headline]", style = outfit(16, FontWeight.Bold), color = OC.nightText)
                        Text("[Advertiser] · [rating]", style = outfit(13), color = OC.nightMuted)
                    }
                    Box(Modifier.height(40.dp).clip(CircleShape).background(OC.nightText).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("[Install]", style = outfit(14, FontWeight.Bold), color = OC.inkFixed)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Shown after a finished session · at most once every 3 sessions", style = outfit(12), color = OC.nightMuted, textAlign = TextAlign.Center)
                Box(Modifier.heightIn(min = 44.dp).plainClickable { app.modal = Modal.Pro }, contentAlignment = Alignment.Center) {
                    Text("Remove ads with Pro ›", style = outfit(13, FontWeight.Bold), color = accent)
                }
            }
        }
    }
}
