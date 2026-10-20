package dev.hd450btnc.companion.ambient

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Maps Android's AudioDeviceInfo into the platform-neutral model used by [AmbientRouting]. */
object AudioDevices {
    fun kind(type: Int): DeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> DeviceKind.BUILTIN_MIC
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> DeviceKind.BUILTIN_SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> DeviceKind.BUILTIN_EARPIECE
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> DeviceKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> DeviceKind.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_USB_HEADSET -> DeviceKind.USB_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DeviceKind.BT_A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceKind.BT_SCO
        AudioDeviceInfo.TYPE_BLE_HEADSET -> DeviceKind.BLE_HEADSET // int constant, inlined at compile time
        AudioDeviceInfo.TYPE_HEARING_AID -> DeviceKind.HEARING_AID
        else -> DeviceKind.OTHER
    }

    fun endpoint(info: AudioDeviceInfo): AudioEndpoint {
        val kind = kind(info.type)
        val product = info.productName?.toString()?.trim().orEmpty()
        val name = when {
            kind == DeviceKind.BUILTIN_MIC -> "Phone microphone"
            kind == DeviceKind.BUILTIN_SPEAKER -> "Phone speaker"
            product.isNotEmpty() -> product
            else -> kind.name.lowercase().replace('_', ' ')
        }
        return AudioEndpoint(kind, name)
    }

    fun outputs(am: AudioManager): List<AudioEndpoint> = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::endpoint)
    fun inputs(am: AudioManager): List<AudioEndpoint> = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map(::endpoint)

    fun communication(am: AudioManager): List<AudioEndpoint> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.availableCommunicationDevices.map(::endpoint) else emptyList()
}
