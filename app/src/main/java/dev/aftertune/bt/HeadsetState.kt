package dev.aftertune.bt

enum class BtAvailability { NO_ADAPTER, PERMISSION_REQUIRED, OFF, ON }

data class AudioDevice(
    val name: String,
    val address: String,
    val mediaAudio: Boolean,
    val calls: Boolean,
    /** Percentage reported to Android over the hands-free link, or null when not reported. */
    val batteryPercent: Int?,
) {
    /** Last two octets only; the full address is not useful to show. */
    val shortAddress: String get() = address.takeLast(5)
}

enum class ConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    PAIRED_NOT_CONNECTED("Not connected"),
    NOT_PAIRED("No headphones paired"),
    BLUETOOTH_OFF("Bluetooth is off"),
    PERMISSION_REQUIRED("Access needed"),
    UNSUPPORTED("No Bluetooth on this device"),
}

data class HeadsetState(
    val availability: BtAvailability = BtAvailability.PERMISSION_REQUIRED,
    val connected: List<AudioDevice> = emptyList(),
    /** Whether any Bluetooth audio device is paired with the phone. */
    val targetPaired: Boolean = false,
    /** True once the first full refresh has completed, so the UI can show a loading state before. */
    val ready: Boolean = false,
) {
    /** The device shown as "the headphones": the one carrying media audio, else the first connected. */
    val target: AudioDevice? get() = connected.firstOrNull { it.mediaAudio } ?: connected.firstOrNull()
    val others: List<AudioDevice> get() = connected.filterNot { it === target }

    val status: ConnectionStatus
        get() = when (availability) {
            BtAvailability.NO_ADAPTER -> ConnectionStatus.UNSUPPORTED
            BtAvailability.PERMISSION_REQUIRED -> ConnectionStatus.PERMISSION_REQUIRED
            BtAvailability.OFF -> ConnectionStatus.BLUETOOTH_OFF
            BtAvailability.ON -> when {
                target != null -> ConnectionStatus.CONNECTED
                targetPaired -> ConnectionStatus.PAIRED_NOT_CONNECTED
                else -> ConnectionStatus.NOT_PAIRED
            }
        }

    companion object {
        /** Battery values outside 0..100 (Android uses -1 for "unknown") are treated as unavailable. */
        fun validBattery(raw: Int?): Int? = raw?.takeIf { it in 0..100 }
    }
}
