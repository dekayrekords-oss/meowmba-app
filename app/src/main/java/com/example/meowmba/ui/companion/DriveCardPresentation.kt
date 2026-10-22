package com.example.meowmba.ui.companion

import java.util.Locale

/** Source availability is informational; it never asserts live or complete traffic coverage. */
internal fun driveRoadSourceSummary(status: String?): String {
    val text = status?.trim()?.lowercase(Locale.US).orEmpty()
    return when {
        text.isEmpty() -> "Drive mode"
        text.startsWith("road reports paused:") && "location" in text -> "Waiting for location"
        "unavailable" in text -> "Road data limited"
        "waiting for fresh data" in text -> "Refreshing road data"
        "checking" in text -> "Checking road data"
        else -> "Road sources"
    }
}

/** Current integration appends actual OBD alerts after this marker. Do not collapse those alerts. */
internal fun driveVehicleWarning(obdLine: String?): String? {
    val line = obdLine ?: return null
    val marker = line.indexOf('⚠')
    return line.substring(marker.takeIf { it >= 0 } ?: return null).trim()
}

internal fun driveSpeedLabel(speedMph: Float): String =
    if (speedMph.isFinite() && speedMph >= 0f) "%.0f mph".format(Locale.US, speedMph) else "— mph"
