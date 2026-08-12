package com.example.meowmba

import com.example.meowmba.core.robot.LooiMotorController
import com.example.meowmba.core.robot.LooiProtocol
import com.example.meowmba.core.robot.LoopbackTransport
import com.example.meowmba.core.robot.RobotManager
import com.example.meowmba.core.robot.SimulatedMotorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LOOI robot body is hardware we cannot hold in a unit test, so we lock
 * down everything that is pure: the wire protocol, the controller-over-transport
 * behavior, and the manager's honest simulated fallback.
 */
class RobotProtocolTest {
    @Test
    fun `servo frame clamps and formats`() {
        assertEquals("S 0 90.0", LooiProtocol.servo(0, 90f))
        assertEquals("S 4 180.0", LooiProtocol.servo(4, 250f)) // clamped high
        assertEquals("S 2 0.0", LooiProtocol.servo(2, -5f))    // clamped low
    }

    @Test
    fun `expression frame trims`() {
        assertEquals("E happy", LooiProtocol.expression("  happy "))
    }

    @Test
    fun `frame validators accept good and reject bad`() {
        assertTrue(LooiProtocol.isServoFrame("S 3 120.0"))
        assertFalse(LooiProtocol.isServoFrame("S 3 400.0"))   // out of range
        assertFalse(LooiProtocol.isServoFrame("S x 90.0"))    // bad id
        assertFalse(LooiProtocol.isServoFrame("E happy"))     // wrong verb
        assertTrue(LooiProtocol.isExpressionFrame("E alert"))
        assertFalse(LooiProtocol.isExpressionFrame("E "))      // blank face
    }
}

class LooiBleProtocolTest {
    @Test
    fun `rotate frames keep left and right as opposite bytes`() {
        val right = com.example.meowmba.core.robot.LooiBleProtocol.rotateFrame(40, 4)
        val left = com.example.meowmba.core.robot.LooiBleProtocol.rotateFrame(-40, 4)
        assertEquals(40, right.value)
        assertEquals(-40, left.value)
        val seq = com.example.meowmba.core.robot.LooiBleProtocol.SequenceCounter()
        val rightBytes = com.example.meowmba.core.robot.LooiBleProtocol.encode(right, seq.next())
        val leftBytes = com.example.meowmba.core.robot.LooiBleProtocol.encode(left, seq.next())
        val valueIdx = com.example.meowmba.core.robot.LooiBleProtocol.Field.VALUE
        assertEquals(40, rightBytes[valueIdx].toInt() and 0xFF)
        assertEquals(216, leftBytes[valueIdx].toInt() and 0xFF) // -40 as uint8
    }
}

class LooiMotorControllerTest {
    @Test
    fun `controller writes protocol frames to the transport`() {
        val loop = LoopbackTransport()
        val c = LooiMotorController(loop)
        c.setServo(1, 100f)
        c.setExpression("proud")
        assertEquals(listOf("S 1 100.0", "E proud"), loop.sent)
    }

    @Test
    fun `disconnected transport gets nothing`() {
        val dead = object : com.example.meowmba.core.robot.RobotTransport {
            override val connected = false
            override fun connect() = false
            override fun send(line: String) { error("should not send") }
            override fun close() {}
        }
        val c = LooiMotorController(dead)
        c.setServo(0, 90f)
        c.setExpression("happy")
        // No exception and no send — the guard held.
    }
}

class RobotManagerTest {
    @Test
    fun `defaults to a simulated body`() {
        val m = RobotManager()
        assertTrue(m.active is SimulatedMotorController)
        assertTrue(m.status.value.contains("simulated"))
    }

    @Test
    fun `unreachable base falls back to simulated and says so`() {
        val m = RobotManager()
        // Port 1 on loopback is closed -> connection refused, fast.
        val ok = m.connectLooi("127.0.0.1", 1)
        assertFalse(ok)
        assertTrue(m.active is SimulatedMotorController)
        assertTrue(m.status.value.contains("unreachable"))
    }

    @Test
    fun `keepPersonInFrame on a simulated body does not throw`() {
        val m = RobotManager()
        m.keepPersonInFrame(0.5f, -0.2f, 0.6f, nowMs = 1_000L)
        m.keepPersonInFrame(null, null, null, nowMs = 2_000L)
        m.active.setExpression("curious")
    }

    @Test
    fun `useSimulated always returns a working simulated controller`() {
        val m = RobotManager()
        m.useSimulated()
        assertTrue(m.active is SimulatedMotorController)
        // Driving it must not throw.
        m.active.setServo(0, 45f)
        m.active.setExpression("content")
    }
}
