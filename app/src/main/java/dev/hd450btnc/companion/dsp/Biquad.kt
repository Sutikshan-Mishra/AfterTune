package dev.hd450btnc.companion.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Normalised second-order IIR coefficients (a0 == 1), RBJ "Audio EQ Cookbook" designs.
 * Processing uses Transposed Direct Form II, which tolerates per-block coefficient updates well.
 */
class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /** Magnitude response in dB at [freq] Hz for sample rate [sampleRate]. */
    fun magnitudeDb(freq: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = 1.0 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        val mag2 = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
        return 10.0 * log10(mag2)
    }

    /** Both poles strictly inside the unit circle (stability triangle). */
    val isStable: Boolean
        get() = abs(a2) < 1.0 && abs(a1) < 1.0 + a2

    val isIdentity: Boolean
        get() = b0 == 1.0 && b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0

    companion object {
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

        /** Highest centre frequency we will realise, as a fraction of the sample rate. */
        const val MAX_RELATIVE_FREQ = 0.45

        fun peaking(sampleRate: Double, f0: Double, q: Double, gainDb: Double): BiquadCoefficients {
            if (gainDb == 0.0 || f0 <= 0.0 || f0 >= sampleRate * MAX_RELATIVE_FREQ) return IDENTITY
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / a
            return BiquadCoefficients(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cosW) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cosW) / a0,
                a2 = (1.0 - alpha / a) / a0,
            )
        }
    }
}
