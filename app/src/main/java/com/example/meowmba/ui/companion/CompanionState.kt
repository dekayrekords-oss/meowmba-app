package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.core.agent.ModelDownloader
import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.ApprovalRequest
import com.example.meowmba.core.database.AuditEntryEntity
import com.example.meowmba.core.database.MemoryEntity

/** One chat bubble in the conversation workspace. */
data class ChatUiMessage(
    val id: Long = 0,
    val sender: String,
    val text: String,
    val isCompanion: Boolean
)

/** A memory that was recalled for the current turn, with its explanation. */
data class RecalledMemory(
    val contentSnippet: String,
    val reason: String
)

/** Generated media (image, video, audio) shown in a popup after creation. */
data class GeneratedMedia(
    val filePath: String,
    val type: MediaType,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class MediaType { IMAGE, VIDEO, AUDIO }
}

/**
 * Represents the complete UI state for the Memba companion.
 */
/** Settings-facing view of a scheduled routine. */
data class RoutineUi(
    val id: Long,
    val name: String,
    val whenLabel: String,
    val enabled: Boolean,
    val userMade: Boolean = false,
    val detail: String = "",
)

data class ProjectUi(
    val id: Long,
    val name: String,
    val kind: String,
    val kindLabel: String,
    val whenLabel: String,
    val conversationId: Long,
)

