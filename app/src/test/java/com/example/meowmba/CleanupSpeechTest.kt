package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.cleanup.CleanupSpeech
import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerAppType
import com.example.meowmba.ui.companion.DrawerCatalog
import com.example.meowmba.ui.companion.DrawerQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupSpeechTest {

    @Test
    fun `clean up my phone is a full scan`() {
        val parsed = CleanupSpeech.parse("clean up my phone")!!
        assertEquals("all", parsed.kind)
        assertEquals("scan", parsed.action)
    }

    @Test
    fun `organize photos and unused apps are specific`() {
        assertEquals("photos", CleanupSpeech.parse("organize my photos")!!.kind)
        assertEquals("organize", CleanupSpeech.parse("organize my photos")!!.action)
        assertEquals("apps", CleanupSpeech.parse("organize my apps")!!.kind)
        assertEquals("videos", CleanupSpeech.parse("clean up videos")!!.kind)
        assertEquals("music", CleanupSpeech.parse("tidy my music")!!.kind)
        assertEquals("documents", CleanupSpeech.parse("clean up downloads")!!.kind)
        assertEquals("messages", CleanupSpeech.parse("organize my messages")!!.kind)
    }

    @Test
    fun `clean them is a confirm`() {
        assertTrue(CleanupSpeech.isConfirm("clean them"))
        assertTrue(CleanupSpeech.isConfirm("go ahead"))
        assertFalse(CleanupSpeech.isConfirm("yes"))
        assertFalse(CleanupSpeech.isConfirm("what time is it"))
    }

    @Test
    fun `router sends cleanup to Paws`() {
        val intent = AgentRouter.route("clean up my phone")!!
        assertEquals("phone", intent.agentId)
        assertEquals("phone.cleanup", intent.toolId)
        assertEquals("all", intent.args["kind"])
        assertEquals("scan", intent.args["action"])
    }

    @Test
    fun `drawer search treats cleanup as a phone task`() {
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("clean up my photos"))
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("organize my apps"))
    }

    @Test
    fun `type folders get two-or-more unfiled apps`() {
        val apps = listOf(
            DrawerApp("Chess", "com.chess", "chess", type = DrawerAppType.GAME),
            DrawerApp("Genshin", "com.genshin", "game", type = DrawerAppType.GAME),
            DrawerApp("Gmail", "com.google.android.gm", "mail", type = DrawerAppType.WORK),
            DrawerApp("Spotify", "com.spotify", "music", type = DrawerAppType.MUSIC),
        )
        val (folders, added) = DrawerCatalog.organizeByType(emptyList(), apps) { "f$it" }
        assertEquals(2, added)
        assertEquals(1, folders.size)
        assertEquals("Games", folders[0].name)
        assertTrue(folders[0].packages.containsAll(listOf("com.chess", "com.genshin")))
        assertNotNull(folders)
    }
}
