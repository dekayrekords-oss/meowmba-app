package com.example.meowmba

import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.audio.VoiceIntentGuard
import com.example.meowmba.core.setup.PlannerReadiness
import org.junit.Test
import org.junit.Assert.*

class PhonePlanningUpgradeTest {
    @Test fun confirmationIsSingleUseBoundToAppContentAndExpiry() {
        val ticket = PhoneConfirmation("facebook", "meow ai test", 100)
        assertTrue(ticket.consume("facebook", "meow ai test", 200))
        assertFalse(ticket.consume("facebook", "meow ai test", 201))
        assertFalse(PhoneConfirmation("facebook", "draft", 100).consume("mail", "draft", 200))
        assertFalse(PhoneConfirmation("facebook", "draft", 100).consume("facebook", "changed", 200))
        assertFalse(PhoneConfirmation("facebook", "draft", 100).consume("facebook", "draft", 120101))
        assertTrue(PhoneActionSafety.canConfirmSocial("Post"))
        assertTrue(PhoneActionSafety.canConfirmSocial("Reply to a comment"))
        assertFalse(PhoneActionSafety.canConfirmSocial("Delete post"))
        assertFalse(PhoneActionSafety.canConfirmSocial("Send payment"))
    }
    @Test fun explicitDraftIsTypedExactlyButNeverPublishedOrOverwritten() {
        val goal = "open Facebook and make a post saying meow ai test"
        val empty = "New post\n1. Next [tap]\n2. What's on your mind? [edit]"
        val action = PhoneGoalHints.next(goal, "com.facebook.katana", empty)!!
        assertEquals("type", action.type); assertEquals("meow ai test", action.value)
        assertEquals("done", PhoneGoalHints.next(goal, "com.facebook.katana", empty.replace("What's on your mind?", "meow ai test"))?.type)
        assertEquals("blocked", PhoneGoalHints.next(goal, "com.facebook.katana", empty.replace("What's on your mind?", "my existing draft"))?.type)
    }
    @Test fun numericTapMeansScreenIndexNotAReportedLikeCount() {
        val action = PhoneAgentLoop.parseAction("{\"action\":\"tap\",\"text\":\"2\"}")!!
        assertEquals("tap_index", action.type); assertEquals(2, action.index)
        assertTrue(PhoneActionSafety.needsHandoff("Like"))
        assertTrue(PhoneActionSafety.needsHandoff("2"))
    }
    @Test fun emptyComposerHintRequiresFacebookAndVisibleEvidence() {
        val goal = "open fact book and start a post"
        assertEquals("tap", PhoneGoalHints.next(goal, "com.facebook.katana", "What's on your mind?")?.type)
        assertNull(PhoneGoalHints.next(goal, "com.example.other", "What's on your mind?"))
        assertNull(PhoneGoalHints.next("write a post about cats", "com.facebook.katana", "What's on your mind?"))
        assertEquals("blocked", PhoneGoalHints.next(goal, "com.facebook.katana", "Create post")?.type)
        assertEquals("done", PhoneGoalHints.next(goal, "com.facebook.katana", "Create post\nAudience\nWhat's on your mind? [edit]")?.type)
        assertEquals("done", PhoneGoalHints.next(goal, "com.facebook.katana", "New post\n1. Next [tap]\n2. What's on your mind? [edit]\nPublic")?.type)
        assertNotEquals("done", PhoneGoalHints.next(goal, "com.facebook.katana", "New post\nNext\nWhat's on your mind? [tap]")?.type)
    }
    @Test fun composerRecipeRecoversFromRestoredScreensWithoutReplying() {
        val goal = "open Facebook and start a post"
        for (control in listOf("Close", "Back", "Home, tab 1 of 6")) {
            assertEquals(control, PhoneGoalHints.next(goal, "com.facebook.katana", "Interactive:\n1. $control [tap]\n2. Reply to a comment [tap]")?.text)
        }
        assertEquals("blocked", PhoneGoalHints.next(goal, "com.facebook.katana", "1. Reply to a comment [tap]")?.type)
        assertTrue(PhoneActionSafety.needsHandoff("Reply to Phil's comment, button"))
        assertTrue(PhoneActionSafety.needsHandoff("Share button. Double tap to share the post."))
    }
    @Test fun directlySelectedPlannerDoesNotRequireOmniRoute() = kotlinx.coroutines.runBlocking {
        val model = object : ModelProvider {
            override val name = "Direct test planner"
            override suspend fun generate(prompt: String) = ModelResponse.Complete("{\"action\":\"tap_index\",\"index\":1}")
            override fun generateStreaming(prompt: String) = kotlinx.coroutines.flow.emptyFlow<ModelResponse>()
        }
        assertTrue(PlannerReadiness.navigationPasses(PhoneAgentLoop.generateAction(model, "next step")))
    }
    private val apps = listOf(InstalledApps.Entry("Facebook", "com.facebook.katana"), InstalledApps.Entry("Messenger", "com.facebook.orca"), InstalledApps.Entry("Gmail", "com.google.android.gm"))
    @Test fun compoundKeepsEveryStep() {
        for (goal in listOf("open facebook and start a post", "open fact book then write a post", "launch Facebook to create a post", "open Facebook and publish my draft", "open Facebook and share this post", "open Messenger and reply to this message")) {
            val route = AgentRouter.route(goal)!!
            assertEquals("phone.run_goal", route.toolId)
            assertEquals(goal, route.args["goal"])
            assertEquals("phone_control", route.agentId)
        }
    }
    @Test fun launchOnlyStillUsesFastRoute() { assertEquals("phone.launch_app", AgentRouter.route("open facebook")!!.toolId) }
    @Test fun socialDraftRequestsKeepTheFullGoalWithoutGrantingPublishPermission() {
        for (goal in listOf("make a post on linked it about the app for promotion", "Meow, draft a post on LinkedIn", "can you write a post for reddit", "please create a post on Instagram about cats")) {
            val intent = AgentRouter.route(goal)!!
            assertEquals(goal, "phone.run_goal", intent.toolId)
            assertEquals(goal, intent.args["goal"])
            assertFalse(PhoneGoalLimits.maySubmit(goal))
        }
        assertNull(AgentRouter.route("I saw a post on LinkedIn"))
        assertNull(AgentRouter.route("do not make a post on LinkedIn"))
    }
    @Test fun linkedItAliasRequiresAnInstalledLinkedInApp() {
        val linkedIn = InstalledApps.Entry("LinkedIn", "com.linkedin.android")
        assertEquals(linkedIn, InstalledApps.match(apps + linkedIn, "linked it"))
        assertNull(InstalledApps.match(apps, "linked it"))
    }
    @Test fun pronounUsesContextPlannerNotAnAppNamedIt() { assertNull(AgentRouter.route("open it")) }
    @Test fun repairsSpeechAgainstRealInventory() {
        for (name in listOf("fact book", "face book", "the facebook app", "facebok", "faceboook")) assertEquals(name, "Facebook", InstalledApps.match(apps, name)?.label)
        assertNull(InstalledApps.match(apps.filterNot { it.label == "Facebook" }, "fact book"))
    }
    @Test fun shortOrAmbiguousNamesDoNotGuess() {
        assertNull(InstalledApps.match(listOf(InstalledApps.Entry("Facebook Lite", "lite"), InstalledApps.Entry("Facebook Work", "work")), "facebook"))
        assertNull(InstalledApps.match(apps, "fa"))
        assertNull(InstalledApps.match(listOf(InstalledApps.Entry("Testa", "a"), InstalledApps.Entry("Testb", "b")), "testc"))
    }
    @Test fun restoredContextSurvivesANewLiveTurn() {
        val c = TurnContext(); c.scope(1); c.add("User", "open it")
        val text = c.historyWith(listOf("User" to "Facebook please", "Meow" to "Shall I open Facebook?"))!!
        assertTrue(text.contains("Facebook")); assertTrue(text.endsWith("User: open it"))
    }
    @Test fun contextOverlapIsNotDuplicated() {
        val c = TurnContext(); c.scope(1); c.add("User", "hello"); c.add("Meow", "hi")
        assertEquals("User: hello\nAssistant: hi", c.historyWith(listOf("User" to "hello")))
        c.scope(2); assertNull(c.history())
    }
    @Test fun likelyAdRequiresClarificationButDiscussionAndWakePhraseDoNot() {
        assertTrue(VoiceIntentGuard.needsClarification("Call now for this limited time offer"))
        assertFalse(VoiceIntentGuard.needsClarification("Meow explain call now and limited time offer"))
        assertFalse(VoiceIntentGuard.needsClarification("The TV says call now for a limited time offer"))
        assertFalse(VoiceIntentGuard.needsClarification("just chilling with the cats"))
        assertFalse(VoiceIntentGuard.needsClarification("open fact book"))
    }
    @Test fun finalActionsAreHandedBackButComposersAreAllowed() {
        for (label in listOf("Post", "Send", "Buy now", "Delete photo", "Confirm", "")) assertTrue(label, PhoneActionSafety.needsHandoff(label))
        for (label in listOf("What's on your mind?", "Create post", "Search", "Notifications")) assertFalse(label, PhoneActionSafety.needsHandoff(label))
    }
    @Test fun readinessRejectsEmptyProseAndFalseCompletion() {
        assertTrue(PlannerReadiness.navigationPasses("{\"action\":\"tap_index\",\"index\":1}"))
        assertFalse(PlannerReadiness.navigationPasses("{\"action\":\"done\",\"summary\":\"opened\"}"))
        assertTrue(PlannerReadiness.contextPasses("{\"action\":\"tool\",\"tool\":\"files.read\",\"args\":{\"path\":\"agenda.md\"}}"))
        assertFalse(PlannerReadiness.contextPasses("OK"))
        assertFalse(PlannerReadiness.contextPasses("{\"action\":\"tool\",\"tool\":\"files.read\",\"args\":{\"path\":\"budget.md\"}}"))
    }
}
