package com.example.meowmba

import com.example.meowmba.core.nav.NavAgent
import com.example.meowmba.core.nav.Place
import com.example.meowmba.core.nav.PlaceRepository
import com.example.meowmba.core.nav.PlaceSource
import com.example.meowmba.core.nav.PlaceSourceKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the interaction the user specified: a named brand routes immediately, a
 * vague category offers a list and asks, and the follow-up reply picks from it.
 */
class NavAgentTest {

    private val burgers = listOf(
        place("Five Guys", 400.0),
        place("Whataburger", 900.0),
        place("In-N-Out Burger", 1500.0),
    )

    private fun place(name: String, dist: Double) = Place(
        name = name, lat = 33.0 + dist / 100000, lon = -112.0,
        distanceMeters = dist, source = PlaceSourceKind.OFFLINE,
    )

    /** Returns a fixed list regardless of query, so we test the agent not the search. */
    private class FakeSource(private val results: List<Place>) : PlaceSource {
        override val kind = PlaceSourceKind.OFFLINE
        override fun isAvailable() = true
        override suspend fun searchByName(
            name: String, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
        ) = results
        override suspend fun searchByCategory(
            categories: List<String>, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
        ) = results
    }

    private class EmptySource : PlaceSource {
        override val kind = PlaceSourceKind.OFFLINE
        override fun isAvailable() = true
        override suspend fun searchByName(
            name: String, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
        ) = emptyList<Place>()
        override suspend fun searchByCategory(
            categories: List<String>, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
        ) = emptyList<Place>()
    }

    private fun agent(source: PlaceSource) =
        NavAgent(PlaceRepository(source, null))

    // ── "take me to Circle K" ────────────────────────────────────────────────────

    @Test
    fun `named request routes immediately without asking`() = runTest {
        val a = agent(FakeSource(listOf(place("Circle K #2410", 300.0))))
        val out = a.handle("take me to Circle K", 33.0, -112.0)
        assertTrue("expected Navigating, got $out", out is NavAgent.Outcome.Navigating)
        assertEquals("Circle K #2410", (out as NavAgent.Outcome.Navigating).place.name)
        assertTrue("should mention distance", out.say.contains("ft") || out.say.contains("mi"))
    }

    // ── "take me to get a burger" ────────────────────────────────────────────────

    @Test
    fun `vague request offers a list and asks`() = runTest {
        val a = agent(FakeSource(burgers))
        val out = a.handle("take me to get a burger", 33.0, -112.0)
        assertTrue("expected Choose, got $out", out is NavAgent.Outcome.Choose)
        out as NavAgent.Outcome.Choose
        assertEquals(3, out.places.size)
        assertEquals("Five Guys", out.places.first().name)   // nearest first
        assertTrue("should offer the choice", out.say.contains("pick"))
    }

    @Test
    fun `saying yes takes the closest`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        val out = a.handle("yes", 33.0, -112.0)
        assertEquals("Five Guys", (out as NavAgent.Outcome.Navigating).place.name)
    }

    @Test
    fun `picking by number works`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        val out = a.handle("2", 33.0, -112.0)
        assertEquals("Whataburger", (out as NavAgent.Outcome.Navigating).place.name)
    }

    @Test
    fun `picking by ordinal works`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        val out = a.handle("the third one", 33.0, -112.0)
        assertEquals("In-N-Out Burger", (out as NavAgent.Outcome.Navigating).place.name)
    }

    @Test
    fun `picking by name works`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        val out = a.handle("whataburger", 33.0, -112.0)
        assertEquals("Whataburger", (out as NavAgent.Outcome.Navigating).place.name)
    }

    @Test
    fun `pending list is cleared after a pick`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        a.handle("yes", 33.0, -112.0)
        assertEquals(null, a.pending)
    }

    // ── Not swallowing unrelated messages ────────────────────────────────────────

    @Test
    fun `unrelated message while a list is pending is not claimed`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        // The user ignores the question. This must reach normal chat, not the navigator.
        assertFalse(a.claims("what's the weather"))
    }

    @Test
    fun `a fresh navigation request while pending is still handled`() = runTest {
        val a = agent(FakeSource(burgers))
        a.handle("take me to get a burger", 33.0, -112.0)
        assertTrue(a.claims("take me to Circle K"))
    }

    // ── Degradation ──────────────────────────────────────────────────────────────

    @Test
    fun `missing location asks for permission rather than failing`() = runTest {
        val a = agent(FakeSource(burgers))
        val out = a.handle("take me to get a burger", null, null)
        assertTrue(out is NavAgent.Outcome.NeedLocation)
    }

    @Test
    fun `no results is reported clearly`() = runTest {
        val a = agent(EmptySource())
        val out = a.handle("take me to Circle K", 33.0, -112.0)
        assertTrue("expected NoResults, got $out", out is NavAgent.Outcome.NoResults)
    }

    @Test
    fun `non navigation text is not claimed`() = runTest {
        val a = agent(FakeSource(burgers))
        assertTrue(a.handle("how am i doing", 33.0, -112.0) is NavAgent.Outcome.NotNavigation)
    }
}
