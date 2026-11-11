package com.onezcodes.ochords.services

import android.content.Context
import android.graphics.Bitmap
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Real sheet reading: photo/PDF → positioned text lines → `ChordSheetParser` → draft song. */
class OnDeviceScanService(private val context: Context, private val server: OCREngine? = null) : ScanService {
    private val device: OCREngine = DeviceOCR()
    /** Flatten the photo to the page edges before reading. */
    var cropToPage = true

    private data class Reading(var lines: List<ScanTextLine>, var readsMyanmar: Boolean, var onServer: Boolean, var raw: List<ScanTextLine> = emptyList())

    /** The server when it answers; otherwise the phone (guest, offline, no key on the server, daily limit). */
    private suspend fun read(page: Bitmap): Reading {
        if (server != null) {
            try {
                val lines = server.recognize(page)
                val completed = withContext(Dispatchers.Default) { DeviceOCR.addMissedChords(lines, page) }
                return Reading(completed, server.readsMyanmar, true, lines)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { coroutineContext.ensureActive() }
        }
        val lines = device.recognize(page)
        return Reading(lines, device.readsMyanmar, false, lines)
    }

    override suspend fun scan(source: ScanSource, progress: (ScanStage) -> Unit): ScanResult {
        progress(ScanStage.Page)
        var reading = Reading(emptyList(), false, false)
        var firstPage: Bitmap? = null
        var cropped = false

        when (source) {
            is ScanSource.Image -> {
                val bmp = withContext(Dispatchers.Default) { ScanImage.prepare(source.data) } ?: throw ServiceException.scanFailed
                val flat = if (cropToPage) withContext(Dispatchers.Default) { ScanImage.cropToPage(bmp) } else bmp
                cropped = flat !== bmp
                firstPage = flat
                coroutineContext.ensureActive()
                progress(ScanStage.Text)
                reading = read(flat)
                // The page-edge finder sometimes picks a shadow or a fold and crops the sheet away; read the whole photo then.
                if (cropped && ChordSheetParser.parse(reading.lines, reading.readsMyanmar).song.usedChords.size < 2) {
                    coroutineContext.ensureActive()
                    reading = read(bmp)
                    cropped = false
                    firstPage = bmp
                }
            }
            is ScanSource.Pdf -> {
                val pages = withContext(Dispatchers.IO) { runCatching { ScanPDF.render(context, source.data) }.getOrDefault(emptyList()) }
                if (pages.isEmpty()) throw ServiceException.scanFailed
                firstPage = pages.first()
                progress(ScanStage.Text)
                reading = Reading(emptyList(), server != null, false)
                pages.forEachIndexed { i, page ->
                    coroutineContext.ensureActive()
                    val r = read(page)
                    reading.readsMyanmar = reading.readsMyanmar && r.readsMyanmar
                    reading.onServer = r.onServer
                    // Stack pages: page i spans y = i…i+1.
                    reading.lines = reading.lines + r.lines.map { l -> ScanTextLine(l.words.map { ScanWord(it.text, it.box.offset(dy = i.toDouble())) }, l.confidence) }
                }
            }
        }

        coroutineContext.ensureActive()
        progress(ScanStage.Chords)
        val zawgyi = Zawgyi.isZawgyi(reading.lines.joinToString("\n") { it.text })
        val lines = if (zawgyi) reading.lines.map(::unicodeLine) else reading.lines
        val sheet = withContext(Dispatchers.Default) { ChordSheetParser.parse(lines, reading.readsMyanmar) }
        progress(ScanStage.Song)
        if (sheet.song.usedChords.isEmpty()) throw ServiceException.scanFailed
        val report = if (zawgyi) report(reading.lines, sheet.song, reading.readsMyanmar) else null
        val archived = if (source is ScanSource.Image && firstPage != null) {
            ScanReading(if (reading.onServer) ScanReading.Kind.Server else ScanReading.Kind.Device, reading.raw, reading.readsMyanmar, zawgyi, ScanImage.jpeg(firstPage))
        } else ScanReading(ScanReading.Kind.Lines, reading.lines, reading.readsMyanmar, zawgyi, null)

        return ScanResult(
            song = sheet.song, detectedZawgyi = zawgyi, lowConfidence = sheet.lowConfidence, chordNotes = sheet.chordNotes,
            needsLyrics = sheet.needsLyrics, thumbnail = firstPage?.let { ScanImage.thumbnail(it) }, regions = sheet.regions,
            linesRead = reading.lines.size, croppedToPage = cropped, readOnServer = reading.onServer, zawgyi = report, reading = archived,
        )
    }

    companion object {
        fun unicodeLine(line: ScanTextLine): ScanTextLine = ScanTextLine(line.words.map { ScanWord(Zawgyi.toUnicode(it.text), it.box) }, line.confidence)

        /** What changed, for the comparison screen: counts plus the title and first lyric line both ways. */
        private fun report(lines: List<ScanTextLine>, converted: Song, readsMyanmar: Boolean): ZawgyiReport {
            val scannedSong = ChordSheetParser.parse(lines, readsMyanmar).song
            val words = lines.flatMap { it.words }.map { it.text }.filter(ChordSheetParser::isMyanmar)
            val changed = words.map { it to Zawgyi.toUnicode(it) }.filter { it.first != it.second }
            val clean = if (changed.isEmpty()) 1.0 else changed.count { Zawgyi.markerCount(it.second) == 0 }.toDouble() / changed.size
            fun lyrics(song: Song): Pair<String, String>? {
                for (section in song.sections) {
                    val line = section.lines.firstOrNull { ChordSheetParser.isMyanmar(it) } ?: continue
                    return "${section.label} · LINE 1" to ChordLine.parse(line).joinToString(" ") { it.text }
                }
                return null
            }
            val preview = ArrayList<ZawgyiReport.Line>()
            if (ChordSheetParser.isMyanmar(scannedSong.title)) preview.add(ZawgyiReport.Line("TITLE", scannedSong.title, converted.title))
            val before = lyrics(scannedSong); val after = lyrics(converted)
            if (before != null && after != null) preview.add(ZawgyiReport.Line(before.first, before.second, after.second))
            return ZawgyiReport(scannedSong, changed.size, clean, preview)
        }
    }
}
