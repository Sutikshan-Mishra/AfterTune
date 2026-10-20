package dev.hd450btnc.companion.eq

import dev.hd450btnc.companion.dsp.EqBands
import dev.hd450btnc.companion.dsp.EqParams
import dev.hd450btnc.companion.dsp.EqResponse

/** Built-in curves. Values are dB for 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz. */
enum class BuiltInPreset(val id: String, val label: String, val gains: List<Float>) {
    NEUTRAL("neutral", "Neutral", EqBands.FLAT),
    BASS("bass", "Bass", listOf(5.5f, 5f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
    VOCAL("vocal", "Vocal", listOf(-2f, -2f, -1f, 0f, 1.5f, 3f, 3f, 2f, 0f, -1f)),
    // The HD 4.50 BTNC is commonly described as bass-forward; Clarity trims lows and lifts presence.
    CLARITY("clarity", "Clarity", listOf(0f, -0.5f, -1.5f, -1.5f, -1f, 0f, 1.5f, 3f, 3.5f, 2.5f));

    companion object {
        fun byId(id: String): BuiltInPreset? = entries.firstOrNull { it.id == id }
    }
}

data class SavedProfile(val id: String, val name: String, val gains: List<Float>)

data class EqState(
    val enabled: Boolean = true,
    /** "neutral" | "bass" | "vocal" | "clarity" | [CUSTOM_ID] | a [SavedProfile.id]. */
    val activeId: String = BuiltInPreset.NEUTRAL.id,
    val gains: List<Float> = EqBands.FLAT,
    val customGains: List<Float> = EqBands.FLAT,
    val profiles: List<SavedProfile> = emptyList(),
    val autoHeadroom: Boolean = true,
) {
    val preampDb: Float get() = if (autoHeadroom) EqResponse.autoPreampDb(gains) else 0f

    /** Peak boost of the curve; > 0 with auto headroom off means loud material can clip. */
    val peakDb: Double get() = EqResponse.peakDb(gains)

    val isFlat: Boolean get() = gains.all { it == 0f }

    val activeProfile: SavedProfile? get() = profiles.firstOrNull { it.id == activeId }

    fun toParams(): EqParams = EqParams(enabled = enabled, gainsDb = gains, preampDb = preampDb)

    companion object {
        const val CUSTOM_ID = "custom"
        const val MAX_PROFILES = 20
        const val MAX_NAME_LENGTH = 24
    }
}
