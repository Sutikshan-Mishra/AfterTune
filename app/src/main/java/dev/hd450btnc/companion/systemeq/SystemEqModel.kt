package dev.hd450btnc.companion.systemeq

import dev.hd450btnc.companion.dsp.EqResponse
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Maps the app's 10-band curve onto Android's effect APIs. The curve itself (and the headroom) come
 * from [EqResponse], so other apps get the same shape as the in-app player, approximated by the
 * band structure each platform effect offers.
 */
object SystemEqMapping {
    /** DynamicsProcessing pre-EQ bands: contiguous regions, each with an upper cutoff and a gain. */
    const val DYNAMICS_BANDS = 24

    data class Band(val cutoffHz: Float, val gainDb: Float)

    /** Log-spaced band edges from 20 Hz to 20 kHz; edge[i+1] is band i's cutoff. */
    fun edges(count: Int = DYNAMICS_BANDS): DoubleArray = DoubleArray(count + 1) { i ->
        exp(ln(EqResponse.MIN_FREQ) + i.toDouble() / count * (ln(EqResponse.MAX_FREQ) - ln(EqResponse.MIN_FREQ)))
    }

    /** Each band's gain is the true curve at the band's geometric centre. */
    fun dynamicsBands(gainsDb: List<Float>, count: Int = DYNAMICS_BANDS): List<Band> {
        val e = edges(count)
        return List(count) { i ->
            val centre = sqrt(e[i] * e[i + 1])
            Band(cutoffHz = e[i + 1].toFloat(), gainDb = EqResponse.responseDb(gainsDb, centre).toFloat())
        }
    }

    /**
     * Levels in millibels for the legacy android.media.audiofx.Equalizer (fixed, device-defined bands).
     * That effect has no preamp, so headroom is emulated by shifting every band down by [preampDb].
     */
    fun legacyLevelsMb(gainsDb: List<Float>, centresMilliHz: IntArray, minMb: Int, maxMb: Int, preampDb: Float): IntArray =
        IntArray(centresMilliHz.size) { i ->
            val hz = centresMilliHz[i] / 1000.0
            val db = if (hz <= 0.0) 0.0 else EqResponse.responseDb(gainsDb, hz) + preampDb
            (db * 100).roundToInt().coerceIn(minMb, maxMb)
        }
}

enum class Mechanism(val label: String) {
    DYNAMICS("DynamicsProcessing"),
    LEGACY_EQ("Equalizer (legacy)"),
}

/** One external audio session the EQ is attached to. */
data class AttachedSession(
    val sessionId: Int,
    val packageName: String,
    val mechanism: Mechanism?,
    val hasControl: Boolean,
    val error: String? = null,
) {
    val working: Boolean get() = mechanism != null && hasControl && error == null
}

enum class GlobalMixState { OFF, CREATED_UNVERIFIED, REJECTED }

data class SystemEqState(
    /** User asked for system-wide EQ. */
    val requested: Boolean = false,
    /** The foreground service holding the session receiver is running. */
    val serviceRunning: Boolean = false,
    val sessions: List<AttachedSession> = emptyList(),
    val global: GlobalMixState = GlobalMixState.OFF,
    val globalMessage: String? = null,
    /** EQ on/off as applied to the attached effects (mirrors the in-app EQ switch). */
    val eqEnabled: Boolean = true,
) {
    val workingCount: Int get() = sessions.count { it.working }

    /** One-line truthful summary for the UI; never reports success that wasn't observed. */
    val summary: String
        get() = when {
            !requested -> "Off — only this app's player is equalized."
            !serviceRunning -> "Starting…"
            sessions.isEmpty() && global != GlobalMixState.CREATED_UNVERIFIED ->
                "Waiting: no player has announced an audio session yet. Start or restart playback in the other app."
            workingCount > 0 && !eqEnabled -> "Attached to $workingCount session(s), EQ currently switched off."
            workingCount > 0 -> "Attached to $workingCount session(s). Listen to confirm the change."
            sessions.isNotEmpty() && sessions.all { it.mechanism != null && !it.hasControl } ->
                "Another app or the phone's own sound effects has control of these sessions."
            sessions.isNotEmpty() -> "Could not attach to the announced session(s). See details below."
            else -> "Global output effect created; whether it is audible depends on the phone."
        }
}

/** Pure state updates for [SystemEqState]. */
object SystemEqReducer {
    fun upsert(s: SystemEqState, session: AttachedSession): SystemEqState =
        s.copy(sessions = s.sessions.filterNot { it.sessionId == session.sessionId } + session)

    fun remove(s: SystemEqState, sessionId: Int): SystemEqState =
        s.copy(sessions = s.sessions.filterNot { it.sessionId == sessionId })

    fun control(s: SystemEqState, sessionId: Int, granted: Boolean): SystemEqState =
        s.copy(sessions = s.sessions.map { if (it.sessionId == sessionId) it.copy(hasControl = granted) else it })

    /** Sessions announced by this app itself are skipped: its player already runs the in-app EQ. */
    fun shouldAttach(sessionId: Int, packageName: String?, ownPackage: String): Boolean =
        sessionId > 0 && packageName != ownPackage
}
