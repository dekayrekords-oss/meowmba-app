package com.example.meowmba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    /** Debounced hand-off to the background wake service (see onPause). */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wakeStandbyStart = Runnable { startWakeStandbyNow() }
    private val followUpStart = Runnable { startFollowUpNow() }

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
                                pendingWakeCommand?.let { cmd ->
                                    pendingWakeCommand = null
                                    it.onExternalWakeCommand(cmd)
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
        // If a background hand-off was scheduled by a transient pause, it is now
        // stale: we are visible again and the in-app engine owns the mic.
        mainHandler.removeCallbacks(wakeStandbyStart)
        mainHandler.removeCallbacks(followUpStart)
        WakeWordService.stop(this)
        companionViewModel?.reclaimWakeMicForForeground()
        companionViewModel?.refreshRobot()
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
            // Debounced like the plain start: an immediate startForegroundService
            // here, immediately undone by onResume's stopService during a transient
            // pause (HOME press as the launcher, rotation), broke the platform's
            // startForeground() promise and got the process killed.
            companionViewModel?.handOffListeningToBackground()
            mainHandler.removeCallbacks(followUpStart)
            mainHandler.postDelayed(followUpStart, WAKE_STANDBY_DEBOUNCE_MS)
        } else {
            companionViewModel?.releaseWakeMicForBackground()
            if (wakeOn && backgroundOn && micGranted) {
                // Debounce: a pause that is immediately followed by a resume
                // (rotation, home re-resolution, app-switcher flick) must NOT
                // start the foreground service, because onResume's stopService
                // would then tear it down before startForeground() runs and the
                // platform kills the process for breaking the FGS promise.
                // Genuine backgrounding outlasts the window, so the service
                // still starts for real.
                mainHandler.removeCallbacks(wakeStandbyStart)
                mainHandler.postDelayed(wakeStandbyStart, WAKE_STANDBY_DEBOUNCE_MS)
            }
        }
    }

    /** Re-checks state at fire time, then hands the mic to follow-up capture. */
    private fun startFollowUpNow() {
        if (isFinishing || isChangingConfigurations) return
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        // No conversation-state re-check here: the hand-off that scheduled this
        // runnable already reset the state to IDLE, so re-checking it always failed
        // and the follow-up service never started. onResume cancels this runnable if
        // we returned to the foreground, so reaching this point still backgrounded is
        // itself the confirmation that the follow-up should run.
        if (micGranted) {
            WakeWordService.startFollowUp(this)
        }
    }

    /** Re-checks state at fire time, then starts the background wake service. */
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

    companion object {
        /** Long enough to swallow transient pause/resume churn, short enough that
         *  real backgrounding still gets the wake service promptly. */
        private const val WAKE_STANDBY_DEBOUNCE_MS = 500L
    }

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
