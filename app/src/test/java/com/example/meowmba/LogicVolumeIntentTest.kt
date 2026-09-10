package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LogicVolumeIntentTest {
    private fun frame(value:Double=0.0, duplicate:Boolean=false, target:String="Lead vocal") = buildJsonObject {
        put("snapshot_id","fresh-token"); put("project","file:///Fixture.logicx/")
        put("frontmost",true); put("observed_at",100)
        putJsonArray("tracks") { repeat(if(duplicate) 2 else 1) { add(buildJsonObject { put("name",target) }) } }
        putJsonArray("controls") { add(buildJsonObject {
            put("id","actual-fader");put("kind","set_volume_db");put("target",target)
            put("parameter","volume (dB)");put("value",value);put("min",-60);put("max",6)
        }) }
    }

    @Test fun exactRequestsSupportNamesQuotesAndDecimalDb() {
        assertEquals(LogicVolumeIntent("Lead vocal",-4.25),LogicVolumeIntent.parse("Please set \"Lead vocal\" volume to -4.25 dB in Logic Pro."))
        assertEquals(LogicVolumeIntent("Audio 1",-3.0),LogicVolumeIntent.parse("In Logic Pro set Audio 1 volume to -3 dB"))
        for(text in listOf("set phone volume to 40%","set Audio 1 volume to -3 dB and export","set drums and bass volume to -3 dB","can you explain how to set Audio 1 volume to -3 dB"))
            assertNull(text,LogicVolumeIntent.parse(text))
    }

    @Test fun unknownDuplicateAndOutOfRangeTargetsNeverProduceEdits() {
        for(f in listOf(frame(target="Other"),frame(duplicate=true)))
            assertTrue(runCatching { LogicVolumeIntent("Lead vocal",-3.0).decision(f) }.isFailure)
        assertTrue(runCatching { LogicVolumeIntent("Lead vocal",8.0).decision(frame()) }.isFailure)
    }

    @Test fun directRequestStillNeedsApprovalAndActualReadback() = runTest {
        var volume=0.0; var edits=0; var reads=0
        val tool=LogicSessionTool({method,_->
            assertEquals("logic_inspect",method);reads++;Result.success(frame(volume).toString())
        }, { error("Exact request must not call the model") }, {id,args,_->
            assertEquals("logic.edit",id);assertEquals("actual-fader",args["control_id"])
            assertEquals("Lead vocal",args["target"]);assertEquals("-4.25",args["value"])
            edits++;volume=-4.25;ToolResult.Success("Verified")
        })
        val result=tool.execute(mapOf("goal" to "set Lead vocal volume to -4.25 dB"))
        assertTrue(result.toString(),result is ToolResult.Success)
        assertEquals(1,edits);assertEquals(3,reads)
    }

    @Test fun approvalDenialAndIncorrectReadbackStopWithoutSecondEdit() = runTest {
        for(deny in listOf(false,true)) {
            var edits=0
            val tool=LogicSessionTool({_,_->Result.success(frame().toString())}, {error("no model")}, {_,_,_->
                edits++;if(deny) ToolResult.Failure("Denied") else ToolResult.Success("Unchanged fixture")
            })
            assertTrue(tool.execute(mapOf("goal" to "set Lead vocal volume to -3 dB")) is ToolResult.Failure)
            assertEquals(1,edits)
        }
    }

    @Test fun alreadyCorrectVolumeRequiresNoMutation() = runTest {
        val tool=LogicSessionTool({_,_->Result.success(frame(-3.0).toString())}, {error("no model")}, {_,_,_->error("no edit")})
        assertTrue(tool.execute(mapOf("goal" to "set Lead vocal volume to -3 dB")) is ToolResult.Success)
    }

    @Test fun invalidProposalsGetBoundedCorrectionWithoutDispatch() = runTest {
        val screen="""{"snapshot_id":"token","project":"file:///Fixture.logicx/","frontmost":true,"elements":[{"id":"button","label":"Gain","role":"AXButton","actions":["press"]}]}"""
        for(recover in listOf(false,true)) {
            var attempts=0;var sends=0
            val tool=LogicSessionTool({_,_->Result.success(screen)}, {prompt->
                attempts++
                if(attempts>1) assertTrue(prompt.contains("NO action sent"))
                if(recover && attempts==2) """{"action":"act","index":1,"operation":"press","value":""}"""
                else """{"action":"act","index":1,"operation":"set_number","value":"3"}"""
            }, {_,_,_->sends++;ToolResult.Failure("Stop fixture after approved valid dispatch")})
            assertTrue(tool.execute(mapOf("goal" to "Open Gain")) is ToolResult.Failure)
            assertEquals(if(recover) 2 else 3,attempts)
            assertEquals(if(recover) 1 else 0,sends)
        }
    }
}
