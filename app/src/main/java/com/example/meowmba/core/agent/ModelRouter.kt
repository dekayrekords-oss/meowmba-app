package com.example.meowmba.core.agent

import kotlinx.coroutines.flow.Flow

/**
 * Routes requests between a lightweight model (quick tasks, low battery)
 * and a main model (heavy reasoning, complex tasks).
 *
 * Light model handles: speech recognition, summarization, quick image
 * captions, wake-word follow-up, low-latency offline commands, and any
 * task where battery life matters.
 *
 * Main model handles: personality, multi-step reasoning, coding, custom
 * function calling, agent planning, system prompt control, LoRA / fine-
 * tuned behavior, live video analysis, complex image generation.
 */
class ModelRouter(
    private val lightProvider: ModelProvider?,
    private val mainProvider: ModelProvider?
) : ModelProvider {

    override val name: String
        get() = "Router(${lightProvider?.name ?: "none"} + ${mainProvider?.name ?: "none"})"

    /** Classify a prompt as light or heavy. */
    fun classify(prompt: String): RouteTarget {
        val cleaned = prompt.trim().lowercase()

        // Always route to main when no light provider is available
        if (lightProvider == null) return RouteTarget.MAIN

        // Light-model indicators: very short, simple commands, factoid questions
        val lightPatterns = listOf(
            "summarize", "summarise", "rewrite", "rephrase", "shorten", "expand",
            "translate", "what time", "what day", "set a timer", "set timer",
            "remind me", "note that", "take a note", "remember that",
            "what did i say", "what was that", "say that again",
            "describe this", "what do you see", "caption this",
            "yes", "no", "okay", "ok", "thanks", "thank you",
            "hello", "hi", "hey", "yo", "sup",
            "stop", "cancel", "never mind", "ignore that",
            "go to sleep", "wake up", "shut up", "be quiet"
        )
        if (lightPatterns.any { cleaned.startsWith(it) }) return RouteTarget.LIGHT

        // Very short queries (< 5 words, no punctuation beyond basic) → light
        val wordCount = cleaned.split(Regex("\\s+")).size
        if (wordCount <= 4 && !cleaned.contains(Regex("[{}();#=]"))) {
            return RouteTarget.LIGHT
        }

        // Multi-line or code-like content → main
        if (cleaned.contains("\n") || cleaned.contains(Regex("```|def |class |fun |import |#include"))) {
            return RouteTarget.MAIN
        }

        // Agent/tool commands → main
        if (cleaned.contains(Regex("(create|delete|write|read|list|run|execute|build|deploy) (file|project|task|tool|agent|code)"))) {
            return RouteTarget.MAIN
        }

        // Complex reasoning indicators → main
        val mainPatterns = listOf(
            "because", "explain", "compare", "contrast", "analyze", "evaluate",
            "what if", "how would", "why is", "why does", "why did",
            "write a", "write an", "write code", "implement", "debug",
            "refactor", "design", "architecture",
            "plan", "strategy", "roadmap",
            "generate an image", "generate image", "create an image",
            "live video", "analyze this video", "what is happening"
        )
        if (mainPatterns.any { cleaned.contains(it) }) return RouteTarget.MAIN

        // Default: longer or ambiguous queries go to main
        return if (wordCount > 10) RouteTarget.MAIN else RouteTarget.LIGHT
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> {
        return when (classify(prompt)) {
            RouteTarget.LIGHT -> lightProvider?.generateStreaming(prompt)
                ?: mainProvider?.generateStreaming(prompt)
                ?: flowOf(ModelResponse.Error("No model available"))
            RouteTarget.MAIN -> mainProvider?.generateStreaming(prompt)
                ?: lightProvider?.generateStreaming(prompt)
                ?: flowOf(ModelResponse.Error("No model available"))
        }
    }

    override suspend fun generate(prompt: String): ModelResponse {
        return when (classify(prompt)) {
            RouteTarget.LIGHT -> lightProvider?.generate(prompt)
                ?: mainProvider?.generate(prompt)
                ?: ModelResponse.Error("No model available")
            RouteTarget.MAIN -> mainProvider?.generate(prompt)
                ?: lightProvider?.generate(prompt)
                ?: ModelResponse.Error("No model available")
        }
    }

    /** Which model should handle this request. */
    enum class RouteTarget { LIGHT, MAIN }
}

private fun <T> flowOf(value: T): Flow<T> = kotlinx.coroutines.flow.flowOf(value)
