package com.example.meowmba.ui.desk

import kotlin.random.Random

/** Acting only: these poses never execute a tool, send mail or change a goal. */
enum class DeskCatPose { REST, WALK, GROOM, STRETCH, SNIFF, TYPE, LETTER, WRITE, LISTEN, TALK }
enum class DeskCatActivity { IDLE, LISTENING, THINKING, SPEAKING }

/** A real reply event, not a canned description of what the cat appears to do. */
data class DeskCatReply(val key: String, val text: String)

internal data class DeskCatSpot(val x: Float, val y: Float)
internal data class DeskCatAct(val spot: DeskCatSpot, val pose: DeskCatPose, val durationMs: Long)

internal object DeskCatBehavior {
    val home = DeskCatSpot(170f, 516f)
    val computer = DeskCatSpot(142f, 516f)
    val letters = DeskCatSpot(250f, 516f)
    val board = DeskCatSpot(126f, 300f)

    fun atObject(obj: DeskObject): DeskCatAct = when (obj) {
        DeskObject.BUILD -> DeskCatAct(computer, DeskCatPose.TYPE, 3_600)
        DeskObject.INBOX -> DeskCatAct(letters, DeskCatPose.LETTER, 3_600)
        DeskObject.PROJECT, DeskObject.TODAY -> DeskCatAct(board, DeskCatPose.WRITE, 3_600)
        DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> DeskCatAct(DeskCatSpot(64f, 656f), DeskCatPose.SNIFF, 2_400)
        else -> DeskCatAct(DeskCatSpot(obj.catX, 516f), DeskCatPose.SNIFF, 2_400)
    }

    /** Long, irregular rests; no interval at which the room must perform. */
    fun restMs(random: Random, baseMs: Long): Long {
        val base = baseMs.coerceIn(4_000, 60_000)
        return random.nextLong(base / 2, base + base / 2 + 1)
    }

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
        return DeskCatAct(spot, if (pose == DeskCatPose.WALK) DeskCatPose.REST else pose, random.nextLong(2_400, 5_201))
    }

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
    }
}
