package com.example.meowmba.ui.flow

import com.example.meowmba.core.flow.Channel
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.Countdown
import com.example.meowmba.core.flow.FlowAction
import com.example.meowmba.core.flow.FlowInterrupt
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.Signal
import com.example.meowmba.core.flow.Urgency

/**
 * The approved demo's moments as snapshots, for previews, UI tests and screenshot
 * comparison with docs/design/screens/. Example data only.
 */
object FlowSamples {
    private fun item(
        id: String, kind: FlowKind, headline: String, because: String, primary: String,
        urgency: Urgency = Urgency.WORTH_IT, sources: List<String> = emptyList(), secondary: String? = null, held: String? = null,
    ) = FlowItem(id, kind, headline, because, FlowAction("primary", primary), secondary?.let { FlowAction("later", it) },
        urgency, Certainty.CONFIRMED, sources, held)

    val deepWork = FlowSnapshot(
        at = 1L,
        signals = listOf(Signal("loc", "Office"), Signal("app", "Android Studio · Meowmba"), Signal("cal", "Free till noon")),
        main = item("handoff", FlowKind.WORK, "The idle heat fix is ready to hand off.",
            "You opened Meowmba. The branch has nine changed files and every test passes. Codex does the builds.",
            "Hand off to Codex", sources = listOf("app"), secondary = "Not now"),
        more = listOf(
            item("note", FlowKind.WORK, "A tester note about the screen you're in", "Sam: \"the voice button is hard to find.\"", "Show", sources = listOf("app")),
            item("quiet", FlowKind.WORK, "Quiet until noon", "Two notifications are held for you.", "See", Urgency.USEFUL, listOf("cal")),
        ),
        interrupt = null, evening = false, judgments = emptyList(),
    )

    private val bulbItem = item("bulb", FlowKind.ERRAND,
        "Main Street Auto Parts is on your right. They usually carry the brake-light bulb you need.",
        "You said the rear light was out; your car takes a 7443 bulb. I can't check their stock, so I can call ahead if you like. The stop adds about 4 minutes and you'll still make your 5:10 pickup.",
        "Add the stop", sources = listOf("need"))

    val drivingPastStore = FlowSnapshot(
        at = 2L,
        signals = listOf(Signal("loc", "Main St"), Signal("cal", "Pickup 5:10"), Signal("need", "Rear light out", fresh = true)),
        main = item("pickup", FlowKind.ERRAND, "Hardware store in six minutes.", "Your dock is at the front counter. Parking is on the side street.",
            "Navigate", sources = listOf("cal", "loc"), secondary = "OK"),
        more = listOf(item("fix", FlowKind.ERRAND, "Fix the rear brake light", "You said on Sunday the rear light was out. Your car takes a 7443 bulb.", "Details", sources = listOf("need"))),
        interrupt = FlowInterrupt(
            bulbItem, Channel.SPEAK_AND_SHOW,
            "Auto parts store on your right usually carries your brake-light bulb. Want to stop? It adds about four minutes.",
            Countdown("Main Street Auto Parts", 183),
        ),
        evening = false, judgments = emptyList(),
    )

    val heldInterrupt = drivingPastStore.copy(
        at = 3L, interrupt = null,
        more = listOf(bulbItem.copy(headline = "Main Street Auto Parts usually carries your 7443 bulb · adds 4 min",
            because = "Waiting quietly in the flow.", primary = FlowAction("primary", "Stop"),
            heldReason = "Held: this is \"Worth it\", and you only allow \"Urgent\" to interrupt.")) + drivingPastStore.more,
    )

    val workWraps = FlowSnapshot(
        at = 4L,
        signals = listOf(Signal("loc", "Home"), Signal("app", "Laptop closed"), Signal("cal", "Nothing tonight")),
        main = item("tomorrow", FlowKind.WORK, "You're done for today. One thing for tomorrow.",
            "Planning build 157 at nine. The agenda is ready, and nothing is due tonight.", "See agenda",
            Urgency.USEFUL, listOf("app"), "Done"),
        more = listOf(
            item("walk", FlowKind.LEISURE, "Sunset walk, Riverside Trail", "Sunset at 7:08. Twelve minutes away, 72°.", "Go", Urgency.USEFUL, listOf("loc")),
            item("album", FlowKind.LEISURE, "New album from an artist you played 14 times", "Out today, about 38 minutes.", "Play", Urgency.USEFUL, listOf("app")),
            item("show", FlowKind.LEISURE, "Your show, series 2 episode 7", "You stopped at episode 6.", "Resume", Urgency.USEFUL, listOf("cal")),
        ),
        interrupt = null, evening = true, judgments = emptyList(),
    )
}
