package dev.aftertune.audio

/**
 * Display form of a track title. File names like "smack-that" or "39-intro" become "Smack That" and "39 Intro":
 * "-" and "_" turn into spaces (only when the title has no spaces yet, so a real tag such as "Half-Life" is left
 * alone), and the first letter of each word is capitalised. Only that first letter changes: words that already
 * contain a capital ("iPhone", "DJ") are kept as they are, and the rest of a word is never lowercased.
 */
object TrackTitle {
    fun pretty(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s
        if (' ' !in s) s = s.replace('-', ' ').replace('_', ' ')
        val sb = StringBuilder(s.length)
        var hasUpper = false
        var word = StringBuilder()
        fun flush() {
            if (word.isEmpty()) return
            if (!hasUpper) {
                val i = word.indexOfFirst { it.isLetter() }
                if (i >= 0) word.setCharAt(i, word[i].uppercaseChar())
            }
            sb.append(word)
            word = StringBuilder()
            hasUpper = false
        }
        for (ch in s) {
            if (ch == ' ') {
                flush()
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            } else {
                if (ch.isUpperCase()) hasUpper = true
                word.append(ch)
            }
        }
        flush()
        return sb.toString().trim()
    }
}
