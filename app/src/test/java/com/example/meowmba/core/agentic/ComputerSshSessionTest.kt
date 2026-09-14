package com.example.meowmba.core.agentic
import org.junit.Assert.*
import org.junit.Test
class ComputerSshSessionTest {
    private val key = "real handshake key".toByteArray()
    @Test fun pinsOnlyWhenFirstConnectAllowed() {
        assertTrue(ComputerSshSession.accepts(null, key, true))
        assertFalse(ComputerSshSession.accepts(null, key, false))
    }
    @Test fun savedKeyMustMatchEvenWhenTofuEnabled() {
        val pin = ComputerSshSession.fingerprint(key)
        assertTrue(ComputerSshSession.accepts(pin, key, false))
        assertFalse(ComputerSshSession.accepts(pin, "changed".toByteArray(), true))
        assertFalse(ComputerSshSession.accepts(pin.lowercase(), key, true))
    }
}
