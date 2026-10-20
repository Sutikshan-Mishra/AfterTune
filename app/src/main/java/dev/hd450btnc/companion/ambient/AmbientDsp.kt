package dev.hd450btnc.companion.ambient

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Ambient gain: digital gain applied to the microphone signal, in dB relative to the mic level
 * (0 dB = the captured signal unchanged). Linear-in-dB slider mapping, bounded on both ends.
 * The phone/headset volume still applies on top of this.
 */
object AmbientGain {
    const val MIN_DB = -30f
    const val MAX_DB = 12f
    /** Conservative start value used every time the engine starts. */
    const val DEFAULT_DB = -6f
    const val STEP_DB = 0.5f

    fun clampDb(db: Float): Float {
        if (db.isNaN()) return DEFAULT_DB
        val stepped = (db.coerceIn(MIN_DB, MAX_DB) / STEP_DB).roundToInt() * STEP_DB
        return if (stepped == 0f) 0f else stepped
    }

    /** Slider position 0..1 -> dB. */
    fun sliderToDb(position: Float): Float = clampDb(MIN_DB + position.coerceIn(0f, 1f) * (MAX_DB - MIN_DB))

    fun dbToSlider(db: Float): Float = (clampDb(db) - MIN_DB) / (MAX_DB - MIN_DB)

    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    fun label(db: Float, muted: Boolean): String {
        if (muted) return "Muted"
        val g = clampDb(db)
        val sign = when {
            g > 0f -> "+"
            g < 0f -> "−"
            else -> ""
        }
        return sign + "%.1f dB".format(java.util.Locale.ROOT, abs(g))
    }
}

/** One-pole DC blocker (~25 Hz corner at 48 kHz): removes mic offset so it cannot be amplified. */
class DcBlocker(sampleRate: Int) {
    private val r = exp(-2.0 * Math.PI * 25.0 / sampleRate).toFloat()
    private var x1 = 0f
    private var y1 = 0f

    fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = y
        return y
    }

    fun reset() { x1 = 0f; y1 = 0f }
}

/**
 * Peak limiter with instant attack and smooth release. Output never exceeds [ceiling]
 * (a final clamp guarantees it even for pathological input).
 */
class PeakLimiter(sampleRate: Int, val ceiling: Float = CEILING, releaseMs: Float = 80f) {
    private val release = exp(-1.0 / (releaseMs / 1000.0 * sampleRate)).toFloat()
    var gain = 1f
        private set

    fun process(x: Float): Float {
        val a = abs(x)
        val needed = if (a > ceiling) ceiling / a else 1f
        // Recover towards 1, but never above what this sample needs.
        val recovered = 1f - (1f - gain) * release
        gain = minOf(recovered, needed)
        val y = x * gain
        return y.coerceIn(-ceiling, ceiling)
    }

    fun reset() { gain = 1f }

    companion object {
        /** -2 dBFS. */
        const val CEILING = 0.794f
    }
}

/**
 * Real-time ambient chain for mono 16-bit PCM: DC blocker -> smoothed gain -> peak limiter.
 * The gain always starts at silence and ramps to the target (fade-in), and every change is ramped
 * so the slider never clicks. [setGainDb]/[setMuted] are safe to call from any thread.
 */
class AmbientProcessor(sampleRate: Int, rampMs: Float = 30f) {
    private val dc = DcBlocker(sampleRate)
    private val limiter = PeakLimiter(sampleRate)
    private val smoothing = exp(-1.0 / (rampMs / 1000.0 * sampleRate)).toFloat()

    @Volatile private var targetGain = AmbientGain.dbToLinear(AmbientGain.DEFAULT_DB)
    @Volatile private var muted = false
    private var gain = 0f

    @Volatile var inputPeakDbfs = SILENCE_DB
        private set
    @Volatile var outputPeakDbfs = SILENCE_DB
        private set

    fun setGainDb(db: Float) { targetGain = AmbientGain.dbToLinear(AmbientGain.clampDb(db)) }
    fun setMuted(on: Boolean) { muted = on }
    val currentGain: Float get() = gain

    fun process(buf: ShortArray, count: Int) {
        val target = if (muted) 0f else targetGain
        var inPeak = 0f
        var outPeak = 0f
        for (i in 0 until count) {
            val x = buf[i] / 32768f
            inPeak = maxOf(inPeak, abs(x))
            gain = target + (gain - target) * smoothing
            // Snap once within -80 dB of the target so mute reaches true silence.
            if (abs(gain - target) < 1e-4f) gain = target
            val y = limiter.process(dc.process(x) * gain)
            outPeak = maxOf(outPeak, abs(y))
            buf[i] = (y * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        inputPeakDbfs = toDbfs(inPeak)
        outputPeakDbfs = toDbfs(outPeak)
    }

    fun reset() {
        dc.reset(); limiter.reset(); gain = 0f
        inputPeakDbfs = SILENCE_DB; outputPeakDbfs = SILENCE_DB
    }

    companion object {
        const val SILENCE_DB = -90f
        fun toDbfs(peak: Float): Float = if (peak <= 0f) SILENCE_DB else maxOf(SILENCE_DB, 20f * log10(peak))
    }
}
