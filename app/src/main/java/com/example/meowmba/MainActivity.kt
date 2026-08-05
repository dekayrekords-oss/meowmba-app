package com.example.meowmba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                                visionManager = visionManager,
                                notificationHelper = notificationHelper,
                                routineDao = database.routineDao()
                            ).also {
                                companionViewModel = it
                                if (pendingRoutineId >= 0) {
                                    it.onRoutineIntent(pendingRoutineId)
                                    pendingRoutineId = -1L
                                }
                            } as T
                        }
                    }
                )

                val uiState by viewModel.uiState.collectAsState()

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

                // Handle system back button
                // Every full-screen overlay must be listed here, or back leaves the app
                // from a page that clearly has somewhere to go back TO.
                BackHandler(
                    enabled = uiState.showSettings || uiState.showModelManager || uiState.isChatExpanded ||
                        uiState.showTiles || uiState.showPrivacyDashboard || uiState.showMemoryScreen ||
                        uiState.showAgentsScreen || uiState.showBackgroundScreen ||
                        uiState.showWeatherScreen || uiState.showNavScreen ||
                        uiState.showAppDrawer || uiState.showBrowser
                ) {
                    when {
                        uiState.showAppDrawer || uiState.showBrowser -> viewModel.closeOverlays()
                        uiState.showSettings -> viewModel.toggleSettings()
                        uiState.showModelManager -> viewModel.toggleModelManager()
                        uiState.showPrivacyDashboard -> viewModel.togglePrivacyDashboard()
                        uiState.showMemoryScreen -> viewModel.toggleMemoryScreen()
                        uiState.showAgentsScreen -> viewModel.toggleAgentsScreen()
                        uiState.showBackgroundScreen -> viewModel.toggleBackgroundScreen()
                        uiState.showWeatherScreen -> viewModel.toggleWeatherScreen()
                        uiState.showNavScreen -> viewModel.toggleNavScreen()
                        uiState.isChatExpanded -> viewModel.toggleChat()
                        uiState.showTiles -> viewModel.onAvatarZoneClick(com.example.meowmba.ui.companion.AvatarZone.BACKGROUND)
                    }
                }

                CompanionScreen(
                    viewModel = viewModel,
                    secureStorage = secureStorage
                )
            }
        }

        consumeWakeCommand(intent)
    }

    // ── Background wake-word handoff (Phase 2.5) ────────────────────────────

    override fun onResume() {
        super.onResume()
        // The in-app engine owns the mic while we are visible.
        WakeWordService.stop(this)
        companionViewModel?.reclaimWakeMicForForeground()
        consumeWakeCommand(intent)
    }

    override fun onPause() {
        super.onPause()
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
        val awaitingReply = companionViewModel?.isAwaitingFollowUp() == true
        if (awaitingReply && micGranted) {
            // Mid-conversation background hop: the in-app recognizer goes deaf the
            // moment we lose the foreground, so hand the turn to the mic-exempt
            // foreground service instead of letting the follow-up die unheard.
            companionViewModel?.handOffListeningToBackground()
            WakeWordService.startFollowUp(this)
        } else {
            companionViewModel?.releaseWakeMicForBackground()
            if (wakeOn && backgroundOn && micGranted) {
                WakeWordService.start(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWakeCommand(intent)
    }

    private fun consumeWakeCommand(source: Intent?) {
        val command = source?.getStringExtra(WakeWordService.EXTRA_WAKE_COMMAND)
        if (command != null) {
            source.removeExtra(WakeWordService.EXTRA_WAKE_COMMAND)
            companionViewModel?.onExternalWakeCommand(command)
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
