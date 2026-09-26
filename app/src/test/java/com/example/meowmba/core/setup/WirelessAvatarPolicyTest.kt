package com.example.meowmba.core.setup

import org.junit.Assert.*
import org.junit.Test

class WirelessAvatarPolicyTest {
    @Test fun onlyWirelessTriggers() {
        for (plug in listOf(-1, 0, 1, 2, 8)) assertFalse(WirelessAvatarPolicy().update(plug, true, 0))
        assertTrue(WirelessAvatarPolicy().update(4, true, 0))
    }
    @Test fun disabledNeverTriggers() { assertFalse(WirelessAvatarPolicy().update(4, false, 0)) }
    @Test fun batteryUpdatesAndFullChargeDoNotReopen() {
        val policy = WirelessAvatarPolicy()
        assertTrue(policy.update(4, true, 0))
        repeat(100) { assertFalse(policy.update(4, true, 1000L * it)) }
    }
    @Test fun alignmentFlappingDoesNotReopen() {
        val policy = WirelessAvatarPolicy()
        assertTrue(policy.update(4, true, 0))
        assertFalse(policy.update(0, true, 1000))
        assertFalse(policy.update(4, true, 3000))
    }
    @Test fun unplugAndRedockTriggersAgain() {
        val policy = WirelessAvatarPolicy()
        assertTrue(policy.update(4, true, 0))
        assertFalse(policy.update(0, true, 1000))
        assertTrue(policy.update(4, true, 6000))
    }
    @Test fun unknownReadingDoesNotResetSession() {
        val policy = WirelessAvatarPolicy()
        assertTrue(policy.update(4, true, 0))
        assertFalse(policy.update(-1, true, 1000))
        assertFalse(policy.update(4, true, 10000))
    }
    @Test fun reenableWhileDockedTriggers() {
        val policy = WirelessAvatarPolicy()
        assertTrue(policy.update(4, true, 0))
        assertFalse(policy.update(4, false, 1000))
        assertTrue(policy.update(4, true, 2000))
    }
}
