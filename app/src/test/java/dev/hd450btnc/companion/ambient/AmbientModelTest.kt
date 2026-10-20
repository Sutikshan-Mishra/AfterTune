package dev.hd450btnc.companion.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientModelTest {
    private val mic = AudioEndpoint(DeviceKind.BUILTIN_MIC, "Phone")
    private val speaker = AudioEndpoint(DeviceKind.BUILTIN_SPEAKER, "Phone")
    private val a2dp = AudioEndpoint(DeviceKind.BT_A2DP, "HD 4.50BTNC")
    private val sco = AudioEndpoint(DeviceKind.BT_SCO, "HD 4.50BTNC")
    private val wired = AudioEndpoint(DeviceKind.WIRED_HEADPHONES, "Wired")

    @Test
    fun phoneMicNeedsHeadphonesToAvoidSpeakerFeedback() {
        val speakerOnly = AmbientRouting.plan(listOf(speaker), listOf(mic), emptyList(), 34)
        assertTrue(speakerOnly.phoneMic is Availability.Unavailable)
        assertNull(speakerOnly.mediaOutput)

        val bt = AmbientRouting.plan(listOf(speaker, a2dp), listOf(mic), emptyList(), 34)
        assertSame(Availability.Available, bt.phoneMic)
        assertEquals(a2dp, bt.mediaOutput)

        // Wired takes priority over Bluetooth, as on Android.
        assertEquals(wired, AmbientRouting.plan(listOf(a2dp, wired), listOf(mic), emptyList(), 34).mediaOutput)
        assertTrue(AmbientRouting.plan(listOf(a2dp), emptyList(), emptyList(), 34).phoneMic is Availability.Unavailable)
    }

    @Test
    fun headsetMicNeedsAndroid12AndAHandsFreeDevice() {
        assertTrue(AmbientRouting.plan(listOf(a2dp), listOf(mic), listOf(sco), 30).headsetMic is Availability.Unavailable)
        assertTrue(AmbientRouting.plan(listOf(a2dp), listOf(mic), emptyList(), 34).headsetMic is Availability.Unavailable)
        assertSame(Availability.Available, AmbientRouting.plan(listOf(a2dp), listOf(mic), listOf(sco), 31).headsetMic)
    }

    @Test
    fun mustStopWhenSafeRouteDisappears() {
        assertTrue(AmbientRouting.mustStop(AmbientInput.PHONE_MIC, listOf(speaker), emptyList()))
        assertFalse(AmbientRouting.mustStop(AmbientInput.PHONE_MIC, listOf(speaker, a2dp), emptyList()))
        assertTrue(AmbientRouting.mustStop(AmbientInput.HEADSET_MIC, listOf(a2dp), emptyList()))
        assertFalse(AmbientRouting.mustStop(AmbientInput.HEADSET_MIC, listOf(a2dp), listOf(sco)))
    }

    @Test
    fun startStopLifecycle() {
        var s = AmbientStatus()
        s = AmbientMachine.reduce(s, AmbientEvent.Gain(9f))
        s = AmbientMachine.reduce(s, AmbientEvent.StartRequested(AmbientInput.PHONE_MIC))
        assertEquals(AmbientPhase.STARTING, s.phase)
        assertEquals("start resets to conservative gain", AmbientGain.DEFAULT_DB, s.gainDb, 0f)
        assertFalse(s.muted)

        // A second start while active is ignored.
        assertSame(s, AmbientMachine.reduce(s, AmbientEvent.StartRequested(AmbientInput.HEADSET_MIC)))

        s = AmbientMachine.reduce(s, AmbientEvent.Started("Phone mic", "HD 4.50BTNC", 48_000, 240))
        assertEquals(AmbientPhase.RUNNING, s.phase)
        assertEquals("HD 4.50BTNC", s.outputDevice)

        s = AmbientMachine.reduce(s, AmbientEvent.Levels(-20f, -26f, 18))
        assertEquals(18, s.inPhoneLatencyMs)

        s = AmbientMachine.reduce(s, AmbientEvent.Stopped("Headphones disconnected"))
        assertEquals(AmbientPhase.IDLE, s.phase)
        assertEquals("Headphones disconnected", s.message)
        assertNull(s.outputDevice)
        assertEquals(AmbientProcessor.SILENCE_DB, s.inputPeakDbfs, 0f)
    }

    @Test
    fun failuresAndStaleEvents() {
        var s = AmbientMachine.reduce(AmbientStatus(), AmbientEvent.StartRequested(AmbientInput.HEADSET_MIC))
        s = AmbientMachine.reduce(s, AmbientEvent.Failed("Mic busy"))
        assertEquals(AmbientPhase.ERROR, s.phase)
        assertEquals(AmbientInput.HEADSET_MIC, s.input)
        // Late "Started"/levels after a failure must not resurrect the engine.
        assertEquals(AmbientPhase.ERROR, AmbientMachine.reduce(s, AmbientEvent.Started("a", "b", 16_000, 160)).phase)
        assertSame(s, AmbientMachine.reduce(s, AmbientEvent.Levels(0f, 0f, 1)))
        // Can start again from error.
        assertEquals(AmbientPhase.STARTING, AmbientMachine.reduce(s, AmbientEvent.StartRequested(AmbientInput.PHONE_MIC)).phase)
        assertEquals(12f, AmbientMachine.reduce(s, AmbientEvent.Gain(99f)).gainDb, 0f)
    }
}
