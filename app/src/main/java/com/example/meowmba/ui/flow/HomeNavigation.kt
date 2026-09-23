package com.example.meowmba.ui.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Explicit navigation survives every avatar style; decorative gestures are only shortcuts. */
@Composable
internal fun FlowHomeNavigation(onMenu: () -> Unit, onAdd: () -> Unit, onDesk: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalFlowColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Menu" to onMenu, "Add task" to onAdd, "Desk" to onDesk, "Settings" to onSettings).forEach { (label, action) ->
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action)
                .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                BasicText(label, style = FlowType.button.copy(color = colors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

@Composable
internal fun DeskNavigation(modifier: Modifier = Modifier, vertical: Boolean = false, onMenu: () -> Unit, onFlow: () -> Unit, onSettings: () -> Unit) {
    val controls: @Composable () -> Unit = {
        listOf("Menu" to onMenu, "Flow" to onFlow, "Settings" to onSettings).forEach { (label, action) ->
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label, color = Color(0xFFF4EEDB))
            }
        }
    }
    if (vertical) Column(modifier.width(128.dp).padding(horizontal = 8.dp)) { controls() }
    else Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) { controls() }
}
