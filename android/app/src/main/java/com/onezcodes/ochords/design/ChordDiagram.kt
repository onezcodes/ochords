package com.onezcodes.ochords.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezcodes.ochords.model.ChordVoicing
import com.onezcodes.ochords.services.ChordLibrary
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.platform.LocalContext
import com.onezcodes.ochords.R

/**
 * Fretboard diagram with coloured finger dots (index gold-brown, middle blue, ring teal, pinky purple).
 * Scales to the given width; 4 frets shown. Draws with one Canvas so it stays crisp at any size.
 */
@Composable
fun ChordDiagram(
    voicing: ChordVoicing,
    width: Dp = 260.dp,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false,
    showFingers: Boolean = true,
    showStringNames: Boolean = true,
    dark: Boolean = false,
) {
    val names = listOf("E", "A", "D", "G", "B", "e")
    val ink = if (dark) OC.nightText else OC.ink
    val fretLine = if (dark) Color.White.copy(alpha = 0.18f) else hex(0xD6D4CF)
    val stringCol = if (dark) Color.White.copy(alpha = 0.5f) else hex(0x8E8A84)
    val mutedCol = if (dark) OC.nightMuted else OC.muted
    val faint = OC.faint
    val context = LocalContext.current
    val density = LocalDensity.current
    val outfitFace = ResourcesCompat.getFont(context, R.font.outfit)
    val monoFace = ResourcesCompat.getFont(context, R.font.jetbrains_mono)
    val s = width.value / 260f
    val totalH = (22 + 6 + 46 * 4 + (if (showStringNames) 6 + 18 else 0)) * s
    val pattern = voicing.pattern
    val fingers = OC.finger
    val nightBg = OC.night

    Canvas(
        modifier.width(width).height(totalH.dp).semantics { contentDescription = "Chord diagram, pattern ${pattern.toCharArray().joinToString(" ")}" }
    ) {
        val px = density.density
        val sp = s * px
        fun x(i: Int) = (if (leftHanded) (250 - 48 * i) else (10 + 48 * i)) * sp
        val w = size.width
        val fretH = 46 * sp
        val gridTop = 28 * sp
        val gridH = fretH * 4
        val dot = 32 * sp
        val hairline = 0.75f * px
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        fun text(t: String, cx: Float, cy: Float, size: Float, color: Color, face: Typeface?, bold: Boolean = true) {
            paint.typeface = if (face != null) Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL) else Typeface.DEFAULT_BOLD
            paint.textSize = size
            paint.color = color.toArgbInt()
            val y = cy - (paint.descent() + paint.ascent()) / 2
            drawContext.canvas.nativeCanvas.drawText(t, cx, y, paint)
        }

        // open / muted markers
        for (i in 0 until 6) {
            val c = if (i < pattern.length) pattern[i] else '0'
            val mark = when (c) { 'x' -> "×"; '0' -> "○"; else -> "" }
            if (mark.isNotEmpty()) text(mark, x(i), 10 * sp, 15 * sp, if (c == 'x') faint else ink, outfitFace)
        }
        // nut
        drawRoundRect(if (voicing.baseFret > 1) fretLine else ink, topLeft = Offset(9 * sp, gridTop - 3 * sp), size = Size(w - 18 * sp, maxOf(6 * sp, 1.5f * px)), cornerRadius = CornerRadius(8 * sp))
        for (f in 1..4) {
            drawRect(fretLine, topLeft = Offset(10 * sp, gridTop + f * fretH - (if (f == 4) sp else 0f) - maxOf(2 * sp, hairline) / 2), size = Size(w - 20 * sp, maxOf(2 * sp, hairline)))
        }
        val widths = listOf(3f, 2.6f, 2.2f, 1.8f, 1.4f, 1.2f)
        for (i in 0 until 6) {
            val sw = maxOf(widths[i] * sp, hairline)
            drawRect(stringCol, topLeft = Offset(x(i) - sw / 2, gridTop), size = Size(sw, gridH))
        }
        voicing.barre?.let { b ->
            val a = minOf(x(b.from), x(b.to)); val z = maxOf(x(b.from), x(b.to))
            val cy = gridTop + (b.fret - 0.5f) * fretH
            drawRoundRect(fingers[1]!!, topLeft = Offset(a - dot / 2, cy - dot / 2), size = Size(z - a + dot, dot), cornerRadius = CornerRadius(dot / 2))
            if (showFingers) text("1", (a + z) / 2, cy, 15 * sp, Color.White, outfitFace)
        }
        for (d in voicing.dots) {
            val cx = x(d.string); val cy = gridTop + (d.fret - 0.5f) * fretH
            drawCircle(if (dark) nightBg else Color.White, radius = dot / 2, center = Offset(cx, cy))
            drawCircle(fingers[d.finger] ?: OC.inkFixed, radius = dot / 2 - 3 * sp, center = Offset(cx, cy))
            if (showFingers) text("${d.finger}", cx, cy, 15 * sp, Color.White, outfitFace)
        }
        if (voicing.baseFret > 1) text("${voicing.baseFret}fr", -8 * sp + 12 * sp, gridTop + 15 * sp, 11 * sp, faint, monoFace)
        if (showStringNames) {
            val ny = gridTop + gridH + 6 * sp + 9 * sp
            for (i in 0 until 6) text(names[i], x(i), ny, 12 * sp, mutedCol, monoFace)
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** Small chord card: name + mini diagram (Warm up, Keys, Song chord strip). */
@Composable
fun ChordCard(name: String, modifier: Modifier = Modifier, width: Dp = 96.dp, highlighted: Boolean = false, isEasySwap: Boolean = false) {
    Column(
        modifier.width(width).glass(16.dp).border(2.dp, if (highlighted) OC.gold else Color.Transparent, RoundedCornerShape(16.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = mono(17), color = if (isEasySwap) OC.teal else OC.goldText)
        val v = ChordLibrary.info(name)?.voicings?.firstOrNull()
        if (v != null) ChordDiagram(v, width = width - 20.dp, showFingers = false, showStringNames = false, modifier = Modifier.padding(top = 6.dp))
        else Box(Modifier.height(width * 0.8f), contentAlignment = Alignment.Center) { Text("?", style = mono(20), color = OC.faint) }
    }
}
