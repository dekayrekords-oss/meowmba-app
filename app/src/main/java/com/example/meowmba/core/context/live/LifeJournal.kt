package com.example.meowmba.core.context.live

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Meow's context journal (docs/MEOW_CONTEXT_PLAN.md §2): everything the user says, and every
 * change in their situation worth remembering, each stamped with where they were, what they
 * were doing and what was around them. On the phone only (owner D8); kept until the user clears it.
 */
@Entity(tableName = "life_events", indices = [Index("at")])
data class LifeEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val at: Long,
    /** [LifeEventKind] name. */
    val kind: String,
    /** What was said, or a readable description of the change ("Started driving"). */
    val text: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    /** Learned place label when known ("Home", "Work", "Main St"). */
    val place: String? = null,
    val activity: String,
    val speedMph: Float? = null,
    /** "quiet" / "talking" / "loud" when the mic was already listening; null otherwise. */
    val noise: String? = null,
    val app: String? = null,
    val charging: Boolean = false,
    /** Connected audio: "car", "headphones" or null. */
    val audio: String? = null,
    val weather: String? = null,
    val nextEvent: String? = null,
)

enum class LifeEventKind { SAID, ACTIVITY, ARRIVED, LEFT, TRIP, NOTE }

/** Raw location fixes kept 30 days, for learning places (plan §1 "Places"). */
@Entity(tableName = "location_samples", indices = [Index("at")])
data class LocationSampleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val activity: String,
)

@Dao
interface LifeJournalDao {
    @Insert suspend fun insert(e: LifeEventEntity): Long
    @Insert suspend fun insertSample(s: LocationSampleEntity)

    @Query("SELECT * FROM life_events ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LifeEventEntity>

    @Query("SELECT * FROM life_events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): kotlinx.coroutines.flow.Flow<List<LifeEventEntity>>

    @Query("SELECT * FROM life_events WHERE at BETWEEN :from AND :to ORDER BY at")
    suspend fun between(from: Long, to: Long): List<LifeEventEntity>

    /** Word search over what was said and what happened; newest first. */
    @Query("SELECT * FROM life_events WHERE text LIKE '%' || :word || '%' OR place LIKE '%' || :word || '%' ORDER BY at DESC LIMIT :limit")
    suspend fun search(word: String, limit: Int): List<LifeEventEntity>

    @Query("SELECT * FROM location_samples WHERE at >= :since ORDER BY at")
    suspend fun samplesSince(since: Long): List<LocationSampleEntity>

    @Query("DELETE FROM location_samples WHERE at < :before")
    suspend fun pruneSamples(before: Long)

    @Query("SELECT COUNT(*) FROM life_events")
    suspend fun count(): Int

    @Query("DELETE FROM life_events")
    suspend fun clear()
}

/** v11 → v12: the journal and location samples. Must match the entities exactly (Room validates). */
val MIGRATION_11_12_STATEMENTS = listOf(
    "CREATE TABLE IF NOT EXISTS `life_events` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `lat` REAL, `lon` REAL, `accuracyM` REAL, `place` TEXT, `activity` TEXT NOT NULL, `speedMph` REAL, `noise` TEXT, `app` TEXT, `charging` INTEGER NOT NULL, `audio` TEXT, `weather` TEXT, `nextEvent` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_life_events_at` ON `life_events` (`at`)",
    "CREATE TABLE IF NOT EXISTS `location_samples` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `accuracyM` REAL NOT NULL, `activity` TEXT NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_location_samples_at` ON `location_samples` (`at`)",
)
