package com.example.meowmba.ui.companion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The memo must return exactly what Compose's lerp returns, hit or miss. */
class ColorLerpCacheTest {
    private fun randomColor(random: Random) = Color(
        red = random.nextFloat(), green = random.nextFloat(), blue = random.nextFloat(),
        alpha = if (random.nextBoolean()) 1f else random.nextFloat(),
    )

    @Test fun `every result is bit-identical to Compose lerp, including repeats and evictions`() {
        for (capacity in listOf(2, 8, 64)) {
            val cache = ColorLerpCache(capacity)
            val random = Random(capacity)
            // A small palette and fraction set, like the painters' constants, so
            // most calls repeat; plus fresh random inputs that force evictions.
            val palette = List(12) { randomColor(random) } +
                listOf(Color.White, Color.Black, Color.Transparent, Color(0xFF8398AE), Color(0xFFF0EEE5))
            val fractions = listOf(0f, .08f, .16f, .22f, .31f, .49f, .55f, .68f, 1f)
            repeat(20_000) {
                val a = if (random.nextInt(10) == 0) randomColor(random) else palette.random(random)
                val b = if (random.nextInt(10) == 0) randomColor(random) else palette.random(random)
                val f = if (random.nextInt(10) == 0) random.nextFloat() else fractions.random(random)
                assertEquals("$a $b $f", lerp(a, b, f).value, cache.lerp(a, b, f).value)
            }
        }
    }

    @Test fun `negative zero and other bit-distinct fractions are never conflated`() {
        val cache = ColorLerpCache(2)
        val a = Color(0xFF123456)
        val b = Color(0x80FEDCBA)
        for (f in listOf(0f, -0f, Float.MIN_VALUE, 1f, Math.nextDown(1f), 0.5f, Math.nextUp(0.5f))) {
            assertEquals(lerp(a, b, f).value, cache.lerp(a, b, f).value)
            assertEquals(lerp(b, a, f).value, cache.lerp(b, a, f).value)
        }
    }
}
