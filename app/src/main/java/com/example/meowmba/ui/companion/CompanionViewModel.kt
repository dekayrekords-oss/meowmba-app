package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.speech.SpeechRecognizer
import com.example.meowmba.core.agent.LiteRtProvider
import com.example.meowmba.core.robot.perform
import com.example.meowmba.core.agent.ImageModelCatalog
import com.example.meowmba.core.agent.ImageModelOption
import com.example.meowmba.core.agent.LocalModelOption
import com.example.meowmba.core.agent.MediaPipeProvider
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.download.DownloadService
import com.example.meowmba.core.agent.ModelDownloader
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.agent.runtime.AgentOrchestrator
import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.plugins.ConnectorManager
import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.AgentBrainRouter
import com.example.meowmba.core.agent.runtime.ClockAnswers
import com.example.meowmba.core.agent.runtime.AuditSink
import com.example.meowmba.core.agent.runtime.HermesAgentBrain
import com.example.meowmba.core.agent.runtime.HermesConfig
import com.example.meowmba.core.agent.runtime.LlmToolCallRouter
import com.example.meowmba.core.agent.runtime.SubmitResult
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.agent.runtime.ToolCallPromptMode
import com.example.meowmba.core.agent.tools.FindPlacesTool
import com.example.meowmba.core.agent.tools.SaveCharacterTool
import com.example.meowmba.core.agent.tools.GetCharacterTool
import com.example.meowmba.core.agent.tools.CreateComicTool
import com.example.meowmba.core.agent.tools.GlassesTakePhotoTool
import com.example.meowmba.core.agent.tools.WriteScriptTool
import com.example.meowmba.core.agent.tools.MemoryRecallTool
import com.example.meowmba.core.agent.tools.WebBrowserTool
import com.example.meowmba.core.agent.tools.DesktopControlTool
import com.example.meowmba.core.agent.tools.SetAlarmTool
import com.example.meowmba.core.agent.tools.SetTimerTool
import com.example.meowmba.core.agent.tools.FlashlightTool
import com.example.meowmba.core.agent.tools.DndTool
import com.example.meowmba.core.agent.tools.BatteryStatusTool
import com.example.meowmba.core.agent.tools.SetVolumeTool
import com.example.meowmba.core.agent.tools.TakePhotoTool
import com.example.meowmba.core.agent.tools.ComposeSmsTool
import com.example.meowmba.core.agent.tools.DialCallTool
import com.example.meowmba.core.agent.tools.MediaControlTool
import com.example.meowmba.core.agent.tools.SetBrightnessTool
import com.example.meowmba.core.agent.tools.UiAutomationTool
import com.example.meowmba.core.agent.tools.SshTool
import com.example.meowmba.core.agent.tools.SshAddHostTool
import com.example.meowmba.core.agent.tools.WeatherTool
import com.example.meowmba.core.agent.tools.ListSavedPlacesTool
import com.example.meowmba.core.agent.tools.NavigateTool
import com.example.meowmba.core.agent.tools.SavePlaceTool
import com.example.meowmba.core.agent.tools.FileDeleteTool
import com.example.meowmba.core.agent.tools.FileListTool
import com.example.meowmba.core.agent.tools.FileReadTool
import com.example.meowmba.core.agent.tools.FileWriteTool
import com.example.meowmba.core.agent.tools.AvatarAnimateTool
import com.example.meowmba.core.agent.tools.DescribeAnimationTool
import com.example.meowmba.core.agent.tools.MemorySaveTool
import com.example.meowmba.core.agent.tools.RememberingTool
import com.example.meowmba.core.agent.tools.MemorySearchTool
import com.example.meowmba.core.agent.tools.ProjectCreateTool
import com.example.meowmba.core.agent.tools.TaskListTool
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.registerInventoryTools
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.agent.tools.DesktopWriteFileTool
import com.example.meowmba.core.agent.tools.DesktopRunBuildTool
import com.example.meowmba.core.agent.tools.DesktopRunScriptTool
import com.example.meowmba.core.agent.tools.WebSearchTool
import com.example.meowmba.core.agent.tools.WebFetchTool
import com.example.meowmba.core.agent.tools.registerVaultTools
import com.example.meowmba.core.vault.NoteSessionIntent
import com.example.meowmba.core.vault.NoteSessionParser
import com.example.meowmba.core.vault.VaultRepository
import com.example.meowmba.core.agent.tools.EmailSendTool
import com.example.meowmba.core.agent.tools.EmailDraftTool
import com.example.meowmba.core.agent.tools.CalendarListTool
import com.example.meowmba.core.agent.tools.CalendarCreateTool
import com.example.meowmba.core.agent.tools.MediaGenerateTool
import com.example.meowmba.core.agent.tools.MediaGenerateVideoTool
import com.example.meowmba.core.agent.tools.MediaGenerateAudioTool
import com.example.meowmba.core.agent.tools.LaunchAppTool
import com.example.meowmba.core.agent.tools.ListAppsTool
import com.example.meowmba.core.agent.tools.PhoneCleanupTool
import com.example.meowmba.core.cleanup.CleanupSpeech
import com.example.meowmba.core.cleanup.PhoneCleanup
import com.example.meowmba.core.agent.tools.PlayMusicTool
import com.example.meowmba.core.agent.tools.OpenUrlTool
import com.example.meowmba.core.agent.tools.OpenSettingsTool
import com.example.meowmba.core.agent.tools.CarStatusTool
import com.example.meowmba.core.agent.tools.CarDriveModeTool
import com.example.meowmba.core.agent.tools.CarTripTool
import com.example.meowmba.core.agent.tools.CarEtaTool
import com.example.meowmba.core.agent.tools.CarHelpTool
import com.example.meowmba.core.car.DrivingDetector
import com.example.meowmba.core.car.TripTracker
import com.example.meowmba.core.car.CrashEvent
import com.example.meowmba.core.media.SDCppImageGenerator
import com.example.meowmba.core.audio.AudioStreamingManager
import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.bridge.UsbAutoBridgeManager
import com.example.meowmba.core.audio.RecognizerWakeWordEngine
import com.example.meowmba.core.audio.SelfVoiceFilter
import com.example.meowmba.core.audio.SherpaAsrEngine
import com.example.meowmba.core.audio.SherpaKwsEngine
import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.core.audio.SpeechSynthesizer
import com.example.meowmba.core.audio.EnrollPhraseParser
import com.example.meowmba.core.audio.SpeechService
import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.audio.WakeWordEngine
import com.example.meowmba.core.vision.NewPersonAsk
import com.example.meowmba.core.vision.TalkerPicker
import com.example.meowmba.core.database.AuditDao
import com.example.meowmba.core.database.AuditEntryEntity
import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.database.MessageRole
import com.example.meowmba.core.memory.EmbeddingInterface
import com.example.meowmba.core.memory.LocalHashEmbedder
import com.example.meowmba.core.memory.MemoryCommandParser
import com.example.meowmba.core.memory.MemoryExporter
import com.example.meowmba.core.memory.IpcMetaMemoryConnector
import com.example.meowmba.core.memory.Mem0MetaMemoryConnector
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.memory.MetaMemoryConnector
import com.example.meowmba.core.memory.NoOpMetaMemoryConnector
import com.example.meowmba.core.memory.PromptBuilder
import com.example.meowmba.core.memory.RecallEngine
import com.example.meowmba.core.memory.RecallResult
import com.example.meowmba.core.notification.NotificationHelper
import com.example.meowmba.core.security.CleartextPolicy
import com.example.meowmba.core.security.SecureStorageManager
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ViewModel managing the logic for the Memba companion.
 *
 * Voice pipeline (Phase 2):
 *  - Push-to-talk and tap-to-toggle via [onMicClick]
 *  - Optional always-on wake phrase ("Hey Memba" / "Hey Meow" / "Hey [name]")
 *    via a swappable [WakeWordEngine] that runs locally while the app is open
 *  - Conversation mode: after speaking, the companion stays for follow-ups
 *  - Interruptible speech: say the wake phrase (or tap the mic) while the
 *    companion is talking to cut it off and speak a new command
 *  - Audio focus is requested for the whole interaction and released on idle
 *  - TTS word boundaries pulse [CompanionState.mouthEnergy] to sync the mouth
 */
class CompanionViewModel(
    private val speechService: SpeechEngine? = null,
    private val ttsManager: SpeechSynthesizer? = null,
    private val secureStorage: SecureStorageManager? = null,
    private val memoryRepository: MemoryRepository? = null,
    private val audioFocusManager: AudioStreamingManager? = null,
    private val embedder: EmbeddingInterface = LocalHashEmbedder(),
    private val auditDao: AuditDao? = null,
    private val workspaceRoot: java.io.File? = null,
    /** App-private directory for downloaded on-device models. */
    private val modelsDir: java.io.File? = null,
    /** Injectable for tests; defaults to a real downloader over [modelsDir]. */
    private val modelDownloader: ModelDownloader? = null,
    /**
     * Starts the foreground download service (survives backgrounding). Null in
     * tests: [downloadModel]/[downloadCustomModel] then download in-process via
     * the injected [modelDownloader], which is what the plain-JUnit tests drive.
     */
    private val downloadServiceStarter: ((android.content.Context, String, String, String, String?, java.io.File) -> Unit)? = null,
    /** Dispatcher for blocking work (on-device inference). Injected for tests. */
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    /** CameraX capture for the vision path; null in tests → honest "no camera". */
    private val visionManager: com.example.meowmba.core.vision.VisionManager? = null,
    private val notificationHelper: NotificationHelper? = null,
    /** MetaMemory ecosystem link. Injected in tests; see [metaMemory] for the default. */
    private val metaMemoryConnector: MetaMemoryConnector? = null,
    /** Phase E: scheduled routines. Null in unit tests. */
    private val routineDao: com.example.meowmba.core.database.RoutineDao? = null,
    /** Saved work sessions. Null in unit tests. */
    private val projectDao: com.example.meowmba.core.database.ProjectDao? = null,
    /** Studio character bibles. Null in unit tests; tools degrade honestly. */
    private val characterDao: com.example.meowmba.core.database.CharacterDao? = null,
    /** Meta Ray-Ban glasses (MWDAT frame bus + status). Null in unit tests. */
    internal val glassesController: com.example.meowmba.core.glasses.MetaGlassesController? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionState())
    private var idleAnimationDirector: IdleAnimationDirector? = null
    val uiState = _uiState.asStateFlow()
    private var cachedMessages: List<com.example.meowmba.core.database.MessageEntity> = emptyList()

    /**
     * High-frequency face-track offsets. Kept OFF [uiState] so each sample does
     * not recompose weather/tiles/chat/settings — that was the choppiness.
     * Avatar look is unchanged; only the Compose subscription graph is fixed.
     */
    private val _faceGaze = MutableStateFlow<Pair<Float, Float>?>(null)
    val faceGaze = _faceGaze.asStateFlow()

    /** Full six-DOF face pose (translation + rotation + distance) for the avatar. */
    private val _facePose = MutableStateFlow<com.example.meowmba.core.vision.FacePose6Dof?>(null)
    val facePose: kotlinx.coroutines.flow.StateFlow<com.example.meowmba.core.vision.FacePose6Dof?> =
        _facePose.asStateFlow()
    private val _chaseHard = MutableStateFlow(false)
    val chaseHard = _chaseHard.asStateFlow()
    private var lastSocialCue: com.example.meowmba.core.vision.SocialCue =
        com.example.meowmba.core.vision.SocialCue.NONE
    private var lastSocialCueAtMs: Long = 0L

    fun getVisionManager(): com.example.meowmba.core.vision.VisionManager? = visionManager

    /** Named GPS places ("remember this as my mailbox"). */
    private val placesStore: com.example.meowmba.core.nav.PlacesStore? by lazy {
        secureStorage?.let { com.example.meowmba.core.nav.PlacesStore(it) }
    }

    /** Active model provider. Internal (not private) so tests can inject fakes. */
    internal var currentProvider: ModelProvider? = null

    /** Tools available for agent routing; null when no workspace/repo exists. */
    private var agentToolRegistry: ToolRegistry? = null

    /** Plugins & MCP connectors the user has added (apps / MCP servers / user plugins). */
    val connectorManager: ConnectorManager? by lazy {
        val ctx = secureStorage?.context
        if (ctx == null) null
        else ConnectorManager(ctx, secretProvider = { id -> secureStorage?.getApiKey(id) })
    }

    /**
     * Weather source. Open-Meteo needs no API key and no account, so "what's the weather"
     * works out of the box with nothing for the user to configure.
     */
    /** Obsidian-compatible note vault stored in app-private files/vault/. */
    val vaultRepository: VaultRepository? by lazy {
        secureStorage?.context?.let { VaultRepository(File(it.filesDir, "vault")) }
    }

    private val weatherRepository: com.example.meowmba.core.weather.WeatherRepository? by lazy {
        val ctx = secureStorage?.context
        if (ctx == null) null else com.example.meowmba.core.weather.WeatherRepository(ctx)
    }

    /**
     * Meow Inventory, over the app-to-app bridge.
     *
     * Lazy and null-safe: the companion works fine on a phone without the inventory app,
     * and every tool reports that in a sentence rather than failing silently.
     */
    val inventoryClient: com.example.meowmba.core.inventory.InventoryBridgeClient? by lazy {
        secureStorage?.context?.let {
            com.example.meowmba.core.inventory.InventoryBridgeClient(it)
        }
    }

    /**
     * MetaMemory ecosystem link. Glasses captures arrive through
     * `MeowmbaRuntimeService` writing Room directly, so the connector's live
     * job today is answering "is the producer app even installed" — honestly,
     * via the no-op when there is no context/repository to check with.
     */
    internal val metaMemory: MetaMemoryConnector by lazy {
        metaMemoryConnector
            ?: if (secureStorage?.context != null && memoryRepository != null) {
                IpcMetaMemoryConnector(secureStorage!!.context, memoryRepository!!)
            } else {
                NoOpMetaMemoryConnector()
            }
    }

    /**
     * meow-mem0 semantic memory adapter. Enabled only when a server URL is
     * configured (Settings), never assumed; [Mem0MetaMemoryConnector.isAvailable]
     * does a real /health check before anything claims a link exists. The
     * per-install user_id is persisted so every device/account scopes its own
     * semantic memories on the shared store.
     */
    internal val mem0: MetaMemoryConnector by lazy {
        val url = _uiState.value.mem0ServerUrl.trim().trimEnd('/')
        if (url.isBlank() || secureStorage == null) {
            NoOpMetaMemoryConnector()
        } else {
            val storage = secureStorage!!
            val userId = storage.getPreference(KEY_MEM0_USER_ID)
                ?: java.util.UUID.randomUUID().toString()
                    .also { storage.savePreference(KEY_MEM0_USER_ID, it) }
            Mem0MetaMemoryConnector(baseUrl = url, userId = userId)
        }
    }

    /** Model downloads into app-private storage, HF token from encrypted prefs. */
    private val downloader: ModelDownloader? by lazy {
        modelDownloader ?: modelsDir?.let { dir ->
            ModelDownloader(dir, hfTokenProvider = { secureStorage?.getApiKey("huggingface") })
        }
    }

    /**
     * Hermes Agent backend, built from [CompanionState.hermesAgentUrl].
     * Held as a single instance (no per-call client leaks) and internal so
     * tests can substitute a fake — this is the agent-framework adapter seam.
     */
    internal var hermesBrain: AgentBrainRouter? = null

    private var matcher = WakePhraseMatcher()
    private var wakeWordEngine: WakeWordEngine? = null
    /** On-device always-on STT. Used when AiAi/Google SpeechRecognizer is wedged. */
    private var sherpaAsrEngine: SherpaAsrEngine? = null

    /**
     * Cloud STT, wrapping [sherpaAsrEngine] for capture + endpointing. Only
     * built when the user picks [AsrBackend.GEMINI_CLOUD] on the Hardware →
     * Voice Recognition page — audio must never leave the device by default.
     */

    /**
     * Persisted voice-recognition backend choice.
     *
     * MUST stay declared above the `init` block. Kotlin runs property
     * initialisers in declaration order, so a copy of this further down the
     * file is still null while `init` → [ensureVoiceModelsThenAttachWake] →
     * [refreshAsrBackendState] runs — which NPEs every ViewModel-constructing
     * test on a non-null enum field.
     */
    private var asrBackend: com.example.meowmba.core.audio.AsrBackend =
        com.example.meowmba.core.audio.AsrBackend.DEFAULT
    private var speakerVerifier: com.example.meowmba.core.audio.SpeakerVerifier? = null
    private val faceSpeaker = HashMap<Int, String>()
    private var lastSpeakerName: String? = null
    private val faceFirstSeen = HashMap<Int, Long>()
    private val askedNameIds = HashSet<Int>()
    private var awaitingNameFaceId: Int? = null
    private var awaitingNameUntilMs = 0L
    private var lastNameAskAtMs = 0L

    /** USB auto-bridge manager — auto-pairs when tethered. */
    private var usbAutoBridge: UsbAutoBridgeManager? = null

    /** Suppresses the companion hearing its own TTS voice during barge-in. */
    private val selfVoiceFilter = SelfVoiceFilter()

    // ── Agent system (Phase 5) ──────────────────────────────────────────────
    private var agentOrchestrator: AgentOrchestrator? = null

    /**
     * Audit trail: ring-buffered into UI state and persisted to Room.
     *
     * The in-memory copy is written straight away so the Agents screen updates without
     * waiting on a database round-trip; [observeAuditTrail] then replaces it with what
     * Room actually holds, which is what makes an agent's history survive a restart.
     */
    private val agentAuditSink = object : AuditSink {
        override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {
            val entry = AuditEntryEntity(agentId = agentId, taskId = taskId, action = action, detail = detail)
            _uiState.value = _uiState.value.copy(
                auditEntries = (listOf(entry) + _uiState.value.auditEntries).take(200)
            )
            runCatching { auditDao?.insert(entry) }
        }
    }

    /** Last captured JPEG bytes for follow-up focus queries (Phase 7b). */
    private var lastVisionJpeg: ByteArray? = null

    private var responseJob: Job? = null
    /**
     * Bumped on every new spoken turn. Old Ktor streams are NOT cancelled —
     * their OkHttp cancel handler is fatal on this device (Unbalanced enter/exit).
     * Stale jobs just stop writing the UI / TTS when their epoch no longer matches.
     */
    private var responseEpoch = 0L
    private val swallowAsyncFatals = kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
        fuLog("swallowed async fatal: ${t.javaClass.simpleName}: ${t.message}")
    }

    /** Backstop that returns the machine to IDLE if a turn never completes. */
    private var thinkingWatchdogJob: Job? = null

    /**
     * Where the self-improving harness keeps what it has learned. A plain JSON
     * document in filesDir so the user can read and delete it.
     */
    private val harnessStore by lazy {
        // cacheDir fallback keeps unit tests (no SecureStorage) from crashing;
        // on device secureStorage.context is always present.
        val dir = secureStorage?.context?.filesDir
            ?: java.io.File(System.getProperty("java.io.tmpdir") ?: ".", "meowmba-test")
        com.example.meowmba.core.agent.prime.HarnessStore(
            java.io.File(dir, "prime_harness.json"),
        )
    }

    /**
     * What the harness has learned, ready for the prompt — or null when it has
     * learned nothing. Read fresh each turn rather than cached, because the
     * heartbeat can change it between turns and a stale copy would quietly
     * keep advising on a problem that has since been fixed.
     */
    private fun learnedBlock(): String? =
        runCatching { harnessStore.load().promptBlock() }.getOrNull()

    /**
     * The trajectory /refine reviews: what the agents actually did, newest
     * first. Reading the audit log means lessons are grounded in this device's
     * real history rather than a model's impression of it.
     */
    private suspend fun recentTrajectory(): List<com.example.meowmba.core.agent.prime.RefineEngine.Step> =
        runCatching {
            auditDao?.getRecent()?.first()?.map { e ->
                com.example.meowmba.core.agent.prime.RefineEngine.Step(
                    timestamp = e.timestamp,
                    action = e.action,
                    detail = e.detail,
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    private var followUpJob: Job? = null

    /** Ambient sky refresh loop; null when the ambient scene is off. */
    private var ambientWeatherJob: Job? = null
    private var roadFeedJob: Job? = null

    /** The turn currently being captured, so a new one can cancel it cleanly. */
    private var utteranceCapture: com.example.meowmba.core.audio.UtteranceCapture? = null
    /** Silent-retry budget for transient recognizer errors (e.g. code 11). */
    private var transientListenRetries = 0

    /**
     * Last seen status per task, so only real transitions are narrated.
     *
     * Declared with the other fields, NOT down beside the function that uses it: the task
     * collector starts inside init{}, and a property declared later in the class body is
     * still null when it first fires — which is a NullPointerException on the very first
     * agent update.
     */
    private val narratedTaskStatus = mutableMapOf<String, TaskStatus>()

    /** Keeps the agent panel up briefly after the last task finishes. */
    private var agentPanelLingerJob: Job? = null

    /** Auto-clears an invented animation when it finishes. */
    private var customAnimJob: Job? = null

    /** One bounded retry when the wake mic was busy at re-arm time. */
    private var wakeRearmJob: Job? = null
    private var mouthDecayJob: Job? = null

    /** Set when a deliberate interruption stops TTS, so the resulting
     *  onSpeechFinished callback is ignored exactly once. */
    private var suppressSpeechFinished = false

    /** True while capturing a follow-up turn inside conversation mode. */
    private var isFollowUpListen = false

    /** Always-on conversation mode (face mode + screen on). See [startAlwaysOnListening]. */
    private var alwaysOnActive = false
    private var lastSeenFaceAtMs = 0L
    private var lastHeldFaceX: Float? = null
    private var lastHeldFaceY: Float? = null
    private var lastHeldFaceScale: Float = 1f
    /** Wall-clock of last always-on startListening — debounce AiAi thrash. */
    private var lastAlwaysOnArmAtMs = 0L

    /**
     * True while the always-on recognizer session is open. A plain session flag
     * rather than an [com.example.meowmba.core.audio.UtteranceCapture]: always-on
     * listens session-by-session (a watchdog that pings forever would keep the
     * test virtual clock busy), re-arming from session end/resetState.
     */
    private var alwaysOnSessionOpen = false
    /** See the echo-guard comment in [armAlwaysOnCapture]. */
    private var alwaysOnOpenedWhileSpeaking = false

    /** Clears [alwaysOnSessionOpen] if the recognizer never calls back. */
    private var alwaysOnSessionWatchdog: Job? = null

    /**
     * The engine the currently-open always-on session was armed on. Re-arm
     * idempotence must compare against THIS, not against hard-coded engine
     * singletons: a fake/test engine (or any future provider) is neither
     * `speechService` nor a [com.example.meowmba.core.audio.SherpaAsrEngine],
     * and an open-session guard that never matches makes every re-arm fall
     * through — under the test virtual clock that is an infinite
     * arm→micNap→re-arm loop that never lets advanceUntilIdle finish.
     */
    private var alwaysOnCaptureEngine: com.example.meowmba.core.audio.SpeechEngine? = null

    /**
     * AiAi vs Sherpa. One object so a wedge cannot leave always-on, the mic
     * button and the wake word on a dead recognizer with no fallback.
     */
    private val voiceHealth = com.example.meowmba.core.audio.VoiceHealth()
    /** Last time we accepted a real utterance. Used to nap the open mic. */
    private var lastUsableSpeechAtMs = System.currentTimeMillis()

    /** Last turn text committed through [processUserSpeech], for dedupe. */
    private var lastCommittedTurnText: String? = null
    private var lastCommittedTurnAtMs = 0L
    private var lastSpokeFinishedAtMs = 0L
    private var micNapJob: Job? = null
    private var alwaysOnRearmJob: Job? = null
    private var alwaysOnCommitJob: Job? = null
    private var alwaysOnPartialJob: Job? = null

    /**
     * Last non-blank partial heard by the open mic, per session. AiAi on this
     * device ends a session that HEARD speech with ERROR_NO_MATCH and no final
     * result, so the partial is the only record of what was said — without it
     * the always-on capture would drop every utterance ("voice chat never
     * answers").
     */
    private var lastAlwaysOnPartial = ""

    /**
     * Silent-window re-arms performed by the watchdog since the last recognizer
     * terminal. A terminal (result/error) resets it; see
     * [MAX_WATCHDOG_SILENT_REARMS].
     */
    private var alwaysOnSilentRearms = 0
    private var wakeAckPending = false
    private var wakeAckListenJob: Job? = null
    /** True once the app has handed off to the background (set on pause, cleared on resume). */
    private var appInBackground = false

    /**
     * True from the moment the conversation is handed to the background
     * service ([handOffListeningToBackground]) until the activity reclaims
     * the foreground. While set, NO in-app listening may be (re)started:
     * the TTS callbacks can fire a beat or two after the hand-off and used
     * to re-arm the in-app capture -- which is deaf in the background and
     * steals the mic from the background service that can actually hear,
     * silently dropping the user's follow-up.
     */
    private var handedOffToBackground = false

    /** Last turns, for "remember this" (content-free remember commands). */
    private var lastUserText: String? = null
    private var lastAssistantText: String? = null
    /** Last live-notes vault title, for "continue notes" with no topic. */
    private var lastNotingTitle: String? = null

    /** One recognizer-final callback must produce exactly one conversational turn. */
    private val turnDeduplicator = com.example.meowmba.core.audio.TurnDeduplicator()

    // Declared BEFORE init: init launches collectors that write to this flow.
    private val _motionTilt = kotlinx.coroutines.flow.MutableStateFlow(0f)

    /** Continuous lateral force (-1..1, + = pushed right) for the avatar lean. */
    private val _motionLean = kotlinx.coroutines.flow.MutableStateFlow(0f)

    /** Continuous braking/launch force (-1..1, + = braking throw) for the lurch. */
    private val _motionLurch = kotlinx.coroutines.flow.MutableStateFlow(0f)

    /** Phase G1: LOOI-style mood state machine (pure, unit-tested). */
    /** Pinned apps: drawer favorites (up to 8); the first two show on the face
     * and the next four in the right-edge app tab (RAIL_APP_LIMIT).
     * Declared before init: the pin-restore block in init reads it. */
    private val _pinnedPackages = MutableStateFlow<List<String>>(emptyList())
    val pinnedPackages: kotlinx.coroutines.flow.StateFlow<List<String>> = _pinnedPackages.asStateFlow()
    private val _drawerFolders = MutableStateFlow<List<DrawerFolder>>(emptyList())
    val drawerFolders: kotlinx.coroutines.flow.StateFlow<List<DrawerFolder>> = _drawerFolders.asStateFlow()

    private val moodEngine = com.example.meowmba.core.personality.MoodEngine()

    /** Phase G2: motor API. [RobotManager] swaps simulated <-> a live LOOI body. */
    private val robotManager = com.example.meowmba.core.robot.RobotManager()
    private val robotConnectGuard = java.util.concurrent.atomic.AtomicBoolean(false)
    /**
     * BLE backoff: with no base nearby every resume retried a 20-40s radio
     * hunt. Failures now back off exponentially (10s doubling to 10min) so an
     * absent robot stops heating the phone.
     */
    private var lastBleAttemptMs = 0L
    private var bleFailStreak = 0
    /** Debounced memory search: one FTS query per pause in typing, not per key. */
    private var searchJob: Job? = null
    /** Last face-gaze publish: caps 50Hz track output to ~15Hz UI updates. */
    private var lastFaceGazeEmitMs = 0L
    private val motorController: com.example.meowmba.core.robot.MotorController
        get() = robotManager.active

    private val confinement = com.example.meowmba.core.robot.ConfinementModel()

    /**
     * Plays the body half of every clip, on the same clock as the face.
     *
     * The window callbacks deliberately ignore their argument: the scheduler
     * reports clip-relative milliseconds, while ConfinementModel measures
     * evidence decay in wall time. Passing the clip offset through would make
     * a ten-minute decay window expire after a ten-second clip.
     */
    private val bodyScheduler = com.example.meowmba.core.robot.BodyScheduler(
        scope = viewModelScope,
        output = robotManager.bodyOutput,
        onMotionWindowStart = { confinement.onMotionWindowStart(System.currentTimeMillis()) },
        onMotionWindowEnd = { confinement.onMotionWindowEnd(System.currentTimeMillis()) },
    )

    // Declared BEFORE init: the init block wires the car assistant and its
    // collectors write to these fields.
    private var carDetector: DrivingDetector? = null
    private var carTripTracker: TripTracker? = null
    private var driveCardSuppressed = false

    /** When the current transient drive reaction began. */
    private var driveReactionStartedAtMs = 0L

    init {
        // Robot body: restore prefs into state, mirror live status, then connect.
        secureStorage?.let { st ->
            _uiState.value = _uiState.value.copy(
                robotBodyEnabled = st.getBooleanPreference("robot_body_enabled", false),
                robotHost = st.getPreference("robot_host") ?: "192.168.1.200",
                robotPort = st.getPreference("robot_port") ?: "9100",
                robotUseBluetooth = st.getBooleanPreference("robot_use_bluetooth", true),
                robotBtAddress = st.getPreference("robot_bt_address") ?: "",
            )
        }
        viewModelScope.launch {
            robotManager.status.collect { st ->
                _uiState.value = _uiState.value.copy(robotStatus = st)
                // Any status that is not a live body resets the confinement
                // envelope. "LOOI BLE: ready (...)" is a transient mid-connect
                // label — collapsing on it is harmless, evidence refresh at
                // connect time is correct.
                val live = st.contains("LOOI body") && !st.contains("simulated")
                if (!live) {
                    confinement.onLinkLost(System.currentTimeMillis())
                }
                onBodyLinkChanged(robotManager.hasLiveBody)
            }
        }

        // Raw fed9 sensor bytes: brake FIRST, interpret second. Subscribe once
        // per fresh link (the SharedFlow reference changes when the link does).
        viewModelScope.launch {
            var linked: kotlinx.coroutines.flow.SharedFlow<ByteArray>? = null
            robotManager.status.collect {
                val frames = robotManager.sensorFrames
                if (frames != null && frames !== linked) {
                    linked = frames
                    viewModelScope.launch {
                        frames.collect { raw ->
                            val event = com.example.meowmba.core.robot.LooiSensorParser.parse(raw)
                            // An edge is not a thing to reason about while still
                            // moving toward it. Unknown counts as an edge, because
                            // an undecoded frame might be one.
                            if (event is com.example.meowmba.core.robot.SensorEvent.Cliff ||
                                event is com.example.meowmba.core.robot.SensorEvent.Unknown
                            ) {
                                bodyScheduler.stop()
                            }
                            confinement.onSensor(event, System.currentTimeMillis())
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            com.example.meowmba.core.alerts.RoadAlertBus.latest.collect { alert ->
                if (alert == null) return@collect
                if (!_uiState.value.roadAlertsEnabled) return@collect
                val was = _uiState.value.policeAlert
                _uiState.value = _uiState.value.copy(policeAlert = alert.summary)
                if (was != alert.summary) speak(alert.summary)
                viewModelScope.launch {
                    delay(20_000L)
                    if (_uiState.value.policeAlert == alert.summary) {
                        _uiState.value = _uiState.value.copy(policeAlert = null)
                    }
                }
            }
        }
        refreshRobot()
        // Give the ~2 GB on-device model back when it stops being used. Checked
        // once a minute; a fresh question reloads it.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                runCatching { releaseIdleLocalBrain() }
            }
        }
        // Face tracking → avatar gaze + Looi head pan. Auto-on at launch (front
        // camera); null track just idles the gaze when nobody is in frame.
        viewModelScope.launch {
            visionManager?.faceTrack?.collect { track ->
                val offX = track?.offsetX
                val offY = track?.offsetY
                val nowFace = System.currentTimeMillis()
                val chasingHand = track?.followSource ==
                    com.example.meowmba.core.vision.FollowSource.HAND
                if (track != null && track.hasFace) {
                    lastSeenFaceAtMs = nowFace
                    lastHeldFaceScale = track.scale
                    // Base follows the FACE box, never the waved hand —
                    // chasing a hand with the treads is a spin.
                    robotManager.keepPersonInFrame(track.frameX, track.frameY, track.scale)
                } else {
                    robotManager.keepPersonInFrame(null, null, null)
                }
                if (offX != null && offY != null && track != null) {
                    lastHeldFaceX = offX
                    lastHeldFaceY = offY
                }
                val holding = lastSeenFaceAtMs != 0L && nowFace - lastSeenFaceAtMs < 550L
                val next = when {
                    offX != null && offY != null && track != null -> offX to offY
                    holding && lastHeldFaceX != null && lastHeldFaceY != null ->
                        lastHeldFaceX!! to lastHeldFaceY!!
                    else -> null
                }
                val cur = _faceGaze.value
                // No movement threshold here. VisionManager already smooths
                // (EMA) and already rate-limits (a steady 40ms cadence); a
                // SECOND dead-band on top meant a sample had to clear 0.012
                // twice to reach the face, so slow movement was discarded
                // entirely and then arrived as a jump. Only the identical-value
                // case is skipped, which is free. Time-throttled to ~15Hz so
                // the avatar recomposes 15x/s instead of 50x/s while tracking
                // (same path a slow drift takes, just fewer frames of it).
                if (next != cur && nowFace - lastFaceGazeEmitMs >= 66L) {
                    lastFaceGazeEmitMs = nowFace
                    _faceGaze.value = next
                }
                val now = System.currentTimeMillis()
                val inView = lastSeenFaceAtMs != 0L && now - lastSeenFaceAtMs < FACE_MIC_HOLD_MS
                val inFocus = VoicePolicy.userInFocus(inView, track?.scale ?: 0f)
                val prev = _uiState.value
                refreshSpeechLikely()
                val faces = track?.faceCount ?: 0
                val talker = speakerLabelFor(track)
                if (inView != prev.userInView || inFocus != prev.userInFocus ||
                    faces != prev.facesInView || talker != prev.activeSpeaker
                ) {
                    _uiState.value = prev.copy(
                        userInView = inView,
                        userInFocus = inFocus,
                        facesInView = faces,
                        activeSpeaker = talker,
                    )
                    speechService?.setFocusedListening(inFocus)
                    updateVoiceListeningMode()
                }
                maybeAskNewPerson(now)
                // Full 6-DOF pose rides the same track; StateFlow dedupes equal
                // values so rotation-only changes still re-emit.
                _facePose.value = track?.let {
                    com.example.meowmba.core.vision.FacePose6Dof(
                        offsetX = it.offsetX,
                        offsetY = it.offsetY,
                        yawDeg = it.yawDeg,
                        pitchDeg = it.pitchDeg,
                        rollDeg = it.rollDeg,
                        scale = it.scale,
                    )
                }
                applySocialCue(track)
                _chaseHard.value = chasingHand
            }
        }
        viewModelScope.launch {
            visionManager?.sceneLuma?.collect { luma ->
                if (luma != null) robotManager.noteSceneLuma(luma)
            }
        }
        // Camera stays off until the user taps an eye or asks "can you see me".
        // Auto-on at launch was the #1 battery hog (front camera + ML Kit + pose).
    }

    init {
        // Restore persisted voice preferences (name, wake word, conversation mode)
        secureStorage?.let { storage ->
            storage.getPreference(KEY_OWNER_SPEAKER)?.trim()?.takeIf { it.isNotEmpty() }?.let { owner ->
                lastSpeakerName = owner
            }
            val savedName = storage.getPreference(KEY_COMPANION_NAME)
            _uiState.value = _uiState.value.copy(
                name = if (savedName.isNullOrBlank()) _uiState.value.name else savedName,
                wakeWordEnabled = storage.getBooleanPreference(KEY_WAKE_WORD, false),
                requireWakeWordInFace = storage.getBooleanPreference(KEY_REQUIRE_WAKE_IN_FACE, false),
                conversationMode = storage.getBooleanPreference(KEY_CONVERSATION, false),
                bargeInEnabled = storage.getBooleanPreference(KEY_BARGE_IN, true),
                idleChatterEnabled = storage.getBooleanPreference(KEY_IDLE_CHATTER, true),
                keepScreenOnInFaceMode = storage.getBooleanPreference(KEY_KEEP_SCREEN_ON, false),
                commentaryEnabled = storage.getBooleanPreference(KEY_COMMENTARY, false),
                roadAlertsEnabled = storage.getBooleanPreference(KEY_ROAD_ALERTS, false),
                isCameraEnabled = storage.getBooleanPreference(KEY_CAMERA_ENABLED, false),
                agentBrainEnabled = storage.getBooleanPreference(KEY_AGENT_BRAIN, true),
                hermesAgentUrl = storage.getPreference(KEY_HERMES_URL) ?: "",
                imageServerUrl = storage.getPreference(KEY_IMAGE_SERVER_URL) ?: "",
                mem0ServerUrl = storage.getPreference(KEY_MEM0_SERVER_URL) ?: "",
                videoServerUrl = storage.getPreference(KEY_VIDEO_SERVER_URL) ?: "",
                preferServerImageGen = storage.getBooleanPreference(KEY_PREFER_SERVER_IMAGE_GEN, false),
                musicServerUrl = storage.getPreference(KEY_MUSIC_SERVER_URL) ?: "",
                backgroundListeningEnabled = storage.getBooleanPreference(KEY_BACKGROUND_LISTENING, false),
                carModeEnabled = storage.getBooleanPreference(KEY_CAR_MODE, false),
                avatarStyle = AvatarStyle.fromKey(storage.getPreference(KEY_AVATAR_STYLE)),
                avatarEyeColor = AvatarColors.parse(
                    storage.getPreference(KEY_AVATAR_EYE_COLOR), AvatarColors.DEFAULT_EYE),
                avatarGlowColor = AvatarColors.parse(
                    storage.getPreference(KEY_AVATAR_GLOW_COLOR), AvatarColors.DEFAULT_GLOW),
                emergencyContact = storage.getPreference(KEY_EMERGENCY_CONTACT) ?: "",
                // Restore the provider picked last session — but only if it is
                // still a buildable provider; a stale/removed label falls back
                // to the default instead of producing a silent null provider.
                selectedModel = storage.getPreference(KEY_SELECTED_MODEL)
                    ?.takeIf { it in availableModelProviders(storage.context) }
                    ?: defaultModelLabel(storage.context)
            )
            _uiState.value = _uiState.value.copy(
                ambientWeatherEnabled = storage.getBooleanPreference(KEY_AMBIENT_WEATHER, false),
                background = com.example.meowmba.core.background.BackgroundSelection.parse(
                    kind = storage.getPreference(KEY_BACKGROUND_KIND),
                    value = storage.getPreference(KEY_BACKGROUND_VALUE),
                    dim = storage.getPreference(KEY_BACKGROUND_DIM),
                ),
            )
        }
        // Any weather fetch — including the agent's tool call — surfaces the card and moves
        // the cat's expression to match the sky.
        weatherRepository?.let { repo ->
            viewModelScope.launch {
                repo.latest.collect { w ->
                    _uiState.value = _uiState.value.copy(
                        weather = w,
                        // A real reading is also the freshest thing the ambient scene could
                        // ask for, so it feeds both. Kept when the card is dismissed.
                        ambientWeather = w ?: _uiState.value.ambientWeather,
                        emotion = w?.let { runCatching {
                            CompanionEmotion.valueOf(it.catEmotionName())
                        }.getOrDefault(_uiState.value.emotion) } ?: _uiState.value.emotion,
                    )
                }
            }
        }
        // Ambient sky, if it was left on last session.
        if (_uiState.value.ambientWeatherEnabled ||
            _uiState.value.background.kind ==
            com.example.meowmba.core.background.BackgroundKind.LIVE_WEATHER
        ) {
            startAmbientWeather()
        }
        if (_uiState.value.roadAlertsEnabled) startRoadFeed()
        viewModelScope.launch(ioDispatcher) {
            val ctx = secureStorage?.context?.applicationContext ?: return@launch
            runCatching { DrawerApps.warm(ctx) }
        }

        matcher = WakePhraseMatcher(_uiState.value.name)
        ensureVoiceModelsThenAttachWake()

        ttsManager?.onSpeechStarted = {
            _uiState.value = _uiState.value.copy(interactionState = InteractionState.SPEAKING)            // HALF-DUPLEX GUARD: the moment her voice starts, close the
            // always-on mic. A session left open WILL hear the TTS through the
            // speaker and deliver it back as a user turn (measured on device:
            // self-answer loops and flushed/cut-off replies). Voice barge-in is
            // disabled while she talks; the mic button still interrupts.
            if (alwaysOnActive) stopAlwaysOnListening()
            // A speech start arriving AFTER the hand-off must not re-arm an
            // in-app listener; the background service owns the turn now.
            //
            // The wake-ack ("Yes?") also skips the watch: holding a live
            // recognizer session through the ack forces the follow-up handoff
            // to pay stop + settle (~500ms of deaf time) right when the user
            // is about to speak. A ~0.4s ack does not need voice barge-in;
            // the mic button still interrupts it.
            if (!handedOffToBackground && !wakeAckPending) startInterruptionWatch()
        }
        ttsManager?.onSpeechFinished = { onSpeechFinished() }

        // Glasses connect/disconnect: when the Meta-glasses backend is picked,
        // a link change re-selects the engine immediately (glasses mic takes
        // over on connect, native mic resumes on disconnect) and refreshes the
        // Hardware page availability badges.
        glassesController?.let { gc ->
            viewModelScope.launch {
                gc.isConnected.collect { connected ->
                    fuLog("glasses link -> $connected")
                    refreshAsrBackendState()
                    if (asrBackend == com.example.meowmba.core.audio.AsrBackend.META_GLASSES_MIC &&
                        _uiState.value.interactionState == InteractionState.IDLE
                    ) {
                        stopAlwaysOnListening()
                        startAlwaysOnListening()
                    }
                }
            }
        }

        // Recovery watchdog: a wedged voice pipeline (always-on policy says
        // LISTEN, but nothing is actually listening) must self-heal. Measured
        // on device: a follow-up error raced resetState, the re-arm chain gave
        // up silently, and the app sat deaf with a frozen caption until the
        // user restarted it.
        viewModelScope.launch {
            while (true) {
                // 30s, skipped in background: the old 10s tick woke the CPU
                // forever even with the screen off, and the background service
                // owns the mic there anyway so a restart would be futile.
                kotlinx.coroutines.delay(30_000)
                if (appInBackground || handedOffToBackground) continue
                val s = _uiState.value
                if (s.isMicEnabled && !s.isMuted &&
                    s.interactionState == InteractionState.IDLE && shouldAlwaysOnMic() &&
                    !alwaysOnActive && !alwaysOnSessionOpen && alwaysOnRearmJob == null
                ) {
                    fuLog("voice wedge detected — forcing always-on restart")
                    startAlwaysOnListening()
                }
            }
        }
        ttsManager?.onWordBoundary = { start, end ->
            selfVoiceFilter.onWordSpoken(start, end)
            bumpMouthEnergy()
        }

        // Single collector for partial transcripts (live captions + barge-in)
        viewModelScope.launch {
            speechService?.partialResults?.collect { partial ->
                val s = _uiState.value
                if (s.interactionState == InteractionState.LISTENING && partial.isNotEmpty()) {
                    _uiState.value = s.copy(captionText = captionWithSpeaker(partial))
                }
                if (s.interactionState == InteractionState.SPEAKING &&
                    s.isMicEnabled && partial.isNotBlank()
                ) {
                    // The recognizer is LIVE while the companion speaks.
                    // The wake phrase always interrupts; novel user speech
                    // barges in; the companion's own echo is suppressed.
                    if (matcher.match(partial) != null || !selfVoiceFilter.isOwnVoice(partial)) {
                        interrupt()
                    }
                }
            }
        }

        // Overlay transitions used to change mic ownership. Always-on now remains
        // foreground-wide, but keeping this transition hook makes changes to the
        // explicit require-wake setting and legacy screen state converge immediately
        // without reacting to caption churn.
        // The initial emission is skipped on purpose: at construction the app
        // has not resumed yet, and arming the open mic then would also make
        // every JVM unit test drive a perpetual listener. The activity's
        // onResume -> reclaimWakeMicForForeground() arms on first launch.
        viewModelScope.launch {
            _uiState.map { faceModeActive(it) }.distinctUntilChanged().drop(1).collect { face ->
                updateVoiceListeningMode()
                // Camera + ML Kit is the heat hog. Pause it whenever the
                // face is covered (apps drawer, settings, …) so the grid
                // is not fighting the ISP for CPU.
                if (!face) {
                    visionManager?.setFaceTrackingEnabled(false)
                } else if (_uiState.value.isCameraEnabled) {
                    visionManager?.setFaceTrackingEnabled(true)
                }
            }
        }

        // Live conversation history from Room drives the chat workspace
        viewModelScope.launch {
            memoryRepository?.allMessages?.collect { messages ->
                cachedMessages = messages
                publishChat()
            }
        }

        // Memory list for the Memory screen
        viewModelScope.launch {
            memoryRepository?.allMemories?.collect { memories ->
                _uiState.value = _uiState.value.copy(allMemories = memories)
            }
        }

        // What the agents have done, from Room. Without this the audit trail only ever
        // showed the current session, so "see what this agent has worked on" was empty
        // every time the app restarted — which is most of the times you would ask.
        viewModelScope.launch {
            auditDao?.getRecent()?.collect { entries ->
                _uiState.value = _uiState.value.copy(auditEntries = entries)
            }
        }

        // Prime Agent's /heartbeat: a periodic self-review, so the harness
        // improves on its own rather than only when someone thinks to ask.
        // Deliberately slow — refine() is pure and cheap, but a lesson is only
        // meaningful once evidence has had time to recur, and anything more
        // frequent would just re-read the same window.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                runCatching {
                    val steps = recentTrajectory()
                    if (steps.size >= com.example.meowmba.core.agent.prime.RefineEngine.MIN_OCCURRENCES) {
                        val now = System.currentTimeMillis()
                        val state = com.example.meowmba.core.agent.prime.RefineEngine
                            .expire(harnessStore.load(), now)
                        val merged = com.example.meowmba.core.agent.prime.RefineEngine.merge(
                            state,
                            com.example.meowmba.core.agent.prime.RefineEngine.refine(steps, now),
                        )
                        if (merged != state) {
                            harnessStore.save(merged)
                            android.util.Log.i(
                                "PrimeHarness",
                                "heartbeat: ${merged.lessons.size} lesson(s) held",
                            )
                        }
                    }
                }
            }
        }

        // Memory controls: pause flag + retention policy (purged once on start)
        viewModelScope.launch {
            secureStorage?.let { storage ->
                val paused = storage.getBooleanPreference(KEY_MEMORY_PAUSED, false)
                val retention = storage.getPreference(KEY_RETENTION_DAYS)?.toIntOrNull()
                    ?: DEFAULT_RETENTION_DAYS
                _uiState.value = _uiState.value.copy(memoryPaused = paused, retentionDays = retention)
                if (retention > 0) memoryRepository?.purgeOlderThan(retention)
            }
        }

        // ── Agent system (Phase 5) ─────────────────────────────────────────
        val toolRegistry = ToolRegistry()
        if (memoryRepository != null) {
            toolRegistry.register(MemorySaveTool(memoryRepository))
            toolRegistry.register(MemorySearchTool(memoryRepository))
        }
        val appCtx = secureStorage?.context
        if (appCtx != null) {
            toolRegistry.register(RememberingTool(appCtx, start = true))
            toolRegistry.register(RememberingTool(appCtx, start = false))
        }
        if (workspaceRoot != null) {
            val workspace = Workspace(workspaceRoot)
            toolRegistry.register(FileReadTool(workspace))
            toolRegistry.register(FileWriteTool(workspace))
            toolRegistry.register(FileDeleteTool(workspace))
            toolRegistry.register(FileListTool(workspace))
            toolRegistry.register(ProjectCreateTool(workspace))
        }
        // Phase 6c/6d: desktop bridge tools ride the same registry — the
        // orchestrator's approval card gates them like any MODERATE/DESTRUCTIVE tool.
        secureStorage?.let {
            val bridgeManager = DesktopBridgeManager(it)
            toolRegistry.register(DesktopWriteFileTool(bridgeManager))
            toolRegistry.register(DesktopRunBuildTool(bridgeManager))
            toolRegistry.register(DesktopRunScriptTool(bridgeManager))
            // Phase F1: open apps/URLs/files, clipboard, media, screenshot.
            toolRegistry.register(
                DesktopControlTool(
                    bridgeManager,
                    screenshotDir = java.io.File(it.context.filesDir, "desktop_screenshots"),
                    describeImage = { bytes: ByteArray ->
                        visionProviderFor()?.describe(bytes, "In one short sentence, what is on this desktop screen?")?.getOrNull()
                    }
                )
            )

            // USB auto-bridge: auto-pair when tethered via USB
            usbAutoBridge = UsbAutoBridgeManager(it.context, bridgeManager, viewModelScope).apply {
                onUsbStateChanged = { connected ->
                    _uiState.value = _uiState.value.copy(usbConnected = connected)
                }
            }
            val usbEnabled = it.getBooleanPreference(KEY_USB_AUTO_BRIDGE, false)
            _uiState.value = _uiState.value.copy(usbAutoBridgeEnabled = usbEnabled)
            if (usbEnabled) usbAutoBridge?.start()
        }
        // Research, email, calendar, and media tools
        toolRegistry.register(WebSearchTool())
        toolRegistry.register(WebFetchTool())
        val appContext = secureStorage?.context
        // Local browser, exposed to the agent as MCP tools. The swipe-left
        // panel is gone; this is the engine that replaced it — a WebView the
        // user never sees, fronted by browser.navigate/read/click/type/...
        // so the model can still drive a real page.
        appContext?.let { ctx ->
            offscreenBrowser = com.example.meowmba.core.browser.OffscreenBrowser(ctx)
            mcpBrowser = com.example.meowmba.core.mcp.McpBrowserServer(offscreenBrowser!!)
            com.example.meowmba.core.mcp.McpToolBridge(mcpBrowser!!)
                .tools()
                .forEach { toolRegistry.register(it) }
            fuLog("MCP browser tools registered")
        }
        // Phase G1: periodic mood tick + optional idle chatter (max 1/hour).
        // Runs on Dispatchers.Default: the real-time delay must NOT be driven by
        // unit-test virtual clocks (an infinite delay loop under advanceUntilIdle
        // would spin forever and exhaust the test JVM heap).
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(10 * 60_000L)
                val now = System.currentTimeMillis()
                moodEngine.tick(now)
                applyMood()
                val st = _uiState.value
                if (st.idleChatterEnabled && st.interactionState == InteractionState.IDLE &&
                    st.captionText == null && moodEngine.mayChatter(now)
                ) {
                    moodEngine.markChattered(now)
                    speak(moodEngine.pickChatter(now, kotlin.random.Random.nextInt()))
                }
            }
        }

        // Phase E1: seed default routines, watch the table, keep alarms armed.
        if (appContext != null) {
            routineDao?.let { dao ->
                viewModelScope.launch {
                    com.example.meowmba.core.routines.RoutineScheduler.seedDefaults(dao)
                    dao.getAll().collect { list ->
                        _uiState.value = _uiState.value.copy(
                            routines = list.map { r ->
                                RoutineUi(
                                    id = r.id,
                                    name = r.name,
                                    whenLabel = r.timeOfDay ?: r.intervalMinutes?.let { "every ${it}m" } ?: "",
                                    enabled = r.enabled,
                                    userMade = r.source == "user",
                                    detail = when (r.action) {
                                        "play_music" -> "plays music"
                                        "launch_app" -> "opens ${r.launchTarget.ifBlank { "an app" }}"
                                        else -> r.message.take(48)
                                    },
                                )
                            }
                        )
                        com.example.meowmba.core.routines.RoutineScheduler.scheduleAll(appContext, list.filter { it.enabled })
                    }
                }
            }
        }
        projectDao?.let { dao ->
            viewModelScope.launch {
                dao.getAll().collect { list ->
                    val savedId = secureStorage?.getPreference(KEY_ACTIVE_PROJECT_ID)?.toLongOrNull()
                    val mapped = list.map { p ->
                        ProjectUi(
                            id = p.id,
                            name = p.name,
                            kind = p.kind,
                            kindLabel = when (p.kind) {
                                "WEBSITE" -> "Website"
                                "PHOTO" -> "Photo"
                                "CODE" -> "Code"
                                else -> "Project"
                            },
                            whenLabel = projectWhenLabel(p.lastOpenedAt),
                            conversationId = p.conversationId,
                        )
                    }
                    val restore = mapped.firstOrNull { it.id == savedId }
                    _uiState.value = _uiState.value.copy(
                        projects = mapped,
                        activeProjectId = restore?.id ?: _uiState.value.activeProjectId,
                        activeProjectName = restore?.name ?: _uiState.value.activeProjectName,
                        activeConversationId = restore?.conversationId
                            ?: _uiState.value.activeConversationId,
                    )
                    if (restore != null) publishChat()
                }
            }
        }
        if (appContext != null) {
            toolRegistry.register(EmailSendTool(appContext))
            toolRegistry.register(CalendarListTool(appContext))
            toolRegistry.register(CalendarCreateTool(appContext))
        }
        // Phone control: open apps, links, and settings pages on this device.
        if (appContext != null) {
            toolRegistry.register(LaunchAppTool(appContext))
            toolRegistry.register(ListAppsTool(appContext))
            toolRegistry.register(PlayMusicTool(appContext))
            toolRegistry.register(OpenUrlTool(appContext))
            toolRegistry.register(OpenSettingsTool(appContext))
            // Phase F2: alarms, timers, flashlight, DND, battery readout.
            toolRegistry.register(SetAlarmTool(appContext))
            toolRegistry.register(SetTimerTool(appContext))
            toolRegistry.register(FlashlightTool(appContext))
            toolRegistry.register(DndTool(appContext))
            toolRegistry.register(BatteryStatusTool(appContext))
            // Phase F4: assistant-class phone tools.
            toolRegistry.register(SetVolumeTool(appContext))
            toolRegistry.register(TakePhotoTool(appContext))
            toolRegistry.register(ComposeSmsTool(appContext))
            toolRegistry.register(DialCallTool(appContext))
            toolRegistry.register(MediaControlTool(appContext))
            toolRegistry.register(SetBrightnessTool(appContext))
            toolRegistry.register(
                PhoneCleanupTool(
                    context = appContext,
                    currentFolders = { _drawerFolders.value },
                    currentApps = {
                        DrawerApps.snapshot(appContext).ifEmpty {
                            runCatching { DrawerApps.loadFast(appContext) }.getOrDefault(emptyList())
                        }
                    },
                    saveFolders = { saveDrawerFolders(it) },
                    requestMediaPermission = {
                        _uiState.value = _uiState.value.copy(mediaPermissionRequested = true)
                    },
                ),
            )
            // Phase F3: opt-in UI automation (off until enabled by the user).
            toolRegistry.register(UiAutomationTool())
            // The body. Without this the companion could describe moving but
            // never actually move.
            toolRegistry.register(
                com.example.meowmba.core.agent.tools.RobotMoveTool(robotManager),
            )

            // The self-improving harness (Prime Agent's /refine, /goal and
            // state, ported to run on the phone). The trajectory it reviews is
            // the audit log — the record of what the agents actually did — so
            // the lessons are grounded in this device's history rather than in
            // a model's impression of it.
            toolRegistry.register(
                com.example.meowmba.core.agent.tools.PrimeRefineTool(
                    store = harnessStore,
                    trajectory = { recentTrajectory() },
                ),
            )
            toolRegistry.register(
                com.example.meowmba.core.agent.tools.PrimeGoalTool(harnessStore),
            )
            toolRegistry.register(
                com.example.meowmba.core.agent.tools.PrimeStateTool(harnessStore),
            )

            // Invented avatar animations: the model writes keyframes, the app
            // validates and plays them — the same propose/constrain boundary as
            // every other tool. Saved clips persist as plain JSON files.
            val animStore = CustomAnimationStore(File(appContext.filesDir, "custom_animations"))
            toolRegistry.register(AvatarAnimateTool(animStore) { spec ->
                // Prefer the Godot channel engine when the clip name is a known expression
                // and the model only sent a bare name (or the frames are empty).
                val composed = CatExpressions.resolve(spec.name)
                // Reached only when something asked for a performance, so the
                // face is worth revealing.
                if (composed != null && spec.frames.size <= 1) {
                    playComposedExpression(composed, spec.name, userRequested = true)
                } else {
                    playCustomAnimation(spec, userRequested = true)
                }
            })
            toolRegistry.register(
                DescribeAnimationTool { spec -> playCustomAnimation(spec, userRequested = true) },
            )

            // Occasional random idle animations so the face feels alive.
            // Opt-out flag for JVM unit tests: their virtual clocks would
            // make the forever-loop spin instead of waiting.
            val idleAnimationsEnabled = System.getProperty("meowmba.idleAnimations", "true") != "false"
            // The very first idle clip of a session is always a big visible
            // one (tail wag, paw lick, zoomies...) so the user sees the body
            // animations early instead of only subtle ear wiggles.
            var idleClipShownThisSession = false
            if (idleAnimationsEnabled) idleAnimationDirector = IdleAnimationDirector(
                scope = viewModelScope,
                canPlay = {
                    val s = _uiState.value
                    // faceModeActive is the "the bare face is on screen" test.
                    // Without it the cat performed behind Settings, the chat
                    // sheet or the browser — invisible, and (before the panel
                    // fix) slamming them shut to show a performance nobody
                    // asked for.
                    faceModeActive(s) &&
                        s.interactionState == InteractionState.IDLE &&
                        s.activeAnimation == null &&
                        s.activeComposed == null &&
                        s.activeTask == null &&
                        // TTS energy decays toward zero; compare with slack so a
                        // lingering 0.001f never blocks performances forever.
                        // Read off its own flow now, not the state snapshot.
                        _mouthEnergy.value <= 0.02f
                },
                pickName = {
                    val saved = animStore.list()
                    // Mood-filtered, so an idle cat never hisses at nothing.
                    val moodPool = CatExpressions.idlePoolFor(_uiState.value.emotion)
                    // Idle now draws mostly from CatExpressions — the channel
                    // compositions that run through CatMotionEngine's springs
                    // and body motions. The keyframe BUILT_INS interpolate
                    // linearly between poses with no follow-through, so an idle
                    // pool made only of them (what shipped before) never showed
                    // the motion system off. Keyframe clips and the user's own
                    // inventions stay in the pool, just no longer all of it.
                    val pool = moodPool +
                        CustomAnimations.BUILT_INS.keys.toList() +
                        saved
                    if (!idleClipShownThisSession) {
                        idleClipShownThisSession = true
                        val showyNames = setOf("tail wag", "paw lick", "happy bop", "stretch", "headshake")
                        val showy = pool.filter { it in showyNames } + saved
                        (showy.ifEmpty { pool }).randomOrNull()
                    } else {
                        pool.randomOrNull()
                    }
                },
                // By name, so a composed expression routes to the spring engine
                // and only genuine keyframe clips take the keyframe path.
                // Ambient, not asked for: never dismisses what is on screen.
                play = { name -> playAnimationNamed(name, userRequested = false) },
            ).also { it.start() }

            // Obsidian-compatible vault notes.
            vaultRepository?.let { toolRegistry.registerVaultTools(it) }

            // "What did we talk about…?" — full-history recall over messages,
            // memories, and vault notes.
            memoryRepository?.let { repo ->
                toolRegistry.register(MemoryRecallTool(repo, vaultRepository))
            }

            // Server management over SSH (meow-nb and friends). Servers are
            // remembered in the encrypted registry; commands are allowlisted.
            secureStorage?.let { storage ->
                sshHostRegistry = com.example.meowmba.core.ssh.SshHostRegistry(storage)
                if (com.example.meowmba.BuildConfig.DEBUG) {
                    sshHostRegistry!!.ensureDevHosts()
                }
                toolRegistry.register(SshTool(sshHostRegistry!!))
                toolRegistry.register(SshAddHostTool(sshHostRegistry!!))
            }

            // Agent-driven browser: the companion can open, click, type and
            // screenshot pages in the swipe-left browser. Pages the agent
            // builds get persisted under web_pages/.
            appContext?.let { ctx ->
                val pagesDir = java.io.File(ctx.filesDir, "web_pages")
                toolRegistry.register(
                    WebBrowserTool(java.io.File(ctx.filesDir, "web_screenshots"), pagesDir)
                )
                // The live browser agent loop (vision = OpenRouter-configurable).
                browserAgent = com.example.meowmba.core.agent.BrowserAgentLoop(
                    scope = viewModelScope,
                    vision = { browserVisionProvider() },
                    brain = { prompt ->
                        askBrowserBrain(prompt)
                    },
                    captureJpeg = {
                        kotlin.coroutines.suspendCoroutine { cont ->
                            com.example.meowmba.core.browser.WebViewBridge.captureJpeg { bytes ->
                                cont.resumeWith(Result.success(bytes))
                            }
                        }
                    },
                    evalJs = { script ->
                        kotlin.coroutines.suspendCoroutine { cont ->
                            com.example.meowmba.core.browser.WebViewBridge.evaluate(script) { r ->
                                cont.resumeWith(Result.success(r))
                            }
                        }
                    },
                    pagesDir = pagesDir,
                    announce = { msg ->
                        _uiState.value = _uiState.value.copy(captionText = msg)
                        speak(msg)
                    },
                    onSource = { kind, code ->
                        _uiState.value = _uiState.value.copy(
                            browserSource = code,
                            browserSourceKind = kind,
                        )
                    },
                )

                // Phone-control agent loop (accessibility-based, modeled on browserAgent)
                phoneAgentLoop = com.example.meowmba.core.agent.PhoneAgentLoop(
                    scope = viewModelScope,
                    brain = { prompt ->
                        runCatching {
                            (omniRoute?.generate(prompt) as? com.example.meowmba.core.agent.ModelResponse.Complete)?.text
                        }.getOrNull()
                    },
                    service = { com.example.meowmba.core.access.MeowAccessibilityService.instance },
                    announce = { msg ->
                        _uiState.value = _uiState.value.copy(captionText = msg)
                        speak(msg)
                    },
                    onComplete = { summary -> speak(summary) },
                )
            }
            // Restore pinned apps (drawer favorites + two face shortcuts).
            secureStorage?.getPreference(KEY_PINNED_APPS)?.let { saved ->
                _pinnedPackages.value = saved.split(',').filter { it.isNotBlank() }.take(8)
            }
            _drawerFolders.value = DrawerCatalog.parseFolders(
                secureStorage?.getPreference(KEY_DRAWER_FOLDERS),
            )

            // IMU: lean with the phone, startle on shake. RobotPoseSource for
            // the future body lives behind MotionSense's interface.
            appContext?.let { ctx ->
                motionSense = com.example.meowmba.core.sensor.MotionSense(ctx).also { sense ->
                    if (sense.available) {
                        sense.start()
                        viewModelScope.launch {
                            sense.motion.collect { st ->
                                // Counter-rotate toward level from the screen-plane
                                // tilt: well-defined in every orientation, so the
                                // avatar keeps tracking in a horizontal car dock —
                                // the old roll channel saturates at ±8° there and
                                // the face looked dead. Partial gain + clamp keep
                                // it a lean, not a full gravity-compass swing.
                                val tilt = (-st.screenLeanDeg * 0.35f).coerceIn(-14f, 14f)
                                if (kotlin.math.abs(tilt - _motionTilt.value) > 0.3f) {
                                    _motionTilt.value = tilt
                                }
                                // Continuous car forces, normalized so ~0.6G is a
                                // full-strength reaction. lean: + = pushed right
                                // (left turn). lurch: + = braking (forward throw).
                                val lean = (st.leanG / 0.6f).coerceIn(-1f, 1f)
                                if (kotlin.math.abs(lean - _motionLean.value) > 0.02f) {
                                    _motionLean.value = lean
                                }
                                val lurch = (-st.surgeG / 0.6f).coerceIn(-1f, 1f)
                                if (kotlin.math.abs(lurch - _motionLurch.value) > 0.02f) {
                                    _motionLurch.value = lurch
                                }
                                // Physical base: counter the in-plane rotation, the
                                // axis the head-tilt servo actually moves on.
                                // Skip while a face is locked — followFace owns the neck.
                                if (_faceGaze.value == null) {
                                    com.example.meowmba.core.robot.FaceMotorMap.balanceTilt(st.screenLeanDeg)
                                        .forEach { (servo, angle) -> motorController.setServo(servo, angle) }
                                }
                            }
                        }
                        viewModelScope.launch {
                            var lastSeen = 0
                            sense.shakeCount.collect { count ->
                                if (count > lastSeen) {
                                    lastSeen = count
                                    noteMoodEvent(com.example.meowmba.core.personality.MoodEvent.Shake)
                                    val spec = CustomAnimations.BUILT_INS["happy bop"]
                                        ?: CustomAnimations.BUILT_INS.values.randomOrNull()
                                    spec?.let { playCustomAnimation(it) }
                                }
                            }
                        }
                    }
                }
            }

            // Navigation. find_places is SAFE and read-only so the companion can look,
            // describe the options and ask "closest, or do you want to pick?" before
            // nav.navigate actually routes anywhere. Offline-first, so it works with no
            // signal — see core/nav.
            val savedPlaces = com.example.meowmba.core.nav.SavedPlaceStore(appContext)
            val offlinePoi = com.example.meowmba.core.nav.OfflinePoiSource(appContext)
            val onlinePoi = com.example.meowmba.core.nav.GeoapifyPlaceSource {
                secureStorage?.getApiKey("geoapify").orEmpty()
            }
            val placeRepo = com.example.meowmba.core.nav.PlaceRepository(offlinePoi, onlinePoi)
            weatherRepository?.let { repo -> toolRegistry.register(WeatherTool(repo)) }
            // Meow Inventory: what the user owns, and everything they can do with it.
            inventoryClient?.let { client ->
                toolRegistry.registerInventoryTools(appContext, client)
                // The card follows whatever item the conversation lands on.
                viewModelScope.launch {
                    client.lastItem.collect { item ->
                        _uiState.value = _uiState.value.copy(inventoryItem = item)
                    }
                }
            }
            toolRegistry.register(FindPlacesTool(appContext, placeRepo, savedPlaces))
            toolRegistry.register(NavigateTool(appContext))
            toolRegistry.register(SavePlaceTool(appContext, savedPlaces))
            toolRegistry.register(ListSavedPlacesTool(savedPlaces))

            // Car assistant: fuse GPS speed + accelerometer + a configured car
            // Bluetooth device to know when the user is driving, track the trip, and
            // watch for a crash. Sensors/location only run while car mode is enabled.
            val drivingDetector = DrivingDetector(appContext, viewModelScope).also {
                it.setCarDeviceAddress(secureStorage?.getPreference(KEY_CAR_DEVICE))
            }
            carDetector = drivingDetector
            val tripTracker = TripTracker()
            carTripTracker = tripTracker

            // Always mirror the persisted switch, so the detector's state and the
            // Settings toggle can never disagree (fresh detector starts disabled).
            drivingDetector.setCarModeEnabled(_uiState.value.carModeEnabled)

            viewModelScope.launch {
                drivingDetector.driving.collect { s ->
                    if (!s.isDriving) driveCardSuppressed = false
                    // Latch transient reactions so a lurch plays out even though
                    // the detector only publishes every couple of seconds.
                    val now = System.currentTimeMillis()
                    val incoming = if (s.isDriving) {
                        driveReactionFor(s.speedMph, s.lateralG, s.longitudinalG)
                    } else {
                        DriveReaction.NONE
                    }
                    val (reaction, startedAt) = nextDriveReaction(
                        current = _uiState.value.driveReaction,
                        startedAtMs = driveReactionStartedAtMs,
                        incoming = incoming,
                        nowMs = now,
                    )
                    driveReactionStartedAtMs = startedAt
                    _uiState.value = _uiState.value.copy(
                        driving = s.isDriving,
                        driveCardVisible = s.isDriving && !driveCardSuppressed,
                        driveSpeedMph = s.speedMph,
                        driveGForce = s.gForce,
                        driveLateralG = s.lateralG,
                        driveLongitudinalG = s.longitudinalG,
                        driveReaction = reaction,
                        driveReason = s.reason,
                        driveManual = s.manualOverride,
                    )
                    val fix = drivingDetector.lastFix
                    tripTracker.update(
                        s.isDriving, s.speedMph,
                        fix?.first, fix?.second,
                    )
                }
            }
            viewModelScope.launch {
                tripTracker.trip.collect { t ->
                    _uiState.value = _uiState.value.copy(driveTrip = t)
                }
            }
            viewModelScope.launch {
                drivingDetector.crashEvents.collect { crash -> onCrashDetected(crash) }
            }

            if (com.example.meowmba.BuildConfig.DEBUG) {
                // Debug-only crash simulator: lets a connected computer exercise
                // the camera-verify + alert path without actually driving.
                val crashFilter = android.content.IntentFilter(
                    "com.example.meowmba.debug.SIMULATE_CRASH",
                )
                appContext.registerReceiver(
                    object : android.content.BroadcastReceiver() {
                        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                            simulateCrash(8.4f, 52f)
                        }
                    },
                    crashFilter,
                    android.content.Context.RECEIVER_EXPORTED,
                )
            }

            toolRegistry.register(
                CarStatusTool(
                    state = { drivingDetector.driving.value },
                    trip = { tripTracker.trip.value },
                )
            )
            toolRegistry.register(
                CarDriveModeTool(
                    currentManual = { drivingDetector.driving.value.manualOverride },
                    onSet = { on -> setManualDriving(on) },
                )
            )
            toolRegistry.register(
                CarTripTool(
                    trip = { tripTracker.trip.value },
                    lastTrip = { tripTracker.lastTrip.value },
                )
            )
            toolRegistry.register(
                CarEtaTool(appContext, savedPlaces) {
                    drivingDetector.driving.value.speedMph
                }
            )
            toolRegistry.register(
                CarHelpTool(
                    context = appContext,
                    emergencyContact = { _uiState.value.emergencyContact },
                    trip = { tripTracker.trip.value },
                )
            )
        }
        if (workspaceRoot != null) {
            val ws = Workspace(workspaceRoot)
            toolRegistry.register(EmailDraftTool(ws))
            // Read the path at call time, not registration time, so installing
            // the weights takes effect without restarting the app.
            toolRegistry.register(
                MediaGenerateTool(
                    ws,
                    localGenerator = {
                        // Read the installed path at call time, not registration time, so
                        // installing/deleting the weights takes effect without a restart.
                        val modelPath = downloader?.installedFile(ImageModelCatalog.options.first())?.path
                        if (modelPath != null) {
                            SDCppImageGenerator(modelPath)
                        } else {
                            null
                        }
                    },
                    remoteGenerator = {
                        val url = _uiState.value.imageServerUrl
                        if (url.isNotBlank()) {
                            com.example.meowmba.core.media.RemoteImageGenerator(url)
                        } else {
                            null
                        }
                    },
                    preferServer = { _uiState.value.preferServerImageGen }
                )
            )
            toolRegistry.register(
                MediaGenerateVideoTool(
                    ws,
                    remoteGenerator = {
                        val url = _uiState.value.videoServerUrl
                        if (url.isNotBlank()) {
                            com.example.meowmba.core.media.RemoteVideoGenerator(url)
                        } else {
                            null
                        }
                    },
                    onProgress = { text ->
                        _uiState.value = _uiState.value.copy(captionText = text)
                    }
                )
            )
            toolRegistry.register(
                MediaGenerateAudioTool(
                    ws,
                    remoteGenerator = {
                        val url = _uiState.value.musicServerUrl
                        if (url.isNotBlank()) {
                            com.example.meowmba.core.media.RemoteMusicGenerator(url)
                        } else {
                            null
                        }
                    }
                )
            )
            // Studio: comics/scripts always render to HTML whose path rides a
            // MEDIA_FILE: line, so onTaskCompleted opens the browser overlay —
            // "made it but didn't show me" is impossible by construction.
            toolRegistry.register(WriteScriptTool(ws))
            toolRegistry.register(CreateComicTool(ws, characterDao))
            toolRegistry.register(GlassesTakePhotoTool(glassesController, ws))
        }
        if (characterDao != null) {
            toolRegistry.register(SaveCharacterTool(characterDao))
            toolRegistry.register(GetCharacterTool(characterDao))
        }
        if (memoryRepository != null || workspaceRoot != null) {
            agentToolRegistry = toolRegistry
            connectorManager?.let { mgr ->
                mgr.load()
                // Enabled local/embedded connectors register immediately; MCP
                // connectors need a network handshake, so register async. The
                // manager filters by the connector's enabled flag.
                viewModelScope.launch {
                    mgr.registerToolsInto(toolRegistry)
                }
            }
            val orchestrator = AgentOrchestrator(viewModelScope, toolRegistry, agentAuditSink)
            toolRegistry.register(TaskListTool {
                orchestrator.tasks.value.take(10).map { "[${it.status}] ${it.title} (${it.agentId})" }
            })
            orchestrator.onTaskCompleted = { task ->
                val result = task.result ?: ""
                when (task.status) {
                    TaskStatus.COMPLETED -> {
                        noteMoodEvent(com.example.meowmba.core.personality.MoodEvent.TaskSuccess)
                        // Persistent task journal: every completed multi-step task gets a
                        // vault entry so meow can recall what was done and why later on.
                        if (task.toolId != "avatar.animate" && task.toolId != "avatar.describe_animation") {
                            vaultRepository?.let { vault ->
                                viewModelScope.launch {
                                    runCatching {
                                        vault.append(
                                            title = "Task log: ${task.title}",
                                            text = buildString {
                                                append("- completed ${java.time.Instant.now()} by ${task.agentId} via ${task.toolId}\n")
                                                append("- result: ${result.take(1200)}")
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        // avatar.animate / avatar.describe_animation: the animation IS
                        // the response — onPlay already set activeAnimation; speaking
                        // the tool result just confuses the user.
                        if (task.toolId == "avatar.animate" || task.toolId == "avatar.describe_animation") {
                            // no-op: playCustomAnimation already set activeAnimation
                        } else {
                            // Check for generated media
                            val mediaLine = result.lineSequence().firstOrNull { it.startsWith("MEDIA_FILE:") }
                            if (mediaLine != null) {
                                val path = mediaLine.removePrefix("MEDIA_FILE:")
                                val file = File(path)
                                // HTML artifacts (comics, scripts, ads) are shown by
                                // showFileInBrowser loading the real page in the
                                // WebView overlay; the GeneratedMedia card would just
                                // try to decode an .html file as a bitmap.
                                val isHtml = path.endsWith(".html") || path.endsWith(".htm")
                                val type = when {
                                    path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ->
                                        GeneratedMedia.MediaType.IMAGE
                                    path.endsWith(".gif") || path.endsWith(".mp4") ->
                                        GeneratedMedia.MediaType.VIDEO
                                    path.endsWith(".wav") || path.endsWith(".mp3") || path.endsWith(".m4a") ->
                                        GeneratedMedia.MediaType.AUDIO
                                    else -> GeneratedMedia.MediaType.IMAGE
                                }
                                val label = task.title.removePrefix("generate ").removePrefix("create ")
                                    .replaceFirstChar { it.uppercase() }
                                if (!isHtml) {
                                    _uiState.value = _uiState.value.copy(
                                        generatedMedia = GeneratedMedia(
                                            filePath = file.absolutePath,
                                            type = type,
                                            label = label
                                        )
                                    )
                                }
                                // Persist it. Without this the media only ever lived in
                                // transient UI state, so nothing generated showed up in
                                // the Memory tab afterwards.
                                if (!_uiState.value.memoryPaused) {
                                    viewModelScope.launch {
                                        runCatching {
                                            memoryRepository?.addMemory(
                                                content = "Created a ${if (isHtml) "page" else type.name.lowercase()}: $label " +
                                                    "(saved as ${file.name})",
                                                type = MemoryType.EPISODIC,
                                                importance = ImportanceLevel.MEDIUM,
                                                tags = listOf("media", if (isHtml) "page" else type.name.lowercase(), file.name)
                                            )
                                        }
                                    }
                                }
                                // "Like every LLM": what meow just made goes straight
                                // into the browser panel so the user actually sees it.
                                showFileInBrowser(file)
                            }
                            speak(result.lineSequence().firstOrNull { !it.startsWith("MEDIA_FILE:") } ?: "Done.")
                        }
                    }
                    TaskStatus.FAILED -> speak("Sorry, that didn't work: ${task.result}")
                    else -> { /* cancelled tasks stay quiet */ }
                }
            }
            agentOrchestrator = orchestrator
            viewModelScope.launch {
                orchestrator.tasks.collect { tasks ->
                    narrateAgentProgress(tasks)
                    _uiState.value = _uiState.value.copy(activeTasks = tasks)
                }
            }
            viewModelScope.launch {
                orchestrator.pendingApproval.collect { approval ->
                    _uiState.value = _uiState.value.copy(pendingApproval = approval)
                }
            }
        }

        rebuildHermesBrain()

        // If no local model path is configured but a catalog model is already
        // downloaded, adopt it — the app works on-device out of the box.
        if (secureStorage?.getEndpoint("local") == null) {
            downloader?.firstInstalledOption()
                ?.let { option -> downloader?.installedFile(option)?.absolutePath }
                ?.let { path -> secureStorage?.saveEndpoint("local", path) }
        }

        // If the configured local model file was deleted outside the app,
        // re-point at any bundle still on disk — otherwise the banner says
        // "no model installed" forever while a perfectly good one sits there.
        secureStorage?.getEndpoint("local")
            ?.takeIf { !java.io.File(it).exists() }
            ?.let {
                downloader?.listInstalled()?.firstOrNull()?.absolutePath
                    ?.let { path -> secureStorage?.saveEndpoint("local", path) }
            }

        // Load initial provider
        updateProvider(_uiState.value.selectedModel)
        // Listening is armed by MainActivity after the VM is assigned
        // (reclaimWakeMicForForeground) — arming here too double-opened STT.
    }

    override fun onCleared() {
        bodyScheduler.stop()
        contextDirector?.stop()
        motionSense?.stop()
        idleAnimationDirector?.stop()
        // The driving detector holds an accelerometer registration AND a GPS
        // request at 0 ms / 0 m — the most expensive subscription in the app.
        // Nothing released them when the ViewModel died, so they kept draining
        // the battery for the life of the process. setCarModeEnabled(false) is
        // the public route to its stop().
        carDetector?.setCarModeEnabled(false)
        roadFeedJob?.cancel()
        thinkingWatchdogJob?.cancel()
        alwaysOnSessionWatchdog?.cancel()
        stopWakeLoopAndRelease()
        (currentProvider as? LiteRtProvider)?.close()
        // The offscreen WebView holds a renderer process — not releasing it
        // leaks a native browser process for the life of the app.
        offscreenBrowser?.detach()
        // Leaving the inventory service bound would keep that app's process alive for as
        // long as this one lives, for nobody's benefit.
        inventoryClient?.unbind()
        super.onCleared()
    }

    /**
     * Which provider a fresh install starts on: the bundled local model is the
     * safe default (nothing to download for the router's last resort). An
     * explicit choice in the picker always wins.
     */

    /**
     * What a fresh install talks to.
     *
     * Omniroute: it routes between whatever this phone actually has, and its last
     * resort is the on-device model, so it is safe as a first-run default — there is no
     * server it depends on and nothing it needs configured to work.
     */
    private fun defaultModelLabel(context: android.content.Context?): String = MODEL_OMNIROUTE

    /**
     * Assembles the local route table from the backends this phone has configured.
     *
     * Only what the user set up gets a route. Nothing is inferred and no address is
     * hard-coded — in particular the app never reaches for the meow server on its own.
     * The on-device model is always present and always last, which is what makes the
     * router safe to be the default.
     */
    /** The local router, when it is the active provider — held so it can be hinted. */
    private var omniRoute: com.example.meowmba.core.agent.OmniRoute? = null

    /** Remembered SSH servers (encrypted). Exposed for the Settings UI. */
    var sshHostRegistry: com.example.meowmba.core.ssh.SshHostRegistry? = null
        private set

    /** IMU awareness — avatar leans with the phone; shake startles the cat. */
    var motionSense: com.example.meowmba.core.sensor.MotionSense? = null
        private set


    /** Watches screen-watch frames and turns them into reactions/commentary. */
    private var contextDirector: com.example.meowmba.core.context.ContextDirector? = null

    /** Live browser agent: keeps a standing goal and monitors/edits the page. */
    private var browserAgent: com.example.meowmba.core.agent.BrowserAgentLoop? = null

    /** Phone-control agent: autonomous multi-step phone operation via accessibility. */
    private var phoneAgentLoop: com.example.meowmba.core.agent.PhoneAgentLoop? = null

    /** One-shot flag: MainActivity watches this to launch the MediaProjection consent. */
    private val _screenWatchConsentRequested = kotlinx.coroutines.flow.MutableStateFlow(false)
    val screenWatchConsentRequested: kotlinx.coroutines.flow.StateFlow<Boolean> =
        _screenWatchConsentRequested.asStateFlow()
    /** Roll in degrees for the avatar lean (±8°), 0 when no IMU. */
    val motionTiltDeg: kotlinx.coroutines.flow.StateFlow<Float> = _motionTilt.asStateFlow()

    /**
     * Speech energy 0..1 from TTS word boundaries; animates the mouth.
     *
     * Its own flow rather than a [CompanionState] field: this changes ~14x a
     * second while speaking, and CompanionState is passed by value to the
     * avatar, so a copy() per boundary recomposed the entire face for the
     * length of every reply. AvatarRenderer reads it only inside the draw
     * lambda, so a pulse now invalidates draw and nothing else.
     */
    private val _mouthEnergy = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val mouthEnergy: kotlinx.coroutines.flow.StateFlow<Float> = _mouthEnergy.asStateFlow()

    /** Live lateral force on the phone, normalized -1..1. Turns lean the avatar. */
    val motionLean: kotlinx.coroutines.flow.StateFlow<Float> = _motionLean.asStateFlow()

    /** Live longitudinal force, normalized -1..1 (+ = braking). Lurches the avatar. */
    val motionLurch: kotlinx.coroutines.flow.StateFlow<Float> = _motionLurch.asStateFlow()

    /**
     * Base URL for a cloud provider — the Settings value when the user set one,
     * otherwise the built-in default. Blank Settings endpoint = use default.
     */
    private fun resolveEndpoint(
        storage: com.example.meowmba.core.security.SecureStorageManager,
        provider: String,
        defaultUrl: String,
    ): String = storage.getEndpoint(provider)?.trim().takeIf { !it.isNullOrBlank() } ?: defaultUrl

    private fun buildOmniRoute(): com.example.meowmba.core.agent.OmniRoute? {
        val storage = secureStorage ?: return null
        val routes = mutableListOf<com.example.meowmba.core.agent.OmniRoute.Route>()

        // OpenRouter — the standalone cloud brain. meow is a Play-Store app:
        // no home server, no LAN dependency. The user pastes their own key in
        // Settings; without one this route errors honestly and OmniRoute
        // falls through to the on-device model, which always works offline.
        val openRouterKey = storage.getApiKey("openrouter")
        val openRouterModel = storage.getModelName("openrouter") ?: OPENROUTER_DEFAULT_MODEL
        if (!openRouterKey.isNullOrBlank()) {
            routes += com.example.meowmba.core.agent.OmniRoute.Route(
                id = "openrouter",
                label = "OpenRouter · $openRouterModel",
                provider = RemoteModelProvider(
                    "OpenRouter", resolveEndpoint(storage, "openrouter", OPENROUTER_ENDPOINT), openRouterKey, openRouterModel,
                ),
                tags = setOf(com.example.meowmba.core.agent.OmniRoute.RouteTag.CHAT, com.example.meowmba.core.agent.OmniRoute.RouteTag.CODING, com.example.meowmba.core.agent.OmniRoute.RouteTag.REASONING),
                priority = 10,
            )
            // Voice-call lane: a flash model, not the 120B chat brain.
            routes += com.example.meowmba.core.agent.OmniRoute.Route(
                id = "call",
                label = "Call · $OPENROUTER_CALL_MODEL",
                provider = RemoteModelProvider(
                    "OpenRouter", OPENROUTER_ENDPOINT, openRouterKey, OPENROUTER_CALL_MODEL,
                ),
                tags = setOf(
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.FAST,
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.CHAT,
                ),
                priority = 0,
            )
        }
        storage.getApiKey("opencode")?.takeIf { it.isNotBlank() }?.let { key ->
            routes += com.example.meowmba.core.agent.OmniRoute.Route(
                id = "bigpickle",
                label = "BigPickle (OpenCode Zen)",
                provider = RemoteModelProvider(
                    "BigPickle", resolveEndpoint(storage, "opencode", "https://opencode.ai/zen/v1"), key, "big-pickle",
                ),
                tags = setOf(com.example.meowmba.core.agent.OmniRoute.RouteTag.CHAT, com.example.meowmba.core.agent.OmniRoute.RouteTag.CODING, com.example.meowmba.core.agent.OmniRoute.RouteTag.REASONING),
                priority = 25,
            )
        }
        storage.getApiKey("openai")?.takeIf { it.isNotBlank() }?.let { key ->
            routes += com.example.meowmba.core.agent.OmniRoute.Route(
                id = "openai",
                label = "OpenAI · ${storage.getModelName("openai") ?: "gpt-4o-mini"}",
                provider = RemoteModelProvider(
                    "OpenAI", resolveEndpoint(storage, "openai", "https://api.openai.com"), key,
                    storage.getModelName("openai") ?: "gpt-4o-mini",
                ),
                tags = setOf(
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.FAST,
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.REASONING,
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.CODING,
                    com.example.meowmba.core.agent.OmniRoute.RouteTag.CHAT,
                ),
                priority = 30,
            )
        }
        // On-device fallback only when a real model file is present. A missing
        // path used to register a broken route that failed every offline turn
        // with "Model file not found" and looked like "local model is dead".
        runCatching {
            val path = resolvedLocalModelPath()
            if (!java.io.File(path).isFile || java.io.File(path).length() == 0L) {
                android.util.Log.w(
                    "CompanionViewModel",
                    "skipping on-device route: no model at $path"
                )
            } else {
                val local: ModelProvider = if (path.endsWith(".litertlm")) {
                    LiteRtProvider(storage.context, path)
                } else {
                    MediaPipeProvider(storage.context, path)
                }
                localBrain = local
                // Lazy-load: the ~2.6GB LiteRT weights load on the first
                // generate call (engine()), not at route build. Warming them
                // eagerly here left the phone slow and the launcher janky for
                // the whole idle window after any route rebuild.
                routes += com.example.meowmba.core.agent.OmniRoute.Route(
                    id = "local",
                    label = "On-device",
                    provider = local,
                    tags = setOf(com.example.meowmba.core.agent.OmniRoute.RouteTag.FAST, com.example.meowmba.core.agent.OmniRoute.RouteTag.CHAT),
                    priority = 90,
                    offline = true,
                )
            }
        }
        if (routes.isEmpty()) return null
        // "Default brain" setting: force one provider as the primary brain.
        // Everything else is dropped except the on-device fallback, which is
        // always kept so the cat never loses its voice offline.
        // Default brain: OpenRouter when the user has saved a key, else the
        // on-device model; an explicit Settings choice always wins.
        val forced = (storage.getPreference("default_brain")
            ?: if (openRouterKey.isNullOrBlank()) "local" else "openrouter")
            .takeIf { it.isNotBlank() && it != "auto" }
        val finalRoutes = if (forced != null && routes.any { it.id == forced }) {
            routes.filter { it.id == forced || it.offline || it.id == "call" }.map {
                if (it.id == forced) it.copy(priority = 1) else it
            }
        } else routes
        return com.example.meowmba.core.agent.OmniRoute(finalRoutes).also { omniRoute = it }
    }

    /** Re-assemble the route table after Settings saved new keys/brain choice. */
    fun rebuildOmniRoute() {
        if (_uiState.value.selectedModel == MODEL_OMNIROUTE) {
            omniRoute = buildOmniRoute()
        }
    }

    /**
     * Which OmniRoute id should answer a normal chat turn.
     * Honors Settings "Default brain"; never pins OpenRouter over BigPickle.
     */
    internal fun preferredChatRouteId(): String? {
        val stored = secureStorage?.getPreference("default_brain")?.takeIf { it.isNotBlank() }
        fun has(id: String): Boolean = when (id) {
            "bigpickle" -> !secureStorage?.getApiKey("opencode").isNullOrBlank()
            "openrouter" -> !secureStorage?.getApiKey("openrouter").isNullOrBlank()
            "openai" -> !secureStorage?.getApiKey("openai").isNullOrBlank()
            "local" -> true
            else -> false
        }
        if (stored != null && stored != "auto" && has(stored)) return stored
        return listOf("openrouter", "bigpickle", "openai").firstOrNull { has(it) }
    }

    /** Instant voice-call lane: flash model first, on-device last. */
    internal fun preferredFastRouteId(): String? {
        if (!secureStorage?.getApiKey("openrouter").isNullOrBlank()) return "call"
        if (!secureStorage?.getApiKey("openai").isNullOrBlank()) return "openai"
        return "local"
    }

    private fun updateProvider(modelName: String) {
        refreshLoadedModels()
        if (secureStorage == null) return

        val mainProvider: ModelProvider? = when (modelName) {
            "On-device (Local)" -> {
                val path = resolvedLocalModelPath()
                if (!java.io.File(path).isFile || java.io.File(path).length() == 0L) {
                    // runCatching like fuLog: android.util.Log is a stub under plain JUnit.
                    runCatching {
                        android.util.Log.w(
                            "CompanionViewModel",
                            "On-device selected but no model file at $path"
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        captionText = "No on-device model installed — open Hardware and download one",
                    )
                    null
                } else if (path.endsWith(".litertlm")) {
                    LiteRtProvider(secureStorage.context, path).also { lite ->
                        localBrain = lite
                        // Lazy-load: weights load on the first generate call,
                        // not at route build. Eager warm-up of the 2.6GB model
                        // on every route build kept the phone slow after each
                        // conversation.
                    }
                } else {
                    MediaPipeProvider(secureStorage.context, path)
                }
            }
            "OpenRouter (Cloud)" -> {
                RemoteModelProvider(
                    "OpenRouter",
                    resolveEndpoint(secureStorage, "openrouter", OPENROUTER_ENDPOINT),
                    secureStorage.getApiKey("openrouter"),
                    secureStorage.getModelName("openrouter") ?: OPENROUTER_DEFAULT_MODEL
                )
            }
            MODEL_OMNIROUTE -> buildOmniRoute()
            "OpenAI (Cloud)" -> {
                RemoteModelProvider(
                    "OpenAI",
                    resolveEndpoint(secureStorage, "openai", "https://api.openai.com"),
                    secureStorage.getApiKey("openai"),
                    secureStorage.getModelName("openai") ?: "gpt-4o-mini"
                )
            }
            else -> null
        }

        currentProvider = mainProvider
    }

    /** The provider to generate with. All providers build synchronously. */
    private suspend fun awaitProvider(): ModelProvider? = currentProvider

    /**
     * The local model file the provider will actually load: the configured
     * path, else a downloaded catalog model, else the legacy adb default.
     */
    private fun resolvedLocalModelPath(): String {
        val configured = secureStorage?.getEndpoint("local")
        if (configured != null && java.io.File(configured).exists()) return configured
        // Catalog pick first, then any .task/.litertlm sitting in models/
        downloader?.firstInstalledOption()
            ?.let { option -> downloader?.installedFile(option)?.absolutePath }
            ?.let { return it }
        downloader?.listInstalled()?.firstOrNull()?.absolutePath?.let { return it }
        // Last resort: configured path (even if missing) so error messages show it,
        // else the historical adb path.
        return configured ?: "/data/local/tmp/llm/gemma.bin"
    }

    /**
     * Snapshot of what model is loaded on-device vs. on servers, derived from
     * the same configuration used to build the providers. The local name is
     * the real model file (catalog display name when recognized, file name
     * otherwise) — never the provider-picker label. Also refreshes the list
     * of model bundles actually present on disk. Displayed on the Hardware
     * (LLM Manager) page.
     */
    private fun refreshLoadedModels() {
        val selected = _uiState.value.selectedModel
        val source = when {
            selected.contains("(Local)") -> ModelSource.LOCAL
            // The old picker said "(Local Network)"; the standalone build's
            // remote brain is OpenRouter — still the NETWORK lane.
            selected.contains("Network") || selected.contains("OpenRouter") -> ModelSource.NETWORK
            else -> ModelSource.CLOUD
        }
        val localPath = resolvedLocalModelPath()
        val localFileName = java.io.File(localPath).name
        _uiState.value = _uiState.value.copy(
            loadedModels = LoadedModelsInfo(
                localName = ModelCatalog.optionForFile(localFileName)?.displayName ?: localFileName,
                localPath = localPath,
                localRuntime = "MediaPipe LLM Inference",
                cloudProvider = "OpenAI",
                cloudModel = secureStorage?.getModelName("openai") ?: "gpt-4o-mini",
                cloudEndpoint = "https://api.openai.com",
                cloudKeyConfigured = !secureStorage?.getApiKey("openai").isNullOrBlank(),
                networkEndpoint = OPENROUTER_ENDPOINT,
                networkModel = secureStorage?.getModelName("openrouter") ?: OPENROUTER_DEFAULT_MODEL,
                localInstalled = java.io.File(localPath).exists(),
                activeSource = source
            ),
            installedModels = downloader?.listInstalled()?.map { f ->
                InstalledModelFile(
                    fileName = f.name,
                    sizeBytes = f.length(),
                    catalogDisplayName = ModelCatalog.optionForFile(f.name)?.displayName
                )
            } ?: emptyList()
        )
    }

    // ── Voice modes ─────────────────────────────────────────────────────────

    /** Enable/disable always-on wake phrase listening (persisted). */
    fun setWakeWordEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wakeWordEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_WAKE_WORD, enabled)
        updateVoiceListeningMode()
    }

    /**
     * Require the wake phrase even in face mode (persisted).
     *
     * With it on, face mode listens for "hey [name]" instead of staying always-on, which is
     * the pre-5741ace behavior. Turning it on when the wake word is off also arms the wake
     * word — requiring a wake word that is disabled would leave face mode silent.
     */
    fun setRequireWakeInFace(enabled: Boolean) {
        secureStorage?.saveBooleanPreference(KEY_REQUIRE_WAKE_IN_FACE, enabled)
        if (enabled && !_uiState.value.wakeWordEnabled) {
            secureStorage?.saveBooleanPreference(KEY_WAKE_WORD, true)
            _uiState.value = _uiState.value.copy(wakeWordEnabled = true)
        }
        _uiState.value = _uiState.value.copy(requireWakeWordInFace = enabled)
        updateVoiceListeningMode()
    }

    /** Enable/disable conversation follow-up mode (persisted). */
    fun setConversationMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(conversationMode = enabled)
        secureStorage?.saveBooleanPreference(KEY_CONVERSATION, enabled)
    }

    /** Face-mode display setting: keep the screen awake while the face shows. */
    /**
     * Watch mode: meow glances at the phone screen every few seconds.
     * Enabling goes through the one-time MediaProjection consent dialog;
     * disabling stops the capture service immediately.
     */
    fun setWatchMode(enabled: Boolean) {
        if (enabled) {
            _screenWatchConsentRequested.value = true
        } else {
            secureStorage?.context?.let { com.example.meowmba.core.screen.ScreenWatchService.stop(it) }
            contextDirector?.stop()
            contextDirector = null
            secureStorage?.saveBooleanPreference(KEY_WATCH_MODE, false)
            _uiState.value = _uiState.value.copy(watchModeEnabled = false)
        }
    }

    fun consumeScreenWatchConsentRequest() {
        _screenWatchConsentRequested.value = false
    }

    fun onScreenCaptureGranted(resultCode: Int, data: android.content.Intent) {
        val ctx = secureStorage?.context ?: return
        com.example.meowmba.core.screen.ScreenWatchService.start(ctx, resultCode, data)
        secureStorage?.saveBooleanPreference(KEY_WATCH_MODE, true)
        _uiState.value = _uiState.value.copy(watchModeEnabled = true)
        startContextDirector()
    }

    /** Fuses screen-watch frames into reactions; see ContextDirector. */
    private fun startContextDirector() {
        if (contextDirector != null) return
        contextDirector = com.example.meowmba.core.context.ContextDirector(
            scope = viewModelScope,
            describe = { jpeg ->
                visionProviderFor()?.describe(
                    jpeg,
                    "In one short sentence, what is on this screen right now?",
                )?.getOrNull()
            },
            onEvent = { event ->
                val anim = when (event.kind) {
                    "game" -> "happy bop"
                    "error" -> "ear wiggle"
                    "document", "browser", "media" -> "slow blink"
                    else -> null
                }
                anim?.let { CustomAnimations.BUILT_INS[it] }?.let { spec ->
                    // Watch-mode reaction to screen content — ambient, so it
                    // must not dismiss the screen the user is looking at.
                    if (faceModeActive(_uiState.value) &&
                        _uiState.value.interactionState == InteractionState.IDLE &&
                        _uiState.value.activeAnimation == null
                    ) playCustomAnimation(spec, userRequested = false)
                }
            },
            onComment = { text -> speak(text) },
            commentaryEnabled = _uiState.value.commentaryEnabled,
            canSpeak = {
                _uiState.value.interactionState == InteractionState.IDLE ||
                    _uiState.value.interactionState == InteractionState.SPEAKING
            },
        ).also { it.start() }
    }

    /** Pin/unpin an app: up to 8 favorites; the first two sit on the face. */
    fun togglePin(packageName: String) {
        val current = _pinnedPackages.value
        val next = if (packageName in current) {
            current - packageName
        } else {
            if (current.size >= 8) {
                speak("Favorites are full — unpin something first.")
                return
            }
            current + packageName
        }
        _pinnedPackages.value = next
        secureStorage?.savePreference(KEY_PINNED_APPS, next.joinToString(","))
    }

    fun createDrawerFolder(name: String, firstPackage: String? = null) {
        val next = DrawerCatalog.createFolder(
            _drawerFolders.value,
            name,
            firstPackage,
            id = System.currentTimeMillis().toString(36),
        )
        saveDrawerFolders(next)
    }

    fun addAppToDrawerFolder(folderId: String, packageName: String) {
        saveDrawerFolders(DrawerCatalog.addToFolder(_drawerFolders.value, folderId, packageName))
    }

    fun removeAppFromDrawerFolder(folderId: String, packageName: String) {
        saveDrawerFolders(DrawerCatalog.removeFromFolder(_drawerFolders.value, folderId, packageName))
    }

    fun renameDrawerFolder(folderId: String, name: String) {
        saveDrawerFolders(DrawerCatalog.renameFolder(_drawerFolders.value, folderId, name))
    }

    fun deleteDrawerFolder(folderId: String) {
        saveDrawerFolders(DrawerCatalog.deleteFolder(_drawerFolders.value, folderId))
    }

    private fun saveDrawerFolders(next: List<DrawerFolder>) {
        _drawerFolders.value = next
        secureStorage?.savePreference(KEY_DRAWER_FOLDERS, DrawerCatalog.encodeFolders(next))
    }

    /** Swipe right on the face: app drawer. */
    fun toggleAppDrawer() {
        _uiState.value = _uiState.value.copy(
            showAppDrawer = !_uiState.value.showAppDrawer,
            showBrowser = false,
        )
    }

    /** Swipe left on the face: the browser meow can drive. */
    fun toggleBrowser() {
        val opening = !_uiState.value.showBrowser
        _uiState.value = _uiState.value.copy(
            showBrowser = opening,
            showAppDrawer = false,
        )
        if (opening) browserAgent?.browserOpened() else browserAgent?.browserClosed()
    }

    /** Open the agent browser and give Weaver a standing goal (build or scrape). */
    fun driveAgentBrowser(req: com.example.meowmba.core.work.BrowserRequest) {
        if (req.job == com.example.meowmba.core.work.BrowserJob.OPEN) {
            if (!_uiState.value.showBrowser) toggleBrowser()
            viewModelScope.launch {
                waitForBrowserAttached()
                com.example.meowmba.core.browser.WebViewBridge.loadWorkshop()
            }
            speak("Agent browser is open. Ask me to make a page or scrape a site.")
            return
        }
        if (!_uiState.value.showBrowser) toggleBrowser()
        val draft = if (req.job == com.example.meowmba.core.work.BrowserJob.BUILD) {
            com.example.meowmba.core.agent.BrowserAgentLoop.scaffoldHtml(req.goal)
        } else null
        _uiState.value = _uiState.value.copy(
            browserGoal = req.goal,
            browserSource = draft ?: "Working: ${req.goal}",
            browserSourceKind = if (draft != null) "html" else "prompt",
        )
        viewModelScope.launch {
            if (!waitForBrowserAttached()) {
                speak("The browser pane didn't open — swipe left and ask again.")
                return@launch
            }
            when {
                req.url != null -> com.example.meowmba.core.browser.WebViewBridge.loadUrl(req.url)
                draft != null -> com.example.meowmba.core.browser.WebViewBridge.loadHtml(draft)
            }
            if (req.url != null) delay(900)
            val agent = browserAgent
            if (agent == null) {
                speak("The page is up. The restyle loop isn't in this build.")
            } else {
                agent.setGoal(req.goal)
            }
        }
    }

    private suspend fun waitForBrowserAttached(timeoutMs: Long = 3_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (com.example.meowmba.core.browser.WebViewBridge.isAttached) return true
            delay(80)
        }
        return com.example.meowmba.core.browser.WebViewBridge.isAttached
    }

    /**
     * Drive the page with the same default brain as chat. The old path called
     * [OmniRoute.generate] with no pin and dropped every Error, so a missing
     * key or a prose reply left the workshop blank.
     */
    private suspend fun askBrowserBrain(prompt: String): String? {
        val hint = _uiState.value.browserGoal ?: "build a web page"
        omniRoute?.hintUserText(hint)
        val provider = omniRoute ?: currentProvider ?: return null
        return runCatching {
            if (provider is com.example.meowmba.core.agent.OmniRoute) {
                val full = StringBuilder()
                var err: String? = null
                provider.generateStreaming(prompt, preferRouteId = preferredChatRouteId())
                    .collect { r ->
                        when (r) {
                            is com.example.meowmba.core.agent.ModelResponse.Partial -> {
                                full.append(r.text)
                                _uiState.value = _uiState.value.copy(
                                    browserSource = full.toString(),
                                    browserSourceKind = "prompt",
                                )
                            }
                            is com.example.meowmba.core.agent.ModelResponse.Complete ->
                                if (full.isBlank()) full.append(r.text)
                            is com.example.meowmba.core.agent.ModelResponse.Error ->
                                err = r.message
                        }
                    }
                full.toString().takeIf { it.isNotBlank() }
                    ?: err?.let { "ERROR: $it" }
            } else {
                when (val r = provider.generate(prompt)) {
                    is com.example.meowmba.core.agent.ModelResponse.Complete ->
                        r.text.takeIf { it.isNotBlank() }
                    is com.example.meowmba.core.agent.ModelResponse.Error ->
                        "ERROR: ${r.message}"
                    else -> null
                }
            }
        }.getOrNull()
    }

    fun closeOverlays() {
        _uiState.value = _uiState.value.copy(showAppDrawer = false, showBrowser = false)
    }

    /**
     * Answer a question straight from the brain. Same chat pipeline as a normal
     * turn, but agent routing is skipped entirely — a question is answered,
     * not converted into a queued task that narrates "looking it up".
     */
    private fun respondDirectly(text: String) {
        // System.err on purpose: shows up in logcat as "System.err: MeowDirect"
        // but does not crash JVM unit tests where android.util.Log is unmocked.
        System.err.println("MeowDirect: answering directly: $text")
        // Mirror the normal pipeline's turn bookkeeping so the UI (and the
        // voice pipeline tests) see the same THINKING transition.
        followUpJob?.cancel()
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.THINKING,
            captionText = text,
            emotion = CompanionEmotion.CURIOUS
        )
        val epoch = supersedeResponseTurn()
        responseJob = viewModelScope.launch(swallowAsyncFatals) {
            val userMessageId = persistTurn(MessageRole.USER, text)
            val onCall = _uiState.value.callMode
            val recalled = if (onCall) emptyList() else recallMemories(text)
            if (recalled.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    recalledMemories = recalled.map {
                        RecalledMemory(it.memory.content.take(80), it.reason)
                    }
                )
                memoryRepository?.touchRecalled(recalled.map { it.memory.id })
            }
            val provider: ModelProvider? = if (onCall) {
                omniRoute ?: buildOmniRoute()?.also { omniRoute = it } ?: currentProvider
            } else {
                omniRoute ?: currentProvider ?: awaitProvider()
            }
            if (provider == null) {
                delay(1500)
                speak("No model is ready yet. Download one from the Hardware page, or configure a server in Settings.")
                return@launch
            }
            val recentMessages = memoryRepository?.getRecentMessages(
                if (onCall) 4 else 10,
                conversationId(),
            ) ?: emptyList()
            // No tool list here on purpose: questions get answered from knowledge,
            // and the capabilities block is what tempted the model into "I can look
            // that up if you say the word" deflections.
            val prompt = PromptBuilder.build(
                _uiState.value.name, text, recentMessages, recalled,
                learned = if (onCall) null else learnedBlock(),
                speakerName = lastSpeakerName,
                deviceFacts = liveDeviceFacts(),
                voiceCall = onCall,
            )
            var fullResponse = ""
            var spokenUpTo = 0
            omniRoute?.hintUserText(text)
            // Voice call uses the flash/fast lane. Other direct answers pin
            // the Settings default brain (short questions used to skip it).
            val stream = if (provider is com.example.meowmba.core.agent.OmniRoute) {
                if (onCall) {
                    provider.generateStreaming(
                        prompt,
                        preferRouteId = preferredFastRouteId(),
                        forceTag = com.example.meowmba.core.agent.OmniRoute.RouteTag.FAST,
                    )
                } else {
                    provider.generateStreaming(prompt, preferRouteId = preferredChatRouteId())
                }
            } else {
                provider.generateStreaming(prompt)
            }
            stream.collect { response ->
                if (epoch != responseEpoch) return@collect
                when (response) {
                    is ModelResponse.Partial -> {
                        // On-device models can emit literal \n \t etc; unescape so SpeechChunker
                        // splits on real newlines and TTS speaks natural pauses.
                        val partial = response.text
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\r", "\r")
                        fullResponse += partial
                        _uiState.value = _uiState.value.copy(captionText = fullResponse)
                        val chunk = com.example.meowmba.core.audio.SpeechChunker
                            .nextChunk(fullResponse, spokenUpTo)
                        if (chunk != null) {
                            spokenUpTo += chunk.length
                            speakStreaming(chunk.trim(), first = spokenUpTo == chunk.length)
                        }
                    }
                    is ModelResponse.Complete -> {
                        if (fullResponse.isBlank()) {
                            showProviderError("${_uiState.value.selectedModel} returned an empty response")
                        } else {
                            val tail = fullResponse.substring(spokenUpTo).trim()
                            if (tail.isNotEmpty()) {
                                speakStreaming(tail, first = spokenUpTo == 0)
                            }
                            recordSpokenResponse(fullResponse)
                            if (!_uiState.value.memoryPaused) {
                                memoryRepository?.saveEpisodicCheckpoint(text, fullResponse, userMessageId)
                                pushMemoriesToMem0()
                            }
                        }
                    }
                    is ModelResponse.Error -> showProviderError(response.message)
                }
            }
        }
    }

    /** Run a web search in a real browser (Chrome) rather than the app panel. */
    fun showSearchInBrowser(query: String) {
        val ctx = secureStorage?.context
        val url = "https://www.google.com/search?q=" +
            java.net.URLEncoder.encode(query, "UTF-8")
        if (ctx != null) {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startInChrome(ctx, intent)) {
                _uiState.value = _uiState.value.copy(
                    captionText = "Searching for \"$query\" in Chrome.",
                )
                speak("Searching for that in the browser now.")
                return
            }
        }
        speak("I couldn't open a browser for that.")
    }

    /** "search X in/on <app>": launch the app's search screen via deep link. */
    private fun deepLinkSearch(target: AgentRouter.DeepLinkTarget) {
        val ctx = secureStorage?.context ?: run {
            speak("I can't open ${target.app} in this build.")
            return
        }
        viewModelScope.launch {
            try {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target.url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                speak("Opening ${target.app} and searching for \"${target.query}\".")
            } catch (e: Exception) {
                speak("I couldn't open ${target.app}: ${e.message}")
            }
        }
    }

    // ── "Show it in the browser" ────────────────────────────────────────────

    /**
     * Open the browser panel and display the newest thing meow made. Pages
     * load directly; images/video/audio get a minimal black wrapper page so
     * the WebView renders them like any LLM chat would.
     */
    fun showLatestArtifactInBrowser() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { findLatestArtifact() }
            if (file == null) {
                if (!_uiState.value.showBrowser) toggleBrowser()
                _uiState.value = _uiState.value.copy(
                    captionText = "Browser open — nothing saved to show yet."
                )
                speak("The browser is open, but I don't have anything saved to show yet.")
            } else {
                showFileInBrowser(file)
            }
        }
    }

    /** Newest renderable artifact across the saved pages and the workspace. */
    private fun findLatestArtifact(): java.io.File? {
        val dirs = listOfNotNull(
            secureStorage?.context?.let { ctx: android.content.Context ->
                java.io.File(ctx.filesDir, "web_pages")
            },
            workspaceRoot
        )
        val exts = setOf(
            "html", "htm", "png", "jpg", "jpeg", "gif", "webp",
            "mp4", "webm", "wav", "mp3"
        )
        return dirs.flatMap { it.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile && it.extension.lowercase() in exts }
            .maxByOrNull { it.lastModified() }
    }

    /**
     * Show one generated artifact by handing it to a real browser (Chrome).
     *
     * This replaced the in-app WebView panel. The panel swallowed ALL speech
     * while it was open (every utterance became a browser goal), which is why
     * "act like a dog" stopped performing, and it covered the face so anything
     * that did play was invisible. Artifacts now open in the browser the user
     * actually wants to look at.
     *
     * filesDir is app-private, so the file goes out through the FileProvider
     * declared in the manifest with a one-off read grant.
     */
    fun showFileInBrowser(file: java.io.File) {
        val ctx = secureStorage?.context ?: run {
            _uiState.value = _uiState.value.copy(
                captionText = "I can't open ${file.name} — no browser context in this build.",
            )
            return
        }
        val mime = mimeFor(file)
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", file,
            )
        }.getOrElse {
            _uiState.value = _uiState.value.copy(
                captionText = "I couldn't share ${file.name} with the browser.",
            )
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            .setDataAndType(uri, mime)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!startInChrome(ctx, intent)) {
            _uiState.value = _uiState.value.copy(
                captionText = "No browser installed to show ${file.name}.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            captionText = "Opened ${file.name} in Chrome.",
        )
    }

    /** Best-effort Chrome; any ACTION_VIEW handler is an acceptable fallback. */
    private fun startInChrome(ctx: android.content.Context, intent: android.content.Intent): Boolean {
        val chrome = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.google.android.apps.chrome",
        ).firstOrNull { pkg ->
            runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
        if (chrome != null) {
            val targeted = android.content.Intent(intent).setPackage(chrome)
            if (runCatching { ctx.startActivity(targeted) }.isSuccess) return true
        }
        // No Chrome (or it refused the URI): fall back to whatever can view it.
        val chooser = android.content.Intent.createChooser(intent, "Open with")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(chooser) }.isSuccess
    }

    private fun mimeFor(file: java.io.File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "kt", "json", "css" -> "text/plain"
        "js" -> "text/javascript"
        "py" -> "text/x-python"
        else -> "text/plain"
    }

    internal fun mediaPageHtml(file: java.io.File): String {
        val ext = file.extension.lowercase()
        if (ext == "html" || ext == "htm") {
            return runCatching { file.readText() }.getOrElse { "<pre>${file.name}</pre>" }
        }
        val mime = when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            else -> "text/plain"
        }
        if (ext in setOf("png", "jpg", "jpeg", "gif", "webp", "mp4", "webm", "wav", "mp3")) {
            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            val tag = when (ext) {
                "mp4", "webm" ->
                    "<video src='data:$mime;base64,$b64' controls autoplay playsinline style='max-width:100%;max-height:100vh'>"
                "wav", "mp3" ->
                    "<audio src='data:$mime;base64,$b64' controls autoplay style='width:90%'>"
                else ->
                    "<img src='data:$mime;base64,$b64' style='max-width:100%;max-height:100vh;display:block;margin:auto'>"
            }
            return "<!doctype html><html><body style='margin:0;background:#0d1117;display:flex;align-items:center;justify-content:center;min-height:100vh'>$tag</body></html>"
        }
        val body = runCatching { file.readText() }.getOrElse { file.name }
            .replace("&", "&amp;").replace("<", "&lt;")
        return "<!doctype html><html><body style='margin:0;background:#0d1117;color:#c9d1d9;padding:16px'><pre style='white-space:pre-wrap;font:13px/1.4 ui-monospace,monospace'>${body.take(20_000)}</pre></body></html>"
    }

    /** document.write wrapper page; outer JS uses double quotes, inner HTML single. */
    private fun mediaWrapperJs(body: String): String =
        "document.open();document.write(\"<!doctype html><html><body " +
            "style='margin:0;background:#000;display:flex;align-items:center;" +
            "justify-content:center;min-height:100vh'>$body</body></html>\");" +
            "document.close();'shown'"

    /** Phase G1: feed a life event into the mood engine and apply its effects. */
    private fun noteMoodEvent(event: com.example.meowmba.core.personality.MoodEvent) {
        moodEngine.onEvent(event, System.currentTimeMillis())
        applyMood()
    }

    private fun applyMood() {
        val (verbs, expression) = com.example.meowmba.core.robot.FaceMotorMap.mapMood(moodEngine.mood)
        verbs.forEach { robotManager.bodyOutput.perform(it) }
        motorController.setExpression(expression)
        (ttsManager as? com.example.meowmba.core.audio.TextToSpeechManager)?.let { tts ->
            tts.setPitch(moodEngine.ttsPitch())
            tts.setSpeechRate(moodEngine.ttsRate())
        }
        _uiState.value = _uiState.value.copy(moodLabel = moodEngine.mood.name.lowercase())
    }

    // ── Robot body (LOOI) ────────────────────────────────────────────────

    /** Ground-truth battery + body for the chat prompt. Never guessed. */
    private fun liveDeviceFacts(): String {
        val ctx = secureStorage?.context
        val battery = if (ctx != null) {
            com.example.meowmba.core.agent.tools.BatteryReading.factLine(
                com.example.meowmba.core.agent.tools.BatteryReading.read(ctx),
            )
        } else {
            "Phone battery percent is unknown — do not invent a number."
        }
        return com.example.meowmba.core.memory.PromptBuilder.deviceFacts(
            batteryLine = battery,
            robotLive = robotManager.hasLiveBody,
            robotStatus = _uiState.value.robotStatus,
        )
    }

    /** True while a real base is attached, so the link change is edge-triggered. */
    private var bodyAttached = false
    /** Camera state we changed for the body, so detaching can put it back. */
    private var autoCameraForBody = false
    private var cameraOnBeforeBody = false

    /**
     * A base that cannot see you cannot follow you, so attaching one is what
     * turns face tracking on — the camera is deliberately NOT on by default
     * (front camera + ML Kit is the battery hog), but it is the whole point of
     * the body. Detaching puts the camera back exactly as it was found, and
     * only if we were the ones who switched it on.
     */
    private fun onBodyLinkChanged(live: Boolean) {
        if (live == bodyAttached) return
        bodyAttached = live
        if (!live) {
            robotManager.haltMotion()
            if (!autoCameraForBody) return
            autoCameraForBody = false
            if (!cameraOnBeforeBody) {
                _uiState.value = _uiState.value.copy(
                    isCameraEnabled = false,
                    isPreviewVisible = false,
                )
                secureStorage?.saveBooleanPreference(KEY_CAMERA_ENABLED, false)
            }
            visionManager?.setFollowOnly(false)
            visionManager?.setFaceTrackingEnabled(false)
            return
        }
        cameraOnBeforeBody = _uiState.value.isCameraEnabled
        if (!cameraOnBeforeBody) {
            // Following needs the camera. Announce it rather than flipping a
            // privacy switch in silence.
            speak("Body attached — turning my camera on so I can follow you.")
        }
        robotManager.setTurnEncoding(
            robotManager.turnEncodingFrom(secureStorage?.getPreference("looi_turn_encoding")),
        )
        autoCameraForBody = true
        ensureFaceTrackingOn(followOnly = true)
    }

    /**
     * The user touched the screen: park the body. Turning and tilting move the
     * display, so following them has to yield while they are using it.
     */
    fun noteScreenTouch() {
        robotManager.pauseFollow()
    }

    /** The on-device brain, kept so it can be released when it goes idle. */
    private var localBrain: com.example.meowmba.core.agent.ModelProvider? = null

    /**
     * The on-device model is ~2 GB resident. Holding it forever put this phone
     * into swap and made everything — including the launcher, which is this
     * app — feel slow. Release it once nobody has asked it anything for a
     * while, sooner if the system is already short on memory; the next question
     * simply reloads it.
     */
    private fun releaseIdleLocalBrain(force: Boolean = false) {
        val lite = localBrain as? com.example.meowmba.core.agent.LiteRtProvider ?: return
        if (!lite.isLoaded) return
        val idle = lite.idleMs()
        val am = secureStorage?.context
            ?.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager
        val lowMemory = am?.let {
            val info = android.app.ActivityManager.MemoryInfo()
            it.getMemoryInfo(info)
            info.lowMemory || info.availMem < 1_000L * 1024L * 1024L
        } ?: false
        if (force || idle >= LOCAL_BRAIN_IDLE_RELEASE_MS ||
            (lowMemory && idle >= LOCAL_BRAIN_LOW_MEM_IDLE_MS)
        ) {
            if (lite.release()) {
                val reason = if (idle == Long.MAX_VALUE) "never used since launch"
                    else "idle ${idle / 1000L}s"
                android.util.Log.i(
                    "CompanionViewModel",
                    "released the on-device brain ($reason, lowMemory=$lowMemory)",
                )
            }
        }
    }

    /** (Re)resolve the active robot body from preferences. Non-blocking. */
    fun refreshRobot() {
        // Concurrency guard: screen on/off and settings toggles can call this
        // in rapid succession. Only one connect attempt runs at a time, or
        // competing LooiBleLink instances drop each other's GATT link.
        if (!robotConnectGuard.compareAndSet(false, true)) return
        val enabled = secureStorage?.getBooleanPreference("robot_body_enabled", false) == true
        val useBt = secureStorage?.getBooleanPreference("robot_use_bluetooth", true) == true
        val host = secureStorage?.getPreference("robot_host") ?: "192.168.1.200"
        val port = (secureStorage?.getPreference("robot_port")?.toIntOrNull()) ?: 9100
        val btAddress = secureStorage?.getPreference("robot_bt_address") ?: ""
        // Exponential backoff across failures: without it every onResume
        // burned a 20-40s BLE hunt + radio even when the base was nowhere near.
        if (enabled && useBt && robotManager.hasLiveBody.not()) {
            val backoffMs = (10_000L shl bleFailStreak.coerceAtMost(4)).coerceAtMost(10 * 60_000L)
            if (System.currentTimeMillis() - lastBleAttemptMs < backoffMs) {
                robotConnectGuard.set(false)
                return
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (!enabled) {
                    robotManager.useSimulated()
                    bleFailStreak = 0
                } else if (useBt) {
                    // Drop any stale "LOOI body (BLE)" from last launch before
                    // we hunt — otherwise Settings and the model keep saying
                    // we are on a base that is not here.
                    robotManager.useSimulated()
                    val ctx = secureStorage?.context
                    if (ctx == null) robotManager.useSimulated()
                    else {
                        lastBleAttemptMs = System.currentTimeMillis()
                        val bleOk = runCatching { robotManager.connectLooiBle(ctx, btAddress) }.getOrDefault(false)
                        if (bleOk) bleFailStreak = 0
                        else {
                            bleFailStreak++
                            if (btAddress.isNotBlank()) robotManager.connectBluetooth(btAddress)
                        }
                    }
                }
                else robotManager.connectLooi(host, port)
            } finally {
                robotConnectGuard.set(false)
            }
        }
    }

    fun setRobotBody(enabled: Boolean) {
        secureStorage?.saveBooleanPreference("robot_body_enabled", enabled)
        _uiState.value = _uiState.value.copy(robotBodyEnabled = enabled)
        refreshRobot()
    }

    fun setRobotHost(host: String) {
        secureStorage?.savePreference("robot_host", host)
        _uiState.value = _uiState.value.copy(robotHost = host)
    }

    fun setRobotPort(port: String) {
        secureStorage?.savePreference("robot_port", port)
        _uiState.value = _uiState.value.copy(robotPort = port)
    }

    fun setRobotUseBluetooth(useBt: Boolean) {
        secureStorage?.saveBooleanPreference("robot_use_bluetooth", useBt)
        _uiState.value = _uiState.value.copy(robotUseBluetooth = useBt)
        refreshRobot()
    }

    fun setRobotBtAddress(address: String) {
        secureStorage?.savePreference("robot_bt_address", address)
        _uiState.value = _uiState.value.copy(robotBtAddress = address)
        refreshRobot()
    }

    /** Populate the Settings base-picker: bonded devices plus a bounded BLE scan. */
    fun scanForRobotBases() {
        if (_uiState.value.robotScanning) return
        _uiState.value = _uiState.value.copy(robotScanning = true, robotScanResults = emptyList())
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val found = robotManager.discoverAllBases()
            _uiState.value = _uiState.value.copy(robotScanning = false, robotScanResults = found)
        }
    }

    fun setIdleChatter(enabled: Boolean) {
        secureStorage?.saveBooleanPreference(KEY_IDLE_CHATTER, enabled)
        _uiState.value = _uiState.value.copy(idleChatterEnabled = enabled)
    }

    /** Phase F3: jump the user to the system accessibility page. */
    fun openAccessibilitySettings() {
        val ctx = secureStorage?.context ?: return
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Phase E3: barge-in — stay listening 8 s after every exchange. */
    fun setBargeIn(enabled: Boolean) {
        secureStorage?.saveBooleanPreference(KEY_BARGE_IN, enabled)
        _uiState.value = _uiState.value.copy(bargeInEnabled = enabled)
    }

    /** Phase E1: enable/disable a routine; alarms re-arm via the DB observer. */
    fun setRoutineEnabled(id: Long, enabled: Boolean) {
        val dao = routineDao ?: return
        val ctx = secureStorage?.context ?: return
        viewModelScope.launch {
            dao.setEnabled(id, enabled)
            if (!enabled) com.example.meowmba.core.routines.RoutineScheduler.cancel(ctx, id)
        }
    }

    /** MainActivity forwards routine alarms here (launch or onNewIntent). */
    fun onRoutineIntent(routineId: Long) {
        if (routineId < 0) return
        viewModelScope.launch { runRoutine(routineId) }
    }

    private suspend fun runRoutine(id: Long) {
        val dao = routineDao ?: return
        val routine = dao.getById(id) ?: return
        if (!routine.enabled) return
        dao.markRun(id, System.currentTimeMillis())
        val ctx = secureStorage?.context
        when (routine.action) {
            "morning_brief" -> speak(buildMorningBrief())
            "memory_followup" -> speak(buildMemoryFollowUp())
            "play_music" -> if (ctx != null) {
                val result = com.example.meowmba.core.agent.tools.PlayMusicTool(ctx)
                    .execute(mapOf("query" to routine.launchTarget.ifBlank { "music" }))
                val line = when (result) {
                    is com.example.meowmba.core.agent.tools.ToolResult.Success ->
                        "Playing ${routine.name}."
                    is com.example.meowmba.core.agent.tools.ToolResult.Failure -> result.error
                }
                speak(line)
            } else if (routine.message.isNotBlank()) speak(routine.message)
            "launch_app" -> if (ctx != null) {
                val result = com.example.meowmba.core.agent.tools.LaunchAppTool(ctx)
                    .execute(mapOf("name" to routine.launchTarget.ifBlank { "music" }))
                val line = when (result) {
                    is com.example.meowmba.core.agent.tools.ToolResult.Success -> result.output
                    is com.example.meowmba.core.agent.tools.ToolResult.Failure -> result.error
                }
                speak(line)
            } else if (routine.message.isNotBlank()) speak(routine.message)
            else -> if (routine.message.isNotBlank()) speak(routine.message)
        }
    }

    /** E1 default template: date + weather in one breath. */
    private suspend fun buildMorningBrief(): String {
        val date = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))
        val weather = try {
            when (val r = weatherRepository?.fetch()) {
                is com.example.meowmba.core.weather.WeatherRepository.Result.Ok -> r.weather.spoken()
                else -> null
            }
        } catch (_: Exception) { null }
        return buildString {
            append("Good morning! It's ").append(date).append('.')
            weather?.let { append(' ').append(it).append('.') }
            append(" Have a good one.")
        }
    }

    /** E2: surface pending/todo memories once a day, three at most. */
    private suspend fun buildMemoryFollowUp(): String {
        val repo = memoryRepository ?: return "Memory follow-ups aren't available in this build."
        val pending = repo.getRecentMemories(200).filter { m ->
            m.tags.any { t -> t.equals("pending", true) || t.equals("todo", true) } ||
                Regex("""\b(todo|to-do|pending|follow ?up)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(m.content)
        }.take(3)
        if (pending.isEmpty()) return "Nothing pending — we're all caught up."
        return buildString {
            append("A few things we said we'd get back to: ")
            append(pending.joinToString("; ") { it.content.take(120) })
            append('.')
        }
    }

    fun setCommentaryMode(enabled: Boolean) {
        secureStorage?.saveBooleanPreference(KEY_COMMENTARY, enabled)
        contextDirector?.commentaryEnabled = enabled
        _uiState.value = _uiState.value.copy(commentaryEnabled = enabled)
    }

    fun onScreenCaptureDenied() {
        _uiState.value = _uiState.value.copy(watchModeEnabled = false)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(keepScreenOnInFaceMode = enabled)
        secureStorage?.saveBooleanPreference(KEY_KEEP_SCREEN_ON, enabled)
    }

    /** Toggle USB auto-bridge. When enabled, monitors USB and auto-pairs. */
    fun setUsbAutoBridge(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(usbAutoBridgeEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_USB_AUTO_BRIDGE, enabled)
        if (enabled) {
            usbAutoBridge?.start()
        } else {
            usbAutoBridge?.stop()
        }
    }

    /** Rename the companion; the wake phrase "Hey [name]" follows automatically. */
    fun renameCompanion(newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        _uiState.value = _uiState.value.copy(name = clean)
        secureStorage?.savePreference(KEY_COMPANION_NAME, clean)
        matcher = WakePhraseMatcher(clean)
        (wakeWordEngine as? RecognizerWakeWordEngine)?.updateMatcher(matcher)
        if (_uiState.value.wakeWordEnabled &&
            _uiState.value.interactionState == InteractionState.IDLE
        ) {
            _uiState.value = _uiState.value.copy(captionText = wakeHint())
        }
    }

    /** The idle caption hint, or null while always-on listening (live partials replace it). */
    private fun wakeHint(): String? = when {
        shouldAlwaysOnMic() -> null
        _uiState.value.wakeWordEnabled -> "Say \"Hey ${_uiState.value.name}\"…"
        else -> null
    }

    /** Wake phrase heard: run the spoken command, or listen for one. */
    internal fun onWakeDetected(command: String?) {
        stopWakeLoopAndRelease()
        if (!_uiState.value.isMicEnabled) {
            resetState()
            return
        }
        if (command != null) {
            processUserSpeech(command)
        } else {
            speakWakeAckThenListen()
        }
    }

    /**
     * Immediate audible callback when the wake phrase is heard with no command
     * attached: Meow says "Yes?" out loud, then opens the mic for the command.
     * A [wakeAckListenJob] opens the mic ~1.5s later so a silent or slow TTS
     * engine never strands the user waiting; [onSpeechFinished] consumes the
     * pending ack so the normal follow-up path doesn't double-own the mic.
     */
    private fun speakWakeAckThenListen() {
        wakeAckPending = true
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.SPEAKING,
            captionText = "Yes?",
            emotion = CompanionEmotion.HAPPY
        )
        speakStreaming("Yes?", first = true)
        wakeAckListenJob?.cancel()
        wakeAckListenJob = viewModelScope.launch {
            delay(1_500L)
            wakeAckPending = false
            // Fallback deadline only — the fast path in onSpeechFinished owns
            // the normal case. A background hand-off during the wait owns the
            // mic now, so do not fight it.
            if (!handedOffToBackground) startFollowUpListening()
        }
    }

    /** Begin standby wake-phrase listening (only while idle). */
    /**
     * Stops the in-app wake loop AND gives the mic back, so WakeWordService can take
     * over when the app goes to the background. Acquiring without releasing would let
     * the in-app side hold the mic forever and silently block background listening.
     */
    private fun stopWakeLoopAndRelease() {
        wakeWordEngine?.stop()
        com.example.meowmba.core.audio.MicOwner
            .release(com.example.meowmba.core.audio.MicOwner.IN_APP)
    }

    /**
     * Stop KWS and wait until AudioRecord is actually released. SpeechRecognizer
     * started against a still-held mic is ERROR_RECOGNIZER_BUSY — the dead mic button.
     */
    private suspend fun stopWakeLoopAndAwait() {
        wakeWordEngine?.stopAndAwait(KWS_RELEASE_TIMEOUT_MS)
        com.example.meowmba.core.audio.MicOwner
            .release(com.example.meowmba.core.audio.MicOwner.IN_APP)
    }

    /**
     * Called when the app goes to the background. Releases the wake mic so
     * WakeWordService can take over.
     *
     * This is not optional. Mic access for this app is granted FOREGROUND-ONLY (appops
     * "Uid mode: RECORD_AUDIO: foreground"), so once backgrounded the in-app AudioRecord
     * keeps returning zeros — measured 440 seconds of peak=0.000 while Snapchat held the
     * foreground. Worse, holding MicOwner meant the foreground service, which DOES have
     * the microphone service-type exemption, was refused the mic. Net effect: nothing
     * listening at all, with every log line claiming it was.
     */
    fun releaseWakeMicForBackground() {
        appInBackground = true
        stopAlwaysOnListening()
        stopWakeLoopAndRelease()
        // Same reasoning as the hand-off: nobody is talking to the local model
        // in the background, so do not make the phone choose between it and us.
        releaseIdleLocalBrain(force = true)
    }

    /** True while meow is still listening for a spoken turn (conversation follow-up). */
    fun isAwaitingFollowUp(): Boolean =
        _uiState.value.interactionState == InteractionState.LISTENING

    /**
     * Broader than [isAwaitingFollowUp]: true for any live conversation turn
     * (listening, thinking, or speaking) while the mic is usable. Used by the
     * background hand-off so a follow-up keeps working even when the app loses
     * the foreground mid-turn — e.g. right after opening Spotify, when the state
     * is still SPEAKING the acknowledgement rather than LISTENING.
     */
    fun isConversationLive(): Boolean {
        val s = _uiState.value
        return s.isMicEnabled && !s.isMuted &&
            s.interactionState in setOf(
                InteractionState.LISTENING,
                InteractionState.THINKING,
                InteractionState.SPEAKING,
            )
    }

    /**
     * Background hand-off for a live conversation: the in-app recognizer goes
     * deaf once the foreground is lost, so stop everything here and let
     * WakeWordService capture the follow-up with its mic exemption.
     */
    fun handOffListeningToBackground() {
        handedOffToBackground = true
        appInBackground = true
        fuLog("handOffListeningToBackground -> in-app listening frozen")
        // Silence the tail of the reply: anything still queued keeps playing
        // AFTER the background follow-up service opens its mic, and the
        // service would capture Meow's own voice as the user's reply.
        ttsManager?.stop()
        // Stop the body when the app leaves the foreground: a scheduler
        // outliving the foreground would leave the base driving unwatched.
        bodyScheduler.stop()
        followUpJob?.cancel()
        isFollowUpListen = false
        utteranceCapture?.cancel()
        utteranceCapture = null
        speechService?.stopListening()
        stopAlwaysOnListening()
        stopWakeLoopAndRelease()
        // Nothing reads the on-device model while the background service owns
        // the mic, and the service is starting its own engines right now.
        // Holding ~2GB through that hand-off is what got the HOME app killed by
        // lmkd on Back (measured: memory watermark breached, then SIG 9).
        releaseIdleLocalBrain(force = true)
        resetState()
        // resetState() left the state IDLE and therefore re-armed the in-app wake
        // loop, which re-grabbed the mic. That blocked the foreground service we are
        // about to start from ever owning the mic ("in-app wake loop owns the mic;
        // not starting a second one"), so the background follow-up went deaf. Release
        // the re-armed loop so the service can take the mic for this follow-up.
        wakeRearmJob?.cancel()
        stopWakeLoopAndRelease()
    }

    /** Called when the app returns to the foreground; retakes the wake mic. */
    fun reclaimWakeMicForForeground() {
        handedOffToBackground = false
        appInBackground = false
        updateVoiceListeningMode()
    }

    /**
     * Writes a missed-wake pre-roll snapshot as a WAV (16 kHz mono PCM16) so the audio
     * the spotter failed on can be pulled and transcribed. Capped at 5 files, oldest
     * dropped first — this is a diagnosis aid, not a recorder.
     */
    private fun saveWakeMissAudio(pcm: ShortArray): String {
        val dir = secureStorage?.context?.filesDir
            ?: throw IllegalStateException("no filesDir")
        val existing = dir.listFiles { f ->
            f.name.startsWith("wake_miss_") && f.name.endsWith(".wav")
        }?.sortedBy { it.lastModified() } ?: emptyList()
        existing.dropLast(4).forEach { it.delete() }
        val file = java.io.File(dir, "wake_miss_${System.currentTimeMillis()}.wav")
        val dataBytes = pcm.size * 2
        val header = java.io.ByteArrayOutputStream().apply {
            fun le32(v: Int) {
                write(v and 0xFF); write((v shr 8) and 0xFF)
                write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
            }
            fun le16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
            write("RIFF".toByteArray()); le32(36 + dataBytes); write("WAVE".toByteArray())
            write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
            le32(16_000); le32(16_000 * 2); le16(2); le16(16)
            write("data".toByteArray()); le32(dataBytes)
        }.toByteArray()
        file.outputStream().use { out ->
            out.write(header)
            val buf = ByteArray(dataBytes)
            for (i in pcm.indices) {
                val v = pcm[i].toInt()
                buf[i * 2] = (v and 0xFF).toByte()
                buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(buf)
        }
        return file.absolutePath
    }

    private fun startWakeStandby(force: Boolean = false) {
        if (handedOffToBackground) {
            // The background service owns the mic after a hand-off. Late
            // recognizer callbacks from the torn-down turn still route
            // through resetState() and used to re-arm the in-app spotter,
            // which then refused the service the mic ("in-app wake loop
            // owns the mic") and the follow-up never started.
            wakeLog( "not re-arming: conversation handed to background service")
            return
        }
        if (!force && shouldAlwaysOnMic()) {
            // The bare face owns the mic in always-on conversation mode; the
            // wake phrase would be noise on top of it.
            wakeLog( "not re-arming: face mode owns the mic")
            return
        }
        val state = _uiState.value.interactionState
        if (state != InteractionState.IDLE) {
            // "Wake word only works once" is what this looks like from outside, so say why.
            wakeLog( "not re-arming: interaction state is $state")
            return
        }
        if (utteranceCapture != null) {
            // A capture/follow-up session is still live; re-arming the spotter here is
            // what grabbed the mic and killed it. The capture's completion path re-arms.
            wakeLog( "not re-arming: a capture is still in flight")
            return
        }
        if (_uiState.value.isMicEnabled) {
            if (wakeWordEngine?.isRunning?.value == true) {
                wakeLog("already listening")
                _uiState.value = _uiState.value.copy(captionText = wakeHint())
                return
            }
            // Exclusive with WakeWordService; whoever holds the mic wins.
            if (com.example.meowmba.core.audio.MicOwner
                    .acquire(com.example.meowmba.core.audio.MicOwner.IN_APP)
            ) {
                wakeWordEngine?.start()
            } else {
                android.util.Log.w(
                    TAG_WAKE,
                    "mic held by ${com.example.meowmba.core.audio.MicOwner.current}; retrying once"
                )
                // The command turn's recognizer can still be tearing down when we get here,
                // so one bounded retry covers the handoff without spinning.
                wakeRearmJob?.cancel()
                wakeRearmJob = viewModelScope.launch {
                    delay(WAKE_REARM_RETRY_MS)
                    if (shouldAlwaysOnMic()) {
                        wakeLog("retry skipped: always-on owns the mic")
                        return@launch
                    }
                    if (_uiState.value.interactionState == InteractionState.IDLE &&
                        _uiState.value.isMicEnabled &&
                        utteranceCapture == null &&
                        com.example.meowmba.core.audio.MicOwner
                            .acquire(com.example.meowmba.core.audio.MicOwner.IN_APP)
                    ) {
                        wakeLog( "re-armed on retry")
                        wakeWordEngine?.start()
                    } else {
                        android.util.Log.w(
                            TAG_WAKE,
                            "still not re-armed; mic held by " +
                                "${com.example.meowmba.core.audio.MicOwner.current}"
                        )
                    }
                }
            }
        } else {
            wakeLog( "not re-arming: mic disabled by the user")
        }
        _uiState.value = _uiState.value.copy(captionText = wakeHint())
    }

    /** True while the foreground app owns the always-on conversation mic. */
    private fun shouldAlwaysOnMic(): Boolean {
        val s = _uiState.value
        if (handedOffToBackground) return false
        if (s.isNoting && s.isMicEnabled) return true
        if (!VoicePolicy.shouldAlwaysOnMic(s, voiceHealth.platformHealthy)) return false
        // Nobody in frame and nobody spoke for a minute: drop to wake-word
        // (if armed) instead of holding SpeechRecognizer open. That's the
        // other big battery drain next to the camera.
        if (s.callMode) return true
        val quiet = System.currentTimeMillis() - lastUsableSpeechAtMs > MIC_NAP_MS
        if (quiet && !s.userInView && s.wakeWordEnabled) return false
        return true
    }

    /**
     * Chooses which listener owns the mic: always-on conversation while the
     * app is foreground, wake-phrase standby otherwise. Called on every mode
     * transition (mic toggle, mute, foreground return, end of a
     * turn) so the two can never disagree.
     */
    private fun updateVoiceListeningMode() {
        if (shouldAlwaysOnMic()) {
            // Foreground always-on owns the mic. Immediate start—a delayed arm
            // left the app deaf after cold launch / resume.
            stopWakeLoopAndRelease()
            startAlwaysOnListening()
            fuLog("voice mode -> ALWAYS_ON (re-arm requested)")
        } else {
            stopAlwaysOnListening()
            if (_uiState.value.wakeWordEnabled && !handedOffToBackground &&
                _uiState.value.interactionState == InteractionState.IDLE
            ) {
                startWakeStandby()
                fuLog("voice mode -> WAKE standby")
            } else {
                fuLog("voice mode -> mic OFF (alwaysOn=${shouldAlwaysOnMic()} wake=${_uiState.value.wakeWordEnabled} handedOff=$handedOffToBackground state=${_uiState.value.interactionState})")
            }
        }
    }

    /**
     * Always-on conversation: the recognizer stays open while the app is
     * foreground, session by session. While it waits it mirrors live partials into the caption; when a
     * turn is recognised it feeds [processUserSpeech] and re-arms once the reply
     * (and any follow-up) has finished. The wake phrase is off the whole time.
     *
     * Idle waiting uses one plain recognizer session. Once that session produces
     * text, the result is promoted into a stitched [UtteranceCapture]. This avoids
     * running a watchdog forever while idle, but does not mistake the recognizer's
     * aggressive acoustic endpoint for the end of a human turn.
     */
    /**
     * Continuous on-device ASR FIRST when the model pack is installed, AiAi as
     * fallback. Measured over days on this Fold: AiAi wedges at the service
     * level (steady BUSY(8) even across fresh rebinds until Google's own app
     * restarts), and every wedge cycle silently demoted always-on to wake
     * standby — the reported "always-on doesn't pick up my voice". The Sherpa
     * stream also has no inter-session gaps, which is what kept eating the
     * first syllable of every turn.
     */
    private fun preferredSpeechEngine(): SpeechEngine? {
        // An explicit pick on the Hardware → Voice Recognition page wins over
        // the health heuristics. It falls THROUGH rather than returning null
        // when the chosen backend is not usable right now (no key, model still
        // downloading): a deaf mic would be a worse answer than a working one
        // from another engine, and the page badges why the pick is unavailable.
        when (asrBackend) {
            // Whisper is a batch refiner, not a capture engine: it rides the
            // streaming zipformer for mic + partials + endpointing and
            // replaces the final text. So the engine here is sherpa.
            com.example.meowmba.core.audio.AsrBackend.WHISPER_LOCAL ->
                sherpaAsrEngine?.takeIf { it.isAvailable }?.let { return it }
            com.example.meowmba.core.audio.AsrBackend.META_GLASSES_MIC -> {
                // Glasses takeover: while connected, the glasses' mic is THE
                // mic. Not connected → fall through so a working native engine
                // answers instead of a deaf badge (same fall-through rule as
                // the other explicit picks).
                if (glassesAsrEngine == null) attachGlassesAsrEngine()
                glassesAsrEngine?.takeIf { glassesMicReady() }?.let { return it }
            }
        }
        // Fall through: the picked backend is not usable right now (model still
        // downloading, glasses not connected), so answer with sherpa rather
        // than going deaf. No health-driven platform hand-off any more — the
        // platform recognizer and the AUTO/health switch were removed with
        // those backends.
        return sherpaAsrEngine?.takeIf { it.isAvailable }
    }

    /**
     * Switch recognition backend and re-open the mic on the new engine.
     *
     * The running session must be torn down explicitly: the always-on loop
     * short-circuits when [alwaysOnCaptureEngine] already matches, so simply
     * flipping the field would leave the OLD engine holding AudioRecord and
     * the change would look like it did nothing until an app restart.
     */
    fun setAsrBackend(backend: com.example.meowmba.core.audio.AsrBackend) {
        if (backend == asrBackend) return
        asrBackend = backend
        secureStorage?.savePreference(
            com.example.meowmba.core.audio.AsrBackend.PREF_KEY,
            backend.id,
        )
        fuLog("voice recognition backend -> ${backend.id}")
        attachAsrEngine()
        attachWhisperRefiner()
        stopAlwaysOnListening()
        refreshAsrBackendState()
        if (shouldAlwaysOnMic()) startAlwaysOnListening()
    }

    /** Local Whisper refiner, built once the Whisper weights are on disk. */
    private var whisperEngine: com.example.meowmba.core.audio.WhisperAsrEngine? = null

    /**
     * The agent's browser: a WebView that is never shown, exposed as MCP tools.
     * Kept here so it can be torn down with the ViewModel rather than leaking a
     * WebView (and its renderer process) for the life of the app.
     */
    private var offscreenBrowser: com.example.meowmba.core.browser.OffscreenBrowser? = null
    private var mcpBrowser: com.example.meowmba.core.mcp.McpBrowserServer? = null

    /**
     * Wire Whisper as the final-transcript refiner — ONLY for the
     * [com.example.meowmba.core.audio.AsrBackend.WHISPER_LOCAL] pick.
     *
     * Live partials and endpointing stay on the streaming zipformer (Whisper
     * cannot stream); Whisper replaces the text that actually gets answered.
     * Every other backend keeps the text it produced: silently rewriting
     * another engine's output would be a surprise, and slower for no consent.
     */
    private fun attachWhisperRefiner() {
        val ctx = secureStorage?.context ?: return
        val engine = sherpaAsrEngine ?: return
        if (asrBackend != com.example.meowmba.core.audio.AsrBackend.WHISPER_LOCAL) {
            engine.whisperRefiner = null
            return
        }
        if (!com.example.meowmba.core.audio.VoiceModelStore(ctx).whisperReady()) {
            fuLog("whisper selected but weights not downloaded — refining disabled")
            engine.whisperRefiner = null
            return
        }
        val w = whisperEngine
            ?: com.example.meowmba.core.audio.WhisperAsrEngine(ctx).also { whisperEngine = it }
        engine.whisperRefiner = w
        fuLog("whisper refiner attached")
    }

    /** Recomputes the picker's availability badges from live device facts. */
    private fun refreshAsrBackendState() {
        val storage = secureStorage
        val store = storage?.context
            ?.let { com.example.meowmba.core.audio.VoiceModelStore(it) }
        val localReady = store?.asrReady() == true
        val whisperReady = store?.whisperReady() == true
        _uiState.value = _uiState.value.copy(
            asrBackend = asrBackend,
            asrBackendStatuses = com.example.meowmba.core.audio.AsrBackendStatus.evaluateAll(
                platformAvailable = speechService?.isAvailable == true,
                localModelReady = localReady,
                cloudKeyConfigured = !storage?.getApiKey("openrouter").isNullOrBlank(),
                glassesConnected = glassesMicReady(),
                whisperReady = whisperReady,
            ),
            asrCloudModel = storage?.getModelName(
                com.example.meowmba.core.audio.AsrBackend.OPENROUTER_ASR_PROVIDER,
            ) ?: com.example.meowmba.core.audio.AsrBackend.DEFAULT_GEMINI_MODEL,
        )
    }

    /** Glasses mic is usable right now: DAT-linked or an SCO device present. */
    private fun glassesMicReady(): Boolean =
        glassesController?.isConnected?.value == true ||
            glassesAsrEngine?.bluetoothHeadsetConnected() == true

    // ---- Meta glasses (Hardware page) ----

    /** Connect / start the glasses session (camera + link). Idempotent. */
    fun glassesConnect() {
        glassesController?.ensureCaptureStream()
            ?: run { _uiState.value = _uiState.value.copy(captionText = "No glasses controller in this build") }
    }

    fun glassesDisconnect() {
        glassesController?.disconnect()
    }

    /** One-time Meta AI registration + glasses camera permission (needs the Activity). */
    fun glassesSetup(activity: android.app.Activity) {
        val gc = glassesController ?: return
        viewModelScope.launch {
            val registered = gc.ensureRegistered(activity)
            if (registered) {
                gc.ensureCameraPermission(activity)
                glassesConnect()
            }
        }
    }

    /** Lazily-built sherpa engine capturing from the glasses' BT SCO mic. */
    private var glassesAsrEngine: com.example.meowmba.core.audio.SherpaAsrEngine? = null

    private fun attachGlassesAsrEngine() {
        if (glassesAsrEngine != null) return
        val ctx = secureStorage?.context ?: return
        if (!com.example.meowmba.core.audio.VoiceModelStore(ctx).asrReady()) return
        glassesAsrEngine = com.example.meowmba.core.audio.SherpaAsrEngine(
            ctx, viewModelScope, bluetoothSco = true,
        )
        fuLog("glasses mic ASR attached")
    }

    fun toggleVoiceModelScreen() {
        val showing = !_uiState.value.showVoiceModelScreen
        if (showing) refreshAsrBackendState()
        _uiState.value = _uiState.value.copy(showVoiceModelScreen = showing)
    }

    private fun alwaysOnSpeechEngine(): SpeechEngine? = preferredSpeechEngine()

    private fun startAlwaysOnListening() {
        viewModelScope.launch {
            stopWakeLoopAndAwait()
            startAlwaysOnListeningAfterKws()
        }
    }

    private fun startAlwaysOnListeningAfterKws() {
        if (sherpaAsrEngine == null) attachAsrEngine()
        val engine = alwaysOnSpeechEngine()
        if (engine == null) {
            requestAsrModel()
            if (_uiState.value.wakeWordEnabled) startWakeStandby(force = true)
            return
        }
        // A session already open on the SAME engine is exactly what we would
        // re-arm — nothing to do. (Identity via [alwaysOnCaptureEngine] so
        // non-singleton engines are handled too; see its doc.)
        if (alwaysOnActive && alwaysOnSessionOpen && alwaysOnCaptureEngine === engine) return
        if (engine === speechService) sherpaAsrEngine?.stop()
        else speechService?.stopListening()
        alwaysOnActive = true
        // NOTE: the mic-nap watch is armed by [armAlwaysOnCapture] once the
        // session actually opens. Arming it here instead re-enqueued the watch
        // every tick even when capture refuses (turn live) — an unbounded
        // arm→fire→re-arm cycle under the test virtual clock, and a pointless
        // relaunch storm on device during long turns.
        armAlwaysOnCapture(engine)
    }

    private fun requestAsrModel() {
        val ctx = secureStorage?.context ?: return
        val store = com.example.meowmba.core.audio.VoiceModelStore(ctx)
        if (store.asrReady()) {
            attachAsrEngine()
            if (shouldAlwaysOnMic()) startAlwaysOnListening()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                captionText = "Getting always-on listening…",
            )
            store.ensureAsr { _, label ->
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(captionText = label)
                }
            }
            attachAsrEngine()
            if (shouldAlwaysOnMic() &&
                _uiState.value.interactionState == InteractionState.IDLE
            ) {
                startAlwaysOnListening()
            }
        }
    }

    /**
     * Keeps the always-on partial mirror alive for as long as the always-on loop
     * is running. Started from [armAlwaysOnCapture] (NOT [startAlwaysOnListening])
     * because a stop/re-arm cycle cancels the job in [stopAlwaysOnListening] and
     * the loop continues through [armAlwaysOnCapture] — a collector started only
     * in [startAlwaysOnListening] would die on the first stop and the recovery
     * path would then miss every later partial ("heard speech but no reply").
     */
    /**
     * The always-on recognizer on AiAi never delivers a final result — sessions
     * end with an empty result or NO_MATCH, so the only copy of what the user
     * said lives in the last partial. When a quiet terminal arrives with a
     * partial banked, treat it as the turn: feed it through and let the turn's
     * reset re-arm the mic from a clean idle. Returns true when consumed.
     *
     * MUST be called before any re-arm, because [armAlwaysOnCapture] clears
     * [lastAlwaysOnPartial] when it opens the next session.
     */
    private fun recoverAlwaysOnPartial(): Boolean {
        if (!alwaysOnActive) return false
        // Partials accumulated while the companion herself was talking are her
        // own TTS, not the user — committing them self-answered in a loop.
        val st = _uiState.value.interactionState
        if (st == InteractionState.SPEAKING || st == InteractionState.THINKING) {
            fuLog("recoverAlwaysOnPartial skipped during $st (own voice): \"$lastAlwaysOnPartial\"")
            lastAlwaysOnPartial = ""
            return false
        }
        val utterance = lastAlwaysOnPartial
        if (!com.example.meowmba.core.audio.SpokenText.isUsable(utterance)) return false
        alwaysOnCommitJob?.cancel()
        lastAlwaysOnPartial = ""
        if (_uiState.value.interactionState == InteractionState.LISTENING) {
            _uiState.value = _uiState.value.copy(interactionState = InteractionState.IDLE)
        }
        fuLog("always-on recovering partial \"$utterance\"")
        processUserSpeech(utterance)
        return true
    }

    private fun ensureAlwaysOnPartialCollector(engine: com.example.meowmba.core.audio.SpeechEngine) {
        if (alwaysOnPartialJob != null) return
        fuLog("always-on collector STARTED on ${engine.javaClass.simpleName}")
        alwaysOnPartialJob = viewModelScope.launch {
            engine.partialResults.collect { partial ->
                if (partial.isNotBlank()) {
                    val usable = com.example.meowmba.core.audio.SpokenText.isUsable(partial)
                    val had = com.example.meowmba.core.audio.SpokenText.isUsable(lastAlwaysOnPartial)
                    // Keep the longest usable hypothesis. AiAi often rewrites a
                    // good sentence as a shorter wrong one, then we committed that.
                    if (usable && (!had || partial.trim().length >= lastAlwaysOnPartial.trim().length)) {
                        lastAlwaysOnPartial = partial
                    } else if (!had) {
                        lastAlwaysOnPartial = partial
                    }
                    fuLog("always-on collector partial=\"$partial\" usable=$usable")
                    if (usable) {
                        // AiAi keeps the session open ~8s after the last word.
                        // Commit the banked sentence once it stops growing.
                        alwaysOnCommitJob?.cancel()
                        alwaysOnCommitJob = viewModelScope.launch {
                            delay(ALWAYS_ON_COMMIT_MS)
                            if (alwaysOnActive &&
                                com.example.meowmba.core.audio.SpokenText.looksFinished(lastAlwaysOnPartial)
                            ) {
                                recoverAlwaysOnPartial()
                            }
                        }
                    }
                }
                val usableNow = com.example.meowmba.core.audio.SpokenText.isUsable(partial)
                val state = _uiState.value.interactionState
                if (alwaysOnActive && shouldAlwaysOnMic() && usableNow &&
                    (state == InteractionState.IDLE || state == InteractionState.LISTENING)
                ) {
                    _uiState.value = _uiState.value.copy(
                        interactionState = InteractionState.LISTENING,
                        captionText = captionWithSpeaker(partial),
                    )
                }
            }
        }
    }

    private fun armMicNapWatch() {
        micNapJob?.cancel()
        micNapJob = viewModelScope.launch {
            delay(MIC_NAP_MS)
            updateVoiceListeningMode()
        }
    }

    private fun stopAlwaysOnListening() {
        micNapJob?.cancel()
        micNapJob = null
        alwaysOnActive = false
        alwaysOnRearmJob?.cancel()
        alwaysOnRearmJob = null
        alwaysOnCommitJob?.cancel()
        alwaysOnCommitJob = null
        alwaysOnPartialJob?.cancel()
        alwaysOnPartialJob = null
        alwaysOnSessionOpen = false
        alwaysOnCaptureEngine = null
        speechService?.stopListening()
        sherpaAsrEngine?.stop()
    }

    /** Opens the always-on mic once the turn is idle (never on top of a live turn). */
    private fun armAlwaysOnCapture(
        engine: com.example.meowmba.core.audio.SpeechEngine,
        ignoreDebounce: Boolean = false,
    ) {
        if (!alwaysOnActive) return
        ensureAlwaysOnPartialCollector(engine)
        if (!shouldAlwaysOnMic()) {
            stopAlwaysOnListening()
            return
        }
        if (alwaysOnSessionOpen && alwaysOnCaptureEngine === engine) return
        val ttsBusy = (ttsManager as? com.example.meowmba.core.audio.TextToSpeechManager)?.isSpeaking() == true
        if (_uiState.value.interactionState != InteractionState.IDLE || utteranceCapture != null || ttsBusy) {
            // A turn is live, or SHE IS STILL SPEAKING: streamed replies drain
            // sentence by sentence and the queue outlives the SPEAKING state
            // flickers between chunks. No polling for the turn case (resetState()
            // re-arms us); for the TTS case schedule a retry so the mic only
            // opens once her voice is truly done.
            if (ttsBusy) {
                alwaysOnRearmJob?.cancel()
                alwaysOnRearmJob = viewModelScope.launch {
                    delay(800L)
                    if (alwaysOnActive && shouldAlwaysOnMic() &&
                        _uiState.value.interactionState == InteractionState.IDLE &&
                        !alwaysOnSessionOpen
                    ) {
                        armAlwaysOnCapture(engine, ignoreDebounce = true)
                    }
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        val since = now - lastAlwaysOnArmAtMs
        // A retry that already waited out the settle window must NOT consult
        // the window again: under the test virtual clock, tasks run far faster
        // than the real clock, so a re-debouncing retry recomputed `since`
        // forever and each attempt scheduled the next — an infinite chain that
        // never let advanceUntilIdle finish (and a pointless double wait on
        // device).
        if (!ignoreDebounce && since in 0 until 2_000L) {
            // AiAi still tearing down the previous slot — schedule, don't stack.
            alwaysOnRearmJob?.cancel()
            alwaysOnRearmJob = viewModelScope.launch {
                delay(2_000L - since)
                if (alwaysOnActive && shouldAlwaysOnMic() &&
                    _uiState.value.interactionState == InteractionState.IDLE &&
                    !alwaysOnSessionOpen
                ) {
                    armAlwaysOnCapture(engine, ignoreDebounce = true)
                }
            }
            return
        }
        lastAlwaysOnArmAtMs = now
        alwaysOnSessionOpen = true
        alwaysOnCaptureEngine = engine
        // Own-voice echo guard: a session that opens while she is speaking (or
        // within a beat of finishing) will mostly hear HER TTS through the
        // speaker. Measured on device: "Do you like cats" answered aloud came
        // back as the next user turn ("Meow, yes, I like.") and started a
        // self-conversation loop. Whatever this session hears is suspect.
        alwaysOnOpenedWhileSpeaking =
            _uiState.value.interactionState == InteractionState.SPEAKING ||
                _uiState.value.interactionState == InteractionState.THINKING ||
                System.currentTimeMillis() - lastSpokeFinishedAtMs < ECHO_GUARD_MS
        // The mic nap is armed HERE, not in [startAlwaysOnListeningAfterKws]:
        // only a genuinely open session should schedule the watch, otherwise a
        // live turn re-enqueues it forever (see the note there).
        armMicNapWatch()
        if (!com.example.meowmba.core.audio.SpokenText.isUsable(lastAlwaysOnPartial)) {
            lastAlwaysOnPartial = ""
        }
        // On-device ASR holds one AudioRecord and only callbacks on speech.
        // Silence for 20s is normal — a SpeechRecognizer-style watchdog would
        // kill the live mic.
        if (engine is SherpaAsrEngine) {
            alwaysOnSessionWatchdog?.cancel()
            engine.startListening(
                onResult = { text ->
                    alwaysOnSessionOpen = false
                    if (!alwaysOnActive || text.isBlank()) return@startListening
                    val st = _uiState.value.interactionState
                    if (st == InteractionState.SPEAKING || st == InteractionState.THINKING) {
                        // The continuous stream hears the companion's OWN TTS.
                        // Committing that made her answer herself in a loop
                        // ("what's up, how can I help you today" as a user
                        // turn). Only an explicit wake phrase may interrupt a
                        // live turn; everything else she says is dropped.
                        val isWake = matcher.match(text) != null
                        fuLog("always-on result during $st (wake=$isWake): \"$text\"")
                        if (!isWake) return@startListening
                    }
                    processUserSpeech(text)
                },
                onError = { code ->
                    alwaysOnSessionOpen = false
                    fuLog("always-on ASR error code=$code")
                    if (alwaysOnActive && shouldAlwaysOnMic() &&
                        _uiState.value.interactionState == InteractionState.IDLE
                    ) {
                        alwaysOnRearmJob?.cancel()
                        alwaysOnRearmJob = viewModelScope.launch {
                            delay(800L)
                            if (alwaysOnActive && shouldAlwaysOnMic()) armAlwaysOnCapture(engine)
                        }
                    }
                },
            )
            return
        }
        // The latch above is what stops us stacking sessions — but it is only
        // ever cleared by a recognizer callback, and a callback is not
        // guaranteed: if another consumer takes the single shared
        // SpeechRecognizer out from under us, neither onResult nor onError ever
        // fires and the latch stays true for the life of the process. Every
        // later arm then returns at the `if (alwaysOnSessionOpen) return` guard
        // and the always-on mic is dead until the app is restarted — the
        // reported "the always on mic stops working". A latch with no timeout
        // is a deadlock, so give it one.
        alwaysOnSessionWatchdog?.cancel()
        alwaysOnSessionWatchdog = viewModelScope.launch {
            delay(ALWAYS_ON_SESSION_TIMEOUT_MS)
            if (alwaysOnSessionOpen) {
                System.err.println(
                    "$TAG_WAKE: always-on session produced no callback in " +
                        "${ALWAYS_ON_SESSION_TIMEOUT_MS}ms — releasing the latch",
                )
                // Release the latch, then re-arm ONCE after tearing the
                // recognizer down — with the stale connection gone a fresh arm
                // lands clean (it can no longer be refused with BUSY forever).
                // The re-arm budget is bounded: a recognizer that keeps going
                // silent without ever delivering a terminal gets a couple of
                // recovery tries, then the loop stops waiting on it (a terminal
                // resets the budget; real sessions deliver one every ~16s).
                alwaysOnSessionOpen = false
                // A hang with words already in the last partial is the
                // reported "I said something and nothing happened". Recover
                // BEFORE rebind/re-arm — those wipe lastAlwaysOnPartial.
                if (recoverAlwaysOnPartial()) return@launch
                // Enforce the bounded re-arm budget the comment below promises.
                // Without this check a recognizer that never calls back re-arms
                // forever — each arm schedules another watchdog — which is an
                // infinite virtual-time loop in unit tests (advanceUntilIdle
                // never goes idle) and a battery-burning relaunch loop on
                // device. A terminal or success resets the counter.
                if (alwaysOnSilentRearms >= MAX_WATCHDOG_SILENT_REARMS) {
                    fuLog("always-on silent-session budget exhausted ($alwaysOnSilentRearms) — switching engines")
                    val before = preferredSpeechEngine()
                    voiceHealth.noteSilentSession()
                    (speechService as? com.example.meowmba.core.audio.SpeechService)
                        ?.rebindRecognizer()
                    attachAsrEngine()
                    val after = preferredSpeechEngine()
                    if (after != null && after !== before) {
                        alwaysOnSilentRearms = 0
                        // Kill the old engine's streams first — leaving the
                        // previous partial collector running produced two
                        // recognizers fighting (partials flapping every 100ms).
                        // NOTE: stop clears alwaysOnActive; start re-sets it,
                        // so only the policy gate decides here.
                        stopAlwaysOnListening()
                        if (shouldAlwaysOnMic() &&
                            _uiState.value.interactionState == InteractionState.IDLE
                        ) {
                            startAlwaysOnListening()
                        }
                    } else {
                        fuLog("always-on: no other engine — holding until the next terminal")
                    }
                    return@launch
                }
                alwaysOnSilentRearms++
                voiceHealth.noteSilentSession()
                (speechService as? com.example.meowmba.core.audio.SpeechService)
                    ?.rebindRecognizer()
                attachAsrEngine()
                fuLog("always-on silent session — VoiceHealth switched engines")
                stopAlwaysOnListening()
                if (shouldAlwaysOnMic() &&
                    _uiState.value.interactionState == InteractionState.IDLE
                ) {
                    startAlwaysOnListening()
                }
            }
        }
        engine.startListening(
            onResult = { text ->
                alwaysOnSessionWatchdog?.cancel()
                alwaysOnSessionOpen = false
                voiceHealth.noteSuccess()
                alwaysOnSilentRearms = 0
                val recentTts = System.currentTimeMillis() - lastSpokeFinishedAtMs < ECHO_GUARD_MS
                if ((alwaysOnOpenedWhileSpeaking || recentTts) && text.isNotBlank()) {
                    // This session was opened while (or right after) she spoke —
                    // the "speech" it heard is almost certainly her own TTS.
                    // Drop it instead of starting a self-conversation. A real
                    // barge-in re-arms fresh and is admitted on the next session.
                    fuLog("dropped own-voice echo (openedWhileSpeaking=$alwaysOnOpenedWhileSpeaking recentTts=$recentTts): \"$text\"")
                    alwaysOnOpenedWhileSpeaking = false
                    if (_uiState.value.interactionState == InteractionState.LISTENING) {
                        _uiState.value = _uiState.value.copy(interactionState = InteractionState.IDLE)
                    }
                    if (shouldAlwaysOnMic()) {
                        alwaysOnRearmJob?.cancel()
                        alwaysOnRearmJob = viewModelScope.launch {
                            delay(400L)
                            if (alwaysOnActive && shouldAlwaysOnMic() &&
                                _uiState.value.interactionState == InteractionState.IDLE &&
                                !alwaysOnSessionOpen
                            ) {
                                armAlwaysOnCapture(engine)
                            }
                        }
                    }
                    return@startListening
                }
                alwaysOnOpenedWhileSpeaking = false
                if (alwaysOnActive && text.isNotBlank()) {
                    // prog's turn-taking fix: a final result is not necessarily the
                    // whole turn — keep capturing through endpointing so pauses
                    // don't cut the user off; TranscriptSelector picks the best
                    // transcript. Health bookkeeping above stays from the merged
                    // v1.1.0 architecture.
                    _uiState.value = _uiState.value.copy(
                        interactionState = InteractionState.LISTENING,
                        captionText = captionWithSpeaker(text),
                        emotion = CompanionEmotion.ALERT,
                    )
                    captureUtterance(engine, initialText = text) { result ->
                        processUserSpeech(result)
                    }
                } else if (alwaysOnActive) {
                    // Empty/quiet result — the recognizer ended the session on
                    // silence. AiAi always ends the open mic this way, even when
                    // it transcribed the user's words into partials (results are
                    // never delivered on this device), so recover the turn from
                    // the banked partial FIRST — a re-arm would erase it.
                    if (recoverAlwaysOnPartial()) return@startListening
                    // Truly nothing said: a stray partial may have flipped us to
                    // LISTENING along the way; clear it or every future arm
                    // refuses at the `interactionState != IDLE` guard and the
                    // always-on loop dies silently. Then reopen the mic promptly.
                    if (_uiState.value.interactionState == InteractionState.LISTENING) {
                        _uiState.value = _uiState.value.copy(interactionState = InteractionState.IDLE)
                    }
                    if (shouldAlwaysOnMic()) {
                        alwaysOnRearmJob?.cancel()
                        alwaysOnRearmJob = viewModelScope.launch {
                            // A clean empty result means the session ended
                            // normally — the service is not tearing anything
                            // down, so reopen fast. The old 1.5s here was the
                            // "first syllable got cut" gap in always-on mode:
                            // speech starting right after a turn landed before
                            // the next session was live.
                            delay(400L)
                            if (alwaysOnActive && shouldAlwaysOnMic() &&
                                _uiState.value.interactionState == InteractionState.IDLE &&
                                !alwaysOnSessionOpen
                            ) {
                                armAlwaysOnCapture(engine)
                            }
                        }
                    }
                }
            },
            onError = { code ->
                alwaysOnSessionWatchdog?.cancel()
                alwaysOnSessionOpen = false
                // A terminal from the recognizer proves it is alive: the
                // watchdog's silent-recovery budget starts over.
                alwaysOnSilentRearms = 0
                // The open mic heard the user, but AiAi ended the session with
                // NO_MATCH and no final result (measured on this device: every
                // always-on session ends this way — results are never delivered).
                // The words only exist in the partial; feed them through or the
                // turn is lost and voice chat never answers.
                val quiet = code == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                    code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val heardSpeech = lastAlwaysOnPartial.isNotBlank()
                fuLog("always-on terminal code=$code quiet=$quiet heardSpeech=$heardSpeech lastPartial=\"$lastAlwaysOnPartial\"")
                if (alwaysOnActive && quiet && heardSpeech && recoverAlwaysOnPartial()) {
                    // The turn's reset re-arms the open mic from a clean idle.
                    return@startListening
                }
                // Nobody spoke (quiet-open timeout / no match) is the normal
                // idle state of always-on listening — put the face back to
                // idle and reopen the mic. Real recognizer hiccups retry with
                // a short backoff instead of leaving the face silently deaf.
                if (_uiState.value.interactionState == InteractionState.LISTENING) {
                    _uiState.value = _uiState.value.copy(interactionState = InteractionState.IDLE)
                }
                if (alwaysOnActive && shouldAlwaysOnMic() &&
                    _uiState.value.interactionState == InteractionState.IDLE
                ) {
                    val contention = VoicePolicy.isContentionError(code)
                    if (!contention) voiceHealth.noteSuccess()
                    if (contention && voiceHealth.noteContention()) {
                        fuLog("always-on wedged code=$code — Sherpa fallback")
                        attachAsrEngine()
                        stopAlwaysOnListening()
                        startAlwaysOnListening()
                        return@startListening
                    }
                    alwaysOnRearmJob?.cancel()
                    alwaysOnRearmJob = viewModelScope.launch {
                        // AiAi thrash-loops if we re-open too fast after 5/8/11,
                        // and a FIXED retry gap never escapes it: measured on
                        // device, a constant 2.5s retry produced alternating
                        // code=11/code=8 every few seconds indefinitely, because
                        // each attempt re-wedged the service we were waiting on.
                        // The retry has to get out of the way — double the gap
                        // per consecutive refusal, up to a minute.
                        delay(when {
                            // Quiet terminals are the NORMAL end of an open
                            // mic; a long wait here is the always-on
                            // "first syllable cut" gap. 500ms is enough settle
                            // for a session that ended by itself.
                            quiet -> 500L
                            contention -> contentionBackoffMs(voiceHealth.strikes)
                            else -> 1_800L
                        })
                        if (alwaysOnActive && shouldAlwaysOnMic() &&
                            _uiState.value.interactionState == InteractionState.IDLE &&
                            !alwaysOnSessionOpen
                        ) {
                            armAlwaysOnCapture(engine)
                        }
                    }
                }
            },
        )
    }

    /**
     * Toggles the voice listening state (push-to-talk / tap-to-toggle).
     * Tapping while the companion speaks interrupts it and starts a new turn.
     */
    fun onMicClick() {
        if (!_uiState.value.isMicEnabled) {
            // Privacy toggle is easy to hit by accident (ear tap) — say why nothing happens.
            _uiState.value = _uiState.value.copy(
                captionText = "Microphone is off — tap an ear or use the Privacy tile to re-enable"
            )
            return
        }

        when (_uiState.value.interactionState) {
            InteractionState.IDLE -> startListening()
            InteractionState.SPEAKING -> interrupt()
            else -> {
                // Tapping the mic while it is listening means "stop", so the stitched
                // capture must stop too — otherwise it just opens another session.
                utteranceCapture?.cancel()
                utteranceCapture = null
                alwaysOnSessionOpen = false
                speechService?.stopListening()
                resetState()
            }
        }
    }

    /**
     * One-shot listen for the app-drawer search bar. Fills the bar with what
     * was said and lets the drawer submit it — does not steal the turn as a
     * face command, and does not close the drawer.
     */
    fun startDrawerVoiceSearch() {
        if (!_uiState.value.isMicEnabled) {
            _uiState.value = _uiState.value.copy(
                captionText = "Microphone is off — tap an ear to re-enable",
            )
            return
        }
        if (_uiState.value.interactionState == InteractionState.LISTENING) {
            utteranceCapture?.cancel()
            utteranceCapture = null
            speechService?.stopListening()
            sherpaAsrEngine?.stop()
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.IDLE,
                captionText = null,
            )
            return
        }
        val engine = preferredSpeechEngine()
        if (engine == null || !engine.isAvailable) {
            _uiState.value = _uiState.value.copy(
                captionText = "Speech recognition isn't available",
            )
            return
        }
        stopAlwaysOnListening()
        stopWakeLoopAndRelease()
        transientListenRetries = 0
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.LISTENING,
            captionText = null,
            drawerVoiceCommit = null,
        )
        captureUtterance(engine) { result ->
            val text = result.trim()
            if (text.isBlank() || isStopCommand(text)) {
                _uiState.value = _uiState.value.copy(
                    interactionState = InteractionState.IDLE,
                    captionText = null,
                )
                return@captureUtterance
            }
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.IDLE,
                captionText = null,
                drawerVoiceCommit = text,
            )
        }
    }

    fun consumeDrawerVoice() {
        if (_uiState.value.drawerVoiceCommit != null) {
            _uiState.value = _uiState.value.copy(drawerVoiceCommit = null)
        }
    }

    private fun startListening(prompt: String = "Listening...") {
        transientListenRetries = 0
        followUpJob?.cancel()
        isFollowUpListen = false
        // A focused, push-to-talk turn takes priority over the always-on
        // listener: stop the open-mic capture and its partial mirror so only
        // this capture drives the caption for the duration of the turn.
        stopAlwaysOnListening()

        val engine = preferredSpeechEngine()
        if (engine == null) {
            stopWakeLoopAndRelease()
            if (speechService != null) {
                // A recognizer IS configured but none of them are usable right
                // now (not installed / wedged). Silently falling into mock mode
                // from here faked an entire conversational turn — the one thing
                // the honesty contract forbids. Say so instead.
                _uiState.value = _uiState.value.copy(
                    captionText = "Speech recognition isn't available on this device — no recognizer service installed"
                )
                return
            }
            startMockListening()
            return
        }
        if (!engine.isAvailable) {
            stopWakeLoopAndRelease()
            _uiState.value = _uiState.value.copy(
                captionText = "Speech recognition isn't available on this device — no recognizer service installed"
            )
            return
        }

        audioFocusManager?.requestFocus()
        // No "Listening…" prompt: captions appear only for live speech (partials)
        // or the reply, never for a waiting state.
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.LISTENING,
            captionText = null,
            emotion = CompanionEmotion.ALERT
        )

        viewModelScope.launch {
            if (wakeWordEngine is SherpaKwsEngine) {
                stopWakeLoopAndAwait()
                delay(KWS_HANDOFF_MS)
            } else {
                stopWakeLoopAndRelease()
            }
            if (_uiState.value.interactionState != InteractionState.LISTENING) return@launch
            captureUtterance(engine) { result -> processUserSpeech(result) }
        }
    }

    /**
     * Captures one spoken turn, tolerating pauses.
     *
     * Everything that listens for a *command* goes through here rather than calling the
     * engine directly, because a raw session ends after about a second of quiet and cuts
     * people off mid-sentence. [UtteranceCapture] stitches sessions until the user has
     * actually stopped. The live transcript is mirrored into the caption so it is obvious
     * the companion is still hearing you during a pause.
     */
    private fun captureUtterance(
        engine: com.example.meowmba.core.audio.SpeechEngine,
        initialText: String = "",
        onError: (Int) -> Unit = { code -> onListeningError(code) },
        onFinal: (String) -> Unit,
    ) {
        utteranceCapture?.cancel()
        fuLog("captureUtterance start on ${engine.javaClass.simpleName} initial=\"${initialText.take(40)}\"")
        val capture = com.example.meowmba.core.audio.UtteranceCapture(
            engine = engine,
            scope = viewModelScope,
            initialText = initialText,
            endpointDelayFor = com.example.meowmba.core.audio.VoiceEndpointing::silenceAfter,
            // "Stop" / "never mind" must land the moment it is heard.
            finishEarlyWhen = { text -> isStopCommand(text) },
        )
        utteranceCapture = capture
        viewModelScope.launch {
            capture.live.collect { text ->
                if (text.isNotBlank() &&
                    _uiState.value.interactionState == InteractionState.LISTENING
                ) {
                    _uiState.value = _uiState.value.copy(captionText = text)
                }
            }
        }
        capture.start(
            onFinal = { text ->
                utteranceCapture = null
                transientListenRetries = 0
                onFinal(text)
            },
            onError = { code ->
                utteranceCapture = null
                onError(code)
            },
        )
    }

    /**
     * Recognizer errors: "nothing heard" codes (no-match, speech timeout) are
     * a normal quiet end-of-turn; infrastructure failures (permission, busy,
     * no service) are surfaced honestly instead of looking like a dead button.
     */
    private fun onListeningError(code: Int) {
        // Errors that must not end an active follow-up: silence (6/7, the user has not
        // spoken yet) and session-conflict (ERROR_CLIENT 5 / ERROR_RECOGNIZER_BUSY 8,
        // the barge-in watch or a wake re-arm raced the capture). Hard-resetting on any
        // of these is exactly what re-armed the wake spotter and killed the follow-up,
        // so re-open the capture instead. Bounded so a dead window still ends via the
        // follow-up timeout.
        fuLog("onListeningError code=$code followUp=$isFollowUpListen state=${_uiState.value.interactionState}")
        if (isFollowUpListen && isFollowUpKeepAliveError(code) &&
            transientListenRetries < MAX_FOLLOW_UP_SILENCE_RETRIES &&
            _uiState.value.interactionState == InteractionState.LISTENING
        ) {
            transientListenRetries++
            viewModelScope.launch {
                delay(250)
                if (_uiState.value.interactionState != InteractionState.LISTENING || !isFollowUpListen) return@launch
                val engine = preferredSpeechEngine() ?: return@launch
                captureUtterance(engine, onError = { c -> onListeningError(c) }) { result ->
                    processUserSpeech(result)
                }
            }
            return
        }
        // Error 11 (ERROR_SERVER_DISCONNECTED) and friends are transient hiccups
        // of the recognition service, not a broken mic. Instead of surfacing a
        // raw "error 11" caption and going deaf, re-open the session silently a
        // couple of times; only give up (and say why) if it keeps happening.
        if (VoicePolicy.isContentionError(code)) {
            voiceHealth.noteContention()
            val asr = sherpaAsrEngine
            if (asr != null && asr.isAvailable) {
                fuLog("mic tap contention code=$code — Sherpa fallback")
                utteranceCapture?.cancel()
                utteranceCapture = null
                speechService?.stopListening()
                captureUtterance(asr) { result -> processUserSpeech(result) }
                return
            }
        }
        val retryable = isTransientSpeechError(code) ||
            code == SpeechRecognizer.ERROR_CLIENT ||
            code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        if (retryable &&
            transientListenRetries < MAX_TRANSIENT_LISTEN_RETRIES &&
            _uiState.value.interactionState == InteractionState.LISTENING
        ) {
            transientListenRetries++
            viewModelScope.launch {
                delay(600)
                if (_uiState.value.interactionState != InteractionState.LISTENING) return@launch
                val engine = speechService ?: return@launch
                captureUtterance(engine) { result -> processUserSpeech(result) }
            }
            return
        }
        transientListenRetries = 0
        resetState()
        speechErrorCaption(code)?.let { msg ->
            _uiState.value = _uiState.value.copy(captionText = msg)
        }
    }

    private fun speechErrorCaption(code: Int): String? = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is off — enable it in system Settings"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognizer is busy — try again in a moment"
        SpeechRecognizer.ERROR_CLIENT ->
            null
        SpeechRecognizer.ERROR_AUDIO ->
            "Audio recording error — is another app holding the mic?"
        else -> "Speech recognition error ($code)"
    }

    private fun startMockListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.LISTENING,
                captionText = null,
                emotion = CompanionEmotion.ALERT
            )
            delay(3000)
            processUserSpeech("Hey Meow, how are you?")
        }
    }

    private fun processUserSpeech(raw: String) {
        val text = com.example.meowmba.core.audio.SpokenText.polish(raw)
        if (!com.example.meowmba.core.audio.SpokenText.isUsable(text)) {
            fuLog("processUserSpeech dropped junk: \"$text\"")
            return
        }
        // Transport-level idempotence (prog): overlapping callbacks and
        // contraction-spelling variants of the same utterance admit once.
        if (!turnDeduplicator.accept(text)) {
            fuLog("processUserSpeech dropped duplicate turn: \"$text\"")
            return
        }
        lastUsableSpeechAtMs = System.currentTimeMillis()
        // Own-voice echo through the fresh follow-up mic: the tail of her
        // reply (room reverb, buffered audio) lands as a "user" turn seconds
        // after she finishes — measured on device 2.7s after onSpeechFinished.
        val sinceSpoke = System.currentTimeMillis() - lastSpokeFinishedAtMs
        if (sinceSpoke in 0..4_000L) {
            lastAssistantText?.let { last ->
                val a = com.example.meowmba.core.audio.SpokenText.normalize(last)
                if (a.isNotBlank() && (a.contains(text) || text.contains(a))) {
                    fuLog("processUserSpeech dropped own-voice echo: \"$text\"")
                    return
                }
            }
        }
        armMicNapWatch()
        fuLog("processUserSpeech: \"$text\"")
        NoteSessionParser.parse(WakePhraseMatcher.normalize(text))?.let {
            handleNoteIntent(it)
            return
        }
        if (_uiState.value.isNoting) {
            appendNoteLine(text)
            if (_uiState.value.isMuted) {
                _uiState.value = _uiState.value.copy(
                    interactionState = InteractionState.IDLE,
                    captionText = text,
                )
                updateVoiceListeningMode()
                return
            }
        }
        if (_uiState.value.callMode && isHangupCommand(text)) {
            endCallMode()
            speak("Call ended.")
            return
        }
        if (_uiState.value.callMode) {
            val intent = AgentRouter.route(text)
            if (intent != null && intent.agentId == "phone") {
                if (intent.toolId == "phone.launch_app") {
                    if (tryLaunchAppImmediately(text)) return
                }
                submitAgentIntent(intent)
                return
            }
            respondDirectly(text)
            return
        }
        val awaiting = awaitingNameFaceId
        if (awaiting != null && System.currentTimeMillis() <= awaitingNameUntilMs) {
            if (NewPersonAsk.declined(text)) {
                awaitingNameFaceId = null
                speak("No worries.")
                return
            }
            EnrollPhraseParser.parseAskedReply(text)?.let { name ->
                awaitingNameFaceId = null
                handleSpeakerEnroll(name, awaiting)
                viewModelScope.launch { persistTurn(MessageRole.USER, text, name) }
                return
            }
        }
        EnrollPhraseParser.parse(text)?.let { name ->
            handleSpeakerEnroll(name)
            viewModelScope.launch { persistTurn(MessageRole.USER, text, name) }
            return
        }
        identifyFromAudio()?.let { bindSpeaker(it) }
            ?: speakerLabelFor(visionManager?.faceTrack?.value)?.let { lastSpeakerName = it.lowercase() }
        val greetNorm = WakePhraseMatcher.normalize(text)
        if (com.example.meowmba.core.agent.runtime.GreetingAnswers.isGreeting(greetNorm)) {
            val reply = com.example.meowmba.core.agent.runtime.GreetingAnswers.reply(lastSpeakerName)
            followUpJob?.cancel()
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.SPEAKING,
                captionText = reply,
                emotion = CompanionEmotion.HAPPY,
            )
            viewModelScope.launch { persistTurn(MessageRole.USER, text) }
            speak(reply)
            return
        }
        // App open must beat THINKING / memory / TTS. "Paws is on it" was
        // holding the launcher until the sentence finished.
        com.example.meowmba.core.work.AssistantRelay.parse(text)?.let { req ->
            relayToAssistant(req)
            return
        }
        if (tryLaunchAppImmediately(text)) return
        if (tryCleanupImmediately(text)) return
        // "Show it in the browser": pull the newest artifact into the panel.
        // Checked before the agent routing so it also works while the browser
        // is already open.
        if (looksLikeShowInBrowser(WakePhraseMatcher.normalize(text))) {
            showLatestArtifactInBrowser()
            return
        }
        // Phone-control engage: "start listening" / "control my phone" arms a
        // state where the NEXT utterance becomes the phone-control goal, so the
        // user can chain "read the screen", "write an email", "fill this form"...
        if (PHONE_CONTROL_ENGAGE.any { WakePhraseMatcher.normalize(text) == it }) {
            if (!com.example.meowmba.core.access.MeowAccessibilityService.isEnabled()) {
                speak("Phone control needs the accessibility service. I'm opening Settings — enable Meow Companion there, then say start listening again.")
                openAccessibilitySettings()
                return
            }
            _uiState.value = _uiState.value.copy(phoneControlAwaitingGoal = true)
            speak("I'm ready. Tell me what to do on the phone — read the screen, look things up, fill forms, or send messages.")
            return
        }
        if (_uiState.value.phoneControlAwaitingGoal) {
            val stopWords = setOf(
                "stop", "cancel", "never mind", "nevermind", "quit", "abort",
                "thank you", "thanks", "goodbye", "bye", "that's all", "thats all"
            )
            _uiState.value = _uiState.value.copy(phoneControlAwaitingGoal = false)
            if (stopWords.any { text.lowercase().trim().startsWith(it) }) {
                speak("Phone control stopped.")
                return
            }
            phoneAgentLoop?.setGoal(text)
            return
        }
        // Phone-control loop: if active, "stop"/"cancel" kills it; else new speech updates the goal.
        if (phoneAgentLoop?.isActive == true) {
            val stopWords = setOf("stop", "cancel", "never mind", "nevermind", "quit", "abort")
            if (stopWords.any { text.lowercase().trim().startsWith(it) }) {
                phoneAgentLoop?.cancel()
                speak("Phone control stopped.")
                return
            }
            // New speech while loop is active = new goal
            phoneAgentLoop?.setGoal(text)
            return
        }

        // "search X in/on <app>": deep-link straight into the app's search
        // screen. Must run before the browser-search block below, which would
        // otherwise grab "search cats on youtube" as a web query and open the
        // browser panel instead of the app.
        AgentRouter.routeDeepLink(WakePhraseMatcher.normalize(text))?.let { target ->
            deepLinkSearch(target)
            return
        }
        // Explicit searches ("search for...", "look up...", "google...") open
        // live in the browser panel — the user sees results, not a queued task
        // narrating "searching...". Checked before the browser-agent routing so
        // it also works while the panel is already open. Memory/notes searches
        // stay with the agent.
        extractSearchQuery(WakePhraseMatcher.normalize(text))?.let { query ->
            showSearchInBrowser(query)
            return
        }
        com.example.meowmba.core.work.BrowserRequestParser.parse(text)?.let { req ->
            driveAgentBrowser(req)
            return
        }
        // Named GPS locations: "remember this as my mailbox" saves the current
        // fix, "where is my mailbox" recalls it. Runs BEFORE the direct-question
        // and generic remember-handling so "where is my X" never leaks to the
        // chat brain, and the place form is never stored as a fact.
        val parsedLocation = parseNamedLocation(text)
        when (val locationCmd = parsedLocation) {
            is LocationCommand.Save -> {
                saveNamedLocation(locationCmd.name)
                return
            }
            is LocationCommand.Recall -> {
                recallNamedLocation(locationCmd.name)
                return
            }
            null -> {}
        }
        // Device specialists before the chat brain. "What do you see" and
        // "how fast am I going" used to hit respondDirectly; the model then
        // invented "I don't have camera/GPS access".
        val normalizedTurn = WakePhraseMatcher.normalize(text)
        com.example.meowmba.core.vision.VisionRouter.parse(text)?.let { query ->
            lookAround(query)
            return
        }
        if (SpeechIntents.isSpeedQuestion(normalizedTurn)) {
            answerHowFast()
            return
        }
        // Informational questions get answered directly by the brain — never
        // queued as agent tasks, never met with another question.
        if (looksLikeDirectQuestion(normalizedTurn)) {
            respondDirectly(text)
            return
        }
        // This branch used to send EVERY utterance to the browser agent while
        // the in-app panel was open ("make me a page about..."). That panel is
        // gone — pages now open in a real browser — so there is no standing
        // browser goal left to feed, and speech falls through to the normal
        // agent routing (which is what made "act like a dog" stop performing).
        // G1: user speech = curiosity; praise gets a proud wag.
        val lower = text.lowercase()
        val praise = listOf("good ", "nice ", "great ", "thank", "well done", "love you", "awesome")
        noteMoodEvent(
            if (praise.any { lower.contains(it) }) com.example.meowmba.core.personality.MoodEvent.Praise
            else com.example.meowmba.core.personality.MoodEvent.UserSpoke
        )
        if (_uiState.value.conversationMode && isStopCommand(text)) {
            endConversation()
            return
        }

        // Cancel follow-up timeout — user spoke, we're in a new turn
        followUpJob?.cancel()

        // A new turn clears the previous turn's recall chips
        _uiState.value = _uiState.value.copy(recalledMemories = emptyList())

        // Explicit "remember …" commands are handled before anything else
        // (checked before updating lastUserText so "remember this" can refer
        // back to the previous turn)
        val rememberCommand = MemoryCommandParser.parse(text)
        if (rememberCommand != null) {
            handleRememberCommand(text, rememberCommand)
            return
        }

        lastUserText = text

        val normalizedNow = com.example.meowmba.core.audio.WakePhraseMatcher.normalize(text)
        com.example.meowmba.core.work.ProjectRequestParser.parse(normalizedNow)?.let { req ->
            startProjectFromVoice(req, text)
            return
        }
        com.example.meowmba.core.work.ToolRequestParser.parse(normalizedNow)?.let { req ->
            makeToolFromVoice(req, text)
            return
        }

        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.THINKING,
            captionText = text,
            emotion = CompanionEmotion.CURIOUS
        )

        // A new turn supersedes the old one. Do not cancel() the previous
        // Ktor job — that crashes this device. Epoch makes the old stream
        // a no-op; TTS is stopped immediately.
        val epoch = supersedeResponseTurn()
        // THINKING is the state every mic re-arm path refuses to run in, and
        // nothing else guarantees we leave it: a provider that never emits a
        // terminal token, a TTS engine that swallows the utterance, or a
        // cancelled job all stranded the companion there permanently — the
        // reported "it works for a bit, then the wake word stops". This watchdog
        // is the backstop that always returns the machine to IDLE.
        thinkingWatchdogJob?.cancel()
        thinkingWatchdogJob = viewModelScope.launch {
            delay(THINKING_TIMEOUT_MS)
            if (_uiState.value.interactionState == InteractionState.THINKING) {
                // System.err, not android.util.Log: this line runs under the
                // JVM unit tests, where the Log stubs throw.
                System.err.println(
                    "MeowState: THINKING exceeded ${THINKING_TIMEOUT_MS}ms — recovering to IDLE",
                )
                supersedeResponseTurn()
                _uiState.value = _uiState.value.copy(
                    interactionState = InteractionState.IDLE,
                    captionText = "That took too long — I gave up on it. Ask me again?",
                )
                // Order matters: the state must already read IDLE, because
                // startWakeStandby refuses to re-arm from anything else.
                startWakeStandby()
            }
        }

        responseJob = viewModelScope.launch(swallowAsyncFatals) {
            // Save user message to memory
            val userMessageId = persistTurn(MessageRole.USER, text)

            // Face-presence: "can you see me" uses the live face tracker first.
            if (com.example.meowmba.core.vision.VisionRouter.isSeeMeQuery(text)) {
                answerSeeMe()
                return@launch
            }

            // Vision fast path: "what do you see" is immediate companion
            // behavior (capture + describe), never an agent tool call.
            com.example.meowmba.core.vision.VisionRouter.parse(text)?.let { query ->
                lookAround(query)
                return@launch
            }

            // Weather is answered directly, ahead of agent routing. It is a lookup with an
            // obvious answer, so it should not become a queued agent task announced with
            // "I'll ask if a step needs your approval".
            if (AgentRouter.isWeatherQuestion(
                    com.example.meowmba.core.audio.WakePhraseMatcher.normalize(text)
                )
            ) {
                android.util.Log.i("Weather", "routed as a weather question: \"$text\"")
                answerWeather()
                return@launch
            }

            // Time and date are answered from the device clock, never from the model —
            // the chat model hallucinated the time because it had no clock. Same reason
            // as weather: an instant lookup with an obvious answer, no agent task.
            val normalizedTurn = com.example.meowmba.core.audio.WakePhraseMatcher.normalize(text)
            if (com.example.meowmba.core.personality.IdleChatter.isFactRequest(normalizedTurn)) {
                speak(moodEngine.pickFact(System.currentTimeMillis(), kotlin.random.Random.nextInt()))
                return@launch
            }
            if (ClockAnswers.isClockQuestion(normalizedTurn)) {
                android.util.Log.i("Clock", "routed as a clock question: \"$text\"")
                speak(
                    ClockAnswers.answer(
                        normalizedTurn,
                        System.currentTimeMillis(),
                        java.util.TimeZone.getDefault()
                    )
                )
                return@launch
            }

            // "What model are you using" is a fact the app holds. Answered here so it
            // never reaches a model that has to guess at its own identity — which is how
            // it ended up promising to "look at your device" and never coming back.
            com.example.meowmba.core.agent.SelfStatusParser.parse(text)?.let { question ->
                speak(describeSelf(question))
                return@launch
            }

            // Inventory, answered directly for the same reason as the weather: "do I have
            // a spare SM58" is a lookup, not a background task to be queued and announced.
            // Anything this parser does not recognise still reaches the tool router, which
            // has the full inventory tool set.
            com.example.meowmba.core.inventory.InventoryRequestParser.parse(text)?.let { req ->
                answerInventory(req)
                return@launch
            }

            // Multi-step phone goals: verbs that clearly need the autonomous
            // loop. The on-device LLM router often misses these, so we catch
            // them deterministically before falling into the model-based router.
            if (isMultiStepPhoneGoal(text)) {
                phoneAgentLoop?.setGoal(text)
                return@launch
            }

            // Agent routing: keyword fast path, then the on-device model brain.
            // The model only proposes — approval gates still decide.
            val agentIntent = routeAgentIntent(text)
            if (agentIntent != null) {
                // Multi-step phone control: if the LLM (not keyword) routed to phone_control,
                // start the autonomous loop instead of single-shot execution.
                if (agentIntent.agentId == "phone_control" && AgentRouter.route(text) == null) {
                    phoneAgentLoop?.setGoal(text)
                    return@launch
                }
                submitAgentIntent(agentIntent)
                return@launch
            }

            // Recall relevant memories (embedding similarity, importance-weighted)
            val recalled = recallMemories(text)
            if (recalled.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    recalledMemories = recalled.map {
                        RecalledMemory(it.memory.content.take(80), it.reason)
                    }
                )
                memoryRepository?.touchRecalled(recalled.map { it.memory.id })
            }

            if (currentProvider == null) {
                delay(1500)
                speak("No model is ready yet. Download one from the Hardware page, or configure a server in Settings.")
                return@launch
            }

            // Context-augmented prompt: persona + recalled memories + recent turns
            val recentMessages = memoryRepository?.getRecentMessages(10, conversationId()) ?: emptyList()
            // Hand the chat model the same tool list the router sees, so it stops
            // describing itself as text-only when it has a full registry behind it.
            val capabilities = agentToolRegistry?.definitions()
                ?.filter { it.id != "robot.move" || robotManager.hasLiveBody }
                ?.map { "${it.displayName} — ${it.description.substringBefore('.')}" }
                ?: emptyList()
            val prompt = PromptBuilder.build(
                _uiState.value.name, text, recentMessages, recalled, capabilities,
                learned = learnedBlock(),
                speakerName = lastSpeakerName,
                deviceFacts = liveDeviceFacts(),
            )

            var fullResponse = ""
            // Index up to which fullResponse has already been handed to TTS. Speaking
            // sentence-by-sentence as they land is what removes the dead-air wait: the
            // old code only called speak() on Complete, so the user heard nothing until
            // the entire answer had been generated.
            var spokenUpTo = 0
            // Route on what the user said, not on the assembled prompt around it.
            omniRoute?.hintUserText(text)
            val provider = awaitProvider()
            fuLog("brain stream start provider=${provider?.name}")
            val stream = if (provider is com.example.meowmba.core.agent.OmniRoute) {
                provider.generateStreaming(prompt, preferRouteId = preferredChatRouteId())
            } else {
                provider?.generateStreaming(prompt)
            }
            stream?.collect { response ->
                if (epoch != responseEpoch) return@collect
                when (response) {
                    is ModelResponse.Partial -> {
                        // On-device models can emit literal \n \t etc; unescape so SpeechChunker
                        // splits on real newlines and TTS speaks natural pauses.
                        val partial = response.text
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\r", "\r")
                        fullResponse += partial
                        _uiState.value = _uiState.value.copy(captionText = fullResponse)
                        val chunk = com.example.meowmba.core.audio.SpeechChunker
                            .nextChunk(fullResponse, spokenUpTo)
                        if (chunk != null) {
                            spokenUpTo += chunk.length
                            speakStreaming(chunk.trim(), first = spokenUpTo == chunk.length)
                        }
                    }
                    is ModelResponse.Complete -> {
                        if (fullResponse.isBlank()) {
                            showProviderError("${_uiState.value.selectedModel} returned an empty response")
                        } else {
                            // Whatever is left after the last sentence boundary.
                            val tail = fullResponse.substring(spokenUpTo).trim()
                            if (tail.isNotEmpty()) {
                                speakStreaming(tail, first = spokenUpTo == 0)
                            }
                            recordSpokenResponse(fullResponse)
                            // Episodic checkpoint: resume exactly where we left off
                            if (!_uiState.value.memoryPaused) {
                                memoryRepository?.saveEpisodicCheckpoint(text, fullResponse, userMessageId)
                                pushMemoriesToMem0()
                            }
                        }
                    }
                    is ModelResponse.Error -> {
                        showProviderError(response.message)
                    }
                }
            }
        }
    }

    /**
     * Agent routing order:
     *  1. Keyword fast path (free, deterministic).
     *  2. Hermes Agent endpoint, when configured (OpenAI-compatible
     *     function-calling — hermes-agent v0.19+ in the Memba stack).
     *  3. On-device brain: the active model renders a single-JSON decision
     *     (fully local when a local model is selected).
     * Whatever proposes, approval gates still decide.
     */
    private suspend fun routeAgentIntent(text: String): AgentRouter.AgentIntent? {
        val kwIntent = AgentRouter.route(text)
        kwIntent?.let { return it }
        if (!_uiState.value.agentBrainEnabled) return null
        val registry = agentToolRegistry ?: return null

        hermesBrain?.route(text, registry)?.let { return it }
        // Hermes declined/unreachable, or not configured: on-device brain next

        val provider = currentProvider ?: return null
        return LlmToolCallRouter(
            provider, registry,
            dispatcher = ioDispatcher,
            promptMode = toolCallPromptMode()
        ).route(text, context = followUpContextFor(text))
    }

    /**
     * The brain routes one utterance with no memory of the conversation — fine for
     * full sentences, broken for follow-ups: measured on device, "yes" (to "want me
     * to try a custom animation?") invented a random 60ms clip. Short utterances get
     * the last exchange attached so the router can see what "yes" agreed to.
     */
    private fun followUpContextFor(text: String): String? {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        if (words > 4) return null
        val recent = _uiState.value.chatMessages.takeLast(2)
        if (recent.isEmpty()) return null
        return recent.joinToString("\n") {
            (if (it.isCompanion) "Meow" else "User") + ": " + it.text.take(200)
        }
    }

    /**
     * Prompt dialect auto-detection: a Hermes fine-tune on-device (e.g.
     * Hermes-3-Llama-3.2-3B) gets its own trained function-calling template;
     * everything else gets the generic JSON contract.
     */
    internal fun toolCallPromptMode(): ToolCallPromptMode {
        val s = _uiState.value
        val haystack = "${s.selectedModel} ${s.loadedModels.localPath}"
        return if (haystack.contains("hermes", ignoreCase = true)) {
            ToolCallPromptMode.HERMES_NATIVE
        } else {
            ToolCallPromptMode.CONTRACT
        }
    }

    /** Submit a recognized agent intent to the orchestrator and say what's happening. */
    /**
     * Fetches the weather, says it, and lets the card appear.
     *
     * The card is not set here: WeatherRepository.latest drives it, and the ViewModel already
     * observes that. One source of truth means the card is identical whether the answer came
     * from here or from the agent calling weather.get.
     */
    private suspend fun answerWeather() {
        val repo = weatherRepository ?: run {
            speak("I can't check the weather in this build.")
            return
        }
        when (val r = repo.fetch()) {
            is com.example.meowmba.core.weather.WeatherRepository.Result.Ok ->
                speak(r.weather.spoken())
            is com.example.meowmba.core.weather.WeatherRepository.Result.Failed ->
                speak(r.reason)
        }
    }

    /**
     * Start the app on this turn, before THINKING or TTS. Returns true when
     * this utterance was an app launch (success or a spoken miss).
     */
    private fun relayToAssistant(req: com.example.meowmba.core.work.AssistantRelay) {
        val ctx = secureStorage?.context
        if (ctx == null) {
            speak("I heard that, but I can't open ${req.target.spoken} from here.")
            return
        }
        val launched = runCatching {
            val search = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                setPackage(req.target.packageName)
                putExtra(android.app.SearchManager.QUERY, req.query)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (search.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(search)
                return@runCatching true
            }
            val launch = ctx.packageManager.getLaunchIntentForPackage(req.target.packageName)
            if (launch != null) {
                launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                launch.putExtra(android.app.SearchManager.QUERY, req.query)
                ctx.startActivity(launch)
                return@runCatching true
            }
            val web = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(req.target.webUrl(req.query)),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(web)
            true
        }.getOrDefault(false)
        if (launched) {
            speak("Sending that to ${req.target.spoken}.")
        } else {
            speak("${req.target.spoken} isn't installed, and I couldn't open it in the browser.")
        }
        updateVoiceListeningMode()
    }

    private fun tryCleanupImmediately(text: String): Boolean {
        val pending = PhoneCleanup.pendingFresh()
        val intent = if (pending != null && CleanupSpeech.isConfirm(text)) {
            AgentRouter.AgentIntent(
                "phone", "phone.cleanup",
                mapOf("kind" to pending.kind, "action" to "tidy"),
                "clean ${pending.kind}",
            )
        } else {
            val parsed = CleanupSpeech.parse(text) ?: return false
            AgentRouter.AgentIntent(
                "phone", "phone.cleanup",
                mapOf("kind" to parsed.kind, "action" to parsed.action),
                parsed.title,
            )
        }
        val tool = agentToolRegistry?.byId("phone.cleanup") ?: return false
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.THINKING,
                captionText = "Looking through your phone…",
                emotion = CompanionEmotion.CURIOUS,
            )
            when (val result = tool.execute(intent.args)) {
                is com.example.meowmba.core.agent.tools.ToolResult.Success -> speak(result.output)
                is com.example.meowmba.core.agent.tools.ToolResult.Failure -> speak(result.error)
            }
        }
        return true
    }

    fun onMediaPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(mediaPermissionRequested = false)
        if (granted) {
            val tool = agentToolRegistry?.byId("phone.cleanup") ?: return
            viewModelScope.launch {
                when (val result = tool.execute(mapOf("kind" to "all", "action" to "scan"))) {
                    is com.example.meowmba.core.agent.tools.ToolResult.Success -> speak(result.output)
                    is com.example.meowmba.core.agent.tools.ToolResult.Failure -> speak(result.error)
                }
            }
        } else {
            speak("Without photo access I can still organize apps and open Messages.")
        }
    }

    fun onCleanupDeleteFinished(deleted: Boolean) {
        if (deleted) speak("Done — those files are gone.")
        else speak("Cancelled. Nothing was deleted.")
    }

    private fun tryLaunchAppImmediately(text: String): Boolean {
        val intent = AgentRouter.route(text) ?: return false
        if (intent.toolId != "phone.launch_app") return false
        val tool = agentToolRegistry?.byId("phone.launch_app")
            ?: secureStorage?.context?.let { LaunchAppTool(it) }
            ?: return false
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                captionText = intent.title,
                interactionState = InteractionState.IDLE,
            )
            when (val result = tool.execute(intent.args)) {
                is com.example.meowmba.core.agent.tools.ToolResult.Success -> {
                    _uiState.value = _uiState.value.copy(captionText = result.output)
                    updateVoiceListeningMode()
                }
                is com.example.meowmba.core.agent.tools.ToolResult.Failure -> speak(result.error)
            }
        }
        return true
    }

    private fun submitAgentIntent(intent: AgentRouter.AgentIntent) {
        val orchestrator = agentOrchestrator ?: run {
            speak("Agents aren't available in this build — the workspace isn't configured.")
            return
        }
        val result = orchestrator.submit(intent.agentId, intent.toolId, intent.args, intent.title)
        when (result) {
            is SubmitResult.Rejected -> speak(result.reason)
            is SubmitResult.Started -> {
                // avatar.animate / avatar.describe_animation: the animation IS the
                // response — no preamble, no TTS. Reset to IDLE immediately so wake
                // word listening restarts; processUserSpeech left us in THINKING.
                if (intent.toolId == "phone.launch_app") {
                    // Tool is already running; do not announce approval.
                    _uiState.value = _uiState.value.copy(
                        interactionState = InteractionState.IDLE,
                        captionText = intent.title,
                    )
                    updateVoiceListeningMode()
                    return
                }
                if (intent.toolId == "avatar.animate" || intent.toolId == "avatar.describe_animation") {
                    _uiState.value = _uiState.value.copy(
                        interactionState = InteractionState.IDLE,
                        captionText = null,
                        emotion = CompanionEmotion.IDLE,
                    )
                    updateVoiceListeningMode()
                    return
                }
                val agentName = AgentRegistry.byId(intent.agentId)?.name ?: "Agent"
                speak(
                    if (result.queued) {
                        "$agentName queued that — I'm at my task limit right now."
                    } else {
                        "$agentName is on it. I'll ask if a step needs your approval."
                    }
                )
            }
        }
    }

    private sealed interface LocationCommand {
        data class Save(val name: String) : LocationCommand
        data class Recall(val name: String) : LocationCommand
    }

    /**
     * Named-place voice forms. Recall only fires for a name we actually have
     * saved, so "where is the best sushi" still reaches chat.
     */
    private fun parseNamedLocation(text: String): LocationCommand? {
        val normalized = WakePhraseMatcher.normalize(text)
        // Save forms may keep a possessive/determiner ("my mailbox", "the gym");
        // recall forms drop it ("where is my mailbox" -> "mailbox"). Strip it
        // here so both sides canonicalise to the same key.
        fun saveName(raw: String): String {
            var n = raw.trim()
            for (p in listOf("my ", "the ")) {
                if (n.startsWith(p)) n = n.removePrefix(p).trim()
            }
            return n
        }
        for (prefix in listOf("remember this place as ", "remember this as ")) {
            if (normalized.startsWith(prefix)) {
                val name = saveName(normalized.removePrefix(prefix))
                if (name.isNotBlank()) return LocationCommand.Save(name)
            }
        }
        if (normalized.startsWith("remember this is my ")) {
            val name = saveName(normalized.removePrefix("remember this is my "))
            if (name.isNotBlank()) return LocationCommand.Save(name)
        }
        if (normalized.startsWith("remember this is the ")) {
            val name = saveName(normalized.removePrefix("remember this is the "))
            if (name.isNotBlank()) return LocationCommand.Save(name)
        }
        if (normalized.startsWith("remember ") && normalized.endsWith(" here")) {
            val name = saveName(normalized.removePrefix("remember ").removeSuffix(" here"))
            if (name.isNotBlank()) return LocationCommand.Save(name)
        }
        if (normalized.startsWith("this is my ") || normalized.startsWith("this is the ")) {
            val prefix = if (normalized.startsWith("this is my ")) "this is my " else "this is the "
            val name = saveName(normalized.removePrefix(prefix))
            if (name.isNotBlank()) return LocationCommand.Save(name)
        }
        for (prefix in listOf(
            "where is my ", "where's my ", "where is the ", "where's the ", "where is ", "where's "
        )) {
            if (normalized.startsWith(prefix)) {
                val name = normalized.removePrefix(prefix).trim()
                if (name.isNotBlank() && placesStore?.find(name) != null) {
                    return LocationCommand.Recall(name)
                }
            }
        }
        return null
    }

    private fun saveNamedLocation(name: String) {
        val context = secureStorage?.context
        val store = placesStore
        if (context == null || store == null) {
            speak("I can't save places right now.")
            resetState()
            return
        }
        if (!com.example.meowmba.core.nav.CurrentLocation.hasPermission(context)) {
            _uiState.value = _uiState.value.copy(locationPermissionRequested = true)
            speak("I need location permission to save places — allow it, then ask me again.")
            return
        }
        val fix = com.example.meowmba.core.nav.CurrentLocation.last(context)
        if (fix == null) {
            fuLog("saveNamedLocation: no cached fix — requesting fresh one-shot for '$name'")
            speak("Let me get a GPS fix first…")
            viewModelScope.launch {
                val fresh = suspendCancellableCoroutine<com.example.meowmba.core.nav.CurrentLocation.Fix?> { cont ->
                    com.example.meowmba.core.nav.CurrentLocation.requestOnce(
                        context,
                        timeoutMs = 12_000L,
                    ) { fix -> cont.resumeWith(kotlin.Result.success(fix)) }
                }
                if (fresh != null) {
                    fuLog("saveNamedLocation: got fresh fix ${fresh.lat},${fresh.lon} — saving '$name'")
                    store.save(name, fresh.lat, fresh.lon)
                    speak("Saved ${name.lowercase()} at ${"%.4f".format(fresh.lat)}, ${"%.4f".format(fresh.lon)}.")
                } else {
                    fuLog("saveNamedLocation: still no fix for '$name'")
                    speak("I can't get a GPS fix right now — try again in a few seconds.")
                    resetState()
                }
            }
            return
        }
        store.save(name, fix.lat, fix.lon)
        speak("Saved ${name.lowercase()} at ${"%.4f".format(fix.lat)}, ${"%.4f".format(fix.lon)}.")
    }

    private fun recallNamedLocation(name: String) {
        val place = placesStore?.find(name)
        if (place == null) {
            speak("I don't have that place saved yet.")
            resetState()
            return
        }
        speak("${name.lowercase()} is at ${"%.4f".format(place.lat)}, ${"%.4f".format(place.lon)}.")
    }

    private fun handleNoteIntent(intent: NoteSessionIntent) {
        when (intent) {
            is NoteSessionIntent.Stop -> stopNoting()
            is NoteSessionIntent.Start -> startNoting(intent.topic)
            is NoteSessionIntent.Continue -> startNoting(intent.topic ?: lastNotingTitle?.substringAfter("Notes/"))
        }
    }

    /** Chip X and "stop notes". */
    fun stopNoting() {
        if (!_uiState.value.isNoting) return
        val title = _uiState.value.notingTitle
        _uiState.value = _uiState.value.copy(
            isNoting = false,
            notingTitle = null,
            interactionState = InteractionState.IDLE,
            captionText = title?.let { "Saved $it" },
        )
        updateVoiceListeningMode()
    }

    private fun startNoting(topic: String?) {
        val name = topic?.trim()?.ifBlank { null } ?: todayNoteTopic()
        val title = NoteSessionParser.vaultTitle(name)
        lastNotingTitle = title
        ttsManager?.stop()
        _uiState.value = _uiState.value.copy(
            isNoting = true,
            notingTitle = title,
            isMuted = true,
            interactionState = InteractionState.IDLE,
            captionText = "Taking notes",
        )
        vaultRepository?.append(title, "_Started ${todayNoteTopic()} ${nowClock()}_")
        updateVoiceListeningMode()
    }

    private fun appendNoteLine(text: String) {
        val title = _uiState.value.notingTitle ?: return
        val stamp = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            .format(java.util.Date())
        vaultRepository?.append(title, "**$stamp**  \n$text.trim()")
    }

    private fun todayNoteTopic(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun nowClock(): String =
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())

    /** Explicit "remember that …" / "remember this" commands. */
    private fun handleRememberCommand(originalText: String, command: MemoryCommandParser.MemoryCommand) {
        responseJob = viewModelScope.launch {
            if (memoryRepository == null) {
                speak("Memory storage isn't configured yet.")
                return@launch
            }
            persistTurn(MessageRole.USER, originalText)

            if (_uiState.value.memoryPaused) {
                speak("Memory saving is paused. Enable it in the Memory screen and I'll remember things again.")
                return@launch
            }

            val content = command.content ?: lastAssistantText ?: lastUserText
            if (content == null) {
                speak("What should I remember? Say it after \"remember that\".")
                return@launch
            }

            memoryRepository.addMemory(
                content = content,
                type = MemoryType.SEMANTIC,
                importance = ImportanceLevel.HIGH,
                tags = listOf("explicit")
            )
            speak("Got it — I'll remember that.")
        }
    }

    /** Embedding recall over the recent memory pool, honoring the pause flag. */
    private suspend fun recallMemories(text: String): List<RecallResult> =
        // 200 cosine-rank + embedding on the turn path: ran on Main and hitched
        // every reply while the DB grew. CPU work on Default, DB on IO.
        withContext(kotlinx.coroutines.Dispatchers.Default) {
            val repo = memoryRepository ?: return@withContext emptyList()
            if (_uiState.value.memoryPaused) return@withContext emptyList()
            val candidates = withContext(ioDispatcher) { repo.getRecentMemories(200) }
                ?: return@withContext emptyList()
            if (candidates.isEmpty()) return@withContext emptyList()
            val queryEmbedding = embedder.getEmbedding(text)
            RecallEngine.rank(queryEmbedding, text, candidates)
        }

    /**
     * Speaks one chunk of a streamed reply. [first] starts a new utterance (flushing
     * anything stale and taking audio focus); later chunks are appended so they do not
     * cut off what is still playing.
     */
    private fun speakStreaming(text: String, first: Boolean) {
        if (text.isBlank()) return
        if (_uiState.value.isMuted) return
        // After the hand-off the background service owns the mic; any speech
        // enqueued from here on would leak into the user's reply window.
        if (handedOffToBackground) return
        val tts = ttsManager
        if (tts == null) {
            if (first) startMockSpeaking(text)
            return
        }
        // Word-boundary offsets are per-utterance, so the echo filter tracks per chunk.
        selfVoiceFilter.begin(text)
        if (first) {
            audioFocusManager?.requestFocus()
            resetMouthEnergy()
            _uiState.value = _uiState.value.copy(
                emotion = CompanionEmotion.HAPPY,
            )
            tts.speak(text)
        } else {
            tts.speakMore(text)
        }
    }

    /**
     * Once-per-reply bookkeeping. Kept out of [speakStreaming] so a reply spoken as
     * five sentences still lands in memory and notifications as ONE assistant message.
     */
    private fun recordSpokenResponse(full: String) {
        viewModelScope.launch {
            persistTurn(MessageRole.ASSISTANT, full)
        }
        // Response notifications only when the app is in the background — in
        // the foreground the face + spoken reply are the feedback.
        if (appInBackground) notificationHelper?.postThought(full)
        lastAssistantText = full
    }

    private fun speak(text: String) {
        fuLog("speak: \"${text.take(80)}\"")
        if (handedOffToBackground) return
        if (_uiState.value.isMuted) {
            fuLog("speak skipped: mouth muted")
            return
        }
        viewModelScope.launch {
            persistTurn(MessageRole.ASSISTANT, text)
        }

        // Same background-only rule as recordSpokenResponse: no notification
        // while the face is in front of the user.
        if (appInBackground) notificationHelper?.postThought(text)

        if (ttsManager == null) {
            startMockSpeaking(text)
            return
        }

        selfVoiceFilter.begin(text)
        audioFocusManager?.requestFocus()
        lastAssistantText = text
        resetMouthEnergy()
        _uiState.value = _uiState.value.copy(
            captionText = text,
            emotion = CompanionEmotion.HAPPY
        )
        ttsManager.speak(text)
    }

    private fun startMockSpeaking(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.SPEAKING,
                captionText = "",
                emotion = CompanionEmotion.HAPPY
            )

            val words = text.split(" ")
            var currentText = ""
            for (word in words) {
                currentText += "$word "
                _uiState.value = _uiState.value.copy(captionText = currentText.trim())
                delay(300)
            }

            delay(1000)
            onSpeechFinished()
        }
    }

    // ── Vision (Phase 7a/7b) ────────────────────────────────────────────────

    /**
     * Look once and describe: permission → capture/reuse → OCR or vision
     * provider → speak. Every step fails honestly and out loud.
     *
     * Phase 7b: persistent frame — follow-up focus queries (e.g. "look at the
     * cup") reuse the last JPEG instead of recapturing. "read this" / "read the
     * screen" use a read-focused prompt for the vision provider.
     */
    internal fun lookAround(query: com.example.meowmba.core.vision.VisionRouter.VisionQuery) {
        val vm = visionManager
        if (vm == null) {
            speak("I don't have a camera in this build.")
            resetState()
            return
        }
        if (!vm.hasPermission()) {
            _uiState.value = _uiState.value.copy(cameraPermissionRequested = true)
            speak("I need camera permission to see — allow it, then ask me again.")
            return
        }
        // Asking "what do you see" is consent for one look. Privacy CAM OFF
        // used to leave the capture on a dead/back camera, or the chat brain
        // inventing "I don't have camera access".
        if (!_uiState.value.isCameraEnabled) {
            _uiState.value = _uiState.value.copy(isCameraEnabled = true)
            secureStorage?.saveBooleanPreference(KEY_CAMERA_ENABLED, true)
        }
        vm.setUseFrontCamera(true)
        vm.setFaceTrackingEnabled(true)

        viewModelScope.launch {
            val jpeg: ByteArray
            if (lastVisionJpeg == null || query.focus == null) {
                jpeg = vm.captureJpeg().getOrElse {
                    showProviderError("camera: ${it.message}")
                    delay(ERROR_DISPLAY_MS)
                    resetState()
                    return@launch
                }
                lastVisionJpeg = jpeg
                val thumb = runCatching {
                    android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                }.getOrNull()
                _uiState.value = _uiState.value.copy(capturedFrame = thumb)
            } else {
                jpeg = lastVisionJpeg!!
            }

            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.THINKING,
                captionText = "Looking…",
                emotion = CompanionEmotion.CURIOUS
            )

            val provider = visionProviderFor()
            if (provider == null) {
                // Defensive — visionProviderFor always falls back to on-device.
                speak("I can't see right now — vision isn't ready. Try again in a moment.")
                resetState()
                return@launch
            }

            provider.describe(
                jpeg, com.example.meowmba.core.vision.VisionRouter.promptFor(query)
            ).fold(
                onSuccess = { description ->
                    memoryRepository?.saveEpisodicCheckpoint(
                        "what do you see", "[vision] $description", null
                    )
                    speak(description)
                },
                onFailure = {
                    showProviderError(it.message ?: "vision failed")
                    delay(ERROR_DISPLAY_MS)
                    resetState()
                }
            )
        }
    }

    /** Live GPS speed. Drive mode is not required — a one-shot fix is enough. */
    private fun answerHowFast() {
        val ctx = secureStorage?.context
        if (ctx == null || !com.example.meowmba.core.nav.CurrentLocation.hasPermission(ctx)) {
            _uiState.value = _uiState.value.copy(locationPermissionRequested = true)
            speak("I need location permission to read speed — allow it, then ask me again.")
            return
        }
        val live = carDetector?.driving?.value?.speedMph ?: 0f
        val last = com.example.meowmba.core.nav.CurrentLocation.last(ctx)?.speedMph ?: 0f
        val mph = maxOf(live, last)
        if (mph >= 1f) {
            speak("You're going ${mph.toInt()} miles per hour.")
            return
        }
        speak("Checking GPS…")
        com.example.meowmba.core.nav.CurrentLocation.requestOnce(ctx, timeoutMs = 4_000L) { fix ->
            val now = maxOf(carDetector?.driving?.value?.speedMph ?: 0f, fix?.speedMph ?: 0f)
            if (now >= 1f) {
                speak("You're going ${now.toInt()} miles per hour.")
            } else {
                speak("GPS has you basically stopped.")
            }
        }
    }

    /** The MainActivity permission launcher reports back through here. */
    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(cameraPermissionRequested = false)
        if (granted) {
            // Start auto face tracking; only announce if a look was mid-flight.
            ensureFaceTrackingOn()
            if (_uiState.value.interactionState == InteractionState.THINKING ||
                _uiState.value.captionText != null
            ) {
                speak("Camera's on — ask me to look again.")
                resetState()
            }
        } else {
            visionManager?.setFaceTrackingEnabled(false)
            speak("No camera permission, so I can't see. You can enable it later in system settings.")
            resetState()
        }
    }

    /** Location permission result from saving a named place. */
    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationPermissionRequested = false)
        if (granted) {
            speak("Location's on — say \"remember this as\" a place name and I'll save it.")
        } else {
            speak("No location permission, so I can't save places. You can enable it later in system settings.")
        }
        resetState()
    }

    /**
     * Vision provider selection mirrors the chat provider. The standalone
     * build has no home server: the "Network" lane only works if the user
     * configured an endpoint themselves, otherwise meow says honestly that
     * it can't see. OpenAI → cloud vision, on-device → DeviceVisionProvider
     * using Google Play Services ML Kit (image labeling + text recognition).
     */
    internal fun visionProviderFor(): com.example.meowmba.core.vision.VisionProvider? {
        val selected = _uiState.value.selectedModel
        return when {
            selected.contains("Network") || selected.contains("OpenRouter") ||
                selected.contains("Omni") ->
                lanVisionOrNull() ?: openRouterVisionOrNull()
            selected.contains("Cloud") -> com.example.meowmba.core.vision.OpenAIVisionProvider(
                "https://api.openai.com",
                secureStorage?.getApiKey("openai"),
                "gpt-4o-mini"
            )
            else -> lanVisionOrNull() ?: openRouterVisionOrNull()
                ?: com.example.meowmba.core.vision.DeviceVisionProvider()
        }
    }

    /**
     * Page design needs a real multimodal model. ML Kit labels cannot restyle
     * a page, so the browser loop never falls back to DeviceVision.
     */
    internal fun browserVisionProvider(): com.example.meowmba.core.vision.VisionProvider? =
        lanVisionOrNull() ?: openRouterVisionOrNull() ?: openAiVisionOrNull()

    private fun lanVisionOrNull(): com.example.meowmba.core.vision.VisionProvider? =
        secureStorage?.getEndpoint("network_vision")?.takeIf { it.isNotBlank() }?.let {
            com.example.meowmba.core.vision.OllamaVisionProvider(
                it.trimEnd('/'),
                secureStorage?.getModelName("network_vision") ?: "llava"
            )
        }

    private fun openRouterVisionOrNull(): com.example.meowmba.core.vision.VisionProvider? {
        val key = secureStorage?.getApiKey("openrouter")?.takeIf { it.isNotBlank() } ?: return null
        val model = secureStorage?.getModelName("openrouter_vision")
            ?.takeIf { it.isNotBlank() }
            ?: OPENROUTER_VISION_MODEL
        return com.example.meowmba.core.vision.OpenAIVisionProvider(
            OPENROUTER_ENDPOINT,
            key,
            model,
            name = "OpenRouter vision",
            maxTokens = 2_500,
        )
    }

    private fun openAiVisionOrNull(): com.example.meowmba.core.vision.VisionProvider? {
        val key = secureStorage?.getApiKey("openai")?.takeIf { it.isNotBlank() } ?: return null
        return com.example.meowmba.core.vision.OpenAIVisionProvider(
            "https://api.openai.com",
            key,
            "gpt-4o-mini",
            maxTokens = 2_500,
        )
    }

    // ── Interruption & conversation flow ────────────────────────────────────

    /**
     * User interruption: stop speaking immediately, cancel the response, and
     * listen for the new command.
     */
    fun interrupt() {
        if (_uiState.value.interactionState != InteractionState.SPEAKING) return
        supersedeResponseTurn()
        suppressSpeechFinished = true
        // A barge-in during the wake ack consumes that turn: drop the pending
        // ack so its fallback timer cannot open a SECOND capture on top of the
        // one startListening below opens.
        if (wakeAckPending) {
            wakeAckPending = false
            wakeAckListenJob?.cancel()
            wakeAckListenJob = null
        }
        ttsManager?.stop()
        startListening(prompt = "Listening…")
    }

    /**
     * While speaking, keep the recognizer LIVE so the user can barge in at
     * any moment. The [SelfVoiceFilter] (plus the wake phrase as an explicit
     * override) decides what is a genuine interruption and what is the
     * companion hearing itself — so playback is never interrupted by echo.
     */
    private fun startInterruptionWatch() {
        val s = _uiState.value
        if (speechService == null || !s.isMicEnabled) return
        speechService.startListening(
            onResult = { text ->
                // Quiet-open sessions end with an empty transcript; that is not
                // speech and must not interrupt playback.
                if (text.isBlank()) return@startListening
                if (matcher.match(text) != null || !selfVoiceFilter.isOwnVoice(text)) {
                    interrupt()
                }
            },
            onError = { /* recognition during playback is best-effort */ }
        )
    }

    private fun onSpeechFinished() {
        if (suppressSpeechFinished) {
            suppressSpeechFinished = false
            return
        }
        selfVoiceFilter.clear()
        if (wakeAckPending) {
            // The ack ("Yes?") finished speaking — open the command mic NOW
            // instead of waiting out the 1.5s fallback deadline. The old fixed
            // delay was sized for a slow TTS engine, so every bare wake paid
            // the full 1.5s of deaf time even though onSpeechFinished already
            // proves the audio drained. A short tail guard keeps the speaker
            // ring-down out of the capture; the delayed job below remains as
            // the fallback for engines that never report completion (muted,
            // silent init failure).
            wakeAckPending = false
            wakeAckListenJob?.cancel()
            wakeAckListenJob = null
            viewModelScope.launch {
                delay(WAKE_ACK_TAIL_GUARD_MS)
                // A background hand-off during the guard owns the mic now.
                if (!handedOffToBackground) startFollowUpListening()
            }
            return
        }
        if (handedOffToBackground) {
            // The reply window is owned by the background service now; an
            // in-app follow-up here would grab the mic and hear nothing.
            fuLog("onSpeechFinished ignored: handed off to background service")
            return
        }
        val s = _uiState.value
        if (s.callMode) {
            resetState()
            return
        }
        val follow = shouldEnterFollowUp(s) && speechService != null
        lastSpokeFinishedAtMs = System.currentTimeMillis()
        fuLog("onSpeechFinished -> ${if (follow) "FOLLOW-UP" else "reset"} (conv=${s.conversationMode} barge=${s.bargeInEnabled} wake=${s.wakeWordEnabled})")
        if (follow) {
            startFollowUpListening()
        } else {
            resetState()
        }
    }

    /** Conversation mode: stay for a follow-up question, then time out to idle. */
    private fun startFollowUpListening() {
        fuLog("startFollowUpListening")
        followUpJob?.cancel()
        // Free the continuous wake AudioRecord and any always-on session first —
        // otherwise SpeechRecognizer opens deaf and follow-up looks broken.
        stopAlwaysOnListening()
        isFollowUpListen = true
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.LISTENING,
            captionText = null,
            emotion = CompanionEmotion.ALERT
        )
        followUpJob = viewModelScope.launch {
            sherpaAsrEngine?.stopAndAwait()
            stopWakeLoopAndAwait()
            val engine = preferredSpeechEngine() ?: speechService ?: run {
                resetState()
                return@launch
            }
            // The interruption watch may still hold a live session; hand the mic over
            // cleanly before starting a stitched capture on top of it.
            if (engine.isListening.value) {
                engine.stopListening()
                delay(80)
            }
            // A follow-up is a spoken turn like any other: it has to survive a pause,
            // and a transient recognizer hiccup must not kill it silently.
            captureUtterance(engine, onError = { code -> onListeningError(code) }) { result ->
                processUserSpeech(result)
            }
            // Do not kill a turn that has already heard words. The old 8s
            // wall-clock cut the back half of follow-ups that started late.
            delay(FOLLOW_UP_TIMEOUT_MS)
            val heard = utteranceCapture?.hasHeardSpeech() == true
            if (_uiState.value.interactionState == InteractionState.LISTENING &&
                isFollowUpListen && !heard
            ) {
                utteranceCapture?.cancel()
                utteranceCapture = null
                engine.stopListening()
                resetState()
            }
        }
    }

    private fun endConversation() {
        isFollowUpListen = false
        followUpJob?.cancel()
        resetState()
    }

    private fun isStopCommand(text: String): Boolean {
        val normalized = WakePhraseMatcher.normalize(text)
        return normalized in SpeechIntents.STOP_COMMANDS
    }

    private fun isHangupCommand(text: String): Boolean {
        val t = WakePhraseMatcher.normalize(text)
        return t in setOf(
            "hang up", "hangup", "end call", "end the call",
            "stop the call", "that's all", "thats all",
        ) || t.startsWith("hang up")
    }

    // ── Mouth synchronization ───────────────────────────────────────────────

    /** TTS hit a word boundary: pulse the mouth, then let the energy decay. */
    private fun bumpMouthEnergy() {
        _mouthEnergy.value = 1f
        mouthDecayJob?.cancel()
        mouthDecayJob = viewModelScope.launch {
            for (level in listOf(0.7f, 0.45f, 0.25f)) {
                delay(70)
                _mouthEnergy.value = level
            }
        }
    }

    /** Silence the mouth: cancel any in-flight decay, then zero the energy. */
    private fun resetMouthEnergy() {
        mouthDecayJob?.cancel()
        mouthDecayJob = null
        _mouthEnergy.value = 0f
    }

    // ── Avatar zones & overlays ─────────────────────────────────────────────

    fun onPointerMove(offset: Offset) {
        _uiState.value = _uiState.value.copy(lookAtOffset = offset)
    }

    fun onAvatarZoneClick(zone: AvatarZone) {
        when (zone) {
            AvatarZone.EAR_LEFT, AvatarZone.EAR_RIGHT -> {
                val newMicState = !_uiState.value.isMicEnabled
                if (!newMicState) {
                    // Mic privacy cut: everything voice-related stops now
                    stopAlwaysOnListening()
                    stopWakeLoopAndRelease()
                    speechService?.stopListening()
                }
                _uiState.value = _uiState.value.copy(
                    isMicEnabled = newMicState,
                    interactionState = if (!newMicState) InteractionState.MUTED else InteractionState.IDLE
                )
                if (newMicState) {
                    // Re-enabling the mic must bring listening back. Which kind —
                    // always-on (face mode) or the wake phrase — is decided here,
                    // so the mic never sits dead after an ear tap. The stale
                    // "microphone is off" sentinel caption is cleared too.
                    _uiState.value = _uiState.value.copy(captionText = null)
                    updateVoiceListeningMode()
                }
            }
            AvatarZone.EYE_LEFT, AvatarZone.EYE_RIGHT -> {
                val camOn = !_uiState.value.isCameraEnabled
                _uiState.value = _uiState.value.copy(
                    isCameraEnabled = camOn,
                    isPreviewVisible = camOn,
                )
                secureStorage?.saveBooleanPreference(KEY_CAMERA_ENABLED, camOn)
                if (camOn) ensureFaceTrackingOn() else visionManager?.setFaceTrackingEnabled(false)
            }
            AvatarZone.MOUTH -> {
                val muting = !_uiState.value.isMuted
                if (muting) {
                    // Muting speech stops anything currently playing
                    ttsManager?.stop()
                }
                _uiState.value = _uiState.value.copy(isMuted = muting)
            }
            AvatarZone.FOREHEAD -> {
                _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
            }
            AvatarZone.BACKGROUND -> {
                _uiState.value = _uiState.value.copy(showTiles = !_uiState.value.showTiles)
            }
        }
    }

    fun toggleChat() {
        val opening = !_uiState.value.isChatExpanded
        if (!opening && _uiState.value.callMode) endCallMode()
        _uiState.value = _uiState.value.copy(isChatExpanded = opening)
        updateVoiceListeningMode()
    }

    fun onChatMicClick() {
        if (_uiState.value.callMode) {
            // In a call the open mic is already the input.
            return
        }
        onMicClick()
    }

    fun toggleCallMode() {
        if (_uiState.value.callMode) {
            endCallMode()
            speak("Call ended.")
        } else {
            startCallMode()
        }
    }

    private fun startCallMode() {
        if (!_uiState.value.isMicEnabled) {
            _uiState.value = _uiState.value.copy(
                captionText = "Microphone is off — tap an ear to re-enable",
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            callMode = true,
            isChatExpanded = true,
            isMuted = false,
        )
        stopWakeLoopAndRelease()
        updateVoiceListeningMode()
        speak("I'm here. Go ahead.")
    }

    private fun endCallMode() {
        if (!_uiState.value.callMode) return
        _uiState.value = _uiState.value.copy(callMode = false)
        ttsManager?.stop()
        updateVoiceListeningMode()
    }

    /** Swipe-up entry point: only opens, never closes. */
    fun openChat() {
        if (!_uiState.value.isChatExpanded) toggleChat()
    }

    /** Swipe-down entry point: only opens, never closes. */
    fun openTiles() {
        if (!_uiState.value.showTiles) {
            onAvatarZoneClick(AvatarZone.BACKGROUND)
        }
    }

    fun togglePrivacyDashboard() {
        _uiState.value = _uiState.value.copy(showPrivacyDashboard = !_uiState.value.showPrivacyDashboard)
    }

    /** Privacy dashboard / accessibility switch for the microphone. */
    fun setMicEnabled(enabled: Boolean) {
        if (_uiState.value.isMicEnabled == enabled) return
        onAvatarZoneClick(if (enabled) AvatarZone.EAR_LEFT else AvatarZone.EAR_RIGHT)
    }

    /** Privacy dashboard / accessibility switch for the camera. Never enables silently. */
    fun setCameraEnabled(enabled: Boolean) {
        if (_uiState.value.isCameraEnabled == enabled) return
        onAvatarZoneClick(AvatarZone.EYE_LEFT)
    }

    /** Privacy dashboard / accessibility switch for companion speech. */
    fun setMuted(muted: Boolean) {
        if (_uiState.value.isMuted == muted) return
        onAvatarZoneClick(AvatarZone.MOUTH)
    }

    /** Text input from the chat workspace, routed through the same pipeline as speech. */
    fun onUserTextSubmitted(text: String) {
        if (text.isBlank()) return
        processUserSpeech(text.trim())
    }

    /**
     * App-drawer search bar. Filters apps while typing; submit asks Meow,
     * runs a phone task, searches Google, or opens the matching app.
     */
    fun submitDrawerQuery(raw: String, matchCount: Int, singleLabel: String?) {
        val q = raw.trim()
        if (q.isBlank()) return
        when (val action = DrawerQuery.resolve(q, matchCount, singleLabel)) {
            is DrawerQuery.Action.Launch -> {
                closeOverlays()
                onUserTextSubmitted("open ${action.name}")
            }
            DrawerQuery.Action.Ask -> onUserTextSubmitted(q)
            DrawerQuery.Action.Task -> {
                closeOverlays()
                onUserTextSubmitted(q)
            }
            is DrawerQuery.Action.Web -> searchDrawerQuery(action.query)
            DrawerQuery.Action.Stay -> { }
        }
    }

    fun searchDrawerQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        closeOverlays()
        showSearchInBrowser(q)
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            memoryRepository?.deleteMessageById(id)
        }
    }

    fun togglePreview() {
        val visible = !_uiState.value.isPreviewVisible
        _uiState.value = _uiState.value.copy(isPreviewVisible = visible)
        if (visible) {
            _uiState.value = _uiState.value.copy(isFrontCamera = true)
            visionManager?.setUseFrontCamera(true)
            ensureFaceTrackingOn()
        } else if (_uiState.value.isCameraEnabled) {
            ensureFaceTrackingOn()
        }
    }

    /**
     * Face smile/wink or a hand sign → emotion + a short clip.
     * Debounced in [SocialCues.shouldReact] so a held smile does not spam.
     */
    private fun applySocialCue(track: com.example.meowmba.core.vision.FaceTrack?) {
        val cue = track?.cue ?: com.example.meowmba.core.vision.SocialCue.NONE
        val now = System.currentTimeMillis()
        if (!com.example.meowmba.core.vision.SocialCues.shouldReact(
                cue, lastSocialCue, now, lastSocialCueAtMs,
            )
        ) {
            if (cue != com.example.meowmba.core.vision.SocialCue.NONE) lastSocialCue = cue
            return
        }
        lastSocialCue = cue
        lastSocialCueAtMs = now
        val (emotionName, clip) = com.example.meowmba.core.vision.SocialCues.reaction(cue)
        val emotion = runCatching { CompanionEmotion.valueOf(emotionName) }.getOrNull()
        if (emotion != null && emotion != CompanionEmotion.IDLE) {
            _uiState.value = _uiState.value.copy(emotion = emotion)
        }
        val speaking = _uiState.value.interactionState == InteractionState.SPEAKING
        if (!speaking && clip != null && faceModeActive(_uiState.value)) {
            playAnimationNamed(clip, userRequested = false)
        }
        android.util.Log.i("MeowSocial", "cue=$cue emotion=$emotionName clip=$clip")
    }

    /**
     * Resume face tracking only if the user left the camera on.
     * [MainActivity.onResume] must call this — [ensureFaceTrackingOn] turns
     * the camera on, and doing that every unlock cooked the Fold.
     */
    fun resumeFaceTrackingIfEnabled() {
        if (!_uiState.value.isCameraEnabled) return
        if (!faceModeActive(_uiState.value)) return
        val vm = visionManager ?: return
        if (!vm.hasPermission()) return
        vm.setUseFrontCamera(true)
        vm.setFaceTrackingEnabled(true)
    }

    /**
     * Auto face tracking: front camera + ML Kit analysis, no PiP required.
     * Requests CAMERA if needed; re-tries from [onCameraPermissionResult].
     *
     * [followOnly] is the cheap mode used when a robot body is attached and the
     * only consumer is "keep them in frame" — see [VisionManager.setFollowOnly].
     * Anything the user actually wants to be *seen* for runs at full quality.
     */
    fun ensureFaceTrackingOn(followOnly: Boolean = false) {
        visionManager?.setFollowOnly(followOnly)
        if (!_uiState.value.isCameraEnabled) {
            // Privacy mark was off — re-enable camera for seeing the user.
            _uiState.value = _uiState.value.copy(isCameraEnabled = true)
            // Body-following is a session state, not a preference: persisting
            // it means a crash or a kill while the base is attached leaves the
            // front camera + ML Kit auto-starting at full rate on every later
            // launch, with no base attached to justify it.
            if (!followOnly) {
                secureStorage?.saveBooleanPreference(KEY_CAMERA_ENABLED, true)
            }
        }
        val vm = visionManager ?: return
        if (!vm.hasPermission()) {
            _uiState.value = _uiState.value.copy(cameraPermissionRequested = true)
            return
        }
        _uiState.value = _uiState.value.copy(isFrontCamera = true, isCameraEnabled = true)
        vm.setUseFrontCamera(true)
        val ok = vm.setFaceTrackingEnabled(true)
        android.util.Log.i("MeowVision", "ensureFaceTrackingOn ok=$ok perm=${vm.hasPermission()}")
    }

    /**
     * "Can you see me?" — answer from the face tracker, not a cloud model.
     * Falls back to a quick on-device describe if no face is in frame yet.
     */
    private fun answerSeeMe() {
        val vm = visionManager
        if (vm == null) {
            speak("I don't have a camera in this build.")
            return
        }
        if (!vm.hasPermission()) {
            _uiState.value = _uiState.value.copy(cameraPermissionRequested = true)
            speak("I need camera permission to see you — allow it, then ask me again.")
            return
        }
        ensureFaceTrackingOn()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.THINKING,
                captionText = "Looking for you…",
                emotion = CompanionEmotion.CURIOUS,
            )
            // Give the detector a beat to publish a face after (re)bind.
            var track = vm.faceTrack.value
            if (track == null) {
                kotlinx.coroutines.delay(900)
                track = vm.faceTrack.value
            }
            if (track != null) {
                val side = when {
                    track.offsetX < -0.25f -> " a bit to my left"
                    track.offsetX > 0.25f -> " a bit to my right"
                    else -> ""
                }
                val vert = when {
                    track.offsetY < -0.30f -> " and a little high"
                    track.offsetY > 0.30f -> " and a little low"
                    else -> ""
                }
                val extra = when (track.cue) {
                    com.example.meowmba.core.vision.SocialCue.SMILE -> " And you're smiling."
                    com.example.meowmba.core.vision.SocialCue.WAVE -> " And you're waving!"
                    com.example.meowmba.core.vision.SocialCue.THUMBS_UP -> " Nice thumbs up."
                    com.example.meowmba.core.vision.SocialCue.HAND_UP,
                    com.example.meowmba.core.vision.SocialCue.PALM -> " I see your hand."
                    com.example.meowmba.core.vision.SocialCue.POINT -> " Are you pointing at something?"
                    else -> ""
                }
                speak("Yes — I see you$side$vert!$extra")
                return@launch
            }
            // No face box yet: still try a frame describe so we aren't mute.
            val jpeg = vm.captureJpeg(768).getOrNull()
            if (jpeg != null) {
                lastVisionJpeg = jpeg
                val desc = visionProviderFor()?.describe(
                    jpeg,
                    "In one short sentence: is there a person visible? If yes, say you can see them."
                )?.getOrNull()
                if (!desc.isNullOrBlank()) {
                    speak(desc)
                    return@launch
                }
            }
            speak(
                "I'm looking with the front camera, but I don't have a clear face yet. " +
                    "Face me and try again — and make sure nothing else is using the camera."
            )
        }
    }

    fun flipCamera() {
        val useFront = !_uiState.value.isFrontCamera
        _uiState.value = _uiState.value.copy(isFrontCamera = useFront)
        visionManager?.setUseFrontCamera(useFront)
        // Pan the head back to center; the new camera is a fresh face-track session.
        if (!useFront) {
            com.example.meowmba.core.robot.FaceMotorMap.followFace(null, null)
                .forEach { (servo, angle) -> motorController.setServo(servo, angle) }
        }
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun toggleModelManager() {
        _uiState.value = _uiState.value.copy(showModelManager = !_uiState.value.showModelManager)
    }

    fun onModelSelected(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
        secureStorage?.savePreference(KEY_SELECTED_MODEL, model)
        updateProvider(model)
    }

    /**
     * Honest provider failure: red OFFLINE halo + explanatory caption for a
     * few seconds, then settle back to idle. Never fake a success.
     */
    internal fun showProviderError(message: String) {
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.OFFLINE,
            errorMessage = message,
            captionText = "Can't reach ${_uiState.value.selectedModel}: $message",
            emotion = CompanionEmotion.IDLE
        )
        viewModelScope.launch {
            delay(ERROR_DISPLAY_MS)
            if (_uiState.value.interactionState == InteractionState.OFFLINE) {
                resetState()
            }
        }
    }

    /** Hardware page: ping the configured cloud server (health check). */
    fun runCloudConnectionTest() {
        val storage = secureStorage ?: run {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Secure storage unavailable"
            )
            return
        }
        if (_uiState.value.connectionTestRunning) return
        _uiState.value = _uiState.value.copy(connectionTestRunning = true, connectionTestResult = null)
        viewModelScope.launch {
            val provider = RemoteModelProvider(
                "OpenAI",
                "https://api.openai.com",
                storage.getApiKey("openai"),
                storage.getModelName("openai") ?: "gpt-4o-mini"
            )
            val result = provider.ping()
            _uiState.value = _uiState.value.copy(
                connectionTestRunning = false,
                connectionTestResult = result
            )
        }
    }

    /** Hardware page: verify the on-device model file is actually present. */
    fun runLocalModelCheck() {
        refreshLoadedModels()
        val path = _uiState.value.loadedModels.localPath
        val exists = _uiState.value.loadedModels.localInstalled
        _uiState.value = _uiState.value.copy(
            connectionTestResult = if (exists) {
                "Model file present: $path"
            } else {
                "Model file not found at $path — download one below"
            }
        )
    }

    // ── Model downloads ─────────────────────────────────────────────────────

    /** Download a curated catalog model into app-private storage. */
    fun downloadModel(option: LocalModelOption) {
        val dl = downloader ?: run {
            setDownloadError(option.id, "Downloads are unavailable in this build")
            return
        }
        if (option.requiresHfToken && secureStorage?.getApiKey("huggingface").isNullOrBlank()) {
            setDownloadError(option.id, "Needs a free Hugging Face token — add it in Settings")
            return
        }
        val existing = _uiState.value.modelDownloads[option.id]
        if (existing != null && !existing.done && existing.error == null) return

        val starter = downloadServiceStarter
        val ctx = secureStorage?.context
        if (starter == null || ctx == null) {
            // In-process path (unit tests): run the download through the injected
            // ModelDownloader so its engine drives progress and install.
            viewModelScope.launch {
                setDownloadProgress(option.id, ModelDownloader.DownloadProgress())
                try {
                    val file = dl.download(option.downloadUrl, option.fileName) { p ->
                        setDownloadProgress(option.id, p)
                    }
                    installDownloadedModel(file.absolutePath)
                } catch (e: Exception) {
                    setDownloadProgress(
                        option.id,
                        ModelDownloader.DownloadProgress(error = e.message ?: "Download failed")
                    )
                }
            }
            return
        }

        // Start foreground service so download survives app backgrounding
        starter(
            ctx, option.downloadUrl, option.fileName, option.id,
            secureStorage?.getApiKey("huggingface"), dl.destinationFor(option.fileName).parentFile!!
        )

        // Lightweight ViewModel-side progress from file size (updates while in foreground).
        // The service handles the actual download and posts a system notification;
        // this coroutine polls so the in-app progress bars have something to show.
        val target = dl.destinationFor(option.fileName)
        viewModelScope.launch {
            setDownloadProgress(option.id, ModelDownloader.DownloadProgress())
            var staleTicks = 0
            while (true) {
                delay(2000)
                if (target.isFile && target.length() > 0) {
                    installDownloadedModel(target.absolutePath)
                    break
                }
                val part = File(target.parentFile, "${option.fileName}.part")
                if (part.isFile) {
                    staleTicks = 0
                    setDownloadProgress(option.id, ModelDownloader.DownloadProgress(received = part.length()))
                    } else if (++staleTicks > 30) {
                    val msg = "Download timed out or was cancelled"
                    setDownloadProgress(option.id, ModelDownloader.DownloadProgress(error = msg))
                    break
                }
            }
        }
    }

    /** Download any model from a custom URL (must be a MediaPipe .task bundle). */
    fun downloadCustomModel(url: String, fileName: String) {
        val id = "custom:$fileName"
        val dl = downloader ?: run {
            setDownloadError(id, "Downloads are unavailable in this build")
            return
        }
        when {
            !url.startsWith("http://") && !url.startsWith("https://") ->
                return setDownloadError(id, "URL must start with http:// or https://")
            !CleartextPolicy.isAllowed(url) ->
                return setDownloadError(id, CleartextPolicy.denyReason(url) ?: "Unsafe URL")
            !ModelCatalog.isValidModelFileName(fileName) ->
                return setDownloadError(id, "File name must be simple and end in .task")
        }

        val starter = downloadServiceStarter
        val ctx = secureStorage?.context
        if (starter == null || ctx == null) {
            // In-process path (unit tests), same as downloadModel.
            viewModelScope.launch {
                setDownloadProgress(id, ModelDownloader.DownloadProgress())
                try {
                    val file = dl.download(url, fileName) { p -> setDownloadProgress(id, p) }
                    installDownloadedModel(file.absolutePath)
                } catch (e: Exception) {
                    setDownloadProgress(
                        id,
                        ModelDownloader.DownloadProgress(error = e.message ?: "Download failed")
                    )
                }
            }
            return
        }

        starter(
            ctx, url, fileName, id,
            secureStorage?.getApiKey("huggingface"), dl.destinationFor(fileName).parentFile!!
        )

        val target = dl.destinationFor(fileName)
        viewModelScope.launch {
            setDownloadProgress(id, ModelDownloader.DownloadProgress())
            var staleTicks = 0
            while (true) {
                delay(2000)
                if (target.isFile && target.length() > 0) {
                    installDownloadedModel(target.absolutePath)
                    break
                }
                val part = File(target.parentFile, "$fileName.part")
                if (part.isFile) {
                    staleTicks = 0
                    setDownloadProgress(id, ModelDownloader.DownloadProgress(received = part.length()))
                } else if (++staleTicks > 30) {
                    val msg = "Download timed out or was cancelled"
                    setDownloadProgress(id, ModelDownloader.DownloadProgress(error = msg))
                    break
                }
            }
        }
    }

    /** Point the local provider at an already-downloaded catalog model. */
    fun useDownloadedModel(option: LocalModelOption) {
        val file = downloader?.installedFile(option) ?: return
        installDownloadedModel(file.absolutePath)
        if (!_uiState.value.selectedModel.contains("(Local)")) {
            onModelSelected("On-device (Local)")
        }
    }

    fun deleteDownloadedModel(option: LocalModelOption) {
        downloader?.delete(option)
        if (_uiState.value.loadedModels.localPath.endsWith(option.fileName)) {
            secureStorage?.saveEndpoint("local", "/data/local/tmp/llm/gemma.bin")
        }
        refreshLoadedModels()
        updateProvider(_uiState.value.selectedModel)
    }

    /** Point the local provider at any bundle present in the models dir (catalog or custom). */
    fun useInstalledModelFile(fileName: String) {
        val file = downloader?.listInstalled()?.firstOrNull { it.name == fileName } ?: return
        // On-device runtimes need ~the file size again in working RAM; past
        // ~45% of device RAM the load reliably crashes the app (observed:
        // 5.3 GB bundle OOM-killed on a 12 GB phone). Warn, don't block.
        val ramGb = secureStorage?.context?.let { com.example.meowmba.core.utils.SystemUtils.getTotalRamGb(it) }
        val tooLarge = ramGb != null &&
            file.length() > (ramGb * 1_073_741_824L * 0.45).toLong()
        installDownloadedModel(file.absolutePath)
        if (tooLarge) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Warning: ${file.name} is ${file.length() / 1_073_741_824L} GB — " +
                    "likely too large for this phone; expect a crash on first reply"
            )
        }
        if (!_uiState.value.selectedModel.contains("(Local)")) {
            onModelSelected("On-device (Local)")
        }
    }

    /** Delete any bundle present in the models dir (catalog or custom). */
    fun deleteInstalledModelFile(fileName: String) {
        val file = downloader?.listInstalled()?.firstOrNull { it.name == fileName } ?: return
        file.delete()
        if (_uiState.value.loadedModels.localPath.endsWith(fileName)) {
            secureStorage?.saveEndpoint("local", "/data/local/tmp/llm/gemma.bin")
        }
        refreshLoadedModels()
        updateProvider(_uiState.value.selectedModel)
    }

    /**
     * Download the on-device image-generation model (stable-diffusion.cpp
     * GGUF weights) into app-private storage. Progress is tracked in the same
     * [CompanionState.modelDownloads] map as LLM downloads, keyed by the
     * catalog option's id — no separate state is needed since the map is
     * already generic over "any download id".
     */
    fun downloadImageModel(option: ImageModelOption = ImageModelCatalog.options.first()) {
        val dl = downloader ?: run {
            setDownloadError(option.id, "Downloads are unavailable in this build")
            return
        }
        if (option.requiresHfToken && secureStorage?.getApiKey("huggingface").isNullOrBlank()) {
            setDownloadError(option.id, "Needs a free Hugging Face token — add it in Settings")
            return
        }
        val existing = _uiState.value.modelDownloads[option.id]
        if (existing != null && !existing.done && existing.error == null) return

        secureStorage?.context?.let { ctx ->
            DownloadService.start(
                ctx, option.downloadUrl, option.fileName, option.id,
                secureStorage?.getApiKey("huggingface"), dl.destinationFor(option.fileName).parentFile!!
            )
        }

        val target = dl.destinationFor(option.fileName)
        viewModelScope.launch {
            setDownloadProgress(option.id, ModelDownloader.DownloadProgress())
            var staleTicks = 0
            while (true) {
                delay(2000)
                if (target.isFile && target.length() > 0) {
                    setDownloadProgress(
                        option.id,
                        ModelDownloader.DownloadProgress(target.length(), target.length(), done = true)
                    )
                    break
                }
                val part = File(target.parentFile, "${option.fileName}.part")
                if (part.isFile) {
                    staleTicks = 0
                    setDownloadProgress(option.id, ModelDownloader.DownloadProgress(received = part.length()))
                } else if (++staleTicks > 30) {
                    val msg = "Download timed out or was cancelled"
                    setDownloadProgress(option.id, ModelDownloader.DownloadProgress(error = msg))
                    break
                }
            }
        }
    }

    /** Delete the installed image model and clear any stale progress entry for it. */
    fun deleteImageModel(option: ImageModelOption = ImageModelCatalog.options.first()) {
        downloader?.delete(option)
        _uiState.value = _uiState.value.copy(
            modelDownloads = _uiState.value.modelDownloads - option.id
        )
    }

    /**
     * Whether the on-device image model is present right now (for the
     * Settings screen and the media-generation tool). Fully derived from
     * disk — no installed-state is persisted, since [ModelDownloader.installedFile]
     * is already the source of truth and a stale flag could disagree with reality.
     */
    fun imageModelInstalled(option: ImageModelOption = ImageModelCatalog.options.first()): Boolean =
        downloader?.installedFile(option) != null

    private fun installDownloadedModel(path: String) {
        secureStorage?.saveEndpoint("local", path)
        refreshLoadedModels()
        updateProvider(_uiState.value.selectedModel)
    }

    private fun setDownloadProgress(id: String, progress: ModelDownloader.DownloadProgress) {
        _uiState.value = _uiState.value.copy(
            modelDownloads = _uiState.value.modelDownloads + (id to progress)
        )
    }

    private fun setDownloadError(id: String, message: String) {
        setDownloadProgress(id, ModelDownloader.DownloadProgress(error = message))
    }

    // ── Agent screen & controls ─────────────────────────────────────────────

    fun toggleAgentsScreen() {
        _uiState.value = _uiState.value.copy(showAgentsScreen = !_uiState.value.showAgentsScreen)
    }

    fun toggleProjectsScreen() {
        _uiState.value = _uiState.value.copy(showProjectsScreen = !_uiState.value.showProjectsScreen)
    }

    fun toggleToolsScreen() {
        _uiState.value = _uiState.value.copy(showToolsScreen = !_uiState.value.showToolsScreen)
    }

    // ── Plugins & MCP connectors ────────────────────────────────────────────

    fun toggleConnectorsScreen() {
        val show = !_uiState.value.showConnectorsScreen
        _uiState.value = _uiState.value.copy(showConnectorsScreen = show)
        if (show) refreshConnectorUi()
    }

    /** Push the current connector list (and lazy app discovery) into state. */
    fun refreshConnectorUi() {
        val mgr = connectorManager ?: return
        mgr.refreshConfigured()
        _uiState.value = _uiState.value.copy(connectors = mgr.connectors)
        if (_uiState.value.discoverableApps.isEmpty()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(discoverableApps = mgr.discoverApps())
            }
        }
    }

    /** Connect a discovered app so the agent can drive it. */
    fun connectDiscoveredApp(packageName: String, label: String) {
        val mgr = connectorManager ?: return
        val app = ConnectorManager.DiscoveredApp(packageName, label)
        mgr.upsert(mgr.connectorForApp(app).copy(configured = true, enabled = true))
        refreshConnectorUi()
    }

    /** Test an external MCP server endpoint; result lands in state for the UI. */
    fun testMcpEndpoint(endpoint: String) {
        val mgr = connectorManager ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectorBusy = true, connectorTestResult = null)
            val outcome = mgr.testMcpServer(endpoint).fold(
                onSuccess = { tools ->
                    true to "${tools.size} tool(s) ready: ${tools.take(6).joinToString(", ") { it.name }}"
                },
                onFailure = { e ->
                    false to (e.message ?: "Could not reach that server.")
                },
            )
            _uiState.value = _uiState.value.copy(connectorBusy = false, connectorTestResult = outcome)
        }
    }

    /** Persist an MCP connector (and its API key) then register its tools. */
    fun addMcpConnector(name: String, endpoint: String, apiKey: String): Result<Unit> {
        val mgr = connectorManager ?: return Result.failure(IllegalStateException("Not available."))
        if (endpoint.isBlank()) return Result.failure(IllegalArgumentException("Enter the server address."))
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Give the connector a name."))
        if (apiKey.isNotBlank()) secureStorage?.saveApiKey("connector_mcp_$endpoint", apiKey)
        val id = "mcp-" + java.util.UUID.randomUUID().toString().take(8)
        mgr.upsert(
            com.example.meowmba.core.plugins.Connector(
                id = id,
                name = name.trim(),
                description = "External MCP server at $endpoint.",
                kind = com.example.meowmba.core.plugins.ConnectorKind.MCP_SERVER,
                backend = com.example.meowmba.core.plugins.ConnectorBackend.MCP_HTTP,
                endpoint = endpoint,
                iconToken = "🔌",
                configured = true,
                enabled = true,
            )
        )
        refreshConnectorUi()
        viewModelScope.launch { mgr.registerToolsInto(agentToolRegistry ?: return@launch) }
        return Result.success(Unit)
    }

    /** Create a user plugin/skill. Icons are mandatory — a blank icon is rejected. */
    fun createUserPlugin(name: String, description: String, iconToken: String): Result<Unit> {
        val mgr = connectorManager ?: return Result.failure(IllegalStateException("Not available."))
        return mgr.createUserPlugin(name, description, iconToken).map { created ->
            refreshConnectorUi()
            viewModelScope.launch { mgr.registerToolsInto(agentToolRegistry ?: return@launch) }
        }
    }

    /** Enable/disable a connector (re-registers its tools). */
    fun setConnectorEnabled(id: String, enabled: Boolean) {
        val mgr = connectorManager ?: return
        if (mgr.setEnabled(id, enabled)) {
            refreshConnectorUi()
            viewModelScope.launch { mgr.registerToolsInto(agentToolRegistry ?: return@launch) }
        }
    }

    fun removeConnector(id: String) {
        val mgr = connectorManager ?: return
        mgr.remove(id)
        refreshConnectorUi()
    }

    fun openProject(id: Long) {
        viewModelScope.launch {
            val dao = projectDao ?: return@launch
            val project = dao.getById(id) ?: return@launch
            dao.touch(id, System.currentTimeMillis())
            applyProject(project.id, project.name, project.conversationId)
            _uiState.value = _uiState.value.copy(
                showProjectsScreen = false,
                showTiles = false,
                isChatExpanded = true,
            )
        }
    }

    fun leaveProject() {
        applyProject(null, null, 0L)
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            projectDao?.deleteById(id)
            if (_uiState.value.activeProjectId == id) leaveProject()
        }
    }

    private fun startProjectFromVoice(
        req: com.example.meowmba.core.work.ProjectStart,
        originalText: String,
    ) {
        viewModelScope.launch {
            val dao = projectDao
            if (dao == null) {
                speak("Projects aren't available in this build.")
                return@launch
            }
            val name = uniqueProjectName(dao, req.name)
            val now = System.currentTimeMillis()
            val conv = now
            val id = dao.insert(
                com.example.meowmba.core.database.ProjectEntity(
                    name = name,
                    kind = req.kind.name,
                    createdAt = now,
                    lastOpenedAt = now,
                    conversationId = conv,
                )
            )
            applyProject(id, name, conv)
            _uiState.value = _uiState.value.copy(
                showProjectsScreen = false,
                showTiles = false,
                isChatExpanded = true,
            )
            persistTurn(MessageRole.USER, originalText)
            memoryRepository?.addMemory(
                content = "Started ${req.kind.label} project: $name",
                type = MemoryType.PROJECT,
                importance = ImportanceLevel.MEDIUM,
                tags = listOf("project", req.kind.name.lowercase()),
            )
            speak("Started a ${req.kind.label} project called $name. It's in Projects — tap it any time to come back.")
            if (req.kind == com.example.meowmba.core.work.ProjectKind.WEBSITE) {
                driveAgentBrowser(
                    com.example.meowmba.core.work.BrowserRequest(
                        job = com.example.meowmba.core.work.BrowserJob.BUILD,
                        goal = "Build a complete styled website for the project called $name.",
                    )
                )
            }
        }
    }

    private fun makeToolFromVoice(
        req: com.example.meowmba.core.work.ToolStart,
        originalText: String,
    ) {
        viewModelScope.launch {
            val dao = routineDao
            if (dao == null) {
                speak("Tools aren't available in this build.")
                return@launch
            }
            persistTurn(MessageRole.USER, originalText)
            dao.insert(
                com.example.meowmba.core.database.RoutineEntity(
                    name = req.name,
                    timeOfDay = req.timeOfDay,
                    action = req.action,
                    message = req.message,
                    enabled = true,
                    source = "user",
                    launchTarget = req.launchTarget,
                )
            )
            val whenBit = req.timeOfDay?.let { " at $it" } ?: ""
            speak("Saved ${req.name}$whenBit. It's on the Tools page.")
        }
    }

    private suspend fun uniqueProjectName(
        dao: com.example.meowmba.core.database.ProjectDao,
        requested: String,
    ): String {
        val existing = dao.names()
        if (existing.none { it.equals(requested, ignoreCase = true) }) return requested
        var n = 2
        while (existing.any { it.equals("$requested $n", ignoreCase = true) }) n++
        return "$requested $n"
    }

    private fun applyProject(id: Long?, name: String?, conversationId: Long) {
        _uiState.value = _uiState.value.copy(
            activeProjectId = id,
            activeProjectName = name,
            activeConversationId = conversationId,
        )
        secureStorage?.savePreference(KEY_ACTIVE_PROJECT_ID, id?.toString() ?: "")
        publishChat()
    }

    private fun conversationId(): Long = _uiState.value.activeConversationId

    private suspend fun persistTurn(role: MessageRole, text: String, speaker: String? = null): Long? {
        val who = if (role == MessageRole.USER) speaker ?: lastSpeakerName else null
        return memoryRepository?.addMessage(role, text, conversationId(), who)
    }

    private fun publishChat() {
        val conv = conversationId()
        val name = _uiState.value.name
        _uiState.value = _uiState.value.copy(
            chatMessages = cachedMessages.filter { it.conversationId == conv }.map { message ->
                ChatUiMessage(
                    id = message.id,
                    sender = when {
                        message.role != MessageRole.USER -> name
                        !message.speakerName.isNullOrBlank() ->
                            EnrollPhraseParser.displayName(message.speakerName)
                        else -> "You"
                    },
                    text = message.content,
                    isCompanion = message.role != MessageRole.USER
                )
            }
        )
    }

    private fun refreshSpeechLikely() {
        val listening = _uiState.value.interactionState == InteractionState.LISTENING ||
            speechService?.isListening?.value == true ||
            lastAlwaysOnPartial.isNotBlank()
        visionManager?.setSpeechLikely(listening)
    }

    private fun captionWithSpeaker(partial: String): String {
        val who = _uiState.value.activeSpeaker
        return if (who != null && (_uiState.value.facesInView >= 2 || faceSpeaker.isNotEmpty())) {
            "$who: $partial"
        } else {
            partial
        }
    }

    private fun speakerLabelFor(track: com.example.meowmba.core.vision.FaceTrack?): String? {
        if (track == null || !track.hasFace) return lastSpeakerName?.let { EnrollPhraseParser.displayName(it) }
        faceSpeaker[track.trackingId]?.let { return EnrollPhraseParser.displayName(it) }
        val faces = visionManager?.lastFaces.orEmpty()
        val seen = faces.firstOrNull { it.trackingId == track.trackingId }
        val slot = seen?.let { TalkerPicker.slotLabel(it, faces) }
        return slot ?: lastSpeakerName?.let { EnrollPhraseParser.displayName(it) }
    }

    private fun bindSpeaker(name: String, faceId: Int? = null) {
        lastSpeakerName = name
        val id = faceId?.takeIf { it >= 0 }
            ?: visionManager?.faceTrack?.value?.trackingId?.takeIf { it >= 0 }
        if (id != null) faceSpeaker[id] = name
        val shown = EnrollPhraseParser.displayName(name)
        val prev = _uiState.value
        if (prev.activeSpeaker != shown) {
            _uiState.value = prev.copy(activeSpeaker = shown)
        }
    }

    private fun identifyFromAudio(): String? {
        val recPcm = (speechService as? SpeechService)?.lastHeardPcm()?.takeIf { it.isNotEmpty() }
        val asrPcm = sherpaAsrEngine?.lastHeardPcm?.takeIf { it.isNotEmpty() }
        val wakePcm = (wakeWordEngine as? com.example.meowmba.core.audio.SherpaKwsEngine)
            ?.lastPreRoll?.takeIf { it.isNotEmpty() }
        val pcm = recPcm ?: asrPcm ?: wakePcm ?: return null
        return speakerVerifier?.identifyPcm(pcm)
    }

    private fun ensureVoiceModelsThenAttachWake() {
        val ctx = secureStorage?.context
        // Restore the saved backend BEFORE any engine is attached, so the
        // first always-on session already runs on the user's pick instead of
        // opening on the default and switching a beat later.
        secureStorage?.let { storage ->
            asrBackend = com.example.meowmba.core.audio.AsrBackend.fromId(
                storage.getPreference(com.example.meowmba.core.audio.AsrBackend.PREF_KEY),
            )
        }
        refreshAsrBackendState()
        if (ctx == null) {
            attachWakeEngine()
            return
        }
        val store = com.example.meowmba.core.audio.VoiceModelStore(ctx)
        if (store.isReady()) {
            if (store.asrReady()) attachAsrEngine()
            attachWakeEngine()
            viewModelScope.launch {
                store.ensureAsr()
                attachAsrEngine()
                if (shouldAlwaysOnMic()) updateVoiceListeningMode()
            }
            return
        }
        _uiState.value = _uiState.value.copy(
            voicePackReady = false,
            voicePackLabel = "Getting voice models…",
            voicePackProgress = 0f,
        )
        viewModelScope.launch {
            val result = store.ensure { frac, label ->
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        voicePackProgress = frac,
                        voicePackLabel = label,
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                voicePackReady = true,
                voicePackProgress = null,
                voicePackLabel = result.exceptionOrNull()?.message,
            )
            attachWakeEngine()
            attachAsrEngine()
            updateVoiceListeningMode()
        }
    }

    private fun attachAsrEngine() {
        val ctx = secureStorage?.context ?: return
        if (sherpaAsrEngine != null) {
            // The local engine exists but the cloud wrapper may not — e.g. the
            // model finished downloading after the user had already picked
            // Gemini. Cheap and idempotent, so always reconcile.
            return
        }
        if (!com.example.meowmba.core.audio.VoiceModelStore(ctx).asrReady()) return
        sherpaAsrEngine = SherpaAsrEngine(ctx, viewModelScope)
        attachWhisperRefiner()
        fuLog("on-device always-on ASR attached")
        // This attach is async relative to the always-on arm that raced it
        // (measured on device 18:57:17: the attach finished AFTER the collector
        // had already opened on the fresh engine, then tore the session down
        // and re-loaded the whole sherpa pack — a collector-restart + double
        // model load in under a second). If a session is already live on this
        // engine, leave it alone; startAlwaysOnListeningAfterKws already
        // handles the not-open case.
        if (shouldAlwaysOnMic() &&
            _uiState.value.interactionState == InteractionState.IDLE &&
            !(alwaysOnActive && alwaysOnSessionOpen && alwaysOnCaptureEngine == sherpaAsrEngine)
        ) {
            stopAlwaysOnListening()
            startAlwaysOnListening()
        }
    }

    private fun attachWakeEngine() {
        wakeWordEngine?.stop()
        (wakeWordEngine as? com.example.meowmba.core.audio.SherpaKwsEngine)?.release()
        wakeWordEngine = null
        speakerVerifier = null
        val wakeEngineSource = (speechService as? SpeechService)
            ?.createSessionEngine() ?: speechService
        val built = com.example.meowmba.core.audio.WakeWordEngineFactory.create(
            context = secureStorage?.context,
            scope = viewModelScope,
            companionName = _uiState.value.name,
            matcher = matcher,
            speechEngine = wakeEngineSource,
        ) ?: return
        speakerVerifier = built.verifier
        wakeWordEngine = built.engine.apply {
            onWake = { command ->
                val pcm = built.kws?.lastPreRoll
                if (pcm != null && pcm.isNotEmpty()) {
                    built.verifier?.identifyPcm(pcm)?.let { bindSpeaker(it) }
                }
                if (command != null) {
                    onWakeDetected(command)
                } else {
                    // One-breath command recovery. The spotter reports only
                    // that the phrase fired; anything said with it lives only
                    // in the pre-roll PCM. Transcribe that buffer and treat
                    // words after the phrase as the command — otherwise they
                    // are lost to recognizer startup and the user repeats
                    // themselves (the "cuts off the first syllable" report).
                    viewModelScope.launch {
                        val transcript = pcm
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { sherpaAsrEngine?.transcribePcm(it) }
                        // Consumed — a spotter re-fire on trailing audio must
                        // not hand the same buffer up twice.
                        built.kws?.clearPreRoll()
                        val recovered = transcript
                            ?.let { matcher.match(it)?.command }
                            ?.trim()
                            ?.takeIf { com.example.meowmba.core.audio.SpokenText.isUsable(it) }
                        if (recovered != null) {
                            fuLog("pre-roll recovered one-breath command: \"$recovered\"")
                        }
                        onWakeDetected(recovered)
                    }
                }
            }
        }
        (built.engine as? RecognizerWakeWordEngine)?.onFatalError = {
            _uiState.value = _uiState.value.copy(
                wakeWordEnabled = false,
                captionText = "Wake phrase stopped — the speech recognizer keeps failing on this device"
            )
        }
        built.kws?.onMicUnavailable = { stopWakeLoopAndRelease() }
        built.kws?.onWakeRejected = {
            _uiState.value = _uiState.value.copy(
                captionText = "I heard the wake phrase but didn't recognise the voice"
            )
        }
        built.kws?.onFatalError = { reason ->
            _uiState.value = _uiState.value.copy(
                wakeWordEnabled = false,
                captionText = "Wake phrase stopped — $reason"
            )
        }
        built.kws?.onLoudNoWake = { pcm ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { saveWakeMissAudio(pcm) }
                    .onSuccess { android.util.Log.i("WakeMissDiag", "wake-miss audio saved: $it") }
                    .onFailure { android.util.Log.w("WakeMissDiag", "wake-miss save failed: ${it.message}") }
            }
            // Camera-off nap left us on the wake spotter. Speech that isn't
            // the wake phrase used to be dumped and ignored — the deaf mic.
            if (VoicePolicy.shouldResumeAlwaysOnFromSpeech(_uiState.value) &&
                _uiState.value.interactionState == InteractionState.IDLE
            ) {
        lastUsableSpeechAtMs = System.currentTimeMillis()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    fuLog("loud speech on wake nap — reopening always-on")
                    updateVoiceListeningMode()
                }
            }
        }
        if (_uiState.value.wakeWordEnabled && !shouldAlwaysOnMic()) startWakeStandby()
    }

    private fun handleSpeakerEnroll(name: String, faceId: Int? = null) {
        bindSpeaker(name, faceId)
        persistOwnerName(name)
        rememberOwnerName(name)
        val pcm = (speechService as? SpeechService)?.lastHeardPcm()?.takeIf { it.isNotEmpty() }
            ?: (wakeWordEngine as? com.example.meowmba.core.audio.SherpaKwsEngine)
                ?.lastPreRoll?.takeIf { it.isNotEmpty() }
        if (pcm != null) speakerVerifier?.enrollPcm(name, pcm)
        speak("I'll remember you as ${EnrollPhraseParser.displayName(name)}")
    }

    private fun persistOwnerName(name: String) {
        val storage = secureStorage ?: return
        if (!storage.getPreference(KEY_OWNER_SPEAKER).isNullOrBlank()) return
        storage.savePreference(KEY_OWNER_SPEAKER, name)
    }

    private fun rememberOwnerName(name: String) {
        if (_uiState.value.memoryPaused) return
        val shown = EnrollPhraseParser.displayName(name)
        viewModelScope.launch {
            memoryRepository?.addMemory(
                content = "The user's name is $shown.",
                type = MemoryType.SEMANTIC,
                importance = ImportanceLevel.HIGH,
                tags = listOf("identity", "name"),
            )
        }
    }

    private fun ownerSpeakerName(): String? =
        secureStorage?.getPreference(KEY_OWNER_SPEAKER)?.trim()?.takeIf { it.isNotEmpty() }
            ?: lastSpeakerName

    private fun maybeAskNewPerson(now: Long) {
        val faces = visionManager?.lastFaces.orEmpty()
        val ids = faces.map { it.trackingId }.filter { it >= 0 }
        val areas = faces.associate { it.trackingId to it.area }
        faceFirstSeen.keys.filter { it !in ids }.forEach { faceFirstSeen.remove(it) }
        ids.forEach { id -> if (id !in faceFirstSeen) faceFirstSeen[id] = now }
        if (awaitingNameFaceId != null && now > awaitingNameUntilMs) {
            awaitingNameFaceId = null
        }
        NewPersonAsk.rebindOwner(
            faceIds = ids,
            namedIds = faceSpeaker.keys.toSet(),
            ownerName = ownerSpeakerName(),
            faceAreas = areas,
        )?.let { (id, name) -> bindSpeaker(name, id) }
        val st = _uiState.value
        val busy = st.interactionState == InteractionState.SPEAKING ||
            st.interactionState == InteractionState.THINKING ||
            st.interactionState == InteractionState.MUTED ||
            st.isMuted
        val askId = NewPersonAsk.pickFaceToAsk(
            faceIds = ids,
            namedIds = faceSpeaker.keys.toSet(),
            askedIds = askedNameIds,
            firstSeenAt = faceFirstSeen,
            nowMs = now,
            lastAskAtMs = lastNameAskAtMs,
            busy = busy,
            alreadyAwaiting = awaitingNameFaceId != null,
            hasKnownPeople = ownerSpeakerName() != null,
            faceAreas = areas,
        ) ?: return
        askedNameIds += askId
        awaitingNameFaceId = askId
        awaitingNameUntilMs = now + NewPersonAsk.AWAIT_MS
        lastNameAskAtMs = now
        speak("Hey — I don't think we've met. What's your name?")
    }

    private fun projectWhenLabel(at: Long): String {
        val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
        return fmt.format(java.util.Date(at))
    }

    fun toggleVaultScreen() {
        _uiState.value = _uiState.value.copy(showVaultScreen = !_uiState.value.showVaultScreen)
    }

    /** User decision on the pending tool approval card. */
    fun resolveApproval(approved: Boolean) {
        agentOrchestrator?.resolveApproval(approved)
    }

    fun cancelTask(taskId: String) {
        agentOrchestrator?.cancel(taskId)
    }

    /** On-device agent brain toggle (persisted). */
    fun setAgentBrainEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(agentBrainEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_AGENT_BRAIN, enabled)
    }

    /** Hermes Agent endpoint (persisted). Blank means on-device brain only. */
    fun setHermesAgentUrl(url: String) {
        val clean = url.trim()
        CleartextPolicy.denyReason(clean)?.let { reason ->
            _uiState.value = _uiState.value.copy(errorMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(hermesAgentUrl = clean)
        secureStorage?.savePreference(KEY_HERMES_URL, clean)
        rebuildHermesBrain()
    }

    fun setImageServerUrl(url: String) {
        // Strip trailing slashes too — otherwise a URL like "http://host/" produces
        // "http://host//generate" when RemoteImageGenerator appends "/generate".
        val clean = url.trim().trimEnd('/')
        CleartextPolicy.denyReason(clean)?.let { reason ->
            _uiState.value = _uiState.value.copy(errorMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(imageServerUrl = clean)
        secureStorage?.savePreference(KEY_IMAGE_SERVER_URL, clean)
    }

    fun setPreferServerImageGen(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(preferServerImageGen = enabled)
        secureStorage?.saveBooleanPreference(KEY_PREFER_SERVER_IMAGE_GEN, enabled)
    }

    /** meow-mem0 semantic memory server. "" disables sync. */
    fun setMem0ServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        CleartextPolicy.denyReason(clean)?.let { reason ->
            _uiState.value = _uiState.value.copy(errorMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(mem0ServerUrl = clean)
        secureStorage?.savePreference(KEY_MEM0_SERVER_URL, clean)
        refreshMem0Link()
    }

    /** Linked only when the mem0 server actually answered /health. */
    private fun refreshMem0Link() {
        viewModelScope.launch {
            val linked = runCatching { mem0.isAvailable() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(mem0Linked = linked)
        }
    }

    // ── Avatar ───────────────────────────────────────────────────────────────

    /**
     * Clips the companion invented, on disk. Same directory the animate tool
     * writes to, so anything it made shows up on the Avatar card.
     */
    private val avatarAnimationStore: CustomAnimationStore? by lazy {
        secureStorage?.let { CustomAnimationStore(File(it.context.filesDir, "custom_animations")) }
    }

    /** Which face to draw. Persisted, so it survives a restart. */
    fun setAvatarStyle(style: AvatarStyle) {
        _uiState.value = _uiState.value.copy(avatarStyle = style)
        secureStorage?.savePreference(KEY_AVATAR_STYLE, style.key)
    }

    fun setAvatarEyeColor(argb: Long) {
        _uiState.value = _uiState.value.copy(avatarEyeColor = argb)
        secureStorage?.savePreference(KEY_AVATAR_EYE_COLOR, AvatarColors.toPref(argb))
    }

    fun setAvatarGlowColor(argb: Long) {
        _uiState.value = _uiState.value.copy(avatarGlowColor = argb)
        secureStorage?.savePreference(KEY_AVATAR_GLOW_COLOR, AvatarColors.toPref(argb))
    }

    fun toggleAvatarScreen() {
        _uiState.value = _uiState.value.copy(showAvatarScreen = !_uiState.value.showAvatarScreen)
    }

    /** Channel expressions (Godot port) first, then keyframe built-ins, then saved. */
    fun avatarAnimationNames(): List<String> =
        (CatExpressions.names() +
            CustomAnimations.BUILT_INS.keys.toList() +
            (avatarAnimationStore?.list() ?: emptyList())).distinct()

    /**
     * Plays a clip by name: channel composition when known, else keyframe
     * built-in/saved. [userRequested] passes through to panel dismissal — see
     * [playCustomAnimation].
     */
    fun playAnimationNamed(name: String, userRequested: Boolean = true) {
        CatExpressions.resolve(name)?.let {
            playComposedExpression(it, name, userRequested)
            return
        }
        val spec = CustomAnimations.builtIn(name) ?: avatarAnimationStore?.load(name)
        if (spec != null) playCustomAnimation(spec, userRequested)
    }

    /**
     * Drive the face with the Godot-style channel motion engine.
     * Closes panels like [playCustomAnimation] so the performance is visible.
     */
    fun playComposedExpression(
        expr: ComposedExpression,
        label: String = "composed",
        userRequested: Boolean = false,
    ) {
        customAnimJob?.cancel()
        System.err.println(
            "MeowAnim: composed \"$label\" " +
                "(${expr.eye}/${expr.ear}/${expr.jaw}/${expr.body}, ${expr.durationSeconds}s)",
        )
        val next = _uiState.value.copy(
            activeComposed = expr,
            activeAnimation = null,
        )
        _uiState.value = if (userRequested) next.withPanelsClosed() else next
        // The body feels the same emotion, on the same clock as the face.
        bodyScheduler.play(
            com.example.meowmba.core.robot.EmotionMap.cues(expr, confinement.profile)
        )
        customAnimJob = viewModelScope.launch {
            delay((expr.durationSeconds * 1000f).toLong() + 350L)
            _uiState.value = _uiState.value.copy(activeComposed = null)
        }
    }

    // ── Car assistant ────────────────────────────────────────────────────────

    /** Master switch: starts/stops the driving detector (sensors + GPS). */
    fun setCarModeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(carModeEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_CAR_MODE, enabled)
        carDetector?.setCarModeEnabled(enabled)
    }

    /** Voice/UI drive-mode override. The tool's reply is what gets spoken. */
    fun setManualDriving(on: Boolean) {
        carDetector?.setManualDriving(on)
    }

    /** Emergency contact number used by the car.help tool. */
    fun setEmergencyContact(number: String) {
        val clean = number.trim()
        _uiState.value = _uiState.value.copy(emergencyContact = clean)
        secureStorage?.savePreference(KEY_EMERGENCY_CONTACT, clean)
    }

    /** MAC address of the user's car hands-free unit (a BT-connected-device cue). */
    fun setCarDeviceAddress(address: String) {
        val clean = address.trim()
        carDetector?.setCarDeviceAddress(clean)
        secureStorage?.savePreference(KEY_CAR_DEVICE, clean)
    }

    /** Dismiss the crash alert card. */
    fun dismissCrashAlert() {
        _uiState.value = _uiState.value.copy(crashAlert = null)
    }

    /** Hide the drive HUD for the rest of this trip. */
    fun dismissDriveCard() {
        driveCardSuppressed = true
        _uiState.value = _uiState.value.copy(driveCardVisible = false)
    }

    /**
     * Quick "text my contact" from the crash card — same flow as the car.help tool,
     * minus the agent. Tells the user (out loud) if no contact is configured yet.
     */
    fun sendEmergencyText() {
        val number = _uiState.value.emergencyContact.trim()
        if (number.isBlank()) {
            speak("I don't have an emergency contact yet. Add one in Settings, under Car assistant.")
            return
        }
        val context = secureStorage?.context
        if (context == null) {
            speak("I can't reach the messaging app right now.")
            return
        }
        try {
            val intent = com.example.meowmba.core.agent.tools.buildEmergencyTextIntent(
                context, number, carTripTracker?.trip?.value,
            )
            context.startActivity(intent)
            speak("Opened a text to $number with your location. Press send when you're ready.")
        } catch (e: Exception) {
            speak("I couldn't open the messaging app. Ask me to call for help instead.")
        }
    }

    /** Debug-only: run the full crash path (camera check + alert) on demand. */
    fun simulateCrash(g: Float = 8.4f, mph: Float = 52f) {
        onCrashDetected(
            com.example.meowmba.core.car.CrashEvent(
                detectedAtMs = System.currentTimeMillis(),
                speedBeforeMph = mph,
                gForce = g,
            ),
        )
    }

    /** A possible collision was detected: surface it loudly, and offer help. */
    private fun onCrashDetected(crash: CrashEvent) {
        val g = String.format(java.util.Locale.US, "%.1f", crash.gForce)
        val mph = String.format(java.util.Locale.US, "%.0f", crash.speedBeforeMph)
        android.util.Log.i("MeowCrash", "onCrashDetected g=$g mph=$mph")
        val vm = visionManager
        if (vm == null || !vm.hasPermission()) {
            // No camera to check with — alert honestly rather than risk silence.
            android.util.Log.i("MeowCrash", "no camera/permission -> alert null")
            raiseCrashAlert(g, mph, verifiedDanger = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                crashAlert = "Hard impact felt — checking the camera…",
                emotion = CompanionEmotion.ALERT,
            )
            // Look at the scene BEFORE alarming: a bump must not call mom. The whole
            // verify block is bounded so that no CameraX or network stall can ever
            // swallow the alert — worst case we hit 30s and alert with "couldn't check".
            val description = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                vm.captureJpeg(768)
                    .mapCatching { jpeg ->
                        visionProviderFor()?.describe(jpeg, DANGER_VERIFY_PROMPT)
                    }
                    .getOrNull()
                    ?.getOrNull()
            }
            android.util.Log.i("MeowCrash", "verify done description=${description?.take(80) ?: "null"}")
            raiseCrashAlert(g, mph, verifiedDanger = classifyCrashDanger(description))
        }
    }

    /** Full alarm, downgraded check-in, or honest "couldn't check" alert. */
    private fun raiseCrashAlert(g: String, mph: String, verifiedDanger: Boolean?) {
        android.util.Log.i("MeowCrash", "raiseCrashAlert g=$g mph=$mph verifiedDanger=$verifiedDanger")
        when (verifiedDanger) {
            true -> {
                _uiState.value = _uiState.value.copy(
                    crashAlert = "Possible crash detected — hard impact at $g g, speed before it was $mph mph.",
                    emotion = CompanionEmotion.ALERT,
                )
                speak("Are you okay? I felt a hard impact and the camera shows what looks like a crash scene. Say \"text mom\" and I'll send your location to your emergency contact.")
            }
            false -> {
                _uiState.value = _uiState.value.copy(
                    crashAlert = "I felt a hard jolt at $g g, but the camera doesn't show an obvious crash scene.",
                    emotion = CompanionEmotion.ALERT,
                )
                speak("I felt a hard jolt, but the camera doesn't show an obvious crash scene. Are you okay? Say \"text mom\" if you need me to send your location.")
            }
            null -> {
                _uiState.value = _uiState.value.copy(
                    crashAlert = "Possible crash detected — hard impact at $g g, speed before it was $mph mph. I couldn't check the camera to confirm.",
                    emotion = CompanionEmotion.ALERT,
                )
                speak("Are you okay? I felt a hard impact but couldn't see the camera to check. Say \"text mom\" and I'll send your location to your emergency contact.")
            }
        }
    }

    private fun classifyCrashDanger(description: String?): Boolean? {
        if (description == null) return null
        val d = description.lowercase()
        val danger = listOf(
            "danger", "crash", "wreck", "accident", "debris", "glass", "smoke",
            "fire", "flam", "injured", "blood", "flipped", "overturn", "collision",
            "airbag", "shattered", "damaged", "bent", "emergency", "scene",
        )
        val safe = listOf(
            "safe", "no danger", "no sign", "no obvious", "normal", "nothing",
            "cannot", "can't", "unable", "not see", "clear", "empty",
        )
        val isDanger = danger.any { d.contains(it) }
        return when {
            isDanger -> true
            safe.any { d.contains(it) } -> false
            // The camera looked and found nothing alarming — don't false-alarm.
            else -> false
        }
    }

    fun setMusicServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        CleartextPolicy.denyReason(clean)?.let { reason ->
            _uiState.value = _uiState.value.copy(errorMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(musicServerUrl = clean)
        secureStorage?.savePreference(KEY_MUSIC_SERVER_URL, clean)
    }

    fun setVideoServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        CleartextPolicy.denyReason(clean)?.let { reason ->
            _uiState.value = _uiState.value.copy(errorMessage = reason)
            return
        }
        _uiState.value = _uiState.value.copy(videoServerUrl = clean)
        secureStorage?.savePreference(KEY_VIDEO_SERVER_URL, clean)
    }

    /** Background wake-phrase listening toggle (persisted; service runs via MainActivity). */
    fun setBackgroundListeningEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(backgroundListeningEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_BACKGROUND_LISTENING, enabled)
    }

    /**
     * A command captured by the background wake service: routed through the
     * exact same pipeline as an in-app utterance.
     */
    fun onExternalWakeCommand(command: String) {
        noteMoodEvent(com.example.meowmba.core.personality.MoodEvent.WakeGreeting)
        if (command.isBlank()) return
        stopWakeLoopAndRelease()
        processUserSpeech(command)
    }

    private fun rebuildHermesBrain() {
        hermesBrain = _uiState.value.hermesAgentUrl
            .takeIf { it.isNotBlank() }
            ?.let { HermesAgentBrain(HermesConfig(it), dispatcher = ioDispatcher) }
    }

    // ── Memory screen & controls ────────────────────────────────────────────

    /**
     * Summons the weather card from the tile menu: fetches real conditions, and if that is
     * not possible (no location, no network) still shows a preview so the card and its
     * animation are reachable rather than silently absent.
     */
    fun showWeatherCard() {
        val repo = weatherRepository ?: run { cycleForcedWeatherCondition(); return }
        viewModelScope.launch {
            when (repo.fetch()) {
                is com.example.meowmba.core.weather.WeatherRepository.Result.Ok -> Unit
                is com.example.meowmba.core.weather.WeatherRepository.Result.Failed -> {
                    android.util.Log.i("Weather", "fetch failed; showing preview card")
                    cycleForcedWeatherCondition()
                }
            }
        }
    }

    /**
     * Debug preview: steps the card and scene through every weather condition.
     *
     * TEMPORARY — a way to see all the animations without waiting for the sky to oblige.
     * Synthesises a reading when nothing has been fetched, so it works with no network and
     * no location. The next real fetch overwrites it.
     */
    fun cycleForcedWeatherCondition() {
        val conditions = com.example.meowmba.core.weather.WeatherCondition.entries
        val current = _uiState.value.weather
        val next = if (current == null) {
            conditions.first()
        } else {
            conditions[(conditions.indexOf(current.condition) + 1) % conditions.size]
        }
        val base = current ?: com.example.meowmba.core.weather.WeatherNow(
            condition = next, temperatureF = 72, feelsLikeF = 72,
            highF = 84, lowF = 61, humidityPct = 38, windMph = 7,
            isDay = true, place = "Preview",
        )
        android.util.Log.i("Weather", "forced condition preview: $next")
        _uiState.value = _uiState.value.copy(
            weather = base.copy(condition = next),
            emotion = runCatching {
                CompanionEmotion.valueOf(base.copy(condition = next).catEmotionName())
            }.getOrDefault(_uiState.value.emotion),
        )
    }

    /**
     * Opens the Weather page and refreshes the reading behind it.
     *
     * The fetch surfaces normally, so the card is also there when the page is closed —
     * the page is where you configure the weather, the card is where you glance at it.
     */
    fun toggleWeatherScreen() {
        val opening = !_uiState.value.showWeatherScreen
        _uiState.value = _uiState.value.copy(showWeatherScreen = opening)
        if (opening) refreshWeather()
    }

    /** Fetches current conditions; silent on failure, the page says so itself. */
    fun refreshWeather() {
        val repo = weatherRepository ?: return
        viewModelScope.launch { repo.fetch() }
    }

    /** Sets (or clears, with null) the forced time of day used by the scene. */
    fun setPreviewTimeOfDay(tod: com.example.meowmba.core.weather.TimeOfDay?) {
        _uiState.value = _uiState.value.copy(previewTimeOfDay = tod)
    }

    /**
     * Steps the scene's forced time of day: Auto → Dawn → Day → Dusk → Night → Auto.
     *
     * Not persisted. It is a way to look at the sky you are not currently standing under,
     * and a forced midday that survived a restart would just look like a bug.
     */
    fun cyclePreviewTimeOfDay() {
        val order = com.example.meowmba.core.weather.TimeOfDay.entries
        val current = _uiState.value.previewTimeOfDay
        val next = when {
            current == null -> order.first()
            current == order.last() -> null
            else -> order[order.indexOf(current) + 1]
        }
        _uiState.value = _uiState.value.copy(previewTimeOfDay = next)
    }

    /** Dismisses the weather card. The ambient scene, if on, keeps playing. */
    fun dismissWeatherCard() {
        weatherRepository?.clear()
    }

    // ── Ambient weather ─────────────────────────────────────────────────────

    /**
     * Keeps the weather animation on screen permanently, matching the real sky (persisted).
     *
     * Switching it on fetches immediately so the change is visible now rather than at the
     * next quarter hour — a setting that appears to do nothing for fifteen minutes reads as
     * a broken setting.
     */
    fun setAmbientWeatherEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(ambientWeatherEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_AMBIENT_WEATHER, enabled)
        if (enabled) {
            startAmbientWeather()
        } else if (_uiState.value.background.kind !=
            com.example.meowmba.core.background.BackgroundKind.LIVE_WEATHER
        ) {
            // The live-weather background needs the same feed, so only stop when nothing
            // else is using it.
            ambientWeatherJob?.cancel()
            ambientWeatherJob = null
        }
    }

    /**
     * Refreshes the ambient sky now and every [AMBIENT_WEATHER_INTERVAL_MS].
     *
     * Fetches with `surface = false`: the card must not reopen itself every quarter hour.
     * A failed fetch keeps the last good reading rather than blanking the scene — stale
     * weather looks better than no weather, and the sky rarely changes in one interval.
     */
    private fun startAmbientWeather() {
        if (ambientWeatherJob?.isActive == true) return
        val repo = weatherRepository ?: return
        ambientWeatherJob = viewModelScope.launch {
            while (true) {
                // No network while backgrounded: the sky isn't visible and the
                // 15-min tick kept the radio/CPU warm overnight for nothing.
                // The next foreground fetch refreshes it.
                if (!appInBackground) {
                    when (val r = repo.fetch(surface = false)) {
                        is com.example.meowmba.core.weather.WeatherRepository.Result.Ok ->
                            _uiState.value = _uiState.value.copy(ambientWeather = r.weather)
                        is com.example.meowmba.core.weather.WeatherRepository.Result.Failed ->
                            android.util.Log.i("Weather", "ambient refresh failed: ${r.reason}")
                    }
                }
                kotlinx.coroutines.delay(AMBIENT_WEATHER_INTERVAL_MS)
            }
        }
    }

    /**
     * What is actually answering, in one spoken sentence.
     *
     * Built from state rather than from the model, so it is true even when the model
     * that would have answered is the thing that is broken.
     */
    private fun describeSelf(
        question: com.example.meowmba.core.agent.SelfStatusParser.Question,
    ): String {
        val s = _uiState.value
        val loaded = s.loadedModels
        val route = omniRoute?.lastRoute?.value
        val onDevice = loaded.localName.takeIf { it.isNotBlank() && it != "none" }

        return when (question) {
            com.example.meowmba.core.agent.SelfStatusParser.Question.MODEL -> buildString {
                if (s.selectedModel == MODEL_OMNIROUTE) {
                    append("Omniroute is picking for me, on the phone. ")
                    append(
                        route?.let { "That last answer came from $it" }
                            ?: "Nothing has been routed yet this session"
                    )
                    onDevice?.let { if (route == null) append(", and on-device is $it") }
                    append(".")
                } else {
                    append("I'm on ${s.selectedModel}")
                    onDevice?.takeIf { s.loadedModels.activeSource == ModelSource.LOCAL }
                        ?.let { append(", running $it") }
                    append(".")
                }
            }

            com.example.meowmba.core.agent.SelfStatusParser.Question.WHERE -> buildString {
                val where = when {
                    s.selectedModel == MODEL_OMNIROUTE && route != null -> route
                    loaded.activeSource == ModelSource.LOCAL ->
                        "on this phone" + (onDevice?.let { " ($it)" } ?: "")
                    loaded.activeSource == ModelSource.NETWORK ->
                        "on your network, at ${loaded.networkEndpoint}"
                    else -> "in the cloud, on ${loaded.cloudProvider}"
                }
                append("Right now: $where.")
                if (s.selectedModel == MODEL_OMNIROUTE) {
                    append(" Omniroute decides per question, and falls back to the ")
                    append("on-device model when nothing else answers.")
                }
            }
        }
    }

    // ── Agent follow-through ────────────────────────────────────────────────

    /**
     * Keeps the user informed while an agent works.
     *
     * Handing a job to an agent used to be the last the user heard of it until it
     * finished speaking a result — the task list existed, but you had to go looking for
     * it. Every transition now goes two places at once:
     *
     *  - the live panel, raised the moment an agent picks up work; and
     *  - the chat, as a SYSTEM line, which is what remains after the panel is swiped
     *    away (and what is still there tomorrow, since chat is persisted and the task
     *    list is not).
     *
     * Only transitions are written. Re-emitting the same state on every list update
     * would bury the conversation in duplicates.
     */
    private fun narrateAgentProgress(tasks: List<AgentTask>) {
        var raisePanel = false
        for (task in tasks) {
            val previous = narratedTaskStatus[task.id]
            if (previous == task.status) continue
            narratedTaskStatus[task.id] = task.status
            val agent = AgentRegistry.byId(task.agentId)?.name ?: task.agentId
            // Deliberately quiet where the companion already speaks. Submitting says
            // "Shelve is on it", finishing speaks the result out loud, and both of those
            // land in the chat too — so narrating them again turns one event into three
            // lines that say the same thing.
            val line = when (task.status) {
                TaskStatus.QUEUED -> null
                TaskStatus.RUNNING ->
                    if (previous == TaskStatus.WAITING_APPROVAL) {
                        "$agent got the go-ahead: ${task.title}"
                    } else {
                        null
                    }
                TaskStatus.WAITING_APPROVAL -> "$agent needs your OK for: ${task.title}"
                TaskStatus.COMPLETED -> "$agent finished: ${task.title}"
                TaskStatus.FAILED -> "$agent couldn't finish: ${task.title}"
                TaskStatus.CANCELLED -> "$agent stopped: ${task.title}"
            }
            // Raised for any task the panel has not shown yet, terminal or not: a
            // file write finishes in milliseconds, and "the agent panel only appears
            // for slow work" is indistinguishable from "the panel is broken".
            if (previous == null || !task.isTerminal) raisePanel = true
            line?.let { text ->
                viewModelScope.launch {
                    runCatching { persistTurn(MessageRole.SYSTEM, text) }
                }
            }
        }
        // Forget tasks that are done and gone, so the map cannot grow forever.
        val live = tasks.map { it.id }.toSet()
        narratedTaskStatus.keys.retainAll(live)

        val anyLive = tasks.any { !it.isTerminal }
        if (raisePanel) {
            _uiState.value = _uiState.value.copy(agentActivityVisible = true)
        }
        // Everything finished: leave the outcome up for a moment, then get out of the
        // way. Hiding it the instant the last task completes means the only thing the
        // panel ever shows is "running", never the result.
        agentPanelLingerJob?.cancel()
        if (!anyLive && _uiState.value.agentActivityVisible) {
            agentPanelLingerJob = viewModelScope.launch {
                delay(AGENT_PANEL_LINGER_MS)
                if (_uiState.value.activeTasks.none { !it.isTerminal }) {
                    _uiState.value = _uiState.value.copy(agentActivityVisible = false)
                }
            }
        }
    }

    /** Swiped away: the panel goes, the chat keeps narrating. */
    fun dismissAgentActivity() {
        _uiState.value = _uiState.value.copy(agentActivityVisible = false)
    }

    /** Opens one agent's history, or clears the selection with null. */
    fun selectAgent(agentId: String?) {
        _uiState.value = _uiState.value.copy(selectedAgentId = agentId)
    }

    /** Opens the Agents screen on a specific agent (tapping the live panel). */
    fun openAgentDetail(agentId: String) {
        _uiState.value = _uiState.value.copy(
            showAgentsScreen = true,
            selectedAgentId = agentId,
            agentActivityVisible = false,
        )
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    /**
     * Answers a parsed inventory request out loud, and puts the item on screen.
     *
     * The shape of every branch is the same on purpose: say the short answer, show the
     * record. Showing it is what makes the next turn work — "sell it" has an obvious
     * subject once the thing is on screen, and the user can see the companion picked the
     * right one before anything is changed.
     *
     * Nothing destructive happens here. Selling and deleting go through the card's own
     * buttons (or the approval-gated tools), because a mishear must never be able to
     * delete a record of something the user owns.
     */
    private suspend fun answerInventory(
        request: com.example.meowmba.core.inventory.InventoryRequestParser.Request,
    ) {
        val client = inventoryClient ?: run {
            speak("I can't reach Meow Inventory in this build.")
            return
        }
        if (!client.isAvailable()) {
            speak("Meow Inventory isn't installed on this phone.")
            return
        }
        val json = org.json.JSONObject()

        suspend fun run(op: String, args: org.json.JSONObject): String? =
            when (val r = client.op(op, args)) {
                is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Ok -> r.json
                is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Failed -> {
                    speak(r.reason)
                    null
                }
            }

        when (request) {
            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Open -> {
                openInventoryApp(null)
                speak("Opening Meow Inventory.")
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Stats -> {
                val body = run("stats", json) ?: return
                val j = org.json.JSONObject(body)
                val value = j.optDouble("estimated_value", 0.0)
                speak(
                    buildString {
                        append("You've got ${j.optInt("total_items")} things logged")
                        val forSale = j.optInt("for_sale")
                        if (forSale > 0) append(", $forSale of them for sale")
                        if (value > 0) {
                            append(", worth about ")
                            append(
                                com.example.meowmba.core.inventory.InventoryItem
                                    .trimNumber(value)
                            )
                            append(" dollars")
                        }
                        append(".")
                    }
                )
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Search -> {
                val body = run(
                    "search",
                    json.put("query", request.query).put("filter", request.filter),
                ) ?: return
                val items = com.example.meowmba.core.inventory.InventoryItem.listFrom(body)
                when {
                    items.isEmpty() -> speak(
                        if (request.query.isBlank()) "There's nothing in your inventory yet."
                        else "I don't see ${request.query} in your inventory."
                    )
                    // One hit: show it and offer the next move, which is the whole point of
                    // pulling the item up rather than reading its fields aloud.
                    items.size == 1 -> {
                        client.show(items.first())
                        speak("${items.first().summary()}. What do you want to do with it?")
                    }
                    else -> {
                        client.show(items.first())
                        speak(
                            "You've got ${items.size}: " +
                                items.take(4).joinToString(", ") { it.name } +
                                if (items.size > 4) ", and more." else "."
                        )
                    }
                }
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Add -> {
                val body = run("create", json.put("name", request.name)) ?: return
                val item = com.example.meowmba.core.inventory.InventoryItem
                    .from(org.json.JSONObject(body))
                client.show(item)
                speak("Added ${item.name}. Want to set a price or a category?")
            }

            // The rest are changes: find the item, show it, and let the user commit. The
            // card's buttons do the write.
            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.ListForSale -> {
                val item = findAndShow(client, request.name) ?: return
                if (request.price != null) {
                    val body = run(
                        "list_for_sale",
                        org.json.JSONObject().put("id", item.id).put("price", request.price),
                    ) ?: return
                    client.show(
                        com.example.meowmba.core.inventory.InventoryItem
                            .from(org.json.JSONObject(body))
                    )
                    speak(
                        "Listed ${item.name} at " +
                            com.example.meowmba.core.inventory.InventoryItem
                                .trimNumber(request.price) + " dollars."
                    )
                } else {
                    speak("Here's ${item.name}. Tap Sell to set an asking price.")
                }
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.MarkSold -> {
                val item = findAndShow(client, request.name) ?: return
                val args = org.json.JSONObject().put("id", item.id)
                request.price?.let { args.put("price", it) }
                val body = run("mark_sold", args) ?: return
                client.show(
                    com.example.meowmba.core.inventory.InventoryItem
                        .from(org.json.JSONObject(body))
                )
                speak("Marked ${item.name} as sold.")
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Rename -> {
                val item = findAndShow(client, request.name) ?: return
                val body = run(
                    "update",
                    org.json.JSONObject().put("id", item.id).put("new_name", request.newName),
                ) ?: return
                client.show(
                    com.example.meowmba.core.inventory.InventoryItem
                        .from(org.json.JSONObject(body))
                )
                speak("Renamed it to ${request.newName}.")
            }

            is com.example.meowmba.core.inventory.InventoryRequestParser.Request.Delete -> {
                val item = findAndShow(client, request.name) ?: return
                // Shown, not deleted: confirmation is a tap on the card, never a
                // transcript the recognizer might have got wrong.
                speak("This is ${item.name}. Tap Delete on the card if you're sure.")
            }
        }
    }

    /** Resolves a spoken name to one item and puts it on screen, or explains the miss. */
    private suspend fun findAndShow(
        client: com.example.meowmba.core.inventory.InventoryBridgeClient,
        name: String,
    ): com.example.meowmba.core.inventory.InventoryItem? {
        val result = client.op("get", org.json.JSONObject().put("name", name))
        return when (result) {
            is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Failed -> {
                speak("I don't see $name in your inventory.")
                null
            }
            is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Ok -> {
                val item = com.example.meowmba.core.inventory.InventoryItem
                    .from(org.json.JSONObject(result.json))
                client.show(item)
                item
            }
        }
    }

    /**
     * Runs one inventory operation from the item card's buttons.
     *
     * The card talks to the bridge directly rather than through the tool layer: these are
     * the user's own taps, already unambiguous about which item they mean, so routing them
     * back through model-facing tools and an approval prompt would only add a dialog
     * between a button and the thing it says it does.
     */
    fun runInventoryAction(op: String, args: Map<String, String>) {
        val client = inventoryClient ?: return
        viewModelScope.launch {
            val payload = org.json.JSONObject().apply {
                args.forEach { (k, v) -> put(k, v) }
            }
            val result = if (op == "delete") client.op(op, payload)
            else client.itemOp(op, payload)
            when (result) {
                is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Failed ->
                    _uiState.value = _uiState.value.copy(captionText = result.reason)
                is com.example.meowmba.core.inventory.InventoryBridgeClient.Result.Ok ->
                    if (op == "delete") {
                        client.show(null)
                        speak("Deleted.")
                    }
            }
        }
    }

    fun dismissInventoryCard() {
        inventoryClient?.show(null)
    }

    /** Opens Meow Inventory, on the given item when the app can deep-link to it. */
    fun openInventoryApp(name: String?) {
        val ctx = secureStorage?.context ?: return
        val intent = ctx.packageManager
            .getLaunchIntentForPackage(com.example.meowmba.core.inventory.InventoryBridgeClient.PKG)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: run {
                _uiState.value = _uiState.value.copy(
                    captionText = "Meow Inventory isn't installed on this phone.",
                )
                return
            }
        name?.takeIf { it.isNotBlank() }?.let {
            intent.data = android.net.Uri.parse(
                "meowinventory://item?name=${android.net.Uri.encode(it)}"
            )
        }
        runCatching { ctx.startActivity(intent) }
    }

    // ── Background ──────────────────────────────────────────────────────────

    fun toggleBackgroundScreen() {
        _uiState.value = _uiState.value.copy(
            showBackgroundScreen = !_uiState.value.showBackgroundScreen
        )
    }

    /** Applies and persists a background choice. */
    fun setBackground(selection: com.example.meowmba.core.background.BackgroundSelection) {
        _uiState.value = _uiState.value.copy(background = selection)
        secureStorage?.let {
            it.savePreference(KEY_BACKGROUND_KIND, selection.kind.name)
            it.savePreference(KEY_BACKGROUND_VALUE, selection.value)
            it.savePreference(KEY_BACKGROUND_DIM, selection.dimPercent.toString())
        }
        if (selection.kind == com.example.meowmba.core.background.BackgroundKind.LIVE_WEATHER) {
            startAmbientWeather()
        } else if (!_uiState.value.ambientWeatherEnabled) {
            ambientWeatherJob?.cancel()
            ambientWeatherJob = null
        }
    }

    fun toggleNavScreen() {
        _uiState.value = _uiState.value.copy(showNavScreen = !_uiState.value.showNavScreen)
    }

    fun toggleMemoryScreen() {
        val opening = !_uiState.value.showMemoryScreen
        _uiState.value = _uiState.value.copy(showMemoryScreen = opening)
        if (opening) {
            refreshMetaMemoryLink()
            refreshMem0Link()
        }
    }

    fun toggleStudioScreen() {
        val opening = !_uiState.value.showStudioScreen
        _uiState.value = _uiState.value.copy(showStudioScreen = opening)
        if (opening) {
            // Fresh reads each open so comics/scripts saved by tools since the
            // last visit show up without an app restart.
            _studioArtifacts.value = listStudioArtifacts()
        }
    }

    /** Saved recurring-character sheets, observed live from Room. */
    val studioCharacters: kotlinx.coroutines.flow.Flow<List<com.example.meowmba.core.database.CharacterEntity>> =
        characterDao?.getAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private val _studioArtifacts = MutableStateFlow<List<File>>(emptyList())
    val studioArtifacts = _studioArtifacts.asStateFlow()

    /** Comic/script/ad pages in the workspace, newest first (capped for scroll). */
    private fun listStudioArtifacts(): List<File> =
        workspaceRoot?.let { root ->
            listOf("comics", "scripts", "ads", "posts").flatMap { dir ->
                File(root, dir).listFiles()?.toList() ?: emptyList()
            }.filter { it.isFile && it.extension.lowercase() == "html" }
                .sortedByDescending { it.lastModified() }
                .take(30)
        } ?: emptyList()

    /**
     * Clears every full-screen panel so the face is visible.
     *
     * Only ever called for an animation the USER asked for. Every panel (chat
     * sheet, tiles, Settings, Model Manager, Privacy, Memory, Weather,
     * Background, Nav, Agents, Vault) is a later child than AvatarRenderer in
     * CompanionScreen's Box, so any one of them left open paints over the face:
     * without this, "show me a trick" plays to a covered screen.
     */
    private fun CompanionState.withPanelsClosed() = copy(
        isChatExpanded = false,
        showTiles = false,
        showSettings = false,
        showModelManager = false,
        showPrivacyDashboard = false,
        showMemoryScreen = false,
        showStudioScreen = false,
        showWeatherScreen = false,
        showBackgroundScreen = false,
        showNavScreen = false,
        showAgentsScreen = false,
        showProjectsScreen = false,
        showToolsScreen = false,
        showConnectorsScreen = false,
        showVaultScreen = false,
        showBrowser = false,
        showAvatarScreen = false,
    )

    /**
     * Plays an invented clip over the face, then clears it. A new clip replaces
     * the old — the face is one screen, and the renderer blends the handoff.
     *
     * [userRequested] decides whether panels are dismissed to reveal the face.
     * It defaults to FALSE, because getting this backwards was a real bug: idle
     * performances also came through here and closed whatever the user had
     * open, so Settings (or chat, or the vault) shut itself every time the cat
     * decided to stretch. An ambient performance is never worth interrupting
     * the screen the user is reading; only "show me a trick" is.
     */
    fun playCustomAnimation(spec: CustomAnimationSpec, userRequested: Boolean = false) {
        customAnimJob?.cancel()
        System.err.println("MeowAnim: playing \"${spec.name}\" (${spec.durationMs}ms)")
        val next = _uiState.value.copy(
            activeAnimation = spec,
            activeComposed = null,
        )
        _uiState.value = if (userRequested) next.withPanelsClosed() else next
        // Body starts from the same instant the face is set, so they cannot drift.
        bodyScheduler.play(
            com.example.meowmba.core.robot.BodyMotionMap.cues(spec, confinement.profile)
        )
        if (userRequested) {
            val html = "<!doctype html><html><body style='margin:0;background:#0d1117;color:#e6edf3;font-family:system-ui;padding:24px'>" +
                "<h1 style='margin:0 0 8px'>${spec.name}</h1>" +
                "<p style='color:#8b949e'>Playing on the face · ${spec.durationMs}ms</p>" +
                "<pre style='white-space:pre-wrap;color:#8b949e'>${spec.durationMs} ms clip</pre></body></html>"
            if (!_uiState.value.showBrowser) toggleBrowser()
            _uiState.value = _uiState.value.copy(
                browserGoal = "Animation: ${spec.name}",
                browserSource = html,
                browserSourceKind = "html",
            )
            viewModelScope.launch {
                waitForBrowserAttached()
                com.example.meowmba.core.browser.WebViewBridge.loadHtml(html)
            }
        }
        customAnimJob = viewModelScope.launch {
            delay(spec.durationMs.toLong() + 300)
            _uiState.value = _uiState.value.copy(activeAnimation = null)
        }
    }

    fun dismissPoliceAlert() {
        _uiState.value = _uiState.value.copy(policeAlert = null)
        com.example.meowmba.core.alerts.RoadAlertBus.clear()
    }

    fun setRoadAlerts(enabled: Boolean) {
        secureStorage?.saveBooleanPreference(KEY_ROAD_ALERTS, enabled)
        _uiState.value = _uiState.value.copy(roadAlertsEnabled = enabled)
        if (!enabled) {
            roadFeedJob?.cancel()
            roadFeedJob = null
            com.example.meowmba.core.alerts.RoadAlertBus.clear()
            _uiState.value = _uiState.value.copy(policeAlert = null)
            return
        }
        val ctx = secureStorage?.context
        if (ctx != null && !com.example.meowmba.core.nav.CurrentLocation.hasPermission(ctx)) {
            _uiState.value = _uiState.value.copy(locationPermissionRequested = true)
        }
        startRoadFeed()
    }

    private fun startRoadFeed() {
        roadFeedJob?.cancel()
        val ctx = secureStorage?.context?.applicationContext ?: return
        roadFeedJob = viewModelScope.launch(ioDispatcher) {
            com.example.meowmba.core.alerts.RoadFeedPoller.run(ctx) {
                // GPS + 2x network every 45s only while useful: foreground, or
                // background while actually driving. Background + parked used to
                // poll forever once the toggle was left on.
                _uiState.value.roadAlertsEnabled &&
                    (!appInBackground || _uiState.value.driving)
            }
        }
    }

    /** The link badge is checked, never assumed: true only if MetaMemory is installed. */
    private fun refreshMetaMemoryLink() {
        viewModelScope.launch {
            val linked = runCatching { metaMemory.isAvailable() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(metaMemoryLinked = linked)
        }
    }

    /**
     * Mirror newly created local memories to the meow-mem0 semantic store.
     * Best-effort and non-blocking: only memories created since the last sync
     * watermark are pushed, and a failure just leaves the watermark in place
     * so the next turn retries. Room stays the source of truth either way.
     */
    private fun pushMemoriesToMem0() {
        viewModelScope.launch {
            val repo = memoryRepository ?: return@launch
            if (runCatching { mem0.isAvailable() }.getOrDefault(false)) {
                val storage = secureStorage ?: return@launch
                val watermark = storage.getPreference(KEY_MEM0_SYNC_WATERMARK)?.toLongOrNull() ?: 0L
                val pending = repo.getRecentMemories(200).filter { it.id > watermark }
                if (pending.isNotEmpty()) {
                    val pushed = mem0.push(pending)
                    if (pushed.isSuccess) {
                        storage.savePreference(KEY_MEM0_SYNC_WATERMARK, pending.maxOf { it.id }.toString())
                        _uiState.value = _uiState.value.copy(mem0Linked = true)
                    }
                }
            }
        }
    }

    /** Full-text search on the Memory screen. Blank query clears search mode. */
    fun searchMemories(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(memorySearchResults = null)
            return
        }
        // Debounced + cancellable + off-main: the old version fired an FTS
        // query per keystroke on the main dispatcher, so fast typing queued
        // overlapping scans and a slow earlier query overwrote a newer one.
        searchJob = viewModelScope.launch {
            delay(300)
            val q = query.trim()
            if (q.isBlank()) {
                _uiState.value = _uiState.value.copy(memorySearchResults = null)
                return@launch
            }
            val results = withContext(ioDispatcher) {
                memoryRepository?.searchMemories(q) ?: emptyList()
            }
            _uiState.value = _uiState.value.copy(memorySearchResults = results)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository?.deleteMemoryById(id)
        }
    }

    fun dismissRecalledMemories() {
        _uiState.value = _uiState.value.copy(recalledMemories = emptyList())
    }

    fun dismissGeneratedMedia() {
        _uiState.value = _uiState.value.copy(generatedMedia = null)
    }

    /** Delete all memories AND conversation history (user-requested). */
    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository?.clearAllMemories()
            memoryRepository?.clearHistory()
            _uiState.value = _uiState.value.copy(memorySearchResults = null, recalledMemories = emptyList())
        }
    }

    /** Retention setting: auto-delete memories older than [days] (0 = keep forever). */
    fun setRetentionDays(days: Int) {
        _uiState.value = _uiState.value.copy(retentionDays = days)
        secureStorage?.savePreference(KEY_RETENTION_DAYS, days.toString())
        if (days > 0) {
            viewModelScope.launch { memoryRepository?.purgeOlderThan(days) }
        }
    }

    /** Privacy control: pause/resume all memory writes. */
    fun setMemoryPaused(paused: Boolean) {
        _uiState.value = _uiState.value.copy(memoryPaused = paused)
        secureStorage?.saveBooleanPreference(KEY_MEMORY_PAUSED, paused)
    }

    /** JSON export of the memory store (user owns their data). */
    fun exportMemoriesJson(): String = MemoryExporter.toJson(_uiState.value.allMemories)

    /** Test-safe lifecycle log: android.util.Log is not mocked in JVM unit tests. */
    /** Stop talking and ignore any in-flight model stream. Never job.cancel(). */
    private fun supersedeResponseTurn(): Long {
        val next = ++responseEpoch
        runCatching { ttsManager?.stop() }
        return next
    }

    private fun fuLog(msg: String) { runCatching { android.util.Log.i("MeowFU", msg) } }

    /**
     * Wake-standby diagnostics. runCatching for the same reason as [fuLog]:
     * android.util.Log is an unmocked stub under the JVM unit tests and throws
     * on call, and these lines sit on paths the tests exercise.
     */
    private fun wakeLog(msg: String) { runCatching { android.util.Log.i(TAG_WAKE, msg) } }

    private fun resetState() {
        fuLog("resetState -> IDLE (was ${_uiState.value.interactionState})")
        followUpJob?.cancel()
        // The turn is over, so the stuck-turn watchdog has nothing left to
        // guard. Leaving it armed would keep a 45-second timer alive after
        // every single turn for no reason.
        thinkingWatchdogJob?.cancel()
        isFollowUpListen = false
        resetMouthEnergy()
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.IDLE,
            captionText = null,
            emotion = CompanionEmotion.IDLE,
            errorMessage = null
        )
        audioFocusManager?.abandonFocus()
        updateVoiceListeningMode()
    }

    companion object {
        private const val KEY_COMPANION_NAME = "companion_name"
        private const val KEY_OWNER_SPEAKER = "owner_speaker_name"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_REQUIRE_WAKE_IN_FACE = "require_wake_in_face"

        /** Phrases that arm the phone-control agent for the next utterance. */
        private val PHONE_CONTROL_ENGAGE = setOf(
            "start listening",
            "begin listening",
            "control my phone",
            "control the phone",
            "take over the phone",
            "drive the phone",
            "phone control"
        )

        /** Camera check before raising a crash alarm: decide DANGER vs SAFE. */
        private const val DANGER_VERIFY_PROMPT = """A crash sensor just fired on this phone. Look at this photo of the scene around the phone. Reply on ONE line starting with DANGER: or SAFE: followed by a short reason.
DANGER if you see a badly damaged vehicle, deployed airbags, smoke or fire, debris or glass on the road, an overturned car, injured people, blood, or anything that looks like a real collision scene.
SAFE if the scene looks like a normal road, car interior, sidewalk, or room, or if nothing crash-related is visible."""
        private const val KEY_CONVERSATION = "conversation_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_face_v2"
        private const val KEY_WATCH_MODE = "watch_mode"
        private const val KEY_COMMENTARY = "commentary_mode"
        private const val KEY_ROAD_ALERTS = "road_alerts"
        private const val KEY_BARGE_IN = "barge_in"
        private const val KEY_IDLE_CHATTER = "idle_chatter"
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"

        private const val KEY_PINNED_APPS = "pinned_apps"
        private const val KEY_DRAWER_FOLDERS = "drawer_folders"
        private const val KEY_CAMERA_ENABLED = "camera_enabled"

        /** OpenRouter — the standalone cloud brain (openrouter.ai). No /v1 suffix:
         *  RemoteModelProvider appends "/v1/chat/completions" itself, and a doubled
         *  /v1/v1 path returns HTTP 404 (observed in the field). */
        private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api"
        /**
         * Dedicated multimodal model for looking at pages. The chat default
         * (nemotron) cannot see screenshots, which is why built pages stayed
         * as unstyled text.
         */
        private const val OPENROUTER_VISION_MODEL = "google/gemini-2.5-flash"
        /** Voice-call lane: flash/instant, not the 120B chat brain. */
        private const val OPENROUTER_CALL_MODEL = "x-ai/grok-4-fast"
        /** Sensible free default; any OpenRouter model id works via Settings. */
        private const val OPENROUTER_DEFAULT_MODEL = "nvidia/nemotron-3-super-120b-a12b:free"
        private const val KEY_MEMORY_PAUSED = "memory_paused"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_AGENT_BRAIN = "agent_brain_enabled"
        private const val KEY_HERMES_URL = "hermes_agent_url"
        private const val KEY_IMAGE_SERVER_URL = "image_server_url"
        private const val KEY_MEM0_SERVER_URL = "mem0_server_url"
        private const val KEY_MEM0_USER_ID = "mem0_user_id"
        private const val KEY_MEM0_SYNC_WATERMARK = "mem0_sync_watermark"
        private const val KEY_CAR_MODE = "car_mode"
        private const val KEY_AVATAR_STYLE = "avatar_style"
        private const val KEY_AVATAR_EYE_COLOR = "avatar_eye_color"
        private const val KEY_AVATAR_GLOW_COLOR = "avatar_glow_color"
        private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
        private const val KEY_CAR_DEVICE = "car_bluetooth_device"
        private const val KEY_VIDEO_SERVER_URL = "video_server_url"
        private const val KEY_PREFER_SERVER_IMAGE_GEN = "prefer_server_image_gen"
        private const val KEY_MUSIC_SERVER_URL = "music_server_url"
        private const val KEY_BACKGROUND_LISTENING = "background_listening"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_USB_AUTO_BRIDGE = "usb_auto_bridge"
        private const val KEY_AMBIENT_WEATHER = "ambient_weather"
        private const val KEY_BACKGROUND_KIND = "background_kind"
        private const val KEY_BACKGROUND_VALUE = "background_value"
        private const val KEY_BACKGROUND_DIM = "background_dim"

        /**
         * Ambient sky refresh. Fifteen minutes is roughly how often Open-Meteo's own
         * current-conditions block changes, so polling faster only costs battery.
         */
        private const val AMBIENT_WEATHER_INTERVAL_MS = 15 * 60 * 1000L

        /**
         * How long the agent panel stays up after everything is done. Long enough to
         * read a one-line result, short enough not to sit on the companion's face.
         */
        private const val AGENT_PANEL_LINGER_MS = 6_000L
        private const val DEFAULT_RETENTION_DAYS = 90

        internal fun isTransientSpeechError(code: Int): Boolean =
            VoicePolicy.isTransientSpeechError(code)

        internal fun isSilenceError(code: Int): Boolean = VoicePolicy.isSilenceError(code)

        internal fun isFollowUpKeepAliveError(code: Int): Boolean =
            VoicePolicy.isFollowUpKeepAliveError(code)

        private const val MAX_TRANSIENT_LISTEN_RETRIES = 3
        private const val MAX_FOLLOW_UP_SILENCE_RETRIES = 10
        private const val FOLLOW_UP_TIMEOUT_MS = 8000L

        fun shouldEnterFollowUp(s: CompanionState): Boolean = VoicePolicy.shouldEnterFollowUp(s)
        private const val ERROR_DISPLAY_MS = 3500L

        private const val TAG_WAKE = "WakeStandby"

        /**
         * How long a turn may sit in THINKING before it is abandoned.
         *
         * Generous enough for a slow on-device generation on a cold model
         * (which can genuinely take ~30s), short enough that a wedged turn does
         * not cost the user their microphone for the rest of the session.
         */
        private const val THINKING_TIMEOUT_MS = 45_000L

        /**
         * Longest a single always-on recognizer session may run without any
         * callback. Real sessions end on speech-timeout well inside this;
         * exceeding it means the session was lost, not that the user is quiet.
         */
        private const val ALWAYS_ON_SESSION_TIMEOUT_MS = 20_000L
        /** Quiet + nobody in frame + wake armed → drop always-on STT. */
        private const val MIC_NAP_MS = 60_000L
        /** Sessions opening this soon after TTS finished are echo-suspect. */
        private const val ECHO_GUARD_MS = 1_500L

        /** After the last growing partial, don't wait for AiAi's empty onResults. */
        private const val ALWAYS_ON_COMMIT_MS = 850L
        /** Keep "user in view" a beat after a one-frame face miss. */
        private const val FACE_MIC_HOLD_MS = 900L

        /** Release the resident on-device model after this long with no local turn. */
        private const val LOCAL_BRAIN_IDLE_RELEASE_MS = 10 * 60 * 1000L

        /** ...or this long when the system is already short on memory. */
        private const val LOCAL_BRAIN_LOW_MEM_IDLE_MS = 2 * 60 * 1000L

        /**
         * How many silent-window re-arms the watchdog may do before giving up.
         * Each re-arm opens a fresh session and a fresh watchdog; unbounded,
         * that is a self-perpetuating loop that keeps a unit-test virtual clock
         * busy forever (and spins a dead recognizer on device). A recognizer
         * terminal (result or error) resets the budget — a working recognizer
         * re-arms freely, a silent one gets exactly this many recovery tries.
         */
        private const val MAX_WATCHDOG_SILENT_REARMS = 2

        /**
         * How often the harness reviews itself unprompted. Slow on purpose: a
         * lesson needs evidence to RECUR, and reviewing the same rolling window
         * every minute would only re-read what it already saw.
         */
        private const val HEARTBEAT_INTERVAL_MS = 15L * 60 * 1000

        internal fun contentionBackoffMs(streak: Int): Long =
            VoicePolicy.contentionBackoffMs(streak)

        /** Long enough for a recognizer to finish tearing down, short enough to feel instant. */
        private const val WAKE_REARM_RETRY_MS = 900L

        /** Max wait for Sherpa's AudioRecord.release() after stop(). */
        private const val KWS_RELEASE_TIMEOUT_MS = 800L

        /** Extra beat after KWS join so the HAL has dropped the input. */
        private const val KWS_HANDOFF_MS = 150L

        /**
         * Beat between the wake-ack TTS draining and opening the command mic:
         * long enough for the speaker ring-down to fall below the recognizer's
         * gate, short enough that "Yes? …go ahead" feels immediate.
         */
        private const val WAKE_ACK_TAIL_GUARD_MS = 200L

        fun stripAddress(normalized: String): String = SpeechIntents.stripAddress(normalized)

        fun extractSearchQuery(normalized: String): String? =
            SpeechIntents.extractSearchQuery(normalized)

        fun isMultiStepPhoneGoal(text: String): Boolean = SpeechIntents.isMultiStepPhoneGoal(text)

        fun looksLikeDirectQuestion(normalized: String): Boolean =
            SpeechIntents.looksLikeDirectQuestion(normalized)

        fun looksLikeShowInBrowser(normalized: String): Boolean =
            SpeechIntents.looksLikeShowInBrowser(normalized)
    }
}

/**
 * Defines interactive zones on the avatar screen.
 */
enum class AvatarZone {
    EAR_LEFT, EAR_RIGHT, EYE_LEFT, EYE_RIGHT, MOUTH, FOREHEAD, BACKGROUND
}
