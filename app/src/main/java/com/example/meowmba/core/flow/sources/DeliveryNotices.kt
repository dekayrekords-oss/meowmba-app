package com.example.meowmba.core.flow.sources

import com.example.meowmba.core.attach.Attachments
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.Intention
import java.util.Locale

/**
 * Package updates in texts and emails (owner, 2026-09-24: "the flow should also monitor text
 * messages like it does emails. I got a few from Samsung saying my package arrived").
 *
 * The notification text is read on the phone only to recognise a delivery update; only the result
 * ("Package delivered · UPS") is kept — never the message itself.
 */
object DeliveryNotices {
    enum class Stage(val headline: String, val action: String) {
        DELIVERED("Package delivered", "Got it"),
        OUT_FOR_DELIVERY("Package arriving today", "Got it"),
        SHIPPED("Package on its way", "Track"),
        PROBLEM("Package delivery problem", "Check"),
    }

    data class Delivery(val stage: Stage, val carrier: String?, val tracking: String?, val url: String?)

    private val DELIVERED = Regex("(?i)\\b(was delivered|has been delivered|delivered (to|at|in)|package (has )?arrived|your (package|order|parcel) (has )?arrived|left at (your )?(front door|door|porch|mailbox|garage)|delivered today)\\b")
    private val OUT = Regex("(?i)\\b(out for delivery|arriving today|will arrive today|on the (delivery )?truck|delivery (window|today)|is nearby|stops? away)\\b")
    private val SHIPPED = Regex("(?i)\\b(has shipped|shipped|on its way|in transit|label created|tracking (number|#)|expected delivery|arriving (on|by|tomorrow))\\b")
    private val PROBLEM = Regex("(?i)\\b(delivery (attempt|exception|failed)|could not be delivered|unable to deliver|missed delivery|held at|returned to sender|delayed)\\b")
    private val PACKAGE_WORDS = Regex("(?i)\\b(package|parcel|order|shipment|delivery|delivered|ups|usps|fedex|dhl|amazon|tracking)\\b")

    private val CARRIERS = listOf("UPS", "USPS", "FedEx", "DHL", "Amazon", "OnTrac", "LaserShip", "Samsung")

    /** A delivery update from a message/email notification, or null when it isn't one. */
    fun parse(title: String?, text: String?): Delivery? {
        val all = listOfNotNull(title, text).joinToString(" \n ").take(2_000)
        if (all.isBlank() || !PACKAGE_WORDS.containsMatchIn(all)) return null
        val stage = when {
            PROBLEM.containsMatchIn(all) -> Stage.PROBLEM
            DELIVERED.containsMatchIn(all) -> Stage.DELIVERED
            OUT.containsMatchIn(all) -> Stage.OUT_FOR_DELIVERY
            SHIPPED.containsMatchIn(all) -> Stage.SHIPPED
            else -> return null
        }
        val track = Attachments.findTracking(all).firstOrNull()
        val carrier = track?.carrier ?: CARRIERS.firstOrNull { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(all) }
        return Delivery(stage, carrier, track?.number, track?.url)
    }

    /**
     * The Flow item. One item per package (tracking number, else carrier+sender) that moves through
     * shipped → arriving → delivered instead of piling up.
     */
    fun toIntention(d: Delivery, sender: String, appLabel: String, at: Long): Intention {
        val key = d.tracking ?: "${d.carrier ?: sender}-${sender}".lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-")
        val from = d.carrier ?: sender
        return Intention("pkg-$key", d.stage.headline + (d.carrier?.let { " ($it)" } ?: ""), FlowKind.HOME,
            "from $from in $appLabel", at,
            facts = buildMap {
                put(FlowPolicy.FACT_ACTION, if (d.url != null && d.stage != Stage.DELIVERED) "Track" else d.stage.action)
                put(FlowPolicy.FACT_BECAUSE, "$from sent a ${if (appLabel.contains("mail", true)) "message" else "text"} in $appLabel.")
                d.url?.let { put("url", it) }
                d.tracking?.let { put("tracking", it) }
                put("stage", d.stage.name)
            })
    }
}