data class CompanionState(
    val name: String = "Meow",
    val interactionState: InteractionState = InteractionState.IDLE,
    val emotion: CompanionEmotion = CompanionEmotion.IDLE,
    val lookAtOffset: Offset = Offset.Zero, // Screen coordinates for eyes to track
    val captionText: String? = null,
    val isChatExpanded: Boolean = false,
    val isMuted: Boolean = false,
    /** Live vault transcription session. Mouth mute is independent. */
    val isNoting: Boolean = false,
    /** Vault title, e.g. Notes/2026-08-19. */
    val notingTitle: String? = null,
    val isCameraEnabled: Boolean = false,
    val isMicEnabled: Boolean = true,
    /** ML Kit currently sees a face (held briefly across one-frame misses). */
    val userInView: Boolean = false,
    /** Face is close enough that speech should get a longer listen window. */
    val userInFocus: Boolean = false,
    /** Enrolled or Left/Right label for the person Meow is looking at. */
    val activeSpeaker: String? = null,
    /** How many faces ML Kit sees right now. */
    val facesInView: Int = 0,
    /** Wake-word models are on disk (or not needed yet). */
    val voicePackReady: Boolean = true,
    val localVoicesReady: Boolean = false,
    val localVoiceId: String = "af_heart",
    val localVoiceEnabled: Boolean = false,
    val localVoiceProgress: Float? = null,
    val localVoiceMessage: String? = null,
    val voicePackLabel: String? = null,
    val voicePackProgress: Float? = null,
    val activeTask: String? = null,
    val errorMessage: String? = null,
    val showTiles: Boolean = false,
    val showSettings: Boolean = false,
    val showModelManager: Boolean = false,
    val showPrivacyDashboard: Boolean = false,
    val showAvatarScreen: Boolean = false,
    /** Which eye style the face is drawn in. Persisted across launches. */
    val avatarStyle: AvatarStyle = AvatarStyle.DEFAULT,
    val appTheme: AppTheme = AppTheme.COMPANION,
    val appearanceMode: AppearanceMode = AppearanceMode.DARK,
    val avatarFeatureColor: Long = 0xFFE8D5B9L,
    /** Godot face eye color (opaque ARGB). Default mint green. */
    val avatarEyeColor: Long = AvatarColors.DEFAULT_EYE,
    /** Soft halo glow behind the Godot face (opaque ARGB). */
    val avatarGlowColor: Long = AvatarColors.DEFAULT_GLOW,
    val selectedModel: String = "On-device (Local)",
    val loadedModels: LoadedModelsInfo = LoadedModelsInfo(),
    /** Always-on wake phrase ("Hey Meow" / "Hey Memba" / "Hey [name]") listening. */
    val wakeWordEnabled: Boolean = false,
    /** Require the wake phrase in the foreground; otherwise the open app listens always-on. */
    val requireWakeWordInFace: Boolean = false,
    /** "start listening" armed the phone-control agent; the next utterance is its goal. */
    val phoneControlAwaitingGoal: Boolean = false,
    /** ChatGPT-style voice call from the Ask box — always-on until hung up. */
    val callMode: Boolean = false,
    /** Stay in conversation after responding, for natural follow-ups. */
    val conversationMode: Boolean = false,
    /** Phase E3: keep listening 8 s after each exchange so you can cut in. */
    val bargeInEnabled: Boolean = true,
    /** Phase E1: scheduled routines shown in Settings. */
    val routines: List<RoutineUi> = emptyList(),
    /** Phase G1: current mood label (content, excited, sleepy, ...). */
    val moodLabel: String = "content",
    val robotBodyEnabled: Boolean = false,
    val robotHost: String = "192.168.1.200",
    val robotPort: String = "9100",
    val robotStatus: String = "simulated (no body)",
    val robotUseBluetooth: Boolean = true,
    val robotBtAddress: String = "",
    /** Base discovery: spinner + candidates (name, address) for the Settings picker. */
    val robotScanning: Boolean = false,
    val robotScanResults: List<Pair<String, String>> = emptyList(),
    /** Phase G1: unprompted one-liners, max once per hour. */
    val idleChatterEnabled: Boolean = true,
    /** Keep the display awake while the companion face is on screen. */
    val keepScreenOnInFaceMode: Boolean = false,
    /** Low-rate screen awareness (MediaProjection) is on. */
    val watchModeEnabled: Boolean = false,
    /** Meow may speak short unsolicited comments about what it sees. */
    val commentaryEnabled: Boolean = false,
    /** Waze / Maps police alerts on the face. */
    val roadAlertsEnabled: Boolean = false,
    val policeAlert: String? = null,
    /** App drawer overlay (swipe right) is open. */
    val showAppDrawer: Boolean = false,
    /** Spoken search just finished; the drawer copies this into the bar once. */
    val drawerVoiceCommit: String? = null,
    /** Agent browser overlay (swipe left) is open. */
    val showBrowser: Boolean = false,
    /** Standing goal the agent browser is working on. */
    val browserGoal: String? = null,
    /** Live HTML/JS/scrape the agent last wrote. */
    val browserSource: String = "",
    val browserSourceKind: String = "html",
    // NOTE: speech energy is deliberately NOT here. It changes ~14x/second
    // while speaking, and every field of this class is carried by value into
    // AvatarRenderer — so a copy() per word boundary recomposed the whole
    // avatar (and re-ran resolvePose) for the entire length of every reply.
    // It lives in CompanionViewModel.mouthEnergy as its own StateFlow, read
    // only inside the Canvas draw lambda.
    /** Result of the last "Test connection" action on the Hardware page. */
    val connectionTestResult: String? = null,
    val connectionTestRunning: Boolean = false,
    /** Conversation history, sourced live from Room. */
    val chatMessages: List<ChatUiMessage> = emptyList(),
    val liveAssistantReply: String? = null,
    val liveReplyConversationId: Long = 0L,
    /** Memories recalled for the current turn, each with its "why". */
    val recalledMemories: List<RecalledMemory> = emptyList(),
    /** All stored memories (Memory screen list). */
    val allMemories: List<MemoryEntity> = emptyList(),
    /** FTS search results on the Memory screen; null = not searching. */
    val memorySearchResults: List<MemoryEntity>? = null,
    val showMemoryScreen: Boolean = false,
    val showStudioScreen: Boolean = false,
    val showAssetBrowser: Boolean = false,
    /** Invented clip currently playing over the face, or null. Clears itself when done. */
    val activeAnimation: CustomAnimationSpec? = null,
    /** Godot-style channel composition currently driving the face, if any. */
    val activeComposed: ComposedExpression? = null,
    /** True only when the MetaMemory glasses app is actually installed — never assumed. */
    val metaMemoryLinked: Boolean = false,
    /** meow-mem0 semantic memory server URL ("" = not configured → feature off). */
    val mem0ServerUrl: String = "",
    /** True only when the mem0 server answered /health. */
    val mem0Linked: Boolean = false,
    /**
     * Car assistant master switch. When true the driving detector runs and the
     * drive card can appear; when false no location/sensor fusion happens at all.
     */
    val carModeEnabled: Boolean = false,
    /** Whether the user is currently in a car (any cue). */
    val driving: Boolean = false,
    /** True while the drive card should be up; false after the user dismisses it. */
    val driveCardVisible: Boolean = false,
    /** Live GPS speed, mph. */
    val driveSpeedMph: Float = 0f,
    /** Live linear-acceleration magnitude, in g. */
    val driveGForce: Float = 0f,
    /**
     * Live car-frame acceleration split (approximate). Positive longitudinal =
     * accelerating, negative = braking; signed lateral = turning. Used to drive
     * the cat's reaction animations.
     */
    val driveLateralG: Float = 0f,
    val driveLongitudinalG: Float = 0f,
    /**
     * What the cat is currently reacting to while driving. Managed by the car
     * collector and read by the avatar state machine + nav overlay. This is an
     * additive animation signal — it never changes [emotion] or [interactionState].
     */
    val driveReaction: DriveReaction = DriveReaction.NONE,
    /** Which cue detected driving: "GPS speed", "Manual", "Bluetooth", … */
    val driveReason: String = "",
    /** True when drive mode was forced on by voice/UI. */
    val driveManual: Boolean = false,
    /** Stats for the trip in progress, or null when parked. */
    val driveTrip: com.example.meowmba.core.car.TripStats? = null,
    /** Crash alert text while one is pending; cleared by dismissing it. */
    val crashAlert: String? = null,
    /** Emergency-contact phone number for the car.help tool (Settings). */
    val emergencyContact: String = "",
    /**
     * Weather to show as a card, or null for none.
     *
     * Set whenever anything fetches weather — the agent tool included — so the companion
     * shows the card whenever it talks about the weather without the model needing to know
     * cards exist.
     */
    val weather: com.example.meowmba.core.weather.WeatherNow? = null,

    /**
     * Last known sky for the *ambient* scene, refreshed on a timer and independent of the
     * card. Dismissing the card clears [weather]; it must not stop the weather playing.
     */
    val ambientWeather: com.example.meowmba.core.weather.WeatherNow? = null,

    /**
     * Keep the weather animation running all the time, matching the real conditions and the
     * real time of day, instead of only while the card is up.
     */
    val ambientWeatherEnabled: Boolean = false,

    /**
     * Forces the scene's time of day while previewing, or null to follow the real clock.
     *
     * Without this the only way to see the dawn or midday sky is to be awake at dawn or
     * midday, which makes half the work unreviewable.
     */
    val previewTimeOfDay: com.example.meowmba.core.weather.TimeOfDay? = null,

    /**
     * The inventory item the conversation is currently about, or null.
     *
     * Set by any inventory tool that lands on a single item, so the companion can put the
     * thing on screen and offer what to do with it instead of describing it in prose.
     */
    val inventoryItem: com.example.meowmba.core.inventory.InventoryItem? = null,

    /** Weather page: readings, the ambient switch, moon, preview controls. */
    val showWeatherScreen: Boolean = false,

    /** Background card: what is painted behind the companion. */
    val background: com.example.meowmba.core.background.BackgroundSelection =
        com.example.meowmba.core.background.BackgroundSelection(),
    val showBackgroundScreen: Boolean = false,

    /** NAV card: offline region packs, online fallback, saved places. */
    val showNavScreen: Boolean = false,
    /** When true, nothing new is written to memory (privacy control). */
    val memoryPaused: Boolean = false,
    /** Auto-delete memories older than this many days; 0 = keep forever. */
    val retentionDays: Int = 90,
    /** Agent tasks (newest first), driven by the orchestrator. */
    val activeTasks: List<AgentTask> = emptyList(),
    /** A tool call waiting for the user's explicit decision, if any. */
    val pendingApproval: ApprovalRequest? = null,
    val showAgentsScreen: Boolean = false,
    /** Tools registered in this installation; not a claim of live account/hardware access. */
    val registeredAgentTools: Set<String> = emptySet(),
    val showProjectsScreen: Boolean = false,
    val showToolsScreen: Boolean = false,
    /** Full-screen LOOI body panel opened from the Robot tile. */
    val showRobotScreen: Boolean = false,

    /** Hardware → Voice Recognition: which speech-to-text backend to use. */
    val showVoiceModelScreen: Boolean = false,
    val asrBackend: com.example.meowmba.core.audio.AsrBackend =
        com.example.meowmba.core.audio.AsrBackend.DEFAULT,
    /** Per-backend availability, with a reason when unavailable. */
    val asrBackendStatuses: List<com.example.meowmba.core.audio.AsrBackendStatus> = emptyList(),
    /** Model id used when the cloud backend is selected. */
    val asrCloudModel: String = com.example.meowmba.core.audio.AsrBackend.DEFAULT_GEMINI_MODEL,
    val projects: List<ProjectUi> = emptyList(),
    val activeProjectId: Long? = null,
    val activeProjectName: String? = null,
    val activeConversationId: Long = 0L,

    /** Obsidian-style vault notes screen. */
    val showVaultScreen: Boolean = false,

    /**
     * The agent whose history is open on the Agents screen, or null for the roster.
     */
    val selectedAgentId: String? = null,

    /**
     * Whether the live agent-activity panel is on screen.
     *
     * Raised whenever an agent picks up work. Swiping the panel away lowers it and the
     * chat carries the updates instead — the work continues either way.
     */
    val agentActivityVisible: Boolean = false,
    /** Audit trail (newest first, ring buffer of 200). */
    val auditEntries: List<AuditEntryEntity> = emptyList(),
    /** On-device agent brain: model-driven tool routing for natural commands. */
    val agentBrainEnabled: Boolean = true,
    /** Optional Hermes Agent endpoint (OpenAI-compatible). Blank = on-device only. */
    val hermesAgentUrl: String = "",
    /** Opt-in HTTP server for image generation. Empty = on-device only. */
    val imageServerUrl: String = "",
    /** When true and imageServerUrl is set, try the server before on-device. */
    val preferServerImageGen: Boolean = false,
    /**
     * Opt-in HTTP server for music generation. Empty = no music backend, and
     * audio requests fall back to the procedural tone generator. Unlike
     * images, there's no on-device generator to prefer against.
     */
    val musicServerUrl: String = "",
    /** Opt-in HTTP server for video generation. Empty = video generation unavailable. */
    val videoServerUrl: String = "",
    /** Active model downloads by id (catalog option id or "custom:<fileName>"). */
    val modelDownloads: Map<String, ModelDownloader.DownloadProgress> = emptyMap(),
    /** Every model bundle actually present in the app-private models dir. */
    val installedModels: List<InstalledModelFile> = emptyList(),
    /** Compatible bundles found in the public Downloads collection. */
    val discoveredModelFiles: List<DiscoveredModelFile> = emptyList(),
    val modelScanRunning: Boolean = false,
    /** Keep listening for the wake phrase in the background (foreground service). */
    val backgroundListeningEnabled: Boolean = false,
    /** Vision: set when a look request needs the CAMERA runtime permission. */
    val cameraPermissionRequested: Boolean = false,
    /** Locations: set when saving a named place needs location permission. */
    val locationPermissionRequested: Boolean = false,
    /** Cleanup: set when scanning photos/files needs media permission. */
    val mediaPermissionRequested: Boolean = false,
    /** Vision: thumbnail of the last captured frame (shown while describing). */
    val capturedFrame: android.graphics.Bitmap? = null,
    /** Vision: show live camera preview. */
    val isPreviewVisible: Boolean = false,
    /** Vision: use front-facing camera. */
    val isFrontCamera: Boolean = false,
    /** Face tracking: the face's horizontal offset (-1..1), null when no face. */
    val faceGazeX: Float? = null,
    /** Face tracking: the face's vertical offset (-1..1), null when no face. */
    val faceGazeY: Float? = null,
    /** Generated media (image, video, audio) to show in a popup. */
    val generatedMedia: GeneratedMedia? = null,
    /** USB auto-bridge is monitoring for a tethered desktop connection. */
    val usbAutoBridgeEnabled: Boolean = false,
    /** True when the phone is plugged into USB (data connection). */
    val usbConnected: Boolean = false,
    /** Plugins & MCP connectors menu visibility. */
    val showConnectorsScreen: Boolean = false,
    /** Current plugins & connectors the user has added (apps / MCP / user). */
    val connectors: List<com.example.meowmba.core.plugins.Connector> = emptyList(),
    /** Installed apps discovered for potential plugin connections, with icons. */
    val discoverableApps: List<com.example.meowmba.core.plugins.ConnectorManager.DiscoveredApp> = emptyList(),
    /** True while app discovery / MCP handshake is in flight. */
    val connectorBusy: Boolean = false,
    /** Last MCP endpoint test outcome: (success, message). */
    val connectorTestResult: Pair<Boolean, String>? = null,
    /** Hosted read-only MCP endpoint is currently listening on the LAN. */
    val mcpServerRunning: Boolean = false,
    /** LAN port the hosted MCP endpoint is bound to (0 when not running). */
    val mcpServerPort: Int = 0,
    /** Phone LAN address shown so Android Studio can paste it as the httpUrl. */
    val mcpServerAddress: String = "",
)

