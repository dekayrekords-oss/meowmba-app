package com.example.meowmba.core.agent.runtime

import org.junit.Assert.*
import org.junit.Test

class NeedleCpuPolicyTest {
    @Test fun olderArm64CannotLoadDotProductRuntime() {
        assertFalse(NeedleCpuPolicy.supported(0L))
        assertFalse(NeedleCpuPolicy.supported(NeedleCpuPolicy.DOT_PRODUCT.inv()))
        var loaded = false
        val error = runCatching {
            NeedleCpuPolicy.loadWhenSupported(0L) { loaded = true }
        }.exceptionOrNull()
        assertTrue(error is UnsupportedOperationException)
        assertFalse("Unsafe vendor library must never be loaded", loaded)
    }
    @Test fun dotProductCapabilityPermitsPinnedRuntime() {
        assertTrue(NeedleCpuPolicy.supported(NeedleCpuPolicy.DOT_PRODUCT))
        assertTrue(NeedleCpuPolicy.supported(NeedleCpuPolicy.DOT_PRODUCT or 3L))
        var loads = 0
        assertEquals("runtime", NeedleCpuPolicy.loadWhenSupported(NeedleCpuPolicy.DOT_PRODUCT) {
            loads++; "runtime"
        })
        assertEquals(1, loads)
    }
}
