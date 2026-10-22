package com.example.meowmba.ui.desk

import kotlin.random.Random

/** Acting only: these poses never execute a tool, send mail or change a goal. */
enum class DeskCatPose { REST, WALK, GROOM, STRETCH, SNIFF, TYPE, LETTER, WRITE, LISTEN, TALK,
    /** Curled up asleep (bed, couch). */ SLEEP,
    /** Head down at the food tray. */ EAT,
    /** Scratching in the litter box. */ DIG,
    /** Sitting up on a cat-tower perch, looking out. */ PERCH,
    /** Sitting, looking up at the TV. */ WATCH,
    /** Controller in paws at the old console. */ GAME,
    /** Head bobbing to the stereo. */ BOB,
    /** A book open in its paws. */ READ,
    /** Up on its hind legs, clawing a scratching post. */ SCRATCH,
    /** Sat down on its haunches, tail curled round (a rest that has gone on a while). */ SIT,
    /** Lying down in a loaf, paws tucked, eyes open or drowsy (a long rest). */ LOAF,
    /** Sitting with one front paw swiping (yarn, a toy, the pen, a moth). */ BAT,
    /** Standing, front paws pressing in turn ("making biscuits"). */ KNEAD,
    /** On its back, paws in the air, asking for a belly rub. */ ROLL,
    /** Sitting in the cardboard box: only the head shows. */ HIDE }

/**
 * A little routine the cat plays out once it arrives (chasing, batting, knocking things over).
 * Acting only: nothing here opens an app, searches the web or changes the user's data.
 */
enum class DeskCatTrick { NONE, YARN, MOUSE, ZOOMIES, TAIL, PEN, LAMP, MOTH, KEYBOARD, RESEARCH, TOY, BIRD, BOX, KNEAD, ROLL }

/** What the idle cat can see: it only chases a moth at night and naps in a sunbeam by day. */
internal data class DeskIdleContext(val night: Boolean = false, val sunbeamX: Float? = null)

/** How often the Desk cat wanders to another room while idle (owner setting, 2026-09-26). */
enum class DeskRoomTrips(val key: String, val label: String, val chance: Float) {
    NEVER("never", "Never", 0f), RARELY("rarely", "Rarely", .12f), SOMETIMES("sometimes", "Sometimes", .35f), OFTEN("often", "Often", .65f);

