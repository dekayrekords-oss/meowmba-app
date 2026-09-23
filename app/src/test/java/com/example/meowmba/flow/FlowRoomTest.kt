package com.example.meowmba.flow

import com.example.meowmba.core.flow.IntentionStatus
import com.example.meowmba.core.flow.db.FlowEntityMapper
import com.example.meowmba.core.flow.db.MIGRATION_10_11_STATEMENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRoomTest {
    @Test fun entityRoundTripKeepsFactsAndPeople() {
        val i = Fx.bulb.copy(waitingPeople = listOf("Sam", "Dana"), status = IntentionStatus.SNOOZED, snoozedUntil = 123L, dueAt = 99L)
        assertEquals(i, FlowEntityMapper.fromEntity(FlowEntityMapper.toEntity(i, 1L)))
    }

    @Test fun badStoredValuesFallBackSafely() {
        val e = FlowEntityMapper.toEntity(Fx.bulb, 1L).copy(kind = "ALIEN", status = "??", factsJson = "{broken")
        val back = FlowEntityMapper.fromEntity(e)
        assertEquals(com.example.meowmba.core.flow.FlowKind.OTHER, back.kind)
        assertEquals(IntentionStatus.OPEN, back.status)
        assertTrue(back.facts.isEmpty())
    }

    @Test fun migrationSqlIsAdditive() {
        assertEquals(2, MIGRATION_10_11_STATEMENTS.size)
        MIGRATION_10_11_STATEMENTS.forEach { stmt ->
            val sql = stmt.uppercase()
            assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS `FLOW_"))
            assertTrue(!sql.contains("DROP ") && !sql.contains("ALTER ") && !sql.contains(";"))
        }
    }
}
