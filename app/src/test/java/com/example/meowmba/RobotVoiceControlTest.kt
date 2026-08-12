package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.robot.RobotManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Speaking to the body.
 *
 * "Turn off the headlights", "shake your head", "spin in a circle" must reach
 * the motors deterministically — a physical action should not wait on a model
 * call, and must never quietly become a sentence about moving instead.
 */
class RobotVoiceControlTest {

    private fun route(text: String) = AgentRouter.route(text)

    // ── the user's own examples ─────────────────────────────────────────────

    @Test
    fun `the three requested commands all reach the motors`() {
        for ((spoken, expected) in listOf(
            "turn off the headlights" to "headlight_off",
            "spin in a circle" to "spin",
        )) {
            val intent = route(spoken)
            assertNotNull("\"$spoken\" must be a command", intent)
            assertEquals("\"$spoken\"", "robot.move", intent!!.toolId)
            assertEquals("\"$spoken\"", expected, intent.args["action"])
        }
    }

    @Test
    fun `turn the headlights off is a robot command with a real owner`() {
        val intent = route("turn the headlights off")
        assertNotNull(intent)
        assertEquals("robot", intent!!.agentId)
        assertEquals("robot.move", intent.toolId)
        assertEquals("headlight_off", intent.args["action"])
        val owner = AgentRegistry.byId("robot")
        assertNotNull("submit used to say Unknown agent 'robot'", owner)
        assertTrue("robot.move" in owner!!.allowedTools)
        assertEquals(owner, AgentRegistry.ownerOf("robot.move"))
    }

    @Test
    fun `headlight on and off are never confused`() {
        assertEquals("headlight_off", route("turn off the lights")?.args?.get("action"))
        assertEquals("headlight_on", route("turn on the lights")?.args?.get("action"))
        assertEquals("headlight_off", route("headlight off")?.args?.get("action"))
        assertEquals("headlight_on", route("turn on the headlight")?.args?.get("action"))
    }

    @Test
    fun `expressive motions stay with the face, which drives the servos too`() {
        // "shake your head" reaches avatar.animate, not robot.move — and the
        // physical head still moves, because FaceMotorMap maps the played clip
        // onto the servos. One phrase, one owner, both effects.
        assertEquals("avatar.animate", route("shake your head")?.toolId)
        assertEquals("avatar.animate", route("wiggle your ears")?.toolId)
    }

    @Test
    fun `the whole movement vocabulary is reachable by voice`() {
        val expected = mapOf(
            "go forward" to "forward",
            "back up" to "back",
            "turn left" to "left",
            "turn right" to "right",
            "spin around" to "spin",
            "stop" to "stop",
            "look up" to "look_up",
            "look down" to "look_down",
        )
        for ((spoken, action) in expected) {
            assertEquals("\"$spoken\"", action, route(spoken)?.args?.get("action"))
        }
    }

    @Test
    fun `every motion the hardware has can be spoken`() {
        // A motion nobody can ask for may as well not exist.
        for (motion in RobotManager.Motion.entries) {
            val phrase = motion.phrases.first()
            assertEquals(
                "no phrase resolves to ${motion.name}",
                motion,
                RobotManager.Motion.fromPhrase(phrase),
            )
        }
    }

    @Test
    fun `the tool can still command face-owned motions explicitly`() {
        // The router hands those phrases to the avatar, but the model must
        // still be able to ask the body for a nod by name.
        assertEquals(
            RobotManager.Motion.NOD,
            RobotManager.Motion.fromPhrase("nod", includeFaceOwned = true),
        )
        assertNull(RobotManager.Motion.fromPhrase("nod", includeFaceOwned = false))
    }

    // ── phrase matching ─────────────────────────────────────────────────────

    @Test
    fun `the longest matching phrase wins`() {
        // "turn off the lights" contains "lights on"? No — but it does contain
        // shorter fragments, and the longest match must decide.
        assertEquals(
            RobotManager.Motion.HEADLIGHT_OFF,
            RobotManager.Motion.fromPhrase("please turn off the headlight now"),
        )
        assertEquals(
            RobotManager.Motion.SPIN,
            RobotManager.Motion.fromPhrase("spin in a circle"),
        )
    }

    @Test
    fun `unknown motions match nothing rather than guessing`() {
        assertNull(RobotManager.Motion.fromPhrase("make me a sandwich"))
        assertNull(RobotManager.Motion.fromPhrase(""))
    }

    // ── conversation must not drive the robot ───────────────────────────────

    @Test
    fun `talking about directions is not a command`() {
        // The dangerous false positive: a sentence that merely mentions a
        // direction must not move a physical robot.
        assertNull(route("i need to turn left at the traffic lights downtown"))
        assertNull(route("what did you do when the car in front stopped suddenly"))
        assertNull(route("which way does the earth spin on its axis exactly"))
    }

    @Test
    fun `questions about motion are answered, not performed`() {
        assertNull(route("why do you spin around when you are excited"))
        assertNull(route("where should i look for the manual"))
    }

    @Test
    fun `a short imperative is still a command`() {
        assertEquals("robot.move", route("stop")?.toolId)
        assertEquals("robot.move", route("spin")?.toolId)
    }

    // ── no regressions in neighbouring routes ───────────────────────────────

    @Test
    fun `alarms and app launches are unaffected`() {
        assertEquals("phone.set_alarm", route("set an alarm for 7am")?.toolId)
        assertEquals("phone.launch_app", route("open spotify")?.toolId)
    }

    @Test
    fun `turn on the flashlight is not the robot headlight`() {
        // The phone torch and the robot's headlight are different things.
        val intent = route("turn on the flashlight")
        assertTrue(
            "flashlight should not become a robot motion, got ${intent?.toolId}",
            intent?.toolId != "robot.move",
        )
    }
}
