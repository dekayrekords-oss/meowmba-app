package com.example.meowmba.core.memory

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.example.meowmba.bridge.MeowmbaRuntimeService
import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Talks to the MetaMemory app's export surface (and uses our own runtime service
 * for local inventory). Pulls glasses events that MetaMemory pushed into us,
 * and can ping MetaMemory when installed.
 *
 * For v1, "pull" returns memories we already received via [MeowmbaRuntimeService]
 * MSG_PUSH_MEMORY (tags contain meta_glasses / meta_memory). Push is a no-op
 * toward MetaMemory until it exposes a reverse ContentProvider — local Room
 * already holds what glasses sent.
 */
class IpcMetaMemoryConnector(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
) : MetaMemoryConnector {

    companion object {
        private const val TAG = "MetaMemoryIpc"
        private const val META_PACKAGE = "com.meow.metamemory"
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(META_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override suspend fun push(memories: List<MemoryEntity>): Result<Int> {
        // MetaMemory is the glasses producer; Meowmba is the shared brain.
        // Outbound push to MetaMemory is not required for capture→chat flow.
        return Result.success(0)
    }

    override suspend fun pull(): Result<List<MemoryEntity>> = withContext(Dispatchers.IO) {
        // Glasses memories already live in our Room DB after MSG_PUSH_MEMORY.
        val list = memoryRepository.getRecentMemories(200)
            .filter { m ->
                m.tags.any {
                    it.contains("meta", ignoreCase = true) ||
                        it.contains("glasses", ignoreCase = true)
                }
            }
        Result.success(list)
    }

    /**
     * Optional: ask MetaMemory (if it ever exposes a pull service). Currently
     * unused — kept for symmetry with the connector contract.
     */
    fun pingLocalRuntime(): Boolean {
        val latch = CountDownLatch(1)
        val ok = AtomicReference(false)
        val replyTo = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MeowmbaRuntimeService.MSG_REPLY) {
                    ok.set(msg.data.getBoolean(MeowmbaRuntimeService.KEY_OK, false))
                    latch.countDown()
                }
            }
        })
        var messenger: Messenger? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                messenger = Messenger(service)
                val msg = Message.obtain(null, MeowmbaRuntimeService.MSG_PING).also {
                    it.replyTo = replyTo
                    it.data = Bundle()
                }
                runCatching { messenger?.send(msg) }.onFailure { latch.countDown() }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(MeowmbaRuntimeService.ACTION)
            .setClassName(context.packageName, MeowmbaRuntimeService::class.java.name)
        if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) return false
        return try {
            latch.await(2, TimeUnit.SECONDS)
            ok.get()
        } finally {
            runCatching { context.unbindService(conn) }
        }
    }
}

/** Import a glasses caption into Meowmba as a tagged episodic memory. */
suspend fun MemoryRepository.importGlassesMemory(
    summary: String,
    analysis: String,
    source: String = "meta_memory",
) {
    val content = if (analysis.startsWith(summary)) analysis else "$summary\n$analysis"
    addMemory(
        content = content.take(2000),
        type = MemoryType.EPISODIC,
        importance = ImportanceLevel.MEDIUM,
        tags = listOf("meta_glasses", "meta_memory", source),
    )
    Log.i("MetaMemoryIpc", "imported glasses memory: ${summary.take(80)}")
}
