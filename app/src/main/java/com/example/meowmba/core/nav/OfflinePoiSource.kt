package com.example.meowmba.core.nav

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fully offline place lookup against a prebuilt OSM POI pack.
 *
 * This is the "copy Geoapify's mechanics locally" path: Geoapify's Places data is
 * OpenStreetMap, so a filtered OSM extract answers the same questions with no key,
 * no quota, and no network. Build a pack with `scripts/build_poi_pack.py`.
 *
 * Schema (see that script):
 *   poi(id INTEGER PRIMARY KEY, name TEXT, brand TEXT, category TEXT,
 *       lat REAL, lon REAL, addr TEXT)
 *   poi_rtree(id, minLat, maxLat, minLon, maxLon)   -- R*Tree, for the bbox prefilter
 *   poi_fts(name, brand)                            -- FTS5, for name search
 *
 * Packs live in filesDir/poi/<region>.sqlite so they can be swapped per region
 * without an app update.
 */
class OfflinePoiSource(context: Context) : PlaceSource {

    private val appContext = context.applicationContext
    override val kind = PlaceSourceKind.OFFLINE

    private val packDir = File(appContext.filesDir, "poi")

    /** What the NAV settings card shows for each installed pack. */
    data class PackInfo(
        val region: String,
        val poiCount: Int,
        val sizeBytes: Long,
        val fileName: String,
    )

    /** One installed region pack, with the coverage box read from its pack_meta row. */
    private class Pack(
        val file: File,
        val region: String,
        val poiCount: Int,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
    ) {
        var db: SQLiteDatabase? = null

        fun intersects(b: BoundingBox): Boolean =
            minLat <= b.maxLat && maxLat >= b.minLat &&
                minLon <= b.maxLon && maxLon >= b.minLon
    }

    /**
     * Lazily discovered packs. With ten state packs installed, opening and querying
     * all of them for every lookup would be wasteful — so each pack carries its
     * bounding box and only the ones covering the search area get opened.
     */
    @Volatile private var packs: List<Pack>? = null

    private fun packFiles(): List<File> =
        packDir.listFiles { f -> f.isFile && f.name.endsWith(".sqlite") }
            ?.sortedBy { it.name }
            ?.toList()
            .orEmpty()

