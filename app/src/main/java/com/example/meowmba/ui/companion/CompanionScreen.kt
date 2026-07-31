package com.example.meowmba.ui.companion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
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
        // 0. Chosen background — first child, so it is genuinely behind the avatar.
        AppBackground(
            selection = uiState.background,
            weather = uiState.ambientWeather ?: uiState.weather,
            modifier = Modifier.matchParentSize(),
        )

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
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Vision: the frame the companion is looking at, while it describes it
                        uiState.capturedFrame?.let { frame ->
                            androidx.compose.foundation.Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = "What the companion is looking at",
                                modifier = Modifier
                                    .size(96.dp)
                                    .padding(bottom = 8.dp)
                            )
                        }
                        Text(
                            text = uiState.captionText ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // The "recalled from memory" overlay was removed at the user's request — it
            // covered the companion during every turn. Recall still happens and still feeds
            // the prompt; it is just no longer narrated on screen. The Memory screen remains
            // the place to see what is stored.


            // Generated media popup (image/video/audio created by Canvas agent)
            AnimatedVisibility(
                visible = uiState.generatedMedia != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                uiState.generatedMedia?.let { media ->
                    Surface(
                        color = Color(0xFF161B22),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { viewModel.dismissGeneratedMedia() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                                }
                            }
                            when (media.type) {
                                GeneratedMedia.MediaType.IMAGE -> {
                                    var bitmap by remember(media.filePath) {
                                        mutableStateOf<android.graphics.Bitmap?>(null)
                                    }
                                    LaunchedEffect(media.filePath) {
                                        bitmap = try {
                                            android.graphics.BitmapFactory.decodeFile(media.filePath)
                                        } catch (_: Exception) { null }
                                    }
                                    bitmap?.let { bmp ->
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = media.label,
                                            modifier = Modifier
                                                .size(240.dp)
                                                .padding(bottom = 8.dp)
                                        )
                                    }
                                }
                                GeneratedMedia.MediaType.VIDEO -> {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF58A6FF),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                GeneratedMedia.MediaType.AUDIO -> {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF3FB950),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Text(media.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                media.filePath.substringAfterLast("/"),
                                color = Color.Gray, fontSize = 11.sp
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

            // Camera preview toggle (bottom-end, above chat)
            IconButton(
                onClick = { viewModel.togglePreview() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(bottom = 72.dp, end = 16.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isPreviewVisible)
                        Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = "Toggle camera preview",
                    tint = if (uiState.isPreviewVisible) Color(0xFF3FB950) else Color(0x55CFF9FF)
                )
            }
        }

        // Live camera preview — a small PiP-style window near the bottom
        AnimatedVisibility(
            visible = uiState.isPreviewVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Viewfinder(
                        visionManager = viewModel.getVisionManager(),
                        modifier = Modifier.size(width = 160.dp, height = 120.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.flipCamera() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Flip camera",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Atmosphere in front of the avatar — weather you are standing in, not a wallpaper.
        // Drawn before the overlays so it never covers the menus.
        //
        // Shown while the card is up, and permanently when ambient weather is on. Skipped
        // when the chosen BACKGROUND is already the live sky: the same scene twice is just
        // double the particles and half the frame rate.
        val liveWeatherIsBackground = uiState.background.kind ==
            com.example.meowmba.core.background.BackgroundKind.LIVE_WEATHER
        val atmosphere = uiState.weather
            ?: uiState.ambientWeather?.takeIf { uiState.ambientWeatherEnabled }
        if (!liveWeatherIsBackground) {
            atmosphere?.let { w ->
                val clockTimeOfDay = rememberTimeOfDay(w.isDay)
                WeatherScene(
                    condition = w.condition,
                    isDay = w.isDay,
                    timeOfDay = uiState.previewTimeOfDay ?: clockTimeOfDay,
                    // Held back: this layer is in front of the face.
                    intensity = overlayIntensity(w.condition),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        // 3. Overlays (Floating Menus)
        BackgroundTiles(
            visible = uiState.showTiles,
            onClose = { viewModel.onAvatarZoneClick(AvatarZone.BACKGROUND) },
            onTileClick = { tileName ->
                // Picking a tile closes the tile menu. Weather in particular puts a card
                // *behind* the still-open grid otherwise, which just looks broken.
                viewModel.onAvatarZoneClick(AvatarZone.BACKGROUND)
                when (tileName) {
                    "Settings" -> viewModel.toggleSettings()
                    "Hardware" -> viewModel.toggleModelManager()
                    "Privacy" -> viewModel.togglePrivacyDashboard()
                    "Memory" -> viewModel.toggleMemoryScreen()
                    "NAV" -> viewModel.toggleNavScreen()
                    "Weather" -> viewModel.toggleWeatherScreen()
                    "Background" -> viewModel.toggleBackgroundScreen()
                    "Agents", "Tools" -> viewModel.toggleAgentsScreen()
                }
            }
        )


        // Weather card: floats above the companion rather than replacing it, so the cat
        // reacting to the weather and the numbers are visible together. The scene itself is
        // drawn FIRST (below), behind the avatar, so the cat sits inside the weather.
        uiState.weather?.let { w ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 48.dp)
            ) {
                WeatherCard(
                    // Forcing the scene to midday and leaving the card on its night palette
                    // would look like two different weathers.
                    weather = w.copy(
                        isDay = uiState.previewTimeOfDay?.isLight ?: w.isDay,
                    ),
                    onDismiss = { viewModel.dismissWeatherCard() },
                    // Tap the card to preview the next condition's animation.
                    onCycleCondition = { viewModel.cycleForcedWeatherCondition() },
                    previewTimeOfDay = uiState.previewTimeOfDay,
                    onCycleTimeOfDay = { viewModel.cyclePreviewTimeOfDay() },
                )
            }
        }

        // The thing the conversation is about. Sits above the avatar, below the menus, and
        // low enough that the weather card at the top is still readable next to it.
        uiState.inventoryItem?.let { item ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 190.dp)
            ) {
                InventoryCard(
                    item = item,
                    onAction = { op, args -> viewModel.runInventoryAction(op, args) },
                    onOpenApp = { viewModel.openInventoryApp(item.name) },
                    onDismiss = { viewModel.dismissInventoryCard() },
                )
            }
        }

        if (uiState.showWeatherScreen) {
            WeatherScreen(
                weather = uiState.weather ?: uiState.ambientWeather,
                ambientEnabled = uiState.ambientWeatherEnabled,
                onAmbientChange = { viewModel.setAmbientWeatherEnabled(it) },
                previewTimeOfDay = uiState.previewTimeOfDay,
                onPreviewTimeOfDay = { viewModel.setPreviewTimeOfDay(it) },
                onCycleCondition = { viewModel.cycleForcedWeatherCondition() },
                onRefresh = { viewModel.refreshWeather() },
                onClose = { viewModel.toggleWeatherScreen() },
            )
        }

        if (uiState.showBackgroundScreen) {
            BackgroundScreen(
                selection = uiState.background,
                onSelect = { viewModel.setBackground(it) },
                onClose = { viewModel.toggleBackgroundScreen() },
                ambientWeatherOn = uiState.ambientWeatherEnabled,
            )
        }

        if (uiState.showNavScreen) {
            NavScreen(
                secureStorage = secureStorage,
                onClose = { viewModel.toggleNavScreen() },
            )
        }

        if (uiState.showSettings && secureStorage != null) {
            SettingsScreen(
                secureStorage = secureStorage,
                companionName = uiState.name,
                wakeWordEnabled = uiState.wakeWordEnabled,
                conversationMode = uiState.conversationMode,
                agentBrainEnabled = uiState.agentBrainEnabled,
                hermesAgentUrl = uiState.hermesAgentUrl,
                imageModelDir = uiState.imageModelDir,
                imageModelInstalled = { viewModel.imageModelInstalled() },
                backgroundListeningEnabled = uiState.backgroundListeningEnabled,
                usbAutoBridgeEnabled = uiState.usbAutoBridgeEnabled,
                onCompanionNameChange = { viewModel.renameCompanion(it) },
                onWakeWordChange = { viewModel.setWakeWordEnabled(it) },
                onConversationModeChange = { viewModel.setConversationMode(it) },
                onAgentBrainChange = { viewModel.setAgentBrainEnabled(it) },
                onHermesUrlChange = { viewModel.setHermesAgentUrl(it) },
                onImageModelDirChange = { viewModel.setImageModelDir(it) },
                onBackgroundListeningChange = { viewModel.setBackgroundListeningEnabled(it) },
                onUsbAutoBridgeChange = { viewModel.setUsbAutoBridge(it) },
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
                onUseInstalledFile = { viewModel.useInstalledModelFile(it) },
                onDeleteInstalledFile = { viewModel.deleteInstalledModelFile(it) },
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
            onDeleteMessage = { viewModel.deleteMessage(it) },
            messages = uiState.chatMessages,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Drawn AFTER ChatOverlay deliberately. In a Box, later children
        // paint on top; while this sat earlier in the file the approval
        // card rendered underneath the open conversation sheet, so a task
        // waiting on approval was invisible and silently timed out.
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
    }
}
