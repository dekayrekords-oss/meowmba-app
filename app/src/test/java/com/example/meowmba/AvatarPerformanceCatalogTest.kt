package com.example.meowmba

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.AvatarPerformanceCatalog
import com.example.meowmba.ui.companion.CustomAnimations
import com.example.meowmba.ui.companion.MouthMode
import com.example.meowmba.ui.companion.PerformanceAccent
import com.example.meowmba.ui.companion.PerformanceRotation
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class AvatarPerformanceCatalogTest {
    private val entries = AvatarPerformanceCatalog.entries
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("[\\s_-]+"), " ")

    @Test fun `catalog contains 55 unique original performance identities and readable names`() {
        assertEquals(55, entries.size)
        assertEquals(55, entries.map { it.id }.toSet().size)
        assertEquals(55, entries.map { normalize(it.title) }.toSet().size)
        assertEquals(entries.map { it.title }, AvatarPerformanceCatalog.names())
        entries.forEach { entry ->
            assertTrue(entry.id, Regex("[a-z][a-z0-9_]*").matches(entry.id))
            assertTrue(entry.title, Regex("[A-Za-z0-9 _-]{1,40}").matches(entry.title))
            assertEquals(entry.title, entry.spec.name)
            assertTrue(entry.category.isNotBlank())
            assertTrue(entry.family.isNotBlank())
        }
    }

    @Test fun `every id title and alias resolves after documented normalization`() {
        entries.forEach { entry ->
            (entry.aliases + entry.id + entry.title).forEach { name ->
                assertEquals(name, entry, AvatarPerformanceCatalog.resolve(name))
                assertEquals(name, entry, AvatarPerformanceCatalog.resolve("  ${name.uppercase(Locale.ROOT)}  "))
            }
            assertEquals(entry, AvatarPerformanceCatalog.resolve(entry.title.replace(" ", "__")))
            assertEquals(entry, AvatarPerformanceCatalog.resolve(entry.title.replace(" ", "-")))
        }
    }

    @Test fun `aliases never collide between entries including normalized ids and titles`() {
        val owners = mutableMapOf<String, String>()
        entries.forEach { entry ->
            (entry.aliases + entry.id + entry.title).forEach { name ->
                assertTrue(name.isNotBlank())
                val previous = owners.put(normalize(name), entry.id)
                assertTrue("$name belongs to $previous and ${entry.id}", previous == null || previous == entry.id)
            }
        }
    }

    @Test fun `lookup never interprets an embedded name or arbitrary instruction`() {
        listOf("", " ", "please show Warm Hello", "a sad story", "I like music",
            "Snow Wonder extra", "don't hide", "download hearts", "unknown performance").forEach {
            assertNull(it, AvatarPerformanceCatalog.resolve(it))
        }
    }

    @Test fun `all clips have bounded duration frame count and strictly increasing timing`() {
        entries.forEach { entry ->
            val frames = entry.spec.frames
            assertTrue(entry.title, entry.spec.durationMs in 1800..(if (entry.category == "Scenes") 10_000 else 6000))
            assertTrue(entry.title, frames.size in 6..16)
            assertEquals(0, frames.first().atMs)
            assertTrue(entry.title, frames.zipWithNext().all { (a, b) -> a.atMs < b.atMs })
            assertTrue(entry.title, frames[1].atMs > 0)
        }
    }

    @Test fun `every performance starts and ends with the same complete neutral pose`() {
        val neutral = AnimFrame(atMs = 0, eyeOpen = 1f, pupilSize = 0.28f, pupilGazeUp = 0f,
            headTiltDeg = 0f, earPerk = 1f, earDroopDeg = 0f, happyEyes = false,
            whiskerFlutter = false, mouth = MouthMode.IDLE, tongueOut = 0f,
            bodyBounce = 0f, tailWag = 0f, sparkle = 0f)
        entries.forEach { entry ->
            assertEquals(entry.title, neutral, entry.spec.frames.first())
            assertEquals(entry.title, neutral.copy(atMs = entry.spec.durationMs), entry.spec.frames.last())
            assertNotEquals(entry.title, neutral, entry.spec.frames[1].copy(atMs = 0))
            assertNotEquals(entry.title, neutral, entry.spec.frames[entry.spec.frames.lastIndex - 1].copy(atMs = 0))
        }
    }

    @Test fun `each authored performance includes a readable action hold before recovery`() {
        entries.forEach { entry ->
            val interior = entry.spec.frames.drop(1).dropLast(1)
            assertTrue(entry.title, interior.zipWithNext().any { (a, b) ->
                a.copy(atMs = 0) == b.copy(atMs = 0) && b.atMs - a.atMs >= 180
            })
        }
    }

    @Test fun `all authored fields stay within existing animation parser bounds`() {
        entries.forEach { entry ->
            entry.spec.frames.forEach { f ->
                val values = listOf(
                    f.eyeOpen to (0f..1.6f), f.pupilSize to (0.05f..0.9f), f.pupilGazeUp to (0f..1f),
                    f.headTiltDeg to (-25f..25f), f.earPerk to (0.3f..1.6f), f.earDroopDeg to (0f..40f),
                    f.tongueOut to (0f..1f), f.bodyBounce to (-1f..1f), f.tailWag to (-1f..1f),
                    f.sparkle to (0f..1f),
                )
                values.forEach { (value, range) ->
                    assertNotNull(entry.title, value)
                    assertTrue("${entry.title} at ${f.atMs}: $value", value!!.isFinite() && value in range)
                }
            }
        }
    }

    @Test fun `catalog round trips through the existing validator without clamps`() {
        entries.forEach { entry ->
            val (parsed, notes) = CustomAnimations.parse(CustomAnimations.toJson(entry.spec))
            assertTrue("${entry.title}: $notes", notes.isEmpty())
            assertEquals(entry.spec, parsed)
        }
    }

    @Test fun `frames never replace user colors or system privacy mouth`() {
        entries.flatMap { it.spec.frames }.forEach { frame ->
            assertNull(frame.glowColorHex)
            assertNotEquals(MouthMode.MUTED, frame.mouth)
        }
    }

    @Test fun `performance names do not disguise identical keyframe curves`() {
        val curves = entries.map { entry -> entry.spec.frames.map { it.copy(atMs = 0) } }
        assertEquals(entries.size, curves.toSet().size)
    }

    @Test fun `ambient choices are six gentle unaccented performances without scene or success cues`() {
        val ambient = entries.filter { it.ambient }
        assertEquals(setOf("listening_lean", "thoughtful_pause", "content_smile",
            "curious_sniff", "slow_blink", "ear_curiosity"), ambient.map { it.id }.toSet())
        assertEquals(ambient.size, ambient.map { it.family }.toSet().size)
        ambient.forEach { entry ->
            assertEquals(entry.title, PerformanceAccent.NONE, entry.accent)
            entry.spec.frames.forEach { frame ->
                assertTrue(entry.title, abs(frame.headTiltDeg!!) <= 3f)
                assertTrue(entry.title, abs(frame.bodyBounce!!) <= 0.08f)
                assertTrue(entry.title, frame.earPerk!! in 0.9f..1.2f)
                assertTrue(entry.title, frame.eyeOpen!! <= 1.1f)
                assertEquals(0f, frame.sparkle!!, 0f)
                assertEquals(0f, frame.tongueOut!!, 0f)
                assertNotEquals(MouthMode.SPEAKING, frame.mouth)
            }
        }
    }

    @Test fun `weather and character labels have explicit original performances and no ambient eligibility`() {
        val expected = mapOf("snow" to PerformanceAccent.SNOW, "wind" to PerformanceAccent.WIND,
            "fire" to PerformanceAccent.EMBER, "lightning" to PerformanceAccent.LIGHTNING,
            "act sloth" to PerformanceAccent.NONE, "high temperature" to PerformanceAccent.WARMTH,
            "fart" to PerformanceAccent.BUBBLE)
        expected.forEach { (label, accent) ->
            val entry = AvatarPerformanceCatalog.resolve(label)!!
            assertEquals(label, accent, entry.accent)
            assertFalse(label, entry.ambient)
        }
        assertEquals("wink", AvatarPerformanceCatalog.resolve("wink")!!.family)
        assertEquals("hide", AvatarPerformanceCatalog.resolve("hide")!!.family)
        assertEquals("peekaboo", AvatarPerformanceCatalog.resolve("peekaboo")!!.id)
    }

    @Test fun `native scene identities have dedicated accents and bounded durations`() {
        val expected = mapOf(
            "butterfly_chase" to PerformanceAccent.BUTTERFLY,
            "detective" to PerformanceAccent.DETECTIVE,
            "yarn_play" to PerformanceAccent.YARN,
            "ball_play" to PerformanceAccent.BALL,
            "acorn_play" to PerformanceAccent.ACORN,
            "mischievous_raccoon" to PerformanceAccent.SNACK,
            "sleepy_nest" to PerformanceAccent.NEST,
            "rainy_window" to PerformanceAccent.RAIN,
            "mouse_chase" to PerformanceAccent.MOUSE,
            "book_reading" to PerformanceAccent.BOOK,
            "cowboy_hat" to PerformanceAccent.HAT_COWBOY,
            "wizard_hat" to PerformanceAccent.HAT_WIZARD,
            "painter_beret" to PerformanceAccent.HAT_BERET,
            "lost_mouse" to PerformanceAccent.MOUSE,
            "reading_realization" to PerformanceAccent.BOOK,
            "hat_mishap" to PerformanceAccent.HAT_COWBOY,
        )
        val scenes = entries.filter { it.category == "Scenes" }
        assertEquals(expected.keys, scenes.map { it.id }.toSet())
        scenes.forEach { entry ->
            assertEquals(entry.id, expected[entry.id], entry.accent)
            assertFalse(entry.id, entry.ambient)
            assertTrue(entry.id, entry.spec.durationMs in 4500..10_000)
            assertTrue(entry.id, entry.spec.frames.size <= 16)
            // Existing renderer meanings must not accidentally hide or wink
            // through an unrelated scene's authored acting channels.
            assertFalse(entry.id, entry.family in setOf("wink", "hide"))
        }
    }

    @Test fun `natural scene aliases resolve exactly without capturing conversation or extra actions`() {
        val expected = mapOf(
            "chase a butterfly" to "butterfly_chase",
            "play detective" to "detective",
            "inspect a clue" to "detective",
            "play with yarn" to "yarn_play",
            "play with a ball" to "ball_play",
            "play with an acorn" to "acorn_play",
            "hide a snack" to "mischievous_raccoon",
            "mischievous racoon" to "mischievous_raccoon",
            "curl up in a nest" to "sleepy_nest",
            "watch the rain" to "rainy_window",
        )
        expected.forEach { (alias, id) ->
            assertEquals(alias, id, AvatarPerformanceCatalog.resolve(alias)?.id)
            assertNull(alias, AvatarPerformanceCatalog.resolve("I saw you $alias"))
            assertNull(alias, AvatarPerformanceCatalog.resolve("$alias and open settings"))
        }
    }

    @Test fun `animal play variants share a rotation family but have distinct emotional curves`() {
        val play = listOf("yarn_play", "ball_play", "acorn_play").map { AvatarPerformanceCatalog.resolve(it)!! }
        assertEquals(setOf("animal_play"), play.map { it.family }.toSet())
        assertEquals(3, play.map { it.spec.frames.map { frame -> frame.copy(atMs = 0) } }.toSet().size)
        assertTrue(play[0].spec.frames.any { it.bodyBounce!! < -0.2f })
        assertTrue(play[1].spec.frames.any { it.pupilGazeUp!! > 0.6f && it.bodyBounce!! > 0.4f })
        assertTrue(play[2].spec.frames.any { it.whiskerFlutter == true })
        assertTrue(play[2].spec.frames.all { abs(it.bodyBounce!!) <= 0.12f })
    }

    @Test fun `rotation avoids the last three ids and last two families when alternatives exist`() {
        val rotation = PerformanceRotation(73)
        val history = mutableListOf<com.example.meowmba.ui.companion.AvatarPerformance>()
        repeat(200) {
            val chosen = rotation.pick(entries)!!
            assertFalse(chosen.id, chosen.id in history.takeLast(3).map { it.id })
            assertFalse(chosen.family, chosen.family in history.takeLast(2).map { it.family })
            history += chosen
        }
    }

    @Test fun `equal seeds yield reproducible rotations and duplicate pool rows add no weight`() {
        val first = PerformanceRotation(19)
        val second = PerformanceRotation(19)
        repeat(60) {
            assertEquals(first.pick(entries)!!.id, second.pick(entries + entries.take(4))!!.id)
        }
    }

    @Test fun `small and empty pools degrade without immediate repeats when another id exists`() {
        val rotation = PerformanceRotation(2)
        assertNull(rotation.pick(emptyList()))
        val one = entries.take(1)
        assertEquals(one.single(), rotation.pick(one))
        assertEquals(one.single(), rotation.pick(one))
        val pair = entries.take(2).map { it.copy(family = "one-family") }
        var previous: String? = null
        repeat(30) {
            val chosen = rotation.pick(pair)!!
            assertNotEquals(previous, chosen.id)
            previous = chosen.id
            assertNull(rotation.pick(emptyList()))
        }
    }

    @Test fun `single-family pools still respect three recent ids when four ids exist`() {
        val rotation = PerformanceRotation(8)
        val pool = entries.take(4).map { it.copy(family = "one-family") }
        val history = mutableListOf<String>()
        repeat(40) {
            val chosen = rotation.pick(pool)!!
            assertFalse(chosen.id in history.takeLast(3))
            history += chosen.id
        }
    }
}
