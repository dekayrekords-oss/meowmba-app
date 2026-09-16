package com.example.meowmba.core.setup

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.meowmba.core.agent.LocalChatModel
import com.example.meowmba.core.agent.LocalModelOption
import com.example.meowmba.core.agent.ModelCatalog
import com.example.meowmba.core.security.SecureStorageManager
import java.io.File

data class SetupDeviceProfile(val ramGb: Double, val freeBytes: Long, val cores: Int, val lowRam: Boolean, val arm64: Boolean) {
    // Conservative first-run defaults, not a claimed GPU benchmark. Never change
    // a user's explicit avatar choice on every launch.
    val preferClassic: Boolean get() = lowRam || ramGb < 4.5 || (cores <= 4 && ramGb < 5.5)
    val supportsLocal: Boolean get() = arm64 && !lowRam && ramGb >= 2.7
    fun recommendedModel(): LocalModelOption? {
        if (!supportsLocal) return null
        val candidates = when {
            ramGb >= 7 -> listOf("gemma-4-e2b", "qwen-1.5b", "qwen-0.5b")
            ramGb >= 5 -> listOf("qwen-1.5b", "qwen-0.5b")
            else -> listOf("qwen-0.5b")
        }
        return candidates.mapNotNull { id -> ModelCatalog.options.find { it.id == id } }
            .firstOrNull { freeBytes >= requiredSpace(it) }
    }
    companion object {
        fun requiredSpace(option: LocalModelOption): Long = when (option.id) {
            "gemma-4-e2b" -> 4_000_000_000L
            "qwen-1.5b", "qwen-1.5b-lc" -> 2_500_000_000L
            else -> 1_000_000_000L
        }
    }
}

data class SetupScan(val profile: SetupDeviceProfile, val models: List<File>, val recommended: LocalModelOption?)

object SetupDeviceScan {
    /** Bounded metadata scan of accessible folders only. Android does not allow
     * silently searching another app's private storage; the picker handles that. */
    fun modelsIn(roots: List<File>, configured: String?): List<File> =
        (listOfNotNull(configured?.let(::File)) + roots.flatMap { root ->
            root.walkTopDown().maxDepth(3).take(1000).filter { it.isFile }.toList()
        }).filter { LocalChatModel.supports(it) && it.isFile && it.length() > 0 }
            .distinctBy { it.canonicalPath }.sortedBy { it.name.lowercase() }

    fun scan(context: Context, storage: SecureStorageManager): SetupScan {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val profile = SetupDeviceProfile(info.totalMem / 1_073_741_824.0, context.filesDir.usableSpace,
            Runtime.getRuntime().availableProcessors(), manager.isLowRamDevice, "arm64-v8a" in Build.SUPPORTED_ABIS)
        val roots = listOfNotNull(File(context.filesDir, "models"), context.getExternalFilesDir(null)?.let { File(it, "models") },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS))
        val models = modelsIn(roots, storage.getEndpoint("local"))
        if (!storage.getBooleanPreference("setup_device_defaults_applied", false)) {
            if (profile.preferClassic && storage.getPreference("avatar_style") == null) storage.savePreference("avatar_style", "classic")
            storage.saveBooleanPreference("setup_device_defaults_applied", true)
        }
        return SetupScan(profile, models, profile.recommendedModel())
    }
}
