package com.example.meowmba

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.example.meowmba.core.agent.tools.PhoneCleanupTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.cleanup.PhoneCleanup
import kotlinx.coroutines.test.runTest
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
import org.robolectric.shadows.ShadowContentResolver

/** Exercise the Play boundary even when a previous install had broad media grants. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class PlayMediaAccessTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun playOnly() {
        assumeFalse(BuildConfig.PHONE_MEDIA_LIBRARY_ENABLED)
        ShadowContentResolver.registerProviderInternal("media", RejectMediaQueries())
    }
    @After fun clear() = PhoneCleanup.clearPending()

    @Test fun `old grants cannot reenable a Play whole library scan`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        assertTrue(PhoneCleanup.mediaPermissions().isEmpty())
        assertFalse(PhoneCleanup.hasMediaPermission(context))
        for (kind in listOf("photos", "videos", "music", "files", "documents")) {
            val plan = PhoneCleanup.scan(context, kind)
            assertTrue(plan.items.isEmpty())
            assertTrue(plan.notes.contains(PhoneCleanup.pickerGuidance))
            assertFalse(PhoneCleanup.format(plan).contains("Looked through"))
        }
    }

    @Test fun `every media action opens Files without requesting unavailable permissions`() = runTest {
        var permissionRequests = 0
        var fileViews = 0
        val tool = PhoneCleanupTool(context, { emptyList() }, { emptyList() }, {},
            { permissionRequests++ }, { fileViews++ })
        for (kind in listOf("photos", "videos", "music", "documents", "files")) {
            for (action in listOf("scan", "organize", "tidy")) {
                val result = tool.execute(mapOf("kind" to kind, "action" to action))
                assertTrue(result is ToolResult.Success)
                assertTrue((result as ToolResult.Success).output.contains(PhoneCleanup.pickerGuidance))
            }
        }
        assertEquals(15, fileViews)
        assertEquals(0, permissionRequests)
        assertNull(PhoneCleanup.pendingFresh())
    }

    class RejectMediaQueries : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
            throw AssertionError("Play must use selected URIs, not query a media collection: $uri")
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
