package dev.hd450btnc.companion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LevelAnalyzerTest {
    private val rate = 44100

    private fun sine(freq: Double, amp: Float, frames: Int, channels: Int = 2, startFrame: Int = 0): FloatArray {
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (amp * sin(2 * PI * freq * (startFrame + f) / rate)).toFloat()
            for (c in 0 until channels) out[f * channels + c] = v
        }
        return out
    }

    private fun feed(a: LevelAnalyzer, freq: Double, amp: Float, totalFrames: Int, block: Int) {
        var done = 0
        while (done < totalFrames) {
            val n = minOf(block, totalFrames - done)
            a.process(sine(freq, amp, n, startFrame = done), n, 2, rate)
            done += n
        }
    }

    @Test
    fun silenceStaysAtZero() {
        val a = LevelAnalyzer()
        a.process(FloatArray(2 * 4096), 4096, 2, rate)
        assertEquals(0f, a.level, 0f)
        assertEquals(0f, a.low, 0f)
    }

    @Test
    fun loudSignalRaisesLevelAndSilenceReleasesIt() {
        val a = LevelAnalyzer()
        feed(a, 1000.0, 0.5f, rate, 2048)
        assertTrue(a.level > 0.6f)
        val loud = a.level
        a.process(FloatArray(2 * rate / 2), rate / 2, 2, rate)
        assertTrue(a.level < loud)
        feed(a, 1000.0, 0f, rate * 3, 4096)
        assertTrue(a.level < 0.05f)
    }

    @Test
    fun bandsSeparateLowMidHigh() {
        val low = LevelAnalyzer().also { feed(it, 80.0, 0.5f, rate, 2048) }
        assertTrue(low.low > low.mid && low.low > low.high)
        val high = LevelAnalyzer().also { feed(it, 9000.0, 0.5f, rate, 2048) }
        assertTrue(high.high > high.low && high.high > high.mid)
    }

    @Test
    fun resultDoesNotDependOnBufferSize() {
        val small = LevelAnalyzer().also { feed(it, 1000.0, 0.3f, rate, 256) }
        val big = LevelAnalyzer().also { feed(it, 1000.0, 0.3f, rate, 8192) }
        assertEquals(small.level, big.level, 0.05f)
    }

    @Test
    fun resetClearsEverything() {
        val a = LevelAnalyzer()
        feed(a, 1000.0, 0.5f, rate, 2048)
        a.reset()
        assertEquals(0f, a.level, 0f)
        assertEquals(0f, a.mid, 0f)
    }

    @Test
    fun analysisNeverModifiesSamples() {
        val a = LevelAnalyzer()
        val buf = sine(440.0, 0.4f, 1024)
        val copy = buf.copyOf()
        a.process(buf, 1024, 2, rate)
        assertTrue(buf.contentEquals(copy))
    }
}
