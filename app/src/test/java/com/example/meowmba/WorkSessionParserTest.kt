package com.example.meowmba

import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.work.ProjectKind
import com.example.meowmba.core.work.ProjectRequestParser
import com.example.meowmba.core.work.ToolRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkSessionParserTest {

    private fun n(s: String) = WakePhraseMatcher.normalize(s)

    @Test
    fun `start a project picks kind and name`() {
        val website = ProjectRequestParser.parse(n("let's start a website project"))!!
        assertEquals(ProjectKind.WEBSITE, website.kind)
        assertEquals("Website", website.name)

        val photos = ProjectRequestParser.parse(n("start a photo project called vacation"))!!
        assertEquals(ProjectKind.PHOTO, photos.kind)
        assertEquals("Vacation", photos.name)

        val code = ProjectRequestParser.parse(n("begin a codebase called Meow app"))!!
        assertEquals(ProjectKind.CODE, code.kind)
        assertEquals("Meow App", code.name)

        val generic = ProjectRequestParser.parse(n("hey meow start a project"))!!
        assertEquals(ProjectKind.OTHER, generic.kind)
        assertEquals("Project", generic.name)
    }

    @Test
    fun `file scaffold create a project is not a work session`() {
        assertNull(ProjectRequestParser.parse(n("create a project called Website")))
        assertNull(ProjectRequestParser.parse(n("what is a project")))
        assertNull(ProjectRequestParser.parse(n("open spotify")))
    }

    @Test
    fun `make a tool at 7am is a music player`() {
        val tool = ToolRequestParser.parse(n("make a tool automatic music player at 7 am"))!!
        assertEquals("play_music", tool.action)
        assertEquals("07:00", tool.timeOfDay)
        assertEquals("music", tool.launchTarget)
    }

    @Test
    fun `make an automatic music player at 7am without saying tool`() {
        val tool = ToolRequestParser.parse(n("make an automatic music player at 7am"))!!
        assertEquals("play_music", tool.action)
        assertEquals("07:00", tool.timeOfDay)
    }

    @Test
    fun `evening and named times`() {
        assertEquals("19:00", ToolRequestParser.parseTime("remind me at 7 in the evening"))
        assertEquals("00:00", ToolRequestParser.parseTime("at midnight"))
        assertEquals("12:00", ToolRequestParser.parseTime("at noon"))
        assertEquals("07:30", ToolRequestParser.parseTime("at 7:30 am"))
    }

    @Test
    fun `ordinary chat is not a tool`() {
        assertNull(ToolRequestParser.parse(n("open the toolbox")))
        assertNull(ToolRequestParser.parse(n("how are you")))
        assertNotNull(ToolRequestParser.parse(n("create a tool called stretch at 3pm")))
    }
}
