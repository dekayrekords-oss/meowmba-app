package com.example.meowmba.ui.companion

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** What an OpenCode permission request asks for, as people read it (not raw JSON). */
internal data class ApprovalCard(val permission: String, val targets: List<String>, val detail: String) {
    val lines: Int get() = detail.lines().size

    companion object {
        fun from(request: JsonObject): ApprovalCard {
            val permission = request["permission"]?.jsonPrimitive?.contentOrNull ?: "action"
            val targets = (request["patterns"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val metadata = request["metadata"] as? JsonObject
            fun field(name: String) = (metadata?.get(name) as? JsonPrimitive)?.contentOrNull
            val detail = field("diff") ?: field("command") ?: metadata?.entries
                ?.joinToString("\n") { (k, v) -> "$k: " + ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
                ?.ifBlank { null } ?: "(no further detail)"
            return ApprovalCard(permission, targets, detail)
        }
    }
}
