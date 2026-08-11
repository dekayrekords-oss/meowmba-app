package com.example.meowmba

import com.example.meowmba.ui.companion.Body
import com.example.meowmba.ui.companion.CatExpressions
import com.example.meowmba.ui.companion.CompanionEmotion
import com.example.meowmba.ui.companion.EarL
import com.example.meowmba.ui.companion.EyeL
import com.example.meowmba.ui.companion.JawL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the idle expression catalog against silent rot.
 *
 * The mood filters in [CatExpressions] are keyed by expression NAME. A name
 * that no longer exists in the catalog does not fail, warn, or log — the filter
 * entry simply stops doing anything, and an idle cat starts performing
 * expressions its mood is supposed to exclude. Five such dead names had
 * accumulated from the older keyframe preset system before these tests existed.
 *
 * This is the whole class of failure this file is here to prevent: renaming or
 * removing an expression is a normal edit, and normal edits must not be able to
 * quietly disable a mood rule.
 */
class CatExpressionCatalogTest {

    private val catalog = CatExpressions.names().toSet()

    // ── The rot guard ─────────────────────────────────────────────────────

    @Test
    fun `every mood-filtered name exists in the catalog`() {
        // THE guard. Each filter set is keyed by name, so a rename or deletion
        // turns an entry into a silent no-op and the mood rule stops applying
        // with nothing to show for it. Checked directly against the sets rather
        // than inferred from pool contents — inference passes even when the
        // entry is dead, which is how five of these survived unnoticed.
        mapOf(
            "NEGATIVE" to CatExpressions.NEGATIVE,
            "HIGH_ENERGY" to CatExpressions.HIGH_ENERGY,
            "SLEEPY" to CatExpressions.SLEEPY,
        ).forEach { (label, names) ->
            val dead = names.filterNot { it in catalog }.sorted()
            assertEquals(
                "$label references expressions that are not in the catalog, so those " +
                    "filter entries do nothing. Either restore the expression or drop the name.",
                emptyList<String>(),
                dead,
            )
        }
    }

    @Test
    fun `every catalog expression is reachable from at least one mood`() {
        // The opposite failure: a filter grown so broad that an expression can
        // never be performed at all. Silent in the same way.
        val everyPooledName = CompanionEmotion.values()
            .flatMap { CatExpressions.idlePoolFor(it) }
            .toSet()

        assertEquals(
            "expressions excluded from every idle pool — confirm this is intended",
            emptySet<String>(),
            (catalog - everyPooledName) - ALWAYS_EXCLUDED,
        )
    }

    @Test
    fun `every motion channel value is reachable from some expression`() {
        // The motion engine's expressive range only exists on screen if some
        // named expression actually spends it. Six values (EyeL.SLIT,
        // EarL.ALERT, JawL.CHATTER, Body.ORBIT/LURCH/SHAKE_V) were supported by
        // the engine and reached by nothing, so that range was dead capability.
        // Failing here means either a new channel needs an expression, or an
        // edit quietly removed the last user of an existing one.
        val expressions = CatExpressions.catalog.map { it.expression }

        assertEquals(
            "EyeL values no expression uses",
            emptyList<EyeL>(),
            EyeL.values().filterNot { v -> expressions.any { it.eye == v } },
        )
        assertEquals(
            "EarL values no expression uses",
            emptyList<EarL>(),
            EarL.values().filterNot { v -> expressions.any { it.ear == v } },
        )
        assertEquals(
            "JawL values no expression uses",
            emptyList<JawL>(),
            JawL.values().filterNot { v -> expressions.any { it.jaw == v } },
        )
        assertEquals(
            "Body values no expression uses",
            emptyList<Body>(),
            Body.values().filterNot { v -> expressions.any { it.body == v } },
        )
    }

    @Test
    fun `every expression has a sane duration and amplitude`() {
        CatExpressions.catalog.forEach { (name, e) ->
            assertTrue(
                "\"$name\" lasts ${e.durationSeconds}s — too short to read, or too long to sit through",
                e.durationSeconds in 0.4f..5.0f,
            )
            assertTrue(
                "\"$name\" has body amplitude ${e.bodyAmp}, outside the range the renderer expects",
                e.bodyAmp in 0.1f..1.3f,
            )
        }
    }

    @Test
    fun `catalog names are unique`() {
        assertEquals(
            "duplicate expression names silently shadow each other in the lookup map",
            CatExpressions.names().size,
            catalog.size,
        )
    }

    @Test
    fun `every catalog name resolves`() {
        CatExpressions.names().forEach { name ->
            assertTrue("\"$name\" is in the catalog but does not resolve", CatExpressions.resolve(name) != null)
        }
    }

    @Test
    fun `resolve is insensitive to case, dashes and underscores`() {
        assertTrue(CatExpressions.resolve("SLOW BLINK") != null)
        assertTrue(CatExpressions.resolve("slow_blink") != null)
        assertTrue(CatExpressions.resolve("slow-blink") != null)
        assertTrue(CatExpressions.resolve("  slow   blink  ") != null)
    }

    // ── Mood rules that must hold ─────────────────────────────────────────

    @Test
    fun `a calm cat never hisses at nothing`() {
        // The literal cause of "sometimes moves without making sense".
        listOf(CompanionEmotion.IDLE, CompanionEmotion.HAPPY, CompanionEmotion.SLEEPY).forEach { mood ->
            val pool = CatExpressions.idlePoolFor(mood)
            listOf("hiss", "growl", "sneer", "disgust").forEach { hostile ->
                assertFalse("$mood should never idle into \"$hostile\"", pool.contains(hostile))
            }
        }
    }

    @Test
    fun `a sleepy cat does not get the zoomies`() {
        val pool = CatExpressions.idlePoolFor(CompanionEmotion.SLEEPY)
        listOf("zoomies", "zoomies burst", "pounce", "play wiggle", "excited").forEach {
            assertFalse("a sleepy cat should not perform \"$it\"", pool.contains(it))
        }
    }

    @Test
    fun `a happy cat does not fall asleep mid-idle`() {
        val pool = CatExpressions.idlePoolFor(CompanionEmotion.HAPPY)
        listOf("fall asleep", "settle", "loaf").forEach {
            assertFalse("a happy cat should not perform \"$it\"", pool.contains(it))
        }
    }

    @Test
    fun `every emotion keeps a usable pool`() {
        // A filter that empties the pool stops idle animation entirely, which
        // reads to the user as "the cat froze" — another silent failure.
        CompanionEmotion.values().forEach { mood ->
            val pool = CatExpressions.idlePoolFor(mood)
            assertTrue("$mood has only ${pool.size} idle expressions", pool.size >= 12)
        }
    }

    @Test
    fun `alert and curious may show guarded expressions`() {
        // These moods are the reason the negative set is conditional at all.
        val alert = CatExpressions.idlePoolFor(CompanionEmotion.ALERT)
        assertTrue("an alert cat should be able to look alarmed", alert.contains("startle"))
    }

    private companion object {
        /**
         * Expressions deliberately never chosen for ambient idle, for reasons
         * other than mood. Empty today: every catalog entry is reachable from
         * at least one emotion. Add a name here only with a comment saying why.
         */
        val ALWAYS_EXCLUDED = emptySet<String>()
    }
}
