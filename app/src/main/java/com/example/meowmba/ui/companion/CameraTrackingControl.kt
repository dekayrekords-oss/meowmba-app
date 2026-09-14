package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.vision.FaceTrackingStatus
import com.example.meowmba.ui.theme.Surface

/** Camera resource control with observed tracking state; it never creates a preview. */
@Composable
internal fun CameraTrackingControl(enabled: Boolean, status: FaceTrackingStatus,
    onToggle: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val shown = if (enabled) status else FaceTrackingStatus.OFF
    Surface(modifier = modifier.testTag("camera-tracking-control"),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (shown == FaceTrackingStatus.ERROR) "Tracking unavailable" else shown.label,
                Modifier.widthIn(max = 138.dp).testTag("camera-tracking-status"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            if (enabled && shown == FaceTrackingStatus.ERROR) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Retry") }
            }
            IconButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = shown.label }) {
                Icon(if (enabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (enabled) "Turn camera off" else "Turn camera on",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
