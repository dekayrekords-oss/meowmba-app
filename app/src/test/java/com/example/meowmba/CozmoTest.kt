package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.robot.cozmo.*
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList

class CozmoTest {
    private val p = CozmoProtocol
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun `gestures are finite bounded and explicitly routed only to Cozmo`() {
        CozmoReactions.names.forEach { name ->
            val steps = CozmoReactions.steps(name)
            assertTrue(steps.isNotEmpty())
            assertTrue(steps.last().atMs <= 3000)
            assertEquals(steps.sortedBy { it.atMs }, steps)
            steps.forEach {
                it.head?.let { value -> assertTrue(value in -25f..44.5f) }
                it.lift?.let { value -> assertTrue(value in 32f..92f) }
                it.expression?.let { value -> assertTrue(value in p.EXPRESSIONS) }
            }
            assertEquals("cozmo.control", AgentRouter.route("Cozmo $name")?.toolId)
            assertNotEquals("cozmo.control", AgentRouter.route("LOOI $name")?.toolId)
        }
    }
    @Test fun `animation clock continues and stop cancels future gesture motors`() {
        FakeRobot().use { robot ->
            val session = CozmoSession(robot.client, {})
            session.start()
            try {
                waitFor { session.state.connected }
                val ticks = robot.commands.count { it.id == 0x8f }
                waitFor { robot.commands.count { it.id == 0x8f } >= ticks + 8 }
                session.action("greet")
                waitFor { robot.commands.any { it.id == 0x36 } }
                session.stop()
                waitFor { robot.commands.any { it.id == 0x3b } }
                val motors = robot.commands.count { it.id in CozmoReliability.MOTORS }
                Thread.sleep(1000)
                assertEquals(motors, robot.commands.count { it.id in CozmoReliability.MOTORS })
            } finally { session.close(); session.awaitClosed() }
        }
    }
    @Test fun `reset and stop match reference wire bytes`() {
        assertArrayEquals(hex("434f5a0352450101010001000000"), p.encode(CozmoProtocol.Frame(1, 0, 0, p.OOB, emptyList())))
        assertArrayEquals(hex("434f5a03524501070100010000000401003b"), p.encode(CozmoProtocol.Frame(7, 0, 0, p.OOB, listOf(p.command(0x3b)))))
    }
    @Test fun `strict parser rejects truncation malformed lengths and wrong magic`() {
        val valid = p.encode(CozmoProtocol.Frame(9, 0, 0, p.OOB, listOf(CozmoProtocol.Packet(2))))
        assertNotNull(p.decode(valid))
        // A 14-byte frame is a valid empty frame; incomplete headers/packet headers are not.
        for (length in (0 until valid.size).filter { it != 14 }) assertNull("length=$length", p.decode(valid.copyOf(length)))
        assertNull(p.decode(valid.copyOf().also { it[0] = 0 }))
        assertNull(p.decode(valid.copyOf().also { it[15] = 10 }))
    }
    @Test fun `motor values are bounded and NaN rejected`() {
        val wheels = p.reader(p.wheels(999f, -999f).data)
        assertEquals(60f, wheels.float, 0f); assertEquals(-60f, wheels.float, 0f)
        assertEquals(92f, p.reader(p.lift(900f).data).float, 0f)
        assertEquals(Math.toRadians(-25.0).toFloat(), p.reader(p.head(-900f).data).float, 0.00001f)
        assertTrue(runCatching { p.head(Float.NaN) }.isFailure)
        assertTrue(runCatching { p.wheels(Float.NaN, 0f) }.isFailure)
    }
    @Test fun `out of band data never advances reliable ack and gaps are buffered`() {
        val wire = CozmoReliability()
        val second = CozmoProtocol.Frame(9, 1, 1, p.OOB, listOf(p.command(0xed)))
        assertTrue(wire.receive(second).isEmpty())
        assertEquals(p.OOB, wire.ack)
        val event = CozmoProtocol.Frame(9, p.OOB, p.OOB, p.OOB, listOf(CozmoProtocol.Packet(5, 0xf0)))
        assertEquals(1, wire.receive(event).size); assertEquals(p.OOB, wire.ack)
        assertEquals(2, wire.receive(CozmoProtocol.Frame(9, 0, 0, p.OOB, listOf(CozmoProtocol.Packet(2)))).size)
        assertEquals(1, wire.ack)
        assertTrue(wire.receive(second).isEmpty())
        assertEquals(0, p.next(65533)); assertEquals(1, p.distance(65533, 0))
    }
    @Test fun `stop invalidates unacknowledged motor replay while retaining sequence numbers`() {
        val wire = CozmoReliability()
        wire.add(p.wheels(40f, 40f), 0); wire.add(p.head(10f), 0); wire.add(p.light(31), 0)
        wire.revokeMotion()
        assertEquals(listOf(0, 1, 2), wire.pending.keys.toList())
        assertEquals(listOf(0x3b, 0x3b, 3), wire.pending.values.map { it.packet.id })
        wire.receive(CozmoProtocol.Frame(11, p.OOB, p.OOB, 0, emptyList()))
        assertEquals(listOf(1, 2), wire.pending.keys.toList())
        wire.receive(CozmoProtocol.Frame(11, p.OOB, p.OOB, 100, emptyList()))
        assertEquals(2, wire.pending.size)
    }
    @Test fun `telemetry uses documented offsets and rejects stale and dangerous states`() {
        val t = CozmoTelemetry.decode(telemetry(42), 1000)!!
        assertEquals(4f, t.batteryVolts, 0f); assertEquals(45f, t.liftMm, 0f)
        assertEquals(listOf(100, 200, 300, 400), t.cliffs)
        assertNull(t.motionBlock(1100)); assertNotNull(t.motionBlock(1501))
        listOf(8, 0x20, 0x1000, 0x4000).forEach { assertNotNull(t.copy(status = it).motionBlock(1100)) }
        assertNull(CozmoTelemetry.decode(ByteArray(90), 1000))
        assertNull(CozmoTelemetry.decode(telemetry(1).also { p.reader(it).putFloat(72, Float.NaN) }, 1000))
    }
    @Test fun `all procedural expressions roundtrip the display RLE and fit one datagram`() {
        p.EXPRESSIONS.forEach { name ->
            val original = p.eyes(name)
            val encoded = p.face(original)
            val b = p.reader(encoded.data); val length = b.short.toInt() and 65535
            assertEquals(length, b.remaining()); assertTrue(length <= 1000)
            val decoded = BooleanArray(4096)
            var x = 0; var y = 0
            while (b.hasRemaining()) {
                val c = b.get().toInt() and 255
                assertTrue(c ushr 6 in 2..3)
                val count = ((c and 63) ushr 2) + 1 + if (c ushr 6 == 3) 16 else 0
                repeat(count) {
                    decoded[y * 128 + x] = c and 3 != 0
                    if (++y == 32) { y = 0; x++ }
                }
            }
            assertArrayEquals(original, decoded); assertEquals(128, x)
        }
    }
    @Test fun `camera drops gaps and bounds mini jpeg conversion`() {
        val c = CozmoCamera()
        fun chunk(id: Int, index: Int, bytes: ByteArray) = p.buffer(20 + bytes.size)
            .putInt(1).putInt(id).putInt(0).put(8).put(4).put(2).put(index.toByte()).putShort(0)
            .putShort(bytes.size.toShort()).put(bytes).array()
        assertNull(c.accept(chunk(1, 0, byteArrayOf(0, 33))))
        assertNull(c.accept(chunk(2, 1, byteArrayOf(34))))
        assertNull(c.accept(chunk(3, 0, byteArrayOf(0, 33))))
        val jpeg = c.accept(chunk(3, 1, byteArrayOf(34)))!!
        assertEquals(0xff.toByte(), jpeg[0]); assertEquals(0xd8.toByte(), jpeg[1])
        assertEquals(0xd9.toByte(), jpeg.last()); assertEquals(240.toByte(), jpeg[95])
        assertNull(CozmoCamera.miniGray(byteArrayOf(0, -1, -1)))
        assertNull(CozmoCamera.miniGray(byteArrayOf(1, 2)))
    }
    @Test fun `addressed voice commands route to Cozmo without changing ordinary robot routing`() {
        mapOf("Cozmo lift up" to "lift_up", "Cozmo lights blue" to "light_blue", "Cozmo happy" to "happy",
            "Cozmo stop" to "stop", "Hey Cozmo status" to "status", "Meow, Cozmo look up" to "look_up").forEach { (text, action) ->
            val intent = AgentRouter.route(text)
            assertEquals(text, "cozmo.control", intent?.toolId); assertEquals(text, action, intent?.args?.get("action"))
        }
        assertNotEquals("cozmo.control", AgentRouter.route("I saw Cozmo turn left yesterday")?.toolId)
        assertNotEquals("cozmo.control", AgentRouter.route("Cozmo forward and delete my files")?.toolId)
    }

