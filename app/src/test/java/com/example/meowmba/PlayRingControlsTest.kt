package com.example.meowmba

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import com.example.meowmba.core.ring.*
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Validate user-operated ring input against the actual Play configuration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class PlayRingControlsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun playOnly() = assumeFalse(BuildConfig.PHONE_AUTOMATION_ENABLED)

    @Test fun ringServiceIsPresentWithoutAutonomousPhoneService() {
        assertTrue(BuildConfig.RING_CONTROLS_ENABLED)
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, RingAccessibilityService::class.java), PackageManager.GET_META_DATA)
        // Android binds through the signature-only accessibility permission.
        assertTrue(service.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.permission)
        val configuration = service.metaData.getInt("android.accessibilityservice")
        assertTrue(configuration != 0)
        context.resources.getXml(configuration).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG &&
                xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) xml.next()
            assertEquals("accessibility-service", xml.name)
            assertFalse(xml.getAttributeBooleanValue("http://schemas.android.com/apk/res/android",
                "isAccessibilityTool", true))
        }
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getServiceInfo(ComponentName(context.packageName,
                "com.example.meowmba.core.access.MeowAccessibilityService"), 0)
        }
    }

    @Test fun scrollAndClickRequireConsentAndVerifiedPhysicalGestures() {
        val state = RingState(controlsConsent = true, connected = true, paused = false,
            mappings = ringScrollClickMappings(), verifiedGestures = setOf("Next", "Single tap"),
            allowlist = context.packageName)
        assertEquals(RingAction.SCROLL_DOWN, ringActionFor(state, "Next", context.packageName))
        assertEquals(RingAction.CLICK, ringActionFor(state, "Single tap", context.packageName))
        assertEquals(RingAction.NONE, ringActionFor(state.copy(controlsConsent = false), "Next", context.packageName))
        assertEquals(RingAction.NONE, ringActionFor(state.copy(verifiedGestures = emptySet()), "Single tap", context.packageName))
        assertEquals(RingAction.NONE, ringActionFor(state, "Next", "other.app"))
    }
}
