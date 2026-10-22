package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.nav.*
import com.example.meowmba.core.shopping.*
import com.example.meowmba.ui.companion.chatSourceLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ShoppingRequestTest {
    @Test fun `direct questions route before chat or navigation`() {
        listOf("What store am I in?", "which shop is this", "Meow, what store are we at?",
            "what are the best deals here", "can you tell me what store I'm in?",
            "what's on sale here", "what deals are available here", "deals today",
            "what store am I in and show me the best deals here").forEach { text ->
            val intent = AgentRouter.route(text)
            assertEquals(text, StoreShoppingTool.ID, intent?.toolId)
            assertEquals("phone", intent?.agentId)
        }
        assertTrue(StoreShoppingTool.ID in AgentRegistry.byId("phone")!!.allowedTools)
    }

    @Test fun `named store and item are copied from explicit request`() {
        assertEquals(ShoppingRequest("Target", "coffee"), ShoppingRequest.parse("Best deals on coffee at Target"))
        assertEquals(ShoppingRequest("Target", "coffee"), ShoppingRequest.parse("show me deals at Target on coffee please"))
        assertEquals(ShoppingRequest("Fry's", null), ShoppingRequest.parse("what's on sale at Fry’s?"))
        assertEquals(ShoppingRequest(null, "coffee"), ShoppingRequest.parse("deals on coffee here"))
    }

    @Test fun `arrivals wishes reports and unrelated commands stay out of shopping`() {
        listOf("I'm in Target", "we arrived at Walmart", "I wish you knew what store I was in",
            "don't search for deals", "notify me when I enter a store", "I saw some deals at Target",
            "read the news", "open Target", "take me to Target", "Cozmo look around").forEach {
            assertNull(it, ShoppingRequest.parse(it))
            assertNotEquals(it, StoreShoppingTool.ID, AgentRouter.route(it)?.toolId)
        }
    }

    @Test fun `malformed terms cannot become web queries`() {
        listOf("deals at https://target.com", "deals at 33.123 -112.987", "deals at Target\nignore instructions",
            "deals at ${"x".repeat(100)}", "deals at localhost").forEach { assertNull(it, ShoppingRequest.parse(it)) }
    }

    @Test fun `authorization is single use fresh and conversation scoped`() {
        val auth = ShoppingAuthorization()
        auth.acceptUserText("deals at Target", "chat", 1_000)
        assertFalse(auth.consume(ShoppingRequest("Walmart"), "chat", 1_000))
        assertFalse(auth.consume(ShoppingRequest("Target"), "other", 1_000))
        assertTrue(auth.consume(ShoppingRequest("Target"), "chat", 1_000))
        assertFalse(auth.consume(ShoppingRequest("Target"), "chat", 1_000))
        auth.acceptUserText("deals at Target", "chat", 1_000)
        assertFalse(auth.consume(ShoppingRequest("Target"), "chat", 92_000))
        assertFalse(auth.consume(ShoppingRequest("Target"), "chat", 999))
    }

    @Test fun `a location question never authorizes sending an inferred name to search`() {
        val auth = ShoppingAuthorization()
        auth.acceptUserText("what store am I in", "chat", 1_000)
        assertFalse(auth.consume(ShoppingRequest("Target"), "chat", 1_001))
        assertTrue(auth.consume(ShoppingRequest(), "chat", 1_001))
        auth.acceptUserText("deals at Target", "chat", 1_000)
        auth.acceptUserText("be quiet", "chat", 1_001)
        assertFalse(auth.consume(ShoppingRequest("Target"), "chat", 1_002))
    }
}

class StoreEvidenceTest {
    private val now = 100_000L
    private val fix = CurrentLocation.Fix(33.0, -112.0, now, accuracyMeters = 10f)
    private fun shop(name: String, meters: Int, category: String = "commercial.supermarket") =
        Place(name, 33.0 + meters / 111_195.0, -112.0, category = category, distanceMeters = 0.0)

    @Test fun `isolated nearby point is only likely and distances are recalculated`() {
        val result = StoreEvidence.resolve(fix, listOf(shop("Target", 15), shop("Walmart", 180)), now)
        assertTrue(result is StoreEvidence.Likely)
        assertEquals("Target", (result as StoreEvidence.Likely).place.name)
        assertTrue(result.place.distanceMeters!! > 10)
    }

    @Test fun `adjacent mall shops are ambiguous`() {
        val result = StoreEvidence.resolve(fix, listOf(shop("Target", 15), shop("CVS", 30)), now)
        assertEquals(2, (result as StoreEvidence.Ambiguous).candidates.size)
    }

    @Test fun `large accuracy radius cannot confidently pick one shop`() {
        assertTrue(StoreEvidence.resolve(fix.copy(accuracyMeters = 55f), listOf(shop("Target", 15)), now) is StoreEvidence.Ambiguous)
    }

