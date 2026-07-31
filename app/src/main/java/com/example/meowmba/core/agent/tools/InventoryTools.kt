package com.example.meowmba.core.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.meowmba.core.inventory.InventoryBridgeClient
import com.example.meowmba.core.inventory.InventoryItem
import org.json.JSONObject

/**
 * The companion's hands on Meow Inventory.
 *
 * Every tool here goes through one [InventoryBridgeClient.op] call, so the set can grow
 * without new plumbing. They share three deliberate behaviours:
 *
 *  - **Items are addressed by name.** The user says "the SM58", not an id. Resolution
 *    happens in the inventory app, against its own search, so the companion never has to
 *    hold ids across turns.
 *  - **Acting shows the thing.** Any operation that lands on a single item publishes it,
 *    which is what puts the item card on screen. Saying "done" without showing what was
 *    touched is how the wrong item gets renamed twice.
 *  - **Failures are sentences.** A missing app or an unmatched name comes back as
 *    something the companion can say, not a stack trace.
 */
abstract class InventoryTool internal constructor(
    protected val client: InventoryBridgeClient,
) : Tool {

    /** Args the model may pass to identify an item, mapped to the bridge's shape. */
    protected fun handle(args: Map<String, String>): JSONObject = JSONObject().apply {
        args["id"]?.takeIf { it.isNotBlank() }?.let { put("id", it) }
        (args["name"] ?: args["item"] ?: args["query"])
            ?.takeIf { it.isNotBlank() }
            ?.let { put("name", it) }
    }

    protected fun JSONObject.putIfPresent(key: String, value: String?) = apply {
        value?.takeIf { it.isNotBlank() }?.let { put(key, it) }
    }

    protected fun JSONObject.putNumberIfPresent(key: String, value: String?) = apply {
        value?.trim()?.removePrefix("$")?.replace(",", "")
            ?.toDoubleOrNull()
            ?.let { put(key, it) }
    }

    protected fun single(result: InventoryBridgeClient.Result): ToolResult = when (result) {
        is InventoryBridgeClient.Result.Failed -> ToolResult.Failure(result.reason)
        is InventoryBridgeClient.Result.Ok -> runCatching {
            InventoryItem.from(JSONObject(result.json))
        }.map { ToolResult.Success(it.summary()) }
            .getOrElse { ToolResult.Success(result.json) }
    }
}

/** `inventory.search` — what do I have. */
class InventorySearchTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.search",
        displayName = "Search my inventory",
        description = "Search the user's Meow Inventory — the things they own, what they " +
            "are worth, where they are kept, and what is listed for sale. Use for any " +
            "question about what the user has, owns, is selling, or where something is. " +
            "Optional filter: all, for_sale, inventory, sold.",
        inputSchema = mapOf("query" to "string", "filter" to "string"),
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val payload = JSONObject()
            .putIfPresent("query", args["query"] ?: args["name"] ?: args["item"])
            .putIfPresent("filter", args["filter"])
        return when (val r = client.op("search", payload)) {
            is InventoryBridgeClient.Result.Failed -> ToolResult.Failure(r.reason)
            is InventoryBridgeClient.Result.Ok -> {
                val items = InventoryItem.listFrom(r.json)
                // Exactly one hit is the case the user means when they say "the amp" —
                // put it on screen so the follow-up ("sell it") has an obvious subject.
                if (items.size == 1) client.show(items.first())
                when {
                    items.isEmpty() -> ToolResult.Success("Nothing in the inventory matches that.")
                    else -> ToolResult.Success(
                        items.joinToString("\n") { "- ${it.summary()}" }
                    )
                }
            }
        }
    }
}

/** `inventory.get` — pull up one specific item. */
class InventoryGetTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.get",
        displayName = "Pull up an item",
        description = "Look up one specific item the user owns by name and show it on " +
            "screen. Use when the user refers to a particular possession and you need its " +
            "details, price, condition or location.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.SAFE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        single(client.itemOp("get", handle(args)))
}

/** `inventory.add` — a new thing. */
class InventoryAddTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.add",
        displayName = "Add to inventory",
        description = "Add a new item to the user's inventory. Use when the user says " +
            "they got, bought, or want to record something. Optional: category, quantity, " +
            "condition, location, price_paid, market_value, description.",
        inputSchema = mapOf(
            "name" to "string", "category" to "string", "quantity" to "string",
            "condition" to "string", "location" to "string", "price_paid" to "string",
            "market_value" to "string", "description" to "string",
        ),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = (args["name"] ?: args["item"])?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("I need a name for the item.")
        val payload = JSONObject()
            .put("name", name)
            .putIfPresent("category", args["category"])
            .putIfPresent("condition", args["condition"])
            .putIfPresent("location", args["location"])
            .putIfPresent("description", args["description"])
            .putNumberIfPresent("quantity", args["quantity"])
            .putNumberIfPresent("price_paid", args["price_paid"])
            .putNumberIfPresent("market_value", args["market_value"])
        return single(client.itemOp("create", payload))
    }
}

/** `inventory.update` — rename, reprice, move, recount. */
class InventoryUpdateTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.update",
        displayName = "Change an item",
        description = "Change something about an item the user owns: rename it " +
            "(new_name), change quantity, condition, location, category, description, " +
            "tags, price_paid or market_value. Identify the item by name.",
        inputSchema = mapOf(
            "name" to "string", "new_name" to "string", "quantity" to "string",
            "condition" to "string", "location" to "string", "category" to "string",
            "description" to "string", "tags" to "string", "market_value" to "string",
            "price_paid" to "string",
        ),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val payload = handle(args)
            .putIfPresent("new_name", args["new_name"] ?: args["rename_to"])
            .putIfPresent("condition", args["condition"])
            .putIfPresent("location", args["location"])
            .putIfPresent("category", args["category"])
            .putIfPresent("description", args["description"])
            .putIfPresent("tags", args["tags"])
            .putNumberIfPresent("quantity", args["quantity"])
            .putNumberIfPresent("market_value", args["market_value"])
            .putNumberIfPresent("price_paid", args["price_paid"])
        if (payload.length() <= 1) {
            return ToolResult.Failure("Tell me what to change about it.")
        }
        return single(client.itemOp("update", payload))
    }
}

