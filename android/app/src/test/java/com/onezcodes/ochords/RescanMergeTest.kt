package com.onezcodes.ochords

import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.services.Rescan
import com.onezcodes.ochords.services.ScannedParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RescanMergeTest {

    private fun section(label: String, vararg lines: String) =
        SongSection(kind = SectionKind.Verse, label = label, lines = lines.toList())

    private fun song(title: String, artist: String, key: String, vararg sections: SongSection) =
        Song(id = "s1", title = title, artist = artist, key = key, sections = sections.toList())

    @Test
    fun uneditedFieldsTakeNewScannerValues() {
        val current = song("Old Title", "Old Artist", "C", section("VERSE 1", "[C]hello"))
        val newer = song("New Title", "New Artist", "G", section("VERSE 1", "[G]hello"))
        val scanned = ScannedParts(current)
        val outcome = Rescan.merge(newer, current, scanned)
        assertNotNull(outcome.updated)
        assertEquals("New Title", outcome.updated!!.title)
        assertEquals("New Artist", outcome.updated.artist)
        assertEquals("G", outcome.updated.key)
        assertEquals(newer.sections.map { it.label }, outcome.updated.sections.map { it.label })
        assertNull(outcome.offered)
    }

    @Test
    fun playerEditedTitleKeptAndDivergedSectionsOffered() {
        val original = song("Scan Title", "Scan Artist", "C", section("VERSE 1", "[C]hello"))
        val scanned = ScannedParts(original)
        // Player renamed the song and rewrote the verse.
        val current = song("My Title", "Scan Artist", "C", section("VERSE 1", "[C]changed"))
        val newer = song("Better Title", "Better Artist", "G", section("VERSE 1", "[G]fresh"))

        val outcome = Rescan.merge(newer, current, scanned)
        assertNotNull(outcome.updated)
        // Title was edited by the player — keep it.
        assertEquals("My Title", outcome.updated!!.title)
        // Artist/key still matched the original scan — take the new reading.
        assertEquals("Better Artist", outcome.updated.artist)
        assertEquals("G", outcome.updated.key)
        // Sections diverged from the original scan → offer the new ones.
        assertNotNull(outcome.offered)
        assertEquals(listOf("[G]fresh"), outcome.offered!!.sections[0].lines)
        // Current sections stay until the player accepts the offer.
        assertEquals(listOf("[C]changed"), outcome.updated.sections[0].lines)
    }

    @Test
    fun identicalReadingYieldsNullUpdated() {
        val current = song("Title", "Artist", "C", section("VERSE 1", "[C]hello"))
        val scanned = ScannedParts(current)
        val newer = song("Title", "Artist", "C", section("VERSE 1", "[C]hello"))
        val outcome = Rescan.merge(newer, current, scanned)
        assertNull(outcome.updated)
        assertNull(outcome.offered)
    }
}
