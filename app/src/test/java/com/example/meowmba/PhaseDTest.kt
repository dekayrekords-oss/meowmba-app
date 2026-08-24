package com.example.meowmba

import com.example.meowmba.core.agent.tools.WebBrowserTool
import com.example.meowmba.core.browser.WebViewBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase D: agent-browser JS escaping + DOM helper contract. */
class PhaseDTest {

    @Test
    fun `jsEscape neutralizes quotes backslashes and newlines`() {
        assertEquals("it\\'s", WebBrowserTool.jsEscape("it's"))
        assertEquals("a\\nb", WebBrowserTool.jsEscape("a\nb"))
        assertEquals("c:\\\\dir", WebBrowserTool.jsEscape("c:\\dir"))
    }

    @Test
    fun `escaped selector cannot break out of the JS string`() {
        val malicious = "'); document.cookie; ('"
        val escaped = WebBrowserTool.jsEscape(malicious)
        // After escaping every quote is backslash-escaped, so the JS string
        // literal the tool builds cannot be closed early.
        assertFalse(escaped.replace("\\'", "").contains("'"))
        assertFalse(escaped.contains("\n"))
    }

    @Test
    fun `DOM helpers expose the agent surface on window __meow`() {
        val js = WebViewBridge.DOM_HELPERS
        assertTrue(js.contains("window.__meow"))
        listOf("find", "title", "links", "click", "type", "scroll", "inject", "readable", "scrape").forEach {
            assertTrue("missing helper: $it", js.contains("$it"))
        }
    }
}
