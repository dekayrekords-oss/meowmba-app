package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.agent.runtime.AvatarFormRequest
import com.example.meowmba.ui.companion.AvatarAnimal
import kotlinx.coroutines.CancellationException

/**
 * Changes this avatar's saved appearance, keeping its identity and brain. The
 * host applies/persists a preset or generates and validates a native form before
 * returning success. The one-argument constructor keeps preset-only hosts valid.
 */
class AvatarFormTool(
    private val onChange: (AvatarAnimal) -> Unit,
    private val onGenerate: (suspend (String, Long?, String?) -> ToolResult)?,
    private val validateGeneration: (Long?) -> Unit,
) : Tool {
    constructor(onChange: (AvatarAnimal) -> Unit) : this(onChange, null, {})
    constructor(onChange: (AvatarAnimal) -> Unit, onGenerate: suspend (String, Long?) -> ToolResult) :
        this(onChange, { req, gen, _ -> onGenerate(req, gen) }, {})

    private val supported: String
        get() = AvatarAnimal.entries.joinToString(", ") {
            if (it == AvatarAnimal.NONE) "eyes only (none)" else it.key
        }

    override val definition = ToolDefinition(
        id = "avatar.form",
        displayName = "Change avatar form",
        description = "Change the saved on-screen avatar when the user explicitly asks to become, " +
            "turn into, or look like something. Give the complete visual description in animal, " +
            "including colors and accessories (up to 720 characters). Built-in forms are immediate: $supported. " +
            "Other descriptions use the selected AI model to design and save a new animated cartoon form; " +
            "saved matching forms can be reused. This is the same companion and brain with a new appearance. " +
            "It is not an image/video file generator or physical robot action. For short 'act like' or " +
            "'pretend' performances use avatar.animate. Do not transform merely because something is mentioned. " +
            "New designs must be applied successfully before claiming the avatar changed.",
        inputSchema = mapOf("animal" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // Host creation has a 90-second deadline; the orchestrator must leave
        // time to validate, persist and report that result rather than cut it at 30s.
        timeoutMs = 130_000L, // thorough invent may run ~120s
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val requested = args["animal"]?.trim()
        if (requested.isNullOrEmpty()) {
            return ToolResult.Failure("Describe the avatar's new form in 'animal' (1-720 characters).")
        }
        if (!AvatarFormRequest.isDescription(requested)) {
            return ToolResult.Failure("Describe one avatar appearance in 1-720 characters, with colors or accessories. Send other actions separately.")
        }
        val animal = AvatarAnimal.resolve(requested)
        return try {
            val generation = args["_form_generation"]?.toLongOrNull()
            // Even an immediate preset can have waited in the agent queue.
            // Reject superseded jobs before either appearance callback runs.
            validateGeneration(generation)
            if (animal != null) {
                onChange(animal)
                ToolResult.Success("Lumen avatar form set to ${animal.label}.")
            } else {
                val generate = onGenerate ?: return ToolResult.Failure(
                    "Unsupported avatar form '$requested' in this host: new-form generation is unavailable. Supported forms: $supported.",
                )
                generate(requested, generation, args["reason_level"])
            }
        } catch (cancelled: CancellationException) {
            // A stopped/superseded generation must never become a false success.
            throw cancelled
        } catch (error: Exception) {
            val detail = error.message?.filterNot { it.isISOControl() }?.take(160)
                ?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            ToolResult.Failure("Could not change Lumen's avatar form: $detail. Supported forms: $supported.")
        }
    }
}
