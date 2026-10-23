package dev.aftertune.settings

import dev.aftertune.ambient.AmbientInput
import dev.aftertune.eq.KeyValueStore

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

/**
 * Persisted preferences. Ambient gain is deliberately NOT persisted: the ambient engine always
 * starts at the conservative default.
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val repeatQueue: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val systemEqEnabled: Boolean = false,
    val globalMixExperimental: Boolean = false,
    val externalArtwork: Boolean = false,
    val ambientInput: AmbientInput = AmbientInput.PHONE_MIC,
)

class SettingsRepository(private val store: KeyValueStore) {
    fun load(): AppSettings = AppSettings(
        theme = store.getString(THEME)?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
        repeatQueue = flag(REPEAT, true),
        pauseOnDisconnect = flag(NOISY, true),
        systemEqEnabled = flag(SYSTEM_EQ, false),
        globalMixExperimental = flag(GLOBAL_MIX, false),
        externalArtwork = flag(EXTERNAL_ART, false),
        ambientInput = store.getString(AMBIENT_INPUT)?.let { v -> AmbientInput.entries.firstOrNull { it.name == v } }
            ?: AmbientInput.PHONE_MIC,
    )

    fun save(s: AppSettings) {
        store.putString(THEME, s.theme.name)
        store.putString(REPEAT, bit(s.repeatQueue))
        store.putString(NOISY, bit(s.pauseOnDisconnect))
        store.putString(SYSTEM_EQ, bit(s.systemEqEnabled))
        store.putString(GLOBAL_MIX, bit(s.globalMixExperimental))
        store.putString(EXTERNAL_ART, bit(s.externalArtwork))
        store.putString(AMBIENT_INPUT, s.ambientInput.name)
    }

    fun clear() {
        listOf(THEME, REPEAT, NOISY, SYSTEM_EQ, GLOBAL_MIX, EXTERNAL_ART, AMBIENT_INPUT).forEach { store.putString(it, null) }
    }

    private fun flag(key: String, default: Boolean) = store.getString(key)?.let { it == "1" } ?: default
    private fun bit(b: Boolean) = if (b) "1" else "0"

    private companion object {
        const val THEME = "settings_theme"
        const val REPEAT = "settings_repeat"
        const val NOISY = "settings_pause_on_disconnect"
        const val SYSTEM_EQ = "settings_system_eq"
        const val GLOBAL_MIX = "settings_global_mix_experimental"
        const val EXTERNAL_ART = "settings_external_artwork"
        const val AMBIENT_INPUT = "settings_ambient_input"
    }
}
