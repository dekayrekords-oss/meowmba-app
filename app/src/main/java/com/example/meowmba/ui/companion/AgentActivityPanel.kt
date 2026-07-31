package com.example.meowmba.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.TaskStatus
import kotlin.math.roundToInt

/**
 * What the agents are doing, right now, over the top of everything else.
 *
 * Translucent on purpose: this is a status surface, not a screen. The companion stays
 * visible behind it, so handing off a job never feels like leaving the conversation.
 *
 * Swipe it sideways to dismiss. Dismissing does not cancel anything — the work carries on
 * and the chat keeps narrating it, which is the whole contract: you can always get rid of
 * the panel without losing the thread. It comes back on its own the next time an agent
 * picks up work.
 */
@Composable
fun AgentActivityPanel(
    tasks: List<AgentTask>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenAgent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = tasks.filter { !it.isTerminal }
    // Terminal tasks are worth a moment on screen — vanishing the instant a job finishes
    // is how "did that work?" happens.
    val recent = tasks.take(4)
    if (recent.isEmpty()) return

    var dragged by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut() + slideOutHorizontally { it },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .offsetPx(dragged)
                .alpha((1f - kotlin.math.abs(dragged) / DISMISS_DISTANCE_PX).coerceIn(0.25f, 1f))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xD91B2430), Color(0xBF0D1117)),
                    ),
                    shape = RoundedCornerShape(20.dp),
                )
                .border(1.dp, Color(0x33A371F7), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragged) > DISMISS_DISTANCE_PX * 0.45f) {
                                onDismiss()
                            }
                            dragged = 0f
                        },
                        onDragCancel = { dragged = 0f },
                    ) { _, delta -> dragged += delta }
                }
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live.isNotEmpty()) WorkingDot()
                    Text(
                        text = when {
                            live.size > 1 -> "${live.size} agents working"
                            live.size == 1 ->
                                "${AgentRegistry.byId(live.first().agentId)?.name ?: "Agent"} is working"
                            else -> "Agents"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = if (live.isEmpty()) 0.dp else 8.dp),
                    )
                    Text(
                        "swipe to hide",
                        color = Color(0xFF6E7681),
                        fontSize = 10.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))

                recent.forEach { task ->
                    TaskLine(task, onClick = { onOpenAgent(task.agentId) })
                }

                if (live.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Updates keep coming in chat if you hide this.",
                        color = Color(0xFF6E7681),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskLine(task: AgentTask, onClick: () -> Unit) {
    val (label, colour) = when (task.status) {
        TaskStatus.QUEUED -> "queued" to Color(0xFF8B949E)
        TaskStatus.RUNNING -> "running" to Color(0xFF58A6FF)
        TaskStatus.WAITING_APPROVAL -> "needs you" to Color(0xFFD29922)
        TaskStatus.COMPLETED -> "done" to Color(0xFF3FB950)
        TaskStatus.FAILED -> "failed" to Color(0xFFF85149)
        TaskStatus.CANCELLED -> "stopped" to Color(0xFF8B949E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = AgentRegistry.byId(task.agentId)?.name ?: task.agentId,
            color = Color(0xFFA371F7),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = task.title,
            color = Color(0xFFD8DEE6),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            color = colour,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(colour.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** A slow pulse, so "working" is legible without reading anything. */
@Composable
private fun WorkingDot() {
    val pulse by rememberInfiniteTransition(label = "agent-dot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(pulse)
            .background(Color(0xFF58A6FF), CircleShape),
    )
}

/** Horizontal drag offset in raw pixels, without dragging a density into the caller. */
private fun Modifier.offsetPx(x: Float): Modifier =
    this.offset { IntOffset(x.roundToInt(), 0) }

/** Roughly a third of a phone width: far enough to be deliberate, short enough to flick. */
private const val DISMISS_DISTANCE_PX = 320f
