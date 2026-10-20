package dev.hd450btnc.companion.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EqEngineTest {
    private val fs = 48_000

    private fun stereoSine(freq: Double, amp: Double, frames: Int, startFrame: Int = 0) = FloatArray(frames * 2) { i ->
        val n = startFrame + i / 2
        (amp * sin(2 * PI * freq * n / fs)).toFloat()
    }

    private fun peak(buf: FloatArray, fromFrame: Int): Float {
        var p = 0f
        for (i in fromFrame * 2 until buf.size) p = maxOf(p, abs(buf[i]))
        return p
    }

    private fun engine(params: EqParams) = EqEngine().apply {
        setParams(params)
        configure(fs, 2)
    }

    @Test
    fun neutralIsBitExactPassthrough() {
        val e = engine(EqParams.FLAT)
        val input = stereoSine(440.0, 0.8, 4096)
        val buf = input.copyOf()
        e.process(buf, 0, 4096)
        assertTrue(input.contentEquals(buf))
    }

    @Test
    fun boostAtBandCentreRaisesLevelBySliderAmount() {
        val gains = EqBands.FLAT.toMutableList().also { it[5] = 6f }
        val e = engine(EqParams(true, gains, 0f))
        val frames = fs // 1 s; measure after filter settles
        val buf = stereoSine(1000.0, 0.25, frames)
        e.process(buf, 0, frames)
        val ratioDb = 20 * kotlin.math.log10(peak(buf, fs / 2) / 0.25)
        assertEquals(6.0, ratioDb, 0.05)
    }

    @Test
    fun bypassReturnsToDrySignal() {
        val gains = List(EqBands.COUNT) { 6f }
        val e = engine(EqParams(true, gains, -6f))
        e.setParams(EqParams(false, gains, -6f))
        val frames = fs / 2
        val input = stereoSine(1000.0, 0.5, frames)
        val buf = input.copyOf()
        e.process(buf, 0, frames)
        // The 15 ms ramp is inaudibly close after 100 ms and bit-exact dry once it snaps (< 250 ms).
        for (i in (fs / 10) * 2 until buf.size) assertEquals(input[i], buf[i], 2e-3f)
        for (i in (fs / 4) * 2 until buf.size) assertEquals(input[i], buf[i], 0f)
    }

    @Test
    fun autoPreampPreventsClippingOfFullScaleSignal() {
        val gains = listOf(12f, 12f, 12f, 6f, 0f, 0f, 0f, 0f, 0f, 0f)
        val params = EqParams(true, gains, EqResponse.autoPreampDb(gains))
        for (f in listOf(31.0, 62.0, 100.0)) {
            val e = engine(params)
            val buf = stereoSine(f, 0.999, fs)
            e.process(buf, 0, fs)
            assertTrue("f=$f clipped", peak(buf, fs / 2) < 0.999f)
        }
    }

    @Test
    fun parameterJumpIsSmoothedWithoutClicks() {
        // A 100 Hz sine at 0.2 changes by at most ~0.0026 per sample. An unsmoothed jump to +12 dB
        // would step by ~0.6 in one sample; the glide must keep the largest step tiny.
        val e = engine(EqParams.FLAT)
        val frames = fs / 2
        val buf = stereoSine(100.0, 0.2, frames)
        e.process(buf, 0, 1024)
        e.setParams(EqParams(true, listOf(12f, 12f, 12f, 12f, 0f, 0f, 0f, 0f, 0f, 0f), 0f))
        e.process(buf, 1024 * 2, frames - 1024)
        var maxStep = 0f
        for (i in 2 until buf.size step 2) maxStep = maxOf(maxStep, abs(buf[i] - buf[i - 2]))
        assertTrue("max step $maxStep", maxStep < 0.05f)
    }

    @Test
    fun processesInPlaceWithOffsetAndMonoChannel() {
        val e = EqEngine().apply {
            setParams(EqParams(true, EqBands.FLAT.toMutableList().also { it[4] = -12f }, 0f))
            configure(fs, 1)
        }
        val buf = FloatArray(10 + fs) { if (it < 10) 7f else (0.5 * sin(2 * PI * 500.0 * it / fs)).toFloat() }
        e.process(buf, 10, fs)
        for (i in 0 until 10) assertEquals(7f, buf[i], 0f) // untouched prefix
        var p = 0f
        for (i in 10 + fs / 2 until buf.size) p = maxOf(p, abs(buf[i]))
        assertEquals(0.5 * 0.2512, p.toDouble(), 0.01) // -12 dB
    }

    @Test
    fun outputStaysFiniteAndBoundedWithoutHeadroom() {
        val e = engine(EqParams(true, List(EqBands.COUNT) { 12f }, 0f))
        val buf = stereoSine(1000.0, 1.0, fs)
        e.process(buf, 0, fs)
        assertTrue(buf.all { it.isFinite() && it in -1f..1f })
    }
}
