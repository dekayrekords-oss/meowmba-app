package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class ComputerIdlePolicyTest {
    @Test fun quietUbuntuOrRemoteCommandsAreNotTreatedAsIdleShells() {
        assertFalse(computerAllowsSilenceDisconnect(CompanionTerminalTarget.Ubuntu.id))
        assertFalse(computerAllowsSilenceDisconnect("ssh-user-target"))
        assertFalse(computerAllowsSilenceDisconnect(null))
        assertTrue(computerAllowsSilenceDisconnect(CompanionTerminalTarget.LOCAL_ID))
    }
    @Test fun idlePollingBacksOffButActiveTaskRemainsResponsive() {
        val policy = ComputerPollCadence()
        assertEquals(10_000L, policy.after("idle"))
        assertEquals(20_000L, policy.after("idle"))
        repeat(20) { assertEquals(30_000L, policy.after("idle")) }
        assertEquals(3_000L, policy.after("busy"))
        assertEquals(3_000L, policy.after("retry"))
        assertEquals(10_000L, policy.after("idle"))
    }
    @Test fun failedConnectionsBackOffAndRecoverWithoutStaleIdleState() {
        val policy = ComputerPollCadence()
        assertEquals(6_000L, policy.after(null))
        assertEquals(12_000L, policy.after(null))
        assertEquals(24_000L, policy.after(null))
        repeat(20) { assertEquals(30_000L, policy.after(null)) }
        assertEquals(3_000L, policy.after("busy"))
        assertEquals(6_000L, policy.after(null))
        assertEquals(10_000L, ComputerPollCadence().after("idle"))
    }
}
