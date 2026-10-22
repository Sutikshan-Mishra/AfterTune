package dev.hd450btnc.companion.artwork

import dev.hd450btnc.companion.artwork.ExternalArtworkRules.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalArtworkRulesTest {
    private val own = "dev.hd450btnc.companion"

    @Test
    fun ignoresOwnSessionAndSessionsWithoutTrack() {
        val list = listOf(Candidate(own, true, true), Candidate("a", true, false))
        assertEquals(-1, ExternalArtworkRules.pick(list, own))
    }

    @Test
    fun playingBeatsPaused() {
        val list = listOf(Candidate("spotify", false, true), Candidate("outertune", true, true))
        assertEquals(1, ExternalArtworkRules.pick(list, own))
    }

    @Test
    fun firstPausedWinsWhenNothingPlays() {
        val list = listOf(Candidate("a", false, true), Candidate("b", false, true))
        assertEquals(0, ExternalArtworkRules.pick(list, own))
    }

    @Test
    fun sourceSelection() {
        // nothing external: always in-app
        assertFalse(ExternalArtworkRules.useExternal(true, true, externalHasTrack = false, externalPlaying = false))
        // empty in-app queue: follow the external track, even when paused
        assertTrue(ExternalArtworkRules.useExternal(false, false, externalHasTrack = true, externalPlaying = false))
        // in-app playing wins
        assertFalse(ExternalArtworkRules.useExternal(true, true, externalHasTrack = true, externalPlaying = true))
        // in-app paused: only a playing external track takes over
        assertTrue(ExternalArtworkRules.useExternal(true, false, externalHasTrack = true, externalPlaying = true))
        assertFalse(ExternalArtworkRules.useExternal(true, false, externalHasTrack = true, externalPlaying = false))
    }

    @Test
    fun accessDecidesTheToggle() {
        assertTrue(ExternalArtworkRules.reconcile(enabled = false, awaitingAccess = true, granted = true))
        assertFalse(ExternalArtworkRules.reconcile(enabled = false, awaitingAccess = true, granted = false))
        assertFalse(ExternalArtworkRules.reconcile(enabled = true, awaitingAccess = false, granted = false))
        assertTrue(ExternalArtworkRules.reconcile(enabled = true, awaitingAccess = false, granted = true))
        assertFalse(ExternalArtworkRules.reconcile(enabled = false, awaitingAccess = false, granted = true))
    }
}
