package dev.hd450btnc.companion.artwork

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun blackPictureIsNotUsable() {
        val s = ArtworkRules.stats(IntArray(100) { argb(0, 0, 0) })
        assertFalse(ArtworkRules.usable(s))
    }

    @Test
    fun flatGreyPictureIsNotUsable() {
        assertFalse(ArtworkRules.usable(ArtworkRules.stats(IntArray(100) { argb(120, 120, 120) })))
    }

    @Test
    fun colourfulPictureIsUsable() {
        val px = IntArray(100) { if (it % 2 == 0) argb(200, 40, 60) else argb(30, 90, 220) }
        assertTrue(ArtworkRules.usable(ArtworkRules.stats(px)))
    }

    @Test
    fun darkArtIsRestrainedAndStrengthIsBounded() {
        val dark = ArtworkRules.strength(ArtworkRules.Stats(0.08f, 0.1f))
        val bright = ArtworkRules.strength(ArtworkRules.Stats(0.9f, 0.1f))
        assertTrue(dark in 0.4f..1f)
        assertTrue(bright <= 1f && bright >= dark)
    }

    @Test
    fun staleResultsAreRejected() {
        val s = ArtworkSession()
        val first = s.begin()
        assertTrue(s.isCurrent(first))
        val second = s.begin() // rapid track change
        assertFalse(s.isCurrent(first))
        assertTrue(s.isCurrent(second))
        s.begin() // queue cleared / stopped
        assertFalse(s.isCurrent(second))
    }
}
