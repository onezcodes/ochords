package com.onezcodes.ochords.services

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR with ML Kit's Latin recogniser: chords and English, works offline. Like Apple Vision it has no
 * Burmese model, so Myanmar lyrics come back as unreadable placeholders (the server reads those when signed in).
 */
class DeviceOCR : OCREngine {
    override val readsMyanmar: Boolean get() = false

    override suspend fun recognize(image: Bitmap): List<ScanTextLine> = read(image)

    companion object {
        private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

        /** Page-wide recognition, then every ink blob the page pass didn't cover becomes an unreadable placeholder, which keeps the layout. */
        suspend fun read(image: Bitmap): List<ScanTextLine> {
            val result = recognizer.process(InputImage.fromBitmap(image, 0)).await()
            val w = image.width.toDouble(); val h = image.height.toDouble()
            val lines = ArrayList<ScanTextLine>()
            for (block in result.textBlocks) for (line in block.lines) {
                val words = line.elements.mapNotNull { e ->
                    val b = e.boundingBox ?: return@mapNotNull null
                    val text = e.text.trim()
                    if (text.isEmpty()) null else ScanWord(text, Box(b.left / w, b.top / h, b.width() / w, b.height() / h))
                }
                val conf = line.confidence ?: 1f
                if (words.isNotEmpty() && conf >= 0.3f) lines.add(ScanTextLine(words, conf))
            }
            val ink = InkMap(image) ?: return lines
            val readWords = lines.flatMap { it.words }
            for (band in ink.bands()) {
                val blobs = ink.blobs(band)
                val missing = blobs.filter { blob ->
                    val b = ink.normalised(blob)
                    readWords.none { wd -> val i = wd.box.intersection(b); !i.isEmpty && i.width * i.height > 0.4 * b.width * b.height }
                }
                if (missing.isEmpty()) continue
                // Ink no word covers is text the recogniser doesn't know (Myanmar script): keep its place on the page.
                lines.add(ScanTextLine(missing.map { ScanWord(UNREADABLE_TEXT, ink.normalised(it)) }, 0f))
            }
            return lines.sortedBy { it.box.midY }
        }

        fun readBlocking(context: Context, image: Bitmap): List<ScanTextLine> = runBlocking { read(image) }

        /**
         * Adds the chords the server's reader skipped: ink that no returned word covers, in a chord row or in a row
         * without Myanmar text, is read again on its own; what's read is kept only if it's a chord name.
         */
        fun addMissedChords(lines: List<ScanTextLine>, image: Bitmap): List<ScanTextLine> {
            val ink = InkMap(image) ?: return lines
            val words = lines.flatMap { it.words }
            val added = ArrayList<ScanTextLine>()
            for (band in ink.bands()) {
                val top = band.first.toDouble() / ink.height; val bottom = band.second.toDouble() / ink.height
                val inBand = words.filter { it.box.midY > top && it.box.midY < bottom }
                val chordRow = inBand.any { ChordSheetParser.isChord(it.text) }
                val myanmar = inBand.filter { ChordSheetParser.isMyanmar(it.text) }
                val after: Double? = if (chordRow || myanmar.isEmpty()) null else (myanmar.maxOf { it.box.maxX } + 0.01)
                val blobs = ink.blobs(band)
                val span = (blobs.maxOfOrNull { it.maxX } ?: 0) - (blobs.minOfOrNull { it.minX } ?: 0)
                if (after == null && blobs.size >= 3 && blobs.sumOf { it.width } > span * 0.6 && !chordRow) continue
                val found = ArrayList<ScanWord>()
                for (blob in blobs) {
                    val b = ink.normalised(blob)
                    if (after != null && b.minX < after) continue
                    if (words.any { wd -> val i = wd.box.intersection(b); !i.isEmpty && i.width * i.height > 0.3 * b.width * b.height }) continue
                    val text = runBlocking { readAlone(image, ink.pixels(blob)) } ?: continue
                    if (ChordSheetParser.isChord(text)) found.add(ScanWord(text, b))
                }
                if (found.isNotEmpty()) added.add(ScanTextLine(found, 0.6f))
            }
            return (lines + added).sortedBy { it.box.midY }
        }

        /** Reads one cut-out of the page on its own (padded, enlarged so small letters register). */
        private suspend fun readAlone(image: Bitmap, rect: android.graphics.Rect): String? {
            val pad = (rect.height() * 0.6).toInt().coerceAtLeast(6)
            val x0 = (rect.left - pad).coerceAtLeast(0); val y0 = (rect.top - pad).coerceAtLeast(0)
            val x1 = (rect.right + pad).coerceAtMost(image.width); val y1 = (rect.bottom + pad).coerceAtMost(image.height)
            if (x1 - x0 < 4 || y1 - y0 < 4) return null
            var crop = Bitmap.createBitmap(image, x0, y0, x1 - x0, y1 - y0)
            if (crop.height < 64) crop = Bitmap.createScaledBitmap(crop, crop.width * 64 / crop.height, 64, true)
            val text = runCatching { recognizer.process(InputImage.fromBitmap(crop, 0)).await().text.trim() }.getOrNull() ?: return null
            return text.takeIf { it.isNotEmpty() && !it.contains('\n') }
        }
    }
}

