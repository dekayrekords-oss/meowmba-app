package com.example.meowmba.flow

import com.example.meowmba.core.flow.EveningLook
import com.example.meowmba.core.flow.FlowKeyValue
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSettingsStore
import com.example.meowmba.core.flow.FlowThemes
import com.example.meowmba.core.flow.SituationBuilder
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.WorkContext
import com.example.meowmba.core.flow.WorkDay
import com.example.meowmba.core.flow.WorkState
import com.example.meowmba.core.flow.WorkStateDetector
import com.example.meowmba.flow.Fx.at
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlowThemesTest {
    @Test fun everyThemeHasReadableTextAndAccentText() {
        for (t in FlowThemes.all) for (night in listOf(false, true)) {
            val p = FlowThemes.palette(t.key, night)
            val tag = "${t.key}/${if (night) "night" else "day"}"
            assertTrue("$tag ink", FlowThemes.contrast(p.ink, p.paper) >= 7.0)
            assertTrue("$tag ink2", FlowThemes.contrast(p.ink2, p.paper) >= 4.5)
            assertTrue("$tag accent on paper", FlowThemes.contrast(p.accent, p.paper) >= 4.5)
            assertTrue("$tag onAccent", FlowThemes.contrast(p.onAccent, p.accent) >= 4.5)
        }
    }

    @Test fun paperNightAccentGetsDarkText() {
        assertEquals(FlowThemes.DARK_ON_ACCENT, FlowThemes.palette("paper", night = true).onAccent)
        assertEquals(FlowThemes.LIGHT_ON_ACCENT, FlowThemes.palette("paper", night = false).onAccent)
    }

    @Test fun userAccentOverridesAndPicksReadableText() {
        val yellow = 0xFFFFE27A.toInt()
        val p = FlowThemes.palette("linen", night = false, accentOverride = yellow)
        assertEquals(yellow, p.accent)
        assertEquals(FlowThemes.DARK_ON_ACCENT, p.onAccent)
    }

    @Test fun eveningLookFollowsWorkOnAuto() {
        assertTrue(FlowThemes.isNight(EveningLook.AUTO, WorkState.OFF))
        assertFalse(FlowThemes.isNight(EveningLook.AUTO, WorkState.WRAPPING))
        assertTrue(FlowThemes.isNight(EveningLook.NIGHT, WorkState.WORKING))
        assertFalse(FlowThemes.isNight(EveningLook.DAY, WorkState.OFF))
    }
}

class FlowSettingsStoreTest {
    private class MapKv : FlowKeyValue {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putAll(values: Map<String, String?>) = values.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
    }

    @Test fun defaultsMatchOwnerDecisions() {
        val s = FlowSettingsStore(MapKv()).load()
        assertEquals(FlowSettings(), s)
        assertTrue(s.voice && s.notifyOtherApps && s.detectWorkEnd)
        assertEquals(3, s.maxPerDay); assertEquals(60, s.minGapMinutes); assertEquals(Urgency.WORTH_IT, s.minUrgency)
        assertFalse(s.allowJev)
    }

    @Test fun roundTrips() {
        val kv = MapKv(); val store = FlowSettingsStore(kv)
        val s = FlowSettings(voice = false, maxPerDay = 5, minGapMinutes = 30, minUrgency = Urgency.URGENT,
            theme = "stone", accent = 0xFF2F7A3E.toInt(), eveningLook = EveningLook.NIGHT, allowJev = true)
        store.save(s)
        assertEquals(s, store.load())
        assertEquals("#2F7A3E", kv.map[FlowSettingsStore.K_ACCENT])
    }

    @Test fun garbageIsNormalized() {
        val kv = MapKv().apply {
            map[FlowSettingsStore.K_MAX] = "99"; map[FlowSettingsStore.K_GAP] = "50"
            map[FlowSettingsStore.K_URGENCY] = "NOT_NOW"; map[FlowSettingsStore.K_THEME] = "neon"
            map[FlowSettingsStore.K_ACCENT] = "#zzz"; map[FlowSettingsStore.K_VOICE] = "maybe"
        }
        val s = FlowSettingsStore(kv).load()
        assertEquals(10, s.maxPerDay); assertEquals(60, s.minGapMinutes); assertEquals(Urgency.USEFUL, s.minUrgency)
        assertEquals("paper", s.theme); assertNull(s.accent); assertTrue(s.voice)
    }
}