/**
 * Which kind of provider is currently driving the companion.
 */
enum class ModelSource { LOCAL, NETWORK, CLOUD }

/**
 * Check if the screen is currently in landscape orientation.
 * Uses the provided context to query the display rotation.
 */
fun isLandscapeScreen(context: android.content.Context?): Boolean {
    val config = context?.resources?.configuration
    return config?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
}

/**
 * Snapshot of what model is loaded where, shown on the Hardware (LLM Manager)
 * page. Populated by the ViewModel from the same secure configuration that
 * builds the actual providers, so the page always reflects reality.
 */
data class LoadedModelsInfo(
    /** On-device model (runs on the phone itself) — catalog display name or real file name. */
    val localName: String = "none",
    val localPath: String = "/data/local/tmp/llm/gemma.bin",
    val localRuntime: String = "MediaPipe LLM Inference",
    /** Cloud server model. */
    val cloudProvider: String = "OpenAI",
    val cloudModel: String = "gpt-4o-mini",
    val cloudEndpoint: String = "https://api.openai.com",
    val cloudKeyConfigured: Boolean = false,
    /** Network (LAN) server model — Ollama. */
    val networkEndpoint: String = "",
    val networkModel: String = "",
    /** True when the local model file actually exists on disk. */
    val localInstalled: Boolean = false,
    /** Which source is actively serving responses right now. */
    val activeSource: ModelSource = ModelSource.LOCAL
)

