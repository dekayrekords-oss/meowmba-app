package com.example.meowmba

import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.ui.companion.sameComputerConnection
import org.junit.Assert.*
import org.junit.Test

class ComputerConnectionIdentityTest {
    private val checked=ConnectionProfile("studio","Studio",ConnectionProfileType.SSH,"host.example",22,"artist","saved_key",workspaceRoot="/opt/meow-computer")
    @Test fun verificationCannotBeReusedAfterChangingAnyConnectionDetail() {
        assertTrue(sameComputerConnection(checked,checked.copy(displayName="Renamed")))
        assertFalse(sameComputerConnection(checked,checked.copy(hostKeyFingerprint="different-pin")))
        for(changed in listOf(checked.copy(id="other"),checked.copy(host="other.example"),checked.copy(port=2222),
            checked.copy(username="other"),checked.copy(credentialRef="other"),checked.copy(workspaceRoot="/tmp/other"),
            checked.copy(type=ConnectionProfileType.HTTPS_RUNTIME),checked.copy(pinHostKeyOnFirstConnect=false))) assertFalse(sameComputerConnection(changed,checked))
        assertFalse(sameComputerConnection(null,checked));assertFalse(sameComputerConnection(checked,null))
    }
}
