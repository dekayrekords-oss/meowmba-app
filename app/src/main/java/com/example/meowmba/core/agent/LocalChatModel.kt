package com.example.meowmba.core.agent

import java.io.File

/** Routing weights and image checkpoints are never conversational LLM bundles. */
object LocalChatModel {
    fun supports(file: File): Boolean = file.extension.lowercase() in setOf("task", "litertlm", "bin") &&
        ModelCatalog.optionForFile(file.name)?.runtime?.canActivate != false

    fun resolve(configured: String?, installed: List<File>): File? {
        val explicit = configured?.let(::File)
        if (explicit != null && supports(explicit) && explicit.isFile && explicit.length() > 0) return explicit
        val available = installed.filter { supports(it) && it.isFile && it.length() > 0 }
        return ModelCatalog.options.asSequence().filter { it.runtime.canActivate }
            .mapNotNull { option -> available.firstOrNull { it.name == option.fileName } }.firstOrNull()
            ?: available.firstOrNull()
    }

    fun errorSummary(message: String): String? = when {
        listOf("model_data.cc", "building tflite model", "Failed to initialize engine", "not a chat model", "Model file not found")
            .any { message.contains(it, ignoreCase = true) } ->
            "The local AI model could not load. Open Your AI setup to select and test a compatible chat model, or re-download it. Needle is a tool router, not a chat model."
        else -> null
    }
}
