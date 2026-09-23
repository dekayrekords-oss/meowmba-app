package com.example.meowmba.flow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.context.live.ContextNow
import com.example.meowmba.core.context.live.JournalRecall
import com.example.meowmba.core.context.live.LifeEventEntity
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.flow.Activity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Remember everything I tell you, with context" — recall answers carry time, place and situation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JournalRecallTest {
    private lateinit var db: MeowmbaDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeowmbaDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private suspend fun seed() {
        val dao = db.lifeJournalDao()
        dao.insert(LifeEventEntity(at = Fx.at(8, 12), kind = "SAID", text = "I'm going to work right now", lat = 33.4152, lon = -111.8315,
            activity = "STILL", noise = "quiet", charging = true, weather = "82° clear"))
        dao.insert(LifeEventEntity(at = Fx.at(8, 41), kind = "ACTIVITY", text = "Left the car (parked here)", lat = 33.4301, lon = -111.9402, activity = "WALKING"))
        dao.insert(LifeEventEntity(at = Fx.at(12, 5), kind = "SAID", text = "the keys are in the blue drawer", place = "Work", activity = "STILL"))
        dao.insert(LifeEventEntity(at = Fx.at(8, 0) - 86_400_000L, kind = "SAID", text = "remind me to call Dana", activity = "DRIVING", speedMph = 41f, audio = "car"))
    }

    @Test fun answersWhenAndWhereSomethingWasSaid() = runTest {
        seed()
        val lines = JournalRecall.forQuestion("when did I say I was going to work?", db.lifeJournalDao(), Fx.at(18), Fx.zone)
        val hit = lines.first { "going to work" in it }
        assertTrue(hit, "8:12 AM" in hit && "33.4152" in hit && "quiet" in hit && "charging" in hit)
    }

    @Test fun parkingKeysAndYesterday() = runTest {
        seed()
        assertTrue(JournalRecall.forQuestion("where did I park?", db.lifeJournalDao(), Fx.at(18), Fx.zone).first().contains("Left the car"))
        assertTrue(JournalRecall.forQuestion("where did I put my keys?", db.lifeJournalDao(), Fx.at(18), Fx.zone).first().contains("blue drawer"))
        val y = JournalRecall.forQuestion("what did I say yesterday?", db.lifeJournalDao(), Fx.at(18), Fx.zone)
        assertEquals(1, y.size)
        assertTrue(y.single(), "driving 41 mph" in y.single() && "car audio" in y.single())
    }

    @Test fun ordinaryChatIsNotRecallAndNowLineIsAlwaysThere() = runTest {
        seed()
        assertFalse(JournalRecall.isRecallQuestion("tell me a joke"))
        assertTrue(JournalRecall.forQuestion("tell me a joke", db.lifeJournalDao(), Fx.at(18), Fx.zone).isEmpty())
        val line = JournalRecall.now(ContextNow(activity = Activity.DRIVING, speedMps = 18f, lat = 33.4, lon = -111.9, fixAt = Fx.at(18),
            audio = "car"), Fx.at(18), "Standup Wed 9:00 AM", "82° clear")
        assertTrue(line, "driving (40 mph)" in line && "car audio connected" in line && "next: Standup" in line)
    }
}
