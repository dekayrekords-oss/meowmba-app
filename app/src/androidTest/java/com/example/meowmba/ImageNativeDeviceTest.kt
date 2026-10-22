package com.example.meowmba

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.media.SDCppJniNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercise real packaged JNI symbols without allocating a multi-GB model. */
@RunWith(AndroidJUnit4::class)
class ImageNativeDeviceTest {
    @Test
    fun allImageEntryPointsResolve() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = File(context.cacheDir, "jni-smoke-missing-${System.nanoTime()}.gguf")
        assertFalse(missing.exists())
        assertEquals(0L, SDCppJniNative.createContext(missing.absolutePath, 1))
        assertNull(SDCppJniNative.generate(0L, "test", 8, 8, 1, 0L, IntArray(3)))
        SDCppJniNative.freeContext(0L)
    }
}
