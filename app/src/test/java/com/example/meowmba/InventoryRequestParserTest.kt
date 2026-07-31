package com.example.meowmba

import com.example.meowmba.core.inventory.InventoryRequestParser
import com.example.meowmba.core.inventory.InventoryRequestParser.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fast path decides what happens before any model sees the sentence, so a wrong match
 * here is worse than no match: it would hijack ordinary conversation. Both directions are
 * pinned — what must route to the inventory, and what must not.
 */
class InventoryRequestParserTest {

    @Test
    fun `asks what i have`() {
        assertEquals(Request.Search(""), InventoryRequestParser.parse("what do I have"))
        assertEquals(Request.Search(""), InventoryRequestParser.parse("what's in my inventory"))
    }

    @Test
    fun `asks about one thing`() {
        assertEquals(
            Request.Search("sm58"),
            InventoryRequestParser.parse("do I have an SM58"),
        )
        assertEquals(
            Request.Search("drill"),
            InventoryRequestParser.parse("where is my drill"),
        )
    }

    @Test
    fun `filters by listing state`() {
        assertEquals(
            Request.Search("", filter = "for_sale"),
            InventoryRequestParser.parse("what am I selling"),
        )
        assertEquals(
            Request.Search("", filter = "sold"),
            InventoryRequestParser.parse("what have I sold"),
        )
    }

    @Test
    fun `totals`() {
        assertEquals(Request.Stats, InventoryRequestParser.parse("what's my inventory worth"))
        assertEquals(Request.Stats, InventoryRequestParser.parse("inventory summary"))
    }

    @Test
    fun `sell with and without a price`() {
        assertEquals(
            Request.ListForSale("fender amp", 250.0),
            InventoryRequestParser.parse("sell my Fender amp for \$250"),
        )
        assertEquals(
            Request.ListForSale("fender amp", null),
            InventoryRequestParser.parse("list my Fender amp for sale"),
        )
    }

    /** Past tense must record a sale, not create a listing. */
    @Test
    fun `sold is not the same as sell`() {
        assertEquals(
            Request.MarkSold("bike", 400.0),
            InventoryRequestParser.parse("I sold my bike for 400"),
        )
    }

    @Test
    fun `rename`() {
        assertEquals(
            Request.Rename("blue guitar", "strat"),
            InventoryRequestParser.parse("rename the blue guitar to the Strat"),
        )
    }

    @Test
    fun `add needs to say inventory`() {
        assertEquals(
            Request.Add("space heater"),
            InventoryRequestParser.parse("add a space heater to my inventory"),
        )
        // A bare "add milk" is a shopping list, not an asset register.
        assertNull(InventoryRequestParser.parse("add milk"))
    }

    @Test
    fun `delete must name the inventory`() {
        assertEquals(
            Request.Delete("old laptop"),
            InventoryRequestParser.parse("delete the old laptop from my inventory"),
        )
        // Deleting a file is somebody else's job.
        assertNull(InventoryRequestParser.parse("delete the file notes.txt"))
    }

    @Test
    fun `ordinary conversation is left alone`() {
        listOf(
            "what's the weather",
            "sell me on it",
            "how are you",
            "take me to a coffee shop",
            "remember that I like oat milk",
            "what do you see",
        ).forEach { assertNull("must not hijack: $it", InventoryRequestParser.parse(it)) }
    }

    @Test
    fun `open the app`() {
        assertEquals(Request.Open, InventoryRequestParser.parse("open my inventory"))
    }

    @Test
    fun `noise words are stripped from names`() {
        val r = InventoryRequestParser.parse("do I still have my old Marshall")
        assertTrue(r is Request.Search && r.query == "old marshall")
    }
}
