package com.example.meowmba.ui.companion

/** Shell echo cannot impersonate the markers: their ID is a separate printf argument. */
internal class ComputerCommandFrame(val id: String = java.util.UUID.randomUUID().toString().replace("-", "")) {
    init { require(id.matches(Regex("[a-zA-Z0-9]+"))) }
    private val begin = "MEOW_${id}_BEGIN"
    private val end = Regex("MEOW_${id}_END:(-?\\d+)")
    fun wrap(command: String): String =
        "printf '\\nMEOW_%s_BEGIN\\n' '$id'; (\n$command\n); __meow_result=\$?; printf '\\nMEOW_%s_END:%d\\n' '$id' \"\$__meow_result\""
    fun result(output: String): com.example.meowmba.core.agentic.ComputerExecResult? {
        val start = output.indexOf(begin)
        if (start < 0) return null
        val match = end.find(output, start + begin.length) ?: return null
        val exit = match.groupValues[1].toIntOrNull() ?: return null
        return com.example.meowmba.core.agentic.ComputerExecResult(exit == 0,
            output.substring(start + begin.length, match.range.first).trim().take(16_000), exit)
    }
}
