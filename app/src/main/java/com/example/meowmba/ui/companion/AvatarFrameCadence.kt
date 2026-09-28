package com.example.meowmba.ui.companion

import android.os.PowerManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Keep animation time real while bounding decorative redraws on high-refresh phones.
 *
 * Two rates, because one number was never right for both jobs. A performed
 * clip or a face being tracked needs the snappier active rate. Idle breathing,
 * drifting weather and background particles do not: they are multi-second
 * cycles that remain readable at a lower idle rate.
 *
 * Root cause of post-1.3.x heat: ACTIVE was briefly ~60 FPS (16 ms). On OLED
 * phones that meant continuous GPU/CPU work even when "just breathing", and
 * THERMAL_STATUS_LIGHT never reduced the admit rate — only MODERATE+ paused
 * via [AvatarWorkPolicy]. Default ACTIVE is now ~30 FPS; user Avatar FPS
 * target (15–60) selects the cool ACTIVE rate; LIGHT halves effective
 * FPS; MODERATE+ refuses admits here as belt-and-suspenders (policy still pauses).
 *
 * With a [phase], the avatar's clock is the [leader] and keeps exactly this
 * rule; other clocks on the screen follow it (see [SharedFramePhase]).
 */
internal class AvatarFrameCadence(
    private val phase: SharedFramePhase? = null,
    private val leader: Boolean = false,
) {
    private var previous = Long.MIN_VALUE
    private var lastPolledNanos = Long.MIN_VALUE
    private var vsyncNanos = DEFAULT_VSYNC_NS
    private var admittedIntervalNs = 0L

    /**
     * [active] = something is actually performing or being tracked.
     * [fpsTarget] = user ACTIVE target FPS when cool (15–60 inclusive; default 30).
     * [thermalStatus] = [PowerManager] thermal severity.
     */
    fun admit(
        nowNanos: Long,
        active: Boolean = true,
        fpsTarget: Int = DEFAULT_ACTIVE_FPS,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): Boolean {
        observeVsync(nowNanos)
        if (thermalStatus >= THERMAL_STOP_AT) return false
        val intervalNs = intervalNs(active, fpsTarget, thermalStatus)
        val admitted = when {
            phase == null -> ownRule(nowNanos, intervalNs)
            leader -> ownRule(nowNanos, intervalNs).also { if (it) phase.leaderAdmitted(nowNanos, intervalNs) }
            else -> when (phase.follow(nowNanos, intervalNs)) {
                SharedFramePhase.Follow.DRAW -> joinLeader(nowNanos, intervalNs(true, fpsTarget, thermalStatus))
                SharedFramePhase.Follow.WAIT -> false
                SharedFramePhase.Follow.OWN -> ownRule(nowNanos, intervalNs)
            }
        }
        if (admitted) admittedIntervalNs = intervalNs
        return admitted
    }

    private fun ownRule(nowNanos: Long, intervalNs: Long): Boolean {
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until intervalNs) return false
        previous = nowNanos
        return true
    }

    /** Draw with the leader, but never sooner than the active rate would allow. */
    private fun joinLeader(nowNanos: Long, activeIntervalNs: Long): Boolean {
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until activeIntervalNs) return false
        previous = nowNanos
        return true
    }

    /**
     * How long a frame loop may sleep right after an admitted frame instead of
     * waking on every vsync only to be refused. Those vsyncs cannot be admitted,
     * so each wake just cost a main-thread frame callback (up to 120 per second
     * on the Fold for ~13-20 admitted frames). The wake comes
     * [WAKE_MARGIN_VSYNCS] vsyncs early, so the next admitted frame lands on the
     * same vsync as it would have. Zero means keep polling every vsync.
     *
     * Call once, right after an admitted frame and immediately before sleeping.
     */
    fun idleNanosAfterAdmit(): Long {
        if (admittedIntervalNs == 0L) return 0L
        val sleep = admittedIntervalNs - WAKE_MARGIN_VSYNCS * vsyncNanos
        if (sleep < vsyncNanos) return 0L
        // The first poll after a sleep is not one vsync after this frame.
        lastPolledNanos = Long.MIN_VALUE
        return sleep
    }

    /** Learns the display's vsync period from consecutively polled frames. */
    private fun observeVsync(nowNanos: Long) {
        val last = lastPolledNanos
        lastPolledNanos = nowNanos
        if (last == Long.MIN_VALUE) return
        val delta = nowNanos - last
        if (delta in MIN_VSYNC_NS..MAX_VSYNC_NS) vsyncNanos = delta
    }

    companion object {
        const val DEFAULT_ACTIVE_FPS = 30
        const val MIN_FPS = 15
        const val MAX_FPS = 60
        const val MIN_IDLE_FPS = 8
        /** Hard floor (~120 Hz) so a mis-set target cannot peg the vsync loop. */
        const val MIN_INTERVAL_NS = 8_000_000L
        /** ~30 FPS default active interval (documentation / tests). */
        const val ACTIVE_INTERVAL_NS = 33_000_000L
        /** ~15 FPS ambient at default target. */
        const val IDLE_INTERVAL_NS = 66_000_000L
        /** Vsyncs of slack a sleeping loop keeps before its next admissible frame. */
        const val WAKE_MARGIN_VSYNCS = 2
        /** Assumed until two consecutive vsyncs are seen (60 Hz, the slowest common panel). */
        private const val DEFAULT_VSYNC_NS = 16_666_667L
        private const val MIN_VSYNC_NS = 4_000_000L
        private const val MAX_VSYNC_NS = 34_000_000L

        /** The admission interval; the same arithmetic the private rule always used. */
        fun intervalNs(active: Boolean, fpsTarget: Int, thermalStatus: Int): Long {
            val target = fpsTarget.coerceIn(MIN_FPS, MAX_FPS)
            val baseFps = if (active) target.toFloat() else (target / 2f).coerceAtLeast(MIN_IDLE_FPS.toFloat())
            // The top eased tier keeps a deliberately sparse 7.5 FPS lifeline so the avatar
            // can settle and acknowledge touch without resuming the old hot loop.
            val thermalMul = when {
                thermalStatus >= THERMAL_EASE_AT -> 0.25f
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 0.5f
                else -> 1f
            }
            val effectiveFps = (baseFps * thermalMul).coerceAtLeast(1f)
            return (1_000_000_000.0 / effectiveFps).toLong().coerceAtLeast(MIN_INTERVAL_NS)
        }
    }
}

