package com.example.meowmba.flow

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.Channel
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.InterruptLedger
import com.example.meowmba.core.flow.Opportunity
import com.example.meowmba.core.flow.RouteFacts
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.WorkEstimate
import com.example.meowmba.core.flow.WorkState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Replays the labelled situation cases (master plan §8) through [FlowPolicy] with
 * recorded assessments. When Laya runs on device, the same cases are replayed with
 * live answers instead of `assessments`.
 */
class FlowReplayTest {

    @Serializable data class CaseFile(val version: String, val labels: String, val notes: String = "", val cases: List<Case>)
    @Serializable data class Case(
        val id: String, val catalogue: List<Int>, val title: String, val now: String,
        val activity: String, val work: String = "WORKING", val surface: String = "FLOW_ON_SCREEN",
        val app: String? = null, val route: Route? = null, val calendar: List<Cal> = emptyList(),
        val intentions: List<Int_>, val opportunities: List<Opp> = emptyList(),
        val assessments: List<Ass>, val settings: Set_ = Set_(), val ledger: Led? = null, val expect: Expect,
    )
    @Serializable data class Route(val destination: String, val etaMinutes: Int, val bufferMinutes: Int)
    @Serializable data class Cal(val title: String, val inMinutes: Int, val durationMinutes: Int, val withPeople: Boolean = false)
    @Serializable data class Int_(val id: String, val text: String, val kind: String, val dueInMinutes: Int? = null, val facts: Map<String, String> = emptyMap())
    @Serializable data class Opp(val intentionId: String, val placeName: String, val distanceMeters: Int, val detourMinutes: Int,
                                 val open: Boolean? = null, val side: String? = null, val certainty: String = "LIKELY")
    @Serializable data class Ass(val intentionId: String, val relevantNow: Double, val chancePassing: Double, val urgency: String)
    @Serializable data class Set_(val voice: Boolean = true, val notifyOtherApps: Boolean = true, val maxPerDay: Int = 3,
                                  val minGapMinutes: Int = 60, val minUrgency: String = "WORTH_IT")
    @Serializable data class Led(val countToday: Int, val lastMinutesAgo: Int? = null)
    @Serializable data class Expect(
        val outcome: String, val intention: String? = null, val channel: String? = null,
        val mustMention: List<String> = emptyList(), val mustNotClaim: List<String> = emptyList(),
        val heldContains: String? = null, val spoken: Boolean? = null, val evening: Boolean? = null,
        val sources: List<String> = emptyList(), val maxWorkItems: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = false }
    private val policy = FlowPolicy(Fx.zone)

    private fun load(): CaseFile {
        val text = javaClass.classLoader!!.getResource("flow/cases/policy_cases_v0.json")!!.readText()
        return json.decodeFromString(CaseFile.serializer(), text)
    }

    @Test fun everyCaseMatchesItsLabel() {
        val file = load()
        assertTrue("need at least 20 cases", file.cases.size >= 20)
        assertTrue("need quiet cases", file.cases.count { it.expect.outcome == "QUIET" } >= 4)
        val failures = file.cases.mapNotNull { c -> runCatching { check(c) }.exceptionOrNull()?.let { "${c.id} (${c.title}): ${it.message}" } }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test fun caseIdsAreUnique() {
        val ids = load().cases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    private fun check(c: Case) {
        val (h, m) = c.now.split(":").map { it.toInt() }
        val now = Fx.at(h, m)
        val intentions = c.intentions.map {
            Intention(it.id, it.text, FlowKind.valueOf(it.kind), "", now, it.dueInMinutes?.let { d -> now + d * 60_000L }, facts = it.facts)
        }
        val situation = Situation(
            now = now, activity = Activity.valueOf(c.activity), foregroundApp = c.app,
            upcoming = c.calendar.map { CalendarItem(it.title, now + it.inMinutes * 60_000L, now + (it.inMinutes + it.durationMinutes) * 60_000L, it.withPeople) },
            route = c.route?.let { RouteFacts(it.destination, it.etaMinutes, it.bufferMinutes) },
            work = WorkEstimate(WorkState.valueOf(c.work), 0.9),
            opportunities = c.opportunities.map { Opportunity(it.intentionId, it.placeName, it.distanceMeters, it.detourMinutes, it.open, it.side, Certainty.valueOf(it.certainty)) },
            surface = Surface.valueOf(c.surface),
        )
        val assessments = c.assessments.map { Assessment(it.intentionId, it.relevantNow, it.chancePassing, Urgency.valueOf(it.urgency), 0.8) }
        val settings = FlowSettings(voice = c.settings.voice, notifyOtherApps = c.settings.notifyOtherApps, maxPerDay = c.settings.maxPerDay,
            minGapMinutes = c.settings.minGapMinutes, minUrgency = Urgency.valueOf(c.settings.minUrgency))
        val ledger = c.ledger?.let { InterruptLedger(LocalDate.of(2026, 9, 22), it.countToday, it.lastMinutesAgo?.let { a -> now - a * 60_000L }) }
            ?: InterruptLedger()

        val snap = policy.decide(situation, intentions, assessments, settings, ledger).snapshot
        val e = c.expect
        val held = snap.more.firstOrNull { it.heldReason != null }
        val actual = when {
            snap.interrupt != null -> "INTERRUPT"
            held != null -> "HELD"
            snap.main == null && snap.more.isEmpty() -> "QUIET"
            else -> "MAIN"
        }
        require(actual == e.outcome) { "expected ${e.outcome}, got $actual" }
        val target = when (e.outcome) {
            "INTERRUPT" -> snap.interrupt!!.item
            "HELD" -> held!!
            "MAIN" -> snap.main!!
            else -> null
        }
        e.intention?.let { require(target?.intentionId == it) { "expected intention $it, got ${target?.intentionId}" } }
        e.channel?.let { require(snap.interrupt?.channel == Channel.valueOf(it)) { "expected channel $it, got ${snap.interrupt?.channel}" } }
        e.spoken?.let { require((snap.interrupt?.spokenLine != null) == it) { "spoken line presence should be $it" } }
        e.heldContains?.let { require(held?.heldReason?.contains(it) == true) { "held reason '${held?.heldReason}' lacks '$it'" } }
        e.evening?.let { require(snap.evening == it) { "evening should be $it" } }
        e.sources.forEach { s -> require(target?.sources?.contains(s) == true) { "sources ${target?.sources} lack $s" } }
        e.maxWorkItems?.let { max ->
            val work = (listOfNotNull(snap.main) + snap.more).count { it.kind == FlowKind.WORK }
            require(work <= max) { "$work work items shown, max $max" }
        }
        val copy = listOfNotNull(target?.headline, target?.because, snap.interrupt?.spokenLine).joinToString(" | ")
        e.mustMention.forEach { require(copy.contains(it)) { "copy '$copy' lacks '$it'" } }
        e.mustNotClaim.forEach { require(!copy.contains(it, ignoreCase = true)) { "copy '$copy' claims '$it'" } }
    }
}
