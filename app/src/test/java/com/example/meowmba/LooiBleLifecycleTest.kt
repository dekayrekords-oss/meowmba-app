package com.example.meowmba

import com.example.meowmba.core.robot.BleSessionLifecycle
import com.example.meowmba.core.robot.BleStopReceipt
import com.example.meowmba.core.robot.LooiBleLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake GATT completions only: these tests never obtain an Android radio. */
class LooiBleLifecycleTest {
    private class FakeGatt {
        val started = mutableListOf<String>()
        var closed = 0
        val rejected = mutableSetOf<String>()
        fun operation(key: String) = BleSessionLifecycle.Operation<FakeGatt>(key) {
            it.started += key
            key !in it.rejected
        }
    }

    private fun lifecycle() = BleSessionLifecycle<FakeGatt> { it.closed++ }

    private fun ready(session: BleSessionLifecycle<FakeGatt>, gatt: FakeGatt): Long {
        val token = checkNotNull(session.beginAttempt())
        assertTrue(session.bind(token, gatt))
        assertTrue(session.initialize(gatt, emptyList()))
        assertEquals(BleSessionLifecycle.Phase.READY, session.phase)
        return token
    }

    private fun stops(gatt: FakeGatt) = listOf("turn-zero", "brake-zero", "drive-zero").map(gatt::operation)

    @Test fun `close before worker starts is terminal and cannot open an attempt`() {
        val session = lifecycle()
        assertFalse(session.stop(emptyList(), closeAfter = true))
        assertNull(session.beginAttempt())
        assertFalse(session.submit(FakeGatt().operation("neck")))
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
    }

    @Test fun `late scan result cannot bind or resurrect an intentionally closed attempt`() {
        val session = lifecycle()
        val token = checkNotNull(session.beginAttempt())
        session.stop(emptyList(), closeAfter = true)
        val lateGatt = FakeGatt()
        assertFalse(session.acceptsAttempt(token))
        assertFalse(session.bind(token, lateGatt))
        assertEquals(1, lateGatt.closed)
        assertNull(session.connection)
        assertNull(session.beginAttempt())
    }

