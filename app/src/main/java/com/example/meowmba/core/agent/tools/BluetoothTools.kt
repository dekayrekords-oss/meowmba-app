package com.example.meowmba.core.agent.tools

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Device-specific services still need their own protocol. Bluetooth pairing
 * and audio profile connection remain in Android's supported Settings UI. */
class BluetoothDevicesTool(private val context: Context) : Tool {
    override val definition = ToolDefinition("phone.bluetooth_devices", "Bluetooth devices", "Read Bluetooth availability and paired device names. Paired does not mean connected. Does not scan nearby strangers or pair automatically.", emptyMap(), riskLevel = RiskLevel.SAFE)
    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return@withContext ToolResult.Failure("Allow Nearby devices for Meow in Android app permissions, then try again.")
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext ToolResult.Failure("This phone has no Bluetooth adapter.")
        runCatching {
            "Bluetooth ${if (adapter.isEnabled) "on" else "off"}. Paired devices (not proof of connection):\n" +
                adapter.bondedDevices.mapNotNull { it.name }.sorted().take(60).joinToString("\n").ifBlank { "None yet. Open Bluetooth setup to pair a device." }
        }.fold({ ToolResult.Success(it) }, { ToolResult.Failure("Bluetooth list unavailable: ${it.message}") })
    }
}

class BluetoothSetupTool(private val context: Context, private val start: (String) -> Boolean) : Tool {
    override val definition = ToolDefinition(
        id = "phone.bluetooth_setup", displayName = "Connect a Bluetooth device",
        description = "Open Android Bluetooth settings and navigate toward the named device. You approve system pairing/PIN prompts. A device is not connected until Android confirms it; special hardware functions need a compatible integration.",
        inputSchema = mapOf("name" to "exact Bluetooth device name, or blank to browse"),
        riskLevel = RiskLevel.MODERATE, confirmationPolicy = ConfirmationPolicy.ALWAYS,
    )
    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .getOrElse { return@withContext ToolResult.Failure("Cannot open Bluetooth settings: ${it.message}") }
        val name = args["name"].orEmpty().trim().take(120)
        if (name.isNotBlank() && com.example.meowmba.core.access.MeowAccessibilityService.isEnabled() &&
            start("In Android Bluetooth settings, find the device named '$name' and connect to it. Ask if names are ambiguous. Do not alter another device. Hand pairing/PIN/permission confirmation to the user; only report connected if Android visibly says Connected."))
            ToolResult.Success("Bluetooth setup opened for $name. Navigation started; pairing and connection are not yet verified.")
        else ToolResult.Success("Android Bluetooth settings opened. Select your device and confirm its pairing code. Connection is not yet verified.")
    }
}
