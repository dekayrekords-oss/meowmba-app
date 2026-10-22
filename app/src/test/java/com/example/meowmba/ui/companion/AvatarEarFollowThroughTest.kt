package com.example.meowmba.ui.companion

import org.junit.Assert.*
import org.junit.Test

class AvatarEarFollowThroughTest {
    @Test fun onlyPaintersThatConsumeLegacyEarLagRequestItsFrameLoop() {
        for (style in AvatarStyle.entries) {
            assertEquals(style.name, style == AvatarStyle.NEO || style == AvatarStyle.PEARL,
                needsLegacyEarFollowThrough(style, lumenOwnsPose = style.isLumen))
        }
    }

    @Test fun aPropPerformanceThatUsesLumenSuspendsTheOtherwiseSelectedLegacyEars() {
        // Invented props temporarily route even NEO/PEARL through the Lumen painter.
        for (style in AvatarStyle.entries) {
            assertFalse(style.name, needsLegacyEarFollowThrough(style, lumenOwnsPose = true))
        }
        for (style in listOf(AvatarStyle.NEO, AvatarStyle.PEARL)) {
            assertTrue("${style.name} resumes its own ears after the Lumen performance",
                needsLegacyEarFollowThrough(style, lumenOwnsPose = false))
        }
    }
}
