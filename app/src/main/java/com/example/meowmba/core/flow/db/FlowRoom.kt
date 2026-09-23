package com.example.meowmba.core.flow.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.IntentionStatus
import com.example.meowmba.core.flow.IntentionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Room tables for Meow Flow (master plan §5.3). NOT yet registered: the integrator adds
 * [FlowIntentionEntity] and [FlowOutcomeEntity] to MeowmbaDatabase.entities, exposes
 * `flowDao()`, bumps to v11 and adds [MIGRATION_10_11_STATEMENTS] as a Migration(10, 11).
 */
@Entity(tableName = "flow_intentions")
data class FlowIntentionEntity(
    @PrimaryKey val id: String,
    val text: String,
    val kind: String,
    val source: String,
    val createdAt: Long,
    val dueAt: Long?,
    val placeHint: String?,
    val factsJson: String,
    val status: String,
    val snoozedUntil: Long?,
    val waitingPeople: String,
    val updatedAt: Long,
)

@Entity(tableName = "flow_outcomes")
data class FlowOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val intentionId: String,
    val at: Long,
    val outcome: String,
    val channel: String?,
    val urgency: String?,
    val engine: String?,
)

@Dao
interface FlowDao {
    @Query("SELECT * FROM flow_intentions ORDER BY createdAt")
    fun observeIntentions(): kotlinx.coroutines.flow.Flow<List<FlowIntentionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FlowIntentionEntity)

    @Query("UPDATE flow_intentions SET status = :status, snoozedUntil = :snoozedUntil, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, snoozedUntil: Long?, now: Long)

    @Query("UPDATE flow_intentions SET status = 'DONE', updatedAt = :now WHERE id IN (:ids) AND status != 'DROPPED'")
    suspend fun closeAll(ids: List<String>, now: Long)

    @Insert
    suspend fun insertOutcome(outcome: FlowOutcomeEntity)

    @Query("SELECT * FROM flow_outcomes ORDER BY at DESC LIMIT :limit")
    suspend fun recentOutcomes(limit: Int): List<FlowOutcomeEntity>

    /** Retention: keep the learning log bounded (master plan §8.4). */
    @Query("DELETE FROM flow_outcomes WHERE at < :before")
    suspend fun pruneOutcomes(before: Long)
}

/**
 * Additive migration 10 → 11; never destructive. One statement per entry because
 * SupportSQLiteDatabase.execSQL runs a single statement. Integrator:
 * `object : Migration(10, 11) { override fun migrate(db) = MIGRATION_10_11_STATEMENTS.forEach(db::execSQL) }`
 */
val MIGRATION_10_11_STATEMENTS = listOf(
    "CREATE TABLE IF NOT EXISTS `flow_intentions` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `kind` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `dueAt` INTEGER, `placeHint` TEXT, `factsJson` TEXT NOT NULL, `status` TEXT NOT NULL, `snoozedUntil` INTEGER, `waitingPeople` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `flow_outcomes` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `intentionId` TEXT NOT NULL, `at` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `channel` TEXT, `urgency` TEXT, `engine` TEXT)",
)

object FlowEntityMapper {
    fun toEntity(i: Intention, now: Long) = FlowIntentionEntity(
        id = i.id, text = i.text, kind = i.kind.name, source = i.source, createdAt = i.createdAt, dueAt = i.dueAt,
        placeHint = i.placeHint, factsJson = JsonObject(i.facts.mapValues { JsonPrimitive(it.value) }).toString(),
        status = i.status.name, snoozedUntil = i.snoozedUntil, waitingPeople = i.waitingPeople.joinToString("\u001F"), updatedAt = now,
    )

    fun fromEntity(e: FlowIntentionEntity) = Intention(
        id = e.id, text = e.text,
        kind = runCatching { FlowKind.valueOf(e.kind) }.getOrDefault(FlowKind.OTHER),
        source = e.source, createdAt = e.createdAt, dueAt = e.dueAt, placeHint = e.placeHint,
        facts = runCatching { Json.parseToJsonElement(e.factsJson).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap()),
        status = runCatching { IntentionStatus.valueOf(e.status) }.getOrDefault(IntentionStatus.OPEN),
        snoozedUntil = e.snoozedUntil,
        waitingPeople = e.waitingPeople.split('\u001F').filter { it.isNotEmpty() },
    )
}

/** Room-backed [IntentionStore]. */
class RoomIntentionStore(
    private val dao: FlowDao,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : IntentionStore {
    override val intentions: StateFlow<List<Intention>> =
        dao.observeIntentions().map { list -> list.map(FlowEntityMapper::fromEntity) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun upsert(intention: Intention) = dao.upsert(FlowEntityMapper.toEntity(intention, clock()))
    override suspend fun setStatus(id: String, status: IntentionStatus, snoozedUntil: Long?) = dao.setStatus(id, status.name, snoozedUntil, clock())
    override suspend fun closeSatisfied(ids: Collection<String>) = dao.closeAll(ids.toList(), clock())
}
