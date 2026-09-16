package com.example.meowmba.core.agent.tools

import android.content.Context
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.PlannerReadiness
import com.example.meowmba.core.setup.SetupWiring
import kotlinx.coroutines.*
import java.io.File

/** No credentials are returned to the model. A switch is verified before
 * persisting, and cannot alter a phone-control task already in progress. */
class AppModelSwitcher(private val context: Context, private val busy: () -> Boolean, private val changed: () -> Unit) {
    private val storage get() = SecureStorageManager(context)
    private val directory get() = File(context.filesDir, "models").canonicalFile
    fun choices(): String {
        val local = directory.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == directory && it.extension in setOf("task", "litertlm") }
            .sortedBy { it.name }.take(40).joinToString("\n") { "local | ${it.name} | ${it.length() / 1_000_000} MB" }
        val s = storage
        val cloud = if (!s.getApiKey("openrouter").isNullOrBlank() && s.getPreference("planner_verified_openrouter") == PlannerReadiness.cloudStamp(s.getModelName("openrouter"), s.getApiKey("openrouter")))
            "openrouter | ${s.getModelName("openrouter")} | previously verified; cloud data sharing and account pricing apply"
        else "OpenRouter not verified: open Your AI setup to add a key, choose a model and review pricing."
        return "Selected: ${s.getPreference("selected_model")}\n${local.ifBlank { "No compatible local models installed." }}\n$cloud"
    }
    suspend fun select(provider: String, model: String): String = withContext(Dispatchers.IO) {
        check(!busy()) { "Finish or stop the current phone/desktop task before changing its model." }
        val s = storage
        when (provider.lowercase()) {
            "local" -> {
                require(model == File(model).name && model.isNotBlank()) { "Choose an exact filename from brain.models." }
                val file = File(directory, model).canonicalFile
                require(file.parentFile == directory && file.isFile && file.extension in setOf("litertlm", "task")) { "Model isn't an installed compatible conversation model." }
                val candidate = if (file.extension == "litertlm") LiteRtProvider(context, file.path) else MediaPipeProvider(context, file.path)
                try { PlannerReadiness.verify(candidate) }
                finally { if (candidate is MediaPipeProvider) withContext(NonCancellable) { candidate.release() } }
                check(!busy()) { "A phone task started during verification; selection was not changed." }
                s.saveEndpoint("local", file.path); s.savePreference("planner_verified_local", file.path)
                SetupWiring.setBrain(context, "local", null, null)
                s.savePreference("selected_model", "On-device (Local)")
            }
            "openrouter" -> {
                val key = s.getApiKey("openrouter")
                require(!key.isNullOrBlank() && model == s.getModelName("openrouter") && s.getPreference("planner_verified_openrouter") == PlannerReadiness.cloudStamp(model, key)) {
                    "Choose the previously verified OpenRouter model from brain.models. New models/keys and pricing require Your AI setup first."
                }
                RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", key, model).use { PlannerReadiness.verify(it) }
                check(!busy()) { "A phone task started during verification; selection was not changed." }
                SetupWiring.setBrain(context, "openrouter", key, model)
                s.savePreference("selected_model", "OpenRouter (Cloud)")
            }
            else -> error("Choose local or openrouter from brain.models.")
        }
        withContext(Dispatchers.Main) { changed() }
        "Verified and selected $provider / $model. Existing downloads and keys were preserved."
    }
}

class ListBrainModelsTool(private val list: () -> String) : Tool {
    override val definition = ToolDefinition("brain.models", "Available AI models", "List installed local models and the previously verified OpenRouter option; never reveals keys.", emptyMap(), riskLevel = RiskLevel.SAFE)
    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) { ToolResult.Success(list()) }
}

class SelectBrainModelTool(private val select: suspend (String, String) -> String) : Tool {
    override val definition = ToolDefinition(
        id = "brain.select_model", displayName = "Verify and change AI model",
        description = "Change to an installed local model or the previously verified OpenRouter model. OpenRouter sends requests and approved app context off the phone and may incur the selected model's account charges. Review provider/model before approving. No new paid model or fallback is selected automatically.",
        inputSchema = mapOf("provider" to "local|openrouter", "model" to "exact model identifier from brain.models"),
        riskLevel = RiskLevel.MODERATE, confirmationPolicy = ConfirmationPolicy.ALWAYS, timeoutMs = 150_000,
    )
    override suspend fun execute(args: Map<String, String>): ToolResult = try {
        ToolResult.Success(select(args["provider"].orEmpty(), args["model"].orEmpty()))
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { ToolResult.Failure(e.message ?: "Model switch failed; previous selection kept") }
}
