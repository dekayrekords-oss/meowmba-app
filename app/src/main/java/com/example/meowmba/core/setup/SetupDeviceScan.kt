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
    val tier: DeviceTier get() = when {
        !arm64 || preferClassic || cores <= 4 -> DeviceTier.LIGHT
        ramGb < 7 -> DeviceTier.BALANCED
        else -> DeviceTier.CAPABLE
    }
    val recommendedFps: Int get() = when (tier) {
        DeviceTier.LIGHT -> 15
        DeviceTier.BALANCED -> 24
        DeviceTier.CAPABLE -> 30
    }
    val recommendPhoneComputer: Boolean get() = arm64 && !lowRam && ramGb >= 7 && cores >= 6 && freeBytes >= 8_000_000_000L
    val computerReason: String get() = when {
        !arm64 -> "The phone Computer needs a supported 64-bit processor."
        lowRam || ramGb < 7 -> "The phone Computer shares memory with Meow. A connected computer is recommended with less than 7 GB of usable RAM."
        cores < 6 -> "A connected computer is recommended to leave processing capacity for Meow."
        freeBytes < 8_000_000_000L -> "Keep at least 8 GB free for Ubuntu, tools and working files."
        else -> "Phone Computer setup is available on demand. Larger jobs can still heat the phone."
    }
    val summary: String get() = "${tier.label} profile recommends ${recommendedFps} FPS animations" +
        (if (tier == DeviceTier.LIGHT) ", a simpler avatar and a still background." else ".") +
        "\n" + computerReason
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

enum class DeviceTier(val label: String) { LIGHT("Light"), BALANCED("Balanced"), CAPABLE("Full") }

data class SetupScan(val profile: SetupDeviceProfile, val models: List<File>, val recommended: LocalModelOption?)

object SetupDeviceScan {
    /** Bounded metadata scan of accessible folders only. Android does not allow
     * silently searching another app's private storage; the picker handles that. */
    fun modelsIn(roots: List<File>, configured: String?): List<File> =
        (listOfNotNull(configured?.let(::File)) + roots.flatMap { root ->
            root.walkTopDown().maxDepth(3).take(1000).filter { it.isFile }.toList()
        }).filter { LocalChatModel.supports(it) && it.isFile && it.length() > 0 }
            .distinctBy { it.canonicalPath }.sortedBy { it.name.lowercase() }

    /** Cheap OS metadata only; no model, network, sensor permission or stress test. */
    fun profile(context: Context): SetupDeviceProfile {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return SetupDeviceProfile(info.totalMem / 1_073_741_824.0, context.filesDir.usableSpace,
            Runtime.getRuntime().availableProcessors(), manager.isLowRamDevice, "arm64-v8a" in Build.SUPPORTED_ABIS)
    }

    /** Missing preferences only: rerunning setup never silently replaces explicit choices. */
    fun defaults(profile: SetupDeviceProfile): Map<String, String> = buildMap {
        put("avatar_fps_target", profile.recommendedFps.toString())
        if (profile.tier == DeviceTier.LIGHT) {
            put("avatar_style", "classic")
            put("background_kind", "NONE")
            put("background_value", "")
        }
    }

    fun applyDefaults(profile: SetupDeviceProfile, storage: SecureStorageManager) {
        defaults(profile).forEach { (key, value) ->
            if (storage.getPreference(key) == null) storage.savePreference(key, value)
        }
        storage.savePreference("device_profile", profile.tier.name)
        storage.saveBooleanPreference("setup_device_defaults_applied", true)
    }

    fun scan(context: Context, storage: SecureStorageManager): SetupScan {
        val profile = profile(context)
        val roots = listOfNotNull(File(context.filesDir, "models"), context.getExternalFilesDir(null)?.let { File(it, "models") },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS))
        val models = modelsIn(roots, storage.getEndpoint("local"))
        applyDefaults(profile, storage)
        return SetupScan(profile, models, profile.recommendedModel())
    }
}
