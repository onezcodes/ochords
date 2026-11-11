package com.onezcodes.ochords.features.importsheet

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.NightBackground
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.services.ScanImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Crop a picked photo to the sheet before it's read: four corners to drag (starting on the page edges the app
 * detects), a quarter-turn for sideways photos, and the result straightened like a scan.
 */
@Composable
fun ImportCropView(
    image: Bitmap,
    onCancel: () -> Unit,
    /** The straightened sheet. */
    onDone: (Bitmap) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var photo by remember(image) { mutableStateOf(image) }
    var detected by remember(image) { mutableStateOf(ScanImage.detectPage(image)) }
    var corners by remember(image) { mutableStateOf(detected ?: cropInset) }
    /** The corner being dragged (for its larger handle). */
    var dragging by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    val bitmap: ImageBitmap = remember(photo) { photo.asImageBitmap() }

    val isWhole = corners == ScanImage.PageCorners.whole || corners == cropInset

    /** Moves a corner, staying on the photo and keeping the outline a proper four-sided shape. */
    fun move(i: Int, p: ScanImage.PointF) {
        val clamped = ScanImage.PointF(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
        val next = when (i) {
            0 -> corners.copy(topLeft = clamped)
            1 -> corners.copy(topRight = clamped)
            2 -> corners.copy(bottomRight = clamped)
            else -> corners.copy(bottomLeft = clamped)
        }
        if (next.isConvex) corners = next
    }

    /** A quarter turn clockwise; the page is looked for again in the turned photo. */
    fun rotate() {
        if (busy) return
        busy = true
        scope.launch {
            val turned = withContext(Dispatchers.Default) { ScanImage.rotatedClockwise(photo) }
            val page = withContext(Dispatchers.Default) { ScanImage.detectPage(turned) }
            photo = turned
            detected = page
            corners = page ?: cropInset
            busy = false
        }
    }

    fun finish() {
        if (busy) return
        busy = true
        scope.launch {
            // The whole photo needs no straightening; otherwise flatten what's inside the corners.
            val result = if (corners == ScanImage.PageCorners.whole) photo
            else withContext(Dispatchers.Default) { ScanImage.flatten(photo, corners) } ?: photo
            busy = false
            onDone(result)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NightBackground()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = outfit(16, FontWeight.SemiBold), color = OC.nightText,
                    modifier = Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp).plainClickable(onClick = onCancel).wrapContentHeight(Alignment.CenterVertically))
                Spacer(Modifier.weight(1f))
                Text("Crop the sheet", style = outfit(17, FontWeight.Bold), color = OC.nightText)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isWhole) (if (detected == null) "Reset" else "Detect page") else "Whole photo",
                    style = outfit(14, FontWeight.SemiBold), color = OC.gold,
                    modifier = Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp)
                        .plainClickable { corners = if (isWhole) (detected ?: cropInset) else ScanImage.PageCorners.whole }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }

            Text(
                if (detected == null) "Drag the corners to the edges of the sheet" else "We found the page — drag a corner to adjust",
                style = outfit(13), color = OC.nightMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val frame = fitted(photo.width.toFloat(), photo.height.toFloat(), boxW, boxH)
                val grab = with(density) { 44.dp.toPx() }
                val handle = with(density) { 26.dp.toPx() }
                fun onScreen(p: ScanImage.PointF) = Offset(frame.left + p.x * frame.width, frame.top + p.y * frame.height)

                Canvas(
                    Modifier.fillMaxSize()
                        .semantics { contentDescription = "Photo with the sheet outlined; drag a corner to the edge of the sheet" }
                        .pointerInput(frame, photo) {
                            detectDragGestures(
                                onDragStart = { at ->
                                    // The nearest handle within reach starts dragging.
                                    dragging = corners.all.map { onScreen(it) }.withIndex()
                                        .filter { (_, c) -> hypot(c.x - at.x, c.y - at.y) <= grab }
                                        .minByOrNull { (_, c) -> hypot(c.x - at.x, c.y - at.y) }?.index ?: -1
                                },
                                onDragEnd = { dragging = -1 },
                                onDragCancel = { dragging = -1 },
                            ) { change, _ ->
                                val i = dragging
                                if (i < 0) return@detectDragGestures
                                change.consume()
                                move(i, ScanImage.PointF((change.position.x - frame.left) / frame.width, (change.position.y - frame.top) / frame.height))
                            }
                        },
                ) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
                        dstSize = IntSize(frame.width.toInt().coerceAtLeast(1), frame.height.toInt().coerceAtLeast(1)),
                    )
                    // The sheet outlined in gold, everything outside it dimmed.
                    val points = corners.all.map { onScreen(it) }
                    val sheet = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (k in 1 until 4) lineTo(points[k].x, points[k].y)
                        close()
                    }
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(frame)
                        addPath(sheet)
                    }
                    drawPath(outside, Color.Black.copy(alpha = 0.55f))
                    drawPath(sheet, OC.gold, style = Stroke(width = 2.dp.toPx()))
                    points.forEachIndexed { i, p ->
                        val d = if (dragging == i) handle * 1.4f else handle
                        drawCircle(Color.Black.copy(alpha = 0.25f), radius = d / 2 + 3.dp.toPx(), center = p + Offset(0f, 2.dp.toPx()))
                        drawCircle(OC.gold, radius = d / 2, center = p)
                        drawCircle(Color.White, radius = d / 2 - 1.dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).heightIn(min = 52.dp).border(1.dp, hex(0x4A4743), RoundedCornerShape(16.dp))
                        .plainClickable(enabled = !busy) { rotate() }.alpha(if (busy) 0.6f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = null, tint = OC.nightText, modifier = Modifier.size(20.dp))
                    Text("Rotate", style = outfit(15, FontWeight.SemiBold), color = OC.nightText)
                }
                GoldButton("Scan", Modifier.weight(1f), height = 52.dp, enabled = !busy,
                    leading = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(20.dp)) }) { finish() }
            }
        }
    }
}

/** Without a detected page: nearly the whole photo, so the corners are easy to grab. */
private val cropInset = ScanImage.PageCorners(
    ScanImage.PointF(0.04f, 0.04f), ScanImage.PointF(0.96f, 0.04f), ScanImage.PointF(0.96f, 0.96f), ScanImage.PointF(0.04f, 0.96f),
)

/** Where the photo sits on screen (aspect-fit). */
private fun fitted(w: Float, h: Float, boxW: Float, boxH: Float): Rect {
    val scale = minOf(boxW / w, boxH / h)
    val fit = Size(w * scale, h * scale)
    return Rect(Offset((boxW - fit.width) / 2, (boxH - fit.height) / 2), fit)
}
