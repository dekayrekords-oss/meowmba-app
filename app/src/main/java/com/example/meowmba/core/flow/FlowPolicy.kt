package com.example.meowmba.core.flow

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many interruptions happened today and when the last one was. Persisted by the controller. */
data class InterruptLedger(val day: LocalDate? = null, val countToday: Int = 0, val lastAt: Long? = null) {
    fun forDay(today: LocalDate) = if (day == today) this else InterruptLedger(today, 0, lastAt)
}

data class PolicyResult(val snapshot: FlowSnapshot, val ledger: InterruptLedger)

/**
 * Pure decision policy (master plan §5.2): judgments + facts + user settings → [FlowSnapshot].
 *
 * Rules:
 * - Work first (D9). Leisure only once work is OFF; after work, at most one
 *   non-urgent work item remains ("one thing for tomorrow").
 * - At most one main item and [MAX_MORE] more.
 * - An interruption needs urgency ≥ the user's level, a passing chance (or an urgent,
 *   relevant commitment), feasibility, room under the daily cap and the minimum gap,
 *   and an allowed channel. Otherwise it joins the flow with a visible held reason (D4).
 * - Changing settings re-runs this function, not the model.
 * - Copy never claims stock; opportunities say "usually carries" (D12).
 */
class FlowPolicy(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun decide(
        situation: Situation,
        intentions: List<Intention>,
        assessments: List<Assessment>,
        settings: FlowSettings,
        ledgerIn: InterruptLedger,
        /** Intention already shown as the current interruption: keep it, don't count it again. */
        activeInterrupt: String? = null,
        /** Intentions whose interruption the user already answered or that passed; never re-interrupt. */
        suppressed: Set<String> = emptySet(),
    ): PolicyResult {
        val s = settings.normalized()
        val now = situation.now
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var ledger = ledgerIn.forDay(today)
        // Evening = work has ended for the day. Early morning before work is not evening.
        val evening = situation.work.state == WorkState.OFF && !situation.work.beforeWork
        val byId = assessments.associateBy { it.intentionId }

        val candidates = intentions.filter { it.isOpenAt(now) }.mapNotNull { i ->
            val a = byId[i.id] ?: return@mapNotNull null
            if (!visible(i, a, evening)) null else i to a
        }.map { (i, a) -> Triple(i, a, score(i, a, evening, now)) }
            .sortedByDescending { it.third }
            .let { if (evening) keepOneWorkItem(it) else it }

        // Pick the best interruption candidate, then gate it by the user's settings.
        var interrupt: FlowInterrupt? = null
        var held: FlowItem? = null
        val candidate = candidates.firstOrNull { (i, a, _) -> i.id !in suppressed && interruptEligible(i, a, situation) }
        if (candidate != null) {
            val (i, a, sc) = candidate
            val item = toItem(i, a, situation, sc)
            val alreadyOut = i.id == activeInterrupt
            val reason = if (alreadyOut) null else gate(a, s, ledger, now) ?: channelHold(s, situation)
            if (reason == null) {
                val channel = channelFor(s, situation)
                interrupt = FlowInterrupt(
                    item = item.copy(headline = interruptHeadline(i, situation) ?: item.headline),
                    channel = channel,
                    spokenLine = if (channel.speaks()) spokenLine(i, situation, item) else null,
                    countdown = situation.opportunities.firstOrNull { it.intentionId == i.id }
                        ?.let { Countdown(it.placeName, it.distanceMeters) },
                )
                if (!alreadyOut) ledger = ledger.copy(countToday = ledger.countToday + 1, lastAt = now)
            } else {
                held = item.copy(heldReason = reason)
            }
        }

        val rest = candidates.filter { it.first.id != held?.intentionId }
            .map { (i, a, sc) -> toItem(i, a, situation, sc) }
        // The interrupting item already has the band at the top; don't repeat it below.
        val below = rest.filter { it.intentionId != interrupt?.item?.intentionId }
        val main = below.firstOrNull()
        val more = (listOfNotNull(held) + below.drop(1)).take(MAX_MORE)

        return PolicyResult(
            FlowSnapshot(
                at = now,
                signals = situation.signals,
                main = main,
                more = more,
                interrupt = interrupt,
                evening = evening,
                judgments = assessments.flatMap { it.judgments },
            ),
            ledger,
        )
    }

    // ---- visibility and ranking ------------------------------------------------------------

    private fun visible(i: Intention, a: Assessment, evening: Boolean): Boolean {
        if (i.kind == FlowKind.LEISURE && !evening) return false
        if (a.urgency == Urgency.NOT_NOW) return false
        return a.urgency.atLeast(Urgency.WORTH_IT) || a.relevantNow >= QUIET_BELOW
    }

    internal fun score(i: Intention, a: Assessment, evening: Boolean, now: Long): Double {
        val kindBonus = when (i.kind) {
            FlowKind.WORK -> if (evening) 0.0 else 0.3
            FlowKind.PEOPLE -> 0.3
            FlowKind.MONEY -> 0.25
            FlowKind.LEISURE -> if (evening) 0.3 else 0.0
            else -> 0.1
        }
        val dueBonus = i.dueAt?.let { if (it - now in 0..DAY_MS) 0.2 else 0.0 } ?: 0.0
        return a.urgency.ordinal + a.relevantNow + 0.5 * a.chancePassing + kindBonus + dueBonus
    }

    private fun keepOneWorkItem(list: List<Triple<Intention, Assessment, Double>>): List<Triple<Intention, Assessment, Double>> {
        var keptWork = false
        return list.filter { (i, a, _) ->
            if (i.kind != FlowKind.WORK || a.urgency == Urgency.URGENT) true
            else if (!keptWork) { keptWork = true; true } else false
        }
    }

    // ---- interruption ----------------------------------------------------------------------

    private fun interruptEligible(i: Intention, a: Assessment, s: Situation): Boolean {
        // "Allow …" setup rows are never worth interrupting for; they wait in the flow.
        if (i.id.startsWith(com.example.meowmba.core.flow.sources.AppIntentions.SETUP)) return false
        if (!a.urgency.atLeast(Urgency.USEFUL)) return false
        val timely = a.chancePassing >= CHANCE_MIN || (a.urgency == Urgency.URGENT && a.relevantNow >= RELEVANT_MIN)
        if (!timely) return false
        val o = s.opportunities.firstOrNull { it.intentionId == i.id } ?: return true
        if (o.open == false) return false
        val buffer = s.route?.bufferMinutes ?: return true
        return o.detourMinutes <= buffer
    }

    private fun gate(a: Assessment, s: FlowSettings, ledger: InterruptLedger, now: Long): String? = when {
        !a.urgency.atLeast(s.minUrgency) ->
            "Held: this is \"${a.urgency.label}\", and you only allow \"${s.minUrgency.label}\" to interrupt."
        ledger.countToday >= s.maxPerDay ->
            "Held: you've had ${ledger.countToday} of your ${s.maxPerDay} interruptions today."
        ledger.lastAt != null && now - ledger.lastAt < s.minGapMinutes * 60_000L ->
            "Held: less than ${gapText(s.minGapMinutes)} since the last interruption."
        else -> null
    }

    private fun channelHold(s: FlowSettings, situation: Situation): String? =
        if (channelFor(s, situation) == Channel.HOLD) "Held: voice and notifications are off while you're in another app." else null

    /** Delivery matrix from the master plan §7.2. */
    internal fun channelFor(s: FlowSettings, situation: Situation): Channel {
        val onScreen = situation.surface == Surface.FLOW_ON_SCREEN || situation.surface == Surface.DESK_ON_SCREEN
        return when {
            onScreen -> if (s.voice) Channel.SPEAK_AND_SHOW else Channel.SHOW_ONLY
            s.notifyOtherApps -> Channel.NOTIFY
            s.voice -> Channel.SPEAK_ONLY
            else -> Channel.HOLD
        }
    }

    private fun Channel.speaks() = this == Channel.SPEAK_AND_SHOW || this == Channel.SPEAK_ONLY || this == Channel.NOTIFY

    // ---- copy (templated from facts; never generated, never claims stock) -------------------

    internal fun toItem(i: Intention, a: Assessment, s: Situation, score: Double): FlowItem {
        val o = s.opportunities.firstOrNull { it.intentionId == i.id }
        return FlowItem(
            intentionId = i.id,
            kind = i.kind,
            headline = i.text,
            because = i.facts[FACT_BECAUSE] ?: because(i, s, o),
            primary = FlowAction("primary", i.facts[FACT_ACTION] ?: "Done"),
            secondary = FlowAction("later", "Later"),
            urgency = a.urgency,
            certainty = o?.certainty ?: Certainty.CONFIRMED,
            sources = sources(i, o),
            score = score,
        )
    }

    private fun because(i: Intention, s: Situation, o: Opportunity?): String = when {
        o != null -> buildString {
            append(o.placeName).append(' ').append(carriesSingular(o.certainty)).append(" it")
            append(". The stop adds about ${o.detourMinutes} min")
            s.route?.let { if (o.detourMinutes <= it.bufferMinutes) append(" and you'll still be on time") }
            append('.')
        }
        i.dueAt != null -> {
            val mins = (i.dueAt - s.now) / 60_000
            when {
                mins < 0 -> "It was due ${-mins} min ago."
                mins < 120 -> "Due in $mins min."
                else -> "Due ${DUE.format(Instant.ofEpochMilli(i.dueAt).atZone(zone))}."
            }
        }
        i.source.isNotBlank() -> "You ${i.source.removePrefix("you ").removePrefix("You ")}."
        else -> ""
    }

    private fun interruptHeadline(i: Intention, s: Situation): String? {
        val o = s.opportunities.firstOrNull { it.intentionId == i.id } ?: return null
        val where = o.side?.let { "${o.placeName} is on your $it" } ?: "${o.placeName} is nearby"
        val what = i.facts[FACT_ITEM] ?: "what you need"
        return "$where. They ${carries(o.certainty)} $what."
    }

    private fun spokenLine(i: Intention, s: Situation, item: FlowItem): String {
        val o = s.opportunities.firstOrNull { it.intentionId == i.id }
            ?: return item.headline
        val side = o.side?.let { " on your $it" } ?: " nearby"
        val what = i.facts[FACT_ITEM] ?: "what you need"
        return "${o.placeName}$side ${carriesSingular(o.certainty)} $what. Want to stop? It adds about ${o.detourMinutes} minutes."
    }

    private fun carries(c: Certainty) = when (c) {
        Certainty.CONFIRMED -> "have"
        Certainty.LIKELY -> "usually carry"
        Certainty.UNKNOWN -> "may carry"
    }

    private fun carriesSingular(c: Certainty) = when (c) {
        Certainty.CONFIRMED -> "has"
        Certainty.LIKELY -> "usually carries"
        Certainty.UNKNOWN -> "may carry"
    }

    private fun sources(i: Intention, o: Opportunity?): List<String> {
        val explicit = i.facts[FACT_SIGNALS]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val auto = buildList {
            if (o != null) add("loc")
            if (i.dueAt != null) add("cal")
        }
        return (explicit + auto).distinct()
    }

    private fun defaultAction(kind: FlowKind, hasOpportunity: Boolean) = when (kind) {
        FlowKind.ERRAND -> if (hasOpportunity) "Add the stop" else "Plan it"
        FlowKind.PEOPLE -> "Reply"
        FlowKind.MONEY -> "Review"
        FlowKind.LEISURE -> "Go"
        else -> "Open"
    }

    private fun gapText(minutes: Int) = if (minutes % 60 == 0) {
        val h = minutes / 60; if (h == 1) "an hour" else "$h hours"
    } else "$minutes minutes"

    companion object {
        const val MAX_MORE = 3
        const val QUIET_BELOW = 0.35
        const val CHANCE_MIN = 0.7
        const val RELEVANT_MIN = 0.7
        const val FACT_ACTION = "action"
        const val FACT_ITEM = "item"
        const val FACT_SIGNALS = "signals"
        /** Exact "because" line supplied by the source (overrides the template). */
        const val FACT_BECAUSE = "because"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val DUE = DateTimeFormatter.ofPattern("EEE 'at' h:mm a", Locale.US)
    }
}
