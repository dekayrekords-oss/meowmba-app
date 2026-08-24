package com.example.meowmba

import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerAppType
import com.example.meowmba.ui.companion.DrawerApps
import com.example.meowmba.ui.companion.DrawerCatalog
import com.example.meowmba.ui.companion.DrawerSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerCatalogTest {

    private val spotify = DrawerApp("Spotify", "com.spotify", "spotify music", 2_000, 80_000_000, DrawerAppType.MUSIC)
    private val gmail = DrawerApp("Gmail", "com.google.android.gm", "gmail mail work", 1_000, 40_000_000, DrawerAppType.WORK)
    private val chess = DrawerApp("Chess", "com.chess", "chess game games", 3_000, 20_000_000, DrawerAppType.GAME)
    private val apps = listOf(spotify, gmail, chess)

    @Test
    fun `sort by name is a to z`() {
        val sorted = DrawerCatalog.sortApps(apps, DrawerSort.NAME).map { it.label }
        assertEquals(listOf("Chess", "Gmail", "Spotify"), sorted)
    }

    @Test
    fun `sort by date newest first`() {
        val sorted = DrawerCatalog.sortApps(apps, DrawerSort.DATE_ADDED).map { it.label }
        assertEquals(listOf("Chess", "Spotify", "Gmail"), sorted)
    }

    @Test
    fun `sort by size largest first`() {
        val sorted = DrawerCatalog.sortApps(apps, DrawerSort.SIZE).map { it.label }
        assertEquals(listOf("Spotify", "Gmail", "Chess"), sorted)
    }

    @Test
    fun `type filter keeps only games`() {
        val visible = DrawerCatalog.visible(apps, "", DrawerSort.NAME, DrawerAppType.GAME)
        assertEquals(listOf("Chess"), visible.map { it.label })
    }

    @Test
    fun `search still works with a type filter`() {
        val visible = DrawerCatalog.visible(apps, "mail", DrawerSort.NAME, DrawerAppType.WORK)
        assertEquals(listOf("Gmail"), visible.map { it.label })
    }

    @Test
    fun `folders round trip and add remove`() {
        val created = DrawerCatalog.createFolder(emptyList(), "Games", "com.chess", id = "f1")
        assertEquals(1, created.size)
        assertEquals("Games", created[0].name)
        assertEquals(listOf("com.chess"), created[0].packages)

        val added = DrawerCatalog.addToFolder(created, "f1", "com.spotify")
        assertEquals(listOf("com.chess", "com.spotify"), added[0].packages)

        val json = DrawerCatalog.encodeFolders(added)
        val parsed = DrawerCatalog.parseFolders(json)
        assertEquals(added, parsed)

        val removed = DrawerCatalog.removeFromFolder(parsed, "f1", "com.chess")
        assertEquals(listOf("com.spotify"), removed[0].packages)
        assertTrue(DrawerCatalog.deleteFolder(removed, "f1").isEmpty())
    }

    @Test
    fun `merge keeps cached sizes for known packages`() {
        val cached = listOf(spotify)
        val fast = listOf(
            spotify.copy(installedAtMs = 0, sizeBytes = 0),
            gmail.copy(installedAtMs = 0, sizeBytes = 0),
        )
        val merged = DrawerApps.merge(cached, fast)
        assertEquals(80_000_000L, merged.first { it.packageName == "com.spotify" }.sizeBytes)
        assertEquals(2_000L, merged.first { it.packageName == "com.spotify" }.installedAtMs)
        assertEquals(0L, merged.first { it.packageName == "com.google.android.gm" }.sizeBytes)
    }

    @Test
    fun `apps list survives a disk round trip`() {
        val json = DrawerCatalog.encodeApps(apps)
        val parsed = DrawerCatalog.parseApps(json)
        assertEquals(apps, parsed)
        assertTrue(DrawerCatalog.parseApps(null).isEmpty())
        assertTrue(DrawerCatalog.parseApps("").isEmpty())
    }

    @Test
    fun `size and date labels`() {
        assertEquals("76 MB", DrawerCatalog.formatSize(80_000_000))
        assertTrue(DrawerCatalog.formatAdded(1_724_000_000_000L).isNotBlank())
        assertEquals("", DrawerCatalog.formatSize(0))
    }
}
