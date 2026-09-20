package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.runtime.NativeCpuCapabilities
import com.example.meowmba.core.agent.runtime.NeedleCpuPolicy
import com.example.meowmba.core.agent.runtime.defaultNeedleNative
import com.example.meowmba.core.media.SDCppJniNative
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the packaged native loader/exports without downloading a model or running tools. */
class NativeCompatibility151DeviceTest {
    @Test fun baselineProbeAndImageRuntimeLoadOnDevice() {
        val caps = NativeCpuCapabilities.arm64Hwcap()
        assertTrue("ARM64 kernel must report CPU capabilities", caps != 0L)
        if (NeedleCpuPolicy.supported(caps)) {
            assertNotNull(defaultNeedleNative())
        } else {
            assertTrue(runCatching { defaultNeedleNative() }.exceptionOrNull() is UnsupportedOperationException)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = File(context.cacheDir, "native-smoke-no-model-${System.nanoTime()}.gguf")
        assertFalse(missing.exists())
        assertEquals("Image runtime must report a missing model without crashing", 0L,
            SDCppJniNative.createContext(missing.absolutePath, 1))
    }
}
