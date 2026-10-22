package com.example.meowmba

import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CrossDeviceControlTest {
    @Test fun explicitNoTypingAndDraftOnlyAreNotSubmissionAuthority() {
        for (goal in listOf("Find search box. Stop before typing or posting.", "Open Reddit but do not type", "navigation-only")) assertFalse(PhoneGoalLimits.mayType(goal))
        assertTrue(PhoneGoalLimits.mayType("Write a draft saying hello"))
        for (goal in listOf("open Facebook and make a post saying meow ai test", "do not send this", "open Reddit without posting", "open ChatGPT and type hello")) assertFalse(goal, PhoneGoalLimits.maySubmit(goal))
        for (goal in listOf("send this to Gemini", "open Facebook and publish my draft", "reply to this comment", "please share this")) assertTrue(goal, PhoneGoalLimits.maySubmit(goal))
        val hint=PhoneGoalHints.next("open Reddit and find the search box. Stop before typing or posting.", "com.reddit.frontpage", "Interactive:\n1. search_text_field [edit]")
        assertEquals("done", hint?.type)
    }
    @Test fun tapWithExplicitIndexIsNormalizedWithoutInventingText() {
        assertEquals("tap_index", PhoneAgentLoop.parseAction("""{"action":"tap","index":2}""")?.type)
        assertEquals(2, PhoneAgentLoop.parseAction("""{"action":"tap","index":2}""")?.index)
    }
    @Test fun cloudControllerUsesExactlyOneRealFunctionCall() {
        val body = RemoteModelProvider.toolDecisionBody("chosen/model", "open Reddit", PhoneAgentLoop.ACTION_SCHEMA)
        assertEquals("chosen/model", body["model"]!!.jsonPrimitive.content)
        assertFalse(body["parallel_tool_calls"]!!.jsonPrimitive.boolean)
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertEquals("next_phone_action", body["tool_choice"]!!.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(PhoneAgentLoop.ACTION_SCHEMA), body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["parameters"])
    }
    private fun reply(name: String = "next_phone_action", args: String = """{"action":"tap_index","index":2}""", count: Int = 1) = buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") {
            putJsonArray("tool_calls") { repeat(count) { add(buildJsonObject { putJsonObject("function") { put("name", name); put("arguments", args) } }) } }
        } }) }
    }.toString()
    @Test fun cloudReplyMustBeValidSingleActionNotSuccessProse() {
        assertTrue(RemoteModelProvider.parseToolDecision(reply()) is ModelResponse.Complete)
        for (invalid in listOf(reply(count=0), reply(count=2), reply(name="run_shell"), reply(args="{}"), reply(args="""{"action":"invented"}"""), "done", """{"choices":[{"message":{"content":"I posted it!"}}]}""")) {
            assertTrue(invalid, RemoteModelProvider.parseToolDecision(invalid) is ModelResponse.Error)
        }
    }
    @Test fun decisionsCannotUseChangedControlsOrAnotherApp() {
        val screen="Interactive:\n1. Draft [edit]\n2. Send [tap]\nText:\n12:30"
        assertTrue(PhoneAgentLoop.sameControls("chat", screen, "chat", screen.replace("12:30", "12:31")))
        assertFalse(PhoneAgentLoop.sameControls("chat", screen, "other", screen))
        assertFalse(PhoneAgentLoop.sameControls("chat", screen, "chat", screen.replace("Draft", "Changed")))
        assertFalse(PhoneAgentLoop.sameControls("chat", screen, "chat", screen.replace("2. Send", "3. Send")))
    }
    @Test fun generalAppsRetainTheirWholeTask() {
        for (goal in listOf("open Reddit and start a post", "open Instagram and draft a message", "open X and search for cats", "open ChatGPT and ask about cats", "open Gemini and type hello")) {
            val route=AgentRouter.route(goal)!!
            assertEquals("phone.run_goal", route.toolId); assertEquals(goal, route.args["goal"])
        }
    }
    @Test fun desktopGoalsAndStatusUseActualTools() {
        val route=AgentRouter.route("on my computer, open TextEdit and write a draft")!!
        assertEquals("desktop.run_goal",route.toolId)
        assertEquals("open TextEdit and write a draft",route.args["goal"])
        assertEquals("status",AgentRouter.route("desktop status")!!.args["action"])
        assertEquals("desktop.control",AgentRouter.route("bridge status")!!.toolId)
        assertNull(AgentRouter.route("I read about computers yesterday"))
    }
    @Test fun bluetoothPairingUsesAndroidAndNeedsUserHandoff() {
        assertEquals("phone.bluetooth_devices", AgentRouter.route("list bluetooth devices")!!.toolId)
        assertEquals("My speaker", AgentRouter.route("connect bluetooth device My speaker")!!.args["name"])
        assertTrue(PhoneActionSafety.needsHandoff("Pair"))
        assertFalse(PhoneActionSafety.canConfirmSocial("Pair"))
    }
    @Test fun modelSwitchRoutesExactIdentifierAndRequiresApproval() = runTest {
        val route=AgentRouter.route("switch your model to local gemma-4-E2B-it.litertlm")!!
        assertEquals("brain.select_model",route.toolId)
        assertEquals("gemma-4-E2B-it.litertlm",route.args["model"])
        var called=false
        val tool=SelectBrainModelTool { provider, model -> called=true; "$provider/$model" }
        assertEquals(ConfirmationPolicy.ALWAYS,tool.definition.confirmationPolicy)
        assertFalse(called)
        assertEquals("local/model",(tool.execute(mapOf("provider" to "local","model" to "model")) as ToolResult.Success).output)
        assertTrue(called)
        assertTrue(SelectBrainModelTool { _,_ -> error("verification failed") }.execute(emptyMap()) is ToolResult.Failure)
    }
    @Test fun registeredOwnersIncludeNewCapabilities() {
        for ((agent,tool) in listOf("main" to "brain.models", "main" to "brain.select_model", "phone" to "phone.bluetooth_devices", "phone" to "phone.bluetooth_setup", "computer" to "desktop.run_goal", "computer" to "desktop.blender_scene"))
            assertTrue(tool,tool in AgentRegistry.byId(agent)!!.allowedTools)
    }
    @Test fun duplicateDesktopParentCannotStarveItsApprovedChild() = runTest {
        val tool=object: Tool {
            override val definition=ToolDefinition("desktop.run_goal","goal","test",emptyMap(),riskLevel=RiskLevel.MODERATE,confirmationPolicy=ConfirmationPolicy.ALWAYS)
            override suspend fun execute(args: Map<String,String>)=ToolResult.Success("done")
        }
        val registry=ToolRegistry().apply { register(tool) }
        val runner=AgentOrchestrator(this,registry,object:AuditSink { override suspend fun log(action:String,agentId:String,taskId:String?,detail:String) {} })
        val first=runner.submit("computer","desktop.run_goal",emptyMap(),"first") as SubmitResult.Started
        runCurrent()
        assertTrue(runner.submit("computer","desktop.run_goal",emptyMap(),"second") is SubmitResult.Rejected)
        runner.cancel(first.taskId); runCurrent()
        assertEquals(TaskStatus.CANCELLED,runner.tasks.value.single().status)
    }
}
