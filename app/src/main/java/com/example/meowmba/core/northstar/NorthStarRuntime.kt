package com.example.meowmba.core.northstar

import com.example.meowmba.core.context.live.LifeJournalDao
import com.example.meowmba.core.flow.FlowRuntime
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.core.flow.sources.FlowNotificationHub
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.UUID

/** Local, event-driven linking. One optional Laya question per five minutes; warm phones use rules. */
class NorthStarRuntime(
    private val dao: NorthStarDao,
    private val journal: LifeJournalDao,
    private val scope: CoroutineScope,
    private val engine: () -> DecisionEngine? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tick = MutableStateFlow(clock())
    val views: StateFlow<List<StarView>> = combine(dao.observeStars(), dao.observeLinks(), tick) { stars, links, now ->
        stars.map { NorthStarPolicy.view(it, links, now) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private var started = false
    private val lock = Mutex()
    private var lastModelAt = Long.MIN_VALUE / 2
    private val attempted = LinkedHashSet<String>()

    fun refresh() { tick.value = clock() }

    fun start(flow: FlowRuntime) {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            combine(dao.observeStars(), flow.store.intentions, journal.observeRecent(100)) { stars, items, entries ->
                stars to (items.filterNot { it.id.startsWith(AppIntentions.SETUP) }.map {
                    StarEvidence("flow:${it.id}", it.text + " " + it.facts["because"].orEmpty(), it.createdAt)
                } + entries.map { StarEvidence("journal:${it.rowId}", it.text, it.at) } +
                    FlowNotificationHub.activePosted().map(::notificationEvidence))
            }.collect { (stars, sources) -> ingest(stars, sources.sortedByDescending { it.at }.take(100)) }
        }
        scope.launch(Dispatchers.IO) {
            FlowNotificationHub.events.collect { e ->
                ingestNotification(e)
            }
        }
        // SharedFlow has no replay. Reconcile the same bounded active window that Flow uses so a
        // listener connected before this runtime does not permanently lose a relevant message.
        scope.launch(Dispatchers.IO) {
            ingestActiveNotifications()
        }
    }

    suspend fun save(id: String?, title: String, why: String, horizon: String, ingredients: List<String>): String = lock.withLock {
        val now = clock()
        val old = id?.let { dao.star(it) }
        require(title.trim().length in 3..240) { "Give your North Star a name (3–240 characters)." }
        require(old != null || dao.stars().size < 20) { "Keep up to 20 North Stars. Remove one before adding another." }
        val prior = old?.ingredients().orEmpty().associateBy { it.text }
        val clean = ingredients.map { it.trim().take(200) }.filter { it.isNotBlank() }.distinct().take(20)
            .map { prior[it] ?: Ingredient(it) }
        val star = NorthStar(old?.id ?: UUID.randomUUID().toString(), title.trim(), why.trim().take(600), horizon.trim().take(120),
            clean.encoded(), old?.createdAt ?: now, now, old?.pinnedAt ?: 0)
        dao.saveForGoal(star, old != null && linkingSignature(old) != linkingSignature(star))
        attempted.clear()
        refresh()
        star.id
    }

    suspend fun capture(text: String): String? {
        val title = NorthStarPolicy.capture(text) ?: return null
        if (dao.stars().any { it.title.equals(title, true) }) return null
        return save(null, title, "", "Someday", NorthStarPolicy.suggestedIngredients(title).map { it.text })
    }

    suspend fun markIngredient(starId: String, text: String, done: Boolean) = lock.withLock {
        val star = dao.star(starId) ?: return@withLock
        dao.save(star.copy(ingredientsJson = star.ingredients().map {
            if (it.text == text) it.copy(doneAt = if (done) clock() else null) else it
        }.encoded(), updatedAt = clock()))
        refresh()
    }

    suspend fun linkTask(starId: String, task: com.example.meowmba.core.agent.runtime.AgentTask) = lock.withLock {
        val star = dao.star(starId) ?: return@withLock false
        if (!task.isTerminal || task.result.isNullOrBlank()) return@withLock false
        val key = "task:${task.id}"
        val evidence = StarEvidence(key, task.title, task.completedAt ?: task.createdAt)
        val id = NorthStarPolicy.linkId(star, evidence)
        val record = NorthStarLink(id, starId, key,
            com.example.meowmba.core.agentic.AgenticOutput.scrub(task.title + "\n" + task.result, 2_000),
            evidence.at, "You linked this task result. Review its outcome; this does not mark the goal achieved.", "CONNECTION", "owner")
        if (dao.link(id) == null) dao.insertLink(record) else dao.updateLink(record)
        refresh()
        true
    }

    suspend fun remove(id: String) = lock.withLock { dao.delete(id); attempted.clear() }
    suspend fun dismiss(id: String) = lock.withLock { dao.dismiss(id) }
    suspend fun pin(id: String) = lock.withLock { dao.pin(id, clock()) }

    /** Link an explicitly shared file's locally extracted text to current North Stars. */
    suspend fun ingestAttachment(sourceKey: String, text: String, at: Long = clock()) {
        if (sourceKey.isBlank() || text.isBlank()) return
        ingestEvidence(StarEvidence("attachment:${sourceKey.take(240)}", text.take(2_000), at))
    }

    /** Shared event entrypoint for local sources; callers never provide a goal id. */
    internal suspend fun ingestEvidence(evidence: StarEvidence) = ingest(dao.stars(), listOf(evidence))

    /** Bounded process-local notification backfill, including for a goal created after startup. */
    internal suspend fun ingestActiveNotifications() {
        val evidence = FlowNotificationHub.activePosted().filter { it.text.isNotBlank() }.map(::notificationEvidence)
        if (evidence.isNotEmpty()) ingest(dao.stars(), evidence)
    }

    private suspend fun ingest(stars: List<NorthStar>, sources: List<StarEvidence>) = lock.withLock {
        val now = clock()
        for (e in sources) for (star in stars) {
            if (e.at < star.createdAt - NorthStarPolicy.DAY || e.at > now + 60_000 || e.text.isBlank()) continue
            val id = NorthStarPolicy.linkId(star, e)
            val revision = id + ":" + star.updatedAt + ":" + e.text.hashCode() + ":" + e.at
            if (revision in attempted) continue
            val prior = dao.link(id) ?: dao.linkForSource(star.id, e.key)
            if (prior?.dismissed == true) { remember(revision); continue }
            var match = NorthStarPolicy.match(star, e)
            // Reject unrelated candidates cheaply before considering an on-device model pass.
            val sharesTerm = NorthStarPolicy.terms(star.title + " " + star.ingredients().joinToString(" ") { it.text })
                .intersect(NorthStarPolicy.terms(e.text)).isNotEmpty()
            if (match == null && !sharesTerm) {
                if (prior != null && dao.star(star.id)?.updatedAt == star.updatedAt) dao.deleteLink(prior.id)
                remember(revision)
                continue
            }
            // The source snapshot may be stale while a model pass is running. Do not attach a
            // result scored for an older goal revision after the user has edited that goal.
            val currentStar = dao.star(star.id)
            if (currentStar?.updatedAt != star.updatedAt) continue
            var modelWasAsked = false
            if (match == null && now - lastModelAt >= 5 * 60_000) {
                val answer = try {
                    val decisionEngine = engine()
                    if (decisionEngine?.available() == true) {
                        lastModelAt = now
                        modelWasAsked = true
                        val state = buildJsonObject {
                            putJsonObject("goal") { put("title", star.title); put("ingredients", star.ingredients().joinToString("; ") { it.text }) }
                            putJsonObject("observation") { put("text", e.text.take(1200)) }
                        }.toString()
                        decisionEngine.ask(state, listOf(NorthStarPolicy.relevance))?.singleOrNull()
                    } else null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // DecisionEngine failures must degrade to the rules path, never stop linking.
                }
                if (answer?.questionId == NorthStarPolicy.relevance.id && answer.probability >= 0.9) {
                    match = StarMatch("Laya suggests this is connected to “${star.title}”. Review the excerpt; this isn't proof of progress.",
                        "CONNECTION", "laya")
                }
            }
            // Re-read after a potentially slow model pass; a user edit wins over a result based
            // on the old title/ingredients and will be reconsidered from the new revision.
            val stillCurrent = dao.star(star.id)?.updatedAt == star.updatedAt
            if (stillCurrent && prior != null) {
                if (match == null) dao.deleteLink(prior.id)
                else dao.updateLink(prior.copy(text = e.text.take(1200), at = e.at,
                    reason = match.reason, kind = match.kind, engine = match.engine))
            } else if (match != null && stillCurrent) dao.insertLink(NorthStarLink(id, star.id, e.key,
                e.text.take(1200), e.at, match.reason, match.kind, match.engine))
            // Do not permanently suppress a candidate merely because the on-device model is
            // currently cold or unavailable. A later event/batch can assess it once warm.
            if (stillCurrent && (match != null || modelWasAsked || prior != null)) remember(revision)
        }
        dao.prune(now - 90 * NorthStarPolicy.DAY)
        refresh()
    }

    private fun remember(id: String) {
        attempted += id
        while (attempted.size > 4000) attempted.remove(attempted.first())
    }

    private suspend fun ingestNotification(e: FlowNotificationHub.Event) {
        if (e is FlowNotificationHub.Posted && e.text.isNotBlank()) {
            ingestEvidence(notificationEvidence(e))
        }
    }

    private fun notificationEvidence(e: FlowNotificationHub.Posted) =
        StarEvidence("notification:${e.notificationKey}", "${e.sender}: ${e.text}", e.at)

    private fun linkingSignature(star: NorthStar): String = buildString {
        append(star.title.trim().lowercase())
        star.ingredients().map { it.text.trim().lowercase() }.sorted().forEach { append('\u0000').append(it) }
    }

    /** Add goal context to existing rows without changing their original action or urgency. */
    fun decorate(snapshot: FlowSnapshot, intentions: List<com.example.meowmba.core.flow.Intention> = emptyList()): FlowSnapshot {
        val bySource = views.value.flatMap { v -> v.links.map { it.sourceKey to v.star.title } }.toMap()
        val notificationSourceForId = intentions.mapNotNull { intention ->
            intention.facts["notification_key"]?.let { "flow:${intention.id}" to "notification:$it" }
        }.toMap()
        fun linked(item: com.example.meowmba.core.flow.FlowItem) =
            (bySource["flow:${item.intentionId}"] ?: notificationSourceForId["flow:${item.intentionId}"]?.let(bySource::get))?.let {
            item.copy(because = item.because + " → " + it)
        } ?: item
        return snapshot.copy(main = snapshot.main?.let(::linked), more = snapshot.more.map(::linked),
            interrupt = snapshot.interrupt?.let { it.copy(item = linked(it.item)) })
    }
}
