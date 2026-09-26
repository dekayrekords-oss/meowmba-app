package com.example.meowmba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meowmba.core.audio.AudioStreamingManager
import com.example.meowmba.core.audio.SpeechService
import com.example.meowmba.core.audio.WakeWordService
import com.example.meowmba.core.audio.TextToSpeechManager
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.notification.NotificationHelper
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionScreen
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.theme.MeowmbaTheme

class MainActivity : ComponentActivity() {
    private lateinit var speechService: SpeechService
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var secureStorage: SecureStorageManager
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var audioStreamingManager: AudioStreamingManager
    private lateinit var visionManager: com.example.meowmba.core.vision.VisionManager
    private lateinit var notificationHelper: NotificationHelper
    private var companionViewModel: CompanionViewModel? = null

    /** One-time MediaProjection consent for watch mode. */
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            companionViewModel?.onScreenCaptureGranted(result.resultCode, result.data!!)
        } else {
            companionViewModel?.onScreenCaptureDenied()
        }
    }

    /** Only the two sensors explained by the setup permission page. */
    private val corePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        granted[Manifest.permission.RECORD_AUDIO]?.let { companionViewModel?.onMicrophonePermissionResult(it) }
        granted[Manifest.permission.CAMERA]?.let { companionViewModel?.onCameraPermissionResult(it) }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> companionViewModel?.onMicrophonePermissionResult(granted) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        companionViewModel?.onCameraPermissionResult(isGranted)
    }
    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        companionViewModel?.onQrScanResult(if (result.resultCode == RESULT_OK) result.data?.getStringExtra(com.example.meowmba.core.vision.QrScannerActivity.EXTRA_VALUE) else null,
            result.data?.getStringExtra(com.example.meowmba.core.vision.QrScannerActivity.EXTRA_ERROR))
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        companionViewModel?.onLocationPermissionResult(isGranted)
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted.values.any { it }
        companionViewModel?.onMediaPermissionResult(ok)
    }

    private val cleanupDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        companionViewModel?.onCleanupDeleteFinished(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.example.meowmba.core.workflows.WorkflowRuntime.get(applicationContext).recover() }
            runCatching { com.example.meowmba.core.agentic.AgenticScheduler.recover(applicationContext) }
                .onFailure { android.util.Log.w("BusinessAgents", "Saved workflows could not be resumed; data was preserved") }
        }
        enableEdgeToEdge()
        // Transparent window so the Godot face + glow float over wallpaper.
        // Keep the window touchable — FLAG_NOT_TOUCHABLE must never be set.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        // Dim amount 0: still a proper activity window that owns the touch stream.
        window.setDimAmount(0f)

        speechService = SpeechService(this)
        ttsManager = TextToSpeechManager(this)
        secureStorage = SecureStorageManager(this)
        if (!com.example.meowmba.core.setup.SetupWiring.isOnboardingDone(this)) {
            com.example.meowmba.core.setup.SetupDeviceScan.applyDefaults(
                com.example.meowmba.core.setup.SetupDeviceScan.profile(this), secureStorage)
        }
        com.example.meowmba.core.audio.MicrophonePrivacy.initializeForFirstRun(secureStorage)
        audioStreamingManager = AudioStreamingManager(this)
        visionManager = com.example.meowmba.core.vision.VisionManager(this, this)
        notificationHelper = NotificationHelper(this)
        
        val database = MeowmbaDatabase.getDatabase(this)
        memoryRepository = MemoryRepository(database.messageDao(), database.memoryDao())
        val auditDao = database.auditDao()
        val workspaceRoot = java.io.File(filesDir, "memba_workspace")
        val modelsDir = java.io.File(filesDir, "models")

        // Do not present a wall of permission prompts before first-run guidance.
        // The wizard offers an explicit, explained permission-review action.

        setContent {
                val viewModel: CompanionViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return CompanionViewModel(
                                speechService,
                                ttsManager,
                                secureStorage,
                                memoryRepository,
                                audioStreamingManager,
                                auditDao = auditDao,
                                workspaceRoot = workspaceRoot,
                                modelsDir = modelsDir,
                                downloadServiceStarter = { ctx, url, fn, id, token, dir ->
                                    com.example.meowmba.core.download.DownloadService.start(
                                        ctx, url, fn, id, token, dir
                                    )
                                },
                                visionManager = visionManager,
                                notificationHelper = notificationHelper,
                                routineDao = database.routineDao(),
                                projectDao = database.projectDao(),
                                characterDao = database.characterDao(),
                                glassesController = com.example.meowmba.core.glasses.MetaGlassesController(this@MainActivity)
                            ).also {
                                companionViewModel = it
                                if (pendingAvatarHome) { pendingAvatarHome = false; it.returnToAvatar() }
                if (pendingBusinessAgents) { pendingBusinessAgents = false; it.openBusinessAgents() }
                                // onResume often runs before first composition assigns
                                // the VM — arm always-on / wake here so the face mic
                                // is not left dead until the next resume.
                                it.reclaimWakeMicForForeground()
                                it.resumeFaceTrackingIfEnabled()
                                if (pendingRoutineId >= 0) {
                                    it.onRoutineIntent(pendingRoutineId)
                                    pendingRoutineId = -1L
                                }
                                pendingWakeCommand?.let { cmd ->
                                    pendingWakeCommand = null
                                    it.onExternalWakeCommand(cmd)
                                }
                            } as T
                        }
                    }
                )

                // The factory is skipped when Android retains the ViewModel across a
                // configuration change. Bind this Activity and drain shares on every
                // committed composition so a recreated Activity cannot lose its input.
                androidx.compose.runtime.SideEffect {
                    companionViewModel = viewModel
                    deliverPendingSharedScreen(viewModel)
                    pendingFlowComment?.let { id -> pendingFlowComment = null; viewModel.openFlowComment(id) }
                }
                val uiState by viewModel.uiState.collectAsState()
                val qrRequested by viewModel.qrScannerRequested.collectAsState()
                androidx.compose.runtime.LaunchedEffect(qrRequested) {
                    if (qrRequested) {
                        viewModel.consumeQrScannerRequest()
                        qrScannerLauncher.launch(Intent(this@MainActivity, com.example.meowmba.core.vision.QrScannerActivity::class.java))
                    }
                }
                MeowmbaTheme(
                    darkTheme = uiState.appearanceMode.isDark(androidx.compose.foundation.isSystemInDarkTheme()),
                    dynamicColor = false,
                    professional = uiState.appTheme == com.example.meowmba.ui.companion.AppTheme.PROFESSIONAL,
                ) {
                val sharedScreen by viewModel.screenHelp.collectAsState()
                sharedScreen?.let {
                    com.example.meowmba.ui.flow.ScreenHelpSheet(it, viewModel::askAboutSharedScreen, viewModel::closeScreenHelp)
                }

                // First-launch onboarding gate: computed once from persisted prefs.
                var needsOnboarding by remember {
                    mutableStateOf(com.example.meowmba.core.setup.SetupWiring.needsOnboarding(this@MainActivity))
                }
                // Peek Terminal during setup without marking onboarding done.
                var pauseWizardForTerminal by remember { mutableStateOf(false) }
                // On-demand wizard opened from the Settings "Connect your AI" button.
                val showOnboarding by viewModel.showOnboarding.collectAsState()
                val wizardVisible = (needsOnboarding || showOnboarding) && !pauseWizardForTerminal

                if (!wizardVisible) {
                    com.example.meowmba.ui.companion.BackupSafetyNotice {
                        if (!uiState.showSettings) viewModel.toggleSettings()
                    }
                }

                // When the user closes Chat after an OpenCode peek, return to the wizard.
                androidx.compose.runtime.LaunchedEffect(uiState.isChatExpanded, pauseWizardForTerminal) {
                    if (pauseWizardForTerminal && !uiState.isChatExpanded) {
                        pauseWizardForTerminal = false
                    }
                }

                // Watch mode: launch the system MediaProjection consent when requested.
                val watchConsent by viewModel.screenWatchConsentRequested.collectAsState()
                androidx.compose.runtime.LaunchedEffect(watchConsent) {
                    if (watchConsent) {
                        viewModel.consumeScreenWatchConsentRequest()
                        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE)
                            as android.media.projection.MediaProjectionManager
                        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                    }
                }

                // Face mode: optionally hold the display awake while the bare
                // companion face is on screen (Settings toggle, default ON).
                val keepAwake = uiState.keepScreenOnInFaceMode &&
                    com.example.meowmba.ui.companion.faceModeActive(uiState)
                androidx.compose.runtime.DisposableEffect(keepAwake) {
                    if (keepAwake) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Ask only for microphone access after an explicit voice action,
                // including when the user chose Explore first during setup.
                androidx.compose.runtime.LaunchedEffect(uiState.microphonePermissionRequested) {
                    if (uiState.microphonePermissionRequested) {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                // Vision: the VM asks for CAMERA only when a look needs it
                androidx.compose.runtime.LaunchedEffect(uiState.cameraPermissionRequested) {
                    if (uiState.cameraPermissionRequested) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                // Places: the VM asks for LOCATION only when saving a named place
                androidx.compose.runtime.LaunchedEffect(uiState.locationPermissionRequested) {
                    if (uiState.locationPermissionRequested) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                androidx.compose.runtime.LaunchedEffect(uiState.mediaPermissionRequested) {
                    if (uiState.mediaPermissionRequested) {
                        mediaPermissionLauncher.launch(
                            com.example.meowmba.core.cleanup.PhoneCleanup.mediaPermissions(),
                        )
                    }
                }

                val cleanupTick by com.example.meowmba.core.cleanup.CleanupGate.tick
                    .collectAsState()
                androidx.compose.runtime.LaunchedEffect(cleanupTick) {
                    if (cleanupTick == 0) return@LaunchedEffect
                    val uris = com.example.meowmba.core.cleanup.CleanupGate.take()
                    if (uris.isEmpty()) return@LaunchedEffect
                    val req = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                    cleanupDeleteLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(req.intentSender)
                            .build(),
                    )
                }

                // Handle system back button
                // Every full-screen overlay must be listed here, or back leaves the app
                // from a page that clearly has somewhere to go back TO.
                BackHandler(
                    enabled = wizardVisible || uiState.showSettings || uiState.showModelManager || uiState.isChatExpanded ||
                        uiState.showTiles || uiState.showPrivacyDashboard || uiState.showMemoryScreen ||
                        uiState.showStudioScreen ||
                        uiState.showAgentsScreen || uiState.showBackgroundScreen ||
                        uiState.showWeatherScreen || uiState.showNavScreen ||
                        uiState.showProjectsScreen || uiState.showToolsScreen ||
                        uiState.showAppDrawer || uiState.showBrowser
                ) {
                    when {
                        wizardVisible -> {
                            if (needsOnboarding) {
                                com.example.meowmba.core.setup.SetupWiring.markOnboardingDone(this@MainActivity)
                                needsOnboarding = false
                            }
                            if (showOnboarding) viewModel.toggleSetupWizard()
                        }
                        uiState.showAppDrawer || uiState.showBrowser -> viewModel.closeOverlays()
                        uiState.showSettings -> viewModel.toggleSettings()
                        uiState.showModelManager -> viewModel.toggleModelManager()
                        uiState.showPrivacyDashboard -> viewModel.togglePrivacyDashboard()
                        uiState.showMemoryScreen -> viewModel.toggleMemoryScreen()
                        uiState.showStudioScreen -> viewModel.toggleStudioScreen()
                        uiState.showAgentsScreen -> viewModel.toggleAgentsScreen()
                        uiState.showProjectsScreen -> viewModel.toggleProjectsScreen()
                        uiState.showToolsScreen -> viewModel.toggleToolsScreen()
                        uiState.showBackgroundScreen -> viewModel.toggleBackgroundScreen()
                        uiState.showWeatherScreen -> viewModel.toggleWeatherScreen()
                        uiState.showNavScreen -> viewModel.toggleNavScreen()
                        uiState.isChatExpanded -> viewModel.toggleChat()
                        uiState.showTiles -> viewModel.onAvatarZoneClick(com.example.meowmba.ui.companion.AvatarZone.BACKGROUND)
                    }
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // One frame phase for the avatar and background clocks, so their
                    // redraws land on the same vsync instead of alternating.
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.example.meowmba.ui.companion.LocalSharedFramePhase provides
                            remember { com.example.meowmba.ui.companion.SharedFramePhase() },
                    ) {
                        CompanionScreen(
                            viewModel = viewModel,
                            secureStorage = secureStorage,
                            onboardingVisible = wizardVisible,
                        )
                    }
                    // Retain the current step while the user peeks at Terminal.
                    val wizardState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                    // Onboarding wizard: full-screen overlay above everything.
                    if (wizardVisible) {
                        wizardState.SaveableStateProvider("setup") {
                        com.example.meowmba.ui.onboarding.SetupWizardRoute(
                            onEnablePermissions = { checkPermissions() },
                            onSkipPermissions = viewModel::skipOnboardingPermissions,
                            onFinished = {
                                com.example.meowmba.core.setup.SetupWiring.markOnboardingDone(this@MainActivity)
                                wizardState.removeState("setup")
                                viewModel.reloadSetupConfiguration()
                                needsOnboarding = false
                                pauseWizardForTerminal = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                            },
                            onOpenChat = {
                                com.example.meowmba.core.setup.SetupWiring.markOnboardingDone(this@MainActivity)
                                wizardState.removeState("setup")
                                viewModel.reloadSetupConfiguration()
                                needsOnboarding = false
                                pauseWizardForTerminal = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                                viewModel.openChat()
                            },
                            onOpenTerminal = {
                                com.example.meowmba.core.setup.SetupWiring.markOnboardingDone(this@MainActivity)
                                wizardState.removeState("setup")
                                viewModel.reloadSetupConfiguration()
                                needsOnboarding = false
                                pauseWizardForTerminal = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                                viewModel.openCompanionTerminalFromOnboarding()
                            },
                            onOpenConnectionsSetup = {
                                wizardState.removeState("setup")
                                viewModel.reloadSetupConfiguration()
                                needsOnboarding = false
                                pauseWizardForTerminal = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                                viewModel.openBrainPairingFromInfo()
                            },
                            onOpenNeuralSetup = {
                                wizardState.removeState("setup")
                                viewModel.reloadSetupConfiguration()
                                needsOnboarding = false
                                pauseWizardForTerminal = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                                viewModel.openNeuralSettingsFromInfo()
                            },
                            onStartOpenCode = {
                                pauseWizardForTerminal = true
                                viewModel.startOpenCodeInCompanionTerminal(finishWizard = false)
                            },
                            onAnimalPicked = viewModel::setAvatarAnimal,
                            onAvatarSized = viewModel::setAvatarScale,
                            onAvatarPicked = { style ->
                                viewModel.setAvatarStyle(style)
                            },
                            onBackgroundPicked = { selection ->
                                viewModel.setBackground(selection)
                            },
                            onGitHubTokenSaved = { token ->
                                viewModel.saveServiceKey(
                                    com.example.meowmba.core.setup.GitHubProjectSetup.STORAGE_KEY,
                                    token,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        }
                    }
                }
            }
        }

        consumeWakeCommand(intent)
        consumeSharedScreen(intent)
    }

    // ── Background wake-word handoff (Phase 2.5) ────────────────────────────

    /**
     * Any touch parks the robot body for a moment. Following you tilts the
     * phone — which is the screen you are trying to touch — so a hand on the
     * glass outranks keeping you centred.
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            companionViewModel?.noteScreenTouch()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        // If a background hand-off was scheduled by a transient pause, it is now
        // stale: we are visible again and the in-app engine owns the mic.
        WakeWordService.stop(this)
        companionViewModel?.reclaimWakeMicForForeground()
        companionViewModel?.refreshRobot()
        // Only resume tracking if the user left the camera on. Forcing it
        // here every unlock kept the front camera + ML Kit running and
        // cooked the phone.
        companionViewModel?.resumeFaceTrackingIfEnabled()
        consumeWakeCommand(intent)
    }

    override fun onPause() {
        super.onPause()
        // A configuration change (fold unfold, rotation) pauses and resumes
        // the activity back-to-back: starting the foreground wake service here
        // only for onResume's stopService to tear it down moments later raced
        // the platform's startForeground() deadline and killed the process
        // (ForegroundServiceDidNotStartInTimeException) — as the HOME app that
        // showed up as a black screen. The activity survives the change, so no
        // handoff is needed; the mic never left our ownership.
        if (isChangingConfigurations) return
        // Hand the mic to the foreground service only when the user opted in
        // to BOTH wake phrase and background listening, and mic is granted.
        val wakeOn = secureStorage.getBooleanPreference("wake_word_enabled", false)
        val backgroundOn = secureStorage.getBooleanPreference(WakeWordService.PREF_KEY, false)
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        // Release BEFORE starting the service: it refuses to start while the in-app loop
        // still owns the mic, and the in-app loop cannot actually hear anything once we
        // are backgrounded (foreground-only mic grant).
        // Any live conversation turn (listening/thinking/speaking), not just
        // LISTENING: right after opening an app the state is still SPEAKING the
        // acknowledgement, and gating on LISTENING dropped the follow-up unheard.
        val awaitingReply = companionViewModel?.isConversationLive() == true
        val micEnabled = com.example.meowmba.core.audio.MicrophonePrivacy.isEnabled(secureStorage)
        if (awaitingReply && micGranted && micEnabled) {
            // Mid-conversation background hop: the in-app recognizer goes deaf the
            // moment we lose the foreground, so hand the turn to the mic-exempt
            // foreground service instead of letting the follow-up die unheard.
            //
            // Started SYNCHRONOUSLY here (no debounce): Android 14+ forbids starting
            // a microphone-typed foreground service once the app is fully in the
            // background, and the old debounced start fired after the activity had
            // stopped -> SecurityException every time on targetSdk 36. The service
            // promotes to foreground synchronously in onCreate(), so onResume's
            // stopService for a transient pause cannot beat the startForeground()
            // deadline.
            companionViewModel?.handOffListeningToBackground()
            startFollowUpNow()
        } else {
            companionViewModel?.releaseWakeMicForBackground()
            if (wakeOn && backgroundOn && micGranted && micEnabled) {
                // See the follow-up branch: synchronous onPause start so the mic FGS
                // is created while the activity is still eligible, not after it stops.
                startWakeStandbyNow()
            }
        }
    }

    /** Follow-up hand-off: the app just went to the background mid-conversation. */
    private fun startFollowUpNow() {
        if (isFinishing || isChangingConfigurations) return
        if (!com.example.meowmba.core.audio.MicrophonePrivacy.isEnabled(secureStorage)) return
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            WakeWordService.startFollowUp(this)
        }
    }

    /** Background wake standby, started while the activity is still eligible. */
    private fun startWakeStandbyNow() {
        if (isFinishing || isChangingConfigurations) return
        if (!com.example.meowmba.core.audio.MicrophonePrivacy.isEnabled(secureStorage)) return
        val wakeOn = secureStorage.getBooleanPreference("wake_word_enabled", false)
        val backgroundOn = secureStorage.getBooleanPreference(WakeWordService.PREF_KEY, false)
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (wakeOn && backgroundOn && micGranted) {
            WakeWordService.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWakeCommand(intent)
        consumeSharedScreen(intent)
    }

    /** Wake command arriving before the ViewModel exists (cold launch). */
    private var pendingAvatarHome = false
    private var pendingFlowComment: String? = null
    private var pendingWakeCommand: String? = null
    private var pendingBusinessAgents = false
    private var pendingSharedText: String? = null
    private var pendingSharedImage: android.net.Uri? = null
    private var pendingSharedAt: Long = 0

    private fun deliverPendingSharedScreen(vm: CompanionViewModel) {
        val text = pendingSharedText ?: return
        val image = pendingSharedImage
        val at = pendingSharedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        pendingSharedText = null
        pendingSharedImage = null
        pendingSharedAt = 0
        vm.onExternalScreenShare(text, image, at)
    }

    private fun consumeSharedScreen(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND) return
        val text = source.getStringExtra(Intent.EXTRA_TEXT)?.take(4000)
        val image = source.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.takeIf { it.scheme == "content" }
        val observedAt = if (image?.authority == "$packageName.fileprovider")
            source.getLongExtra(com.example.meowmba.core.screen.ScreenCaptureOnceService.EXTRA_CAPTURED_AT, System.currentTimeMillis())
            else System.currentTimeMillis()
        if (text == null && image == null) return
        val vm = companionViewModel
        if (vm == null) {
            pendingSharedText = text ?: ""
            pendingSharedImage = image
            pendingSharedAt = observedAt
        } else {
            vm.onExternalScreenShare(text ?: "", image, observedAt)
        }
        source.action = null
    }

    private fun consumeWakeCommand(source: Intent?) {
        source?.getStringExtra(com.example.meowmba.core.flow.delivery.AndroidFlowNotifier.EXTRA_COMMENT_FLOW)?.let { id ->
            companionViewModel?.openFlowComment(id) ?: run { pendingFlowComment = id }
            source.removeExtra(com.example.meowmba.core.flow.delivery.AndroidFlowNotifier.EXTRA_COMMENT_FLOW)
        }
        if (source?.hasExtra(com.example.meowmba.core.flow.delivery.AndroidFlowNotifier.EXTRA_OPEN_FLOW) == true) {
            secureStorage.savePreference("app_theme", "flow")
            companionViewModel?.setAppTheme(com.example.meowmba.ui.companion.AppTheme.FLOW)
            source.removeExtra(com.example.meowmba.core.flow.delivery.AndroidFlowNotifier.EXTRA_OPEN_FLOW)
        }
        if (com.example.meowmba.ui.companion.isAvatarHomeIntent(source?.action, source?.categories)) {
            companionViewModel?.returnToAvatar() ?: run { pendingAvatarHome = true }
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        if (source?.getBooleanExtra(com.example.meowmba.core.workflows.BusinessNotifications.OPEN_TEAM, false) == true) {
            source.removeExtra(com.example.meowmba.core.workflows.BusinessNotifications.OPEN_TEAM)
            companionViewModel?.openBusinessAgents() ?: run { pendingBusinessAgents = true }
        }
        val command = source?.getStringExtra(WakeWordService.EXTRA_WAKE_COMMAND)
        if (command != null) {
            source.removeExtra(WakeWordService.EXTRA_WAKE_COMMAND)
            val vm = companionViewModel
            if (vm != null) {
                vm.onExternalWakeCommand(command)
            } else {
                // Cold launch: the ViewModel is created during the first
                // composition, which happens after onCreate's intent handling.
                // Dropping the command here is why a tapped wake-command
                // notification sometimes did nothing.
                pendingWakeCommand = command
            }
        }
        // Phase E: a routine alarm brought us to the front.
        val routineId = source?.getLongExtra(
            com.example.meowmba.core.routines.RoutineScheduler.EXTRA_ROUTINE_ID, -1L
        ) ?: -1L
        if (routineId >= 0) {
            source?.removeExtra(com.example.meowmba.core.routines.RoutineScheduler.EXTRA_ROUTINE_ID)
            val vm = companionViewModel
            if (vm != null) vm.onRoutineIntent(routineId) else pendingRoutineId = routineId
        }
    }

    /** Routine alarm arriving before the ViewModel exists. */
    private var pendingRoutineId: Long = -1L

    private fun checkPermissions() {
        companionViewModel?.prepareOnboardingPermissions()
        val core = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA).filter {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (it == Manifest.permission.RECORD_AUDIO) companionViewModel?.onMicrophonePermissionResult(true)
                else companionViewModel?.onCameraPermissionResult(true)
            }
            !granted
        }
        if (core.isNotEmpty()) {
            corePermissionsLauncher.launch(core.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // VisionManager owns a single-thread executor plus a 10 Hz watchdog that
        // nothing cancelled while tracking was on, so without this every Activity
        // recreate leaks a live thread and a scheduled wakeup. CameraX is unbound
        // asynchronously; we never block here on the provider.
        if (::visionManager.isInitialized) visionManager.close()
        speechService.destroy()
        ttsManager.shutdown()
    }
}
