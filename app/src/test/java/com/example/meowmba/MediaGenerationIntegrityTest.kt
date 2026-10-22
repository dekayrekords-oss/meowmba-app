package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tiny deterministic PCM fixture, not model-generated music. */
internal fun validGeneratedWav(): ByteArray = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(40); put("WAVEfmt ".toByteArray()); putInt(16)
    putShort(1); putShort(1); putInt(22_050); putInt(44_100); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(4); putShort(100); putShort(-100)
}.array()

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class MediaGenerationIntegrityTest {
    private fun workspace() = Workspace(kotlin.io.path.createTempDirectory().toFile())

    @Test fun `music failure saves no substitute tones or artifacts`() = runTest {
        val ws = workspace()
        val result = MediaGenerateAudioTool(ws).execute(mapOf("prompt" to "mix a beautiful song"))
        assertTrue(result is ToolResult.Failure)
        assertTrue(ws.root.walkTopDown().none { it.isFile })
    }

    @Test fun `server error pages truncated WAV and empty WAV are not saved as songs`() = runTest {
        val emptyWav = validGeneratedWav().copyOf(44).apply { this[4] = 36; this[40] = 0 }
        for (payload in listOf("{\"error\":\"failed\"}".toByteArray(), "RIFF".toByteArray(), emptyWav,
            validGeneratedWav().copyOf(47))) {
            val ws = workspace()
            val generator = object : MusicGenerator {
                override fun isAvailable() = true
                override suspend fun generate(prompt: String, durationSec: Int) = Result.success(payload)
            }
            val result = MediaGenerateAudioTool(ws, { generator }).execute(mapOf("prompt" to "lofi song"))
            assertTrue(result is ToolResult.Failure)
            assertTrue(ws.root.walkTopDown().none { it.isFile })
        }
    }

    @Test fun `audio keeps native cancellation when thrown or wrapped in a Result`() = runTest {
        for (wrapped in listOf(false, true)) {
            val ws = workspace()
            val generator = object : MusicGenerator {
                override fun isAvailable() = true
                override suspend fun generate(prompt: String, durationSec: Int): Result<ByteArray> {
                    val stop = CancellationException("stopped")
                    if (wrapped) return Result.failure(stop) else throw stop
                }
            }
            try {
                MediaGenerateAudioTool(ws, { generator }).execute(mapOf("prompt" to "lofi song"))
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
            assertTrue(ws.root.walkTopDown().none { it.isFile })
        }
    }

    @Test fun `video keeps native cancellation when thrown or wrapped in a Result`() = runTest {
        for (wrapped in listOf(false, true)) {
            val ws = workspace()
            val generator = object : VideoGenerator {
                override fun isAvailable() = true
                override suspend fun generate(prompt: String, onProgress: (String) -> Unit): Result<ByteArray> {
                    val stop = CancellationException("stopped")
                    if (wrapped) return Result.failure(stop) else throw stop
                }
            }
            try {
                MediaGenerateVideoTool(ws, { generator }).execute(mapOf("prompt" to "cat video"))
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
            assertTrue(ws.root.walkTopDown().none { it.isFile })
        }
    }

    @Test fun `valid returned audio is saved byte for byte with artifact path`() = runTest {
        val ws = workspace()
        val generator = object : MusicGenerator {
            override fun isAvailable() = true
            override suspend fun generate(prompt: String, durationSec: Int) = Result.success(validGeneratedWav())
        }
        val result = MediaGenerateAudioTool(ws, { generator }).execute(mapOf("prompt" to "lofi song"))
        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("MEDIA_FILE:"))
        val file = ws.root.walkTopDown().single { it.isFile }
        assertArrayEquals(validGeneratedWav(), file.readBytes())
    }
}
