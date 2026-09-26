package com.example.meowmba.core.setup

import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import com.example.meowmba.MainActivity
import com.example.meowmba.core.security.SecureStorageManager
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WirelessAvatarMonitorTest {
    private lateinit var app: Application
    private lateinit var monitor: WirelessAvatarMonitor
    private lateinit var storage: org.mockito.MockedConstruction<SecureStorageManager>
    @Before fun setup() {
        storage = mockConstruction(SecureStorageManager::class.java) { mock, _ ->
            `when`(mock.getBooleanPreference("onboarding_done", false)).thenReturn(true)
        }
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences(WirelessAvatarMonitor.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(WirelessAvatarMonitor.ENABLED, true).commit()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        monitor = WirelessAvatarMonitor(app)
        monitor.install()
    }
    @After fun teardown() { storage.close() }
    private fun power(plugged: Int) {
        app.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, plugged))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
    }
    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
    @Test fun backgroundWithoutOverlayPostsOnlySilentTapFallback() {
        power(4)
        assertEquals(1, notifications().size)
        assertNotNull(notifications().single().contentIntent)
        assertNull(shadowOf(app).nextStartedActivity)
        power(4)
        assertEquals(1, notifications().size)
        power(0)
        assertTrue(notifications().isEmpty())
    }
    @Test fun lockedPhoneWaitsForUnlockAndCancelsWhenUnplugged() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        power(4)
        assertTrue(notifications().isEmpty())
        power(0)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertTrue(notifications().isEmpty())
    }
    @Test fun foregroundReceivesOneNavigationAndNoNotification() {
        val activity = mock(MainActivity::class.java)
        monitor.onActivityResumed(activity)
        power(4)
        power(4)
        verify(activity, times(1)).showWirelessAvatar()
        assertTrue(notifications().isEmpty())
    }
    @Test fun usbDoesNotOpenAvatarOrNotify() {
        power(2)
        assertTrue(notifications().isEmpty())
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun lockedCoilDropoutStillShowsOnceAfterPowerReturnsAndUserUnlocks() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        power(4)
        power(0)
        power(4)
        assertTrue(notifications().isEmpty())
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertEquals(1, notifications().size)
    }
    @Test fun unknownPowerReadingCannotDeliverPendingLockedRequest() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        power(4)
        power(-1)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        assertTrue(notifications().isEmpty())
        power(4)
        assertEquals(1, notifications().size)
    }
}
