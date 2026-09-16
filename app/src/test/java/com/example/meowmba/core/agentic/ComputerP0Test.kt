package com.example.meowmba.core.agentic

import com.example.meowmba.core.computer.ComputerProfileMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerP0Test {
    @Test fun explicitComputerCommandNeverCallsModel() {
        val planner = HeuristicAgenticPlanner(object : ModelAdapter {
            override fun complete(prompt: String): String = error("Model must not replace explicit command")
        })
        val goal = AgenticGoal("explicit", "Run on Computer: printf verified")
        val call = planner.propose(goal, listOf("computer_exec"), emptyList(), emptyList())
        assertEquals("computer_exec", call.tool)
        assertEquals(listOf("sh", "-c", "printf verified"), call.argv)
        val approved = planner.propose(goal.copy(status = GoalStatus.WAITING_APPROVAL, approvalGranted = true), listOf("computer_exec"), emptyList(), emptyList())
        assertEquals("computer_exec", approved.tool)
    }


    @Test
    fun `worker port avoids Desktop Bridge discovery 8766`() {
        assertEquals(18766, ComputerProfileMarks.WORKER_PORT)
        assertTrue(ComputerProfileMarks.WORKER_PORT != 8766)
    }

    @Test
    fun `httpsReachabilityHint mentions Fold and SSH forward`() {
        val hint = ComputerProfileMarks.httpsReachabilityHint("127.0.0.1")
        assertTrue(hint.contains("Fold", ignoreCase = true))
        assertTrue(hint.contains("ssh -L", ignoreCase = true) || hint.contains("SSH", ignoreCase = true))
        assertTrue(hint.contains("18766"))
    }

    @Test
    fun `baseUrlFor uses http for meow-computer worker port`() {
        val profile = ConnectionProfile(
            id = "p1",
            displayName = "meow",
            type = ConnectionProfileType.HTTPS_RUNTIME,
            host = "100.77.97.236",
            port = ComputerProfileMarks.WORKER_PORT,
            username = "u",
            credentialRef = "ref",
        )
        val url = HttpsRuntimeClient.baseUrlFor(profile)
        assertEquals("http://100.77.97.236:18766", url)
    }

    @Test
    fun `baseUrlFor keeps full URL and https for 8443`() {
        val full = ConnectionProfile(
            id = "p2",
            displayName = "full",
            type = ConnectionProfileType.HTTPS_RUNTIME,
            host = "http://100.77.97.236:18766/",
            port = 0,
            username = "u",
            credentialRef = "ref",
        )
        assertEquals("http://100.77.97.236:18766", HttpsRuntimeClient.baseUrlFor(full))

        val classic = ConnectionProfile(
            id = "p3",
            displayName = "classic",
            type = ConnectionProfileType.HTTPS_RUNTIME,
            host = "worker.example",
            port = 8443,
            username = "u",
            credentialRef = "ref",
        )
        assertEquals("https://worker.example:8443", HttpsRuntimeClient.baseUrlFor(classic))
    }

    @Test
    fun `autoTick stops at DONE and respects cancel`() {
        val start = AgenticGoal(
            id = "g1",
            title = "uname -a",
            status = GoalStatus.OPEN,
            budgets = AgenticBudgets(maxSteps = 4, maxToolCalls = 2),
        )
        var ticks = 0
        val done = AgenticAutoTick.run(
            initial = start,
            tick = { g ->
                ticks++
                g.copy(
                    status = GoalStatus.DONE,
                    budgets = g.budgets.copy(stepsUsed = g.budgets.stepsUsed + 1),
                )
            },
        )
        assertEquals(GoalStatus.DONE, done.status)
        assertEquals(1, ticks)

        var cancelTicks = 0
        val cancelled = AgenticAutoTick.run(
            initial = start,
            tick = { g ->
                cancelTicks++
                g.copy(
                    status = GoalStatus.RUNNING,
                    budgets = g.budgets.copy(stepsUsed = g.budgets.stepsUsed + 1),
                )
            },
            isCancelled = { true },
        )
        assertEquals(GoalStatus.CANCELLED, cancelled.status)
        assertEquals(0, cancelTicks)
    }

    @Test
    fun `autoTick stops at WAITING_APPROVAL without further ticks`() {
        val start = AgenticGoal(id = "g2", title = "rm -rf /", status = GoalStatus.OPEN)
        var ticks = 0
        val out = AgenticAutoTick.run(
            initial = start,
            tick = { g ->
                ticks++
                g.copy(
                    status = GoalStatus.WAITING_APPROVAL,
                    budgets = g.budgets.copy(stepsUsed = 1, toolCallsUsed = 1),
                )
            },
        )
        assertEquals(GoalStatus.WAITING_APPROVAL, out.status)
        assertEquals(1, ticks)
        assertFalse(AgenticAutoTick.shouldContinue(out.status, out.budgets))
    }

    @Test
    fun `shellish chat titles map to computer_exec argv`() {
        assertTrue(HeuristicAgenticPlanner.looksLikeShellCommand("uname -a"))
        assertTrue(HeuristicAgenticPlanner.looksLikeShellCommand("Run on Computer: ls -la"))
        val argv = HeuristicAgenticPlanner.shellArgvFromTitle("Run on Computer: ls -la")
        assertEquals(listOf("sh", "-c", "ls -la"), argv)
    }
}
