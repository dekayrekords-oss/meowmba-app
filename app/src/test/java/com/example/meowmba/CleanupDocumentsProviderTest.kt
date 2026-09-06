package com.example.meowmba

import android.provider.DocumentsContract.Document
import com.example.meowmba.core.cleanup.CleanupDocumentsProvider
import com.example.meowmba.core.cleanup.PhoneCleanup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CleanupDocumentsProviderTest {
    @After fun clear() = PhoneCleanup.clearPending()

    @Test fun `review contains every suggested file but not apps`() {
        val files = (1..45).map { PhoneCleanup.Item("photos", "Photo $it", 100, "large photo", "content://media/external/images/media/$it") }
        PhoneCleanup.remember(PhoneCleanup.Plan("all", files + PhoneCleanup.Item("apps", "App", 0, "unused", packageName = "example.app"), emptyList()))
        CleanupDocumentsProvider().queryChildDocuments("suggestions", null, sortOrder = null).use {
            assertEquals(45, it.count)
            it.moveToFirst()
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow(Document.COLUMN_FLAGS)))
        }
    }

    @Test(expected = FileNotFoundException::class)
    fun `arbitrary content URI is never exposed`() {
        CleanupDocumentsProvider().queryDocument("content://private/secret", null)
    }

    @Test(expected = FileNotFoundException::class)
    fun `file manager cannot write through review provider`() {
        CleanupDocumentsProvider().openDocument("content://media/1", "rw", null)
    }
}
