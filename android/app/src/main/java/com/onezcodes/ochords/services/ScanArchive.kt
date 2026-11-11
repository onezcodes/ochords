package com.onezcodes.ochords.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File

/** The parts of a song the scanner produces, to tell what the player changed afterwards. */
@Serializable
data class ScannedParts(val title: String, val artist: String, val key: String, val sections: List<Section>) {
    /** A section without its id (ids are new each time a sheet is read). */
    @Serializable
    data class Section(val kind: SectionKind, val label: String, val note: String?, val lines: List<String>, val bars: List<String>, val strumPattern: String?)

    constructor(song: Song) : this(song.title, song.artist, song.key, song.sections.filter { it.kind != SectionKind.Flow }.map {
        Section(it.kind, it.label, it.note, it.lines, it.bars, it.strumPattern)
    })
}

/** Reading a song's sheet again with the current scanner, and folding the result into the song the player has. */
object Rescan {
    /** The scanner's reading logic version. **Raise it whenever reading or parsing improves.** */
    const val scannerVersion = 2

    /** The sheet as the current scanner reads it, or null if it finds no chords. */
    fun read(context: Context, reading: ScanReading): Song? {
        var lines = reading.lines
        val page = reading.page?.let { image(it) }
        if (page != null) {
            when (reading.kind) {
                ScanReading.Kind.Server -> lines = DeviceOCR.addMissedChords(lines, page)
                ScanReading.Kind.Device -> lines = runCatching { DeviceOCR.readBlocking(context, page) }.getOrDefault(lines)
                ScanReading.Kind.Lines -> {}
            }
        }
        if (Zawgyi.isZawgyi(lines.joinToString("\n") { it.text })) lines = lines.map(OnDeviceScanService::unicodeLine)
        val song = ChordSheetParser.parse(lines, readsMyanmar = reading.readsMyanmar).song
        return if (song.usedChords.isEmpty()) null else song
    }

    data class Outcome(val updated: Song?, val offered: Song?)

    /** Folds a new reading into the player's song, part by part. */
    fun merge(new: Song, current: Song, scanned: ScannedParts): Outcome {
        val mine = ScannedParts(current); val fresh = ScannedParts(new)
        var song = current
        if (mine.title == scanned.title && new.title.isNotEmpty()) song = song.copy(title = new.title)
        if (mine.artist == scanned.artist) song = song.copy(artist = new.artist)
        if (mine.key == scanned.key) song = song.copy(key = new.key)
        var offered: Song? = null
        if (fresh.sections != mine.sections) {
            if (mine.sections == scanned.sections) song = song.copy(sections = new.sections) else offered = song.copy(sections = new.sections)
        }
        return Outcome(updated = if (ScannedParts(song) == mine) null else song, offered = offered)
    }

    fun image(data: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(data, 0, data.size)

    fun jpeg(image: Bitmap, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}

/** Each scanned song's reading, on this phone only (app-private files). Deleted with the song, and with an account's songs on sign-out. */
class ScanArchive(context: Context) {
    @Serializable
    data class Entry(val reading: ScanReading, val scanned: ScannedParts, val version: Int, val offered: Song?)

    private val directory = File(context.filesDir, "Scans")
    private fun file(songID: String) = File(directory, songID.replace("/", "_") + ".json")

    fun save(entry: Entry, songID: String) {
        directory.mkdirs()
        val tmp = File(directory, "${file(songID).name}.tmp")
        tmp.writeText(json.encodeToString(Entry.serializer(), entry))
        tmp.renameTo(file(songID))
    }

    fun entry(songID: String): Entry? {
        val f = file(songID)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Entry.serializer(), f.readText()) }.getOrNull()
    }

    /** Song IDs with a stored reading. */
    val songIDs: List<String> get() = directory.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension } ?: emptyList()

    fun remove(songID: String) { file(songID).delete() }

    /** Signing out: an account's readings go; a guest's (still on the phone) stay. */
    fun removeAll(except: Set<String>) { songIDs.filter { it !in except }.forEach(::remove) }
}
