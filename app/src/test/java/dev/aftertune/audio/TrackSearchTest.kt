package dev.aftertune.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSearchTest {
    private val queue = listOf(
        QueueItem("u1", "smack-that", false),
        QueueItem("u2", "nowhere", false),
        QueueItem("u3", "Curiosity", false),
    )
    private val library = listOf(
        LibraryTrack("u1", "smack-that", artist = "Akon, Eminem", album = "Konvicted"),
        LibraryTrack("u2", "nowhere", artist = "Eminem"),
        LibraryTrack("u9", "Lose Yourself", artist = "Eminem", album = "8 Mile"),
        LibraryTrack("u8", "Sunflower", artist = "Post Malone"),
    )

    @Test fun punctuationIsSpace() {
        assertEquals(listOf(0), TrackSearch.queueMatches(queue, library, "smack that"))
    }

    @Test fun artistAndCaseMatch() {
        assertEquals(listOf(0, 1), TrackSearch.queueMatches(queue, library, "EMINEM"))
    }

    @Test fun allWordsMustMatch() {
        assertEquals(listOf(0), TrackSearch.queueMatches(queue, library, "akon konvicted"))
        assertTrue(TrackSearch.queueMatches(queue, library, "akon nowhere").isEmpty())
    }

    @Test fun emptyQueryKeepsQueue() {
        assertEquals(listOf(0, 1, 2), TrackSearch.queueMatches(queue, library, "  "))
        assertTrue(TrackSearch.libraryMatches(queue, library, "").isEmpty())
    }

    @Test fun libraryResultsExcludeQueued() {
        assertEquals(listOf("u9"), TrackSearch.libraryMatches(queue, library, "eminem").map { it.uri })
    }
}
