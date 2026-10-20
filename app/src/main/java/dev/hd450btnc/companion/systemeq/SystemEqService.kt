package dev.hd450btnc.companion.systemeq

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.hd450btnc.companion.CompanionApp
import dev.hd450btnc.companion.Notifications

/**
 * Keeps a context-registered receiver for audio-session announcements alive while the user has
 * System-wide EQ switched on. (Since Android 8, manifest receivers don't get these implicit
 * broadcasts, so a running, visible service is required.) Holds no audio and does no capture.
 */
class SystemEqService : Service() {
    private val controller get() = (application as CompanionApp).controller

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> controller.systemEq.attach(session, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> controller.systemEq.detach(session)
            }
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.setSystemEqEnabled(false) // stops this service
            return START_NOT_STICKY
        }
        try {
            Notifications.ensureChannels(this)
            val n = Notifications.build(
                this, Notifications.CHANNEL_SYSTEM_EQ,
                "System-wide EQ on",
                "Applies your EQ to apps that announce their audio session. Not all apps do.",
                SystemEqService::class.java, ACTION_STOP,
            )
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID_SYSTEM_EQ, n, type)
        } catch (e: Exception) {
            controller.systemEq.setServiceRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            // Sent by other apps, so the receiver must be exported.
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        controller.onSystemEqServiceStarted()
        // Not sticky: a background restart can't legally re-enter the foreground on Android 12+;
        // the app restarts the service when it is opened and the setting is on.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
            registered = false
        }
        controller.systemEq.releaseAll()
        controller.systemEq.setServiceRunning(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.hd450btnc.companion.systemeq.START"
        const val ACTION_STOP = "dev.hd450btnc.companion.systemeq.STOP"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, SystemEqService::class.java).setAction(ACTION_START))

        fun stop(context: Context) = context.stopService(Intent(context, SystemEqService::class.java))
    }
}
