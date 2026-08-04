package com.example.meowmba

import com.example.meowmba.core.agent.tools.MediaGenerateVideoTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.VideoGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeVideoGenerator(
    private val available: Boolean,
    private val succeeds: Boolean
) : VideoGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, onProgress: (String) -> Unit): Result<ByteArray> {
        callCount++
        onProgress("testing progress callback")
        return if (succeeds) Result.success(byteArrayOf(1, 2, 3))
               else Result.failure(IllegalStateException("boom"))
    }
}

@RunWith(RobolectricTestRunner::class)
class MediaGenerateVideoToolRoutingTest {

    private fun workspace(): Workspace =
        Workspace(kotlin.io.path.createTempDirectory().toFile())

    @Test
    fun `uses the remote generator when configured and available`() = runTest {
        val remote = FakeVideoGenerator(available = true, succeeds = true)
        val tool = MediaGenerateVideoTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `falls back to the frame-manifest placeholder when nothing is configured`() = runTest {
        val tool = MediaGenerateVideoTool(workspace())

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("video_"))
    }

    @Test
    fun `falls back to the placeholder when the remote generator fails`() = runTest {
        val remote = FakeVideoGenerator(available = true, succeeds = false)
        val tool = MediaGenerateVideoTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }
}
