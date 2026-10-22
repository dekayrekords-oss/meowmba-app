package com.example.meowmba.core.robot.cozmo

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Cozmo firmware 2381 wire format, independently encoded from PyCozmo's MIT protocol reference. */
object CozmoProtocol {
    const val HOST = "172.31.1.1"
    const val PORT = 5551
    const val MOD = 65534
    const val OOB = 65535
    private val magic = byteArrayOf(67, 79, 90, 3, 82, 69, 1)
    data class Packet(val type: Int = 4, val id: Int = -1, val data: ByteArray = byteArrayOf())
    data class Frame(val type: Int, val first: Int, val last: Int, val ack: Int, val packets: List<Packet>)
    fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    fun reader(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun next(seq: Int) = (seq + 1) % MOD
    fun distance(from: Int, to: Int) = (to - from + MOD) % MOD

    fun encode(frame: Frame): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(buffer(14).put(magic).put(frame.type.toByte()).putShort((frame.first + 1).toShort())
            .putShort((frame.last + 1).toShort()).putShort((frame.ack + 1).toShort()).array())
        frame.packets.forEach { p ->
            if (frame.type != 11) {
                val hasId = p.type == 4 || p.type == 5
                out.write(buffer(3).put(p.type.toByte()).putShort((p.data.size + if (hasId) 1 else 0).toShort()).array())
                if (hasId) out.write(p.id)
            }
            out.write(p.data)
        }
        return out.toByteArray().also { require(it.size <= 1051) { "Cozmo frame too large" } }
    }

    fun decode(bytes: ByteArray): Frame? = runCatching {
        require(bytes.size in 14..1051 && bytes.copyOfRange(0, 7).contentEquals(magic))
        val b = reader(bytes).apply { position(7) }
        val type = b.get().toInt() and 255
        require(type in listOf(1, 2, 3, 7, 9, 11))
        fun seq() = ((b.short.toInt() and 65535) - 1) and 65535
        val first = seq(); val last = seq(); val ack = seq()
        val packets = mutableListOf<Packet>()
        if (type == 11) {
            require(b.remaining() == 17)
            packets += Packet(11, data = ByteArray(17).also { b.get(it) })
        } else if (type == 7 || type == 9) {
            while (b.hasRemaining()) {
                require(b.remaining() >= 3)
                val pt = b.get().toInt() and 255
                val size = b.short.toInt() and 65535
                require(pt in listOf(2, 3, 4, 5, 10, 11) && size <= b.remaining())
                val hasId = pt == 4 || pt == 5
                require(!hasId || size > 0)
                val id = if (hasId) b.get().toInt() and 255 else -1
                packets += Packet(pt, id, ByteArray(size - if (hasId) 1 else 0).also { b.get(it) })
            }
            val reliable = packets.count { it.type < 5 }
            require(reliable == 0 || (first in 0 until MOD && last == (first + reliable - 1) % MOD))
        } else require(!b.hasRemaining())
        Frame(type, first, last, ack, packets)
    }.getOrNull()

    fun command(id: Int, data: ByteArray = byteArrayOf()) = Packet(id = id, data = data)
    fun floats(vararg values: Float): ByteArray = buffer(values.size * 4).apply {
        values.forEach { require(it.isFinite()); putFloat(it) }
    }.array()
    fun wheels(left: Float, right: Float) = command(0x32, floats(left.coerceIn(-60f, 60f), right.coerceIn(-60f, 60f), 100f, 100f))
    fun head(degrees: Float) = command(0x37, floats(Math.toRadians(degrees.coerceIn(-25f, 44.5f).toDouble()).toFloat(), 1.5f, 4f, 0f) + byteArrayOf(0))
    fun lift(height: Float) = command(0x36, floats(height.coerceIn(32f, 92f), 1.5f, 4f, 0f) + byteArrayOf(0))
    fun camera(on: Boolean) = command(0x4c, byteArrayOf(if (on) 1 else 0, 4))
    fun light(color: Int, side: Boolean = false): Packet {
        val b = buffer((if (side) 2 else 3) * 10 + 1)
        repeat(if (side) 2 else 3) { b.putShort(color.toShort()).putShort(color.toShort()).putInt(0).putShort(0) }
        return command(if (side) 0x11 else 3, b.array())
    }