/**
 * Puts the background clocks' redraws on the avatar's vsyncs.
 *
 * The avatar and the background scene (weather, animated rooms) each run a
 * clock at the same idle interval. With private phases they drift apart: a
 * September 22 trace on the Fold showed two ~20 FPS clocks interleaved into a
 * steady 40 FPS of full-window redraws, so RenderThread, path rasterization and
 * GPU composition ran twice as often as either layer moved.
 *
 * The avatar's clock is the leader and keeps its own rule unchanged. A clock
 * with the same interval as the leader draws exactly when the leader draws, so
 * its rate is unchanged too; it only joins no sooner than the active rate would
 * allow. With a different interval (a performing avatar), or once the leader
 * stops, it runs on its own rule as before.
 *
 * Only touched from Compose frame callbacks on the main thread.
 */
internal class SharedFramePhase {
    private var leaderLast = Long.MIN_VALUE
    private var leaderInterval = 0L

    enum class Follow { DRAW, WAIT, OWN }

    fun leaderAdmitted(nowNanos: Long, intervalNs: Long) {
        leaderLast = nowNanos
        leaderInterval = intervalNs
    }

    /**
     * Whether a clock with [intervalNs] should draw on this vsync. Frame
     * callbacks run in any order within a vsync, so the leader's decision is
     * predicted from the rule it applies rather than waited for.
     */
    fun follow(nowNanos: Long, intervalNs: Long): Follow {
        val last = leaderLast
        if (last == Long.MIN_VALUE || intervalNs != leaderInterval) return Follow.OWN
        val since = nowNanos - last
        // Stale: the leader paused or stopped, so its next frame is not coming.
        if (since !in 0L..intervalNs + intervalNs / 2) return Follow.OWN
        return if (since == 0L || since >= intervalNs) Follow.DRAW else Follow.WAIT
    }
}

/**
 * The companion screen's [SharedFramePhase]. Null (the default) keeps each
 * clock private, which is what previews, onboarding and isolated tests use.
 */
internal val LocalSharedFramePhase = staticCompositionLocalOf<SharedFramePhase?> { null }
