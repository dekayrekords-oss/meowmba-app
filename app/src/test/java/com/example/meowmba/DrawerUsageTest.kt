
package com.example.meowmba

import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerAppType
import com.example.meowmba.ui.companion.DrawerCatalog
import com.example.meowmba.ui.companion.DrawerSort
import com.example.meowmba.ui.companion.DrawerUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerUsageTest {
    @Test fun recordLaunchOrdersMostUsed() {
        var snap = DrawerUsage.Snapshot()
        snap = DrawerUsage.recordLaunch(snap, "a")
        snap = DrawerUsage.recordLaunch(snap, "b")
        snap = DrawerUsage.recordLaunch(snap, "b")
        snap = DrawerUsage.recordLaunch(snap, "c")
        snap = DrawerUsage.recordLaunch(snap, "c")
        snap = DrawerUsage.recordLaunch(snap, "c")
        assertEquals(listOf("c", "b", "a"), DrawerUsage.frequentPackages(snap, setOf("a", "b", "c")))
    }

    @Test fun neverFrequentExcludesAndAllowRestores() {
        var snap = DrawerUsage.recordLaunch(DrawerUsage.Snapshot(), "x")
        snap = DrawerUsage.setNeverFrequent(snap, "x", true)
        assertTrue(DrawerUsage.frequentPackages(snap, setOf("x")).isEmpty())
        snap = DrawerUsage.allowFrequent(snap, "x")
        assertEquals(listOf("x"), DrawerUsage.frequentPackages(snap, setOf("x")))
    }

    @Test fun hideFromFrequentUntilRelaunch() {
        var snap = DrawerUsage.recordLaunch(DrawerUsage.Snapshot(), "y")
        snap = DrawerUsage.hideFromFrequent(snap, "y")
        assertTrue(DrawerUsage.frequentPackages(snap, setOf("y")).isEmpty())
        snap = DrawerUsage.recordLaunch(snap, "y")
        assertEquals(listOf("y"), DrawerUsage.frequentPackages(snap, setOf("y")))
    }

    @Test fun roundTripJson() {
        var snap = DrawerUsage.recordLaunch(DrawerUsage.Snapshot(), "pkg")
        snap = DrawerUsage.setNeverFrequent(snap, "other", true)
        val parsed = DrawerUsage.parse(DrawerUsage.encode(snap))
        assertEquals(snap.launches, parsed.launches)
        assertEquals(snap.neverFrequent, parsed.neverFrequent)
    }
}

class DrawerSubsequenceSearchTest {
    private val bambu = DrawerApp("Bambu Handy", "com.bambu.handy", "bambu handy printer", type = DrawerAppType.OTHER)

    @Test fun subsequenceTokensFindBambuHandy() {
        assertTrue(DrawerCatalog.matchesSearch(bambu, "bamu Hand"))
        assertTrue(DrawerCatalog.matchesSearch(bambu, "bmhy"))
        assertTrue(DrawerCatalog.subsequenceLetters("bambu handy", "bamu"))
        assertFalse(DrawerCatalog.matchesSearch(bambu, "zzzz"))
    }

    @Test fun visibleUsesSubsequence() {
        val apps = listOf(bambu, DrawerApp("Clock", "com.clock", "clock", type = DrawerAppType.OTHER))
        assertEquals(listOf("Bambu Handy"), DrawerCatalog.visible(apps, "bamu hand", DrawerSort.NAME, null).map { it.label })
    }
}
