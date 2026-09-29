package com.example.meowmba

import com.example.meowmba.core.audio.VoiceTurnGate
import com.example.meowmba.core.audio.VoiceTurnGate.Decision.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lines taken from the Fold's chat on 2026-09-29, all sent to the LLM before this gate. */
class VoiceTurnGateTest {
    private fun ambient(t: String) = VoiceTurnGate.decide(t, addressed = false, inConversation = false, meowAskedQuestion = false)
    private fun followUp(t: String, asked: Boolean = false) = VoiceTurnGate.decide(t, addressed = false, inConversation = true, meowAskedQuestion = asked)

    @Test fun clickTagsCutOffMidTagAreNoise() {
        assertEquals(DROP_NOISE, ambient("(clicking) (clicking) (clicking) ("))
        assertEquals(DROP_NOISE, ambient("(clicking) (clicking) (clicking) (clicking) (clicking) (cli"))
        assertEquals(DROP_NOISE, followUp("[typing]"))
        assertEquals(DROP_NOISE, ambient("*clicking*"))
    }

    @Test fun reactionsDoNotCallTheModel() {
        for (t in listOf("Oh no.", "Ooh.", "Great.", "Good.", "No.", "No, no.", "Okay.", "Thank you.", "Nice dude. Really, yeah. Good."))
            assertEquals(t, DROP_REACTION, followUp(t))
        assertEquals(DROP_REACTION, ambient("Thank you."))
    }

    @Test fun aReactionAnsweringMeowsQuestionIsKept() {
        assertEquals(ACCEPT, followUp("No.", asked = true))
        assertEquals(ACCEPT, followUp("Okay.", asked = true))
    }

    @Test fun overheardFragmentsAreIgnoredOutsideAConversation() {
        for (t in listOf("mail to their physical mail.", "other will lead it by at least", "Two more",
                "Oh, I can't.", "Oh the cat was almost using its cat wheel", "I'm going to get me a double warning"))
            assertEquals(t, DROP_AMBIENT, ambient(t))
    }

    @Test fun realRequestsStillGetThrough() {
        for (t in listOf("Play the last video on YouTube.", "What time does McDonald's first breakfast?",
                "Are you listening?", "You should already know what the weather is if I tell you that it's cold.",
                "Can you set a timer for ten minutes", "I need to text Sam back tonight"))
            assertEquals(t, ACCEPT, ambient(t))
        assertEquals(ACCEPT, VoiceTurnGate.decide("I'm back home", addressed = true, inConversation = false, meowAskedQuestion = false))
        assertEquals(ACCEPT, followUp("I said it feels cold outside"))
    }

    @Test fun speechAboutNoiseIsNotNoise() {
        assertEquals(ACCEPT, ambient("What is that clicking noise?"))
    }
}
