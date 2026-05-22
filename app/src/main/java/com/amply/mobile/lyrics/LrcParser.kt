package com.amply.mobile.lyrics

data class LyricLine(
    val timeMs: Long?,
    val text: String,
)

private val timeTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

fun parseLrc(raw: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { line ->
        val matches = timeTagRegex.findAll(line).toList()
        val text = timeTagRegex.replace(line, "").trim()
        if (matches.isEmpty()) {
            if (text.isNotBlank()) {
                lines += LyricLine(timeMs = null, text = text)
            }
            return@forEach
        }
        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues.getOrNull(3).orEmpty()
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                else -> fraction.take(3).toLongOrNull() ?: 0L
            }
            lines += LyricLine(
                timeMs = minutes * 60_000L + seconds * 1_000L + millis,
                text = text,
            )
        }
    }
    return lines.sortedWith(compareBy<LyricLine> { it.timeMs ?: Long.MAX_VALUE }.thenBy { it.text })
}

fun isSyncedLyrics(raw: String): Boolean = parseLrc(raw).any { it.timeMs != null }

fun validateLyricsQuality(raw: String): Boolean {
    val parsed = parseLrc(raw)
    if (parsed.isEmpty()) return false

    val textLines = parsed.filter { it.text.isNotBlank() }
    if (textLines.size < 2) return false

    val uniqueRatio = textLines.map { it.text.trim().lowercase() }.toSet().size.toFloat() / textLines.size
    if (uniqueRatio < 0.3f) return false

    val timedLines = parsed.filter { it.timeMs != null }
    if (timedLines.isNotEmpty()) {
        if (timedLines.size < parsed.size * 0.5f) return false
        val times = timedLines.mapNotNull { it.timeMs }
        if (times.zipWithNext().any { (left, right) -> right < left || right - left > 300_000L }) {
            return false
        }
    }
    return true
}
