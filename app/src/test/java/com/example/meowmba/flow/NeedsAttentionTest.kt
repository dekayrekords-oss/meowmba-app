package com.example.meowmba.flow

import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.FlowAction
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.ui.flow.needsAttention
import org.junit.Assert.assertEquals
import org.junit.Test

/** Owner 2026-09-24: tapping Flow's top bar opens the urgent issues. */
class NeedsAttentionTest {
    private fun item(id: String, u: Urgency, held: String? = null) = FlowItem(id, FlowKind.OTHER, id, "", FlowAction("primary", "Done"),
        FlowAction("later", "Later"), u, Certainty.CONFIRMED, emptyList(), heldReason = held)

    @Test fun urgentFirstQuietOnesLeftOut() {
        val s = FlowSnapshot.EMPTY.copy(main = item("worth", Urgency.WORTH_IT),
            more = listOf(item("quiet", Urgency.USEFUL), item("urgent", Urgency.URGENT), item("held", Urgency.USEFUL, "Held: limit reached")))
        assertEquals(listOf("urgent", "worth", "held"), needsAttention(s).map { it.intentionId })
    }
}
