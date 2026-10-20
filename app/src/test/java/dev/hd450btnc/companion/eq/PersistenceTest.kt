package dev.hd450btnc.companion.eq

import dev.hd450btnc.companion.settings.AppSettings
import dev.hd450btnc.companion.settings.SettingsRepository
import dev.hd450btnc.companion.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenceTest {
    @Test
    fun fullStateRoundTrips() {
        var s = EqController.setBand(EqState(), 4, -3.5f)
        s = EqController.saveProfile(s, "A|b \\ c\nd", "p-1")
        s = EqController.selectPreset(s, "clarity")
        s = EqController.setEnabled(s, false)
        s = EqController.setAutoHeadroom(s, false)

        val repo = EqRepository(InMemoryStore())
        repo.save(s)
        val loaded = repo.load()
        assertEquals(s, loaded)
        assertEquals("A|b \\ c d", loaded.profiles[0].name) // separators survive; newline collapsed by name cleaning
    }

    @Test
    fun escapingRoundTrips() {
        val raw = "a|b\\c\nd\re\\p"
        assertEquals(raw, EqStateCodec.unescape(EqStateCodec.escape(raw)))
    }

    @Test
    fun missingOrCorruptDataFallsBackSafely() {
        assertEquals(EqState(), EqStateCodec.decode(null))
        assertEquals(EqState(), EqStateCodec.decode("garbage\n=\nactive"))
        val s = EqStateCodec.decode(
            """
            v=1
            active=does-not-exist
            custom=1,2,abc
            profile=onlytwo|parts
            profile=ok|Fine|1,1,1
            gains=5,5,5,5,5,5,5,5,5,5,5,5,5
            """.trimIndent(),
        )
        assertEquals("neutral", s.activeId)
        assertEquals(List(10) { 0f }, s.gains) // active curve is always re-derived from its source
        assertEquals(1, s.profiles.size)
        assertEquals(listOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), s.profiles[0].gains)
    }

    @Test
    fun activeCustomCurveIsRestored() {
        val s = EqController.setBand(EqState(), 9, 4f)
        val loaded = EqStateCodec.decode(EqStateCodec.encode(s))
        assertEquals("custom", loaded.activeId)
        assertEquals(4f, loaded.gains[9], 0f)
    }

    @Test
    fun settingsRoundTripWithDefaults() {
        val store = InMemoryStore()
        val repo = SettingsRepository(store)
        assertEquals(AppSettings(), repo.load())
        val s = AppSettings(
            theme = ThemeMode.LIGHT, repeatQueue = false, pauseOnDisconnect = false,
            systemEqEnabled = true, globalMixExperimental = true,
            ambientInput = dev.hd450btnc.companion.ambient.AmbientInput.HEADSET_MIC,
        )
        repo.save(s)
        assertEquals(s, repo.load())
        // Unknown stored enum values fall back to safe defaults.
        store.putString("settings_ambient_input", "LASER")
        assertEquals(dev.hd450btnc.companion.ambient.AmbientInput.PHONE_MIC, repo.load().ambientInput)
        repo.clear()
        assertEquals(AppSettings(), repo.load())
    }
}
