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
        return if (succeeds) Result.success(byteArrayOf(0, 0, 0, 16) + "ftypisom0000".toByteArray())
               else Result.failure(IllegalStateException("boom"))
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class MediaGenerateVideoToolRoutingTest {

    @Test
    fun `error payload masquerading as video is rejected`() = runTest {
        val ws = workspace()
        val remote = object : VideoGenerator {
            override fun isAvailable() = true
            override suspend fun generate(prompt: String, onProgress: (String) -> Unit) =
                Result.success("<html>server error</html>".toByteArray())
        }
        val result = MediaGenerateVideoTool(ws, remoteGenerator = { remote })
            .execute(mapOf("prompt" to "cat playing"))
        assertTrue(result is ToolResult.Failure)
        assertTrue(ws.root.listFiles().orEmpty().isEmpty())
    }

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
    fun `missing video backend fails without pretending a still frame is video`() = runTest {
        val tool = MediaGenerateVideoTool(workspace())

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("No playable video was created"))
    }

    @Test
    fun `remote generation failure remains a failure`() = runTest {
        val remote = FakeVideoGenerator(available = true, succeeds = false)
        val tool = MediaGenerateVideoTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Failure)
    }
}
