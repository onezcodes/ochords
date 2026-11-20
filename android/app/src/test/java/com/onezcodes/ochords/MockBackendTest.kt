package com.onezcodes.ochords

import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.services.MockBackend
import com.onezcodes.ochords.services.MockData
import com.onezcodes.ochords.services.ServiceException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MockBackendTest {

    @Test
    fun signInRejectsEmptyEmailOrShortPassword() = runTest {
        val backend = MockBackend()
        try {
            backend.signIn("", "password")
            fail("expected invalid credentials")
        } catch (e: ServiceException) {
            assertEquals(ServiceException.Kind.InvalidCredentials, e.kind)
        }
        try {
            backend.signIn("you@example.com", "abc")
            fail("expected invalid credentials")
        } catch (e: ServiceException) {
            assertEquals(ServiceException.Kind.InvalidCredentials, e.kind)
        }
        val profile = backend.signIn("you@example.com", "pass")
        assertEquals("you@example.com", profile.email)
    }

    @Test
    fun exploreFiltersByTitleArtistOrUploader() = runTest {
        val backend = MockBackend()
        val all = backend.explore("")
        assertEquals(MockData.explore.size, all.size)

        val hits = backend.explore("စဉ်းစားပါ")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.song.title.contains("စဉ်းစားပါ") })

        val none = backend.explore("zzzz-not-a-song")
        assertTrue(none.isEmpty())
    }

    @Test
    fun saveLibraryAndDelete() = runTest {
        val backend = MockBackend()
        val before = backend.library()
        assertEquals(1, before.size)

        val extra = Song(
            id = "extra",
            title = "Extra",
            artist = "Someone",
            key = "G",
            sections = listOf(SongSection(kind = SectionKind.Verse, label = "VERSE 1", lines = listOf("[G]hi"))),
        )
        backend.save(extra)
        val mid = backend.library()
        assertEquals(2, mid.size)
        assertEquals("extra", mid.first().id)

        backend.delete("extra")
        val after = backend.library()
        assertEquals(1, after.size)
        assertFalse(after.any { it.id == "extra" })
    }
}
