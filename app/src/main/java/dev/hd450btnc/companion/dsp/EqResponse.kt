package dev.hd450btnc.companion.dsp

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.exp

/**
 * Frequency response of the *actual* filter cascade. The curve on screen and the automatic
 * headroom are computed from the same coefficients the audio engine runs, so they cannot drift.
 */
object EqResponse {
    const val REFERENCE_SAMPLE_RATE = 48_000.0
    const val MIN_FREQ = 20.0
    const val MAX_FREQ = 20_000.0

    fun responseDb(
        gainsDb: List<Float>,
        freq: Double,
        sampleRate: Double = REFERENCE_SAMPLE_RATE,
    ): Double = responseDb(EqBands.coefficients(gainsDb, sampleRate), freq, sampleRate)

    fun responseDb(coeffs: Array<BiquadCoefficients>, freq: Double, sampleRate: Double): Double {
        var sum = 0.0
        for (c in coeffs) if (!c.isIdentity) sum += c.magnitudeDb(freq, sampleRate)
        return sum
    }

    /** Log-spaced frequency of point [index] out of [count] between [MIN_FREQ] and [MAX_FREQ]. */
    fun logFrequency(index: Int, count: Int): Double {
        val t = if (count <= 1) 0.0 else index.toDouble() / (count - 1)
        return exp(ln(MIN_FREQ) + t * (ln(MAX_FREQ) - ln(MIN_FREQ)))
    }

    /** Fraction 0..1 of [freq] on the log axis used by [logFrequency]. */
    fun logPosition(freq: Double): Double =
        ((ln(freq) - ln(MIN_FREQ)) / (ln(MAX_FREQ) - ln(MIN_FREQ))).coerceIn(0.0, 1.0)

    fun curve(gainsDb: List<Float>, points: Int, sampleRate: Double = REFERENCE_SAMPLE_RATE): FloatArray {
        val coeffs = EqBands.coefficients(gainsDb, sampleRate)
        return FloatArray(points) { i -> responseDb(coeffs, logFrequency(i, points), sampleRate).toFloat() }
    }

    /** Highest boost anywhere in the audible band, including the band centres. */
    fun peakDb(gainsDb: List<Float>, sampleRate: Double = REFERENCE_SAMPLE_RATE): Double {
        val coeffs = EqBands.coefficients(gainsDb, sampleRate)
        var peak = Double.NEGATIVE_INFINITY
        val grid = 512
        for (i in 0 until grid) peak = maxOf(peak, responseDb(coeffs, logFrequency(i, grid), sampleRate))
        for (f in EqBands.FREQUENCIES) peak = maxOf(peak, responseDb(coeffs, f, sampleRate))
        return peak
    }

    /**
     * Pre-amplification (<= 0 dB) that cancels the largest boost so a full-scale signal cannot be
     * pushed over 0 dBFS by the EQ. Rounded *down* to 0.1 dB so it never under-compensates.
     */
    fun autoPreampDb(gainsDb: List<Float>): Float {
        // Bilinear warping makes the top band differ slightly between 44.1 and 48 kHz; cover both.
        val peak = maxOf(peakDb(gainsDb, 44_100.0), peakDb(gainsDb, 48_000.0))
        if (peak <= 0.0) return 0f
        return (-ceil(peak * 10.0) / 10.0).toFloat()
    }
}