    @Test fun `UDP handshake driving deadline stop and telemetry loss`() {
        FakeRobot().use { robot ->
            val states = CopyOnWriteArrayList<CozmoState>()
            val session = CozmoSession(robot.client, states::add)
            session.start()
            try {
                waitFor { session.state.connected }
                assertTrue(robot.commands.any { it.id == 0x9f })
                assertTrue(session.action("forward").contains("Enable short"))
                assertFalse(robot.commands.any { it.id == 0x32 })
                session.setWheels(true)
                waitFor { session.state.wheelsEnabled }
                assertTrue(session.action("forward").startsWith("Command queued"))
                waitFor { robot.commands.any { it.id == 0x32 } }
                waitFor { robot.commands.any { it.id == 0x3b } }
                assertTrue(session.state.wheelsEnabled)
                session.stop()
                waitFor { !session.state.wheelsEnabled }
                robot.sendTelemetry = false
                waitFor(4000) { !session.state.connected }
                assertTrue(session.state.message.contains("lost"))
            } finally { session.close(); session.awaitClosed() }
        }
    }
    @Test fun `incompatible firmware never enables motors`() {
        FakeRobot(version = 100).use { robot ->
            val session = CozmoSession(robot.client, {})
            session.start()
            try {
                waitFor { session.state.message.contains("Unsupported") }
                assertFalse(session.state.connected)
                assertFalse(robot.commands.any { it.id in listOf(0x25, 0x32, 0x36, 0x37) })
            } finally { session.close(); session.awaitClosed() }
        }
    }
    @Test fun `telemetry alone cannot hide an unacknowledged command link`() {
        FakeRobot().use { robot ->
            val session = CozmoSession(robot.client, {})
            session.start()
            try {
                waitFor { session.state.connected }
                robot.acknowledgeCommands = false
                session.action("look_up")
                waitFor(4000) { !session.state.connected }
                assertTrue(session.state.message.contains("not acknowledging"))
            } finally { session.close(); session.awaitClosed() }
        }
    }
    @Test fun `camera traffic preserves motor stop deadlines and sensor-only face reactions`() {
        FakeRobot().use { robot ->
            val images = CopyOnWriteArrayList<ByteArray>()
            val session = CozmoSession(robot.client, {}, { it?.let(images::add) })
            session.start()
            try {
                waitFor { session.state.connected }
                session.setCamera(true)
                waitFor { session.state.cameraEnabled }
                robot.sendImages = true
                waitFor { images.size >= 3 }
                session.setWheels(true)
                waitFor { session.state.wheelsEnabled }
                session.action("forward")
                waitFor { robot.commands.any { it.id == 0x32 } }
                waitFor { robot.commands.any { it.id == 0x3b } }
                assertTrue(session.state.connected)
                val motorCount = robot.commands.count { it.id in CozmoReliability.MOTORS }
                robot.status = 8
                waitFor { session.state.expression == "curious" }
                assertEquals(motorCount, robot.commands.count { it.id in CozmoReliability.MOTORS })
            } finally { session.close(); session.awaitClosed() }
        }
    }
    private fun telemetry(time: Int, status: Int = 0): ByteArray = p.buffer(91).apply {
        putInt(0, time); putFloat(44, 45f); putFloat(72, 4f)
        putInt(76, status)
        (0..3).forEach { putShort(80 + it * 2, ((it + 1) * 100).toShort()) }
    }.array()
    private fun waitFor(timeout: Long = 3000, condition: () -> Boolean) {
        val end = System.nanoTime() + timeout * 1_000_000
        while (!condition() && System.nanoTime() < end) Thread.sleep(10)
        assertTrue("Condition did not become true within ${timeout}ms", condition())
    }
    private inner class FakeRobot(version: Int = 2381) : AutoCloseable {
        val server = DatagramSocket(0, InetAddress.getLoopbackAddress()).apply { soTimeout = 25 }
        val client = DatagramSocket().apply { connect(InetAddress.getLoopbackAddress(), server.localPort) }
        val commands = CopyOnWriteArrayList<CozmoProtocol.Packet>()
        @Volatile var sendTelemetry = true
        @Volatile var acknowledgeCommands = true
        @Volatile var sendImages = false
        @Volatile var status = 0
        @Volatile private var running = true
        private val worker = Thread {
            var target: java.net.SocketAddress? = null
            var ack = p.OOB
            var timestamp = 0
            fun send(frame: CozmoProtocol.Frame) {
                val bytes = p.encode(frame)
                server.send(DatagramPacket(bytes, bytes.size, target))
            }
            while (running) {
                try {
                    val packet = DatagramPacket(ByteArray(2048), 2048)
                    try {
                        server.receive(packet); target = packet.socketAddress
                        val frame = p.decode(packet.data.copyOf(packet.length)) ?: continue
                        if (frame.type == 1) {
                            val json = "{\"version\":$version,\"build\":\"RELEASE\"}".toByteArray()
                            val firmware = p.buffer(4 + json.size).putShort(0).putShort(json.size.toShort()).put(json).array()
                            send(CozmoProtocol.Frame(9, 0, 2, ack, listOf(CozmoProtocol.Packet(2), p.command(0xee, firmware), p.command(0xed, ByteArray(12)))))
                        } else if (frame.type == 7) {
                            commands.addAll(frame.packets)
                            if (acknowledgeCommands) ack = frame.last
                        }
                    } catch (_: SocketTimeoutException) { }
                    if (target != null && sendTelemetry) send(CozmoProtocol.Frame(9, p.OOB, p.OOB, ack,
                        listOf(CozmoProtocol.Packet(5, 0xf0, telemetry(++timestamp, status)))))
                    if (target != null && sendImages) {
                        val chunk = p.buffer(22).putInt(timestamp).putInt(timestamp).putInt(0)
                            .put(8).put(4).put(1).put(0).putShort(0).putShort(2).put(0).put(33).array()
                        send(CozmoProtocol.Frame(9, p.OOB, p.OOB, ack, listOf(CozmoProtocol.Packet(5, 0xf2, chunk))))
                    }
                } catch (_: Exception) { if (running) throw AssertionError("Fake robot failed") }
            }
        }.apply { isDaemon = true; start() }
        override fun close() { running = false; server.close(); client.close(); worker.join(1000) }
    }
}
