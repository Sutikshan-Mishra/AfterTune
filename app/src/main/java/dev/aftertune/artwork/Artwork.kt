package dev.aftertune.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.aftertune.audio.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/** Artwork the UI may show. [None] means: no effect at all, pure black. */
sealed interface ArtworkState {
    data object None : ArtworkState

    /** [key] is the track the picture was read from; [strength] 0..1 keeps dark art restrained. */
    class Ready(
        val key: String,
        val bitmap: ImageBitmap,
        val strength: Float,
        /** ARGB of a vivid colour taken from the cover, already lifted to read on black; null = keep the app blue. */
        val accent: Int? = null,
        /** A larger, sharper copy for the queue sheet (the backdrop only ever uses the tiny [bitmap]). */
        val cover: ImageBitmap? = null,
    ) : ArtworkState
}

/** Pure rules about whether a decoded picture is worth showing. */
object ArtworkRules {
    class Stats(val meanLuma: Float, val spread: Float)

    fun stats(argb: IntArray): Stats {
        if (argb.isEmpty()) return Stats(0f, 0f)
        var sum = 0.0
        for (p in argb) sum += luma(p)
        val mean = sum / argb.size
        var varSum = 0.0
        for (p in argb) {
            val d = luma(p) - mean
            varSum += d * d
        }
        return Stats(mean.toFloat(), sqrt(varSum / argb.size).toFloat())
    }

    private fun luma(p: Int): Double {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    }

    /** Black, near-black or perfectly flat pictures give no usable colour: show nothing. */
    fun usable(s: Stats): Boolean = s.meanLuma >= 0.05f && s.spread >= 0.02f

    /**
     * One vivid colour from the picture, for tinting the UI: the heaviest hue family among saturated, bright
     * pixels, lifted so it stays readable on black. Greys and near-black pictures give null.
     */
    fun accent(argb: IntArray): Int? {
        if (argb.isEmpty()) return null
        val buckets = 12
        val weight = DoubleArray(buckets)
        val rs = DoubleArray(buckets)
        val gs = DoubleArray(buckets)
        val bs = DoubleArray(buckets)
        for (p in argb) {
            val r = ((p shr 16) and 0xFF) / 255.0
            val g = ((p shr 8) and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            if (mx < 0.30) continue
            val s = (mx - mn) / mx
            if (s < 0.28) continue
            val w = s * s * mx
            val i = (hue(r, g, b, mx, mn) / 360.0 * buckets).toInt().coerceIn(0, buckets - 1)
            weight[i] += w
            rs[i] += r * w
            gs[i] += g * w
            bs[i] += b * w
        }
        var best = 0
        for (i in 1 until buckets) if (weight[i] > weight[best]) best = i
        if (weight[best] / argb.size < 0.02) return null
        val r = rs[best] / weight[best]
        val g = gs[best] / weight[best]
        val b = bs[best] / weight[best]
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val h = hue(r, g, b, mx, mn)
        val s = (if (mx <= 0.0) 0.0 else (mx - mn) / mx).coerceIn(0.45, 0.85)
        val v = maxOf(mx, 0.80)
        return hsvToArgb(h, s, v)
    }

    private fun hue(r: Double, g: Double, b: Double, mx: Double, mn: Double): Double {
        val d = mx - mn
        if (d <= 0.0) return 0.0
        val h = when (mx) {
            r -> ((g - b) / d).mod(6.0)
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        } * 60.0
        return if (h < 0) h + 360.0 else h
    }

    private fun hsvToArgb(h: Double, s: Double, v: Double): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60.0) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60.0).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun ch(d: Double) = ((d + m) * 255.0).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Dim pictures stay restrained, bright ones never exceed full strength. */
    fun strength(s: Stats): Float = (s.meanLuma * 2.5f).coerceIn(0.4f, 1f)
}

/**
 * Makes sure only the answer for the CURRENT track is ever applied. [begin] is called for every track
 * change (including "no track"); a result is accepted only if its token is still the latest one.
 */
class ArtworkSession {
    private var latest = 0L

    fun begin(): Long = ++latest

    fun isCurrent(token: Long): Boolean = token == latest
}

/**
 * Reads the picture embedded in the playing file, off the main thread, and publishes it only when it belongs
 * to the current track. Nothing is generated, guessed from the title, or taken from the app icon; the pink
 * noise reference and unreadable files give [ArtworkState.None]. No network is involved.
 */
class ArtworkController(private val context: Context, private val scope: CoroutineScope) {
    private val session = ArtworkSession()
    private var job: Job? = null

    private class Entry(val bitmap: Bitmap?, val strength: Float, val accent: Int? = null, val cover: Bitmap? = null)

    // Small and bounded; includes negative results so a file without art is not re-read.
    private val cache = LruCache<String, Entry>(8)

    private val _state = MutableStateFlow<ArtworkState>(ArtworkState.None)
    val state: StateFlow<ArtworkState> = _state.asStateFlow()

    fun onTrack(item: QueueItem?) {
        val token = session.begin()
        job?.cancel()
        _state.value = ArtworkState.None // never keep the previous track's art while the new one loads
        if (item == null || item.isReference) return

        val cached = cache.get(item.uri)
        if (cached != null) {
            publish(item.uri, cached)
            return
        }
        job = scope.launch {
            val entry = withContext(Dispatchers.IO) { decode(item.uri) }
            if (!session.isCurrent(token)) return@launch // stale: another track started meanwhile
            cache.put(item.uri, entry)
            publish(item.uri, entry)
        }
    }

    private fun publish(key: String, entry: Entry) {
        val bmp = entry.bitmap
        _state.value = if (bmp == null) {
            ArtworkState.None
        } else {
            ArtworkState.Ready(key, bmp.asImageBitmap(), entry.strength, entry.accent, entry.cover?.asImageBitmap())
        }
    }

    private fun decode(uri: String): Entry {
        val none = Entry(null, 0f)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            val bytes = retriever.embeddedPicture ?: return none
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return none
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_PX * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return none
            val scale = TARGET_PX.toFloat() / max(decoded.width, decoded.height)
            val small = Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * scale).toInt()),
                max(1, (decoded.height * scale).toInt()),
                true,
            )
            if (small !== decoded) decoded.recycle()
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            val stats = ArtworkRules.stats(px)
            return if (ArtworkRules.usable(stats)) {
                Entry(small, ArtworkRules.strength(stats), ArtworkRules.accent(px), decodeCover(bytes, bounds))
            } else {
                none
            }
        } catch (e: Exception) {
            return none
        } catch (e: OutOfMemoryError) {
            return none
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /** A sharper copy (at most [COVER_PX]) for the queue sheet; null if it cannot be decoded. */
    private fun decodeCover(bytes: ByteArray, bounds: BitmapFactory.Options): Bitmap? {
        return try {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val TARGET_PX = 40 // tiny on purpose: it is shown very blurred
        const val COVER_PX = 480
    }
}
