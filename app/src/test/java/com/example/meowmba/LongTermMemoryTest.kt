package com.example.meowmba

import androidx.room.Room
import com.example.meowmba.core.database.*
import com.example.meowmba.core.memory.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LongTermMemoryTest {
    private lateinit var db: MeowmbaDatabase
    private lateinit var repo: MemoryRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MeowmbaDatabase::class.java).allowMainThreadQueries().build()
        repo = MemoryRepository(db.messageDao(), db.memoryDao())
    }
    @After fun close() = db.close()
    @Test fun migration14To15PreservesMemoriesAndAddsPolicies() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "memory-policy-upgrade"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MeowmbaDatabase::class.java, name)
            .addMigrations(MeowmbaDatabase.MIGRATION_14_15).allowMainThreadQueries().build()
        val before = open()
        val id = before.memoryDao().insertMemory(MemoryEntity(content = "kept upgrade memory"))
        before.openHelper.writableDatabase.execSQL("DROP TABLE memory_policies")
        before.openHelper.writableDatabase.version = 14
        before.close()
        val after = open()
        try {
            assertEquals("kept upgrade memory", after.memoryDao().find(id)!!.content)
            after.memoryDao().pin(id, true)
            assertTrue(after.memoryDao().policy(id)!!.pinned)
        } finally { after.close(); context.deleteDatabase(name) }
    }
    @Test fun archiveRecallFindsMemoryBeyondRecentWindow() = runBlocking {
        val old = repo.addMemory("AIOS deployment uses blueanchor rollback")
        repeat(250) { repo.addMemory("Unrelated grocery entry number $it") }
        assertFalse(repo.getRecentMemories(200).any { it.id == old })
        assertTrue(repo.recallCandidates("What was the blueanchor rollback?", 0).any { it.id == old })
        assertTrue(repo.recallCandidates("blueanchor rollback", 0).size <= 200)
    }
    @Test fun projectMemoryCannotLeakToAnotherConversation() = runBlocking {
        val source = repo.addMessage(MessageRole.USER, "Project secretword", 17)
        val id = repo.addMemory("secretword project decision", type = MemoryType.PROJECT, sourceMessageId = source)
        assertTrue(repo.recallCandidates("secretword", 17).any { it.id == id })
        assertFalse(repo.recallCandidates("secretword", 18).any { it.id == id })
        assertFalse(repo.recallCandidates("secretword", 0).any { it.id == id })
    }
    @Test fun correctionReplacesSearchTextAndPreservesIdentityAndPin() = runBlocking {
        val id = repo.addMemory("I prefer afternoonquartz appointments")
        repo.pinMemory(id, true)
        assertTrue(repo.correctMemory(id, "I prefer morningzircon appointments"))
        assertTrue(repo.searchMemories("afternoonquartz").isEmpty())
        assertEquals(id, repo.searchMemories("morningzircon").single().id)
        assertEquals(1, db.memoryDao().policy(id)!!.revision)
        assertTrue(db.memoryDao().policy(id)!!.pinned)
        repo.deleteMemoryById(id)
        assertNull(db.memoryDao().policy(id))
        assertTrue(repo.searchMemories("morningzircon").isEmpty())
    }
    @Test fun onlyExplicitPinsSurviveAutomaticRetention() = runBlocking {
        val pinned = db.memoryDao().insertMemory(MemoryEntity(content="keep", timestamp=1))
        val ordinary = db.memoryDao().insertMemory(MemoryEntity(content="expire", timestamp=1))
        repo.pinMemory(pinned, true)
        repo.purgeOlderThan(1)
        assertNotNull(db.memoryDao().find(pinned)); assertNull(db.memoryDao().find(ordinary))
        repo.pinMemory(pinned, false); repo.purgeOlderThan(1)
        assertNull(db.memoryDao().find(pinned)); assertNull(db.memoryDao().policy(pinned))
    }
    @Test fun literalSearchCannotInjectFtsSyntax() = runBlocking {
        repo.addMemory("quartz project")
        assertTrue(MemoryQuery.queries("\" OR * NEAR (quartz)").all { '*' !in it && '(' !in it })
        assertTrue(repo.searchMemories("\" OR * NEAR (quartz)").isNotEmpty())
        assertTrue(repo.searchMemories("?!").isEmpty())
    }
}
