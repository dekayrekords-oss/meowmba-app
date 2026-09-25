package com.example.meowmba.ui.companion

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.robot.cozmo.CozmoController
import com.example.meowmba.core.robot.cozmo.CozmoProtocol
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CozmoScreen(controller: CozmoController, onLooi: () -> Unit, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val camera by controller.image.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    DisposableEffect(controller) {
        onDispose { controller.stop(); controller.setCamera(false) }
    }
    val action: (String) -> Unit = { result = controller.action(it) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cozmo", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("Cozmo · Wi-Fi") })
                FilterChip(selected = false, onClick = onLooi, label = { Text("LOOI · Bluetooth") })
            }
            Text("Meow's pocket robot", style = MaterialTheme.typography.titleMedium)
            Text("Wake Cozmo on his charger. Join his Wi-Fi using the password on his face, close the official Cozmo app, then connect here.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Wi-Fi settings") }
                Button(enabled = !state.connecting && !state.connected, onClick = {
                    scope.launch { withContext(Dispatchers.IO) { controller.connect(context.applicationContext) } }
                }) { Text(if (state.connecting) "Connecting…" else "Connect") }
            }
            Text(state.message, style = MaterialTheme.typography.titleSmall)
            if (state.firmware.isNotEmpty()) Text("Firmware ${state.firmware} · ${state.serial}", style = MaterialTheme.typography.bodySmall)
            Text("Experimental integration: bench testing on your Cozmo is still required. Cozmo's Wi-Fi has no internet; use Meow's on-device model or cellular for chat.",
                style = MaterialTheme.typography.bodySmall)
            if (result.isNotBlank()) Text(result, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.stop(); result = "Stop requested; check Cozmo has stopped" }, enabled = state.connected || state.connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("STOP") }
                OutlinedButton(onClick = controller::disconnect, enabled = state.connected || state.connecting) { Text("Disconnect") }
            }
            state.telemetry?.let { t ->
                Text("Battery %.2f V · Head %.0f° · Lift %.0f mm".format(t.batteryVolts, t.headDegrees, t.liftMm))
                val notes = listOfNotNull("Charging".takeIf { t.charging }, "On charger".takeIf { t.onCharger },
                    "Picked up".takeIf { t.pickedUp }, "Edge detected".takeIf { t.cliff }, "Falling".takeIf { t.falling })
                if (notes.isNotEmpty()) Text(notes.joinToString(" · "))
                Text("Position %.0f, %.0f mm · Wheels %.0f / %.0f mm/s".format(t.x, t.y, t.leftSpeed, t.rightSpeed),
                    style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Head and lift", style = MaterialTheme.typography.titleMedium)
            CozmoButtons(listOf("look_up" to "Look up", "look_center" to "Center", "look_down" to "Look down"), state.connected, action)
            CozmoButtons(listOf("lift_up" to "Lift up", "lift_down" to "Lift down"), state.connected, action)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Short floor moves", modifier = Modifier.weight(1f))
                Switch(state.wheelsEnabled, controller::setWheels, enabled = state.connected)
            }
            Text("Use on a clear floor. Each tap moves for at most 0.35 seconds. Stop and disconnect disable driving. Do not rely on edge sensors on a table.",
                style = MaterialTheme.typography.bodySmall)
            CozmoButtons(listOf("forward" to "Forward", "back" to "Back", "left" to "Left", "right" to "Right"), state.connected && state.wheelsEnabled, action)
            HorizontalDivider()
            Text("Personality", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("React to Meow and blink", modifier = Modifier.weight(1f))
                Switch(state.companionEyes, controller::setCompanion, enabled = state.connected)
            }
            CozmoProtocol.EXPRESSIONS.chunked(3).forEach { names ->
                CozmoButtons(names.map { it to it.replaceFirstChar(Char::uppercaseChar) }, state.connected, action)
            }
            Text("Backpack lights", style = MaterialTheme.typography.titleMedium)
            CozmoProtocol.COLORS.keys.toList().chunked(3).forEach { names ->
                CozmoButtons(names.map { "light_$it" to it.replaceFirstChar(Char::uppercaseChar) }, state.connected, action)
            }
            CozmoButtons(listOf("headlight_on" to "Camera light on", "headlight_off" to "Light off"), state.connected, action)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cozmo camera", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(state.cameraEnabled, controller::setCamera, enabled = state.connected)
            }
            Text("Local grayscale preview, about 3 frames/sec. Turns off when you leave this screen. Images are not stored or sent to an AI service.",
                style = MaterialTheme.typography.bodySmall)
            if (state.cameraEnabled) {
                camera?.let { Image(it.asImageBitmap(), "Cozmo live camera", Modifier.fillMaxWidth().aspectRatio(4f / 3f)) }
                    ?: Text("Waiting for a camera frame…")
            }
            Text("Cubes", style = MaterialTheme.typography.titleMedium)
            if (state.cubes.isEmpty()) Text("Tap a nearby powered cube to wake it. Discovered cubes appear here.")
            state.cubes.forEach { cube ->
                OutlinedButton(enabled = state.connected && cube.objectId == null,
                    onClick = { result = controller.connectCube(cube.factoryId) }) {
                    Text("Cube ${cube.type} · ${if (cube.objectId != null) "Connected" else "Connect"} · ${cube.rssi} dBm")
                }
            }
            if (state.lastEvent.isNotBlank()) Text(state.lastEvent)
            Text("Voice examples: ‘Cozmo look up’, ‘Cozmo lift up’, ‘Cozmo happy’, ‘Cozmo lights blue’, ‘Cozmo status’, ‘Cozmo stop’. Meow hears and speaks through your phone.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CozmoButtons(actions: List<Pair<String, String>>, enabled: Boolean, onAction: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { (action, label) ->
            OutlinedButton(onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) { Text(label) }
        }
    }
}
