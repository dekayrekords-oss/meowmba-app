package com.example.meowmba.core.bridge

import kotlinx.serialization.json.*

/** Optional facts from the signed status RPC, not a claim that mixing is ready. */
object LogicProPreflight {
    fun describe(value: JsonElement?): String {
        val data = value as? JsonObject ?: return "Logic Pro readiness not reported by this helper."
        fun flag(key: String) = (data[key] as? JsonPrimitive)?.booleanOrNull
        if (flag("platform_supported") != true) return "Logic Pro control requires a Mac."
        if (flag("installed") != true) return "Logic Pro not found in the Mac's standard Applications folders."
        val version = (data["version"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.matches(Regex("[0-9]+(?:[.][0-9]+){0,4}")) }
        val access = when {
            flag("ui_enabled") != true -> "Desktop UI control is off."
            flag("accessibility_trusted") == false -> "macOS Accessibility access is not granted to the helper."
            flag("accessibility_trusted") == true -> "Helper Accessibility access is granted; Automation and actual Logic controls still need checking."
            else -> "Helper Accessibility access could not be verified."
        }
        val adapter = when {
            flag("dedicated_adapter") != true -> "Dedicated song editing, mixing and mastering are not implemented or verified."
            flag("logic_enabled") != true -> "The Logic adapter is installed but disabled in the Mac helper."
            else -> "Typed Logic controls are enabled. Inspect the open project to check available tracks and plugin parameters."
        }
        return "Logic Pro${version?.let { " $it" }.orEmpty()} is installed. $access $adapter " +
            "Full song editing and finished masters are not verified."
    }
}
