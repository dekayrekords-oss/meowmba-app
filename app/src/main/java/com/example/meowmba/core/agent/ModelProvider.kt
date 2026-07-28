package com.example.meowmba.core.agent

import kotlinx.coroutines.flow.Flow

/**
 * Interface for AI model providers.
 * Supports streaming and one-shot generation.
 */
interface ModelProvider {
    val name: String
    
    /**
     * Generates a response from the model.
     * @param prompt The user input.
     * @return A Flow of partial results (streaming).
     */
    fun generateStreaming(prompt: String): Flow<ModelResponse>

    /**
     * Generates a complete response from the model.
     */
    suspend fun generate(prompt: String): ModelResponse
}

sealed class ModelResponse {
    data class Partial(val text: String) : ModelResponse()
    data class Complete(val text: String) : ModelResponse()
    data class Error(val message: String) : ModelResponse()
}
