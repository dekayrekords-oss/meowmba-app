package com.example.meowmba

import android.graphics.Bitmap
import com.example.meowmba.core.media.SDCppImageGenerator
import com.example.meowmba.core.media.SDCppNative
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

/**
 * SDCppImageGenerator.isAvailable() does a real File(modelPath).isFile check
 * before ever touching [SDCppNative], so these tests need a path that
 * genuinely exists on disk — a literal nonexistent path would make every
 * test short-circuit before exercising the fake native layer at all.
 */
private fun fakeModelPath(): String =
    File.createTempFile("fake-model", ".gguf").apply { deleteOnExit() }.absolutePath

private class FakeSDCppNative(
    private val createResult: Long = 1L,
    private val generateResult: ByteArray? = ByteArray(4 * 4 * 4) { 0x7F },
    private val dims: IntArray = intArrayOf(4, 4, 4)
) : SDCppNative {
    var freed = false
    override fun createContext(modelPath: String, nThreads: Int) = createResult
    override fun generate(
        ctxPtr: Long, prompt: String, width: Int, height: Int,
        steps: Int, seed: Long, outDims: IntArray
    ): ByteArray? {
        dims.copyInto(outDims)
        return generateResult
    }
    override fun freeContext(ctxPtr: Long) { freed = true }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class SDCppImageGeneratorTest {

    @Test
    fun `generate returns a bitmap built from native bytes`() = runTest {
        val fake = FakeSDCppNative()
        val generator = SDCppImageGenerator(
            modelPath = fakeModelPath(),
            native = fake
        )

        val result = generator.generate("a cat")

        assertTrue(result.isSuccess)
        assertTrue(fake.freed)
    }

    @Test
    fun `null context (model load failure) is a clean failure, not a crash`() = runTest {
        val fake = FakeSDCppNative(createResult = 0L)
        val generator = SDCppImageGenerator(modelPath = fakeModelPath(), native = fake)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
    }

    @Test
    fun `null generate result is a clean failure, not a crash`() = runTest {
        val fake = FakeSDCppNative(generateResult = null)
        val generator = SDCppImageGenerator(modelPath = fakeModelPath(), native = fake)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
        assertTrue(fake.freed)  // context must still be released even on a failed generate
    }

    @Test
    fun `a native Throwable is caught, not propagated`() = runTest {
        val throwing = object : SDCppNative {
            override fun createContext(modelPath: String, nThreads: Int) = 1L
            override fun generate(
                ctxPtr: Long, prompt: String, width: Int, height: Int,
                steps: Int, seed: Long, outDims: IntArray
            ): ByteArray? = throw UnsatisfiedLinkError("simulated native crash")
            override fun freeContext(ctxPtr: Long) {}
        }
        val generator = SDCppImageGenerator(modelPath = fakeModelPath(), native = throwing)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
    }
}
