package com.example.meowmba.flow

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.flow.sources.UsageSource
import com.example.meowmba.flow.Fx.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageSourceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val usm get() = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private fun grant(mode: Int = AppOpsManager.MODE_ALLOWED) {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName, mode)
    }

    private fun event(pkg: String, type: Int, t: Long) {
        shadowOf(usm).addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent().setPackage(pkg).setTimeStamp(t).setEventType(type).build())
    }

    @Test fun noAccessMeansNoData() {
        grant(AppOpsManager.MODE_IGNORED)
        event("com.google.android.gm", UsageEvents.Event.ACTIVITY_RESUMED, at(9))
        val src = UsageSource(ctx) { setOf("com.google.android.gm") }
        assertTrue(src.foregroundEvidence(at(8), at(10)).isEmpty())
        assertNull(src.workContext(at(10)).minutesSinceWorkApp)
    }

    @Test fun foregroundAppsBecomeEvidence() {
        grant()
        event("com.google.android.gm", UsageEvents.Event.ACTIVITY_RESUMED, at(9))
        event("com.google.android.gm", UsageEvents.Event.ACTIVITY_RESUMED, at(9, 1))   // duplicate
        event("com.spotify.music", UsageEvents.Event.ACTIVITY_RESUMED, at(9, 5))
        val ev = UsageSource(ctx) { setOf("com.google.android.gm") }.foregroundEvidence(at(8), at(10))
        assertEquals(listOf("com.google.android.gm", "com.spotify.music"), ev.map { it.payload["package"] })
        assertEquals("true", ev.first().payload["work"])
    }

    @Test fun workContextFromLastWorkApp() {
        grant()
        event("com.google.android.apps.docs", UsageEvents.Event.ACTIVITY_RESUMED, at(17, 40))
        event("com.google.android.apps.docs", UsageEvents.Event.ACTIVITY_PAUSED, at(18, 20))
        event("com.netflix.mediaclient", UsageEvents.Event.ACTIVITY_RESUMED, at(18, 21))
        val w = UsageSource(ctx) { setOf("com.google.android.apps.docs") }.workContext(at(18, 40))
        assertEquals(20, w.minutesSinceWorkApp)
        assertTrue(!w.activelyWorking)
    }
}
