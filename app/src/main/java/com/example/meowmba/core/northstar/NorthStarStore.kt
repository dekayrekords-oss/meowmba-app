package com.example.meowmba.core.northstar

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Ingredient(val text: String, val doneAt: Long? = null)

@Entity(tableName = "north_stars")
data class NorthStar(
    @PrimaryKey val id: String,
    val title: String,
    val why: String,
    val horizon: String,
    val ingredientsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedAt: Long = 0,
) {
    fun ingredients(): List<Ingredient> = runCatching {
        Json.decodeFromString<List<Ingredient>>(ingredientsJson)
    }.getOrDefault(emptyList())
}

@Entity(tableName = "north_star_links",
    foreignKeys = [ForeignKey(entity = NorthStar::class, parentColumns = ["id"], childColumns = ["starId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("starId"), Index("at")])
data class NorthStarLink(
    @PrimaryKey val id: String,
    val starId: String,
    val sourceKey: String,
    val text: String,
    val at: Long,
    val reason: String,
    /** CONNECTION is evidence of relevance, never evidence of achievement. */
    val kind: String,
    val engine: String,
    /** A tombstone prevents a dismissed connection returning on the next app refresh. */
    val dismissed: Boolean = false,
)

@Dao
abstract class NorthStarDao {
    @Query("SELECT * FROM north_stars ORDER BY pinnedAt DESC, createdAt ASC")
    abstract fun observeStars(): Flow<List<NorthStar>>
    @Query("SELECT * FROM north_stars ORDER BY pinnedAt DESC, createdAt ASC")
    abstract suspend fun stars(): List<NorthStar>
    @Query("SELECT * FROM north_stars WHERE id = :id")
    abstract suspend fun star(id: String): NorthStar?
    @Upsert abstract suspend fun save(star: NorthStar)
    @Query("DELETE FROM north_star_links WHERE starId = :starId AND dismissed = 0")
    protected abstract suspend fun clearVisibleLinks(starId: String)
    /** A goal edit can make visible evidence misleading; dismissed tombstones remain suppressed. */
    @Transaction open suspend fun saveForGoal(star: NorthStar, invalidateVisibleLinks: Boolean) {
        if (invalidateVisibleLinks) clearVisibleLinks(star.id)
        save(star)
    }
    @Query("DELETE FROM north_stars WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("UPDATE north_stars SET pinnedAt = 0") protected abstract suspend fun unpinAll()
    @Query("UPDATE north_stars SET pinnedAt = :at WHERE id = :id") protected abstract suspend fun pinOne(id: String, at: Long)
    @Transaction open suspend fun pin(id: String, at: Long) { unpinAll(); pinOne(id, at) }
    @Query("SELECT * FROM north_star_links WHERE dismissed = 0 ORDER BY at DESC LIMIT 500")
    abstract fun observeLinks(): Flow<List<NorthStarLink>>
    @Query("SELECT * FROM north_star_links WHERE id = :id") abstract suspend fun link(id: String): NorthStarLink?
    @Query("SELECT * FROM north_star_links WHERE starId = :starId AND sourceKey = :sourceKey LIMIT 1")
    abstract suspend fun linkForSource(starId: String, sourceKey: String): NorthStarLink?
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertLink(link: NorthStarLink)
    @Update abstract suspend fun updateLink(link: NorthStarLink)
    @Query("DELETE FROM north_star_links WHERE id = :id") abstract suspend fun deleteLink(id: String)
    // Keep only an opaque primary-key tombstone so a removed connection cannot reappear while
    // its notification/conversation identifier is no longer retained.
    @Query("UPDATE north_star_links SET dismissed = 1, sourceKey = '', text = '', reason = '' WHERE id = :id")
    abstract suspend fun dismiss(id: String)
    @Query("DELETE FROM north_star_links WHERE at < :before") abstract suspend fun prune(before: Long)
}

val MIGRATION_12_13_STATEMENTS = listOf(
    "CREATE TABLE IF NOT EXISTS `north_stars` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `why` TEXT NOT NULL, `horizon` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pinnedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `north_star_links` (`id` TEXT NOT NULL, `starId` TEXT NOT NULL, `sourceKey` TEXT NOT NULL, `text` TEXT NOT NULL, `at` INTEGER NOT NULL, `reason` TEXT NOT NULL, `kind` TEXT NOT NULL, `engine` TEXT NOT NULL, `dismissed` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`starId`) REFERENCES `north_stars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_north_star_links_starId` ON `north_star_links` (`starId`)",
    "CREATE INDEX IF NOT EXISTS `index_north_star_links_at` ON `north_star_links` (`at`)",
)

internal fun List<Ingredient>.encoded(): String = Json.encodeToString(this)
