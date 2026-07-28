package com.example.meowmba

import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.StreamParsers
import com.example.meowmba.core.audio.SelfVoiceFilter
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamParsersTest {

    @Test
    fun `ollama NDJSON partial and done lines`() {
        val partial = StreamParsers.parseOllamaLine("""{"response":"Hello","done":false}""")
        assertEquals(ModelResponse.Partial("Hello"), partial)

        val done = StreamParsers.parseOllamaLine("""{"response":"","done":true}""")
        assertTrue(done is ModelResponse.Complete)
    }

    @Test
    fun `ollama parser ignores noise`() {
        assertNull(StreamParsers.parseOllamaLine(""))
        assertNull(StreamParsers.parseOllamaLine("   "))
        assertNull(StreamParsers.parseOllamaLine("not json"))
        assertNull(StreamParsers.parseOllamaLine("""{"response":"","done":false}"""))
    }

    @Test
    fun `openai SSE partial and DONE sentinel`() {
        val partial = StreamParsers.parseOpenAISseLine(
            """data: {"choices":[{"delta":{"content":"Hi"}}]}"""
        )
        assertEquals(ModelResponse.Partial("Hi"), partial)

        val done = StreamParsers.parseOpenAISseLine("data: [DONE]")
        assertTrue(done is ModelResponse.Complete)
    }

    @Test
    fun `openai parser ignores comments, blanks and malformed chunks`() {
        assertNull(StreamParsers.parseOpenAISseLine(": keepalive"))
        assertNull(StreamParsers.parseOpenAISseLine(""))
        assertNull(StreamParsers.parseOpenAISseLine("data: garbage"))
        assertNull(StreamParsers.parseOpenAISseLine("""data: {"choices":[{"delta":{}}]}"""))
    }

    @Test
    fun `model list counters`() {
        assertEquals(2, StreamParsers.countOllamaModels("""{"models":[{"name":"a"},{"name":"b"}]}"""))
        assertEquals(3, StreamParsers.countOpenAIModels("""{"data":[{"id":"a"},{"id":"b"},{"id":"c"}]}"""))
        assertEquals(0, StreamParsers.countOllamaModels("not json"))
    }
}

class SelfVoiceFilterTest {

    private val filter = SelfVoiceFilter()

    @Test
    fun `echo of the current utterance is suppressed`() {
        filter.begin("The quick brown fox jumps over the lazy dog")
        assertTrue(filter.isOwnVoice("the quick brown"))
        assertTrue(filter.isOwnVoice("jumps over the lazy"))
    }

    @Test
    fun `echo still suppressed as playback advances`() {
        filter.begin("The quick brown fox jumps over the lazy dog")
        filter.onWordSpoken(0, 16) // "The quick brown" spoken
        // Words right around the playhead are still recognized as echo
        assertTrue(filter.isOwnVoice("brown fox jumps"))
    }

    @Test
    fun `novel user speech is allowed through as barge-in`() {
        filter.begin("The quick brown fox jumps over the lazy dog")
        assertFalse(filter.isOwnVoice("wait stop that"))
        assertFalse(filter.isOwnVoice("actually I have a question"))
    }

    @Test
    fun `mostly-echo with one novel word is still echo`() {
        filter.begin("The quick brown fox jumps over the lazy dog")
        // 5 of 6 tokens are the companion's own words
        assertTrue(filter.isOwnVoice("quick brown fox jumps over potato"))
    }

    @Test
    fun `short noise never interrupts`() {
        filter.begin("The quick brown fox jumps over the lazy dog")
        assertTrue(filter.isOwnVoice("uh"))
        assertTrue(filter.isOwnVoice(""))
    }

    @Test
    fun `after clearing, any real speech is the user`() {
        filter.begin("The quick brown fox")
        filter.clear()
        assertFalse(filter.isOwnVoice("the quick brown"))
    }
}

/**
 * Phase 3 model-routing behavior in the ViewModel: honest error surfacing
 * and echo-tolerant barge-in while the recognizer is live during playback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelRoutingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `provider error surfaces OFFLINE then recovers to idle`() = runTest {
        val vm = CompanionViewModel()
        vm.showProviderError("connection refused")

        assertEquals(InteractionState.OFFLINE, vm.uiState.value.interactionState)
        assertEquals("connection refused", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.captionText?.contains("connection refused") == true)

        advanceUntilIdle()
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `connection test without secure storage reports honestly`() = runTest {
        val vm = CompanionViewModel()
        vm.runCloudConnectionTest()
        assertEquals("Secure storage unavailable", vm.uiState.value.connectionTestResult)
        assertFalse(vm.uiState.value.connectionTestRunning)
    }

    @Test
    fun `local model check reports missing file honestly`() = runTest {
        val vm = CompanionViewModel()
        vm.runLocalModelCheck()
        assertTrue(vm.uiState.value.connectionTestResult?.contains("not found") == true)
    }
}
