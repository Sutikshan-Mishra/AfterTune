package dev.hd450btnc.companion.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes the HD 4.50 BTNC over the standard Bluetooth Classic profiles it implements
 * (A2DP for media audio, HFP for calls). Event driven: no discovery scans, no BLE, no background work.
 * Active only between [start] and [stop] (tied to the visible activity).
 *
 * Battery: the headset reports its level to the phone over HFP (Sennheiser's manual notes the level
 * can appear on the phone's display). Android stores that value; we read it through the
 * framework's BluetoothDevice#getBatteryLevel and its BATTERY_LEVEL_CHANGED broadcast. Those are
 * not public SDK API, so any failure is reported as "not reported" — a value is never estimated.
 */
class HeadsetMonitor(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(HeadsetState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    private var a2dp: BluetoothProfile? = null
    private var hfp: BluetoothProfile? = null
    private var a2dpUnsupported = false
    private var hfpUnsupported = false
    private var started = false
    private var receiverRegistered = false
    private val reportedBattery = mutableMapOf<String, Int>()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!started) {
                adapter?.closeProfileProxy(profile, proxy)
                return
            }
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = proxy
                BluetoothProfile.HEADSET -> hfp = proxy
            }
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = null
                BluetoothProfile.HEADSET -> hfp = null
            }
            refresh()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                if (device != null) {
                    val valid = HeadsetState.validBattery(level)
                    if (valid != null) reportedBattery[device.address] = valid else reportedBattery.remove(device.address)
                }
            }
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) openProxies()
            refresh()
        }
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started) return
        started = true
        registerReceiver()
        openProxies()
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        closeProxies()
    }

    /** Call after the permission dialog closes. */
    fun onPermissionResult() {
        if (!started) return
        if (!receiverRegistered) registerReceiver()
        openProxies()
        refresh()
    }

    private fun registerReceiver() {
        if (receiverRegistered || !hasConnectPermission()) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        // These are protected broadcasts sent by the Bluetooth stack (its own uid, not system uid);
        // other apps cannot send them. EXPORTED avoids the compat permission wrapper used for
        // NOT_EXPORTED on Android 12 and lower, which the Bluetooth process does not hold.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun openProxies() {
        val a = adapter ?: return
        if (!hasConnectPermission()) return
        try {
            // false = profile not supported on this phone (e.g. no hands-free on some tablets).
            if (a2dp == null) a2dpUnsupported = !a.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            if (hfp == null) hfpUnsupported = !a.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (_: SecurityException) {
        }
    }

    private fun closeProxies() {
        val a = adapter
        a2dp?.let { a?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        hfp?.let { a?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        a2dp = null
        hfp = null
    }

    @SuppressLint("MissingPermission") // Every path checks hasConnectPermission() first.
    fun refresh() {
        val a = adapter
        val next = when {
            a == null -> HeadsetState(BtAvailability.NO_ADAPTER, ready = true)
            !hasConnectPermission() -> HeadsetState(BtAvailability.PERMISSION_REQUIRED, ready = true)
            !safe { a.isEnabled }.orFalse() -> HeadsetState(
                BtAvailability.OFF,
                targetPaired = bondedTarget(a),
                ready = true,
            )
            else -> {
                val media = safe { a2dp?.connectedDevices }.orEmpty()
                val calls = safe { hfp?.connectedDevices }.orEmpty()
                val all = (media + calls).distinctBy { it.address }
                val devices = all.map { d ->
                    val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) safe { d.alias } else null
                    val name = safe { d.name } ?: alias ?: "Unknown device"
                    AudioDevice(
                        name = name,
                        address = d.address,
                        isTarget = DeviceMatcher.isHd450Btnc(name),
                        mediaAudio = media.any { it.address == d.address },
                        calls = calls.any { it.address == d.address },
                        batteryPercent = batteryOf(d),
                    )
                }
                HeadsetState(
                    availability = BtAvailability.ON,
                    connected = devices,
                    targetPaired = bondedTarget(a) || devices.any { it.isTarget },
                    // Proxies bind asynchronously; until both answered, "not connected" could be wrong.
                    ready = (a2dp != null || a2dpUnsupported) && (hfp != null || hfpUnsupported),
                )
            }
        }
        _state.value = next
    }

    @SuppressLint("MissingPermission")
    private fun bondedTarget(a: BluetoothAdapter): Boolean =
        safe { a.bondedDevices?.any { DeviceMatcher.isHd450Btnc(safe { it.name }) } }.orFalse()

    private fun batteryOf(device: BluetoothDevice): Int? {
        val fromFramework = try {
            device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int
        } catch (_: Exception) {
            null
        }
        return HeadsetState.validBattery(fromFramework) ?: reportedBattery[device.address]
    }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun Boolean?.orFalse() = this ?: false

    private companion object {
        // Framework constants (BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED / EXTRA_BATTERY_LEVEL).
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
