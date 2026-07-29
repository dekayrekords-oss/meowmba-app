package com.example.meowmba.core.agent

/**
 * Curated on-device model catalog.
 *
 * Every entry was verified against the Hugging Face API (2026-07):
 *  - URLs resolve to real MediaPipe/LiteRT `.task` bundles
 *  - `requiresHfToken = false` entries are truly ungated (Apache 2.0 repos,
 *    plain HTTPS download, no account)
 *  - `requiresHfToken = true` entries are license-gated (Gemma) and need a
 *    free Hugging Face token after accepting the license in a browser
 *
 * Any other model can still be downloaded via a custom URL — the catalog is
 * guidance, not a fence. Pure Kotlin, unit-testable.
 */
data class LocalModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val approxSize: String,
    val downloadUrl: String,
    /** Destination file name inside the app's private models directory. */
    val fileName: String,
    val minRamGb: Int,
    val requiresHfToken: Boolean,
    val recommended: Boolean = false
)

object ModelCatalog {

    private const val HF = "https://huggingface.co"

    val options: List<LocalModelOption> = listOf(
        LocalModelOption(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B IT",
            description = "Google's mobile-first model. Apache 2.0, no token needed. Best quality for 11 GB devices.",
            approxSize = "~2.59 GB",
            downloadUrl = "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            minRamGb = 4,
            requiresHfToken = false,
            recommended = true
        ),
        LocalModelOption(
            id = "qwen-0.5b",
            displayName = "Qwen 2.5 0.5B Instruct",
            description = "Tiny. Runs on any phone, instant routing decisions.",
            approxSize = "~0.6 GB",
            downloadUrl = "$HF/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
                "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-0.5b-instruct-q8.task",
            minRamGb = 3,
            requiresHfToken = false
        ),
        LocalModelOption(
            id = "qwen-1.5b",
            displayName = "Qwen 2.5 1.5B Instruct",
            description = "Best balance of quality and footprint. No account needed.",
            approxSize = "~1.7 GB",
            downloadUrl = "$HF/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-1.5b-instruct-q8.task",
            minRamGb = 4,
            requiresHfToken = false
        ),
        LocalModelOption(
            id = "qwen-1.5b-lc",
            displayName = "Qwen 2.5 1.5B (long context)",
            description = "Same model with a 4096-token window — better for memory recall prompts.",
            approxSize = "~1.8 GB",
            downloadUrl = "$HF/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            fileName = "qwen2.5-1.5b-instruct-q8-4096.task",
            minRamGb = 4,
            requiresHfToken = false
        ),
        LocalModelOption(
            id = "gemma-3-1b",
            displayName = "Gemma 3 1B IT",
            description = "Google's smallest full-quality model. Needs a free Hugging Face token.",
            approxSize = "~0.6 GB",
            downloadUrl = "$HF/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            fileName = "gemma3-1b-it-int4.task",
            minRamGb = 3,
            requiresHfToken = true
        )
    )

    fun optionForFile(fileName: String): LocalModelOption? =
        options.find { it.fileName == fileName }

    /**
     * Filename safety for custom downloads: simple name only (no paths or
     * traversal), and it must be a MediaPipe-runnable bundle.
     */
    fun isValidModelFileName(name: String): Boolean =
        name.matches(Regex("[a-zA-Z0-9._-]+")) &&
            (name.endsWith(".task") || name.endsWith(".litertlm"))
}
