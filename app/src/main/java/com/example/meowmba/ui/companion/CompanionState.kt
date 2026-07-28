package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.ApprovalRequest
import com.example.meowmba.core.database.AuditEntryEntity
import com.example.meowmba.core.database.MemoryEntity

/** One chat bubble in the conversation workspace. */
data class ChatUiMessage(
    val sender: String,
    val text: String,
    val isCompanion: Boolean
)

/** A memory that was recalled for the current turn, with its explanation. */
data class RecalledMemory(
    val contentSnippet: String,
    val reason: String
)

/**
 * Represents the complete UI state for the Memba companion.
 */
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
    val selectedModel: String = "Gemma 2b (Local)",
    val loadedModels: LoadedModelsInfo = LoadedModelsInfo(),
    /** Always-on wake phrase ("Hey Meow" / "Hey Memba" / "Hey [name]") listening. */
    val wakeWordEnabled: Boolean = false,
    /** Stay in conversation after responding, for natural follow-ups. */
    val conversationMode: Boolean = false,
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
    /** When true, nothing new is written to memory (privacy control). */
    val memoryPaused: Boolean = false,
    /** Auto-delete memories older than this many days; 0 = keep forever. */
    val retentionDays: Int = 90,
    /** Agent tasks (newest first), driven by the orchestrator. */
    val activeTasks: List<AgentTask> = emptyList(),
    /** A tool call waiting for the user's explicit decision, if any. */
    val pendingApproval: ApprovalRequest? = null,
    val showAgentsScreen: Boolean = false,
    /** Audit trail (newest first, ring buffer of 200). */
    val auditEntries: List<AuditEntryEntity> = emptyList(),
    /** On-device agent brain: model-driven tool routing for natural commands. */
    val agentBrainEnabled: Boolean = true,
    /** Optional Hermes Agent endpoint (OpenAI-compatible). Blank = on-device only. */
    val hermesAgentUrl: String = ""
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
    /** On-device model (runs on the phone itself). */
    val localName: String = "Gemma 2b",
    val localPath: String = "/data/local/tmp/llm/gemma.bin",
    val localRuntime: String = "MediaPipe LLM Inference",
    /** Cloud server model. */
    val cloudProvider: String = "OpenAI",
    val cloudModel: String = "gpt-4o-mini",
    val cloudEndpoint: String = "https://api.openai.com",
    val cloudKeyConfigured: Boolean = false,
    /** Which source is actively serving responses right now. */
    val activeSource: ModelSource = ModelSource.LOCAL
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
