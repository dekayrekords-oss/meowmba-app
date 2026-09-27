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
    /** Tap target in scene units. The office is x 0–412; the living room is left of it (negative x), the bedroom right (412–824). */
    val left: Float, val top: Float, val width: Float, val height: Float,
    /** Where the cat walks to (scene x of the cat's left edge). */
    val catX: Float,
) {
    WINDOW("Window: weather", 232f, 60f, 160f, 220f, 250f),
    PROJECT("Corkboard: project", 16f, 172f, 202f, 182f, 150f),
    /** The computer on the old Gateway 2000: opens Computer and Development Studio. */
    BUILD("Computer: Development Studio", 18f, 404f, 184f, 196f, 150f),
    TODAY("Sticky note: next up", 202f, 414f, 66f, 64f, 150f),
    MONEY("Jar: money", 270f, 506f, 68f, 100f, 205f),
    INBOX("Letter tray: messages", 332f, 518f, 78f, 88f, 250f),
    IDEA("Lamp: an idea from Meow", 286f, 404f, 120f, 92f, 190f),
    /** Opens Android's Clock (alarms, timers). */
    CLOCK("Clock: alarms and timers", 34f, 74f, 76f, 76f, 60f),
    /** The desk drawers: folders of apps and Meow tools the user fills. */
    DRAWER_TOP("Top drawer: apps and tools", 30f, 640f, 150f, 42f, 70f),
    DRAWER_BOTTOM("Bottom drawer: apps and tools", 30f, 686f, 150f, 38f, 70f),

    // ---- bedroom (right of the office) ----
    BIG_WINDOW("Big window: weather", 522f, 86f, 220f, 300f, 546f),
    /** The old TV and the console under it open the MAME arcade. */
    ARCADE("Old TV and game console: arcade", 424f, 520f, 128f, 220f, 552f),
    TOWER_BED("Cat tower by the window", 568f, 356f, 66f, 384f, 546f),
    BED("Bed", 682f, 576f, 140f, 164f, 702f),
    FOOD("Food tray", 632f, 692f, 52f, 48f, 562f),
    LITTER("Litter box", 504f, 684f, 60f, 56f, 478f),

    // ---- living room (left of the office); frames match DeskLayout.STANDARD, see [frame] ----
    BOOKSHELF("Bookshelf: audiobooks and the manual", -412f, 420f, 80f, 320f, -393f),
    BIG_TV("Big-screen TV: what's on YouTube", -232f, 356f, 218f, 132f, -290f),
    STEREO("Stereo: what's playing", -190f, 568f, 176f, 172f, -157f),
    COUCH("Couch", -324f, 628f, 136f, 112f, -290f),
    /** The tall tower behind the couch. */
    TOWER_LIVING("Cat tower behind the couch", -302f, 306f, 70f, 290f, -323f),
    /** A framed picture on the wall; the user chooses the photo. */
    PICTURE("Picture: choose your own", -404f, 214f, 88f, 126f, -324f),
    ;

    val room: DeskRoom get() = DeskRoom.of(left + width / 2 - 55f)

    /** Where this object is in [layout]: landscape widens the living room and bedroom outward. */
    fun frame(layout: DeskLayout): DeskFrame {
        if (layout == DeskLayout.STANDARD) return DeskFrame(left, top, width, height)
        val p = layout.living
        return when (this) {
            BOOKSHELF -> DeskFrame(p.shelfX, 420f, p.shelfW, 320f)
            PICTURE -> DeskFrame(p.pictureX, p.pictureY - 8f, p.pictureW, p.pictureH + 12f)
            BIG_TV -> DeskFrame(p.tvX, p.tvY, p.tvW, p.tvH)
            STEREO -> DeskFrame(p.stereoX, p.stereoTop, p.stereoW, 740f - p.stereoTop)
            COUCH -> DeskFrame(p.couchX - 4f, 628f, 136f, 112f)
            TOWER_LIVING -> DeskFrame(p.towerX - 46f, 306f, 70f, 290f)
            BED -> DeskFrame(682f, 576f, minOf(layout.bedRight, layout.right) - 682f, 164f)
            else -> DeskFrame(left, top, width, height)
        }
    }
    /** Objects the cat uses on its own; tapping them sends the cat there, no panel opens. */
    val catOnly: Boolean get() = this in setOf(TOWER_BED, BED, FOOD, LITTER, COUCH, TOWER_LIVING)
}