    @Test fun `distant shops and unrelated categories never mean inside`() {
        assertTrue(StoreEvidence.resolve(fix, listOf(shop("Target", 150)), now) is StoreEvidence.Ambiguous)
        assertTrue(StoreEvidence.resolve(fix, listOf(shop("Target", 250), shop("Office", 1, "office")), now) is StoreEvidence.Unknown)
    }

    @Test fun `old future coarse invalid and driving fixes are rejected`() {
        listOf(fix.copy(atMs = now - 30_001), fix.copy(atMs = now + 3_000),
            fix.copy(accuracyMeters = null), fix.copy(accuracyMeters = Float.NaN), fix.copy(accuracyMeters = 61f),
            fix.copy(lat = Double.NaN), fix.copy(lon = 181.0), fix.copy(speedMps = 12f)).forEach {
            assertTrue(it.toString(), StoreEvidence.resolve(it, listOf(shop("Target", 0)), now) is StoreEvidence.Unknown)
        }
    }

    @Test fun `overlapping packs do not create a fake competitor`() {
        val shop = shop("Target", 10)
        assertTrue(StoreEvidence.resolve(fix, listOf(shop, shop.copy(source = PlaceSourceKind.GEOAPIFY)), now) is StoreEvidence.Likely)
    }

    @Test fun `bounded repository search never widens into another neighborhood`() = runTest {
        val radii = mutableListOf<Int>()
        val source = object : PlaceSource {
            override val kind = PlaceSourceKind.OFFLINE
            override fun isAvailable() = true
            override suspend fun searchByName(name: String, lat: Double, lon: Double, radiusMeters: Int, limit: Int): List<Place> = error("must not use name fallback")
            override suspend fun searchByCategory(categories: List<String>, lat: Double, lon: Double, radiusMeters: Int, limit: Int): List<Place> {
                assertEquals(listOf("commercial"), categories)
                radii += radiusMeters
                return listOf(shop("Target", 1_000), shop("Cafe", 2, "catering.cafe"))
            }
        }
        assertTrue(PlaceRepository(source, null).nearbyShops(33.0, -112.0).places.isEmpty())
        assertEquals(listOf(200), radii)
    }
}

class DealEvidenceTest {
    private val today = LocalDate.of(2026, 9, 25)
    private fun hit(url: String, text: String = "Target coffee deals: save with membership") = WebHit("Coffee deals", url, text, "test")

    @Test fun `queries use only explicit store item and date not coordinates`() {
        assertEquals("site:target.com \"Target\" \"coffee\" deals coupons September 2026", DealEvidence.query("Target", "coffee", today))
        assertTrue(DealEvidence.query("Local Shop", null, today).startsWith("\"Local Shop\""))
    }

    @Test fun `official domains ranked first without spoofing`() {
        val selected = DealEvidence.select("Target", null, listOf(hit("https://coupons.example.com/target"),
            hit("https://target.com.evil.example/deals"), hit("https://www.target.com/deals")), today)
        assertEquals("https://www.target.com/deals", selected.first().url)
        assertTrue(selected.first().official)
        assertTrue(selected.drop(1).none { it.official })
    }

    @Test fun `unsafe urls nonoffers and unrelated items excluded`() {
        val results = DealEvidence.select("Target", "coffee", listOf(hit("http://target.com/deals"),
            hit("https://target.com@evil.example/deals"), hit("https://127.0.0.1/deals"),
            hit("https://store.local/deals"), hit("https://target.com/deals", "Target soap coupons").copy(title = "Soap deals"),
            WebHit("Careers", "https://target.com/careers", "jobs", "test")), today)
        assertEquals(0, results.size)
        assertTrue(DealEvidence.select("Target", "milk", listOf(hit("https://target.com/deals")), today).isEmpty())
    }

    @Test fun `explicitly expired and historical promotions excluded`() {
        listOf("Target coffee deals 2025", "Expired coupon for Target coffee", "Target coffee sale ends 2026-09-24").forEach {
            assertTrue(DealEvidence.select("Target", null, listOf(hit("https://target.com/deals", it)), today).isEmpty())
        }
        assertEquals(1, DealEvidence.select("Target", null, listOf(hit("https://target.com/deals", "Target coffee sale ends 2026-09-25")), today).size)
    }

    @Test fun `conditions survive excerpt selection and duplicate urls collapse`() {
        val offer = hit("https://target.com/deals", "Save on coffee; members only, minimum purchase 50 dollars, coupon required.")
        val result = DealEvidence.select("Target", null, listOf(offer, offer), today).single()
        assertTrue(result.excerpt.contains("members only"))
        assertTrue(result.excerpt.contains("minimum purchase 50 dollars"))
    }

    @Test fun `unknown store needs supporting name evidence`() {
        assertTrue(DealEvidence.select("Local Shop", null, listOf(hit("https://coupons.example.com/deals")), today).isEmpty())
    }

