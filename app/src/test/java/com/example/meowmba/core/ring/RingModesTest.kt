package com.example.meowmba.core.ring

import org.junit.Assert.*
import org.junit.Test

class RingModesTest {
    private val configured = RingState(controlsConsent = true, connected = true, paused = false, allApps = true,
        verifiedGestures = setOf("Single tap"), mappings = defaultRingMappings() + ("Single tap" to RingAction.MEOW_COMMAND),
        customCommands = mapOf("Single tap" to "  Show my next task  "))
    @Test fun commandsAreSingleUseAndRequireTheCurrentVerifiedConfiguration() {
        val mailbox = RingCommandMailbox()
        assertTrue(mailbox.offer("Single tap", configured, 100))
        val request = mailbox.pending.value!!
        assertFalse(mailbox.offer("Single tap", configured, 101))
        assertEquals("Show my next task", mailbox.take(request, configured, 102))
        assertNull(mailbox.take(request, configured, 103))
        for (blocked in listOf(configured.copy(paused = true), configured.copy(controlsConsent = false),
            configured.copy(connected = false), configured.copy(testing = true), configured.copy(sleeping = true),
            configured.copy(verifiedGestures = emptySet()), configured.copy(customCommands = mapOf("Single tap" to "Different")),
            configured.copy(mappings = defaultRingMappings()))) {
            assertTrue(mailbox.offer("Single tap", configured, 200))
            assertNull(mailbox.take(mailbox.pending.value!!, blocked, 201))
        }
    }
    @Test fun requestsExpireAndCannotCarryAnEmptyOrOversizedCommand() {
        val mailbox = RingCommandMailbox()
        assertTrue(mailbox.offer("Single tap", configured, 0))
        assertNull(mailbox.take(mailbox.pending.value!!, configured, 10_000))
        assertFalse(mailbox.offer("Single tap", configured.copy(customCommands = emptyMap()), 1))
        assertFalse(mailbox.offer("Single tap", configured.copy(customCommands = mapOf("Single tap" to "x".repeat(2001))), 1))
    }
    @Test fun presetsHaveDistinctActionsAndEveryActionKeepsTheSafetyGates() {
        assertEquals(RingAction.CLICK, ringModeMappings(RingMode.NAVIGATION)["Single tap"])
        assertEquals(RingAction.MEDIA_PLAY_PAUSE, ringModeMappings(RingMode.MEDIA)["Single tap"])
        assertEquals(RingAction.CAMERA_SHUTTER, ringModeMappings(RingMode.CAMERA)["Single tap"])
        for (action in RingAction.entries.filter { it != RingAction.NONE }) {
            val state = configured.copy(mappings = mapOf("Single tap" to action), showTarget = true)
            assertEquals(action, ringActionFor(state, "Single tap", "test.app"))
            assertEquals(RingAction.NONE, ringActionFor(state.copy(paused = true), "Single tap", "test.app"))
            assertEquals(RingAction.NONE, ringActionFor(state.copy(controlsConsent = false), "Single tap", "test.app"))
            assertEquals(RingAction.NONE, ringActionFor(state.copy(allApps = false), "Single tap", "test.app"))
            assertEquals(RingAction.NONE, ringActionFor(state.copy(verifiedGestures = emptySet()), "Single tap", "test.app"))
        }
    }
}
