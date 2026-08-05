package com.example.meowmba

import com.example.meowmba.core.agent.BrowserAgentLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live browser agent: action parsing and JS escaping. */
class BrowserAgentLoopTest {

    @Test
    fun `parses a clean json action`() {
        val a = BrowserAgentLoop.parseAction("""{"action":"click","selector":"button=Save"}""")
        assertNotNull(a)
        assertEquals("click", a!!.type)
        assertEquals("button=Save", a.selector)
    }

    @Test
    fun `parses an action buried in prose`() {
        val a = BrowserAgentLoop.parseAction(
            "Sure! I'll rewrite the page.\n{\"action\":\"inject\",\"html\":\"<h1>Hi</h1>\"}\nDone."
        )
        assertNotNull(a)
        assertEquals("inject", a!!.type)
        assertEquals("<h1>Hi</h1>", a.html)
    }

    @Test
    fun `done action carries the summary in text`() {
        val a = BrowserAgentLoop.parseAction("""{"action":"done","summary":"Page built."}""")
        assertNotNull(a)
        assertEquals("done", a!!.type)
        assertEquals("Page built.", a.text)
    }

    @Test
    fun `braces inside strings do not end the object early`() {
        val a = BrowserAgentLoop.parseAction(
            """{"action":"js","code":"el.style.background = 'url({x})'"}"""
        )
        assertNotNull(a)
        assertEquals("js", a!!.type)
        assertTrue(a.code!!.contains("{x}"))
    }

    @Test
    fun `prose without json returns null`() {
        assertNull(BrowserAgentLoop.parseAction("I am still thinking about it."))
    }

    @Test
    fun `malformed json returns null not crash`() {
        assertNull(BrowserAgentLoop.parseAction("""{"action":"click","selector":"""))
    }

    @Test
    fun `js string literal escapes quotes and backslashes`() {
        val lit = BrowserAgentLoop.jsStringLiteral("<b>it's \\ fine</b>")
        assertEquals("'<b>it\\'s \\\\ fine</b>'", lit)
    }

    @Test
    fun `plan prompt carries goal and dom`() {
        val p = BrowserAgentLoop.planPrompt("make a page about cats", "<h1>hi</h1>", 3)
        assertTrue(p.contains("make a page about cats"))
        assertTrue(p.contains("<h1>hi</h1>"))
        assertTrue(p.contains("3"))
        assertTrue(p.contains("ONE JSON object"))
    }

    @Test
    fun `scroll parses pixels`() {
        val a = BrowserAgentLoop.parseAction("""{"action":"scroll","pixels":-600}""")
        assertEquals(-600, a!!.pixels)
    }
}
