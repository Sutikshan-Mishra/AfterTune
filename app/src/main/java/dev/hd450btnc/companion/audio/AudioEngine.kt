package dev.hd450btnc.companion.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.hd450btnc.companion.dsp.EqParams
import dev.hd450btnc.companion.dsp.PinkNoise
import dev.hd450btnc.companion.eq.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class QueueItem(val uri: String, val title: String, val isReference: Boolean)

data class PlayerState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val preparingReference: Boolean = false,
    val shuffle: Boolean = false,
) {
    val current: QueueItem? get() = queue.getOrNull(currentIndex)
    val hasMedia: Boolean get() = queue.isNotEmpty()
}

/**
 * The supported EQ scope: audio played by this app. Every decoded sample passes through
 * [EqAudioProcessor] inside ExoPlayer's audio sink before reaching the output (e.g. the headphones).
 * All methods must be called on the main thread.
 */
@OptIn(UnstableApi::class)
class AudioEngine(
    private val context: Context,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
    private val library: Library,
) {
    private val processor = EqAudioProcessor()

    /** Live level/band analysis of in-app playback (read-only). */
    val levels: LevelAnalyzer get() = processor.analyzer

    val player: ExoPlayer = run {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                // DefaultAudioSink bypasses all audio processors when float output is used, which
                // would silently disable the EQ. Always use the integer PCM path (decoders' other PCM
                // encodings are converted to 16-bit before our processor).
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private var queue: List<QueueItem> = emptyList()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var progressJob: Job? = null

    // Listening history: what is being heard, for how long, and its length (feeds the recommendations).
    private var trackedUri: String? = null
    private var trackedDurationMs = 0L
    private var listenedMs = 0L

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
                updateProgressTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                flushListen(naturalEnd = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                startTracking(mediaItem?.mediaId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) flushListen(naturalEnd = true)
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = describe(error), isPlaying = false)
            }
        })
        player.shuffleModeEnabled = store.getString(KEY_SHUFFLE) == "1"
        restoreQueue()
    }

    fun setEqParams(params: EqParams) = processor.setParams(params)

    fun setRepeat(on: Boolean) {
        player.repeatMode = if (on) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        store.putString(KEY_SHUFFLE, if (player.shuffleModeEnabled) "1" else "0")
        publish()
    }

    fun setPauseOnDisconnect(on: Boolean) = player.setHandleAudioBecomingNoisy(on)

    fun togglePlay() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) player.pause() else play()
    }

    fun playAt(index: Int) {
        if (index !in queue.indices) return
        player.seekToDefaultPosition(index)
        play()
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() = player.seekToPrevious()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    fun removeAt(index: Int) {
        if (index !in queue.indices) return
        queue = queue.toMutableList().also { it.removeAt(index) }
        player.removeMediaItem(index)
        persistQueue()
        publish()
    }

    /** Adds one library track to the end of the queue without playing it. */
    fun enqueue(item: QueueItem) {
        if (queue.any { it.uri == item.uri }) return
        append(listOf(item))
    }

    /** Plays a library track: jumps to it when it is queued, otherwise queues it and plays it. */
    fun playTrack(item: QueueItem) {
        val existing = queue.indexOfFirst { it.uri == item.uri }
        if (existing >= 0) {
            playAt(existing)
            return
        }
        val startIndex = queue.size
        append(listOf(item))
        playAt(startIndex)
    }

    fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        persistQueue()
        _state.value = PlayerState(shuffle = player.shuffleModeEnabled)
    }

    /** Adds documents picked with the system file picker (no storage permission needed). */
    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // Not persistable: still playable for this session.
                    }
                    QueueItem(uri.toString(), displayName(uri), isReference = false)
                }
            }
            val startIndex = queue.size
            append(items)
            playAt(startIndex)
        }
    }

    /** Adds every audio file in a folder (and its subfolders) picked with the system folder picker. */
    fun addFolder(tree: Uri) {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Not persistable: still readable for this session.
                }
                FolderScan.audioFiles(context, tree).map { QueueItem(it.uri.toString(), it.title, isReference = false) }
            }
            if (items.isEmpty()) {
                _state.value = _state.value.copy(error = "No audio files in that folder.")
                return@launch
            }
            val startIndex = queue.size
            append(items)
            playAt(startIndex)
        }
    }

    /** Plays the generated pink-noise reference so EQ changes are audible without any music. */
    fun playReference() {
        val existing = queue.indexOfFirst { it.isReference }
        if (existing >= 0) {
            playAt(existing)
            return
        }
        _state.value = _state.value.copy(preparingReference = true)
        scope.launch {
            val file = withContext(Dispatchers.IO) { referenceFile().also { ensureReference(it) } }
            _state.value = _state.value.copy(preparingReference = false)
            val startIndex = queue.size
            append(listOf(QueueItem(Uri.fromFile(file).toString(), REFERENCE_TITLE, isReference = true)))
            playAt(startIndex)
        }
    }

    private fun play() {
        when (player.playbackState) {
            Player.STATE_IDLE -> player.prepare()
            Player.STATE_ENDED -> player.seekToDefaultPosition(0)
        }
        _state.value = _state.value.copy(error = null)
        startService()
        player.play()
    }

    private fun startService() {
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (_: IllegalStateException) {
            // App in background: the running session keeps playback alive anyway.
        }
    }

    private fun startTracking(uri: String?) {
        trackedUri = uri
        trackedDurationMs = 0L
        listenedMs = 0L
    }

    private fun flushListen(naturalEnd: Boolean) {
        val uri = trackedUri ?: return
        val outcome = ListenRules.classify(listenedMs, trackedDurationMs, naturalEnd)
        library.recordListen(uri, outcome, System.currentTimeMillis())
        trackedUri = null
        listenedMs = 0L
    }

    private fun append(items: List<QueueItem>) {
        library.noteAdded(items)
        queue = queue + items
        player.addMediaItems(items.map(::toMediaItem))
        persistQueue()
        publish()
    }

    private fun publish() {
        val duration = player.duration
        if (trackedUri == null && player.isPlaying) startTracking(player.currentMediaItem?.mediaId)
        if (duration != C.TIME_UNSET && trackedUri != null && trackedUri == player.currentMediaItem?.mediaId) {
            trackedDurationMs = duration.coerceAtLeast(0L)
        }
        _state.value = _state.value.copy(
            queue = queue,
            currentIndex = if (player.mediaItemCount == 0) -1 else player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
            error = if (player.isPlaying) null else _state.value.error,
            shuffle = player.shuffleModeEnabled,
        )
    }

    private fun updateProgressTicker() {
        if (player.isPlaying) {
            if (progressJob?.isActive == true) return
            progressJob = scope.launch {
                while (isActive) {
                    if (player.isPlaying) listenedMs += 250L
                    publish()
                    delay(250)
                }
            }
        } else {
            progressJob?.cancel()
            progressJob = null
        }
    }

    private fun toMediaItem(item: QueueItem): MediaItem = MediaItem.Builder()
        .setUri(item.uri)
        .setMediaId(item.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(if (item.isReference) "Reference signal" else null)
                .build(),
        )
        .build()

    private fun displayName(uri: Uri): String {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "Untitled")
    }

    private fun referenceFile() = File(context.filesDir, "reference_pink_noise.wav")

    private fun ensureReference(file: File) {
        if (file.exists() && file.length() > PinkNoise.HEADER_BYTES) return
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(PinkNoise.wav())
        tmp.renameTo(file)
    }

    private fun persistQueue() {
        store.putString(KEY_QUEUE, queue.joinToString("\n") { "${it.uri}\t${it.title.replace('\t', ' ')}\t${if (it.isReference) 1 else 0}" })
    }

    private fun restoreQueue() {
        val raw = store.getString(KEY_QUEUE) ?: return
        val items = raw.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 3 || p[0].isBlank()) return@mapNotNull null
            val ref = p[2] == "1"
            if (ref && !referenceFile().exists()) return@mapNotNull null
            QueueItem(p[0], p[1], ref)
        }.toList()
        if (items.isEmpty()) return
        queue = items
        library.noteAdded(items)
        player.setMediaItems(items.map(::toMediaItem))
        publish()
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> "This file is no longer available. Remove it and add it again."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> "This format can't be played on this phone."
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> "Audio output failed. Check the headphones are connected, then press play."
        else -> "Playback failed (${error.errorCodeName})."
    }

    companion object {
        const val REFERENCE_TITLE = "Pink noise"
        private const val KEY_QUEUE = "player_queue_v1"
        private const val KEY_SHUFFLE = "player_shuffle"
    }
}
