package com.example.meowmba.flow

import com.example.meowmba.core.flow.laya.LayaSequence
import com.example.meowmba.core.flow.laya.LayaTokenizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Token-for-token parity with Laya's reference tokenizer and sequence builder.
 * Golden cases: nvkudva/laya-web app/public/parity.json (Apache-2.0), produced by the
 * Python original. Needs the downloaded tokenizer (LAYA_DIR or ~/Meowmba-Models/laya/v1).
 */
class LayaParityTest {
    private val dir = File(System.getenv("LAYA_DIR") ?: (System.getProperty("user.home") + "/Meowmba-Models/laya/v1"))

    @Test fun tokenIdsAndMarkersMatchAllGoldenCases() {
        val tf = File(dir, "tokenizer.json")
        assumeTrue("Laya tokenizer not downloaded", tf.isFile)
        val tok = LayaTokenizer.load(tf)
        val root = Json.parseToJsonElement(javaClass.getResource("/laya/parity.json")!!.readText()).jsonObject
        val cfg = root.getValue("cfg").jsonObject
        val maxLen = cfg.getValue("max_len").jsonPrimitive.int
        val headMax = cfg.getValue("head_max_len").jsonPrimitive.int
        var checked = 0
        for (case in root.getValue("cases").jsonArray.map { it.jsonObject }) {
            val id = case.getValue("id").jsonPrimitive.content
            val stateEl = case.getValue("state")
            val state = if (stateEl is JsonPrimitive && stateEl.isString) stateEl.content else LayaSequence.pyJsonDumps(stateEl)
            val expect = case.getValue("expect").jsonObject
            for ((qid, qdef) in case.getValue("questions").jsonObject) {
                val exp = expect[qid]?.jsonObject ?: continue
                if (exp["input_ids"] == null) continue
                val q = toQ(qdef.jsonObject)
                assertEquals("$id/$qid options", exp.getValue("rendered_options").jsonArray.map { it.jsonPrimitive.content },
                    LayaSequence.renderOptions(q))
                val built = LayaSequence.build(tok, state, q, maxLen, headMax)
                assertEquals("$id/$qid ids", exp.getValue("input_ids").jsonArray.map { it.jsonPrimitive.int }, built.ids.toList())
                assertEquals("$id/$qid markers", exp.getValue("marker_pos").jsonArray.map { it.jsonPrimitive.int }, built.markers.toList())
                checked++
            }
        }
        assertEquals(true, checked >= 24)
    }

    private fun toQ(o: JsonObject): LayaSequence.Q {
        val type = LayaSequence.QType.entries.first { it.wire == o.getValue("type").jsonPrimitive.content }
        val insEl = o.getValue("instructions")
        val ins = if (insEl is JsonPrimitive && insEl.isString) insEl.content else LayaSequence.pyJsonDumps(insEl, ensureAscii = true)
        val crit = o["criteria"]
        return when {
            crit == null || crit is JsonNull -> LayaSequence.Q(type, ins)
            type == LayaSequence.QType.SCORE -> LayaSequence.Q(type, ins, levels = crit.jsonArray.map { it.jsonPrimitive.content })
            crit is JsonArray -> LayaSequence.Q(type, ins, crit.associate { it.jsonPrimitive.content to null })
            else -> LayaSequence.Q(type, ins, crit.jsonObject.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content })
        }
    }
}
