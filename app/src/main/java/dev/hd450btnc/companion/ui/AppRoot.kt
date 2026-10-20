package dev.hd450btnc.companion.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.hd450btnc.companion.ui.components.LocalContentBottomInset
import dev.hd450btnc.companion.ui.components.recordBackdrop
import dev.hd450btnc.companion.ui.components.rememberGlassBackdrop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hd450btnc.companion.AppController
import dev.hd450btnc.companion.ui.components.Glyph
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.headphones.HeadphonesActions
import dev.hd450btnc.companion.ui.headphones.HeadphonesScreen
import dev.hd450btnc.companion.ui.player.MiniPlayer
import dev.hd450btnc.companion.ui.player.PlayerActions
import dev.hd450btnc.companion.ui.settings.SettingsActions
import dev.hd450btnc.companion.ui.settings.SettingsScreen
import dev.hd450btnc.companion.ui.sound.SoundActions
import dev.hd450btnc.companion.ui.sound.SoundScreen
import dev.hd450btnc.companion.ui.sound.SystemEqActions
import dev.hd450btnc.companion.ui.ambient.AmbientActions
import dev.hd450btnc.companion.ui.ambient.AmbientScreen
import dev.hd450btnc.companion.ui.ambient.MicPermission
import dev.hd450btnc.companion.ambient.AmbientService
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.artwork.ExternalMediaListener
import dev.hd450btnc.companion.ui.components.NavIcon
import dev.hd450btnc.companion.ui.components.NavIconKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

enum class Destination(val label: String, val glyph: GlyphKind, val icon: NavIconKind) {
    SOUND("Sound", GlyphKind.CURVE, NavIconKind.SOUND),
    AMBIENT("Ambient", GlyphKind.MIC, NavIconKind.AMBIENT),
    HEADPHONES("Headphones", GlyphKind.HEADPHONES, NavIconKind.HEADPHONES),
    SETTINGS("Settings", GlyphKind.SLIDERS, NavIconKind.SETTINGS),
}

