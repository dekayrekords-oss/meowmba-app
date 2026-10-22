package com.example.meowmba.core.flow.laya

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Laya's tokenizer (ModernBERT: NFC → added tokens → GPT-2 byte-level BPE), read straight
 * from `tokenizer.json`. Encodes without special tokens, which is all Laya's sequence
 * builder uses. Token ids must match the Python/transformers.js tokenizer exactly;
 * `LayaParityTest` checks them against the reference port's 24 golden cases.
 */
class LayaTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val ranks: Map<String, Int>,
    private val added: List<Added>,
) {
    private class Added(val content: String, val id: Int, val lstrip: Boolean, val rstrip: Boolean)

    val maskTokenId: Int = idOf("[MASK]")
    val clsTokenId: Int = idOf("[CLS]")
    val sepTokenId: Int = idOf("[SEP]")
    val padTokenId: Int = idOf("[PAD]")

    private fun idOf(token: String): Int =
        added.firstOrNull { it.content == token }?.id ?: vocab[token] ?: error("$token missing from tokenizer.json")

    private val cache = object : LinkedHashMap<String, IntArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 4096
    }

    fun encode(text: String): List<Int> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = ArrayList<Int>()
        for (seg in splitAdded(normalized)) {
            if (seg.addedId != null) { out += seg.addedId; continue }
            val m = PRETOKEN.matcher(seg.text)
            while (m.find()) out += bpe(m.group())
        }
        return out
    }

    private class Segment(val text: String, val addedId: Int? = null)

    /** Leftmost-longest match of added tokens (HF AddedVocabulary), honouring lstrip/rstrip. */
    private fun splitAdded(s: String): List<Segment> {
        val segs = ArrayList<Segment>()
        val buf = StringBuilder()
        var i = 0
        while (i < s.length) {
            val hit = added.firstOrNull { s.startsWith(it.content, i) }
            if (hit == null) { buf.append(s[i]); i++; continue }
            if (hit.lstrip) while (buf.isNotEmpty() && buf.last().isWhitespace()) buf.setLength(buf.length - 1)
            if (buf.isNotEmpty()) { segs += Segment(buf.toString()); buf.setLength(0) }
            segs += Segment(hit.content, hit.id)
            i += hit.content.length
            if (hit.rstrip) while (i < s.length && s[i].isWhitespace()) i++
        }
        if (buf.isNotEmpty()) segs += Segment(buf.toString())
        return segs
    }

    private fun bpe(piece: String): IntArray = synchronized(cache) { cache[piece] } ?: run {
        val bytes = piece.toByteArray(Charsets.UTF_8)
        var word = MutableList(bytes.size) { BYTE_TO_UNICODE[bytes[it].toInt() and 0xFF].toString() }
        while (word.size > 1) {
            var best = -1; var bestRank = Int.MAX_VALUE
            for (k in 0 until word.size - 1) {
                val r = ranks[word[k] + " " + word[k + 1]] ?: continue
                if (r < bestRank) { bestRank = r; best = k }
            }
            if (best < 0) break
            val a = word[best]; val b = word[best + 1]
            val merged = ArrayList<String>(word.size)
            var k = 0
            while (k < word.size) {
                if (k < word.size - 1 && word[k] == a && word[k + 1] == b) { merged += a + b; k += 2 }
                else { merged += word[k]; k++ }
            }
            word = merged
        }
        val ids = IntArray(word.size) { vocab[word[it]] ?: error("BPE produced unknown symbol ${word[it]}") }
        synchronized(cache) { cache[piece] = ids }
        ids
    }

    private operator fun ArrayList<Int>.plusAssign(ids: IntArray) { ids.forEach { add(it) } }

    companion object {
        /**
         * Unicode White_Space, spelled out: Rust's `\\s` (used by HF tokenizers) is Unicode, Java's
         * default is ASCII, and Android's regex rejects UNICODE_CHARACTER_CLASS. Same on both.
         */
        private const val WS = "\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"

        /** GPT-2 / HF ByteLevel(use_regex = true). */
        private val PRETOKEN: Pattern = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^$WS\\p{L}\\p{N}]+|[$WS]+(?![^$WS])|[$WS]+",
        )

        /** GPT-2 bytes_to_unicode: printable bytes map to themselves, the rest to U+0100+. */
        private val BYTE_TO_UNICODE: CharArray = CharArray(256).also { t ->
            val keep = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toSet()
            var n = 0
            for (b in 0 until 256) t[b] = if (b in keep) b.toChar() else (256 + n++).toChar()
        }

        fun load(file: File): LayaTokenizer = parse(file.readText())

        fun parse(json: String): LayaTokenizer {
            val root = Json.parseToJsonElement(json).jsonObject
            val model = root.getValue("model").jsonObject
            require(model["type"]?.jsonPrimitive?.content == "BPE") { "Laya tokenizer must be BPE" }
            val vocab = HashMap<String, Int>(60_000)
            model.getValue("vocab").jsonObject.forEach { (k, v) -> vocab[k] = v.jsonPrimitive.int }
            val ranks = HashMap<String, Int>(60_000)
            model.getValue("merges").jsonArray.forEachIndexed { i, m ->
                val key = if (m is JsonArray) m[0].jsonPrimitive.content + " " + m[1].jsonPrimitive.content
                else m.jsonPrimitive.content
                ranks.putIfAbsent(key, i)
            }
            val added = (root["added_tokens"] as? JsonArray).orEmpty().map { it as JsonObject }.map {
                Added(it.getValue("content").jsonPrimitive.content, it.getValue("id").jsonPrimitive.int,
                    it["lstrip"]?.jsonPrimitive?.boolean == true, it["rstrip"]?.jsonPrimitive?.boolean == true)
            }.sortedByDescending { it.content.length } // longest first at a given position
            return LayaTokenizer(vocab, ranks, added)
        }
    }
}
