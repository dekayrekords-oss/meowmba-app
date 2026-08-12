# LOOI Body Personality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the LOOI base perform the same animation clips the companion's face performs, on the same clock, without ever driving off a desk.

**Architecture:** The body becomes a second renderer of `CustomAnimationSpec`. A pure `BodyMotionMap` turns the fourteen face channels into LOOI verbs, filtered by a `BodyProfile` that fails safe to DESK. A `BodyScheduler` plays those verbs on the clip's timeline against a `BodyOutput` interface. A `ConfinementModel` widens the profile only on evidence gathered from motion the companion was going to make anyway.

**Tech Stack:** Kotlin, Android, Jetpack Compose, kotlinx.coroutines, JUnit 4, `kotlinx-coroutines-test`, `org.json:json` (JVM), Gradle KTS.

**Spec:** `docs/superpowers/specs/2026-08-11-looi-body-personality-design.md`

## Global Constraints

- Package root is `com.example.meowmba`. New robot code goes in `com.example.meowmba.core.robot`.
- Unit tests live flat in `app/src/test/java/com/example/meowmba/`, package `com.example.meowmba`, JUnit 4, `org.junit.Assert.*` static imports, backtick test names.
- Unit tests run with `systemProperty("meowmba.idleAnimations", "false")` set for the whole test task (`app/build.gradle.kts:107`). Do not rely on `IdleAnimationDirector` running in tests.
- Coroutine tests use `runTest` / `TestScope` / `runCurrent` from `kotlinx.coroutines.test`, as in `IdleBehaviorTest.kt`.
- `BodyProfile.DESK` is the default everywhere and the value used whenever anything is uncertain. `DESK` must never produce a `Drive`.
- `LooiBleProtocol.rotateFrame(speedPercent, ticks)` has **no direction parameter**. Turn direction is not commandable. Never write code that assumes two turns can cancel.
- `LooiBleLink.driveSpeed(speed0to1: Float)` is **forward only**, range `0f..1f`. There is no reverse channel.
- The body must never block the face. Any `BodyOutput` failure is swallowed by the scheduler.
- Build check: `./gradlew compileDebugKotlin`. Test: `./gradlew testDebugUnitTest`.

---

### Task 1: Body vocabulary and the pure mapper

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/robot/BodyVerb.kt`
- Create: `app/src/main/java/com/example/meowmba/core/robot/BodyMotionMap.kt`
- Test: `app/src/test/java/com/example/meowmba/BodyMotionMapTest.kt`

**Interfaces:**
- Consumes: `CustomAnimationSpec(name, frames)` and `AnimFrame(atMs, eyeOpen, pupilSize, pupilGazeUp, headTiltDeg, earPerk, earDroopDeg, happyEyes, whiskerFlutter, mouth, glowColorHex, tongueOut, bodyBounce, tailWag, sparkle)` from `com.example.meowmba.ui.companion`.
- Produces: `BodyProfile.{DESK,FLOOR}`; `BodyVerb.{Neck,Turn,Drive,Light,Brake}`; `BodyCue(atMs: Int, verb: BodyVerb)`; `BodyMotionMap.cues(spec: CustomAnimationSpec, profile: BodyProfile): List<BodyCue>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/BodyMotionMapTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.robot.BodyCue
import com.example.meowmba.core.robot.BodyMotionMap
import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.CustomAnimations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The face-to-body mapper is pure, so every rule it encodes is pinned here.
 * The load-bearing test is `desk profile never drives` — the safety property
 * the whole design rests on.
 */
class BodyMotionMapTest {

    private fun spec(vararg frames: AnimFrame) =
        CustomAnimationSpec("test", frames.toList())

    @Test
    fun `gaze up becomes neck pitch, inverted`() {
        // gaze 1.0 = looking up; neck pitch -1 = up.
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, pupilGazeUp = 1.0f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val neck = cues.first { it.verb is BodyVerb.Neck }.verb as BodyVerb.Neck
        assertEquals(-1.0f, neck.pitch, 0.01f)
    }

