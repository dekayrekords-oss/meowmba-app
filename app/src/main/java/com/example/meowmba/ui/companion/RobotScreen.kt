package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.robot.RobotBaseControlPolicy

/**
 * LOOI body console, opened from the Robot tile. Every body option lives here
 * regardless of whether a physical base is linked — the old homescreen hid
 * this whole section until a "physical" status string appeared, which made the
 * controls look deleted. Status + connection settings + base picker all in
 * one place, honest about what the [robotStatus] string reports.
 */
@Composable
fun RobotScreen(
    robotStatus: String = "simulated (no body)",
    robotBodyEnabled: Boolean = false,
    robotConnected: Boolean = false,
    robotUseBluetooth: Boolean = true,
    robotBtAddress: String = "",
    robotHost: String = "",
    robotPort: String = "",
    robotScanning: Boolean = false,
    robotScanResults: List<Pair<String, String>> = emptyList(),
    onRobotBodyChange: (Boolean) -> Unit = {},
    onRobotUseBluetoothChange: (Boolean) -> Unit = {},
    onRobotBtAddressChange: (String) -> Unit = {},
    onRobotHostChange: (String) -> Unit = {},
    onRobotPortChange: (String) -> Unit = {},
    onRobotScan: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onStop: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val controlAvailable = RobotBaseControlPolicy.CONTROL_AVAILABLE
    val attached = robotConnected && controlAvailable
    val statusColor = when {
        attached -> MaterialTheme.colorScheme.primary
        robotStatus.contains("offline", ignoreCase = true) -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Robot base",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(
                "Physical control is temporarily unavailable. On-screen attention and avatar animation remain available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Connection status, always visible — never gated on the string.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (attached) Icons.Default.CheckCircle else Icons.Default.Android,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(
                            if (!controlAvailable) RobotBaseControlPolicy.UNAVAILABLE_MESSAGE
                            else robotStatus.replace("LOOI", "base", ignoreCase = true),
                            color = if (attached) MaterialTheme.colorScheme.onSurface else statusColor,
                            fontSize = 14.sp,
                        )
                    }
                    OutlinedButton(onClick = onRefresh, enabled = controlAvailable) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (!controlAvailable) "Unavailable" else if (attached) "Connected" else "Connect")
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStop, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Disable control")
                }
                OutlinedButton(onClick = { onRobotBodyChange(false) }, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
            }
            Text(
                "Disconnect sends no motor command because the stop protocol is not verified. " +
                    "It cannot guarantee that a moving base stops. Keep the base powered off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            OptionRow(
                title = "Connect robot base",
                subtitle = "Locked off until the motor and stop protocols are verified for your base.",
            ) {
                Switch(checked = robotBodyEnabled && controlAvailable, onCheckedChange = onRobotBodyChange,
                    enabled = controlAvailable)
            }
            OptionRow(
                title = "Connect over Bluetooth",
                subtitle = "Connection and scanning are disabled during the safety lockout.",
            ) {
                Switch(checked = robotUseBluetooth, onCheckedChange = onRobotUseBluetoothChange,
                    enabled = controlAvailable)
            }

            if (robotUseBluetooth) {
                OutlinedTextField(
                    value = robotBtAddress,
                    onValueChange = onRobotBtAddressChange,
                    label = { Text("Paired device MAC (e.g. 00:11:22:33:44:55)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onRobotScan, enabled = controlAvailable && !robotScanning) {
                    Text(if (robotScanning) "Scanning for bases…" else "Find base (scan)")
                }
                if (robotScanResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap your robot base to use its address:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    robotScanResults.forEach { (name, address) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRobotBtAddressChange(address) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (address == robotBtAddress) {
                                Text("selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (robotScanning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Scanning…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                OutlinedTextField(
                    value = robotHost,
                    onValueChange = onRobotHostChange,
                    label = { Text("Base host (IP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = robotPort,
                    onValueChange = onRobotPortChange,
                    label = { Text("Base port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
