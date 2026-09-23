package com.example.meowmba.flow

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.IntentionCapture
import com.example.meowmba.core.flow.Opportunity
import com.example.meowmba.core.flow.RouteFacts
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.SituationBuilder
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.WorkEstimate
import com.example.meowmba.core.flow.WorkState
import com.example.meowmba.core.flow.decide.FlowQuestions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exports the exact (state, questions) the phone sends to Laya for every replay case, to
 * build/flow-laya/inputs.json. scripts/flow/laya_golden.py runs the Python reference on
 * the same ONNX file and writes golden answers for the Kotlin engine's parity test
 * (master plan §4 S2, §6.4). Also checks every state fits Laya's context budget.
 */
class LayaInputsExportTest {
    private val builder = SituationBuilder(Fx.zone)

    @Test fun exportReplayInputs() {
        val text = javaClass.classLoader!!.getResource("flow/cases/policy_cases_v0.json")!!.readText()
        val cases = Json.parseToJsonElement(text).jsonObject["cases"] as JsonArray
        val questions = buildJsonObject { FlowQuestions.forIntention().forEach { put(it.id, it.toWire()) } }
        val out = buildJsonArray {
            cases.forEach { el ->
                val c = Json.decodeFromJsonElement(FlowReplayTest.Case.serializer(), el)
                val (h, m) = c.now.split(":").map { it.toInt() }
                val now = Fx.at(h, m)
                val situation = Situation(
                    now = now, activity = Activity.valueOf(c.activity), foregroundApp = c.app,
                    upcoming = c.calendar.map { CalendarItem(it.title, now + it.inMinutes * 60_000L, now + (it.inMinutes + it.durationMinutes) * 60_000L, it.withPeople) },
                    route = c.route?.let { RouteFacts(it.destination, it.etaMinutes, it.bufferMinutes) },
                    work = WorkEstimate(WorkState.valueOf(c.work), 0.9),
                    opportunities = c.opportunities.map { Opportunity(it.intentionId, it.placeName, it.distanceMeters, it.detourMinutes, it.open, it.side, Certainty.valueOf(it.certainty)) },
                    surface = Surface.valueOf(c.surface),
                )
                c.intentions.forEach { i ->
                    val intention = Intention(i.id, i.text, FlowKind.valueOf(i.kind), "", now, i.dueInMinutes?.let { now + it * 60_000L }, facts = i.facts)
                    val state = builder.stateFor(situation, intention)
                    assertTrue("${c.id}/${i.id} state too long for Laya: ${state.length}", state.length <= SituationBuilder.DEFAULT_MAX_CHARS)
                    val expected = c.assessments.firstOrNull { it.intentionId == i.id }
                    add(buildJsonObject {
                        put("case", c.id); put("intention", i.id); put("state", state)
                        expected?.let { e -> putJsonObject("label") {
                            put("relevant_now", e.relevantNow); put("chance_passing", e.chancePassing); put("urgency", e.urgency.lowercase())
                        } }
                    })
                }
            }
        }
        val doc = buildJsonObject {
            put("version", "v0")
            put("note", "labels are the proposed replay assessments; use them for calibration only after owner/Codex review")
            put("questions", questions)
            putJsonObject("capture_questions") { IntentionCapture.QUESTIONS.forEach { put(it.id, it.toWire()) } }
            put("items", out)
        }
        File("build/flow-laya").apply { mkdirs() }.resolve("inputs.json").writeText(Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), doc))
        assertTrue(out.size >= 25)
    }
}
