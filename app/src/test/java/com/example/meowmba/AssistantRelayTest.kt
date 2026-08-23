package com.example.meowmba

import com.example.meowmba.core.work.AssistantRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantRelayTest {

    @Test
    fun `ask alexa to does a thing`() {
        val r = AssistantRelay.parse("ask alexa to turn off the lights")!!
        assertEquals(AssistantRelay.Target.ALEXA, r.target)
        assertEquals("turn off the lights", r.query)
    }

    @Test
    fun `tell chatgpt and gemini and claude`() {
        assertEquals(
            AssistantRelay.Target.CHATGPT,
            AssistantRelay.parse("tell chatgpt to write a haiku")!!.target,
        )
        assertEquals(
            AssistantRelay.Target.GEMINI,
            AssistantRelay.parse("ask gemini what a capacitor is")!!.target,
        )
        assertEquals(
            "explain rust",
            AssistantRelay.parse("ask claude to explain rust")!!.query,
        )
    }

    @Test
    fun `ask meow itself is not a relay`() {
        assertNull(AssistantRelay.parse("ask meow to set a timer"))
        assertNull(AssistantRelay.parse("open spotify"))
    }
}
