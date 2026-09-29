package com.example.meowmba

import com.example.meowmba.ui.companion.ApprovalCard
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalCardTest {
    @Test fun largeEditShowsTheWholeReadableDiff() {
        val diff = "--- a/index.html\n+++ b/index.html\n" + (1..400).joinToString("\n") { "+<p>line $it with \"quotes\"</p>" }
        val card = ApprovalCard.from(buildJsonObject {
            put("permission", "edit")
            putJsonArray("patterns") { add("site/index.html") }
            putJsonObject("metadata") { put("filepath", "/root/site/index.html"); put("diff", diff) }
        })
        assertEquals("edit", card.permission)
        assertEquals(listOf("site/index.html"), card.targets)
        assertEquals(diff, card.detail) // unescaped and not truncated
        assertTrue(card.lines > 400)
    }

    @Test fun bashShowsTheCommand() {
        val card = ApprovalCard.from(buildJsonObject {
            put("permission", "bash"); putJsonObject("metadata") { put("command", "ls -la site") }
        })
        assertEquals("ls -la site", card.detail)
    }

    @Test fun unknownMetadataIsStillListed() {
        val card = ApprovalCard.from(buildJsonObject { put("permission", "read"); putJsonObject("metadata") {} })
        assertEquals("(no further detail)", card.detail)
    }
}
