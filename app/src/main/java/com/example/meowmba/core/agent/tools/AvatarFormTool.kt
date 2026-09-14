package com.example.meowmba.core.agent.tools

import com.example.meowmba.ui.companion.AvatarAnimal
import kotlinx.coroutines.CancellationException

/**
 * Changes the one local Lumen avatar's selected appearance, not its identity or
 * physical body. The host callback must apply/persist the selection and throw
 * if it cannot do so; returning successfully is the tool's completion boundary.
 */
class AvatarFormTool(private val onChange: (AvatarAnimal) -> Unit) : Tool {
    private val supported: String
        get() = AvatarAnimal.entries.joinToString(", ") {
            if (it == AvatarAnimal.NONE) "eyes only (none)" else it.key
        }

    override val definition = ToolDefinition(
        id = "avatar.form",
        displayName = "Change avatar form",
        description = "Change Lumen's saved local on-screen form when the user explicitly asks " +
            "to become, be, turn into, or look like an animal or classic helper (Clippy or the wizard). Supported forms: $supported. " +
            "This is a reversible appearance setting for the same avatar, not a new character, " +
            "generated image/video/asset/file, or robot movement. For a short 'act like' or " +
            "'pretend' performance use avatar.animate instead. Do not change form merely " +
            "because the user mentions a pet. Unsupported forms must fail, never be substituted.",
        inputSchema = mapOf("animal" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val requested = args["animal"]?.trim()
        if (requested.isNullOrEmpty()) {
            return ToolResult.Failure("Missing 'animal'. Supported forms: $supported.")
        }
        if (requested.length > 64 || requested.any { it.isISOControl() }) {
            return ToolResult.Failure("Invalid 'animal': use one short form name. Supported forms: $supported.")
        }
        val animal = AvatarAnimal.resolve(requested)
            ?: return ToolResult.Failure("Unsupported avatar form '$requested'. Supported forms: $supported.")
        return try {
            onChange(animal)
            ToolResult.Success("Lumen avatar form set to ${animal.label}.")
        } catch (cancelled: CancellationException) {
            // Cancellation must remain cancellation; it cannot manufacture a
            // success or keep a superseded form-change task alive.
            throw cancelled
        } catch (error: Exception) {
            val detail = error.message?.filterNot { it.isISOControl() }?.take(160)
                ?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            ToolResult.Failure("Could not change Lumen's avatar form: $detail. Supported forms: $supported.")
        }
    }
}
