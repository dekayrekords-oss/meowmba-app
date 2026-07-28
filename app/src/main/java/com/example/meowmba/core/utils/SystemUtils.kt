package com.example.meowmba.core.utils

import android.app.ActivityManager
import android.content.Context
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
}
