package dev.aftertune.artwork

import dev.aftertune.ui.components.formatBandReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkAccentTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun greyPictureHasNoAccent() {
        assertNull(ArtworkRules.accent(IntArray(400) { argb(120, 120, 120) }))
    }

    @Test
    fun darkPictureHasNoAccent() {
        assertNull(ArtworkRules.accent(IntArray(400) { argb(20, 5, 5) }))
    }

    @Test
    fun redPictureGivesBrightRed() {
        val a = ArtworkRules.accent(IntArray(400) { argb(150, 20, 20) })
        assertNotNull(a)
        val r = (a!! shr 16) and 0xFF
        val g = (a shr 8) and 0xFF
        val b = a and 0xFF
        assertTrue("red should dominate", r > g + 60 && r > b + 60)
        assertTrue("lifted to read on black", r >= 200)
    }

    @Test
    fun heaviestHueWins() {
        val px = IntArray(400) { if (it < 300) argb(30, 60, 200) else argb(200, 30, 30) }
        val a = ArtworkRules.accent(px)!!
        assertTrue(((a and 0xFF)) > ((a shr 16) and 0xFF))
    }

    @Test
    fun readoutFormatsGainAndFrequency() {
        assertEquals("+3.0 dB \u00b7 1 kHz", formatBandReadout(3f, 1000.0))
        assertEquals("\u22122.5 dB \u00b7 62 Hz", formatBandReadout(-2.5f, 62.0))
        assertEquals("0.0 dB \u00b7 16 kHz", formatBandReadout(0f, 16000.0))
    }
}
