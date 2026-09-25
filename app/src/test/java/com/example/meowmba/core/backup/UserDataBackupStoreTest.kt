package com.example.meowmba.core.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserDataBackupStoreTest {
    private lateinit var context: Context
    private lateinit var db: SupportSQLiteDatabase
    private val secure = linkedMapOf<String, Any?>()

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.deleteRecursively(); context.filesDir.mkdirs()
        context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs()
        context.getSharedPreferences("meow_flow", 0).edit().clear().commit()
        db = newDatabase().writableDatabase
        db.execSQL("DELETE FROM records")
        secure.clear()
    }

    @After fun closeDatabase() { db.close() }

    @Test fun roundTripPreservesTypedRowsBlobsNullsPrefsKeysAndFiles() {
        db.execSQL("INSERT INTO records VALUES ('one','hello',7,?,NULL)", arrayOf(byteArrayOf(1, 2, 3)))
        context.getSharedPreferences("meow_flow", 0).edit().putString("theme", "dark").putInt("count", 4).commit()
        secure["api_key_test"] = "secret"
        val source = temp("source"); File(context.filesDir, "workspace/note.txt").apply { parentFile!!.mkdirs(); writeText("note") }
        val summary = store().snapshot(source)

        db.execSQL("DELETE FROM records"); context.getSharedPreferences("meow_flow", 0).edit().clear().commit(); secure.clear(); File(context.filesDir, "workspace/note.txt").delete()
        val result = store().restore(source)
        assertTrue(result.complete); assertEquals(5, result.restored); assertEquals(1, summary.rows)
        assertEquals("hello", db.query("SELECT value FROM records WHERE id='one'").use { it.moveToFirst(); it.getString(0) })
        db.query("SELECT number, raw, nullable FROM records").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(7L, c.getLong(0)); assertArrayEquals(byteArrayOf(1, 2, 3), c.getBlob(1)); assertTrue(c.isNull(2))
        }
        assertEquals("dark", context.getSharedPreferences("meow_flow", 0).getString("theme", null))
        assertEquals("secret", secure["api_key_test"])
        assertEquals("note", File(context.filesDir, "workspace/note.txt").readText())
    }

    @Test fun secondRestoreIsCompleteAndUnchanged() {
        db.execSQL("INSERT INTO records VALUES ('one','hello',7,?,NULL)", arrayOf(byteArrayOf(1)))
        val source = temp("source"); store().snapshot(source)
        val result = store().restore(source)
        assertTrue(result.complete); assertTrue(result.unchanged > 0); assertEquals(0, result.restored)
    }

    @Test fun conflictRefusesAllAdditionsIncludingOtherMissingRows() {
        db.execSQL("INSERT INTO records VALUES ('one','from-source',1,?,NULL)", arrayOf(byteArrayOf(1)))
        db.execSQL("INSERT INTO records VALUES ('two','missing',2,?,NULL)", arrayOf(byteArrayOf(2)))
        val source = temp("source"); store().snapshot(source)
        db.execSQL("DELETE FROM records")
        db.execSQL("INSERT INTO records VALUES ('one','different',1,?,NULL)", arrayOf(byteArrayOf(9)))
        val result = store().restore(source)
        assertFalse(result.complete); assertEquals(1, result.conflicts.size)
        assertEquals(1, db.query("SELECT COUNT(*) FROM records").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("different", db.query("SELECT value FROM records").use { it.moveToFirst(); it.getString(0) })
    }

    @Test fun malformedTypeIsRejectedBeforePrefsFilesOrDatabaseWrites() {
        db.execSQL("INSERT INTO records VALUES ('one','hello',7,?,NULL)", arrayOf(byteArrayOf(1)))
        val source = temp("source"); store().snapshot(source)
        val json = JSONObject(File(source, "user-data.json").readText())
        json.getJSONObject("preferences").getJSONObject("meow_flow").put("bad", JSONObject().put("t", "unknown"))
        File(source, "user-data.json").writeText(json.toString())
        try { store().restore(source); fail("expected malformed value rejection") } catch (_: IllegalStateException) { }
        assertEquals(1, db.query("SELECT COUNT(*) FROM records").use { it.moveToFirst(); it.getInt(0) })
        assertFalse(context.getSharedPreferences("meow_flow", 0).contains("bad"))
    }

    @Test fun schemaAndUnknownTableAreRejectedBeforeWrites() {
        val source = temp("source"); store().snapshot(source)
        val json = JSONObject(File(source, "user-data.json").readText())
        val table = json.getJSONArray("tables").getJSONObject(0)
        table.put("schema", "tampered")
        json.getJSONArray("tables").put(JSONObject().put("name", "unknown").put("schema", "x").put("rows", emptyList<Any>()))
        File(source, "user-data.json").writeText(json.toString())
        try { store().restore(source); fail("expected schema rejection") } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.query("SELECT COUNT(*) FROM records").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun disallowedFileAndSymlinkSourceAreRejected() {
        val source = temp("source"); store().snapshot(source)
        File(source, "files/cache/blocked").apply { parentFile!!.mkdirs(); writeText("x") }
        try { store().restore(source); fail("expected disallowed file rejection") } catch (_: IllegalArgumentException) { }
        val symlink = File(context.cacheDir, "symlink").apply { deleteRecursively() }
        Files.createSymbolicLink(symlink.toPath(), source.toPath())
        try { store().restore(symlink); fail("expected symlink rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun differingPreferenceOrFileProducesConflictWithoutPartialChanges() {
        context.getSharedPreferences("meow_flow", 0).edit().putString("theme", "dark").commit()
        val source = temp("source"); File(context.filesDir, "workspace/note.txt").apply { parentFile!!.mkdirs(); writeText("source") }
        store().snapshot(source)
        context.getSharedPreferences("meow_flow", 0).edit().putString("theme", "light").commit()
        File(context.filesDir, "workspace/note.txt").writeText("different")
        val result = store().restore(source)
        assertFalse(result.complete); assertTrue(result.conflicts.isNotEmpty())
        assertEquals("light", context.getSharedPreferences("meow_flow", 0).getString("theme", null))
        assertEquals("different", File(context.filesDir, "workspace/note.txt").readText())
    }

    @Test fun actualRoomSchemaRoundTripRebuildsSearchIndex() {
        db.close()
        val room = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.meowmba.core.database.MeowmbaDatabase::class.java)
            .allowMainThreadQueries().build()
        db = room.openHelper.writableDatabase
        db.execSQL("INSERT INTO messages (id,role,content,timestamp,conversationId,speakerName) VALUES (1,'USER','test backup memory',1,0,NULL)")
        val source = temp("room-source"); store().snapshot(source)
        db.execSQL("DELETE FROM messages")
        assertTrue(store().restore(source).complete)
        db.query("SELECT rowid FROM messages_fts WHERE messages_fts MATCH 'backup'").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)) }
        assertTrue(store().restore(source).complete)
    }

    @Test fun schema12ImportRestoresOldRowsAndPreservesTargetNorthStars() {
        db.close()
        db = newDatabase(12, includeNorthStars = false).writableDatabase
        db.execSQL("INSERT INTO records VALUES ('old','from backup',7,NULL,NULL)")
        val source = temp("schema12-source"); store().snapshot(source)

        db.close()
        db = newDatabase(13, includeNorthStars = true).writableDatabase
        db.execSQL("INSERT INTO north_stars VALUES ('keep','Owner goal')")
        val result = store().restore(source)

        assertTrue(result.complete)
        assertEquals("from backup", db.query("SELECT value FROM records WHERE id='old'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(1, db.query("SELECT COUNT(*) FROM north_stars").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun unsupportedSchemaVersionIsRejectedBeforeAnyRestore() {
        db.execSQL("INSERT INTO records VALUES ('old','from backup',7,NULL,NULL)")
        val source = temp("bad-version-source"); store().snapshot(source)
        val json = JSONObject(File(source, "user-data.json").readText()).put("databaseVersion", 11)
        File(source, "user-data.json").writeText(json.toString())
        db.execSQL("DELETE FROM records")
        try { store().restore(source); fail("unsupported schema must be rejected") } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.query("SELECT COUNT(*) FROM records").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun savedAvatarsAndFactHistorySurviveRestore() {
        val paths = listOf("avatar_forms/custom.json", "facts/history.jsonl", "neural/core.json")
        paths.forEach { path -> File(context.filesDir, path).apply { parentFile!!.mkdirs(); writeText("test $path") } }
        val source = temp("creative-source"); assertEquals(3, store().snapshot(source).files)
        paths.forEach { File(context.filesDir, it).delete() }
        assertTrue(store().restore(source).complete)
        paths.forEach { assertEquals("test $it", File(context.filesDir, it).readText()) }
    }

    @Test fun concurrentPrivateKeySaveAbortsRestoreAndRollsBackRows() {
        secure["api_key_test"] = "backup-key"
        db.execSQL("INSERT INTO records VALUES ('one','hello',7,NULL,NULL)")
        val source = temp("concurrent-source"); store().snapshot(source)
        secure.clear(); db.execSQL("DELETE FROM records")
        var reads = 0
        val racing = UserDataBackupStore(context, { db }, {
            val snapshot = secure.toMap()
            if (++reads == 1) secure["api_key_test"] = "new-user-key"
            snapshot
        }) { secure.putAll(it) }
        try { racing.restore(source); fail("Concurrent save must block restore") } catch (_: IllegalArgumentException) { }
        assertEquals("new-user-key", secure["api_key_test"])
        db.query("SELECT COUNT(*) FROM records").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }

    private fun store() = UserDataBackupStore(context, { db }, { secure.toMap() }) { secure.putAll(it) }
    private fun temp(name: String) = File(context.cacheDir, name).apply { deleteRecursively(); mkdirs() }
    private fun newDatabase(version: Int = 1, includeNorthStars: Boolean = false): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name("backup-test-${System.nanoTime()}.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL, value TEXT, number INTEGER, raw BLOB, nullable TEXT)")
                    if (includeNorthStars) db.execSQL("CREATE TABLE north_stars (id TEXT PRIMARY KEY NOT NULL, text TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
}
