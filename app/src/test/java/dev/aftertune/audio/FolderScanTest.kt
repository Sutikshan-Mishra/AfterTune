package dev.aftertune.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanTest {
    @Test
    fun audioIsRecognisedByMimeOrExtension() {
        assertTrue(FolderScan.isAudio("audio/mpeg", "a.bin"))
        assertTrue(FolderScan.isAudio("application/octet-stream", "Song.FLAC"))
        assertTrue(FolderScan.isAudio(null, "x.opus"))
        assertFalse(FolderScan.isAudio("image/jpeg", "cover.mp3"))
        assertFalse(FolderScan.isAudio("application/octet-stream", "notes.txt"))
    }

    @Test
    fun titleDropsOnlyTheExtension() {
        assertEquals("Lose Yourself", FolderScan.titleOf("Lose Yourself.mp3"))
        assertEquals("v1.2 mix", FolderScan.titleOf("v1.2 mix.flac"))
        assertEquals("noext", FolderScan.titleOf("noext"))
    }
}
