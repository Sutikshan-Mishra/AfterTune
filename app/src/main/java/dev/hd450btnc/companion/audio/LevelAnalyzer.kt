package dev.hd450btnc.companion.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheap, read-only loudness/band analysis of the audio the in-app player is decoding. It never changes the
 * samples. One pass per decoded buffer: a mono mix split with two one-pole filters into low (<250 Hz),
 * mid (250 Hz-3 kHz) and high (>3 kHz), turned into 0..1 values (-55..-10 dBFS) and smoothed with a fast
 * attack and a slow release, using the buffer's real duration so the result does not depend on buffer size
 * or on how often the UI reads it. The UI only reads the volatile fields; nothing here touches Compose.
 */
class LevelAnalyzer {
    @Volatile var level = 0f; private set
    @Volatile var low = 0f; private set
    @Volatile var mid = 0f; private set
    @Volatile var high = 0f; private set

    private var lpLow = 0f
    private var lpMid = 0f

    fun process(samples: FloatArray, frames: Int, channels: Int, sampleRate: Int) {
        if (frames <= 0 || channels <= 0 || sampleRate <= 0) return
        val aLow = 1f - exp(-2.0 * PI * 250.0 / sampleRate).toFloat()
        val aMid = 1f - exp(-2.0 * PI * 3000.0 / sampleRate).toFloat()
        var sAll = 0f
        var sLow = 0f
        var sMid = 0f
        var sHigh = 0f
        var idx = 0
        for (f in 0 until frames) {
            var m = 0f
            for (ch in 0 until channels) m += samples[idx++]
            m /= channels
            lpLow += aLow * (m - lpLow)
            lpMid += aMid * (m - lpMid)
            val l = lpLow
            val md = lpMid - lpLow
            val h = m - lpMid
            sAll += m * m
            sLow += l * l
            sMid += md * md
            sHigh += h * h
        }
        val dt = frames / sampleRate.toFloat()
        level = smooth(level, normalise(sAll / frames), dt)
        low = smooth(low, normalise(sLow / frames), dt)
        mid = smooth(mid, normalise(sMid / frames), dt)
        high = smooth(high, normalise(sHigh / frames), dt)
    }

    /** New stream, seek, stop: no stale reaction. */
    fun reset() {
        lpLow = 0f
        lpMid = 0f
        level = 0f
        low = 0f
        mid = 0f
        high = 0f
    }

    companion object {
        fun normalise(meanSquare: Float): Float {
            val rms = sqrt(max(meanSquare, 0f))
            val db = 20f * log10(max(rms, 1e-6f))
            return ((db + 55f) / 45f).coerceIn(0f, 1f)
        }

        fun smooth(previous: Float, target: Float, dtSeconds: Float): Float {
            val tau = if (target > previous) 0.04f else 0.26f
            return previous + (target - previous) * (1f - exp(-dtSeconds / tau))
        }
    }
}
