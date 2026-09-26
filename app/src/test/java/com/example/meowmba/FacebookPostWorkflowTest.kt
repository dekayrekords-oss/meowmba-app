package com.example.meowmba

import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.work.BrowserRequestParser
import org.junit.Assert.*
import org.junit.Test

class FacebookPostWorkflowTest {
    private fun frame() = PhoneObservation(FacebookPageSelection.PACKAGE, 1, "picker", listOf(
        PhoneControl(1, "Studio Page", "fb:id/a", "tap"),
        PhoneControl(2, "Band Page", "fb:id/b", "tap"),
        PhoneControl(3, "Create a Page", "fb:id/c", "tap"),
    ), listOf("Switch profile"), "")
    private val choose = PhoneAction("choose_facebook_page", value = "1,2")
    @Test fun routesActualPostsBeforeHtmlEvenWhenTheyMentionPageOrWebsite() {
        for (goal in listOf("make a Facebook post", "Meow, make a Facebook post for my page", "can you create a post on Facebook about my website", "write me a Facebook post", "open Facebook and make a post")) {
            assertTrue(goal, SocialPostRequest.facebook(goal))
            assertEquals(goal, "phone.run_goal", AgentRouter.route(goal)?.toolId)
            assertNull(goal, BrowserRequestParser.parse(goal))
        }
    }
    @Test fun platformMentionInsideContentDoesNotChangeDestination() {
        assertFalse(SocialPostRequest.facebook("make a Reddit post about Facebook"))
        assertFalse(SocialPostRequest.facebook("write a post on Instagram about Facebook"))
        assertTrue(SocialPostRequest.facebook("make a Facebook post about Reddit"))
    }
    @Test fun questionsMentionsNegationsAndMockupsAreNotPostCommands() {
        for (text in listOf("how do I make a Facebook post?", "do not make a Facebook post", "I saw a Facebook post", "make a website about Facebook posts", "design an HTML Facebook post mockup"))
            assertFalse(text, SocialPostRequest.matches(text))
    }
    @Test fun choicesAreCopiedFromAccountControlsNotModelLabels() {
        assertEquals(listOf("Studio Page", "Band Page"), FacebookPageSelection().candidates(choose, frame()).map { it.label })
        assertTrue(FacebookPageSelection().candidates(choose.copy(value = "99"), frame()).isEmpty())
        assertTrue(FacebookPageSelection().candidates(choose.copy(value = "1,1"), frame()).isEmpty())
        assertTrue(FacebookPageSelection().candidates(choose.copy(value = "Invented Page"), frame()).isEmpty())
    }
    @Test fun feedSearchResultsOtherAppsAndAccountActionsCannotBecomeChoices() {
        val policy = FacebookPageSelection()
        assertTrue(policy.candidates(choose, frame().copy(pkg = "browser")).isEmpty())
        assertTrue(policy.candidates(choose, frame().copy(visibleText = listOf("Search results"))).isEmpty())
        assertTrue(policy.candidates(choose.copy(value = "3"), frame()).isEmpty())
        assertTrue(policy.candidates(choose, frame().copy(controls = frame().controls.map { it.copy(protected = true) })).isEmpty())
    }
    @Test fun noTypingOrCompletionBeforeUserSelects() {
        val policy = FacebookPageSelection()
        assertEquals("blocked", policy.guard(PhoneAction("type", index = 1, value = "Hello"), frame()).type)
        assertEquals("blocked", policy.guard(PhoneAction("done", expect = PhoneExpectation("package", FacebookPageSelection.PACKAGE)), frame()).type)
        assertEquals("launch_app", policy.guard(PhoneAction("launch_app", text = "Facebook"), frame()).type)
        assertEquals("blocked", policy.guard(PhoneAction("tap", index = 1), frame()).type)
    }
    @Test fun chosenDestinationMustStillBeVisibleBeforeDrafting() {
        val policy = FacebookPageSelection(); val f = frame()
        assertEquals("Studio Page", policy.choose(f.controls[0]).selector?.label)
        val action = PhoneAction("type", index = 1, value = "Hello")
        assertEquals("blocked", policy.guard(action, f.copy(controls = emptyList(), visibleText = listOf("Other Page"))).type)
        assertEquals("type", policy.guard(action, f.copy(controls = emptyList(), visibleText = listOf("Studio Page"))).type)
        assertEquals("blocked", policy.guard(PhoneAction("done", expect = PhoneExpectation("package", f.pkg)), f).type)
        assertTrue(policy.candidates(choose, f).isEmpty())
    }
    @Test fun selectionSchemaParsesWithoutGrantingPublishPermission() {
        val action = PhoneAgentLoop.parseAction("""{"action":"choose_facebook_page","value":"1,2"}""")!!
        assertEquals(listOf("Studio Page", "Band Page"), FacebookPageSelection().candidates(action, frame()).map { it.label })
        assertFalse(PhoneGoalLimits.maySubmit("make a Facebook post"))
    }
}
