package com.example.meowmba

import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.diagnostics.LoopTelemetry
import com.example.meowmba.ui.companion.Fly3DPhoneTelemetry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in actual-app measurement that survives unplugging ADB. No battery simulation,
 * preference changes, sensor activation, thermal overrides or hardware commands. */
class PhoneEnduranceTest {
    @Test fun recordActualForegroundAvatar() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit endurance invocation only", args.getString("runEndurance") == "true")
        val seconds = args.getString("seconds")?.toIntOrNull()?.coerceIn(30, 1800) ?: 600
        val waitSeconds = args.getString("awaitUnplugSeconds")?.toIntOrNull()?.coerceIn(0, 600) ?: 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val battery = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val output = File(context.getExternalFilesDir(null), "phone-endurance-${System.currentTimeMillis()}.jsonl")
        fun append(row: JSONObject) { output.appendText(row.toString() + "\n") }
        fun status() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun plugged() = status()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val version = context.packageManager.getPackageInfo(context.packageName, 0)
        append(JSONObject().put("event", "header").put("schema", 1).put("versionCode", version.longVersionCode)
            .put("durationSeconds", seconds).put("awaitUnplugSeconds", waitSeconds)
            .put("label", args.getString("label") ?: "actual foreground avatar")
            .put("scope", "Whole-phone battery and app-process CPU; app+instrumentation cost; no all-day inference"))
        // Authentication stays with the owner. Waiting here also survives a flaky USB cable;
        // launching ActivityScenario behind keyguard otherwise fails before sampling starts.
        if (keyguard.isKeyguardLocked || !power.isInteractive) {
            append(JSONObject().put("event", "waiting_for_owner_unlock"))
            val unlockDeadline = SystemClock.elapsedRealtime() + 600_000L
            while ((keyguard.isKeyguardLocked || !power.isInteractive) && SystemClock.elapsedRealtime() < unlockDeadline)
                SystemClock.sleep(1000)
            if (keyguard.isKeyguardLocked || !power.isInteractive) {
                append(JSONObject().put("event", "aborted").put("reason", "Owner unlock timed out"))
                assertTrue("Phone remained locked; measurement was not started", false)
            }
        }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        var originallyKeptAwake = false
        try {
            scenario.onActivity {
                originallyKeptAwake = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SystemClock.sleep(4000)
            if (waitSeconds > 0) {
                append(JSONObject().put("event", "waiting_for_unplug"))
                val deadline = SystemClock.elapsedRealtime() + waitSeconds * 1000L
                while (plugged() != 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(1000)
                if (plugged() != 0) {
                    append(JSONObject().put("event", "aborted").put("reason", "No physical unplug detected"))
                    assertTrue("Reconnect and rerun: no unplug detected before timeout", false)
                }
            }
            val started = SystemClock.elapsedRealtime()
            var count = 0
            var foregroundCount = 0
            var unpluggedCount = 0
            var severeCount = 0
            val initialPresented = Fly3DPhoneTelemetry.presented.get()
            val initialCpu = Process.getElapsedCpuTime()
            val initialCharge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            append(JSONObject().put("event", "measurement_started").put("elapsedRealtimeMs", started))
            while (true) {
                val now = SystemClock.elapsedRealtime()
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() && !it.isFinishing }
                val foreground = focused && power.isInteractive && !keyguard.isKeyguardLocked
                val b = status()
                val plug = b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                val thermal = power.currentThermalStatus
                count++
                if (foreground) foregroundCount++
                if (plug == 0) unpluggedCount++
                if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) severeCount++
                append(JSONObject().put("event", "sample").put("elapsedMs", now - started)
                    .put("foreground", foreground).put("plugged", plug).put("thermalStatus", thermal)
                    .put("batteryTenthsC", b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
                    .put("batteryLevel", b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1)
                    .put("chargeMicroAh", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
                    .put("processCpuMs", Process.getElapsedCpuTime()).put("submittedFrames", Fly3DPhoneTelemetry.submitted.get())
                    .put("presentedFrames", Fly3DPhoneTelemetry.presented.get())
                    .put("drawWallNanos", Fly3DPhoneTelemetry.drawWallNanos.get())
                    .put("renderPixels", Fly3DPhoneTelemetry.renderPixels).put("stageMode", Fly3DPhoneTelemetry.stageMode)
                    .put("room", Fly3DPhoneTelemetry.room).put("stageActive", Fly3DPhoneTelemetry.active))
                if (now - started >= seconds * 1000L) break
                SystemClock.sleep(minOf(10_000L, seconds * 1000L - (now - started)))
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            val frames = Fly3DPhoneTelemetry.presented.get() - initialPresented
            val finalCharge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val loops = JSONArray()
            LoopTelemetry.snapshot().forEach { loops.put(JSONObject().put("feature", it.feature)
                .put("iterations", it.iterations).put("busyMs", it.busyMs).put("windowMs", it.windowMs)) }
            val validCharge = initialCharge > 0 && finalCharge > 0 && unpluggedCount == count
            val sustained = foregroundCount >= count * .95 && severeCount == 0 && frames >= seconds * 5
            append(JSONObject().put("event", "complete").put("elapsedMs", elapsed).put("samples", count)
                .put("foregroundSamples", foregroundCount).put("unpluggedSamples", unpluggedCount)
                .put("severeThermalSamples", severeCount).put("presentedFrames", frames)
                .put("averagePresentedFps", frames * 1000.0 / elapsed)
                .put("cpuPercentOfOneCore", (Process.getElapsedCpuTime() - initialCpu) * 100.0 / elapsed)
                .put("chargeUsedMicroAh", if (validCharge) initialCharge - finalCharge else JSONObject.NULL)
                .put("batteryMeasurementValid", validCharge).put("sustainedForegroundMotionGate", sustained)
                .put("loops", loops))
            assertTrue("Measurement preserved but sustained foreground-motion gate failed: $output", sustained)
            if (waitSeconds > 0) assertTrue("Unplugged measurement interrupted or charge counter unavailable", validCharge)
        } finally {
            scenario.onActivity { if (!originallyKeptAwake) it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            scenario.close()
        }
    }
}
