package dev.hd450btnc.companion.eq

import dev.hd450btnc.companion.dsp.EqBands

/**
 * Pure state transitions for the EQ. No Android, no I/O: every rule here is unit tested.
 *
 * Editing rule: built-in presets and saved profiles are never modified by dragging a band.
 * The first edit copies the current curve into "Custom" and continues there.
 */
object EqController {

    fun selectPreset(state: EqState, id: String): EqState {
        BuiltInPreset.byId(id)?.let { return state.copy(activeId = it.id, gains = it.gains) }
        if (id == EqState.CUSTOM_ID) return state.copy(activeId = id, gains = state.customGains)
        val profile = state.profiles.firstOrNull { it.id == id } ?: return state
        return state.copy(activeId = profile.id, gains = profile.gains)
    }

    fun setBand(state: EqState, index: Int, gainDb: Float): EqState {
        if (index !in 0 until EqBands.COUNT) return state
        val value = EqBands.normalizeGain(gainDb)
        if (state.gains[index] == value && state.activeId == EqState.CUSTOM_ID) return state
        val newGains = state.gains.toMutableList().also { it[index] = value }
        return state.copy(activeId = EqState.CUSTOM_ID, gains = newGains, customGains = newGains)
    }

    /** Custom: flatten the custom curve. Anything else: go back to Neutral. Saved data is untouched. */
    fun reset(state: EqState): EqState =
        if (state.activeId == EqState.CUSTOM_ID) {
            state.copy(gains = EqBands.FLAT, customGains = EqBands.FLAT)
        } else {
            selectPreset(state, BuiltInPreset.NEUTRAL.id)
        }

    fun setEnabled(state: EqState, enabled: Boolean): EqState = state.copy(enabled = enabled)

    fun setAutoHeadroom(state: EqState, on: Boolean): EqState = state.copy(autoHeadroom = on)

    fun canSaveProfile(state: EqState): Boolean = state.profiles.size < EqState.MAX_PROFILES

    /** Saves the current curve as a new profile and activates it. Returns state unchanged if full. */
    fun saveProfile(state: EqState, rawName: String, newId: String): EqState {
        if (!canSaveProfile(state)) return state
        val name = uniqueName(state, cleanName(rawName).ifEmpty { "Profile" }, exceptId = null)
        val profile = SavedProfile(newId, name, state.gains)
        return state.copy(profiles = state.profiles + profile, activeId = newId)
    }

    fun renameProfile(state: EqState, id: String, rawName: String): EqState {
        val clean = cleanName(rawName)
        if (clean.isEmpty() || state.profiles.none { it.id == id }) return state
        val name = uniqueName(state, clean, exceptId = id)
        return state.copy(profiles = state.profiles.map { if (it.id == id) it.copy(name = name) else it })
    }

    /** Deleting the active profile keeps its sound by moving it into Custom. */
    fun deleteProfile(state: EqState, id: String): EqState {
        val target = state.profiles.firstOrNull { it.id == id } ?: return state
        val remaining = state.profiles.filterNot { it.id == id }
        return if (state.activeId == id) {
            state.copy(profiles = remaining, activeId = EqState.CUSTOM_ID, customGains = target.gains, gains = target.gains)
        } else {
            state.copy(profiles = remaining)
        }
    }

    /** Repairs anything inconsistent (e.g. from old or corrupted storage). */
    fun sanitize(state: EqState): EqState {
        val profiles = state.profiles
            .filter { it.id.isNotBlank() && it.id != EqState.CUSTOM_ID && BuiltInPreset.byId(it.id) == null }
            .distinctBy { it.id }
            .take(EqState.MAX_PROFILES)
            .map { it.copy(name = cleanName(it.name).ifEmpty { "Profile" }, gains = EqBands.normalizeGains(it.gains)) }
        val custom = EqBands.normalizeGains(state.customGains)
        val base = state.copy(profiles = profiles, customGains = custom, gains = EqBands.normalizeGains(state.gains))
        val known = BuiltInPreset.byId(base.activeId) != null || base.activeId == EqState.CUSTOM_ID ||
            profiles.any { it.id == base.activeId }
        // The active curve is always re-derived from its source so it cannot disagree with it.
        return selectPreset(if (known) base else base.copy(activeId = BuiltInPreset.NEUTRAL.id), if (known) base.activeId else BuiltInPreset.NEUTRAL.id)
    }

    fun cleanName(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(Regex("\\s+"), " ").take(EqState.MAX_NAME_LENGTH).trim()

    private fun uniqueName(state: EqState, name: String, exceptId: String?): String {
        val taken = state.profiles.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet() +
            BuiltInPreset.entries.map { it.label.lowercase() } + "custom"
        if (name.lowercase() !in taken) return name
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = name.take(EqState.MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }
}
