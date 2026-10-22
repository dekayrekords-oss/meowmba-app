package com.example.meowmba.flow

import com.example.meowmba.core.flow.IntentionCapture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Found recording the Android demo (2026-09-27): "I need to text Sam back tonight" became "Reply to Sam back tonight". */
class IntentionCaptureReplyTest {
    private fun text(s: String) = runBlocking { IntentionCapture(null).capture(s, 1_790_000_000_000L)?.text }

    @Test fun replyDropsTheBack() {
        assertEquals("Reply to Sam tonight", text("I need to text Sam back tonight"))
        assertEquals("Reply to Priya", text("I have to email Priya back"))
        assertEquals("Reply to Jordan about Saturday", text("I need to reply to Jordan about Saturday"))
    }
}