    @Test
    fun `centered gaze is centered neck`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, pupilGazeUp = 0.5f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val neck = cues.first { it.verb is BodyVerb.Neck }.verb as BodyVerb.Neck
        assertEquals(0.0f, neck.pitch, 0.01f)
    }

    @Test
    fun `head tilt becomes a turn pulse carrying its sign`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, headTiltDeg = -20f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        val turn = cues.first { it.verb is BodyVerb.Turn }.verb as BodyVerb.Turn
        assertEquals(-1, turn.sign)
        assertTrue("speed must be positive", turn.speedPercent > 0)
        assertTrue("ticks must be positive", turn.ticks > 0)
    }

    @Test
    fun `small head tilt is below the deadzone and emits no turn`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, headTiltDeg = 2f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertFalse(cues.any { it.verb is BodyVerb.Turn })
    }

    @Test
    fun `body bounce is a neck bob on the desk`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb is BodyVerb.Neck })
        assertFalse(cues.any { it.verb is BodyVerb.Drive })
    }

    @Test
    fun `body bounce is a drive pulse on the floor`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.FLOOR,
        )
        val drive = cues.first { it.verb is BodyVerb.Drive }.verb as BodyVerb.Drive
        assertTrue("drive is forward only", drive.speed > 0f && drive.speed <= 1f)
    }

    @Test
    fun `downward bounce never drives, because there is no reverse`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, bodyBounce = -0.9f), AnimFrame(atMs = 500)),
            BodyProfile.FLOOR,
        )
        assertFalse(cues.any { it.verb is BodyVerb.Drive })
    }

    @Test
    fun `tail wag becomes a turn pulse`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, tailWag = 0.9f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb is BodyVerb.Turn })
    }

    @Test
    fun `bright glow turns the headlight on`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, glowColorHex = 0xFFD29922L), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb == BodyVerb.Light(true) })
    }

    @Test
    fun `sparkle turns the headlight on`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, sparkle = 0.8f), AnimFrame(atMs = 500)),
            BodyProfile.DESK,
        )
        assertTrue(cues.any { it.verb == BodyVerb.Light(true) })
    }

    @Test
    fun `every clip ends braked and dark`() {
        val cues = BodyMotionMap.cues(
            spec(AnimFrame(atMs = 0, sparkle = 0.9f), AnimFrame(atMs = 800)),
            BodyProfile.FLOOR,
        )
        assertEquals(BodyVerb.Brake, cues.last().verb)
        assertEquals(800, cues.last().atMs)
        assertTrue(cues.any { it.verb == BodyVerb.Light(false) })
    }

    @Test
    fun `cues are sorted by time`() {
        val cues = BodyMotionMap.cues(
            spec(
                AnimFrame(atMs = 0, pupilGazeUp = 0.2f),
                AnimFrame(atMs = 300, bodyBounce = 0.7f),
                AnimFrame(atMs = 900, tailWag = -0.8f),
                AnimFrame(atMs = 1200),
            ),
            BodyProfile.FLOOR,
        )
        assertEquals(cues.map { it.atMs }.sorted(), cues.map { it.atMs })
    }

    @Test
    fun `ear channels are deliberately unmapped`() {
        val cues = BodyMotionMap.cues(
            spec(
                AnimFrame(atMs = 0, earPerk = 1.6f, earDroopDeg = 35f),
                AnimFrame(atMs = 500),
            ),
            BodyProfile.FLOOR,
        )
        // Only the unconditional end-of-clip Brake survives.
        assertEquals(listOf<BodyVerb>(BodyVerb.Brake), cues.map { it.verb })
    }

    // ── The safety property ───────────────────────────────────────────────

    @Test
    fun `desk profile never drives, across every built-in clip`() {
        CustomAnimations.BUILT_INS.forEach { (name, builtIn) ->
            val cues = BodyMotionMap.cues(builtIn, BodyProfile.DESK)
            assertFalse(
                "built-in \"$name\" produced a Drive cue in DESK profile",
                cues.any { it.verb is BodyVerb.Drive },
            )
        }
    }

    @Test
    fun `desk profile never drives, across a fuzz corpus`() {
        val rng = kotlin.random.Random(20260811)
        repeat(2_000) { iteration ->
            val frames = (0..rng.nextInt(1, 16)).map { i ->
                AnimFrame(
                    atMs = i * rng.nextInt(1, 600),
                    eyeOpen = rng.nextFloat() * 1.6f,
                    pupilGazeUp = rng.nextFloat(),
                    headTiltDeg = rng.nextFloat() * 50f - 25f,
                    earPerk = 0.3f + rng.nextFloat() * 1.3f,
                    earDroopDeg = rng.nextFloat() * 40f,
                    bodyBounce = rng.nextFloat() * 2f - 1f,
                    tailWag = rng.nextFloat() * 2f - 1f,
                    sparkle = rng.nextFloat(),
                )
            }
            val cues = BodyMotionMap.cues(
                CustomAnimationSpec("fuzz-$iteration", frames),
                BodyProfile.DESK,
            )
            assertFalse(
                "fuzz iteration $iteration produced a Drive cue in DESK profile",
                cues.any { it.verb is BodyVerb.Drive },
            )
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodyMotionMapTest"`
Expected: FAIL — compilation error, `Unresolved reference: BodyMotionMap`.

- [ ] **Step 3: Write the vocabulary**

Create `app/src/main/java/com/example/meowmba/core/robot/BodyVerb.kt`:

```kotlin
package com.example.meowmba.core.robot

/**
 * How much the base is allowed to move.
 *
 * [DESK] is the fail-safe default and the value used whenever anything is
 * uncertain — an unparsed sensor frame, a dropped link, a fresh start. The
 * only difference that matters is that DESK never translates, so it cannot
 * drive off an edge.
 */
enum class BodyProfile { DESK, FLOOR }

/** One physical action the LOOI base can perform. */
sealed class BodyVerb {

    /** Neck pitch: -1 fully up, 0 center, +1 down. Safety-clamped in the link. */
    data class Neck(val pitch: Float) : BodyVerb()

    /**
     * In-place rotation for [ticks] (~20ms each) at [speedPercent].
     *
     * [sign] (-1 left, +1 right) records the direction the *face* implied. It
     * is deliberately carried but not yet honoured: LooiBleProtocol.rotateFrame
     * exposes no direction parameter, so the base turns whichever way the
     * verified packet turns. See BodyMotionMap's note on turn direction.
     */
    data class Turn(val speedPercent: Int, val ticks: Int, val sign: Int) : BodyVerb()

    /** Forward drive, 0..1. The base has no reverse channel. FLOOR only. */
    data class Drive(val speed: Float) : BodyVerb()

    data class Light(val on: Boolean) : BodyVerb()

    /** Hardware brake plus motor halt. */
    object Brake : BodyVerb()
}

/** A verb scheduled at a moment on the clip's own timeline. */
data class BodyCue(val atMs: Int, val verb: BodyVerb)
```

- [ ] **Step 4: Write the mapper**

Create `app/src/main/java/com/example/meowmba/core/robot/BodyMotionMap.kt`:

```kotlin
package com.example.meowmba.core.robot

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.CustomAnimationSpec
import kotlin.math.abs

/**
 * Turns a face clip into body motion.
 *
 * Pure by construction: [BodyProfile] is an argument, not ambient state, so
 * "DESK never drives" is a property a test can prove exhaustively rather than
 * a promise made in a comment.
 *
 * ## On axes
 *
 * `headTiltDeg` is ROLL — a head cocked sideways ("negative=left"). The neck
 * characteristic fed1 is PITCH — up and down. They are different axes, and
 * mapping one to the other (as FaceMotorMap used to) makes a cocked head nod.
 * The channel that means pitch is `pupilGazeUp`.
 *
 * ## On turn direction
 *
 * LooiBleProtocol.rotateFrame carries no direction parameter, so a pair of
 * opposite turns would rotate the same way twice rather than cancelling.
 * [BodyVerb.Turn.sign] is populated because the information is real and free,
 * but LooiBleBodyOutput ignores it. Rotation is in-place, so this is a heading
 * problem, never a falling problem.
 */
object BodyMotionMap {

    /** Below these, a channel is noise and earns no command. */
    private const val TILT_DEADZONE_DEG = 4f
    private const val BOUNCE_DEADZONE = 0.15f
    private const val WAG_DEADZONE = 0.15f

    private const val TURN_SPEED_PERCENT = 25
    private const val TURN_TICKS = 3
    private const val NECK_BOB_SCALE = 0.5f
    private const val DRIVE_SCALE = 0.5f
    private const val DRIVE_PULSE_MS = 200

    /** A glow this dim reads as mood lighting, not "light up". */
    private const val GLOW_LUMA_THRESHOLD = 0.25f
    private const val SPARKLE_THRESHOLD = 0.5f

    fun cues(spec: CustomAnimationSpec, profile: BodyProfile): List<BodyCue> {
        val out = mutableListOf<BodyCue>()
        var lightOn = false

        spec.frames.forEach { frame ->
            out += neckCues(frame)
            out += tiltCues(frame)
            out += bounceCues(frame, profile)
            out += wagCues(frame)
            if (!lightOn && wantsLight(frame)) {
                lightOn = true
                out += BodyCue(frame.atMs, BodyVerb.Light(true))
            }
        }

        val end = spec.durationMs
        if (lightOn) out += BodyCue(end, BodyVerb.Light(false))
        // Unconditional: a clip always ends with the base stopped.
        out += BodyCue(end, BodyVerb.Brake)

        return out.sortedBy { it.atMs }
    }

    /** gaze 1.0 = looking up = neck pitch -1. gaze 0.5 = center = 0. */
    private fun neckCues(frame: AnimFrame): List<BodyCue> {
        val gaze = frame.pupilGazeUp ?: return emptyList()
        return listOf(BodyCue(frame.atMs, BodyVerb.Neck(1f - 2f * gaze)))
    }

    private fun tiltCues(frame: AnimFrame): List<BodyCue> {
        val tilt = frame.headTiltDeg ?: return emptyList()
        if (abs(tilt) < TILT_DEADZONE_DEG) return emptyList()
        return listOf(
            BodyCue(
                frame.atMs,
                BodyVerb.Turn(TURN_SPEED_PERCENT, TURN_TICKS, if (tilt < 0) -1 else 1),
            )
        )
    }

    private fun bounceCues(frame: AnimFrame, profile: BodyProfile): List<BodyCue> {
        val bounce = frame.bodyBounce ?: return emptyList()
        if (abs(bounce) < BOUNCE_DEADZONE) return emptyList()
        return when (profile) {
            // A bob works in both directions: the neck can go up and down.
            BodyProfile.DESK -> listOf(
                BodyCue(frame.atMs, BodyVerb.Neck(-bounce * NECK_BOB_SCALE)),
                BodyCue(frame.atMs + DRIVE_PULSE_MS, BodyVerb.Neck(0f)),
            )
            // Drive is forward-only, so a downward squish has no floor analogue.
            BodyProfile.FLOOR -> if (bounce <= 0f) emptyList() else listOf(
                BodyCue(frame.atMs, BodyVerb.Drive(bounce * DRIVE_SCALE)),
                BodyCue(frame.atMs + DRIVE_PULSE_MS, BodyVerb.Brake),
            )
        }
    }

    private fun wagCues(frame: AnimFrame): List<BodyCue> {
        val wag = frame.tailWag ?: return emptyList()
        if (abs(wag) < WAG_DEADZONE) return emptyList()
        return listOf(
            BodyCue(
                frame.atMs,
                BodyVerb.Turn(TURN_SPEED_PERCENT, TURN_TICKS, if (wag < 0) -1 else 1),
            )
        )
    }

    private fun wantsLight(frame: AnimFrame): Boolean {
        if ((frame.sparkle ?: 0f) > SPARKLE_THRESHOLD) return true
        val argb = frame.glowColorHex ?: return false
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        // Rec. 601 luma — good enough to tell "lit" from "moody".
        return (0.299f * r + 0.587f * g + 0.114f * b) > GLOW_LUMA_THRESHOLD
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodyMotionMapTest"`
Expected: PASS, 15 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/robot/BodyVerb.kt app/src/main/java/com/example/meowmba/core/robot/BodyMotionMap.kt app/src/test/java/com/example/meowmba/BodyMotionMapTest.kt
git commit -m "feat(robot): body verb vocabulary and pure face-to-body mapper

- BodyVerb/BodyCue/BodyProfile replace phantom servo angles with verbs the
  LOOI base can actually perform: neck, turn, drive, light, brake.
- BodyMotionMap derives body motion from the 14 face channels a clip already
  carries, so every invented animation gains a body with no new vocabulary.
- Corrects an axis error: headTiltDeg is roll, fed1 is pitch. pupilGazeUp is
  the channel that means pitch.
- DESK profile provably never emits Drive: asserted across every built-in clip
  and a 2000-case fuzz corpus."
```

---

### Task 2: Sensor events and the honest parser

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/robot/LooiSensorParser.kt`
- Test: `app/src/test/java/com/example/meowmba/LooiSensorParserTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `SensorEvent.{Touch,Cliff,Tof,Unknown}`; `LooiSensorParser.parse(raw: ByteArray): SensorEvent`; `LooiSensorParser.CaptureLog` with `record(raw: ByteArray, motionInFlight: String?, atMs: Long)`, `entries: List<String>`, `dump(): String`.

**Context for the implementer:** the `fed9` frame layout is **not known**. `LooiBleProtocol` documents it as "notify: touch/cliff; TOF/status partial", and nobody has captured frames from this robot. Do not invent a decode table. The parser's job right now is to classify everything as `Unknown` and to record raw frames well enough that the layout can be decoded later from real data. Writing speculative bit-masks here would produce a parser that looks finished and silently lies.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/LooiSensorParserTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.robot.LooiSensorParser
import com.example.meowmba.core.robot.SensorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fed9 layout has never been captured from this robot, so the parser
 * classifies everything as Unknown on purpose. These tests pin that honesty
 * in place: an Unknown result is a *designed* outcome, and the capture log is
 * the thing that will eventually let the layout be decoded.
 */
class LooiSensorParserTest {

    @Test
    fun `an undecoded frame is Unknown, not an error`() {
        val event = LooiSensorParser.parse(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertTrue("expected Unknown, got $event", event is SensorEvent.Unknown)
    }

    @Test
    fun `an empty frame is Unknown and does not crash`() {
        assertTrue(LooiSensorParser.parse(ByteArray(0)) is SensorEvent.Unknown)
    }

    @Test
    fun `Unknown carries the raw bytes for later decoding`() {
        val raw = byteArrayOf(0x7F, 0x00, 0x2A.toByte())
        val event = LooiSensorParser.parse(raw) as SensorEvent.Unknown
        assertEquals(listOf<Byte>(0x7F, 0x00, 0x2A), event.raw.toList())
    }

    @Test
    fun `capture log records frames with the motion in flight`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x0A, 0x0B), motionInFlight = "Turn", atMs = 1000L)
        assertEquals(1, log.entries.size)
        val line = log.entries.single()
        assertTrue("expected hex bytes in \"$line\"", line.contains("0a0b"))
        assertTrue("expected motion label in \"$line\"", line.contains("Turn"))
        assertTrue("expected timestamp in \"$line\"", line.contains("1000"))
    }

    @Test
    fun `capture log records frames with no motion in flight`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x01), motionInFlight = null, atMs = 5L)
        assertTrue(log.entries.single().contains("idle"))
    }

    @Test
    fun `capture log is bounded and keeps the newest frames`() {
        val log = LooiSensorParser.CaptureLog(capacity = 3)
        repeat(10) { i -> log.record(byteArrayOf(i.toByte()), null, i.toLong()) }
        assertEquals(3, log.entries.size)
        assertTrue("oldest should have been evicted", log.entries.none { it.contains("t=0 ") })
        assertTrue("newest must be present", log.entries.any { it.contains("t=9 ") })
    }

    @Test
    fun `dump joins entries one per line`() {
        val log = LooiSensorParser.CaptureLog(capacity = 4)
        log.record(byteArrayOf(0x01), null, 1L)
        log.record(byteArrayOf(0x02), null, 2L)
        assertEquals(2, log.dump().lines().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.LooiSensorParserTest"`
Expected: FAIL — compilation error, `Unresolved reference: LooiSensorParser`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/meowmba/core/robot/LooiSensorParser.kt`:

```kotlin
package com.example.meowmba.core.robot

/** What a fed9 notification turned out to mean. */
sealed class SensorEvent {
    object Touch : SensorEvent()

    /** The base is at an edge. Absolute and immediate: brake, then think. */
    data class Cliff(val raw: ByteArray) : SensorEvent() {
        override fun equals(other: Any?): Boolean =
            other is Cliff && other.raw.contentEquals(raw)
        override fun hashCode(): Int = raw.contentHashCode()
    }

    data class Tof(val mm: Int) : SensorEvent()

    /** Not decoded. Treated as danger everywhere it is consumed. */
    data class Unknown(val raw: ByteArray) : SensorEvent() {
        override fun equals(other: Any?): Boolean =
            other is Unknown && other.raw.contentEquals(raw)
        override fun hashCode(): Int = raw.contentHashCode()
    }
}

/**
 * Decoder for the fed9 sensor characteristic.
 *
 * **The layout is not known.** LooiBleProtocol records it as "notify:
 * touch/cliff; TOF/status partial", and no frames have been captured from this
 * robot. Every frame therefore returns [SensorEvent.Unknown], and every
 * consumer treats Unknown as danger — ConfinementModel collapses to DESK on
 * it.
 *
 * This is deliberate. A speculative bit-mask here would produce a parser that
 * looks finished and silently misreports cliffs, which is the one failure this
 * subsystem must not have. [CaptureLog] exists to make the real decode
 * possible: run the robot, perform motion, dump the log, read the bytes.
 *
 * When the layout is decoded, only [parse] changes.
 */
object LooiSensorParser {

    fun parse(raw: ByteArray): SensorEvent = SensorEvent.Unknown(raw.copyOf())

    /**
     * A bounded ring of recent frames, each tagged with the motion in flight
     * when it arrived — the correlation that makes the layout decodable.
     */
    class CaptureLog(private val capacity: Int = 200) {
        private val ring = ArrayDeque<String>()

        val entries: List<String> get() = ring.toList()

        fun record(raw: ByteArray, motionInFlight: String?, atMs: Long) {
            val hex = raw.joinToString("") { "%02x".format(it) }
            ring.addLast("t=$atMs ${motionInFlight ?: "idle"} $hex")
            while (ring.size > capacity) ring.removeFirst()
        }

        fun dump(): String = ring.joinToString("\n")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.LooiSensorParserTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/robot/LooiSensorParser.kt app/src/test/java/com/example/meowmba/LooiSensorParserTest.kt
git commit -m "feat(robot): fed9 sensor event types and capture log

- SensorEvent models Touch/Cliff/Tof/Unknown; Unknown is a first-class
  designed outcome, not an error path.
- The fed9 layout has never been captured from this robot, so parse() returns
  Unknown for every frame rather than guessing a bit-mask that would silently
  misreport cliffs.
- CaptureLog rings recent frames tagged with the motion in flight, which is
  the correlation needed to decode the layout from real hardware."
```

---

### Task 3: The confinement model

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/robot/ConfinementModel.kt`
- Test: `app/src/test/java/com/example/meowmba/ConfinementModelTest.kt`

**Interfaces:**
- Consumes: `BodyProfile` (Task 1), `SensorEvent` (Task 2).
- Produces: `ConfinementModel(cleanWindowsForFloor: Int = 4, minWindowMs: Long = 1_500L, evidenceDecayMs: Long = 10 * 60_000L)` with `profile: BodyProfile`, `cleanWindows: Int`, `onMotionWindowStart(atMs: Long)`, `onMotionWindowEnd(atMs: Long): BodyProfile`, `onSensor(event: SensorEvent, atMs: Long): BodyProfile`, `onPickedUp(atMs: Long)`, `onLinkLost(atMs: Long)`.

**Context for the implementer:** heading is *not* available — see the Global Constraints. Evidence is counted in motion windows, not directions. A motion window is a contiguous stretch of commanded rotation or drive; a window that completes without a `Cliff` and lasted at least `minWindowMs` is one unit of clean evidence. This is deliberately weaker than per-direction evidence, which is why FLOOR needs several separate windows and why `Cliff` is absolute.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/ConfinementModelTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.ConfinementModel
import com.example.meowmba.core.robot.SensorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The envelope widens only on evidence the robot gathered from motion it was
 * going to make anyway, and collapses on anything surprising. Every rule here
 * is a safety rule, so they are all pinned.
 */
class ConfinementModelTest {

    /**
     * Runs [count] clean windows of [durationMs] each, starting at [from].
     * Named `runCleanWindows` rather than `cleanWindows` so it cannot be
     * confused with the model's property of that name.
     */
    private fun ConfinementModel.runCleanWindows(
        count: Int,
        durationMs: Long = 2_000L,
        from: Long = 0L,
        gapMs: Long = 1_000L,
    ): BodyProfile {
        var t = from
        var result = profile
        repeat(count) {
            onMotionWindowStart(t)
            t += durationMs
            result = onMotionWindowEnd(t)
            t += gapMs
        }
        return result
    }

    @Test
    fun `starts on the desk`() {
        assertEquals(BodyProfile.DESK, ConfinementModel().profile)
    }

    @Test
    fun `four clean windows unlock the floor`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        assertEquals(BodyProfile.FLOOR, m.runCleanWindows(4))
    }

    @Test
    fun `three clean windows are not enough`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        assertEquals(BodyProfile.DESK, m.runCleanWindows(3))
    }

    @Test
    fun `a cliff collapses to desk immediately and zeroes evidence`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.profile)

        assertEquals(BodyProfile.DESK, m.onSensor(SensorEvent.Cliff(byteArrayOf(1)), 20_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `an unknown frame collapses to desk, because undecoded means dangerous`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.DESK, m.onSensor(SensorEvent.Unknown(byteArrayOf(9)), 20_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `touch is harmless and changes nothing`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.onSensor(SensorEvent.Touch, 20_000L))
        assertEquals(4, m.cleanWindows)
    }

    @Test
    fun `a cliff during a window poisons that window`() {
        val m = ConfinementModel(cleanWindowsForFloor = 2)
        m.onMotionWindowStart(0L)
        m.onSensor(SensorEvent.Cliff(byteArrayOf(1)), 500L)
        m.onMotionWindowEnd(2_000L)
        assertEquals(0, m.cleanWindows)
        assertEquals(BodyProfile.DESK, m.profile)
    }

    @Test
    fun `a window too short to be evidence does not count`() {
        val m = ConfinementModel(cleanWindowsForFloor = 1, minWindowMs = 1_500L)
        m.onMotionWindowStart(0L)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(400L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `being picked up resets everything`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        m.onPickedUp(30_000L)
        assertEquals(BodyProfile.DESK, m.profile)
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `losing the link resets everything`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        m.onLinkLost(30_000L)
        assertEquals(BodyProfile.DESK, m.profile)
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `stale evidence decays and the floor re-locks`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4, evidenceDecayMs = 60_000L)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.profile)

        // A window starting long after the last evidence finds none of it left.
        m.onMotionWindowStart(10 * 60_000L)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(10 * 60_000L + 2_000L))
        assertEquals(1, m.cleanWindows)
    }

    @Test
    fun `evidence does not double count an unclosed window`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.onMotionWindowStart(0L)
        m.onMotionWindowStart(100L) // spurious restart
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(3_000L))
        assertEquals(1, m.cleanWindows)
    }

    @Test
    fun `ending a window that never started is ignored`() {
        val m = ConfinementModel(cleanWindowsForFloor = 1)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(5_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `a cliff outside any window still collapses the profile`() {
        val m = ConfinementModel(cleanWindowsForFloor = 2)
        m.runCleanWindows(2)
        assertEquals(BodyProfile.FLOOR, m.profile)
        assertTrue(m.onSensor(SensorEvent.Cliff(byteArrayOf(0)), 99_000L) == BodyProfile.DESK)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.ConfinementModelTest"`
Expected: FAIL — compilation error, `Unresolved reference: ConfinementModel`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/meowmba/core/robot/ConfinementModel.kt`:

```kotlin
package com.example.meowmba.core.robot

/**
 * Decides whether the base is confined (a desk) or free (a floor), from
 * evidence produced by motion the companion was going to perform anyway.
 *
 * It never commands motion. That is the whole point: the robot does not creep
 * around a desk to measure it, because a probe is exactly the risky moment.
 *
 * ## Why windows and not directions
 *
 * LooiBleProtocol.rotateFrame has no direction parameter, so turn direction is
 * neither commandable nor observable and signed dead reckoning is impossible.
 * Evidence is therefore counted in *motion windows* — contiguous stretches of
 * commanded motion — rather than per-direction sectors.
 *
 * This is strictly weaker: it can conclude "not confined" while a cliff sits
 * in an untested direction. That is why [cleanWindowsForFloor] requires
 * several separate windows, why a window must last [minWindowMs] to count, and
 * why any cliff is absolute and immediate. Sector logic becomes possible once
 * turn direction is decoded.
 */
class ConfinementModel(
    private val cleanWindowsForFloor: Int = 4,
    private val minWindowMs: Long = 1_500L,
    private val evidenceDecayMs: Long = 10 * 60_000L,
) {

    var profile: BodyProfile = BodyProfile.DESK
        private set

    /** Clean motion windows currently counting toward FLOOR. */
    var cleanWindows: Int = 0
        private set

    private var windowStartedAt: Long? = null
    private var windowPoisoned = false
    private var lastEvidenceAt: Long = 0L

    /** A stretch of commanded motion began. A second call without an end is ignored. */
    fun onMotionWindowStart(atMs: Long) {
        if (windowStartedAt != null) return
        windowStartedAt = atMs
        windowPoisoned = false
    }

    /** The stretch ended. Long enough and uneventful enough, it is evidence. */
    fun onMotionWindowEnd(atMs: Long): BodyProfile {
        val started = windowStartedAt ?: return profile
        windowStartedAt = null

        decayIfStale(atMs)

        val longEnough = atMs - started >= minWindowMs
        if (longEnough && !windowPoisoned) {
            cleanWindows += 1
            lastEvidenceAt = atMs
            if (cleanWindows >= cleanWindowsForFloor) profile = BodyProfile.FLOOR
        }
        windowPoisoned = false
        return profile
    }

    fun onSensor(event: SensorEvent, atMs: Long): BodyProfile {
        when (event) {
            // An edge, or a frame nobody can read. Both mean stop trusting the room.
            is SensorEvent.Cliff, is SensorEvent.Unknown -> collapse()
            // Being touched says nothing about the surface.
            is SensorEvent.Touch -> Unit
            // TOF is captured for later use; it is not evidence on its own yet.
            is SensorEvent.Tof -> Unit
        }
        return profile
    }

    /** The IMU says the base was lifted: whatever it learned is about a different place. */
    fun onPickedUp(atMs: Long) = collapse()

    fun onLinkLost(atMs: Long) = collapse()

    private fun collapse() {
        profile = BodyProfile.DESK
        cleanWindows = 0
        lastEvidenceAt = 0L
        windowPoisoned = true
    }

    /** Evidence is about a room the robot may have left. Let it expire. */
    private fun decayIfStale(atMs: Long) {
        if (cleanWindows > 0 && atMs - lastEvidenceAt > evidenceDecayMs) {
            cleanWindows = 0
            profile = BodyProfile.DESK
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.ConfinementModelTest"`
Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/robot/ConfinementModel.kt app/src/test/java/com/example/meowmba/ConfinementModelTest.kt
git commit -m "feat(robot): opportunistic confinement model

- Learns desk-vs-floor from motion the companion was going to make anyway;
  never probes, because probing on a desk is the risky moment.
- Counts evidence in motion windows rather than direction sectors: rotateFrame
  exposes no direction parameter, so signed dead reckoning is impossible.
- Cliff and Unknown both collapse to DESK immediately. Undecoded means
  dangerous.
- Pick-up, link loss and ten-minute staleness all reset the evidence."
```

---

### Task 4: Body output and the missing sensor stream

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/robot/BodyOutput.kt`
- Modify: `app/src/main/java/com/example/meowmba/core/robot/LooiBleLink.kt`
- Test: `app/src/test/java/com/example/meowmba/BodyOutputTest.kt`

**Interfaces:**
- Consumes: `BodyVerb` (Task 1), `SensorEvent`/`LooiSensorParser` (Task 2).
- Produces: `BodyOutput` interface with `neck(pitch: Float)`, `turn(speedPercent: Int, ticks: Int, sign: Int)`, `drive(speed: Float)`, `light(on: Boolean)`, `brake()`; `SimulatedBodyOutput(log: (String) -> Unit = ::println)` exposing `history: List<String>`; `LooiBleBodyOutput(link: LooiBleLink)`; `BodyOutput.perform(verb: BodyVerb)` extension. Also `LooiBleLink.sensorFrames: SharedFlow<ByteArray>`.

**Context for the implementer:** `LooiBleLink` already captures the fed9 characteristic (`LooiBleLink.kt:230`), CCCD-subscribes it in the generic init loop, and reads it every 3s in the keep-alive — but the class has **no `onCharacteristicChanged` override**, so every notification is discarded. Adding that override is what makes sensor data exist at all.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/BodyOutputTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.robot.BodyOutput
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.SimulatedBodyOutput
import com.example.meowmba.core.robot.perform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BLE body cannot be held in a unit test, so what is pinned here is the
 * honest stand-in and the verb dispatch every implementation shares.
 */
class BodyOutputTest {

    @Test
    fun `simulated output records every verb`() {
        val out = SimulatedBodyOutput(log = {})
        out.perform(BodyVerb.Neck(-0.5f))
        out.perform(BodyVerb.Turn(25, 3, -1))
        out.perform(BodyVerb.Drive(0.4f))
        out.perform(BodyVerb.Light(true))
        out.perform(BodyVerb.Brake)

        assertEquals(5, out.history.size)
        assertTrue(out.history[0].contains("neck"))
        assertTrue(out.history[1].contains("turn"))
        assertTrue(out.history[2].contains("drive"))
        assertTrue(out.history[3].contains("light"))
        assertTrue(out.history[4].contains("brake"))
    }

    @Test
    fun `simulated output clamps neck pitch to the legal range`() {
        val out = SimulatedBodyOutput(log = {})
        out.neck(5f)
        out.neck(-5f)
        assertTrue(out.history[0].contains("1.0"))
        assertTrue(out.history[1].contains("-1.0"))
    }

    @Test
    fun `simulated output clamps drive to forward only`() {
        val out = SimulatedBodyOutput(log = {})
        out.drive(-0.8f)
        assertTrue("reverse must clamp to zero", out.history.single().contains("0.0"))
    }

    @Test
    fun `history is bounded so a long session cannot grow without limit`() {
        val out = SimulatedBodyOutput(log = {})
        repeat(500) { out.brake() }
        assertTrue("history should be capped", out.history.size <= 200)
    }

    @Test
    fun `a failing output never throws through perform`() {
        val exploding = object : BodyOutput {
            override fun neck(pitch: Float) = error("link down")
            override fun turn(speedPercent: Int, ticks: Int, sign: Int) = error("link down")
            override fun drive(speed: Float) = error("link down")
            override fun light(on: Boolean) = error("link down")
            override fun brake() = error("link down")
        }
        // perform swallows: the face must never die because the body did.
        exploding.perform(BodyVerb.Neck(0f))
        exploding.perform(BodyVerb.Brake)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodyOutputTest"`
Expected: FAIL — compilation error, `Unresolved reference: BodyOutput`.

- [ ] **Step 3: Write BodyOutput**

Create `app/src/main/java/com/example/meowmba/core/robot/BodyOutput.kt`:

```kotlin
package com.example.meowmba.core.robot

/**
 * Everything the LOOI base can be told to do, in the base's own vocabulary.
 *
 * This deliberately does not reuse [MotorController], whose `setServo(id,
 * angle)` describes an articulated puppet the hardware is not. That interface
 * still serves the TCP/serial transport and is left alone.
 */
interface BodyOutput {
    /** -1 fully up, 0 center, +1 down. */
    fun neck(pitch: Float)

    /** In-place rotation. [sign] is recorded but not yet honourable — see BodyVerb.Turn. */
    fun turn(speedPercent: Int, ticks: Int, sign: Int)

    /** Forward only, 0..1. There is no reverse channel. */
    fun drive(speed: Float)

    fun light(on: Boolean)

    fun brake()
}

/**
 * Dispatch a verb, swallowing any failure.
 *
 * The body must never block the face: a dropped BLE link mid-clip should leave
 * the cat animating happily on screen, not crash the animation coroutine.
 */
fun BodyOutput.perform(verb: BodyVerb) {
    runCatching {
        when (verb) {
            is BodyVerb.Neck -> neck(verb.pitch)
            is BodyVerb.Turn -> turn(verb.speedPercent, verb.ticks, verb.sign)
            is BodyVerb.Drive -> drive(verb.speed)
            is BodyVerb.Light -> light(verb.on)
            is BodyVerb.Brake -> brake()
        }
    }
}

/** Logs instead of moving hardware — the honest stand-in, as SimulatedMotorController is. */
class SimulatedBodyOutput(
    private val log: (String) -> Unit = { println(it) },
) : BodyOutput {

    private val entries = mutableListOf<String>()
    val history: List<String> get() = entries

    private fun record(entry: String) {
        entries.add(entry)
        if (entries.size > 200) entries.removeAt(0)
        log("body: $entry")
    }

    override fun neck(pitch: Float) = record("neck -> ${pitch.coerceIn(-1f, 1f)}")

    override fun turn(speedPercent: Int, ticks: Int, sign: Int) =
        record("turn -> ${speedPercent.coerceIn(0, 100)}% x${ticks}t sign=$sign")

    override fun drive(speed: Float) = record("drive -> ${speed.coerceIn(0f, 1f)}")

    override fun light(on: Boolean) = record("light -> $on")

    override fun brake() = record("brake")
}

/**
 * Drives a real LOOI base over BLE.
 *
 * [turn]'s `sign` is ignored: LooiBleProtocol.rotateFrame carries no direction
 * parameter, so the base rotates whichever way the verified packet rotates.
 * The argument is kept so the mapper can record intent, and so honouring it is
 * a one-line change here once direction is decoded.
 */
class LooiBleBodyOutput(private val link: LooiBleLink) : BodyOutput {

    override fun neck(pitch: Float) {
        if (link.connected) link.setNeckPitch(pitch)
    }

    override fun turn(speedPercent: Int, ticks: Int, sign: Int) {
        if (link.connected) link.turnPulse(speedPercent, ticks)
    }

    override fun drive(speed: Float) {
        if (link.connected) link.driveSpeed(speed)
    }

    override fun light(on: Boolean) {
        if (link.connected) link.setHeadlight(on)
    }

    override fun brake() {
        if (link.connected) link.stopAll()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodyOutputTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Add the missing sensor notification handler**

In `app/src/main/java/com/example/meowmba/core/robot/LooiBleLink.kt`, add these imports alongside the existing kotlinx imports:

```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
```

Add the backing flow next to the other private fields (near `private val seq = LooiBleProtocol.SequenceCounter()`):

```kotlin
    /**
     * Raw fed9 notifications.
     *
     * extraBufferCapacity keeps emission non-suspending from the GATT
     * callback thread, which must never block; DROP_OLDEST means a slow
     * collector loses stale frames rather than stalling the BLE stack.
     */
    private val _sensorFrames = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** Every fed9 frame the robot sends. Undecoded — see LooiSensorParser. */
    val sensorFrames: SharedFlow<ByteArray> = _sensorFrames.asSharedFlow()
```

Inside the `gattCallback` object, add the override the class has never had — put it directly after `onCharacteristicRead`:

```kotlin
        /**
         * Sensor notifications. Before this existed the app CCCD-subscribed
         * fed9 and then discarded every frame it produced, so touch and cliff
         * data never reached any consumer.
         */
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid != LooiBleProtocol.CHAR_SENSORS) return
            val bytes = ch.value ?: return
            _sensorFrames.tryEmit(bytes.copyOf())
        }
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/robot/BodyOutput.kt app/src/main/java/com/example/meowmba/core/robot/LooiBleLink.kt app/src/test/java/com/example/meowmba/BodyOutputTest.kt
git commit -m "feat(robot): BodyOutput vocabulary and the missing fed9 notify handler

- BodyOutput speaks the base's own verbs (neck/turn/drive/light/brake) instead
  of MotorController's puppet servo angles; the servo interface is untouched
  and still serves the TCP/serial transport.
- perform() swallows failures so a dropped link never kills the face.
- LooiBleLink had no onCharacteristicChanged override: it subscribed fed9 and
  discarded every frame. Sensor data now reaches consumers via sensorFrames."
```

---

### Task 5: The body scheduler

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/robot/BodyScheduler.kt`
- Test: `app/src/test/java/com/example/meowmba/BodySchedulerTest.kt`

**Interfaces:**
- Consumes: `BodyCue`, `BodyVerb` (Task 1); `BodyOutput`, `perform` (Task 4).
- Produces: `BodyScheduler(scope: CoroutineScope, output: BodyOutput, minWriteIntervalMs: Long = 60L, onMotionWindowStart: (Long) -> Unit = {}, onMotionWindowEnd: (Long) -> Unit = {})` with `play(cues: List<BodyCue>)` and `stop()`.

**Context for the implementer:** tests drive this on a virtual clock. `TestScope` from `kotlinx-coroutines-test` makes `delay` instant and `advanceTimeBy` deterministic — see `IdleBehaviorTest.kt` for the established pattern in this repo. The scheduler must serialize motion verbs: `Turn` and `Drive` carry their own duration in ticks, so overlapping them stacks unpredictable motion, while `Neck` and `Light` are state writes that may interleave freely.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/BodySchedulerTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.robot.BodyCue
import com.example.meowmba.core.robot.BodyOutput
import com.example.meowmba.core.robot.BodyScheduler
import com.example.meowmba.core.robot.BodyVerb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodySchedulerTest {

    private class Recorder : BodyOutput {
        val calls = mutableListOf<String>()
        var failEverything = false
        private fun record(s: String) {
            if (failEverything) error("link down")
            calls.add(s)
        }
        override fun neck(pitch: Float) = record("neck:$pitch")
        override fun turn(speedPercent: Int, ticks: Int, sign: Int) = record("turn:$sign")
        override fun drive(speed: Float) = record("drive:$speed")
        override fun light(on: Boolean) = record("light:$on")
        override fun brake() = record("brake")
    }

    @Test
    fun `cues fire at their scheduled times`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(-0.5f)),
                BodyCue(500, BodyVerb.Light(true)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )

        runCurrent()
        assertEquals(listOf("neck:-0.5"), out.calls)

        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf("neck:-0.5", "light:true"), out.calls)

        advanceUntilIdle()
        assertEquals(listOf("neck:-0.5", "light:true", "brake"), out.calls)

        testScheduler.advanceUntilIdle()
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun `writes closer than the minimum interval are coalesced`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out, minWriteIntervalMs = 60L)

        // Three neck writes 10ms apart: only the first and the settled last survive.
        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(0.1f)),
                BodyCue(10, BodyVerb.Neck(0.2f)),
                BodyCue(20, BodyVerb.Neck(0.3f)),
                BodyCue(500, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        val neckWrites = out.calls.count { it.startsWith("neck") }
        assertTrue("expected coalescing, saw $neckWrites neck writes", neckWrites < 3)
        assertTrue("the clip must still end braked", out.calls.last() == "brake")
    }

    @Test
    fun `two motion verbs never overlap`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        // Drive and Turn scheduled at the same instant: one must be dropped.
        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Drive(0.5f)),
                BodyCue(0, BodyVerb.Turn(25, 3, 1)),
                BodyCue(2000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        val motion = out.calls.filter { it.startsWith("drive") || it.startsWith("turn") }
        assertEquals("only one motion verb may be in flight", 1, motion.size)
    }

    @Test
    fun `state writes may interleave with motion`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Turn(25, 3, 1)),
                BodyCue(0, BodyVerb.Light(true)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertTrue("light is a state write and must survive", out.calls.contains("light:true"))
        assertTrue(out.calls.contains("turn:1"))
    }

    @Test
    fun `stop brakes immediately and cancels the rest`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Neck(0.2f)),
                BodyCue(5000, BodyVerb.Light(true)),
                BodyCue(9000, BodyVerb.Brake),
            )
        )
        runCurrent()

        scheduler.stop()
        runCurrent()
        assertEquals("brake", out.calls.last())

        advanceUntilIdle()
        assertTrue("cancelled cues must not fire", !out.calls.contains("light:true"))
    }

    @Test
    fun `a new clip supersedes the previous one`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0.9f)), BodyCue(8000, BodyVerb.Light(true))))
        runCurrent()
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(-0.9f)), BodyCue(100, BodyVerb.Brake)))
        advanceUntilIdle()

        assertTrue("the superseded clip's late cue must not fire", !out.calls.contains("light:true"))
        assertTrue(out.calls.contains("neck:-0.9"))
    }

    @Test
    fun `a failing output never breaks the schedule`() = runTest {
        val out = Recorder()
        out.failEverything = true
        val scheduler = BodyScheduler(TestScope(testScheduler), out)

        // Must complete without throwing — the face depends on this.
        scheduler.play(listOf(BodyCue(0, BodyVerb.Neck(0f)), BodyCue(200, BodyVerb.Brake)))
        advanceUntilIdle()
    }

    @Test
    fun `motion windows are reported for the confinement model`() = runTest {
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Long>()
        val scheduler = BodyScheduler(
            TestScope(testScheduler),
            Recorder(),
            onMotionWindowStart = { starts.add(it) },
            onMotionWindowEnd = { ends.add(it) },
        )

        scheduler.play(
            listOf(
                BodyCue(0, BodyVerb.Drive(0.4f)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertEquals(1, starts.size)
        assertEquals(1, ends.size)
    }

    @Test
    fun `brake is never swallowed by the write budget`() = runTest {
        val out = Recorder()
        val scheduler = BodyScheduler(TestScope(testScheduler), out, minWriteIntervalMs = 60L)

        // BodyMotionMap emits Light(false) and Brake at the same instant. If
        // the budget applied to Brake, the base would keep moving after the
        // clip ended — the one failure this subsystem must not have.
        scheduler.play(
            listOf(
                BodyCue(1000, BodyVerb.Light(false)),
                BodyCue(1000, BodyVerb.Brake),
            )
        )
        advanceUntilIdle()

        assertTrue("brake must survive a same-instant neighbour", out.calls.contains("brake"))
    }

    @Test
    fun `an empty cue list is harmless`() = runTest {
        val out = Recorder()
        BodyScheduler(TestScope(testScheduler), out).play(emptyList())
        advanceUntilIdle()
        assertTrue(out.calls.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodySchedulerTest"`
Expected: FAIL — compilation error, `Unresolved reference: BodyScheduler`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/meowmba/core/robot/BodyScheduler.kt`:

```kotlin
package com.example.meowmba.core.robot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a cue list on the clip's own timeline.
 *
 * The face renders the same [com.example.meowmba.ui.companion.CustomAnimationSpec]
 * from the same instant, so as long as both start together they cannot drift.
 *
 * Owns three concerns the mapper deliberately does not:
 *
 *  1. **Write budget** — BLE is not a frame buffer. Writes closer together
 *     than [minWriteIntervalMs] are coalesced, keeping the wire quiet.
 *  2. **One motion verb in flight** — [BodyVerb.Turn] and [BodyVerb.Drive]
 *     carry their own duration in ticks, so overlapping them stacks motion the
 *     robot will execute unpredictably. [BodyVerb.Neck] and [BodyVerb.Light]
 *     are state writes and interleave freely.
 *  3. **Cancellation** — a superseded or cancelled clip brakes at once.
 */
class BodyScheduler(
    private val scope: CoroutineScope,
    private val output: BodyOutput,
    private val minWriteIntervalMs: Long = 60L,
    private val onMotionWindowStart: (Long) -> Unit = {},
    private val onMotionWindowEnd: (Long) -> Unit = {},
) {

    private var job: Job? = null

    /** Start a clip. Any clip already playing is superseded and braked. */
    fun play(cues: List<BodyCue>) {
        job?.cancel()
        if (cues.isEmpty()) return

        job = scope.launch {
            var elapsed = 0L
            var lastWriteAt = Long.MIN_VALUE
            var motionBusyUntil = Long.MIN_VALUE
            var windowOpen = false

            cues.sortedBy { it.atMs }.forEach { cue ->
                if (!isActive) return@launch
                val wait = cue.atMs - elapsed
                if (wait > 0) {
                    delay(wait)
                    elapsed = cue.atMs.toLong()
                }

                val isMotion = cue.verb is BodyVerb.Turn || cue.verb is BodyVerb.Drive

                // A motion verb still executing on the robot wins; drop the new one.
                if (isMotion && elapsed < motionBusyUntil) return@forEach

                // Brake is never budgeted or dropped. BodyMotionMap emits
                // Light(false) and Brake at the same instant, so a budget check
                // here would swallow the stop and leave the base moving.
                val isBrake = cue.verb is BodyVerb.Brake

                // Other state writes are cheap but not free — respect the budget.
                if (!isMotion && !isBrake && elapsed - lastWriteAt < minWriteIntervalMs) return@forEach

                if (isMotion && !windowOpen) {
                    windowOpen = true
                    onMotionWindowStart(elapsed)
                }
                if (cue.verb is BodyVerb.Brake && windowOpen) {
                    windowOpen = false
                    onMotionWindowEnd(elapsed)
                }

                output.perform(cue.verb)
                lastWriteAt = elapsed
                if (isMotion) motionBusyUntil = elapsed + motionDurationMs(cue.verb)
            }

            if (windowOpen) onMotionWindowEnd(elapsed)
        }
    }

    /** Stop now: brake first, then drop everything still scheduled. */
    fun stop() {
        job?.cancel()
        job = null
        output.perform(BodyVerb.Brake)
    }

    /** How long the robot will be busy executing this verb. Ticks are ~20ms. */
    private fun motionDurationMs(verb: BodyVerb): Long = when (verb) {
        is BodyVerb.Turn -> verb.ticks * TICK_MS
        is BodyVerb.Drive -> DRIVE_BUSY_MS
        else -> 0L
    }

    private companion object {
        const val TICK_MS = 20L
        /** fed0 drive is a level, not a pulse; treat it as busy until braked. */
        const val DRIVE_BUSY_MS = 200L
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.BodySchedulerTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/robot/BodyScheduler.kt app/src/test/java/com/example/meowmba/BodySchedulerTest.kt
git commit -m "feat(robot): body scheduler plays cues on the clip timeline

- Walks a BodyCue list on the same clock the face uses, so face and body
  cannot drift.
- Coalesces writes to a 60ms budget, keeps one motion verb in flight at a
  time, and brakes immediately on cancel or supersede.
- Reports motion windows so ConfinementModel can learn from motion that was
  going to happen anyway.
- A failing BodyOutput never breaks the schedule: the face plays through."
```

---

### Task 6: Wire the body into the companion

**Files:**
- Modify: `app/src/main/java/com/example/meowmba/core/robot/FaceMotorMap.kt`
- Modify: `app/src/main/java/com/example/meowmba/core/robot/RobotManager.kt`
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`
- Modify: `app/src/test/java/com/example/meowmba/PhaseGTest.kt`
- Test: `app/src/test/java/com/example/meowmba/FaceMotorMapMoodTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–5.
- Produces: `FaceMotorMap.mapMood(mood: Mood): Pair<List<BodyVerb>, String>`; `RobotManager.bodyOutput: BodyOutput`; `RobotManager.sensorFrames: SharedFlow<ByteArray>?`.

**Context for the implementer:** `FaceMotorMap.mapAnimation()` is already dead in production — its only caller is `PhaseGTest.kt:85`. Deleting it removes an assertion that four hardcoded clip names produce servos, coverage that never touched real hardware. `FaceMotorMap.balanceTilt()` and `panForFace()` stay exactly as they are; they serve the IMU and face-tracking paths at `CompanionViewModel.kt:439`, `:921` and `:3786` and are outside this design.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/meowmba/FaceMotorMapMoodTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.personality.Mood
import com.example.meowmba.core.robot.BodyVerb
import com.example.meowmba.core.robot.FaceMotorMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mood now produces a resting pose in the base's own verbs rather than servo
 * angles for ears and a tail the hardware does not have.
 */
class FaceMotorMapMoodTest {

    @Test
    fun `every mood produces a resting pose and an expression`() {
        Mood.values().forEach { mood ->
            val (verbs, expression) = FaceMotorMap.mapMood(mood)
            assertTrue("$mood produced no verbs", verbs.isNotEmpty())
            assertTrue("$mood produced a blank expression", expression.isNotBlank())
        }
    }

    @Test
    fun `a resting pose never drives, in any mood`() {
        Mood.values().forEach { mood ->
            val (verbs, _) = FaceMotorMap.mapMood(mood)
            assertFalse(
                "$mood produced a Drive in a resting pose",
                verbs.any { it is BodyVerb.Drive },
            )
        }
    }

    @Test
    fun `sleepy droops the neck and dims the light`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.SLEEPY)
        assertEquals("sleepy", expression)
        val neck = verbs.filterIsInstance<BodyVerb.Neck>().single()
        assertTrue("sleepy should look down", neck.pitch > 0f)
        assertTrue(verbs.contains(BodyVerb.Light(false)))
    }

    @Test
    fun `excited lifts the neck and lights up`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.EXCITED)
        assertEquals("happy", expression)
        val neck = verbs.filterIsInstance<BodyVerb.Neck>().single()
        assertTrue("excited should look up", neck.pitch < 0f)
        assertTrue(verbs.contains(BodyVerb.Light(true)))
    }

    @Test
    fun `content rests at center`() {
        val (verbs, expression) = FaceMotorMap.mapMood(Mood.CONTENT)
        assertEquals("content", expression)
        assertEquals(0f, verbs.filterIsInstance<BodyVerb.Neck>().single().pitch, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.FaceMotorMapMoodTest"`
Expected: FAIL — `mapMood` still returns `Pair<Map<Int, Float>, String>`, so the destructuring and `filterIsInstance<BodyVerb.Neck>()` do not compile.

- [ ] **Step 3: Rewrite FaceMotorMap**

Replace the whole of `app/src/main/java/com/example/meowmba/core/robot/FaceMotorMap.kt` with:

```kotlin
package com.example.meowmba.core.robot

import com.example.meowmba.core.personality.Mood

/**
 * Maps mood, IMU tilt and face tracking to a physical body.
 *
 * Mood now speaks [BodyVerb] — the LOOI base's own vocabulary. It used to emit
 * servo angles for ears, a tail and a pan axis the hardware does not have, of
 * which four channels were silently dropped in translation.
 *
 * [balanceTilt] and [panForFace] still speak servo angles: they drive the
 * legacy servo path and are unrelated to the clip-driven body.
 */
object FaceMotorMap {
    const val SERVO_HEAD_TILT = 0
    const val SERVO_HEAD_PAN = 1
    const val SERVO_EAR_LEFT = 2
    const val SERVO_EAR_RIGHT = 3
    const val SERVO_TAIL = 4
    const val SERVO_BODY = 5

    private const val CENTER = 90f

    /** Mood → resting body pose + expression name. Never a drive. */
    fun mapMood(mood: Mood): Pair<List<BodyVerb>, String> = when (mood) {
        Mood.EXCITED -> listOf(BodyVerb.Neck(-0.4f), BodyVerb.Light(true)) to "happy"
        Mood.SLEEPY -> listOf(BodyVerb.Neck(0.6f), BodyVerb.Light(false)) to "sleepy"
        Mood.STARTLED -> listOf(BodyVerb.Neck(-0.7f), BodyVerb.Light(true)) to "alert"
        Mood.PROUD -> listOf(BodyVerb.Neck(-0.5f), BodyVerb.Light(true)) to "proud"
        Mood.CURIOUS -> listOf(BodyVerb.Neck(-0.2f), BodyVerb.Light(true)) to "curious"
        Mood.CONTENT -> listOf(BodyVerb.Neck(0f), BodyVerb.Light(true)) to "content"
    }

    /**
     * IMU balance demo: the head counter-rotates against the phone's tilt
     * so the body appears to stay level. Clamped to ±15° around center.
     */
    fun balanceTilt(tiltDeg: Float): Map<Int, Float> {
        val correction = (-tiltDeg).coerceIn(-15f, 15f)
        return mapOf(SERVO_HEAD_TILT to CENTER + correction)
    }

    /**
     * Face tracking → head pan. [offsetX] is the face's horizontal position
     * normalized to -1..1 (negative = subject left of frame). A small deadzone
     * (±0.1) keeps jitter from chattering the servo at center; the head pans
     * ±35° to follow the face and recenters when the face leaves the frame
     * ([offsetX] is null there — callers pass a centered value or empty map).
     */
    fun panForFace(offsetX: Float?): Map<Int, Float> {
        if (offsetX == null) return mapOf(SERVO_HEAD_PAN to CENTER)
        val clamped = offsetX.coerceIn(-1f, 1f)
        if (kotlin.math.abs(clamped) < 0.1f) return mapOf(SERVO_HEAD_PAN to CENTER)
        return mapOf(SERVO_HEAD_PAN to CENTER + clamped * 35f)
    }
}
```

- [ ] **Step 4: Remove the dead assertions from PhaseGTest**

In `app/src/test/java/com/example/meowmba/PhaseGTest.kt`, delete the test that calls `FaceMotorMap.mapAnimation(...)` (around line 85) and the test that destructures `FaceMotorMap.mapMood(mood)` into `(servos, expression)` (around line 92). Mood-pose coverage now lives in `FaceMotorMapMoodTest`. Leave the `balanceTilt` assertions (around lines 100–104) untouched. Remove the now-unused `FaceMotorMap` import only if no other test in the file uses it.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.FaceMotorMapMoodTest" --tests "com.example.meowmba.PhaseGTest"`
Expected: PASS.

- [ ] **Step 6: Expose the body from RobotManager**

In `app/src/main/java/com/example/meowmba/core/robot/RobotManager.kt`:

Add these fields next to the existing `controller` field:

```kotlin
    private var body: BodyOutput = SimulatedBodyOutput()

    /** The base's own verb vocabulary. Simulated until a BLE link comes up. */
    val bodyOutput: BodyOutput get() = body

    /** Raw fed9 frames when a BLE link exists; null otherwise. */
    val sensorFrames: kotlinx.coroutines.flow.SharedFlow<ByteArray>?
        get() = bleLink?.sensorFrames
```

In `connectLooiBle`, after the link reports ready and `bleLink` is assigned, set the real output:

```kotlin
        body = bleLink?.let { LooiBleBodyOutput(it) } ?: SimulatedBodyOutput()
```

In `useSimulated()` and in `close()`, reset it:

```kotlin
        body = SimulatedBodyOutput()
```

Delete the `BleMotorController` private class and its use: it exists only to translate phantom servo ids, and `LooiBleBodyOutput` replaces it. If `connectLooiBle` assigned `controller = BleMotorController(...)`, assign `controller = SimulatedMotorController()` there instead — the servo path is no longer how the BLE base is driven.

- [ ] **Step 7: Wire the scheduler into the companion**

In `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`:

Add the collaborators next to `private val robotManager` (around line 394):

```kotlin
    private val confinement = com.example.meowmba.core.robot.ConfinementModel()

    /**
     * Plays the body half of every clip, on the same clock as the face.
     *
     * The window callbacks deliberately ignore their argument: the scheduler
     * reports clip-relative milliseconds, while ConfinementModel measures
     * evidence decay in wall time. Passing the clip offset through would make
     * a ten-minute decay window expire after a ten-second clip.
     */
    private val bodyScheduler = com.example.meowmba.core.robot.BodyScheduler(
        scope = viewModelScope,
        output = robotManager.bodyOutput,
        onMotionWindowStart = { confinement.onMotionWindowStart(System.currentTimeMillis()) },
        onMotionWindowEnd = { confinement.onMotionWindowEnd(System.currentTimeMillis()) },
    )
```

In `playCustomAnimation(spec)` (line 5026), start the body from the same instant the face state is set — add this immediately after the `_uiState.value = ...` assignment and before `customAnimJob = viewModelScope.launch {`:

```kotlin
        bodyScheduler.play(
            com.example.meowmba.core.robot.BodyMotionMap.cues(spec, confinement.profile)
        )
```

At line 1924, replace the mood-pose block. The current code is:

```kotlin
        val (servos, expression) = com.example.meowmba.core.robot.FaceMotorMap.mapMood(moodEngine.mood)
```

Replace that line and the `motorController.setServo(...)` calls that consume `servos` with:

Add this import to the file's import block:

```kotlin
import com.example.meowmba.core.robot.perform
```

Then replace the mood-pose line and the `motorController.setServo(...)` calls that consumed `servos` with:

```kotlin
        val (verbs, expression) = com.example.meowmba.core.robot.FaceMotorMap.mapMood(moodEngine.mood)
        verbs.forEach { robotManager.bodyOutput.perform(it) }
```

Keep the existing `motorController.setExpression(expression)` call exactly as it is.

Collect sensor frames where the robot connection is established (near line 1957, after `connectLooiBle` succeeds):

```kotlin
        viewModelScope.launch {
            robotManager.sensorFrames?.collect { raw ->
                val event = com.example.meowmba.core.robot.LooiSensorParser.parse(raw)
                // Interlock: brake FIRST, interpret second. An edge is not a
                // thing to reason about while still moving toward it. Unknown
                // counts as an edge, because an undecoded frame might be one.
                if (event is com.example.meowmba.core.robot.SensorEvent.Cliff ||
                    event is com.example.meowmba.core.robot.SensorEvent.Unknown
                ) {
                    bodyScheduler.stop()
                }
                confinement.onSensor(event, System.currentTimeMillis())
            }
        }
```

Collapse the envelope when the link goes down. `robotManager.status` is already collected around line 420 — add to that collector's body:

```kotlin
                if (!st.startsWith("looi") && !st.contains("connected")) {
                    confinement.onLinkLost(System.currentTimeMillis())
                }
```

Adjust the string test to match whatever `RobotManager` actually sets for a live link (`_status.value` is assigned in `bringUp` and `connectLooiBle`); the rule is "any status that is not a live link resets the evidence".

Finally, stop the body when the app leaves the foreground. `CompanionViewModel` already exposes a lifecycle entry point used for this class of cleanup — add `bodyScheduler.stop()` to it, and to `onCleared()` as the backstop:

```kotlin
    override fun onCleared() {
        bodyScheduler.stop()
        super.onCleared()
    }
```

If the ViewModel has no `onCleared()` override yet, add the one above. If it does, add the `bodyScheduler.stop()` line as its first statement. Additionally, locate the foreground/background handler that already cancels `customAnimJob` outside `playCustomAnimation` and add `bodyScheduler.stop()` beside that cancel — a scheduler outliving the foreground is the failure mode this step exists to prevent, and `onCleared()` alone fires too late to prevent it.

- [ ] **Step 8: Verify the whole suite**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(robot): wire the LOOI body into the companion

- playCustomAnimation now starts BodyScheduler from the same instant it sets
  activeAnimation, so every invented clip performs on the base too.
- Mood resting poses speak BodyVerb; FaceMotorMap.mapAnimation is deleted
  (already dead — only PhaseGTest called it) and BleMotorController goes with
  it, since LooiBleBodyOutput replaces phantom servo translation.
- fed9 frames now reach ConfinementModel, which widens the envelope only on
  evidence from motion already commanded.
- The scheduler stops and brakes when the app leaves the foreground: a body
  coroutine outliving the foreground would leave the base driving unwatched."
```

---

## Verification against the spec

After Task 6, confirm on real hardware before calling this done:

- [ ] Connect the base, play a built-in clip, and watch the neck move with `gaze_up` rather than with `head_tilt`.
- [ ] Dump `LooiSensorParser.CaptureLog` after nudging the base near a desk edge. Those bytes are what makes a real `parse()` possible — decoding them is the immediate follow-up, and until it lands the model stays in DESK forever, which is the correct failure.
- [ ] Confirm the base brakes when the app is backgrounded mid-clip.

## Known gaps this plan leaves open, deliberately

**`ConfinementModel.onPickedUp()` is implemented and tested but never called.**
The spec lists pick-up as a failure mode that must reset the envelope. Detecting
it needs an IMU signature — a free-fall window, or a sustained change in the
gravity vector — and `CompanionViewModel` currently exposes only `st.rollDeg`
(used at line 921 for the balance demo), which is not enough to distinguish
"lifted" from "the desk got bumped". Writing a guess here would give the model a
signal that fires at the wrong times, which is worse than no signal.

Until it lands, the envelope still collapses on cliff, on `Unknown`, on link
loss, and on ten-minute staleness. The practical consequence: carry the base
from the floor to a desk without disconnecting it and it can stay in `FLOOR`
profile for up to ten minutes. **Do not leave it unattended on a desk during
that window.** Wiring pick-up detection should be the first follow-up after the
`fed9` decode.

**`SensorEvent.Tof` is parsed into the type system but not used as evidence.**
`ConfinementModel.onSensor` accepts it and deliberately does nothing, because
what a given distance *means* about confinement is unknown until real frames
are captured.

## Not in this plan

Phase 2 — authored body channels (`drive`, `turn`, `neck`, `light` on `AnimFrame`) — is deferred by the spec and needs its own plan once phase 1 is on hardware.