/** `inventory.list_for_sale` — put it up. */
class InventoryListForSaleTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.list_for_sale",
        displayName = "List an item for sale",
        description = "Mark one of the user's items as for sale, optionally with an " +
            "asking price and marketplaces (ebay, fb_market, offerup, mercari, " +
            "craigslist, poshmark, reverb, etsy). Use for 'sell', 'list', 'put up for " +
            "sale', 'resell'.",
        inputSchema = mapOf("name" to "string", "price" to "string", "platforms" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val payload = handle(args)
            .putNumberIfPresent("price", args["price"] ?: args["asking_price"])
            .putIfPresent("platforms", args["platforms"] ?: args["platform"])
        return single(client.itemOp("list_for_sale", payload))
    }
}

/** `inventory.mark_sold` — it's gone. */
class InventoryMarkSoldTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.mark_sold",
        displayName = "Mark an item sold",
        description = "Record that the user sold an item, optionally for a price. Use for " +
            "'I sold the…', 'it sold', 'mark it sold'.",
        inputSchema = mapOf("name" to "string", "price" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        single(client.itemOp("mark_sold", handle(args).putNumberIfPresent("price", args["price"])))
}

/** `inventory.unlist` — take it back off the market. */
class InventoryUnlistTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.unlist",
        displayName = "Take an item off sale",
        description = "Stop selling an item and return it to the normal inventory. Use " +
            "for 'take it down', 'unlist', 'I'm keeping it after all'.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        single(client.itemOp("unlist", handle(args)))
}

/** `inventory.delete` — remove the record entirely. */
class InventoryDeleteTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.delete",
        displayName = "Delete an item",
        description = "Permanently remove an item from the user's inventory. Only for " +
            "'delete', 'remove from my inventory', 'get rid of the record'. If the item " +
            "was sold, use inventory.mark_sold instead — that keeps the history.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.DESTRUCTIVE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        when (val r = client.op("delete", handle(args))) {
            is InventoryBridgeClient.Result.Failed -> ToolResult.Failure(r.reason)
            is InventoryBridgeClient.Result.Ok -> {
                // The card must not keep showing a record that no longer exists.
                client.show(null)
                val name = runCatching { JSONObject(r.json).optString("deleted") }
                    .getOrDefault("it")
                ToolResult.Success("Deleted $name from the inventory.")
            }
        }
}

/** `inventory.stats` — the shape of the whole collection. */
class InventoryStatsTool(client: InventoryBridgeClient) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.stats",
        displayName = "Inventory summary",
        description = "Totals across the user's whole inventory: how many items, how many " +
            "are for sale or sold, estimated value, and a breakdown by category.",
        riskLevel = RiskLevel.SAFE,
        timeoutMs = 12_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        when (val r = client.op("stats")) {
            is InventoryBridgeClient.Result.Failed -> ToolResult.Failure(r.reason)
            is InventoryBridgeClient.Result.Ok -> runCatching {
                val j = JSONObject(r.json)
                ToolResult.Success(
                    buildString {
                        append("${j.optInt("total_items")} items")
                        append(", ${j.optInt("for_sale")} for sale")
                        append(", ${j.optInt("sold")} sold")
                        val value = j.optDouble("estimated_value", 0.0)
                        if (value > 0) {
                            append(". Estimated value $${InventoryItem.trimNumber(value)}")
                        }
                        append(".")
                    }
                )
            }.getOrElse { ToolResult.Success(r.json) }
        }
}

/**
 * `inventory.open` — hand off to the app itself.
 *
 * There is a limit to what a chat turn should do. When the user wants photos, listings or
 * the marketing screens, opening the app beats reimplementing it in a card.
 */
class InventoryOpenTool(
    private val context: Context,
    client: InventoryBridgeClient,
) : InventoryTool(client) {

    override val definition = ToolDefinition(
        id = "inventory.open",
        displayName = "Open Meow Inventory",
        description = "Open the Meow Inventory app itself, for photos, listings and the " +
            "marketing tools. Use when the user asks to see or open the inventory app.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.SAFE,
        timeoutMs = 8_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val intent = context.packageManager
            .getLaunchIntentForPackage(InventoryBridgeClient.PKG)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return ToolResult.Failure("Meow Inventory isn't installed on this phone.")
        args["name"]?.takeIf { it.isNotBlank() }?.let { name ->
            intent.data = Uri.parse("meowinventory://item?name=${Uri.encode(name)}")
        }
        context.startActivity(intent)
        return ToolResult.Success("Opened Meow Inventory.")
    }
}

/** Registers every inventory tool; call once when the registry is built. */
fun ToolRegistry.registerInventoryTools(context: Context, client: InventoryBridgeClient) {
    register(InventorySearchTool(client))
    register(InventoryGetTool(client))
    register(InventoryAddTool(client))
    register(InventoryUpdateTool(client))
    register(InventoryListForSaleTool(client))
    register(InventoryMarkSoldTool(client))
    register(InventoryUnlistTool(client))
    register(InventoryDeleteTool(client))
    register(InventoryStatsTool(client))
    register(InventoryOpenTool(context, client))
}
