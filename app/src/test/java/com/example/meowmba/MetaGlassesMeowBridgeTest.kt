package com.example.meowmba

import com.example.meowmba.core.agent.tools.ServerShellTool
import com.example.meowmba.core.agentic.AgenticSshPolicy
import com.example.meowmba.core.glasses.MetaGlassesMeowBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaGlassesMeowBridgeTest {

    @Test
    fun `strips Hey Meta and Hey Meow prefixes`() {
        val prepared = MetaGlassesMeowBridge.normalizeTranscript(
            "Hey Meta, Hey Meow check disk on the server",
        )
        assertNotNull(prepared)
        assertTrue(prepared!!.text.contains("check disk", ignoreCase = true))
        assertEquals(MetaGlassesMeowBridge.CommandIntent.SSH_MEOW, prepared.intent)
    }

    @Test
    fun `opencode intent adds tool hint`() {
        val prepared = MetaGlassesMeowBridge.normalizeTranscript("run opencode to list files in the repo")
        assertNotNull(prepared)
        assertEquals(MetaGlassesMeowBridge.CommandIntent.OPENCODE, prepared!!.intent)
        assertTrue(prepared.text.contains("opencode.run"))
    }

    @Test
    fun `empty after wake only is ignored`() {
        assertNull(MetaGlassesMeowBridge.normalizeTranscript("Hey Meta"))
        assertNull(MetaGlassesMeowBridge.normalizeTranscript("   "))
    }

    @Test
    fun `status line mentions bridge and host`() {
        val line = MetaGlassesMeowBridge.statusLine(
            hasSdk = true,
            glassesConnected = true,
            bridgeEnabled = true,
            defaultHost = "meow",
        )
        assertTrue(line.contains("bridge ON"))
        assertTrue(line.contains("meow"))
    }

    @Test
    fun `server shell argv policy matches agentic`() {
        assertFalse(AgenticSshPolicy.requiresApproval(ServerShellTool.shellArgv("uname -a")))
        assertTrue(AgenticSshPolicy.requiresApproval(ServerShellTool.shellArgv("rm -rf /tmp/x")))
    }
}
