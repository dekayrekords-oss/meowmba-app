
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

    @Test fun subsequenceTokensFindBambuHandyWhenNoStrongerMatch() {
        assertTrue(DrawerCatalog.matchesSearch(bambu, "bamu Hand"))
        assertTrue(DrawerCatalog.matchesSearch(bambu, "bmhy"))
        assertTrue(DrawerCatalog.subsequenceLetters("bambu handy", "bamu"))
        assertFalse(DrawerCatalog.matchesSearch(bambu, "zzzz"))
    }

    @Test fun visibleUsesSubsequenceWhenAlone() {
        val apps = listOf(bambu, DrawerApp("Clock", "com.clock", "clock", type = DrawerAppType.OTHER))
        assertEquals(listOf("Bambu Handy"), DrawerCatalog.visible(apps, "bamu hand", DrawerSort.NAME, null).map { it.label })
    }
}

class DrawerSearchRankingTest {
    private val messages = DrawerApp(
        "Messages",
        "com.samsung.android.messaging",
        "messages message sms mms texting samsung",
        type = DrawerAppType.SOCIAL,
    )
    private val googleMessages = DrawerApp(
        "Messages",
        "com.google.android.apps.messaging",
        "messages message sms mms texting google",
        type = DrawerAppType.SOCIAL,
    )
    private val settings = DrawerApp(
        "Settings",
        "com.android.settings",
        "settings setting system",
        type = DrawerAppType.OTHER,
    )
    private val messenger = DrawerApp(
        "Messenger",
        "com.facebook.orca",
        "messenger social chat messaging messages",
        type = DrawerAppType.SOCIAL,
    )
    private val samsungNotes = DrawerApp(
        "Samsung Notes",
        "com.samsung.android.app.notes",
        "samsung notes note",
        type = DrawerAppType.WORK,
    )
    private val chrome = DrawerApp(
        "Chrome",
        "com.android.chrome",
        "chrome browser google",
        type = DrawerAppType.OTHER,
    )
    private val gallery = DrawerApp(
        "Gallery",
        "com.sec.android.gallery3d",
        "gallery photos photo images",
        type = DrawerAppType.PHOTO,
    )
    private val noise = DrawerApp(
        "Microsoft Something",
        "com.microsoft.noise",
        "microsoft office suite apps manager",
        type = DrawerAppType.WORK,
    )

    @Test fun messagesQueryPrefersRealMessagesAppsNotLetterSoup() {
        val apps = listOf(settings, samsungNotes, noise, messenger, messages, googleMessages)
        val visible = DrawerCatalog.visible(apps, "messages", DrawerSort.NAME, null)
        assertTrue("expected Messages in results: $visible", visible.any { it.packageName.contains("messaging") })
        assertEquals("Messages", visible.first().label)
        // Weak subsequence-only noise must not flood results when strong hits exist.
        assertFalse(visible.any { it.packageName == "com.microsoft.noise" })
        assertFalse(visible.any { it.packageName == "com.android.settings" })
        assertTrue(visible.size <= 4)
    }

    @Test fun frequencyBoostsUsedMessagesApp() {
        val apps = listOf(messages, googleMessages)
        val visible = DrawerCatalog.visible(
            apps,
            "messages",
            DrawerSort.NAME,
            null,
            launchCounts = mapOf("com.google.android.apps.messaging" to 12),
        )
        assertEquals("com.google.android.apps.messaging", visible.first().packageName)
    }

    @Test fun chromeAndPhotosAliases() {
        val apps = listOf(chrome, gallery, settings, noise)
        assertEquals(listOf("Chrome"), DrawerCatalog.visible(apps, "chrome", DrawerSort.NAME, null).map { it.label })
        val photos = DrawerCatalog.visible(apps, "photos", DrawerSort.NAME, null)
        assertEquals("Gallery", photos.first().label)
    }

    @Test fun exactPrefixBeatsSubsequence() {
        val apps = listOf(
            DrawerApp("Mail", "com.mail", "mail email", type = DrawerAppType.WORK),
            DrawerApp("Marketplace", "com.market", "marketplace shop", type = DrawerAppType.OTHER),
        )
        val visible = DrawerCatalog.visible(apps, "mail", DrawerSort.NAME, null)
        assertEquals("Mail", visible.first().label)
        // "Marketplace" contains "mail" as letters in order / contains — may appear via CONTAINS on token?
        // "marketplace".contains("mail") is true — CONTAINS is allowed. That's OK.
        assertTrue(visible.first().label.equals("Mail", ignoreCase = true))
    }
}
