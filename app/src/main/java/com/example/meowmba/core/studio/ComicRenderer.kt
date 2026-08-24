package com.example.meowmba.core.studio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure HTML renderers for studio artifacts (comics, scripts). No Android
 * imports — same pattern as CustomAnimation — so the whole pipeline runs in
 * plain unit tests.
 *
 * Why HTML: the browser overlay already displays workspace files, and a tool
 * result ending in `MEDIA_FILE:<path>` auto-opens it (see onTaskCompleted).
 * A comic that renders to HTML therefore ends up ON SCREEN with zero new UI
 * plumbing — which is the whole point of the studio.
 */
object ComicRenderer {

    /** Filesystem-safe slug for titles ("Meowmba #1!" -> "meowmba_1"). */
    fun slugify(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9\\- ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "untitled" }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    /**
     * Renders one comic page.
     *
     * [panelsJson] is a JSON array of panel objects; every field optional:
     *   {"caption": "...", "dialogue": "...", "description": "..."}
     * [cast] maps character name -> sheet text; rendered as a CAST page
     * footer so a reader (and the model proofreading its own work) can see
     * exactly which locked design each character follows.
     */
    fun renderComicHtml(title: String, panelsJson: String, cast: Map<String, String> = emptyMap()): String {
        val panels = try { JSONArray(panelsJson) } catch (_: Exception) { JSONArray() }
        val body = StringBuilder()
        for (i in 0 until panels.length()) {
            val p = panels.optJSONObject(i) ?: JSONObject()
            val caption = p.optString("caption", "").trim()
            val dialogue = p.optString("dialogue", "").trim()
            val description = p.optString("description", "").trim()
            if (caption.isEmpty() && dialogue.isEmpty() && description.isEmpty()) continue
            body.append("<div class='panel'><div class='num'>").append(i + 1)
                .append("</div>")
            if (dialogue.isNotEmpty()) body.append("<div class='bubble'>").append(esc(dialogue)).append("</div>")
            if (caption.isNotEmpty()) body.append("<div class='caption'>").append(esc(caption)).append("</div>")
            if (description.isNotEmpty()) body.append("<div class='desc'>").append(esc(description)).append("</div>")
            body.append("</div>")
        }
        if (body.isEmpty()) {
            body.append("<div class='panel'><div class='desc'>No usable panels were provided.</div></div>")
        }
        val castHtml = if (cast.isEmpty()) "" else buildString {
            append("<h2>CAST — locked sheets</h2><div class='cast'>")
            for ((name, sheet) in cast) {
                append("<pre><b>").append(esc(name)).append("</b>\n").append(esc(sheet.take(1200))).append("</pre>")
            }
            append("</div>")
        }
        return """<!doctype html><html><head><meta charset='utf-8'>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>${esc(title)}</title><style>
body{margin:0;background:#14101c;color:#f2ecff;font-family:sans-serif;padding:20px 12px 40px}
h1{font-size:24px;text-align:center;margin:0 0 4px}.sub{color:#b9a8e8;text-align:center;margin-bottom:18px;font-size:13px}
.panel{position:relative;background:#fff8ea;color:#222;border:4px solid #2b2140;border-radius:12px;min-height:130px;padding:14px;margin:0 auto 16px;width:min(640px,94vw);box-sizing:border-box}
.num{position:absolute;top:-14px;left:10px;background:#2b2140;color:#ffd76e;font-weight:bold;font-size:13px;padding:2px 10px;border-radius:999px}
.bubble{background:#fff;border:3px solid #222;border-radius:16px;padding:8px 12px;font-weight:600;display:inline-block;max-width:80%}
.caption{margin-top:10px;background:#fffbe8;border:2px solid #222;border-radius:8px;padding:5px 9px;font-size:13px;display:inline-block}
.desc{margin-top:10px;color:#555;font-style:italic;font-size:14px;line-height:1.35}
h2{font-size:17px;margin:26px 0 8px;text-align:center}
.cast pre{white-space:pre-wrap;background:#1e1730;border:1px solid #3a2d5c;border-radius:10px;padding:10px;font-size:12px;line-height:1.4;overflow-wrap:anywhere;width:min(640px,94vw);box-sizing:border-box;margin:0 auto 10px}
</style></head><body>
<h1>${esc(title)}</h1><div class='sub'>Made by Meowmba Studio</div>
$body
$castHtml
</body></html>"""
    }

    /**
     * Renders a script/ad/post draft as a readable page. [body] is markdown-ish:
     * # headings, - bullets, **bold**. Kept deliberately tiny — this is a
     * proofreading view, not a document editor.
     */
    fun renderScriptHtml(title: String, kind: String, body: String): String {
        val html = StringBuilder()
        for (rawLine in body.lines()) {
            val line = rawLine.trimEnd()
            val text = esc(line.trim()).replace(
                Regex("\\*\\*(.+?)\\*\\*")
            ) { "<b>${it.groupValues[1]}</b>" }
            html.append(
                when {
                    line.isBlank() -> ""
                    line.startsWith("# ") -> "<h2>${text.removePrefix("# ")}</h2>"
                    line.startsWith("## ") || line.startsWith("- ") || line.startsWith("* ") ->
                        if (line.startsWith("## ")) "<h3>${text.drop(3)}</h3>"
                        else "<div class='bullet'>${text.drop(2)}</div>"
                    else -> "<p>$text</p>"
                }
            )
        }
        return """<!doctype html><html><head><meta charset='utf-8'>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>${esc(title)}</title><style>
body{margin:0;background:#14101c;color:#f2ecff;font-family:sans-serif;padding:20px 16px 40px}
h1{font-size:24px;margin:0 0 2px}.kind{display:inline-block;background:#8f6fe833;border:1px solid #8f6fe8;color:#cdbcff;font-size:11px;letter-spacing:.08em;text-transform:uppercase;border-radius:999px;padding:3px 10px;margin-bottom:14px}
h2{font-size:18px;margin:18px 0 6px;color:#ffd76e}h3{font-size:15px;margin:14px 0 4px}
p{line-height:1.5;margin:6px 0}.bullet{margin:4px 0 4px 14px;line-height:1.45}
.page{max-width:680px;margin:0 auto;background:#1e1730;border:1px solid #3a2d5c;border-radius:12px;padding:18px}
</style></head><body><div class='page'>
<h1>${esc(title)}</h1><span class='kind'>${esc(kind)}</span>
$html
</div></body></html>"""
    }
}
