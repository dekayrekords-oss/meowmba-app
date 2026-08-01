package com.example.meowmba

import android.content.Context
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.memory.MetaMemoryConnector
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionEmotion
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.anyString

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockSecureStorage = mock(SecureStorageManager::class.java)
    private val mockContext = mock(Context::class.java)
    private val mockMemoryRepository = mock(MemoryRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(mockSecureStorage.context).thenReturn(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
        assertEquals(CompanionEmotion.IDLE, viewModel.uiState.value.emotion)
    }

    @Test
    fun `mic click transitions to LISTENING`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage, memoryRepository = mockMemoryRepository)
        viewModel.onMicClick()
        assertEquals(InteractionState.LISTENING, viewModel.uiState.value.interactionState)
        assertEquals(CompanionEmotion.ALERT, viewModel.uiState.value.emotion)
    }

    @Test
    fun `opening the memory screen reports a real MetaMemory link`() = runTest {
        val viewModel = CompanionViewModel(
            metaMemoryConnector = object : MetaMemoryConnector {
                override suspend fun isAvailable() = true
                override suspend fun push(memories: List<MemoryEntity>) = Result.success(0)
                override suspend fun pull() = Result.success(emptyList<MemoryEntity>())
            }
        )
        assertFalse(viewModel.uiState.value.metaMemoryLinked)
        viewModel.toggleMemoryScreen()
        assertTrue(viewModel.uiState.value.metaMemoryLinked)
    }

    @Test
    fun `the MetaMemory link is reported false when there is no connector to ask`() = runTest {
        // No storage/repository → the honest no-op connector → never claims a link.
        val viewModel = CompanionViewModel()
        viewModel.toggleMemoryScreen()
        assertFalse(viewModel.uiState.value.metaMemoryLinked)
    }

    @Test
    fun `playing an animation closes the chat sheet so the face is visible`() = runTest {
        // The sheet covers the bottom 60% — face included. Real tasks played in
        // the audit log while the user read chat and saw nothing.
        val viewModel = CompanionViewModel()
        viewModel.toggleChat()
        assertTrue(viewModel.uiState.value.isChatExpanded)
        viewModel.playCustomAnimation(
            com.example.meowmba.ui.companion.CustomAnimations.BUILT_INS.getValue("ear wiggle")
        )
        assertFalse(viewModel.uiState.value.isChatExpanded)
        assertEquals("ear wiggle", viewModel.uiState.value.activeAnimation?.name)
    }
}
