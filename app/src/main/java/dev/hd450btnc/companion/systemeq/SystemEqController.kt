package dev.hd450btnc.companion.systemeq

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import dev.hd450btnc.companion.dsp.EqParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Applies the app's EQ to OTHER apps' audio sessions using Android's documented effect-control
 * protocol: a player announces its session (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, or by opening
 * our control panel), and we attach an audio effect to that session.
 *
 * - Android 9+: DynamicsProcessing (24-band pre-EQ from the real curve + input gain for headroom + limiter).
 * - Android 8.x: the legacy Equalizer with the device's fixed bands.
 * - Optional, experimental: the deprecated global output mix (session 0). Creation success is reported,
 *   but whether it is audible depends on the phone and is never assumed.
 *
 * Main thread only.
 */
class SystemEqController(context: Context) {
    private val ownPackage = context.packageName

    private val _state = MutableStateFlow(SystemEqState())
    val state: StateFlow<SystemEqState> = _state.asStateFlow()

    private val effects = mutableMapOf<Int, AudioEffect>()
    private var global: AudioEffect? = null
    private var params: EqParams = EqParams.FLAT

    fun setRequested(on: Boolean) = _state.update { it.copy(requested = on) }

    fun setServiceRunning(on: Boolean) = _state.update { it.copy(serviceRunning = on) }

    fun attach(sessionId: Int, packageName: String?) {
        if (!SystemEqReducer.shouldAttach(sessionId, packageName, ownPackage)) return
        val pkg = packageName ?: "unknown app"
        val existing = effects[sessionId]
        if (existing != null) {
            applyTo(sessionId, existing)
            return
        }
        val (effect, mechanism, error) = create(sessionId)
        if (effect == null) {
            _state.update { SystemEqReducer.upsert(it, AttachedSession(sessionId, pkg, null, false, error)) }
            return
        }
        effects[sessionId] = effect
        effect.setControlStatusListener { _, granted ->
            _state.update { SystemEqReducer.control(it, sessionId, granted) }
            if (granted) applyTo(sessionId, effect)
        }
        _state.update { SystemEqReducer.upsert(it, AttachedSession(sessionId, pkg, mechanism, safeHasControl(effect))) }
        applyTo(sessionId, effect)
    }

    fun detach(sessionId: Int) {
        effects.remove(sessionId)?.let { safeRelease(it) }
        _state.update { SystemEqReducer.remove(it, sessionId) }
    }

    fun apply(newParams: EqParams) {
        params = newParams
        effects.forEach { (id, e) -> applyTo(id, e) }
        global?.let { applyTo(GLOBAL_SESSION, it) }
        _state.update { it.copy(eqEnabled = newParams.enabled) }
    }

    fun setGlobalExperimental(on: Boolean) {
        if (!on) {
            global?.let { safeRelease(it) }
            global = null
            _state.update { it.copy(global = GlobalMixState.OFF, globalMessage = null) }
            return
        }
        if (global != null) return
        val (effect, mechanism, error) = create(GLOBAL_SESSION)
        if (effect == null) {
            _state.update { it.copy(global = GlobalMixState.REJECTED, globalMessage = "This phone refused a global effect: $error") }
        } else {
            global = effect
            applyTo(GLOBAL_SESSION, effect)
            _state.update {
                it.copy(
                    global = GlobalMixState.CREATED_UNVERIFIED,
                    globalMessage = "${mechanism?.label} created on the output mix. Many phones ignore this deprecated path; listen to check.",
                )
            }
        }
    }

    fun releaseAll() {
        effects.values.forEach { safeRelease(it) }
        effects.clear()
        global?.let { safeRelease(it) }
        global = null
        _state.update { it.copy(sessions = emptyList(), global = GlobalMixState.OFF, globalMessage = null) }
    }

    private data class Created(val effect: AudioEffect?, val mechanism: Mechanism?, val error: String?)

    private fun create(sessionId: Int): Created {
        var firstError: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val bands = SystemEqMapping.DYNAMICS_BANDS
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, bands, false, 0, false, 0, true,
                ).setLimiterAllChannelsTo(
                    // Safety limiter: fast attack, -1 dBFS threshold.
                    DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f),
                ).build()
                return Created(DynamicsProcessing(PRIORITY, sessionId, config), Mechanism.DYNAMICS, null)
            } catch (e: Exception) {
                firstError = e.message ?: e.javaClass.simpleName
            }
        }
        return try {
            Created(Equalizer(PRIORITY, sessionId), Mechanism.LEGACY_EQ, null)
        } catch (e: Exception) {
            Created(null, null, listOfNotNull(firstError, e.message ?: e.javaClass.simpleName).joinToString("; "))
        }
    }

    private fun applyTo(sessionId: Int, effect: AudioEffect) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && effect is DynamicsProcessing) {
                val bands = SystemEqMapping.dynamicsBands(params.gainsDb)
                val eq = DynamicsProcessing.Eq(true, true, bands.size)
                bands.forEachIndexed { i, b -> eq.setBand(i, DynamicsProcessing.EqBand(true, b.cutoffHz, b.gainDb)) }
                effect.setPreEqAllChannelsTo(eq)
                effect.setInputGainAllChannelsTo(params.preampDb)
            } else if (effect is Equalizer) {
                val n = effect.numberOfBands.toInt()
                val centres = IntArray(n) { effect.getCenterFreq(it.toShort()) }
                val range = effect.bandLevelRange
                val levels = SystemEqMapping.legacyLevelsMb(params.gainsDb, centres, range[0].toInt(), range[1].toInt(), params.preampDb)
                levels.forEachIndexed { i, l -> effect.setBandLevel(i.toShort(), l.toShort()) }
            }
            effect.setEnabled(params.enabled)
        } catch (e: Exception) {
            if (sessionId != GLOBAL_SESSION) {
                _state.update { s ->
                    s.copy(sessions = s.sessions.map { if (it.sessionId == sessionId) it.copy(error = e.message ?: e.javaClass.simpleName) else it })
                }
            }
        }
    }

    private fun safeHasControl(e: AudioEffect) = try { e.hasControl() } catch (_: Exception) { false }
    private fun safeRelease(e: AudioEffect) = try { e.release() } catch (_: Exception) {}

    companion object {
        const val GLOBAL_SESSION = 0
        /** Above default (0) so the phone's built-in per-app effects don't silently take over. */
        const val PRIORITY = 1000
    }
}
