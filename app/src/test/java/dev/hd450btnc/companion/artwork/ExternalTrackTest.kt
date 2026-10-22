package dev.hd450btnc.companion.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTrackTest {
    private fun track(position: Long = 10_000, playing: Boolean = true, duration: Long = 200_000, speed: Float = 1f) =
        ExternalTrack("pkg", "Song", "Artist", duration, position, updatedAtMs = 1_000, speed = speed, playing = playing)

    @Test
    fun positionAdvancesWhilePlaying() {
        assertEquals(15_000L, track().positionAt(6_000))
    }

    @Test
    fun positionHoldsWhilePaused() {
        assertEquals(10_000L, track(playing = false).positionAt(60_000))
    }

    @Test
    fun speedScalesAdvance() {
        assertEquals(20_000L, track(speed = 2f).positionAt(6_000))
    }

    @Test
    fun positionIsClampedToDuration() {
        assertEquals(200_000L, track(position = 199_000).positionAt(60_000))
    }

    @Test
    fun unknownPositionStaysUnknown() {
        assertEquals(-1L, track(position = -1).positionAt(5_000))
        assertEquals(-1f, track(position = -1).fractionAt(5_000), 0f)
        assertEquals(-1f, track(duration = 0).fractionAt(5_000), 0f)
    }

    @Test
    fun fractionIsProgress() {
        assertEquals(0.5f, track(position = 100_000, playing = false).fractionAt(0), 0.001f)
    }

    @Test
    fun clockGoingBackwardsNeverRewinds() {
        assertEquals(10_000L, track().positionAt(0))
    }

    @Test
    fun meterFiresOncePerTrackAfterThreshold() {
        val m = ListenMeter(30_000)
        assertFalse(m.update("a", true, 0))
        assertFalse(m.update("a", true, 20_000))
        assertTrue(m.update("a", true, 31_000))
        assertFalse(m.update("a", true, 90_000))
    }

    @Test
    fun meterIgnoresPausedTime() {
        val m = ListenMeter(30_000)
        m.update("a", true, 0)
        assertFalse(m.update("a", false, 10_000))
        assertFalse(m.update("a", false, 500_000)) // paused for a long time: nothing accumulates
        assertFalse(m.update("a", true, 500_000))
        assertFalse(m.update("a", true, 515_000)) // 10 s + 15 s = 25 s
        assertTrue(m.update("a", true, 521_000))
    }

    @Test
    fun meterResetsOnNewTrack() {
        val m = ListenMeter(30_000)
        m.update("a", true, 0)
        assertFalse(m.update("b", true, 25_000))
        assertTrue(m.update("b", true, 56_000))
    }

    @Test
    fun meterWithNoTrackNeverFires() {
        val m = ListenMeter(1)
        assertFalse(m.update(null, true, 100))
    }
}
