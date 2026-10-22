package dev.hd450btnc.companion

import android.app.Application
import dev.hd450btnc.companion.ambient.AmbientEngine
import dev.hd450btnc.companion.ambient.AmbientInput
import dev.hd450btnc.companion.audio.AudioEngine
import dev.hd450btnc.companion.bt.HeadsetMonitor
import dev.hd450btnc.companion.data.PrefsStore
import dev.hd450btnc.companion.eq.EqController
import dev.hd450btnc.companion.eq.EqRepository
import dev.hd450btnc.companion.eq.EqState
import dev.hd450btnc.companion.settings.AppSettings
import dev.hd450btnc.companion.settings.SettingsRepository
import dev.hd450btnc.companion.systemeq.SystemEqController
import dev.hd450btnc.companion.systemeq.SystemEqService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.artwork.ExternalArtworkController
import dev.hd450btnc.companion.artwork.ExternalArtworkRules
import dev.hd450btnc.companion.artwork.ExternalTrack
import dev.hd450btnc.companion.audio.QueueItem
import dev.hd450btnc.companion.audio.Recommendation
import dev.hd450btnc.companion.audio.Recommender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Process-wide owner of app state. Lives in [CompanionApp] so the EQ keeps working for the
 * playback/system-EQ services even when no activity exists, and survives configuration changes.
 * Main thread only.
 */
class AppController(private val app: Application) {
    private val scope = MainScope()
    private val store = PrefsStore(app)
    private val eqRepo = EqRepository(store)
    private val settingsRepo = SettingsRepository(store)

    private val _eq = MutableStateFlow(eqRepo.load())
    val eq: StateFlow<EqState> = _eq.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val library = dev.hd450btnc.companion.audio.Library(app, store, scope)
    val audio = AudioEngine(app, store, scope, library)
    val headset = HeadsetMonitor(app)
    val ambient = AmbientEngine(app)
    val artwork = dev.hd450btnc.companion.artwork.ArtworkController(app, scope)
    val externalArtwork = ExternalArtworkController(app, scope)
    val systemEq = SystemEqController(app)

    /** Whether the UI was following another app on the previous update (so a pause does not hand control back). */
    private var followingExternal = false

    /**
     * What the UI follows: the in-app player, or another app's media session ([external]). For an external
     * source [track] carries its title, artist and progress; [state] is its picture (or none).
     */
    class ShownArtwork(val state: ArtworkState, val external: Boolean, val track: ExternalTrack? = null)

    val shownArtwork: StateFlow<ShownArtwork> = combine(
        artwork.state,
        audio.state.map { it.hasMedia to it.isPlaying }.distinctUntilChanged(),
        externalArtwork.state,
        externalArtwork.track,
    ) { inApp, inAppNow, ext, track ->
        val useExternal = ExternalArtworkRules.useExternal(
            inAppHasMedia = inAppNow.first,
            inAppPlaying = inAppNow.second,
            externalHasTrack = track != null,
            externalPlaying = track?.playing == true,
            followingExternal = followingExternal,
        )
        followingExternal = useExternal
        if (useExternal) ShownArtwork(ext, true, track) else ShownArtwork(inApp, false)
    }.stateIn(scope, SharingStarted.Eagerly, ShownArtwork(ArtworkState.None, false))

