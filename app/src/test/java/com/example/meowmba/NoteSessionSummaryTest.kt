package com.example.meowmba

import com.example.meowmba.core.vault.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class NoteSessionSummaryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun summaryUsesActualTimestampedSpeechAndDoesNotCreateActions() {
        val note = VaultNote("Notes/meeting", "_Started today_\n\n**9:01 AM**  \nWe agreed to use blue.\n\n**9:02 AM**  \nRemind me to send the draft tomorrow.", emptyList(), 1, 2)
        val summary = NoteSessionSummary.render(note, "Stopped by you")
        assertTrue(summary.contains("[[Notes/meeting]]"))
        assertTrue(summary.contains("2 recorded segment(s)"))
        assertTrue(summary.contains("9:01 AM — We agreed to use blue."))
        assertTrue(summary.contains("Possible follow-ups to review"))
        assertTrue(summary.contains("No reminders or external actions were created"))
    }
    @Test fun emptyNotesDoNotInventSummary() {
        val summary = NoteSessionSummary.render(VaultNote("Notes/day", "_Started today_", emptyList(), 1, 2), "Stopped")
        assertTrue(summary.contains("No speech segments were saved."))
        assertFalse(summary.contains("## Possible follow-ups"))
    }
    @Test fun journalAppendPreservesSegmentsAndEnforcesStorageLimitBeforeMutation() {
        val vault = VaultRepository(temp.newFolder())
        vault.appendTranscript("Notes/day", "**9:01 AM**  \nFirst words")
        val before = vault.transcript("Notes/day")!!.body
        try { vault.appendTranscript("Notes/day", "must not fit", maxBytes = 1); fail("Expected limit") }
        catch (_: IllegalStateException) {}
        assertEquals(before, vault.transcript("Notes/day")!!.body)
        vault.appendTranscript("Notes/day", "**9:02 AM**  \nSecond words")
        assertTrue(vault.transcript("Notes/day")!!.body.contains("First words"))
        assertTrue(vault.transcript("Notes/day")!!.body.contains("Second words"))
        assertEquals(1, vault.list().size)
    }
    @Test fun digestIsBoundedEvenForManyLongTranscriptSegments() {
        val body = (1..2_050).joinToString("\n\n") { "**9:01 AM**  \nPlease follow up on item $it " + "long ".repeat(100) }
        val summary = NoteSessionSummary.render(VaultNote("Notes/day", body, emptyList(), 1, 2), "Stopped")
        assertTrue(summary.length < 12_000)
        assertTrue(summary.contains("first 2,000 segments"))
    }
}