/** A model bundle file present in the app-private models directory. */
data class InstalledModelFile(
    val fileName: String,
    val sizeBytes: Long,
    /** Catalog display name when the file matches a curated option, else null (custom). */
    val catalogDisplayName: String? = null
)

data class DiscoveredModelFile(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
)

/**
 * Functional states of the companion's interaction cycle.
 */
enum class InteractionState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    MUTED,
    OFFLINE
}

/**
 * Emotional states affecting the avatar's appearance and animations.
 */
enum class CompanionEmotion {
    IDLE,
    HAPPY,
    CURIOUS,
    ALERT,
    SLEEPY,
    CONFUSED,
    PROUD
}

/**
 * True when the bare companion face is what's on screen — no overlay panels,
 * drawers, or full-screen cards. Used for face-mode behaviours such as
 * keeping the display awake. Pure function of state so it is unit-testable.
 */
fun faceModeActive(state: CompanionState): Boolean =
    !state.showSettings &&
        !state.showModelManager &&
        !state.isChatExpanded &&
        !state.showTiles &&
        !state.showPrivacyDashboard &&
        !state.showMemoryScreen &&
        !state.showStudioScreen &&
        !state.showAssetBrowser &&
        !state.showAgentsScreen &&
        !state.showBackgroundScreen &&
        !state.showWeatherScreen &&
        !state.showNavScreen &&
        !state.showAvatarScreen &&
        !state.showVaultScreen &&
        !state.showProjectsScreen &&
        !state.showToolsScreen &&
        !state.showRobotScreen &&
        !state.showVoiceModelScreen &&
        !state.showAppDrawer

