package dev.hd450btnc.companion.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PinkNoiseTest {
    @Test
    fun wavHeaderAndSizeAreValid() {
        val wav = PinkNoise.wav(seconds = 2)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals(wav.size - 8, b.getInt(4))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(2, b.getShort(22).toInt())
        assertEquals(44_100, b.getInt(24))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(44_100 * 2 * 2 * 2, b.getInt(40))
        assertEquals(PinkNoise.HEADER_BYTES + 44_100 * 2 * 2 * 2, wav.size)
    }

    @Test
    fun levelIsNormalisedAndEndsAreFaded() {
        val wav = PinkNoise.wav(seconds = 2, peakDbfs = -6f)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0
        var i = PinkNoise.HEADER_BYTES
        while (i < wav.size) { peak = maxOf(peak, kotlin.math.abs(b.getShort(i).toInt())); i += 2 }
        assertEquals(16_422.0, peak.toDouble(), 120.0) // -6 dBFS of 32767
        assertEquals(0, b.getShort(PinkNoise.HEADER_BYTES).toInt())
        assertEquals(0, b.getShort(wav.size - 2).toInt())
    }

    @Test
    fun spectrumIsPinkNotWhite() {
        // Pink noise has much more energy at low than at high frequency: compare a low-passed proxy.
        val s = PinkNoise.samples(200_000, 1)
        var diffEnergy = 0.0
        var energy = 0.0
        for (k in 1 until s.size) {
            energy += s[k] * s[k]
            val d = s[k] - s[k - 1]
            diffEnergy += d * d
        }
        // For white noise diffEnergy/energy ~= 2; for pink it is far lower.
        assertTrue(diffEnergy / energy < 0.8)
    }
}
