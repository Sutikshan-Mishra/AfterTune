package dev.hd450btnc.companion.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dev.hd450btnc.companion.eq.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow

/** A track the user has added to the player, with what has been learned about how they listen to it. */
data class LibraryTrack(
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val plays: Int = 0,
    val completions: Int = 0,
    val skips: Int = 0,
    val lastPlayedAt: Long = 0L,
    val tagged: Boolean = false,
)

enum class ListenOutcome { IGNORED, SKIP, PARTIAL, COMPLETE }

/** How one listen is judged when the track ends or the user leaves it. Pure, unit tested. */
object ListenRules {
    const val MIN_COUNTED_MS = 8_000L
    const val SKIP_BELOW_MS = 20_000L

    fun classify(listenedMs: Long, durationMs: Long, naturalEnd: Boolean): ListenOutcome = when {
        naturalEnd && listenedMs >= MIN_COUNTED_MS -> ListenOutcome.COMPLETE
        durationMs > 0 && listenedMs >= durationMs * 8 / 10 -> ListenOutcome.COMPLETE
        listenedMs < MIN_COUNTED_MS -> ListenOutcome.IGNORED
        listenedMs < SKIP_BELOW_MS && (durationMs <= 0 || listenedMs < durationMs / 2) -> ListenOutcome.SKIP
        else -> ListenOutcome.PARTIAL
    }
}

data class Recommendation(val uri: String, val title: String, val reason: String, val inQueue: Boolean)

/**
 * Ranks the user's own library by how much it resembles what they actually listen to. No network and no
 * model: tracks are compared through artist, album and genre tags (read from the files) and through names
 * heard in other apps. Nothing is recommended until there is some listening to learn from.
 */
object Recommender {
    private const val HALF_LIFE_DAYS = 30.0
    private const val DAY_MS = 86_400_000.0

