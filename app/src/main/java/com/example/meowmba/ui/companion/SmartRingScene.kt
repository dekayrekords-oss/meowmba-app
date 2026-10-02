package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.ring.*

@Composable
internal fun SmartRingScene(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val driver = remember(context) { AndroidSmartRing.get(context) }
    RingSettingsPage("Smart Ring", onBack) {
        val state by driver.state.collectAsState()
        DisposableEffect(driver) { onDispose { driver.setTesting(false); driver.stopSensorTest() } }
        Text("R08 / QRing", style = MaterialTheme.typography.titleMedium)
        Text(state.status)
        if (state.selectedName.isNotEmpty()) Text("Selected: ${state.selectedName}")
        Text("Disconnect the ring in QRing first, keep it awake and nearby, then scan and select your ring. Accept Android's pairing prompt. Your firmware and tap support still need a device test.", style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = driver::scan, enabled = !state.scanning, modifier = Modifier.fillMaxWidth()) { Text(if (state.scanning) "Scanning…" else "Scan / reconnect") }
            OutlinedButton(onClick = driver::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
        }
        state.devices.forEach { device ->
            OutlinedButton(onClick = { driver.connect(device.address) }, modifier = Modifier.fillMaxWidth()) {
                Text("${device.name} · ${device.address}")
            }
        }
        OutlinedButton(onClick = driver::openBluetooth) { Text("Bluetooth settings") }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(if (state.accessibility) "Accessibility service connected" else "Accessibility service is off")
        Text("Enable Meowmba Ring Controls to receive ring keys and scroll in allowed apps. Input and screen actions stay on this phone. Meowmba does not record screen contents.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = driver::openAccessibility) { Text("Open Accessibility settings") }
        RingToggle("Pause ring controls", state.paused, driver::setPaused)
        RingToggle("Gesture tester (consume inputs without actions)", state.testing, driver::setTesting)
        RingTestPanel(state, driver::testSensor, driver::stopSensorTest, driver::clearTestResults)
        Text("Use the tester to confirm Next, Previous and tap events before unpausing. Media keys from devices named R08 are recognized. Other keyboards and headphones are ignored.", style = MaterialTheme.typography.bodySmall)
        if (state.inputDevice.isNotEmpty()) Text("Verified input device: ${state.inputDevice}")
        Text("Verified gestures: ${state.verifiedGestures.joinToString().ifEmpty { "none yet" }}", style = MaterialTheme.typography.bodySmall)
        Text("Only gestures confirmed in the tester can run actions.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = driver::resetVerification) { Text("Reset gesture verification") }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Scroll and click in other apps", style = MaterialTheme.typography.titleMedium)
        RingToggle("Allow all apps", state.allApps, driver::setAllApps)
        Text(if (state.allApps) "Ring controls can act in any foreground app while the phone is unlocked."
            else "Ring controls are limited to the apps selected below.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { ringScrollClickMappings().forEach(driver::setMapping) }) { Text("Use scroll & click controls") }
        RingToggle("Show click target", state.showTarget, driver::setShowTarget)
        OutlinedButton(onClick = driver::centerTarget) { Text("Center click target") }
        Text("Swipes scroll. Single tap clicks the crosshair. One tap + swipe moves it up/down; two taps + swipe moves it left/right. You can also drag the crosshair with your finger. Confirm these gestures in the tester, turn the tester off, then turn Pause ring controls off.", style = MaterialTheme.typography.bodySmall)
        Text("Keep Show click target on to enable clicks and target movement. Android or an app may restrict injected touch gestures.", style = MaterialTheme.typography.bodySmall)
        RingToggle("Reverse scroll direction", state.reverse, driver::setReverse)
        Text("Scroll distance: ${(state.distance * 100).toInt()}% of the screen")
        Slider(value = state.distance, onValueChange = driver::setDistance, valueRange = 0.15f..0.65f)
        Text("Gesture actions", style = MaterialTheme.typography.titleMedium)
        state.mappings.forEach { (gesture, action) ->
            var expanded by remember(gesture) { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("$gesture: ${action.label}") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RingAction.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, onClick = { driver.setMapping(gesture, choice); expanded = false })
                    }
                }
            }
        }
        var choices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        OutlinedButton(onClick = { choices = driver.allowedAppChoices() }) { Text("Choose allowed apps") }
        choices?.let { apps ->
            AlertDialog(onDismissRequest = { choices = null }, title = { Text("Allowed apps") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(apps, key = { it.first }) { (packageName, name) ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = packageName in ringAllowedPackages(state.allowlist), onCheckedChange = { enabled ->
                                    val allowed = ringAllowedPackages(state.allowlist)
                                    driver.setAllowlist((if (enabled) allowed + packageName else allowed - packageName).sorted().joinToString(", "))
                                })
                                Text(name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { choices = null }) { Text("Done") } })
        }
        OutlinedTextField(value = state.allowlist, onValueChange = driver::setAllowlist,
            label = { Text("Allowed app package IDs") }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Choose apps above, or enter package IDs separated by commas or new lines. Empty disables actions when Allow all apps is off.") })
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Live gesture log", style = MaterialTheme.typography.titleMedium)
        if (state.events.isEmpty()) Text("No input yet. Enable the tester and swipe or tap your ring.")
        state.events.take(12).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = driver::exportDiagnostics) { Text("Share diagnostic log") }
        Text("Diagnostics include Meowmba's connection and input events. They are not a firmware backup or QRing traffic capture. Firmware flashing is not included.", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun RingToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = value, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun RingSettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), content = content)
        }
    }
}
