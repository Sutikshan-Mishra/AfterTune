package dev.aftertune.audio

/**
 * Plain-text search over the queue and the library. Case-insensitive; punctuation counts as a space (file names
 * like "smack-that" are found by "smack that"); every word typed must appear in the title, artist or album.
 */
object TrackSearch {
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var space = true
        for (ch in s.lowercase()) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch); space = false
            } else if (!space) {
                sb.append(' '); space = true
            }
        }
        return sb.toString().trim()
    }

    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    fun matches(tokens: List<String>, vararg fields: String): Boolean {
        if (tokens.isEmpty()) return true
        val hay = fields.joinToString(" ") { normalize(it) }
        return tokens.all { it in hay }
    }

    /** Indices into [queue] that match; with an empty query, every index. */
    fun queueMatches(queue: List<QueueItem>, library: List<LibraryTrack>, query: String): List<Int> {
        val t = tokens(query)
        if (t.isEmpty()) return queue.indices.toList()
        val byUri = library.associateBy { it.uri }
        return queue.indices.filter { i ->
            val item = queue[i]
            val meta = byUri[item.uri]
            matches(t, item.title, meta?.artist.orEmpty(), meta?.album.orEmpty())
        }
    }

    /** Library tracks that match but are not in the queue (so they can be played or added). Empty for an empty query. */
    fun libraryMatches(queue: List<QueueItem>, library: List<LibraryTrack>, query: String, limit: Int = 60): List<LibraryTrack> {
        val t = tokens(query)
        if (t.isEmpty()) return emptyList()
        val queued = queue.mapTo(HashSet()) { it.uri }
        return library.asSequence()
            .filter { it.uri !in queued && matches(t, it.title, it.artist, it.album) }
            .take(limit)
            .toList()
    }
}
