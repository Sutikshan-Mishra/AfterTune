package dev.hd450btnc.companion.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.hd450btnc.companion.dsp.EqEngine
import dev.hd450btnc.companion.dsp.EqParams
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Media3 audio processor that runs every decoded sample of the in-app player through [EqEngine].
 * Accepts 16-bit and float PCM; output format equals input format.
 */
@OptIn(UnstableApi::class)
class EqAudioProcessor : BaseAudioProcessor() {
    private val engine = EqEngine()
    private var scratch = FloatArray(0)

    /** Read-only level analysis of the incoming audio (before the EQ), for the UI. Never modifies samples. */
    val analyzer = LevelAnalyzer()

    fun setParams(params: EqParams) = engine.setParams(params)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val channels = format.channelCount
        val frames = remaining / (bytesPerSample * channels)
        val samples = frames * channels
        if (scratch.size < samples) scratch = FloatArray(samples)

        if (isFloat) {
            for (i in 0 until samples) scratch[i] = inputBuffer.getFloat()
        } else {
            for (i in 0 until samples) scratch[i] = inputBuffer.getShort() / 32768f
        }
        // Consume any trailing partial frame (should not happen with well-formed PCM).
        inputBuffer.position(inputBuffer.limit())

        analyzer.process(scratch, frames, channels, format.sampleRate)
        engine.process(scratch, 0, frames)

        val out = replaceOutputBuffer(samples * bytesPerSample)
        if (isFloat) {
            for (i in 0 until samples) out.putFloat(scratch[i])
        } else {
            for (i in 0 until samples) {
                // Same scale as the input conversion, so Neutral is bit-exact for 16-bit PCM.
                out.putShort((scratch[i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        out.flip()
    }

    override fun onFlush() {
        analyzer.reset()
        // New stream or seek: (re)configure for the active format and clear filter memory.
        val format = inputAudioFormat
        if (format.sampleRate > 0 && format.channelCount > 0) engine.configure(format.sampleRate, format.channelCount)
    }

    override fun onReset() {
        analyzer.reset()
        engine.resetState()
    }
}
