package com.example.meowmba.flow

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.flow.Channel
import com.example.meowmba.core.flow.FlowInterrupt
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InterruptLedger
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.delivery.AndroidFlowNotifier
import com.example.meowmba.core.flow.delivery.FlowNotifier
import com.example.meowmba.core.flow.delivery.InterruptDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class InterruptDeliveryTest {
    private class FakeNotifier : FlowNotifier {
        val posted = mutableListOf<FlowInterrupt>(); var cancels = 0
        override fun post(interrupt: FlowInterrupt) { posted += interrupt }
        override fun cancel() { cancels++ }
    }

    private fun interrupt(surface: Surface, settings: FlowSettings = FlowSettings()) =
        FlowPolicy(Fx.zone).decide(Fx.drivingPastStore(surface), listOf(Fx.bulb), listOf(Fx.a("bulb", Urgency.WORTH_IT, 0.9, 0.92)),
            settings, InterruptLedger()).snapshot.interrupt!!

    @Test fun onScreenSpeaksButDoesNotNotify() {
        val spoken = mutableListOf<String>(); val n = FakeNotifier()
        val d = InterruptDelivery({ spoken += it; true }, n).deliver(interrupt(Surface.FLOW_ON_SCREEN))!!
        assertTrue(d.spoke); assertFalse(d.notified)
        assertTrue(spoken.single().contains("usually carries"))
        assertTrue(n.posted.isEmpty())
    }

    @Test fun otherAppNotifiesAndSpeaks() {
        val n = FakeNotifier()
        val d = InterruptDelivery({ true }, n).deliver(interrupt(Surface.OTHER_APP))!!
        assertTrue(d.notified && d.spoke)
        assertEquals(1, n.posted.size)
    }

    @Test fun speakerGateIsRespected() {
        val d = InterruptDelivery({ false }, FakeNotifier()).deliver(interrupt(Surface.FLOW_ON_SCREEN))!!
        assertFalse(d.spoke)
    }

    @Test fun deliversOnceAndCancelsWhenGone() {
        val n = FakeNotifier(); val delivery = InterruptDelivery({ true }, n)
        val i = interrupt(Surface.OTHER_APP)
        delivery.deliver(i)
        assertNull(delivery.deliver(i))
        assertEquals(1, n.posted.size)
        delivery.deliver(null)
        assertEquals(1, n.cancels)
    }

    @Test fun showOnlyDoesNothingExtra() {
        val n = FakeNotifier()
        val i = interrupt(Surface.FLOW_ON_SCREEN, FlowSettings(voice = false))
        assertEquals(Channel.SHOW_ONLY, i.channel)
        val d = InterruptDelivery({ error("must not speak") }, n).deliver(i)!!
        assertFalse(d.spoke || d.notified)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidFlowNotifierTest {
    @Test fun postsHeadsUpWithThreeActionsOnItsOwnChannel() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val i = FlowPolicy(Fx.zone).decide(Fx.drivingPastStore(Surface.OTHER_APP), listOf(Fx.bulb),
            listOf(Fx.a("bulb", Urgency.WORTH_IT, 0.9, 0.92)), FlowSettings(), InterruptLedger()).snapshot.interrupt!!
        AndroidFlowNotifier(ctx).post(i)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).allNotifications.single()
        assertEquals(AndroidFlowNotifier.CHANNEL_ID, posted.channelId)
        assertEquals(3, posted.actions.size)
        assertEquals("Later", posted.actions[1].title.toString())
        assertEquals("Comment", posted.actions[2].title.toString())
        val commentIntent = shadowOf(posted.actions[2].actionIntent).savedIntent
        assertEquals(i.item.intentionId, commentIntent.getStringExtra(AndroidFlowNotifier.EXTRA_COMMENT_FLOW))
        assertEquals("comment", commentIntent.data?.host)
        assertTrue(shadowOf(posted.actions[2].actionIntent).isActivity)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(AndroidFlowNotifier.CHANNEL_ID).importance)
    }
}
