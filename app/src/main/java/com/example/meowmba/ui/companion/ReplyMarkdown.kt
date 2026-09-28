package com.example.meowmba.ui.companion

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The little Markdown that cloud models put in replies (**bold**, *italic*, `code`, # headings),
 * rendered instead of shown as raw asterisks in the caption and chat.
 */
internal object ReplyMarkdown {
    enum class Style { BOLD, ITALIC, CODE }
    data class Span(val start: Int, val end: Int, val style: Style)
    data class Parsed(val text: String, val spans: List<Span>)

    private val inline = Regex("""\*\*(.+?)\*\*|__(.+?)__|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|`([^`]+)`""")

    fun parse(raw: String): Parsed {
        val spans = ArrayList<Span>()
        val out = StringBuilder()
        raw.lines().forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            // A heading line reads as a bold line.
            val heading = Regex("""^\s{0,3}#{1,6}\s+""").find(line)
            val body = if (heading != null) line.substring(heading.range.last + 1) else line
            val lineStart = out.length
            var last = 0
            inline.findAll(body).forEach { m ->
                out.append(body, last, m.range.first)
                val (inner, style) = when {
                    m.groups[1] != null -> m.groupValues[1] to Style.BOLD
                    m.groups[2] != null -> m.groupValues[2] to Style.BOLD
                    m.groups[3] != null -> m.groupValues[3] to Style.ITALIC
                    else -> m.groupValues[4] to Style.CODE
                }
                val s = out.length
                out.append(inner)
                spans += Span(s, out.length, style)
                last = m.range.last + 1
            }
            out.append(body, last, body.length)
            if (heading != null && out.length > lineStart) spans += Span(lineStart, out.length, Style.BOLD)
        }
        return Parsed(out.toString(), spans)
    }

    fun annotated(raw: String): AnnotatedString {
        val p = parse(raw)
        return buildAnnotatedString {
            append(p.text)
            p.spans.forEach { s ->
                addStyle(when (s.style) {
                    Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Style.CODE -> SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }, s.start, s.end)
            }
        }
    }
}
