package com.example.meowmba.flow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.flow.*
import com.example.meowmba.core.flow.decide.RulesAssessor
import com.example.meowmba.core.flow.sources.SituationAssembler
import com.example.meowmba.core.flow.sources.EvidenceMappers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowIntegrationRegressionTest {
    @Test fun unavailableCalendarNeverClaimsFreeTime() = runTest {
        var reads = 0
        val assembler = SituationAssembler(InMemoryIntentionStore(), WorkStateDetector(), { WorkContext() }, { false },
            { reads++; emptyList() }, { Surface.FLOW_ON_SCREEN }, calendarAvailable = { false })
        assertEquals("Calendar not connected", assembler.current(Fx.at(9)).signals.single().label)
        assertEquals(0, reads)
    }
    @Test fun unrelatedMeetingDoesNotPromoteAnUndatedPeopleTask() = runTest {
        val intention = Fx.handoff.copy(kind = FlowKind.PEOPLE, dueAt = null)
        val situation = Situation(Fx.at(9), Activity.STILL, upcoming = listOf(CalendarItem("Other meeting", Fx.at(9, 10), Fx.at(10), true)))
        assertEquals(Urgency.USEFUL, RulesAssessor().assess(situation, listOf(intention)).single().urgency)
    }
    @Test fun evidencePublishedBeforeStartIsNotLost() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val runtime = FlowRuntime(ctx, backgroundScope, { false })
        runtime.publish(EvidenceMappers.fromPlace("Office", true, Fx.at(9)))
        assertEquals("Office", runtime.assembler.current(Fx.at(9)).place)
    }
    @Test fun version10MigrationPreservesMessagesAndValidatesNewRoomSchema() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "flow-migration-test.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(File("schemas/com.example.meowmba.core.database.MeowmbaDatabase/10.json"),
            File("app/schemas/com.example.meowmba.core.database.MeowmbaDatabase/10.json")).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO messages (role,content,timestamp,conversationId) VALUES ('USER','preserve this',1,0)")
            db.version = 10
        }
        val database = Room.databaseBuilder(context, MeowmbaDatabase::class.java, name)
            .addMigrations(MeowmbaDatabase.MIGRATION_10_11, MeowmbaDatabase.MIGRATION_11_12).allowMainThreadQueries().build()
        try {
            database.openHelper.writableDatabase.query("SELECT content FROM messages WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("preserve this", it.getString(0))
            }
            database.flowDao().upsert(com.example.meowmba.core.flow.db.FlowEntityMapper.toEntity(Fx.bulb, Fx.at(9)))
            database.flowDao().setStatus(Fx.bulb.id, "SNOOZED", Fx.at(10), Fx.at(9))
            database.openHelper.readableDatabase.query("SELECT status FROM flow_intentions WHERE id = ?", arrayOf(Fx.bulb.id)).use {
                assertTrue(it.moveToFirst()); assertEquals("SNOOZED", it.getString(0))
            }
            // v12: the context journal survives the same upgrade path and is searchable.
            val journal = database.lifeJournalDao()
            journal.insert(com.example.meowmba.core.context.live.LifeEventEntity(at = Fx.at(8, 5), kind = "SAID",
                text = "I'm going to work right now", place = "Home", activity = "STILL", lat = 33.4, lon = -111.9))
            assertEquals("Home", journal.search("work", 5).single().place)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
