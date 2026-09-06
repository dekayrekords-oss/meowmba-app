package com.example.meowmba

import android.graphics.Bitmap
import com.example.meowmba.core.agent.tools.MediaGenerateTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.ImageGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

/** Fake backend so routing logic is testable with no real model or network. */
private class FakeImageGenerator(
    private val available: Boolean,
    private val succeeds: Boolean,
    val name: String
) : ImageGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, iterations: Int, seed: Int): Result<Bitmap> {
        callCount++
        return if (succeeds) {
            Result.success(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        } else {
            Result.failure(IllegalStateException("$name failed"))
        }
    }
}

/** Simulates a backend that throws instead of returning Result.failure — e.g. a
 *  native crash escaping a future JNI-based backend's generate(). */
private class ThrowingImageGenerator(val name: String) : ImageGenerator {
    var callCount = 0
    override fun isAvailable() = true
    override suspend fun generate(prompt: String, iterations: Int, seed: Int): Result<Bitmap> {
        callCount++
        throw RuntimeException("$name crashed")
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class MediaGenerateToolRoutingTest {

    private fun workspace(): Workspace {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        return Workspace(dir)
    }

    @Test
    fun `local-first default tries local before server`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, local.callCount)
        assertEquals(0, remote.callCount)
    }

    @Test
    fun `prefer-server setting tries server before local`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { true }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, remote.callCount)
        assertEquals(0, local.callCount)
    }

    @Test
    fun `falls back to the other backend when the primary fails`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = false, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, local.callCount)
        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `missing backend fails honestly without a placeholder`() = runTest {
        val tool = MediaGenerateTool(workspace())

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("No image was created"))
    }

    @Test
    fun `unavailable backend is skipped without being called`() = runTest {
        val local = FakeImageGenerator(available = false, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(0, local.callCount)
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `a backend that throws instead of failing still falls through to the next backend`() = runTest {
        val local = ThrowingImageGenerator(name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, local.callCount)
        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `every backend throwing reports failure instead of fake success`() = runTest {
        val local = ThrowingImageGenerator(name = "local")
        val remote = ThrowingImageGenerator(name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("No image was created"))
    }
}
