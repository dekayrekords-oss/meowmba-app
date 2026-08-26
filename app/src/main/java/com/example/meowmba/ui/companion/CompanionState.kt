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
)

data class CompanionState(
    val name: String = "Meow",
    val interactionState: InteractionState = InteractionState.IDLE,
    val emotion: CompanionEmotion = CompanionEmotion.IDLE,
    val lookAtOffset: Offset = Offset.Zero, // Screen coordinates for eyes to track
    val captionText: String? = null,
    val isChatExpanded: Boolean = false,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isMicEnabled: Boolean = true,
    val activeTask: String? = null,
    val errorMessage: String? = null,
    val showTiles: Boolean = false,
    val showSettings: Boolean = false,
    val showModelManager: Boolean = false,
    val showPrivacyDashboard: Boolean = false,
    val showAvatarScreen: Boolean = false,
    /** Which eye style the face is drawn in. Persisted across launches. */
    val avatarStyle: AvatarStyle = AvatarStyle.DEFAULT,
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
    /** Stay in conversation after responding, for natural follow-ups. */
    val conversationMode: Boolean = false,
    /** Phase E3: keep listening 8 s after each exchange so you can cut in. */
    val bargeInEnabled: Boolean = true,
    /** Phase E1: scheduled routines shown in Settings. */
    val routines: List<RoutineUi> = emptyList(),
    /** Phase G1: current mood label (content, excited, sleepy, ...). */
    val moodLabel: String = "content",
    val robotBodyEnabled: Boolean = true,
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
    val keepScreenOnInFaceMode: Boolean = true,
    /** Low-rate screen awareness (MediaProjection) is on. */
    val watchModeEnabled: Boolean = false,
    /** Meow may speak short unsolicited comments about what it sees. */
    val commentaryEnabled: Boolean = false,
    /** App drawer overlay (swipe right) is open. */
    val showAppDrawer: Boolean = false,
    /** Agent browser overlay (swipe left) is open. */
    val showBrowser: Boolean = false,
    /** Speech energy 0..1 driven by TTS word boundaries; animates the mouth. */
    val mouthEnergy: Float = 0f,
    /** Result of the last "Test connection" action on the Hardware page. */
    val connectionTestResult: String? = null,
    val connectionTestRunning: Boolean = false,
    /** Conversation history, sourced live from Room. */
    val chatMessages: List<ChatUiMessage> = emptyList(),
    /** Memories recalled for the current turn, each with its "why". */
    val recalledMemories: List<RecalledMemory> = emptyList(),
    /** All stored memories (Memory screen list). */
    val allMemories: List<MemoryEntity> = emptyList(),
    /** FTS search results on the Memory screen; null = not searching. */
    val memorySearchResults: List<MemoryEntity>? = null,
    val showMemoryScreen: Boolean = false,
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
    /** Opt-in HTTP server for video generation. Empty = frame-manifest placeholder only. */
    val videoServerUrl: String = "",
    /** Active model downloads by id (catalog option id or "custom:<fileName>"). */
    val modelDownloads: Map<String, ModelDownloader.DownloadProgress> = emptyMap(),
    /** Every model bundle actually present in the app-private models dir. */
    val installedModels: List<InstalledModelFile> = emptyList(),
    /** Keep listening for the wake phrase in the background (foreground service). */
    val backgroundListeningEnabled: Boolean = false,
    /** Vision: set when a look request needs the CAMERA runtime permission. */
    val cameraPermissionRequested: Boolean = false,
    /** Locations: set when saving a named place needs location permission. */
    val locationPermissionRequested: Boolean = false,
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
    /** Gemini Nano is active as the light model alongside the main model. */
    val geminiNanoActive: Boolean = false,
    /** Generated media (image, video, audio) to show in a popup. */
    val generatedMedia: GeneratedMedia? = null,
    /** USB auto-bridge is monitoring for a tethered desktop connection. */
    val usbAutoBridgeEnabled: Boolean = false,
    /** True when the phone is plugged into USB (data connection). */
    val usbConnected: Boolean = false
)

/**
 * Which kind of provider is currently driving the companion.
 */
enum class ModelSource { LOCAL, NETWORK, CLOUD }

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
        !state.showAgentsScreen &&
        !state.showBackgroundScreen &&
        !state.showWeatherScreen &&
        !state.showNavScreen &&
        !state.showAvatarScreen &&
        !state.showVaultScreen

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
