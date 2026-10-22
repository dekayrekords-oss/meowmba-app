package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.download.DownloadService
import com.example.meowmba.core.setup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

internal class FakeBrainSetupBackend(val root: File) : BrainSetupBackend {
    override val downloads = MutableStateFlow(DownloadService.Companion.Status())
    var savedState = SavedBrain()
    var key = ""
    var verifyKeyError: String? = null
    var catalogError = false
    var saveError = false
    var cloudError = false
    var localError = false
    var verifiedFiles = mutableSetOf<String>()
    var localGate: CompletableDeferred<Unit>? = null
    var downloadsStarted = 0
    var localVerifications = 0
    var keyChecks = 0
    val cloudAttempts = mutableListOf<String>()
    val catalog = listOf(OpenRouterModel("test:free", "Free test", false, true, true), OpenRouterModel("paid", "Paid test", false, false, true))
    override fun saved() = savedState
    override fun savedKey() = key
    override suspend fun scan() = SetupDeviceProfile(8.0, 20_000_000_000, 8, false, true).let {
        SetupScan(it, SetupDeviceScan.modelsIn(listOf(root), savedState.localPath), it.recommendedModel())
    }
    override suspend fun verifyKey(key: String) { keyChecks++; verifyKeyError?.let { error(it) } }
    override suspend fun saveKey(key: String) { check(!saveError) { "Storage full" }; this.key = key; savedState = savedState.copy(keyPresent = true, cloudSelected = true, consent = true, ready = false) }
    override suspend fun deselectCloud() { savedState = savedState.copy(cloudSelected = false, consent = false, ready = savedState.localPath in verifiedFiles) }
    override suspend fun models(): List<OpenRouterModel> { check(!catalogError) { "Catalog unavailable" }; return catalog }
    override suspend fun verifyCloud(key: String, model: String) { cloudAttempts += model; check(!cloudError) { "HTTP 429" } }
    override suspend fun selectCloud(key: String, model: String) { savedState = savedState.copy(cloudSelected = true, ready = true, model = model) }
    override suspend fun verifyLocal(file: File) { localVerifications++; localGate?.await(); check(!localError) { "Engine failed" }; verifiedFiles += file.path; savedState = savedState.copy(localPath = file.path) }
    override fun localReady(file: File) = file.path in verifiedFiles
    override suspend fun selectLocal(file: File) { savedState = savedState.copy(cloudSelected = false, ready = true, localPath = file.path) }
    override suspend fun pendingDownload(fileName: String) { savedState = savedState.copy(pendingDownload = fileName) }
    override suspend fun download(option: LocalModelOption) { downloadsStarted++; downloads.value = DownloadService.Companion.Status(option.fileName, true) }
    override suspend fun verifyPlanner() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrainSetupRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<BrainSetupModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(backend: FakeBrainSetupBackend) = BrainSetupModel(backend).also { models += it }

