package com.example.meowmba.core.inventory

/**
 * On-device parsing of "about my stuff" requests. No LLM, no network.
 *
 * The model-driven tool router already covers arbitrary phrasing, but it is only as good
 * as whatever model is loaded, and asking what you own should not depend on that. This
 * handles the phrasings people actually use, deterministically, so the inventory answers
 * the same way every time — the same reason the weather has a fast path.
 *
 * Anything not recognised returns null and falls through to normal routing.
 */
object InventoryRequestParser {

    sealed interface Request {
        /** Look things up. [query] blank means "everything". */
        data class Search(val query: String, val filter: String = "all") : Request

        /** Totals across the whole inventory. */
        data object Stats : Request

        data class Add(val name: String) : Request
        data class ListForSale(val name: String, val price: Double?) : Request
        data class MarkSold(val name: String, val price: Double?) : Request
        data class Rename(val name: String, val newName: String) : Request
        data class Delete(val name: String) : Request
        data object Open : Request
    }

    /**
     * Words that make a sentence about the user's possessions.
     *
     * Required for the vaguer verbs: "sell the car" is an inventory command, but "sell me
     * on it" is not, and "add milk" is a shopping list, not an asset. Requiring one of
     * these (or an explicit "inventory") keeps the fast path from hijacking normal
     * conversation.
     */
    private val POSSESSION_HINTS = listOf(
        "inventory", "my stuff", "my things", "my gear", "i own", "do i have",
        "do i still have", "what do i have", "how many",
    )

    fun parse(raw: String): Request? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        // Explicit app request.
        if (text in setOf("open inventory", "open my inventory", "open the inventory",
                "open meow inventory", "show me my inventory app")
        ) return Request.Open

        // Totals.
        if (text.containsAny(
                "what is my inventory worth", "what's my inventory worth",
                "whats my inventory worth", "how much is my stuff worth",
                "how much is my inventory worth", "inventory summary",
                "inventory stats", "how many things do i own", "how many items do i have",
            )
        ) return Request.Stats

        // Rename: "rename the fender to the strat", "change the name of X to Y".
        renameOf(text)?.let { return it }

        // Delete: explicit, and only about the inventory record.
        after(text, "delete ", "remove ")?.let { rest ->
            if (rest.contains("from my inventory") || rest.contains("from the inventory")) {
                val name = rest.substringBefore("from my inventory")
                    .substringBefore("from the inventory")
                return Request.Delete(clean(name))
            }
        }

        // Sold: past tense, so it must not be confused with "sell".
        after(text, "i sold my ", "i sold the ", "i sold ", "mark my ", "mark the ")?.let { rest ->
            val (name, price) = splitPrice(rest.removeSuffix(" as sold").removeSuffix(" sold"))
            if (name.isNotBlank()) return Request.MarkSold(name, price)
        }

        // Sell / list.
        after(
            text,
            "list my ", "list the ", "put my ", "put the ",
            "sell my ", "sell the ", "resell my ", "resell the ",
        )?.let { rest ->
            if (rest.containsAny("for sale", "on ebay", "on marketplace", "up for sale") ||
                text.startsWith("sell ") || text.startsWith("resell ")
            ) {
                val body = rest.substringBefore(" for sale")
                    .substringBefore(" up for sale")
                    .substringBefore(" on ")
                val (name, price) = splitPrice(body)
                if (name.isNotBlank()) return Request.ListForSale(name, price)
            }
        }

        // Add.
        after(text, "add ")?.let { rest ->
            if (rest.containsAny("to my inventory", "to the inventory", "to my stuff")) {
                val name = rest.substringBefore("to my inventory")
                    .substringBefore("to the inventory")
                    .substringBefore("to my stuff")
                if (clean(name).isNotBlank()) return Request.Add(clean(name))
            }
        }

        // What am I selling.
        if (text.containsAny(
                "what am i selling", "what is for sale", "what's for sale",
                "whats for sale", "what am i listing", "what have i listed",
            )
        ) return Request.Search("", filter = "for_sale")

        if (text.containsAny("what have i sold", "what did i sell")) {
            return Request.Search("", filter = "sold")
        }

        // Everything.
        if (text in setOf(
                "what do i have", "what do i own", "what's in my inventory",
                "whats in my inventory", "what is in my inventory", "show my inventory",
                "list my inventory", "show me my stuff", "what stuff do i have",
            )
        ) return Request.Search("")

        // Targeted lookups: the query is whatever is left after the trigger.
        after(
            text,
            "do i have any ", "do i have a ", "do i have an ", "do i have ",
            "do i still have my ", "do i still have the ", "do i still have ",
            "how many ", "where is my ", "where's my ", "wheres my ",
            "where did i put my ", "where do i keep my ",
            "search my inventory for ", "look up ", "find my ", "find the ",
            "what is my ", "what's my ", "whats my ",
        )?.let { rest ->
            val query = clean(
                rest.substringBefore(" in my inventory")
                    .substringBefore(" do i have")
                    .substringBefore(" do i own")
            )
            if (query.isNotBlank() && (text.containsAny(*POSSESSION_HINTS.toTypedArray()) ||
                    text.startsWith("where") || text.startsWith("find my"))
            ) {
                return Request.Search(query)
            }
        }

        // "… in my inventory" anywhere is unambiguous enough on its own.
        if (text.contains("in my inventory")) {
            val query = clean(text.substringBefore("in my inventory")
                .removePrefix("do i have")
                .removePrefix("is there")
                .removePrefix("what")
            )
            return Request.Search(query)
        }

        return null
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9$. ']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }

    private fun after(text: String, vararg triggers: String): String? {
        for (t in triggers.sortedByDescending { it.length }) {
            val idx = text.indexOf(t)
            if (idx >= 0) return text.substring(idx + t.length).trim()
        }
        return null
    }

    private val LEADING_NOISE = listOf("a ", "an ", "the ", "my ", "some ")

    private fun clean(raw: String): String {
        var s = raw.trim().trim('.', ',', '?', '!').trim()
        var changed = true
        while (changed) {
            changed = false
            for (n in LEADING_NOISE) {
                if (s.startsWith(n)) {
                    s = s.removePrefix(n).trim()
                    changed = true
                }
            }
        }
        return s.removeSuffix(" please").trim()
    }

    /** Pulls a trailing "for $250" / "for 250" off a name. */
    private fun splitPrice(raw: String): Pair<String, Double?> {
        val m = Regex("(.*?)\\s+for\\s+\\$?([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:bucks|dollars)?$")
            .find(raw.trim())
            ?: return clean(raw) to null
        return clean(m.groupValues[1]) to m.groupValues[2].toDoubleOrNull()
    }

    private fun renameOf(text: String): Request.Rename? {
        // "rename X to Y" / "change the name of X to Y" / "call X Y instead"
        after(text, "rename ", "change the name of ", "change name of ")?.let { rest ->
            val idx = rest.lastIndexOf(" to ")
            if (idx > 0) {
                val from = clean(rest.substring(0, idx))
                val to = clean(rest.substring(idx + 4))
                if (from.isNotBlank() && to.isNotBlank()) return Request.Rename(from, to)
            }
        }
        return null
    }
}
