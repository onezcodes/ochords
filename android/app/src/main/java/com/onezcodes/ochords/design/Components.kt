package com.onezcodes.ochords.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.model.SectionKind
import kotlinx.coroutines.delay

// MARK: - Backgrounds

/** Warm #F6F4EF page with soft gold light. In dark mode: #0B0A09 with a 22% gold glow. */
@Composable
fun WarmBackground(modifier: Modifier = Modifier) {
    val bg = OC.background
    val dark = isDark
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(bg)
        if (dark) {
            drawRect(Brush.radialGradient(listOf(OC.gold.copy(alpha = 0.22f), Color.Transparent), center = Offset(w, -0.05f * h), radius = w * 1.1f))
            drawRect(Brush.radialGradient(listOf(hex(0xFF7828, 0.12f), Color.Transparent), center = Offset(-0.1f * w, 0.7f * h), radius = w * 0.9f))
        } else {
            drawRect(Brush.radialGradient(listOf(OC.gold.copy(alpha = 0.30f), Color.Transparent), center = Offset(w, -0.05f * h), radius = maxOf(w * 1.1f, h * 0.5f)))
            drawRect(Brush.radialGradient(listOf(hex(0xFFD678, 0.22f), Color.Transparent), center = Offset(-0.1f * w, 0.55f * h), radius = w * 0.9f))
            drawRect(Brush.radialGradient(listOf(hex(0xFF9646, 0.14f), Color.Transparent), center = Offset(1.1f * w, 1.05f * h), radius = w * 0.8f))
        }
    }
}

/** Always-dark background used by Stage, Session recorded, Listen, Chord Rush, ads. */
@Composable
fun NightBackground(modifier: Modifier = Modifier, glowCenter: Offset = Offset(1f, -0.05f)) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(OC.night)
        drawRect(Brush.radialGradient(listOf(OC.gold.copy(alpha = 0.26f), Color.Transparent), center = Offset(glowCenter.x * w, glowCenter.y * h), radius = w))
        drawRect(Brush.radialGradient(listOf(hex(0xFF7828, 0.14f), Color.Transparent), center = Offset(-0.1f * w, 0.8f * h), radius = w * 0.9f))
    }
}

/** Standard page scaffold: warm background under the content, status-bar inset applied. */
@Composable
fun OCPage(modifier: Modifier = Modifier, night: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        if (night) NightBackground() else WarmBackground()
        Box(Modifier.fillMaxSize().statusBarsPadding(), content = content)
    }
}

// MARK: - Surfaces

/** White 58% glass + 1px white border + soft shadow. */
fun Modifier.glass(radius: Dp = OC.rCard): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    this.shadow(15.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.12f))
        .clip(shape)
        .background(OC.surface.copy(alpha = if (isDark) 0.6f else 0.58f))
        .background(OC.glass)
        .border(1.dp, OC.glassBorder, shape)
}

/** Ink 86% hero card. */
fun Modifier.inkGlass(radius: Dp = OC.rCard): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    this.shadow(17.dp, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.3f))
        .clip(shape).background(OC.inkGlass).border(1.dp, Color.White.copy(alpha = 0.10f), shape)
}

/** White 7% on dark screens. */
fun Modifier.nightGlass(radius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(OC.nightGlass).border(1.dp, OC.nightBorder, shape)
}


/** Click without a ripple (the design's plain buttons). */
fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

// MARK: - Buttons

@Composable
private fun pressScale(interaction: MutableInteractionSource, pressed: Float = 0.98f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    return if (isPressed) pressed else 1f
}

/** Gold gradient primary button (54dp). */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, height: Dp = 54.dp, fullWidth: Boolean = true, enabled: Boolean = true,
               leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OC.rButton)
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(pressScale(interaction))
            .shadow(10.dp, shape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom)))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = height)
            .padding(horizontal = if (fullWidth) 0.dp else 18.dp)
            .alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = outfit(16, FontWeight.Bold), color = OC.inkFixed)
    }
}

/** Ink (or paper, in dark mode) solid button. */
@Composable
fun InkButton(text: String, modifier: Modifier = Modifier, height: Dp = 54.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OC.rButton)
    Box(
        modifier.fillMaxWidth().scale(pressScale(interaction)).clip(shape).background(OC.ink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = height).alpha(if (enabled) 1f else 0.6f),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(16, FontWeight.Bold), color = if (isDark) hex(0x171614) else hex(0xFAFAF8)) }
}

/** Glass secondary button (50dp). */
@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, height: Dp = 50.dp, enabled: Boolean = true,
                leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().scale(pressScale(interaction)).glass(OC.rButton)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = height).alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = outfit(15, FontWeight.Bold), color = OC.ink)
    }
}

@Composable
fun DestructiveButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OC.rButton)
    Box(
        modifier.fillMaxWidth().clip(shape).background(OC.red)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 54.dp).alpha(if (enabled) 1f else 0.6f),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(16, FontWeight.Bold), color = Color.White) }
}

