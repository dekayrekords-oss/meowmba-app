package com.example.meowmba.flow

import com.example.meowmba.core.flow.sources.DeliveryNotices
import com.example.meowmba.core.flow.sources.DeliveryNotices.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner 2026-09-24: Flow should notice package texts (e.g. Samsung saying the package arrived). */
class DeliveryNoticesTest {
    @Test fun recognisesDeliveryTexts() {
        val samsung = DeliveryNotices.parse("Samsung", "Good news! Your package has arrived. Order #SA1234567.")!!
        assertEquals(Stage.DELIVERED, samsung.stage); assertEquals("Samsung", samsung.carrier)

        val ups = DeliveryNotices.parse("72975", "UPS: Your package 1Z999AA10123456784 is out for delivery today.")!!
        assertEquals(Stage.OUT_FOR_DELIVERY, ups.stage); assertEquals("UPS", ups.carrier)
        assertEquals("1Z999AA10123456784", ups.tracking); assertTrue(ups.url!!.contains("ups.com"))

        val amazon = DeliveryNotices.parse("Amazon", "Your order has shipped. Arriving Thursday. Track: TBA123456789012")!!
        assertEquals(Stage.SHIPPED, amazon.stage); assertEquals("TBA123456789012", amazon.tracking)

        assertEquals(Stage.PROBLEM, DeliveryNotices.parse("FedEx", "Delivery attempt failed for your package.")!!.stage)
    }

    @Test fun ignoresChatAndAds() {
        assertNull(DeliveryNotices.parse("Dana", "Are we still on for dinner?"))
        assertNull(DeliveryNotices.parse("Pizza Place", "50% off tonight, order now!"))
        assertNull(DeliveryNotices.parse(null, null))
    }

    @Test fun onePackageMovesThroughStages() {
        val shipped = DeliveryNotices.toIntention(DeliveryNotices.parse("UPS", "Shipped: 1Z999AA10123456784")!!, "UPS", "Messages", 1L)
        val delivered = DeliveryNotices.toIntention(DeliveryNotices.parse("UPS", "Delivered to front door: 1Z999AA10123456784")!!, "UPS", "Messages", 2L)
        assertEquals(shipped.id, delivered.id)          // same package, same Flow item
        assertEquals("Package delivered (UPS)", delivered.text)
        assertEquals("DELIVERED", delivered.facts["stage"])
        assertEquals("UPS sent a text in Messages.", delivered.facts[com.example.meowmba.core.flow.FlowPolicy.FACT_BECAUSE])
    }
}
