package com.example.meowmba

import com.example.meowmba.core.workflows.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BusinessReadinessTest {
    @get:Rule val temp = TemporaryFolder()
    private fun agent(id: String = "reader") = BusinessAgent(id = id, name = id, instructions = "Read assigned inputs", tools = setOf("files.read"))

    @Test fun unconfiguredServicesAreNeverDisplayedAsAutonomousAccess() {
        val summary = BusinessReadiness.from(WorkflowSnapshot())
        assertEquals(0, summary.configuredServices)
        assertEquals(0, summary.grantedServiceTools)
        assertEquals(0, summary.automaticReads)
        assertEquals(0, summary.configuredSchedules)
    }

    @Test fun scheduledReadsRequireEnabledServiceAgentAndSpecificReadGrant() {
        val connection = BusinessMcpConnection(id = "mail", name = "Mail", endpoint = "https://example.invalid/mcp",
            tools = listOf(BusinessMcpTool("list", "Read mail", "{}", true), BusinessMcpTool("send", "Send mail", "{}", false)),
            automaticReadTools = setOf("list", "send"))
        val enabled = agent().copy(tools = setOf("mcp.mail.list", "mcp.mail.send", "mcp.missing.read"), intervalMinutes = 15, scheduledGoal = "Summarize mail")
        val snapshot = WorkflowSnapshot(agents = listOf(enabled), connections = listOf(connection))
        val ready = BusinessReadiness.from(snapshot)
        assertEquals(2, ready.grantedServiceTools); assertEquals(1, ready.automaticReads)
        assertEquals(1, ready.configuredSchedules)
        assertEquals(0, BusinessReadiness.from(snapshot.copy(agents = listOf(enabled.copy(enabled = false)))).automaticReads)
        assertEquals(0, BusinessReadiness.from(snapshot.copy(connections = listOf(connection.copy(enabled = false)))).grantedServiceTools)
        assertEquals(0, BusinessReadiness.from(snapshot.copy(connections = listOf(connection.copy(automaticReadTools = emptySet())))).automaticReads)
    }

    @Test fun repeatedQueueRequestsReuseThePendingJobIncludingAcrossStoreReload() {
        val file = File(temp.root, "jobs.json"); val store = WorkflowStore(file)
        store.saveAgent(agent())
        val id = store.enqueue("reader", "Summarize files")
        assertEquals(id, store.enqueue("reader", "  Summarize files  "))
        val restored = WorkflowStore(file)
        assertEquals(id, restored.enqueue("reader", "Summarize files"))
        assertEquals(1, restored.state.value.runs.size)
        restored.cancel(id)
        assertNotEquals(id, restored.enqueue("reader", "Summarize files"))
    }

    @Test fun pausingSchedulesIsDurableAndPreservesPermissionsAndManualJobs() {
        val file = File(temp.root, "jobs.json"); val store = WorkflowStore(file)
        val configured = agent().copy(intervalMinutes = 15, scheduledGoal = "Summarize assigned files")
        store.saveAgent(configured)
        store.change { it.copy(schedulesPaused = true) }
        val restored = WorkflowStore(file)
        restored.scheduleDue(1_000_000)
        assertTrue(restored.state.value.runs.isEmpty())
        assertEquals(configured, restored.agent(configured.id))
        assertTrue(BusinessReadiness.from(restored.state.value).schedulesPaused)
        val manual = restored.enqueue(configured.id, "Read now")
        assertEquals("QUEUED", restored.run(manual)!!.status)
        restored.cancel(manual)
        restored.change { it.copy(schedulesPaused = false) }
        restored.scheduleDue(2_000_000)
        assertEquals(2, restored.state.value.runs.size)
    }

    @Test fun olderStoredDataDefaultsToItsExistingScheduleBehavior() {
        val file = File(temp.root, "old.json").also { it.writeText("""{"version":1,"agents":[],"runs":[],"connections":[]}""") }
        assertFalse(WorkflowStore(file).state.value.schedulesPaused)
    }
    @org.junit.Test fun businessModeCommandsOpenTheWorkspaceWithoutStartingJobs() {
        for (phrase in listOf("business mode", "open business mode", "switch to business mode")) {
            val intent = com.example.meowmba.core.agent.runtime.AgentRouter.route(phrase)
            org.junit.Assert.assertEquals(phrase, "business.open", intent?.toolId)
            org.junit.Assert.assertTrue(intent!!.args.isEmpty())
        }
    }

}
