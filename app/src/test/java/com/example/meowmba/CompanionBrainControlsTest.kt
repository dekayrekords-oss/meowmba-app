package com.example.meowmba

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.audio.MicrophonePrivacy
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.behavior.PetCareState
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralModeStore
import com.example.meowmba.core.neural.NeuralSubstrate
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.thermal.ThermalGovernor
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.AvatarZone
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.HabitatAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

/** Brain controls route real touch/food events and preserve the user's other choices. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionBrainControlsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<CompanionViewModel>()
    private lateinit var androidMocks: ScopedCompanionAndroidMocks
    private var oldIdle: String? = null

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        androidMocks = ScopedCompanionAndroidMocks { dispatcher.scheduler.currentTime }
        ThermalGovernor.resetForTest()
        oldIdle = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
    }

    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        ThermalGovernor.resetForTest()
        androidMocks.close()
        Dispatchers.resetMain()
        if (oldIdle == null) System.clearProperty("meowmba.idleAnimations")
        else System.setProperty("meowmba.idleAnimations", oldIdle!!)
    }

    private fun storage(values: MutableMap<String, String>): SecureStorageManager =
        mock(SecureStorageManager::class.java).also { store ->
            `when`(store.context).thenReturn(mock(Context::class.java))
            `when`(store.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument(1); null }
                .`when`(store).savePreference(anyString(), anyString())
            `when`(store.getBooleanPreference(anyString(), anyBoolean())).thenAnswer {
                values[it.getArgument<String>(0)]?.toBooleanStrictOrNull() ?: it.getArgument<Boolean>(1)
            }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Boolean>(1).toString(); null }
                .`when`(store).saveBooleanPreference(anyString(), anyBoolean())
        }

    private fun model(values: MutableMap<String, String> = mutableMapOf()) = CompanionViewModel(
        secureStorage = storage(values),
        ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(),
        ioDispatcher = dispatcher,
    ).also {
        models += it
        // These are synchronous routing tests. Stop the real Default-dispatcher
        // tick before assertions; neural stepping has separate replay tests.
        it.viewModelScope.cancel()
    }

    @Test fun openingClassicBrainClosesMenusWithoutEnablingANetwork() = runTest(dispatcher) {
        val values = mutableMapOf(NeuralModeStore.KEY_MODE to NeuralMode.LEGACY.name)
        val vm = model(values)
        vm.openTiles()
        vm.toggleSettings()
        val preferencesBefore = values.toMap()

        vm.toggleNeuralInfo()

        val state = vm.uiState.value
        assertTrue(state.showNeuralInfo)
        assertFalse(state.showTiles)
        assertFalse(state.showSettings)
        assertEquals(NeuralMode.LEGACY, state.neuralMode)
        assertEquals(NeuralMode.LEGACY, state.neuralDiagnostics?.mode)
        assertEquals(preferencesBefore, values)
    }

    @Test fun explicitLocalActionRecoversUnpairedRemoteAndPersistsWithoutChangingPetOrPrivacy() = runTest(dispatcher) {
        val values = mutableMapOf(
            NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name,
            NeuralModeStore.KEY_SUBSTRATE to NeuralSubstrate.MALECNS_REMOTE.name,
            MicrophonePrivacy.PREF_KEY to "true",
            "camera_enabled" to "false",
        )
        val vm = model(values)
        vm.setAvatarStyle(AvatarStyle.FULL_FLY)
        vm.setAvatarScale(2.5f)
        vm.setBackground(BackgroundSelection(BackgroundKind.ANIMATION, BackgroundAnimation.LIBRARY.id))
        vm.setPetCare(true)
        vm.setPetLearning(false)
        vm.toggleNeuralInfo()
        val before = vm.uiState.value
        assertFalse(before.neuralDesktopPaired)
        assertEquals(NeuralSubstrate.MALECNS_REMOTE, before.neuralSubstrate)

        vm.startLocalBrainFromInfo()

        val after = vm.uiState.value
        assertEquals(NeuralMode.NEURAL, after.neuralMode)
        assertEquals(NeuralSubstrate.RATE, after.neuralSubstrate)
        assertEquals(NeuralMode.NEURAL.name, values[NeuralModeStore.KEY_MODE])
        assertEquals(NeuralSubstrate.RATE.name, values[NeuralModeStore.KEY_SUBSTRATE])
        assertFalse(after.neuralInputNotice.isNullOrBlank())
        assertTrue(after.showNeuralInfo)
        assertEquals(before.avatarStyle, after.avatarStyle)
        assertEquals(before.avatarScale, after.avatarScale, 0f)
        assertEquals(before.background, after.background)
        assertEquals(before.habitatBackgroundId, after.habitatBackgroundId)
        assertEquals(before.habitatMode, after.habitatMode)
        assertEquals(before.petCare, after.petCare)
        assertEquals(before.petLearningEnabled, after.petLearningEnabled)
        assertEquals(before.habitatCommand, after.habitatCommand)
        assertFalse(after.isMicEnabled)
        assertFalse(after.isCameraEnabled)
        assertEquals("true", values[MicrophonePrivacy.PREF_KEY])
        assertEquals("false", values["camera_enabled"])

        val restored = model(values).uiState.value
        assertEquals(NeuralMode.NEURAL, restored.neuralMode)
        assertEquals(NeuralSubstrate.RATE, restored.neuralSubstrate)
    }

    @Test fun classicControlsCannotFeedOrPetAnInactiveNetwork() = runTest(dispatcher) {
        val vm = model(mutableMapOf(NeuralModeStore.KEY_MODE to NeuralMode.LEGACY.name))
        vm.setPetCare(true)
        vm.toggleNeuralInfo()
        val before = vm.uiState.value

        vm.feedBrainFromInfo()
        vm.petBrainFromInfo()
        vm.enableNeuralControlFromInfo()

        assertEquals(before.petCare, vm.uiState.value.petCare)
        assertEquals(before.habitatCommand, vm.uiState.value.habitatCommand)
        assertFalse(vm.reflexSensory.value.affectionPulse)
        assertNotEquals("Pet sent to the brain", vm.uiState.value.neuralInputNotice)
        assertNotEquals("Food sent to the brain", vm.uiState.value.neuralInputNotice)
        assertEquals(NeuralMode.LEGACY, vm.uiState.value.neuralMode)
    }

    @Test fun explicitShadowActivationPreservesTheChosenSpikingCore() = runTest(dispatcher) {
        val values = mutableMapOf(NeuralModeStore.KEY_SUBSTRATE to NeuralSubstrate.SPIKING.name)
        val vm = model(values)
        vm.toggleNeuralInfo()
        assertEquals(NeuralMode.SHADOW, vm.uiState.value.neuralMode)
        vm.enableNeuralControlFromInfo()
        assertEquals(NeuralMode.NEURAL, vm.uiState.value.neuralMode)
        assertEquals(NeuralSubstrate.SPIKING, vm.uiState.value.neuralSubstrate)
        assertEquals(NeuralMode.NEURAL.name, values[NeuralModeStore.KEY_MODE])
        assertTrue(vm.uiState.value.showNeuralInfo)
    }

    @Test fun feedingOnAStaticBackgroundKeepsTheBrainPanelOpen() = runTest(dispatcher) {
        val vm = model(mutableMapOf(NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name))
        vm.setHabitatMode(false)
        vm.toggleNeuralInfo()
        vm.feedBrainFromInfo()
        assertEquals(HabitatAction.FEED, vm.uiState.value.habitatCommand.action)
        assertEquals("Food sent to the brain", vm.uiState.value.neuralInputNotice)
        assertTrue(vm.uiState.value.showNeuralInfo)
        assertFalse(vm.uiState.value.habitatMode)
        assertNotNull("Feed must start a visible reaction while Brain stays open", vm.uiState.value.activeAnimation)
    }

    @Test fun avatarNavigationClosesTheMonitorBeforeCoveringTheScene() = runTest(dispatcher) {
        val vm = model()
        vm.toggleNeuralInfo()
        vm.onAvatarZoneClick(AvatarZone.FOREHEAD)
        assertFalse(vm.uiState.value.showNeuralInfo)
        assertTrue(vm.uiState.value.showSettings)
        vm.toggleNeuralInfo()
        vm.openTiles()
        assertFalse(vm.uiState.value.showNeuralInfo)
        assertTrue(vm.uiState.value.showTiles)
        vm.toggleNeuralInfo()
        vm.toggleAppDrawer()
        assertFalse(vm.uiState.value.showNeuralInfo)
        assertTrue(vm.uiState.value.showAppDrawer)
        vm.toggleNeuralInfo()
        vm.toggleBrowser()
        assertFalse(vm.uiState.value.showNeuralInfo)
        assertTrue(vm.uiState.value.showBrowser)
    }

    @Test fun unpairedRemoteControlsDoNotClaimToDeliverInputs() = runTest(dispatcher) {
        val vm = model(mutableMapOf(
            NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name,
            NeuralModeStore.KEY_SUBSTRATE to NeuralSubstrate.MALECNS_REMOTE.name,
        ))
        vm.toggleNeuralInfo()
        val before = vm.uiState.value
        assertFalse(before.neuralDesktopPaired)

        vm.feedBrainFromInfo()
        vm.petBrainFromInfo()

        assertEquals(before.petCare, vm.uiState.value.petCare)
        assertEquals(before.habitatCommand, vm.uiState.value.habitatCommand)
        assertFalse(vm.reflexSensory.value.affectionPulse)
        assertNotEquals("Food sent to the brain", vm.uiState.value.neuralInputNotice)
        assertNotEquals("Pet sent to the brain", vm.uiState.value.neuralInputNotice)
        assertEquals(NeuralSubstrate.MALECNS_REMOTE, vm.uiState.value.neuralSubstrate)
    }

    @Test fun localFeedUsesTheExistingHabitatAndCareEventPath() = runTest(dispatcher) {
        val care = PetCareState(true, food = .3f, happiness = .4f, updatedAtMs = System.currentTimeMillis())
        val vm = model(mutableMapOf(
            NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name,
            "pet_care_v1" to care.encode(),
        ))
        vm.setHabitatMode(true)
        vm.toggleNeuralInfo()
        val before = vm.uiState.value

        vm.feedBrainFromInfo()

        val after = vm.uiState.value
        assertEquals(before.habitatCommand.id + 1L, after.habitatCommand.id)
        assertEquals(HabitatAction.FEED, after.habitatCommand.action)
        assertTrue(after.petCare.food > before.petCare.food)
        assertTrue(after.petCare.happiness > before.petCare.happiness)
        assertEquals("Food sent to the brain", after.neuralInputNotice)
    }

    @Test fun localPetUsesTheExistingAffectionPulseWithoutPretendingToFeed() = runTest(dispatcher) {
        val vm = model(mutableMapOf(NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name))
        vm.toggleNeuralInfo()
        val before = vm.uiState.value
        assertFalse(vm.reflexSensory.value.affectionPulse)

        vm.petBrainFromInfo()

        assertTrue(vm.reflexSensory.value.affectionPulse)
        assertEquals(before.petCare, vm.uiState.value.petCare)
        assertEquals(before.habitatCommand, vm.uiState.value.habitatCommand)
        assertEquals("Pet sent to the brain", vm.uiState.value.neuralInputNotice)
        vm.toggleNeuralInfo()
        vm.toggleNeuralInfo()
        assertNull(vm.uiState.value.neuralInputNotice)
    }

    @Test fun coolingImmediatelyBlocksControlsBeforeTheNextDiagnosticsRefresh() = runTest(dispatcher) {
        val vm = model(mutableMapOf(NeuralModeStore.KEY_MODE to NeuralMode.NEURAL.name))
        vm.toggleNeuralInfo()
        val before = vm.uiState.value
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_SEVERE, 1L)

        vm.feedBrainFromInfo()
        vm.petBrainFromInfo()

        assertEquals(before.petCare, vm.uiState.value.petCare)
        assertEquals(before.habitatCommand, vm.uiState.value.habitatCommand)
        assertFalse(vm.reflexSensory.value.affectionPulse)
        assertNotEquals("Food sent to the brain", vm.uiState.value.neuralInputNotice)
        assertNotEquals("Pet sent to the brain", vm.uiState.value.neuralInputNotice)
    }

    @Test fun brainSettingsOpensTheNeuralCategoryAndClosesTheMonitor() = runTest(dispatcher) {
        val vm = model()
        vm.toggleNeuralInfo()

        vm.openNeuralSettingsFromInfo()

        assertFalse(vm.uiState.value.showNeuralInfo)
        assertFalse(vm.uiState.value.showTiles)
        assertTrue(vm.uiState.value.showSettings)
        assertEquals("Neural / Lumen", vm.uiState.value.settingsInitialPage)
    }

    @Test fun pairingActionOpensConnectionsWithoutChangingTheSelectedRemoteBrain() = runTest(dispatcher) {
        val vm = model(mutableMapOf(NeuralModeStore.KEY_SUBSTRATE to NeuralSubstrate.MALECNS_REMOTE.name))
        vm.toggleNeuralInfo()

        vm.openBrainPairingFromInfo()

        assertFalse(vm.uiState.value.showNeuralInfo)
        assertFalse(vm.uiState.value.showTiles)
        assertTrue(vm.uiState.value.showSettings)
        assertEquals("Connections & memory", vm.uiState.value.settingsInitialPage)
        assertEquals(NeuralSubstrate.MALECNS_REMOTE, vm.uiState.value.neuralSubstrate)
        assertFalse(vm.uiState.value.neuralDesktopPaired)
    }
}
