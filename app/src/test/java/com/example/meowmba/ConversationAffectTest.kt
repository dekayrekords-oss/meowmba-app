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
