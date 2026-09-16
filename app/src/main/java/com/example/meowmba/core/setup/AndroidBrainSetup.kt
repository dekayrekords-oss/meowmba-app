package com.example.meowmba.core.setup

import android.content.Context
import android.net.Uri
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.download.DownloadService
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.workflows.WorkflowRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

class AndroidBrainSetup(context: Context) : BrainSetupBackend {
    private val context = context.applicationContext
    private val storage = SecureStorageManager(this.context)
    override val downloads = DownloadService.status
    private val directory = File(this.context.filesDir, "models")
    override fun saved() = SavedBrain(!storage.getApiKey("openrouter").isNullOrBlank(),
        storage.getPreference("brain_provider") == "openrouter",
        storage.getBooleanPreference("openrouter_cloud_consent", false) || storage.getPreference("brain_provider") == "openrouter",
        SetupWiring.isBrainReady(context), storage.getModelName("openrouter").orEmpty(), storage.getEndpoint("local"),
        storage.getPreference("setup_pending_download").orEmpty())
    override fun savedKey() = storage.getApiKey("openrouter").orEmpty()
    override suspend fun scan() = withContext(Dispatchers.IO) { SetupDeviceScan.scan(context, storage) }
    override suspend fun verifyKey(key: String) = withContext(Dispatchers.IO) { BrainConnection.verifyKey(key) }
    override suspend fun saveKey(key: String) = withContext(Dispatchers.IO) {
        storage.saveSetupSelection(mapOf("brain_provider" to "openrouter", "default_brain" to "openrouter",
            "selected_model" to "Omniroute (Local router)"), "openrouter", key)
        storage.saveBooleanPreference("openrouter_cloud_consent", true)
    }
    override suspend fun deselectCloud() = withContext(Dispatchers.IO) {
        storage.saveSetupSelection(mapOf("brain_provider" to "local", "default_brain" to "local", "selected_model" to "Omniroute (Local router)"))
        storage.saveBooleanPreference("openrouter_cloud_consent", false)
    }
    override suspend fun models() = OpenRouterModels.fetch().getOrThrow()
    override suspend fun verifyCloud(key: String, model: String) = withContext(Dispatchers.IO) {
        RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", key, model).use { BrainConnection.verifyChat(it) }
    }
    override suspend fun selectCloud(key: String, model: String) = withContext(Dispatchers.IO) {
        storage.saveSetupSelection(mapOf("brain_provider" to "openrouter", "default_brain" to "openrouter",
            "selected_model" to "Omniroute (Local router)", "chat_verified_openrouter" to PlannerReadiness.cloudStamp(model, key)),
            "openrouter", key, model)
    }
    override suspend fun verifyLocal(file: File) = withContext(Dispatchers.IO) {
        withLocalProvider(file) { BrainConnection.verifyChat(it) }
        storage.saveSetupSelection(mapOf("chat_verified_local" to SetupWiring.localStamp(file)))
        storage.saveVerifiedEndpoint("local", file.path)
    }
    override fun localReady(file: File): Boolean = file.isFile && file.length() > 0 &&
        (storage.getPreference("chat_verified_local") == SetupWiring.localStamp(file) || storage.getPreference("planner_verified_local") == file.path)
    override suspend fun selectLocal(file: File) = withContext(Dispatchers.IO) {
        storage.saveVerifiedEndpoint("local", file.path)
        storage.saveSetupSelection(mapOf("brain_provider" to "local", "default_brain" to "local", "selected_model" to "Omniroute (Local router)"))
    }
    override suspend fun pendingDownload(fileName: String) = withContext(Dispatchers.IO) {
        storage.saveSetupSelection(mapOf("setup_pending_download" to fileName))
    }
    override suspend fun download(option: LocalModelOption) {
        check(context.filesDir.usableSpace >= SetupDeviceProfile.requiredSpace(option)) { "Not enough free storage. Free space or connect OpenRouter." }
        DownloadService.start(context, option.downloadUrl, option.fileName, option.id, null, directory)
    }
    private suspend fun withLocalProvider(file: File, block: suspend (ModelProvider) -> Unit) {
        check(LocalChatModel.supports(file) && file.isFile && file.length() > 0) { "Choose a downloaded .task or .litertlm chat model" }
        val runtime = WorkflowRuntime.get(context)
        runtime.mutex.withLock {
            check(runtime.coolEnough()) { "Let the phone cool and turn off power-saving mode before testing local AI" }
            runtime.releaseModel()
            val info = android.app.ActivityManager.MemoryInfo()
            context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
            check(!info.lowMemory && info.availMem > 900L * 1024 * 1024 && file.length() <= info.totalMem * 0.45) {
                "Not enough memory for this model. Close other apps or use a smaller model / OpenRouter"
            }
            val provider: ModelProvider = if (file.extension.equals("litertlm", true)) LiteRtProvider(context, file.path)
                else MediaPipeProvider(context, file.path, maxTokens = if (file.name.contains("4096")) 4096 else 1280)
            try { block(provider) }
            finally {
                withContext(NonCancellable) {
                    when (provider) { is LiteRtProvider -> provider.release(); is MediaPipeProvider -> provider.release() }
                }
            }
        }
    }
    override suspend fun verifyPlanner() = withContext(Dispatchers.IO) {
        val choice = saved()
        if (choice.cloudSelected) {
            val key = savedKey()
            RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", key, choice.model).use { PlannerReadiness.verify(it) }
            storage.saveSetupSelection(mapOf("planner_verified_openrouter" to PlannerReadiness.cloudStamp(choice.model, key)))
        } else {
            val file = File(checkNotNull(choice.localPath))
            withLocalProvider(file) { PlannerReadiness.verify(it) }
            storage.saveSetupSelection(mapOf("planner_verified_local" to file.path))
        }
    }
    /** Android's document picker grants access to exactly the user-selected file.
     * Never copy credentials into logs or request broad phone-storage access. */
    suspend fun importModel(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: error("Could not read the selected filename")
        check(name == File(name).name && name.matches(Regex("[a-zA-Z0-9._ -]+")) && LocalChatModel.supports(File(name))) {
            "Choose a .task or .litertlm chat model. Needle, voice files and GGUF image weights are not chat bundles."
        }
        directory.mkdirs()
        val target = File(directory, name)
        if (target.isFile && target.length() > 0) return@withContext target
        val pending = File.createTempFile("import-", ".part", directory)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> pending.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    check(directory.usableSpace > count + 250_000_000L) { "Not enough free storage to import this model" }
                    output.write(buffer, 0, count)
                }
            } } ?: error("Android did not grant access to this file")
            check(pending.length() > 0 && pending.renameTo(target)) { "Import did not finish. Choose the file again." }
            target
        } finally { pending.delete() }
    }
}
