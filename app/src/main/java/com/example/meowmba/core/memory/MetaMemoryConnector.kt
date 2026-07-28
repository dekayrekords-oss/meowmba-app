package com.example.meowmba.core.memory

import com.example.meowmba.core.database.MemoryEntity

/**
 * Adapter for the Memba Meta Memory ecosystem.
 *
 * Per the architecture contract, the companion's memory storage must never be
 * coupled to one remote service: local Room storage is the source of truth
 * and sync is an optional adapter. The Meta Memory app's IPC/API contract is
 * not yet published to this project, so the default implementation is an
 * honest no-op — when the contract lands, a real connector implements this
 * interface without touching callers.
 */
interface MetaMemoryConnector {
    /** True when the Meta Memory app/service is reachable and paired. */
    suspend fun isAvailable(): Boolean

    /** Push local memories to the Meta Memory service. Returns synced count. */
    suspend fun push(memories: List<MemoryEntity>): Result<Int>

    /** Pull remote memories for local merge. Conflict resolution is last-write-wins. */
    suspend fun pull(): Result<List<MemoryEntity>>
}

/**
 * Default connector: clearly-marked placeholder until the Meta Memory app
 * exposes its sync contract. Never pretends a sync happened.
 */
class NoOpMetaMemoryConnector : MetaMemoryConnector {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun push(memories: List<MemoryEntity>): Result<Int> =
        Result.failure(UnsupportedOperationException("Meta Memory sync is not available yet"))
    override suspend fun pull(): Result<List<MemoryEntity>> =
        Result.failure(UnsupportedOperationException("Meta Memory sync is not available yet"))
}
