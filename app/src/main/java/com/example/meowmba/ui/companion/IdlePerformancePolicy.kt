package com.example.meowmba.ui.companion

import kotlin.random.Random

/**
 * One eligibility rule for starting and continuing an unsolicited performance.
 * Muting capture is a privacy choice, not a request to freeze the character.
 * Mouth energy is separate because a muted microphone can coexist with TTS.
 * The caller supplies foreground/thermal/pause eligibility through stageAllowed
 * and may hold unsolicited acting during a serious conversation through socialContextAllowed.
 * The compact Brain monitor leaves this visual stage visible. Speech and other
 * bare-face policies continue to use faceModeActive without this exception.
 */
internal fun idlePerformanceAllowed(
    state: CompanionState,
    mouthEnergy: Float,
    stageAllowed: Boolean = true,
    socialContextAllowed: Boolean = true,
): Boolean = stageAllowed && socialContextAllowed && faceModeActive(state.copy(showNeuralInfo = false)) &&
    !state.showConnectorsScreen && !state.showBrowser && !state.driving && !state.songRecognitionPrompt &&
    state.activeTask == null && state.pendingApproval == null && state.emotionPreview == null &&
    mouthEnergy.isFinite() && mouthEnergy <= .02f &&
    when (state.interactionState) {
        InteractionState.IDLE, InteractionState.MUTED, InteractionState.OFFLINE -> true
        else -> false
    }

/**
 * Random quiet acting interleaved with the existing native scenes. A shuffled
 * scene bag guarantees Butterfly Chase and every other scene get a turn instead
 * of repeatedly losing a random draw to tiny ear movements. Scene cooldowns
 * include their duration; they never stack on top of a previous performance.
 * No timer, audio, camera or physical-body operation is owned by this selector.
 */
