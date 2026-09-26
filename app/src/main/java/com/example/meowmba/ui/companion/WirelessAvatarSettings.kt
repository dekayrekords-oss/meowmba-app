package com.example.meowmba.ui.companion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.setup.WirelessAvatarMonitor

@Composable
internal fun WirelessAvatarSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(WirelessAvatarMonitor.PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(WirelessAvatarMonitor.ENABLED, false)) }
    var background by remember { mutableStateOf(prefs.getBoolean(WirelessAvatarMonitor.BACKGROUND, false)) }
    var error by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wireless-charger avatar", style = MaterialTheme.typography.titleMedium)
        Text("Show your selected face/avatar when wireless power connects. Works with any wireless charger, including magnetic ones—not USB or a plain magnet. Turn this on before placing the phone on its charger.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show avatar on wireless charging", Modifier.weight(1f))
            Switch(enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(WirelessAvatarMonitor.ENABLED, it).apply()
            })
        }
        if (enabled) {
            Text("While Meow is running, it switches once per charging session. It waits until the phone is awake and unlocked; it doesn't keep the screen awake. If Android stops Meow, open it again. Removing the charger doesn't close your avatar.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Allow opening over other apps", Modifier.weight(1f))
                Switch(background, onCheckedChange = {
                    background = it
                    prefs.edit().putBoolean(WirelessAvatarMonitor.BACKGROUND, it).apply()
                })
            }
            if (background) OutlinedButton(onClick = {
                error = runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"))) }.isFailure
            }) { Text("Allow display over other apps in Android") }
            Text("Without that access, background charging offers a silent notification instead. Notifications must be allowed in Android.")
            OutlinedButton(onClick = {
                error = runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.isFailure
            }) { Text("Open notification settings") }
            if (error) Text("Open Android Settings → Apps → Special access → Appear on top, then select Meow.")
        }
    }
}
