package com.example.meowmba.ui.flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Flow/Desk side dock and the Desk drawers hold (owner, 2026-09-23: "make each drawer on
 * the desk a folder for apps and tools", "a moveable side bar dock that holds 6 apps").
 * Each entry is a key: `app:<package>` or `tool:<Meow menu tile name>`. Stored as preferences.
 */
class HomeShelves(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    /** Apps to start with when nothing is saved yet: pinned first, then most used. */
    defaultApps: () -> List<String>,
) {
    data class State(
        /** Exactly [DOCK_SLOTS] entries; null = empty slot. */
        val dock: List<String?>,
        val dockRight: Boolean,
        /** 0 = top of the screen, 1 = bottom. */
        val dockY: Float,
        val dockCollapsed: Boolean,
        val topDrawer: List<String>,
        val bottomDrawer: List<String>,
    )

    private val _state = MutableStateFlow(load(defaultApps))
    val state: StateFlow<State> = _state

    fun setDockSlot(slot: Int, key: String?) = update { s ->
        s.copy(dock = s.dock.toMutableList().also { if (slot in it.indices) it[slot] = key })
    }

    fun moveDock(right: Boolean, y: Float) = update { it.copy(dockRight = right, dockY = y.coerceIn(0f, 1f)) }

    fun setDockCollapsed(collapsed: Boolean) = update { it.copy(dockCollapsed = collapsed) }

    fun addToDrawer(top: Boolean, key: String) = update { s ->
        if (top) s.copy(topDrawer = (s.topDrawer - key + key).take(DRAWER_MAX)) else s.copy(bottomDrawer = (s.bottomDrawer - key + key).take(DRAWER_MAX))
    }

    fun removeFromDrawer(top: Boolean, key: String) = update { s ->
        if (top) s.copy(topDrawer = s.topDrawer - key) else s.copy(bottomDrawer = s.bottomDrawer - key)
    }

    private fun update(f: (State) -> State) {
        val next = f(_state.value)
        _state.value = next
        write(K_DOCK, next.dock.joinToString(",") { it.orEmpty() })
        write(K_DOCK_POS, "${if (next.dockRight) "right" else "left"}:${next.dockY}:${next.dockCollapsed}")
        write(K_TOP, next.topDrawer.joinToString(","))
        write(K_BOTTOM, next.bottomDrawer.joinToString(","))
    }

    private fun load(defaultApps: () -> List<String>): State {
        val apps by lazy { runCatching(defaultApps).getOrDefault(emptyList()).distinct().map(::app) }
        val dock = read(K_DOCK)?.split(',')?.map { it.takeIf(String::isNotBlank) }
            ?: apps.take(DOCK_SLOTS)
        val pos = read(K_DOCK_POS)?.split(':')
        return State(
            dock = (dock + List(DOCK_SLOTS) { null }).take(DOCK_SLOTS),
            dockRight = pos?.getOrNull(0) != "left",
            dockY = pos?.getOrNull(1)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.45f,
            // Rests hidden on the edge until pulled out, like the Lumen screen's edge tab.
            dockCollapsed = pos?.getOrNull(2)?.toBooleanStrictOrNull() ?: true,
            topDrawer = read(K_TOP)?.split(',')?.filter(String::isNotBlank) ?: DEFAULT_TOOLS.map(::tool),
            bottomDrawer = read(K_BOTTOM)?.split(',')?.filter(String::isNotBlank) ?: apps.take(8),
        )
    }

    companion object {
        const val DOCK_SLOTS = 6
        const val DRAWER_MAX = 16
        private const val K_DOCK = "home_dock_items"
        private const val K_DOCK_POS = "home_dock_position"
        private const val K_TOP = "desk_drawer_top"
        private const val K_BOTTOM = "desk_drawer_bottom"

        /** Meow tools that can live in a drawer or the dock (names match the Meow menu tiles). */
        val TOOLS = listOf("Projects", "Notes", "Weather", "Files", "Memory", "Computer", "Agents", "Tools",
            "Brain", "Privacy", "Settings", "Chat", "Apps", "Browser", "Auto", "Cozmo", "LOOI")
        val DEFAULT_TOOLS = listOf("Projects", "Notes", "Weather", "Files", "Memory", "Computer")

        fun app(pkg: String) = "app:$pkg"
        fun tool(name: String) = "tool:$name"
        fun isApp(key: String) = key.startsWith("app:")
        fun packageOf(key: String) = key.removePrefix("app:")
        fun toolOf(key: String) = key.removePrefix("tool:")
    }
}
