package com.example.meowmba

import android.Manifest
import android.app.Application
import com.example.meowmba.core.context.live.ContextSensing
import com.example.meowmba.core.flow.sources.AppIntentions
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** An upgrade must stop background delivery even if Android retained an older grant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class PlayLocationAccessTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun publicOnly() { assumeFalse(BuildConfig.BACKGROUND_LOCATION_ENABLED) }
    @After fun leaveForeground() { ContextSensing.setAppVisible(context, false) }

    @Test fun `old background grant cannot enable sampling after leaving Meow`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertFalse(ContextSensing.canLocateInBackground(context))
        ContextSensing.setAppVisible(context, true)
        assertTrue(ContextSensing.canReceiveLocation(context))
        ContextSensing.setAppVisible(context, false)
        assertFalse(ContextSensing.canReceiveLocation(context))
        ContextSensing.start(context) // Boot/grant re-registration cannot restore hidden sampling.
        assertFalse(ContextSensing.canReceiveLocation(context))
    }

    @Test fun `approximate access works only while Meow is visible`() {
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        ContextSensing.setAppVisible(context, true)
        assertTrue(ContextSensing.canReceiveLocation(context))
        ContextSensing.setAppVisible(context, false)
        assertFalse(ContextSensing.canReceiveLocation(context))
    }

    @Test fun `foreground visibility cannot replace location consent`() {
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ContextSensing.setAppVisible(context, true)
        assertFalse(ContextSensing.canReceiveLocation(context))
    }

    @Test fun `public setup asks only for while using access`() {
        val cards = AppIntentions.build(System.currentTimeMillis(), ZoneId.of("UTC"), emptyList(),
            emptyList(), emptyList(), AppIntentions.Access(true, true, true, locationAlways = false))
        val setup = cards.single { it.id == AppIntentions.SETUP_LOCATION }
        assertFalse(setup.text.contains("closed"))
        assertFalse(setup.source.contains("Allow all the time"))
        assertTrue(setup.source.contains("Only while using the app"))
    }
}
