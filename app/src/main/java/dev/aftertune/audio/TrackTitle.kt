package dev.aftertune.audio

object TrackTitle {
    fun pretty(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""

        val normalized = if (' ' in s) {
            s
        } else {
            s.replace('-', ' ').replace('_', ' ')
        }

        return normalized
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                val i = word.indexOfFirst { it.isLetter() }
                if (i < 0 || word.any { it.isUpperCase() }) {
                    word
                } else {
                    word.replaceRange(i, i + 1, word[i].uppercaseChar().toString())
                }
            }
    }
}
