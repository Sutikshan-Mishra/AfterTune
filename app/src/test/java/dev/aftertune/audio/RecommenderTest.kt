package dev.aftertune.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun t(
        id: String,
        artist: String = "",
        album: String = "",
        genre: String = "",
        plays: Int = 0,
        completions: Int = 0,
        skips: Int = 0,
        ago: Long = 5 * day,
        title: String = id,
    ) = LibraryTrack("uri:$id", title, artist, album, genre, plays, completions, skips, if (plays > 0) now - ago else 0L, tagged = true)

    @Test
    fun classifyListens() {
        assertEquals(ListenOutcome.IGNORED, ListenRules.classify(3_000, 200_000, false))
        assertEquals(ListenOutcome.SKIP, ListenRules.classify(12_000, 200_000, false))
        assertEquals(ListenOutcome.PARTIAL, ListenRules.classify(60_000, 200_000, false))
        assertEquals(ListenOutcome.COMPLETE, ListenRules.classify(190_000, 200_000, false))
        assertEquals(ListenOutcome.COMPLETE, ListenRules.classify(60_000, 200_000, true))
        // a short clip played to the end is not a skip
        assertEquals(ListenOutcome.COMPLETE, ListenRules.classify(9_000, 10_000, false))
    }

    @Test
    fun noHistoryMeansNoRecommendations() {
        val lib = listOf(t("a", artist = "X"), t("b", artist = "Y"))
        assertTrue(Recommender.rank(lib, emptyMap(), null, now).isEmpty())
    }

    @Test
    fun sameArtistAsListenedTracksRanksFirst() {
        val lib = listOf(
            t("heard1", artist = "Daft", plays = 3, completions = 3),
            t("heard2", artist = "Daft", plays = 2, completions = 2),
            t("other", artist = "Someone"),
            t("sibling", artist = "Daft"),
        )
        val ranked = Recommender.rank(lib, emptyMap(), null, now)
        assertEquals("uri:sibling", ranked.first().track.uri)
        assertTrue(ranked.none { it.track.uri == "uri:other" })
        assertEquals("You play Daft", ranked.first().reason)
    }

    @Test
    fun currentTrackIsNeverSuggested() {
        val lib = listOf(t("a", artist = "X", plays = 2, completions = 2), t("b", artist = "X"))
        val ranked = Recommender.rank(lib, emptyMap(), "uri:a", now)
        assertTrue(ranked.none { it.track.uri == "uri:a" })
    }

    @Test
    fun repeatedSkipsPushATrackDown() {
        val lib = listOf(
            t("liked", artist = "X", plays = 4, completions = 4),
            t("skipped", artist = "X", plays = 3, skips = 3),
            t("fresh", artist = "X"),
        )
        val ranked = Recommender.rank(lib, emptyMap(), null, now)
        assertTrue(ranked.none { it.track.uri == "uri:skipped" })
    }

    @Test
    fun justPlayedTracksAreHeldBack() {
        val lib = listOf(
            t("justNow", artist = "X", plays = 3, completions = 3, ago = 5 * 60_000L),
            t("earlier", artist = "X", plays = 3, completions = 3, ago = 3 * day),
        )
        val ranked = Recommender.rank(lib, emptyMap(), null, now)
        assertEquals("uri:earlier", ranked.first().track.uri)
    }

    @Test
    fun albumAndGenreMatchesCount() {
        val lib = listOf(
            t("heard", artist = "A", album = "Alb", genre = "Jazz", plays = 3, completions = 3),
            t("sameAlbum", artist = "B", album = "Alb"),
            t("sameGenre", artist = "C", genre = "Jazz"),
            t("unrelated", artist = "D", album = "Z", genre = "Metal"),
        )
        val ranked = Recommender.rank(lib, emptyMap(), null, now).map { it.track.uri }
        assertTrue("uri:sameAlbum" in ranked)
        assertTrue("uri:sameGenre" in ranked)
        assertTrue("uri:unrelated" !in ranked)
        assertTrue(ranked.indexOf("uri:sameAlbum") < ranked.indexOf("uri:sameGenre"))
    }

    @Test
    fun artistsHeardInOtherAppsMatchTagsAndFileTitles() {
        val lib = listOf(
            t("tagged", artist = "Radiohead"),
            t("byTitle", title = "Radiohead - Nude"),
            t("nope", artist = "Abba"),
        )
        val ranked = Recommender.rank(lib, mapOf("radiohead" to 5), null, now).map { it.track.uri }
        assertTrue("uri:tagged" in ranked)
        assertTrue("uri:byTitle" in ranked)
        assertTrue("uri:nope" !in ranked)
    }

    @Test
    fun oldListeningCountsLess() {
        val recent = t("r", plays = 2, completions = 2, ago = 1 * day)
        val old = t("o", plays = 2, completions = 2, ago = 120 * day)
        assertTrue(Recommender.weight(recent, now) > Recommender.weight(old, now))
    }

    @Test
    fun limitIsRespected() {
        val lib = listOf(t("h", artist = "X", plays = 3, completions = 3)) + (1..50).map { t("n$it", artist = "X") }
        assertEquals(10, Recommender.rank(lib, emptyMap(), null, now, limit = 10).size)
    }

    @Test
    fun codecRoundTrips() {
        val lib = listOf(
            LibraryTrack("content://a/b", "Title", "Art", "Alb", "Rock", 3, 2, 1, 12345L, true, liked = true),
            LibraryTrack("content://c", "Tab\there", plays = 0),
        )
        val back = LibraryCodec.decode(LibraryCodec.encode(lib))
        assertEquals(lib[0], back[0])
        assertEquals("Tab here", back[1].title)
        assertEquals(mapOf("a" to 2, "b" to 7), LibraryCodec.decodeExternal(LibraryCodec.encodeExternal(mapOf("a" to 2, "b" to 7))))
    }

    @Test
    fun likedTracksSurfaceWithTheirOwnReasonAndShapeTaste() {
        val lib = listOf(
            LibraryTrack("uri:l", "Liked song", artist = "X", liked = true),
            LibraryTrack("uri:s", "Same artist", artist = "X"),
            LibraryTrack("uri:o", "Other", artist = "Y"),
        )
        val ranked = Recommender.rank(lib, emptyMap(), null, now)
        val byUri = ranked.associateBy { it.track.uri }
        assertEquals("You liked this", byUri.getValue("uri:l").reason)
        assertTrue("uri:s" in byUri)
        assertTrue("uri:o" !in byUri)
    }

    @Test
    fun codecStillReadsLinesWithoutTheLikedField() {
        val back = LibraryCodec.decode("content://x\tT\tA\tAl\tG\t1\t1\t0\t5\t1")
        assertEquals(1, back.size)
        assertEquals(false, back[0].liked)
        assertEquals(true, back[0].tagged)
    }

    @Test
    fun codecSkipsBrokenLines() {
        assertEquals(0, LibraryCodec.decode("junk\nline").size)
        assertEquals(0, LibraryCodec.decode(null).size)
    }
}
