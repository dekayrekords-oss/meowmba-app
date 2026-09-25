package com.example.meowmba.flow

import com.example.meowmba.core.flow.sources.FlowNotificationHub as Hub
import com.example.meowmba.core.flow.sources.AppIntentions
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

class NotificationTargetTest {
    @Before fun start() { Hub.reconcileActive(emptySet()) }
    @After fun finish() { Hub.reconcileActive(emptySet()) }
    private fun posted(key: String, at: Long) = Hub.Posted("messages", "Messages", "msg", "Jim", key, at, text = "A message")
    @Test fun oldRemovalCannotDeleteReplacementTarget() {
        Hub.post(posted("conversation", 200))
        Hub.post(Hub.Removed("messages", "Jim", "conversation", 100))
        assertEquals(200, Hub.activePosted().single().at)
        Hub.post(Hub.Removed("messages", "Jim", "conversation", 200))
        assertTrue(Hub.activePosted().isEmpty())
    }
    @Test fun reconnectRemovesExpiredTargetsAndKeepsOtherConversations() {
        Hub.post(posted("jim-personal", 100)); Hub.post(posted("jim-band", 200))
        Hub.reconcileActive(setOf("jim-band"))
        assertEquals("jim-band", Hub.activePosted().single().notificationKey)
    }
    @Test fun originalKeysCannotCollideUsingJavaStringHashCollision() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(AppIntentions.fromMessage("messages", "Messages", "Jim", 1, "Aa")!!.id,
            AppIntentions.fromMessage("messages", "Messages", "Jim", 1, "BB")!!.id)
    }
    @Test fun retainedStateAndTextStayBounded() {
        repeat(300) { Hub.post(posted("$it", it.toLong()).copy(text = "x".repeat(1000))) }
        assertEquals(256, Hub.activePosted().size)
        assertTrue(Hub.activePosted().all { it.text.length == 500 })
    }
}
