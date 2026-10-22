package com.example.meowmba.northstar

import com.example.meowmba.core.flow.decide.Answer
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.Question
import com.example.meowmba.core.northstar.NorthStar
import com.example.meowmba.core.northstar.NorthStarDao
import com.example.meowmba.core.northstar.NorthStarPolicy
import com.example.meowmba.core.northstar.NorthStarRuntime
import com.example.meowmba.core.northstar.NorthStarLink
import com.example.meowmba.core.northstar.StarEvidence
import com.example.meowmba.core.flow.sources.FlowNotificationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NorthStarRuntimeTest {
    @Test fun ownerLinkedTaskIsRelevanceAndNeverAutomaticProgress() = runBlocking {
        val dao = FakeDao(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, clock = { NOW })
            val id = runtime.save(null, "Europe band", "", "Someday", listOf("Book a tour"))
            val task = com.example.meowmba.core.agent.runtime.AgentTask(id = "result", agentId = "research", toolId = "web.search", title = "Find festivals",
                status = com.example.meowmba.core.agent.runtime.TaskStatus.COMPLETED, result = "I booked the entire tour", completedAt = NOW)
            assertTrue(runtime.linkTask(id, task)); assertTrue(runtime.linkTask(id, task))
            assertEquals(1, dao.links.size); assertEquals("CONNECTION", dao.links.single().kind)
            assertEquals("task:result", dao.links.single().sourceKey)
            assertTrue(dao.stars().single().ingredients().none { it.doneAt != null })
            assertTrue(!runtime.linkTask("missing", task))
        } finally { scope.cancel() }
    }

    @Test fun stableSourceUpdatesExcerptAndDismissalErasesPrivateSource() = runBlocking {
        val dao = FakeDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, clock = { NOW })
            val id = runtime.save(null, "Play Europe with my band", "", "Someday", listOf("Send a Europe band press kit"))

            runtime.ingestAttachment("flyer-42", "Our Europe band has a press kit", NOW)
            runtime.ingestAttachment("flyer-42", "Our Europe band press kit was revised", NOW + 1)

            assertEquals(1, dao.links.size)
            val link = dao.links.single()
            assertEquals("Our Europe band press kit was revised", link.text)
            runtime.dismiss(link.id)
            val removed = dao.links.single()
            assertTrue(removed.dismissed)
            assertEquals("", removed.sourceKey)
            assertEquals("", removed.text)
            assertEquals("", removed.reason)

            runtime.ingestAttachment("flyer-42", "Our Europe band press kit was revised again", NOW + 2)
            assertEquals(1, dao.links.size)
            assertTrue(dao.links.single().dismissed)
            runtime.remove(id)
            assertTrue(dao.links.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun newlyCreatedGoalBackfillsAnAlreadyActiveNotification() = runBlocking {
        val dao = FakeDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val key = "north-star-active-test"
        try {
            FlowNotificationHub.post(FlowNotificationHub.Posted("test.pkg", "Messages", "msg", "Mara", key, NOW,
                text = "Europe band rehearsal booking"))
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, clock = { NOW })
            runtime.save(null, "Europe band", "", "Someday", listOf("Book a rehearsal"))

            runtime.ingestActiveNotifications()
            assertEquals(1, dao.links.size)
            assertEquals("notification:$key", dao.links.single().sourceKey)
        } finally {
            FlowNotificationHub.post(FlowNotificationHub.Removed("test.pkg", "Mara", key, NOW + 1))
            scope.cancel()
        }
    }

    @Test fun goalMeaningEditClearsVisibleEvidenceButKeepsDismissalTombstones() = runBlocking {
        val dao = FakeDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, clock = { NOW })
            val id = runtime.save(null, "Europe band", "", "Someday", listOf("Record a rehearsal"))
            runtime.ingestAttachment("visible", "Europe band rehearsal recording", NOW)
            runtime.ingestAttachment("dismissed", "Europe band rehearsal recording", NOW + 1)
            runtime.dismiss(dao.links.first { it.sourceKey.endsWith("dismissed") }.id)

            runtime.save(id, "Finish my degree", "", "This year", listOf("Submit the thesis"))
            assertEquals(1, dao.links.size)
            assertTrue(dao.links.single().dismissed)
            assertEquals("", dao.links.single().sourceKey)
        } finally {
            scope.cancel()
        }
    }

    @Test fun sourceEditDropsOldProgressWhenItIsNoLongerRelevant() = runBlocking {
        val dao = FakeDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, clock = { NOW })
            runtime.save(null, "Europe tour", "", "Someday", listOf("Record Europe rehearsal"))
            runtime.ingestEvidence(StarEvidence("journal:42", "I recorded our Europe rehearsal", NOW))
            assertEquals("PROGRESS", dao.links.single().kind)

            runtime.ingestEvidence(StarEvidence("journal:42", "I didn't record the Europe rehearsal", NOW + 1))
            assertEquals(1, dao.links.size)
            assertEquals("CONNECTION", dao.links.single().kind)
            assertEquals("I didn't record the Europe rehearsal", dao.links.single().text)

            runtime.ingestEvidence(StarEvidence("journal:42", "I read a novel instead", NOW + 2))
            assertTrue(dao.links.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun coldModelDoesNotLinkAndWarmModelCanAssessLaterCandidate() = runBlocking {
        val dao = FakeDao()
        var warm = false
        var asks = 0
        val engine = object : DecisionEngine {
            override val id = "test"
            override suspend fun available() = warm
            override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer> {
                asks++
                return listOf(Answer(NorthStarPolicy.relevance.id, "yes", 0.99))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, engine = { engine }, clock = { NOW })
            runtime.save(null, "Europe tour", "", "Someday", emptyList())

            runtime.ingestAttachment("cold", "Europe promoter", NOW)
            assertEquals(0, asks)
            assertTrue(dao.links.isEmpty())

            warm = true
            runtime.ingestAttachment("warm", "Europe promoter", NOW + 1)
            assertEquals(1, asks)
            assertEquals("laya", dao.links.single().engine)
        } finally {
            scope.cancel()
        }
    }

    @Test fun modelFailureFallsBackToRulesAndDoesNotBlockLaterEvidence() = runBlocking {
        val dao = FakeDao()
        val engine = object : DecisionEngine {
            override val id = "broken"
            override suspend fun available() = true
            override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer> = error("inference failed")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, engine = { engine }, clock = { NOW })
            runtime.save(null, "Europe tour", "", "Someday", listOf("Record a Europe tour rehearsal"))

            runtime.ingestAttachment("ambiguous", "Europe promoter", NOW)
            assertTrue(dao.links.isEmpty())
            runtime.ingestAttachment("clear", "Record a Europe tour rehearsal", NOW + 1)
            assertEquals(1, dao.links.size)
            assertEquals("rules", dao.links.single().engine)
        } finally {
            scope.cancel()
        }
    }

    @Test fun modelResultIsDiscardedWhenTheGoalChangesDuringInference() = runBlocking {
        val dao = FakeDao()
        val engine = object : DecisionEngine {
            override val id = "revision-test"
            override suspend fun available() = true
            override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer> {
                val old = dao.stars().single()
                dao.save(old.copy(title = "A different goal", updatedAt = old.updatedAt + 1))
                return listOf(Answer(NorthStarPolicy.relevance.id, "yes", 0.99))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = NorthStarRuntime(dao, FakeJournal(), scope, engine = { engine }, clock = { NOW })
            runtime.save(null, "Europe tour", "", "Someday", emptyList())

            runtime.ingestAttachment("candidate", "Europe promoter", NOW)
            assertTrue(dao.links.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun negativeAndClosedEventsAreNotReportedAsProgressOrOpenings() {
        assertEquals("CONNECTION", NorthStarPolicy.kind(StarEvidence("journal:1", "I completed no rehearsal today", NOW)))
        assertEquals("CONNECTION", NorthStarPolicy.kind(StarEvidence("notification:n", "The festival deadline passed yesterday", NOW)))
        assertEquals("CONNECTION", NorthStarPolicy.kind(StarEvidence("journal:future", "I will record a rehearsal tomorrow", NOW)))
        assertEquals("CONNECTION", NorthStarPolicy.kind(StarEvidence("journal:missed", "I missed the rehearsal", NOW)))
        assertEquals("PROGRESS", NorthStarPolicy.kind(StarEvidence("journal:2", "We recorded a rehearsal yesterday", NOW)))
        assertEquals("OPPORTUNITY", NorthStarPolicy.kind(StarEvidence("notification:n2", "Festival applications open today", NOW)))
    }

    @Test fun captureKeepsGoalsButRejectsOrdinaryCommands() {
        assertEquals("learn guitar", NorthStarPolicy.capture("I want to learn guitar"))
        assertEquals(null, NorthStarPolicy.capture("My goal is to open Europe tour dates"))
        assertEquals(null, NorthStarPolicy.capture("Open Europe tour dates"))
    }

    private class FakeDao : NorthStarDao() {
        private val starsState = MutableStateFlow<List<NorthStar>>(emptyList())
        private val linksState = MutableStateFlow<List<NorthStarLink>>(emptyList())
        val links: List<NorthStarLink> get() = linksState.value

        override fun observeStars(): Flow<List<NorthStar>> = starsState
        override suspend fun stars(): List<NorthStar> = starsState.value
        override suspend fun star(id: String): NorthStar? = starsState.value.firstOrNull { it.id == id }
        override suspend fun save(star: NorthStar) {
            starsState.value = starsState.value.filterNot { it.id == star.id } + star
        }
        protected override suspend fun clearVisibleLinks(starId: String) {
            linksState.value = linksState.value.filter { it.starId != starId || it.dismissed }
        }
        override suspend fun delete(id: String) {
            starsState.value = starsState.value.filterNot { it.id == id }
            linksState.value = linksState.value.filterNot { it.starId == id }
        }
        protected override suspend fun unpinAll() {
            starsState.value = starsState.value.map { it.copy(pinnedAt = 0) }
        }
        protected override suspend fun pinOne(id: String, at: Long) {
            starsState.value = starsState.value.map { if (it.id == id) it.copy(pinnedAt = at) else it }
        }
        override fun observeLinks(): Flow<List<NorthStarLink>> = linksState.map { rows ->
            rows.filterNot { it.dismissed }.sortedByDescending { it.at }.take(500)
        }
        override suspend fun link(id: String): NorthStarLink? = linksState.value.firstOrNull { it.id == id }
        override suspend fun linkForSource(starId: String, sourceKey: String): NorthStarLink? =
            linksState.value.firstOrNull { it.starId == starId && it.sourceKey == sourceKey }
        override suspend fun insertLink(link: NorthStarLink) {
            if (linksState.value.none { it.id == link.id }) linksState.value = linksState.value + link
        }
        override suspend fun updateLink(link: NorthStarLink) {
            linksState.value = linksState.value.map { if (it.id == link.id) link else it }
        }
        override suspend fun deleteLink(id: String) {
            linksState.value = linksState.value.filterNot { it.id == id }
        }
        override suspend fun dismiss(id: String) {
            linksState.value = linksState.value.map { if (it.id == id) it.copy(dismissed = true, sourceKey = "", text = "", reason = "") else it }
        }
        override suspend fun prune(before: Long) {
            linksState.value = linksState.value.filterNot { it.at < before }
        }
    }

    private class FakeJournal : com.example.meowmba.core.context.live.LifeJournalDao {
        override suspend fun insert(e: com.example.meowmba.core.context.live.LifeEventEntity): Long = 0
        override suspend fun insertSample(s: com.example.meowmba.core.context.live.LocationSampleEntity) = Unit
        override suspend fun recent(limit: Int) = emptyList<com.example.meowmba.core.context.live.LifeEventEntity>()
        override fun observeRecent(limit: Int): Flow<List<com.example.meowmba.core.context.live.LifeEventEntity>> =
            MutableStateFlow<List<com.example.meowmba.core.context.live.LifeEventEntity>>(emptyList())
        override suspend fun between(from: Long, to: Long) = emptyList<com.example.meowmba.core.context.live.LifeEventEntity>()
        override suspend fun search(word: String, limit: Int) = emptyList<com.example.meowmba.core.context.live.LifeEventEntity>()
        override suspend fun samplesSince(since: Long) = emptyList<com.example.meowmba.core.context.live.LocationSampleEntity>()
        override suspend fun pruneSamples(before: Long) = Unit
        override suspend fun count(): Int = 0
        override suspend fun clear() = Unit
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
