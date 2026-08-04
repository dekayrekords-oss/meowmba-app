package com.example.meowmba

import android.content.Context
import com.example.meowmba.core.agent.ImageModelCatalog
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
    fun `catalog has five distinct, downloadable options`() {
        assertEquals(5, ModelCatalog.options.size)
        assertEquals(5, ModelCatalog.options.map { it.id }.distinct().size)
        assertEquals(5, ModelCatalog.options.map { it.fileName }.distinct().size)
        assertTrue(ModelCatalog.options.all { it.downloadUrl.startsWith("https://") })
        assertTrue(ModelCatalog.options.all {
            it.fileName.endsWith(".task") || it.fileName.endsWith(".litertlm")
        })
    }

    @Test
    fun `gating flags match reality - qwen ungated, gemma3 needs a token`() {
        val qwen = ModelCatalog.options.filter { it.id.startsWith("qwen") }
        assertTrue(qwen.isNotEmpty())
        assertTrue(qwen.all { !it.requiresHfToken })
        val gemma3 = ModelCatalog.options.first { it.id == "gemma-3-1b" }
        assertTrue(gemma3.requiresHfToken)
        val gemma4 = ModelCatalog.options.first { it.id == "gemma-4-e2b" }
        assertFalse(gemma4.requiresHfToken)
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

class ModelDownloadImageCatalogTest {

    @Test
    fun `catalog has one downloadable SDXL-Lightning entry, GGUF Q8_0 quantized`() {
        assertEquals(1, ImageModelCatalog.options.size)
        val sdxl = ImageModelCatalog.options.single()
        assertEquals("sdxl-lightning-4step-q8", sdxl.id)
        assertTrue(sdxl.downloadUrl.startsWith("https://huggingface.co/"))
        assertTrue(sdxl.fileName.endsWith(".gguf"))
        assertTrue(sdxl.fileName.contains("q8_0"))
        assertFalse(sdxl.requiresHfToken)
        assertTrue(sdxl.recommended)
    }

    @Test
    fun `optionForFile finds the entry by installed filename`() {
        val option = ImageModelCatalog.optionForFile("sdxl_lightning_4step.q8_0.gguf")
        assertNotNull(option)
        assertEquals("sdxl-lightning-4step-q8", option!!.id)
        assertNull(ImageModelCatalog.optionForFile("nonexistent.gguf"))
    }

    @Test
    fun `installedFile reports null until the gguf is actually present on disk`() {
        val modelsDir = Files.createTempDirectory("image_model_dl_test").toFile()
        val downloader = ModelDownloader(modelsDir, hfTokenProvider = { null })
        val option = ImageModelCatalog.options.single()

        assertNull(downloader.installedFile(option))

        File(modelsDir, option.fileName).writeBytes(ByteArray(64))

        val installed = downloader.installedFile(option)
        assertNotNull(installed)
        assertEquals(option.fileName, installed!!.name)
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

    @Test
    fun `downloadImageModel without a models directory fails honestly instead of crashing`() = runTest {
        // No modelsDir/modelDownloader injected → the ViewModel's `downloader` is null,
        // so this must hit the early guard and never touch DownloadService.start (which
        // needs a real Android Context — the same reason the three catalog-model download
        // tests above fail under plain JUnit rather than Robolectric).
        val vm = CompanionViewModel(secureStorage = mockStorage)
        val option = ImageModelCatalog.options.first()

        vm.downloadImageModel(option)

        val progress = vm.uiState.value.modelDownloads[option.id]
        assertNotNull(progress?.error)
        assertTrue(progress!!.error!!.contains("unavailable"))
    }

    @Test
    fun `deleting the image model returns to not-installed honestly`() = runTest {
        // Install by writing the file directly (not via downloadImageModel, which
        // routes through the same real DownloadService.start Android dependency the
        // three pre-existing catalog-download tests above already can't exercise
        // under plain JUnit) so this test verifies deleteImageModel in isolation.
        val option = ImageModelCatalog.options.first()
        File(modelsDir, option.fileName).writeBytes(ByteArray(64))
        val vm = viewModel()
        assertTrue(vm.imageModelInstalled())

        vm.deleteImageModel(option)
        assertFalse(File(modelsDir, option.fileName).exists())
        assertFalse(vm.imageModelInstalled())
    }

    @Test
    fun `an already-downloaded image model is detected at startup without any download`() = runTest {
        val option = ImageModelCatalog.options.first()
        File(modelsDir, option.fileName).writeBytes(ByteArray(64))

        val vm = viewModel()
        assertTrue(vm.imageModelInstalled())
        assertEquals(0, engineCalls)
    }
}
