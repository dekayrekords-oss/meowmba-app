package com.example.meowmba

import com.example.meowmba.core.audio.EnrollPhraseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnrollPhraseParserTest {

    @Test
    fun `explicit enroll phrases take the name`() {
        assertEquals("alice", EnrollPhraseParser.parse("enroll Alice"))
        assertEquals("bob", EnrollPhraseParser.parse("call me Bob"))
        assertEquals("sam lee", EnrollPhraseParser.parse("my name is Sam Lee"))
        assertEquals("wyatt", EnrollPhraseParser.parse("remember me as Wyatt"))
    }

    @Test
    fun `short introductions take a single name`() {
        assertEquals("alice", EnrollPhraseParser.parse("I'm Alice"))
        assertEquals("bob", EnrollPhraseParser.parse("I am Bob"))
        assertEquals("cara", EnrollPhraseParser.parse("this is Cara"))
    }

    @Test
    fun `mood and filler after I'm is not a name`() {
        assertNull(EnrollPhraseParser.parse("I'm fine"))
        assertNull(EnrollPhraseParser.parse("I'm hungry"))
        assertNull(EnrollPhraseParser.parse("I'm ready"))
        assertNull(EnrollPhraseParser.parse("this is great"))
        assertNull(EnrollPhraseParser.parse("call me a taxi"))
    }

    @Test
    fun `display names are title-cased`() {
        assertEquals("Alice", EnrollPhraseParser.displayName("alice"))
        assertEquals("Sam Lee", EnrollPhraseParser.displayName("sam lee"))
    }

    @Test
    fun `after being asked a bare name counts`() {
        assertEquals("alice", EnrollPhraseParser.parseAskedReply("Alice"))
        assertEquals("bob", EnrollPhraseParser.parseAskedReply("it's Bob"))
        assertNull(EnrollPhraseParser.parseAskedReply("hey"))
        assertNull(EnrollPhraseParser.parseAskedReply("yes"))
        assertNull(EnrollPhraseParser.parse("Alice"))
    }
}