/** Primary button that looks disabled (grey) until `enabled`. */
@Composable
fun GatedButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onColor: Color = OC.gold, onText: Color = OC.inkFixed, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OC.rButton)
    Box(
        modifier.fillMaxWidth().clip(shape).background(if (enabled) onColor else OC.border)
            .plainClickable(enabled = enabled, onClick = onClick).heightIn(min = 54.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(16, FontWeight.Bold), color = if (enabled) onText else OC.faint) }
}

/** 44dp round glass icon button (bell, profile, close…). */
@Composable
fun CircleIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, badge: Int? = null, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(modifier.size(size + 4.dp)) {
        Box(
            Modifier.size(size).glass(size / 2).plainClickable(onClick = onClick).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = OC.ink, modifier = Modifier.size(20.dp)) }
        if (badge != null && badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).defaultMinSize(18.dp, 18.dp).clip(CircleShape).background(OC.red)
                    .border(2.dp, OC.background, CircleShape).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", style = outfit(11, FontWeight.Heavy), color = Color.White) }
        }
    }
}

// MARK: - Top bar

/** Back chevron · centred title · optional trailing view. Matches the 54pt-top header on most screens. */
@Composable
fun TopBar(title: String = "", backLabel: String = "Back", onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).plainClickable { onBack?.invoke() ?: app.pop() }.semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = OC.ink, modifier = Modifier.size(20.dp).offset(x = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = outfit(16, FontWeight.Bold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(Modifier.widthIn(min = 44.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

// MARK: - Small pieces

/** Uppercase eyebrow with a short gold rule: "── ADS KEEP O CHORDS FREE" */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = OC.muted) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(18.dp).height(2.dp).background(OC.gold))
        Text(text.uppercase(), style = outfit(12, FontWeight.SemiBold, letterSpacing = 1.9.sp), color = color)
    }
}

/** VERSE / CHORUS / MUSIC tags. */
@Composable
fun SectionTag(label: String, kind: SectionKind, modifier: Modifier = Modifier) {
    val (bg, fg) = sectionColors(kind)
    Box(modifier.height(22.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label.uppercase(), style = outfit(11, FontWeight.Heavy, letterSpacing = 1.sp), color = fg)
    }
}

@Composable
fun sectionColors(k: SectionKind): Pair<Color, Color> = when (k) {
    SectionKind.Verse, SectionKind.Intro -> OC.blueTint to OC.blue
    SectionKind.Chorus -> OC.gold to OC.inkFixed
    SectionKind.Music -> OC.tealTint to OC.tealText
    SectionKind.Bridge -> hex(0x6D28D9, 0.14f) to OC.purple
    SectionKind.Flow -> OC.softFill to OC.muted
}

enum class PillTone { Teal, Gold, Red, Neutral, Blue, Ink }

@Composable
fun StatusPill(text: String, tone: PillTone = PillTone.Teal, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        PillTone.Teal -> OC.tealTint to OC.tealText
        PillTone.Gold -> OC.goldTint to OC.goldText
        PillTone.Red -> OC.redTint to OC.red
        PillTone.Neutral -> OC.softFill to OC.muted
        PillTone.Blue -> OC.blueTint to OC.blue
        PillTone.Ink -> OC.ink to OC.background
    }
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = outfit(12, FontWeight.Bold), color = fg)
    }
}

/** Selectable chip (filters, chord chips). */
@Composable
fun Chip(text: String, isOn: Boolean, modifier: Modifier = Modifier, mono: Boolean = false, height: Dp = 36.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.defaultMinSize(minWidth = 44.dp, minHeight = height).clip(shape)
            .background(if (isOn) OC.gold else OC.glass).border(1.dp, if (isOn) OC.gold else OC.border, shape)
            .plainClickable(onClick = onClick).semantics { contentDescription = text }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (mono) mono(15) else outfit(14, FontWeight.SemiBold), color = if (isOn) OC.inkFixed else OC.ink)
    }
}

/** Ink-on / grey-off switch (gold when dark). */
@Composable
fun OCSwitch(isOn: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val thumb by animateDpAsState(if (isOn) 20.dp else 0.dp, label = "switch")
    Box(
        modifier.width(52.dp).height(32.dp).clip(CircleShape)
            .background(if (isOn) (if (isDark) OC.gold else OC.inkFixed) else OC.switchOff)
            .plainClickable(role = Role.Switch) { onChange(!isOn) }.padding(3.dp),
    ) {
        Box(Modifier.offset(x = thumb).size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** A row with a label (+subtitle) and an OC switch. */
@Composable
fun ToggleRow(title: String, isOn: Boolean, modifier: Modifier = Modifier, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = outfit(15, FontWeight.SemiBold), color = OC.ink)
            if (subtitle != null) Text(subtitle, style = outfit(12), color = OC.muted)
        }
        Spacer(Modifier.width(8.dp))
        OCSwitch(isOn, onChange = onChange)
    }
}

/** Pill segmented control on a soft fill. */
@Composable
fun <T> Segmented(options: List<T>, selection: T, modifier: Modifier = Modifier, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(OC.softFill).padding(3.dp)) {
        options.forEach { o ->
            val on = o == selection
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp)
                    .then(if (on) Modifier.shadow(3.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.06f)).clip(RoundedCornerShape(12.dp)).background(OC.surface) else Modifier)
                    .plainClickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), style = outfit(13, FontWeight.Bold), color = if (on) OC.ink else OC.muted) }
        }
    }
}

