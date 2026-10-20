package dev.hd450btnc.companion

import android.content.Intent
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hd450btnc.companion.settings.ThemeMode
import dev.hd450btnc.companion.ui.AppRoot
import dev.hd450btnc.companion.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {
    private val controller: AppController
        get() = (application as CompanionApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleEffectPanelIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by controller.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            // Keep status/navigation bar icon contrast in sync with the in-app theme choice.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            StudioTheme(dark = dark) {
                AppRoot(controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEffectPanelIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        controller.headset.start()
        // Visible activity: the only place a foreground service may (re)start on Android 12+.
        controller.ensureSystemEqService()
    }

    /** A player's "Equalizer" button opened us as its effect control panel, with its audio session. */
    private fun handleEffectPanelIntent(intent: Intent?) {
        if (intent?.action != AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) return
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: callingPackage
        controller.onEffectPanelRequest(session, pkg)
    }

    override fun onStop() {
        controller.headset.stop()
        controller.flush()
        super.onStop()
    }
}
