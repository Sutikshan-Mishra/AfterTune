package dev.aftertune.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTitleTest {
    @Test fun dashesBecomeSpacesAndFirstLettersCapitalised() {
        assertEquals("Smack That", TrackTitle.pretty("smack-that"))
        assertEquals("39 Intro", TrackTitle.pretty("39-intro"))
        assertEquals("Lose Yourself", TrackTitle.pretty("lose-yourself"))
        assertEquals("Back On My Bs", TrackTitle.pretty("back-on-my-bs"))
        assertEquals("Alfreds Theme", TrackTitle.pretty("alfreds_theme"))
    }

    @Test fun onlyTheFirstLetterChanges() {
        assertEquals("Hello World", TrackTitle.pretty("hello world"))
        assertEquals("iPhone Ring", TrackTitle.pretty("iPhone ring"))
        assertEquals("DJ Khaled", TrackTitle.pretty("DJ khaled"))
    }

    @Test fun realTitlesWithSpacesKeepTheirDashes() {
        assertEquals("Half-Life", TrackTitle.pretty("Half-Life"))
        assertEquals("Jay-Z Intro", TrackTitle.pretty("Jay-Z intro"))
    }

    @Test fun messyInput() {
        assertEquals("A B", TrackTitle.pretty("  a--b "))
        assertEquals("", TrackTitle.pretty("   "))
    }
}