/** Where the ink is on a page: dark pixels grouped into horizontal bands (rows) and blobs (words). */
class InkMap private constructor(private val dark: BooleanArray, val width: Int, val height: Int) {
    data class Blob(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val width get() = maxX - minX + 1
        val heightPx get() = maxY - minY + 1
    }

    companion object {
        operator fun invoke(image: Bitmap): InkMap? {
            val w = image.width; val h = image.height
            if (w < 16 || h < 16) return null
            val px = IntArray(w * h)
            image.getPixels(px, 0, w, 0, 0, w, h)
            val lum = IntArray(w * h) { val p = px[it]; (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000 }
            // Adaptive threshold: ink is clearly darker than the paper's median.
            val sample = lum.filterIndexed { i, _ -> i % 97 == 0 }.sorted()
            val paper = sample.getOrElse(sample.size * 3 / 4) { 200 }
            val threshold = (paper * 0.62).toInt()
            return InkMap(BooleanArray(w * h) { lum[it] < threshold }, w, h)
        }
    }

    /** Horizontal rows of ink (top, bottom) in pixels. */
    fun bands(): List<Pair<Int, Int>> {
        val rowInk = IntArray(height) { y -> var n = 0; val base = y * width; for (x in 0 until width) if (dark[base + x]) n++; n }
        val minInk = (width * 0.004).toInt().coerceAtLeast(2)
        val out = ArrayList<Pair<Int, Int>>()
        var start = -1
        var gap = 0
        val maxGap = (height * 0.004).toInt().coerceAtLeast(2)
        for (y in 0 until height) {
            if (rowInk[y] >= minInk) { if (start < 0) start = y; gap = 0 } else if (start >= 0) {
                gap++
                if (gap > maxGap) { if (y - gap - start >= 4) out.add(start to y - gap); start = -1; gap = 0 }
            }
        }
        if (start >= 0 && height - start >= 4) out.add(start to height)
        return out
    }

    /** Words in a band: runs of ink columns separated by gaps wider than a fraction of the band's height. */
    fun blobs(band: Pair<Int, Int>): List<Blob> {
        val (top, bottom) = band
        val colInk = IntArray(width) { x -> var n = 0; for (y in top until bottom) if (dark[y * width + x]) n++; n }
        val maxGap = ((bottom - top) * 0.45).toInt().coerceAtLeast(3)
        val out = ArrayList<Blob>()
        var start = -1; var gap = 0
        for (x in 0 until width) {
            if (colInk[x] > 0) { if (start < 0) start = x; gap = 0 } else if (start >= 0) {
                gap++
                if (gap > maxGap) { out.add(tight(start, x - gap, top, bottom)); start = -1; gap = 0 }
            }
        }
        if (start >= 0) out.add(tight(start, width, top, bottom))
        return out.filter { it.width >= 3 && it.heightPx >= 3 }
    }

    private fun tight(x0: Int, x1: Int, top: Int, bottom: Int): Blob {
        var minY = bottom; var maxY = top
        for (y in top until bottom) for (x in x0 until x1) if (dark[y * width + x]) { if (y < minY) minY = y; if (y > maxY) maxY = y; break }
        return Blob(x0, minY.coerceAtMost(bottom - 1), x1 - 1, maxY.coerceAtLeast(top))
    }

    fun normalised(b: Blob): Box = Box(b.minX.toDouble() / width, b.minY.toDouble() / height, b.width.toDouble() / width, b.heightPx.toDouble() / height)
    fun pixels(b: Blob): android.graphics.Rect = android.graphics.Rect(b.minX, b.minY, b.maxX + 1, b.maxY + 1)
}
