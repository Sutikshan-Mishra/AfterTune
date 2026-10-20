package dev.hd450btnc.companion.ambient

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Microphone passthrough: AudioRecord -> [AmbientProcessor] -> AudioTrack on a dedicated
 * URGENT_AUDIO thread. Nothing is ever written to storage.
 *
 * Phone mic: built-in mic -> media output (A2DP/wired). Music keeps playing and is mixed by Android;
 *   adds the Bluetooth link delay.
 * Headset mic (Android 12+): the headset's hands-free (HFP/SCO) mic via setCommunicationDevice.
 *   This switches the headset to its call link (mono, call-grade audio); other media is typically
 *   interrupted or degraded until Stop, which restores the previous route and audio mode.
 *
 * start/stop/setters: main thread. Status is a StateFlow safe to observe anywhere.
 */
class AmbientEngine(private val context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(AmbientStatus())
    val status: StateFlow<AmbientStatus> = _status.asStateFlow()

    private val _plan = MutableStateFlow(computePlan())
    val plan: StateFlow<RoutePlan> = _plan.asStateFlow()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var processor: AmbientProcessor? = null
    private val effects = mutableListOf<AudioEffect>()
    private var previousMode: Int? = null
    private var scoConfirmed = false
    private var commListener: Any? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshPlan()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshPlan()
            val s = _status.value
            if (s.isActive && AmbientRouting.mustStop(s.input, AudioDevices.outputs(am), AudioDevices.communication(am))) {
                stop(
                    if (s.input == AmbientInput.PHONE_MIC) "Headphones disconnected. Stopped so sound can't come from the phone speaker."
                    else "Headset hands-free link ended. Stopped.",
                )
            }
        }
    }

    init {
        am.registerAudioDeviceCallback(deviceCallback, main)
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun refreshPlan() {
        _plan.value = computePlan()
    }

    private fun computePlan(): RoutePlan = try {
        AmbientRouting.plan(AudioDevices.outputs(am), AudioDevices.inputs(am), AudioDevices.communication(am), Build.VERSION.SDK_INT)
    } catch (e: RuntimeException) {
        val why = Availability.Unavailable("Audio devices could not be read: ${e.message}")
        RoutePlan(why, why, null)
    }

    private fun reduce(e: AmbientEvent) = _status.update { AmbientMachine.reduce(it, e) }

    fun setGain(db: Float) {
        val v = AmbientGain.clampDb(db)
        reduce(AmbientEvent.Gain(v))
        processor?.setGainDb(v)
    }

    fun resetGain() = setGain(AmbientGain.DEFAULT_DB)

    fun setMuted(on: Boolean) {
        reduce(AmbientEvent.Mute(on))
        processor?.setMuted(on)
    }

    /** Report a failure that happened before the engine could start (e.g. the service was refused). */
    fun reportFailure(reason: String) = reduce(AmbientEvent.Failed(reason))

    /** Main thread. Must be called by [AmbientService] after it entered the foreground. */
    fun start(input: AmbientInput) {
        if (_status.value.isActive) return
        reduce(AmbientEvent.StartRequested(input))
        if (!hasMicPermission()) {
            fail("Microphone permission is not granted.")
            return
        }
        val plan = computePlan().also { _plan.value = it }
        val availability = if (input == AmbientInput.PHONE_MIC) plan.phoneMic else plan.headsetMic
        if (availability is Availability.Unavailable) {
            fail(availability.reason)
            return
        }
        try {
            open(input, plan)
        } catch (e: SecurityException) {
            fail("Microphone access was refused by Android.")
        } catch (e: Exception) {
            fail(e.message ?: "Could not start ambient listening.")
        }
    }

    /** Main thread. Idempotent. */
    fun stop(reason: String? = null) {
        val wasActive = _status.value.isActive
        release()
        if (wasActive) reduce(AmbientEvent.Stopped(reason))
    }

    private fun fail(reason: String) {
        release()
        reduce(AmbientEvent.Failed(reason))
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun open(input: AmbientInput, plan: RoutePlan) {
        val headset = input == AmbientInput.HEADSET_MIC
        var scoInput: AudioDeviceInfo? = null

        if (headset) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) error("Needs Android 12 or newer.")
            val sco = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: error("The headset microphone is not available.")
            previousMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            scoConfirmed = false
            registerCommunicationListener()
            if (!am.setCommunicationDevice(sco)) error("Android refused to route audio to the headset's hands-free link.")
            scoInput = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }

        val nativeRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val candidates = if (headset) listOf(16_000, 8_000) else listOfNotNull(nativeRate, 48_000, 44_100).distinct()
        val rate = candidates.firstOrNull { r ->
            AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                AudioTrack.getMinBufferSize(r, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: error("No supported sample rate for microphone passthrough.")

        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()?.takeIf { it in 32..2048 }
        // Native burst when running at the native rate; otherwise ~5 ms (10 ms for call-grade links).
        val chunk = when {
            headset -> rate / 100
            burst != null && rate == nativeRate -> burst
            else -> rate / 200
        }

        val recMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioSource(if (headset) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build(),
            )
            .setBufferSizeInBytes(maxOf(recMin, chunk * 2 * 4))
            .build()
        record = rec
        if (rec.state != AudioRecord.STATE_INITIALIZED) error("The microphone could not be opened.")
        scoInput?.let { rec.setPreferredDevice(it) }
        disableAutoProcessing(rec.audioSessionId)

        val trackMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val trk = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (headset) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(trackMin)
            .build()
        track = trk
        if (trk.state != AudioTrack.STATE_INITIALIZED) error("The audio output could not be opened.")
        // Keep the output queue short so delay cannot build up; Android may round this up.
        trk.setBufferSizeInFrames(chunk * 2)

        val proc = AmbientProcessor(rate).apply {
            setGainDb(_status.value.gainDb)
            setMuted(_status.value.muted)
        }
        processor = proc

        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            error("The microphone is in use by another app or a call.")
        }
        trk.play()

        running = true
        thread = Thread({ loop(rec, trk, proc, rate, chunk) }, "ambient-audio").apply { start() }

        val inName = rec.routedDevice?.let { AudioDevices.endpoint(it).name }
            ?: if (headset) "Headset hands-free mic" else "Phone microphone"
        val outName = trk.routedDevice?.let { AudioDevices.endpoint(it).name }
            ?: plan.mediaOutput?.name ?: "Headset"
        reduce(AmbientEvent.Started(inName, outName, rate, chunk))
    }

    /** AGC would boost quiet rooms (runaway gain) and AEC would cancel the very sound we pass through. */
    private fun disableAutoProcessing(sessionId: Int) {
        try {
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.let { it.setEnabled(false); effects += it }
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.let { it.setEnabled(false); effects += it }
        } catch (_: RuntimeException) {
            // Not fatal: the limiter still bounds the output.
        }
    }

    private fun loop(rec: AudioRecord, trk: AudioTrack, proc: AmbientProcessor, rate: Int, chunk: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ShortArray(chunk)
        val ts = AudioTimestamp()
        var written = 0L
        var lastReport = 0L
        while (running) {
            val n = rec.read(buf, 0, chunk, AudioRecord.READ_BLOCKING)
            if (!running) break
            if (n < 0) {
                main.post { if (running) fail("The microphone stopped delivering audio (error $n).") }
                break
            }
            if (n == 0) continue
            proc.process(buf, n)
            val w = trk.write(buf, 0, n, AudioTrack.WRITE_BLOCKING)
            if (w < 0) {
                main.post { if (running) fail("Audio output failed (error $w).") }
                break
            }
            written += w
            val now = SystemClock.elapsedRealtime()
            if (now - lastReport >= 400) {
                lastReport = now
                val latency = if (trk.getTimestamp(ts)) {
                    val played = ts.framePosition + (System.nanoTime() - ts.nanoTime) * rate / 1_000_000_000L
                    val pending = (written - played).coerceAtLeast(0L)
                    ((pending + chunk) * 1000L / rate).toInt()
                } else null
                reduce(AmbientEvent.Levels(proc.inputPeakDbfs, proc.outputPeakDbfs, latency))
            }
        }
    }

    private fun registerCommunicationListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || commListener != null) return
        val l = AudioManager.OnCommunicationDeviceChangedListener { device ->
            if (device?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                scoConfirmed = true
            } else if (scoConfirmed && _status.value.isActive) {
                stop("The headset's hands-free link was taken over or ended. Stopped.")
            }
        }
        am.addOnCommunicationDeviceChangedListener(ContextCompat.getMainExecutor(context), l)
        commListener = l
    }

    /** Releases everything and restores the audio route. Safe to call repeatedly. */
    private fun release() {
        running = false
        try { record?.stop() } catch (_: RuntimeException) {} // unblocks a pending read
        thread?.let { if (it != Thread.currentThread()) it.join(1_000) }
        thread = null
        try { track?.pause(); track?.flush(); track?.stop() } catch (_: RuntimeException) {}
        record?.release(); record = null
        track?.release(); track = null
        effects.forEach { try { it.release() } catch (_: RuntimeException) {} }
        effects.clear()
        processor = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (commListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let {
                am.removeOnCommunicationDeviceChangedListener(it)
            }
            commListener = null
            if (previousMode != null) am.clearCommunicationDevice()
        }
        previousMode?.let { am.mode = it }
        previousMode = null
        scoConfirmed = false
    }
}
