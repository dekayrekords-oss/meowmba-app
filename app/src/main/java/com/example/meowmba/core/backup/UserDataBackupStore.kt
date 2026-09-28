package com.example.meowmba.core.backup

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

data class BackupDataSummary(val preferences: Int, val rows: Int, val files: Int, val bytes: Long)
data class BackupRestoreResult(val restored: Int, val unchanged: Int, val conflicts: List<String>, val complete: Boolean)

/** Logical Room snapshots avoid copying a live WAL. Restore is additive and refuses conflicting
 * content before writing. A storage failure can leave additions; retry is safe and cleanup stays blocked.
 * Never exports Android Keystore material, raw preference XML, caches or downloaded models.
 */
class UserDataBackupStore internal constructor(
    private val context: Context,
    private val database: () -> SupportSQLiteDatabase,
    private val secureRead: () -> Map<String, Any?>,
    private val secureWrite: (Map<String, Any?>) -> Unit,
) {
    constructor(context: Context) : this(context,
        { MeowmbaDatabase.getDatabase(context).openHelper.writableDatabase },
        { SecureStorageManager(context).portableValues() },
        { SecureStorageManager(context).importPortableValues(it); Unit })

    fun snapshot(destination: File): BackupDataSummary {
        require(!Files.isSymbolicLink(destination.toPath()) && (destination.isDirectory || destination.mkdirs()))
        require(destination.listFiles().orEmpty().isEmpty())
        val root = JSONObject().put("format", 2)
        val prefs = JSONObject().put("secure", encodePrefs(secureRead()))
        PREFS.forEach { name -> prefs.put(name, encodePrefs(context.getSharedPreferences(name, 0).all)) }
        root.put("preferences", prefs)
        val db = database(); var rows = 0; var jsonBudget = prefs.toString().toByteArray().size.toLong()
        val tables = JSONArray()
        db.beginTransaction()
        try {
            root.put("databaseVersion", db.version)
            for (table in schemas(db)) {
                val data = JSONArray()
                db.query("SELECT * FROM ${quote(table.name)}").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        table.columns.indices.forEach { row.put(encodeCell(cursorValue(cursor, it))) }
                        jsonBudget += row.toString().toByteArray().size; require(jsonBudget <= MAX_JSON)
                        data.put(row); rows++; require(rows <= MAX_ROWS)
                    }
                }
                tables.put(JSONObject().put("name", table.name).put("schema", table.signature).put("rows", data))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        root.put("tables", tables)
        val serialized = root.toString().toByteArray(); require(serialized.size <= MAX_JSON)
        File(destination, DATA).writeBytes(serialized)
        var bytes = 0L; var files = 0
        for (rel in ROOTS) {
            val source = checkedPath(context.filesDir, rel)
            if (!source.exists()) continue
            for (input in regularFiles(source)) {
                val path = input.relativeTo(context.filesDir).invariantSeparatorsPath
                if (!allowedFile(path)) continue
                val output = checkedPath(File(destination, "files"), path)
                require(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
                files++; require(files <= MAX_FILES)
                input.inputStream().use { from -> output.outputStream().use { to ->
                    val b = ByteArray(64 * 1024)
                    while (true) { val n = from.read(b); if (n < 0) break; bytes += n; require(bytes <= MAX_BYTES); to.write(b, 0, n) }
                } }
            }
        }
        return BackupDataSummary(prefs.keys().asSequence().sumOf { prefs.getJSONObject(it).length() }, rows, files, bytes)
    }

    fun restore(source: File): BackupRestoreResult {
        require(source.isDirectory && !Files.isSymbolicLink(source.toPath()))
        require(source.listFiles().orEmpty().all { it.name == DATA || it.name == "files" })
        val data = checkedPath(source, DATA); require(data.isFile && data.length() <= MAX_JSON)
        val root = JSONObject(data.readText()); require(root.getInt("format") == 2)
        val incomingPrefs = root.getJSONObject("preferences")
        require(incomingPrefs.keys().asSequence().toSet() == PREFS + "secure")
        val prefValues = (PREFS + "secure").associateWith { name -> decodePrefs(incomingPrefs.getJSONObject(name), name == "secure") }
        val filesRoot = checkedPath(source, "files")
        val inputs = if (filesRoot.exists()) regularFiles(filesRoot) else emptyList()
        require(inputs.size <= MAX_FILES)
        var size = 0L
        val files = inputs.map { input ->
            val path = input.relativeTo(filesRoot).invariantSeparatorsPath; require(allowedFile(path))
            size += input.length(); require(size <= MAX_BYTES)
            input to checkedPath(context.filesDir, path)
        }
        val db = database(); val conflicts = mutableListOf<String>(); var unchanged = 0; var restored = 0
        db.beginTransaction()
        try {
            val sourceVersion = root.getInt("databaseVersion")
            val targetVersion = db.version
            val compatibleAdditiveImport = sourceVersion in 12..15 && targetVersion in sourceVersion..15
            require(sourceVersion == targetVersion || compatibleAdditiveImport) { "Backup database version is not compatible" }
            val excludedForCompatibility = buildSet {
                if (sourceVersion < 15 && targetVersion >= 15) add("memory_policies")
                if (sourceVersion < 13 && targetVersion >= 13) addAll(setOf("north_stars", "north_star_links"))
                if (sourceVersion < 14 && targetVersion >= 14) add("companion_tasks")
            }
            val expected = schemas(db).filterNot { it.name in excludedForCompatibility }.associateBy { it.name }
            val tableArray = root.getJSONArray("tables")
            require(tableArray.length() == expected.size)
            val seen = mutableSetOf<String>(); var count = 0
            val additions = mutableListOf<Pair<Schema, List<Any?>>>()
            // Decode and validate every table, column and value before applying anything.
            for (i in 0 until tableArray.length()) {
                val table = tableArray.getJSONObject(i); val name = table.getString("name")
                require(seen.add(name)); val schema = requireNotNull(expected[name])
                require(table.getString("schema") == schema.signature)
                val rows = table.getJSONArray("rows"); val rowKeys = mutableSetOf<String>()
                for (j in 0 until rows.length()) {
                    require(++count <= MAX_ROWS)
                    val row = rows.getJSONArray(j); require(row.length() == schema.columns.size)
                    val values = (0 until row.length()).map { decodeCell(row.getJSONObject(it)) }
                    require(values.all { it == null || it is String || it is Long || it is Double || it is ByteArray })
                    schema.columns.forEachIndexed { k, col -> require(!col.notNull || values[k] != null) }
                    val keys = schema.columns.withIndex().filter { it.value.pk > 0 }.sortedBy { it.value.pk }.map { it.index }
                    require(keys.isNotEmpty() && keys.all { values[it] is String || values[it] is Long })
                    val keyString = JSONArray(keys.map { row.getJSONObject(it) }).toString(); require(rowKeys.add(keyString))
                    val where = keys.joinToString(" AND ") { "${quote(schema.columns[it].name)} = ?" }
                    db.query("SELECT * FROM ${quote(name)} WHERE $where", keys.map { values[it] }.toTypedArray()).use { cursor ->
                        if (cursor.moveToFirst()) {
                            if (values.indices.all { sameValue(values[it], cursorValue(cursor, it)) }) unchanged++
                            else conflicts += "Saved record in $name"
                        } else additions += schema to values
                    }
                }
            }
            val preferenceAdds = mutableMapOf<String, Map<String, Any?>>()
            val preferenceBefore = mutableMapOf<String, Map<String, Any?>>()
            for ((name, values) in prefValues) {
                val existing = if (name == "secure") secureRead() else context.getSharedPreferences(name, 0).all
                preferenceBefore[name] = existing.filterKeys { it in values }
                val missing = mutableMapOf<String, Any?>()
                for ((key, value) in values) {
                    if (!existing.containsKey(key)) missing[key] = value
                    else if (existing[key] == value) unchanged++
                    else conflicts += "Setting in ${if (name == "secure") "private settings" else name}"
                }
                preferenceAdds[name] = missing
            }
            val fileAdds = mutableListOf<Pair<File, File>>()
            for ((input, target) in files) {
                if (!target.exists()) fileAdds += input to target
                else if (target.isFile && hash(input) == hash(target)) unchanged++
                else conflicts += "User-created file"
            }
            // A differing parent/key/settings set must not be combined with imported children.
            if (conflicts.isNotEmpty()) return BackupRestoreResult(0, unchanged, conflicts, false)
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            for ((schema, values) in additions) {
                val cv = ContentValues(); schema.columns.forEachIndexed { i, col -> put(cv, col.name, values[i]) }
                check(db.insert(schema.name, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L)
                restored++
            }
            db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
            BackupPreferences.locked {
                // Background saves may have completed after preflight. Never replace such a save.
                for ((name, values) in prefValues) {
                    val actual = if (name == "secure") secureRead() else context.getSharedPreferences(name, 0).all
                    require(actual.filterKeys { it in values } == preferenceBefore[name]) { "Settings changed during restore; retry" }
                }
                for ((name, values) in preferenceAdds) {
                    if (values.isEmpty()) continue
                    if (name == "secure") secureWrite(values)
                    else {
                        val prefs = context.getSharedPreferences(name, 0)
                        val editor = prefs.edit()
                        values.forEach { (key, value) -> require(!prefs.contains(key)); put(editor, key, value) }
                        check(editor.commit())
                    }
                    val actual = if (name == "secure") secureRead() else context.getSharedPreferences(name, 0).all
                    require(values.all { (key, value) -> actual[key] == value }) { "Settings verification failed" }
                    restored += values.size
                }
            }
            for ((input, target) in fileAdds) {
                checkedPath(context.filesDir, target.relativeTo(context.filesDir).invariantSeparatorsPath)
                require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                // Copy to a sibling temporary file, then publish without replacing existing content.
                val temp = File.createTempFile("restore-", ".pending", target.parentFile)
                try {
                    input.inputStream().use { from -> temp.outputStream().use { from.copyTo(it, 64 * 1024) } }
                    require(hash(temp) == hash(input)); Files.move(temp.toPath(), target.toPath()); restored++
                } finally { temp.delete() }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return BackupRestoreResult(restored, unchanged, emptyList(), true)
    }

    private data class Column(val name: String, val notNull: Boolean, val pk: Int)
    private data class Schema(val name: String, val signature: String, val columns: List<Column>)
    private fun schemas(db: SupportSQLiteDatabase): List<Schema> {
        val tables = mutableListOf<Pair<String, String>>()
        db.query("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0); val sql = c.getString(1).orEmpty()
                if (name.startsWith("sqlite_") || name in setOf("android_metadata", "room_master_table") || name == "messages_fts" || name.startsWith("messages_fts_") || name == "memories_fts" || name.startsWith("memories_fts_") || sql.contains("CREATE VIRTUAL TABLE", true)) continue
                tables += name to sql
            }
        }
        return tables.map { (name, sql) ->
            val columns = mutableListOf<Column>(); val definition = JSONArray()
            db.query("PRAGMA table_info(${quote(name)})").use { c -> while (c.moveToNext()) {
                columns += Column(c.getString(1), c.getInt(3) != 0, c.getInt(5))
                definition.put(JSONArray().put(c.getString(1)).put(c.getString(2)).put(c.getInt(3)).put(if (c.isNull(4)) JSONObject.NULL else c.getString(4)).put(c.getInt(5)))
            } }
            Schema(name, definition.toString(), columns)
        }
    }
    private fun encodePrefs(values: Map<String, Any?>): JSONObject = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) -> require(key.length in 1..1024 && '\u0000' !in key); put(key, encodeCell(value)) }
    }
    private fun decodePrefs(obj: JSONObject, secure: Boolean): Map<String, Any?> {
        require(obj.length() <= 20_000)
        return obj.keys().asSequence().associateWith { key ->
            require(key.length in 1..1024 && '\u0000' !in key && (!secure || SecureStorageManager.isPortableKey(key)))
            val value = decodeCell(obj.getJSONObject(key))
            require(value is String || value is Boolean || value is Int || value is Long || value is Float || value is Set<*>)
            value
        }
    }
    private fun encodeCell(v: Any?): JSONObject = JSONObject().apply {
        when (v) {
            null -> put("t", "null")
            is String -> put("t", "text").put("v", v)
            is Boolean -> put("t", "bool").put("v", v)
            is Int -> put("t", "int").put("v", v.toString())
            is Long -> put("t", "long").put("v", v.toString())
            is Float -> { require(v.isFinite()); put("t", "float").put("v", v.toString()) }
            is Double -> { require(v.isFinite()); put("t", "double").put("v", v.toString()) }
            is ByteArray -> put("t", "blob").put("v", Base64.encodeToString(v, Base64.NO_WRAP))
            is Set<*> -> { require(v.all { it is String }); put("t", "set").put("v", JSONArray(v.map { it as String }.sorted())) }
            else -> error("Unsupported stored value")
        }
    }
    private fun decodeCell(o: JSONObject): Any? = when (o.getString("t")) {
        "null" -> null
        "text" -> o.getString("v")
        "bool" -> o.getBoolean("v")
        "int" -> o.getString("v").toInt()
        "long" -> o.getString("v").toLong()
        "float" -> o.getString("v").toFloat().also { require(it.isFinite()) }
        "double" -> o.getString("v").toDouble().also { require(it.isFinite()) }
        "blob" -> Base64.decode(o.getString("v"), Base64.NO_WRAP)
        "set" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> error("Unsupported backup value")
    }
    private fun cursorValue(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null; Cursor.FIELD_TYPE_INTEGER -> c.getLong(i); Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i); else -> c.getString(i)
    }
    private fun sameValue(a: Any?, b: Any?): Boolean = if (a is ByteArray && b is ByteArray) a.contentEquals(b) else a == b
    private fun put(cv: ContentValues, key: String, v: Any?) { when (v) {
        null -> cv.putNull(key); is String -> cv.put(key, v); is Long -> cv.put(key, v); is Double -> cv.put(key, v); is ByteArray -> cv.put(key, v)
        else -> error("Invalid database value")
    } }
    @Suppress("UNCHECKED_CAST")
    private fun put(e: SharedPreferences.Editor, k: String, v: Any?) { when (v) {
        is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v)
        is Set<*> -> e.putStringSet(k, v as Set<String>); else -> error("Invalid preference value")
    } }
    private fun quote(name: String): String { require(name.matches(Regex("[A-Za-z0-9_]+"))); return "`$name`" }
    private fun allowedFile(path: String): Boolean = ROOTS.any { path == it || path.startsWith("$it/") } &&
        path.split('/').none { it in setOf("cache", "traces", "models", "voice_models", "desktop_screenshots", "web_screenshots") || it.endsWith(".part") || it.endsWith(".pending") }
    private fun checkedPath(base: File, path: String): File {
        require(path.isNotEmpty() && !path.startsWith('/') && '\\' !in path && '\u0000' !in path && path.split('/').none { it.isEmpty() || it == "." || it == ".." })
        require(!Files.isSymbolicLink(base.toPath()))
        var current = base
        path.split('/').forEach { part -> current = File(current, part); require(!Files.isSymbolicLink(current.toPath())) }
        require(current.canonicalFile.toPath().startsWith(base.canonicalFile.toPath()))
        return current
    }
    private fun regularFiles(root: File): List<File> {
        val files = mutableListOf<File>()
        root.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }.forEach {
            require(!Files.isSymbolicLink(it.toPath()))
            if (it.isFile) { files += it; require(files.size <= MAX_FILES) }
        }
        return files
    }
    private fun hash(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256"); val b = ByteArray(64 * 1024)
        while (true) { val n = input.read(b); if (n < 0) break; md.update(b, 0, n) }; md.digest().joinToString("") { "%02x".format(it) }
    }
    companion object {
        private const val DATA = "user-data.json"
        private const val MAX_JSON = 32L * 1024 * 1024
        private const val MAX_BYTES = 450L * 1024 * 1024
        private const val MAX_FILES = 19_000
        private const val MAX_ROWS = 100_000
        internal val PREFS = setOf("meow_flow", "nav_saved_places", "voice_profiles", "weather_location", "local_neural_voice", "phone_recipe_preferences", "asset_browser", "agentic_memory_index")
        private val ROOTS = setOf("comics", "scripts", "ads", "posts", "media", "workspace", "memba_workspace", "web_pages", "project_state", "vault", "companion_props", "custom_animations", "avatar_forms", "generated_avatars", "phone_recipes", "business_agents", "agentic-memory", "neural/core.json", "facts/history.jsonl", "prime_harness.json", "connectors.json")
    }
}
