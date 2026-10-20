package dev.hd450btnc.companion.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadTest {
    private val fs = 48_000.0

    @Test
    fun zeroGainIsExactIdentity() {
        assertSame(BiquadCoefficients.IDENTITY, BiquadCoefficients.peaking(fs, 1000.0, 1.2, 0.0))
    }

    @Test
    fun peakingHitsRequestedGainAtCentre() {
        for (g in listOf(-12.0, -6.0, -0.5, 0.5, 3.0, 12.0)) {
            for (f in EqBands.FREQUENCIES) {
                val c = BiquadCoefficients.peaking(fs, f, EqBands.Q, g)
                assertEquals("f=$f g=$g", g, c.magnitudeDb(f, fs), 1e-6)
            }
        }
    }

    @Test
    fun peakingReturnsToUnityFarFromCentre() {
        val c = BiquadCoefficients.peaking(fs, 1000.0, EqBands.Q, 9.0)
        assertEquals(0.0, c.magnitudeDb(20.0, fs), 0.1)
        assertEquals(0.0, c.magnitudeDb(20_000.0, fs), 0.15)
    }

    @Test
    fun allBandDesignsAreStable() {
        for (rate in listOf(22_050.0, 44_100.0, 48_000.0, 96_000.0)) {
            for (f in EqBands.FREQUENCIES) for (g in -24..24) {
                val c = BiquadCoefficients.peaking(rate, f, EqBands.Q, g * 0.5)
                assertTrue("unstable fs=$rate f=$f g=$g", c.isStable || c.isIdentity)
            }
        }
    }

    @Test
    fun bandsAboveNyquistLimitAreBypassed() {
        // 16 kHz cannot be realised at 22.05 kHz sampling; it must degrade to identity, not misbehave.
        assertTrue(BiquadCoefficients.peaking(22_050.0, 16_000.0, EqBands.Q, 6.0).isIdentity)
    }
}
