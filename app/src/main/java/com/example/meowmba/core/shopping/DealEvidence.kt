package com.example.meowmba.core.shopping

import com.example.meowmba.core.agent.tools.WebHit
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A source lead, not a verified price. A fresh search does not make an old coupon current. */
data class DealLead(val title: String, val url: String, val excerpt: String, val official: Boolean)

object DealEvidence {
    private data class Retailer(val aliases: List<String>, val domains: List<String>)
    // Domain identity only, never hardcoded promotions/prices. Unknown shops still get a web search.
    private val retailers = listOf(
        Retailer(listOf("target"), listOf("target.com")),
        Retailer(listOf("walmart", "wal mart"), listOf("walmart.com")),
        Retailer(listOf("costco"), listOf("costco.com")),
        Retailer(listOf("sam's club", "sams club"), listOf("samsclub.com")),
        Retailer(listOf("best buy"), listOf("bestbuy.com")),
        Retailer(listOf("kroger"), listOf("kroger.com")),
        Retailer(listOf("fry's", "frys"), listOf("frysfood.com")),
        Retailer(listOf("safeway"), listOf("safeway.com")),
        Retailer(listOf("albertsons"), listOf("albertsons.com")),
        Retailer(listOf("bashas", "basha's"), listOf("bashas.com")),
        Retailer(listOf("sprouts"), listOf("sprouts.com")),
        Retailer(listOf("whole foods"), listOf("wholefoodsmarket.com")),
        Retailer(listOf("trader joe's", "trader joes"), listOf("traderjoes.com")),
        Retailer(listOf("aldi"), listOf("aldi.us")),
        Retailer(listOf("walgreens"), listOf("walgreens.com")),
        Retailer(listOf("cvs"), listOf("cvs.com")),
        Retailer(listOf("home depot", "the home depot"), listOf("homedepot.com")),
        Retailer(listOf("lowe's", "lowes"), listOf("lowes.com")),
        Retailer(listOf("kohl's", "kohls"), listOf("kohls.com")),
        Retailer(listOf("macy's", "macys"), listOf("macys.com")),
        Retailer(listOf("dollar general"), listOf("dollargeneral.com")),
        Retailer(listOf("dollar tree"), listOf("dollartree.com")),
        Retailer(listOf("family dollar"), listOf("familydollar.com")),
        Retailer(listOf("ikea"), listOf("ikea.com")),
    )

    private fun retailer(store: String): Retailer? {
        val name = store.lowercase(Locale.ROOT).replace('’', '\'').trim()
        return retailers.firstOrNull { retailer -> retailer.aliases.any { alias ->
            name == alias || name.matches(Regex("${Regex.escape(alias)} (?:supercenter|superstore|marketplace|food(?: and drug)?|pharmacy|wholesale|#?\\d+)(?: #?\\d+)?"))
        } }
    }

    fun query(store: String, item: String?, today: LocalDate): String {
        require(ShoppingRequest.validTerm(store))
        require(item == null || ShoppingRequest.validTerm(item, 100))
        val site = retailer(store)?.domains?.firstOrNull()?.let { "site:$it " }.orEmpty()
        val focus = item?.let { "\"$it\" deals coupons" } ?: "deals coupons weekly ad"
        return "$site\"$store\" $focus ${today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))}"
    }

    fun select(store: String, item: String?, hits: List<WebHit>, today: LocalDate): List<DealLead> {
        val domains = retailer(store)?.domains.orEmpty()
        val storeWords = words(store)
        val itemWords = item?.let(::words).orEmpty()
        return hits.mapNotNull { hit ->
            val host = safeHost(hit.url) ?: return@mapNotNull null
            val official = domains.any { host == it || host.endsWith(".$it") }
            val title = clean(hit.title, 180)
            // Keep conditions alongside the discount instead of extracting just the attractive number.
            val excerpt = clean(hit.snippet, 650)
            val text = "$title $excerpt"
            if (!official && !words(text).containsAll(storeWords)) return@mapNotNull null
            if (itemWords.isNotEmpty() && !words(text).containsAll(itemWords)) return@mapNotNull null
            if (!dealCue.containsMatchIn(text) || expired(text, today)) return@mapNotNull null
            DealLead(title.ifBlank { host }, hit.url, excerpt, official)
        }.distinctBy { it.url.substringBefore('#').trimEnd('/') }
            .sortedByDescending { if (it.official) 1 else 0 }.take(4)
    }

    fun safeHost(url: String): String? = runCatching {
        if (url.length > 2048) return null
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in setOf(-1, 443) ||
            !host.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}")) ||
            host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal")) return null
        host
    }.getOrNull()

    private val dealCue = Regex("(?i)\\b(?:deal|deals|sale|sales|save|saving|savings|coupon|coupons|offer|offers|discount|clearance|weekly ad|bogo)\\b|\\d+%\\s*off")
    private fun words(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it !in setOf("the", "and", "s") }.toSet()
    private fun clean(text: String, max: Int): String = text.replace(Regex("<[^>]*>"), " ")
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").replace(Regex("\\s+"), " ").trim().take(max)

    internal fun expired(text: String, today: LocalDate): Boolean {
        if (Regex("(?i)\\b(?:offer|coupon|deal|sale) (?:has )?expired\\b|\\bexpired (?:offer|coupon|deal|sale)\\b").containsMatchIn(text)) return true
        // Conservative exclusion of explicitly historical ads; undated leads remain UNVERIFIED.
        if (Regex("\\b20\\d{2}\\b").findAll(text).any { it.value.toInt() < today.year }) return true
        val isoEnd = Regex("(?i)(?:ends?|expires?|valid (?:through|until))[: ]+(20\\d{2}-\\d{2}-\\d{2})")
        return isoEnd.findAll(text).any { match ->
            runCatching { LocalDate.parse(match.groupValues[1]).isBefore(today) }.getOrDefault(false)
        }
    }
}