    /** Full columns only; no dependence on the decoder's implicit skip/repeat cursor. */
    fun face(pixels: BooleanArray): Packet {
        require(pixels.size == 128 * 32)
        val rle = ByteArrayOutputStream()
        for (x in 0 until 128) {
            var y = 0
            while (y < 32) {
                val lit = pixels[y * 128 + x]
                var n = 1
                while (y + n < 32 && pixels[(y + n) * 128 + x] == lit) n++
                rle.write((if (n <= 16) 0x80 else 0xc0) or (((n - 1) % 16) shl 2) or
                    (if (lit) { if (y + n == 32) 2 else 1 } else 0))
                y += n
            }
        }
        val data = rle.toByteArray()
        require(data.size <= 1000)
        return command(0x97, buffer(2).putShort(data.size.toShort()).array() + data)
    }

    fun eyes(expression: String, blink: Boolean = false): BooleanArray {
        require(expression in EXPRESSIONS)
        return BooleanArray(4096) { i ->
            val x = i % 128; val y = i / 128
            val cx = if (x < 64) 38 else 90
            val dx = kotlin.math.abs(x - cx)
            when {
                expression == "sleepy" || blink -> dx < 16 && y in 19..21
                expression == "happy" -> dx < 17 && y in (8 + dx / 4)..(12 + dx / 4)
                expression == "surprised" -> dx * dx + (y - 16) * (y - 16) in 70..160
                expression == "curious" -> dx < 14 && y in (if (x < 64) 7..24 else 13..24)
                expression == "sad" -> dx < 16 && y in (15 - dx / 5)..25
                else -> dx < 16 && y in 7..24 && (dx < 12 || y in 10..21)
            }
        }
    }
    val EXPRESSIONS = listOf("neutral", "happy", "curious", "surprised", "sleepy", "sad")
    val COLORS = linkedMapOf("green" to 0x03e0, "blue" to 0x001f, "red" to 0x7c00,
        "yellow" to 0x7fe0, "purple" to 0x7c1f, "white" to 0x7fff, "off" to 0)
}

data class CozmoTelemetry(
    val batteryVolts: Float, val headDegrees: Float, val liftMm: Float,
    val leftSpeed: Float, val rightSpeed: Float, val x: Float, val y: Float,
    val status: Int, val cliffs: List<Int>, val receivedAt: Long,
) {
    val pickedUp get() = status and 8 != 0
    val falling get() = status and 0x20 != 0
    val onCharger get() = status and 0x1000 != 0
    val charging get() = status and 0x2000 != 0
    val cliff get() = status and 0x4000 != 0
    fun motionBlock(now: Long): String? = when {
        now - receivedAt !in 0..500 -> "Robot sensors are stale"
        pickedUp -> "Cozmo is being held"
        falling -> "Cozmo is falling"
        cliff -> "Cozmo sees an edge"
        onCharger -> "Take Cozmo off his charger first"
        else -> null
    }
    companion object {
        fun decode(data: ByteArray, now: Long): CozmoTelemetry? = runCatching {
            require(data.size >= 91)
            val b = CozmoProtocol.reader(data)
            fun f(at: Int) = b.getFloat(at).also { require(it.isFinite()) }
            CozmoTelemetry(f(72), Math.toDegrees(f(40).toDouble()).toFloat(), f(44), f(32), f(36),
                f(12), f(16), b.getInt(76), (0..3).map { b.getShort(80 + it * 2).toInt() and 65535 }, now)
        }.getOrNull()
    }
}
