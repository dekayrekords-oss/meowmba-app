package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agentic.*
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.*
import org.junit.Test

class AgenticNotice129DeviceTest {
    @Test fun dismissSurvivesRecreationAndKeepsTaskHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = FeatureFlags.from(context)
        val previous = flags.agenticRuntimeV1
        flags.setAgenticRuntimeV1(true)
        val runtime = AgenticRuntime.from(context)
        val db = MeowmbaDatabase.getDatabase(context)
        val store = RoomAgenticGoalStore(db.agenticGoalDao(), db.agenticEventDao())
        val goal = runtime.createGoal("Notice129 acceptance ${System.currentTimeMillis()}")!!
        try {
            store.updateGoal(goal.copy(status = GoalStatus.BLOCKED))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var vm: CompanionViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java]; vm.refreshAgenticPresenceHint() }
                val notice = vm.uiState.value.agenticDismissibleRevision
                assertNotNull(notice)
                assertTrue(notice!!.startsWith(goal.id))
                scenario.onActivity { vm.dismissAgenticPresenceHint() }
                assertNotEquals(notice, vm.uiState.value.agenticDismissibleRevision)
                scenario.recreate()
                scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java]; vm.refreshAgenticPresenceHint() }
                assertNotEquals(notice, vm.uiState.value.agenticDismissibleRevision)
                assertEquals(GoalStatus.BLOCKED, runtime.getGoal(goal.id)!!.status)
                assertTrue(runtime.listEvents(goal.id).any { it.type == "created" })
                Thread.sleep(10)
                store.updateGoal(runtime.getGoal(goal.id)!!)
                scenario.onActivity { vm.refreshAgenticPresenceHint() }
                assertTrue(vm.uiState.value.agenticDismissibleRevision!!.startsWith(goal.id))
                scenario.onActivity { vm.openAgenticTaskDetails() }
                assertTrue(vm.uiState.value.showSettings)
                assertEquals("Neural / Lumen", vm.uiState.value.settingsInitialPage)
            }
        } finally {
            runtime.cancelGoal(goal.id)
            flags.setAgenticRuntimeV1(previous)
        }
    }

    /** Explicit repair of only the old printf/sleep acceptance fixtures; user tasks stay intact. */
    @Test fun retireKnownComputer120Fixtures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = FeatureFlags.from(context)
        val previous = flags.agenticRuntimeV1
        flags.setAgenticRuntimeV1(true)
        try {
            val runtime = AgenticRuntime.from(context)
            val fixture = Regex("Run on Computer: (?:printf meow120_(?:[0-9]+_)?(?:verified|failed)(?:; exit 7)?|sleep 3; printf meow120_[0-9]+_late)")
            val all = runtime.listOpenGoals()
            val retained = all.filterNot { fixture.matches(it.title) }
            val retired = all.filter { fixture.matches(it.title) }
            retired.forEach { runtime.cancelGoal(it.id) }
            assertTrue(retired.all { runtime.getGoal(it.id)?.status == GoalStatus.CANCELLED })
            assertTrue(retained.all { runtime.getGoal(it.id)?.status == it.status })
            println("NOTICE129 retired_test_tasks=${retired.size} retained_user_tasks=${retained.size}")
        } finally { flags.setAgenticRuntimeV1(previous) }
    }
}
