package com.example.meowmba.neural
import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test
class NeuralMotorDirectorTest {
    @Test fun neuralFeedingRespectsOwnershipBusyStateAndDoesNotRepeat() {
        val d = NeuralMotorDirector()
        assertNull(d.choose(BehaviorAction.REST, 0, false, false, 1f))
        assertNull(d.choose(BehaviorAction.REST, 0, true, true, 1f))
        assertEquals("content_smile", d.choose(BehaviorAction.REST, 0, true, false, 1f))
        assertNull(d.choose(BehaviorAction.REST, 20_000, true, false, 1f))
        d.choose(BehaviorAction.REST, 21_000, true, false, 0f)
        assertEquals("content_smile", d.choose(BehaviorAction.REST, 22_000, true, false, 1f))
    }
    @Test fun respectsOwnershipCooldownAndClockReversal() {
        val d = NeuralMotorDirector()
        assertNull(d.choose(BehaviorAction.STARTLE, 0, false, false))
        assertNull(d.choose(BehaviorAction.STARTLE, 0, true, true))
        assertEquals("startled_peek", d.choose(BehaviorAction.STARTLE, 0, true, false))
        assertNull(d.choose(BehaviorAction.STARTLE, 20_000, true, false))
        assertNull(d.choose(BehaviorAction.INSPECT, 500, true, false))
        assertNull(d.choose(BehaviorAction.SETTLE, -1, true, false))
        assertNull(d.choose(BehaviorAction.REST, 11_000, true, false))
        assertEquals("curious_sniff", d.choose(BehaviorAction.INSPECT, 11_000, true, false))
    }
}
