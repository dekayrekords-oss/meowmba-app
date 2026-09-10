package com.example.meowmba

import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.vision.DeviceVisionProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Phase 7a/7b: vision trigger parsing, prompt building, provider selection, OCR routing.
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

    // ── Phase 7b: read queries ──────────────────────────────────────────────

    @Test
    fun `read this triggers as read query`() {
        val q = VisionRouter.parse("read this")
        assertNotNull(q)
        assertTrue(q!!.isReadQuery)
    }

    @Test
    fun `read the screen triggers as read query with focus`() {
        val q = VisionRouter.parse("read the screen")
        assertEquals("screen", q?.focus)
        assertTrue(q?.isReadQuery ?: false)
    }

    @Test
    fun `exact look is NOT a read query`() {
        assertFalse(VisionRouter.parse("what do you see")?.isReadQuery ?: true)
    }

    @Test
    fun `prompt for read query uses read instruction`() {
        val readQuery = VisionRouter.VisionQuery(focus = "label", isReadQuery = true)
        val prompt = VisionRouter.promptFor(readQuery)
        assertTrue(prompt.contains("Read any visible text"))
        assertTrue(prompt.contains("Focus on: label."))
    }

    @Test
    fun `look at phrase is not a read query`() {
        val q = VisionRouter.parse("look at the cup")
        assertFalse(q?.isReadQuery ?: true)
    }

    // ── Provider selection ─────────────────────────────────────────────────

    @Test
    fun `network selection with configured vision endpoint maps to ollama vision`() = runTest {
        `when`(mockSecureStorage.getEndpoint("network_vision")).thenReturn("http://192.168.1.69:11434")
        `when`(mockSecureStorage.getModelName("network_vision")).thenReturn("gemma4:12b-it-qat")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenRouter (Cloud)")

        val provider = vm.visionProviderFor()
        assertTrue(provider is OllamaVisionProvider)
    }

    @Test
    fun `network selection without a configured vision endpoint stays honest`() = runTest {
        // Standalone app: no home server and no OpenRouter key — vision reports
        // nothing rather than dialing a hardcoded LAN address.
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenRouter (Cloud)")

        assertNull(vm.visionProviderFor())
        assertNull(vm.browserVisionProvider())
    }

    @Test
    fun `openrouter key uses a dedicated vision model for the browser`() = runTest {
        `when`(mockSecureStorage.getApiKey("openrouter")).thenReturn("sk-or-test")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onModelSelected("OpenRouter (Cloud)")
        val provider = vm.browserVisionProvider()
        assertTrue(provider is OpenAIVisionProvider)
        assertEquals("OpenRouter vision", provider!!.name)
    }

    @Test
    fun `openai selection maps to openai vision`() = runTest {
        `when`(mockSecureStorage.getApiKey("openai")).thenReturn("sk-test")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenAI (Cloud)")

        assertTrue(vm.visionProviderFor() is OpenAIVisionProvider)
    }

    @Test
    fun `saved OpenAI vision model and endpoint apply to camera and browser`() = runTest {
        `when`(mockSecureStorage.getApiKey("openai")).thenReturn("test-key")
        `when`(mockSecureStorage.getModelName("openai_vision")).thenReturn("test-image-model")
        `when`(mockSecureStorage.getEndpoint("openai")).thenReturn("https://vision.example.test")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onModelSelected("OpenAI (Cloud)")
        listOf(vm.visionProviderFor(), vm.browserVisionProvider()).forEach { provider ->
            assertTrue(provider is OpenAIVisionProvider)
            assertEquals("test-image-model", provider!!.javaClass.getDeclaredField("model").apply { isAccessible = true }.get(provider))
            assertEquals("https://vision.example.test", provider.javaClass.getDeclaredField("baseUrl").apply { isAccessible = true }.get(provider))
        }
    }

    @Test
    fun `OpenAI default inside OmniRoute uses the saved OpenAI vision configuration`() = runTest {
        `when`(mockSecureStorage.getApiKey("openai")).thenReturn("test-key")
        `when`(mockSecureStorage.getPreference("default_brain")).thenReturn("openai")
        `when`(mockSecureStorage.getModelName("openai_vision")).thenReturn("test-image-model")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onModelSelected("Omniroute (Local router)")
        val provider = vm.visionProviderFor()
        assertEquals("OpenAI vision", provider?.name)
        assertEquals("test-image-model", provider!!.javaClass.getDeclaredField("model").apply { isAccessible = true }.get(provider))
    }

    @Test
    fun `OpenRouter vision uses the configured model and endpoint`() = runTest {
        `when`(mockSecureStorage.getApiKey("openrouter")).thenReturn("test-key")
        `when`(mockSecureStorage.getModelName("openrouter_vision")).thenReturn("test/vision")
        `when`(mockSecureStorage.getEndpoint("openrouter")).thenReturn("https://gateway.example.test/api")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onModelSelected("OpenRouter (Cloud)")
        val provider = vm.visionProviderFor()!!
        assertEquals("test/vision", provider.javaClass.getDeclaredField("model").apply { isAccessible = true }.get(provider))
        assertEquals("https://gateway.example.test/api", provider.javaClass.getDeclaredField("baseUrl").apply { isAccessible = true }.get(provider))
    }

    @Test
    fun `on-device selection uses device vision`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("On-device (Local)")

        assertTrue("on-device should use device vision", vm.visionProviderFor() is DeviceVisionProvider)
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

    @Test
    fun `read request without camera also speaks honestly`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        vm.onUserTextSubmitted("read this")
        advanceUntilIdle()
        assertEquals(com.example.meowmba.ui.companion.InteractionState.IDLE, vm.uiState.value.interactionState)
    }
}
