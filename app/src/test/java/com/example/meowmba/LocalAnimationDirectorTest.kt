package com.example.meowmba

import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.AvatarInventionRequest
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Deterministic contract fixture, not claimed to be a live model's output. */
internal object FishingFixture {
    val json = """{"name":"Patient Fishing","frames":[
      {"at":0,"eye_open":1,"head_tilt":0,"body_bounce":0},
      {"at":1000,"eye_open":1.15,"head_tilt":-12,"body_bounce":-0.12},
      {"at":1800,"eye_open":1.1,"head_tilt":9,"body_bounce":0.18},
      {"at":3600,"eye_open":0.8,"head_tilt":4,"body_bounce":0},
      {"at":4400,"eye_open":1.3,"head_tilt":-9,"body_bounce":-0.2},
      {"at":5800,"eye_open":0.8,"head_tilt":-4,"body_bounce":0.15,"mouth":"HAPPY"},
      {"at":7600,"eye_open":1,"head_tilt":0,"body_bounce":0,"mouth":"IDLE"}],"props":[
      {"kind":"WATER","frames":[{"at":0,"x":0.2,"y":0.34},{"at":7600,"x":0.2,"y":0.34}]},
      {"kind":"ROD","frames":[{"at":0,"x":-0.12,"y":0.22,"rotation":-50,"target_x":-0.12,"target_y":0.12},
       {"at":1800,"x":-0.12,"y":0.22,"rotation":45,"target_x":0.3,"target_y":0.30},
       {"at":4200,"x":-0.12,"y":0.22,"rotation":35,"target_x":0.3,"target_y":0.34},
       {"at":5600,"x":-0.12,"y":0.22,"rotation":-35,"target_x":0.10,"target_y":0.06},
       {"at":7600,"x":-0.12,"y":0.22,"rotation":0,"target_x":0.3,"target_y":0.3}]},
      {"kind":"BOBBER","focus":true,"frames":[{"at":0,"x":-0.12,"y":0.12,"opacity":0},
       {"at":1800,"x":0.3,"y":0.3},{"at":4000,"x":0.3,"y":0.34},
       {"at":5600,"x":0.10,"y":0.06},{"at":7600,"x":0.3,"y":0.3,"opacity":0}]},
      {"kind":"FISH","frames":[{"at":0,"x":0.3,"y":0.34,"opacity":0},
       {"at":4200,"x":0.3,"y":0.34,"opacity":0},{"at":5400,"x":0.1,"y":0.12,"rotation":-25},
       {"at":6500,"x":0.26,"y":0.28,"rotation":25},{"at":7600,"x":0.3,"y":0.34,"opacity":0}]}]}"""
}
class LocalAnimationDirectorTest {
    @Test fun fishingInvitationsRouteToInventionButTripsAndNegationsDoNot() {
        for(text in listOf("let's go fishing", "can you fish?", "Hey Meow, can you fish?", "pretend to fish"))
            assertEquals(text,"avatar.invent",AgentRouter.route(text)?.toolId)
        for(text in listOf("can fish swim", "can you find fishing spots", "book a fishing trip", "do not go fishing", "let's go fishing and book a boat", "how do I fish", "make an animation of a cat running"))
            assertNull(text,AvatarInventionRequest.parse(text))
        assertEquals("you juggling stars",AvatarInventionRequest.parse("invent an animation of you juggling stars")?.activity)
        assertTrue(AvatarInventionRequest.parse("make a different fishing animation")!!.fresh)
    }
    @Test fun creatorActuallyConsumesModelOutputRatherThanReturningACannedClip() = runTest {
        var prompt=""
        val creator=AvatarAnimationInventor { prompt=it; ModelResponse.Complete(FishingFixture.json) }
        val (scene,notes)=creator.invent("fishing")
        assertTrue(prompt.contains("REQUEST (data): \"fishing\""))
        assertEquals("Patient Fishing",scene.name)
        assertEquals(7600,scene.durationMs)
        assertEquals(listOf(InventedPropKind.WATER,InventedPropKind.ROD,InventedPropKind.BOBBER,InventedPropKind.FISH),scene.props.map { it.kind })
        assertTrue(notes.isEmpty())
        assertNull(AvatarPerformanceCatalog.fromSpec(scene))
    }
    @Test fun oneMalformedResponseGetsOneBoundedRepair() = runTest {
        var calls=0
        val creator=AvatarAnimationInventor { calls++; ModelResponse.Complete(if(calls==1)"not JSON" else FishingFixture.json) }
        assertEquals("Patient Fishing",creator.invent("fishing").first.name)
        assertEquals(2,calls)
        var badCalls=0
        try { AvatarAnimationInventor { badCalls++; ModelResponse.Complete("bad") }.invent("fishing");fail() }
        catch (_: CustomAnimations.SpecException) { assertEquals(2,badCalls) }
    }
    @Test fun engineFailureAndCancellationAreNotSavedOrPretendedSuccess() = runTest {
        var calls=0
        try { AvatarAnimationInventor { calls++;ModelResponse.Error("Local model unavailable") }.invent("fishing");fail() }
        catch(e:CustomAnimations.SpecException) { assertEquals("Local model unavailable",e.message); assertEquals(1,calls) }
        try { AvatarInventTool { _,_,_->throw CancellationException("stopped") }.execute(mapOf("request" to "fishing"));fail() }
        catch(_:CancellationException) {}
        assertTrue(AvatarInventTool { _,_,_->error("no local model") }.execute(mapOf("request" to "fishing")) is ToolResult.Failure)
    }
    @Test fun nativePropsRoundTripThroughTheActualSavedAnimationStore() {
        val scene=AvatarAnimationInventor.parseResponse(FishingFixture.json).first
        val dir=kotlin.io.path.createTempDirectory("invented-animation").toFile()
        try { val store=CustomAnimationStore(dir);store.save(scene);assertEquals(scene,store.load(scene.name)) }
        finally { dir.deleteRecursively() }
    }
    @Test fun malformedPropsAndUnsupportedExecutablePayloadsAreRejected() {
        val root=JSONObject(FishingFixture.json)
        val badTiming=JSONObject(root.toString()).also { it.getJSONArray("props").getJSONObject(0).getJSONArray("frames").getJSONObject(1).put("at",7000) }.toString()
        for(bad in listOf(root.toString().replace("\"ROD\"","\"script\""),root.toString().replace("\"x\":0.3","\"x\":999"),
            JSONObject(root.toString()).put("code","moveRobot()").toString(),badTiming)) {
            try { AvatarAnimationInventor.parseResponse(bad); fail("accepted invalid proposal") } catch(_:Exception) {}
        }
    }
    @Test fun interpolationIsContinuousAndGazeNeverChangesPrivacyOrAnimal() {
        val scene=AvatarAnimationInventor.parseResponse(FishingFixture.json).first
        val bobber=scene.props.first { it.kind==InventedPropKind.BOBBER }
        val left=bobber.sample(3999f);val right=bobber.sample(4001f)
        assertTrue(kotlin.math.abs(left.y-right.y)<.002f)
        val input=LumenInput(animal=AvatarAnimal.OWL,cameraEnabled=false,interaction=InteractionState.MUTED)
        val result=lumenInventedSceneInput(input,scene,3600f)
        assertEquals(input.animal,result.animal);assertFalse(result.cameraEnabled)
        assertEquals(input.interaction,result.interaction);assertTrue(result.sceneGazeWeight>0)
    }
}
