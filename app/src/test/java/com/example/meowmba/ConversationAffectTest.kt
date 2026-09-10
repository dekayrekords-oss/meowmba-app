package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test

class ConversationAffectTest {
    @Test fun apologyUncertaintyAndFailureAreNotHappyReplies() {
        assertEquals(CompanionEmotion.APOLOGETIC, ConversationAffect.reply("I'm sorry, that failed.", CompanionEmotion.HAPPY))
        assertEquals(CompanionEmotion.CONCERNED, ConversationAffect.reply("I couldn't open the file.", CompanionEmotion.PROUD))
        assertEquals(CompanionEmotion.CONFUSED, ConversationAffect.reply("I'm not sure which one you meant.", CompanionEmotion.HAPPY))
        assertEquals(CompanionEmotion.CONCERNED, ConversationAffect.reply("Here are the steps.", CompanionEmotion.CONCERNED))
        assertEquals(CompanionEmotion.HAPPY, ConversationAffect.reply("I can't wait to show you.", CompanionEmotion.HAPPY))
        assertEquals(CompanionEmotion.HAPPY, ConversationAffect.reply("I couldn't agree more.", CompanionEmotion.HAPPY))
    }
    @Test fun aReplyOwnAffectCarriesSignedValenceIntoTheDrives() {
        // This is what the continuous drive layer consumes: the companion's own
        // reply is the honest source of how it currently feels.
        val apology = ConversationAffect.reply("Sorry, I couldn't do that.", CompanionEmotion.HAPPY)
        assertEquals(CompanionEmotion.APOLOGETIC, apology)
        assertTrue("an apology must read as negative", ConversationAffect.valence(apology) < 0f)

        val proud = ConversationAffect.reply("All done, that worked.", CompanionEmotion.CURIOUS)
        assertTrue("a success must not read as negative", ConversationAffect.valence(proud) >= 0f)
    }

    @Test fun valenceIsBoundedAndAFailureNeverPlungesTheCharacter() {
        CompanionEmotion.values().forEach { e ->
            val v = ConversationAffect.valence(e)
            assertTrue("${e.name} valence out of range: $v", v in -1f..1f)
        }
        assertTrue(ConversationAffect.valence(CompanionEmotion.AFFECTIONATE) >
            ConversationAffect.valence(CompanionEmotion.ATTENTIVE))
        assertTrue(ConversationAffect.valence(CompanionEmotion.HAPPY) > 0f)
        // A failed operation should take the edge off, not crash the affect:
        // this biases a simulation over seconds, it is not a mood swing.
        assertTrue("a failure must not plunge the companion",
            ConversationAffect.valence(CompanionEmotion.CONCERNED) > -0.75f)
    }

    @Test fun literalPraiseIsDistinctFromEmbeddedWordsOrNegation() {
        assertEquals(CompanionEmotion.PROUD, ConversationAffect.user("Thank you!"))
        assertEquals(CompanionEmotion.AFFECTIONATE, ConversationAffect.user("I love you"))
        assertEquals(CompanionEmotion.CURIOUS, ConversationAffect.user("Not a great job"))
        assertEquals(CompanionEmotion.CURIOUS, ConversationAffect.user("Send a thank you email"))
        assertEquals(CompanionEmotion.CONCERNED, ConversationAffect.user("I feel worried about tomorrow"))
        assertEquals(CompanionEmotion.CONCERNED, ConversationAffect.user("I'm sad"))
        assertEquals(CompanionEmotion.CONCERNED, ConversationAffect.user("I’m scared"))
    }
}
