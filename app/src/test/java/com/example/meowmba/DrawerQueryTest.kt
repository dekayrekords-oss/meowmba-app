package com.example.meowmba

import com.example.meowmba.ui.companion.DrawerQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerQueryTest {

    @Test
    fun `bare app name is find-app`() {
        assertEquals(DrawerQuery.Kind.FIND_APP, DrawerQuery.classify("spotify"))
        assertEquals(DrawerQuery.Kind.FIND_APP, DrawerQuery.classify("gmail"))
    }

    @Test
    fun `questions go to meow`() {
        assertEquals(DrawerQuery.Kind.ANSWER, DrawerQuery.classify("what is a capacitor"))
        assertEquals(DrawerQuery.Kind.ANSWER, DrawerQuery.classify("why is the sky blue"))
        assertEquals(DrawerQuery.Kind.ANSWER, DrawerQuery.classify("how do I install ollama"))
    }

    @Test
    fun `phone tasks are tasks`() {
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("set a timer for 5 minutes"))
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("open gmail"))
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("turn on the flashlight"))
        assertEquals(DrawerQuery.Kind.PHONE_TASK, DrawerQuery.classify("play sublime"))
    }

    @Test
    fun `explicit google is web`() {
        assertEquals(DrawerQuery.Kind.WEB_SEARCH, DrawerQuery.classify("google cat facts"))
        assertEquals(DrawerQuery.Kind.WEB_SEARCH, DrawerQuery.classify("search for best tacos"))
        assertEquals("cat facts", DrawerQuery.webQuery("google cat facts"))
    }

    @Test
    fun `unique app match launches`() {
        val action = DrawerQuery.resolve("spotify", matchCount = 1, singleLabel = "Spotify")
        assertEquals(DrawerQuery.Action.Launch("Spotify"), action)
    }

    @Test
    fun `several app matches stay filtered`() {
        assertEquals(
            DrawerQuery.Action.Stay,
            DrawerQuery.resolve("spot", matchCount = 3, singleLabel = null),
        )
    }

    @Test
    fun `no app match searches google`() {
        val action = DrawerQuery.resolve("best tacos in austin", matchCount = 0, singleLabel = null)
        assertTrue(action is DrawerQuery.Action.Web)
        assertEquals("best tacos in austin", (action as DrawerQuery.Action.Web).query)
    }

    @Test
    fun `question resolve asks even if an app name is in the text`() {
        assertEquals(
            DrawerQuery.Action.Ask,
            DrawerQuery.resolve("what is spotify", matchCount = 1, singleLabel = "Spotify"),
        )
    }
}
