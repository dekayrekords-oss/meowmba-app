package com.example.meowmba.core.agent.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.meowmba.core.car.CarGeo
import com.example.meowmba.core.car.DrivingState
import com.example.meowmba.core.car.TripStats
import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.nav.SavedPlaceStore
import java.util.Locale

/**
 * Car-assistant tools.
 *
 * These read the live [DrivingState]/[TripStats] produced by the car module and are
 * the voice surface for everything an in-car assistant should do: "how fast am I
 * going?", "are we there yet?", "find gas", "drive mode on", "help, I crashed".
 *
 * `car.status`, `car.trip` and `car.eta` are SAFE reads. `car.drive_mode` flips the
 * manual override (reversible, and visibly announced). `car.help` texts the
 * emergency contact — the one genuinely consequential action, hence MODERATE and
 * gated on the contact actually being configured.
 */

private fun mphLabel(mph: Float): String =
    if (mph < 1f) "stopped" else "%.0f mph".format(Locale.US, mph)

private fun tripSummary(t: TripStats): String = buildString {
    append("%.1f miles".format(Locale.US, t.distanceMiles))
    if (t.durationMinutes > 0) append(", ${t.durationMinutes} min")
    if (t.maxSpeedMph >= 1f) append(", max %.0f mph".format(Locale.US, t.maxSpeedMph))
}

