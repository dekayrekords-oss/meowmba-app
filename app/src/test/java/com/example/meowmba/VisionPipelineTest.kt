package com.example.meowmba

import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.vision.OllamaVisionProvider
import com.example.meowmba.core.vision.OpenAIVisionProvider
import com.example.meowmba.core.vision.VisionRouter
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Phase 7a: vision trigger parsing, prompt building, provider selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisionPipelineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockSecureStorage = mock(SecureStorageManager::class.java)
    private val mockMemoryRepository = mock(MemoryRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(mockSecureStorage.context).thenReturn(mock(android.content.Context::class.java))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Router ─────────────────────────────────────────────────────────────

    @Test
    fun `exact look phrases trigger vision`() {
        listOf(
            "what do you see", "look at this", "look", "what is this",
            "what's that", "describe what you see", "what am i holding",
            "take a look", "what's in front of you"
        ).forEach { phrase ->
            assertTrue("should trigger: $phrase", VisionRouter.parse(phrase) != null)
        }
    }

    @Test
    fun `focus is extracted from look-at phrases`() {
        assertEquals("screen", VisionRouter.parse("look at the screen")!!.focus)
        assertEquals("plant", VisionRouter.parse("can you see the plant")!!.focus)
        assertEquals("document", VisionRouter.parse("read the document")!!.focus)
    }

    @Test
    fun `non-vision phrases do not trigger`() {
        listOf(
            "what time is it", "create file called notes.txt",
            "look up the weather online", "how are you",
            "search memory for tea", "what's the forecast"
        ).forEach { phrase ->
            assertNull("should NOT trigger: $phrase", VisionRouter.parse(phrase))
        }
    }

    @Test
    fun `prompt includes focus when present`() {
        assertTrue(VisionRouter.promptFor(VisionRouter.VisionQuery()).contains("Describe what you see"))
        val focused = VisionRouter.promptFor(VisionRouter.VisionQuery("keyboard"))
        assertTrue(focused.contains("Focus on: keyboard."))
    }

    // ── Provider selection ─────────────────────────────────────────────────

    @Test
    fun `ollama selection maps to ollama vision with configured model`() = runTest {
        `when`(mockSecureStorage.getEndpoint("ollama")).thenReturn("http://192.168.1.69:11434")
        `when`(mockSecureStorage.getModelName("ollama_vision")).thenReturn("gemma4:12b-it-qat")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("Ollama (Local Network)")

        val provider = vm.visionProviderFor()
        assertTrue(provider is OllamaVisionProvider)
    }

    @Test
    fun `openai selection maps to openai vision`() = runTest {
        `when`(mockSecureStorage.getApiKey("openai")).thenReturn("sk-test")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenAI (Cloud)")

        assertTrue(vm.visionProviderFor() is OpenAIVisionProvider)
    }

    @Test
    fun `on-device selection honestly has no vision`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("On-device (Local)")

        assertNull(vm.visionProviderFor())
    }

    @Test
    fun `look request without camera speaks honestly`() = runTest {
        // No visionManager (tests) → honest "no camera", no crash
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onUserTextSubmitted("what do you see")
        advanceUntilIdle()
        // It answered out loud rather than hanging or crashing
        assertEquals(com.example.meowmba.ui.companion.InteractionState.IDLE, vm.uiState.value.interactionState)
    }
}