    companion object {
        const val PREF = "desk_room_trips"
        val DEFAULT = SOMETIMES
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The Desk's room size, a percentage of the size that fits the screen (owner setting, 2026-09-27). */
object DeskScale {
    const val PREF = "desk_scale"
    const val MIN = 60
    const val MAX = 200
    const val DEFAULT = 100
    fun of(value: String?): Int = value?.toIntOrNull()?.coerceIn(MIN, MAX) ?: DEFAULT
}

/** The cat's face. Independent of the pose, so any pose can carry any mood. */
enum class DeskCatFace { NEUTRAL, HAPPY, PURR, LOVE, CURIOUS, SURPRISED, FOCUSED, SLEEPY, ASLEEP, YAWN, ANNOYED, PLAYFUL, CHEW }

enum class DeskCatActivity { IDLE, LISTENING, THINKING, SPEAKING }

/** A real reply event, not a canned description of what the cat appears to do. */
data class DeskCatReply(val key: String, val text: String)

internal data class DeskCatSpot(val x: Float, val y: Float)
internal data class DeskCatAct(val spot: DeskCatSpot, val pose: DeskCatPose, val durationMs: Long,
    val face: DeskCatFace? = null, val facingLeft: Boolean? = null, val trick: DeskCatTrick = DeskCatTrick.NONE)

internal object DeskCatBehavior {
    /** Floor level for the cat's top edge (paws on the floor at y 740). */
    const val FLOOR = 656f
    val home = DeskCatSpot(180f, 516f)
    val computer = DeskCatSpot(150f, 516f)
    val letters = DeskCatSpot(250f, 516f)
    val board = DeskCatSpot(126f, 300f)

    /** Living-room and bed spots follow the furniture, which a landscape layout spreads out. */
    class RoomSpots(layout: DeskLayout) {
        private val p = layout.living
        val couch = DeskCatSpot(p.towerX - 34f, 598f)
        /** The top perch of the tower behind the couch. */
        val livingTower = DeskCatSpot(p.towerX - 67f, 236f)
        /** The tower's middle platform: the cat climbs up and down in two jumps. */
        val towerStep = DeskCatSpot(p.towerX - 76f, 372f)
        val stereo = DeskCatSpot(p.stereoX + p.stereoW / 2 - 55f, FLOOR)
        val bookshelf = DeskCatSpot(p.shelfX + p.shelfW / 2 - 21f, FLOOR)
        /** Where the ball of yarn rests on the rug, and the cat's spot beside it. */
        val yarnHome = p.couchX + 70f
        val yarn = DeskCatSpot(yarnHome - 100f, FLOOR)
        /** On the rug in front of the couch. */
        val rug = DeskCatSpot(p.couchX + 10f, FLOOR)
        /** Paws up on the couch's arm. */
        val scratch = DeskCatSpot(p.couchX - 70f, FLOOR)
        val bed = DeskCatSpot(702f + (layout.bedRight - 826f) * .57f, 578f)
    }
    private var cached: Pair<DeskLayout, RoomSpots> = DeskLayout.STANDARD to RoomSpots(DeskLayout.STANDARD)
    fun spots(layout: DeskLayout): RoomSpots = cached.takeIf { it.first == layout }?.second ?: RoomSpots(layout).also { cached = layout to it }

    // Office props
    /** On the desk beside the pen, facing it. */
    val pen = DeskCatSpot(150f, 516f)
    /** On the desk under the lamp's shade. */
    val lamp = DeskCatSpot(232f, 516f)
    /** Sitting in the cardboard box on the office floor. */
    val box = DeskCatSpot(282f, 632f)

    // Bedroom (right)
    val windowPerch = DeskCatSpot(538f, 294f)
    val food = DeskCatSpot(564f, FLOOR)
    val litter = DeskCatSpot(484f, 626f)
    val arcade = DeskCatSpot(548f, FLOOR)

    fun atObject(obj: DeskObject, layout: DeskLayout = DeskLayout.STANDARD): DeskCatAct = spots(layout).let { sp -> when (obj) {
        DeskObject.BUILD -> DeskCatAct(computer, DeskCatPose.TYPE, 3_600, DeskCatFace.FOCUSED)
        DeskObject.INBOX -> DeskCatAct(letters, DeskCatPose.LETTER, 3_600, DeskCatFace.CURIOUS)
        DeskObject.PROJECT, DeskObject.TODAY -> DeskCatAct(board, DeskCatPose.WRITE, 3_600, DeskCatFace.FOCUSED)
        DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> DeskCatAct(DeskCatSpot(64f, FLOOR), DeskCatPose.SNIFF, 2_400, DeskCatFace.CURIOUS)
        DeskObject.IDEA -> DeskCatAct(home, DeskCatPose.SNIFF, 2_400, DeskCatFace.LOVE)
        DeskObject.MONEY -> DeskCatAct(DeskCatSpot(obj.catX, 516f), DeskCatPose.SNIFF, 2_400, DeskCatFace.CURIOUS)
        DeskObject.BIG_WINDOW, DeskObject.TOWER_BED -> DeskCatAct(windowPerch, DeskCatPose.PERCH, 5_000, DeskCatFace.CURIOUS)
        DeskObject.ARCADE -> DeskCatAct(arcade, DeskCatPose.GAME, 5_000, DeskCatFace.PLAYFUL, facingLeft = true)
        DeskObject.BED -> DeskCatAct(sp.bed, DeskCatPose.SLEEP, 9_000, DeskCatFace.ASLEEP)
        DeskObject.FOOD -> DeskCatAct(food, DeskCatPose.EAT, 4_500, DeskCatFace.CHEW, facingLeft = false)
        DeskObject.LITTER -> DeskCatAct(litter, DeskCatPose.DIG, 3_600, DeskCatFace.FOCUSED)
        DeskObject.BOOKSHELF -> DeskCatAct(sp.bookshelf, DeskCatPose.READ, 4_500, DeskCatFace.FOCUSED)
        // The TV hangs to the right of the couch.
        DeskObject.BIG_TV -> DeskCatAct(sp.couch, DeskCatPose.WATCH, 5_000, DeskCatFace.CURIOUS, facingLeft = false)
        DeskObject.STEREO -> DeskCatAct(sp.stereo, DeskCatPose.BOB, 5_000, DeskCatFace.PURR, facingLeft = false)
        DeskObject.COUCH -> DeskCatAct(sp.couch, DeskCatPose.SLEEP, 8_000, DeskCatFace.ASLEEP)
        DeskObject.TOWER_LIVING -> DeskCatAct(sp.livingTower, DeskCatPose.PERCH, 4_500, DeskCatFace.HAPPY)
        // Admires the picture from the top of the tower beside it.
        DeskObject.PICTURE -> DeskCatAct(sp.livingTower, DeskCatPose.PERCH, 4_000, DeskCatFace.LOVE, facingLeft = true)
        else -> DeskCatAct(DeskCatSpot(obj.catX, 516f), DeskCatPose.SNIFF, 2_400)
    } }

    /** Long, irregular rests; no interval at which the room must perform. */
    fun restMs(random: Random, baseMs: Long): Long {
        val base = baseMs.coerceIn(4_000, 60_000)
        return random.nextLong(base / 2, base + base / 2 + 1)
    }

    /** Office-only idle acting (the original Desk). */
    fun idle(random: Random, previous: DeskCatPose, hasMail: Boolean, hasGoal: Boolean): DeskCatAct {
        val choices = mutableListOf(DeskCatPose.GROOM, DeskCatPose.STRETCH, DeskCatPose.SNIFF, DeskCatPose.WALK)
        if (hasMail) choices += DeskCatPose.LETTER
        if (hasGoal) choices += DeskCatPose.WRITE
        choices.remove(previous)
        val pose = choices.random(random)
        val spot = when (pose) {
            DeskCatPose.LETTER -> letters
            DeskCatPose.WRITE -> board
            else -> if (random.nextBoolean()) DeskCatSpot(random.nextInt(32, 275).toFloat(), random.nextInt(654, 659).toFloat())
                    else DeskCatSpot(random.nextInt(126, 251).toFloat(), 516f)
        }
        val face = when (pose) {
            DeskCatPose.GROOM -> DeskCatFace.PURR
            DeskCatPose.STRETCH -> if (random.nextBoolean()) DeskCatFace.YAWN else DeskCatFace.SLEEPY
            DeskCatPose.LETTER -> DeskCatFace.CURIOUS
            DeskCatPose.WRITE -> DeskCatFace.FOCUSED
            else -> listOf(DeskCatFace.NEUTRAL, DeskCatFace.CURIOUS, DeskCatFace.PLAYFUL).random(random)
        }
        return DeskCatAct(spot, if (pose == DeskCatPose.WALK) DeskCatPose.REST else pose, random.nextLong(2_400, 5_201), face)
    }

    /** The office's personality acts: web "research", the keyboard, the pen, the lamp, the box. */
    fun officeTricks(random: Random, ctx: DeskIdleContext): List<DeskCatAct> = buildList {
        add(DeskCatAct(computer, DeskCatPose.TYPE, random.nextLong(5_000, 7_001), DeskCatFace.FOCUSED, facingLeft = true, trick = DeskCatTrick.RESEARCH))
        add(DeskCatAct(computer, DeskCatPose.SIT, 4_500, DeskCatFace.HAPPY, facingLeft = true, trick = DeskCatTrick.KEYBOARD))
        add(DeskCatAct(pen, DeskCatPose.BAT, 1_600, DeskCatFace.PLAYFUL, facingLeft = false, trick = DeskCatTrick.PEN))
        add(DeskCatAct(lamp, DeskCatPose.BAT, 1_200, DeskCatFace.CURIOUS, facingLeft = false, trick = DeskCatTrick.LAMP))
        add(DeskCatAct(box, DeskCatPose.HIDE, random.nextLong(5_000, 8_001), DeskCatFace.PLAYFUL, facingLeft = true, trick = DeskCatTrick.BOX))
        if (ctx.night) add(DeskCatAct(lamp, DeskCatPose.BAT, 1_200, DeskCatFace.PLAYFUL, facingLeft = false, trick = DeskCatTrick.MOTH))
    }

    /** Acts that work in any room: zoomies, chasing its tail, a toy mouse. */
    fun anywhereTricks(random: Random, room: DeskRoom): List<DeskCatAct> {
        val x = (room.left + 40f + random.nextFloat() * (room.right - room.left - 190f)).coerceIn(room.left + 20f, room.right - 130f)
        val floor = DeskCatSpot(x, FLOOR)
        return listOf(
            DeskCatAct(floor, DeskCatPose.WALK, 1_000, DeskCatFace.PLAYFUL, trick = DeskCatTrick.ZOOMIES),
            DeskCatAct(floor, DeskCatPose.SIT, 1_000, DeskCatFace.PLAYFUL, trick = DeskCatTrick.TAIL),
            DeskCatAct(floor, DeskCatPose.SNIFF, 1_000, DeskCatFace.CURIOUS, trick = DeskCatTrick.MOUSE),
        )
    }

    /**
     * Whole-apartment idle acting. [roomChance] is how often the cat heads to a different room
     * (higher on the folded cover screen, where it wanders back and forth between rooms).
     */
    fun apartmentIdle(random: Random, previous: DeskCatPose, current: DeskRoom, hasMail: Boolean, hasGoal: Boolean,
                      musicPlaying: Boolean, videoPlaying: Boolean, roomChance: Float,
                      layout: DeskLayout = DeskLayout.STANDARD, ctx: DeskIdleContext = DeskIdleContext()): DeskCatAct {
        val sp = spots(layout)
        val room = if (random.nextFloat() < roomChance) DeskRoom.entries.filter { it != current }.random(random) else current
        val acts: List<DeskCatAct> = when (room) {
            DeskRoom.OFFICE -> if (random.nextFloat() < .45f) officeTricks(random, ctx) + anywhereTricks(random, room)
                else return idle(random, previous, hasMail, hasGoal)
            DeskRoom.LIVING -> buildList {
                add(DeskCatAct(sp.couch, DeskCatPose.SLEEP, random.nextLong(6_000, 10_001), DeskCatFace.ASLEEP))
                add(DeskCatAct(sp.livingTower, DeskCatPose.PERCH, random.nextLong(3_600, 6_001), DeskCatFace.HAPPY))
                add(DeskCatAct(sp.scratch, DeskCatPose.SCRATCH, 3_000, DeskCatFace.PLAYFUL, facingLeft = false))
                add(DeskCatAct(sp.bookshelf, DeskCatPose.READ, random.nextLong(4_000, 6_001), DeskCatFace.FOCUSED))
                if (videoPlaying) add(DeskCatAct(sp.couch, DeskCatPose.WATCH, 6_000, DeskCatFace.CURIOUS, facingLeft = false))
                if (musicPlaying) add(DeskCatAct(sp.stereo, DeskCatPose.BOB, 6_000, DeskCatFace.PURR, facingLeft = false))
                // Reading on the couch, the yarn, the dangling toy, a belly roll on the rug.
                add(DeskCatAct(sp.couch, DeskCatPose.READ, random.nextLong(5_000, 7_001), DeskCatFace.FOCUSED, facingLeft = false))
                add(DeskCatAct(sp.yarn, DeskCatPose.BAT, 1_200, DeskCatFace.PLAYFUL, facingLeft = false, trick = DeskCatTrick.YARN))
                add(DeskCatAct(sp.towerStep, DeskCatPose.BAT, 1_200, DeskCatFace.PLAYFUL, facingLeft = false, trick = DeskCatTrick.TOY))
                add(DeskCatAct(sp.rug, DeskCatPose.ROLL, random.nextLong(3_500, 5_001), DeskCatFace.LOVE, trick = DeskCatTrick.ROLL))
                addAll(anywhereTricks(random, room))
            }
            DeskRoom.BEDROOM -> listOf(
                DeskCatAct(sp.bed, DeskCatPose.SLEEP, random.nextLong(7_000, 12_001), DeskCatFace.ASLEEP),
                DeskCatAct(windowPerch, DeskCatPose.PERCH, random.nextLong(4_000, 7_001), DeskCatFace.CURIOUS),
                DeskCatAct(food, DeskCatPose.EAT, random.nextLong(3_600, 5_201), DeskCatFace.CHEW, facingLeft = false),
                DeskCatAct(litter, DeskCatPose.DIG, 3_000, DeskCatFace.FOCUSED),
                DeskCatAct(arcade, DeskCatPose.GAME, random.nextLong(4_000, 6_001), DeskCatFace.PLAYFUL, facingLeft = true),
                DeskCatAct(DeskCatSpot(640f, FLOOR), DeskCatPose.STRETCH, 2_600, DeskCatFace.YAWN),
                // Kneading the bed before a nap, watching a bird on the sill, a sunbeam nap.
                DeskCatAct(sp.bed, DeskCatPose.KNEAD, 3_000, DeskCatFace.PURR, trick = DeskCatTrick.KNEAD),
                DeskCatAct(windowPerch, DeskCatPose.PERCH, random.nextLong(5_000, 7_001), DeskCatFace.CURIOUS, trick = DeskCatTrick.BIRD),
            ) + listOfNotNull(ctx.sunbeamX?.takeUnless { ctx.night }?.let {
                DeskCatAct(DeskCatSpot((it - 55f).coerceIn(430f, 700f), FLOOR), DeskCatPose.SLEEP, random.nextLong(8_000, 12_001), DeskCatFace.ASLEEP)
            }) + anywhereTricks(random, room)
        }
        // Don't repeat the last pose; tricks are always welcome.
        return acts.filter { it.trick != DeskCatTrick.NONE || it.pose != previous }.ifEmpty { acts }.random(random)
    }

    /** The face a pose settles into when the act gives none. */
    fun defaultFace(pose: DeskCatPose): DeskCatFace = when (pose) {
        DeskCatPose.SLEEP -> DeskCatFace.ASLEEP
        DeskCatPose.EAT -> DeskCatFace.CHEW
        DeskCatPose.TYPE, DeskCatPose.WRITE, DeskCatPose.READ, DeskCatPose.DIG -> DeskCatFace.FOCUSED
        DeskCatPose.LETTER, DeskCatPose.LISTEN, DeskCatPose.WATCH, DeskCatPose.PERCH -> DeskCatFace.CURIOUS
        DeskCatPose.TALK -> DeskCatFace.HAPPY
        DeskCatPose.BOB, DeskCatPose.GROOM -> DeskCatFace.PURR
        DeskCatPose.GAME, DeskCatPose.SCRATCH -> DeskCatFace.PLAYFUL
        DeskCatPose.LOAF -> DeskCatFace.SLEEPY
        DeskCatPose.BAT, DeskCatPose.HIDE -> DeskCatFace.PLAYFUL
        DeskCatPose.KNEAD -> DeskCatFace.PURR
        DeskCatPose.ROLL -> DeskCatFace.LOVE
        else -> DeskCatFace.NEUTRAL
    }

    /** What the cat says when it is petted (tapped). */
    val petLines = listOf("Mrrp?", "Hi!", "Purrr…", "Head scratches accepted.", "Mew.", "You found me.")

    fun description(pose: DeskCatPose, x: Float): String = when (pose) {
        DeskCatPose.REST -> "resting at ${x.toInt()}"
        DeskCatPose.WALK -> "walking around the room"
        DeskCatPose.GROOM -> "washing its face"
        DeskCatPose.STRETCH -> "stretching"
        DeskCatPose.SNIFF -> "exploring the room"
        DeskCatPose.TYPE -> "using the computer"
        DeskCatPose.LETTER -> "reading a letter"
        DeskCatPose.WRITE -> "writing on the board"
        DeskCatPose.LISTEN -> "listening to you"
        DeskCatPose.TALK -> "speaking to you"
        DeskCatPose.SLEEP -> "napping"
        DeskCatPose.EAT -> "eating"
        DeskCatPose.DIG -> "using the litter box"
        DeskCatPose.PERCH -> "sitting on the cat tower"
        DeskCatPose.WATCH -> "watching TV"
        DeskCatPose.GAME -> "playing a video game"
        DeskCatPose.BOB -> "listening to music"
        DeskCatPose.READ -> "reading a book"
        DeskCatPose.SCRATCH -> "scratching the post"
        DeskCatPose.SIT -> "sitting"
        DeskCatPose.LOAF -> "lying down"
        DeskCatPose.BAT -> "batting at something"
        DeskCatPose.KNEAD -> "kneading"
        DeskCatPose.ROLL -> "rolling on its back"
        DeskCatPose.HIDE -> "hiding in a box"
    }
}
