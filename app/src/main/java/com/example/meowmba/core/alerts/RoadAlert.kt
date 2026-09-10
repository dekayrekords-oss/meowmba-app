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
    )

    private val POLICE = listOf(
        "police", "cop", "cops", "officer", "speed trap", "speedtrap",
        "radar",
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
        // Navigation destinations and removed reports are not nearby hazards.
        if (Regex("\\b(?:no (?:police|cops|hazards)|police (?:station|department)|report (?:removed|dismissed)|no longer (?:there|reported))\\b")
                .containsMatchIn(blob)) return null
        val kind = when {
            CAMERA.any { containsPhrase(blob, it) } -> RoadAlert.Kind.CAMERA
            POLICE.any { containsPhrase(blob, it) } -> RoadAlert.Kind.POLICE
            HAZARD.any { containsPhrase(blob, it) } -> RoadAlert.Kind.HAZARD
            else -> return null
        }
        val source = when {
            packageName.contains("waze") -> "Waze"
            packageName.contains("maps") -> "Maps"
            else -> "Google"
        }
        val label = when (kind) {
            RoadAlert.Kind.POLICE -> "Police report"
            RoadAlert.Kind.CAMERA -> "Camera report"
            RoadAlert.Kind.HAZARD -> "Road hazard report"
        }
        val detail = (title ?: text ?: "").trim().take(80)
        return RoadAlert(
            kind = kind,
            summary = if (detail.isBlank() || detail.equals(label, ignoreCase = true)) {
                "$label ($source)"
            } else {
                "$label ($source) — $detail"
            },
            source = source,
        )
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("\\b" + Regex.escape(phrase) + "\\b").containsMatchIn(text)
}
