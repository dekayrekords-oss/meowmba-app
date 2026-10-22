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
import kotlin.math.roundToInt
import com.example.meowmba.core.ring.*

@Composable
internal fun SmartRingScene(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val driver = remember(context) { AndroidSmartRing.get(context) }
    var disclosure by remember { mutableStateOf(false) }
    RingSettingsPage("Smart Ring", onBack) {
        val state by driver.state.collectAsState()
        LaunchedEffect(state.connected) {
            if (state.connected && !state.controlsConsent && com.example.meowmba.BuildConfig.RING_CONTROLS_ENABLED) disclosure = true
        }
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
        if (!com.example.meowmba.BuildConfig.RING_CONTROLS_ENABLED) {
            Text("Cross-app ring controls and the gesture tester are unavailable in this edition. Bluetooth connection and on-demand sensor readings stay on this phone.")
            RingTestPanel(state, driver::testSensor, driver::stopSensorTest, driver::clearTestResults, showInput = false)
            return@RingSettingsPage
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(if (state.accessibility) "Accessibility service connected" else "Accessibility service is off")
        Text("Use your ring for media, navigation, photos or custom Meow requests. Review the Accessibility disclosure before enabling these controls.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { if (state.controlsConsent) driver.openAccessibility() else disclosure = true }) { Text("Open Accessibility settings") }
        if (state.controlsConsent) OutlinedButton(onClick = { driver.setControlsConsent(false) }) { Text("Withdraw ring controls consent") }
        RingToggle("Pause ring controls", state.paused, driver::setPaused, enabled = state.controlsConsent)
        RingToggle("Gesture tester (consume inputs without actions)", state.testing, driver::setTesting, enabled = state.controlsConsent)
        RingModeSettings(state, driver)
        Text("Pausing stops Meow's remapped actions. The ring's native Android media keys may still operate outside Meow.", style = MaterialTheme.typography.bodySmall)
        Text("Sleep mode", style = MaterialTheme.typography.titleMedium)
        RingToggle("Sleep after inactivity", state.sleepEnabled, driver::setSleepEnabled)
        Text(if (state.sleeping) "Ring controls asleep · double tap to wake" else if (ringSleepEligible(state)) "Ring controls awake" else "Sleep timer inactive")
        Text("Sleep after: ${state.sleepAfterSeconds} seconds")
        Slider(value = state.sleepAfterSeconds.toFloat(), enabled = state.sleepEnabled,
            onValueChange = { driver.setSleepAfterSeconds((it / 5).roundToInt().coerceAtLeast(1) * 5) },
            valueRange = 5f..300f, steps = 58, modifier = Modifier.semantics { contentDescription = "Idle sleep timeout" })
        Text("Sleep hides the cursor and ignores ring actions until a double tap. Ring input or dragging the cursor restarts the timer. The wake tap does not click or go Back.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = if (state.sleeping) driver::wakeControls else driver::sleepNow,
            enabled = ringSleepEligible(state)) { Text(if (state.sleeping) "Wake controls" else "Sleep now") }
        RingTestPanel(state, driver::testSensor, driver::stopSensorTest, driver::clearTestResults)
        Text("Use the tester to confirm Next, Previous and tap events before unpausing. Media keys from devices named R08 are recognized. Other keyboards and headphones are ignored.", style = MaterialTheme.typography.bodySmall)
        if (state.inputDevice.isNotEmpty()) Text("Verified input device: ${state.inputDevice}")
        Text("Verified gestures: ${state.verifiedGestures.joinToString().ifEmpty { "none yet" }}", style = MaterialTheme.typography.bodySmall)
        Text("Clicks and other actions require their gesture to be confirmed. Cursor movement can also combine verified taps and swipes.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = driver::resetVerification) { Text("Reset gesture verification") }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Allowed foreground apps", style = MaterialTheme.typography.titleMedium)
        RingToggle("Allow all apps", state.allApps, driver::setAllApps)
        Text(if (state.allApps) "Ring controls can act in any foreground app while the phone is unlocked."
            else "Ring controls are limited to the apps selected below.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { driver.setMode(RingMode.NAVIGATION) }) { Text("Use scroll & click controls") }
        RingToggle("Show click target", state.showTarget, driver::setShowTarget)
        OutlinedButton(onClick = driver::centerTarget) { Text("Center click target") }
        Text("Swipes scroll. Single tap clicks the crosshair. One tap + swipe moves it left/right; two taps + swipe moves it up/down. You can also drag the crosshair with your finger. Confirm taps and both swipe directions in the tester, turn the tester off, then turn Pause ring controls off.", style = MaterialTheme.typography.bodySmall)
        Text("Keep Show click target on to enable clicks and target movement. Android or an app may restrict injected touch gestures.", style = MaterialTheme.typography.bodySmall)
        RingToggle("Reverse scroll direction", state.reverse, driver::setReverse)
        Text("Fallback swipe distance: ${(state.distance * 100).toInt()}% of the scroll area")
        Slider(value = state.distance, onValueChange = driver::setDistance, valueRange = 0.15f..0.65f)
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
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false },
        title = { Text("Enable ring controls") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Meow uses Android's AccessibilityService API to receive your ring's key events and access the active app's package, window bounds and control structure, including photo-shutter labels. Your configured gestures can scroll, move/click the target, navigate, control media/volume, open a chosen app or camera, take a photo in a compatible camera, or submit a saved Meow request in allowed apps.")
            Spacer(Modifier.height(12.dp))
            Text("The ring service processes app controls locally. It does not record screen contents or send screen contents or raw ring input to AI providers. You can explicitly share local connection and gesture logs with a destination you choose. If you choose Ask Meow, your saved request is submitted like a typed chat message and may go to your selected AI provider. Normal task permissions and approvals still apply. Requests are stored privately on this phone; avoid saving secrets. Actions require verified ring gestures and an unlocked phone. Controls start paused. You choose allowed apps; Allow all apps is optional.")
            Spacer(Modifier.height(12.dp))
            Text("You can decline, pause controls, withdraw consent here or turn off Meowmba Ring Controls in Android Accessibility settings.")
        } },
        confirmButton = { TextButton(onClick = { driver.setControlsConsent(true); disclosure = false; driver.openAccessibility() }) { Text("Agree and continue") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } })
}
@Composable
private fun RingToggle(label: String, value: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = value, onCheckedChange = change, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
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
