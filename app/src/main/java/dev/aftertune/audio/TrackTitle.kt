
package dev.aftertune.audio

object TrackTitle {
    fun pretty(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s

        if (' ' !in s) {
            s = s.replace('_', ' ')
                .replace(Regex("-+(?=[a-z])"), " ")
        }

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
