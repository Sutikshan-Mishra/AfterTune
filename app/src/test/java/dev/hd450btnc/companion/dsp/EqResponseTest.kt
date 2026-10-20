package dev.hd450btnc.companion.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqResponseTest {
    @Test
    fun flatCurveIsZeroEverywhere() {
        val c = EqResponse.curve(EqBands.FLAT, 64)
        assertTrue(c.all { it == 0f })
        assertEquals(0f, EqResponse.autoPreampDb(EqBands.FLAT), 0f)
    }

    @Test
    fun logAxisEndpointsAndRoundTrip() {
        assertEquals(20.0, EqResponse.logFrequency(0, 100), 1e-9)
        assertEquals(20_000.0, EqResponse.logFrequency(99, 100), 1e-6)
        assertEquals(0.5, EqResponse.logPosition(EqResponse.logFrequency(50, 101)), 1e-9)
    }

    @Test
    fun singleBandResponseMatchesSlider() {
        val gains = EqBands.FLAT.toMutableList().also { it[5] = 6f }
        assertEquals(6.0, EqResponse.responseDb(gains, 1000.0), 1e-6)
    }

    @Test
    fun overlappingBoostsAreCoveredByAutoPreamp() {
        // Neighbouring boosts sum above the individual slider values; headroom must account for that.
        val gains = listOf(12f, 12f, 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val peak = EqResponse.peakDb(gains)
        assertTrue("overlap should exceed 12 dB, was $peak", peak > 12.0)
        val pre = EqResponse.autoPreampDb(gains)
        assertTrue(pre <= -peak.toFloat())
    }

    @Test
    fun cutOnlyCurveNeedsNoPreamp() {
        val gains = List(EqBands.COUNT) { -6f }
        assertEquals(0f, EqResponse.autoPreampDb(gains), 0f)
    }

    @Test
    fun gainNormalisationClampsAndSteps() {
        assertEquals(12f, EqBands.normalizeGain(40f), 0f)
        assertEquals(-12f, EqBands.normalizeGain(-13f), 0f)
        assertEquals(1.5f, EqBands.normalizeGain(1.4f), 0f)
        assertEquals(0f, EqBands.normalizeGain(Float.NaN), 0f)
        assertEquals(10, EqBands.normalizeGains(listOf(1f)).size)
        assertEquals("+3.5", EqBands.formatGain(3.5f))
        assertEquals("−6", EqBands.formatGain(-6f))
        assertEquals("0", EqBands.formatGain(-0.1f))
    }
}