    private fun loadPacks(): List<Pack> {
        packs?.let { return it }
        val loaded = packFiles().mapNotNull { f ->
            try {
                SQLiteDatabase.openDatabase(
                    f.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    db.rawQuery(
                        "SELECT region, minLat, maxLat, minLon, maxLon, poi_count " +
                            "FROM pack_meta LIMIT 1",
                        null,
                    ).use { c ->
                        if (c.moveToFirst()) {
                            Pack(
                                file = f,
                                region = c.getString(0) ?: f.nameWithoutExtension,
                                poiCount = c.getInt(5),
                                minLat = c.getDouble(1), maxLat = c.getDouble(2),
                                minLon = c.getDouble(3), maxLon = c.getDouble(4),
                            )
                        } else {
                            Log.w(TAG, "${f.name} has no pack_meta row; ignoring")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read POI pack ${f.name}: ${e.message}")
                null
            }
        }
        packs = loaded
        return loaded
    }

    override fun isAvailable(): Boolean = packFiles().isNotEmpty()

    /** Call after installing or removing a pack so the bbox list is re-read. */
    fun refresh() {
        close()
        packs = null
    }

    /** Installed packs, for the NAV settings card. */
    fun installedPacks(): List<PackInfo> = loadPacks().map {
        PackInfo(
            region = it.region,
            poiCount = it.poiCount,
            sizeBytes = it.file.length(),
            fileName = it.file.name,
        )
    }

    /**
     * Copies a pack into the app's pack directory. Source is anything readable —
     * typically a file the user pushed to Downloads, or picked with the system
     * document picker.
     *
     * @return true on success. Rejects files without a pack_meta table rather than
     * installing something that will silently never match.
     */
    fun install(name: String, open: () -> java.io.InputStream): Boolean {
        if (!packDir.exists() && !packDir.mkdirs()) return false
        val safeName = name.substringAfterLast('/').let {
            if (it.endsWith(".sqlite")) it else "$it.sqlite"
        }
        val dest = File(packDir, safeName)
        val tmp = File(packDir, "$safeName.part")
        return try {
            open().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            if (!looksLikePack(tmp)) {
                Log.w(TAG, "$safeName has no pack_meta table; not installing")
                tmp.delete()
                return false
            }
            // Only publish once validated, so a half-copied file is never picked up.
            if (dest.exists()) dest.delete()
            val ok = tmp.renameTo(dest)
            if (ok) refresh()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Install of $safeName failed: ${e.message}")
            tmp.delete()
            false
        }
    }

    fun uninstall(fileName: String): Boolean {
        val f = File(packDir, fileName.substringAfterLast('/'))
        val ok = f.exists() && f.delete()
        if (ok) refresh()
        return ok
    }

    private fun looksLikePack(f: File): Boolean = try {
        SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='pack_meta'",
                    null,
                ).use { it.moveToFirst() }
            }
    } catch (e: Exception) {
        false
    }

    private fun openPack(pack: Pack): SQLiteDatabase? {
        pack.db?.let { if (it.isOpen) return it }
        return try {
            SQLiteDatabase.openDatabase(
                pack.file.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
            ).also { pack.db = it }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open POI pack ${pack.file.name}: ${e.message}")
            null
        }
    }

    fun close() {
        packs?.forEach { p ->
            p.db?.takeIf { it.isOpen }?.close()
            p.db = null
        }
    }

    override suspend fun searchByName(
        name: String, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        val box = BoundingBox.around(lat, lon, radiusMeters)
        val relevant = loadPacks().filter { it.intersects(box) }
        if (relevant.isEmpty()) return@withContext emptyList()

        // FTS prefix match so "circle k" finds "Circle K #2410".
        val ftsQuery = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        if (ftsQuery.isBlank()) return@withContext emptyList()

        val sql = """
            SELECT p.name, p.brand, p.category, p.lat, p.lon, p.addr
            FROM poi_fts f
            JOIN poi p ON p.id = f.rowid
            JOIN poi_rtree r ON r.id = p.id
            WHERE poi_fts MATCH ?
              AND r.minLat >= ? AND r.maxLat <= ?
              AND r.minLon >= ? AND r.maxLon <= ?
            LIMIT ?
        """.trimIndent()

        val args = arrayOf(
            ftsQuery,
            box.minLat.toString(), box.maxLat.toString(),
            box.minLon.toString(), box.maxLon.toString(),
            // Over-fetch: the bbox is square but the radius filter below is circular,
            // and we sort by true distance afterwards.
            (limit * 8).toString(),
        )
        acrossPacks(relevant, sql, args, lat, lon, radiusMeters, limit)
    }

    override suspend fun searchByCategory(
        categories: List<String>, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyList()
        val box = BoundingBox.around(lat, lon, radiusMeters)
        val relevant = loadPacks().filter { it.intersects(box) }
        if (relevant.isEmpty()) return@withContext emptyList()

        // Prefix match so "catering.fast_food" also matches "catering.fast_food.burger".
        val where = categories.joinToString(" OR ") { "p.category LIKE ?" }
        val sql = """
            SELECT p.name, p.brand, p.category, p.lat, p.lon, p.addr
            FROM poi p
            JOIN poi_rtree r ON r.id = p.id
            WHERE ($where)
              AND r.minLat >= ? AND r.maxLat <= ?
              AND r.minLon >= ? AND r.maxLon <= ?
            LIMIT ?
        """.trimIndent()

        val args = categories.map { "$it%" } + listOf(
            box.minLat.toString(), box.maxLat.toString(),
            box.minLon.toString(), box.maxLon.toString(),
            (limit * 8).toString(),
        )
        acrossPacks(relevant, sql, args.toTypedArray(), lat, lon, radiusMeters, limit)
    }

    /**
     * Runs the same query over every covering pack and merges. Near a state line the
     * nearest burger may well be in the neighbouring pack, so results are re-sorted by
     * true distance across all of them rather than per-pack.
     */
    private fun acrossPacks(
        relevant: List<Pack>,
        sql: String,
        args: Array<String>,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        limit: Int,
    ): List<Place> = relevant
        .mapNotNull { openPack(it) }
        .flatMap { db -> query(db, sql, args, lat, lon, radiusMeters, limit) }
        .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        .take(limit)

    private fun query(
        database: SQLiteDatabase,
        sql: String,
        args: Array<String>,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        limit: Int,
    ): List<Place> = try {
        val out = mutableListOf<Place>()
        database.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val pLat = c.getDouble(3)
                val pLon = c.getDouble(4)
                val d = haversineMeters(lat, lon, pLat, pLon)
                if (d > radiusMeters) continue   // square bbox -> circular radius
                val name = c.getString(0)?.takeIf { it.isNotBlank() }
                    ?: c.getString(1)?.takeIf { it.isNotBlank() }
                    ?: continue
                out += Place(
                    name = name,
                    lat = pLat,
                    lon = pLon,
                    address = c.getString(5),
                    category = c.getString(2),
                    distanceMeters = d,
                    source = PlaceSourceKind.OFFLINE,
                )
            }
        }
        out.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }.take(limit)
    } catch (e: Exception) {
        Log.w(TAG, "POI query failed: ${e.message}")
        emptyList()
    }

    /** Square bounding box, used as a cheap prefilter before the exact distance test. */
    private data class BoundingBox(
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double,
    ) {
        companion object {
            fun around(lat: Double, lon: Double, radiusMeters: Int): BoundingBox {
                val dLat = radiusMeters / 111_320.0
                // Longitude degrees shrink with latitude; clamp so we never divide by ~0
                // near the poles and blow the box up to the whole world.
                val cos = Math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
                val dLon = radiusMeters / (111_320.0 * cos)
                return BoundingBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
            }
        }
    }

    companion object {
        private const val TAG = "OfflinePoi"
    }
}