/**
 * Additive animation signal for the car: what the cat is reacting to right now.
 * Managed by the car collector; read by the avatar state machine and the nav
 * overlay. Never changes [CompanionState.emotion] or interaction state.
 */
enum class DriveReaction {
    /** Cruising, or not driving. */
    NONE,
    /** Hard turn to the cat's left (negative lateral G). */
    LEAN_LEFT,
    /** Hard turn to the cat's right (positive lateral G). */
    LEAN_RIGHT,
    /** Hard braking (strong negative longitudinal G) — lurch forward. */
    LURCH,
    /** Sustained highway speed. */
    SPEED;

    companion object {
        /** |lateral G| above this reads as a hard turn. */
        const val HARD_TURN_G = 0.35f
        /** Deceleration (negative longitudinal G) below this reads as hard braking. */
        const val HARD_BRAKE_G = 0.55f
        /** Speed above this reads as "fast". */
        const val FAST_MPH = 60f
        /** How long a transient reaction (lean/lurch) is held before relaxing. */
        const val HOLD_MS = 1_500L
    }
}

/**
 * Pure mapping from a live driving snapshot to a face reaction. Braking beats
 * turning beats speed, so the scariest thing wins. Thresholds come from
 * [DriveReaction.Companion] so detector and UI cannot drift apart.
 */
