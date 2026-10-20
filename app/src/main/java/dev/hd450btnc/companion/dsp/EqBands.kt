package dev.hd450btnc.companion.dsp

import kotlin.math.roundToInt

/** 10-band ISO octave graphic EQ layout shared by the DSP, the curve view and persistence. */
object EqBands {
    val FREQUENCIES = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    const val COUNT = 10
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val STEP_DB = 0.5f

    /** Bandwidth chosen so adjacent octave bands overlap smoothly without large ripple. */
    const val Q = 1.2

    val FLAT: List<Float> = List(COUNT) { 0f }

    /** Clamp to the allowed range and quantise to [STEP_DB]. Also normalises -0.0 to 0.0. */
    fun normalizeGain(gainDb: Float): Float {
        if (gainDb.isNaN()) return 0f
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val stepped = (clamped / STEP_DB).roundToInt() * STEP_DB
        return if (stepped == 0f) 0f else stepped
    }

    /** Returns exactly [COUNT] normalised gains: extra values dropped, missing values flat. */
    fun normalizeGains(gains: List<Float>): List<Float> =
        List(COUNT) { i -> normalizeGain(gains.getOrNull(i) ?: 0f) }

    fun coefficients(gainsDb: List<Float>, sampleRate: Double): Array<BiquadCoefficients> =
        Array(COUNT) { i ->
            BiquadCoefficients.peaking(sampleRate, FREQUENCIES[i], Q, (gainsDb.getOrNull(i) ?: 0f).toDouble())
        }

    fun formatGain(gainDb: Float): String {
        val g = normalizeGain(gainDb)
        val sign = when {
            g > 0f -> "+"
            g < 0f -> "−"
            else -> ""
        }
        val abs = kotlin.math.abs(g)
        val text = if (abs % 1f == 0f) abs.toInt().toString() else abs.toString()
        return sign + text
    }
}
