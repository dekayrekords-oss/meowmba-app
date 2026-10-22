package com.example.meowmba

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerApps
import com.example.meowmba.ui.companion.DrawerCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawerAppsCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun successfulEmptyRefreshCannotResurrectRemovedAppsFromDisk() {
        val context = mock(Context::class.java)
        val manager = mock(PackageManager::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.packageName).thenReturn("test.drawer")
        `when`(context.filesDir).thenReturn(folder.root)
        `when`(context.packageManager).thenReturn(manager)
        `when`(manager.queryIntentActivities(any(Intent::class.java), eq(0))).thenReturn(emptyList())
        DrawerApps.warm(context)
        val disk = File(folder.root, "drawer_apps.json")
        val removed = DrawerApp("Removed app", "test.removed", "removed app")
        disk.writeText(DrawerCatalog.encodeApps(listOf(removed)))
        assertEquals(listOf(removed), DrawerApps.snapshot(context))
        DrawerApps.warm(context)
        assertEquals("[]", disk.readText())
        assertTrue(DrawerApps.snapshot(context).isEmpty())
    }
}