fun driveReactionFor(speedMph: Float, lateralG: Float, longitudinalG: Float): DriveReaction = when {
    longitudinalG <= -DriveReaction.HARD_BRAKE_G -> DriveReaction.LURCH
    lateralG >= DriveReaction.HARD_TURN_G -> DriveReaction.LEAN_RIGHT
    lateralG <= -DriveReaction.HARD_TURN_G -> DriveReaction.LEAN_LEFT
    speedMph >= DriveReaction.FAST_MPH -> DriveReaction.SPEED
    else -> DriveReaction.NONE
}

/** True for reactions that are momentary events rather than sustained states. */
private fun DriveReaction.isTransient(): Boolean =
    this == DriveReaction.LEAN_LEFT ||
        this == DriveReaction.LEAN_RIGHT ||
        this == DriveReaction.LURCH

/**
 * Latch a transient reaction for [holdMs], then relax.
 *
 * A hard brake is an EVENT, not a state: the face should play a lurch for a beat
 * and recover. Deriving the reaction straight from the latest sample cannot do
 * that, because the detector only publishes every couple of seconds - so the cat
 * stayed stuck mid-lurch until the next publish happened to arrive.
 *
 * SPEED is deliberately NOT latched: it is a sustained condition, so it should
 * follow the samples and end the moment the car slows down.
 *
 * @return the reaction to show, paired with the timestamp it started.
 */
fun nextDriveReaction(
    current: DriveReaction,
    startedAtMs: Long,
    incoming: DriveReaction,
    nowMs: Long,
    holdMs: Long = DriveReaction.HOLD_MS,
): Pair<DriveReaction, Long> {
    // A fresh transient always wins and restarts the clock, so a second jolt
    // during the first one re-triggers rather than being swallowed.
    if (incoming.isTransient()) return incoming to nowMs
    // An unexpired transient keeps playing even though the samples have calmed.
    if (current.isTransient() && nowMs - startedAtMs < holdMs) return current to startedAtMs
    return incoming to nowMs
}
