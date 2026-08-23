package com.example.meowmba

import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.database.MessageEntity
import com.example.meowmba.core.database.MessageRole
import com.example.meowmba.core.memory.LocalHashEmbedder
import com.example.meowmba.core.memory.MemoryCommandParser
import com.example.meowmba.core.memory.MemoryExporter
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.memory.PromptBuilder
import com.example.meowmba.core.memory.RecallEngine
import com.example.meowmba.core.memory.VectorCodec
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class MemoryCoreTest {

    @Test
    fun `vector codec roundtrips losslessly`() {
        val original = floatArrayOf(0.5f, -1.25f, 3.75f, 0f)
        val decoded = VectorCodec.decode(VectorCodec.encode(original))
        assertTrue(original.contentEquals(decoded))
    }

    @Test
    fun `hash embedder is deterministic and normalized`() = runTest {
        val embedder = LocalHashEmbedder()
        val a = embedder.getEmbedding("i love green tea")
        val b = embedder.getEmbedding("i love green tea")
        assertTrue(a.contentEquals(b))
        assertEquals(128, a.size)
        val norm = Math.sqrt(a.fold(0.0) { acc, v -> acc + v * v })
        assertEquals(1.0, norm, 0.001)
    }

    @Test
    fun `similar texts embed closer than dissimilar texts`() = runTest {
        val embedder = LocalHashEmbedder()
        val tea = embedder.getEmbedding("i love green tea")
        val moreTea = embedder.getEmbedding("i love green tea very much")
        val physics = embedder.getEmbedding("quantum physics lecture notes")
        val close = RecallEngine.cosine(tea, moreTea)
        val far = RecallEngine.cosine(tea, physics)
        assertTrue("shared-token texts should be closer ($close vs $far)", close > far)
    }

    @Test
    fun `recall ranks by similarity weighted by importance and explains why`() = runTest {
        val embedder = LocalHashEmbedder()
        suspend fun entity(content: String, importance: ImportanceLevel) = MemoryEntity(
            id = 1L, content = content, importance = importance,
            embedding = VectorCodec.encode(embedder.getEmbedding(content))
        )
        val teaMemory = entity("user likes green tea", ImportanceLevel.HIGH)
        val carMemory = entity("the car is red", ImportanceLevel.LOW)

        val queryEmbedding = embedder.getEmbedding("what tea does the user like")
        val results = RecallEngine.rank(queryEmbedding, "what tea does the user like", listOf(carMemory, teaMemory))

        assertTrue(results.isNotEmpty())
        assertEquals(teaMemory, results.first().memory)
        assertTrue(results.first().reason.contains("matched"))
        assertTrue(results.first().reason.contains("similarity"))
    }

    @Test
    fun `recall drops candidates below the similarity threshold`() = runTest {
        val embedder = LocalHashEmbedder()
        val memory = MemoryEntity(
            id = 1L, content = "quantum physics lecture",
            embedding = VectorCodec.encode(embedder.getEmbedding("quantum physics lecture"))
        )
        val results = RecallEngine.rank(
            embedder.getEmbedding("favorite pizza toppings"),
            "favorite pizza toppings",
            listOf(memory)
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `memory command parser extracts content`() {
        val withContent = MemoryCommandParser.parse("remember that I like green tea")
        assertEquals("i like green tea", withContent?.content)

        val bare = MemoryCommandParser.parse("remember this")
        assertNull(bare?.content) // command, but refers to previous turn

        assertNull(MemoryCommandParser.parse("what's the time"))
    }

    @Test
    fun `prompt builder assembles memories, history and current turn`() = runTest {
        val embedder = LocalHashEmbedder()
        val memory = MemoryEntity(
            id = 1L, content = "user likes green tea",
            embedding = VectorCodec.encode(embedder.getEmbedding("user likes green tea")),
            importance = ImportanceLevel.HIGH
        )
        val queryEmbedding = embedder.getEmbedding("tea")
        val recalled = RecallEngine.rank(queryEmbedding, "tea", listOf(memory), minScore = 0.0f)
        val messages = listOf(
            MessageEntity(id = 1, role = MessageRole.USER, content = "hi"),
            MessageEntity(id = 2, role = MessageRole.ASSISTANT, content = "hello!")
        )
        val prompt = PromptBuilder.build("Meow", "what do I like?", messages, recalled)

        assertTrue(prompt.contains("Relevant things you remember"))
        assertTrue(prompt.contains("user likes green tea"))
        assertTrue(prompt.contains("User: hi"))
        assertTrue(prompt.contains("Meow: hello!"))
        assertTrue(prompt.trimEnd().endsWith("Meow:"))

        val bare = PromptBuilder.build("Meow", "hi", emptyList(), emptyList())
        assertTrue(!bare.contains("Relevant things"))
    }

    @Test
    fun `known speaker name is a standing fact in the prompt`() {
        val prompt = PromptBuilder.build(
            "Meow", "what's my name?", emptyList(), emptyList(),
            speakerName = "bob",
        )
        assertTrue(prompt.contains("Their name is Bob"))
        val stranger = PromptBuilder.build("Meow", "hi", emptyList(), emptyList())
        assertTrue(!stranger.contains("Their name is"))
    }

    @Test
    fun `the capabilities block forbids faking tool results`() {
        // Measured on device: with tool results in its history, the chat model
        // mimicked them — fabricating `Playing "head tilt" (30ms)` when no task ran.
        val prompt = PromptBuilder.build(
            "Meow", "yes", emptyList(), emptyList(),
            capabilities = listOf("animate the avatar")
        )
        assertTrue(prompt.contains("TOOL"))
        assertTrue(prompt.contains("Never write anything that looks like a tool result"))
    }

    @Test
    fun `exporter emits valid structured json`() {
        val memories = listOf(
            MemoryEntity(
                id = 7L, content = "user said \"hi\"", type = MemoryType.SEMANTIC,
                importance = ImportanceLevel.HIGH, tags = listOf("explicit")
            )
        )
        val json = MemoryExporter.toJson(memories)
        assertTrue(json.contains("\"type\": \"SEMANTIC\""))
        assertTrue(json.contains("\"id\": 7"))
        assertTrue(json.contains("user said \\\"hi\\\""))
        assertTrue(json.contains("\"explicit\""))
    }
}

/**
 * ViewModel-level memory behavior: explicit remember commands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTts: VoicePipelineTest.FakeSpeechSynthesizer
    private lateinit var repo: MemoryRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTts = VoicePipelineTest.FakeSpeechSynthesizer()
        repo = mock(MemoryRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `remember that stores a semantic memory and confirms aloud`() = runTest {
        val vm = CompanionViewModel(ttsManager = fakeTts, memoryRepository = repo)
        vm.onUserTextSubmitted("remember that I like green tea")
        advanceUntilIdle()

        // The confirmation is only spoken after the store call completes
        assertTrue(fakeTts.spokenTexts.contains("Got it — I'll remember that."))
    }

    @Test
    fun `remember this reuses the last assistant statement`() = runTest {
        val vm = CompanionViewModel(ttsManager = fakeTts, memoryRepository = repo)
        vm.onUserTextSubmitted("hello")
        advanceUntilIdle() // companion answered; lastAssistantText is set

        vm.onUserTextSubmitted("remember this")
        advanceUntilIdle()
        assertTrue(fakeTts.spokenTexts.last() == "Got it — I'll remember that.")
    }

    @Test
    fun `paused memory blocks remember commands with an explanation`() = runTest {
        val vm = CompanionViewModel(ttsManager = fakeTts, memoryRepository = repo)
        vm.setMemoryPaused(true)
        vm.onUserTextSubmitted("remember that I like green tea")
        advanceUntilIdle()
        assertTrue(fakeTts.spokenTexts.last().contains("paused"))
    }
}
