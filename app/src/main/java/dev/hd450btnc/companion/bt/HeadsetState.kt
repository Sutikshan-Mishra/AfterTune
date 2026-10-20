package dev.hd450btnc.companion.bt

/**
 * Identifies the HD 4.50 BTNC by its Bluetooth name. Punctuation and spacing are ignored so
 * "HD 4.50BTNC", "HD4.50 BTNC" and "HD 4.50 BTNC" all match, while the different HD 450BT,
 * HD 4.40 BT and HD 4.50 SE do not.
 */
object DeviceMatcher {
    fun isHd450Btnc(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.uppercase().filter { it.isLetterOrDigit() }
        return "HD450BTNC" in n
    }
}

enum class BtAvailability { NO_ADAPTER, PERMISSION_REQUIRED, OFF, ON }

data class AudioDevice(
    val name: String,
    val address: String,
    val isTarget: Boolean,
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
    OTHER_DEVICE("Another device is connected"),
    PAIRED_NOT_CONNECTED("Paired · not connected"),
    NOT_PAIRED("Not paired"),
    BLUETOOTH_OFF("Bluetooth is off"),
    PERMISSION_REQUIRED("Access needed"),
    UNSUPPORTED("No Bluetooth on this device"),
}

data class HeadsetState(
    val availability: BtAvailability = BtAvailability.PERMISSION_REQUIRED,
    val connected: List<AudioDevice> = emptyList(),
    val targetPaired: Boolean = false,
    /** True once the first full refresh has completed, so the UI can show a loading state before. */
    val ready: Boolean = false,
) {
    val target: AudioDevice? get() = connected.firstOrNull { it.isTarget }
    val others: List<AudioDevice> get() = connected.filterNot { it.isTarget }

    val status: ConnectionStatus
        get() = when (availability) {
            BtAvailability.NO_ADAPTER -> ConnectionStatus.UNSUPPORTED
            BtAvailability.PERMISSION_REQUIRED -> ConnectionStatus.PERMISSION_REQUIRED
            BtAvailability.OFF -> ConnectionStatus.BLUETOOTH_OFF
            BtAvailability.ON -> when {
                target != null -> ConnectionStatus.CONNECTED
                connected.isNotEmpty() -> ConnectionStatus.OTHER_DEVICE
                targetPaired -> ConnectionStatus.PAIRED_NOT_CONNECTED
                else -> ConnectionStatus.NOT_PAIRED
            }
        }

    companion object {
        /** Battery values outside 0..100 (Android uses -1 for "unknown") are treated as unavailable. */
        fun validBattery(raw: Int?): Int? = raw?.takeIf { it in 0..100 }
    }
}
