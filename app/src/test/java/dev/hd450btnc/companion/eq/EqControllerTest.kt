package dev.hd450btnc.companion.eq

import dev.hd450btnc.companion.dsp.EqBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqControllerTest {
    private val base = EqState()

    @Test
    fun builtInPresetsHaveTenValidBands() {
        assertEquals(listOf("Neutral", "Bass", "Vocal", "Clarity"), BuiltInPreset.entries.map { it.label })
        for (p in BuiltInPreset.entries) {
            assertEquals(EqBands.COUNT, p.gains.size)
            assertEquals(p.gains, EqBands.normalizeGains(p.gains))
        }
        assertTrue(BuiltInPreset.NEUTRAL.gains.all { it == 0f })
        assertTrue(BuiltInPreset.BASS.gains[0] > 0f)
    }

    @Test
    fun selectingPresetAppliesItsCurve() {
        val s = EqController.selectPreset(base, "bass")
        assertEquals("bass", s.activeId)
        assertEquals(BuiltInPreset.BASS.gains, s.gains)
    }

    @Test
    fun editingAPresetMovesToCustomWithoutChangingPreset() {
        val s = EqController.setBand(EqController.selectPreset(base, "vocal"), 0, 4f)
        assertEquals(EqState.CUSTOM_ID, s.activeId)
        assertEquals(4f, s.gains[0], 0f)
        assertEquals(BuiltInPreset.VOCAL.gains.drop(1), s.gains.drop(1))
        assertEquals(s.gains, s.customGains)
        assertEquals(-2f, BuiltInPreset.VOCAL.gains[0], 0f)
    }

    @Test
    fun customCurveSurvivesPresetSwitching() {
        val custom = EqController.setBand(base, 3, 2.5f)
        val back = EqController.selectPreset(EqController.selectPreset(custom, "clarity"), EqState.CUSTOM_ID)
        assertEquals(2.5f, back.gains[3], 0f)
    }

    @Test
    fun setBandClampsQuantisesAndIgnoresBadIndex() {
        val s = EqController.setBand(base, 2, 99f)
        assertEquals(12f, s.gains[2], 0f)
        assertEquals(1.5f, EqController.setBand(base, 2, 1.6f).gains[2], 0f)
        assertEquals(base, EqController.setBand(base, 10, 3f))
    }

    @Test
    fun resetFlattensCustomOrReturnsToNeutral() {
        val custom = EqController.setBand(base, 1, 5f)
        val r = EqController.reset(custom)
        assertEquals(EqState.CUSTOM_ID, r.activeId)
        assertTrue(r.isFlat && r.customGains.all { it == 0f })

        val fromBass = EqController.reset(EqController.selectPreset(custom, "bass"))
        assertEquals("neutral", fromBass.activeId)
        assertEquals(5f, fromBass.customGains[1], 0f) // custom untouched
    }

    @Test
    fun saveRenameDeleteProfiles() {
        var s = EqController.setBand(base, 0, 3f)
        s = EqController.saveProfile(s, "  Commute  ", "p1")
        assertEquals("p1", s.activeId)
        assertEquals("Commute", s.activeProfile?.name)

        s = EqController.saveProfile(s, "commute", "p2")
        assertEquals("commute 2", s.profiles[1].name)

        s = EqController.saveProfile(s, "Bass", "p3")
        assertEquals("Bass 2", s.profiles[2].name) // cannot shadow a built-in

        s = EqController.renameProfile(s, "p1", "Train")
        assertEquals("Train", s.profiles[0].name)
        assertEquals(s, EqController.renameProfile(s, "p1", "   "))

        // Deleting the active profile keeps the sound as Custom.
        s = EqController.selectPreset(s, "p1")
        s = EqController.deleteProfile(s, "p1")
        assertNull(s.profiles.firstOrNull { it.id == "p1" })
        assertEquals(EqState.CUSTOM_ID, s.activeId)
        assertEquals(3f, s.gains[0], 0f)
    }

    @Test
    fun profileLimitAndNameLength() {
        var s = base
        repeat(EqState.MAX_PROFILES) { s = EqController.saveProfile(s, "P$it", "id$it") }
        assertFalse(EqController.canSaveProfile(s))
        assertEquals(s, EqController.saveProfile(s, "extra", "x"))
        assertEquals(EqState.MAX_NAME_LENGTH, EqController.cleanName("x".repeat(80)).length)
        assertEquals("a b", EqController.cleanName("a\n\t b"))
    }

    @Test
    fun autoHeadroomTracksCurve() {
        val boosted = EqController.selectPreset(base, "bass")
        assertTrue(boosted.preampDb < 0f)
        assertEquals(0f, EqController.setAutoHeadroom(boosted, false).preampDb, 0f)
        assertEquals(0f, base.preampDb, 0f)
        assertEquals(boosted.preampDb, boosted.toParams().preampDb, 0f)
    }
}