@Composable
fun AppRoot(controller: AppController) {
    val c = Studio.colors
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val eq by controller.eq.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val player by controller.audio.state.collectAsStateWithLifecycle()
    val shown by controller.shownArtwork.collectAsStateWithLifecycle()
    val headset by controller.headset.state.collectAsStateWithLifecycle()
    val systemEq by controller.systemEq.state.collectAsStateWithLifecycle()
    val panelRequest by controller.panelRequest.collectAsStateWithLifecycle()
    val ambient by controller.ambient.status.collectAsStateWithLifecycle()
    val ambientPlan by controller.ambient.plan.collectAsStateWithLifecycle()
    var dest by rememberSaveable { mutableStateOf(Destination.SOUND) }

    // --- Launch screen: shown while start-up work runs (Bluetooth state, audio routes), with a short
    // minimum so the credit can be read. No artificial multi-second delay. ---
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launched) return@LaunchedEffect
        val t0 = SystemClock.elapsedRealtime()
        controller.ambient.refreshPlan()
        withTimeoutOrNull(1_500) { controller.headset.state.first { it.ready } }
        val elapsed = SystemClock.elapsedRealtime() - t0
        if (elapsed < MIN_LAUNCH_MS) delay(MIN_LAUNCH_MS - elapsed)
        launched = true
    }
    if (panelRequest != null) LaunchedEffect(panelRequest) { dest = Destination.SOUND }

    BackHandler(enabled = dest != Destination.SOUND) { dest = Destination.SOUND }

    // --- Bluetooth permission (Android 12+), requested only from the Headphones screen. ---
    val prefs = remember(context) { context.getSharedPreferences("companion", Context.MODE_PRIVATE) }
    var asked by remember { mutableStateOf(prefs.getBoolean(KEY_ASKED, false)) }
    var lastDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        lastDenied = !granted
        controller.headset.onPermissionResult()
    }
    val permissionBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && asked && activity != null &&
        !activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) &&
        (lastDenied || !controller.headset.hasConnectPermission())

    // --- Microphone (+ notification on Android 13+) for ambient, requested only when Start is pressed. ---
    var micTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        micTick++
        controller.ambient.refreshPlan()
        controller.reconcileExternalArtwork()
        onPauseOrDispose { }
    }
    var micAsked by remember { mutableStateOf(prefs.getBoolean(KEY_MIC_ASKED, false)) }
    var notifAsked by remember { mutableStateOf(prefs.getBoolean(KEY_NOTIF_ASKED, false)) }
    fun startAmbientService() {
        try {
            AmbientService.start(context, settings.ambientInput)
        } catch (e: Exception) {
            controller.ambient.reportFailure("Android did not allow ambient listening to start (${e.javaClass.simpleName}).")
        }
    }
    val ambientPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        micAsked = true
        notifAsked = true
        prefs.edit().putBoolean(KEY_MIC_ASKED, true).putBoolean(KEY_NOTIF_ASKED, true).apply()
        micTick++
        if (controller.ambient.hasMicPermission()) startAmbientService()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifAsked = true
        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
    }
    fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val micPermission = run {
        micTick // re-evaluate after resume / permission result
        val granted = controller.ambient.hasMicPermission()
        when {
            granted -> MicPermission.GRANTED
            !micAsked -> MicPermission.NOT_ASKED
            activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> MicPermission.BLOCKED
            else -> MicPermission.DENIED
        }
    }

    // --- Audio files via the system picker (no storage permission). ---
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        controller.audio.addUris(uris)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) controller.audio.addFolder(tree)
    }

    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Nothing to open on this device; the screen already explains the state.
        }
    }

    fun openNotificationAccess(): Boolean {
        val listener = ComponentName(context, ExternalMediaListener::class.java)
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString()),
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // try the next, more general screen
            }
        }
        return false
    }

    val playerActions = PlayerActions(
        togglePlay = controller.audio::togglePlay,
        next = controller.audio::next,
        previous = controller.audio::previous,
        seekTo = controller.audio::seekTo,
        playAt = controller.audio::playAt,
        removeAt = controller.audio::removeAt,
        clear = controller.audio::clearQueue,
        addMusic = { pickAudio.launch(arrayOf("audio/*")) },
        addFolder = { pickFolder.launch(null) },
        toggleShuffle = controller.audio::toggleShuffle,
        playReference = controller.audio::playReference,
    )

    // Screen content is recorded as the backdrop of the floating glass mini player and scrolls beneath it.
    val backdrop = rememberGlassBackdrop()
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val contentInset = with(LocalDensity.current) { overlayHeightPx.toDp() }

    if (!launched) {
        LaunchScreen()
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        CompositionLocalProvider(LocalContentBottomInset provides contentInset) {
            AnimatedContent(
                targetState = dest,
                modifier = Modifier.fillMaxSize().statusBarsPadding().recordBackdrop(backdrop),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "destination",
            ) { d ->
                when (d) {
                    Destination.SOUND -> SoundScreen(
                        art = shown.state,
                        levels = controller.audio.levels,
                        playing = player.isPlaying && !shown.external,
                        eq = eq,
                        headset = headset,
                        systemEq = systemEq,
                        globalMix = settings.globalMixExperimental,
                        panelRequest = panelRequest,
                        systemEqActions = SystemEqActions(
                            setEnabled = { on ->
                                if (on && !notificationsGranted() && !notifAsked && Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                controller.setSystemEqEnabled(on)
                            },
                            setGlobalMix = controller::setGlobalMixExperimental,
                            dismissPanelRequest = controller::dismissPanelRequest,
                        ),
                        actions = SoundActions(
                            selectPreset = controller::selectPreset,
                            setBand = controller::setBand,
                            reset = controller::resetCurve,
                            setEnabled = controller::setEnabled,
                            saveProfile = controller::saveProfile,
                            openHeadphones = { dest = Destination.HEADPHONES },
                        ),
                    )
                    Destination.AMBIENT -> AmbientScreen(
                        status = ambient,
                        plan = ambientPlan,
                        selectedInput = settings.ambientInput,
                        accent = (shown.state as? ArtworkState.Ready)?.accent?.let { androidx.compose.ui.graphics.Color(it) },
                        permission = micPermission,
                        actions = AmbientActions(
                            start = {
                                val needed = buildList {
                                    if (!controller.ambient.hasMicPermission()) add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted() && !notifAsked) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                if (needed.isEmpty()) startAmbientService() else ambientPermissions.launch(needed.toTypedArray())
                            },
                            stop = { controller.ambient.stop("Stopped.") },
                            selectInput = controller::setAmbientInput,
                            setGain = controller.ambient::setGain,
                            resetGain = controller.ambient::resetGain,
                            setMuted = controller.ambient::setMuted,
                            openAppSettings = {
                                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            },
                        ),
                    )
                    Destination.HEADPHONES -> HeadphonesScreen(
                        state = headset,
                        permissionBlocked = permissionBlocked,
                        actions = HeadphonesActions(
                            requestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            },
                            openAppSettings = {
                                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            },
                            openBluetoothSettings = { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            refresh = controller.headset::refresh,
                        ),
                    )
                    Destination.SETTINGS -> SettingsScreen(
                        eq = eq,
                        settings = settings,
                        actions = SettingsActions(
                            selectProfile = controller::selectPreset,
                            renameProfile = controller::renameProfile,
                            deleteProfile = controller::deleteProfile,
                            setAutoHeadroom = controller::setAutoHeadroom,
                            updateSettings = controller::updateSettings,
                            setExternalArtwork = { on -> controller.requestExternalArtwork(on, ::openNotificationAccess) },
                            resetAll = controller::resetAll,
                        ),
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { overlayHeightPx = it.height }) {
            MiniPlayer(
                player, eq.enabled, playerActions, backdrop = backdrop,
                art = shown.state, levels = controller.audio.levels, artPlaying = player.isPlaying && !shown.external,
            )
            Spacer(Modifier.height(Space.s))
            BottomBar(dest) { dest = it }
        }
    }
}

@Composable
private fun BottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    val c = Studio.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Destination.entries.forEach { d ->
                val isSel = d == selected
                val tint by animateColorAsState(if (isSel) c.accent else c.textMuted, label = "navTint")
                Column(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .selectable(selected = isSel, role = Role.Tab) { onSelect(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    NavIcon(d.icon, isSel, tint, size = 24.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(d.label, style = StudioType.monoSmall, color = tint)
                }
            }
        }
    }
}

private const val KEY_ASKED = "bt_permission_asked"
private const val KEY_MIC_ASKED = "mic_permission_asked"
private const val KEY_NOTIF_ASKED = "notification_permission_asked"
private const val MIN_LAUNCH_MS = 700L

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
