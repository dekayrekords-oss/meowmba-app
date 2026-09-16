package com.example.meowmba

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.robot.LooiBleLink
import com.example.meowmba.core.robot.RobotManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RobotDisconnectTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `disconnect retires connect work that has not started yet`() {
        val manager = RobotManager { error("Retired work must not create a Bluetooth link") }
        val token = manager.connectionToken()
        manager.useSimulated()
        assertFalse(manager.connectLooiBle(context, "00:11:22:33:44:55", token))
        assertFalse(manager.hasLiveBody)
        assertTrue(manager.status.value.startsWith("Disconnected"))
    }

    @Test fun `disconnect closes pending candidate and late successful connect cannot attach`() {
        val link = mock(LooiBleLink::class.java)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        `when`(link.connected).thenReturn(true)
        `when`(link.connectBlocking("00:11:22:33:44:55")).thenAnswer {
            started.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            true // Model an uncancellable platform callback completing late.
        }
        val manager = RobotManager { link }
        val result = AtomicReference<Boolean>()
        val worker = thread { result.set(manager.connectLooiBle(context, "00:11:22:33:44:55")) }
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS))
            manager.useSimulated()
            verify(link, atLeastOnce()).close()
            release.countDown()
            worker.join(3000)
            assertFalse(worker.isAlive)
            assertEquals(false, result.get())
            assertFalse(manager.hasLiveBody)
            assertTrue(manager.status.value.startsWith("Disconnected"))
        } finally { release.countDown(); worker.join(3000) }
    }

    @Test fun `a failed selected base cannot silently connect a different device`() {
        val link = mock(LooiBleLink::class.java)
        val manager = RobotManager { link }
        assertFalse(manager.connectLooiBle(context, "00:11:22:33:44:55"))
        verify(link).connectBlocking("00:11:22:33:44:55")
        verify(link, never()).connectAuto()
        assertFalse(manager.hasLiveBody)
    }

    @Test fun `unverified legacy transports never install a motor controller`() {
        val manager = RobotManager()
        assertFalse(manager.connectBluetooth("00:11:22:33:44:55"))
        assertFalse(manager.connectLooi("127.0.0.1", 9100))
        assertFalse(manager.hasLiveBody)
        assertTrue(manager.status.value.contains("no verified motor protocol"))
    }

    @Test fun `delayed initialization status cannot detach a now ready base`() {
        var callback: ((LooiBleLink.LinkState, String) -> Unit)? = null
        val link = mock(LooiBleLink::class.java) { invocation ->
            if (invocation.method.name == "setOnStateChange") {
                @Suppress("UNCHECKED_CAST")
                callback = invocation.arguments[0] as ((LooiBleLink.LinkState, String) -> Unit)?
                null
            } else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        `when`(link.connected).thenReturn(true)
        `when`(link.state).thenReturn(LooiBleLink.LinkState.READY)
        `when`(link.connectBlocking("00:11:22:33:44:55")).thenReturn(true)
        val manager = RobotManager { link }
        assertTrue(manager.connectLooiBle(context, "00:11:22:33:44:55"))
        assertNull(manager.bodyMotionBlockedReason)
        callback!!(LooiBleLink.LinkState.INITIALIZING, "old initialization callback")
        assertNull(manager.bodyMotionBlockedReason)
        assertTrue(manager.status.value.startsWith("Robot base connected"))
        manager.useSimulated()
        callback!!(LooiBleLink.LinkState.READY, "old ready callback")
        assertFalse(manager.hasLiveBody)
        assertTrue(manager.status.value.startsWith("Disconnected"))
    }
}
