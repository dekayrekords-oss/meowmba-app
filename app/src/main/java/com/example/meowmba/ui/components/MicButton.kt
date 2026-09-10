package com.example.meowmba.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.companion.InteractionState

@Composable
fun MicButton(
    interactionState: InteractionState,
    isMicEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The glow only communicates something while listening, and this button is on
    // the companion screen permanently. Creating the infinite transition
    // unconditionally kept the composable invalidating at vsync forever: while
    // idle it animated 1f -> 1f, a value that never changes but still cost a full
    // recomposition and render every frame. Measured on device that was ~25% of
    // RenderThread plus main-thread time for a no-op — a real share of the
    // app's idle CPU and of the phone's heat. Build it only when it can be seen.
    val listening = interactionState == InteractionState.LISTENING
    val glowScale = if (listening) {
        rememberInfiniteTransition(label = "mic_glow").animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        ).value
    } else {
        1f
    }

    val color = when (interactionState) {
        InteractionState.LISTENING -> Color(0xFF00E5FF)
        InteractionState.THINKING -> Color(0xFFFFD600)
        InteractionState.SPEAKING -> Color(0xFF00E676)
        else -> if (isMicEnabled) Color(0xFF448AFF) else Color.Gray
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
            .clickable(onClick = onClick)
    ) {
        // Outer Glow
        Box(
            modifier = Modifier
                .size(64.dp * glowScale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
        )
        
        // Button Base
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0.8f))
                    )
                )
        ) {
            Icon(
                imageVector = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