/** A tap target in scene units. */
data class DeskFrame(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * How Meow's place is laid out. A landscape screen has width to spare, so the living room grows
 * to the left and the bedroom to the right by [wing] scene units (0…[MAX_WING]); the office and
 * both doorways stay where they are. The furniture grows with it: a wider bookshelf, a much
 * bigger TV and a longer bed. The Desk picks the wing that fits the screen ("auto fit").
 */
data class DeskLayout(val wing: Float) {
    /** 0 = standard rooms, 1 = the full landscape rooms. */
    private val t: Float get() = (wing / MAX_WING).coerceIn(0f, 1f)
    val living: LivingPlan get() = if (t == 0f) STD_PLAN else if (t == 1f) WIDE_PLAN else STD_PLAN.lerp(WIDE_PLAN, t)
    /** A nightstand (and lamp) fits beside the bed once the bedroom has grown enough. */
    val nightstand: Boolean get() = wing >= 162f
    /** The bed's headboard edge: the bed runs right up to the wall, or to the nightstand. */
    val bedRight: Float get() = if (nightstand) DeskRoom.APT_RIGHT + wing - 58f else DeskRoom.APT_RIGHT + 2f + wing
    val left: Float get() = DeskRoom.APT_LEFT - wing
    val right: Float get() = DeskRoom.APT_RIGHT + wing
    val width: Float get() = right - left

    companion object {
        const val MAX_WING = 180f
        val STANDARD = DeskLayout(0f)
        val LANDSCAPE = DeskLayout(MAX_WING)
        val entries = listOf(STANDARD, DeskLayout(90f), LANDSCAPE)
        /** The layout that fills a screen with room for [wing] extra units per side. */
        fun fitting(wing: Float) = DeskLayout(kotlin.math.floor(wing.coerceIn(0f, MAX_WING)))
        // The bookshelf stands flush against the outer wall, so on a filled screen it touches the edge.
        private val STD_PLAN = LivingPlan(shelfX = -412f, shelfW = 80f, pictureX = -404f, pictureY = 222f, pictureW = 88f, pictureH = 114f,
            couchX = -320f, tvX = -232f, tvY = 356f, tvW = 218f, tvH = 132f, stereoX = -190f, stereoW = 176f)
        private val WIDE_PLAN = LivingPlan(shelfX = -592f, shelfW = 164f, pictureX = -566f, pictureY = 200f, pictureW = 120f, pictureH = 140f,
            couchX = -424f, tvX = -330f, tvY = 306f, tvW = 316f, tvH = 186f, stereoX = -294f, stereoW = 280f)
    }
}

/**
 * Where the living room's furniture stands (scene units). The bookshelf is on the left with the
 * picture above it, then the couch with the tall cat tower behind it, then the stereo, with the
 * TV on the wall above it. The stereo's receiver display is as wide as its rack.
 */
data class LivingPlan(
    val shelfX: Float, val shelfW: Float,
    val pictureX: Float, val pictureY: Float, val pictureW: Float, val pictureH: Float,
    /** Left edge of the couch (128 wide including its arms); the tower's post is at its centre. */
    val couchX: Float,
    val tvX: Float, val tvY: Float, val tvW: Float, val tvH: Float,
    /** The stereo: a component rack between two tower speakers, standing on the floor. */
    val stereoX: Float, val stereoW: Float,
) {
    fun lerp(o: LivingPlan, t: Float): LivingPlan {
        fun l(a: Float, b: Float) = a + (b - a) * t
        return LivingPlan(l(shelfX, o.shelfX), l(shelfW, o.shelfW), l(pictureX, o.pictureX), l(pictureY, o.pictureY),
            l(pictureW, o.pictureW), l(pictureH, o.pictureH), l(couchX, o.couchX), l(tvX, o.tvX), l(tvY, o.tvY),
            l(tvW, o.tvW), l(tvH, o.tvH), l(stereoX, o.stereoX), l(stereoW, o.stereoW))
    }
    val couchW: Float get() = 128f
    val towerX: Float get() = couchX + couchW / 2
    val speakerW: Float get() = (20f + (stereoW - 176f) * 14f / 104f).coerceIn(20f, 34f)
    val rackX: Float get() = stereoX + speakerW + 2f
    val rackW: Float get() = stereoW - 2 * speakerW - 4f
    val displayX: Float get() = rackX + 6f
    val displayW: Float get() = rackW - 12f
    val displayH: Float get() = minOf(displayW * .5f, 88f)
    /** Top of the rack: turntable, receiver (the display), cassette deck and amplifier stack up from the floor. */
    val stereoTop: Float get() = 740f - displayH - 112f
}

/** The three rooms of Meow's place, left to right. Scene x ranges; the office is the original Desk. */
enum class DeskRoom(val label: String, val left: Float, val right: Float) {
    LIVING("Living room", -412f, 0f), OFFICE("Office", 0f, 412f), BEDROOM("Bedroom", 412f, 824f);

    val center: Float get() = (left + right) / 2

    companion object {
        const val APT_LEFT = -412f
        const val APT_RIGHT = 824f
        const val APT_WIDTH = APT_RIGHT - APT_LEFT
        /** The room the cat is in; [catX] is the cat's left edge (the sprite is 110 wide). */
        fun of(catX: Float): DeskRoom { val c = catX + 55f; return when { c < 0f -> LIVING; c < 412f -> OFFICE; else -> BEDROOM } }
    }
}

/** Something playing on the phone, as shown on the stereo, the TV or the bookshelf. */
data class RoomMedia(val title: String, val subtitle: String? = null, val playing: Boolean = false, val app: String? = null)

/** One Development Studio project, as the desk computer lists it. */
data class StudioLine(val name: String, val status: String)

/** Live things in the rest of the apartment. Empty stays empty: nothing is invented. */
data class RoomState(
    val music: RoomMedia? = null,
    val video: RoomMedia? = null,
    val audiobook: RoomMedia? = null,
    /** False when Android's notification access (which media titles need) is off. */
    val mediaAccess: Boolean = true,
    val studio: List<StudioLine> = emptyList(),
)

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
    val room: RoomState = RoomState(),
) {
    companion object {
        /** Binds a Flow snapshot + app facts to desk objects (master plan §9.1 table). */
        fun from(snapshot: FlowSnapshot, facts: DeskFacts, open: List<com.example.meowmba.core.flow.Intention>? = null, room: RoomState = RoomState()): DeskState {
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
                monitorLines = facts.monitorLines.take(4),
                sticky = sticky,
                trayCount = people,
                jarNote = facts.jarNote ?: moneyText?.let(::shortMoney),
                // With the full list, the caller decides (facts.ideaQueued); otherwise fall back to what Flow shows.
                lampOn = facts.ideaQueued || (open == null && snapshot.interrupt == null && items.any { it.kind == FlowKind.OTHER }),
                room = room,
            )
        }

        const val MAX_TALLY = 14

        /** "Card payment, $412" → "$412"; keeps the note short enough for the jar label. */
        internal fun shortMoney(text: String): String =
            Regex("""\$[\d,]+(\.\d\d)?""").find(text)?.value ?: text.take(10)
    }
}
