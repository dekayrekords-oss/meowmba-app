package com.example.meowmba

import com.example.meowmba.core.audio.KwsKeywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong token sequence does not error — it simply never fires, which is
 * indistinguishable from "the wake word is broken". So the format is pinned here.
 */
class KwsKeywordsTest {

    /** A slice of the real gigaspeech vocabulary, enough to cover these cases. */
    private val vocab = KwsKeywords.parseVocab(
        sequenceOf(
            "<blk> 0", "<sos/eos> 1", "<unk> 2",
            "S 3", "T 4", "▁THE 5", "▁A 6", "▁TO 7",
            "▁HE 8", "Y 9", "▁ME 10", "OW 11", "M 12", "B 13", "A 14",
            "▁GO 15", "O 16", "G 17", "LE 18", "▁HI 19",
            "▁ROB 20", "OT 21", "Z 22",
        )
    )

    @Test
    fun `parses vocab from tokens_txt lines`() {
        assertTrue("▁HE" in vocab)
        assertTrue("OW" in vocab)
        // The id column must not leak into the token.
        assertTrue(vocab.none { it.contains(' ') })
    }

    @Test
    fun `built-in phrase uses the verified exact split`() {
        // Produced by the model's own bpe.model, not by the greedy fallback.
        assertEquals("▁HE Y ▁ME OW", KwsKeywords.build(listOf("hey meow"), vocab))
    }

    @Test
    fun `built-in split is case-insensitive`() {
        assertEquals("▁HE Y ▁ME OW", KwsKeywords.build(listOf("Hey Meow"), vocab))
        assertEquals("▁HE Y ▁ME OW", KwsKeywords.build(listOf("HEY MEOW"), vocab))
    }

    @Test
    fun `all built-ins are known`() {
        val out = KwsKeywords.build(listOf("hey meow", "hey memba", "hey meowmba"), vocab)
        assertEquals(3, out.lines().size)
        assertTrue(out.lines().all { it.startsWith("▁HE Y ") })
    }

    @Test
    fun `one line per phrase`() {
        val out = KwsKeywords.build(listOf("hey meow", "hey memba"), vocab)
        assertEquals(listOf("▁HE Y ▁ME OW", "▁HE Y ▁ME M B A"), out.lines())
    }

    @Test
    fun `duplicates collapse`() {
        // "hey meow" twice must not be sent twice.
        val out = KwsKeywords.build(listOf("hey meow", "HEY MEOW"), vocab)
        assertEquals(1, out.lines().size)
    }

    @Test
    fun `greedy fallback marks word starts and covers the rest`() {
        val toks = KwsKeywords.tokenise("ROBOT", vocab)
        assertEquals("▁ROB OT", toks)
    }

    @Test
    fun `greedy fallback prefers the longest match`() {
        // "▁HE" + "Y" rather than shorter pieces.
        assertEquals("▁HE Y", KwsKeywords.tokenise("HEY", vocab))
    }

    @Test
    fun `uncoverable phrase is dropped rather than half-tokenised`() {
        // 'Q' is absent from the vocabulary; a partial sequence would never fire, so the
        // keyword must be rejected outright.
        assertNull(KwsKeywords.tokenise("QQQ", vocab))
        assertEquals("", KwsKeywords.build(listOf("qqq"), vocab))
    }

    @Test
    fun `punctuation and blanks are ignored`() {
        assertEquals("▁HE Y", KwsKeywords.tokenise("hey!", vocab))
        assertEquals("", KwsKeywords.build(listOf("   ", ""), vocab))
    }

    @Test
    fun `empty vocab yields nothing rather than crashing`() {
        assertNull(KwsKeywords.tokenise("HEY", emptySet()))
    }
}