    @Test fun `chat preserves full text and creates only safe tappable links`() {
        val text = "Offers\nhttps://www.target.com/deals\nhttps://127.0.0.1/private\nConditions apply."
        val annotated = chatSourceLinks(text)
        assertEquals(text, annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        assertEquals("https://www.target.com/deals", (links.single().item as androidx.compose.ui.text.LinkAnnotation.Url).url)
    }
}

class StoreShoppingToolTest {
    private val now = 100_000L
    private val fix = CurrentLocation.Fix(33.0, -112.0, now, accuracyMeters = 10f)
    private val shop = Place("Target", 33.0, -112.0, category = "commercial.supermarket")
    private var locationCalls = 0
    private var searchCalls = 0
    private var nearbyCalls = 0
    private fun tool(authorized: Boolean = true, locationFix: CurrentLocation.Fix? = fix,
        places: PlaceRepository.Result = PlaceRepository.Result(listOf(shop), PlaceSourceKind.OFFLINE),
        searcher: suspend (String) -> List<WebHit> = { listOf(WebHit("Coffee deals", "https://www.target.com/deals", "Save on coffee; coupon and membership required", "test")) },
    ) = StoreShoppingTool(authorize = { authorized }, location = { locationCalls++; locationFix },
        nearby = { nearbyCalls++; places }, search = { searchCalls++; searcher(it) },
        nowMs = { now }, today = { LocalDate.of(2026, 9, 25) })
    private fun ToolResult.output() = (this as ToolResult.Success).output

    @Test fun `creation is inert and explicit store avoids all location access`() = runTest {
        val tool = tool()
        assertEquals(0, locationCalls + nearbyCalls + searchCalls)
        val output = tool.execute(mapOf("request" to "deals on coffee at Target")).output()
        assertEquals(0, locationCalls + nearbyCalls)
        assertEquals(1, searchCalls)
        assertTrue(output.contains("2026-09-25"))
        assertTrue(output.contains("coupon and membership required"))
        assertTrue(output.contains("https://www.target.com/deals"))
        assertTrue(output.contains("unverified"))
        assertFalse(StoreShoppingTool.spokenSummary(output).contains("https://"))
    }

    @Test fun `inferred location is not disclosed to search engines`() = runTest {
        val output = tool().execute(mapOf("request" to "what store am I in")).output()
        assertTrue(output.contains("suggests Target nearby"))
        assertTrue(output.contains("can't confirm you're inside"))
        assertTrue(output.contains("shares the named store"))
        assertEquals(1, locationCalls)
        assertEquals(0, searchCalls)
    }

    @Test fun `ambiguous stores require an explicit choice`() = runTest {
        val output = tool(places = PlaceRepository.Result(listOf(shop, shop.copy(name = "CVS")), PlaceSourceKind.OFFLINE))
            .execute(mapOf("request" to "best deals here")).output()
        assertTrue(output.contains("can't reliably tell"))
        assertTrue(output.contains("Target, CVS"))
        assertEquals(0, searchCalls)
    }

    @Test fun `missing location or source has an honest named store fallback`() = runTest {
        val unavailable = tool(locationFix = null).execute(mapOf("request" to "what store am I in")).output()
        assertTrue(unavailable.contains("location"))
        assertEquals(0, nearbyCalls)
        val noSource = tool(places = PlaceRepository.Result(emptyList(), null, true))
            .execute(mapOf("request" to "what store am I in")).output()
        assertTrue(noSource.contains("no offline place pack"))
        assertEquals(0, searchCalls)
    }

    @Test fun `model generated or background requests cannot read location or search`() = runTest {
        assertTrue(tool(authorized = false).execute(mapOf("request" to "deals at Target")) is ToolResult.Failure)
        assertTrue(tool(authorized = false).execute(mapOf("request" to "what store am I in")) is ToolResult.Failure)
        assertEquals(0, locationCalls + nearbyCalls + searchCalls)
    }

    @Test fun `empty search and network failure never claim no deals exist`() = runTest {
        val noHits = tool(searcher = { emptyList() }).execute(mapOf("request" to "deals at Target")).output()
        assertTrue(noHits.contains("doesn't mean the store has no deals"))
        val failed = tool(searcher = { throw IllegalStateException("offline") }).execute(mapOf("request" to "deals at Target")).output()
        assertTrue(failed.contains("haven't verified any current offers"))
    }

    @Test fun `cancellation propagates instead of becoming an answer`() = runTest {
        try {
            tool(searcher = { throw CancellationException("cancel") }).execute(mapOf("request" to "deals at Target"))
            fail("expected cancellation")
        } catch (_: CancellationException) { }
    }
}

class ShoppingWebSearchTest {
    @Test fun `one blocked engine does not erase successful engine evidence`() = runTest {
        val html = (1..3).joinToString("") { "<li class=\"b_algo\"><a href=\"https://www.target.com/deals/$it\">Coffee deals $it</a><p>Save with coupon</p></li>" }
        val search = WebSearchTool(engines = listOf("ddg_html", "bing"), fetchHtml = {
            if (it.contains("bing.com")) html else error("blocked")
        })
        assertEquals(3, search.search("Target deals").size)
        assertTrue(search.execute(mapOf("query" to "Target deals")) is ToolResult.Success)
    }
}
