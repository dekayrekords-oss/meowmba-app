package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class LogicProToolTest {
    private val args = mapOf("snapshot_id" to "observed", "project" to "file:///Working%20Copy.logicx/", "control_id" to "1.2",
        "kind" to "set_plugin_parameter", "target" to "Track: Plugin", "parameter" to "Threshold", "value" to "-12.5")

    @Test fun editsAlwaysRequireApprovalAndKeepNumericValuesTyped() = runTest {
        var called = false
        val tool = LogicProTool("edit") { method, params ->
            assertEquals("logic_edit", method); assertEquals(JsonPrimitive(-12.5), params["value"])
            called = true; Result.success("{\"state\":\"verified\"}")
        }
        assertEquals(ConfirmationPolicy.ALWAYS, tool.definition.confirmationPolicy)
        assertTrue(tool.execute(args) is ToolResult.Success); assertTrue(called)
    }

    @Test fun rejectsUnknownArgumentsAndNonfiniteNumbersBeforeNetwork() = runTest {
        val tool = LogicProTool("edit") { _, _ -> error("must not call bridge") }
        for (invalid in listOf(args + ("value" to "NaN"), args + ("value" to "Infinity"), args - "target", args + ("script" to "run"))) {
            assertTrue(tool.execute(invalid) is ToolResult.Failure)
        }
    }

    @Test fun uncertainAndStartedRepliesNeverBecomeSuccess() = runTest {
        for (state in listOf("started", "uncertain", "not_found")) {
            val result = LogicProTool("edit") { _, _ -> Result.success("{\"state\":\"$state\"}") }.execute(args)
            assertTrue(result is ToolResult.Failure)
        }
    }

    @Test fun cancellationIsNotTurnedIntoAnOrdinaryToolFailure() = runTest {
        val tool = LogicProTool("inspect") { _, _ -> Result.failure(CancellationException("stopped")) }
        try { tool.execute(emptyMap()); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }

    @Test fun readOnlyOperationsDoNotRequestAnEditApproval() {
        for (op in listOf("inspect", "receipt", "verify_audio")) {
            assertEquals(ConfirmationPolicy.NONE, LogicProTool(op) { _, _ -> Result.success("{}") }.definition.confirmationPolicy)
        }
    }
    @Test fun viewChangesKeepBooleanTargetsAndBounceVerificationHasASeparateReadOnlyRoute() = runTest {
        val view = LogicProTool("edit") { method, params ->
            assertEquals("logic_edit", method)
            assertEquals(true, params["value"])
            Result.success("{\"state\":\"verified\"}")
        }
        assertTrue(view.execute(args + mapOf("kind" to "show_plugin_controls", "value" to "true")) is ToolResult.Success)
        val verify = LogicProTool("verify_audio") { method, params ->
            assertEquals("logic_verify_audio", method)
            assertEquals("/workspace/mix.wav", params["path"])
            Result.success("{\"verified_audio\":true}")
        }
        assertTrue(verify.execute(mapOf("path" to "/workspace/mix.wav")) is ToolResult.Success)
    }

}
