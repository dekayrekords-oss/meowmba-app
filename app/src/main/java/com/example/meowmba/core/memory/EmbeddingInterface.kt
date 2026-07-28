package com.example.meowmba.core.memory

/**
 * Interface for generating vector embeddings.
 */
interface EmbeddingInterface {
    suspend fun getEmbedding(text: String): FloatArray
}

/**
 * Mock implementation for Phase 4.
 */
class MockEmbeddingProvider : EmbeddingInterface {
    override suspend fun getEmbedding(text: String): FloatArray {
        // Return a dummy vector of 128 dimensions
        return FloatArray(128) { it.toFloat() }
    }
}
