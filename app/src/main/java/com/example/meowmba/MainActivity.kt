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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    /**
     * Asks for everything the companion needs in ONE prompt: microphone, location, camera.
     *
     * Location was previously never requested at runtime, only declared in the manifest —
     * so weather and navigation silently had nothing to work with. Camera was requested
     * lazily on first "look", which is still fine, but including it here means the user
     * grants once instead of being interrupted later.
     */
    private val corePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        granted[Manifest.permission.CAMERA]?.let { companionViewModel?.onCameraPermissionResult(it) }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        companionViewModel?.onCameraPermissionResult(isGranted)
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
        audioStreamingManager = AudioStreamingManager(this)
        visionManager = com.example.meowmba.core.vision.VisionManager(this, this)
        notificationHelper = NotificationHelper(this)
        
        val database = MeowmbaDatabase.getDatabase(this)
        memoryRepository = MemoryRepository(database.messageDao(), database.memoryDao())
        val auditDao = database.auditDao()
        val workspaceRoot = java.io.File(filesDir, "memba_workspace")
        val modelsDir = java.io.File(filesDir, "models")

        checkPermissions()

        setContent {
            MeowmbaTheme(darkTheme = true) {
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
                                // onResume often runs before first composition assigns
                                // the VM — arm always-on / wake here so the face mic
                                // is not left dead until the next resume.
                                it.reclaimWakeMicForForeground()
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

                val uiState by viewModel.uiState.collectAsState()

                // First-launch onboarding gate: computed once from persisted prefs.
                var needsOnboarding by remember {
                    mutableStateOf(com.example.meowmba.core.setup.SetupWiring.needsOnboarding(this@MainActivity))
                }
                // On-demand wizard opened from the Settings "Connect your AI" button.
                val showOnboarding by viewModel.showOnboarding.collectAsState()
                val wizardVisible = needsOnboarding || showOnboarding

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
                    CompanionScreen(
                        viewModel = viewModel,
                        secureStorage = secureStorage
                    )
                    // Onboarding wizard: full-screen overlay above everything.
                    if (wizardVisible) {
                        com.example.meowmba.ui.onboarding.SetupWizardRoute(
                            onFinished = {
                                com.example.meowmba.core.setup.SetupWiring.markOnboardingDone(this@MainActivity)
                                needsOnboarding = false
                                if (showOnboarding) viewModel.toggleSetupWizard()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        consumeWakeCommand(intent)
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
        if (awaitingReply && micGranted) {
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
            if (wakeOn && backgroundOn && micGranted) {
                // See the follow-up branch: synchronous onPause start so the mic FGS
                // is created while the activity is still eligible, not after it stops.
                startWakeStandbyNow()
            }
        }
    }

    /** Follow-up hand-off: the app just went to the background mid-conversation. */
    private fun startFollowUpNow() {
        if (isFinishing || isChangingConfigurations) return
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
    }

    /** Wake command arriving before the ViewModel exists (cold launch). */
    private var pendingWakeCommand: String? = null

    private fun consumeWakeCommand(source: Intent?) {
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
        // Mic, location and camera together. Anything already granted is dropped, so a
        // returning user is not re-prompted for what they have already allowed.
        val core = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            // Declared in the manifest is not enough — querying CalendarProvider2 without
            // the runtime grant threw SecurityException and killed the app.
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            // Auto-connect to a paired LOOI base requires reading bonded devices.
            Manifest.permission.BLUETOOTH_CONNECT,
            // FINDING a base requires BLUETOOTH_SCAN as well, and declaring it
            // in the manifest is not the same as holding it: it is a runtime
            // permission on Android 12+. Without the grant every startScan
            // throws SecurityException, so "Find base (scan)" silently listed
            // only already-bonded devices — which never includes an unpaired
            // LOOI, making the base look undiscoverable.
            Manifest.permission.BLUETOOTH_SCAN,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (core.isNotEmpty()) {
            corePermissionsLauncher.launch(core.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.destroy()
        ttsManager.shutdown()
    }
}
