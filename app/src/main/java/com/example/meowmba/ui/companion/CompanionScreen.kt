package com.example.meowmba.ui.companion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.ui.components.MicButton
import com.example.meowmba.core.security.SecureStorageManager

/**
 * The avatar-first companion screen. This is the screen the app opens to:
 * the animated cat fills the space, the mic sits near the bottom, and every
 * other surface (chat, tiles, settings, privacy) is revealed on demand.
 */
@Composable
fun CompanionScreen(
    viewModel: CompanionViewModel,
    secureStorage: SecureStorageManager? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // 1. Animated cat avatar (Memba 2 face) — full-bleed canvas
        AvatarRenderer(
            state = uiState,
            onZoneClick = { viewModel.onAvatarZoneClick(it) },
            onSwipeUp = { viewModel.openChat() },
            onSwipeDown = { viewModel.openTiles() }
        )

        // 2. Controls layer — everything interactive respects the system bars
        //    (status bar, nav/gesture bar, display cutout) so it always fits.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // Privacy status line (bottom-start): explicit, text-based backup
            // for the face-level privacy marks.
            val privacyFlags = buildList {
                if (!uiState.isMicEnabled) add("MIC OFF")
                if (!uiState.isCameraEnabled) add("CAM OFF")
                if (uiState.isMuted) add("MUTED")
            }
            if (privacyFlags.isNotEmpty()) {
                Text(
                    text = privacyFlags.joinToString("  ·  "),
                    color = Color(0xFFF85149).copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            // Animated Captions
            AnimatedVisibility(
                visible = uiState.captionText != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp, start = 24.dp, end = 24.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = uiState.captionText ?: "",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // "Why recalled" chips: the user can always see which memories
            // surfaced for this turn and why.
            AnimatedVisibility(
                visible = uiState.recalledMemories.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 220.dp, start = 24.dp, end = 24.dp)
            ) {
                Surface(
                    color = Color(0xFF161B22).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Recalled from memory",
                            color = Color(0xFF58A6FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.recalledMemories.forEach { recall ->
                            Text(
                                "• ${recall.contentSnippet}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Text(
                                "  ${recall.reason}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Command Center (Mic)
            MicButton(
                interactionState = uiState.interactionState,
                isMicEnabled = uiState.isMicEnabled,
                onClick = { viewModel.onMicClick() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )

            // Chat shortcut (bottom-end); swipe-up on the avatar does the same.
            // imePadding keeps it riding above the keyboard.
            IconButton(
                onClick = { viewModel.openChat() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Open chat",
                    tint = Color(0x55CFF9FF)
                )
            }
        }

        // 3. Overlays (Floating Menus)
        BackgroundTiles(
            visible = uiState.showTiles,
            onClose = { viewModel.onAvatarZoneClick(AvatarZone.BACKGROUND) },
            onTileClick = { tileName ->
                when (tileName) {
                    "Settings" -> viewModel.toggleSettings()
                    "Hardware" -> viewModel.toggleModelManager()
                    "Privacy" -> viewModel.togglePrivacyDashboard()
                    "Memory" -> viewModel.toggleMemoryScreen()
                    "Agents", "Tools" -> viewModel.toggleAgentsScreen()
                }
            }
        )

        // Tool approval card: a risky action is blocked until you decide
        uiState.pendingApproval?.let { approval ->
            Surface(
                color = Color(0xFF161B22),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE) {
                        Color(0xFFF85149)
                    } else {
                        Color(0xFFD29922)
                    }
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE) {
                            "Destructive action — your call"
                        } else {
                            "Approval needed"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(approval.title, color = Color(0xFF58A6FF), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(approval.detail, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.resolveApproval(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Deny")
                        }
                        Button(
                            onClick = { viewModel.resolveApproval(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE) {
                                    Color(0xFFF85149)
                                } else {
                                    Color(0xFF3FB950)
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve")
                        }
                    }
                }
            }
        }

        if (uiState.showSettings && secureStorage != null) {
            SettingsScreen(
                secureStorage = secureStorage,
                companionName = uiState.name,
                wakeWordEnabled = uiState.wakeWordEnabled,
                conversationMode = uiState.conversationMode,
                agentBrainEnabled = uiState.agentBrainEnabled,
                hermesAgentUrl = uiState.hermesAgentUrl,
                backgroundListeningEnabled = uiState.backgroundListeningEnabled,
                onCompanionNameChange = { viewModel.renameCompanion(it) },
                onWakeWordChange = { viewModel.setWakeWordEnabled(it) },
                onConversationModeChange = { viewModel.setConversationMode(it) },
                onAgentBrainChange = { viewModel.setAgentBrainEnabled(it) },
                onHermesUrlChange = { viewModel.setHermesAgentUrl(it) },
                onBackgroundListeningChange = { viewModel.setBackgroundListeningEnabled(it) },
                onClose = { viewModel.toggleSettings() }
            )
        }

        if (uiState.showModelManager) {
            ModelManagerScreen(
                state = uiState,
                onModelSelected = { viewModel.onModelSelected(it) },
                onTestCloud = { viewModel.runCloudConnectionTest() },
                onCheckLocal = { viewModel.runLocalModelCheck() },
                onDownloadModel = { viewModel.downloadModel(it) },
                onUseModel = { viewModel.useDownloadedModel(it) },
                onDeleteModel = { viewModel.deleteDownloadedModel(it) },
                onCustomDownload = { url, name -> viewModel.downloadCustomModel(url, name) },
                onClose = { viewModel.toggleModelManager() }
            )
        }

        if (uiState.showPrivacyDashboard) {
            PrivacyDashboard(
                state = uiState,
                onToggleMic = { viewModel.setMicEnabled(it) },
                onToggleCamera = { viewModel.setCameraEnabled(it) },
                onToggleMute = { viewModel.setMuted(it) },
                onClose = { viewModel.togglePrivacyDashboard() }
            )
        }

        if (uiState.showMemoryScreen) {
            MemoryScreen(
                state = uiState,
                onSearch = { viewModel.searchMemories(it) },
                onDelete = { viewModel.deleteMemory(it) },
                onClearAll = { viewModel.clearAllMemories() },
                onRetentionChange = { viewModel.setRetentionDays(it) },
                onPausedChange = { viewModel.setMemoryPaused(it) },
                onExport = { viewModel.exportMemoriesJson() },
                onClose = { viewModel.toggleMemoryScreen() }
            )
        }

        if (uiState.showAgentsScreen) {
            AgentsScreen(
                state = uiState,
                onCancelTask = { viewModel.cancelTask(it) },
                onClose = { viewModel.toggleAgentsScreen() }
            )
        }

        ChatOverlay(
            isExpanded = uiState.isChatExpanded,
            onClose = { viewModel.toggleChat() },
            onSend = { viewModel.onUserTextSubmitted(it) },
            messages = uiState.chatMessages,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
