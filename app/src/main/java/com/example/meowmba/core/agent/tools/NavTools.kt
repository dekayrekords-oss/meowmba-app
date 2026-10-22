package com.example.meowmba.core.agent.tools

import android.content.Context
import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.nav.NavRequest
import com.example.meowmba.core.nav.NavRequestParser
import com.example.meowmba.core.nav.NavigationLauncher
import com.example.meowmba.core.nav.Place
import com.example.meowmba.core.nav.PlaceRepository
import com.example.meowmba.core.nav.SavedPlaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Navigation tools.
 *
 * Split into find-then-go on purpose. `nav.find_places` is SAFE and read-only, so the
 * companion can look, describe the options, and ask "closest or do you want to pick?"
 * before anything happens. `nav.navigate` is the separate act of actually routing.
 *
 * That split is what makes the requested behaviour fall out naturally:
 *   "take me to Circle K"     -> find_places(query=Circle K), then navigate to #1
 *   "take me to get a burger" -> find_places(query=burger), read the list back, ask,
 *                                then navigate to whichever the user picked
 *
 * Lookups are offline-first (OSM region packs), so this works with no signal.
 */

/** Formats a result list identically for every tool that returns places. */
private fun renderPlaces(places: List<Place>): String =
    places.mapIndexed { i, p ->
        val dist = p.distanceLabel()?.let { " ($it)" } ?: ""
        val addr = p.address?.let { " — $it" } ?: ""
        "${i + 1}. ${p.name}$dist$addr [${p.lat},${p.lon}]"
    }.joinToString("\n")

/**
 * `nav.find_places` — search nearby places without going anywhere.
 *
 * SAFE: pure lookup. The model is expected to call this first and talk to the user
 * about the results before calling `nav.navigate`.
 */
class FindPlacesTool(
    private val context: Context,
    private val repository: PlaceRepository,
    private val savedPlaces: SavedPlaceStore,
) : Tool {

    override val definition = ToolDefinition(
        id = "nav.find_places",
        displayName = "Find places nearby",
        description = "Search for places near the user, nearest first. Use for either a " +
            "specific business (\"Circle K\", \"Whataburger\") or a vague category " +
            "(\"burger\", \"coffee\", \"gas\"). Returns a numbered list with distances " +
            "and coordinates. For a vague request, read the closest few back to the " +
            "user and ask whether they want the nearest or would rather pick, then " +
            "call nav.navigate with the coordinates they chose.",
        inputSchema = mapOf("query" to "string", "limit" to "int"),
        requiredPermissions = setOf("android.permission.ACCESS_FINE_LOCATION"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 25_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            val query = args["query"]?.trim()?.takeIf { it.isNotBlank() }
                ?: return@withContext ToolResult.Failure(
                    "Missing 'query' — what should I look for?"
                )
            val limit = args["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5

            // Reuse the same classifier the offline path uses so "burger" searches the
            // burger categories rather than looking for a shop literally named burger.
            val request = NavRequestParser.parse("take me to $query", savedPlaces.names())
                ?: NavRequest.Named(query)

            if (request is NavRequest.Saved) {
                val saved = savedPlaces.get(request.placeName)
                    ?: return@withContext ToolResult.Failure(
                        "\"${request.placeName}\" isn't saved yet."
                    )
                return@withContext ToolResult.Success(renderPlaces(listOf(saved.toPlace())))
            }

            val fix = CurrentLocation.fresh(context)
                ?: return@withContext ToolResult.Failure(
                    if (CurrentLocation.hasPermission(context)) {
                        "No location fix yet — can't tell what's nearby."
                    } else {
                        "Location permission not granted, so I can't search nearby."
                    }
                )

            val result = repository.find(request, fix.lat, fix.lon, limit = limit)
            when {
                result.noSourceAvailable -> ToolResult.Failure(
                    "No offline map packs are installed and no Geoapify key is set, so " +
                        "I have nothing to search. Install a region pack from the NAV card."
                )
                result.places.isEmpty() -> ToolResult.Failure("Nothing matching \"$query\" nearby.")
                else -> ToolResult.Success(renderPlaces(result.places))
            }
        }
}

/**
 * `nav.navigate` — start turn-by-turn to a coordinate.
 *
 * MODERATE, no confirmation card: it opens a visible maps app and the user just asked
 * out loud, same reasoning as phone.launch_app. Takes coordinates rather than text so
 * the user gets the exact place they picked from the list, not whatever a fresh maps
 * search would surface.
 */
class NavigateTool(private val context: Context) : Tool {

    override val definition = ToolDefinition(
        id = "nav.navigate",
        displayName = "Navigate somewhere",
        description = "Start turn-by-turn navigation to a coordinate, using the lat and " +
            "lon from a nav.find_places result. Pass 'name' too so the map is labelled. " +
            "Only call this once you know which place the user wants.",
        inputSchema = mapOf("lat" to "string", "lon" to "string", "name" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val lat = args["lat"]?.toDoubleOrNull()
        val lon = args["lon"]?.toDoubleOrNull()
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return ToolResult.Failure(
                "Need numeric 'lat' and 'lon' — take them from a nav.find_places result."
            )
        }
        val name = args["name"]?.takeIf { it.isNotBlank() } ?: "Destination"
        val place = Place(name = name, lat = lat, lon = lon)
        return if (NavigationLauncher.navigateTo(context, place)) {
            ToolResult.Success("Opened Maps for $name. Check the maps app to start or confirm turn-by-turn navigation.")
        } else {
            ToolResult.Failure("No maps app on this phone could handle that.")
        }
    }
}

/**
 * `nav.save_place` — remember where the user is now under a name.
 *
 * MODERATE: writes local state, but trivially reversible from the NAV card.
 */
class SavePlaceTool(
    private val context: Context,
    private val savedPlaces: SavedPlaceStore,
) : Tool {

    override val definition = ToolDefinition(
        id = "nav.save_place",
        displayName = "Save this location",
        description = "Remember the user's current location under a name, so they can " +
            "later say \"take me home\". Example names: home, work, gym, mom's.",
        inputSchema = mapOf("name" to "string"),
        requiredPermissions = setOf("android.permission.ACCESS_FINE_LOCATION"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 18_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = args["name"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name' — what should I call this place?")
        val fix = CurrentLocation.fresh(context)
            ?: return ToolResult.Failure(
                if (CurrentLocation.hasPermission(context)) "No location fix yet."
                else "Location permission not granted."
            )
        savedPlaces.save(name, fix.lat, fix.lon)
        return ToolResult.Success("Saved \"${name.lowercase()}\" at ${fix.lat}, ${fix.lon}.")
    }
}

/** `nav.list_saved_places` — what the user has taught us. */
class ListSavedPlacesTool(private val savedPlaces: SavedPlaceStore) : Tool {

    override val definition = ToolDefinition(
        id = "nav.list_saved_places",
        displayName = "List saved places",
        description = "List the places the user has saved by name (home, work, …), with " +
            "coordinates you can pass to nav.navigate.",
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val all = savedPlaces.all()
        return if (all.isEmpty()) {
            ToolResult.Success("No saved places yet.")
        } else {
            ToolResult.Success(renderPlaces(all.map { it.toPlace() }))
        }
    }
}
