package com.example.meowmba.core.inventory

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * The companion's link to Meow Inventory.
 *
 * The inventory app already binds *into* Meowmba for models and memory; this is the return
 * path, so the chat can answer "what do I have" and act on the answer. One Messenger
 * connection, one generic [op] call — the operation name and its arguments are JSON, so
 * adding a capability is a tool definition here and a `when` branch there, with no shared
 * constants to drift.
 *
 * [lastItem] is what the UI observes: whenever an operation resolves to a single item, it
 * is published here so the companion can put the thing on screen and ask what to do with
 * it, rather than describing it in prose and hoping the user recognises which one.
 */
class InventoryBridgeClient(private val context: Context) {

    companion object {
        private const val TAG = "InventoryClient"
        const val ACTION = "com.meow.inventory.action.INVENTORY_BRIDGE"
        const val PKG = "com.meow.inventory"

        private const val MSG_PING = 1
        private const val MSG_OP = 10
        private const val MSG_REPLY = 100

        private const val KEY_OK = "ok"
        private const val KEY_ERROR = "error"
        private const val KEY_OP = "op"
        private const val KEY_ARGS_JSON = "args_json"
        private const val KEY_RESULT_JSON = "result_json"

        /** Long enough for a cold app start plus a Room query on a slow device. */
        private const val CALL_TIMEOUT_MS = 8_000L
        private const val CONNECT_WAIT_MS = 500L
    }

    private val _lastItem = MutableStateFlow<InventoryItem?>(null)

    /** The item the conversation is currently about, or null. */
    val lastItem: StateFlow<InventoryItem?> = _lastItem.asStateFlow()

    private var service: Messenger? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = Messenger(binder)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    /** True when the inventory app is installed at all. */
    fun isAvailable(): Boolean =
        context.packageManager.getLaunchIntentForPackage(PKG) != null

    fun bind(): Boolean {
        if (bound) return true
        return try {
            context.bindService(
                Intent(ACTION).setPackage(PKG),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "bind failed: ${e.message}")
            false
        }
    }

    fun unbind() {
        if (!bound) return
        runCatching { context.unbindService(connection) }
        bound = false
        service = null
    }

    /** Publishes an item to the UI (or clears it with null). */
    fun show(item: InventoryItem?) {
        _lastItem.value = item
    }

    sealed interface Result {
        data class Ok(val json: String) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Runs one inventory operation.
     *
     * Never throws: a missing app, a refused bind and a rejected operation all come back
     * as [Result.Failed] with something the companion can say out loud.
     */
    suspend fun op(name: String, args: JSONObject = JSONObject()): Result {
        if (!isAvailable()) {
            return Result.Failed("Meow Inventory isn't installed on this phone.")
        }
        val data = Bundle().apply {
            putString(KEY_OP, name)
            putString(KEY_ARGS_JSON, args.toString())
        }
        val reply = call(MSG_OP, data)
            ?: return Result.Failed("Meow Inventory didn't respond.")
        if (!reply.getBoolean(KEY_OK, false)) {
            return Result.Failed(reply.getString(KEY_ERROR) ?: "the inventory app refused that")
        }
        return Result.Ok(reply.getString(KEY_RESULT_JSON) ?: "{}")
    }

    suspend fun ping(): Boolean = call(MSG_PING, Bundle())?.getBoolean(KEY_OK, false) ?: false

    /** Convenience: run an op that returns one item, and publish it to the UI. */
    suspend fun itemOp(name: String, args: JSONObject): Result {
        val result = op(name, args)
        if (result is Result.Ok) {
            runCatching { InventoryItem.from(JSONObject(result.json)) }
                .getOrNull()
                ?.let { _lastItem.value = it }
        }
        return result
    }

    private suspend fun call(what: Int, data: Bundle): Bundle? =
        suspendCancellableCoroutine { cont ->
            if (!bind()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val main = Handler(Looper.getMainLooper())
            var settled = false
            fun settle(value: Bundle?) {
                if (settled) return
                settled = true
                if (cont.isActive) cont.resume(value)
            }
            // A bind is asynchronous, so the first call after a cold start has no
            // Messenger yet. Waiting a beat is the difference between "the inventory app
            // never answers" and "it answers from the second question onwards".
            main.postDelayed({
                val svc = service
                if (svc == null) {
                    settle(null)
                    return@postDelayed
                }
                val replyTo = Messenger(object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what == MSG_REPLY) settle(msg.data)
                    }
                })
                try {
                    svc.send(Message.obtain(null, what).also {
                        it.data = data
                        it.replyTo = replyTo
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "send failed: ${e.message}")
                    settle(null)
                }
            }, if (service == null) CONNECT_WAIT_MS else 0L)

            // Never hang the conversation on a service that died mid-call.
            main.postDelayed({ settle(null) }, CALL_TIMEOUT_MS)
        }
}

/**
 * One inventory item, in the fields a person would actually mention.
 *
 * Parsed leniently: this crosses an app boundary, and a companion that crashes because the
 * other app added a column is not a companion.
 */
data class InventoryItem(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "Misc",
    val quantity: Double = 1.0,
    val condition: String = "",
    val location: String = "",
    val tags: String = "",
    val status: String = "inventory",
    val forSale: Boolean = false,
    val pricePaid: Double? = null,
    val marketValue: Double? = null,
    val askingPrice: Double? = null,
    val platforms: String = "",
) {
    /** One line suitable for speaking or for a tool result. */
    fun summary(): String = buildString {
        append(name)
        if (quantity != 1.0) append(" ×${trimNumber(quantity)}")
        if (category.isNotBlank()) append(" · $category")
        if (condition.isNotBlank()) append(" · $condition")
        if (location.isNotBlank()) append(" · in $location")
        when {
            status == "sold" -> append(" · SOLD")
            forSale || status == "listed" ->
                append(" · for sale${askingPrice?.let { " at $${trimNumber(it)}" } ?: ""}")
            marketValue != null -> append(" · worth about $${trimNumber(marketValue)}")
        }
    }

    companion object {
        fun from(json: JSONObject) = InventoryItem(
            id = json.optString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            category = json.optString("category", "Misc"),
            quantity = json.optDouble("quantity", 1.0),
            condition = json.optString("condition"),
            location = json.optString("location"),
            tags = json.optString("tags"),
            status = json.optString("status", "inventory"),
            forSale = json.optBoolean("for_sale", false),
            pricePaid = json.optDouble("price_paid").takeIf { !it.isNaN() },
            marketValue = json.optDouble("market_value").takeIf { !it.isNaN() },
            askingPrice = json.optDouble("asking_price").takeIf { !it.isNaN() },
            platforms = json.optString("platforms"),
        )

        fun listFrom(json: String): List<InventoryItem> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { from(it) }
            }
        }.getOrDefault(emptyList())

        /** "2" not "2.0"; money and counts read wrong with a trailing .0. */
        fun trimNumber(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)
    }
}
