package com.example.meowmba.core.ring

import org.junit.Test
import org.junit.Assert.*

class SmartRingTest {
    @Test fun controlPacketsMatchDocumentedR08Mode() {
        assertArrayEquals(byteArrayOf(0x3b, 2, 0, 1, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x43), ringPacket(0))
        assertArrayEquals(byteArrayOf(0x3b, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x3e), ringPacket(1))
        assertArrayEquals(byteArrayOf(0x3b, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x41), ringPacket(2))
    }
    @Test fun allowlistRequiresExplicitPackages() {
        assertEquals(emptySet<String>(), ringAllowedPackages(" * , , com.*\n"))
        assertEquals(setOf("com.example.meowmba", "org.mozilla.firefox"), ringAllowedPackages("com.example.meowmba,\norg.mozilla.firefox com.example.meowmba"))
    }
    @Test fun controlsRequireConnectionUnpausedVerifiedGestureAndAllowedApp() {
        val ready = RingState(connected = true, paused = false, verifiedGestures = setOf("Next", "Double tap"))
        assertEquals(RingAction.SCROLL_DOWN, ringActionFor(ready, "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(paused = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(connected = false), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(testing = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Previous", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Next", "com.android.settings"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(allowlist = ""), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Next", null))
        assertEquals(RingAction.SCROLL_UP, ringActionFor(ready.copy(reverse = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.BACK, ringActionFor(ready.copy(reverse = true), "Double tap", "com.example.meowmba"))
    }
    @Test fun allAppsStillRequiresForegroundAndVerifiedInput() {
        val global = RingState(allApps = true, connected = true, paused = false, allowlist = "",
            verifiedGestures = setOf("Next", "Single tap"), mappings = ringScrollClickMappings())
        assertEquals(RingAction.SCROLL_DOWN, ringActionFor(global, "Next", "com.android.settings"))
        assertEquals(RingAction.CLICK, ringActionFor(global, "Single tap", "org.mozilla.firefox"))
        assertEquals(RingAction.NONE, ringActionFor(global, "Next", null))
        assertEquals(RingAction.NONE, ringActionFor(global, "Next", ""))
        assertEquals(RingAction.NONE, ringActionFor(global.copy(paused = true), "Next", "com.android.settings"))
        assertEquals(RingAction.NONE, ringActionFor(global.copy(testing = true), "Single tap", "org.mozilla.firefox"))
        assertEquals(RingAction.NONE, ringActionFor(global.copy(verifiedGestures = emptySet()), "Single tap", "org.mozilla.firefox"))
        assertEquals(RingAction.NONE, ringActionFor(global.copy(allApps = false), "Next", "com.android.settings"))
        assertEquals(RingAction.NONE, ringActionFor(global.copy(showTarget = false), "Single tap", "org.mozilla.firefox"))
    }
    @Test fun targetMovesInBothAxesAndStaysOnScreen() {
        val old = ringScrollClickMappings() + mapOf("1 tap + Next" to RingAction.POINTER_DOWN,
            "1 tap + Previous" to RingAction.POINTER_UP, "2 taps + Next" to RingAction.POINTER_RIGHT,
            "2 taps + Previous" to RingAction.POINTER_LEFT)
        assertEquals(ringScrollClickMappings(), ringMigrateCursorAxes(old))
        val custom = old + ("1 tap + Previous" to RingAction.BACK)
        assertEquals(custom, ringMigrateCursorAxes(custom))
        assertEquals(ringScrollClickMappings(), ringMigrateCursorAxes(ringScrollClickMappings()))
        val p = RingPoint()
        assertTrue(p.move(RingAction.POINTER_UP).y < p.y)
        assertTrue(p.move(RingAction.POINTER_DOWN).y > p.y)
        assertTrue(p.move(RingAction.POINTER_LEFT).x < p.x)
        assertTrue(p.move(RingAction.POINTER_RIGHT).x > p.x)
        var edge = p
        repeat(100) { edge = edge.move(RingAction.POINTER_LEFT).move(RingAction.POINTER_UP) }
        assertEquals(.03f, edge.x, .0001f); assertEquals(.03f, edge.y, .0001f)
        repeat(100) { edge = edge.move(RingAction.POINTER_RIGHT).move(RingAction.POINTER_DOWN) }
        assertEquals(.97f, edge.x, .0001f); assertEquals(.97f, edge.y, .0001f)
    }

}
