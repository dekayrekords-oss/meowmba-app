package com.example.meowmba

import com.example.meowmba.core.agent.tools.MediaGenerateAudioTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.MusicGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeMusicGenerator(
    private val available: Boolean,
    private val succeeds: Boolean
) : MusicGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, durationSec: Int): Result<ByteArray> {
        callCount++
        return if (succeeds) Result.success(byteArrayOf(1, 2, 3))
               else Result.failure(IllegalStateException("boom"))
    }
}

@RunWith(RobolectricTestRunner::class)
class MediaGenerateAudioToolRoutingTest {

    private fun workspace(): Workspace =
        Workspace(kotlin.io.path.createTempDirectory().toFile())

    @Test
    fun `uses the remote generator when configured and available`() = runTest {
        val remote = FakeMusicGenerator(available = true, succeeds = true)
        val tool = MediaGenerateAudioTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `falls back to the tone generator when nothing is configured`() = runTest {
        val tool = MediaGenerateAudioTool(workspace())

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("audio_"))
    }

    @Test
    fun `falls back to the tone generator when the remote generator fails`() = runTest {
        val remote = FakeMusicGenerator(available = true, succeeds = false)
        val tool = MediaGenerateAudioTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }
}
