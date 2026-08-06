package com.example.meowmba.core.agent

import android.util.Log
import com.example.meowmba.core.bridge.DesktopBridgeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dual-mode intent router: decides whether to execute locally on-device
 * or delegate to the home server (meow) via the desktop bridge.
 *
 * Policy:
 * - LOCAL_ONLY: device settings, notifications, sensors, alarms, timers, flashlight, DND
 * - REMOTE_PREFERRED: code gen, large model prompts, file operations, multi-agent tasks
 * - HYBRID: use local model if disconnected, remote if connected
 */
enum class ExecutionMode {
    /** Always on-device, never needs the server. */
    LOCAL_ONLY,
    /** Prefers remote server but can fall back to local model. */
    REMOTE_PREFERRED,
    /** Must use remote (file writes, builds, desktop control). */
    REMOTE_REQUIRED,
}

enum class EdgeState {
    /** No bridge connection. All processing is local. */
    OFFLINE,
    /** Bridge reachable. Heavy tasks delegate to server. */
    SYNCED,
    /** Bridge is reconnecting. Queued items pending. */
    RECONNECTING,
}

data class RoutingDecision(
    val mode: ExecutionMode,
    val reason: String,
)

class DualModeRouter(
    private val bridgeManager: DesktopBridgeManager?,
) {
    companion object {
        private const val TAG = "DualModeRouter"

        /** Tool IDs that always execute locally. */
        private val LOCAL_TOOLS = setOf(
            "phone.set_alarm", "phone.set_timer", "phone.flashlight",
            "phone.dnd", "phone.battery", "phone.brightness", "phone.volume",
            "phone.location", "notification.list", "notification.summary",
            "phone.launch_app", "phone.list_apps", "ui.automate",
        )

        /** Tool IDs that require the remote bridge. */
        private val REMOTE_TOOLS = setOf(
            "desktop.write_file", "desktop.run_build", "desktop.run_script",
            "desktop.control",
        )
    }

    private val _edgeState = MutableStateFlow(EdgeState.OFFLINE)
    val edgeState: StateFlow<EdgeState> = _edgeState.asStateFlow()

    /** Check bridge connectivity and update state. */
    suspend fun refreshState() {
        if (bridgeManager == null || !bridgeManager.isPaired()) {
            _edgeState.value = EdgeState.OFFLINE
            return
        }
        val pingResult = bridgeManager.ping()
        _edgeState.value = if (pingResult.isSuccess) EdgeState.SYNCED else EdgeState.OFFLINE
        Log.d(TAG, "Edge state: ${_edgeState.value}")
    }

    fun isConnected(): Boolean = _edgeState.value == EdgeState.SYNCED

    /** Decide execution mode for a given tool invocation. */
    fun route(toolId: String): RoutingDecision {
        return when {
            toolId in LOCAL_TOOLS -> RoutingDecision(ExecutionMode.LOCAL_ONLY, "Device tool — always local")
            toolId in REMOTE_TOOLS -> {
                if (isConnected()) RoutingDecision(ExecutionMode.REMOTE_REQUIRED, "Desktop tool — bridge connected")
                else RoutingDecision(ExecutionMode.REMOTE_REQUIRED, "Desktop tool — QUEUED (offline)")
            }
            else -> {
                if (isConnected()) RoutingDecision(ExecutionMode.REMOTE_PREFERRED, "Prefer server model")
                else RoutingDecision(ExecutionMode.REMOTE_PREFERRED, "Using local model (offline)")
            }
        }
    }

    /** Decide whether to use local or remote model for a prompt. */
    fun shouldUseRemoteModel(): Boolean = isConnected()
}
