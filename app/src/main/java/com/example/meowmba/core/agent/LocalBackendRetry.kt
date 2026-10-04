package com.example.meowmba.core.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Retry only a known GPU buffer failure before any answer was delivered.
 * The primary flow must finish native cleanup before its CPU replacement starts.
 * Cancellation and downstream failures propagate; neither authorizes a retry.
 */
internal object LocalBackendRetry {
    fun stream(
        primary: () -> Flow<ModelResponse>,
        gpuUsed: () -> Boolean,
        cpu: () -> Flow<ModelResponse>,
    ): Flow<ModelResponse> = flow {
        var answerStarted = false
        var retry = false
        primary().collect { response ->
            when (response) {
                is ModelResponse.Partial -> {
                    if (response.text.isNotEmpty()) answerStarted = true
                    emit(response)
                }
                is ModelResponse.Complete -> {
                    answerStarted = true
                    emit(response)
                }
                is ModelResponse.Error -> {
                    if (!answerStarted && gpuUsed() && bufferFailure(response.message)) retry = true
                    else emit(response)
                }
            }
        }
        if (retry && !answerStarted) emitAll(cpu())
    }

    private fun bufferFailure(message: String): Boolean =
        "convert_tensor_buffer.h" in message || "litert_tensor_buffer.h" in message
}