class SituationBuilderTest {
    private val builder = SituationBuilder(Fx.zone)

    @Test fun storeStateHasWhatLayaNeeds() {
        val json = Json.parseToJsonElement(builder.stateFor(Fx.drivingPastStore(), Fx.bulb)).jsonObject
        assertEquals("\"Tue 16:58\"", json["now"].toString())
        assertEquals("\"driving\"", json["activity"].toString())
        assertTrue(json.containsKey("route") && json.containsKey("place_ahead") && json.containsKey("intention"))
        assertEquals("\"likely\"", json["place_ahead"]!!.jsonObject["carries_item"].toString())
    }

    @Test fun secretsAreRedacted() {
        val i = Fx.bill.copy(
            text = "Pay card 4111 1111 1111 1111 for bob@example.com",
            facts = mapOf("account_number" to "12345678", "item" to "7443 bulb", "body" to "full email text"),
        )
        val text = builder.stateFor(Fx.drivingPastStore(), i)
        assertFalse(text.contains("4111")); assertFalse(text.contains("bob@example.com"))
        assertFalse(text.contains("12345678")); assertFalse(text.contains("full email text"))
        assertTrue(text.contains("7443 bulb"))
    }

    @Test fun staysUnderBudgetByDroppingLowValueFieldsFirst() {
        val tight = SituationBuilder(Fx.zone, maxChars = 420)
        val long = Fx.bulb.copy(source = "said " + "very long remark ".repeat(20), facts = mapOf("item" to "7443"))
        val text = tight.stateFor(Fx.drivingPastStore(), long)
        assertTrue(text.length <= 420)
        assertTrue(text.contains("Fix the rear brake light"))
        assertFalse(text.contains("very long remark"))
    }
}

class WorkStateDetectorTest {
    private val d = WorkStateDetector(Fx.zone)

    @Test fun workingLateOverridesSchedule() {
        val e = d.estimate(at(18, 5), WorkContext(minutesSinceWorkApp = 1, activelyWorking = true, atWorkPlace = true))
        assertEquals(WorkState.WORKING, e.state)
        assertTrue(e.reason.contains("past your usual finish"))
    }

    @Test fun severalStoppedSignalsMeanOff() {
        val e = d.estimate(at(18, 40), WorkContext(minutesSinceWorkApp = 12, computerClosed = true, atWorkPlace = false))
        assertEquals(WorkState.OFF, e.state)
        assertTrue(e.probability > 0.9)
    }

    @Test fun oneSignalAfterHoursIsWrapping() {
        val e = d.estimate(at(17, 20), WorkContext(minutesSinceWorkApp = 6, computerClosed = true, atWorkPlace = true, onWorkNetwork = true))
        assertEquals(WorkState.WRAPPING, e.state)
    }

    @Test fun detectionOffUsesScheduleOnly() {
        val e = d.estimate(at(18, 5), WorkContext(activelyWorking = true), detectWorkEnd = false)
        assertEquals(WorkState.OFF, e.state)
    }

    @Test fun learnsUsualHoursFromHistory() {
        val start = LocalDate.of(2026, 8, 25) // a Tuesday
        (0 until 6).forEach { w -> d.recordDay(WorkDay(start.plusWeeks(w.toLong()), 8 * 60 + 30, 18 * 60)) }
        assertEquals(510 to 1080, d.usualHours(java.time.DayOfWeek.TUESDAY))
        assertNull(d.usualHours(java.time.DayOfWeek.SATURDAY))
        assertEquals(WorkState.WORKING, d.estimate(at(17, 30), WorkContext()).state)
    }
}

class FlowKindCoverageTest {
    @Test fun urgencyOrderingIsStable() {
        assertTrue(Urgency.URGENT.atLeast(Urgency.WORTH_IT))
        assertFalse(Urgency.USEFUL.atLeast(Urgency.WORTH_IT))
        assertEquals(8, FlowKind.entries.size)
    }
}
