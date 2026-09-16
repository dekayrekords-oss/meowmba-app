package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.bridge.DesktopBridgeProtocol
import com.example.meowmba.core.bridge.cancellableBridgeIo
import com.example.meowmba.core.agent.runtime.AgentRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.content.TextContent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LogicWorkflowTest {
    @Test fun logicPlanningUsesLogicSchemaAndValidatorAtTheProviderBoundary() = runTest {
        var called=false
        val client=HttpClient(MockEngine { request ->
            called=true
            val body=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(Json.parseToJsonElement(LogicSessionTool.ACTION_SCHEMA),body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["parameters"])
            respond("""{"choices":[{"message":{"tool_calls":[{"function":{"name":"next_phone_action","arguments":"{\"action\":\"controls\"}"}}]}}]}""")
        }) { install(HttpTimeout) }
        com.example.meowmba.core.agent.RemoteModelProvider("Test","https://fixture.invalid","fixture-key","fixture-model",clientOverride=client).use { provider ->
            assertEquals("""{"action":"controls"}""",LogicSessionTool.planWith(provider,"Inspect the Logic mixer"))
        }
        assertTrue(called)
    }

    @Test fun cloudFunctionReplyIsValidatedForItsActualController() {
        fun reply(args:String)=buildJsonObject { putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") {
            putJsonArray("tool_calls") { add(buildJsonObject { putJsonObject("function") { put("name","next_phone_action");put("arguments",args) } }) }
        } }) } }.toString()
        val args="""{"action":"act","index":2,"operation":"set_text","value":"New bounce.wav"}"""
        assertTrue(com.example.meowmba.core.agent.RemoteModelProvider.parseToolDecision(reply(args),LogicSessionTool::validDecision) is com.example.meowmba.core.agent.ModelResponse.Complete)
        assertTrue(com.example.meowmba.core.agent.RemoteModelProvider.parseToolDecision(reply(args)) is com.example.meowmba.core.agent.ModelResponse.Error)
        assertTrue(LogicSessionTool.validDecision("""{"action":"edit","index":128,"value":"0.5"}"""))
        for(invalid in listOf("{}","""{"action":"act","index":6001,"operation":"press","value":""}""", """{"action":"act","index":1,"operation":"shell","value":"anything"}""", """{"action":"done","summary":"done"}"""))
            assertFalse(invalid,LogicSessionTool.validDecision(invalid))
    }

    @Test fun providerJsonContentStillRequiresExactlyOneValidAction() {
        fun reply(content:String)=buildJsonObject { putJsonArray("choices") {add(buildJsonObject {putJsonObject("message") {put("content",content);put("tool_calls",JsonNull)}})} }.toString()
        for (content in listOf("""{"action":"controls"}""","```json\n{\"action\":\"controls\"}\n```"))
            assertTrue(com.example.meowmba.core.agent.RemoteModelProvider.parseToolDecision(reply(content),LogicSessionTool::validDecision) is com.example.meowmba.core.agent.ModelResponse.Complete)
        for (content in listOf("I changed the volume", """{"action":"controls"}{"action":"edit","index":1,"value":"-3"}""", """[{"action":"controls"}]""", """{"action":"shell"}"""))
            assertTrue(com.example.meowmba.core.agent.RemoteModelProvider.parseToolDecision(reply(content),LogicSessionTool::validDecision) is com.example.meowmba.core.agent.ModelResponse.Error)
    }

    @Test fun approvalUsesFreshTokenOnlyForUnchangedControls() = runTest {
        for(changed in listOf(false,true)) {
            var reads=0;var approved=false
            val tool=LogicSessionTool({_,_->
                reads++
                Result.success(frame.replace("workflow-test","snapshot-$reads").let {if(changed && reads>1) it.replace("0 dB","Human change") else it})
            }, {"""{"action":"act","index":1,"operation":"press","value":""}"""}, {_,args,_->
                approved=true;assertEquals("snapshot-2",args["snapshot_id"]);ToolResult.Failure("Stop after test dispatch")
            })
            assertTrue(tool.execute(mapOf("goal" to "Open Gain")) is ToolResult.Failure)
            assertEquals(!changed,approved)
        }
    }

    @Test fun malformedPlanningReplyGetsOneCorrectionBeforeAnyAction() = runTest {
        for (repairs in listOf(true,false)) {
            var attempts=0
            val client=HttpClient(MockEngine { request ->
                attempts++
                val body=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                if(attempts==2) assertTrue(body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content.contains("EXACTLY ONE"))
                respond(buildJsonObject { putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") {
                    putJsonArray("tool_calls") { repeat(if(repairs && attempts==2) 1 else 2) {
                        add(buildJsonObject { putJsonObject("function") {put("name","next_phone_action");put("arguments","""{"action":"controls"}""")} })
                    } }
                } }) } }.toString())
            }) { install(HttpTimeout) }
            com.example.meowmba.core.agent.RemoteModelProvider("Test","https://fixture.invalid","fixture-key","fixture-model",clientOverride=client).use { provider ->
                val result=provider.generateToolDecision("Inspect mixer",LogicSessionTool.ACTION_SCHEMA,LogicSessionTool::validDecision)
                assertEquals(repairs,result is com.example.meowmba.core.agent.ModelResponse.Complete)
            }
            assertEquals(2,attempts)
        }
    }
    private val frame = """{"snapshot_id":"workflow-test","project":"file:///Working.logicx/","frontmost":true,"observed_at":100,"elements":[{"id":"0.1","role":"AXMenuItem","label":"Gain","path":"Audio 1 / Gain","value":"0 dB","actions":["press"]}]}"""

    @Test fun controllerUsesObservedIdentityAndRegularApprovedStep() = runTest {
        val calls = mutableListOf<Map<String,String>>()
        val answers = ArrayDeque(listOf("""{"action":"act","index":1,"operation":"press","value":""}""",
            """{"action":"done","summary":"Opened Gain","evidence":[{"index":1,"value":"Gain"}]}"""))
        val tool = LogicSessionTool({ _, _ -> Result.success(frame) }, { answers.removeFirst() }, { id,args,_ ->
            assertEquals("logic.act",id); calls.add(args); ToolResult.Success("{\"state\":\"observed\"}")
        })
        assertTrue(tool.execute(mapOf("goal" to "Open Gain in Logic Pro")) is ToolResult.Success)
        assertEquals("workflow-test",calls.single()["snapshot_id"])
        assertEquals("0.1",calls.single()["element_id"])
        assertFalse(tool.isActive)
    }

    @Test fun unavailableControlOrMissingEvidenceNeverCompletes() = runTest {
        for (answer in listOf("""{"action":"act","index":99,"operation":"press","value":""}""",
            """{"action":"done","summary":"All done"}""",
            """{"action":"done","summary":"All done","evidence":[{"index":1,"value":"Invented"}]}""")) {
            val tool=LogicSessionTool({_,_->Result.success(frame)}, {answer}, {_,_,_->error("No mutation allowed")})
            assertTrue(tool.execute(mapOf("goal" to "Edit Logic Pro")) is ToolResult.Failure)
        }
    }

    @Test fun repeatedUiActionStopsBeforeThirdDispatch() = runTest {
        var actions=0
        val tool=LogicSessionTool({_,_->Result.success(frame)},
            {"""{"action":"act","index":1,"operation":"press","value":""}"""},
            {_,_,_->actions++;ToolResult.Success("{\"state\":\"observed\"}")})
        assertTrue(tool.execute(mapOf("goal" to "Open Gain in Logic")) is ToolResult.Failure)
        assertEquals(2,actions)
    }

    @Test fun exportCannotFinishUsingAnOldOrSilentFile() = runTest {
        for ((modified,signal,success) in listOf(Triple(90,true,false),Triple(101,false,false),Triple(101,true,false))) {
            val answers=ArrayDeque(listOf("""{"action":"verify_audio","path":"/workspace/bounce.wav"}""",
                """{"action":"done","summary":"Bounce written","evidence":[{"index":1,"value":"Gain"}]}"""))
            val tool=LogicSessionTool({method,_->Result.success(if(method=="logic_verify_audio")
                """{"verified_audio":true,"signal_present":$signal,"modified_at":$modified,"path":"/workspace/bounce.wav","sha256":"test"}""" else frame)},
                {answers.removeFirst()},{_,_,_->error("No edit")})
            assertEquals(success,tool.execute(mapOf("goal" to "Bounce in Logic Pro")) is ToolResult.Success)
        }
    }

    @Test fun exportCompletionRequiresAnObservedExportActionAndNewMeasuredAudio() = runTest {
        val exportFrame=frame.replace("AXMenuItem","AXButton").replace("Audio 1 / Gain","Bounce Output / Bounce").replace("\"label\":\"Gain\"","\"label\":\"Bounce\"")
        val answers=ArrayDeque(listOf("""{"action":"act","index":1,"operation":"press","value":""}""",
            """{"action":"verify_audio","path":"/workspace/new.wav"}""",
            """{"action":"done","summary":"Bounce written","evidence":[{"index":1,"value":"Bounce"}]}"""))
        val tool=LogicSessionTool({method,_->Result.success(if(method=="logic_verify_audio")
            """{"verified_audio":true,"signal_present":true,"modified_at":101,"path":"/workspace/new.wav","sha256":"test"}""" else exportFrame)},
            {answers.removeFirst()},{_,_,_->ToolResult.Success("{\"state\":\"observed\"}")})
        assertTrue(tool.execute(mapOf("goal" to "Bounce in Logic Pro")) is ToolResult.Success)
    }

    @Test fun foregroundRequestIsApprovedAndMustBeObservedBeforeAnyEditing() = runTest {
        var focus=false
        val tool=LogicSessionTool({_,_->Result.success(if(focus)frame else frame.replace("\"frontmost\":true","\"frontmost\":false"))},
            {"""{"action":"done","summary":"Inspected Gain","evidence":[{"index":1,"value":"Gain"}]}"""},
            {id,args,_->assertEquals("desktop.control",id);assertEquals("Logic Pro",args["name"]);focus=true;ToolResult.Success("Launched")})
        assertTrue(tool.execute(mapOf("goal" to "Inspect the open plugin")) is ToolResult.Success)
        assertTrue(focus)
    }

    @Test fun cancellationSendsASeparateRevocationWithTheSameOperationIdentity() = runTest {
        val called=mutableListOf<String>()
        val tool=LogicProTool("act") { method,params ->
            called.add(method)
            if(method=="logic_act") throw CancellationException("Stop")
            assertEquals(DesktopBridgeProtocol.sha256Hex("workflow-test".toByteArray()),params["operation_id"])
            Result.success("{\"state\":\"cancel_requested\"}")
        }
        try {
            tool.execute(mapOf("snapshot_id" to "workflow-test","project" to "file:///Working.logicx/","element_id" to "0.1","action" to "press","value" to ""))
            fail("Must propagate cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf("logic_act","logic_cancel"),called)
    }

    @Test fun blockedNetworkReadDoesNotDelayCoroutineCancellation() = runBlocking {
        val started=CountDownLatch(1);val release=CountDownLatch(1);val disconnected=CountDownLatch(1)
        val job=launch { cancellableBridgeIo({ disconnected.countDown() }) { started.countDown();release.await(5,TimeUnit.SECONDS) } }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(2,TimeUnit.SECONDS)) }
            withTimeout(500) { job.cancelAndJoin() }
            withContext(Dispatchers.IO) { assertTrue(disconnected.await(2,TimeUnit.SECONDS)) }
        } finally { release.countDown() }
    }

    @Test fun spokenInspectionIsReadableAndDoesNotExposeSnapshotJson() {
        val raw="""{"tracks":[{"name":"Vocals"},{"name":"Guitar"}],"frontmost":true,"snapshot_id":"secret-handle","channel_strips":[{"plugins":[{"name":"StudioAI"}]}]}"""
        val summary=LogicResultPresentation.summary("logic.inspect",raw)
        assertTrue(summary.contains("Vocals"));assertTrue(summary.contains("StudioAI"))
        assertFalse(summary.contains("snapshot"));assertFalse(summary.contains("{"))
        assertTrue(LogicResultPresentation.summary("logic.verify_audio","""{"verified_audio":true,"signal_present":false}""").contains("silent"))
    }

    @Test fun naturalLogicEditingRequestsReachTheWorkflow() {
        for (text in listOf("mix my song in Logic Pro", "add a compressor in logic", "bounce my Logic project"))
            assertEquals("logic.run_goal",AgentRouter.route(text)?.toolId)
        assertEquals("logic.inspect",AgentRouter.route("inspect logic")?.toolId)
    }

    @Test fun logicTrackNumbersNeverBecomePhoneVolumePercentages() {
        for (text in listOf("In Logic Pro set Audio 1 volume to -3 dB", "Meow, turn down track 2 in Logic",
            "mute Audio 1 in Logic Pro", "on my computer set the Logic Pro fader to -6 dB",
            "create a new project called Test in Logic Pro", "adjust the compressor threshold in Logic")) {
            val intent=AgentRouter.route(text)
            assertEquals(text,"logic.run_goal",intent?.toolId)
            assertEquals(text,intent?.args?.get("goal"))
        }
        assertEquals("logic.inspect",AgentRouter.route("Meow inspect logic")?.toolId)
        assertEquals("phone.set_volume",AgentRouter.route("set phone volume to 50 percent")?.toolId)
        assertNotEquals("phone.set_volume",AgentRouter.route("set track 1 volume to -3 dB")?.toolId)
        assertNotEquals("logic.run_goal",AgentRouter.route("change the business logic in my app")?.toolId)
    }
}
