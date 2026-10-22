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
    fun `create and start project both create saved work sessions`() {
        assertEquals("Website", ProjectRequestParser.parse(n("create a project called Website"))!!.name)
        assertEquals(ProjectKind.WEBSITE, ProjectRequestParser.parse(n("create a new website project"))!!.kind)
        assertNull(ProjectRequestParser.parse(n("create a report for this project")))
        assertNull(ProjectRequestParser.parse(n("scaffold a project folder")))
        assertNull(ProjectRequestParser.parse(n("what is a project")))
        assertNull(ProjectRequestParser.parse(n("open spotify")))
    }

    @Test
    fun `the apps spoken confirmation cannot recursively create projects`() {
        assertNull(ProjectRequestParser.parse(n("Started Website. Its plan, activity and files are together in Projects. I'm drafting a plan for you to review.")))
        assertNull(ProjectRequestParser.parse(n("Started Project 2")))
        assertNull(ProjectRequestParser.parse(n("Project setup couldn't finish. Any existing project record and files were kept.")))
    }

    @Test
    fun `project bug reports negations and progress discussion are not creation requests`() {
        listOf(
            "it keeps starting projects until i close the app",
            "do not start a project", "don't start a project", "stop making projects",
            "the project won't start", "restart the app to fix projects",
            "i already started a project", "beginning the project was difficult",
            "tell me how to start a project", "when should we begin our project",
            "you said start a project", "start working on this project",
        ).forEach { assertNull(it, ProjectRequestParser.parse(n(it))) }
    }

    @Test
    fun `polite direct new project requests still work`() {
        listOf("let's start a project", "let us begin a codebase", "i want to create a photo project",
            "could you please make a new website project", "new code project", "help me plan a project")
            .forEach { assertNotNull(it, ProjectRequestParser.parse(n(it))) }
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
