package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.InstalledApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spoken commands must survive the words people actually say around them.
 *
 * Two reported bugs live here: "open the clock" reported no matching app
 * because the article was never stripped, and "set an alarm" only worked when
 * spoken to the background wake service — in the app it fell through to the
 * language model, which is neither deterministic nor fast.
 */
class IntentContextTest {

    private val apps = listOf(
        InstalledApps.Entry("Clock", "com.google.android.deskclock"),
        InstalledApps.Entry("Spotify", "com.spotify.music"),
        InstalledApps.Entry("YouTube", "com.google.android.youtube"),
        InstalledApps.Entry("Play Store", "com.android.vending"),
        InstalledApps.Entry("Gmail", "com.google.android.gm"),
        InstalledApps.Entry("Settings", "com.android.settings"),
    )

    // ── app matching ────────────────────────────────────────────────────────

    @Test
    fun `the reported bug - open the clock finds Clock`() {
        assertEquals("Clock", InstalledApps.match(apps, "the clock")?.label)
    }

    @Test
    fun `articles and filler around an app name are ignored`() {
        for (spoken in listOf("clock", "the clock", "a clock", "my clock",
            "the clock app", "clock app", "the clock please", "my spotify app")) {
            assertNotNull("\"$spoken\" should match an app", InstalledApps.match(apps, spoken))
        }
        assertEquals("Spotify", InstalledApps.match(apps, "my spotify app")?.label)
    }

    @Test
    fun `an exact label still wins over a substring`() {
        assertEquals("Clock", InstalledApps.match(apps, "clock")?.label)
    }

    @Test
    fun `filler is stripped from the ends but never the middle`() {
        // "Play Store" must not be reduced to "Store" by treating "play" as filler.
        assertEquals("Play Store", InstalledApps.match(apps, "play store")?.label)
        assertEquals("Play Store", InstalledApps.match(apps, "the play store")?.label)
    }

    @Test
    fun `speech mangling that splits a name still matches`() {
        assertEquals("YouTube", InstalledApps.match(apps, "you tube")?.label)
        assertEquals("YouTube", InstalledApps.match(apps, "the you tube app")?.label)
    }

    @Test
    fun `a label mentioned inside a longer phrase is found`() {
        assertEquals("Clock", InstalledApps.match(apps, "set my alarm in the clock")?.label)
    }

    @Test
    fun `the longest matching label wins so a short one cannot steal it`() {
        // "play store" contains "play"; if a "Play" app existed it must not win.
        val withPlay = apps + InstalledApps.Entry("Play", "com.example.play")
        assertEquals("Play Store", InstalledApps.match(withPlay, "open the play store")?.label)
    }

    @Test
    fun `nonsense matches nothing rather than guessing`() {
        assertNull(InstalledApps.match(apps, "zzzzqqq"))
        assertNull(InstalledApps.match(apps, ""))
        assertNull(InstalledApps.match(apps, "the"))
    }

    // ── alarms ──────────────────────────────────────────────────────────────

    private fun alarm(text: String) = AgentRouter.route(text)

    @Test
    fun `set an alarm routes deterministically without the model`() {
        val intent = alarm("set an alarm for 7am")
        assertNotNull("alarm must route on the keyword path", intent)
        assertEquals("phone.set_alarm", intent!!.toolId)
        assertEquals("7", intent.args["hour"])
        assertEquals("0", intent.args["minutes"])
    }

    @Test
    fun `alarm phrasings all reach the same tool`() {
        for (spoken in listOf(
            "set an alarm for 7am", "set alarm for 7 am", "wake me up at 7am",
            "wake me at 7:00 am", "alarm for 7am",
        )) {
            val i = alarm(spoken)
            assertEquals("\"$spoken\" should set an alarm", "phone.set_alarm", i?.toolId)
            assertEquals("\"$spoken\" hour", "7", i?.args?.get("hour"))
        }
    }

    @Test
    fun `pm times convert to 24 hour`() {
        assertEquals("19", alarm("set an alarm for 7pm")?.args?.get("hour"))
        assertEquals("30", alarm("set an alarm for 7:30 pm")?.args?.get("minutes"))
        assertEquals("19", alarm("set an alarm for 7:30 pm")?.args?.get("hour"))
    }

    @Test
    fun `midnight and noon are understood`() {
        assertEquals("0", alarm("set an alarm for midnight")?.args?.get("hour"))
        assertEquals("12", alarm("set an alarm for noon")?.args?.get("hour"))
        assertEquals("0", alarm("set an alarm for 12am")?.args?.get("hour"))
    }

    @Test
    fun `spoken word times work`() {
        assertEquals("7", alarm("set an alarm for seven")?.args?.get("hour"))
        val halfPast = alarm("wake me up at half past seven")
        assertEquals("7", halfPast?.args?.get("hour"))
        assertEquals("30", halfPast?.args?.get("minutes"))
        val quarterTo = alarm("set an alarm for quarter to eight")
        assertEquals("7", quarterTo?.args?.get("hour"))
        assertEquals("45", quarterTo?.args?.get("minutes"))
    }

    @Test
    fun `24 hour input is passed through`() {
        val i = alarm("set an alarm for 19:05")
        assertEquals("19", i?.args?.get("hour"))
        assertEquals("5", i?.args?.get("minutes"))
    }

    @Test
    fun `an alarm with no time asks rather than inventing one`() {
        // Falling through to the model is correct here: guessing an hour is
        // worse than asking which one.
        assertNull(alarm("set an alarm"))
    }

    @Test
    fun `alarm beats the app launcher`() {
        // "set an alarm in the clock app" must SET one, not just open Clock.
        assertEquals("phone.set_alarm", alarm("set an alarm for 6am in the clock app")?.toolId)
    }

    // ── timers ──────────────────────────────────────────────────────────────

    @Test
    fun `timers parse to total seconds`() {
        assertEquals("600", alarm("set a timer for 10 minutes")?.args?.get("seconds"))
        assertEquals("90", alarm("set a timer for 90 seconds")?.args?.get("seconds"))
        assertEquals("3600", alarm("timer for 1 hour")?.args?.get("seconds"))
        assertEquals("330", alarm("set a timer for 5 minutes 30 seconds")?.args?.get("seconds"))
    }

    @Test
    fun `a timer with no duration falls through`() {
        assertNull(alarm("set a timer"))
    }

    // ── no regressions in neighbouring routes ───────────────────────────────

    @Test
    fun `open still routes to the app launcher`() {
        val i = alarm("open spotify")
        assertEquals("phone.launch_app", i?.toolId)
        assertEquals("spotify", i?.args?.get("name"))
    }

    @Test
    fun `conversation is not mistaken for a command`() {
        assertNull(alarm("open up about your feelings"))
        // "alarming" must not trip the alarm rule.
        assertNull(alarm("that is alarming"))
    }
}
