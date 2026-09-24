package com.example.meowmba.ui.desk

import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSnapshot

/**
 * Meow's Desk (AppTheme.DESK mode) — a second face on the same FlowSnapshot as Meow Flow
 * (master plan §9, docs/PRO_DESK_AVATAR_PASS_DOWN.md, docs/design/pro-desk-avatar.html).
 * Every object shows real data from the snapshot or from app facts; no fake content.
 */
enum class DeskObject(
    val label: String,
    /** Tap target in the 412 × 960 scene. */
    val left: Float, val top: Float, val width: Float, val height: Float,
    /** Where the cat walks to (scene x of the cat's left edge). */
    val catX: Float,
) {
    WINDOW("Window: weather", 232f, 60f, 160f, 220f, 250f),
    PROJECT("Corkboard: project", 16f, 172f, 202f, 182f, 150f),
    BUILD("Monitor: latest build", 20f, 432f, 104f, 152f, 150f),
    TODAY("Sticky note: next up", 122f, 418f, 78f, 72f, 150f),
    MONEY("Jar: money", 270f, 506f, 68f, 100f, 205f),
    INBOX("Letter tray: messages", 332f, 518f, 78f, 88f, 250f),
    IDEA("Lamp: an idea from Meow", 194f, 440f, 84f, 164f, 190f),
    /** Opens Android's Clock (alarms, timers). */
    CLOCK("Clock: alarms and timers", 34f, 74f, 76f, 76f, 60f),
    /** The desk drawers: folders of apps and Meow tools the user fills. */
    DRAWER_TOP("Top drawer: apps and tools", 30f, 640f, 150f, 42f, 70f),
    DRAWER_BOTTOM("Bottom drawer: apps and tools", 30f, 686f, 150f, 38f, 70f),
}

/** App facts the Desk shows that don't come from Flow items (project status, build, weather). */
data class DeskFacts(
    val projectTitle: String = "",
    val tallyDone: Int = 0,
    val tallyTotal: Int = 0,
    val projectStatus: String = "",
    val pinnedNote: List<String> = emptyList(),
    val monitorLines: List<MonitorLine> = emptyList(),
    val nextEventTime: String? = null,
    val nextEventWords: List<String> = emptyList(),
    val weatherLine: String? = null,
    val night: Boolean = false,
    val clockHour: Int = 9,
    val clockMinute: Int = 0,
    /** Short handwritten money note, e.g. "$412 tmrw"; derived from a MONEY item when null. */
    val jarNote: String? = null,
    /** An AI suggestion is queued (lamp glows). */
    val ideaQueued: Boolean = false,
)

data class MonitorLine(val text: String, val highlight: Boolean = false)

/** Everything the dynamic layer draws. Equality drives redraws: unchanged state = no redraw. */
data class DeskState(
    val clockHour: Int, val clockMinute: Int,
    val night: Boolean, val weatherLine: String?,
    val projectTitle: String, val tallyDone: Int, val tallyTotal: Int, val projectStatus: String,
    val pinnedNote: List<String>,
    val monitorLines: List<MonitorLine>,
    val sticky: List<String>,
    val trayCount: Int,
    val jarNote: String?,
    val lampOn: Boolean,
) {
    companion object {
        /** Binds a Flow snapshot + app facts to desk objects (master plan §9.1 table). */
        fun from(snapshot: FlowSnapshot, facts: DeskFacts, open: List<com.example.meowmba.core.flow.Intention>? = null): DeskState {
            val items = listOfNotNull(snapshot.main) + snapshot.more
            // Prefer every open intention (the tray counts everyone waiting, not just the 4 shown in Flow).
            val people = open?.count { it.kind == FlowKind.PEOPLE } ?: items.count { it.kind == FlowKind.PEOPLE }
            val moneyText = (open?.firstOrNull { it.kind == FlowKind.MONEY }?.text)
                ?: items.firstOrNull { it.kind == FlowKind.MONEY }?.headline
            val sticky = facts.nextEventTime?.let { listOf(it) + facts.nextEventWords.take(2) }.orEmpty()
            return DeskState(
                clockHour = facts.clockHour, clockMinute = facts.clockMinute,
                night = facts.night, weatherLine = facts.weatherLine,
                projectTitle = facts.projectTitle, tallyDone = facts.tallyDone.coerceIn(0, facts.tallyTotal.coerceAtLeast(0)),
                tallyTotal = facts.tallyTotal.coerceIn(0, MAX_TALLY), projectStatus = facts.projectStatus,
                pinnedNote = facts.pinnedNote.take(2),
                monitorLines = facts.monitorLines.take(3),
                sticky = sticky,
                trayCount = people,
                jarNote = facts.jarNote ?: moneyText?.let(::shortMoney),
                // With the full list, the caller decides (facts.ideaQueued); otherwise fall back to what Flow shows.
                lampOn = facts.ideaQueued || (open == null && snapshot.interrupt == null && items.any { it.kind == FlowKind.OTHER }),
            )
        }

        const val MAX_TALLY = 14

        /** "Card payment, $412" → "$412"; keeps the note short enough for the jar label. */
        internal fun shortMoney(text: String): String =
            Regex("""\$[\d,]+(\.\d\d)?""").find(text)?.value ?: text.take(10)
    }
}