class IdlePerformanceSelector(
    private val random: Random = Random.Default,
    initialHistory: PerformanceHistory = PerformanceHistory(),
    private val onHistoryChanged: (PerformanceHistory) -> Unit = {},
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {
    private val quietPool = AvatarPerformanceCatalog.entries.filter { it.ambient }
    private val scenes = AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }
    private var lastAnimal: AvatarAnimal? = null
    private val sceneBag = ArrayDeque<AvatarPerformance>()
    private var history = initialHistory.validated(wallClockMs())
    private val recentIds = ArrayDeque(history.recent.takeLast(3).map { it.id })
    private val recentFamilies = ArrayDeque(history.recent.takeLast(2).map { it.family })
    private var lastSceneId: String? = history.lastScene?.id
    private var lastSceneFamily: String? = history.lastScene?.family
    private var nextSceneAtMs = 0L
    private var balancedSceneAtMs = 0L
    private var lastPickAtMs: Long? = null
    private var restored = false
    private var firstPickAtMs = 0L

    init {
        val byId = scenes.associateBy { it.id }
        sceneBag.addAll(history.remainingSceneIds.mapNotNull(byId::get))
    }

    fun pick(
        nowMs: Long,
        scenesEnabled: Boolean = true,
        reducedMotion: Boolean = false,
        playfulness: Playfulness = Playfulness.BALANCED,
        animal: AvatarAnimal? = null,
    ): AvatarPerformance? {
        if (animal != lastAnimal) { sceneBag.clear(); lastAnimal = animal }
        val eligibleScenes = scenes.filter { scene ->
            LumenSignatureScenes.find(scene.id)?.let { animal == null || it.animal == animal } ?: true
        }
        if (nowMs < 0) return null
        val last = lastPickAtMs
        if (last != null && (nowMs < last || nowMs - last < MIN_INTERVAL_MS)) return null
        if (!restored) {
            restored = true
            val wallNow = wallClockMs()
            // Translate surviving wall-clock recovery once. Later clock corrections
            // cannot freeze or hurry this session's monotonic cooldowns.
            fun remaining(at: Long?, duration: Long): Long =
                if (at == null || at <= 0L || wallNow < at) 0L
                else (duration - (wallNow - at)).coerceIn(0L, duration)
            firstPickAtMs = deadline(nowMs, remaining(history.recent.lastOrNull()?.playedAtMs, MIN_INTERVAL_MS))
            val scene = scenes.firstOrNull { it.id == lastSceneId }
            nextSceneAtMs = deadline(nowMs, remaining(history.lastScene?.playedAtMs,
                (scene?.spec?.durationMs?.toLong() ?: 0L) + SCENE_RECOVERY_MS))
            balancedSceneAtMs = nextSceneAtMs
        }
        if (nowMs < firstPickAtMs) return null
        // Becoming quieter may lengthen recovery; a later setting change never
        // shortens a cooldown already promised by an earlier selection.
        if (playfulness != Playfulness.LIVELY) nextSceneAtMs = maxOf(nextSceneAtMs, balancedSceneAtMs)
        val selected = if (scenesEnabled && !reducedMotion && playfulness != Playfulness.QUIET &&
            nowMs >= nextSceneAtMs && scenes.isNotEmpty()) {
            if (sceneBag.isEmpty()) {
                val shuffled = eligibleScenes.shuffled(random).toMutableList()
                if (shuffled.size > 1 && shuffled.first().id == lastSceneId) {
                    val other = shuffled.removeAt(1)
                    shuffled.add(0, other)
                }
                sceneBag.addAll(shuffled)
            }
            // Several hats share one family. Let a chase/read/play scene take
            // the next slot when available instead of a run of costume changes.
            val familyOptions = sceneBag.filter { it.family != lastSceneFamily }.ifEmpty { sceneBag.toList() }
            val candidates = varied(familyOptions)
            sceneBag.removeAt(sceneBag.indexOfFirst { it.id == candidates.first().id }).also {
                lastSceneId = it.id
                lastSceneFamily = it.family
                balancedSceneAtMs = deadline(nowMs, it.spec.durationMs + SCENE_RECOVERY_MS)
                nextSceneAtMs = deadline(nowMs, it.spec.durationMs +
                    if (playfulness == Playfulness.LIVELY) LIVELY_SCENE_RECOVERY_MS else SCENE_RECOVERY_MS)
            }
        } else varied(quietPool).let { if (it.isEmpty()) null else it[random.nextInt(it.size)] }
        if (selected != null) {
            lastPickAtMs = nowMs
            recentIds.addLast(selected.id)
            if (recentIds.size > 3) recentIds.removeFirst()
            recentFamilies.addLast(selected.family)
            if (recentFamilies.size > 2) recentFamilies.removeFirst()
            val wallNow = wallClockMs().coerceAtLeast(0L)
            val entry = PerformanceHistoryEntry(selected.id, selected.family, wallNow)
            history = PerformanceHistory(savedAtMs = wallNow,
                recent = (history.recent + entry).takeLast(PerformanceHistory.MAX_RECENT),
                lastScene = if (selected.category == "Scenes") entry else history.lastScene,
                remainingSceneIds = sceneBag.map { it.id }).validated(wallNow)
            // Preference I/O failure must not stop the visible director.
            runCatching { onHistoryChanged(history) }
        }
        return selected
    }

    private fun varied(pool: List<AvatarPerformance>): List<AvatarPerformance> {
        val freshIds = pool.filter { it.id !in recentIds }
        return freshIds.filter { it.family !in recentFamilies }.ifEmpty { freshIds }.ifEmpty {
            pool.filter { it.id != recentIds.lastOrNull() && it.family !in recentFamilies }
        }.ifEmpty { pool.filter { it.id != recentIds.lastOrNull() } }.ifEmpty { pool }
    }

    private fun deadline(nowMs: Long, afterMs: Long): Long =
        if (Long.MAX_VALUE - nowMs < afterMs) Long.MAX_VALUE else nowMs + afterMs

    companion object {
        const val MIN_INTERVAL_MS = 9_000L
        const val SCENE_RECOVERY_MS = 30_000L
        const val LIVELY_SCENE_RECOVERY_MS = 20_000L
    }
}
