package com.example.meowmba.core.work

import com.example.meowmba.core.database.ProjectDao
import com.example.meowmba.core.database.ProjectEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * One creation at a time, never a queue of repeated ASR callbacks. A recently
 * repeated request refers to the record actually inserted rather than creating
 * "Project 2", "Project 3", etc. The durable reservation is kept even when workspace setup
 * or cancellation interrupts after the insert, so a retry repairs that record.
 *
 * This is local workspace allocation only. It does not draft, run a plan, speak,
 * change the selected conversation, or mark work complete. Those effects belong
 * to the current user turn after [Result.Ready] and its saved files are verified.
 */
class ProjectCreationService(
    private val dao: ProjectDao,
    private val work: ProjectWorkStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data class Ready(val project: ProjectEntity, val created: Boolean) : Result
        data object Busy : Result
        data object Superseded : Result
    }

    private val journal get() = work.allocationJournal

    suspend fun create(req: ProjectStart, objective: String, isCurrent: () -> Boolean = { true }): Result {
        // Validation precedes acquiring the slot or allocating any record.
        val name = req.name.trim()
        require(name.isNotEmpty() && name.length <= 80) { "Choose a project name from 1 to 80 characters" }
        require(objective.length <= ProjectWorkStore.MAX_OBJECTIVE) { "Keep the project objective under ${ProjectWorkStore.MAX_OBJECTIVE} characters" }
        if (!mutex.tryLock()) return Result.Busy
        try {
            return withContext(dispatcher) {
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withContext Result.Superseded
                val key = MessageDigest.getInstance("SHA-256").digest(
                    listOf(normalize(name), req.kind.name, normalize(objective)).joinToString("\u0000").toByteArray()
                ).joinToString("") { "%02x".format(it) }
                val startedAt = now()
                val records = journal.read().filterNot { it.verified && startedAt - it.at > REPEAT_WINDOW_MS }.toMutableList()
                val allProjects = dao.getAll().first()
                var receipt = records.find { it.key == key }
                if (receipt == null) {
                    check(records.size < MAX_RECEIPTS) { "Several projects are still being set up. Review Projects before creating another." }
                    val names = allProjects.map { normalize(it.name) }.toSet()
                    val unique = if (normalize(name) !in names) name else
                        generateSequence(2) { it + 1 }.map { "$name $it" }.first { normalize(it) !in names }
                    val priorConversations = allProjects.map { it.conversationId } + records.map { it.conversationId }
                    val latest = priorConversations.maxOrNull() ?: 0L
                    check(latest < Long.MAX_VALUE) { "Project conversation identity exhausted" }
                    val conversation = maxOf(1L, startedAt, latest + 1)
                    receipt = ProjectAllocationJournal.Receipt(key, unique, req.kind.name, startedAt, conversation)
                    records += receipt
                    // If the process dies at any later point, this reservation
                    // identifies a committed Room row or the one pending insert.
                    journal.save(records)
                }
                fun saveReceipt(next: ProjectAllocationJournal.Receipt) {
                    records[records.indexOfFirst { it.key == key }] = next
                    journal.save(records)
                    receipt = next
                }
                val reserved = checkNotNull(receipt)
                val matches = allProjects.filter { it.conversationId == reserved.conversationId }
                check(matches.size <= 1) { "Project allocation is ambiguous. Review Projects before continuing." }
                val existing = if (reserved.projectId > 0) dao.getById(reserved.projectId) else matches.singleOrNull()
                check(reserved.projectId == 0L || existing != null) { "The previously allocated project could not be verified. Open Projects before trying another creation." }
                val project: ProjectEntity
                val created: Boolean
                if (existing != null) {
                    check(existing.name == reserved.name && existing.kind == reserved.kind &&
                        existing.createdAt == reserved.at && existing.conversationId == reserved.conversationId) {
                        "The saved project changed. Open it from Projects before continuing."
                    }
                    project = existing
                    created = false
                    if (reserved.projectId == 0L) saveReceipt(reserved.copy(projectId = existing.id))
                } else {
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) return@withContext Result.Superseded
                    val candidate = ProjectEntity(name = reserved.name, kind = reserved.kind,
                        createdAt = reserved.at, lastOpenedAt = reserved.at, conversationId = reserved.conversationId)
                    project = withContext(NonCancellable) {
                        val saved = candidate.copy(id = dao.insert(candidate))
                        check(saved.id > 0) { "Project storage did not return a saved record" }
                        saveReceipt(reserved.copy(projectId = saved.id))
                        saved
                    }
                    created = true
                }
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withContext Result.Superseded
                check(dao.getById(project.id) == project) { "The project record could not be verified. Open Projects before retrying." }
                val saved = work.ensure(project.id, project.name, project.kind, objective)
                check(saved.projectId == project.id && saved.name == project.name && saved.kind == project.kind) {
                    "The saved project workspace could not be verified"
                }
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@withContext Result.Superseded
                saveReceipt(checkNotNull(receipt).copy(verified = true))
                Result.Ready(project, created)
            }
        } finally { mutex.unlock() }
    }

    companion object {
        // Shared across ViewModel/service recreation within the process.
        private val mutex = Mutex()
        private const val REPEAT_WINDOW_MS = 60_000L
        private const val MAX_RECEIPTS = 16
        private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }
}
