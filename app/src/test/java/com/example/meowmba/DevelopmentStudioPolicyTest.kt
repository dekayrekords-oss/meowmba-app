package com.example.meowmba

import com.example.meowmba.core.computer.DevelopmentStudioPolicy
import com.example.meowmba.core.computer.DevelopmentApkReceipt
import org.junit.Assert.*
import org.junit.Test

class DevelopmentStudioPolicyTest {
    @Test fun apkReceiptsAreStrictAndBounded() {
        val prefix = "MEOW_APK ${"a".repeat(48)} ${"b".repeat(64)} "
        assertEquals(1234L, DevelopmentApkReceipt.parse(prefix + "1234")?.bytes)
        listOf("0", "-1", "536870913", "99999999999", "12 other", "http://evil/").forEach {
            assertNull(DevelopmentApkReceipt.parse(prefix + it))
        }
        assertNull(DevelopmentApkReceipt.parse("MEOW_APK bad bad 100"))
        assertEquals(prefix + "42", DevelopmentApkReceipt.latest("hello\n${prefix}42\nDone")?.line)
        assertNull(DevelopmentApkReceipt.latest("Someone said $prefix" + "42"))
    }
    @Test fun onlyFixedPreviewOriginAllowed() {
        assertTrue(DevelopmentStudioPolicy.previewAllowed("http://127.0.0.1:8765/index.html?v=2"))
        listOf("https://example.com", "http://127.0.0.1:4096/", "http://localhost:8765/",
            "file:///etc/passwd", "javascript:alert(1)", "http://user@127.0.0.1:8765/",
            "http://127.0.0.1.example.com:8765/", "http://[::1]:8765/", "invalid")
            .forEach { assertFalse(it, DevelopmentStudioPolicy.previewAllowed(it)) }
    }
    @Test fun scrollbackIsBoundedAndRemovesTerminalControls() {
        assertEquals("hello world\n", DevelopmentStudioPolicy.appendOutput("hello ", "\u001b[31mworld\u001b[0m\n"))
        assertEquals(48_000, DevelopmentStudioPolicy.appendOutput("x".repeat(48_000), "y".repeat(90_000)).length)
        assertFalse(DevelopmentStudioPolicy.appendOutput("", "a\u0007b\u0003").any { it < ' ' })
    }
}
