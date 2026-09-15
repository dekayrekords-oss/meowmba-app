package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Sync
import com.example.meowmba.ui.theme.AlertDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
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
    secureStorage: SecureStorageManager? = null,
    onboardingVisible: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val terminalUiStatus by viewModel.companionTerminalStatus.collectAsState()
    val terminalUiStatusDetail by viewModel.companionTerminalStatusDetail.collectAsState()
    val terminalUiScrollback by viewModel.companionTerminalScrollback.collectAsState()
    val terminalUiConnectedTargetId by viewModel.companionTerminalConnectedTargetId.collectAsState()
    val terminalPaneRequest by viewModel.companionTerminalPaneRequest.collectAsState()
    val terminalTargets by rememberTerminalTargets(viewModel, uiState.isChatExpanded) {
        viewModel.listCompanionTerminalTargets()
    }
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    // Pinned apps + drawer folders are used by both the base surfaces and the
    // shared overlay layer, so they live at the screen root.
    val pinnedApps by viewModel.pinnedPackages.collectAsState()
    val drawerFolders by viewModel.drawerFolders.collectAsState()
    val drawerUsage by viewModel.drawerUsage.collectAsState()

    // Foldable split: on the unfolded inner display (wide, >=600dp) the
    // agent browser lives in the right half while the companion keeps the
    // left, so meow can drive the browser in real time while you watch.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverContext = LocalContext.current
    val coverConfiguration = LocalConfiguration.current
    val isFlipCover = useCoverAvatarLayout(
        uiState.coverAvatarEnabled,
        coverContext.display?.displayId ?: 0,
        isFlipCoverWindow(android.os.Build.MANUFACTURER, android.os.Build.MODEL,
            coverContext.display?.displayId ?: 0, screenWidthDp, coverConfiguration.screenHeightDp) ||
            (coverContext.display?.let { it.flags and android.view.Display.FLAG_PRESENTATION == 0 } == true &&
                coverConfiguration.touchscreen == android.content.res.Configuration.TOUCHSCREEN_FINGER),
        screenWidthDp, coverConfiguration.screenHeightDp,
    )
    val isUnfolded = screenWidthDp >= 600
    val splitBrowser = isUnfolded && uiState.showBrowser
    val halfWidth = (screenWidthDp / 2).dp
    // Meow freezes while a surface covers the face — chat sheet, app menu,
    // or the full-screen (folded) browser. In split view the avatar stays
    // alive beside the browser.
    val avatarWorkStatus = rememberAvatarWorkStatus()
    val avatarWorkAllowed = avatarWorkStatus.allowed
    val habitatCovered = !avatarSceneVisible(uiState) ||
        uiState.showConnectorsScreen || showOnboarding || onboardingVisible || (uiState.showBrowser && !splitBrowser)
    val animationsPaused = !avatarWorkAllowed || habitatCovered
    // Report the real stage policy, including thermal/fold/onboarding changes.
    // Camera/body preferences must not cancel a visible acting performance.
    LaunchedEffect(viewModel, animationsPaused) {
        viewModel.setAvatarPerformanceStageAllowed(!animationsPaused)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.setAvatarPerformanceStageAllowed(false) }
    }
    DisposableEffect(viewModel, animationsPaused, uiState.showNeuralInfo, uiState.isCameraEnabled, uiState.robotBodyEnabled) {
        // Brain is a visual monitor; opening it does not enable a physical body.
        viewModel.setBodyStageAllowed(!animationsPaused && !uiState.showNeuralInfo)
        onDispose { viewModel.setBodyStageAllowed(false) }
    }

    LaunchedEffect(uiState.showSettings, uiState.showNeuralInfo) {
        if (uiState.showSettings || uiState.showNeuralInfo) viewModel.refreshMaleCnsServer()
    }
    val isNeo = uiState.avatarStyle == AvatarStyle.NEO
    val hasUserBg = uiState.background.kind !=
        com.example.meowmba.core.background.BackgroundKind.NONE
    // Transparent desk for Godot when no user wallpaper is set. Use a 1/255
    // black instead of fully transparent so the window still receives touches
    // (true 0-alpha lets taps fall through on some OEMs — cards looked "dead").
    val voidColor = when {
        uiState.appTheme == AppTheme.PROFESSIONAL -> MaterialTheme.colorScheme.background
        uiState.avatarStyle.isLumen -> MaterialTheme.colorScheme.background
        isNeo && !hasUserBg -> Color(0x03000000)
        else -> MaterialTheme.colorScheme.background
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(voidColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (splitBrowser) Modifier.padding(end = halfWidth) else Modifier)
        ) {
        if (isFlipCover) {
            FlipCoverAvatar(uiState, onMicClick = viewModel::onMicClick) {
                AvatarMotionHost(viewModel, uiState, animationsPaused,
                    Modifier.fillMaxSize(), interactive = false)
            }
        } else if (uiState.appTheme == AppTheme.PROFESSIONAL) {
            ProfessionalWorkspace(uiState, viewModel) {
                AvatarMotionHost(viewModel, uiState, animationsPaused || uiState.showAvatarScreen,
                    Modifier.fillMaxSize(), interactive = false)
            }
        } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Reserve real layout space for the monitor so even a 300% avatar can
        // remain visible. Background and props share the same resized viewport.
        val brainSpace = when {
            !uiState.showNeuralInfo -> Modifier
            maxWidth > maxHeight -> Modifier.padding(end = minOf(360.dp, maxWidth * .48f) + 24.dp)
            else -> Modifier.padding(bottom = maxHeight * .56f + 24.dp)
        }
        Box(Modifier.matchParentSize().then(brainSpace)) {
        // 0. User-chosen background always draws when set. Live weather sky too.
        // Only the empty NEO desk stays see-through.
        if ((!isNeo || hasUserBg) && !(uiState.avatarStyle == AvatarStyle.FULL_FLY && uiState.habitatMode)) {
            AppBackground(
                selection = if (uiState.habitatMode) com.example.meowmba.core.background.BackgroundSelection(com.example.meowmba.core.background.BackgroundKind.ANIMATION, uiState.habitatBackgroundId, 15) else uiState.background,
                weather = uiState.ambientWeather ?: uiState.weather,
                modifier = Modifier.matchParentSize(),
                paused = animationsPaused,
            )
        }

        // 1. Avatar + high-frequency motion isolated so face-track/IMU never
        // recompose weather, tiles, chat, captions, or settings (choppiness fix).
        // Avatar drawing code is unchanged.
        AvatarMotionHost(
            viewModel = viewModel,
            uiState = uiState,
            paused = animationsPaused,
            modifier = Modifier.matchParentSize(),
        )
        }
        }

        if (!habitatCovered && !uiState.showNeuralInfo) {
            PetCarePanel(
                habitatMode = uiState.habitatMode, onTrick = viewModel::playCompanionTrick,
                care = uiState.petCare, motionPaused = !avatarWorkAllowed, onCareChanged = viewModel::setPetCare,
                onAction = viewModel::habitatAction, onFeedback = viewModel::petFeedback,
                onLearningChanged = viewModel::setPetLearning, learningEnabled = uiState.petLearningEnabled,
                feedbackNotice = uiState.petFeedbackNotice,
                onResetLearning = viewModel::resetPetLearning,
                formKey = uiState.generatedAvatarForm?.let { "generated" }
                    ?: (uiState.performanceAnimal ?: uiState.avatarAnimal).key,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 12.dp, top = 64.dp),
            )
        }

        // 2. Controls layer — everything interactive respects the system bars
        //    (status bar, nav/gesture bar, display cutout) so it always fits.

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val faceContext = LocalContext.current

            // Two pinned apps on the face (first two favorites).
            if (pinnedApps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pinnedApps.take(2).forEach { pkg ->
                        Box(
                            modifier = Modifier.clickable { launchApp(faceContext, pkg) },
                            contentAlignment = Alignment.Center,
                        ) {
                            DrawerIcon(
                                packageName = pkg,
                                size = 44.dp,
                                label = "pinned app",
                            )
                        }
                    }
                }
            }

            if (uiState.computerTaskRunning) {
                OutlinedButton(onClick = viewModel::cancelAgenticComputerChat,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)) {
                    Text("Stop Computer")
                }
            }

            // Right-edge app tab: four more pinned apps in a strip down the
            // right side. Swipe left to pull it out, swipe left again to put
            // it back; the "+" opens the drawer where long-press pins an app.
            AppRailTab(
                apps = pinnedApps.drop(2).take(RAIL_APP_LIMIT),
                onLaunch = { launchApp(faceContext, it) },
                onAdd = { viewModel.toggleAppDrawer() },
                onUnpin = { viewModel.togglePin(it) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )

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
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            if (uiState.isNoting) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "Local notes · recording",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        IconButton(
                            onClick = { viewModel.stopNoting() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Stop taking notes and save a local summary",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            if (uiState.policeAlert != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp, start = 20.dp, end = 20.dp)
                        .clickable { viewModel.dismissPoliceAlert() }
                ) {
                    Text(
                        text = uiState.policeAlert ?: "",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Animated Captions — only while a turn is actually live: your
            // speech (live transcript) or the reply. Never the idle hint.
            AnimatedVisibility(
                visible = !uiState.isChatExpanded && uiState.captionText != null &&
                    uiState.interactionState != InteractionState.IDLE,
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


            // Command Center (Mic)
            // Landscape puts it on the left edge, vertically centered: the wide
            // canvas has spare room at the sides and none under the chin, so a
            // bottom-center mic crowds the mouth. Portrait keeps it under the
            // face, where the thumb expects it. BottomStart is already taken by
            // the privacy flags, so centering vertically avoids that overlap.
            val landscape = LocalConfiguration.current.screenWidthDp >
                LocalConfiguration.current.screenHeightDp
            MicButton(
                interactionState = uiState.interactionState,
                isMicEnabled = uiState.isMicEnabled,
                onClick = { viewModel.onMicClick() },
                modifier = if (landscape) {
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                }
            )

            // Chat shortcut (bottom-end); swipe-up on the avatar does the same.
            // imePadding keeps it riding above the keyboard.
            IconButton(
                onClick = { viewModel.openChat() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Open chat",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Capture controls: the camera action releases/enables capture, independent of chat.
            MicKillButton(
                enabled = uiState.isMicEnabled,
                onEnabledChange = viewModel::setMicEnabled,
                modifier = Modifier.align(Alignment.BottomEnd).imePadding()
                    .padding(bottom = 128.dp, end = 16.dp),
            )
            CameraTrackingControl(
                enabled = uiState.isCameraEnabled,
                status = uiState.cameraTrackingStatus,
                onToggle = { viewModel.setCameraEnabled(!uiState.isCameraEnabled) },
                onRetry = viewModel::resumeFaceTrackingIfEnabled,
                modifier = Modifier.align(Alignment.BottomEnd).imePadding()
                    .padding(bottom = 72.dp, end = 16.dp),
            )

        // Live camera preview — draggable PiP so it can sit anywhere on screen.
        val density = LocalDensity.current
        val pipWpx = with(density) { 160.dp.toPx() }
        val pipHpx = with(density) { 148.dp.toPx() }
        val parentWpx = constraints.maxWidth.toFloat()
        val parentHpx = constraints.maxHeight.toFloat()
        val defaultPipX = ((parentWpx - pipWpx) / 2f).coerceAtLeast(0f)
        val defaultPipY = (parentHpx - pipHpx - with(density) { 120.dp.toPx() })
            .coerceAtLeast(0f)
        val pipX = remember { mutableFloatStateOf(Float.NaN) }
        val pipY = remember { mutableFloatStateOf(Float.NaN) }
        if (pipX.floatValue.isNaN()) pipX.floatValue = defaultPipX
        if (pipY.floatValue.isNaN()) pipY.floatValue = defaultPipY
        AnimatedVisibility(
            visible = uiState.isPreviewVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(pipX.floatValue.roundToInt(), pipY.floatValue.roundToInt()) }
        ) {
            Box(
                modifier = Modifier.pointerInput(parentWpx, parentHpx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val next = PreviewWindow.clamp(
                            x = pipX.floatValue + drag.x,
                            y = pipY.floatValue + drag.y,
                            winW = pipWpx,
                            winH = pipHpx,
                            parentW = parentWpx,
                            parentH = parentHpx,
                        )
                        pipX.floatValue = next.first
                        pipY.floatValue = next.second
                    }
                }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                            )
                        }
                        Viewfinder(
                            visionManager = viewModel.getVisionManager(),
                            modifier = Modifier.size(width = 160.dp, height = 120.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.flipCamera() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp)
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
        // Particle atmosphere is several full-screen blurred Canvases — very expensive.
        // Only run it when ambient weather is explicitly enabled. Weather *cards*
        // still show numbers without the continuous particle storm.
        // (After any weather fetch, uiState.weather stays non-null forever, which
        // used to leave WeatherScene running and chop the UI.)
        val atmosphere = uiState.ambientWeather?.takeIf { uiState.ambientWeatherEnabled }
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
                    paused = animationsPaused,
                )
            }
        }

        }

        if (!uiState.voicePackReady || !uiState.voicePackLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = uiState.voicePackLabel ?: "Getting voice models…",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                    val p = uiState.voicePackProgress
                    if (p != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { p.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
                    "LLM", "Hardware" -> viewModel.toggleModelManager()
                    "Brain" -> viewModel.toggleNeuralInfo()
                    "Privacy" -> viewModel.togglePrivacyDashboard()
                    "Memory" -> viewModel.toggleMemoryScreen()
                    "Vending" -> viewModel.toggleStudioScreen()
                    "Files" -> viewModel.toggleAssetBrowser()
                    "NAV" -> viewModel.toggleNavScreen()
                    "Weather" -> viewModel.toggleWeatherScreen()
                    "Background" -> viewModel.toggleBackgroundScreen()
                    "Avatar" -> viewModel.toggleAvatarScreen()
                    "Agents" -> viewModel.toggleAgentsScreen()
                    "Projects" -> viewModel.toggleProjectsScreen()
                    "Tools" -> viewModel.toggleConnectorsScreen()
                    "Notes" -> viewModel.toggleVaultScreen()
                    "Robot" -> viewModel.toggleRobotScreen()
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

        // A possible collision is the most urgent thing on screen — it wins the top slot
        // over the weather and drive cards, so it is impossible to miss while driving.
        uiState.crashAlert?.let { alert ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 48.dp)
            ) {
                CrashAlertCard(
                    message = alert,
                    onTextContact = { viewModel.sendEmergencyText() },
                    onDismiss = { viewModel.dismissCrashAlert() },
                )
            }
        }

        // Drive card: while the user is driving, a glanceable heads-up with speed,
        // G-force and trip stats. Pushed below the weather/crash cards when those
        // are up so the top of the screen doesn't stack three panels.
        if (uiState.driveCardVisible && uiState.crashAlert == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 48.dp)
            ) {
                DriveCard(
                    speedMph = uiState.driveSpeedMph,
                    gForce = uiState.driveGForce,
                    reason = uiState.driveReason,
                    manual = uiState.driveManual,
                    trip = uiState.driveTrip,
                    onDismiss = { viewModel.dismissDriveCard() },
                    roadStatus = if (uiState.roadAlertsEnabled) uiState.roadAlertStatus else null,
                    modifier = Modifier.padding(top = if (uiState.weather != null) 200.dp else 0.dp),
                )
            }
        }

        // Lumen / agentic presence chip — only when flags warrant and hint is non-null.
        val presenceHint = uiState.agenticPresenceHint
            ?: uiState.reflexSummary?.takeIf { uiState.lumenReflexV2Enabled }
        if (presenceHint != null &&
            (uiState.agenticRuntimeV1Enabled || uiState.lumenReflexV2Enabled)
        ) {
            PresenceHintChip(
                hint = presenceHint,
                onReview = if (uiState.agenticPresenceHint != null) viewModel::openAgenticTaskDetails else null,
                onDismiss = if (uiState.agenticDismissibleRevision != null) viewModel::dismissAgenticPresenceHint else null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp),
            )
        }

        // What the agents are doing. Above the avatar, below the full-screen menus, and
        // translucent — handing work to an agent should not take you out of the room.
        AgentActivityPanel(
            tasks = uiState.activeTasks,
            visible = uiState.agentActivityVisible,
            onDismiss = { viewModel.dismissAgentActivity() },
            onOpenAgent = { viewModel.openAgentDetail(it) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp)
                .padding(top = if (uiState.weather != null) 250.dp else 12.dp),
        )

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
                selection = if (uiState.habitatMode) com.example.meowmba.core.background.BackgroundSelection(com.example.meowmba.core.background.BackgroundKind.ANIMATION, uiState.habitatBackgroundId, 15) else uiState.background,
                onSelect = { viewModel.setBackground(it) },
                onClose = { viewModel.toggleBackgroundScreen() },
                ambientWeatherOn = uiState.ambientWeatherEnabled,
                avatarScale = uiState.avatarScale, onAvatarScale = viewModel::setAvatarScale,
                habitatMode = uiState.habitatMode, onHabitatMode = viewModel::setHabitatMode,
            )
        }

        if (uiState.showAvatarScreen) {
            AvatarScreen(
                avatarScale = uiState.avatarScale, onAvatarScale = viewModel::setAvatarScale,
                habitatMode = uiState.habitatMode, onHabitatMode = viewModel::setHabitatMode,
                style = uiState.avatarStyle,
                onSelectStyle = { viewModel.setAvatarStyle(it) },
                animal = uiState.avatarAnimal,
                onSelectAnimal = viewModel::setAvatarAnimal,
                generatedAvatarForm = uiState.generatedAvatarForm,
                savedGeneratedForms = uiState.savedGeneratedForms,
                avatarFormGenerating = uiState.avatarFormGenerating,
                avatarFormMessage = uiState.avatarFormMessage,
                avatarFormError = uiState.avatarFormError,
                onGenerateForm = viewModel::createAvatarForm,
                onRefineForm = viewModel::refineAvatarForm,
                onCancelGenerateForm = viewModel::cancelAvatarFormGeneration,
                onSelectGeneratedForm = viewModel::selectGeneratedAvatarForm,
                onDeleteGeneratedForm = viewModel::deleteGeneratedAvatarForm,
                expressionIntensity = uiState.expressionIntensity,
                onExpressionIntensity = viewModel::setExpressionIntensity,
                playfulness = uiState.playfulness,
                onPlayfulness = viewModel::setPlayfulness,
                diagnostics = {
                    var timingSnapshot by remember { mutableStateOf(viewModel.interactionTimings.snapshot()) }
                    InteractionTimingPanel(timingSnapshot,
                        onEnabledChange = { viewModel.interactionTimings.setEnabled(it); timingSnapshot = viewModel.interactionTimings.snapshot() },
                        onReset = { viewModel.interactionTimings.reset(); timingSnapshot = viewModel.interactionTimings.snapshot() },
                        onRefresh = { timingSnapshot = viewModel.interactionTimings.snapshot() })
                },
                eyeColor = uiState.avatarEyeColor,
                glowColor = uiState.avatarGlowColor,
                onSelectEyeColor = { viewModel.setAvatarEyeColor(it) },
                onSelectGlowColor = { viewModel.setAvatarGlowColor(it) },
                featureColor = uiState.avatarFeatureColor,
                onSelectFeatureColor = viewModel::setAvatarFeatureColor,
                appTheme = uiState.appTheme,
                onSelectTheme = viewModel::setAppTheme,
                appearanceMode = uiState.appearanceMode,
                onSelectAppearance = viewModel::setAppearanceMode,
                preview = {
                    AvatarMotionHost(viewModel, uiState, !avatarWorkAllowed, Modifier.fillMaxSize(), interactive = false)
                },
                animations = viewModel.avatarAnimationNames(),
                onPlayAnimation = { viewModel.playAnimationNamed(it) },
                onClose = { viewModel.toggleAvatarScreen() },
            )
        }

        if (uiState.showNeuralInfo) {
            NeuralInfoPanel(
                diagnostics = uiState.neuralDiagnostics,
                onClose = viewModel::toggleNeuralInfo,
                desktopPaired = uiState.neuralDesktopPaired,
                remoteServerPaired = uiState.maleCnsServer.paired,
                remoteServerName = uiState.maleCnsServer.serverName,
                onSetupBrainServer = viewModel::openNeuralSettingsFromInfo,
                inputNotice = uiState.neuralInputNotice,
                onUseLocalBrain = viewModel::startLocalBrainFromInfo,
                onEnableNeuralControl = viewModel::enableNeuralControlFromInfo,
                onPet = viewModel::petBrainFromInfo,
                onFeed = viewModel::feedBrainFromInfo,
                onOpenSettings = viewModel::openNeuralSettingsFromInfo,
                onPairComputer = viewModel::openBrainPairingFromInfo,
            )
        }

        if (uiState.showNavScreen) {
            NavScreen(
                secureStorage = secureStorage,
                state = uiState,
                onClose = { viewModel.toggleNavScreen() },
            )
        }


        val pendingApprovalId = uiState.agenticPendingApprovalGoalId
        if (pendingApprovalId != null && uiState.agenticRuntimeV1Enabled) {
            AlertDialog(
                onDismissRequest = { viewModel.rejectAgenticGoal(pendingApprovalId) },
                title = { Text("Approve agentic SSH?") },
                text = {
                    Text(
                        "Goal '" + (uiState.agenticPendingApprovalTitle ?: pendingApprovalId).take(80) +
                            "' wants to run a side-effecting ssh_exec. Approve or reject."
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.approveAgenticGoal(pendingApprovalId) }) {
                        Text("Approve")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.rejectAgenticGoal(pendingApprovalId) }) {
                        Text("Reject")
                    }
                },
            )
        }

        if (uiState.showSettings && secureStorage != null) {
            SettingsScreen(
                secureStorage = secureStorage,
                initialPage = uiState.settingsInitialPage,
                onOpenBrain = viewModel::toggleNeuralInfo,
                companionName = uiState.name,
                coverAvatarEnabled = uiState.coverAvatarEnabled,
                onCoverAvatarChange = viewModel::setCoverAvatarEnabled,
                wakeWordEnabled = uiState.wakeWordEnabled,
                requireWakeWordInFace = uiState.requireWakeWordInFace,
                conversationMode = uiState.conversationMode,
                bargeInEnabled = uiState.bargeInEnabled,
                routines = uiState.routines,
                keepScreenOnInFaceMode = uiState.keepScreenOnInFaceMode,
                agentBrainEnabled = uiState.agentBrainEnabled,
                hermesAgentUrl = uiState.hermesAgentUrl,
                imageServerUrl = uiState.imageServerUrl,
                mem0ServerUrl = uiState.mem0ServerUrl,
                preferServerImageGen = uiState.preferServerImageGen,
                videoServerUrl = uiState.videoServerUrl,
                imageModelInstalled = { viewModel.imageModelInstalled() },
                imageModelDownload = uiState.modelDownloads[
                    com.example.meowmba.core.agent.ImageModelCatalog.options.first().id
                ],
                musicServerUrl = uiState.musicServerUrl,
                backgroundListeningEnabled = uiState.backgroundListeningEnabled,
                usbAutoBridgeEnabled = uiState.usbAutoBridgeEnabled,
                robotBodyEnabled = uiState.robotBodyEnabled,
                robotHost = uiState.robotHost,
                robotPort = uiState.robotPort,
                robotStatus = uiState.robotStatus,
                robotUseBluetooth = uiState.robotUseBluetooth,
                robotBtAddress = uiState.robotBtAddress,
                robotScanning = uiState.robotScanning,
                robotScanResults = uiState.robotScanResults,
                carModeEnabled = uiState.carModeEnabled,
                avatarStyle = uiState.avatarStyle,
                onAvatarStyleChange = { viewModel.setAvatarStyle(it) },
                emergencyContact = uiState.emergencyContact,
                onCarModeChange = { viewModel.setCarModeEnabled(it) },
                onEmergencyContactChange = { viewModel.setEmergencyContact(it) },
                onRobotScan = { viewModel.scanForRobotBases() },
                onRobotBodyChange = { viewModel.setRobotBody(it) },
                onRobotHostChange = { viewModel.setRobotHost(it) },
                onRobotPortChange = { viewModel.setRobotPort(it) },
                onRobotUseBluetoothChange = { viewModel.setRobotUseBluetooth(it) },
                onRobotBtAddressChange = { viewModel.setRobotBtAddress(it) },
                onCompanionNameChange = { viewModel.renameCompanion(it) },
                onWakeWordChange = { viewModel.setWakeWordEnabled(it) },
                onRequireWakeInFaceChange = { viewModel.setRequireWakeInFace(it) },
                onConversationModeChange = { viewModel.setConversationMode(it) },

                onBargeInChange = { viewModel.setBargeIn(it) },
                onOpenAccessibility = { viewModel.openAccessibilitySettings() },
                onOpenSetup = { viewModel.toggleSettings(); viewModel.toggleSetupWizard() },
                onOpenVoiceModels = { viewModel.toggleSettings(); viewModel.toggleVoiceModelScreen() },
                onOpenModels = { viewModel.toggleSettings(); viewModel.toggleModelManager() },
                appTheme = uiState.appTheme,
                appearanceMode = uiState.appearanceMode,
                onAppThemeChange = viewModel::setAppTheme,
                onAppearanceModeChange = viewModel::setAppearanceMode,
                idleChatterEnabled = uiState.idleChatterEnabled,
                onIdleChatterChange = { viewModel.setIdleChatter(it) },

                onRoutineToggle = { id, on -> viewModel.setRoutineEnabled(id, on) },
                onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) },
                watchModeEnabled = uiState.watchModeEnabled,
                onWatchModeChange = { viewModel.setWatchMode(it) },
                commentaryEnabled = uiState.commentaryEnabled,
                onCommentaryChange = { viewModel.setCommentaryMode(it) },
                roadAlertsEnabled = uiState.roadAlertsEnabled,
                roadAlertStatus = uiState.roadAlertStatus,
                idleScenesEnabled = uiState.idleScenesEnabled,
                onIdleScenesChange = viewModel::setIdleScenesEnabled,
                onRoadAlertsChange = { viewModel.setRoadAlerts(it) },
                onSaved = { viewModel.rebuildOmniRoute() },
                onAgentBrainChange = { viewModel.setAgentBrainEnabled(it) },
                onHermesUrlChange = { viewModel.setHermesAgentUrl(it) },
                onDownloadImageModel = { viewModel.downloadImageModel() },
                onDeleteImageModel = { viewModel.deleteImageModel() },
                onImageServerUrlChange = { viewModel.setImageServerUrl(it) },
                onMem0ServerUrlChange = { viewModel.setMem0ServerUrl(it) },
                onPreferServerImageGenChange = { viewModel.setPreferServerImageGen(it) },
                onMusicServerUrlChange = { viewModel.setMusicServerUrl(it) },
                onVideoServerUrlChange = { viewModel.setVideoServerUrl(it) },
                onBackgroundListeningChange = { viewModel.setBackgroundListeningEnabled(it) },
                onUsbAutoBridgeChange = { viewModel.setUsbAutoBridge(it) },
                maleCnsServer = uiState.maleCnsServer,
                onPairMaleCnsServer = viewModel::pairMaleCnsServer,
                onUsePairedComputerForMaleCns = viewModel::usePairedComputerForMaleCns,
                onTestMaleCnsServer = viewModel::testMaleCnsServer,
                onPairComputerForMaleCns = viewModel::openBrainPairingFromInfo,
                neuralMode = uiState.neuralMode, neuralSubstrate = uiState.neuralSubstrate,
                neuralTraceConsent = uiState.neuralTraceConsent, neuralDiagnostics = uiState.neuralDiagnostics,
                onNeuralMode = viewModel::setNeuralMode, onNeuralSubstrate = viewModel::setNeuralSubstrate,
                onNeuralTrace = viewModel::setNeuralTraceConsent, onNeuralReset = viewModel::resetNeuralController,
                lumenReflexV2 = uiState.lumenReflexV2Enabled,
                onLumenReflexV2Change = viewModel::setLumenReflexV2,
                agenticRuntimeV1 = uiState.agenticRuntimeV1Enabled,
                onAgenticRuntimeV1Change = viewModel::setAgenticRuntimeV1,
                connectionProfiles = viewModel.listAgenticConnectionProfiles(),
                onSaveConnectionProfile = viewModel::saveAgenticConnectionProfile,
                onDeleteConnectionProfile = viewModel::deleteAgenticConnectionProfile,
                onSaveAgenticCredential = viewModel::saveAgenticCredential,
                agenticGoals = viewModel.listAgenticOpenGoals(),
                agenticModelConfig = viewModel.agenticModelConfigSnapshot(),
                sshCapabilitySummary = viewModel.agenticSshCapabilitySummary(),
                onCreateAgenticGoal = viewModel::createAgenticGoal,
                onTickAgenticGoal = viewModel::tickAgenticGoal,
                onTickOpenAgenticGoals = viewModel::tickOpenAgenticGoals,
                onApproveAgenticGoal = viewModel::approveAgenticGoal,
                onRejectAgenticGoal = viewModel::rejectAgenticGoal,
                onRefreshAgenticTasks = viewModel::refreshAgenticPresenceHint,
                onSaveAgenticModelConfig = viewModel::saveAgenticModelConfig,


                computerPhoneProbeSummary = uiState.computerPhoneProbeSummary,
                computerPhoneReady = uiState.computerPhoneReady,
                computerSetupStatus = uiState.computerSetupStatus,
                computerStatusDetail = uiState.computerStatusDetail,
                computerInstallLog = uiState.computerInstallLog,
                computerPowerState = uiState.computerPowerState,
                computerVncUrl = uiState.computerVncUrl,
                computerSavedProfile = viewModel.computerSavedProfileOrNull(),
                computerShowInstallPrompt = uiState.computerShowInstallPrompt,
                onComputerSaveProfile = viewModel::saveComputerProfile,
                onComputerTestConnection = viewModel::testComputerConnection,
                onComputerInstall = viewModel::installComputerOnServer,
                onComputerOpenTerminal = {
                    viewModel.connectCompanionTerminal(
                        com.example.meowmba.ui.companion.CompanionTerminalTarget.LOCAL_ID
                    )
                },
                onComputerOpenDesktop = viewModel::openComputerDesktopUrl,
                onComputerConfirmInstallPrompt = viewModel::confirmComputerInstallPrompt,
                lumenReflexGain = uiState.lumenReflexGain,
                onLumenReflexGainChange = viewModel::setLumenReflexGain,
                avatarFpsTarget = uiState.avatarFpsTarget,
                onAvatarFpsTargetChange = viewModel::setAvatarFpsTarget,
                computerPower = uiState.computerPowerState,
                onComputerPowerChange = viewModel::setComputerPowerState,
                computerIdleMinutes = uiState.computerIdleMinutes,
                onComputerIdleMinutesChange = viewModel::setComputerIdleMinutes,
                onClose = { viewModel.toggleSettings() }
            )
        }

        if (uiState.showModelManager) {
            ModelManagerScreen(
                state = uiState,
                viewModel = viewModel,
                secureStorage = secureStorage,
                onProviderSettingsSaved = viewModel::reloadSetupConfiguration,
                onModelSelected = { viewModel.onModelSelected(it) },
                onTestCloud = { viewModel.runCloudConnectionTest() },
                onCheckLocal = { viewModel.runLocalModelCheck() },
                onDownloadModel = { viewModel.downloadModel(it) },
                onUseModel = { viewModel.useDownloadedModel(it) },
                onDeleteModel = { viewModel.deleteDownloadedModel(it) },
                onCustomDownload = { url, name -> viewModel.downloadCustomModel(url, name) },
                onUseInstalledFile = { viewModel.useInstalledModelFile(it) },
                onDeleteInstalledFile = { viewModel.deleteInstalledModelFile(it) },
                onScanPhone = viewModel::scanPhoneForModels,
                onImportFoundModel = viewModel::importDiscoveredModel,
                onOpenVoiceModels = { viewModel.toggleVoiceModelScreen() },
                onClose = { viewModel.toggleModelManager() }
            )
        }

        if (uiState.showVoiceModelScreen) {
            VoiceModelScreen(
                state = uiState,
                onSelectBackend = { viewModel.setAsrBackend(it) },
                onDownloadWhisper = { viewModel.downloadWhisperModel() },
                onRepairStreamingModel = { viewModel.repairStreamingAsrModel() },
                onDownloadLocalVoices = { viewModel.downloadLocalVoices() },
                onSelectLocalVoice = { viewModel.selectLocalVoice(it) },
                onPreviewLocalVoice = { viewModel.previewLocalVoice(it) },
                onUseSystemVoice = { viewModel.useSystemVoice() },
                onClose = { viewModel.toggleVoiceModelScreen() }
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

        if (uiState.showAssetBrowser) {
            AssetBrowserScreen(askAi = viewModel::askAssetBrain,
                onProjects = { viewModel.toggleAssetBrowser(); viewModel.toggleProjectsScreen() },
                onClose = viewModel::toggleAssetBrowser, initialPath = uiState.assetBrowserInitialPath)
        }

        if (uiState.showStudioScreen) {
            VendingMachineScreen(
                viewModel = viewModel,
                onClose = { viewModel.toggleStudioScreen() }
            )
        }

        if (uiState.showBusinessAgents) {
            androidx.activity.compose.BackHandler { viewModel.closeBusinessAgents() }
            BusinessAgentsScreen(onOpenFiles = { viewModel.openBusinessFiles() },
                onOpenBugReports = { viewModel.openBusinessFiles(diagnostics = true) }) { viewModel.closeBusinessAgents() }
        }
        if (uiState.showAgentsScreen) {
            AgentsScreen(
                state = uiState,
                onCancelTask = { viewModel.cancelTask(it) },
                onSelectAgent = { viewModel.selectAgent(it) },
                onClose = { viewModel.toggleAgentsScreen() }
            )
        }

        if (uiState.showProjectsScreen) {
            ProjectsScreen(
                state = uiState,
                onOpen = { viewModel.openProject(it) },
                onDelete = { viewModel.deleteProject(it) },
                onClose = { viewModel.toggleProjectsScreen() },
                onCreate = viewModel::createProject, onSavePlan = viewModel::saveProjectPlan,
                onDraftPlan = viewModel::draftProjectPlan, onCancelPlan = viewModel::cancelProjectPlanning,
                onStepDone = viewModel::setProjectStepDone, onContinue = viewModel::continueProject,
                onFiles = viewModel::openProjectFiles, onArtifact = viewModel::openProjectArtifact,
                onCancelTask = viewModel::cancelTask, onRefresh = { viewModel.refreshProjectWork() },
            )
        }

        if (uiState.showToolsScreen) {
            ToolsScreen(
                state = uiState,
                onToggle = { id, on -> viewModel.setRoutineEnabled(id, on) },
                onClose = { viewModel.toggleToolsScreen() }
            )
        }

        if (uiState.showRobotScreen) {
            RobotScreen(
                robotStatus = uiState.robotStatus,
                robotConnected = uiState.robotConnected,
                robotBodyEnabled = uiState.robotBodyEnabled,
                robotUseBluetooth = uiState.robotUseBluetooth,
                robotBtAddress = uiState.robotBtAddress,
                robotHost = uiState.robotHost,
                robotPort = uiState.robotPort,
                robotScanning = uiState.robotScanning,
                robotScanResults = uiState.robotScanResults,
                onRobotBodyChange = { viewModel.setRobotBody(it) },
                onRobotUseBluetoothChange = { viewModel.setRobotUseBluetooth(it) },
                onRobotBtAddressChange = { viewModel.setRobotBtAddress(it) },
                onRobotHostChange = { viewModel.setRobotHost(it) },
                onRobotPortChange = { viewModel.setRobotPort(it) },
                onRobotScan = { viewModel.scanForRobotBases() },
                onStop = { viewModel.stopRobotFromControls() },
                onRefresh = { viewModel.connectRobotFromControls() },
                onClose = { viewModel.toggleRobotScreen() }
            )
        }

        if (uiState.showConnectorsScreen) {
            PluginsConnectorsScreen(
                state = uiState,
                onClose = { viewModel.toggleConnectorsScreen() },
                onSaveServiceKey = { svc, key -> viewModel.saveServiceKey(svc, key) },
                onOpenUrl = { url -> viewModel.openExternalUrl(url) },
                onConnectApp = { pkg, label -> viewModel.connectDiscoveredApp(pkg, label) },
                onCreatePlugin = { name, desc, icon -> viewModel.createUserPlugin(name, desc, icon) },
                onToggleConnector = { id, enabled -> viewModel.setConnectorEnabled(id, enabled) },
                onRemoveConnector = { viewModel.removeConnector(it) },
            )
        }

        // Swipe up = app drawer (launcher); swipe right = chat; swipe left
        // = agent browser (full-screen when folded, split pane when unfolded).
        if (uiState.showAppDrawer) {
            AppDrawerScreen(
                onClose = { viewModel.toggleAppDrawer() },
                pins = pinnedApps,
                onTogglePin = { viewModel.togglePin(it) },
                folders = drawerFolders,
                onCreateFolder = { name, pkg -> viewModel.createDrawerFolder(name, pkg) },
                onAddToFolder = { id, pkg -> viewModel.addAppToDrawerFolder(id, pkg) },
                onRemoveFromFolder = { id, pkg -> viewModel.removeAppFromDrawerFolder(id, pkg) },
                onRenameFolder = { id, name -> viewModel.renameDrawerFolder(id, name) },
                onDeleteFolder = { viewModel.deleteDrawerFolder(it) },
                frequentPackages = DrawerUsage.frequentPackages(
                    drawerUsage,
                    installed = drawerUsage.launches.keys,
                ),
                neverFrequentPackages = drawerUsage.neverFrequent,
                launchCounts = drawerUsage.launches,
                onHideFromFrequent = viewModel::hideFromFrequentApps,
                onSetNeverFrequent = viewModel::setNeverFrequentApp,
                onAllowFrequent = viewModel::allowFrequentApp,
                onRecordLaunch = viewModel::recordDrawerLaunch,

                onSubmitQuery = { raw, count, label ->
                    viewModel.submitDrawerQuery(raw, count, label)
                },
                onSearchGoogle = { viewModel.searchDrawerQuery(it) },
                answer = uiState.captionText,
                thinking = uiState.interactionState == InteractionState.THINKING,
                listening = uiState.interactionState == InteractionState.LISTENING,
                listenPartial = uiState.captionText,
                onMicClick = { viewModel.startDrawerVoiceSearch() },
                onCancelVoice = { viewModel.cancelDrawerVoice() },
                voiceCommit = uiState.drawerVoiceCommit,
                onVoiceConsumed = { viewModel.consumeDrawerVoice() },
            )
        }

        // The in-app WebView browser panel is GONE (replaced by handing pages
        // to a real browser, see showFileInBrowser). It swallowed every
        // utterance while open and covered the face.
        if (uiState.showVaultScreen) {
            val vault = viewModel.vaultRepository
            if (vault != null) {
                VaultScreen(
                    vault = vault,
                    onClose = { viewModel.toggleVaultScreen() }
                )
            }
        }

        ChatOverlay(
            recognitionIssue = uiState.voiceRecognitionStatus,
            isExpanded = uiState.isChatExpanded,
            onClose = { viewModel.toggleChat() },
            onSend = { viewModel.onUserTextSubmitted(it) },
            onDeleteMessage = { viewModel.deleteMessage(it) },
            messages = uiState.chatMessages,
            title = uiState.activeProjectName ?: "Conversation",
            onProjectOverview = if (uiState.activeProjectId != null) viewModel::toggleProjectsScreen else null,
            onLeaveProject = if (uiState.activeProjectId != null) {
                { viewModel.leaveProject() }
            } else null,
            listening = uiState.interactionState == InteractionState.LISTENING,
            listenPartial = uiState.captionText,
            liveReply = uiState.liveAssistantReply.takeIf { uiState.liveReplyConversationId == uiState.activeConversationId },
            thinking = uiState.interactionState == InteractionState.THINKING,
            conversationKey = uiState.activeConversationId.toString(),
            onMicClick = { viewModel.onChatMicClick() },
            callActive = uiState.callMode,
            onCallClick = { viewModel.toggleCallMode() },
            terminalTargets = terminalTargets,
            terminalStatus = terminalUiStatus,
            terminalStatusDetail = terminalUiStatusDetail,
            terminalScrollback = terminalUiScrollback,
            terminalConnectedTargetId = terminalUiConnectedTargetId,
            onTerminalConnect = viewModel::connectCompanionTerminal,
            onTerminalDisconnect = viewModel::disconnectCompanionTerminal,
            onTerminalSendLine = viewModel::sendCompanionTerminalLine,
            onTerminalInterrupt = viewModel::interruptCompanionTerminal,
            onTerminalClear = viewModel::clearCompanionTerminalScrollback,
            onTerminalResize = viewModel::resizeCompanionTerminal,
            onRunTerminalCommand = { targetId, command ->
                viewModel.runCompanionTerminalCommand(targetId, command)
            },
            computerPower = uiState.computerPowerState,
            onComputerPower = viewModel::setComputerPowerState,
            terminalPaneRequest = terminalPaneRequest,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (uiState.callMode && !uiState.isChatExpanded && !showOnboarding) {
            Surface(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.openChat() }) { Text("Voice call active · Open chat") }
                    TextButton(onClick = { viewModel.toggleCallMode() }) { Text("Hang up") }
                }
            }
        }

        // Drawn AFTER ChatOverlay deliberately. In a Box, later children
        // paint on top; while this sat earlier in the file the approval
        // card rendered underneath the open conversation sheet, so a task
        // waiting on approval was invisible and silently timed out.
        // Tool approval card: a risky action is blocked until you decide
        // Thermal banner: NONE/LIGHT never warn; MODERATE needs hold (AvatarWorkPolicy);
        // copy is mild at MODERATE and only stronger at SEVERE+.
        thermalUiBannerText(avatarWorkStatus.warning)?.takeIf {
            faceModeActive(uiState.copy(isChatExpanded = false))
        }?.let { banner ->
            Surface(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
                shape = MaterialTheme.shapes.medium) {
                Text(banner, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        // Above chat and settings, but below approvals and onboarding.
        uiState.generatedMedia?.let { media ->
            ArtifactResultCard(media, uiState.artifactOpenMessage,
                onOpen = { viewModel.showFileInBrowser(java.io.File(media.filePath)) },
                onFiles = viewModel::showGeneratedFiles, onClose = viewModel::dismissGeneratedMedia,
                modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(16.dp)
                    .heightIn(max = (coverConfiguration.screenHeightDp * 0.65f).dp))
        }
        uiState.phoneControlSetupGoal?.let {
            AlertDialog(onDismissRequest = viewModel::dismissPhoneControlSetup,
                title = { Text("Reconnect phone control") },
                text = { Text("Android has disconnected Meow Accessibility. Turn Meow off and back on in Settings → Accessibility → Installed apps, then return here. Retry will ask for task approval again; nothing will be posted automatically.") },
                confirmButton = { TextButton(onClick = viewModel::openAccessibilitySettings) { Text("Open settings") } },
                dismissButton = { Row {
                    TextButton(onClick = viewModel::dismissPhoneControlSetup) { Text("Cancel") }
                    TextButton(onClick = viewModel::retryPhoneControlSetup) { Text("Retry task") }
                } })
        }
        if (uiState.modelSetupRequired && !showOnboarding) {
            Surface(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                shape = MaterialTheme.shapes.medium) {
                TextButton(onClick = viewModel::repairModelSetup) { Text("Repair local AI setup") }
            }
        }
        uiState.pendingApproval?.let { approval ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(approval.title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(approval.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = (coverConfiguration.screenHeightDp * .42f).dp)
                            .verticalScroll(rememberScrollState()))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.resolveApproval(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Deny")
                        }
                        Button(
                            onClick = { viewModel.resolveApproval(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (approval.riskLevel == com.example.meowmba.core.agent.tools.RiskLevel.DESTRUCTIVE)
                                    MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve")
                        }
                    }
                }
            }
        }

        // Setup wizard: full-screen overlay for first-launch or re-open from Settings.
        // Drawn AFTER everything else so it covers the entire companion surface.
        if (showOnboarding) {
            com.example.meowmba.ui.onboarding.SetupWizardRoute(
                onFinished = { viewModel.finishOnboarding() },
                onEnablePermissions = { viewModel.requestOnboardingPermissions() },
                onOpenChat = {
                    viewModel.finishOnboarding()
                    viewModel.openChat()
                },
                onOpenTerminal = {
                    viewModel.finishOnboarding()
                    viewModel.openCompanionTerminalFromOnboarding()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Unfolded split: the browser used to own the right half. That half is
        // free again now that pages go to a real browser instead.
    }
    }
}

/** Collects faceGaze + IMU only here — keeps the rest of CompanionScreen stable. */
@Composable
private fun AvatarMotionHost(
    viewModel: CompanionViewModel,
    uiState: CompanionState,
    paused: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val motionTilt = viewModel.motionScreenLeanDeg.collectAsState()
    val motionSampleTime = viewModel.motionObservedAtMs.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val displayRotation = LocalConfiguration.current.let { view.display?.rotation ?: 0 }
    val motionLean by viewModel.motionLean.collectAsState()
    val motionLurch by viewModel.motionLurch.collectAsState()
    val shakeCount by viewModel.avatarShakeCount.collectAsState()
    val faceGaze by viewModel.faceGaze.collectAsState()
    // Deliberately NOT `by`: delegating would read the value here, in
    // composition, and every TTS word boundary would recompose this screen.
    // Handed to the avatar as a State and read only inside its draw lambda.
    val mouthEnergy = viewModel.mouthEnergy.collectAsState()
    val facePose by viewModel.facePose.collectAsState()
    val chaseHard by viewModel.chaseHard.collectAsState()
    val flyContext = androidx.compose.ui.platform.LocalContext.current
    val flyView = remember(flyContext, uiState.avatarStyle) {
        if (uiState.avatarStyle == AvatarStyle.FULL_FLY) Fly3DView(flyContext) else null
    }
    var flyError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(flyView) { onDispose { flyView?.dispose() } }
    SideEffect { flyView?.setStageActive(!paused); flyView?.onFailure = { flyError = it } }
    Box(modifier = modifier) {
        if (flyView != null) {
            androidx.compose.ui.viewinterop.AndroidView(factory = { flyView }, modifier = Modifier.matchParentSize())
        }
        AvatarRenderer(
            state = uiState,
            onZoneClick = { if (interactive) viewModel.onAvatarZoneClick(it) },
            onSwipeUp = { if (interactive) viewModel.toggleAppDrawer() },
            onSwipeDown = { if (interactive) viewModel.openTiles() },
            onSwipeLeft = { if (interactive) viewModel.toggleBrowser() },
            onSwipeRight = { if (interactive) viewModel.openChat() },
            paused = paused,
            timings = viewModel.interactionTimings,
            displayRefreshRate = view.display?.refreshRate,
            // Statue mode: no gesture consumption either, so a non-interactive
            // stage (the Professional workspace orb) leaves taps to its parent
            // — the clickable that flips the mic.
            pointerEnabled = interactive,
            faceGazeX = faceGaze?.first,
            faceGazeY = faceGaze?.second,
            mouthEnergy = mouthEnergy,
            faceYawDeg = facePose?.yawDeg,
            facePitchDeg = facePose?.pitchDeg,
            faceRollDeg = facePose?.rollDeg,
            faceScale = facePose?.scale,
            motionLean = motionLean,
            motionLurch = motionLurch,
            motionObservedAtMs = motionSampleTime,
            nervousBias = viewModel::nervousBiasNow,
            neuralGaze = viewModel::neuralGazeNow,
            neuralWingPower = viewModel::neuralWingPowerNow,
            neuralFlyMotor = viewModel::neuralFlyMotorNow,
            onPet = viewModel::onAvatarPetted,
            onPlay = { viewModel.playCompanionTrick("peekaboo") },
            onHabitatAction = viewModel::habitatAction,
            onFly3DFrame = flyView?.let { gpu -> { frame: Fly3DFrame -> gpu.publish(frame) } },
            motionScreenLean = motionTilt,
            displayRotation = displayRotation,
            shakeCount = shakeCount,
            chaseHard = chaseHard,
            reflexFeed = { viewModel.reflexSensory.value },
            reflexGain = uiState.lumenReflexGain,
            onReflexSummary = viewModel::onReflexSummary,
        )
        if (flyError != null) {
            androidx.compose.material3.Text("3D graphics unavailable: $flyError", color=MaterialTheme.colorScheme.onSurface,
                modifier=Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp))
        }
    }
}
