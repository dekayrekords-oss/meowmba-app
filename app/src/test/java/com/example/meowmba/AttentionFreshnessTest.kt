package com.example.meowmba

import com.example.meowmba.core.vision.AttentionFreshness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionFreshnessTest {
    @Test
    fun `freshness expires without another camera sample`() {
        assertTrue(AttentionFreshness.isFresh(1_000L, 1_499L))
        assertFalse(AttentionFreshness.isFresh(1_000L, 1_500L))
        assertFalse(AttentionFreshness.isFresh(1_000L, 20_000L))
    }

    @Test
    fun `unknown and future observations are not fresh`() {
        assertFalse(AttentionFreshness.isFresh(-1L, 1_000L))
        assertFalse(AttentionFreshness.isFresh(2_000L, 1_000L))
        assertTrue(AttentionFreshness.isFresh(0L, 0L))
    }

    @Test
    fun `disable rejects a completing detector callback`() {
        val session = AttentionFreshness.Session()
        val token = session.start()
        assertTrue(session.accepts(token, 1_000L, 1_020L))
        session.stop()
        assertFalse(session.accepts(token, 1_000L, 1_030L))
    }

    @Test
    fun `re-enable cannot resurrect a previous session face`() {
        val session = AttentionFreshness.Session()
        val old = session.start()
        session.stop()
        val current = session.start()
        assertFalse(session.accepts(old, 1_000L, 1_050L))
        assertTrue(session.accepts(current, 1_040L, 1_050L))
    }

    @Test
    fun `lens or orientation rebind invalidates an in-flight frame`() {
        val session = AttentionFreshness.Session()
        val old = session.start()
        val current = session.invalidate()
        assertFalse(session.accepts(old, 1_000L, 1_030L))
        assertTrue(session.accepts(current, 1_020L, 1_030L))
    }

    @Test
    fun `slow detector completion cannot refresh old camera evidence`() {
        val session = AttentionFreshness.Session()
        val token = session.start()
        assertFalse(session.accepts(token, 1_000L, 1_500L))
        assertTrue(session.accepts(token, 1_490L, 1_510L))
    }
}
