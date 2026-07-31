package com.example.meowmba

import android.content.Context
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.ModelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Tests for the "Currently Loaded" snapshot shown on the Hardware page:
 * it must reflect the selected provider and the secure configuration that
 * actually builds the providers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelStatusTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockSecureStorage = mock(SecureStorageManager::class.java)
    private val mockContext = mock(Context::class.java)
    private val mockMemoryRepository = mock(MemoryRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(mockSecureStorage.context).thenReturn(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults show local model active without secure storage`() = runTest {
        val vm = CompanionViewModel()
        val loaded = vm.uiState.value.loadedModels
        // No storage → legacy default path; name reflects the real file, not a label
        assertEquals("gemma.bin", loaded.localName)
        assertEquals("gpt-4o-mini", loaded.cloudModel)
        assertEquals(ModelSource.LOCAL, loaded.activeSource)
        assertFalse(loaded.cloudKeyConfigured)
    }

    @Test
    fun `selecting cloud model reflects configured cloud model and key`() = runTest {
        `when`(mockSecureStorage.getModelName("openai")).thenReturn("gpt-4o")
        `when`(mockSecureStorage.getApiKey("openai")).thenReturn("sk-test")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenAI (Cloud)")

        val loaded = vm.uiState.value.loadedModels
        assertEquals("gpt-4o", loaded.cloudModel)
        assertTrue(loaded.cloudKeyConfigured)
        assertEquals(ModelSource.CLOUD, loaded.activeSource)
        assertEquals("https://api.openai.com", loaded.cloudEndpoint)
    }

    @Test
    fun `cloud key not configured is reported honestly`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("OpenAI (Cloud)")

        assertFalse(vm.uiState.value.loadedModels.cloudKeyConfigured)
        assertEquals(ModelSource.CLOUD, vm.uiState.value.loadedModels.activeSource)
    }

    @Test
    fun `selecting local model shows custom on-device path`() = runTest {
        `when`(mockSecureStorage.getEndpoint("local")).thenReturn("/sdcard/llm/gemma3.bin")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("On-device (Local)")

        val loaded = vm.uiState.value.loadedModels
        assertEquals("/sdcard/llm/gemma3.bin", loaded.localPath)
        // Unknown (non-catalog) file → the honest file name, not "Gemma 2b"
        assertEquals("gemma3.bin", loaded.localName)
        assertEquals(ModelSource.LOCAL, loaded.activeSource)
    }

    @Test
    fun `catalog file resolves to its display name`() = runTest {
        `when`(mockSecureStorage.getEndpoint("local"))
            .thenReturn("/data/user/0/com.example.meowmba/files/models/qwen2.5-1.5b-instruct-q8.task")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("On-device (Local)")

        assertEquals("Qwen 2.5 1.5B Instruct", vm.uiState.value.loadedModels.localName)
    }

    @Test
    fun `ollama selection populates the network card`() = runTest {
        `when`(mockSecureStorage.getEndpoint("ollama")).thenReturn("http://192.168.1.69:11434")
        `when`(mockSecureStorage.getModelName("ollama")).thenReturn("qwen3.6:latest")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("Ollama (Local Network)")

        val loaded = vm.uiState.value.loadedModels
        assertEquals(ModelSource.NETWORK, loaded.activeSource)
        assertEquals("http://192.168.1.69:11434", loaded.networkEndpoint)
        assertEquals("qwen3.6:latest", loaded.networkModel)
    }

    @Test
    fun `selecting ollama marks network as the active source`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("Ollama (Local Network)")

        assertEquals(ModelSource.NETWORK, vm.uiState.value.loadedModels.activeSource)
    }

    @Test
    fun `every provider-picker entry builds a real provider`() = runTest {
        // Regression net: picker entries that build nothing trap users in
        // "No model is ready" (the removed Phi-3/Anthropic/Memba-Custom stubs).
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        // Only the entries this device actually OFFERS. Gemini Nano is filtered out
        // where AICore is absent (as in this JVM test), which is the point of
        // availableModelProviders — an offered entry must always build.
        com.example.meowmba.ui.companion
            .availableModelProviders(mockSecureStorage.context)
            .forEach { label ->
            vm.onModelSelected(label)
            org.junit.Assert.assertNotNull("picker entry '$label' builds no provider", vm.currentProvider)
        }
    }

    @Test
    fun `provider selection persists across restarts`() = runTest {
        `when`(mockSecureStorage.getPreference("selected_model")).thenReturn("Ollama (Local Network)")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        assertEquals("Ollama (Local Network)", vm.uiState.value.selectedModel)
        assertEquals(ModelSource.NETWORK, vm.uiState.value.loadedModels.activeSource)
    }

    @Test
    fun `stale persisted provider falls back to default honestly`() = runTest {
        // A label removed in a later version must not resurrect the null-provider trap
        `when`(mockSecureStorage.getPreference("selected_model")).thenReturn("Phi-3 Mini (Local)")
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        // Falls back to the current default — the local router — and the assertion below
        // is the real point: a provider actually got built, no null-provider trap.
        assertEquals(
            com.example.meowmba.ui.companion.MODEL_OMNIROUTE,
            vm.uiState.value.selectedModel,
        )
        org.junit.Assert.assertNotNull(vm.currentProvider)
    }

    @Test
    fun `deleted configured model file is re-pointed at an installed bundle`() = runTest {
        // The user's gemm4.task was deleted outside the app; qwen sits on disk.
        // Startup must adopt the installed bundle instead of showing
        // "no model installed" while the network provider keeps answering.
        val dir = java.nio.file.Files.createTempDirectory("models").toFile()
        val bundle = java.io.File(dir, "qwen2.5-1.5b-instruct-q8.task")
        bundle.writeText("fake bundle")
        `when`(mockSecureStorage.getEndpoint("local")).thenReturn("/gone/gemm4.task")

        val vm = CompanionViewModel(
            secureStorage = mockSecureStorage,
            memoryRepository = mockMemoryRepository,
            modelsDir = dir
        )

        // The repoint happened at startup…
        org.mockito.Mockito.verify(mockSecureStorage)
            .saveEndpoint("local", bundle.absolutePath)

        // …and once the stored value reflects it, the snapshot is honest
        `when`(mockSecureStorage.getEndpoint("local")).thenReturn(bundle.absolutePath)
        vm.onModelSelected("On-device (Local)")
        assertEquals(bundle.absolutePath, vm.uiState.value.loadedModels.localPath)
        assertEquals("Qwen 2.5 1.5B Instruct", vm.uiState.value.loadedModels.localName)
        dir.deleteRecursively()
    }
}
