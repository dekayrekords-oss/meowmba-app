package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.LocalModelOption
import com.example.meowmba.core.agent.MediaPipeProvider
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.agent.ModelDownloader
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.agent.runtime.AgentOrchestrator
import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.AgentBrainRouter
import com.example.meowmba.core.agent.runtime.AuditSink
import com.example.meowmba.core.agent.runtime.HermesAgentBrain
import com.example.meowmba.core.agent.runtime.HermesConfig
import com.example.meowmba.core.agent.runtime.LlmToolCallRouter
import com.example.meowmba.core.agent.runtime.SubmitResult
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.agent.runtime.ToolCallPromptMode
import com.example.meowmba.core.agent.tools.FileDeleteTool
import com.example.meowmba.core.agent.tools.FileListTool
import com.example.meowmba.core.agent.tools.FileReadTool
import com.example.meowmba.core.agent.tools.FileWriteTool
import com.example.meowmba.core.agent.tools.MemorySaveTool
import com.example.meowmba.core.agent.tools.MemorySearchTool
import com.example.meowmba.core.agent.tools.ProjectCreateTool
import com.example.meowmba.core.agent.tools.TaskListTool
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.audio.AudioStreamingManager
import com.example.meowmba.core.audio.RecognizerWakeWordEngine
import com.example.meowmba.core.audio.SelfVoiceFilter
import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.core.audio.SpeechSynthesizer
import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.audio.WakeWordEngine
import com.example.meowmba.core.database.AuditDao
import com.example.meowmba.core.database.AuditEntryEntity
import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.database.MessageRole
import com.example.meowmba.core.memory.EmbeddingInterface
import com.example.meowmba.core.memory.LocalHashEmbedder
import com.example.meowmba.core.memory.MemoryCommandParser
import com.example.meowmba.core.memory.MemoryExporter
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.memory.PromptBuilder
import com.example.meowmba.core.memory.RecallEngine
import com.example.meowmba.core.memory.RecallResult
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    /** Dispatcher for blocking work (on-device inference). Injected for tests. */
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionState())
    val uiState = _uiState.asStateFlow()

    /** Active model provider. Internal (not private) so tests can inject fakes. */
    internal var currentProvider: ModelProvider? = null

    /** Tools available for agent routing; null when no workspace/repo exists. */
    private var agentToolRegistry: ToolRegistry? = null

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

    /** Suppresses the companion hearing its own TTS voice during barge-in. */
    private val selfVoiceFilter = SelfVoiceFilter()

    // ── Agent system (Phase 5) ──────────────────────────────────────────────
    private var agentOrchestrator: AgentOrchestrator? = null

    /** Audit trail: ring-buffered into UI state and persisted to Room. */
    private val agentAuditSink = object : AuditSink {
        override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {
            val entry = AuditEntryEntity(agentId = agentId, taskId = taskId, action = action, detail = detail)
            _uiState.value = _uiState.value.copy(
                auditEntries = (listOf(entry) + _uiState.value.auditEntries).take(200)
            )
            runCatching { auditDao?.insert(entry) }
        }
    }

    private var responseJob: Job? = null
    private var followUpJob: Job? = null
    private var mouthDecayJob: Job? = null

    /** Set when a deliberate interruption stops TTS, so the resulting
     *  onSpeechFinished callback is ignored exactly once. */
    private var suppressSpeechFinished = false

    /** True while capturing a follow-up turn inside conversation mode. */
    private var isFollowUpListen = false

    /** Last turns, for "remember this" (content-free remember commands). */
    private var lastUserText: String? = null
    private var lastAssistantText: String? = null

    init {
        // Restore persisted voice preferences (name, wake word, conversation mode)
        secureStorage?.let { storage ->
            val savedName = storage.getPreference(KEY_COMPANION_NAME)
            _uiState.value = _uiState.value.copy(
                name = if (savedName.isNullOrBlank()) _uiState.value.name else savedName,
                wakeWordEnabled = storage.getBooleanPreference(KEY_WAKE_WORD, false),
                conversationMode = storage.getBooleanPreference(KEY_CONVERSATION, false),
                agentBrainEnabled = storage.getBooleanPreference(KEY_AGENT_BRAIN, true),
                hermesAgentUrl = storage.getPreference(KEY_HERMES_URL) ?: ""
            )
        }
        matcher = WakePhraseMatcher(_uiState.value.name)

        if (speechService != null) {
            wakeWordEngine = RecognizerWakeWordEngine(speechService, matcher, viewModelScope).apply {
                onWake = { command -> onWakeDetected(command) }
            }
            if (_uiState.value.wakeWordEnabled) startWakeStandby()
        }

        ttsManager?.onSpeechStarted = {
            _uiState.value = _uiState.value.copy(interactionState = InteractionState.SPEAKING)
            startInterruptionWatch()
        }
        ttsManager?.onSpeechFinished = { onSpeechFinished() }
        ttsManager?.onWordBoundary = { start, end ->
            selfVoiceFilter.onWordSpoken(start, end)
            bumpMouthEnergy()
        }

        // Single collector for partial transcripts (live captions + barge-in)
        viewModelScope.launch {
            speechService?.partialResults?.collect { partial ->
                val s = _uiState.value
                if (s.interactionState == InteractionState.LISTENING && partial.isNotEmpty()) {
                    _uiState.value = s.copy(captionText = partial)
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

        // Live conversation history from Room drives the chat workspace
        viewModelScope.launch {
            memoryRepository?.allMessages?.collect { messages ->
                _uiState.value = _uiState.value.copy(
                    chatMessages = messages.map { message ->
                        ChatUiMessage(
                            sender = if (message.role == MessageRole.USER) "You" else _uiState.value.name,
                            text = message.content,
                            isCompanion = message.role != MessageRole.USER
                        )
                    }
                )
            }
        }

        // Memory list for the Memory screen
        viewModelScope.launch {
            memoryRepository?.allMemories?.collect { memories ->
                _uiState.value = _uiState.value.copy(allMemories = memories)
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
        if (workspaceRoot != null) {
            val workspace = Workspace(workspaceRoot)
            toolRegistry.register(FileReadTool(workspace))
            toolRegistry.register(FileWriteTool(workspace))
            toolRegistry.register(FileDeleteTool(workspace))
            toolRegistry.register(FileListTool(workspace))
            toolRegistry.register(ProjectCreateTool(workspace))
        }
        if (memoryRepository != null || workspaceRoot != null) {
            agentToolRegistry = toolRegistry
            val orchestrator = AgentOrchestrator(viewModelScope, toolRegistry, agentAuditSink)
            toolRegistry.register(TaskListTool {
                orchestrator.tasks.value.take(10).map { "[${it.status}] ${it.title} (${it.agentId})" }
            })
            orchestrator.onTaskCompleted = { task ->
                when (task.status) {
                    TaskStatus.COMPLETED -> speak(task.result ?: "Done.")
                    TaskStatus.FAILED -> speak("Sorry, that didn't work: ${task.result}")
                    else -> { /* cancelled tasks stay quiet */ }
                }
            }
            agentOrchestrator = orchestrator
            viewModelScope.launch {
                orchestrator.tasks.collect { tasks ->
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

        // Load initial provider
        updateProvider(_uiState.value.selectedModel)
    }

    override fun onCleared() {
        wakeWordEngine?.stop()
        super.onCleared()
    }

    private fun updateProvider(modelName: String) {
        refreshLoadedModels()
        if (secureStorage == null) return

        currentProvider = when (modelName) {
            "Gemma 2b (Local)" -> {
                MediaPipeProvider(secureStorage.context, resolvedLocalModelPath())
            }
            "Ollama (Local Network)" -> {
                RemoteModelProvider(
                    "Ollama",
                    secureStorage.getEndpoint("ollama") ?: "http://10.0.2.2:11434",
                    null,
                    secureStorage.getModelName("ollama") ?: "gemma2"
                )
            }
            "OpenAI (Cloud)" -> {
                RemoteModelProvider(
                    "OpenAI",
                    "https://api.openai.com",
                    secureStorage.getApiKey("openai"),
                    secureStorage.getModelName("openai") ?: "gpt-4o-mini"
                )
            }
            else -> null
        }
    }

    /**
     * The local model file the provider will actually load: the configured
     * path, else a downloaded catalog model, else the legacy adb default.
     */
    private fun resolvedLocalModelPath(): String {
        val configured = secureStorage?.getEndpoint("local")
        if (configured != null) return configured
        return downloader?.firstInstalledOption()
            ?.let { option -> downloader?.installedFile(option)?.absolutePath }
            ?: "/data/local/tmp/llm/gemma.bin"
    }

    /**
     * Snapshot of what model is loaded on-device vs. on the cloud server,
     * derived from the same configuration used to build the providers.
     * Displayed on the Hardware (LLM Manager) page.
     */
    private fun refreshLoadedModels() {
        val selected = _uiState.value.selectedModel
        val source = when {
            selected.contains("(Local)") -> ModelSource.LOCAL
            selected.contains("Network") -> ModelSource.NETWORK
            else -> ModelSource.CLOUD
        }
        val localPath = resolvedLocalModelPath()
        _uiState.value = _uiState.value.copy(
            loadedModels = LoadedModelsInfo(
                localName = if (source == ModelSource.LOCAL) {
                    selected.substringBefore(" (")
                } else {
                    "Gemma 2b"
                },
                localPath = localPath,
                localRuntime = "MediaPipe LLM Inference",
                cloudProvider = "OpenAI",
                cloudModel = secureStorage?.getModelName("openai") ?: "gpt-4o-mini",
                cloudEndpoint = "https://api.openai.com",
                cloudKeyConfigured = !secureStorage?.getApiKey("openai").isNullOrBlank(),
                localInstalled = java.io.File(localPath).exists(),
                activeSource = source
            )
        )
    }

    // ── Voice modes ─────────────────────────────────────────────────────────

    /** Enable/disable always-on wake phrase listening (persisted). */
    fun setWakeWordEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wakeWordEnabled = enabled)
        secureStorage?.saveBooleanPreference(KEY_WAKE_WORD, enabled)
        if (enabled) {
            startWakeStandby()
        } else {
            wakeWordEngine?.stop()
            if (_uiState.value.interactionState == InteractionState.IDLE) {
                _uiState.value = _uiState.value.copy(captionText = null)
            }
        }
    }

    /** Enable/disable conversation follow-up mode (persisted). */
    fun setConversationMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(conversationMode = enabled)
        secureStorage?.saveBooleanPreference(KEY_CONVERSATION, enabled)
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

    private fun wakeHint() = "Say \"Hey ${_uiState.value.name}\"…"

    /** Wake phrase heard: run the spoken command, or listen for one. */
    internal fun onWakeDetected(command: String?) {
        wakeWordEngine?.stop()
        if (!_uiState.value.isMicEnabled) {
            resetState()
            return
        }
        if (command != null) {
            processUserSpeech(command)
        } else {
            startListening(prompt = "Yes?")
        }
    }

    /** Begin standby wake-phrase listening (only while idle). */
    private fun startWakeStandby() {
        if (_uiState.value.interactionState != InteractionState.IDLE) return
        if (_uiState.value.isMicEnabled) wakeWordEngine?.start()
        _uiState.value = _uiState.value.copy(captionText = wakeHint())
    }

    /**
     * Toggles the voice listening state (push-to-talk / tap-to-toggle).
     * Tapping while the companion speaks interrupts it and starts a new turn.
     */
    fun onMicClick() {
        if (!_uiState.value.isMicEnabled) return

        when (_uiState.value.interactionState) {
            InteractionState.IDLE -> startListening()
            InteractionState.SPEAKING -> interrupt()
            else -> {
                speechService?.stopListening()
                resetState()
            }
        }
    }

    private fun startListening(prompt: String = "Listening...") {
        followUpJob?.cancel()
        isFollowUpListen = false
        wakeWordEngine?.stop()

        if (speechService == null) {
            startMockListening()
            return
        }

        audioFocusManager?.requestFocus()
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.LISTENING,
            captionText = prompt,
            emotion = CompanionEmotion.ALERT
        )

        speechService.startListening(
            onResult = { result -> processUserSpeech(result) },
            onError = { _ -> resetState() }
        )
    }

    private fun startMockListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                interactionState = InteractionState.LISTENING,
                captionText = "Listening...",
                emotion = CompanionEmotion.ALERT
            )
            delay(3000)
            processUserSpeech("Hey Meow, how are you?")
        }
    }

    private fun processUserSpeech(text: String) {
        if (_uiState.value.conversationMode && isStopCommand(text)) {
            endConversation()
            return
        }

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

        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.THINKING,
            captionText = text,
            emotion = CompanionEmotion.CURIOUS
        )

        responseJob = viewModelScope.launch {
            // Save user message to memory
            val userMessageId = memoryRepository?.addMessage(MessageRole.USER, text)

            // Agent routing: keyword fast path, then the on-device model brain.
            // The model only proposes — approval gates still decide.
            val agentIntent = routeAgentIntent(text)
            if (agentIntent != null) {
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
            val recentMessages = memoryRepository?.getRecentMessages(10) ?: emptyList()
            val prompt = PromptBuilder.build(_uiState.value.name, text, recentMessages, recalled)

            var fullResponse = ""
            currentProvider?.generateStreaming(prompt)?.collect { response ->
                when (response) {
                    is ModelResponse.Partial -> {
                        fullResponse += response.text
                        _uiState.value = _uiState.value.copy(captionText = fullResponse)
                    }
                    is ModelResponse.Complete -> {
                        if (fullResponse.isBlank()) {
                            showProviderError("${_uiState.value.selectedModel} returned an empty response")
                        } else {
                            speak(fullResponse)
                            // Episodic checkpoint: resume exactly where we left off
                            if (!_uiState.value.memoryPaused) {
                                memoryRepository?.saveEpisodicCheckpoint(text, fullResponse, userMessageId)
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
        AgentRouter.route(text)?.let { return it }
        if (!_uiState.value.agentBrainEnabled) return null
        val registry = agentToolRegistry ?: return null

        hermesBrain?.route(text, registry)?.let { return it }
        // Hermes declined/unreachable, or not configured: on-device brain next

        val provider = currentProvider ?: return null
        return LlmToolCallRouter(
            provider, registry,
            dispatcher = ioDispatcher,
            promptMode = toolCallPromptMode()
        ).route(text)
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
    private fun submitAgentIntent(intent: AgentRouter.AgentIntent) {
        val orchestrator = agentOrchestrator ?: run {
            speak("Agents aren't available in this build — the workspace isn't configured.")
            return
        }
        when (val result = orchestrator.submit(intent.agentId, intent.toolId, intent.args, intent.title)) {
            is SubmitResult.Rejected -> speak(result.reason)
            is SubmitResult.Started -> {
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

    /** Explicit "remember that …" / "remember this" commands. */
    private fun handleRememberCommand(originalText: String, command: MemoryCommandParser.MemoryCommand) {
        responseJob = viewModelScope.launch {
            if (memoryRepository == null) {
                speak("Memory storage isn't configured yet.")
                return@launch
            }
            memoryRepository.addMessage(MessageRole.USER, originalText)

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
    private suspend fun recallMemories(text: String): List<RecallResult> {
        val repo = memoryRepository ?: return emptyList()
        if (_uiState.value.memoryPaused) return emptyList()
        val candidates = repo.getRecentMemories(200) ?: return emptyList()
        if (candidates.isEmpty()) return emptyList()
        val queryEmbedding = embedder.getEmbedding(text)
        return RecallEngine.rank(queryEmbedding, text, candidates)
    }

    private fun speak(text: String) {
        viewModelScope.launch {
            memoryRepository?.addMessage(MessageRole.ASSISTANT, text)
        }

        if (ttsManager == null) {
            startMockSpeaking(text)
            return
        }

        selfVoiceFilter.begin(text)
        audioFocusManager?.requestFocus()
        lastAssistantText = text
        _uiState.value = _uiState.value.copy(
            captionText = text,
            emotion = CompanionEmotion.HAPPY,
            mouthEnergy = 0f
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
            resetState()
        }
    }

    // ── Interruption & conversation flow ────────────────────────────────────

    /**
     * User interruption: stop speaking immediately, cancel the response, and
     * listen for the new command.
     */
    fun interrupt() {
        if (_uiState.value.interactionState != InteractionState.SPEAKING) return
        responseJob?.cancel()
        suppressSpeechFinished = true
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
        val s = _uiState.value
        if (s.conversationMode && s.isMicEnabled && !s.isMuted && speechService != null) {
            startFollowUpListening()
        } else {
            resetState()
        }
    }

    /** Conversation mode: stay for a follow-up question, then time out to idle. */
    private fun startFollowUpListening() {
        val engine = speechService ?: run {
            resetState()
            return
        }
        isFollowUpListen = true
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.LISTENING,
            captionText = "Listening for follow-up…",
            emotion = CompanionEmotion.ALERT
        )
        engine.startListening(
            onResult = { result -> processUserSpeech(result) },
            onError = { _ -> resetState() }
        )
        followUpJob = viewModelScope.launch {
            delay(FOLLOW_UP_TIMEOUT_MS)
            if (_uiState.value.interactionState == InteractionState.LISTENING && isFollowUpListen) {
                speechService.stopListening()
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
        return normalized in STOP_COMMANDS
    }

    // ── Mouth synchronization ───────────────────────────────────────────────

    /** TTS hit a word boundary: pulse the mouth, then let the energy decay. */
    private fun bumpMouthEnergy() {
        _uiState.value = _uiState.value.copy(mouthEnergy = 1f)
        mouthDecayJob?.cancel()
        mouthDecayJob = viewModelScope.launch {
            for (level in listOf(0.7f, 0.45f, 0.25f)) {
                delay(70)
                _uiState.value = _uiState.value.copy(mouthEnergy = level)
            }
        }
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
                    wakeWordEngine?.stop()
                    speechService?.stopListening()
                }
                _uiState.value = _uiState.value.copy(
                    isMicEnabled = newMicState,
                    interactionState = if (!newMicState) InteractionState.MUTED else InteractionState.IDLE
                )
            }
            AvatarZone.EYE_LEFT, AvatarZone.EYE_RIGHT -> {
                _uiState.value = _uiState.value.copy(isCameraEnabled = !_uiState.value.isCameraEnabled)
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
        _uiState.value = _uiState.value.copy(isChatExpanded = !_uiState.value.isChatExpanded)
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

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun toggleModelManager() {
        _uiState.value = _uiState.value.copy(showModelManager = !_uiState.value.showModelManager)
    }

    fun onModelSelected(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
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
        if (existing != null && !existing.done && existing.error == null) return  // already running

        viewModelScope.launch {
            setDownloadProgress(option.id, ModelDownloader.DownloadProgress())
            try {
                val file = dl.download(option.downloadUrl, option.fileName) { progress ->
                    setDownloadProgress(option.id, progress)
                }
                installDownloadedModel(file.absolutePath)
            } catch (e: Exception) {
                setDownloadError(option.id, e.message ?: "Download failed")
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
            !ModelCatalog.isValidModelFileName(fileName) ->
                return setDownloadError(id, "File name must be simple and end in .task")
        }
        viewModelScope.launch {
            setDownloadProgress(id, ModelDownloader.DownloadProgress())
            try {
                val file = dl.download(url, fileName) { progress ->
                    setDownloadProgress(id, progress)
                }
                installDownloadedModel(file.absolutePath)
            } catch (e: Exception) {
                setDownloadError(id, e.message ?: "Download failed")
            }
        }
    }

    /** Point the local provider at an already-downloaded catalog model. */
    fun useDownloadedModel(option: LocalModelOption) {
        val file = downloader?.installedFile(option) ?: return
        installDownloadedModel(file.absolutePath)
        if (!_uiState.value.selectedModel.contains("(Local)")) {
            onModelSelected("Gemma 2b (Local)")
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
        _uiState.value = _uiState.value.copy(hermesAgentUrl = clean)
        secureStorage?.savePreference(KEY_HERMES_URL, clean)
        rebuildHermesBrain()
    }

    private fun rebuildHermesBrain() {
        hermesBrain = _uiState.value.hermesAgentUrl
            .takeIf { it.isNotBlank() }
            ?.let { HermesAgentBrain(HermesConfig(it), dispatcher = ioDispatcher) }
    }

    // ── Memory screen & controls ────────────────────────────────────────────

    fun toggleMemoryScreen() {
        _uiState.value = _uiState.value.copy(showMemoryScreen = !_uiState.value.showMemoryScreen)
    }

    /** Full-text search on the Memory screen. Blank query clears search mode. */
    fun searchMemories(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(memorySearchResults = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                memorySearchResults = memoryRepository?.searchMemories(query) ?: emptyList()
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository?.deleteMemoryById(id)
        }
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

    private fun resetState() {
        followUpJob?.cancel()
        isFollowUpListen = false
        _uiState.value = _uiState.value.copy(
            interactionState = InteractionState.IDLE,
            captionText = null,
            emotion = CompanionEmotion.IDLE,
            errorMessage = null,
            mouthEnergy = 0f
        )
        audioFocusManager?.abandonFocus()
        if (_uiState.value.wakeWordEnabled) startWakeStandby()
    }

    companion object {
        private const val KEY_COMPANION_NAME = "companion_name"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_CONVERSATION = "conversation_mode"
        private const val KEY_MEMORY_PAUSED = "memory_paused"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_AGENT_BRAIN = "agent_brain_enabled"
        private const val KEY_HERMES_URL = "hermes_agent_url"
        private const val DEFAULT_RETENTION_DAYS = 90
        private const val FOLLOW_UP_TIMEOUT_MS = 8000L
        private const val ERROR_DISPLAY_MS = 3500L

        /** Phrases that politely end conversation mode. */
        private val STOP_COMMANDS = setOf(
            "stop", "never mind", "nevermind", "that's all", "thats all",
            "thank you", "thanks", "stop listening", "goodbye", "bye"
        )
    }
}

/**
 * Defines interactive zones on the avatar screen.
 */
enum class AvatarZone {
    EAR_LEFT, EAR_RIGHT, EYE_LEFT, EYE_RIGHT, MOUTH, FOREHEAD, BACKGROUND
}
