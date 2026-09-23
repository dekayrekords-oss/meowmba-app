package com.example.meowmba.ui.companion.lumen

import com.example.meowmba.core.lumen.ReflexSensoryFeed
import com.example.meowmba.ui.companion.InteractionState
import com.example.meowmba.ui.companion.LumenInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** The per-frame label is reused between changes; it must equal a fresh format every time. */
class ReflexSummaryLabelTest {
    private fun expected(bridge: LumenReflexBridge): String {
        val s = bridge.reflexState.coerced()
        val action = bridge.currentAction.name.lowercase().replace('_', ' ')
        return "Reflex: $action · c${"%.0f".format(s.curiosity * 100)} s${"%.0f".format(s.socialAttention * 100)}"
    }

    @Test fun `label always equals the freshly formatted text`() {
        val bridge = LumenReflexBridge()
        val random = Random(42)
        var t = 0f
        var feed = ReflexSensoryFeed()
        repeat(4_000) { step ->
            if (step % 40 == 0) feed = ReflexSensoryFeed(
                cameraEnabled = random.nextBoolean(),
                hasFace = random.nextBoolean(),
                attentionFresh = random.nextBoolean(),
                trackingId = random.nextInt(-1, 3),
                speaking = random.nextInt(4) == 0,
                resting = random.nextBoolean(),
                speechEnergy = random.nextFloat(),
                affectionPulse = random.nextInt(8) == 0,
                loudSound = random.nextInt(10) == 0,
                motionMagnitude = random.nextFloat() * 2f,
                thermalCaution = random.nextInt(12) == 0,
            )
            t += 1f / 30f
            bridge.modulate(LumenInput(interaction = InteractionState.IDLE), t, feed)
            assertEquals("step $step", expected(bridge), bridge.summaryLabel())
        }
    }

    @Test fun `a locale change reformats even when the values did not move`() {
        val original = Locale.getDefault()
        try {
            val bridge = LumenReflexBridge()
            bridge.modulate(LumenInput(), 0f, ReflexSensoryFeed())
            Locale.setDefault(Locale.US)
            assertEquals(expected(bridge), bridge.summaryLabel())
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals(expected(bridge), bridge.summaryLabel())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `half-up rounding only changes the text at half-unit boundaries`() {
        // The reuse rule: within one floor(2v) bucket "%.0f" prints the same digits.
        val byBucket = HashMap<Double, String>()
        var v = 0f
        while (v <= 100f) {
            for (probe in listOf(v, Math.nextUp(v), Math.nextDown(v).coerceAtLeast(0f))) {
                val text = "%.0f".format(Locale.US, probe)
                val bucket = kotlin.math.floor(probe.toDouble() * 2)
                val seen = byBucket.getOrPut(bucket) { text }
                assertEquals("value $probe", seen, text)
            }
            v += 0.0625f
        }
    }
}