/** Shared by the car.help tool and the crash-alert card button. */
fun buildEmergencyTextIntent(
    context: Context,
    number: String,
    trip: TripStats?,
): Intent {
    val fix = CurrentLocation.last(context)
    val mapsLink = if (fix != null) {
        "http://maps.google.com/?q=${fix.lat},${fix.lon}"
    } else {
        "I couldn't get a location fix."
    }
    val body = buildString {
        append("Meowmba car alert. ")
        trip?.let { append("Trip: ${tripSummary(it)}. ") }
        append("Location: $mapsLink")
    }
    return Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("smsto:$number")
    ).putExtra("sms_body", body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** `car.status` — am I driving, how fast, what's pulling us. */
class CarStatusTool(
    private val state: () -> DrivingState,
    private val trip: () -> TripStats?,
) : Tool {

    override val definition = ToolDefinition(
        id = "car.status",
        displayName = "Driving status",
        description = "Whether the user is driving right now, their current speed, " +
            "G-force, and the reason driving was detected (GPS speed, manual, " +
            "Bluetooth). Use for \"how fast am I going\", \"are we driving\", " +
            "\"are we there yet\", \"is the car moving\". Also reports the current " +
            "trip's distance, duration and top speed.",
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val s = state()
        val active = trip()
        val body = if (s.isDriving) {
            buildString {
                append("You're driving")
                s.speedMph.let { if (it >= 1f) append(" at %.0f mph".format(Locale.US, it)) }
                if (s.reason.isNotBlank()) append(" (${s.reason.lowercase()})")
                if (s.gForce >= 1.5f) append(", pulling %.1f g".format(Locale.US, s.gForce))
                append(". ")
                if (active != null) {
                    append("Trip so far: ${tripSummary(active)}.")
                } else {
                    append("No trip logged yet.")
                }
            }
        } else {
            "You're not driving right now. Current speed ${mphLabel(s.speedMph)}." +
                if (s.carModeEnabled) " Drive mode is on, so I'll pick you up when you start moving." else " Drive mode is off — say \"drive mode on\" or enable it in Settings to have me watch the road."
        }
        return ToolResult.Success(body)
    }
}

/** `car.drive_mode` — force drive mode on or off by voice. */
class CarDriveModeTool(
    private val currentManual: () -> Boolean,
    private val onSet: (Boolean) -> Unit,
) : Tool {

    override val definition = ToolDefinition(
        id = "car.drive_mode",
        displayName = "Toggle drive mode",
        description = "Turn drive mode on or off. Use when the user says \"drive mode " +
            "on/off\", \"I'm driving now\", \"put on drive mode\" — typically before a " +
            "drive or at a stop where GPS hasn't picked them up yet. Pass on=true or " +
            "on=false, or omit for toggle.",
        inputSchema = mapOf("on" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val on = when (args["on"]?.lowercase()?.trim()) {
            null, "", "toggle", "switch" -> !currentManual()
            "true", "on", "yes", "1" -> true
            "false", "off", "no", "0" -> false
            else -> return ToolResult.Failure("Say on or off, or nothing to toggle.")
        }
        onSet(on)
        return ToolResult.Success(
            if (on) "Drive mode on. I'll watch the road with you." else "Drive mode off."
        )
    }
}

/** `car.trip` — the current trip or the last completed one. */
class CarTripTool(
    private val trip: () -> TripStats?,
    private val lastTrip: () -> TripStats?,
) : Tool {

    override val definition = ToolDefinition(
        id = "car.trip",
        displayName = "Trip stats",
        description = "Stats for the drive currently in progress (distance, time, " +
            "average and top speed). If no drive is running, reports the last " +
            "completed trip instead. Use for \"how far have we driven\", \"how long " +
            "have we been driving\", \"how fast did I go on the way home\".",
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val active = trip()
        if (active != null) {
            return ToolResult.Success(
                "This trip: ${tripSummary(active)}" +
                    if (active.avgSpeedMph >= 1f)
                        ", averaging %.0f mph".format(Locale.US, active.avgSpeedMph)
                    else ""
            )
        }
        val last = lastTrip()
        if (last != null) {
            return ToolResult.Success("Last trip: ${tripSummary(last)}.")
        }
        return ToolResult.Success("No trips recorded yet.")
    }
}

/** `car.eta` — distance and time to a coordinate or a saved place. */
class CarEtaTool(
    private val context: Context,
    private val savedPlaces: SavedPlaceStore,
    private val speedMph: () -> Float,
) : Tool {

    override val definition = ToolDefinition(
        id = "car.eta",
        displayName = "ETA to a place",
        description = "How far away a place is and roughly how long it will take to " +
            "get there at the current driving speed. Accepts either a saved place " +
            "name (\"home\", \"work\") or raw lat/lon. Use for \"how long until we " +
            "get to mom's\", \"are we close to home\", \"when will we arrive\".",
        inputSchema = mapOf("name" to "string", "lat" to "string", "lon" to "string"),
        requiredPermissions = setOf("android.permission.ACCESS_FINE_LOCATION"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val lat = args["lat"]?.toDoubleOrNull()
        val lon = args["lon"]?.toDoubleOrNull()
        val name = args["name"]?.trim()?.takeIf { it.isNotBlank() }

        val target = when {
            lat != null && lon != null -> lat to lon
            name != null -> savedPlaces.get(name.lowercase())
                ?.let { it.lat to it.lon }
                ?: return ToolResult.Failure("\"$name\" isn't a saved place yet.")
            else -> return ToolResult.Failure("Give me a saved place name, or lat and lon.")
        }

        val fix = CurrentLocation.last(context)
            ?: return ToolResult.Failure(
                if (CurrentLocation.hasPermission(context)) {
                    "No location fix yet — I don't know where we are."
                } else {
                    "Location permission isn't granted, so I can't work out an ETA."
                }
            )

        val miles = CarGeo.haversineMiles(fix.lat, fix.lon, target.first, target.second)
        val mins = CarGeo.etaMinutes(miles, speedMph())
        return ToolResult.Success(
            "%s is %.1f miles away — about %d minutes at your current speed."
                .format(Locale.US, name ?: "That place", miles, mins)
        )
    }
}

/**
 * `car.help` — text the emergency contact with where we are.
 *
 * MODERATE, not DESTRUCTIVE: sending a text is consequential but reversible and the
 * contact is the user's own configured number. No contact configured → honest failure
 * pointing at Settings rather than a silent no-op.
 */
class CarHelpTool(
    private val context: Context,
    private val emergencyContact: () -> String,
    private val trip: () -> TripStats?,
) : Tool {

    override val definition = ToolDefinition(
        id = "car.help",
        displayName = "Text emergency contact",
        description = "Compose a text to the user's configured emergency contact with " +
            "their current location and trip info, ready to send. Use when the user " +
            "asks for help, or is in trouble, or was in a crash: \"text mom\", " +
            "\"call for help\", \"send my location\". Requires the emergency contact " +
            "number to be set in Settings.",
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val number = emergencyContact().takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure(
                "No emergency contact is set — add one in Settings → Car assistant " +
                    "so I can text them in a jam."
            )

        val intent = buildEmergencyTextIntent(context, number, trip())

        return try {
            context.startActivity(intent)
            ToolResult.Success("Opened a text to $number with your location — press send.")
        } catch (_: ActivityNotFoundException) {
            ToolResult.Failure("No messaging app on this phone could handle that.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open the messaging app: ${e.message}")
        }
    }
}

/** `car.vehicle` — Bluetooth / projection awareness (honest CarPlay limits). */
class CarVehicleTool(
    private val identity: () -> com.example.meowmba.core.car.VehicleIdentity,
) : Tool {
    override val definition = ToolDefinition(
        id = "car.vehicle",
        displayName = "Vehicle identity",
        description = "What Meow knows about the connected car: Bluetooth name/address, " +
            "Android Auto hint, and whether CarPlay remote control is available " +
            "(it is not for third-party Android apps). Use for \"what car is connected\", " +
            "\"are we on CarPlay\", \"car Bluetooth\".",
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val id = identity()
        val body = com.example.meowmba.core.car.VehicleAwareness.summary(id) +
            "\n" + id.notes
        return ToolResult.Success(body)
    }
}

/** `car.obd` — live OBD-II snapshot from ELM327 when connected. */
class CarObdTool(
    private val reading: () -> com.example.meowmba.core.car.obd2.Obd2Reading,
    private val alerts: () -> List<com.example.meowmba.core.car.obd2.Obd2Alert>,
) : Tool {
    override val definition = ToolDefinition(
        id = "car.obd",
        displayName = "OBD-II diagnostics",
        description = "Read live OBD-II values from a paired ELM327 adapter: RPM, speed, " +
            "coolant, battery voltage, MIL, fuel level when available. Also reports " +
            "threshold alerts. Use for \"engine RPM\", \"coolant temp\", \"check engine\", " +
            "\"battery voltage\", \"OBD\".",
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 8_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val r = reading()
        if (!r.connected) {
            return ToolResult.Failure(
                "No OBD adapter connected. Pair an ELM327 over Bluetooth and enable " +
                    "OBD-II in Settings → Driving, or say \"connect OBD\".",
            )
        }
        val body = buildString {
            append("OBD connected")
            if (r.adapterAddress.isNotBlank()) append(" (${r.adapterAddress})")
            append(". ")
            if (r.hasRpm) append("RPM %.0f. ".format(Locale.US, r.rpm))
            if (r.hasSpeed) append("Speed %.0f mph. ".format(Locale.US, r.speedMph))
            if (r.hasCoolant) append("Coolant %.0f°C. ".format(Locale.US, r.coolantC))
            if (r.hasBattery) append("Battery %.1f V. ".format(Locale.US, r.batteryV))
            if (r.hasFuel) append("Fuel %.0f%%. ".format(Locale.US, r.fuelPercent))
            if (r.milOn) append("MIL / check-engine is ON. ")
            val a = alerts()
            if (a.isNotEmpty()) append("Alerts: " + a.joinToString("; ") { it.message } + ".")
            if (r.lastError.isNotBlank()) append(" Note: ${r.lastError}")
        }
        return ToolResult.Success(body.trim())
    }
}

/** `car.obd_connect` — connect to a saved or named ELM327 Bluetooth address. */
class CarObdConnectTool(
    private val connect: suspend (String) -> Result<Unit>,
    private val lastAddress: () -> String,
) : Tool {
    override val definition = ToolDefinition(
        id = "car.obd_connect",
        displayName = "Connect OBD-II adapter",
        description = "Connect to an ELM327 OBD-II adapter over classic Bluetooth SPP. " +
            "Args: address = MAC (optional if previously saved). Use for \"connect OBD\", " +
            "\"pair ELM327\".",
        inputSchema = mapOf("address" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 20_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val address = args["address"]?.trim().orEmpty().ifBlank { lastAddress() }
        if (address.isBlank()) {
            return ToolResult.Failure(
                "Tell me the ELM327 Bluetooth MAC address, or save one in Settings → Driving.",
            )
        }
        return connect(address).fold(
            onSuccess = { ToolResult.Success("Connected to OBD adapter $address.") },
            onFailure = { ToolResult.Failure("OBD connect failed: ${it.message}") },
        )
    }
}

/** `car.cameras` — list driver / dashcam sources and optional switch. */
class CarCamerasTool(
    private val sources: () -> List<com.example.meowmba.core.car.camera.CarCameraSource>,
    private val activeId: () -> String?,
    private val onSelect: (String) -> Unit,
) : Tool {
    override val definition = ToolDefinition(
        id = "car.cameras",
        displayName = "Car cameras",
        description = "List driver-facing and car-connected cameras (front, rear, USB UVC, " +
            "IP RTSP dashcam). Pass id= to switch the active preview. Use for " +
            "\"show driver camera\", \"switch to dashcam\", \"list car cameras\".",
        inputSchema = mapOf("id" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val list = sources()
        val want = args["id"]?.trim().orEmpty()
        if (want.isNotBlank()) {
            val match = list.firstOrNull { it.id.equals(want, ignoreCase = true) ||
                it.label.contains(want, ignoreCase = true) }
                ?: return ToolResult.Failure("No camera matching \"$want\".")
            onSelect(match.id)
            return ToolResult.Success("Active car camera: ${match.label}.")
        }
        return ToolResult.Success(
            com.example.meowmba.core.car.camera.CarCameraSources.describe(list, activeId()),
        )
    }
}
