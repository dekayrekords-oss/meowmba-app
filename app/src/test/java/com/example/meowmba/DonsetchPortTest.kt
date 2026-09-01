package com.example.meowmba

import com.example.meowmba.core.agent.tools.KeylessWebSearch
import com.example.meowmba.core.agent.tools.WebFetchTool
import com.example.meowmba.core.agent.tools.WebHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonsetchPortTest {

    // ── Keyless multi-engine search ───────────────────────────────────────

    @Test
    fun `ddg lite results parse and decode redirect wrapper`() {
        val html = """
            <table>
            <tr class="result">
              <a rel="nofollow" class="result-link" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fwiki%2FOwls">Owls — Wikipedia</a>
              <td class="result-snippet">Owls are birds of prey.</td>
            </tr>
            <tr class="result">
              <a class="result-link" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fowl.example%2F">Owl Facts</a>
              <td class="result-snippet">All about owls.</td>
            </tr>
            <tr class="result">
              <a class="result-link" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fbirds.example%2Fhamlet">Birds</a>
              <td class="result-snippet">Birds generally.</td>
            </tr>
            </table>
        """.trimIndent()
        val hits = KeylessWebSearch.parse("ddg_lite", html)
        assertTrue(hits.size >= 3)
        assertEquals("https://example.com/wiki/Owls", KeylessWebSearch.realUrl(hits.first().url))
    }

    @Test
    fun `bing ck_a stub decodes back to the real url`() {
        // base64url of https://example.com/owls
        assertEquals(
            "https://example.com/owls",
            KeylessWebSearch.realUrl(
                "https://www.bing.com/ck/a?u=a1aHR0cHM6Ly9leGFtcGxlLmNvbS9vd2xz"
            )
        )
    }

    @Test
    fun `under three hits counts as a failed engine`() {
        val html = """
            <table>
            <tr class="result"><a class="result-link" href="/l/?uddg=https%3A%2F%2Fa.example%2F">A</a></tr>
            <tr class="result"><a class="result-link" href="/l/?uddg=https%3A%2F%2Fb.example%2F">B</a></tr>
            </table>
        """.trimIndent()
        // Only 2 hits < minHits(3) -> engine treated as failed (empty).
        assertTrue(KeylessWebSearch.parse("ddg_lite", html).isEmpty())
    }

    @Test
    fun `fusion collapses duplicate urls across engines`() {
        val a = WebHit("Owls", "https://example.com/owls", "s1", "ddg_lite")
        val b = WebHit("Owls Wiki", "https://example.com/owls", "s2", "bing")
        val c = WebHit("Birds", "https://birds.example/", "s3", "brave")
        val fused = KeylessWebSearch.fuse(listOf(a, b, c))
        assertEquals(2, fused.size)
        assertEquals("s1", fused.first().snippet) // first engine's snippet kept
    }

    // ── Page fetch + extraction ───────────────────────────────────────────

    @Test
    fun `extraction strips scripts and returns readable text`() {
        val tool = WebFetchTool()
        val html = """
            <html><head><title>Owl Guide</title></head><body>
            <nav>Home About</nav>
            <script>var x = 1;</script>
            <h1>Owls</h1>
            <p>Owls are <b>nocturnal</b> birds.</p><p>They hunt at night.</p>
            <footer>Copyright</footer>
            </body></html>
        """.trimIndent()
        val out = tool.extract(html)
        assertEquals("Owl Guide", out.title)
        assertTrue(out.body.contains("nocturnal"))
        assertFalse(out.body.contains("var x"))
        assertFalse(out.body.contains("Copyright"))
    }

    @Test
    fun `plain page extracts its readable body`() {
        val tool = WebFetchTool()
        val out = tool.extract("<html><head><title>T</title></head><body><p>hello world</p></body></html>")
        assertEquals("hello world", out.body)
    }
}
