package com.example.meowmba

import com.example.meowmba.core.security.CleartextPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    @Test
    fun `https is always allowed`() {
        assertTrue(CleartextPolicy.isAllowed("https://openrouter.ai/api/v1"))
        assertTrue(CleartextPolicy.isAllowed("https://192.168.1.10:8642"))
    }

    @Test
    fun `LAN and loopback http is allowed`() {
        assertTrue(CleartextPolicy.isAllowed("http://192.168.1.10:8642"))
        assertTrue(CleartextPolicy.isAllowed("http://10.0.0.5:9100"))
        assertTrue(CleartextPolicy.isAllowed("http://172.16.0.2"))
        assertTrue(CleartextPolicy.isAllowed("http://127.0.0.1:11434"))
        assertTrue(CleartextPolicy.isAllowed("http://localhost:8642"))
        assertTrue(CleartextPolicy.isAllowed("http://meow.local:11438"))
    }

    @Test
    fun `public http is refused`() {
        assertFalse(CleartextPolicy.isAllowed("http://openrouter.ai/api/v1"))
        assertFalse(CleartextPolicy.isAllowed("http://example.com"))
        assertTrue(CleartextPolicy.denyReason("http://evil.example")!!.contains("HTTPS"))
    }

    @Test
    fun `blank is fine so a field can be cleared`() {
        assertNull(CleartextPolicy.denyReason(""))
        assertNull(CleartextPolicy.denyReason("   "))
    }

    @Test
    fun `browser blocks file intent and javascript urls`() {
        assertFalse(CleartextPolicy.isSafeBrowseUrl("file:///data/data/x"))
        assertFalse(CleartextPolicy.isSafeBrowseUrl("javascript:alert(1)"))
        assertFalse(CleartextPolicy.isSafeBrowseUrl("intent://scan/#Intent;end"))
        assertTrue(CleartextPolicy.isSafeBrowseUrl("https://example.com"))
        assertTrue(CleartextPolicy.isSafeBrowseUrl("https://meow.local/workshop"))
        assertTrue(CleartextPolicy.isSafeBrowseUrl("http://192.168.1.10/page"))
        assertFalse(CleartextPolicy.isSafeBrowseUrl("http://example.com"))
    }
}
