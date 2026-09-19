package com.example.meowmba

import com.example.meowmba.core.agent.tools.PropInventor
import com.example.meowmba.ui.companion.AvatarWorkshopPrompt
import com.example.meowmba.ui.companion.GeneratedPropForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PropInventorTest {
    @Test fun parsesValidPropJson() {
        val json = """
            {"version":1,"name":"Paper umbrella","description":"A tiny peach paper umbrella","category":"held","use_hint":"Hold above head","parts":[{"shape":"ellipse","x":0.0,"y":-0.05,"width":0.42,"height":0.22,"fill":"#F4C7A5","outline":"#202230","stroke":0.012,"rotation":0,"motion":"still","phase":0},{"shape":"round_rect","x":0.0,"y":0.12,"width":0.04,"height":0.28,"fill":"#8B6A4A","outline":"#202230","stroke":0.01,"rotation":0,"motion":"still","phase":0}],"anchor":{"x":0.0,"y":0.22}}
        """.trimIndent()
        val form = PropInventor.parseResponse(json)
        assertEquals("Paper umbrella", form.name)
        assertEquals(2, form.parts.size)
        assertTrue(form.id.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun rejectsHugePayload() {
        assertThrows(IllegalArgumentException::class.java) {
            PropInventor.parseResponse("x".repeat(GeneratedPropForms.MAX_JSON + 1))
        }
    }

    @Test fun workshopPromptComposesParts() {
        val prompt = AvatarWorkshopPrompt.build(
            subject = "fox barista",
            look = "peach fur",
            style = "cartoon",
            head = "soft ears",
            body = "apron",
            arms = "mitten paws",
            legs = "boots",
            tail = "fluffy",
            freeform = "cheerful",
        )
        assertTrue(prompt.contains("fox barista"))
        assertTrue(prompt.contains("head soft ears"))
        assertTrue(com.example.meowmba.core.agent.runtime.AvatarFormRequest.isDescription(prompt))
        assertTrue(prompt.length <= 720)
    }

    @Test fun composeRequestRespectsCap() {
        val long = "a".repeat(800)
        val composed = PropInventor.composeRequest(long, "held", "detail")
        assertEquals(GeneratedPropForms.MAX_REQUEST, composed.length)
    }
}
