package com.example.meowmba

import android.content.Context
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.memory.MetaMemoryConnector
import com.example.meowmba.core.audio.MicOwner
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.AvatarZone
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
        // Idle-animation director uses real delays; disable it here so its
        // forever-loop never runs on the test virtual clock.
        System.setProperty("meowmba.idleAnimations", "false")
        `when`(mockSecureStorage.context).thenReturn(mockContext)
        MicOwner.resetForTest()
    }

    @After
    fun tearDown() {
        MicOwner.resetForTest()
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

    @Test
    fun `playing an animation closes every full-screen panel, not just chat`() = runTest {
        // Each panel added since the chat-sheet fix (tiles, Settings, Model
        // Manager, Privacy, Memory, Weather, Background, Nav, Agents, Vault)
        // is a later Box child than AvatarRenderer, so any one left open
        // paints over the face exactly like the chat sheet used to.
        val viewModel = CompanionViewModel()
        viewModel.openTiles()
        viewModel.toggleSettings()
        viewModel.toggleModelManager()
        viewModel.togglePrivacyDashboard()
        viewModel.toggleMemoryScreen()
        viewModel.toggleWeatherScreen()
        viewModel.toggleBackgroundScreen()
        viewModel.toggleNavScreen()
        viewModel.toggleAgentsScreen()
        viewModel.toggleVaultScreen()

        viewModel.playCustomAnimation(
            com.example.meowmba.ui.companion.CustomAnimations.BUILT_INS.getValue("ear wiggle")
        )

        val state = viewModel.uiState.value
        assertFalse(state.showTiles)
        assertFalse(state.showSettings)
        assertFalse(state.showModelManager)
        assertFalse(state.showPrivacyDashboard)
        assertFalse(state.showMemoryScreen)
        assertFalse(state.showWeatherScreen)
        assertFalse(state.showBackgroundScreen)
        assertFalse(state.showNavScreen)
        assertFalse(state.showAgentsScreen)
        assertFalse(state.showVaultScreen)
    }

    @Test
    fun `setImageServerUrl trims and persists the URL`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        viewModel.setImageServerUrl("  http://192.168.1.10:9100  ")
        assertEquals("http://192.168.1.10:9100", viewModel.uiState.value.imageServerUrl)
        verify(mockSecureStorage).savePreference("image_server_url", "http://192.168.1.10:9100")
    }

    @Test
    fun `setMusicServerUrl trims and persists the URL`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        viewModel.setMusicServerUrl("  http://192.168.1.10:9200/  ")
        assertEquals("http://192.168.1.10:9200", viewModel.uiState.value.musicServerUrl)
        verify(mockSecureStorage).savePreference("music_server_url", "http://192.168.1.10:9200")
    }

    @Test
    fun `setVideoServerUrl trims trailing slashes and persists the URL`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        viewModel.setVideoServerUrl("  http://192.168.1.10:9300/  ")
        assertEquals("http://192.168.1.10:9300", viewModel.uiState.value.videoServerUrl)
        verify(mockSecureStorage).savePreference("video_server_url", "http://192.168.1.10:9300")
    }

    @Test
    fun `preferServerImageGen defaults to false`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        assertFalse(viewModel.uiState.value.preferServerImageGen)
    }

    @Test
    fun `setPreferServerImageGen updates state`() = runTest {
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        viewModel.setPreferServerImageGen(true)
        assertTrue(viewModel.uiState.value.preferServerImageGen)
        verify(mockSecureStorage).saveBooleanPreference("prefer_server_image_gen", true)
    }

    @Test
    fun `ear tap re-enable re-arms wake standby`() = runTest {
        // Regression: tapping an ear to cut the mic and tapping again used to flip
        // isMicEnabled back on WITHOUT calling startWakeStandby(), leaving the wake
        // phrase dead until the next background/foreground cycle — which reads from
        // the user's side exactly as "the wake word stopped working".
        val viewModel = CompanionViewModel(secureStorage = mockSecureStorage)
        viewModel.setWakeWordEnabled(true)
        // Face mode defaults to always-on listening (no wake phrase, no MicOwner),
        // which is the other half of the re-enable path. This regression is about
        // the wake-standby half, so gate face mode behind the wake phrase here.
        viewModel.setRequireWakeInFace(true)

        // Ear tap cuts the mic...
        viewModel.onAvatarZoneClick(AvatarZone.EAR_LEFT)
        assertFalse(viewModel.uiState.value.isMicEnabled)
        assertEquals(InteractionState.MUTED, viewModel.uiState.value.interactionState)

        // ...and a mic tap while cut shows the sentinel caption. Unless the wake
        // standby is re-armed on re-enable, this caption must survive the ear tap.
        viewModel.onMicClick()
        assertTrue(viewModel.uiState.value.captionText!!.startsWith("Microphone is off"))

        // Ear tap again re-enables the mic AND must re-arm wake listening.
        viewModel.onAvatarZoneClick(AvatarZone.EAR_LEFT)
        assertTrue(viewModel.uiState.value.isMicEnabled)
        assertEquals(InteractionState.IDLE, viewModel.uiState.value.interactionState)
        assertEquals(MicOwner.IN_APP, MicOwner.current)
        assertEquals(
            "Say \"Hey ${viewModel.uiState.value.name}\"…",
            viewModel.uiState.value.captionText
        )
    }
}