/** Glass list group with hairline dividers between its children. */
@Composable
fun GlassList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(), content = content)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(OC.line)) }

/** Row inside a GlassList: title (+subtitle) with a trailing chevron / value. */
@Composable
fun ListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, value: String? = null, icon: ImageVector? = null,
            tint: Color = OC.ink, chevron: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.plainClickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = outfit(15, FontWeight.Medium), color = tint)
            if (subtitle != null) Text(subtitle, style = outfit(12), color = OC.muted)
        }
        if (value != null) Text(value, style = outfit(14), color = OC.muted)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
    }
}

/** "── ACCOUNT" group header (11pt, 14dp gold rule). */
@Composable
fun GroupHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(14.dp).height(2.dp).background(OC.gold))
        Text(title.uppercase(), style = outfit(11, FontWeight.SemiBold, letterSpacing = 1.8.sp), color = OC.muted)
    }
}

/** Dashed-border placeholder box ("[Banner ad]"). */
@Composable
fun PlaceholderBox(text: String, modifier: Modifier = Modifier, height: Dp = 120.dp, dark: Boolean = false) {
    val stroke = if (dark) Color.White.copy(alpha = 0.25f) else OC.faint.copy(alpha = 0.5f)
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(if (dark) Color.White.copy(alpha = 0.06f) else OC.softFill)
            .drawBehind {
                drawRoundRect(stroke, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = outfit(14, FontWeight.Bold), color = if (dark) OC.nightMuted else OC.muted) }
}

/** Selectable card (gold border / tint when on). */
@Composable
fun ChoiceCard(isOn: Boolean, modifier: Modifier = Modifier, minHeight: Dp = 60.dp, onClick: () -> Unit, label: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(OC.rCard)
    Box(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(if (isOn) OC.goldTint else OC.glass)
            .border(2.dp, if (isOn) OC.gold else OC.border, shape).plainClickable(onClick = onClick).semantics { contentDescription = if (isOn) "selected" else "" },
        contentAlignment = Alignment.CenterStart, content = label,
    )
}

/** Radio dot used by plan / option pickers. */
@Composable
fun RadioDot(isOn: Boolean, modifier: Modifier = Modifier, color: Color = OC.gold) {
    Box(modifier.size(22.dp).border(2.dp, if (isOn) color else OC.border, CircleShape), contentAlignment = Alignment.Center) {
        if (isOn) Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

// MARK: - Toast

data class Toast(val text: String, val icon: ImageVector = Icons.Filled.CheckCircle)

/** Bottom toast over `content`; clears itself after 2 s. */
@Composable
fun ToastHost(toast: Toast?, onDismiss: () -> Unit, modifier: Modifier = Modifier, bottomPadding: Dp = 110.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        content()
        LaunchedEffect(toast) { if (toast != null) { delay(2000); onDismiss() } }
        AnimatedVisibility(
            visible = toast != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            val t = remember(toast) { toast } ?: return@AnimatedVisibility
            Row(Modifier.height(44.dp).clip(CircleShape).background(OC.inkGlass).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(t.icon, contentDescription = null, tint = OC.gold, modifier = Modifier.size(18.dp))
                Text(t.text, style = outfit(14, FontWeight.SemiBold), color = OC.paper)
            }
        }
    }
}

/** Shown to guests where account details would be: what an account adds, with Sign up / Log in. */
@Composable
fun GuestAccountCard(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Column(modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(OC.goldTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonAddAlt, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You’re using O Chords as a guest", style = outfit(17, FontWeight.Bold), color = OC.ink)
                Text("Your songs are saved on this phone. Create an account to keep them safe, share sheets, earn O Points and read Myanmar lyrics when you scan.",
                    style = outfit(13), color = OC.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Create account", Modifier.weight(1f), height = 44.dp) { app.showAccountOptions(signUp = true) }
            GlassButton("Log in", Modifier.weight(1f), height = 44.dp) { app.showAccountOptions(signUp = false) }
        }
        if (app.library.isNotEmpty()) {
            Text("Your ${app.library.size} song${if (app.library.size == 1) "" else "s"} will move into your account.", style = outfit(12), color = OC.muted)
        }
    }
}

/** Frosted bottom action bar (Feedback, Delete account, Pro). */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().shadow(12.dp, ambientColor = Color.Black.copy(alpha = 0.05f))
            .background(OC.surface.copy(alpha = 0.7f)).background(OC.glass)
            .drawBehind { drawRect(Color.White.copy(alpha = 0.6f), size = size.copy(height = 1.dp.toPx())) }
            .padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}


/** The standard section card: glass with 14dp padding. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = OC.rCard, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(radius).padding(padding), content = content)
}

fun shapeOf(radius: Dp): Shape = RoundedCornerShape(radius)
fun outlined(color: Color, width: Dp = 1.dp) = BorderStroke(width, color)
