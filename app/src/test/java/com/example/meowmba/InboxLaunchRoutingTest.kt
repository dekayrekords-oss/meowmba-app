package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import org.junit.Assert.*
import org.junit.Test

class InboxLaunchRoutingTest {
    @Test fun `mail checks invoke an actual launcher and never claim inbox contents`() {
        for (text in listOf("check my email", "read my emails", "show my inbox",
            "can you check my email", "hey meow check my email",
            "Check my emile and tell me what the last piece of male was")) {
            val route = AgentRouter.route(text)
            assertNotNull(text, route)
            assertEquals(text, "phone.launch_app", route!!.toolId)
            assertEquals("gmail", route.args["name"])
            assertEquals("inbox", route.args["purpose"])
        }
    }

    @Test fun `questions about email do not launch apps`() {
        assertNull(AgentRouter.route("what is email"))
        assertNull(AgentRouter.route("how do I check my email"))
    }

    @Test fun `compose email still uses the approved composer flow`() {
        assertEquals("email.send", AgentRouter.route("open gmail and write a message saying hi to test@example.com")!!.toolId)
    }
}
