package com.onezcodes.ochords.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Bitmap helpers for sheet reading (the iOS `ScanImage`). */
object ScanImage {
    /** Upright image at most `maxSide` pixels on its longest side (EXIF rotation applied). */
    fun prepare(data: ByteArray, maxSide: Int = 2600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching { ExifInterface(data.inputStream()).rotationDegrees }.getOrDefault(0)
        val scale = min(1f, maxSide.toFloat() / max(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); if (rotation != 0) postRotate(rotation.toFloat()) }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        return out.toSoftware()
    }

    private fun Bitmap.toSoftware(): Bitmap = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

    /** The four corners of a sheet in a photo, as fractions of its size with the origin at the top left. */
    data class PageCorners(val topLeft: PointF, val topRight: PointF, val bottomRight: PointF, val bottomLeft: PointF) {
        val all: List<PointF> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

        /** A proper four-sided outline (no corner dragged across another), which flattening needs. */
        val isConvex: Boolean
            get() {
                val p = all
                var sign = 0.0
                for (i in 0 until 4) {
                    val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
                    val cross = ((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)).toDouble()
                    if (kotlin.math.abs(cross) <= 1e-6) return false
                    if (sign == 0.0) sign = cross else if ((cross > 0) != (sign > 0)) return false
                }
                return true
            }

        companion object { val whole = PageCorners(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)) }
    }

    data class PointF(val x: Float, val y: Float)

    /**
     * Where the sheet of paper is, if a clear page edge is found (null otherwise). Android has no document
     * segmentation API, so this looks for the largest bright region against a darker surround by scanning the
     * image's brightness profile from each edge inward.
     */
    fun detectPage(image: Bitmap): PageCorners? {
        val w = image.width; val h = image.height
        val step = max(1, max(w, h) / 200)
        val cols = w / step; val rows = h / step
        if (cols < 8 || rows < 8) return null
        val lum = IntArray(cols * rows)
        val px = IntArray(w)
        for (r in 0 until rows) {
            image.getPixels(px, 0, w, 0, r * step, w, 1)
            for (c in 0 until cols) {
                val p = px[c * step]
                lum[r * cols + c] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            }
        }
        val sorted = lum.sortedArray()
        val bright = sorted[(sorted.size * 0.85).toInt()]
        val dark = sorted[(sorted.size * 0.15).toInt()]
        if (bright - dark < 40) return null
        val threshold = (bright + dark) / 2
        fun rowBright(r: Int) = (0 until cols).count { lum[r * cols + it] > threshold } > cols * 0.5
        fun colBright(c: Int) = (0 until rows).count { lum[it * cols + c] > threshold } > rows * 0.5
        var top = 0; while (top < rows && !rowBright(top)) top++
        var bottom = rows - 1; while (bottom > top && !rowBright(bottom)) bottom--
        var left = 0; while (left < cols && !colBright(left)) left++
        var right = cols - 1; while (right > left && !colBright(right)) right--
        if (bottom <= top || right <= left) return null
        val area = ((bottom - top).toFloat() / rows) * ((right - left).toFloat() / cols)
        if (area <= 0.2f || area >= 0.98f) return null
        val x0 = left.toFloat() / cols; val x1 = (right + 1).toFloat() / cols
        val y0 = top.toFloat() / rows; val y1 = (bottom + 1).toFloat() / rows
        return PageCorners(PointF(x0, y0), PointF(x1, y0), PointF(x1, y1), PointF(x0, y1))
    }

    /** The area inside `corners`, straightened into a rectangle (perspective correction). */
    fun flatten(image: Bitmap, corners: PageCorners): Bitmap? {
        val w = image.width.toFloat(); val h = image.height.toFloat()
        val src = floatArrayOf(corners.topLeft.x * w, corners.topLeft.y * h, corners.topRight.x * w, corners.topRight.y * h,
            corners.bottomRight.x * w, corners.bottomRight.y * h, corners.bottomLeft.x * w, corners.bottomLeft.y * h)
        val outW = ((dist(src, 0, 2) + dist(src, 6, 4)) / 2).toInt().coerceAtLeast(1)
        val outH = ((dist(src, 0, 6) + dist(src, 2, 4)) / 2).toInt().coerceAtLeast(1)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
        return out
    }

    private fun dist(p: FloatArray, a: Int, b: Int): Float = kotlin.math.hypot(p[a] - p[b], p[a + 1] - p[b + 1])

    /** Finds the sheet of paper and flattens it. Returns the input when no clear page edge is found. */
    fun cropToPage(image: Bitmap): Bitmap = detectPage(image)?.let { flatten(image, it) } ?: image

    /** The image turned a quarter turn clockwise. */
    fun rotatedClockwise(image: Bitmap): Bitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)

    /** The image rotated by `degrees` (small angles, for levelling), white behind. */
    fun rotated(image: Bitmap, degrees: Float): Bitmap {
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); rotate(degrees, image.width / 2f, image.height / 2f); drawBitmap(image, 0f, 0f, null) }
        return out
    }

    fun scaled(image: Bitmap, maxSide: Int): Bitmap {
        val scale = min(1f, maxSide.toFloat() / max(image.width, image.height))
        if (scale >= 1f) return image
        return Bitmap.createScaledBitmap(image, (image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1), true)
    }

    /** Scales the page down and encodes it as JPEG. */
    fun jpeg(image: Bitmap, maxSide: Int = 2000, quality: Int = 80): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (scaled(image, maxSide).compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
    }

    fun thumbnail(image: Bitmap, maxSide: Int = 520): ByteArray? = jpeg(image, maxSide, 70)
}

/** PDF pages, via the platform renderer. */
object ScanPDF {
    const val maxPages = 4

    /** Renders each page (up to `maxPages`) for OCR. Android's renderer gives no text layer, so typed PDFs are read like photos. */
    fun render(context: Context, data: ByteArray, maxSide: Int = 2400, pages: Int = maxPages): List<Bitmap> {
        val tmp = File.createTempFile("sheet", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(data)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    return (0 until min(renderer.pageCount, pages)).map { i ->
                        renderer.openPage(i).use { page ->
                            val scale = maxSide.toFloat() / max(page.width, page.height)
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } finally { tmp.delete() }
    }
}
