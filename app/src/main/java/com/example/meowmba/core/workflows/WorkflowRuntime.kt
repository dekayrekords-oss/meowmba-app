package com.example.meowmba.core.workflows

import android.content.Context
import android.os.PowerManager
import androidx.work.*
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkflowRuntime private constructor(context: Context) {
    private val context = context.applicationContext
    val store = WorkflowStore(File(this.context.filesDir, "business_agents/state.json"))
    val workspace = File(this.context.filesDir, "memba_workspace/business")
    private val thermalGate = WorkflowThermalGate(store.state.value.runs.any { it.status == "PAUSED" })
    val tools get() = WorkflowTools.local(workspace).also { registry ->
        WorkflowMcpTools.register(registry, store.state.value.connections,
            { id -> store.state.value.connections.find { it.id == id && it.enabled } },
            { id -> storage.getApiKey("business_mcp_$id").orEmpty() })
    }
    private var providerKey = ""
    private var provider: ModelProvider? = null
    private var testingLocalPath: String? = null
    private val storage by lazy { SecureStorageManager(this.context) }
    val mutex = Mutex()
    val brain = WorkflowModelBrain { agent ->
        val key: String
        val create: () -> ModelProvider
        if (agent.provider == "openrouter") {
            check(agent.cloudConsent) { "Cloud access is not approved for this agent" }
            val apiKey = storage.getApiKey("openrouter")?.takeIf { it.isNotBlank() } ?: error("Connect OpenRouter in Your agent team")
            val model = agent.model.ifBlank { storage.getModelName("openrouter") ?: "openrouter/free" }
            key = "openrouter:$model:$apiKey"
            create = { RemoteModelProvider("OpenRouter", "https://openrouter.ai/api", apiKey, model) }
        } else {
            val path = testingLocalPath ?: storage.getEndpoint("local") ?: error("Download and select a local conversation model in Hardware first. Needle only routes actions.")
            check(File(path).isFile && (path.endsWith(".task") || path.endsWith(".litertlm"))) { "Select a .task or .litertlm conversation model in Hardware. Needle is not a conversation model." }
            val info = android.app.ActivityManager.MemoryInfo()
            context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
            check(!info.lowMemory && info.availMem > 900L * 1024 * 1024) { "Not enough free memory for local planning. Close other apps, then start another job." }
            key = path
            create = { if (path.endsWith(".litertlm")) LiteRtProvider(this.context, path) else MediaPipeProvider(this.context, path, maxTokens = if (path.contains("4096")) 4096 else 1280) }
        }
        if (providerKey != key || provider == null) { releaseModel(); provider = create(); providerKey = key }
        provider!!
    }
    suspend fun releaseModel() = withContext(NonCancellable + Dispatchers.IO) {
        when (val old = provider) {
            is RemoteModelProvider -> old.close()
            is LiteRtProvider -> old.release()
            is MediaPipeProvider -> old.release()
        }
        provider = null; providerKey = ""
    }
    /** Caller holds the execution mutex; never replaces a working choice until
     * the candidate produces a real structured answer. */
    suspend fun verifyAndSelectLocal(path: String) {
        check(coolEnough()) { "Let the phone cool and turn off power-saving mode before testing" }
        testingLocalPath = path
        try {
            val agent = BusinessAgent(name = "Model test", instructions = "Return exactly one JSON decision: action blocked, summary Model ready.", tools = emptySet())
            val run = BusinessRun(agentId = agent.id, goal = "Test structured output. Return blocked with summary Model ready.")
            val decision = kotlinx.coroutines.withTimeout(90_000) { brain.decide(agent, run.frames.first(), run, emptyList(), emptyList()) }
            check(decision.action == "blocked" && decision.summary.isNotBlank()) { "Model did not return a valid structured decision" }
            withContext(Dispatchers.IO) { storage.saveVerifiedEndpoint("local", path) }
        } finally { testingLocalPath = null; releaseModel() }
    }
    fun coolEnough(): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val tenthsC = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return thermalGate.allowsRun(tenthsC, power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE, power.isPowerSaveMode)
    }
    fun enqueue(agentId: String, goal: String): String = store.enqueue(agentId, goal).also { wake() }
    fun wake() {
        // Independent kicks share the execution mutex. Chaining them behind
        // a worker in thermal retry/backoff made new jobs and approvals wait
        // behind an obsolete job, even after that job had been cancelled.
        // Never REPLACE a running worker: it might be writing externally.
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BusinessWorker>()
                .addTag("business-dispatch")
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun ensureSchedules() {
        val manager = WorkManager.getInstance(context)
        if (store.state.value.agents.any { it.enabled && it.intervalMinutes >= 15 }) {
            manager.enqueueUniquePeriodicWork("business-schedules", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BusinessWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        } else manager.cancelUniqueWork("business-schedules")
    }
    fun recover() {
        ensureSchedules()
        if (store.state.value.runs.any { it.status in setOf("QUEUED", "RUNNING", "PAUSED") }) wake()
    }
    fun exportAndRemove(id: String) {
        val run = store.run(id) ?: error("Job not found")
        check(run.status in WorkflowStore.TERMINAL) { "Stop the job before archiving" }
        val folder = File(workspace, "job-history").also { it.mkdirs() }
        val target = File(folder, "$id-${System.currentTimeMillis()}.json")
        val bytes = Json { prettyPrint = true }.encodeToString(run).toByteArray()
        val pending = java.nio.file.Files.createTempFile(folder.toPath(), ".history-", ".pending")
        try {
            java.io.FileOutputStream(pending.toFile()).use { it.write(bytes); it.fd.sync() }
            java.nio.file.Files.move(pending, target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { java.nio.file.Files.deleteIfExists(pending) }
        check(target.readBytes().contentEquals(bytes)) { "History export could not be verified; the job was kept" }
        store.removeRun(id)
    }
    companion object {
        @Volatile private var instance: WorkflowRuntime? = null
        fun get(context: Context): WorkflowRuntime = instance ?: synchronized(this) {
            instance ?: WorkflowRuntime(context).also { instance = it }
        }
    }
}

/** WorkManager owns restart/reboot scheduling. Jobs checkpoint each step and
 * yield below Android's worker time limit. Android force-stop suspends work
 * until the user opens the app again; it is never advertised as a 24/7 daemon. */
class BusinessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val runtime = WorkflowRuntime.get(applicationContext)
        return runtime.mutex.withLock {
            try {
            runtime.store.scheduleDue(System.currentTimeMillis())
            val engine = WorkflowEngine(runtime.store, runtime.tools, runtime.brain, runtime::coolEnough)
            val start = android.os.SystemClock.elapsedRealtime()
            while (android.os.SystemClock.elapsedRealtime() - start < 180_000) {
                val run = runtime.store.state.value.runs.firstOrNull { it.status in setOf("QUEUED", "RUNNING", "PAUSED") } ?: return@withLock Result.success()
                if (!runtime.coolEnough()) {
                    engine.tick(run.id)
                    return@withLock Result.retry()
                }
                engine.tick(run.id)
                runtime.store.run(run.id)?.takeIf { it.status != run.status }?.let {
                    runCatching { BusinessNotifications.post(applicationContext, it) }
                }
            }
            Result.retry()
            } finally { runtime.releaseModel() }
        }
    }
}
