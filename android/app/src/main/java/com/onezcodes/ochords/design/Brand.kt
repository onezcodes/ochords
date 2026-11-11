package com.onezcodes.ochords.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.R

/** The O Chords mark: gold ring (a guitar sound hole) crossed by three ink strings. */
@Composable
fun LogoMark(size: Dp = 30.dp, stringColor: Color = OC.ink, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 100f
        drawOval(OC.gold, topLeft = Offset(22 * s, 22 * s), size = Size(56 * s, 56 * s), style = Stroke(15 * s))
        for ((x, w) in listOf(40f to 4.6f, 50f to 4.2f, 60f to 3.8f)) {
            drawLine(stringColor, Offset(x * s, 16 * s), Offset(x * s, 84 * s), strokeWidth = w * s, cap = StrokeCap.Round)
        }
    }
}

/** Mark + "Chords" wordmark (the mark reads as the "O"). */
@Composable
fun Wordmark(size: Dp = 30.dp, color: Color = OC.ink, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "O Chords" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size * 0.1f)) {
        LogoMark(size, color)
        Text("Chords", style = outfit((size.value * 0.57f), FontWeight.Heavy).copy(letterSpacing = (-0.2).sp), color = color)
    }
}

/** App icon tile (white rounded square with the mark). */
@Composable
fun AppIconTile(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(modifier.size(size).shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f)).clip(shape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.08f), shape), contentAlignment = Alignment.Center) {
        LogoMark(size * 0.86f, OC.inkFixed)
    }
}

/** "POWERED BY" + Onez Codes logo (always on white). */
@Composable
fun PoweredByOnez(compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("POWERED BY", style = outfit(11, FontWeight.SemiBold, letterSpacing = 3.sp), color = OC.faint)
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(if (compact) 6.dp else 8.dp)) {
            Image(painterResource(R.drawable.onez_logo), contentDescription = "Onez Codes", modifier = Modifier.width(if (compact) 130.dp else 200.dp), contentScale = ContentScale.FillWidth)
        }
    }
}

/** "Ad" badge. */
@Composable
fun AdBadge(modifier: Modifier = Modifier) {
    Box(modifier.height(16.dp).clip(RoundedCornerShape(4.dp)).background(OC.goldTint).border(1.dp, hex(0xB8860B), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text("Ad", style = outfit(10, FontWeight.Heavy), color = OC.goldText)
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
