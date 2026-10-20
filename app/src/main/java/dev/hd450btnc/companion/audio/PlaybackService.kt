package dev.hd450btnc.companion.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.hd450btnc.companion.CompanionApp
import dev.hd450btnc.companion.MainActivity

/**
 * Keeps in-app playback alive with the screen off and provides the media notification.
 * The MediaSession also routes the headset's play/pause/skip buttons (AVRCP) to the player.
 * Exists only while there is something to play; stops itself when the task is removed while paused.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as CompanionApp).controller.audio.player
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // onTaskRemoved: Media3's default stops the service unless playback is ongoing, which is what we want.

    override fun onDestroy() {
        // The player is owned by the app-wide AudioEngine; only the session is released here.
        session?.release()
        session = null
        super.onDestroy()
    }
}
