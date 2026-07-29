package com.example.meowmba

import com.example.meowmba.core.nav.NavRequest
import com.example.meowmba.core.nav.NavRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the whole reason navigation works with meow unreachable, so the
 * cases the user actually described are pinned here.
 */
class NavRequestParserTest {

    // ── The two headline cases ───────────────────────────────────────────────────

    @Test
    fun `named brand routes directly`() {
        val r = NavRequestParser.parse("take me to Circle K")
        assertTrue(r is NavRequest.Named)
        assertEquals("circle k", (r as NavRequest.Named).query)
    }

    @Test
    fun `vague food request is a category`() {
        val r = NavRequestParser.parse("take me to get a burger")
        assertTrue("expected Category, got $r", r is NavRequest.Category)
        r as NavRequest.Category
        assertEquals("burger", r.label)
        assertTrue(r.categories.any { it.contains("burger") })
    }

    // ── Trigger coverage ─────────────────────────────────────────────────────────

    @Test
    fun `various phrasings all reach navigation`() {
        val phrases = listOf(
            "navigate to Whataburger",
            "drive me to QuikTrip",
            "directions to Home Depot",
            "where's the nearest Circle K",
            "let's go to Target",
            "i need gas",
            "i could go for some tacos",
            "find me a pharmacy",
        )
        phrases.forEach { p ->
            assertTrue("should parse: $p", NavRequestParser.parse(p) != null)
        }
    }

    @Test
    fun `non navigation text is ignored`() {
        val phrases = listOf(
            "what's the weather",
            "remember this",
            "how am i doing",
            "take a picture",
            "",
            "hello",
        )
        phrases.forEach { p ->
            assertNull("should not parse: $p", NavRequestParser.parse(p))
        }
    }

    // ── Cleanup of noise words ───────────────────────────────────────────────────

    @Test
    fun `strips leading and trailing noise`() {
        val r = NavRequestParser.parse("take me to the Circle K nearby")
        assertEquals("circle k", (r as NavRequest.Named).query)
    }

    @Test
    fun `handles trailing punctuation and please`() {
        val r = NavRequestParser.parse("navigate to Starbucks please!")
        assertEquals("starbucks", (r as NavRequest.Named).query)
    }

    // ── The distinction that drives the UX ───────────────────────────────────────

    @Test
    fun `category words only match as whole words`() {
        // "Burgerville" is a brand, not the category "burger" — otherwise a named
        // request would wrongly turn into a pick-one-of-these list.
        val r = NavRequestParser.parse("take me to Burgerville")
        assertTrue("expected Named, got $r", r is NavRequest.Named)
    }

    @Test
    fun `unknown words default to a name search`() {
        val r = NavRequestParser.parse("take me to Buc-ee's")
        assertTrue(r is NavRequest.Named)
    }

    @Test
    fun `gas and coffee are categories`() {
        assertTrue(NavRequestParser.parse("i need gas") is NavRequest.Category)
        assertTrue(NavRequestParser.parse("take me to get coffee") is NavRequest.Category)
    }

    // ── Saved places ─────────────────────────────────────────────────────────────

    @Test
    fun `home and work resolve to saved places`() {
        assertEquals(
            "home",
            (NavRequestParser.parse("take me to home") as NavRequest.Saved).placeName,
        )
        assertEquals(
            "work",
            (NavRequestParser.parse("navigate to the office") as NavRequest.Saved).placeName,
        )
    }

    @Test
    fun `user taught place names are recognised`() {
        val r = NavRequestParser.parse("take me to mom's", setOf("mom's"))
        assertTrue("expected Saved, got $r", r is NavRequest.Saved)
    }
}
