package com.example.meowmba.core.agentic

import com.example.meowmba.core.database.AgenticEventDao
import com.example.meowmba.core.database.AgenticEventEntity
import com.example.meowmba.core.database.AgenticGoalDao
import com.example.meowmba.core.database.AgenticGoalEntity
import com.example.meowmba.core.database.ConnectionProfileDao
import com.example.meowmba.core.database.ConnectionProfileEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Always-empty store for flag-OFF / design-time callers. */
class EmptyAgenticGoalStore : AgenticGoalStore {
    override fun insertGoal(title: String, budgets: AgenticBudgets): AgenticGoal =
        AgenticGoal(id = "", title = title, budgets = budgets)

    override fun getGoal(id: String): AgenticGoal? = null
    override fun updateGoal(goal: AgenticGoal): AgenticGoal? = null
    override fun listOpenGoals(): List<AgenticGoal> = emptyList()
    override fun appendEvent(goalId: String, type: String, detail: String, taskId: String?): AgenticEvent? = null
    override fun listEvents(goalId: String): List<AgenticEvent> = emptyList()
}

/**
 * Room-backed [AgenticGoalStore]. Sync facade over suspend DAOs via IO
 * [runBlocking] — matches the existing sync [AgenticRuntime] API.
 */
class RoomAgenticGoalStore(
    private val goalDao: AgenticGoalDao,
    private val eventDao: AgenticEventDao,
) : AgenticGoalStore {

    private val openish = listOf(
        GoalStatus.OPEN.name,
        GoalStatus.RUNNING.name,
        GoalStatus.BLOCKED.name,
        GoalStatus.WAITING_APPROVAL.name,
    )

    override fun insertGoal(title: String, budgets: AgenticBudgets): AgenticGoal = db {
        val now = System.currentTimeMillis()
        val goal = AgenticGoal(
            id = UUID.randomUUID().toString(),
            title = title,
            status = GoalStatus.OPEN,
            budgets = budgets,
            createdAt = now,
            updatedAt = now,
        )
        goalDao.upsert(goal.toEntity())
        goal
    }

    override fun getGoal(id: String): AgenticGoal? = db {
        goalDao.getById(id)?.toModel()
    }

    override fun updateGoal(goal: AgenticGoal): AgenticGoal? = db {
        if (goalDao.getById(goal.id) == null) return@db null
        val updated = goal.copy(updatedAt = System.currentTimeMillis())
        goalDao.upsert(updated.toEntity())
        updated
    }

    override fun listOpenGoals(): List<AgenticGoal> = db {
        goalDao.listByStatuses(openish).map { it.toModel() }.sortedBy { it.createdAt }
    }

    override fun appendEvent(
        goalId: String,
        type: String,
        detail: String,
        taskId: String?,
    ): AgenticEvent? = db {
        if (goalDao.getById(goalId) == null) return@db null
        val event = AgenticEvent(
            id = UUID.randomUUID().toString(),
            goalId = goalId,
            taskId = taskId,
            type = type,
            detail = detail,
            timestamp = System.currentTimeMillis(),
        )
        eventDao.insert(event.toEntity())
        goalDao.getById(goalId)?.let { g ->
            goalDao.upsert(g.copy(updatedAt = event.timestamp))
        }
        event
    }

    override fun listEvents(goalId: String): List<AgenticEvent> = db {
        eventDao.listForGoal(goalId).map { it.toModel() }
    }

    private fun <T> db(block: suspend () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }
}

private fun AgenticGoal.toEntity() = AgenticGoalEntity(
    id = id,
    title = title,
    status = status.name,
    maxSteps = budgets.maxSteps,
    maxToolCalls = budgets.maxToolCalls,
    maxSshExecs = budgets.maxSshExecs,
    stepsUsed = budgets.stepsUsed,
    toolCallsUsed = budgets.toolCallsUsed,
    sshExecsUsed = budgets.sshExecsUsed,
    approvalGranted = approvalGranted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AgenticGoalEntity.toModel() = AgenticGoal(
    id = id,
    title = title,
    status = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.OPEN),
    budgets = AgenticBudgets(
        maxSteps = maxSteps,
        maxToolCalls = maxToolCalls,
        maxSshExecs = maxSshExecs,
        stepsUsed = stepsUsed,
        toolCallsUsed = toolCallsUsed,
        sshExecsUsed = sshExecsUsed,
    ),
    approvalGranted = approvalGranted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AgenticEvent.toEntity() = AgenticEventEntity(
    id = id,
    goalId = goalId,
    taskId = taskId,
    type = type,
    detail = detail,
    timestamp = timestamp,
)

private fun AgenticEventEntity.toModel() = AgenticEvent(
    id = id,
    goalId = goalId,
    taskId = taskId,
    type = type,
    detail = detail,
    timestamp = timestamp,
)

/** Room-backed [ConnectionProfileStore]. */
class RoomConnectionProfileStore(
    private val dao: ConnectionProfileDao,
) : ConnectionProfileStore {

    override fun list(): List<ConnectionProfile> = db {
        dao.listAll().map { it.toModel() }
    }

    override fun get(id: String): ConnectionProfile? = db {
        dao.getById(id)?.toModel()
    }

    override fun save(profile: ConnectionProfile): ConnectionProfile = db {
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val saved = if (profile.id == id) profile else profile.copy(id = id)
        dao.upsert(saved.toEntity())
        saved
    }

    override fun delete(id: String): Boolean = db {
        dao.deleteById(id) > 0
    }

    private fun <T> db(block: suspend () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }
}

private fun ConnectionProfile.toEntity() = ConnectionProfileEntity(
    id = id,
    displayName = displayName,
    type = type.name,
    host = host,
    port = port,
    username = username,
    credentialRef = credentialRef,
    hostKeyFingerprint = hostKeyFingerprint,
    workspaceRoot = workspaceRoot,
    pinHostKeyOnFirstConnect = pinHostKeyOnFirstConnect,
)

private fun ConnectionProfileEntity.toModel() = ConnectionProfile(
    id = id,
    displayName = displayName,
    type = runCatching { ConnectionProfileType.valueOf(type) }.getOrDefault(ConnectionProfileType.SSH),
    host = host,
    port = port,
    username = username,
    credentialRef = credentialRef,
    hostKeyFingerprint = hostKeyFingerprint,
    workspaceRoot = workspaceRoot,
    pinHostKeyOnFirstConnect = pinHostKeyOnFirstConnect,
)
