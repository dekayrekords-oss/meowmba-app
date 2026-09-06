package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.audio.WakePhraseMatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cheap pre-filter: only spend a model call on text that smells like a tool
 * command. Keyword commands never reach the model at all (zero-latency fast
 * path), and plain conversation never triggers a routing roundtrip.
 */
object CommandHeuristics {
    private val SIGNAL_WORDS = setOf(
        // Files, projects, memory
        "create", "make", "file", "files", "assets", "library", "organize", "cleanup", "clean", "folder", "project", "delete", "remove",
        "read", "open", "write", "save", "search", "find", "list", "show",
        "task", "workspace", "memory", "remember", "note", "scaffold", "todo",
        // Email and calendar — these tools are registered but were unreachable
        // without their own signal words, so the router never saw the request.
        "email", "mail", "send", "draft", "reply", "compose", "inbox",
        "calendar", "event", "schedule", "meeting", "appointment", "remind",
        // Media generation
        "image", "picture", "photo", "draw", "paint", "sketch", "render",
        "generate", "video", "animation", "animate", "song", "audio", "sound",
        // Phone and desktop control
        "app", "launch", "start", "call", "text", "settings", "camera",
        "run", "script", "build", "desktop", "computer",
        // Phone control (accessibility-based)
        "tap", "click", "press", "swipe", "scroll", "type", "back",
        "control", "navigate", "play", "pause", "stop", "next", "previous",
        // Avatar performance + follow-up affirmations. Measured on device:
        // "lick your paw" and a bare "yes" were gated out here, so the CHAT
        // model answered — and it mimicked the tool results in its history,
        // fabricating `Playing "head tilt" (30ms)` with no task in the audit
        // log. A false positive costs one model call that returns null; a
        // false negative here is a lie the user can see.
        "trick", "dance", "perform", "wiggle", "paw", "lick", "gesture",
        "yes", "yeah", "yep", "sure", "ok", "okay", "please",
    )

    fun mightBeCommand(text: String): Boolean {
        val tokens = WakePhraseMatcher.normalize(text).split(' ')
        return tokens.any { it in SIGNAL_WORDS }
    }
}

/**
 * Adapter seam for agent-brain backends (the spec's "don't couple the UI to
 * one agent framework" rule). Implementations: [HermesAgentBrain] (Hermes
 * Agent endpoint), [LlmToolCallRouter]-style on-device routing (wrapped by
 * the ViewModel), or test fakes.
 */
interface AgentBrainRouter {
    suspend fun route(text: String, registry: com.example.meowmba.core.agent.tools.ToolRegistry): AgentRouter.AgentIntent?
}

/**
 * The on-device agent brain.
 *
 * Routing order:
 *  1. Keyword fast path (free, deterministic).
 *  2. Command heuristic gate (free; skips the model for chit-chat).
 *  3. The active [ModelProvider] renders a single-JSON tool decision
 *     (prompt contract + defensive parse). With a local MediaPipe model
 *     selected this never leaves the phone; no agent framework required.
 *
 * The model only ever PROPOSES. The orchestrator's permission checks and
 * the user's approval card still decide what actually runs.
 */
class LlmToolCallRouter(
    private val provider: ModelProvider,
    private val toolRegistry: ToolRegistry,
    private val keywordRouter: (String) -> AgentRouter.AgentIntent? = AgentRouter::route,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val promptMode: ToolCallPromptMode = ToolCallPromptMode.CONTRACT
) {

    suspend fun route(text: String, context: String? = null): AgentRouter.AgentIntent? {
        // 1. Zero-latency keyword path
        val kwResult = keywordRouter(text)
        kwResult?.let { return it }

        // 2. Don't spend a model call on obvious conversation
        if (!CommandHeuristics.mightBeCommand(text)) return null

        // 3. Model-based decision (IO-bound: inference must not block main)
        val prompt = when (promptMode) {
            ToolCallPromptMode.CONTRACT -> ToolCallPrompt.build(text, toolRegistry.definitions(), context)
            ToolCallPromptMode.HERMES_NATIVE -> ToolCallPrompt.buildHermesNative(text, toolRegistry.definitions(), context)
        }
        val response = try {
            withContext(dispatcher) {
                provider.generate(prompt)
            }
        } catch (e: Exception) {
            null
        } ?: return null
        val output = (response as? ModelResponse.Complete)?.text ?: return null

        // Parse must never be able to crash the app: the model's output is
        // untrusted by contract ("garbage yields null"), and an exception here
        // took the whole app down on device.
        val call = runCatching { ToolCallParser.parse(output, toolRegistry) }
            .getOrNull() ?: return null

        // Resolve which available agent owns this tool
        val owner = AgentRegistry.ownerOf(call.toolId) ?: return null

        return AgentRouter.AgentIntent(
            agentId = owner.id,
            toolId = call.toolId,
            args = call.args,
            title = describe(call)
        )
    }

    private fun describe(call: ToolCallParser.ParsedToolCall): String {
        val tool = toolRegistry.byId(call.toolId)
        val keyArg = call.args.values.firstOrNull()?.take(40)
        return listOfNotNull(tool?.definition?.displayName, keyArg).joinToString(": ")
            .ifBlank { call.toolId }
    }
}
