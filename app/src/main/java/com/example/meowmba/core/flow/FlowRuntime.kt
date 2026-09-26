package com.example.meowmba.core.flow

import android.content.Context
import com.example.meowmba.core.flow.decide.Assessor
import com.example.meowmba.core.flow.decide.AssessorChain
import com.example.meowmba.core.flow.decide.RulesAssessor
import com.example.meowmba.core.flow.delivery.AndroidFlowNotifier
import com.example.meowmba.core.flow.delivery.FlowActionBus
import com.example.meowmba.core.flow.delivery.FlowNotifier
import com.example.meowmba.core.flow.delivery.FlowSpeaker
import com.example.meowmba.core.flow.delivery.InterruptDelivery
import com.example.meowmba.core.flow.sources.SituationAssembler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Everything Meow Flow needs, assembled in one place so the integrator only has to create
 * it, feed evidence and render [snapshot] (see docs/FLOW_WIRING_GUIDE.md).
 *
 * Engine order (owner D8): Laya on the phone → Jev only when the user allows it → rules.
 */
class FlowRuntime(
    context: Context,
    private val scope: CoroutineScope,
    speaker: FlowSpeaker,
    val store: IntentionStore = InMemoryIntentionStore(),
    calendar: suspend (Long) -> List<CalendarItem> = { emptyList() },
    surface: () -> Surface = { Surface.FLOW_ON_SCREEN },
    workContext: () -> WorkContext = { WorkContext() },
    route: () -> RouteFacts? = { null },
    opportunities: suspend (Long, String?) -> List<Opportunity> = { _, _ -> emptyList() },
    private val laya: Assessor? = null,
    private val jev: Assessor? = null,
    notifier: FlowNotifier = AndroidFlowNotifier(context),
    outcomes: OutcomeLog = InMemoryOutcomeLog(),
    clock: () -> Long = System::currentTimeMillis,
    calendarAvailable: () -> Boolean = { true },
    /** Shown in the dateline when there is room, e.g. current weather. */
    ambient: () -> Signal? = { null },
) {
    private val managedWriteLock = Mutex()
    private val prefs = com.example.meowmba.core.backup.BackupPreferences.open(context, FlowSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsStore = FlowSettingsStore(SharedPreferencesKeyValue(prefs))
    private val settingsState = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<FlowSettings> = settingsState.asStateFlow()

    val bus = EvidenceBus()
    val work = WorkStateDetector()
    val assembler = SituationAssembler(store, work, workContext, { settingsState.value.detectWorkEnd }, calendar, surface, route, opportunities, calendarAvailable = calendarAvailable, ambient = ambient)

    /** Chain rebuilt per decision so the Jev switch takes effect immediately. */
    private val chain = object : Assessor {
        var last: AssessorChain? = null
        override val id: String get() = last?.lastUsed ?: "flow"
        override suspend fun available() = true
        override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>? {
            val c = AssessorChain(listOfNotNull(laya, jev.takeIf { settingsState.value.allowJev }, RulesAssessor()))
            last = c
            return c.assess(situation, intentions)
        }
    }

    val controller = FlowController(scope, store, bus, assembler, chain, settings,
        PrefsLedgerStore(SharedPreferencesKeyValue(prefs)), outcomes, clock = clock)
    val snapshot: StateFlow<FlowSnapshot> get() = controller.snapshot
    private val delivery = InterruptDelivery(speaker, notifier)
    private var jobs: List<Job> = emptyList()

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch { controller.snapshot.collect { delivery.deliver(it.interrupt) } },
            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                com.example.meowmba.core.flow.sources.FlowNotificationHub.events.collect { onNotification(it) }
            },
            scope.launch {
                FlowActionBus.actions.collect { a ->
                    if (a.action == "primary") controller.onPrimary(a.intentionId) else controller.onLater(a.intentionId)
                    delivery.resolved(a.intentionId)
                }
            },
        )
        // Reconcile active notifications after the collector is installed. This covers a
        // listener connection that happened before FlowRuntime started without replaying removals.
        jobs = jobs + scope.launch {
            com.example.meowmba.core.flow.sources.FlowNotificationHub.activePosted().forEach { onNotification(it.copy(seed = true)) }
        }
        controller.start()
    }

    fun stop() { jobs.forEach { it.cancel() }; jobs = emptyList(); controller.stop(); delivery.deliver(null) }

    fun publish(evidence: Evidence) {
        // Update first, even before collectors start. Two collectors raced the first snapshot.
        assembler.observe(evidence)
        bus.publish(evidence)
    }

    /**
     * Apply intentions generated from app data (AppIntentions). New ones are added; changed ones
     * keep the user's status (Done/Later); managed ones that no longer apply are closed.
     */
    suspend fun syncManaged(fresh: List<Intention>, prefixes: List<String>) = managedWriteLock.withLock {
        val existing = store.intentions.value.associateBy { it.id }
        fresh.forEach { source ->
            val legacyId = FlowComments.target(source)?.let {
                "${com.example.meowmba.core.flow.sources.AppIntentions.CALENDAR}${it.begin}-${it.title.trim().ifBlank { "Busy" }.take(60).hashCode().toUInt()}"
            }
            // Preserve Later/Done across the legacy title-hash → provider-ID migration,
            // but never transfer a colliding legacy card's state to two different events.
            val legacy = legacyId?.takeIf { key -> fresh.count { candidate ->
                FlowComments.target(candidate)?.let {
                    "${com.example.meowmba.core.flow.sources.AppIntentions.CALENDAR}${it.begin}-${it.title.trim().ifBlank { "Busy" }.take(60).hashCode().toUInt()}"
                } == key
            } == 1 }?.let { store.find(it) }
            val old = store.find(source.id) ?: legacy
            val n = source.copy(facts = FlowComments.retainComment(source.facts, old?.facts.orEmpty()))
            when {
                old == null -> store.upsert(n)
                old.id != n.id || old.text != n.text || old.dueAt != n.dueAt || old.facts != n.facts ->
                    store.upsert(n.copy(status = old.status, snoozedUntil = old.snoozedUntil, createdAt = old.createdAt))
            }
        }
        val keep = fresh.map { it.id }.toSet()
        val gone = existing.values.filter { i ->
            i.id !in keep && prefixes.any { i.id.startsWith(it) } &&
                (i.status == IntentionStatus.OPEN || i.status == IntentionStatus.SNOOZED)
        }.map { it.id }
        if (gone.isNotEmpty()) store.closeSatisfied(gone)
    }

    /** Something the user said in chat; saved only for explicit "I need to…" phrasing (rules mode). */
    suspend fun cancelCalendarOccurrence(title: String, begin: Long, eventId: Long? = null) = managedWriteLock.withLock {
        val id = "${com.example.meowmba.core.flow.sources.AppIntentions.CALENDAR}$begin-${title.take(60).hashCode().toUInt()}"
        // Exact provider title + occurrence key; never retire unrelated recurring reminders.
        val ids = store.intentions.value.filter { item ->
            val target = FlowComments.target(item)
            if (target != null) target.begin == begin && target.title == title &&
                (eventId == null || eventId == target.eventId)
            else item.id == id
        }.map { it.id }.ifEmpty { listOf(eventId?.let { "cal-event-$it-$begin" } ?: id) }
        ids.forEach { key ->
            store.setStatus(key, IntentionStatus.DROPPED)
            controller.onPrimary(key, completes = false)
            delivery.resolved(key)
        }
        publish(Evidence(EvidenceType.CALENDAR, System.currentTimeMillis(), "cal", "Calendar cancellation confirmed"))
    }

    suspend fun saveComment(id: String, text: String): Boolean = managedWriteLock.withLock {
        val item = store.find(id) ?: return@withLock false
        if (item.status !in setOf(IntentionStatus.OPEN, IntentionStatus.SNOOZED) || text.isBlank()) return@withLock false
        store.upsert(item.copy(facts = item.facts + mapOf(FlowComments.TEXT to text.trim().take(1000),
            FlowComments.AT to System.currentTimeMillis().toString())))
        controller.schedule(immediate = true)
        true
    }

    /** Something the user said in chat; saved only for explicit "I need to…" phrasing (rules mode). */
    suspend fun captureFromChat(text: String, now: Long = System.currentTimeMillis()): Intention? {
        val i = IntentionCapture(null).capture(text, now) ?: return null
        store.upsert(i)
        publish(com.example.meowmba.core.flow.sources.EvidenceMappers.fromCapturedNeed(i.text, now))
        return i
    }

    private suspend fun onNotification(e: com.example.meowmba.core.flow.sources.FlowNotificationHub.Event) {
        val hub = com.example.meowmba.core.flow.sources.FlowNotificationHub
        val apps = com.example.meowmba.core.flow.sources.AppIntentions
        when (e) {
            is com.example.meowmba.core.flow.sources.FlowNotificationHub.Posted -> {
                if (e.seed && hub.activePosted().none { it.notificationKey == e.notificationKey && it.at == e.at }) return
                // Package updates from texts/emails/carrier apps (any sender, including short codes).
                e.delivery?.let { d ->
                    val item = com.example.meowmba.core.flow.sources.DeliveryNotices.toIntention(d, e.sender, e.appLabel, e.at)
                    val old = store.intentions.value.firstOrNull { it.id == item.id }
                    // One item per package moves forward (shipped → arriving → delivered).
                    if (old == null || old.facts["stage"] != item.facts["stage"]) store.upsert(item)
                    publish(Evidence(EvidenceType.NOTIFICATION, e.at, "pkg", item.text, mapOf("stage" to d.stage.name)))
                    return
                }
                // Otherwise only people (messages, calls) matter to Flow. Other notifications ("New from
                // Google Maps Timeline") are not context and would re-run Flow on every post.
                if (e.category !in hub.PEOPLE_CATEGORIES) return
                com.example.meowmba.core.flow.sources.EvidenceMappers
                    .fromNotification(e.packageName, e.appLabel, e.category, e.sender, e.at)?.let(::publish)
                apps.fromMessage(e.packageName, e.appLabel, e.sender, e.at, e.notificationKey)?.let { m ->
                    // Replace pre-172 sender-only rows, which cannot identify a conversation.
                    val legacy = "${apps.MESSAGE}${e.packageName}-${e.sender.trim().take(40).lowercase(java.util.Locale.ROOT).hashCode().toUInt()}"
                    if (store.find(legacy) != null) store.closeSatisfied(listOf(legacy))
                    val old = store.find(m.id)
                    // A new message reopens a finished "Reply to" for the same person.
                    if (old == null || (!e.seed && (old.status == IntentionStatus.DONE || old.status == IntentionStatus.DROPPED))) store.upsert(m)
                }
            }
            is com.example.meowmba.core.flow.sources.FlowNotificationHub.Removed ->
                apps.fromMessage(e.packageName, "", e.sender, 0L, e.notificationKey)?.let { store.closeSatisfied(listOf(it.id)) }
        }
    }

    fun updateSettings(s: FlowSettings) {
        val n = s.normalized()
        settingsStore.save(n)
        settingsState.value = n
    }

    suspend fun onPrimary(intentionId: String) { controller.onPrimary(intentionId); delivery.resolved(intentionId) }
    suspend fun onLater(intentionId: String) { controller.onLater(intentionId); delivery.resolved(intentionId) }
}

/** Persists today's interruption count and last time across restarts. */
class PrefsLedgerStore(private val kv: FlowKeyValue) : LedgerStore {
    override fun load(): InterruptLedger = InterruptLedger(
        day = kv.getString(K_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        countToday = kv.getString(K_COUNT)?.toIntOrNull() ?: 0,
        lastAt = kv.getString(K_LAST)?.toLongOrNull(),
    )

    override fun save(ledger: InterruptLedger) = kv.putAll(mapOf(
        K_DAY to ledger.day?.toString(), K_COUNT to ledger.countToday.toString(), K_LAST to ledger.lastAt?.toString(),
    ))

    companion object {
        const val K_DAY = "flow.ledger.day"
        const val K_COUNT = "flow.ledger.count"
        const val K_LAST = "flow.ledger.last"
    }
}
