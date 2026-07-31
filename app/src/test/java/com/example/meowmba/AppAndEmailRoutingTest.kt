package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reported failure: "open gmail and write a message that says hi to
 * wlgilmore@gmail.com" came back as
 *
 *     No installed app matches "gmail and write a message that says hi to
 *     wlgilmore@gmail.com"
 *
 * Root cause: the extractor after "open " returned the entire remainder of the sentence,
 * so every trailing clause became part of the app name. These pin both halves of the fix
 * — the name is bounded, and a request that names an address and a message is a compose,
 * not an app launch.
 */
class AppAndEmailRoutingTest {

    @Test
    fun `open plus compose goes to the email composer, not the app launcher`() {
        val intent = AgentRouter.route(
            "open gmail and write a message that says hi to wlgilmore@gmail.com"
        )!!
        assertEquals("email.send", intent.toolId)
        assertEquals("wlgilmore@gmail.com", intent.args["to"])
        assertEquals("hi", intent.args["body"])
    }

    @Test
    fun `the app name stops at a conjunction`() {
        val intent = AgentRouter.route("open spotify and play something")!!
        assertEquals("phone.launch_app", intent.toolId)
        assertEquals("spotify", intent.args["name"])
    }

    @Test
    fun `politeness is not part of the app name`() {
        listOf(
            "open gmail please" to "gmail",
            "open the app calculator for me" to "calculator",
            "launch spotify, then pause it" to "spotify",
        ).forEach { (said, expected) ->
            val intent = AgentRouter.route(said)!!
            assertEquals(said, "phone.launch_app", intent.toolId)
            assertEquals(said, expected, intent.args["name"])
        }
    }

    @Test
    fun `a plain open still launches the app`() {
        val intent = AgentRouter.route("open gmail")!!
        assertEquals("phone.launch_app", intent.toolId)
        assertEquals("gmail", intent.args["name"])
    }

    @Test
    fun `email recipient is bounded and the message is captured`() {
        val intent = AgentRouter.route(
            "send an email to bob@example.com saying the build is green"
        )!!
        assertEquals("email.send", intent.toolId)
        assertEquals("bob@example.com", intent.args["to"])
        assertEquals("the build is green", intent.args["body"])
    }

    @Test
    fun `an address with no message still composes`() {
        val intent = AgentRouter.route("email wlgilmore@gmail.com")!!
        assertEquals("email.send", intent.toolId)
        assertEquals("wlgilmore@gmail.com", intent.args["to"])
    }

    @Test
    fun `a draft request saves a draft instead of opening the composer`() {
        val intent = AgentRouter.route(
            "draft an email to bob@example.com saying the build is green"
        )!!
        assertEquals("email.draft", intent.toolId)
        assertEquals("bob@example.com", intent.args["to"])
        assertEquals("the build is green", intent.args["body"])
    }

    @Test
    fun `the draft recipient stops at a conjunction`() {
        val intent = AgentRouter.route("draft email to bob and then relax")!!
        assertEquals("email.draft", intent.toolId)
        assertEquals("bob", intent.args["to"])
    }

    @Test
    fun `the subject is captured from the middle of the sentence`() {
        val intent = AgentRouter.route(
            "send an email to bob@example.com with the subject build status " +
                "saying the build is green"
        )!!
        assertEquals("email.send", intent.toolId)
        assertEquals("build status", intent.args["subject"])
        assertEquals("the build is green", intent.args["body"])
    }

    @Test
    fun `a message about a subject is not a subject`() {
        val intent = AgentRouter.route(
            "email bob@example.com saying the subject is green"
        )!!
        assertEquals("", intent.args["subject"])
        assertEquals("the subject is green", intent.args["body"])
    }

    /** The specific forms that must keep winning over the bare "open " prefix. */
    @Test
    fun `more specific open forms are unaffected`() {
        assertEquals("file.read", AgentRouter.route("open the file notes.txt")!!.toolId)
        assertEquals("phone.open_settings", AgentRouter.route("open settings")!!.toolId)
        assertEquals(
            "phone.open_url",
            AgentRouter.route("open the website example.com")!!.toolId,
        )
    }

    @Test
    fun `ordinary conversation is still not routed`() {
        listOf(
            "how are you",
            "what's the weather",
            "open up about your feelings",
        ).forEach { assertNull("must not route: $it", AgentRouter.route(it)) }
    }
}

/**
 * Measured on device: Gmail's external compose activity takes the recipient from the
 * mailto: URI and ignores EXTRA_TEXT, so a body passed only as an intent extra arrives
 * as an empty message. The body has to be in the URI.
 */
class MailtoUriTest {

    @org.junit.Test
    fun `body and subject ride in the query string`() {
        assertEquals(
            "mailto:wlgilmore@gmail.com?body=hi",
            com.example.meowmba.core.agent.tools.EmailSendTool.mailtoUri(
                "wlgilmore@gmail.com", "", "hi",
            ),
        )
    }

    @org.junit.Test
    fun `spaces are percent-encoded, never plus`() {
        val uri = com.example.meowmba.core.agent.tools.EmailSendTool.mailtoUri(
            "bob@example.com", "build status", "the build is green",
        )
        assertEquals(
            "mailto:bob@example.com?subject=build%20status&body=the%20build%20is%20green",
            uri,
        )
        org.junit.Assert.assertFalse("a + would arrive as a literal plus", uri.contains("+"))
    }

    @org.junit.Test
    fun `an empty body leaves the query off entirely`() {
        assertEquals(
            "mailto:bob@example.com",
            com.example.meowmba.core.agent.tools.EmailSendTool.mailtoUri("bob@example.com", "", ""),
        )
    }
}
