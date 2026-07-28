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
        assertEquals("Gemma 2b", loaded.localName)
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

        vm.onModelSelected("Gemma 2b (Local)")

        val loaded = vm.uiState.value.loadedModels
        assertEquals("/sdcard/llm/gemma3.bin", loaded.localPath)
        assertEquals("Gemma 2b", loaded.localName)
        assertEquals(ModelSource.LOCAL, loaded.activeSource)
    }

    @Test
    fun `selecting ollama marks network as the active source`() = runTest {
        val vm = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)

        vm.onModelSelected("Ollama (Local Network)")

        assertEquals(ModelSource.NETWORK, vm.uiState.value.loadedModels.activeSource)
    }
}
