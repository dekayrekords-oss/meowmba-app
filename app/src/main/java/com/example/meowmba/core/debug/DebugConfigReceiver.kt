package com.example.meowmba.core.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.meowmba.BuildConfig
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Debug-only configuration hook (no-op in release builds). Lets a connected
 * computer arm the wake word and save brain API keys without touching the
 * Settings UI, which cannot be driven reliably via adb on this launcher.
 *
 *   adb shell am broadcast -n \$PKG/.core.debug.DebugConfigReceiver \
 *     -a \$PKG.debug.CONFIG --ez wake_word true
 *
 * After arming the wake word, force-stop and relaunch the app so the ViewModel
 * reloads the pref and arms the WakeStandby service on first resume.
 */
class DebugConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return

        val storage = SecureStorageManager(context)
        if (intent.getBooleanExtra("wake_word", false)) {
            // Same key as CompanionViewModel.KEY_WAKE_WORD; the ViewModel reads
            // it at construction and arms the wake phrase on first resume.
            storage.saveBooleanPreference("wake_word_enabled", true)
        }
        if (intent.getBooleanExtra("wake_in_face", false)) {
            // CompanionViewModel.KEY_REQUIRE_WAKE_IN_FACE — the hot word gates
            // turns even in face mode, so the flaky always-on recognizer session
            // is never opened.
            storage.saveBooleanPreference("require_wake_in_face", true)
        }
        intent.getStringExtra("openrouter_key")?.takeIf { it.isNotBlank() }?.let { key ->
            storage.saveApiKey("openrouter", key)
        }
        intent.getStringExtra("opencode_key")?.takeIf { it.isNotBlank() }?.let { key ->
            storage.saveApiKey("opencode", key)
        }
        intent.getStringExtra("default_brain")?.takeIf { it.isNotBlank() }?.let { brain ->
            storage.savePreference("default_brain", brain)
        }
        // Which left/right encoding the LOOI base obeys (legacy/inverted/captured).
        // Toggle the robot body off and on in Settings to apply it to a live link.
        intent.getStringExtra("looi_turn")?.takeIf { it.isNotBlank() }?.let { mode ->
            storage.savePreference("looi_turn_encoding", mode.trim().lowercase())
        }
        // Feature flags (Phase 0-2 Lumen/agentic scaffolding). Defaults remain OFF
        // unless explicitly set; see core/config/FeatureFlags.kt.
        if (intent.hasExtra("lumen_reflex_v2")) {
            storage.saveBooleanPreference(
                com.example.meowmba.core.config.FeatureFlags.KEY_LUMEN_REFLEX_V2,
                intent.getBooleanExtra("lumen_reflex_v2", false),
            )
        }
        if (intent.hasExtra("agentic_runtime_v1")) {
            storage.saveBooleanPreference(
                com.example.meowmba.core.config.FeatureFlags.KEY_AGENTIC_RUNTIME_V1,
                intent.getBooleanExtra("agentic_runtime_v1", false),
            )
        }
    }
}
