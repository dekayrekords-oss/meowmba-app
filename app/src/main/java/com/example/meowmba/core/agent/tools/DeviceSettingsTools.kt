package com.example.meowmba.core.agent.tools

import android.content.Context
import android.location.LocationManager
import android.media.AudioManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `phone.brightness` — get or set screen brightness.
 */
class BrightnessTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.brightness",
        displayName = "Screen brightness",
        description = "Get or set screen brightness. Args: level (0-255) or 'auto'. No args = report current.",
        inputSchema = mapOf("level" to "0-255 or 'auto'"),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val levelArg = args["level"]?.trim()?.lowercase()
        try {
            if (levelArg == null || levelArg.isEmpty()) {
                val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                val auto = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                val modeStr = if (auto == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "auto" else "manual"
                return@withContext ToolResult.Success("Brightness: $current/255 ($modeStr mode)")
            }
            if (levelArg == "auto") {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                return@withContext ToolResult.Success("Brightness set to auto.")
            }
            val level = levelArg.toIntOrNull()?.coerceIn(0, 255)
                ?: return@withContext ToolResult.Failure("Level must be 0-255 or 'auto'.")
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
            ToolResult.Success("Brightness set to $level/255.")
        } catch (e: SecurityException) {
            ToolResult.Failure("Need WRITE_SETTINGS permission. Go to Settings > Apps > Meow > Permissions.")
        } catch (e: Exception) {
            ToolResult.Failure("Brightness error: ${e.message}")
        }
    }
}

/**
 * `phone.volume` — get or set media/ring/alarm volume.
 */
class VolumeTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.volume",
        displayName = "Volume control",
        description = "Get or set volume. Args: stream (media|ring|alarm|notification), level (0-max). No args = report all.",
        inputSchema = mapOf("stream" to "media|ring|alarm|notification", "level" to "0 to max"),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@withContext ToolResult.Failure("No audio service.")
        val streamArg = args["stream"]?.trim()?.lowercase()
        val levelArg = args["level"]?.trim()

        if (streamArg == null && levelArg == null) {
            // Report all volumes
            val sb = StringBuilder("Volume levels:\n")
            sb.append("  Media: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}\n")
            sb.append("  Ring: ${am.getStreamVolume(AudioManager.STREAM_RING)}/${am.getStreamMaxVolume(AudioManager.STREAM_RING)}\n")
            sb.append("  Alarm: ${am.getStreamVolume(AudioManager.STREAM_ALARM)}/${am.getStreamMaxVolume(AudioManager.STREAM_ALARM)}\n")
            sb.append("  Notification: ${am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)}/${am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)}")
            return@withContext ToolResult.Success(sb.toString())
        }

        val stream = when (streamArg) {
            "media", "music" -> AudioManager.STREAM_MUSIC
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification", "notif" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }

        if (levelArg == null) {
            val current = am.getStreamVolume(stream)
            val max = am.getStreamMaxVolume(stream)
            return@withContext ToolResult.Success("${streamArg ?: "media"} volume: $current/$max")
        }

        val level = levelArg.toIntOrNull()
        if (level == null) return@withContext ToolResult.Failure("Level must be a number.")
        val max = am.getStreamMaxVolume(stream)
        am.setStreamVolume(stream, level.coerceIn(0, max), 0)
        ToolResult.Success("${streamArg ?: "media"} volume set to ${level.coerceIn(0, max)}/$max.")
    }
}

/**
 * `phone.location` — get current GPS coordinates and address.
 */
class LocationTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.location",
        displayName = "Current location",
        description = "Get the phone's current GPS coordinates. Returns lat/lng and approximate address.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext ToolResult.Failure("No location service.")
        try {
            val providers = lm.getProviders(true)
            var bestLocation: android.location.Location? = null
            for (provider in providers) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }
            if (bestLocation == null) {
                return@withContext ToolResult.Failure("No location available. Make sure GPS is enabled and location permission is granted.")
            }
            val lat = bestLocation.latitude
            val lng = bestLocation.longitude
            val acc = bestLocation.accuracy.toInt()
            // Try reverse geocoding
            val address = try {
                val geocoder = android.location.Geocoder(context)
                val results = geocoder.getFromLocation(lat, lng, 1)
                results?.firstOrNull()?.let { addr ->
                    (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                }
            } catch (_: Exception) { null }
            val sb = StringBuilder("Location: $lat, $lng (accuracy: ${acc}m)")
            if (address != null) sb.append("\nAddress: $address")
            ToolResult.Success(sb.toString())
        } catch (e: SecurityException) {
            ToolResult.Failure("Location permission not granted. Enable in Settings > Apps > Meow > Permissions.")
        } catch (e: Exception) {
            ToolResult.Failure("Location error: ${e.message}")
        }
    }
}
