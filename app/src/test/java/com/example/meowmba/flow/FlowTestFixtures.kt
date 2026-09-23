package com.example.meowmba.flow

import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.Opportunity
import com.example.meowmba.core.flow.RouteFacts
import com.example.meowmba.core.flow.Signal
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.WorkEstimate
import com.example.meowmba.core.flow.WorkState
import java.time.LocalDateTime
import java.time.ZoneId

internal object Fx {
    val zone: ZoneId = ZoneId.of("America/Phoenix")

    /** Tue 22 Sep 2026 at [h]:[m] Phoenix time. */
    fun at(h: Int, m: Int = 0): Long = LocalDateTime.of(2026, 9, 22, h, m).atZone(zone).toInstant().toEpochMilli()

    val bulb = Intention(
        id = "bulb", text = "Fix the rear brake light", kind = FlowKind.ERRAND,
        source = "said Sun 19:14: 'the rear light is out again'", createdAt = at(9),
        facts = mapOf("item" to "your brake-light bulb", "signals" to "need"),
    )
    val handoff = Intention("handoff", "Hand off the idle heat fix", FlowKind.WORK, "opened Meowmba in Android Studio", at(9), facts = mapOf("signals" to "app"))
    val bill = Intention("bill", "Card payment, \$412", FlowKind.MONEY, "statement email", at(9), dueAt = at(9) + 26 * 3_600_000L)
    val walk = Intention("walk", "Sunset walk, Riverside Trail", FlowKind.LEISURE, "you like evening walks", at(9))
    val album = Intention("album", "New album from an artist you played 14 times", FlowKind.LEISURE, "music this week", at(9))
    val plan = Intention("plan", "Plan build 157", FlowKind.WORK, "calendar", at(9), dueAt = at(9) + 24 * 3_600_000L)

    fun drivingPastStore(surface: Surface = Surface.FLOW_ON_SCREEN, buffer: Int = 12) = Situation(
        now = at(16, 58), activity = Activity.DRIVING, place = "Main St",
        route = RouteFacts("hardware store pickup", 6, buffer),
        signals = listOf(Signal("loc", "Main St"), Signal("cal", "Pickup 5:10"), Signal("need", "Rear light out", fresh = true)),
        work = WorkEstimate(WorkState.WORKING, 0.7),
        opportunities = listOf(Opportunity("bulb", "Main Street Auto Parts", 180, 4, open = true, side = "right", certainty = Certainty.LIKELY)),
        surface = surface,
    )

    fun evening() = Situation(now = at(18, 40), activity = Activity.STILL, place = "Home", work = WorkEstimate(WorkState.OFF, 0.93))

    fun a(id: String, urgency: Urgency, relevant: Double = 0.8, chance: Double = 0.0) =
        Assessment(id, relevant, chance, urgency, 0.8)
}
