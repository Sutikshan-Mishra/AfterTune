package dev.hd450btnc.companion.ambient

/** Where ambient sound is captured. */
enum class AmbientInput(val label: String) {
    PHONE_MIC("Phone mic"),
    HEADSET_MIC("Headset mic"),
}

/** Platform-neutral audio device description (mapped from AudioDeviceInfo on Android). */
enum class DeviceKind {
    BUILTIN_MIC, BUILTIN_SPEAKER, BUILTIN_EARPIECE,
    WIRED_HEADSET, WIRED_HEADPHONES, USB_HEADSET,
    BT_A2DP, BT_SCO, BLE_HEADSET, HEARING_AID, OTHER;

    /** Outputs that sit on or in the ears, so the mic cannot pick its own output up as feedback. */
    val isPersonalOutput: Boolean
        get() = this in setOf(WIRED_HEADSET, WIRED_HEADPHONES, USB_HEADSET, BT_A2DP, BT_SCO, BLE_HEADSET, HEARING_AID)
}

data class AudioEndpoint(val kind: DeviceKind, val name: String)

sealed interface Availability {
    data object Available : Availability
    data class Unavailable(val reason: String) : Availability
}

data class RoutePlan(
    val phoneMic: Availability,
    val headsetMic: Availability,
    /** Output that phone-mic mode would play to (null when no safe output exists). */
    val mediaOutput: AudioEndpoint?,
)

/** Decides which ambient modes are possible with the devices currently connected. Pure, unit tested. */
object AmbientRouting {
    const val HEADSET_MIN_SDK = 31

    fun plan(
        outputs: List<AudioEndpoint>,
        inputs: List<AudioEndpoint>,
        communicationDevices: List<AudioEndpoint>,
        sdkInt: Int,
    ): RoutePlan {
        // Media output priority mirrors Android's: wired/USB first, then Bluetooth media.
        val order = listOf(DeviceKind.WIRED_HEADPHONES, DeviceKind.WIRED_HEADSET, DeviceKind.USB_HEADSET,
            DeviceKind.BT_A2DP, DeviceKind.BLE_HEADSET, DeviceKind.HEARING_AID)
        val mediaOut = order.firstNotNullOfOrNull { k -> outputs.firstOrNull { it.kind == k } }

        val phoneMic = when {
            inputs.none { it.kind == DeviceKind.BUILTIN_MIC } -> Availability.Unavailable("This phone reports no built-in microphone.")
            mediaOut == null -> Availability.Unavailable(
                "Connect headphones first. Playing the microphone through the phone speaker would cause loud feedback.",
            )
            else -> Availability.Available
        }

        val headsetMic = when {
            sdkInt < HEADSET_MIN_SDK -> Availability.Unavailable("Needs Android 12 or newer.")
            communicationDevices.none { it.kind == DeviceKind.BT_SCO } -> Availability.Unavailable(
                "The headset's hands-free microphone isn't offered by Android. Check that the headphones are connected and “Phone calls” is enabled for them.",
            )
            else -> Availability.Available
        }
        return RoutePlan(phoneMic, headsetMic, mediaOut)
    }

    /** True if, after a device change, the running mode no longer has a safe route and must stop. */
    fun mustStop(input: AmbientInput, outputs: List<AudioEndpoint>, communicationDevices: List<AudioEndpoint>): Boolean =
        when (input) {
            AmbientInput.PHONE_MIC -> outputs.none { it.kind.isPersonalOutput }
            AmbientInput.HEADSET_MIC -> communicationDevices.none { it.kind == DeviceKind.BT_SCO }
        }
}

enum class AmbientPhase { IDLE, STARTING, RUNNING, ERROR }

data class AmbientStatus(
    val phase: AmbientPhase = AmbientPhase.IDLE,
    val input: AmbientInput = AmbientInput.PHONE_MIC,
    val inputDevice: String? = null,
    val outputDevice: String? = null,
    val sampleRate: Int = 0,
    val chunkFrames: Int = 0,
    /** Measured delay inside the phone (capture chunk + playback queue). Excludes the Bluetooth link. */
    val inPhoneLatencyMs: Int? = null,
    val inputPeakDbfs: Float = AmbientProcessor.SILENCE_DB,
    val outputPeakDbfs: Float = AmbientProcessor.SILENCE_DB,
    val message: String? = null,
    val gainDb: Float = AmbientGain.DEFAULT_DB,
    val muted: Boolean = false,
) {
    val isActive: Boolean get() = phase == AmbientPhase.STARTING || phase == AmbientPhase.RUNNING
}

sealed interface AmbientEvent {
    data class StartRequested(val input: AmbientInput) : AmbientEvent
    data class Started(val inputDevice: String, val outputDevice: String, val sampleRate: Int, val chunkFrames: Int) : AmbientEvent
    data class Stopped(val reason: String?) : AmbientEvent
    data class Failed(val reason: String) : AmbientEvent
    data class Levels(val inputDbfs: Float, val outputDbfs: Float, val latencyMs: Int?) : AmbientEvent
    data class Gain(val db: Float) : AmbientEvent
    data class Mute(val on: Boolean) : AmbientEvent
}

/** State transitions for the ambient engine. Pure, unit tested. */
object AmbientMachine {
    fun reduce(s: AmbientStatus, e: AmbientEvent): AmbientStatus = when (e) {
        is AmbientEvent.StartRequested ->
            if (s.isActive) s
            // Always start conservatively: default gain, not muted, levels cleared.
            else AmbientStatus(phase = AmbientPhase.STARTING, input = e.input)
        is AmbientEvent.Started ->
            if (s.phase != AmbientPhase.STARTING) s
            else s.copy(phase = AmbientPhase.RUNNING, inputDevice = e.inputDevice, outputDevice = e.outputDevice,
                sampleRate = e.sampleRate, chunkFrames = e.chunkFrames, message = null)
        is AmbientEvent.Stopped ->
            if (!s.isActive) s.copy(message = e.reason ?: s.message)
            else AmbientStatus(phase = AmbientPhase.IDLE, input = s.input, message = e.reason, gainDb = s.gainDb)
        is AmbientEvent.Failed -> AmbientStatus(phase = AmbientPhase.ERROR, input = s.input, message = e.reason, gainDb = s.gainDb)
        is AmbientEvent.Levels ->
            if (s.phase != AmbientPhase.RUNNING) s
            else s.copy(inputPeakDbfs = e.inputDbfs, outputPeakDbfs = e.outputDbfs, inPhoneLatencyMs = e.latencyMs ?: s.inPhoneLatencyMs)
        is AmbientEvent.Gain -> s.copy(gainDb = AmbientGain.clampDb(e.db))
        is AmbientEvent.Mute -> s.copy(muted = e.on)
    }
}
