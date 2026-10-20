package dev.hd450btnc.companion.eq

/**
 * Versioned, line-based text format for [EqState]. Human readable and dependency free.
 * Unknown keys are ignored and malformed lines are skipped, so a damaged file degrades to defaults
 * instead of crashing the app.
 *
 *   v=1
 *   enabled=1
 *   active=custom
 *   headroom=1
 *   gains=0,0,...
 *   custom=0,1.5,...
 *   profile=<id>|<escaped name>|<gains>
 */
object EqStateCodec {
    private const val VERSION = 1

    fun encode(state: EqState): String = buildString {
        appendLine("v=$VERSION")
        appendLine("enabled=${if (state.enabled) 1 else 0}")
        appendLine("active=${escape(state.activeId)}")
        appendLine("headroom=${if (state.autoHeadroom) 1 else 0}")
        appendLine("gains=${encodeGains(state.gains)}")
        appendLine("custom=${encodeGains(state.customGains)}")
        for (p in state.profiles) {
            appendLine("profile=${escape(p.id)}|${escape(p.name)}|${encodeGains(p.gains)}")
        }
    }

    fun decode(text: String?): EqState {
        if (text.isNullOrBlank()) return EqState()
        var state = EqState()
        val profiles = mutableListOf<SavedProfile>()
        for (line in text.lineSequence()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            when (key) {
                "enabled" -> state = state.copy(enabled = value != "0")
                "active" -> state = state.copy(activeId = unescape(value))
                "headroom" -> state = state.copy(autoHeadroom = value != "0")
                "gains" -> decodeGains(value)?.let { state = state.copy(gains = it) }
                "custom" -> decodeGains(value)?.let { state = state.copy(customGains = it) }
                "profile" -> {
                    val parts = splitUnescaped(value)
                    if (parts.size == 3) {
                        val gains = decodeGains(parts[2])
                        if (gains != null) profiles += SavedProfile(unescape(parts[0]), unescape(parts[1]), gains)
                    }
                }
            }
        }
        return EqController.sanitize(state.copy(profiles = profiles))
    }

    private fun encodeGains(g: List<Float>) = g.joinToString(",")

    private fun decodeGains(s: String): List<Float>? {
        val values = s.split(',').map { it.trim().toFloatOrNull() ?: return null }
        return if (values.isEmpty()) null else values
    }

    // Escape the separators we use: backslash, pipe and newlines.
    fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '|' -> append("\\p")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> append('\\')
                    'p' -> append('|')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c); i++
            }
        }
    }

    /** Splits on '|' that is not escaped (escaped pipes are written as "\p", so any raw '|' is a separator). */
    private fun splitUnescaped(s: String): List<String> = s.split('|')
}
