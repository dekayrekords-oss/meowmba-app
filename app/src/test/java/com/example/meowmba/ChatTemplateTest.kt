package com.example.meowmba

import com.example.meowmba.core.agent.ChatTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device chat template: instruct models must not receive raw flat
 * prompts (template-token leakage = "funny characters" on-device).
 */
class ChatTemplateTest {

    @Test
    fun `family detection from model path`() {
        assertEquals(ChatTemplate.Family.QWEN, ChatTemplate.familyFor("/models/qwen2.5-1.5b-instruct-q8.task"))
        assertEquals(ChatTemplate.Family.QWEN, ChatTemplate.familyFor("/models/Qwen2.5-0.5B.task"))
        assertEquals(ChatTemplate.Family.GEMMA, ChatTemplate.familyFor("/models/gemma3-1b-it-int4.task"))
        assertEquals(ChatTemplate.Family.HERMES, ChatTemplate.familyFor("/models/hermes3-3b.task"))
        assertEquals(ChatTemplate.Family.PLAIN, ChatTemplate.familyFor("/models/phi3.task"))
        assertEquals(ChatTemplate.Family.PLAIN, ChatTemplate.familyFor(""))
    }

    @Test
    fun `qwen and hermes wrap in ChatML`() {
        val wrapped = ChatTemplate.wrap("/models/qwen2.5-1.5b.task", "hello")
        assertEquals(
            "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n" +
                "<|im_start|>user\nhello<|im_end|>\n" +
                "<|im_start|>assistant\n",
            wrapped
        )
        assertEquals(wrapped, ChatTemplate.wrap("/models/hermes3-3b.task", "hello"))
    }

    @Test
    fun `gemma wraps in gemma turns`() {
        assertEquals(
            "<start_of_turn>user\nhello<end_of_turn>\n<start_of_turn>model\n",
            ChatTemplate.wrap("/models/gemma3-1b.task", "hello")
        )
    }

    @Test
    fun `unknown models pass through unchanged`() {
        assertEquals("hello", ChatTemplate.wrap("/models/phi3.task", "hello"))
    }

    @Test
    fun `template markers never leak into the raw prompt`() {
        val wrapped = ChatTemplate.wrap("/models/qwen2.5-1.5b.task", "hi")
        assertTrue(wrapped.startsWith("<|im_start|>system"))
        assertTrue(wrapped.endsWith("<|im_start|>assistant\n"))
    }
}
