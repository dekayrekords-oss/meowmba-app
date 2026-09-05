package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Privacy dashboard.
 *
 * Every sensor toggle here is a real control wired to [CompanionState] — the
 * same state the avatar's face-level privacy marks (ear/eye/mouth "X" marks)
 * reflect. These switches are the accessible, button-based equivalents of the
 * avatar touch zones required by the spec.
 */
@Composable
fun PrivacyDashboard(
    state: CompanionState,
    onToggleMic: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Privacy & Security",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrivacyItem(
                label = "Microphone",
                description = "Required for push-to-talk and wake phrase. Never enabled silently.",
                isEnabled = state.isMicEnabled,
                icon = Icons.Default.Mic,
                onToggle = onToggleMic
            )
            PrivacyItem(
                label = "Camera",
                description = "Off by default in Phase 1. Eye-tap on the avatar also toggles this.",
                isEnabled = state.isCameraEnabled,
                icon = Icons.Default.Camera,
                onToggle = onToggleCamera
            )
            PrivacyItem(
                label = "Companion Voice",
                description = "Mutes spoken responses. Touching the avatar's mouth does the same.",
                isEnabled = !state.isMuted,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onToggle = { onToggleMute(!it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Voice, camera, chat, memories, and API keys stay on this phone unless " +
                    "you turn on a cloud brain or a LAN server in Settings. Keys are encrypted " +
                    "on device. Cloud backup of chat and keys is off. HTTP is only allowed on " +
                    "your own network; everything else must be HTTPS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
fun PrivacyItem(
    label: String,
    description: String,
    isEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}
