package com.example.meowmba.core.agent.runtime

internal object NativeCpuCapabilities {
    init { System.loadLibrary("meow_cpu_features") }
    external fun arm64Hwcap(): Long
}

/** The pinned Needle 3 archive contains SDOT instructions. Check before loading it. */
internal object NeedleCpuPolicy {
    const val DOT_PRODUCT = 1L shl 20 // Linux arm64 HWCAP_ASIMDDP.
    const val UNAVAILABLE = "This phone doesn't support the local Needle action model. Other configured assistants and basic commands remain available."
    fun supported(hwcap: Long): Boolean = hwcap and DOT_PRODUCT != 0L
    fun <T> loadWhenSupported(hwcap: Long, loadRuntime: () -> T): T {
        if (!supported(hwcap)) throw UnsupportedOperationException(UNAVAILABLE)
        return loadRuntime()
    }
}

internal fun defaultNeedleNative(): NeedleNative =
    NeedleCpuPolicy.loadWhenSupported(NativeCpuCapabilities.arm64Hwcap()) { NeedleJniNative }

private object NeedleJniNative : NeedleNative {
    init { System.loadLibrary("needle_jni_bridge") }
    external override fun load(bytes: ByteArray): Int
    external override fun initialize(systemPrompt: String, toolsJson: String): Int
    external override fun complete(input: String, maxTokens: Int): String?
    external override fun reset()
}

