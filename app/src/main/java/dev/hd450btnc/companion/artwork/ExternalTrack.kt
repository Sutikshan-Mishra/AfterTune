package dev.hd450btnc.companion.artwork

/**
 * What another app's media session says it is playing. Position is a snapshot: [positionMs] was true at
 * [updatedAtMs] (SystemClock.elapsedRealtime), so the current position is derived with [positionAt].
 */
data class ExternalTrack(
    val packageName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAtMs: Long,
    val speed: Float,
    val playing: Boolean,
) {
    /** Key that changes when the track changes (not when it is paused or seeked). */
    val key: String get() = "$packageName|$title|$artist"

    /** Current position in ms, or -1 when the app does not report one. */
    fun positionAt(nowMs: Long): Long = ExternalProgress.positionAt(positionMs, updatedAtMs, speed, playing, nowMs, durationMs)

    /** 0..1, or -1 when position or duration is unknown. */
    fun fractionAt(nowMs: Long): Float {
        val p = positionAt(nowMs)
        return if (p < 0 || durationMs <= 0) -1f else (p.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

object ExternalProgress {
    /** Extrapolates a media-session position snapshot to [nowMs]. Unknown positions (negative) stay unknown. */
    fun positionAt(positionMs: Long, updatedAtMs: Long, speed: Float, playing: Boolean, nowMs: Long, durationMs: Long): Long {
        if (positionMs < 0) return -1L
        val advanced = if (playing) ((nowMs - updatedAtMs).coerceAtLeast(0L) * speed).toLong() else 0L
        val p = (positionMs + advanced).coerceAtLeast(0L)
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }
}

/**
 * Counts how long the current external track has really been playing and reports, exactly once per track,
 * when that crosses [thresholdMs]. Call [update] on every state change and on a slow timer.
 */
class ListenMeter(private val thresholdMs: Long) {
    private var key: String? = null
    private var accumulated = 0L
    private var since = -1L
    private var fired = false

    fun update(key: String?, playing: Boolean, nowMs: Long): Boolean {
        if (key != this.key) {
            this.key = key
            accumulated = 0L
            since = -1L
            fired = false
        }
        if (key == null) return false
        if (playing) {
            if (since < 0) since = nowMs else {
                accumulated += (nowMs - since).coerceAtLeast(0L)
                since = nowMs
            }
        } else if (since >= 0) {
            accumulated += (nowMs - since).coerceAtLeast(0L)
            since = -1L
        }
        if (!fired && accumulated >= thresholdMs) {
            fired = true
            return true
        }
        return false
    }
}
