package com.example.lyrics

data class LyricLine(
    val timestampMs: Long?, // null for plain unsynchronized lyrics
    val text: String
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean
)

object LyricsParser {
    // Regex matching timestamp formats like [01:23.45], [1:23.456], [01:23]
    private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(rawText: String?): ParsedLyrics {
        if (rawText.isNullOrBlank()) {
            return ParsedLyrics(emptyList(), false)
        }

        val rawLines = rawText.lines()
        val parsedLines = mutableListOf<LyricLine>()
        var foundTimeTag = false

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check if this line contains one or more timestamp tags
            val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                foundTimeTag = true
                // Text is what remains after stripping tags
                val textOnly = trimmed.replace(TIME_TAG_REGEX, "").trim()
                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val millisStr = match.groupValues.getOrNull(3).orEmpty()
                    val millis = when (millisStr.length) {
                        1 -> millisStr.toLong() * 100
                        2 -> millisStr.toLong() * 10
                        3 -> millisStr.toLong()
                        else -> 0L
                    }
                    val totalMs = (minutes * 60 + seconds) * 1000 + millis
                    parsedLines.add(LyricLine(timestampMs = totalMs, text = textOnly))
                }
            } else {
                // Ignore metadata tags like [ar:Artist], [ti:Title] if it starts with [
                if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                    parsedLines.add(LyricLine(timestampMs = null, text = trimmed))
                }
            }
        }

        if (foundTimeTag) {
            // Sort by timestamp for synchronized lines
            val sorted = parsedLines.filter { it.timestampMs != null }.sortedBy { it.timestampMs }
            return ParsedLyrics(lines = sorted, isSynchronized = true)
        } else {
            return ParsedLyrics(lines = parsedLines, isSynchronized = false)
        }
    }

    /**
     * Finds index of the active lyric line for the given playback position in milliseconds.
     * Returns -1 if no line is active yet.
     */
    fun findActiveLineIndex(lyrics: ParsedLyrics, positionMs: Long): Int {
        if (!lyrics.isSynchronized || lyrics.lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lyrics.lines.indices) {
            val time = lyrics.lines[i].timestampMs ?: continue
            if (positionMs >= time) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