    /** Library tracks ranked by how much they resemble what has been listened to; empty until there is history. */
    val recommendations: StateFlow<List<Recommendation>> = combine(
        library.tracks,
        library.external,
        audio.state.map { it.current?.uri }.distinctUntilChanged(),
        audio.state.map { it.queue }.distinctUntilChanged(),
    ) { tracks, heard, currentUri, queue ->
        val queued = queue.mapTo(HashSet()) { it.uri }
        Recommender.rank(tracks, heard, currentUri, System.currentTimeMillis())
            .map { Recommendation(it.track.uri, it.track.title, it.reason, it.track.uri in queued) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun playRecommendation(r: Recommendation) = audio.playTrack(QueueItem(r.uri, r.title, isReference = false))
    fun queueRecommendation(r: Recommendation) = audio.enqueue(QueueItem(r.uri, r.title, isReference = false))

    private var awaitingAccess = false

    /** Session handed to us by a player that opened our effect control panel. */
    private var pendingPanelSession: Pair<Int, String?>? = null

    private val _panelRequest = MutableStateFlow<String?>(null)
    /** Package (or "a player") that opened AfterTune as its equalizer, for an on-screen notice. */
    val panelRequest: StateFlow<String?> = _panelRequest.asStateFlow()

    private var saveJob: Job? = null

    init {
        externalArtwork.onListen = { t -> library.recordExternal(t.artist) }
        scope.launch {
            audio.state
                .map { it.current }
                .distinctUntilChangedBy { it?.uri }
                .collect { artwork.onTrack(it) }
        }
        audio.setEqParams(_eq.value.toParams())
        systemEq.apply(_eq.value.toParams())
        systemEq.setRequested(_settings.value.systemEqEnabled)
        applySettings(_settings.value)
        reconcileExternalArtwork()
    }

    fun updateEq(transform: (EqState) -> EqState) {
        val next = transform(_eq.value)
        if (next == _eq.value) return
        _eq.value = next
        val params = next.toParams()
        audio.setEqParams(params)
        systemEq.apply(params)
        // Slider drags emit many updates; persist once they settle.
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            eqRepo.save(_eq.value)
        }
    }

    fun selectPreset(id: String) = updateEq { EqController.selectPreset(it, id) }
    fun setBand(index: Int, gainDb: Float) = updateEq { EqController.setBand(it, index, gainDb) }
    fun resetCurve() = updateEq(EqController::reset)
    fun setEnabled(on: Boolean) = updateEq { EqController.setEnabled(it, on) }
    fun setAutoHeadroom(on: Boolean) = updateEq { EqController.setAutoHeadroom(it, on) }
    fun saveProfile(name: String) = updateEq { EqController.saveProfile(it, name, UUID.randomUUID().toString()) }
    fun renameProfile(id: String, name: String) = updateEq { EqController.renameProfile(it, id, name) }
    fun deleteProfile(id: String) = updateEq { EqController.deleteProfile(it, id) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        settingsRepo.save(next)
        applySettings(next)
    }

    // ---- External media artwork ----

    /** Switch off right away; switch on only once notification access exists (otherwise [launchAccess] runs). */
    fun requestExternalArtwork(on: Boolean, launchAccess: () -> Boolean) {
        if (!on) {
            awaitingAccess = false
            updateSettings { it.copy(externalArtwork = false) }
        } else if (externalArtwork.hasAccess()) {
            updateSettings { it.copy(externalArtwork = true) }
        } else {
            awaitingAccess = launchAccess() // stays off until the user comes back with access granted
        }
    }

    /** Call when the app returns to the foreground: applies a fresh grant and drops the toggle if access was revoked. */
    fun reconcileExternalArtwork() {
        val granted = externalArtwork.hasAccess()
        val next = ExternalArtworkRules.reconcile(_settings.value.externalArtwork, awaitingAccess, granted)
        awaitingAccess = false
        updateSettings { it.copy(externalArtwork = next) }
        if (next) externalArtwork.start() else externalArtwork.stop()
    }

    // ---- System-wide EQ ----

    fun setSystemEqEnabled(on: Boolean) {
        updateSettings { it.copy(systemEqEnabled = on) }
        systemEq.setRequested(on)
        if (on) startSystemEqService() else SystemEqService.stop(app)
    }

    fun setGlobalMixExperimental(on: Boolean) {
        updateSettings { it.copy(globalMixExperimental = on) }
        if (systemEq.state.value.serviceRunning) systemEq.setGlobalExperimental(on)
    }

    /** Called from the visible activity, where starting a foreground service is allowed. */
    fun ensureSystemEqService() {
        if (_settings.value.systemEqEnabled && !systemEq.state.value.serviceRunning) startSystemEqService()
    }

    private fun startSystemEqService() {
        try {
            SystemEqService.start(app)
        } catch (e: Exception) {
            systemEq.setServiceRunning(false)
        }
    }

    fun onSystemEqServiceStarted() {
        systemEq.setServiceRunning(true)
        systemEq.apply(_eq.value.toParams())
        systemEq.setGlobalExperimental(_settings.value.globalMixExperimental)
        pendingPanelSession?.let { (id, pkg) -> systemEq.attach(id, pkg) }
        pendingPanelSession = null
    }

    /** A player opened AfterTune via ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL with its session. */
    fun onEffectPanelRequest(sessionId: Int, packageName: String?) {
        _panelRequest.value = packageName ?: "a player"
        if (sessionId <= 0) return
        if (systemEq.state.value.serviceRunning) systemEq.attach(sessionId, packageName)
        else pendingPanelSession = sessionId to packageName
    }

    fun dismissPanelRequest() {
        _panelRequest.value = null
    }

    // ---- Ambient ----

    fun setAmbientInput(input: AmbientInput) = updateSettings { it.copy(ambientInput = input) }

    /** Writes pending changes immediately (called when the UI goes to the background). */
    fun flush() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            eqRepo.save(_eq.value)
        }
    }

    /** Restores defaults: stops ambient and system EQ, removes profiles, custom curve, settings and the queue. */
    fun resetAll() {
        ambient.stop("Stopped: app data was reset.")
        setSystemEqEnabled(false)
        saveJob?.cancel()
        eqRepo.clear()
        settingsRepo.clear()
        audio.clearQueue()
        library.clear()
        _eq.value = EqState()
        _settings.value = AppSettings()
        audio.setEqParams(_eq.value.toParams())
        systemEq.apply(_eq.value.toParams())
        applySettings(_settings.value)
    }

    private fun applySettings(s: AppSettings) {
        audio.setRepeat(s.repeatQueue)
        audio.setPauseOnDisconnect(s.pauseOnDisconnect)
        if (s.externalArtwork) externalArtwork.start() else externalArtwork.stop()
    }
}
