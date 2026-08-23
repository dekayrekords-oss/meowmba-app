package com.example.meowmba.core.alerts

/**
 * A nearby road hazard. Comes from the GPS live-map feed, or (as a bonus)
 * from a Waze / Maps notification if the user granted listener access.
 */
data class RoadAlert(
    val kind: Kind,
    val summary: String,
    val source: String,
    val atMs: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceMeters: Int? = null,
) {
    enum class Kind { POLICE, CAMERA, HAZARD }
}

object RoadAlerts {

    private val SOURCE_PACKAGES = setOf(
        "com.waze",
        "com.google.android.apps.maps",
        "com.google.android.apps.mapslite",
        "com.google.android.googlequicksearchbox",
    )

    private val POLICE = listOf(
        "police", "cop", "cops", "officer", "speed trap", "speedtrap",
        "radar", "checkpoint", "k-9", "k9",
    )
    private val CAMERA = listOf(
        "speed camera", "speed cam", "red light camera", "traffic camera",
    )
    private val HAZARD = listOf(
        "crash", "accident", "hazard", "object on road", "vehicle stopped",
    )

    fun fromNotification(packageName: String, title: String?, text: String?): RoadAlert? {
        if (packageName !in SOURCE_PACKAGES) return null
        val blob = listOfNotNull(title, text).joinToString(" ").lowercase()
        if (blob.isBlank()) return null
        val kind = when {
            POLICE.any { it in blob } -> RoadAlert.Kind.POLICE
            CAMERA.any { it in blob } -> RoadAlert.Kind.CAMERA
            HAZARD.any { it in blob } -> RoadAlert.Kind.HAZARD
            else -> return null
        }
        val source = when {
            packageName.contains("waze") -> "Waze"
            packageName.contains("maps") -> "Maps"
            else -> "Google"
        }
        val label = when (kind) {
            RoadAlert.Kind.POLICE -> "Police nearby"
            RoadAlert.Kind.CAMERA -> "Speed camera nearby"
            RoadAlert.Kind.HAZARD -> "Road hazard nearby"
        }
        val detail = (title ?: text ?: "").trim().take(80)
        return RoadAlert(
            kind = kind,
            summary = if (detail.isBlank() || detail.equals(label, ignoreCase = true)) {
                "$label ($source)"
            } else {
                "$label — $detail"
            },
            source = source,
        )
    }
}
