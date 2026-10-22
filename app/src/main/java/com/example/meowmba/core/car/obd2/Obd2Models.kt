package com.example.meowmba.core.car.obd2

/**
 * Snapshot of common OBD-II PIDs polled from an ELM327 adapter.
 * Values are NaN when not yet received.
 */
data class Obd2Reading(
    val rpm: Float = Float.NaN,
    val speedMph: Float = Float.NaN,
    val coolantC: Float = Float.NaN,
    val batteryV: Float = Float.NaN,
    val milOn: Boolean = false,
    val fuelPercent: Float = Float.NaN,
    val connected: Boolean = false,
    val adapterAddress: String = "",
    val lastError: String = "",
    val updatedAtMs: Long = 0L,
) {
    val hasRpm get() = rpm.isFinite()
    val hasSpeed get() = speedMph.isFinite()
    val hasCoolant get() = coolantC.isFinite()
    val hasBattery get() = batteryV.isFinite()
    val hasFuel get() = fuelPercent.isFinite()
}

data class Obd2AlertThresholds(
    val coolantHighC: Float = 105f,
    val batteryLowV: Float = 11.8f,
    val rpmHigh: Float = 5500f,
)

enum class Obd2AlertKind { COOLANT_HIGH, BATTERY_LOW, RPM_HIGH, MIL }

data class Obd2Alert(
    val kind: Obd2AlertKind,
    val message: String,
    val value: Float = Float.NaN,
)

object Obd2Alerts {
    fun evaluate(r: Obd2Reading, t: Obd2AlertThresholds = Obd2AlertThresholds()): List<Obd2Alert> {
        if (!r.connected) return emptyList()
        val out = mutableListOf<Obd2Alert>()
        if (r.hasCoolant && r.coolantC >= t.coolantHighC) {
            out += Obd2Alert(Obd2AlertKind.COOLANT_HIGH,
                "Coolant ${"%.0f".format(r.coolantC)}°C — high", r.coolantC)
        }
        if (r.hasBattery && r.batteryV > 0f && r.batteryV <= t.batteryLowV) {
            out += Obd2Alert(Obd2AlertKind.BATTERY_LOW,
                "Battery ${"%.1f".format(r.batteryV)} V — low", r.batteryV)
        }
        if (r.hasRpm && r.rpm >= t.rpmHigh) {
            out += Obd2Alert(Obd2AlertKind.RPM_HIGH,
                "RPM ${"%.0f".format(r.rpm)} — high", r.rpm)
        }
        if (r.milOn) {
            out += Obd2Alert(Obd2AlertKind.MIL, "Check-engine light (MIL) is on")
        }
        return out
    }
}

/**
 * Parse ELM327 hex PID responses. Tolerant of spaces, echo, and "SEARCHING…".
 */
object Obd2PidParser {
    fun parseRpm(raw: String): Float? {
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x0C) ?: return null
        if (bytes.size < 2) return null
        return ((bytes[0] * 256) + bytes[1]) / 4f
    }

    fun parseSpeedKph(raw: String): Float? {
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x0D) ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0].toFloat()
    }

    fun parseSpeedMph(raw: String): Float? = parseSpeedKph(raw)?.times(0.621371f)

    fun parseCoolantC(raw: String): Float? {
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x05) ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0] - 40f
    }

    fun parseBatteryV(raw: String): Float? {
        // Mode 01 PID 42 — control module voltage
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x42) ?: return null
        if (bytes.size < 2) return null
        return ((bytes[0] * 256) + bytes[1]) / 1000f
    }

    fun parseMil(raw: String): Boolean? {
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x01) ?: return null
        if (bytes.isEmpty()) return null
        return (bytes[0] and 0x80) != 0
    }

    fun parseFuelPercent(raw: String): Float? {
        val bytes = dataBytes(raw, mode = 0x41, pid = 0x2F) ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0] * 100f / 255f
    }

    /** Extract payload bytes after mode+pid echo (e.g. "41 0C 1A F8"). */
    fun dataBytes(raw: String, mode: Int, pid: Int): List<Int>? {
        val cleaned = raw.uppercase()
            .replace("SEARCHING...", "")
            .replace("SEARCHING…", "")
            .replace("NODATA", "")
            .replace("NO DATA", "")
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        if (cleaned.isBlank() || cleaned.contains("UNABLE") || cleaned.contains("ERROR")) return null
        val hex = cleaned.split(Regex("\\s+")).filter { it.matches(Regex("[0-9A-F]{2}")) }
        if (hex.size < 2) return null
        val ints = hex.mapNotNull { it.toIntOrNull(16) }
        val modeIdx = ints.indexOfFirst { it == mode }
        if (modeIdx < 0 || modeIdx + 1 >= ints.size) return null
        if (ints[modeIdx + 1] != pid) return null
        return ints.drop(modeIdx + 2)
    }
}

/** Map OBD values into soft Lumen reaction cues (0..1). */
object Obd2LumenCues {
    fun excitementFromRpm(rpm: Float, redline: Float = 6000f): Float {
        if (!rpm.isFinite() || rpm <= 0f) return 0f
        return ((rpm - 1200f) / (redline - 1200f)).coerceIn(0f, 1f)
    }

    fun alertFromCoolant(c: Float, high: Float = 105f): Float {
        if (!c.isFinite()) return 0f
        return ((c - (high - 15f)) / 15f).coerceIn(0f, 1f)
    }
}
