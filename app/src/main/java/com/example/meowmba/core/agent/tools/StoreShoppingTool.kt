package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.nav.PlaceRepository
import com.example.meowmba.core.shopping.DealEvidence
import com.example.meowmba.core.shopping.ShoppingRequest
import com.example.meowmba.core.shopping.StoreEvidence
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/** On-demand only: no arrival callback, geofence, camera, notifications or persistent store state. */
class StoreShoppingTool(
    private val authorize: (ShoppingRequest) -> Boolean,
    private val location: suspend () -> CurrentLocation.Fix?,
    private val nearby: suspend (CurrentLocation.Fix) -> PlaceRepository.Result,
    private val search: suspend (String) -> List<WebHit>,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) : Tool {
    override val definition = ToolDefinition(
        id = ID,
        displayName = "Store and deal lookup",
        description = "Only when the user asks which store they are in or asks for store deals: " +
            "pass their explicit request unchanged. Checks fresh location against existing offline " +
            "places or the already-configured place provider. GPS proximity is not proof of being inside. " +
            "Inferred store names are NOT sent to web search: ask the user to name the store in a deal request. " +
            "For an explicitly named store, returns live search evidence, not guaranteed best prices. " +
            "No background checks, arrival announcements, purchases or coupon enrollment.",
        inputSchema = mapOf("request" to "string"),
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 50_000L,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val request = args["request"]?.let(ShoppingRequest::parse)
            ?: return ToolResult.Failure("Ask ‘What store am I in?’ or ‘Best deals on coffee at Target’.")
        if (!authorize(request)) return ToolResult.Failure("A new, matching request from you is needed for this store lookup. No location or search was accessed.")
        val store = request.store
        if (store == null) return identifyStore(request)
        // This name was supplied in the explicit request, never derived from a location fix.
        val date = today()
        val query = DealEvidence.query(store, request.item, date)
        val hits = try { search(query) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            return ToolResult.Success("I couldn't check deal sources for $store online right now. I haven't verified any current offers.")
        }
        val leads = DealEvidence.select(store, request.item, hits, date)
        if (leads.isEmpty()) return ToolResult.Success("I couldn't find usable deal evidence at $store${request.item?.let { " for $it" }.orEmpty()}. Search may be blocked or incomplete; that doesn't mean the store has no deals.")
        return ToolResult.Success(buildString {
            append("I found ${leads.size} deal sources for $store${request.item?.let { " for $it" }.orEmpty()}; details and links are in chat. These aren't verified checkout prices.")
            append("\n\nDeal leads searched $date — retailer sources first, not ranked by guaranteed savings. Search excerpts may be stale or incomplete. Check dates, local availability, sizes, minimum spend, coupon and membership terms before buying.")
            leads.forEachIndexed { index, lead ->
                append("\n\n${index + 1}. ${lead.title} (${if (lead.official) "retailer website" else "third-party source; verify with the store"})")
                if (lead.excerpt.isNotBlank()) append("\nSearch excerpt (unverified): ${lead.excerpt}")
                append("\n${lead.url}")
            }
            if (request.item == null) append("\n\nFor a focused search, say ‘deals on coffee at $store’. I haven't checked your cart, store app, loyalty account or checkout price.")
        })
    }

    private suspend fun identifyStore(request: ShoppingRequest): ToolResult {
        val evidence = try {
            val fix = location()
            StoreEvidence.unusable(fix, nowMs())?.let {
                return ToolResult.Success("$it Tell me the store name, for example ‘deals at Target’, or check Meow's location permission and try again.")
            }
            val places = nearby(requireNotNull(fix))
            if (places.noSourceAvailable) {
                return ToolResult.Success("I can't identify the store yet: no offline place pack or configured place provider is available. Tell me the store name, for example ‘deals at Target’, and I can still search for deals.")
            }
            StoreEvidence.resolve(fix, places.places, nowMs())
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            return ToolResult.Success("The nearby-store lookup didn't finish. Tell me the store name, for example ‘deals at Target’, and I can still search for deals.")
        }
        return ToolResult.Success(when (evidence) {
            is StoreEvidence.Unknown -> "${evidence.reason} Tell me the store name, for example ‘deals at Target’."
            is StoreEvidence.Ambiguous -> buildString {
                append("I can't reliably tell which store you're in. Nearby options are ")
                append(evidence.candidates.joinToString(", ") { it.name })
                append(". Say ‘deals at’ followed by the right store name. I haven't searched offers for an unconfirmed store.")
            }
            is StoreEvidence.Likely -> "Location suggests ${evidence.place.name} nearby; I can't confirm you're inside. " +
                "For offers, say ‘deals${request.item?.let { " on $it" }.orEmpty()} at ${evidence.place.name}’. " +
                "That shares the named store with public search engines, not your GPS coordinates. I haven't shared the inferred store name with them."
        })
    }

    companion object {
        const val ID = "shopping.store_deals"
        fun spokenSummary(output: String): String = output.substringBefore("\n\n")
    }
}
