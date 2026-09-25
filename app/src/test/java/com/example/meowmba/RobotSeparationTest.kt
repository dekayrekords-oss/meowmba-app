package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.CozmoControlTool
import com.example.meowmba.core.agent.tools.RobotMoveTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.robot.RobotCommandTarget
import com.example.meowmba.core.robot.RobotManager
import com.example.meowmba.core.robot.cozmo.CozmoController
import com.example.meowmba.ui.flow.HomeShelves
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RobotSeparationTest {
    @Test fun `robots have disjoint tool ownership`() {
        val looi = AgentRegistry.byId("robot")!!
        val cozmo = AgentRegistry.byId("cozmo")!!
        assertEquals("LOOI", looi.name)
        assertEquals("Cozmo", cozmo.name)
        assertEquals(setOf("robot.move"), looi.allowedTools)
        assertEquals(setOf("cozmo.control"), cozmo.allowedTools)
        assertEquals(looi, AgentRegistry.ownerOf("robot.move"))
        assertEquals(cozmo, AgentRegistry.ownerOf("cozmo.control"))
    }

    @Test fun `named commands stay on their own robot`() {
        for (action in listOf("turn left", "look up", "stop")) {
            val cozmo = AgentRouter.route("Meow, Cozmo $action")!!
            assertEquals("cozmo", cozmo.agentId)
            assertEquals("cozmo.control", cozmo.toolId)
            val looi = AgentRouter.route("LOOI $action")!!
            assertEquals("robot", looi.agentId)
            assertEquals("robot.move", looi.toolId)
        }
        assertEquals("cozmo.control", AgentRouter.route("Cosmo stop")?.toolId)
        assertEquals("robot.move", AgentRouter.route("go forward")?.toolId)
    }

    @Test fun `unsupported and indirect Cozmo commands cannot fall through to LOOI`() {
        for (text in listOf("Cozmo spin", "Cozmo wiggle", "Cozmo go forward then turn left",
            "tell Cozmo to turn right", "please ask Cosmo to look down", "Cozmo and LOOI stop")) {
            assertNotEquals(text, "robot.move", AgentRouter.route(text)?.toolId)
            assertNotEquals(text, "avatar.animate", AgentRouter.route(text)?.toolId)
            assertFalse(text, RobotCommandTarget.permits("robot.move", text))
        }
    }

    @Test fun `model proposals need the current robot name not old context or connection state`() {
        for (text in listOf("LOOI stop", "go forward", "do it", "yes", "stop", "Cozmo and LOOI stop")) {
            assertFalse(text, RobotCommandTarget.permits("cozmo.control", text))
        }
        assertTrue(RobotCommandTarget.permits("cozmo.control", "tell Cozmo to look up"))
        assertTrue(RobotCommandTarget.permits("cozmo.control", "Cosmo, stop!"))
        assertTrue(RobotCommandTarget.permits("robot.move", "LOOI stop"))
        assertTrue(RobotCommandTarget.permits("phone.launch_app", "open Cozmo"))
    }

    @Test fun `a disconnected robot reports failure without substituting another`() = runBlocking {
        val cozmo = CozmoController()
        val before = cozmo.state.value
        assertTrue(RobotMoveTool(RobotManager()).execute(mapOf("action" to "forward")) is ToolResult.Failure)
        assertEquals(before, cozmo.state.value)
        assertTrue(CozmoControlTool(cozmo).execute(mapOf("action" to "forward")) is ToolResult.Failure)
        assertEquals(before, cozmo.state.value)
    }

    @Test fun `both robots can be added separately to desk drawers and dock`() {
        assertTrue("Cozmo" in HomeShelves.TOOLS)
        assertTrue("LOOI" in HomeShelves.TOOLS)
        assertNotEquals(HomeShelves.tool("Cozmo"), HomeShelves.tool("LOOI"))
    }
}
