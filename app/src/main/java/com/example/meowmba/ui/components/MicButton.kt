package com.example.meowmba.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.companion.InteractionState
import com.example.meowmba.ui.theme.Button

/** Native talk button with the same ink base as menus. Status changes need no animation timer. */
@Composable
fun MicButton(
    interactionState: InteractionState,
    isMicEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (fill, ink) = when {
        !isMicEnabled -> scheme.errorContainer to scheme.onErrorContainer
        interactionState == InteractionState.LISTENING -> scheme.secondaryContainer to scheme.onSecondaryContainer
        interactionState == InteractionState.THINKING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Box(modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Button(onClick = onClick, modifier = Modifier.size(64.dp).semantics {
            stateDescription = if (!isMicEnabled) "Microphone off" else interactionState.name.lowercase()
        }, shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink),
            border = BorderStroke(if (interactionState == InteractionState.LISTENING) 3.dp else 2.dp, scheme.outlineVariant),
            contentPadding = PaddingValues(0.dp)) {
            Icon(if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Microphone", modifier = Modifier.size(30.dp))
        }
    }
}
