package com.example.meowmba

import com.example.meowmba.core.audio.*
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.MusicRequest
import com.example.meowmba.core.agent.tools.CalendarCancellation
import com.example.meowmba.core.car.CrashSceneVerdict
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CompanionBehaviorRegressionTest {
    @Test fun realHistoryNoiseNeverBecomesCommands() {
        for (noise in listOf("(clanking)", "(clicking)", "(gunshot)", "(gun shot)", "(clattering)", "(door slams)", "(upbeat music)", "[keyboard clicks]", "(car engine)", "(dog barks)")) {
            assertEquals(noise, NonSpeechSound.Kind.BACKGROUND, NonSpeechSound.parse(noise)?.kind)
            assertFalse(noise, SpokenText.isFinalTranscriptUsable(noise))
            assertEquals("", SpokenText.preferFinal(noise, "call mom"))
        }
    }
    @Test fun humanSoundsRemainObservationsNotUserRequests() {
        for (sound in listOf("*sigh*", "(sighs)", "(cough)", "[coughing]", "(laughs)", "(sniffle)", "(human sneeze)")) {
            assertEquals(sound, NonSpeechSound.Kind.HUMAN, NonSpeechSound.parse(sound)?.kind)
            InputSoundLevel.clear()
            assertEquals("", SpokenText.preferFinal(sound, "look up"))
            assertTrue(InputSoundLevel.snapshot(InputSoundLevel.monotonicNowMs(), false).humanVocalization)
        }
        assertEquals(NonSpeechSound.Kind.BACKGROUND, NonSpeechSound.parse("(keyboard clicks and cough)")?.kind)
        assertNull(NonSpeechSound.parse("I heard a cough"))
        assertTrue(SpokenText.isFinalTranscriptUsable("I heard a cough"))
    }
    @Test fun energyAloneIsNotHumanEvidence() {
        InputSoundLevel.clear()
        InputSoundLevel.publishSystemRms(10f, 1000)
        assertFalse(InputSoundLevel.snapshot(1000, false).humanVocalization)
        InputSoundLevel.noteHumanVocalization(1000)
        assertTrue(InputSoundLevel.snapshot(1000, false).humanVocalization)
        assertFalse(InputSoundLevel.snapshot(2500, false).humanVocalization)
        assertFalse(InputSoundLevel.snapshot(1001, true).humanVocalization)
    }
    @Test fun emojisAndCoordinatesAreSpeechOnlyRedactions() {
        assertEquals("Hello !", SpeechText.clean("Hello 😊🐱✨!"))
        assertEquals("Exit", SpeechText.clean("Exit 1️⃣"))
        assertEquals("Ready", SpeechText.clean("Ready ⌚⏰〰️▶️‼️"))
        assertEquals("Near your current location.", SpeechText.clean("Near 33.44838, -112.07404."))
        assertFalse(SpeechText.clean("latitude 33.44838, longitude -112.07404").contains("112"))
        assertFalse(SpeechText.clean("latitude 33.4, longitude -112").contains("112"))
        assertEquals("It costs 12.50", SpeechText.clean("It costs 12.50"))
        assertEquals("Meet at 3:30 on 5th Street", SpeechText.clean("Meet at 3:30 on 5th Street"))
    }
    private fun context(driving: Boolean = false, media: Boolean = false, quiet: Boolean = false,
        voice: Boolean = false, mode: SpeechMode = SpeechMode.ALWAYS) =
        SpeechInterruptionPolicy.Context(quiet, false, driving, media, true, voice, mode)
    @Test fun drivingAndMediaNeverAuthorizeRoutineChatter() {
        for (c in listOf(context(driving = true), context(media = true))) {
            assertFalse(SpeechInterruptionPolicy.maySpeak(c, SpeechInterruptionPolicy.Purpose.ROUTINE))
            assertTrue(SpeechInterruptionPolicy.maySpeak(c, SpeechInterruptionPolicy.Purpose.ANNOUNCEMENT))
        }
        assertFalse(SpeechInterruptionPolicy.maySpeak(context(mode = SpeechMode.IMPORTANT), SpeechInterruptionPolicy.Purpose.ROUTINE))
    }
    @Test fun quietStopsEvenUrgentAndReplySpeechUntilAddressed() {
        SpeechInterruptionPolicy.Purpose.entries.forEach { assertFalse(SpeechInterruptionPolicy.maySpeak(context(quiet = true, voice = true), it)) }
        assertTrue(SpeechInterruptionPolicy.maySpeak(context(voice = true, mode = SpeechMode.REPLIES), SpeechInterruptionPolicy.Purpose.REPLY))
        assertFalse(SpeechInterruptionPolicy.maySpeak(context(mode = SpeechMode.REPLIES), SpeechInterruptionPolicy.Purpose.REPLY))
        for (phrase in listOf("be quiet", "Meow, stop talking", "please stop speaking", "shush")) assertTrue(phrase, SpeechInterruptionPolicy.isQuietRequest(phrase))
        assertFalse(SpeechInterruptionPolicy.isQuietRequest("is this a quiet restaurant"))
    }
    @Test fun safeCameraPrefixDoesNotBecomeDangerFromItsExplanation() {
        assertEquals(false, CrashSceneVerdict.parse("SAFE: no crash scene or damaged vehicle is visible"))
        assertEquals(true, CrashSceneVerdict.parse("DANGER: deployed airbag"))
        assertNull(CrashSceneVerdict.parse("I cannot tell if this is a crash scene"))
        assertNull(CrashSceneVerdict.parse(null))
    }
    @Test fun qrAndResearchDoNotMoveRobotNeck() {
        assertNotEquals("robot.move", AgentRouter.route("scan this qr code and look up the manual")?.toolId)
        assertNotEquals("robot.move", AgentRouter.route("look up the manual")?.toolId)
        assertEquals("robot.move", AgentRouter.route("look up")?.toolId)
        assertEquals("cozmo.control", AgentRouter.route("Cozmo look up")?.toolId)
    }
    @Test fun musicRequestsActuallyRouteToPhonePlayback() {
        assertEquals("phone.play_music", AgentRouter.route("play music")?.toolId)
        assertEquals("phone.play_music", AgentRouter.route("can you please play music?")?.toolId)
        assertEquals("", AgentRouter.route("play music")?.args?.get("query"))
        assertEquals(MusicRequest.Request("jazz", "Spotify"), MusicRequest.parse("Meow, play jazz on Spotify"))
        assertNull(MusicRequest.parse("play a game"))
        assertNull(MusicRequest.parse("play with me"))
    }
    @Test fun appSuggestionsFillDraftFromRealLabels() {
        val apps = listOf(DrawerApp("Spotify", "spotify", "spotify music"), DrawerApp("Settings", "settings", "settings"))
        assertEquals(listOf("open Spotify"), OpenAppCompletion.suggest("open spot", apps))
        assertEquals(listOf("open Spotify"), OpenAppCompletion.suggest("open sptfy", apps))
        assertTrue(OpenAppCompletion.suggest("please explain open", apps).isEmpty())
    }
    @Test fun cancellationSelectsDateAndTitleWithoutGuessing() {
        val today = LocalDate.of(2026, 9, 25)
        assertEquals(CalendarCancellation.Request("", today.plusDays(1)), CalendarCancellation.parse("the event tomorrow is canceled", today))
        val dentist = CalendarCancellation.parse("cancel my dentist appointment tomorrow", today)!!
        assertTrue(CalendarCancellation.matchesTitle(dentist, "Dentist appointment"))
        assertFalse(CalendarCancellation.matchesTitle(dentist, "Hair appointment"))
        assertEquals("calendar.cancel_event", AgentRouter.route("the event tomorrow is canceled")?.toolId)
        for (notCancel in listOf("don't cancel my dentist tomorrow", "if the event tomorrow is canceled", "is my event tomorrow canceled?", "cancel all events tomorrow", "cancel my event next Monday"))
            assertNull(notCancel, CalendarCancellation.parse(notCancel, today))
    }
}
