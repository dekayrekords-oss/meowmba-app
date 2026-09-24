package com.example.meowmba

import com.example.meowmba.core.diagnostics.BugReportIntake
import com.example.meowmba.core.diagnostics.PossibleBugReports
import com.example.meowmba.core.nav.PlaceVoiceIntent
import org.junit.Assert.*
import org.junit.Test

class BugIntakeAndPlacesTest {
    @Test fun explicitReportCapturesDescriptionInsteadOfAskingTheModel() {
        val intake = BugReportIntake()
        assertEquals(BugReportIntake.Action.Ask, intake.accept("I said I wanted to report a bug.", 100))
        assertEquals(BugReportIntake.Action.Save("The phone gets really hot"), intake.accept("The phone gets really hot", 200))
        assertNull(intake.accept("hello", 300))
    }
    @Test fun inlineReportAndIndependentFailuresAreSaved() {
        val intake = BugReportIntake()
        assertEquals(BugReportIntake.Action.Save("wrong color"), intake.accept("report a bug: wrong color", 100))
        for (text in listOf("constantly overheating", "many voice recognition errors", "unable to report bugs correctly", "traffic information is dead"))
            assertNotNull(text, intake.accept(text, 200))
        assertNull(PossibleBugReports.extract("what if the phone is overheating?"))
    }
    @Test fun cancellationExpiryAndNavigationEscapeIntake() {
        val intake = BugReportIntake()
        intake.accept("report a bug", 100)
        assertEquals(BugReportIntake.Action.Cancel, intake.accept("never mind", 200))
        intake.accept("report a bug", 300)
        assertNull(intake.accept("where are we?", 400))
        assertNull(intake.accept("hi there", 500))
        intake.accept("report a bug", 600)
        assertNull(intake.accept("a blue circle", 121_000))
    }
    @Test fun placeCommandsMatchTheUsersNaturalPhrases() {
        assertEquals(PlaceVoiceIntent.Heading("home"), PlaceVoiceIntent.parse("I'm heading home"))
        assertEquals(PlaceVoiceIntent.Heading("work"), PlaceVoiceIntent.parse("I'm going to work right now"))
        assertEquals(PlaceVoiceIntent.Heading("work"), PlaceVoiceIntent.parse("on my way to the office"))
        assertEquals(PlaceVoiceIntent.Arrived("home"), PlaceVoiceIntent.parse("I'm home now"))
        assertEquals(PlaceVoiceIntent.Arrived("work"), PlaceVoiceIntent.parse("just got to work"))
        assertEquals(null, PlaceVoiceIntent.parse("I'm going to sleep"))
        assertEquals(null, PlaceVoiceIntent.parse("I'm going to call Dana"))
        assertEquals(PlaceVoiceIntent.Destination, PlaceVoiceIntent.parse("where are we going"))
        assertEquals(PlaceVoiceIntent.Current, PlaceVoiceIntent.parse("Where are we?"))
        assertEquals(PlaceVoiceIntent.Destination, PlaceVoiceIntent.parse("Do you know where we’re going?"))
        assertEquals(PlaceVoiceIntent.AskName, PlaceVoiceIntent.parse("Remember here"))
        assertEquals(PlaceVoiceIntent.Save("fishing spot"), PlaceVoiceIntent.parse("Remember here as my fishing spot"))
        assertEquals(PlaceVoiceIntent.Go("fishing spot"), PlaceVoiceIntent.parse("Ok, let's go to \"fishing spot\"."))
        assertEquals("fishing spot", PlaceVoiceIntent.cleanName("call it \"fishing spot\""))
        assertFalse(PlaceVoiceIntent.isNameReply("where are we"))
        assertNull(PlaceVoiceIntent.parse("remember that I like fishing"))
    }
}
