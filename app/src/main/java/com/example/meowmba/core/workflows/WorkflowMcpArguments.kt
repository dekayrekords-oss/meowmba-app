package com.example.meowmba.core.workflows

import kotlinx.serialization.json.*
import org.json.JSONObject

/** Pure preflight before an approval is offered. It cannot contact a service. */
interface WorkflowArgumentValidator { fun validateArguments(args: Map<String, String>) }

/** Present natural named arguments to small models. Preserve the explicit JSON
 * envelope for callers with nested data; do not guess numbers from strings when
 * the server declares a string. The server still validates its complete schema. */
object WorkflowMcpArguments {
    fun describe(schema: String): Map<String, String> {
        val properties = Json.parseToJsonElement(schema).jsonObject["properties"] as? JsonObject ?: return mapOf("arguments" to "JSON object string")
        return properties.mapValues { (_, value) -> (value as? JsonObject)?.get("type")?.toString()?.replace("\"", "") ?: "string" }
    }

    fun decode(schema: String, args: Map<String, String>): JSONObject {
        val root = Json.parseToJsonElement(schema).jsonObject
        val properties = root["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val supplied = if (args.keys == setOf("arguments") && "arguments" !in properties) {
            Json.parseToJsonElement(args.getValue("arguments")) as? JsonObject ?: error("arguments must be a JSON object")
        } else buildJsonObject {
            args.forEach { (name, raw) ->
                val spec = properties[name] as? JsonObject ?: JsonObject(emptyMap())
                val types = types(spec)
                val value: JsonElement = when {
                    raw == "null" && "null" in types -> JsonNull
                    "string" in types || types.isEmpty() -> JsonPrimitive(raw)
                    "boolean" in types -> { require(raw in setOf("true", "false")) { "$name must be true or false" }; JsonPrimitive(raw == "true") }
                    "integer" in types -> { require(raw.matches(Regex("-?(0|[1-9][0-9]*)"))) { "$name must be an integer" }; JsonPrimitive(raw.toLongOrNull() ?: error("$name is outside the supported integer range")) }
                    "number" in types -> {
                        require(raw.length <= 100) { "$name is too large" }
                        Json.parseToJsonElement(raw).also { require(it is JsonPrimitive && !it.isString && it.content.toBigDecimalOrNull() != null) { "$name must be a number" } }
                    }
                    "object" in types || "array" in types -> Json.parseToJsonElement(raw)
                    else -> error("Unsupported argument type for $name; use the arguments JSON object")
                }
                put(name, value)
            }
        }
        validate(root, supplied, "arguments")
        return JSONObject(supplied.toString())
    }

    private fun types(spec: JsonObject): Set<String> = when (val type = spec["type"]) {
        is JsonPrimitive -> setOf(type.content)
        is JsonArray -> type.map { it.jsonPrimitive.content }.toSet()
        else -> emptySet()
    }
    private fun validate(spec: JsonObject, value: JsonElement, name: String, depth: Int = 0) {
        require(depth <= 12) { "Arguments are nested too deeply" }
        val types = types(spec)
        val matches = when (value) {
            JsonNull -> "null" in types
            is JsonObject -> "object" in types
            is JsonArray -> "array" in types
            is JsonPrimitive -> if (value.isString) "string" in types else when {
                value.booleanOrNull != null -> "boolean" in types
                value.content.toBigDecimalOrNull() != null -> "number" in types || "integer" in types && value.content.toBigDecimal().stripTrailingZeros().scale() <= 0
                else -> false
            }
        }
        require(types.isEmpty() || matches) { "$name must match type ${types.joinToString()}" }
        (spec["enum"] as? JsonArray)?.let { require(value in it) { "$name must be one of $it" } }
        if (value is JsonObject) {
            val properties = spec["properties"] as? JsonObject ?: JsonObject(emptyMap())
            (spec["required"] as? JsonArray)?.forEach { require(it.jsonPrimitive.content in value) { "Missing argument: ${it.jsonPrimitive.content}" } }
            value.forEach { (key, child) ->
                val childSpec = properties[key] as? JsonObject
                require(childSpec != null || spec["additionalProperties"] != JsonPrimitive(false)) { "Unknown argument: $key. Allowed: ${properties.keys.joinToString()}" }
                (childSpec ?: spec["additionalProperties"] as? JsonObject)?.let { validate(it, child, "$name.$key", depth + 1) }
            }
        }
        if (value is JsonArray) (spec["items"] as? JsonObject)?.let { item -> value.forEach { validate(item, it, "$name[]", depth + 1) } }
    }
}
