package com.example.meowmba.core.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object SystemUtils {
    fun getTotalRamGb(context: Context): Double {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    }

    fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    fun getRecommendedModel(ramGb: Double): String {
        return when {
            ramGb >= 8.0 -> "Gemma 7b (Recommended)"
            ramGb >= 4.0 -> "Gemma 2b (Recommended)"
            else -> "TinyLlama (Recommended for low RAM)"
        }
    }

    /** Check whether Google AI Core (Gemini Nano) is available on this device. */
    fun hasGeminiNano(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.ai.core", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Check whether the device has a hardware NPU / DSP for on-device AI. */
    fun hasNpu(): Boolean {
        val props = listOf(
            "ro.chipname", "ro.board.platform", "ro.hardware"
        )
        // Exynos, Tensor, Snapdragon NPU, MediaTek APU, Apple Neural Engine
        val npuHints = listOf("exynos", "tensor", "mtk", "qcom", "samsung")
        return props.any { prop ->
            val value = try {
                Build::class.java.getDeclaredField(prop).apply { isAccessible = true }.get(null) as? String
            } catch (_: Exception) { null }
            value?.let { npuHints.any { hint -> it.lowercase().contains(hint) } } ?: false
        }
    }

    /** Returns a map of detected hardware capabilities for the UI. */
    data class HardwareCapabilities(
        val ramGb: Double,
        val cpuCores: Int,
        val hasGeminiNano: Boolean,
        val hasNpu: Boolean,
        val androidApi: Int,
        val is64Bit: Boolean
    ) {
        val recommendedModel: String
            get() = getRecommendedModel(ramGb)
    }

    fun getCapabilities(context: Context): HardwareCapabilities {
        return HardwareCapabilities(
            ramGb = getTotalRamGb(context),
            cpuCores = getCpuCores(),
            hasGeminiNano = hasGeminiNano(context),
            hasNpu = hasNpu(),
            androidApi = Build.VERSION.SDK_INT,
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        )
    }
}
