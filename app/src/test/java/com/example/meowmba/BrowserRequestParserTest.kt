package com.example.meowmba

import com.example.meowmba.core.work.BrowserJob
import com.example.meowmba.core.work.BrowserRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserRequestParserTest {

    private fun p(s: String) = BrowserRequestParser.parse(s)

    @Test
    fun `make a page is a build job`() {
        val r = p("make a page about cats")!!
        assertEquals(BrowserJob.BUILD, r.job)
        val site = p("can you build a website for the bakery")!!
        assertEquals(BrowserJob.BUILD, site.job)
        assertNotNull(p("write a landing page"))
        assertNotNull(p("design a webpage"))
        assertEquals(BrowserJob.BUILD, p("create a website")!!.job)
        assertEquals(BrowserJob.BUILD, p("make a web site for the bakery")!!.job)
        assertEquals(BrowserJob.BUILD, p("generate a site about cats")!!.job)
    }

    @Test
    fun `scrape pulls a url`() {
        val r = p("scrape https://example.com/news")!!
        assertEquals(BrowserJob.SCRAPE, r.job)
        assertEquals("https://example.com/news", r.url)
        assertEquals(BrowserJob.SCRAPE, p("extract the text from this page")!!.job)
        assertEquals(BrowserJob.SCRAPE, p("pull the links")!!.job)
    }

    @Test
    fun `open the agent browser`() {
        assertEquals(BrowserJob.OPEN, p("open the browser")!!.job)
        assertEquals(BrowserJob.OPEN, p("hey meow open the agent browser")!!.job)
    }

    @Test
    fun `does not steal nearby phrases`() {
        assertNull(p("how to scrape a website"))
        assertNull(p("how to create a website"))
        assertNull(p("what is a web page"))
        assertNull(p("start a project"))
        assertNull(p("open spotify"))
        assertNull(p("tell me a fact"))
    }
}
