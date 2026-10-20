package dev.hd450btnc.companion.ambient

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.hd450btnc.companion.CompanionApp
import dev.hd450btnc.companion.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service (type "microphone") that owns ambient capture while it runs, so it continues
 * with the screen off and is always visible as a notification with a Stop action.
 * The engine stops when this service is destroyed for any reason.
 */
class AmbientService : Service() {
    private val scope = MainScope()
    private var watcher: Job? = null
    private val engine get() = (application as CompanionApp).controller.ambient

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stop("Stopped from the notification.")
            finish()
            return START_NOT_STICKY
        }
        val input = intent?.getStringExtra(EXTRA_INPUT)?.let { v -> AmbientInput.entries.firstOrNull { it.name == v } }
            ?: AmbientInput.PHONE_MIC
        try {
            Notifications.ensureChannels(this)
            val n = Notifications.build(
                this, Notifications.CHANNEL_AMBIENT,
                "Ambient listening on",
                if (input == AmbientInput.HEADSET_MIC) "Headset microphone (hands-free link). Not hearing protection."
                else "Phone microphone to your headphones. Not hearing protection.",
                AmbientService::class.java, ACTION_STOP,
            )
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, Notifications.ID_AMBIENT, n, type)
        } catch (e: Exception) {
            engine.reportFailure("Android did not allow the microphone service to start (${e.javaClass.simpleName}). Open the app and try again.")
            finish()
            return START_NOT_STICKY
        }
        engine.start(input)
        watcher?.cancel()
        watcher = scope.launch {
            engine.status.first { !it.isActive } // stopped, failed, or device removed
            finish()
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (engine.status.value.isActive) engine.stop("Ambient service ended.")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.hd450btnc.companion.ambient.START"
        const val ACTION_STOP = "dev.hd450btnc.companion.ambient.STOP"
        const val EXTRA_INPUT = "input"

        fun start(context: Context, input: AmbientInput) {
            val i = Intent(context, AmbientService::class.java).setAction(ACTION_START).putExtra(EXTRA_INPUT, input.name)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AmbientService::class.java).setAction(ACTION_STOP))
        }
    }
}
