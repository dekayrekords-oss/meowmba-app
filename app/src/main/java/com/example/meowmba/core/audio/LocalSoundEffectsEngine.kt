package com.example.meowmba.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.meowmba.R

/**
 * Low-latency sound effects engine using SoundPool.
 * All sounds are pre-loaded at init from res/raw — no network, no streaming.
 * Designed for rapid-fire SFX triggered by animation keyframes.
 */
class LocalSoundEffectsEngine(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sfxMap: Map<String, Int> = buildMap {
        runCatching { put("meow", pool.load(context, R.raw.sfx_meow, 1)) }
        runCatching { put("purr", pool.load(context, R.raw.sfx_purr, 1)) }
        runCatching { put("trill", pool.load(context, R.raw.sfx_trill, 1)) }
        runCatching { put("chirp", pool.load(context, R.raw.sfx_chirp, 1)) }
        runCatching { put("boop", pool.load(context, R.raw.sfx_boop, 1)) }
        runCatching { put("gasp", pool.load(context, R.raw.sfx_gasp, 1)) }
    }

    /**
     * Play a named sound effect. No-op if the name is not registered.
     * @param sfxName One of: meow, purr, trill, chirp, boop, gasp
     * @param volume 0f..1f
     */
    fun play(sfxName: String, volume: Float = 1f) {
        val id = sfxMap[sfxName]
        if (id == null) {
            Log.w("SFX", "Unknown sound effect: $sfxName")
            return
        }
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
