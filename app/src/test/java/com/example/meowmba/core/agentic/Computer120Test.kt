package com.example.meowmba.core.agentic

import android.content.Context
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.security.SecureStorageManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class Computer120Test {
    private fun runtime(planner: AgenticPlanner, store: InMemoryAgenticGoalStore = InMemoryAgenticGoalStore()): AgenticRuntime {
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.getBooleanPreference("agentic_runtime_v1", false)).thenReturn(true)
        return AgenticRuntime(mock(Context::class.java), FeatureFlags(storage), store,
            InMemoryConnectionProfileStore(), AgenticSshExecutor { _, _, _ -> SshExecResult.Error("unused") },
            planner, NoOpAgenticMemoryIndex(), object : CredentialResolver { override fun resolve(ref: String) = null })
    }
    private fun planner(argv: List<String>) = object : AgenticPlanner {
        override fun propose(goal: AgenticGoal, tools: List<String>, profiles: List<ConnectionProfile>, recentEvents: List<AgenticEvent>) =
            ProposedToolCall("computer_exec", argv = argv)
    }
    @Test fun `last allowed successful command stays done`() {
        val rt = runtime(planner(listOf("printf", "hello")))
        val g = rt.createGoal("hello", AgenticBudgets(maxSteps = 1, maxToolCalls = 1))!!
        val done = rt.tickUntilTerminal(g.id, host = ComputerExecHost { _, _, _ -> ComputerExecResult(true, "hello", 0) })!!
        assertEquals(GoalStatus.DONE, done.status)
        assertEquals(1, done.budgets.toolCallsUsed)
    }
    @Test fun `nonzero and absent exit evidence are never done`() {
        for (exit in listOf(7, null)) {
            val rt = runtime(planner(listOf("false")))
            val g = rt.createGoal("false")!!
            assertEquals(GoalStatus.BLOCKED, rt.tickUntilTerminal(g.id,
                host = ComputerExecHost { _, _, _ -> ComputerExecResult(true, "failed", exit) })!!.status)
        }
    }
    @Test fun `cancelled in flight cannot resurrect and approval cannot revive it`() {
        val rt = runtime(planner(listOf("printf", "hello")))
        val g = rt.createGoal("hello")!!
        val result = rt.tickUntilTerminal(g.id, host = ComputerExecHost { _, _, _ ->
            rt.cancelGoal(g.id)
            ComputerExecResult(true, "late", 0)
        })!!
        assertEquals(GoalStatus.CANCELLED, result.status)
        assertEquals(GoalStatus.CANCELLED, rt.approveGoal(g.id)!!.status)
    }
    @Test fun `approval reaches Computer host exactly once`() {
        val rt = runtime(planner(listOf("sh", "-c", "rm -rf example")))
        val g = rt.createGoal("delete example")!!
        var calls = 0
        val host = ComputerExecHost { _, _, approved -> calls++; assertTrue(approved); ComputerExecResult(true, "ok", 0) }
        assertEquals(GoalStatus.WAITING_APPROVAL, rt.tickUntilTerminal(g.id, host = host)!!.status)
        assertEquals(0, calls)
        rt.approveGoal(g.id)
        val done = rt.tickUntilTerminal(g.id, host = host)!!
        assertEquals(GoalStatus.DONE, done.status)
        assertFalse(done.approvalGranted)
        assertEquals(1, calls)
    }
    @Test fun `exhausted initial budget never calls planner`() {
        val rt = runtime(object : AgenticPlanner {
            override fun propose(goal: AgenticGoal, tools: List<String>, profiles: List<ConnectionProfile>, recentEvents: List<AgenticEvent>): ProposedToolCall = error("must not plan")
        })
        val g = rt.createGoal("stop", AgenticBudgets(maxSteps = 0))!!
        assertEquals(GoalStatus.BLOCKED, rt.tickUntilTerminal(g.id)!!.status)
        assertEquals(GoalStatus.BLOCKED, rt.tick(g.id)!!.status)
    }
    @Test fun `stalled null and guard-exhausted loops explicitly block`() {
        val g = AgenticGoal("g", "test")
        assertEquals(GoalStatus.BLOCKED, AgenticAutoTick.run(g, { it }).status)
        assertEquals(GoalStatus.BLOCKED, AgenticAutoTick.run(g, { null }).status)
        assertEquals(GoalStatus.BLOCKED, AgenticAutoTick.run(g,
            { it.copy(budgets = it.budgets.copy(stepsUsed = it.budgets.stepsUsed + 1)) }, maxGuard = 1).status)
    }
    @Test fun `private keys and credentials are scrubbed before stored output`() {
        val text = "hello\n-----BEGIN OPENSSH PRIVATE KEY-----\nABCSECRET\n-----END OPENSSH PRIVATE KEY-----\nAPI_KEY = hidden\nAuthorization: Bearer nope\nbye"
        val rt = runtime(planner(listOf("printf", "hello")))
        val g = rt.createGoal("hello")!!
        rt.tickUntilTerminal(g.id, host = ComputerExecHost { _, _, _ -> ComputerExecResult(true, text, 0) })
        val output = rt.listEvents(g.id).last { it.type == "computer_exec" }.detail
        assertTrue(output.contains("hello")); assertTrue(output.contains("bye"))
        listOf("ABCSECRET", "hidden", "nope").forEach { assertFalse(output.contains(it)) }
    }
}
