package com.example.meowmba.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.meowmba.BuildConfig
import java.io.File
import kotlinx.serialization.json.*

/** Allowlisted operational metadata only. Never reads messages, preferences, logs,
 * exception descriptions, endpoints, recordings, account names or device identifiers. */
object SupportDiagnostics {
    data class Exit(val timestamp: Long, val reason: Int, val status: Int, val importance: Int)
    data class Snapshot(
        val versionName: String, val versionCode: Long, val androidApi: Int,
        val memoryClassMb: Int?, val lowMemory: Boolean?, val thermalStatus: Int?,
        val micEnabled: Boolean, val cameraEnabled: Boolean,
        val wakeWordEnabled: Boolean, val backgroundListeningEnabled: Boolean,
        val exits: List<Exit>, val exitHistoryAvailable: Boolean,
    )

    fun render(snapshot: Snapshot): String = Json { prettyPrint = true }.encodeToString(
        JsonObject.serializer(), buildJsonObject {
            put("schema", 1)
            put("scope", "Build, runtime health, permission-mode switches and recent process exit codes only")
            put("version", snapshot.versionName)
            put("versionCode", snapshot.versionCode)
            put("androidApi", snapshot.androidApi)
            snapshot.memoryClassMb?.let { put("memoryClassMb", it) }
            snapshot.lowMemory?.let { put("systemLowMemory", it) }
            snapshot.thermalStatus?.let { put("thermalStatus", it) }
            putJsonObject("modes") {
                put("microphone", snapshot.micEnabled)
                put("camera", snapshot.cameraEnabled)
                put("wakeWord", snapshot.wakeWordEnabled)
                put("backgroundListening", snapshot.backgroundListeningEnabled)
            }
            put("exitHistoryAvailable", snapshot.exitHistoryAvailable)
            putJsonArray("recentProcessExits") {
                snapshot.exits.sortedByDescending { it.timestamp }.take(5).forEach { exit ->
                    add(buildJsonObject {
                        put("timestamp", exit.timestamp)
                        put("reasonCode", exit.reason)
                        put("status", exit.status)
                        put("importance", exit.importance)
                    })
                }
            }
        }
    )

    fun capture(context: Context, mic: Boolean, camera: Boolean, wake: Boolean, background: Boolean): Snapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = runCatching { ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) } }.getOrNull()
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).map {
                Exit(it.timestamp, it.reason, it.status, it.importance)
            }
        }
        return Snapshot(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT,
            runCatching { manager.memoryClass }.getOrNull(), memory?.lowMemory,
            runCatching { context.getSystemService(PowerManager::class.java).currentThermalStatus }.getOrNull(),
            mic, camera, wake, background, exits.getOrDefault(emptyList()), exits.isSuccess)
    }

    /** Only this explicitly generated report is shared via a temporary URI grant. */
    fun write(context: Context, snapshot: Snapshot): File {
        val folder = File(context.cacheDir, "support-diagnostics")
        check(folder.isDirectory || folder.mkdirs()) { "Diagnostic report folder unavailable" }
        return File.createTempFile("meow-diagnostics-", ".json", folder).also {
            it.writeText(render(snapshot), Charsets.UTF_8)
        }
    }
}