    @Test fun `concurrent connect cannot replace an active attempt or connection`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        val token = ready(session, gatt)
        assertNull(session.beginAttempt())
        assertFalse(session.bind(token, gatt))
        assertSame(gatt, session.connection)
        assertEquals(0, gatt.closed)
    }

    @Test fun `initialization is serial and ready waits for its last matching callback`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        session.bind(checkNotNull(session.beginAttempt()), gatt)
        session.initialize(gatt, listOf(gatt.operation("descriptor"), gatt.operation("read")))
        assertEquals(listOf("descriptor"), gatt.started)
        assertFalse(session.submit(gatt.operation("neck")))
        assertFalse(session.completed(gatt, "unrelated", true))
        session.completed(gatt, "descriptor", true)
        assertEquals(listOf("descriptor", "read"), gatt.started)
        assertEquals(BleSessionLifecycle.Phase.INITIALIZING, session.phase)
        session.completed(gatt, "read", true)
        assertEquals(BleSessionLifecycle.Phase.READY, session.phase)
    }

    @Test fun `busy ordinary movement is dropped not replayed behind stop`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        assertTrue(session.submit(gatt.operation("neck-one")))
        assertFalse(session.submit(gatt.operation("neck-two")))
        assertTrue(session.stop(stops(gatt)))
        assertFalse(session.submit(gatt.operation("neck-three")))
        assertEquals(listOf("neck-one"), gatt.started)
        session.completed(gatt, "neck-one", true)
        assertEquals(listOf("neck-one", "turn-zero"), gatt.started)
        session.completed(gatt, "turn-zero", true)
        session.completed(gatt, "brake-zero", true)
        session.completed(gatt, "drive-zero", true)
        assertEquals(listOf("neck-one", "turn-zero", "brake-zero", "drive-zero"), gatt.started)
        assertEquals(BleSessionLifecycle.Phase.READY, session.phase)
    }

    @Test fun `keep alive and motor writes share the same one operation lane`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        assertTrue(session.submit(gatt.operation("sensor-read")))
        assertFalse(session.submit(gatt.operation("neck")))
        session.completed(gatt, "sensor-read", true)
        assertTrue(session.submit(gatt.operation("neck")))
        assertFalse(session.submit(gatt.operation("sensor-read")))
        assertEquals(listOf("sensor-read", "neck"), gatt.started)
    }

    @Test fun `close cancels queued initialization and never transitions back to ready`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        session.bind(checkNotNull(session.beginAttempt()), gatt)
        session.initialize(gatt, listOf(gatt.operation("descriptor"), gatt.operation("queued-read")))
        session.stop(stops(gatt), closeAfter = true)
        assertFalse(session.accepts(gatt))
        assertFalse(session.initialize(gatt, listOf(gatt.operation("late-init"))))
        session.completed(gatt, "descriptor", true)
        session.completed(gatt, "turn-zero", true)
        session.completed(gatt, "brake-zero", true)
        session.completed(gatt, "drive-zero", true)
        assertEquals(listOf("descriptor", "turn-zero", "brake-zero", "drive-zero"), gatt.started)
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
        assertEquals(1, gatt.closed)
        assertFalse(session.completed(gatt, "queued-read", true))
    }

    @Test fun `close drains each zero stop before closing exact gatt`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        assertTrue(session.stop(stops(gatt), closeAfter = true))
        assertEquals(BleStopReceipt.PENDING, session.stopReceipt)
        assertEquals(listOf("turn-zero"), gatt.started)
        assertEquals(0, gatt.closed)
        session.completed(gatt, "turn-zero", true)
        assertEquals(listOf("turn-zero", "brake-zero"), gatt.started)
        assertEquals(BleStopReceipt.PENDING, session.stopReceipt)
        session.completed(gatt, "brake-zero", true)
        assertEquals(0, gatt.closed)
        session.completed(gatt, "drive-zero", true)
        assertEquals(1, gatt.closed)
        assertEquals(BleStopReceipt.PLATFORM_COMPLETED, session.stopReceipt)
        assertNull(session.connection)
        session.stop(emptyList(), closeAfter = true)
        assertEquals(BleStopReceipt.PLATFORM_COMPLETED, session.stopReceipt)
        assertEquals(1, gatt.closed)
        assertNull(session.beginAttempt())
    }

    @Test fun `repeated stop and close dedupe without changing deadline identity`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        session.stop(stops(gatt))
        val request = session.stopRequestId
        repeat(8) { session.stop(stops(gatt)) }
        session.stop(stops(gatt), closeAfter = true)
        repeat(8) { session.stop(emptyList(), closeAfter = true) }
        assertEquals(request, session.stopRequestId)
        assertEquals(listOf("turn-zero"), gatt.started)
        session.completed(gatt, "turn-zero", true)
        session.completed(gatt, "brake-zero", true)
        session.completed(gatt, "drive-zero", true)
        assertEquals(listOf("turn-zero", "brake-zero", "drive-zero"), gatt.started)
        assertEquals(1, gatt.closed)
    }

    @Test fun `missing stop callbacks expire unconfirmed and cannot resume or enqueue movement`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        val token = ready(session, gatt)
        session.submit(gatt.operation("neck"))
        session.stop(stops(gatt), closeAfter = true)
        assertTrue(session.expireStop(token, session.stopRequestId))
        assertEquals(BleStopReceipt.UNCONFIRMED, session.stopReceipt)
        assertEquals(listOf("neck"), gatt.started)
        assertEquals(1, gatt.closed)
        assertFalse(session.completed(gatt, "neck", true))
        assertFalse(session.submit(gatt.operation("late-neck")))
        assertFalse(session.expireStop(token, session.stopRequestId))
    }

    @Test fun `rejected first stop still attempts remaining zero channels and stays unconfirmed`() {
        val session = lifecycle()
        val gatt = FakeGatt().apply { rejected += "turn-zero" }
        ready(session, gatt)
        session.stop(stops(gatt))
        assertEquals(listOf("turn-zero", "brake-zero"), gatt.started)
        session.completed(gatt, "brake-zero", true)
        session.completed(gatt, "drive-zero", true)
        assertEquals(BleStopReceipt.UNCONFIRMED, session.stopReceipt)
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
        assertEquals(1, gatt.closed)
    }

    @Test fun `failed stop callback cannot be reported as completed or reopen movement`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        session.stop(stops(gatt))
        session.completed(gatt, "turn-zero", true)
        session.completed(gatt, "brake-zero", false)
        session.completed(gatt, "drive-zero", true)
        assertEquals(BleStopReceipt.UNCONFIRMED, session.stopReceipt)
        assertFalse(session.submit(gatt.operation("neck")))
        assertEquals(1, gatt.closed)
    }

    @Test fun `platform rejection and exception are returned without leaving a busy latch`() {
        val session = lifecycle()
        val gatt = FakeGatt().apply { rejected += "neck-rejected" }
        ready(session, gatt)
        assertFalse(session.submit(gatt.operation("neck-rejected")))
        assertFalse(session.submit(BleSessionLifecycle.Operation("throws") { error("fake rejection") }))
        assertTrue(session.submit(gatt.operation("neck-accepted")))
        assertEquals(listOf("neck-rejected", "neck-accepted"), gatt.started)
    }

    @Test fun `old attempt callbacks and timeouts cannot retire or advance a new explicit attempt`() {
        val session = lifecycle()
        val old = FakeGatt()
        val oldToken = ready(session, old)
        session.submit(old.operation("neck"))
        session.abort(oldToken)
        val current = FakeGatt()
        val currentToken = ready(session, current)
        session.submit(current.operation("neck"))
        assertFalse(session.acceptsAttempt(oldToken))
        assertFalse(session.abort(oldToken))
        assertFalse(session.expireStop(oldToken, session.stopRequestId))
        assertFalse(session.completed(old, "neck", true))
        assertFalse(session.disconnected(old))
        assertSame(current, session.connection)
        assertTrue(session.acceptsAttempt(currentToken))
        assertEquals(0, current.closed)
        assertTrue(session.completed(current, "neck", true))
    }

    @Test fun `foreign gatt or wrong characteristic cannot acknowledge a pending stop`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        session.stop(stops(gatt), closeAfter = true)
        assertFalse(session.completed(FakeGatt(), "turn-zero", true))
        assertFalse(session.completed(gatt, "brake-zero", true))
        assertEquals(listOf("turn-zero"), gatt.started)
        assertEquals(BleStopReceipt.PENDING, session.stopReceipt)
        assertEquals(0, gatt.closed)
    }

    @Test fun `unexpected disconnect retires operations without automatic new connection`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        ready(session, gatt)
        session.stop(stops(gatt))
        assertTrue(session.disconnected(gatt))
        assertEquals(BleStopReceipt.UNCONFIRMED, session.stopReceipt)
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
        assertEquals(1, gatt.closed)
        assertFalse(session.completed(gatt, "turn-zero", true))
        assertEquals(listOf("turn-zero"), gatt.started)
    }

    @Test fun `unverified wheel control remains disabled at the final link boundary`() {
        assertFalse(LooiBleLink.WHEEL_CONTROL_VERIFIED)
    }

    @Test fun `rejected initialization read cannot publish ready`() {
        val session = lifecycle()
        val gatt = FakeGatt().apply { rejected += "sensor-read" }
        session.bind(checkNotNull(session.beginAttempt()), gatt)
        session.initialize(gatt, listOf(gatt.operation("sensor-read")))
        assertTrue(session.initializationFailed)
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
        assertFalse(session.submit(gatt.operation("neck")))
        assertEquals(1, gatt.closed)
    }

    @Test fun `failed initialization descriptor callback cannot publish ready`() {
        val session = lifecycle()
        val gatt = FakeGatt()
        session.bind(checkNotNull(session.beginAttempt()), gatt)
        session.initialize(gatt, listOf(gatt.operation("sensor-cccd"), gatt.operation("sensor-read")))
        session.completed(gatt, "sensor-cccd", false)
        session.completed(gatt, "sensor-read", true)
        assertTrue(session.initializationFailed)
        assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
        assertEquals(1, gatt.closed)
    }

    @Test fun `explicit scan accepts suffixed or advertised looi names but not unrelated devices`() {
        assertTrue(LooiBleLink.matchesLooiName("looi_a444da", null))
        assertTrue(LooiBleLink.matchesLooiName(null, "LOOI_A444DA"))
        assertTrue(LooiBleLink.matchesLooiName("LOOI", null))
        assertFalse(LooiBleLink.matchesLooiName(null, null))
        assertFalse(LooiBleLink.matchesLooiName("headphones", "speaker"))
    }
}
