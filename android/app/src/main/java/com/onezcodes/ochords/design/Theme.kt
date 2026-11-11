package com.onezcodes.ochords.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.R

// MARK: - Colour tokens
// Source: "HANDOVER · DESIGN TOKENS (GLASS UI)" + "DARK MODE MAPPING" on the design canvas (same as iOS `OC`).

fun hex(value: Long, alpha: Float = 1f): Color = Color(
    red = ((value shr 16) and 0xFF) / 255f,
    green = ((value shr 8) and 0xFF) / 255f,
    blue = (value and 0xFF) / 255f,
    alpha = alpha,
)

/** Whether the app is drawing in dark mode (the chosen appearance, or the system's). */
val LocalDark = staticCompositionLocalOf { false }

val isDark: Boolean
    @Composable @ReadOnlyComposable get() = LocalDark.current

/** A colour that switches with the interface style. */
@Composable
@ReadOnlyComposable
private fun adaptive(light: Color, dark: Color): Color = if (isDark) dark else light

object OC {
    // Brand
    val gold = hex(0xFFB800)
    val goldTop = hex(0xFFCB3D)
    val goldBottom = hex(0xFFB300)
    /** Gold used for text on light surfaces (#8F5E00) / dark (#FFC940). */
    val goldText: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x8F5E00), hex(0xFFC940))
    val goldDeep = hex(0x6B4600)
    val goldTint: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xFFF4D6), hex(0xFFB800, 0.16f))

    // Ink & neutrals
    val ink: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x171614), hex(0xF4F1EA))
    val inkFixed = hex(0x171614)
    val paper = hex(0xFAFAF8)
    val muted: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x6B6660), hex(0xA8A49E))
    val faint = hex(0x8E8A84)
    val line: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xF1F0ED), Color.White.copy(alpha = 0.10f))
    val border: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xE8E7E4), Color.White.copy(alpha = 0.14f))
    val softFill: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x171614, 0.05f), Color.White.copy(alpha = 0.08f))
    val switchOff: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xD6D4CF), Color.White.copy(alpha = 0.2f))
    /** Background of the page (under the gold glow). */
    val background: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xF6F4EF), hex(0x0B0A09))
    val surface: Color @Composable @ReadOnlyComposable get() = adaptive(Color.White, hex(0x1A1917))
    /** Body copy colour used on legal / FAQ text. */
    val bodyText: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x3A3835), hex(0xD6D2CB))

    // Glass
    val glass: Color @Composable @ReadOnlyComposable get() = adaptive(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.07f))
    val glassBorder: Color @Composable @ReadOnlyComposable get() = adaptive(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.12f))
    val inkGlass = hex(0x171614, 0.86f)

    // Semantic
    val blue: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x1E3A8A), hex(0x93A8E8))
    val blueTint: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xE3E8F6), hex(0x1E3A8A, 0.35f))
    val teal: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x0F766E), hex(0x5EEAD4))
    val tealText: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x0F5B53), hex(0x5EEAD4))
    val tealTint: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xDDEFEA), hex(0x0F766E, 0.28f))
    val red: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xB91C1C), hex(0xF87171))
    val redTint: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0xFDE8E8), hex(0xB91C1C, 0.25f))
    val purple: Color @Composable @ReadOnlyComposable get() = adaptive(hex(0x6D28D9), hex(0xC4B5FD))

    // Finger colours (chord diagrams) — fixed, always white text.
    val finger: Map<Int, Color> = mapOf(1 to hex(0x8F5E00), 2 to hex(0x1E3A8A), 3 to hex(0x0F766E), 4 to hex(0x6D28D9))

    // Always-dark screens (Stage, Complete, Listen, Game…)
    val night = hex(0x0B0A09)
    val nightText = hex(0xF4F1EA)
    val nightMuted = hex(0xA8A49E)
    val nightGlass = Color.White.copy(alpha = 0.07f)
    val nightBorder = Color.White.copy(alpha = 0.12f)

    // Radii
    val rCard: Dp = 20.dp
    val rButton: Dp = 14.dp
    val rChip: Dp = 8.dp
}

// MARK: - Typography
// Outfit (UI) · JetBrains Mono (chords) · Noto Sans Myanmar (lyrics, line-height 1.8). All three are variable fonts.

private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

object OCFonts {
    val outfit = variable(R.font.outfit, 300, 400, 500, 600, 700, 800, 900)
    val mono = variable(R.font.jetbrains_mono, 400, 500, 600, 700, 800)
    val myanmar = variable(R.font.noto_sans_myanmar, 400, 500, 600, 700)
}

fun outfit(size: Int, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(fontFamily = OCFonts.outfit, fontSize = size.sp, fontWeight = weight, letterSpacing = letterSpacing)

fun outfit(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = OCFonts.outfit, fontSize = size.sp, fontWeight = weight)

fun mono(size: Int, weight: FontWeight = FontWeight.Bold): TextStyle =
    TextStyle(fontFamily = OCFonts.mono, fontSize = size.sp, fontWeight = weight)

fun mono(size: Float, weight: FontWeight = FontWeight.Bold): TextStyle =
    TextStyle(fontFamily = OCFonts.mono, fontSize = size.sp, fontWeight = weight)

fun myanmar(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = OCFonts.myanmar, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.8f).sp)

val FontWeight.Companion.Heavy: FontWeight get() = FontWeight.ExtraBold

// MARK: - Appearance

enum class Appearance(val title: String) {
    System("System"), Light("Light"), Dark("Dark");

    companion object {
        fun from(raw: String?): Appearance = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System
    }
}

@Composable
fun OChordsTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val scheme: ColorScheme = if (dark) darkColorScheme(
        primary = hex(0xFFC940), onPrimary = hex(0x171614), background = hex(0x0B0A09), onBackground = hex(0xF4F1EA),
        surface = hex(0x1A1917), onSurface = hex(0xF4F1EA), error = hex(0xF87171),
    ) else lightColorScheme(
        primary = hex(0x8F5E00), onPrimary = Color.White, background = hex(0xF6F4EF), onBackground = hex(0x171614),
        surface = Color.White, onSurface = hex(0x171614), error = hex(0xB91C1C),
    )
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.copy(bodyLarge = outfit(16)), content = content)
    }
}
