package dev.hd450btnc.companion.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Immutable parameter snapshot handed from the UI thread to the audio thread. */
data class EqParams(
    val enabled: Boolean,
    val gainsDb: List<Float>,
    val preampDb: Float,
) {
    companion object {
        val FLAT = EqParams(enabled = true, gainsDb = EqBands.FLAT, preampDb = 0f)
    }
}

/**
 * Real-time 10-band EQ on interleaved float PCM. Platform-independent so it can be unit tested.
 *
 * Click avoidance:
 *  - band gains glide towards their targets and coefficients are recomputed per [BLOCK] frames;
 *  - preamp and the enable/bypass mix are smoothed per sample with a one-pole ramp.
 * Filters keep running while bypassed so re-enabling starts from a warm state.
 *
 * Thread model: [setParams] may be called from any thread; everything else from the audio thread.
 */
class EqEngine {
    @Volatile
    private var target: EqParams = EqParams.FLAT

    private var sampleRate = 48_000.0
    private var channels = 2
    private var configured = false

    private val currentGains = DoubleArray(EqBands.COUNT)
    private var coeffs: Array<BiquadCoefficients> = Array(EqBands.COUNT) { BiquadCoefficients.IDENTITY }
    private var z1 = Array(EqBands.COUNT) { DoubleArray(channels) }
    private var z2 = Array(EqBands.COUNT) { DoubleArray(channels) }

    private var preampLin = 1.0
    private var mix = 1.0
    private var sampleSmoothing = 0.0
    private var blockAlpha = 1.0

    fun setParams(params: EqParams) {
        target = params.copy(gainsDb = EqBands.normalizeGains(params.gainsDb))
    }

    fun params(): EqParams = target

    /** (Re)configure for a stream. Snaps to the current target: a new stream needs no glide. */
    fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate > 0 && channelCount > 0)
        this.sampleRate = sampleRate.toDouble()
        this.channels = channelCount
        z1 = Array(EqBands.COUNT) { DoubleArray(channelCount) }
        z2 = Array(EqBands.COUNT) { DoubleArray(channelCount) }
        sampleSmoothing = exp(-1.0 / (SAMPLE_TAU_S * this.sampleRate))
        blockAlpha = 1.0 - exp(-BLOCK / (GAIN_TAU_S * this.sampleRate))
        val t = target
        for (i in 0 until EqBands.COUNT) currentGains[i] = t.gainsDb[i].toDouble()
        coeffs = Array(EqBands.COUNT) { i ->
            BiquadCoefficients.peaking(this.sampleRate, EqBands.FREQUENCIES[i], EqBands.Q, currentGains[i])
        }
        preampLin = dbToLinear(t.preampDb)
        mix = if (t.enabled) 1.0 else 0.0
        configured = true
    }

    /** Clears filter memory (seek/flush) without touching parameters. */
    fun resetState() {
        for (b in 0 until EqBands.COUNT) {
            z1[b].fill(0.0)
            z2[b].fill(0.0)
        }
    }

    /** Processes [frameCount] interleaved frames of [samples] starting at [offset], in place. */
    fun process(samples: FloatArray, offset: Int, frameCount: Int) {
        if (!configured) configure(sampleRate.toInt(), channels)
        val t = target
        val targetPreamp = dbToLinear(t.preampDb)
        val targetMix = if (t.enabled) 1.0 else 0.0
        val ch = channels
        var frame = 0
        while (frame < frameCount) {
            val blockFrames = minOf(BLOCK, frameCount - frame)
            glideGains(t.gainsDb)
            for (f in 0 until blockFrames) {
                preampLin = targetPreamp + (preampLin - targetPreamp) * sampleSmoothing
                mix = targetMix + (mix - targetMix) * sampleSmoothing
                if (abs(preampLin - targetPreamp) < SNAP_LINEAR) preampLin = targetPreamp
                if (abs(mix - targetMix) < SNAP_LINEAR) mix = targetMix
                val base = offset + (frame + f) * ch
                for (c in 0 until ch) {
                    val x = samples[base + c].toDouble()
                    var y = x
                    for (b in 0 until EqBands.COUNT) {
                        val k = coeffs[b]
                        if (k.isIdentity) continue
                        val out = k.b0 * y + z1[b][c]
                        z1[b][c] = k.b1 * y - k.a1 * out + z2[b][c]
                        z2[b][c] = k.b2 * y - k.a2 * out
                        y = out
                    }
                    val wet = y * preampLin
                    var v = x + mix * (wet - x)
                    // Guard only: with automatic headroom this never engages. Keeps float output in range.
                    if (v > 1.0) v = 1.0 else if (v < -1.0) v = -1.0
                    samples[base + c] = v.toFloat()
                }
            }
            frame += blockFrames
        }
    }

    private fun glideGains(targetGains: List<Float>) {
        for (b in 0 until EqBands.COUNT) {
            val goal = targetGains[b].toDouble()
            val cur = currentGains[b]
            if (cur == goal) continue
            var next = cur + (goal - cur) * blockAlpha
            if (abs(goal - next) < SNAP_DB) next = goal
            currentGains[b] = next
            val wasIdentity = coeffs[b].isIdentity
            coeffs[b] = BiquadCoefficients.peaking(sampleRate, EqBands.FREQUENCIES[b], EqBands.Q, next)
            if (wasIdentity && !coeffs[b].isIdentity) {
                // Identity bands were skipped, so their state is stale: start this filter clean.
                z1[b].fill(0.0)
                z2[b].fill(0.0)
            }
        }
    }

    companion object {
        const val BLOCK = 32
        private const val GAIN_TAU_S = 0.035
        private const val SAMPLE_TAU_S = 0.015
        private const val SNAP_DB = 0.005
        private const val SNAP_LINEAR = 1e-5

        fun dbToLinear(db: Float): Double = 10.0.pow(db / 20.0)
    }
}
