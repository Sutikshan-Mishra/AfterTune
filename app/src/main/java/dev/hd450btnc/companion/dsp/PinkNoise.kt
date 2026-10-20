package dev.hd450btnc.companion.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates a pink-noise reference track (equal energy per octave) as a 16-bit PCM WAV.
 * Pink noise makes EQ changes easy to hear across the whole spectrum without needing music files.
 */
object PinkNoise {
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2
    const val HEADER_BYTES = 44

    /** Paul Kellet's refined pink filter. Deterministic for a given [seed]. */
    fun samples(count: Int, seed: Int): FloatArray {
        val rnd = Random(seed)
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        val out = FloatArray(count)
        for (i in 0 until count) {
            val white = rnd.nextDouble() * 2.0 - 1.0
            b0 = 0.99886 * b0 + white * 0.0555179
            b1 = 0.99332 * b1 + white * 0.0750759
            b2 = 0.96900 * b2 + white * 0.1538520
            b3 = 0.86650 * b3 + white * 0.3104856
            b4 = 0.55000 * b4 + white * 0.5329522
            b5 = -0.7616 * b5 - white * 0.0168980
            out[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362).toFloat()
            b6 = white * 0.115926
        }
        return out
    }

    /**
     * Stereo (decorrelated channels) WAV, normalised to [peakDbfs], with short fades so the loop
     * point does not click.
     */
    fun wav(seconds: Int = 20, peakDbfs: Float = -6f, seed: Int = 450): ByteArray {
        val frames = SAMPLE_RATE * seconds
        val left = samples(frames, seed)
        val right = samples(frames, seed + 1)
        var peak = 0f
        for (i in 0 until frames) peak = maxOf(peak, abs(left[i]), abs(right[i]))
        val scale = if (peak > 0f) (EqEngine.dbToLinear(peakDbfs) / peak).toFloat() else 0f
        val fade = (SAMPLE_RATE * 0.03).toInt()

        val dataBytes = frames * CHANNELS * 2
        val buf = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(CHANNELS.toShort())
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * CHANNELS * 2)
        buf.putShort((CHANNELS * 2).toShort())
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        for (i in 0 until frames) {
            val env = min(1f, min(i, frames - 1 - i).toFloat() / fade)
            buf.putShort(toPcm16(left[i] * scale * env))
            buf.putShort(toPcm16(right[i] * scale * env))
        }
        return buf.array()
    }

    private fun toPcm16(v: Float): Short = (v * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
}
