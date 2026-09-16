package com.example.meowmba.core.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.download.DownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class SavedBrain(val keyPresent: Boolean = false, val cloudSelected: Boolean = false,
    val consent: Boolean = false, val ready: Boolean = false, val model: String = "", val localPath: String? = null,
    val pendingDownload: String = "")

interface BrainSetupBackend {
    val downloads: StateFlow<DownloadService.Companion.Status>
    fun saved(): SavedBrain
    fun savedKey(): String
    suspend fun scan(): SetupScan
    suspend fun verifyKey(key: String)
    suspend fun saveKey(key: String)
    suspend fun deselectCloud()
    suspend fun models(): List<OpenRouterModel>
    suspend fun verifyCloud(key: String, model: String)
    suspend fun selectCloud(key: String, model: String)
    suspend fun verifyLocal(file: File)
    fun localReady(file: File): Boolean
    suspend fun selectLocal(file: File)
    suspend fun pendingDownload(fileName: String)
    suspend fun download(option: LocalModelOption)
    suspend fun verifyPlanner()
}

data class BrainSetupState(
    val scanning: Boolean = true, val scan: SetupScan? = null, val saved: SavedBrain = SavedBrain(),
    val localFile: File? = null, val localBusy: Boolean = false, val localStatus: String = "Scanning accessible model folders…",
    val key: String = "", val consent: Boolean = false, val cloudBusy: Boolean = false,
    val cloudStatus: String = "", val models: List<OpenRouterModel> = emptyList(), val selected: String = "",
    val allowPaid: Boolean = false, val plannerBusy: Boolean = false, val plannerStatus: String = "",
) {
    val canConnect: Boolean get() = consent && !cloudBusy && (key.isNotBlank() || saved.keyPresent)
    val canContinue: Boolean get() = saved.ready && !cloudBusy && (!localBusy || saved.cloudSelected)
}

/** ViewModel lifetime retains paste/progress across rotation; secrets are never
 * put in saved-instance bundles. The encrypted key and filesystem survive process death. */
