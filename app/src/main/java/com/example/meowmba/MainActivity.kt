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
    private var companionViewModel: CompanionViewModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
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
                                visionManager = visionManager
                            ).also { companionViewModel = it } as T
                        }
                    }
                )

                val uiState by viewModel.uiState.collectAsState()

                // Vision: the VM asks for CAMERA only when a look needs it
                androidx.compose.runtime.LaunchedEffect(uiState.cameraPermissionRequested) {
                    if (uiState.cameraPermissionRequested) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                // Handle system back button
                BackHandler(
                    enabled = uiState.showSettings || uiState.showModelManager || uiState.isChatExpanded ||
                        uiState.showTiles || uiState.showPrivacyDashboard || uiState.showMemoryScreen ||
                        uiState.showAgentsScreen
                ) {
                    when {
                        uiState.showSettings -> viewModel.toggleSettings()
                        uiState.showModelManager -> viewModel.toggleModelManager()
                        uiState.showPrivacyDashboard -> viewModel.togglePrivacyDashboard()
                        uiState.showMemoryScreen -> viewModel.toggleMemoryScreen()
                        uiState.showAgentsScreen -> viewModel.toggleAgentsScreen()
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
        // The in-app engine owns the mic while we are visible
        WakeWordService.stop(this)
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
        if (wakeOn && backgroundOn && micGranted) {
            WakeWordService.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWakeCommand(intent)
    }

    private fun consumeWakeCommand(source: Intent?) {
        val command = source?.getStringExtra(WakeWordService.EXTRA_WAKE_COMMAND) ?: return
        source.removeExtra(WakeWordService.EXTRA_WAKE_COMMAND)
        companionViewModel?.onExternalWakeCommand(command)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
