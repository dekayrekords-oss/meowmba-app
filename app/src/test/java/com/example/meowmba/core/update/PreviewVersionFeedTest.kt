package com.example.meowmba.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewVersionFeedTest {

    @Test
    fun `parse minimal feed`() {
        val feed = PreviewVersionFeed.parse(
            """{"versionCode":131,"versionName":"1.3.81","apk":"preview.apk","sha256":"abc","size":100}""",
        )
        assertEquals(131, feed.versionCode)
        assertEquals("1.3.81", feed.versionName)
        assertEquals("preview.apk", feed.apk)
        assertEquals("abc", feed.sha256)
        assertEquals(100L, feed.size)
        assertNull(feed.notes)
        assertNull(feed.apkAlt)
    }

    @Test
    fun `parse optional fields and reject bad versionCode`() {
        val feed = PreviewVersionFeed.parse(
            """
            {
              "versionCode": 132,
              "versionName": "1.3.82",
              "apk": "meowmba-preview-vc132.apk",
              "sha256": "deadbeef",
              "size": 426000000,
              "notes": "Update check",
              "publishedAt": "2026-09-17T22:00:00-07:00",
              "apkAlt": "meowmba-preview-vc132-alt.apk",
              "sha256Alt": "cafebabe",
              "sizeAlt": 426000001
            }
            """.trimIndent(),
        )
        assertEquals("Update check", feed.notes)
        assertEquals("meowmba-preview-vc132-alt.apk", feed.apkAlt)
        assertEquals(426000001L, feed.sizeAlt)
        assertTrue(feed.versionCode > 131)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse requires apk`() {
        PreviewVersionFeed.parse("""{"versionCode":1,"versionName":"x","apk":" ","sha256":"","size":0}""")
    }
}
