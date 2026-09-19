package com.example.meowmba.ui.companion

import com.example.meowmba.core.agentic.ConnectionProfile

internal const val COMPUTER_TOOLS_TITLE = "Computer & task tools"

internal enum class ComputerToolsPage(val title: String, val setup: Boolean = false) {
    HOME(COMPUTER_TOOLS_TITLE),
    SETUP("Set up Computer", true),
    PHONE_INSTALL("Get Termux", true),
    PHONE_PREPARE("Prepare your workspace", true),
    PHONE_OPEN("Open your Computer", true),
    SERVER_ADDRESS("Choose your server", true),
    SERVER_SIGN_IN("Sign in to your server", true),
    SERVER_REVIEW("Check your connection", true),
    TASKS("Tasks & approvals"), MODEL("AI for tasks"),
    HUB("HUB mode"), PERSONAL("Personal companion"),
    LEGACY_TASKS("Legacy tasks"),
    PROFILES("Saved connections"), POWER("Power & sleep"), DETAILS("Connection details & logs"),
}

/** A successful check belongs to these exact connection details, never merely a status label. */
internal fun sameComputerConnection(a: ConnectionProfile?, b: ConnectionProfile?): Boolean =
    a != null && b != null && a.id == b.id && a.type == b.type && a.host == b.host &&
        a.port == b.port && a.username == b.username && a.credentialRef == b.credentialRef &&
        a.workspaceRoot == b.workspaceRoot && a.hostKeyFingerprint == b.hostKeyFingerprint &&
        a.pinHostKeyOnFirstConnect == b.pinHostKeyOnFirstConnect
