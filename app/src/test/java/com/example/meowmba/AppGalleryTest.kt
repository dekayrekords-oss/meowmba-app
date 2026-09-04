package com.example.meowmba

import com.example.meowmba.core.plugins.AppGallery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the curated "apps the AI can work with" catalog — the Loona
 * Deskmate-style list. It must be explicit and curated, never a mirror of every
 * installed package: every entry needs a name, capability and an integration
 * state, and any API_KEY entry must be able to tell the user where to get the
 * key.
 */
class AppGalleryTest {

    @Test
    fun `catalog is curated - has entries across the expected categories`() {
        val all = AppGallery.all
        assertFalse("gallery must not be empty", all.isEmpty())
        val categories = all.map { it.category }.toSet()
        assertTrue("should cover Mail", categories.contains("Mail"))
        assertTrue("should cover Calendar", categories.contains("Calendar"))
        assertTrue("should cover Messaging", categories.contains("Messaging"))
        assertTrue("should cover Developer/Media", categories.containsAll(setOf("Media", "Developer")))
        assertTrue("should cover Smart Home/Streaming", categories.containsAll(setOf("Smart Home", "Streaming")))
    }

    @Test
    fun `every entry is well-formed`() {
        AppGallery.all.forEach { e ->
            assertTrue("id blank: ${e.name}", e.id.isNotBlank())
            assertTrue("name blank", e.name.isNotBlank())
            assertTrue("capability blank: ${e.name}", e.capability.isNotBlank())
            assertFalse("badge blank: ${e.name}", e.badge.isBlank())
            assertTrue("badge too long for a tile: ${e.badge}", e.badge.length <= 3)
        }
    }

    @Test
    fun `every key-requiring integration points to where the key comes from`() {
        AppGallery.all
            .filter { it.integration == AppGallery.Integration.API_KEY }
            .forEach { e ->
                assertNotNull("API_KEY entry ${e.name} needs a keyLink", e.keyLink)
                assertTrue("keyLink invalid: ${e.name}", (e.keyLink ?: "").startsWith("https://"))
                assertNotNull("API_KEY entry ${e.name} needs a storageKey", e.storageKey)
            }
    }

    @Test
    fun `built-in entries that map to an app package are connectable`() {
        AppGallery.all
            .filter { it.integration == AppGallery.Integration.BUILT_IN }
            .forEach { e ->
                // A built-in either has a package-backed driver (connectorId) or is
                // a device capability (Desktop/Browser) with no package.
                if (e.connectorId != null) assertTrue(e.connectorId!!.contains("."))
            }
        // Sanity: the anchors we promised on the tile.
        assertEquals(
            "https://api.slack.com/apps",
            AppGallery.all.first { it.id == "slack" }.keyLink,
        )
        assertEquals("Gmail", AppGallery.all.first { it.id == "gmail" }.name)
    }

    @Test
    fun `every built-in that names a package has a real android package id`() {
        // WhatsApp/Telegram/Discord and the smart-home set were added from the
        // user's actual installed apps — their package names must resolve.
        val byId = AppGallery.all.associateBy { it.id }
        assertEquals("com.whatsapp", byId["whatsapp"]!!.connectorId)
        assertEquals("org.telegram.messenger", byId["telegram"]!!.connectorId)
        assertEquals("com.discord", byId["discord"]!!.connectorId)
        assertEquals(
            "io.homeassistant.companion.android",
            byId["home_assistant"]!!.connectorId,
        )
        assertEquals("com.govee.home", byId["govee"]!!.connectorId)
        assertEquals("com.tuya.smart", byId["tuya"]!!.connectorId)
    }
}
