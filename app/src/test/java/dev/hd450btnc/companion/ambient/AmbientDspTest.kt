package dev.hd450btnc.companion.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AmbientDspTest {
    private val fs = 48_000

    @Test
    fun gainMappingIsBoundedLinearInDbAndRoundTrips() {
        assertEquals(AmbientGain.MIN_DB, AmbientGain.sliderToDb(0f), 0f)
        assertEquals(AmbientGain.MAX_DB, AmbientGain.sliderToDb(1f), 0f)
        assertEquals(AmbientGain.MAX_DB, AmbientGain.sliderToDb(7f), 0f)
        assertEquals(AmbientGain.MIN_DB, AmbientGain.sliderToDb(-1f), 0f)
        for (db in listOf(-30f, -6f, 0f, 3.5f, 12f)) assertEquals(db, AmbientGain.sliderToDb(AmbientGain.dbToSlider(db)), 0f)
        assertEquals(1f, AmbientGain.dbToLinear(0f), 1e-6f)
        assertEquals(0.5012f, AmbientGain.dbToLinear(-6f), 1e-3f)
        assertEquals(12f, AmbientGain.clampDb(40f), 0f)
        assertEquals(AmbientGain.DEFAULT_DB, AmbientGain.clampDb(Float.NaN), 0f)
        assertTrue("default must be conservative", AmbientGain.DEFAULT_DB <= 0f)
    }

    @Test
    fun gainLabelsAreAccurate() {
        assertEquals("−6.0 dB", AmbientGain.label(-6f, false))
        assertEquals("+3.5 dB", AmbientGain.label(3.5f, false))
        assertEquals("0.0 dB", AmbientGain.label(0f, false))
        assertEquals("Muted", AmbientGain.label(3f, true))
    }

    @Test
    fun limiterNeverExceedsCeilingAndLeavesQuietSignalAlone() {
        val lim = PeakLimiter(fs)
        for (n in 0 until fs) {
            val y = lim.process((4.0 * sin(2 * PI * 300 * n / fs)).toFloat())
            assertTrue(abs(y) <= PeakLimiter.CEILING + 1e-6f)
        }
        val quiet = PeakLimiter(fs)
        for (n in 0 until 1000) {
            val x = (0.2 * sin(2 * PI * 300 * n / fs)).toFloat()
            assertEquals(x, quiet.process(x), 0f)
        }
    }

    @Test
    fun limiterRecoversAfterLoudBurst() {
        val lim = PeakLimiter(fs)
        repeat(100) { lim.process(3f) }
        assertTrue(lim.gain < 0.3f)
        repeat(fs) { lim.process(0.1f) }
        assertTrue("gain should recover, was ${lim.gain}", lim.gain > 0.99f)
    }

    @Test
    fun dcBlockerRemovesOffset() {
        val dc = DcBlocker(fs)
        var y = 1f
        repeat(fs) { y = dc.process(0.5f) }
        assertTrue(abs(y) < 1e-3f)
    }

    private fun tone(amp: Double, n: Int, freq: Double = 1000.0) =
        ShortArray(n) { (amp * 32767 * sin(2 * PI * freq * it / fs)).toInt().toShort() }

    @Test
    fun processorFadesInFromSilenceAndReachesTargetGain() {
        val p = AmbientProcessor(fs)
        p.setGainDb(0f)
        val buf = tone(0.25, fs)
        p.process(buf, buf.size)
        assertEquals(0, buf[0].toInt()) // starts from silence
        var late = 0
        for (i in fs / 2 until fs) late = maxOf(late, abs(buf[i].toInt()))
        assertEquals(0.25 * 32767, late.toDouble(), 0.25 * 32767 * 0.03)
        assertEquals(1f, p.currentGain, 1e-4f)
    }

    @Test
    fun gainChangesAreRampedNotStepped() {
        val p = AmbientProcessor(fs)
        p.setGainDb(-30f)
        p.process(tone(0.1, fs), fs)
        p.setGainDb(12f)
        val buf = tone(0.1, 480) // 10 ms
        p.process(buf, buf.size)
        assertTrue("gain jumped instantly", p.currentGain < AmbientGain.dbToLinear(12f) * 0.9f)
    }

    @Test
    fun muteRampsToSilenceAndMaxGainStaysUnderCeiling() {
        val p = AmbientProcessor(fs)
        p.setGainDb(12f)
        val loud = tone(0.9, fs)
        p.process(loud, loud.size)
        assertTrue(loud.all { abs(it.toInt()) <= (PeakLimiter.CEILING * 32767).toInt() + 1 })
        p.setMuted(true)
        val buf = tone(0.5, fs / 2)
        p.process(buf, buf.size)
        // Ramp from +12 dB reaches true silence within ~320 ms (30 ms time constant, -80 dB snap).
        for (i in (fs * 0.35).toInt() until buf.size) assertEquals(0, buf[i].toInt())
        assertTrue(p.inputPeakDbfs > -10f)
    }

    @Test
    fun dbfsConversion() {
        assertEquals(AmbientProcessor.SILENCE_DB, AmbientProcessor.toDbfs(0f), 0f)
        assertEquals(0f, AmbientProcessor.toDbfs(1f), 1e-5f)
        assertEquals(-6.02f, AmbientProcessor.toDbfs(0.5f), 0.01f)
    }
}
