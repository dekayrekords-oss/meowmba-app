package com.example.meowmba

import com.example.meowmba.core.robot.RobotManager
import org.junit.Assert.*
import org.junit.Test

class BodyStopCommandTest {
    @Test fun `bare and explicit body stop requests are recognized`() {
        listOf("stop", "HALT!", "freeze", "stay", "whoa", "stop moving",
            "stop the robot", "please stop your body", "could you stop the base now?",
            "can you please stop moving right now", "stop the motors, please").forEach {
            assertTrue(it, RobotManager.isBodyStopCommand(it))
        }
    }

    @Test fun `stop words embedded in ordinary conversation never latch the body`() {
        listOf("open stopwatch", "that is unstoppable", "I want to stay in touch",
            "we should stop by the store", "don't stop", "do not stop moving",
            "can you tell me when to stop the robot", "freeze my account").forEach {
            assertFalse(it, RobotManager.isBodyStopCommand(it))
        }
    }

    @Test fun `stopping other app activities does not silently stop body motion`() {
        listOf("stop talking", "stop the music", "stop listening", "stop the timer",
            "stop generating", "please stop the download", "stop recording").forEach {
            assertFalse(it, RobotManager.isBodyStopCommand(it))
        }
    }
}
