package com.example.meowmba.core.robot

import java.util.UUID

/**
 * BLE protocol for the TangibleFuture LOOI robot — the exact setup used by the
 * Memba 2 droid section (reverse-engineered by the sooperchargeforbots project).
 *
 * LOOI accepts 17-byte command frames on characteristic fe00. Frames carry a
 * rolling sequence byte — the robot silently drops any frame whose SEQ does
 * not increment. Commands behave like animation-script frames ("Script
 * Mode"): each frame carries its own duration in ticks (~20ms each).
 */
object LooiBleProtocol {

    // 16-bit BLE characteristic shorthands expanded to full 128-bit base UUIDs.
    private fun uuid16(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    val CHAR_COMMAND: UUID = uuid16("fe00")       // 17-byte script frames (turn/anim)
    val CHAR_MOTOR_BOOST: UUID = uuid16("ff02")   // hardware brake/stop: write 0x00
    val CHAR_MOTOR_SLOW: UUID = uuid16("fed0")    // precision FORWARD drive; 0x00 stops
    val CHAR_NECK: UUID = uuid16("fed1")          // neck position byte
    val CHAR_HEADLIGHT: UUID = uuid16("fed2")     // 0x00 off / 0x03 on
    val CHAR_SENSORS: UUID = uuid16("fed9")       // notify: touch/cliff; TOF/status partial

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val FRAME_SIZE = 17

    /** Byte offsets in a command frame. */
    object Field {
        const val SEQ = 0
        const val OPCODE = 1
        const val SUB_OP = 2
        const val MASK_A = 3
        const val MASK_B = 4
        const val PAYLOAD = 5     // bytes 5..8
        const val VALUE = 9       // magnitude/speed
        const val PARAM_1 = 10
        const val PARAM_2 = 11
        const val DURATION = 12   // execution time in ticks
        const val PARAM_3 = 13
        const val PARAM_4 = 14
        const val RES = 15
        const val END = 16        // footer/CRC
    }

    /** A single Script Mode frame. */
    data class Frame(
        val opcode: Int,
        val subOp: Int = 0,
        val maskA: Int = 0,
        val maskB: Int = 0,
        val payload: ByteArray = ByteArray(4),
        val value: Int = 0,
        val param1: Int = 0,
        val param2: Int = 0,
        val durationTicks: Int = 0,
        val param3: Int = 0,
        val param4: Int = 0,
        val end: Int = 0
    ) {
        init { require(payload.size == 4) { "payload must be exactly 4 bytes" } }

        override fun equals(other: Any?): Boolean =
            other is Frame && other.opcode == opcode && other.subOp == subOp &&
                other.maskA == maskA && other.maskB == maskB &&
                other.payload.contentEquals(payload) && other.value == value &&
                other.param1 == param1 && other.param2 == param2 &&
                other.durationTicks == durationTicks && other.param3 == param3 &&
                other.param4 == param4 && other.end == end

        override fun hashCode(): Int = opcode * 31 + value
    }

    /** Serializes a frame with the given rolling sequence number. */
    fun encode(frame: Frame, seq: Int): ByteArray {
        val out = ByteArray(FRAME_SIZE)
        out[Field.SEQ] = (seq and 0xFF).toByte()
        out[Field.OPCODE] = (frame.opcode and 0xFF).toByte()
        out[Field.SUB_OP] = (frame.subOp and 0xFF).toByte()
        out[Field.MASK_A] = (frame.maskA and 0xFF).toByte()
        out[Field.MASK_B] = (frame.maskB and 0xFF).toByte()
        frame.payload.copyInto(out, Field.PAYLOAD)
        out[Field.VALUE] = (frame.value and 0xFF).toByte()
        out[Field.PARAM_1] = (frame.param1 and 0xFF).toByte()
        out[Field.PARAM_2] = (frame.param2 and 0xFF).toByte()
        out[Field.DURATION] = (frame.durationTicks and 0xFF).toByte()
        out[Field.PARAM_3] = (frame.param3 and 0xFF).toByte()
        out[Field.PARAM_4] = (frame.param4 and 0xFF).toByte()
        out[Field.RES] = 0
        out[Field.END] = (frame.end and 0xFF).toByte()
        return out
    }

    /** Rolling 00-FF sequence counter; the robot drops non-incrementing frames. */
    class SequenceCounter(start: Int = 0) {
        private var seq = start and 0xFF
        fun next(): Int {
            val current = seq
            seq = (seq + 1) and 0xFF
            return current
        }
    }

    /**
     * Motion vocabulary derived from the sniffed known-good rotation packet
     * (00 07 00 FF 05 00 00 00 00 64 02 0A 96 02 14 00 02). The mask/param
     * bytes select the drive subsystem — opcode 0x07 without them just makes
     * the motors twitch (verified on the real robot). value = speed 0-100,
     * ticks ~ 20ms each.
     */
    /**
     * 17-byte Script-Mode turn. On this robot [subOp] 0 + speed 1–100 is the
     * verified LEFT spin. [right]=true sets subOp 0x01 (the unused sibling
     * of the golden packet) so RIGHT is not another LEFT.
     */
    fun rotateFrame(speedPercent: Int, ticks: Int, right: Boolean = false) = Frame(
        opcode = 0x07,
        subOp = if (right) 0x01 else 0x00,
        maskA = 0xFF, maskB = 0x05,
        value = speedPercent.coerceIn(0, 100),
        param1 = 0x02, param2 = 0x0A,
        durationTicks = ticks.coerceIn(1, 255),
        param3 = 0x02, param4 = 0x14, end = 0x02
    )

    /**
     * Captured tread scripts from this exact LOOI (Memba live session).
     * The 17-byte 0x07 rotateFrame only spins one way on this robot; these
     * 20-byte writes are what actually turn left vs right. First byte is SEQ.
     *
     * Left is the reverse-engineered capture. Right inverts its signed motion
     * bytes (0xEB/-21 → 0x15/+21, 0xF5/-11 → 0x0B/+11).
     */
    private val CAPTURED_TURN_LEFT_TAIL = byteArrayOf(
        0x02, 0x64, 0x00, 0x02, 0x64, 0x1E, 0x03,
        0x6A, 0xEB.toByte(), 0x02, 0x71, 0xEB.toByte(), 0x02,
        0x73, 0x32, 0x03, 0x79, 0xF5.toByte(), 0x02,
    )
    private val CAPTURED_TURN_RIGHT_TAIL = byteArrayOf(
        0x02, 0x64, 0x00, 0x02, 0x64, 0x1E, 0x03,
        0x6A, 0x15, 0x02, 0x71, 0x15, 0x02,
        0x73, 0x32, 0x03, 0x79, 0x0B, 0x02,
    )

    fun capturedTurn(seq: Int, left: Boolean): ByteArray {
        val tail = if (left) CAPTURED_TURN_LEFT_TAIL else CAPTURED_TURN_RIGHT_TAIL
        return byteArrayOf((seq and 0xFF).toByte()) + tail
    }
}
