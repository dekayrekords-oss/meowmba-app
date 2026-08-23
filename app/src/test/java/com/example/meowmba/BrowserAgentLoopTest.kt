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
    fun `a markdown html fence is treated as inject`() {
        val a = BrowserAgentLoop.parseAction(
            "Here you go:\n```html\n<!doctype html><html><body><h1>Bakery</h1></body></html>\n```"
        )
        assertNotNull(a)
        assertEquals("inject", a!!.type)
        assertTrue(a.html!!.contains("Bakery"))
    }

    @Test
    fun `a raw html document is treated as inject`() {
        val a = BrowserAgentLoop.parseAction(
            "<!doctype html><html><body><p>Hi</p></body></html>"
        )
        assertEquals("inject", a!!.type)
        assertTrue(a.html!!.contains("<p>Hi</p>"))
    }

    @Test
    fun `scaffold is a dark page named after the goal`() {
        val html = BrowserAgentLoop.scaffoldHtml("create a website for the bakery")
        assertTrue(html.contains("<!doctype html>"))
        assertTrue(html.contains("#0d1117"))
        assertTrue(html.contains("Bakery"))
        assertEquals("Bakery", BrowserAgentLoop.topicFromGoal("make a web site for the bakery"))
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

    @Test
    fun `scrape action parses a save name`() {
        val a = BrowserAgentLoop.parseAction("""{"action":"scrape","name":"news"}""")
        assertEquals("scrape", a!!.type)
        assertEquals("news", a.name)
        val prompt = BrowserAgentLoop.planPrompt("scrape this page", "<p>hi</p>", 0)
        assertTrue(prompt.contains("scrape"))
    }

    @Test
    fun `planner uses the text brain when vision is missing`() {
        val json = """{"action":"inject","html":"<h1>Hi</h1>"}"""
        assertEquals(json, BrowserAgentLoop.pickPlanner(json, null))
        assertEquals("vision", BrowserAgentLoop.pickPlanner(null, "vision"))
        assertNull(BrowserAgentLoop.pickPlanner(null, null))
        assertTrue(BrowserAgentLoop.isDirectScrape("scrape this page"))
        assertTrue(BrowserAgentLoop.isDirectScrape("extract the text"))
        assertTrue(!BrowserAgentLoop.isDirectScrape("make a page about cats"))
    }

    @Test
    fun `build goals refuse done before a look`() {
        assertTrue(BrowserAgentLoop.isBuildGoal("make a page about cats"))
        assertTrue(BrowserAgentLoop.shouldRefuseDone(isBuild = true, looksTaken = 0))
        assertTrue(!BrowserAgentLoop.shouldRefuseDone(isBuild = true, looksTaken = 1))
        val look = BrowserAgentLoop.lookPrompt("make a page about cats", "<p>hi</p>", 1)
        assertTrue(look.contains("SCREENSHOT"))
        assertTrue(look.contains("white"))
        val plan = BrowserAgentLoop.planPrompt("make a page about cats", "", 0)
        assertTrue(plan.contains("dark canvas"))
        assertTrue(plan.contains("never a white document"))
    }
}
