package com.example.meowmba

import com.example.meowmba.core.agent.BrowserAgentLoop
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserAgentLifecycleTest {
    @Test fun inspectingAPageIsNotPermissionToReplaceIt() {
        assertFalse(BrowserAgentLoop.isBuildGoal("check the page"))
        assertFalse(BrowserAgentLoop.isBuildGoal("make a Facebook post for my page"))
        assertTrue(BrowserAgentLoop.isBuildGoal("make a web page about cats"))
    }
    @Test fun doneDoesNotRestartAfterOldMonitoringIntervalOrReopen() = runTest {
        var calls = 0
        val loop = BrowserAgentLoop(this, { null }, { calls++; """{"action":"done","summary":"Finished"}""" }, { null }, { "<p>ready</p>" }, File("unused"), {}, stepDelayMs = 1, monitorEveryMs = 10)
        loop.setGoal("check the page")
        advanceUntilIdle()
        assertFalse(loop.isActive); assertEquals(1, calls)
        advanceTimeBy(90_000); loop.browserOpened(); advanceUntilIdle()
        assertEquals(1, calls)
    }
    @Test fun completedBuildDoesNotReplaceItsPreviewWithAnotherScaffold() = runTest {
        var captures = 0; val source = mutableListOf<String>()
        val loop = BrowserAgentLoop(this, { null }, { """{"action":"done","summary":"Finished"}""" }, { captures++; null }, { "<p>ready</p>" }, File("unused"), {}, { _, text -> source += text }, stepDelayMs = 1, monitorEveryMs = 10)
        loop.setGoal("make a page about cats"); advanceUntilIdle()
        val count = source.size
        assertTrue(count > 0); assertEquals(1, captures); assertFalse(loop.isActive)
        advanceTimeBy(90_000); loop.browserOpened(); advanceUntilIdle()
        assertEquals(count, source.size); assertEquals(1, captures)
    }
    @Test fun modelFailureDoesNotRepeatSpeechOrCalls() = runTest {
        var calls = 0; val speech = mutableListOf<String>()
        val loop = BrowserAgentLoop(this, { null }, { calls++; null }, { null }, { "" }, File("unused"), { speech += it }, stepDelayMs = 1, monitorEveryMs = 10)
        loop.setGoal("check the page"); advanceUntilIdle()
        assertFalse(loop.isActive); assertEquals(1, calls)
        advanceTimeBy(90_000); assertEquals(2, speech.size)
    }
    @Test fun waitActionsAreBoundedToo() = runTest {
        var calls = 0
        val loop = BrowserAgentLoop(this, { null }, { calls++; """{"action":"wait"}""" }, { null }, { "" }, File("unused"), {}, stepDelayMs = 1)
        loop.setGoal("check the page"); advanceUntilIdle()
        assertFalse(loop.isActive); assertTrue(calls in 1..30)
    }
    @Test fun lateCancelledModelCannotMutatePageOrClearReplacement() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var calls = 0; var writes = 0
        val loop = BrowserAgentLoop(this, { null }, {
            calls++
            if (calls == 1) { entered.complete(Unit); withContext(NonCancellable) { release.await() }; """{"action":"js","code":"bad"}""" }
            else """{"action":"done","summary":"Finished"}"""
        }, { null }, { if (it == "bad") writes++; "" }, File("unused"), {}, stepDelayMs = 1)
        loop.setGoal("old page"); advanceTimeBy(251); entered.await()
        loop.setGoal("new page"); release.complete(Unit); advanceUntilIdle()
        assertEquals(0, writes); assertEquals(2, calls); assertEquals("Finished", loop.lastReport)
    }
}
