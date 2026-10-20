package dev.hd450btnc.companion.artwork

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Pure decisions for the external-artwork feature (unit tested). */
object ExternalArtworkRules {
    class Candidate(val packageName: String, val playing: Boolean, val hasTrack: Boolean)

    /**
     * Which external session to follow: never our own, never one without a track; a playing session beats
     * a paused one, otherwise the first (most recently active) wins. Returns the index or -1.
     */
    fun pick(candidates: List<Candidate>, ownPackage: String): Int {
        var firstEligible = -1
        for ((i, c) in candidates.withIndex()) {
            if (c.packageName == ownPackage || !c.hasTrack) continue
            if (c.playing) return i
            if (firstEligible < 0) firstEligible = i
        }
        return firstEligible
    }

    /** In-app art wins while the in-app player is playing (or when nothing external is available). */
    fun preferInApp(inAppReady: Boolean, inAppPlaying: Boolean, externalReady: Boolean): Boolean = when {
        !externalReady -> true
        !inAppReady -> false
        else -> inAppPlaying
    }

    /** What the toggle should be after returning from (or resuming without) the access screen. */
    fun reconcile(enabled: Boolean, awaitingAccess: Boolean, granted: Boolean): Boolean = when {
        awaitingAccess -> granted
        else -> enabled && granted
    }
}

/**
 * Follows the current track of other apps' media sessions (Spotify, OuterTune, ...) and publishes its
 * picture as an [ArtworkState]. Needs notification-listener access; without it nothing is read.
 */
class ExternalArtworkController(private val context: Context, private val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    private val component = ComponentName(context, ExternalMediaListener::class.java)
    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val session = ArtworkSession()
    private var job: Job? = null
    private var running = false
    private var controllers: List<MediaController> = emptyList()
    private var shownKey: String? = null

    private val _state = MutableStateFlow<ArtworkState>(ArtworkState.None)
    val state: StateFlow<ArtworkState> = _state.asStateFlow()

    fun hasAccess(): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (e: Exception) {
        false
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = reselect()
        override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
        override fun onSessionDestroyed() = reselect()
    }

    fun start() {
        if (running) return
        val m = manager ?: return
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            running = true
            attach(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            stop()
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        if (running) {
            try {
                manager?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
        }
        running = false
        detach()
        session.begin()
        job?.cancel()
        shownKey = null
        _state.value = ArtworkState.None
    }

    private fun detach() {
        controllers.forEach {
            try {
                it.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        controllers = emptyList()
    }

    private fun attach(list: List<MediaController>) {
        detach()
        controllers = list.filter { it.packageName != context.packageName }
        controllers.forEach {
            try {
                it.registerCallback(callback, main)
            } catch (_: Exception) {
            }
        }
        reselect()
    }

    private fun reselect() {
        if (!running) return
        val candidates = controllers.map {
            val md = try { it.metadata } catch (_: Exception) { null }
            ExternalArtworkRules.Candidate(
                it.packageName,
                playing = it.playbackState?.state == PlaybackState.STATE_PLAYING,
                hasTrack = md != null,
            )
        }
        val index = ExternalArtworkRules.pick(candidates, context.packageName)
        if (index < 0) {
            clear()
            return
        }
        val controller = controllers[index]
        val md = try { controller.metadata } catch (_: Exception) { null }
        if (md == null) {
            clear()
            return
        }
        // Apps often publish the title first and the picture a moment later: the picture's presence is part of the key.
        val key = trackKey(controller.packageName, md) + "|" + artSignature(md)
        if (key == shownKey) return // same track and picture: no flicker on play/pause
        shownKey = key
        load(key, md)
    }

    private fun clear() {
        session.begin()
        job?.cancel()
        shownKey = null
        _state.value = ArtworkState.None
    }

    private fun trackKey(pkg: String, md: MediaMetadata): String {
        val id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        return "$pkg|${id ?: "$title|$artist|$album"}"
    }

    private fun artSignature(md: MediaMetadata): String {
        if (firstBitmap(md) != null) return "bitmap"
        val uri = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).firstNotNullOfOrNull { md.getString(it) }
        return uri ?: "none"
    }

    private fun load(key: String, md: MediaMetadata) {
        val token = session.begin()
        job?.cancel()
        // Art that is already in the metadata is used right away; only URI art needs a background read.
        val direct = firstBitmap(md)
        if (direct != null) {
            publish(key, direct)
            return
        }
        _state.value = ArtworkState.None // never keep the previous track's art while a new one loads
        val uris = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).mapNotNull { md.getString(it) }
        if (uris.isEmpty()) return
        job = scope.launch {
            val bmp = withContext(Dispatchers.IO) { uris.firstNotNullOfOrNull { decodeUri(it) } }
            if (!session.isCurrent(token) || bmp == null) return@launch
            publish(key, bmp)
        }
    }

    private fun firstBitmap(md: MediaMetadata): Bitmap? = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
    ).firstNotNullOfOrNull { k -> try { md.getBitmap(k) } catch (_: Exception) { null } }

    private fun decodeUri(raw: String): Bitmap? {
        val uri = Uri.parse(raw)
        if (uri.scheme != "content" && uri.scheme != "file") return null // no network access
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_PX * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun publish(key: String, source: Bitmap) {
        val prepared = prepare(source)
        _state.value = if (prepared == null) {
            ArtworkState.None
        } else {
            ArtworkState.Ready(
                key,
                prepared.small.asImageBitmap(),
                prepared.strength,
                prepared.accent,
                prepared.cover.asImageBitmap(),
            )
        }
    }

    private class Prepared(val small: Bitmap, val strength: Float, val accent: Int?, val cover: Bitmap)

    /** Tiny copy, strength, accent and a sharper copy, or null when the picture is unusable. The source is never recycled. */
    private fun prepare(source: Bitmap): Prepared? {
        return try {
            if (source.width <= 0 || source.height <= 0) return null
            val scale = TARGET_PX.toFloat() / max(source.width, source.height)
            val small = Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * scale).toInt()),
                max(1, (source.height * scale).toInt()),
                true,
            )
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            val stats = ArtworkRules.stats(px)
            if (!ArtworkRules.usable(stats)) return null
            val coverScale = COVER_PX.toFloat() / max(source.width, source.height)
            val cover = if (coverScale < 1f) {
                Bitmap.createScaledBitmap(source, max(1, (source.width * coverScale).toInt()), max(1, (source.height * coverScale).toInt()), true)
            } else {
                source
            }
            Prepared(small, ArtworkRules.strength(stats), ArtworkRules.accent(px), cover)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val TARGET_PX = 40
        const val COVER_PX = 480
    }
}
