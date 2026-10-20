package dev.hd450btnc.companion

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Visible, low-importance notifications for the app's foreground services (never covert). */
object Notifications {
    const val CHANNEL_AMBIENT = "ambient"
    const val CHANNEL_SYSTEM_EQ = "system_eq"
    const val ID_AMBIENT = 41
    const val ID_SYSTEM_EQ = 42

    fun ensureChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_AMBIENT, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Ambient listening")
                .setDescription("Shown while the microphone is passed through to your headphones")
                .build(),
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SYSTEM_EQ, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("System-wide EQ")
                .setDescription("Shown while the EQ listens for other apps' audio sessions")
                .build(),
        )
    }

    fun build(
        context: Context,
        channel: String,
        title: String,
        text: String,
        stopService: Class<out Service>,
        stopAction: String,
    ): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context, channel.hashCode(),
            Intent(context, stopService).setAction(stopAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
