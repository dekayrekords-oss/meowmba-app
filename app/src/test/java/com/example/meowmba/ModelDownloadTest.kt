package com.example.meowmba

import android.content.Context
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.agent.ModelDownloader
import com.example.meowmba.core.security.SecureStorageManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Files

class ModelCatalogTest {

    @Test
    fun `catalog has four distinct, downloadable options`() {
        assertEquals(4, ModelCatalog.options.size)
        assertEquals(4, ModelCatalog.options.map { it.id }.distinct().size)
        assertEquals(4, ModelCatalog.options.map { it.fileName }.distinct().size)
        assertTrue(ModelCatalog.options.all { it.downloadUrl.startsWith("https://") })
        assertTrue(ModelCatalog.options.all { it.fileName.endsWith(".task") })
    }

    @Test
    fun `gating flags match reality - qwen ungated, gemma needs a token`() {
        val qwen = ModelCatalog.options.filter { it.id.startsWith("qwen") }
        assertTrue(qwen.isNotEmpty())
        assertTrue(qwen.all { !it.requiresHfToken })
        val gemma = ModelCatalog.options.single { it.id.startsWith("gemma") }
        assertTrue(gemma.requiresHfToken)
    }

    @Test
    fun `exactly one recommended option`() {
        assertEquals(1, ModelCatalog.options.count { it.recommended })
    }

    @Test
    fun `filename validation`() {
        assertTrue(ModelCatalog.isValidModelFileName("qwen2.5-0.5b.task"))
        assertTrue(ModelCatalog.isValidModelFileName("hermes3-3b.litertlm"))
        assertFalse(ModelCatalog.isValidModelFileName("../evil.task"))
        assertFalse(ModelCatalog.isValidModelFileName("a/b.task"))
        assertFalse(ModelCatalog.isValidModelFileName("model.gguf"))
        assertFalse(ModelCatalog.isValidModelFileName("no extension"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var modelsDir: File
    private lateinit var mockStorage: SecureStorageManager
    private var engineCalls = 0

    /** Fake byte engine: writes 100 bytes with two progress ticks, records calls. */
    private val fakeEngine: suspend (String, File, String?, (Long, Long?) -> Unit) -> Unit =
        { _, dest, _, onProgress ->
            engineCalls++
            onProgress(50, 100)
            dest.writeBytes(ByteArray(100))
            onProgress(100, 100)
        }

    private lateinit var downloader: ModelDownloader

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        modelsDir = Files.createTempDirectory("model_dl_test").toFile()
        mockStorage = mock(SecureStorageManager::class.java)
        `when`(mockStorage.context).thenReturn(mock(Context::class.java))
        engineCalls = 0
        downloader = ModelDownloader(
            modelsDir,
            hfTokenProvider = { null },
            engine = fakeEngine,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CompanionViewModel(
        secureStorage = mockStorage,
        modelsDir = modelsDir,
        modelDownloader = downloader
    )

    @Test
    fun `downloading a catalog model installs it and marks it ready`() = runTest {
        val vm = viewModel()
        val option = ModelCatalog.options.first { !it.requiresHfToken }

        vm.downloadModel(option)
        advanceUntilIdle()

        assertEquals(1, engineCalls)
        assertTrue(vm.uiState.value.modelDownloads[option.id]?.done == true)
        assertTrue("file on disk", File(modelsDir, option.fileName).exists())
        assertTrue(vm.uiState.value.loadedModels.localInstalled)
        assertTrue(vm.uiState.value.loadedModels.localPath.endsWith(option.fileName))
    }

    @Test
    fun `gated model without token fails honestly and never calls the network`() = runTest {
        val vm = viewModel()
        val gated = ModelCatalog.options.single { it.requiresHfToken }

        vm.downloadModel(gated)
        advanceUntilIdle()

        assertEquals(0, engineCalls)
        val progress = vm.uiState.value.modelDownloads[gated.id]
        assertNotNull(progress?.error)
        assertTrue(progress!!.error!!.contains("Hugging Face token"))
    }

    @Test
    fun `custom download validates filename and installs on success`() = runTest {
        val vm = viewModel()

        vm.downloadCustomModel("https://example.com/m.task", "../evil.task")
        assertTrue(vm.uiState.value.modelDownloads["custom:../evil.task"]?.error != null)
        assertEquals(0, engineCalls)

        vm.downloadCustomModel("notaurl", "m.task")
        assertTrue(vm.uiState.value.modelDownloads["custom:m.task"]?.error != null)
        assertEquals(0, engineCalls)

        vm.downloadCustomModel("https://example.com/m.task", "m.task")
        advanceUntilIdle()
        assertEquals(1, engineCalls)
        assertTrue(File(modelsDir, "m.task").exists())
    }

    @Test
    fun `an already-downloaded model is adopted automatically at startup`() = runTest {
        // Pre-install a catalog model before the ViewModel exists
        val option = ModelCatalog.options.first()
        File(modelsDir, option.fileName).writeBytes(ByteArray(64))

        val vm = viewModel()
        assertTrue(vm.uiState.value.loadedModels.localInstalled)
        assertTrue(vm.uiState.value.loadedModels.localPath.endsWith(option.fileName))
    }

    @Test
    fun `deleting an installed model returns to not-installed honestly`() = runTest {
        val vm = viewModel()
        val option = ModelCatalog.options.first { !it.requiresHfToken }
        vm.downloadModel(option)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadedModels.localInstalled)

        vm.deleteDownloadedModel(option)
        assertFalse(File(modelsDir, option.fileName).exists())
        assertFalse(vm.uiState.value.loadedModels.localInstalled)
    }
}
