package com.example.meowmba.core.glasses

/**
 * Glasses capture modes.
 *
 * **Video** — continuous stream at the given FPS (SDK StreamConfiguration).
 * **Photo** — low-rate stream (1 fps from hardware) with app-side throttle to
 * the requested interval (1s … 5 min).
 *
 * Hardware note: HIGH@15+ is often rejected on Ray-Ban Meta; high-FPS video
 * uses MEDIUM quality. Bridge falls back to VIDEO_15 if a tier is rejected.
 */
enum class CaptureKind { VIDEO, PHOTO }

enum class CaptureMode(
    val label: String,
    val kind: CaptureKind,
    /** Stream FPS sent to MWDAT StreamConfiguration. */
    val fps: Int,
    /** Maps to SDK VideoQuality: HIGH / MEDIUM / LOW. */
    val quality: String,
    val description: String,
    /**
     * Photo modes: min ms between decoded frames pushed into the app.
     * Video modes: 0 = pass every stream frame (ingest still sampled separately).
     */
    val photoIntervalMs: Long = 0L,
    /**
     * How often CaptureService ingests latest JPEG into memory (ms).
     * Video modes use a sane sample rate so we don't store 30 memories/sec.
     */
    val ingestIntervalMs: Long,
) {
    // ── Video ────────────────────────────────────────────────────────────
    VIDEO_30(
        label = "Video 30fps",
        kind = CaptureKind.VIDEO,
        fps = 30,
        quality = "MEDIUM",
        description = "Max stream rate — heavy battery / heat",
        photoIntervalMs = 0L,
        ingestIntervalMs = 1_000L,
    ),
    VIDEO_15(
        label = "Video 15fps",
        kind = CaptureKind.VIDEO,
        fps = 15,
        quality = "MEDIUM",
        description = "Real-time AI — strong default video",
        photoIntervalMs = 0L,
        ingestIntervalMs = 1_000L,
    ),
    VIDEO_1(
        label = "Video 1fps",
        kind = CaptureKind.VIDEO,
        fps = 1,
        quality = "LOW",
        description = "Continuous 1 fps stream — lightest video",
        photoIntervalMs = 0L,
        ingestIntervalMs = 1_000L,
    ),

    // ── Photo ────────────────────────────────────────────────────────────
    PHOTO_1S(
        label = "Photo 1/s",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every second",
        photoIntervalMs = 1_000L,
        ingestIntervalMs = 1_000L,
    ),
    PHOTO_2_5S(
        label = "Photo 2.5s",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every 2.5 seconds",
        photoIntervalMs = 2_500L,
        ingestIntervalMs = 2_500L,
    ),
    PHOTO_5S(
        label = "Photo 5s",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every 5 seconds",
        photoIntervalMs = 5_000L,
        ingestIntervalMs = 5_000L,
    ),
    PHOTO_30S(
        label = "Photo 30s",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every 30 seconds",
        photoIntervalMs = 30_000L,
        ingestIntervalMs = 30_000L,
    ),
    PHOTO_1M(
        label = "Photo 1m",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every minute",
        photoIntervalMs = 60_000L,
        ingestIntervalMs = 60_000L,
    ),
    PHOTO_5M(
        label = "Photo 5m",
        kind = CaptureKind.PHOTO,
        fps = 1,
        quality = "LOW",
        description = "1 photo every 5 minutes",
        photoIntervalMs = 300_000L,
        ingestIntervalMs = 300_000L,
    );

    val isPhotoOnly: Boolean get() = kind == CaptureKind.PHOTO
    val isVideo: Boolean get() = kind == CaptureKind.VIDEO

    /** Bridge throttle between decoded frames (ms). 0 = every stream frame. */
    val uploadIntervalMs: Long
        get() = if (isPhotoOnly) photoIntervalMs else 0L

    /** Seconds (rounded up) for legacy settings / UI. */
    val intervalSec: Int
        get() = ((ingestIntervalMs + 999) / 1000).toInt().coerceAtLeast(1)

    companion object {
        val VIEWING = VIDEO_15
        /** Responsive default so first capture is noticeable (was PHOTO_1M — felt “broken”). */
        val DEFAULT_BACKGROUND = PHOTO_5S

        fun videoModes(): List<CaptureMode> = entries.filter { it.isVideo }
        fun photoModes(): List<CaptureMode> = entries.filter { it.isPhotoOnly }

        fun parse(name: String): CaptureMode? {
            val t = name.trim().lowercase().replace('_', ' ').replace('-', ' ')
            return entries.find {
                it.name.equals(name, true) ||
                    it.label.equals(name, true) ||
                    it.label.lowercase() == t ||
                    it.name.lowercase().replace('_', ' ') == t
            } ?: when {
                t.contains("30") && t.contains("video") || t == "30fps" || t == "30 fps" -> VIDEO_30
                t.contains("15") && (t.contains("video") || t.contains("fps")) -> VIDEO_15
                t.contains("video") && (t.contains("1fps") || t.contains("1 fps") || t == "1fps") -> VIDEO_1
                t.contains("2.5") || t.contains("2,5") || t.contains("two point five") -> PHOTO_2_5S
                t.contains("5 min") || t.contains("5m") || t.contains("five minute") -> PHOTO_5M
                t.contains("30 sec") || t.contains("30s") || t == "photo 30s" -> PHOTO_30S
                (t.contains("1 min") || t.contains("1m") || t.contains("minute")) && t.contains("photo") -> PHOTO_1M
                t.contains("photo") && (t.contains("5s") || t.contains("5 sec")) -> PHOTO_5S
                t.contains("photo") && (t.contains("1/s") || t.contains("1 s") || t.contains("every second") || t.contains("1s")) -> PHOTO_1S
                t.contains("eco") -> PHOTO_1M
                t.contains("action") -> VIDEO_15
                t.contains("live") -> VIDEO_15
                t.contains("balanced") -> PHOTO_30S
                t.contains("ultra") -> PHOTO_5S
                else -> null
            }
        }
    }
}
