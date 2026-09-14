package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.agentic.AgenticOutput
import org.junit.Test

/** Read-only, bounded diagnostics for a user-reported Computer block. Never export the database. */
class ComputerDiagnosticsTest {
    @Test fun recentBlockedComputerReasons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = MeowmbaDatabase.getDatabase(context).openHelper.readableDatabase
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        db.query("SELECT id,title,status,updatedAt FROM agentic_goals WHERE status='BLOCKED' AND updatedAt>=? AND title NOT LIKE '%meow120_%' ORDER BY updatedAt DESC LIMIT 3", arrayOf(since)).use { goals ->
            println("COMPUTER_DIAGNOSTICS count=${goals.count}")
            while (goals.moveToNext()) {
                val id = goals.getString(0)
                println("COMPUTER_GOAL ${goals.getString(2)} updated=${goals.getLong(3)} title=" + AgenticOutput.scrub(goals.getString(1), 240))
                db.query("SELECT type,detail FROM agentic_events WHERE goalId=? ORDER BY timestamp DESC LIMIT 6", arrayOf(id)).use { events ->
                    while (events.moveToNext()) println("COMPUTER_EVENT ${events.getString(0)} " + AgenticOutput.scrub(events.getString(1), 320))
                }
            }
        }
    }
}
