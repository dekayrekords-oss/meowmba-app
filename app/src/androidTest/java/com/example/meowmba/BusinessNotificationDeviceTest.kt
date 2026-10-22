package com.example.meowmba

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Synthetic approval fixture for real notification-tap/warm/cold UI checks.
 * This is not a model benchmark and never connects a real business account. */
class BusinessNotificationDeviceTest {
    @Test fun prepareApprovalNotification(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val runtime = WorkflowRuntime.get(context)
        val fixture = File(context.filesDir, "business_agents/notification-qa.json")
        check(!fixture.exists()) { "Clean up the previous notification fixture first" }
        val suffix = UUID.randomUUID().toString()
        val path = "acceptance/notification-$suffix.txt"
        val agent = BusinessAgent(name = "QA notification check", instructions = "Synthetic approval check only", tools = setOf("files.write"))
        runtime.store.saveAgent(agent)
        val id = runtime.store.enqueue(agent.id, "Synthetic notification approval — deny this action")
        fixture.writeText(JSONObject().put("run", id).put("agent", agent.id).put("path", path).toString())
        WorkflowEngine(runtime.store, runtime.tools, WorkflowBrain { _, _, _, _, _ ->
            WorkDecision("tool", "files.write", mapOf("path" to path, "content" to "This synthetic action must be denied."))
        }).tick(id)
        val run = runtime.store.run(id)!!
        assertEquals("APPROVAL", run.status)
        assertFalse(File(runtime.workspace, path).exists())
        BusinessNotifications.post(context, run)
        // NotificationManager queues delivery; a synchronous snapshot can
        // precede the system's post even though notify() returned normally.
        val posted = kotlinx.coroutines.withTimeout(5_000) {
            var found = context.getSystemService(NotificationManager::class.java).activeNotifications
                .firstOrNull { it.id == id.hashCode() }
            while (found == null) {
                kotlinx.coroutines.delay(50)
                found = context.getSystemService(NotificationManager::class.java).activeNotifications
                    .firstOrNull { it.id == id.hashCode() }
            }
            found
        }
        assertNotNull(posted.notification.contentIntent)
        println("NOTIFICATION_QA run=$id posted=true pending_approval=true")
    }

    @Test fun verifyDeniedAndCleanUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = WorkflowRuntime.get(context)
        val fixture = File(context.filesDir, "business_agents/notification-qa.json")
        val saved = JSONObject(fixture.readText())
        val id = saved.getString("run")
        val agentId = saved.getString("agent")
        val path = saved.getString("path")
        try {
            val run = runtime.store.run(id)!!
            assertEquals("BLOCKED", run.status)
            assertEquals("DENIED", run.steps.last().status)
            assertFalse(File(runtime.workspace, path).exists())
            println("NOTIFICATION_QA run=$id denied=true file_absent=true")
        } finally {
            runtime.store.cancel(id)
            runtime.exportAndRemove(id)
            runtime.store.change { it.copy(agents = it.agents.filterNot { a -> a.id == agentId }) }
            context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
            fixture.delete()
        }
    }
}