    @Test fun keyAndConsentEnableSaveWithoutLoadingAModelList() = runTest {
        val backend = FakeBrainSetupBackend(temp.root); val model = model(backend)
        model.setKey(" pasted-key ")
        assertFalse(model.ui.value.canConnect)
        model.setConsent(true)
        assertTrue(model.ui.value.canConnect)
        assertTrue(model.ui.value.models.isEmpty())
        model.connectCloud(); runCurrent()
        assertEquals("pasted-key", backend.key)
        assertTrue(model.ui.value.canContinue)
        assertTrue(model.ui.value.saved.cloudSelected)
        assertEquals(listOf("test:free"), backend.cloudAttempts)
        assertEquals("", model.ui.value.key)
    }
    @Test fun catalogFailureKeepsTheKeyAndRestartCanRetryWithoutRepasting() = runTest {
        val backend = FakeBrainSetupBackend(temp.root).apply { catalogError = true }
        val first = model(backend); first.setConsent(true); first.setKey("test-key"); first.connectCloud(); runCurrent()
        assertEquals("test-key", backend.key)
        assertFalse(first.ui.value.canContinue)
        assertFalse(first.ui.value.cloudBusy)
        val reopened = model(backend)
        assertTrue(reopened.ui.value.canConnect)
        backend.catalogError = false; reopened.connectCloud(); runCurrent()
        assertTrue(reopened.ui.value.canContinue)
    }
    @Test fun rejectedKeyDoesNotReplaceAnExistingKey() = runTest {
        val backend = FakeBrainSetupBackend(temp.root).apply { key = "original"; savedState = SavedBrain(keyPresent = true); verifyKeyError = "HTTP 401" }
        val model = model(backend); model.setConsent(true); model.setKey("wrong"); model.connectCloud(); runCurrent()
        assertEquals("original", backend.key)
        assertTrue(backend.cloudAttempts.isEmpty())
        assertFalse(model.ui.value.cloudBusy)
        assertTrue(model.ui.value.cloudStatus.contains("Previous saved key kept"))
    }
    @Test fun diskWriteFailureDoesNotClaimTheKeyWasSaved() = runTest {
        val backend = FakeBrainSetupBackend(temp.root).apply { saveError = true }
        val model = model(backend); model.setConsent(true); model.setKey("key"); model.connectCloud(); runCurrent()
        assertEquals("", backend.key)
        assertFalse(model.ui.value.saved.keyPresent)
        assertFalse(model.ui.value.canContinue)
        assertFalse(model.ui.value.cloudBusy)
        assertTrue(model.ui.value.cloudStatus.contains("Storage full"))
    }
    @Test fun reopeningSetupPreservesAnAlreadyVerifiedPaidChoice() = runTest {
        val backend = FakeBrainSetupBackend(temp.root).apply { key = "key"; savedState = SavedBrain(keyPresent = true, cloudSelected = true, consent = true, ready = true, model = "paid") }
        val model = model(backend)
        assertEquals("paid", model.ui.value.selected)
        model.connectCloud(); runCurrent()
        assertEquals(listOf("paid"), backend.cloudAttempts)
        assertEquals("paid", backend.savedState.model)
    }
    @Test fun unavailableFreeModelsDoNotFallBackToPaidOrPretendSetupCompleted() = runTest {
        val backend = FakeBrainSetupBackend(temp.root).apply { cloudError = true }
        val model = model(backend); model.setConsent(true); model.setPaid(true); model.setKey("key"); model.connectCloud(); runCurrent()
        assertEquals(listOf("test:free"), backend.cloudAttempts)
        assertEquals("key", backend.key)
        assertFalse(model.ui.value.canContinue)
        assertTrue(model.ui.value.cloudStatus.contains("429"))
    }
    @Test fun initialScanFindsCompletedModelsAndDoesNotRedownload() = runTest {
        val file = File(temp.root, ModelCatalog.options.first { it.id == "gemma-4-e2b" }.fileName).apply { writeText("fixture") }
        File(temp.root, "needle2.cact").writeText("router")
        File(temp.root, "incomplete.task.part").writeText("partial")
        val backend = FakeBrainSetupBackend(temp.root); val model = model(backend)
        assertEquals(listOf(file), model.ui.value.scan!!.models)
        assertTrue(model.ui.value.localStatus.contains("already on this phone"))
        assertFalse(model.ui.value.canContinue)
        model.downloadLocal(); runCurrent()
        assertEquals(0, backend.downloadsStarted)
        assertEquals(1, backend.localVerifications)
        assertTrue(model.ui.value.canContinue)
    }
    @Test fun pendingDownloadCompletionIsRecoveredAfterProcessRestart() = runTest {
        val file = File(temp.root, "model.task").apply { writeText("fixture") }
        val backend = FakeBrainSetupBackend(temp.root).apply { savedState = SavedBrain(pendingDownload = file.name) }
        val model = model(backend); runCurrent()
        assertEquals(1, backend.localVerifications)
        assertEquals("", backend.savedState.pendingDownload)
        assertTrue(model.ui.value.canContinue)
        model.refresh(); runCurrent()
        assertEquals(1, backend.localVerifications)
    }
    @Test fun downloadCompletionAutomaticallyChangesStatusAndTestsTheModel() = runTest {
        val backend = FakeBrainSetupBackend(temp.root); val model = model(backend)
        model.downloadLocal(); runCurrent()
        val name = backend.downloads.value.fileName
        File(temp.root, name).writeText("download fixture")
        backend.downloads.value = DownloadService.Companion.Status(name, received = 16, total = 16)
        runCurrent()
        assertTrue(model.ui.value.localStatus.contains("Ready for chat"))
        assertTrue(model.ui.value.canContinue)
    }
    @Test fun failedLocalVerificationDoesNotLoseTheDownloadedFile() = runTest {
        val file = File(temp.root, "model.task").apply { writeText("fixture") }
        val backend = FakeBrainSetupBackend(temp.root).apply { localError = true }
        val model = model(backend); model.verifyLocal(file); runCurrent()
        assertTrue(file.exists())
        assertTrue(model.ui.value.localStatus.contains("Downloaded, but not ready"))
        assertFalse(model.ui.value.canContinue)
        assertFalse(model.ui.value.localBusy)
    }
    @Test fun aLateLocalTestCannotOverwriteTheUsersNewOpenRouterDefault() = runTest {
        val file = File(temp.root, "model.task").apply { writeText("fixture") }
        val backend = FakeBrainSetupBackend(temp.root).apply { localGate = CompletableDeferred() }
        val model = model(backend); model.verifyLocal(file)
        model.setConsent(true); model.setKey("key"); model.connectCloud(); runCurrent()
        assertTrue(model.ui.value.canContinue)
        backend.localGate!!.complete(Unit); runCurrent()
        assertTrue(backend.savedState.cloudSelected)
        assertTrue(model.ui.value.localStatus.contains("OpenRouter stays the default"))
    }
    @Test fun withdrawingCloudConsentDeselectsCloudButRetainsTheKey() = runTest {
        val backend = FakeBrainSetupBackend(temp.root); val model = model(backend)
        model.setConsent(true); model.setKey("key"); model.connectCloud(); runCurrent()
        model.setConsent(false); runCurrent()
        assertFalse(backend.savedState.cloudSelected)
        assertEquals("key", backend.key)
        assertFalse(model.ui.value.canConnect)
        assertFalse(model.ui.value.canContinue)
    }
    @Test fun recommendationsConsiderRamArchitectureStorageAndClassicFallback() {
        assertEquals("gemma-4-e2b", SetupDeviceProfile(7.3, 6_000_000_000, 8, false, true).recommendedModel()?.id)
        assertEquals("qwen-1.5b", SetupDeviceProfile(5.5, 3_000_000_000, 8, false, true).recommendedModel()?.id)
        assertEquals("qwen-0.5b", SetupDeviceProfile(3.0, 2_000_000_000, 4, false, true).recommendedModel()?.id)
        assertNull(SetupDeviceProfile(8.0, 500_000_000, 8, false, true).recommendedModel())
        assertNull(SetupDeviceProfile(2.0, 9_000_000_000, 4, true, false).recommendedModel())
        assertTrue(SetupDeviceProfile(3.0, 0, 4, false, true).preferClassic)
        assertFalse(SetupDeviceProfile(8.0, 0, 8, false, true).preferClassic)
    }
}
