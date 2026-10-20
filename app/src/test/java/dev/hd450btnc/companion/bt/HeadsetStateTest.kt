package dev.hd450btnc.companion.bt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetStateTest {
    @Test
    fun matchesOnlyTheHd450Btnc() {
        listOf("HD 4.50BTNC", "HD 4.50 BTNC", "HD4.50BTNC", "hd 4.50 btnc", "Sennheiser HD 4.50 BTNC").forEach {
            assertTrue(it, DeviceMatcher.isHd450Btnc(it))
        }
        listOf("HD 450BT", "HD 4.40BT", "HD 4.50 SE", "HD 450SE", "MOMENTUM 4", "", null).forEach {
            assertFalse(it.toString(), DeviceMatcher.isHd450Btnc(it))
        }
    }

    private fun dev(name: String, target: Boolean) =
        AudioDevice(name, "00:11:22:33:44:55", target, mediaAudio = true, calls = true, batteryPercent = null)

    @Test
    fun statusReflectsAvailabilityAndDevices() {
        assertEquals(ConnectionStatus.PERMISSION_REQUIRED, HeadsetState().status)
        assertEquals(ConnectionStatus.UNSUPPORTED, HeadsetState(BtAvailability.NO_ADAPTER).status)
        assertEquals(ConnectionStatus.BLUETOOTH_OFF, HeadsetState(BtAvailability.OFF, targetPaired = true).status)
        assertEquals(ConnectionStatus.NOT_PAIRED, HeadsetState(BtAvailability.ON).status)
        assertEquals(ConnectionStatus.PAIRED_NOT_CONNECTED, HeadsetState(BtAvailability.ON, targetPaired = true).status)
        val other = HeadsetState(BtAvailability.ON, listOf(dev("Car", false)), targetPaired = true)
        assertEquals(ConnectionStatus.OTHER_DEVICE, other.status)
        assertNull(other.target)
        val both = HeadsetState(BtAvailability.ON, listOf(dev("Car", false), dev("HD 4.50BTNC", true)))
        assertEquals(ConnectionStatus.CONNECTED, both.status)
        assertEquals("HD 4.50BTNC", both.target?.name)
        assertEquals(listOf("Car"), both.others.map { it.name })
    }

    @Test
    fun batteryOnlyAcceptedWhenInRange() {
        assertNull(HeadsetState.validBattery(-1))
        assertNull(HeadsetState.validBattery(101))
        assertNull(HeadsetState.validBattery(null))
        assertEquals(0, HeadsetState.validBattery(0))
        assertEquals(80, HeadsetState.validBattery(80))
        assertEquals("44:55", dev("x", true).shortAddress)
    }
}