    /** How much a track's own history says the user likes it (can be negative for repeated skips). */
    fun weight(t: LibraryTrack, now: Long): Double {
        val partial = (t.plays - t.completions - t.skips).coerceAtLeast(0)
        val raw = t.completions * 1.0 + partial * 0.4 - t.skips * 0.8
        if (raw <= 0.0) return raw
        val ageDays = if (t.lastPlayedAt <= 0L) 0.0 else ((now - t.lastPlayedAt) / DAY_MS).coerceAtLeast(0.0)
        return raw * 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    class Taste(val artists: Map<String, Double>, val albums: Map<String, Double>, val genres: Map<String, Double>) {
        val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && genres.isEmpty()
    }

    fun norm(s: String): String = s.trim().lowercase()

    fun taste(library: List<LibraryTrack>, external: Map<String, Int>, now: Long): Taste {
        val artists = HashMap<String, Double>()
        val albums = HashMap<String, Double>()
        val genres = HashMap<String, Double>()
        for (t in library) {
            val w = weight(t, now)
            if (w <= 0.0) continue
            norm(t.artist).takeIf { it.isNotEmpty() }?.let { artists.merge(it, w, { a, b -> a + b }) }
            norm(t.album).takeIf { it.isNotEmpty() }?.let { albums.merge(it, w, { a, b -> a + b }) }
            norm(t.genre).takeIf { it.isNotEmpty() }?.let { genres.merge(it, w, { a, b -> a + b }) }
        }
        for ((a, n) in external) {
            val key = norm(a)
            if (key.isNotEmpty() && n > 0) artists.merge(key, n.coerceAtMost(50).toDouble(), { a, b -> a + b })
        }
        return Taste(artists, albums, genres)
    }

    private fun share(map: Map<String, Double>, key: String): Double {
        if (key.isEmpty()) return 0.0
        val top = map.values.maxOrNull() ?: return 0.0
        if (top <= 0.0) return 0.0
        return ((map[key] ?: 0.0) / top).coerceIn(0.0, 1.0)
    }

    /** Names heard in other apps also match tracks whose file title contains them ("Artist - Song"). */
    private fun artistScore(t: LibraryTrack, taste: Taste): Double {
        val direct = share(taste.artists, norm(t.artist))
        if (direct > 0.0 || taste.artists.isEmpty()) return direct
        val title = norm(t.title)
        var best = 0.0
        val top = taste.artists.values.maxOrNull() ?: return 0.0
        for ((a, w) in taste.artists) {
            if (a.length >= 3 && title.contains(a)) best = max(best, (w / top).coerceIn(0.0, 1.0))
        }
        return best
    }

    class Scored(val track: LibraryTrack, val score: Double, val reason: String)

    fun rank(
        library: List<LibraryTrack>,
        external: Map<String, Int>,
        currentUri: String?,
        now: Long,
        limit: Int = 30,
    ): List<Scored> {
        val taste = taste(library, external, now)
        if (taste.isEmpty) return emptyList()
        val topOwn = library.maxOfOrNull { weight(it, now) }?.takeIf { it > 0.0 } ?: 1.0
        val out = ArrayList<Scored>()
        for (t in library) {
            if (t.uri == currentUri) continue
            val artist = artistScore(t, taste)
            val album = share(taste.albums, norm(t.album))
            val genre = share(taste.genres, norm(t.genre))
            val own = (weight(t, now) / topOwn).coerceIn(-1.0, 1.0)
            var score = 0.45 * artist + 0.20 * album + 0.15 * genre + 0.10 * own
            if (score < 0.08) continue // nothing about it resembles what you listen to
            if (t.plays == 0) score += 0.15 // unheard tracks that match are worth surfacing
            if (t.skips >= 2 && t.completions == 0) score -= 0.5
            if (t.lastPlayedAt > 0L && now - t.lastPlayedAt < 30 * 60_000L) score -= 0.3 // just heard it
            if (score <= 0.1) continue
            val reason = when {
                artist >= 0.3 && artist >= album && artist >= genre -> if (t.artist.isNotBlank()) "You play ${t.artist}" else "Matches artists you play"
                album >= 0.3 && album >= genre -> "From an album you play"
                genre >= 0.3 -> "Like the ${t.genre} you play"
                own >= 0.5 -> "You often finish this"
                t.plays == 0 -> "Not played yet"
                else -> "Close to your recent listening"
            }
            out.add(Scored(t, score, reason))
        }
        out.sortByDescending { it.score }
        return if (out.size > limit) out.subList(0, limit).toList() else out
    }
}

/** Text codec for the persisted library (one track per line, tab separated). */
object LibraryCodec {
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun encode(tracks: List<LibraryTrack>): String = tracks.joinToString("\n") {
        listOf(
            clean(it.uri), clean(it.title), clean(it.artist), clean(it.album), clean(it.genre),
            it.plays, it.completions, it.skips, it.lastPlayedAt, if (it.tagged) 1 else 0,
        ).joinToString("\t")
    }

    fun decode(raw: String?): List<LibraryTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 10 || p[0].isBlank()) return@mapNotNull null
            LibraryTrack(
                uri = p[0], title = p[1], artist = p[2], album = p[3], genre = p[4],
                plays = p[5].toIntOrNull() ?: 0, completions = p[6].toIntOrNull() ?: 0, skips = p[7].toIntOrNull() ?: 0,
                lastPlayedAt = p[8].toLongOrNull() ?: 0L, tagged = p[9] == "1",
            )
        }.toList()
    }

    fun encodeExternal(map: Map<String, Int>): String = map.entries.joinToString("\n") { "${clean(it.key)}\t${it.value}" }

    fun decodeExternal(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        raw.lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 2 && p[0].isNotBlank()) out[p[0]] = p[1].toIntOrNull() ?: 0
        }
        return out
    }
}

/**
 * Everything the user has added to the player plus how they listen to it. Stored on the phone only.
 * Main thread only; tag reading runs on a background dispatcher and applies its results on the main thread.
 */
