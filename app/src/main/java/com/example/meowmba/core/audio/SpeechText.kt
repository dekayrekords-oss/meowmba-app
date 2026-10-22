package com.example.meowmba.core.audio

/** Only the spoken copy is cleaned; the full original remains visible in chat. */
object SpeechText {
    private val coordinates = Regex("(?i)(?:latitude\\s*[:=]?\\s*)?([+-]?\\d{1,2}\\.\\d{3,})\\s*°?\\s*[NS]?\\s*(?:[,;/]|\\s+longitude\\s*[:=]?)\\s*([+-]?\\d{1,3}\\.\\d{3,})\\s*°?\\s*[EW]?")
    private val labeledCoordinate = Regex("(?i)\\b(?:latitude|longitude|lat|lon|lng)\\s*[:=]?\\s*[+-]?\\d{1,3}(?:\\.\\d+)?\\s*°?\\s*[NSEW]?\\b")

    fun clean(raw: String): String {
        val withoutKeycaps = raw.replace(Regex("[#*0-9]\\uFE0F?\\u20E3"), "")
            .replace(Regex("[+-]?\\d{1,3}°\\s*\\d{1,2}['′]\\s*\\d{1,2}(?:\\.\\d+)?[\"″]\\s*[NSEW]", RegexOption.IGNORE_CASE), "your current location")
        // Markdown from cloud replies (**bold**, `code`, # headings) is formatting, not words.
        val withoutMarkdown = withoutKeycaps.replace(Regex("""\*\*|__|`"""), "")
            .replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
        val located = coordinates.replace(withoutMarkdown) { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0)
                "your current location" else m.value
        }.replace(labeledCoordinate, "your current location")
        val out = StringBuilder()
        var offset = 0
        while (offset < located.length) {
            val point = located.codePointAt(offset)
            offset += Character.charCount(point)
            // Emoji, skin tones, flags, variation selectors and joined/keycap components.
            if (point in 0x1F000..0x1FAFF || point in 0x2600..0x27BF ||
                point in 0x231A..0x231B || point in 0x23E9..0x23F3 || point in 0x23F8..0x23FA ||
                point in setOf(0xA9, 0xAE, 0x203C, 0x2049, 0x2122, 0x2139, 0x2328, 0x23CF,
                    0x24C2, 0x25AA, 0x25AB, 0x25B6, 0x25C0, 0x25FB, 0x25FC, 0x25FD, 0x25FE,
                    0x2934, 0x2935, 0x2B05, 0x2B06, 0x2B07, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55,
                    0x3030, 0x303D, 0x3297, 0x3299) ||
                point in 0xFE00..0xFE0F || point == 0x200D || point == 0x20E3 ||
                point in 0xE0020..0xE007F) continue
            out.appendCodePoint(point)
        }
        return out.toString().replace(Regex("[ \\t]{2,}"), " ").trim()
    }
}
