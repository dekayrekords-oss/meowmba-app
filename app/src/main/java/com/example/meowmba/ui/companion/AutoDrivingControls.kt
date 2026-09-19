package com.example.meowmba.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AutoDrivingControls(state: CompanionState, viewModel: CompanionViewModel) {
            // Car assistant: knows when you're driving, tracks trips, watches for crashes.
            Text("Car assistant", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            VoiceToggle(
                label = "Drive mode",
                description = "Meow senses when you're in a car (GPS speed, your car's " +
                    "Bluetooth, or a voice \"drive mode on\"), shows a speed/trip card, " +
                    "and can answer \"how fast\", \"are we there yet\", or find gas and " +
                    "food along the way. Uses GPS + accelerometer only while enabled.",
                checked = state.carModeEnabled,
                onCheckedChange = viewModel::setCarModeEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsTextField(
                value = state.emergencyContact,
                onValueChange = viewModel::setEmergencyContact,
                label = { Text("Emergency contact phone (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Used by the crash alert — say \"text mom\" after a detected " +
                    "impact and Meow will open a text with your location ready to send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Drive reactivity & cameras", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            VoiceToggle(
                label = "Stronger emotion while driving",
                description = "Faster face/social reactions and stronger lean/lurch while Drive mode is active.",
                checked = state.driveEmotionBoost,
                onCheckedChange = viewModel::setDriveEmotionBoost,
            )
            Spacer(modifier = Modifier.height(8.dp))
            VoiceToggle(
                label = "Driver camera on drive card",
                description = "Shows the live front-camera preview on the drive HUD when CAMERA is allowed.",
                checked = state.driveDriverCameraEnabled,
                onCheckedChange = viewModel::setDriveDriverCameraEnabled,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = state.carDashcamRtspUrl,
                onValueChange = viewModel::setCarDashcamRtspUrl,
                label = { Text("IP dashcam RTSP URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Lists an IP/RTSP dashcam for tools and the drive camera list. In-app live " +
                    "preview uses the phone cameras; RTSP playback needs an external player for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("OBD-II (ELM327)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            VoiceToggle(
                label = "Native OBD-II",
                description = "Connect to a classic Bluetooth ELM327 adapter and poll RPM, speed, " +
                    "coolant, battery, MIL, and fuel. Feeds Lumen excitement/alerts. " +
                    "USB ELM327 is detected but needs a CDC driver — Bluetooth SPP is the supported path.",
                checked = state.obdEnabled,
                onCheckedChange = viewModel::setObdEnabled,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTextField(
                value = state.obdAdapterAddress,
                onValueChange = viewModel::setObdAdapterAddress,
                label = { Text("ELM327 Bluetooth MAC address") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Apple CarPlay: third-party Android apps cannot fully remote-control a CarPlay " +
                    "session. Meow uses MediaSession / volume / dialer / Bluetooth and Android Auto " +
                    "hints instead — see docs/DRIVE_CARPLAY_OBD2.md.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

}
