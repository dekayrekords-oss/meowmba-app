package com.example.meowmba.core.flow.laya

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Port of Laya's rl_common.render_options / build_sequence and the answer post-processing,
 * following the reference port (nvkudva/laya-web, Apache-2.0) token for token:
 *
 *   [CLS] <type> question: <instructions> [SEP] [MASK] opt0 [MASK] opt1 … [SEP] <state> [SEP]
 *
 * Every slice bound below mirrors the original; LayaParityTest checks ids and marker
 * positions against the 24 golden cases.
 */
object LayaSequence {
    enum class QType(val index: Int, val wire: String) { CHOICE(0, "choice"), SCORE(1, "score"), NOUL(2, "noul") }

    /** A question in Laya's internal form. [criteria] is insertion-ordered; null values fall back to the key. */
    data class Q(val type: QType, val instructions: String, val criteria: Map<String, String?> = emptyMap(),
                 val levels: List<String> = emptyList())

    class Built(val ids: IntArray, val markers: IntArray)

    /** A question in Laya's wire form ({type, instructions, criteria}), as the golden cases hold them. */
    fun fromWire(o: JsonObject): Q {
        val type = QType.entries.first { it.wire == (o["type"] as JsonPrimitive).content }
        val insEl = o.getValue("instructions")
        val ins = if (insEl is JsonPrimitive && insEl.isString) insEl.content else pyJsonDumps(insEl, ensureAscii = true)
        return when (val crit = o["criteria"]) {
            null, is JsonNull -> Q(type, ins)
            is JsonArray -> if (type == QType.SCORE) Q(type, ins, levels = crit.map { (it as JsonPrimitive).content })
                else Q(type, ins, crit.associate { (it as JsonPrimitive).content to null })
            is JsonObject -> Q(type, ins, crit.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content })
            else -> Q(type, ins)
        }
    }

    fun renderOptions(q: Q): List<String> = when (q.type) {
        QType.CHOICE -> q.criteria.map { (k, v) -> if (v.isNullOrEmpty()) k else "$k: $v" }
        QType.SCORE -> q.levels.mapIndexed { i, c -> "level $i: $c" }
        QType.NOUL -> listOf(
            "false: " + (q.criteria["false"]?.takeIf { it.isNotEmpty() } ?: "no, the statement does not hold"),
            "true: " + (q.criteria["true"]?.takeIf { it.isNotEmpty() } ?: "yes, the statement holds"),
        )
    }

    fun build(tok: LayaTokenizer, state: String, q: Q, maxLen: Int, headMaxLen: Int): Built {
        fun scrub(s: String) = s.split("[MASK]").joinToString(" ")
        val opts = renderOptions(q)
        var headIds = tok.encode("${q.type.wire} question: ${scrub(q.instructions)}")
        var optIds = opts.map { listOf(tok.maskTokenId) + tok.encode(" " + scrub(it)).take(48) }
        var optBudget = headMaxLen - optIds.sumOf { it.size }
        if (optBudget < 16) {
            val per = max(4, (headMaxLen - 16) / max(1, optIds.size))
            optIds = optIds.map { it.take(per) }
            optBudget = headMaxLen - optIds.sumOf { it.size }
        }
        headIds = headIds.take(max(8, optBudget))

        val ids = ArrayList<Int>(maxLen)
        ids += tok.clsTokenId; ids += headIds; ids += tok.sepTokenId
        val markers = ArrayList<Int>()
        for (o in optIds) { markers += ids.size; ids += o }
        ids += tok.sepTokenId
        val room = max(0, maxLen - ids.size - 1)
        val st = tok.encode(scrub(state)).take(room)
        ids += st; ids += tok.sepTokenId
        val all = ids.take(maxLen)
        return Built(all.toIntArray(), markers.filter { it < maxLen }.toIntArray())
    }

    /**
     * Python's `json.dumps` (", " and ": " separators). Laya was trained on this form, so the
     * app's compact JSON is re-serialised before tokenising. [ensureAscii] as in Python.
     */
    fun pyJsonDumps(v: JsonElement, ensureAscii: Boolean = false): String = buildString { dump(v, ensureAscii) }

    private fun StringBuilder.dump(v: JsonElement, ascii: Boolean) {
        when (v) {
            is JsonNull -> append("null")
            is JsonPrimitive -> if (v.isString) str(v.content, ascii) else append(v.content)
            is JsonArray -> { append('['); v.forEachIndexed { i, x -> if (i > 0) append(", "); dump(x, ascii) }; append(']') }
            is JsonObject -> { append('{'); v.entries.forEachIndexed { i, (k, x) -> if (i > 0) append(", "); str(k, ascii); append(": "); dump(x, ascii) }; append('}') }
        }
    }

    private fun StringBuilder.str(s: String, ascii: Boolean) {
        append('"')
        for (ch in s) {
            val c = ch.code
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                c < 0x20 || (ascii && c > 0x7f) -> append("\\u").append(c.toString(16).padStart(4, '0'))
                else -> append(ch)
            }
        }
        append('"')
    }

    /** "choice:3-5" etc.; a two-way noul and a 20-way choice need different scaling. */
    fun tempBucket(type: QType, k: Int): String =
        "${type.wire}:" + when { k <= 2 -> "2"; k <= 5 -> "3-5"; k <= 10 -> "6-10"; else -> "11+" }

    fun softmax(z: FloatArray, temp: Double = 1.0): DoubleArray {
        val m = z.maxOrNull()?.toDouble() ?: 0.0
        val e = DoubleArray(z.size) { exp((z[it] - m) / temp) }
        val s = e.sum()
        return DoubleArray(e.size) { e[it] / s }
    }

    /** 1 − normalised entropy, as Jev reports it. */
    fun confidence(p: DoubleArray): Double {
        if (p.size < 2) return 1.0
        val ent = -p.sumOf { it * ln(min(max(it, 1e-12), 1.0)) }
        return 1 - ent / ln(p.size.toDouble())
    }
}
