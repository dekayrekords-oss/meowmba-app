package com.example.meowmba.core.media

interface VideoGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, onProgress: (String) -> Unit = {}): Result<ByteArray>
}
