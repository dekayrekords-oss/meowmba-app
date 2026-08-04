package com.example.meowmba.core.media

/**
 * Raw JNI declarations only — no logic. Kept separate from
 * [SDCppImageGenerator] so the Kotlin-side error handling and marshalling
 * can be unit-tested against a fake implementation of this interface,
 * without a real .so.
 */
internal interface SDCppNative {
    fun createContext(modelPath: String, nThreads: Int): Long
    fun generate(
        ctxPtr: Long,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        outDims: IntArray
    ): ByteArray?
    fun freeContext(ctxPtr: Long)
}

internal object SDCppJniNative : SDCppNative {
    init {
        System.loadLibrary("sd_jni_bridge")
    }

    external override fun createContext(modelPath: String, nThreads: Int): Long
    external override fun generate(
        ctxPtr: Long,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        outDims: IntArray
    ): ByteArray?
    external override fun freeContext(ctxPtr: Long)
}