class Library(
    private val context: Context,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
) {
    private val _tracks = MutableStateFlow(LibraryCodec.decode(store.getString(KEY_LIBRARY)))
    val tracks: StateFlow<List<LibraryTrack>> = _tracks.asStateFlow()

    private val _external = MutableStateFlow(LibraryCodec.decodeExternal(store.getString(KEY_EXTERNAL)))
    /** Lower-cased artist -> number of tracks of theirs heard in other apps. */
    val external: StateFlow<Map<String, Int>> = _external.asStateFlow()

    private var tagJob: Job? = null

    /** Adds tracks that are not known yet (keeps history of known ones) and starts reading their tags. */
    fun noteAdded(items: List<QueueItem>) {
        val known = _tracks.value.mapTo(HashSet()) { it.uri }
        val fresh = items.filter { !it.isReference && it.uri !in known }.distinctBy { it.uri }
        if (fresh.isEmpty()) return
        val merged = (_tracks.value + fresh.map { LibraryTrack(it.uri, it.title) }).takeLast(MAX_TRACKS)
        _tracks.value = merged
        save()
        readTags()
    }

    fun recordListen(uri: String, outcome: ListenOutcome, now: Long) {
        if (outcome == ListenOutcome.IGNORED) return
        update(uri) {
            it.copy(
                plays = it.plays + 1,
                completions = it.completions + if (outcome == ListenOutcome.COMPLETE) 1 else 0,
                skips = it.skips + if (outcome == ListenOutcome.SKIP) 1 else 0,
                lastPlayedAt = now,
            )
        }
    }

    fun recordExternal(artist: String) {
        val key = Recommender.norm(artist)
        if (key.isEmpty()) return
        val next = LinkedHashMap(_external.value)
        next[key] = ((next[key] ?: 0) + 1).coerceAtMost(500)
        while (next.size > MAX_EXTERNAL) next.remove(next.keys.first())
        _external.value = next
        store.putString(KEY_EXTERNAL, LibraryCodec.encodeExternal(next))
    }

    fun clear() {
        tagJob?.cancel()
        _tracks.value = emptyList()
        _external.value = emptyMap()
        store.putString(KEY_LIBRARY, null)
        store.putString(KEY_EXTERNAL, null)
    }

    private fun update(uri: String, change: (LibraryTrack) -> LibraryTrack) {
        val list = _tracks.value
        val i = list.indexOfFirst { it.uri == uri }
        if (i < 0) return
        _tracks.value = list.toMutableList().also { it[i] = change(it[i]) }
        save()
    }

    private fun save() = store.putString(KEY_LIBRARY, LibraryCodec.encode(_tracks.value))

    /** Reads artist/album/genre tags of tracks that have none yet, a few at a time, off the main thread. */
    private fun readTags() {
        if (tagJob?.isActive == true) return
        tagJob = scope.launch {
            while (true) {
                val batch = _tracks.value.filter { !it.tagged && !it.uri.startsWith("file:") }.take(BATCH)
                if (batch.isEmpty()) break
                val results = withContext(Dispatchers.IO) { batch.map { it.uri to readTag(it.uri) } }
                val byUri = results.toMap()
                _tracks.value = _tracks.value.map { t ->
                    val tag = byUri[t.uri] ?: return@map t
                    t.copy(artist = tag[0], album = tag[1], genre = tag[2], tagged = true)
                }
                save()
            }
        }
    }

    private fun readTag(uri: String): List<String> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            fun field(key: Int) = r.extractMetadata(key)?.trim().orEmpty()
            listOf(
                field(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifEmpty { field(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) },
                field(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                field(MediaMetadataRetriever.METADATA_KEY_GENRE),
            )
        } catch (e: Exception) {
            listOf("", "", "") // unreadable file: marked as tagged so it is not retried forever
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val KEY_LIBRARY = "library_v1"
        const val KEY_EXTERNAL = "library_external_v1"
        const val MAX_TRACKS = 1500
        const val MAX_EXTERNAL = 200
        const val BATCH = 20
    }
}
