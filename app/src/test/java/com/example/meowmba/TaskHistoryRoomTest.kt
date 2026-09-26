package com.example.meowmba

import androidx.room.Room
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.ToolOutcome
import com.example.meowmba.core.northstar.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskHistoryRoomTest {
    @Test fun realRoomRoundTripKeepsResultAndContextWithoutArguments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowmbaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val journal = RoomTaskJournal(db.companionTaskDao())
            val task = AgentTask(agentId = "research", toolId = "web.search", title = "Find festivals", args = mapOf("q" to "query"),
                status = TaskStatus.COMPLETED, result = "https://example.org/opportunity", projectId = 7, conversationId = 8, outcome = ToolOutcome.RESULT)
            journal.write(task)
            val saved = journal.read().single()
            assertEquals(task.id, saved.id); assertEquals(task.result, saved.result)
            assertEquals(7L, saved.projectId); assertEquals(8L, saved.conversationId); assertTrue(saved.args.isEmpty())
        } finally { db.close() }
    }

    @Test fun retentionKeepsLinkedEvidenceAndRecentWork() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowmbaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val journal = RoomTaskJournal(db.companionTaskDao())
            db.northStarDao().save(NorthStar("star", "Europe", "", "Someday", "[]", 1, 1))
            journal.write(AgentTask(id = "linked", agentId = "research", toolId = "web.search", title = "Evidence", createdAt = 0, status = TaskStatus.COMPLETED))
            db.northStarDao().insertLink(NorthStarLink("link", "star", "task:linked", "evidence", 1, "Owner selected", "CONNECTION", "owner"))
            for (i in 1..205) journal.write(AgentTask(id = "t$i", agentId = "research", toolId = "web.search", title = "$i", createdAt = i.toLong(), status = TaskStatus.COMPLETED))
            assertEquals(200, journal.read().size); assertNotNull(journal.find("linked")); assertNull(journal.find("t1"))
        } finally { db.close() }
    }

    @Test fun migration13To14PreservesGoalsAndCreatesUsableHistory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "task-history-migration-test"; context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MeowmbaDatabase::class.java, name)
            .addMigrations(MeowmbaDatabase.MIGRATION_13_14).allowMainThreadQueries().build()
        val before = open()
        before.northStarDao().save(NorthStar("keep", "Europe", "Music", "Someday", "[]", 1, 1))
        before.openHelper.writableDatabase.execSQL("DROP TABLE companion_tasks")
        before.openHelper.writableDatabase.version = 13
        before.close()
        val after = open()
        try {
            assertEquals("Music", after.northStarDao().star("keep")!!.why)
            val journal = RoomTaskJournal(after.companionTaskDao())
            journal.write(AgentTask(agentId = "file", toolId = "file.read", title = "Read"))
            assertEquals(1, journal.read().size)
        } finally { after.close(); context.deleteDatabase(name) }
    }
}
