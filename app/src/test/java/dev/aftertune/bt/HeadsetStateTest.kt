package dev.aftertune.bt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadsetStateTest {
    private fun dev(name: String, media: Boolean = true, address: String = "00:11:22:33:44:55") =
        AudioDevice(name, address, mediaAudio = media, calls = true, batteryPercent = null)

    @Test
    fun statusReflectsAvailabilityAndDevices() {
        assertEquals(ConnectionStatus.PERMISSION_REQUIRED, HeadsetState().status)
        assertEquals(ConnectionStatus.UNSUPPORTED, HeadsetState(BtAvailability.NO_ADAPTER).status)
        assertEquals(ConnectionStatus.BLUETOOTH_OFF, HeadsetState(BtAvailability.OFF, targetPaired = true).status)
        assertEquals(ConnectionStatus.NOT_PAIRED, HeadsetState(BtAvailability.ON).status)
        assertEquals(ConnectionStatus.PAIRED_NOT_CONNECTED, HeadsetState(BtAvailability.ON, targetPaired = true).status)
        val one = HeadsetState(BtAvailability.ON, listOf(dev("Any Headphones")), targetPaired = true)
        assertEquals(ConnectionStatus.CONNECTED, one.status)
        assertEquals("Any Headphones", one.target?.name)
    }

    @Test
    fun mediaDeviceIsTheShownOne() {
        val both = HeadsetState(BtAvailability.ON, listOf(dev("Car kit", media = false, address = "AA:BB:CC:DD:EE:01"), dev("Buds")))
        assertEquals("Buds", both.target?.name)
        assertEquals(listOf("Car kit"), both.others.map { it.name })
        val callsOnly = HeadsetState(BtAvailability.ON, listOf(dev("Car kit", media = false)))
        assertEquals("Car kit", callsOnly.target?.name)
    }

    @Test
    fun batteryOnlyAcceptedWhenInRange() {
        assertNull(HeadsetState.validBattery(-1))
        assertNull(HeadsetState.validBattery(101))
        assertNull(HeadsetState.validBattery(null))
        assertEquals(0, HeadsetState.validBattery(0))
        assertEquals(80, HeadsetState.validBattery(80))
        assertEquals("44:55", dev("x").shortAddress)
    }
}
