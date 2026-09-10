package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** App microphone privacy latch, separate from push-to-talk and speaker mute. */
@Composable
internal fun MicKillButton(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val tint = if (enabled) Color(0xFFB2CBD0) else Color(0xFFFFA69D)
    IconToggleButton(
        checked = !enabled, onCheckedChange = { onEnabledChange(!it) },
        modifier = modifier.size(48.dp).semantics {
            stateDescription = if (enabled) "Microphone available" else "Microphone off"
        },
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color(0x99303E46), contentColor = tint,
            checkedContainerColor = Color(0xDD702D30), checkedContentColor = tint),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (enabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (enabled) "Turn microphone off" else "Turn microphone on",
                modifier = Modifier.size(22.dp))
            Text(if (enabled) "MIC ON" else "MIC OFF", fontSize = 8.sp, lineHeight = 10.sp)
        }
    }
}
