package com.example.meowmba

import com.example.meowmba.core.media.*
import com.example.meowmba.core.security.SecureStorageManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ImageServiceSettingsTest {
    private val storage = mock(SecureStorageManager::class.java)
    private val local = mock(ImageGenerator::class.java)

    @Test fun upgradesKeepExistingRoutingUntilUserChoosesAProvider() {
        assertEquals(ImageService.LEGACY, ImageServiceSettings.selected(storage))
        val plan = ImageServiceSettings.plan(storage, { local }, "", false) {}
        assertEquals(2, plan.size)
        assertSame(local, plan[0]())
        assertNull(plan[1]())
    }

    @Test fun localOnlyNeverIncludesACloudFallbackEvenWithServerConfigured() {
        `when`(storage.getPreference(ImageServiceSettings.PROVIDER)).thenReturn("LOCAL")
        val plan = ImageServiceSettings.plan(storage, { local }, "https://example.com", true) {}
        assertEquals(1, plan.size)
        assertSame(local, plan[0]())
    }

    @Test fun hordePlanIsExclusiveAndSnapshotsConsentBeforeSettingsChange() {
        `when`(storage.getPreference(ImageServiceSettings.PROVIDER)).thenReturn("HORDE")
        `when`(storage.getBooleanPreference(ImageServiceSettings.HORDE_CONSENT, false)).thenReturn(true)
        val plan = ImageServiceSettings.plan(storage, { error("Local must not load") }, "https://example.com", false) {}
        `when`(storage.getPreference(ImageServiceSettings.PROVIDER)).thenReturn("CLOUDFLARE")
        `when`(storage.getBooleanPreference(ImageServiceSettings.HORDE_CONSENT, false)).thenReturn(false)
        assertEquals(1, plan.size)
        val generator = plan.single()()
        assertTrue(generator is HordeImageGenerator)
        assertTrue(generator!!.isAvailable())
    }

    @Test fun cloudflareCannotFallBackToHordeOrLocalWhenCredentialsMissing() {
        `when`(storage.getPreference(ImageServiceSettings.PROVIDER)).thenReturn("CLOUDFLARE")
        val plan = ImageServiceSettings.plan(storage, { error("Local must not load") }, "https://example.com", false) {}
        assertEquals(1, plan.size)
        val generator = plan.single()()
        assertTrue(generator is CloudflareImageGenerator)
        assertFalse(generator!!.isAvailable())
    }
}