class BrainSetupModel(private val backend: BrainSetupBackend) : ViewModel() {
    private val initial = backend.saved()
    private val previousModel = initial.model.takeIf { initial.ready && initial.cloudSelected }.orEmpty()
    private val state = MutableStateFlow(BrainSetupState(saved = initial, consent = initial.consent,
        selected = previousModel, allowPaid = previousModel.isNotBlank() && !previousModel.endsWith(":free") && previousModel != "openrouter/free"))
    val ui: StateFlow<BrainSetupState> = state.asStateFlow()
    val downloads = backend.downloads
    private var selectionRevision = 0
    private var scanJob: Job? = null
    private var rescanRequested = false
    init {
        refresh()
        viewModelScope.launch { downloads.drop(1).collect { status -> if (!status.active) refresh() } }
    }
    fun setKey(value: String) { state.update { it.copy(key = value, cloudStatus = "") } }
    fun setConsent(value: Boolean) {
        if (state.value.cloudBusy) return
        state.update { it.copy(consent = value) }
        if (!value && backend.saved().cloudSelected) {
            ++selectionRevision
            state.update { it.copy(saved = it.saved.copy(cloudSelected = false, ready = false)) }
            viewModelScope.launch {
                try {
                    backend.deselectCloud()
                    state.update { it.copy(saved = backend.saved(), cloudStatus = "Cloud deselected. Your key is retained; verify local AI to continue privately.") }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { state.update { it.copy(cloudStatus = "Could not save the cloud preference. Retry before leaving setup.") } }
            }
        }
    }
    fun setPaid(value: Boolean) { state.update { it.copy(allowPaid = value, selected = "") } }
    fun selectModel(id: String) { state.update { it.copy(selected = id) } }
    fun message(value: String) { state.update { it.copy(localStatus = value) } }

    fun refresh() {
        if (scanJob?.isActive == true) { rescanRequested = true; return }
        scanJob = viewModelScope.launch {
            try {
                val scan = backend.scan()
                val saved = backend.saved()
                val selected = scan.models.find { it.path == state.value.localFile?.path }
                    ?: scan.models.find { it.path == saved.localPath }
                    ?: scan.models.find { it.name == scan.recommended?.fileName } ?: scan.models.firstOrNull()
                state.update { current -> current.copy(scanning = false, scan = scan, saved = saved, localFile = selected,
                    localStatus = if (current.localBusy) current.localStatus else when {
                        selected != null && backend.localReady(selected) -> "Downloaded · Ready for chat: ${selected.name}"
                        selected != null -> "Download complete / already on this phone: ${selected.name}. No download needed; test it below."
                        saved.pendingDownload.isNotBlank() && !downloads.value.active -> "Download interrupted or unfinished. Tap Download to retry."
                        else -> "No local chat model found. Download one or use OpenRouter."
                    }) }
                if (saved.pendingDownload.isNotBlank() && !downloads.value.active) {
                    val completed = scan.models.find { it.name == saved.pendingDownload }
                    val error = downloads.value.takeIf { it.fileName == saved.pendingDownload }?.error
                    if (completed != null) {
                        backend.pendingDownload("")
                        verifyLocal(completed, makeDefault = !backend.saved().cloudSelected)
                    } else if (error != null) state.update { it.copy(localStatus = "Download failed: $error. Retry below.") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(scanning = false, localStatus = "Scan failed. Retry scan or choose an existing file.") } }
            finally {
                scanJob = null
                if (rescanRequested) { rescanRequested = false; refresh() }
            }
        }
    }

    fun chooseLocal(file: File) { state.update { it.copy(localFile = file, localStatus = "Already downloaded: ${file.name}. Test and select below.") } }
    fun downloadLocal() {
        val option = state.value.scan?.recommended ?: return
        if (downloads.value.active || state.value.localBusy) return
        // Re-scan inside the backend before starting: reopening setup must never
        // redownload a model that completed while this screen was away.
        viewModelScope.launch {
            try {
                val installed = backend.scan().models.find { it.name == option.fileName }
                if (installed != null) { chooseLocal(installed); verifyLocal(installed, !backend.saved().cloudSelected); return@launch }
                backend.pendingDownload(option.fileName)
                backend.download(option)
                state.update { it.copy(localStatus = "Downloading ${option.displayName}. OpenRouter setup can run alongside it.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(localStatus = "Download could not start: ${e.message}") } }
        }
    }
    fun verifyLocal(file: File, makeDefault: Boolean = true) {
        if (state.value.localBusy) return
        val revision = if (makeDefault) ++selectionRevision else selectionRevision
        state.update { it.copy(localFile = file, localBusy = true, localStatus = "Downloaded · Testing a real reply on this phone…") }
        viewModelScope.launch {
            try {
                backend.verifyLocal(file)
                if (makeDefault && revision == selectionRevision) backend.selectLocal(file)
                state.update { it.copy(saved = backend.saved(), localStatus = "Downloaded · Ready for chat. ${if (backend.saved().cloudSelected) "OpenRouter stays the default." else "Local AI selected."}") }
            } catch (e: TimeoutCancellationException) { state.update { it.copy(localStatus = "Downloaded, but the reply test timed out. Retry when cool or connect OpenRouter; the download is kept.") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(localStatus = "Downloaded, but not ready: ${e.message}. Retry verification or use OpenRouter.") } }
            finally { state.update { it.copy(localBusy = false) } }
        }
    }
    fun loadModels() {
        if (!state.value.consent || state.value.cloudBusy) return
        state.update { it.copy(cloudBusy = true, cloudStatus = "Loading available tool-capable models…") }
        viewModelScope.launch {
            try {
                val models = backend.models().filter { it.tools }
                state.update { it.copy(models = models, cloudStatus = "Automatic setup chooses free models only. You can also choose one below.") }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(cloudStatus = "Model list unavailable. Your key can still be saved; retry the connection below.") } }
            finally { state.update { it.copy(cloudBusy = false) } }
        }
    }
    fun connectCloud() {
        if (!state.value.canConnect) return
        val candidateKey = state.value.key.trim().ifBlank { backend.savedKey() }
        val selected = state.value.selected
        ++selectionRevision // a completing local test cannot undo this choice
        state.update { it.copy(cloudBusy = true, cloudStatus = "Checking your key with OpenRouter…") }
        viewModelScope.launch {
            var savedThisAttempt = false
            try {
                backend.verifyKey(candidateKey)
                backend.saveKey(candidateKey)
                savedThisAttempt = true
                state.update { it.copy(key = "", saved = backend.saved(), cloudStatus = "Key saved securely · OpenRouter is the default. Finding a working model…") }
                val models = state.value.models.ifEmpty { backend.models().filter { it.tools } }
                state.update { it.copy(models = models) }
                val candidates = BrainConnection.candidates(models.filter { it.free || state.value.allowPaid }, selected)
                check(candidates.isNotEmpty()) { "No free tool-capable model is available. Retry later or explicitly choose a paid model." }
                var lastError = "No model answered"
                for (model in candidates) {
                    state.update { it.copy(cloudStatus = "Key saved · Testing ${model.name}…") }
                    try {
                        withTimeout(30_000) { backend.verifyCloud(candidateKey, model.id) }
                        backend.selectCloud(candidateKey, model.id)
                        state.update { it.copy(saved = backend.saved(), selected = model.id, cloudStatus = "Key saved · Real reply verified · OpenRouter selected. You can continue now.") }
                        return@launch
                    } catch (e: TimeoutCancellationException) { lastError = "Model timed out" }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastError = e.message ?: "Model failed" }
                }
                error("$lastError. Choose another model or retry later.")
            } catch (e: TimeoutCancellationException) { state.update { it.copy(cloudStatus = "Connection timed out. ${if (backend.saved().keyPresent) "Saved key retained." else "Retry to save your key."}") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val safe = (e.message ?: "Connection failed").replace(candidateKey, "[key hidden]").take(350)
                state.update { it.copy(saved = backend.saved(), cloudStatus = "${if (savedThisAttempt) "Key saved; model not ready. " else if (backend.saved().keyPresent) "Previous saved key kept. " else ""}$safe") }
            } finally { state.update { it.copy(cloudBusy = false) } }
        }
    }
    fun testPlanner() {
        if (!state.value.saved.ready || state.value.plannerBusy) return
        state.update { it.copy(plannerBusy = true, plannerStatus = "Checking navigation and conversation context…") }
        viewModelScope.launch {
            try { backend.verifyPlanner(); state.update { it.copy(plannerStatus = "Both app-control checks passed. Tasks still need permissions and approval.") } }
            catch (e: TimeoutCancellationException) { state.update { it.copy(plannerStatus = "Advanced test timed out. Chat is still ready; choose a stronger model for app control.") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(plannerStatus = "Advanced planning not verified. Chat is still ready. Try a stronger model.") } }
            finally { state.update { it.copy(plannerBusy = false) } }
        }
    }
}
