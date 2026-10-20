package dev.hd450btnc.companion.systemeq

import dev.hd450btnc.companion.dsp.EqBands
import dev.hd450btnc.companion.dsp.EqResponse
import dev.hd450btnc.companion.eq.BuiltInPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEqModelTest {
    @Test
    fun dynamicsBandsCoverAudibleRangeMonotonically() {
        val bands = SystemEqMapping.dynamicsBands(EqBands.FLAT)
        assertEquals(SystemEqMapping.DYNAMICS_BANDS, bands.size)
        assertEquals(20_000f, bands.last().cutoffHz, 0.5f)
        assertTrue(bands.first().cutoffHz > 20f)
        for (i in 1 until bands.size) assertTrue(bands[i].cutoffHz > bands[i - 1].cutoffHz)
        assertTrue(bands.all { it.gainDb == 0f })
    }

    @Test
    fun dynamicsBandsFollowTheRealCurve() {
        val gains = BuiltInPreset.BASS.gains
        val bands = SystemEqMapping.dynamicsBands(gains)
        val e = SystemEqMapping.edges()
        bands.forEachIndexed { i, b ->
            val centre = kotlin.math.sqrt(e[i] * e[i + 1])
            assertEquals(EqResponse.responseDb(gains, centre).toFloat(), b.gainDb, 1e-4f)
        }
        assertTrue(bands.first().gainDb > 4f) // bass boost present
        assertEquals(0f, bands.last().gainDb, 0.2f)
    }

    @Test
    fun legacyLevelsApplyHeadroomAndClampToDeviceRange() {
        val gains = EqBands.FLAT.toMutableList().also { it[5] = 6f } // +6 dB at 1 kHz
        val centres = intArrayOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000) // typical 5-band device, mHz
        val noHeadroom = SystemEqMapping.legacyLevelsMb(gains, centres, -1500, 1500, 0f)
        assertTrue(noHeadroom[2] in 500..600)
        val shifted = SystemEqMapping.legacyLevelsMb(gains, centres, -1500, 1500, -6f)
        assertEquals(noHeadroom[2] - 600, shifted[2])
        val clamped = SystemEqMapping.legacyLevelsMb(List(10) { 12f }, centres, -1500, 1000, 0f)
        assertTrue(clamped.all { it <= 1000 })
        assertEquals(0, SystemEqMapping.legacyLevelsMb(gains, intArrayOf(0), -1500, 1500, 0f)[0])
    }

    @Test
    fun summaryNeverClaimsUnobservedSuccess() {
        assertTrue(SystemEqState().summary.startsWith("Off"))
        val waiting = SystemEqState(requested = true, serviceRunning = true)
        assertTrue(waiting.summary.startsWith("Waiting"))

        var s = SystemEqReducer.upsert(waiting, AttachedSession(77, "com.spotify.music", Mechanism.DYNAMICS, hasControl = true))
        assertEquals(1, s.workingCount)
        assertTrue(s.summary.contains("Listen to confirm"))
        assertTrue(s.copy(eqEnabled = false).summary.contains("switched off"))

        s = SystemEqReducer.control(s, 77, granted = false)
        assertEquals(0, s.workingCount)
        assertTrue(s.summary.contains("control"))

        val failed = SystemEqReducer.upsert(waiting, AttachedSession(5, "x", null, false, "Not supported"))
        assertTrue(failed.summary.startsWith("Could not attach"))

        assertTrue(SystemEqState(requested = true, serviceRunning = true, global = GlobalMixState.CREATED_UNVERIFIED)
            .summary.contains("depends on the phone"))

        s = SystemEqReducer.remove(s, 77)
        assertTrue(s.sessions.isEmpty())
    }

    @Test
    fun ownSessionsAndInvalidIdsAreSkipped() {
        assertFalse(SystemEqReducer.shouldAttach(12, "dev.hd450btnc.companion", "dev.hd450btnc.companion"))
        assertFalse(SystemEqReducer.shouldAttach(0, "com.spotify.music", "dev.hd450btnc.companion"))
        assertTrue(SystemEqReducer.shouldAttach(12, "com.spotify.music", "dev.hd450btnc.companion"))
        assertTrue(SystemEqReducer.shouldAttach(12, null, "dev.hd450btnc.companion"))
    }
}
